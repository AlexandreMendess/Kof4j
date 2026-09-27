package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Linha 3.3 do tracker universal (fila §5 do makealive-plan): o LOOP de
 * reconciliação. {@code reconcile(design, provider, intervalMs)} delega ao
 * {@code scheduler.every} e cada tick roda {@code apply} dentro de
 * {@code spawn} (forma CONC003-JS-01, idêntica ao schedule do
 * workflow-sched-host). O mundo é MEMÓRIA DO PROGRAMA (classe
 * compartilhada closure→provider): o teste prova (i) o guard do intervalo
 * recusa {@code <= 0} nomeando {@code intervalMs} (R6), (ii) o laço CONVERGE
 * o mundo sozinho em <=1.2s (poll com sleep, que no JS deixa o pump
 * cooperativo rodar os ticks), (iii) {@code scheduler.cancel(id)} mata o
 * laço: após destroy, 4 intervalos se passam e o mundo permanece vazio com
 * o contador de escritas congelado ({@code dead-loop:true/true}) — sem
 * cancelamento o tick recriaria "alpha" (é a semântica de reconcile!).
 * Correção medida da fila: Native NÃO precisa de stub CRON001 aqui —
 * {@code every} é real em todos os alvos (SCHED001 05/09); o Native ganha
 * pin de COMPILAÇÃO (como todo host). Saídas sem números/ids = byte-parity
 * JVM==JS possível.
 */
class MakealiveReconcileE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private record Run(boolean ok, String output) {}

    private Run runJvm(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.JVM);
        if (!r.success()) return new Run(false, diags(r));
        var oldOut = System.out;
        var buf = new ByteArrayOutputStream();
        System.setOut(new java.io.PrintStream(buf, true));
        try {
            var cl = new URLClassLoader(new java.net.URL[]{out.toUri().toURL()},
                    getClass().getClassLoader());
            Class.forName("Default.Main", true, cl)
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            return new Run(true, buf.toString());
        } catch (java.lang.reflect.InvocationTargetException e) {
            return new Run(false, "THROW: " + e.getCause());
        } finally {
            System.setOut(oldOut);
        }
    }

    private Run runJs(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.JS);
        if (!r.success()) return new Run(false, diags(r));
        var buf = new ByteArrayOutputStream();
        try {
            int rc = dev.kof.runtime.KofJsRunner.run(
                    out.resolve("Default.mjs"), buf,
                    new ByteArrayInputStream(new byte[0]), buf);
            return new Run(rc == 0, buf.toString());
        } catch (Exception e) {
            return new Run(false, "THROW: " + e.getMessage() + "\n" + buf);
        }
    }

    private static String diags(CompilationResult r) {
        StringBuilder sb = new StringBuilder();
        r.diagnostics().getDiagnostics().forEach(d -> sb.append(d.message()).append("\n"));
        return sb.toString();
    }

    private static final String PROGRAM = """
        import kof.makealive

        class MemWorld {
            List<String> items = listOf()
            Int writes = 0
        }

        Map<String, String> memRead(MemWorld w, Resource r) {
            var m = mapOf()
            if (!w.items.contains(r.name())) { return m }
            m.put("text", "v-" + r.name())
            return m
        }

        Bool memSet(MemWorld w, Resource r, Map<String, String> props) {
            if (!w.items.contains(r.name())) { w.items.add(r.name()) }
            w.writes = w.writes + 1
            return true
        }

        Bool memDelete(MemWorld w, Resource r) {
            var i = 0
            var found = -1
            while (i < w.items.size && found < 0) {
                if (w.items.get(i) == r.name()) { found = i }
                i = i + 1
            }
            if (found >= 0) { w.items.remove(found) }
            return !w.items.contains(r.name())
        }

        main() {
            var w = MemWorld()
            var p = Provider(
                (r: Resource) -> memRead(w, r),
                (r: Resource, m: Map<String, String>) -> memSet(w, r, m),
                (r: Resource) -> memDelete(w, r)
            )
            var d = Infrastructure("recon")
            d.resource("kv", "alpha")
            d.prop("alpha", "text", "v-alpha")
            var g1 = false
            try {
                reconcile(d, p, 0)
            } catch (String e) {
                g1 = e.indexOf("intervalMs") > 0
            }
            println("guard-interval:" + g1)
            var id = reconcile(d, p, 50)
            println("started:" + (id != ""))
            // um sono longo so (o padrao JS estabelecido do schedule do workflow;
            // polling curto dentro de while compete com o pump cooperativo)
            time.sleep(300)
            var seen = w.items.contains("alpha")
            println("converged:" + seen)
            scheduler.cancel(id)
            time.sleep(200)
            // o state do laco e interno (celula do job); para o destroy honesto o
            // usuario reconstrói o state pelo READ do provider: apply com State()
            // vazio nao toca o mundo ja convergido alem do registro.
            var rep = apply(d, State(), p)
            var dead = destroy(d, rep.state, p)
            var frozen = w.writes
            println("stable:" + !w.items.contains("alpha"))
            time.sleep(400)
            println("dead-loop:" + (!w.items.contains("alpha")) + "/" + (w.writes == frozen))
        }
        """;

    @Test
    void reconcileLoopConvergesAndDiesOnCancelJvmJsNative() throws Exception {
        Path src = tmp.resolve("MkReconcile.kf");
        Files.writeString(src, PROGRAM);
        Run jvm = runJvm(src, tmp.resolve("o-jvm"));
        assertTrue(jvm.ok(), () -> "JVM failed: " + jvm.output());
        Run js = runJs(src, tmp.resolve("o-js"));
        assertTrue(js.ok(), () -> "JS failed: " + js.output());
        String expected = "guard-interval:true\nstarted:true\nconverged:true\nstable:true\n"
                + "dead-loop:true/true\n";
        assertEquals(expected, jvm.output(),
                () -> "3.3 reconcile golden JVM diverge do contrato:\n" + jvm.output());
        assertEquals(jvm.output(), js.output(), "3.3 reconcile golden: JVM/JS diverge");
        var native_ = driver.compile(src, tmp.resolve("o-native"), Target.NATIVE);
        assertTrue(native_.success(), () -> "Native must compile (every real, sem stub): "
                + diags(native_));
    }
}
