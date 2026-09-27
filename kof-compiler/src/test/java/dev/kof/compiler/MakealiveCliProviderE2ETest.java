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
 * Fatia MK-1 do tracker 3.1 (D-MAKEALIVE Q2 — providers genéricos): o
 * PROVIDER CLI como corpo de usuário. A face foi MEDIDA 20/09 (standalone):
 * `shell.run(prog, listOf(args))` -> Result{exitCode, stdout}; comando
 * inexistente = exitCode -1 (nunca throw — ao contrário do `http`, que
 * propaga falha de conexão nos DOIS motores); `pipeline(List<List<String>>)`
 * composto; JVM==JS byte-identico no probe. Aqui o provider grava o recurso
 * como arquivo no diretório do design (`tee`/`printf` via `sh -c`, `rm`,
 * `test -f`) e lê o mundo pelos exit codes + stdout (`cat` + parse `k=v`),
 * tudo em .kf puro. O DISCO é o estado compartilhado entre motores: run1 JVM
 * cria (applied:1, resettle:0 = leitura fiel), run2 JVM e o run JS entram num
 * mundo já vivo (applied:1=overwrite com resettle:0 = leitura do MUNDO DO OUTRO
 * MOTOR funcionando, não da memória do próprio processo) e o destroy fecha o
 * ciclo nomeando o recurso sumido. Paridade byte JVM(run2)==JS. O workaround
 * §382 não é necessário: `shell` não é ponte kof.io — exitCode vem tipado.
 */
class MakealiveCliProviderE2ETest {

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
        import kof.shell
        import kof.makealive

        class CliWorld {
            String base = ""
        }

        String cliPath(CliWorld w, String name) {
            return w.base + "/" + name + ".res"
        }

        Bool cliExists(CliWorld w, String name) {
            var r = shell.run("test", listOf("-f", cliPath(w, name)))
            return r.exitCode == 0
        }

        Map<String, String> cliRead(CliWorld w, Resource r) {
            var m = mapOf()
            if (!cliExists(w, r.name())) { return m }
            var out = shell.run("cat", listOf(cliPath(w, r.name())))
            if (out.exitCode != 0) { return m }
            var lines = out.stdout.split("\\n")
            var i = 0
            while (i < lines.length) {
                var ln = lines[i]
                if (!ln.isEmpty()) {
                    var p = ln.indexOf("=")
                    if (p > 0) { m.put(ln.substring(0, p), ln.substring(p + 1)) }
                }
                i = i + 1
            }
            return m
        }

        Bool cliSet(CliWorld w, Resource r, Map<String, String> props) {
            var buf = ""
            var ks = props.keys()
            var i = 0
            while (i < ks.size) {
                var k = ks.get(i)
                buf = buf + k + "=" + props.get(k) + "\\n"
                i = i + 1
            }
            var q = "'" + buf + "'"
            shell.run("sh", listOf("-c", "printf %s " + q + " > " + "'" + cliPath(w, r.name()) + "'"))
            return cliExists(w, r.name())
        }

        Bool cliDelete(CliWorld w, Resource r) {
            shell.run("rm", listOf("-f", cliPath(w, r.name())))
            return !cliExists(w, r.name())
        }

        main() {
            var w = CliWorld()
            w.base = "__BASE__"
            var p = Provider(
                (r: Resource) -> cliRead(w, r),
                (r: Resource, m: Map<String, String>) -> cliSet(w, r, m),
                (r: Resource) -> cliDelete(w, r)
            )
            var d = Infrastructure("cli")
            d.resource("file", "note")
            d.prop("note", "text", "hello")
            var rep = apply(d, State(), p)
            println("applied:" + rep.created.size() + "/" + rep.updated.size() + "/" + rep.deleted.size())
            var rep2 = apply(d, rep.state, p)
            println("resettle:" + rep2.created.size() + "/" + rep2.updated.size() + "/" + rep2.deleted.size())
            if (__DOOM__ == 1) {
                var rep3 = destroy(d, rep2.state, p)
                println("destroyed:" + rep3.deleted.size())
                println("alive-after:" + cliExists(w, "note"))
            }
        }
        """;

    @Test
    void cliProviderCrossEngineRoundtripParityJvmJs() throws Exception {
        Path world = tmp.resolve("world");
        Files.createDirectories(world);

        // runA (JVM, sem destroy): cria o mundo e O DEIXA VIVO.
        Path srcA = tmp.resolve("MkCliA.kf");
        Files.writeString(srcA, PROGRAM.replace("__BASE__", world.toString()).replace("__DOOM__", "0"));
        Run a = runJvm(srcA, tmp.resolve("o-a"));
        assertTrue(a.ok(), () -> "JVM runA failed: " + a.output());
        assertTrue(a.output().contains("applied:1/0/0"), () -> a.output());
        assertTrue(a.output().contains("resettle:0/0/0"), () -> a.output());
        assertTrue(Files.exists(world.resolve("note.res")), () -> "runA deve deixar o mundo vivo");

        // runB (JS, com destroy): o provider READ JS enxerga os ARQUIVOS ESCRITOS
        // POR PROCESSOS DO JVM (cross-engine world, não memória própria); o
        // resettle no-op é a prova da leitura fiel.
        Path srcB = tmp.resolve("MkCliB.kf");
        Files.writeString(srcB, PROGRAM.replace("__BASE__", world.toString()).replace("__DOOM__", "1"));
        Run b = runJs(srcB, tmp.resolve("o-b"));
        assertTrue(b.ok(), () -> "JS runB failed: " + b.output());
        assertTrue(b.output().contains("applied:1/0/0"), () -> b.output());
        assertTrue(b.output().contains("resettle:0/0/0"),
                () -> "JS deve ler o mundo escrito pelos processos do JVM:\n" + b.output());
        assertTrue(b.output().contains("destroyed:1"), () -> b.output());
        assertTrue(b.output().contains("alive-after:false"), () -> b.output());

        // runC (JVM, idêntica à B em mundo limpo): byte-parity com o run JS.
        Path srcC = tmp.resolve("MkCliC.kf");
        Files.writeString(srcC, PROGRAM.replace("__BASE__", world.toString()).replace("__DOOM__", "1"));
        Run c = runJvm(srcC, tmp.resolve("o-c"));
        assertTrue(c.ok(), () -> "JVM runC failed: " + c.output());
        assertEquals(c.output(), b.output(), "3.1 cli-provider golden: JVM/JS diverge");
    }
}
