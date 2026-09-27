package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §388-B — como um array primitivo INTEIRO se imprime (regra 6, voto da
 * mantenedora 21/09: formato de container). O contrato da casa já existia
 * para List/Map/Set (§107: oracle = ArrayList.toString, JS espelha com
 * kofFormat, nativo com kof_{list,map,set}_to_string); a célula do array
 * cru estava NAO DECLARADA — JVM/Script davam a identidade Java `[I@hash`
 * e JS dava `65,66` sem colchetes. Agora: `println(new Int[n])` e
 * `println(readBytes())` imprimem `[65, 66]` nos três, aninhado recursa
 * (`[[65, 66]]`), elemento nulo é `null`. Native fica com a célula
 * excluída da matriz (sem kof_array_to_string em asm — mesmo precedente
 * §104b/§107 de recusa documentada, residual registrado no §388).
 */
class ArrayPrintFormatE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private record RunResult(int exit, String out) {}

    private Path write(Path tmp, String name, String src) throws IOException {
        Path f = tmp.resolve(name + "-" + System.nanoTime() + ".kf");
        Files.writeString(f, src);
        return f;
    }

    private static final String FLAT = """
            main() {
                val b = new Int[2]
                b[0] = 65
                b[1] = 66
                val f = File("apf-flat.bin")
                println(f.writeBytes(b))
                println(f.readBytes())
            }
            """;

    private static final String NESTED = """
            main() {
                val n = new Int[1][2]
                n[0][0] = 65
                n[0][1] = 66
                println(n)
            }
            """;

    private RunResult runJvm(Path tmp, String name, String src) throws IOException {
        Path outDir = tmp.resolve("out-" + name + "-" + System.nanoTime());
        CompilationResult r = driver.compile(write(tmp, name, src), outDir, Target.JVM);
        assertTrue(r.success(), name + " JVM compile: " + r.diagnostics().getDiagnostics());
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
            pb.directory(tmp.toFile());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
            return new RunResult(p.waitFor(), output);
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    private RunResult runJs(Path tmp, String name, String src) throws IOException {
        Path outDir = tmp.resolve("outjs-" + name + "-" + System.nanoTime());
        CompilationResult r = driver.compile(write(tmp, name, src), outDir, Target.JS);
        assertTrue(r.success(), name + " JS compile: " + r.diagnostics().getDiagnostics());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(outDir.resolve("Default.mjs"), out,
                new ByteArrayInputStream(new byte[0]), out);
        return new RunResult(ec, out.toString().trim());
    }

    @Test
    void flatByteArrayIsContainerFormatOnJvm(@TempDir Path tmp) throws IOException {
        RunResult rr = runJvm(tmp, "flat", FLAT);
        assertEquals(0, rr.exit(), "run: " + rr.out());
        assertEquals("true\n[65, 66]", rr.out(),
                "identidade [I@… vazando? (§388-B voto = formato de container)");
    }

    @Test
    void nestedIntArrayRecursesOnJvm(@TempDir Path tmp) throws IOException {
        RunResult rr = runJvm(tmp, "nested", NESTED);
        assertEquals(0, rr.exit(), "run: " + rr.out());
        assertEquals("[[65, 66]]", rr.out());
    }

    @Test
    void flatByteArrayIsContainerFormatOnScript(@TempDir Path tmp) throws IOException {
        KofInterpreter.Result ir = driver.interpret(java.util.List.of(write(tmp, "scr", FLAT)),
                tmp, new String[0]);
        assertEquals(0, ir.exitCode(), "script stderr: " + ir.stderr());
        assertEquals("true\n[65, 66]", ir.stdout().trim(),
                "o interpretador espelha o oracle JVM");
    }

    @Test
    void flatByteArrayIsContainerFormatOnJs(@TempDir Path tmp) throws IOException {
        RunResult rr = runJs(tmp, "flat", """
                main() {
                    val b = new Int[2]
                    b[0] = 65
                    b[1] = 66
                    println(b)
                }
                """);
        assertEquals(0, rr.exit(), "run: " + rr.out());
        assertEquals("[65, 66]", rr.out(),
                "JS sem colchetes era a paridade reversa medida do §388-B");
    }

    @Test
    void listPrintUnchangedAsOracle(@TempDir Path tmp) throws IOException {
        RunResult rr = runJvm(tmp, "list", """
                main() {
                    println(listOf(65, 66))
                }
                """);
        assertEquals(0, rr.exit(), "run: " + rr.out());
        assertEquals("[65, 66]", rr.out(), "o oracle §107 nao pode mudar");
    }

    @Test
    void miscArrayKindsFollowContainerOnJvm(@TempDir Path tmp) throws IOException {
        RunResult rr = runJvm(tmp, "kinds", """
                main() {
                    val e = new Int[0]
                    println(e)
                    val t = new Bool[2]
                    t[0] = true
                    t[1] = false
                    println(t)
                    val s = new String[1]
                    s[0] = "x"
                    println(s)
                    val l = new Long[2]
                    l[0] = 100000000000L
                    l[1] = 2
                    println(l)
                }
                """);
        assertEquals(0, rr.exit(), "run: " + rr.out());
        assertEquals("[]\n[true, false]\n[x]\n[100000000000, 2]", rr.out());
    }

    @Test
    void flatByteArrayIsContainerFormatOnNative(@TempDir Path tmp) throws IOException {
        Path src = write(tmp, "nat", """
                main() {
                    val b = new Int[2]
                    b[0] = 65
                    b[1] = 66
                    println(b)
                }
                """);
        Path outDir = tmp.resolve("outnat-" + System.nanoTime());
        CompilationResult r = driver.compile(src, outDir, Target.NATIVE);
        assertTrue(r.success(), "native compile: " + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(outDir.resolve("Default/Main").toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec;
        try {
            ec = p.waitFor();
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
        assertEquals(0, ec, "exit " + ec + ", output: '" + out + "'");
        assertEquals("[65, 66]", out, "célula §388-B nativa (mesma face medida do §107)");
    }
}
