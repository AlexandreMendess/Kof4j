package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #467 (PR #479 + face b da revisora) — {@code String += <qualquer>} era
 * barrado por SEM012 no analisador embora {@code s = s + 5} compilasse e o
 * lowerer já stringificasse. O gate foi liberado pelo PR #479, mas o path de
 * LOCAL/ARRAY empurrava o RHS cru na pilha sob {@code valueOf(Object)} →
 * VerifyError (R6: troca SEM012 por crash silencioso). Face b (esta lane):
 * a concatenação composta passa a usar o helper canônico do {@code +}
 * ({@link ExpressionBinaryLowerer#emitOperandToString}) nos 4 alvos —
 * D-PRINT #168 (Char vira caráter "c", nunca code point), D-NULL-INTENT #278
 * (Nullable sem rebox), §264 (JS float no contrato do JDK).
 *
 * <p>Oracle medido no tip pré-fix: "z99" (char) / VerifyError (local int);
 * pós-fix: idêntico a {@code s = s + ...} em JVM/JS/Script/Native.
 */
class StringCompoundAssignE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path outDir) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder(TestJdk.javaBin(),
                    "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            assertEquals(0, p.waitFor(), "JVM exit code, output: " + output);
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
    }

    private static final String SOURCE = """
            class Box {
                String t = "b"
                bump() { t += 9 }
            }
            main() {
                var s = "v"
                s += 7
                s += true
                s += 'c'
                s += 1.25
                var l: Long = 8
                s += l
                var bx = Box()
                bx.bump()
                var cap = "c"
                val f = () -> { cap += 1 }
                f()
                var arr = listOf("a")
                arr.set(0, arr.get(0) + 5)
                println(s)
                println(bx.t)
                println(cap)
                println(arr.get(0))
            }
            """;

    private static final String EXPECTED = "v7truec1.258\nb9\nc1\na5";

    @Test
    void stringCompoundAssignMatchesConcatOnJvm(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("ca.kf");
        Files.writeString(src, SOURCE);
        Path out = tempDir.resolve("ca-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "compilar sem SEM012: " + r.diagnostics().getDiagnostics());
        assertEquals(EXPECTED, runJvm(out), "String += <any> == String + <any> (#467)");
    }

    @Test
    void stringCompoundAssignSameOnScriptAndJs(@TempDir Path tempDir) throws IOException, InterruptedException {
        Path src = tempDir.resolve("cap.kf");
        Files.writeString(src, SOURCE);
        KofInterpreter.Result i = driver.interpret(java.util.List.of(src), src.getParent(), new String[0]);
        assertEquals(0, i.exitCode(), "Script exit/stderr: " + i.stdout() + " " + i.stderr());
        assertEquals(EXPECTED, i.stdout().trim(), "Script");
        Path jsOut = tempDir.resolve("cap-js");
        CompilationResult js = driver.compile(src, jsOut, Target.JS);
        assertTrue(js.success(), "JS compile: " + js.diagnostics().getDiagnostics());
        ProcessBuilder jb = new ProcessBuilder(TestJdk.onPath("node"), jsOut.resolve("Default.mjs").toString());
        jb.redirectErrorStream(true);
        Process jp = jb.start();
        String jout = new String(jp.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
            .replace("\r\n", "\n").trim();
        assertEquals(0, jp.waitFor(), "JS exit, output: " + jout);
        assertEquals(EXPECTED, jout, "JS");
    }

    @Test
    void charRhsStringifiesAsCaracterNotCodepoint(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("ch.kf");
        Files.writeString(src, """
                main() {
                    var u = "z"
                    u += 'c'
                    println(u)
                }
                """);
        Path out = tempDir.resolve("ch-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "compilar: " + r.diagnostics().getDiagnostics());
        assertEquals("zc", runJvm(out), "D-PRINT #168: Char no += vira 'c', nunca \"99\"");
    }

    @Test
    void nonStringCompoundArithmeticUnaffected(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("num.kf");
        Files.writeString(src, """
                main() {
                    var n = 10
                    n += 5
                    var d = 1.5
                    d -= 0.25
                    println(n)
                    println(d)
                }
                """);
        Path out = tempDir.resolve("num-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "compilar: " + r.diagnostics().getDiagnostics());
        assertEquals("15\n1.25", runJvm(out), "numérico += continua aritmética (§103/§295)");
    }
}
