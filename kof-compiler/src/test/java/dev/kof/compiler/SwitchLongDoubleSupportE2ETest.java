package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #473/#474 — switch sobre Long/Double/Float: o "EQ não-SUB" de 2238bd0a
 * cobriu a face pattern/expression e deixou o ramo NUMÉRICO do STATEMENT
 * com {@code SUB}+{@code if_icmpeq} → {@code VerifyError: Type long_2nd not
 * assignable to integer} no JVM (medido; o launcher JavaFX mascarava — regra
 * do launcher reflexivo) e {@code IllegalStateException: unexpected op} no
 * parser JS. A face b (esta lane) troca o ramo largo/FP do stmt pelo mesmo
 * {@code KofBinary(EQ, tipo)} da face pattern (LCMP/DCMP+IFEQ no backend;
 * NaN nunca casa, como o switch do Java) e ensina o {@code JsSwitchParser} a
 * pular o EQ numérico (=== do JS casa a mesma semântica). Golden = saída
 * MEDIDA no JVM/Script/JS/Native, não chute.
 */
class SwitchLongDoubleSupportE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String SOURCE = """
            main() {
                val big: Long = 4000000000
                var hits = 0
                switch (big) {
                    case 4000000000L: hits += 1
                    default: hits += 100
                }
                println(hits)
                val miss = 7L
                println(switch (miss) {
                    case 4000000000L -> "far"
                    case 7L -> "seven"
                    default -> "none"
                })
                val neg = -0.5
                println(switch (neg) {
                    case -0.5 -> "neg-hit"
                    default -> "neg-miss"
                })
            }
            """;

    private static final String EXPECTED = "1\nseven\nneg-hit";

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

    @Test
    void switchOnLongAndDoubleWorksOnJvmStmtAndExprForms(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("sl.kf");
        Files.writeString(src, SOURCE);
        Path out = tempDir.resolve("sl-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "compilar: " + r.diagnostics().getDiagnostics());
        assertEquals(EXPECTED, runJvm(out), "switch Long/Double stmt+expr (#473/#474)");
    }

    @Test
    void intLiteralCasesWidenToLongSubject(@TempDir Path tempDir) throws IOException {
        // #473 face b: caso com literal Int contra subject Long (o teste
        // original só usava literais com sufixo L). Sem o widening, o
        // KofBinary(EQ, long) emitia LCMP sobre [long, int] e o COMPUTE_FRAMES
        // do ASM estourava (NegativeArraySizeException).
        String source = """
                main() {
                    var x: Long = 3
                    switch (x) {
                        case 1: println("one"); break
                        case 3: println("three"); break
                        default: println("other")
                    }
                    val d: Double = 2.5
                    println(switch (d) {
                        case 1 -> "one"
                        case 2 -> "two"
                        default -> "other"
                    })
                }
                """;
        Path src = tempDir.resolve("slw.kf");
        Files.writeString(src, source);
        Path out = tempDir.resolve("slw-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "compilar: " + r.diagnostics().getDiagnostics());
        assertEquals("three\nother", runJvm(out), "literal Int promovido ao subject largo");
    }

    @Test
    void intLiteralCasesWidenCrossTargetParity(@TempDir Path tempDir) throws IOException, InterruptedException {
        String source = """
                main() {
                    var x: Long = 3
                    switch (x) {
                        case 1: println("one"); break
                        case 3: println("three"); break
                        default: println("other")
                    }
                    val d: Double = 2.5
                    println(switch (d) {
                        case 1 -> "one"
                        case 2 -> "two"
                        default -> "other"
                    })
                }
                """;
        Path src = tempDir.resolve("slwx.kf");
        Files.writeString(src, source);
        Path jvmOut = tempDir.resolve("slwx-jvm");
        assertTrue(driver.compile(src, jvmOut, Target.JVM).success(), "JVM compile");
        assertEquals("three\nother", runJvm(jvmOut), "JVM golden");
        KofInterpreter.Result i = driver.interpret(java.util.List.of(src), src.getParent(), new String[0]);
        assertEquals(0, i.exitCode(), "Script exit: " + i.stdout() + " " + i.stderr());
        assertEquals("three\nother", i.stdout().trim(), "Script");
        Path jsOut = tempDir.resolve("slwx-js");
        assertTrue(driver.compile(src, jsOut, Target.JS).success(), "JS compile");
        ProcessBuilder jb = new ProcessBuilder(TestJdk.onPath("node"), jsOut.resolve("Default.mjs").toString());
        jb.redirectErrorStream(true);
        Process jp = jb.start();
        String jout = new String(jp.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
            .replace("\r\n", "\n").trim();
        assertEquals(0, jp.waitFor(), "JS exit, output: " + jout);
        assertEquals("three\nother", jout, "JS");
        Path natOut = tempDir.resolve("slwx-native");
        CompilationResult nr = driver.compile(src, natOut, Target.NATIVE);
        assertTrue(nr.success(), "native compile: " + nr.diagnostics().getDiagnostics());
        Path bin = natOut.resolve("Default/Main");
        assertTrue(Files.exists(bin), "ELF produzido");
        ProcessBuilder nb = new ProcessBuilder(bin.toString());
        nb.redirectErrorStream(true);
        Process np = nb.start();
        String nout = new String(np.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
            .replace("\r\n", "\n").trim();
        assertEquals(0, np.waitFor(), "native exit, output: " + nout);
        assertEquals("three\nother", nout, "Native = mesmo golden");
    }

    @Test
    void switchOnLongAndDoubleSameOnScriptAndJs(@TempDir Path tempDir) throws IOException, InterruptedException {
        Path src = tempDir.resolve("sl2.kf");
        Files.writeString(src, SOURCE);
        KofInterpreter.Result i = driver.interpret(java.util.List.of(src), src.getParent(), new String[0]);
        assertEquals(0, i.exitCode(), "Script exit/stderr: " + i.stdout() + " " + i.stderr());
        assertEquals(EXPECTED, i.stdout().trim(), "Script");
        Path jsOut = tempDir.resolve("sl2-js");
        CompilationResult js = driver.compile(src, jsOut, Target.JS);
        assertTrue(js.success(), "JS compile: " + js.diagnostics().getDiagnostics());
        ProcessBuilder jb = new ProcessBuilder(TestJdk.onPath("node"), jsOut.resolve("Default.mjs").toString());
        jb.redirectErrorStream(true);
        Process jp = jb.start();
        String jout = new String(jp.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
            .replace("\r\n", "\n").trim();
        assertEquals(0, jp.waitFor(), "JS exit, output: " + jout);
        assertEquals(EXPECTED, jout, "JS (=== casa NaN-nunca/-0.0 igual Java)");
    }

    @Test
    void switchOnLongAndDoubleNativeMatchesJvm(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("sl3.kf");
        Files.writeString(src, SOURCE);
        Path out = tempDir.resolve("sl3-native");
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        assertTrue(r.success(), "native compile: " + r.diagnostics().getDiagnostics());
        Path bin = out.resolve("Default/Main");
        assertTrue(Files.exists(bin), "ELF produzido");
        try {
            ProcessBuilder pb = new ProcessBuilder(bin.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            assertEquals(0, p.waitFor(), "native exit, output: " + output);
            assertEquals(EXPECTED, output, "Native = mesmo golden do JVM");
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
    }
}
