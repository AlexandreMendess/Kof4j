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
 * known-bugs §382 — as faces BOOL do kof.io no host JS devolviam o NUMERO
 * 0/-1 do try/catch (escreveu=true→0→FALSY: sucesso virava `false` no guest,
 * com o arquivo no disco e rc=0 — a classe silenciosa §255). O fix é no
 * KofJsRunner: as 6 faces tipadas BOOL pelo typer (`KofIo`) devolvem o
 * booleano REAL (delete/dirDelete propagam o `deleteIfExists` — miss é
 * false de verdade, como JVM/Script medidos). Golden = o programa medido
 * 20/09 motor a motor (oracle JVM; JS via KofJsRunner, o mesmo host dos
 * goldens makealive). As faces NUMERICAS (size, readText, exitCode, format,
 * db.execute = INT por contrato) NÃO pertencem a esta família e ficam
 * intactas. Bytes vão por `new Int[n]` — o caminho `listOf(...)` em
 * writeBytes é o §387 (outra lane, não tocar aqui).
 */
class IoBoolFacesE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private record Run(boolean ok, String output) {}

    private static final String PROGRAM = """
            main() {
                val f = File("__BASE__/probe.txt")
                println(f.writeText("oi"))
                println(f.appendText("!"))
                println(f.readText())
                println(f.delete())
                println(f.delete())
                val d = Directory("__BASE__/deep/dir")
                println(d.createDirectories())
                println(d.isDirectory())
                println(f.writeText("x"))
                println(f.delete())
                val g = File("__BASE__/b.bin")
                val arr = new Int[2]
                arr[0] = 65
                arr[1] = 66
                println(g.writeBytes(arr))
                val add = new Int[1]
                add[0] = 67
                println(g.appendBytes(add))
                println(g.size())
                println(g.delete())
            }
            """;

    // Oracle = medido 25/09 no tip com o fix (JVM == JS byte a byte):
    //   writeText true | appendText true | readText "oi!" | delete true |
    //   delete-miss false | createDirectories true | isDirectory true
    //   (mesma face nos 2 motores — contrato compartilhado, não §382) |
    //   writeText pós-delete true | delete true | writeBytes true |
    //   appendBytes true | size 3 | delete true.
    // #617 (24/09): a linha antiga usava `File.dirCreateDirs()` — método que
    // NÃO existe no kof.io (o typer aceitava em silêncio → no-op; o
    // `isDirectory()` seguinte dava `false`). A face real é
    // `Directory(p).createDirectories()` (training/language/io.md), que devolve
    // Bool e cria de verdade → o `isDirectory()` passa a `true`.
    private static final String EXPECTED =
            "true\ntrue\noi!\ntrue\nfalse\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\n3\ntrue\n";

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
        int rc = dev.kof.runtime.KofJsRunner.run(
                out.resolve("Default.mjs"), buf,
                new ByteArrayInputStream(new byte[0]), buf);
        return new Run(rc == 0, buf.toString());
    }

    private static String diags(CompilationResult r) {
        return String.valueOf(r.diagnostics().getDiagnostics());
    }

    @Test
    void boolFacesMatchJvmOracleOnJsHost() throws Exception {
        Files.createDirectories(tmp.resolve("w-jvm"));
        Files.createDirectories(tmp.resolve("w-js"));
        Run jvm = runJvm(tmp.resolve("jvm.kf"), tmp.resolve("o-jvm"),
                tmp.resolve("w-jvm").toString());
        assertTrue(jvm.ok(), "JVM compile/run failed: " + jvm.output());
        assertEquals(EXPECTED, jvm.output(), "JVM oracle drifted (golden = measured 20/09)");
        Run js = runJs(tmp.resolve("js.kf"), tmp.resolve("o-js"),
                tmp.resolve("w-js").toString());
        assertTrue(js.ok(), "JS run failed rc/out: " + js.output());
        assertEquals(EXPECTED, js.output(),
                "§382: JS host devolve as faces BOOL como 0/-1 — sucesso vira false "
                        + "silencioso (arquivo no disco, rc=0); o fix é o KofJsRunner "
                        + "retornar true/false reais nas 6 faces BOOL");
    }
}
