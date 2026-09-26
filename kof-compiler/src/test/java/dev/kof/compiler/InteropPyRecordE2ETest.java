package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * X2 fatia 2 (passo B, §516): args/resultado RECORD no motor Python via a
 * face `KofPy.callJson` (JSON cru — o usuário compõe `json.encode(args)` e
 * `json.decode<R>`). Golden = saída JVM REAL medida; paridade x86/JS byte a
 * byte; recusas nomeadas INTEROP006; gate JSN002 para elemento-coleção
 * aninhado no nativo (nunca "null" falso — R6).
 */
class InteropPyRecordE2ETest {
    private final CompilerDriver driver = new CompilerDriver();

    @TempDir
    Path tmp;

    private record Run(boolean ok, String out) {}

    private static void requirePython3() {
        assumeTrue(Files.isExecutable(Path.of("/usr/bin/python3")),
                "python3 ausente — gate de ambiente, não regressão");
    }

    private Run runJvm(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.JVM);
        if (!r.success()) return new Run(false, diags(r));
        var oldOut = System.out;
        var buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true));
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

    private Run runNative(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        if (!r.success()) return new Run(false, diags(r));
        Path bin = out.resolve("Default/Main");
        assertTrue(Files.exists(bin), "binário nativo deve existir");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String o = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new Run(p.waitFor() == 0, o);
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
        for (Diagnostic d : r.diagnostics().getDiagnostics()) {
            sb.append(d.code()).append(": ").append(d.message()).append('\n');
        }
        return sb.toString();
    }

    private static final String RECORDS = """
            import kof.interop
            record Point(Int x, Int y)
            record Msg(String text)
            main() {
                var py = KofPy("def norm(p):\\n    return {'x': p['x']*2, 'y': p['y']*2}\\n" +
                    "def shout(m):\\n    return {'text': m['text'] + chr(10) + 'q' + chr(34) + '10' + chr(34) + '!'}\\n" +
                    "def zero():\\n    return 0")
                println(json.encode(listOf(Point(3, 4), Point(5, 6))))
                var q = json.decode<Point>(py.callJson("norm", json.encode(listOf(Point(3, 4)))))
                println(q.x())
                println(q.y())
                var m = json.decode<Msg>(py.callJson("shout", json.encode(listOf(Msg("he-\\"llo\\"")))))
                println(m.text)
                println(json.decode<Int>(py.callJson("zero", "[]")))
                println(json.encode(Msg("rt-OK")))
            }
            """;

    @Test
    void recordRoundTripMatchesJvmAcrossEngineTargets() throws Exception {
        requirePython3();
        assumeTrue(Files.exists(Path.of("/usr/bin/as")) || Files.exists(Path.of("/bin/as")),
                "as ausente — gate de ambiente, não regressão");
        Path src = tmp.resolve("InteropRec.kf");
        Files.writeString(src, RECORDS);
        Run jvm = runJvm(src, tmp.resolve("out-jvm"));
        assertTrue(jvm.ok(), "JVM deve rodar: " + jvm.out());
        assertEquals(ORACLE, jvm.out(), "golden JVM (medido 26/09)");

        Run nat = runNative(src, tmp.resolve("out-nat"));
        assertTrue(nat.ok(), "NATIVE deve rodar: " + nat.out());
        assertEquals(jvm.out(), nat.out(), "X2 fatia 2: x86 ≡ JVM (record round-trip)");

        Run js = runJs(src, tmp.resolve("out-js"));
        assertTrue(js.ok(), "JS deve rodar: " + js.out());
        assertEquals(jvm.out(), js.out(), "X2 fatia 2: JS ≡ JVM (record round-trip)");
    }

    private static final String ORACLE = "[{\"x\":3,\"y\":4},{\"x\":5,\"y\":6}]\n6\n8\nhe-\"llo\"\nq\"10\"!\n0\n{\"text\":\"rt-OK\"}\n";

    @Test
    void remoteErrorOnRecordArgIsNamedInterop006(@TempDir Path d) throws Exception {
        requirePython3();
        Path src = d.resolve("InteropRecErr.kf");
        Files.writeString(src, """
                import kof.interop
                record Point(Int x, Int y)
                main() {
                    var py = KofPy("def boom(p):\\n    return p['nope']")
                    try {
                        py.callJson("boom", json.encode(listOf(Point(1, 2))))
                    } catch (String e) {
                        println(e.substring(0, 11))
                    }
                }
                """);
        Run jvm = runJvm(src, d.resolve("out-jvm"));
        assertTrue(jvm.ok(), "JVM: " + jvm.out());
        assertTrue(jvm.out().startsWith("INTEROP006"),
                "falha remota nomeada no round-trip de record: " + jvm.out());
    }

    @Test
    void nestedCollectionElementIsDiagnosedOnNative(@TempDir Path d) throws Exception {
        Path src = d.resolve("NestedColl.kf");
        Files.writeString(src, """
                record Point(Int x, Int y)
                main() {
                    println(json.encode(listOf(listOf(Point(1, 2)))))
                }
                """);
        CompilationResult jr = driver.compile(src, d.resolve("out-jvm"), Target.JVM);
        assertTrue(jr.diagnostics().getDiagnostics().isEmpty(), "JVM compila: " + diags(jr));
        CompilationResult nr = driver.compile(src, d.resolve("out-nat"), Target.NATIVE);
        String dd = diags(nr);
        assertTrue(dd.contains("JSN002"),
                "elemento-coleção aninhado no nativo: esperado JSN002 honesto, obtive: " + dd);
    }
}
