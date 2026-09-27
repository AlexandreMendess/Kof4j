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
 * Fatia MK-1 do tracker 3.1 (D-MAKEALIVE Q2: providers genéricos) — o
 * PROVIDER DE SISTEMA DE ARQUIVOS como corpo de usuário: o host
 * `kof.makealive` NÃO referencia kof.io (host é composição
 * target-independent e roda até no Native; File é JVM/JS por natureza),
 * então a face fs vive no CORPO do provider, escrita em KOF puro e validada
 * aqui ponta a ponta: apply grava arquivos reais, plan sobre mundo já
 * convergido é no-op (idempotência vinda do READ do provider — estado na
 * DISCA), prop nova vira update com o arquivo reescrito, destroy apaga e o
 * estado fica vazio. Serialização `k=v\n` + parse por `split`/`indexOf`/
 * `substring`/`keys()` — superfícies MEDIDAS 20/09 (o "D0 sem parser" da
 * sonda inicial era falso: StringMethodRegistry tem split/substring/toInt;
 * Map.keys() é JVM==JS, target-dependent só no Native). JVM==JS byte-parity
 * (cada motor no SEU diretório — a lição do token de H2 não vale só para db).
 * Este programa é tambem o exemplo `fs` de docs/stdlib/makealive.md.
 */
class MakealiveFsProviderE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private record Run(boolean ok, String output) {}

    private Run runJvm(Path src, Path out, String base) throws Exception {
        Files.writeString(src, PROGRAM.replace("__BASE__", base));
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

    private Run runJs(Path src, Path out, String base) throws Exception {
        Files.writeString(src, PROGRAM.replace("__BASE__", base));
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
        import kof.io
        import kof.makealive

        class FsDir {
            String base = ""
        }

        String fsPath(FsDir w, Resource r) {
            return w.base + "/" + r.name() + ".mk"
        }

        Map<String, String> fsRead(FsDir w, Resource r) {
            var f = File(fsPath(w, r))
            var m = mapOf()
            if (!f.exists()) { return m }
            var t = f.readText()
            if (t == null) { return m }
            if (t.isEmpty()) { return m }
            var lines = t.split("\\n")
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

        Bool fsSet(FsDir w, Resource r, Map<String, String> props) {
            var f = File(fsPath(w, r))
            var buf = ""
            var ks = props.keys()
            var i = 0
            while (i < ks.size) {
                var k = ks.get(i)
                buf = buf + k + "=" + props.get(k) + "\\n"
                i = i + 1
            }
            f.writeText(buf)
            // workaround §382: a ponte JS do writeText devolve o codigo 0/-1
            // (0 e falsy em JS) — confirmar o efeito testando o mundo.
            return f.exists()
        }

        Bool fsDelete(FsDir w, Resource r) {
            var f = File(fsPath(w, r))
            if (!f.exists()) { return true }
            f.delete()
            // workaround §382 (mesmo shape do delete na ponte JS)
            return !f.exists()
        }

        main() {
            var w = FsDir()
            w.base = "__BASE__"
            var p = Provider(
                (r: Resource) -> fsRead(w, r),
                (r: Resource, m: Map<String, String>) -> fsSet(w, r, m),
                (r: Resource) -> fsDelete(w, r)
            )
            var d = Infrastructure("web")
            d.resource("file", "index")
            d.prop("index", "html", "hi")
            d.resource("file", "style")
            d.prop("style", "css", "body")
            var rep = apply(d, State(), p)
            println("created:" + rep.created.size())
            var pl2 = plan(d, rep.state)
            println("noop:" + pl2.creates.size() + "/" + pl2.updates.size() + "/" + pl2.deletes.size())
            var f2 = File(w.base + "/index.mk")
            println("disk-index:" + f2.readText())
            d.prop("index", "lang", "en")
            var rep3 = apply(d, rep.state, p)
            println("upd:" + rep3.updated.size())
            println("disk2:" + f2.readText())
            var rep4 = destroy(d, rep3.state, p)
            println("destroy:" + rep4.deleted.size() + " gone:" + f2.exists() + " gone2:" + File(w.base + "/style.mk").exists())
        }
        """;

    @Test
    void fsProviderLifecycleParityJvmJs() throws Exception {
        Path src = tmp.resolve("MkFs.kf");
        Files.createDirectories(tmp.resolve("fs-jvm"));
        Files.createDirectories(tmp.resolve("fs-js"));
        Run jvm = runJvm(src, tmp.resolve("o-jvm"), tmp.resolve("fs-jvm").toString());
        assertTrue(jvm.ok(), () -> "JVM failed: " + jvm.output());
        Run js = runJs(src, tmp.resolve("o-js"), tmp.resolve("fs-js").toString());
        assertTrue(js.ok(), () -> "JS failed: " + js.output());
        for (String e : new String[] {
                "created:2",
                "noop:0/0/0",
                "disk-index:html=hi\\n",
                "upd:1",
                "disk2:html=hi\\nlang=en\\n",
                "destroy:2 gone:false gone2:false",
        }) {
            assertTrue(jvm.output().contains(e.replace("\\n", "\n")), () -> "JVM expected '" + e + "' in:\n" + jvm.output());
        }
        String a = jvm.output().replace(tmp.resolve("fs-jvm").toString(), "BASE");
        String b = js.output().replace(tmp.resolve("fs-js").toString(), "BASE");
        assertEquals(a, b, "3.1 fs-provider golden: JVM/JS diverge");
    }
}
