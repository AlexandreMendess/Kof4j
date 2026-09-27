package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §485 (face de referência) — um bridge de erasure covariante cujo slot do pai
 * já tem os MESMOS parâmetros apagados e retorno referência (ex.:
 * {@code Box<String>.get()} implementado por {@code get(): String}) era emitido
 * como um segundo símbolo asm idêntico ({@code SBox_get}) no Native, porque
 * {@code NativeSymbolMangling.sigTag} só codifica os TIPOS DE PARÂMETRO. O
 * {@code as} falhava com "symbol already defined" (JVM/Script/JS verdes → rule 5).
 * O bridge é um pass-through de registrador desnecessário no Native; a vtable
 * aponta direto para o método concreto. O oráculo é o JVM, rodado no teste.
 *
 * <p>A face de retorno PRIMITIVO ({@code Holder<Int>.get() → Int}) era a face
 * aberta (§485 face (b)): o bridge faz o box e é realmente necessário, e o
 * símbolo colidia pelo mesmo motivo. O fix de mangling ciente do retorno do
 * §613/#608 (bridge ganha sufixo do retorno apagado) resolve os dois lados —
 * esta classe agora prova as DUAS faces.
 */
class NativeGenericIfaceBridgeE2ETest {

    private static final String PROGRAM = """
            interface Box<T> {
                get(): T
                default describe(): String { return "Box: " + get() }
            }
            class SBox implements Box<String> {
                String v
                constructor(s: String) { v = s }
                get(): String { return v }
            }
            interface Holder<T> { get(): T }
            class IntHolder implements Holder<Int> {
                get(): Int { return 42 }
            }
            interface Converter<A, B> { convert(input: A): B }
            class IntToString implements Converter<Int, String> {
                convert(input: Int): String { return input.toString() }
            }
            main() {
                var b: Box<String> = SBox("hi")
                println(b.get())
                println(b.describe())
                var s = SBox("direct")
                println(s.get())
                var h: Holder<Int> = IntHolder()
                println(h.get())
                val cv: Converter<Int, String> = IntToString()
                println(cv.convert(99))
            }
            """;

    private static final String EXPECTED = "hi\nBox: hi\ndirect\n42\n99";

    private static CompilationResult compile(Path tmp, String name, Target t) throws IOException {
        Path file = tmp.resolve("Main-" + name + ".kf");
        Files.writeString(file, PROGRAM);
        return new CompilerDriver().compile(file, tmp.resolve("out-" + name + "-" + t.name()), t);
    }

    private static String runJvm(Path tmp, Path outDir) throws IOException {
        try {
            Path runnerDir = Files.createDirectory(tmp.resolve("runner-" + System.nanoTime()));
            Path runnerSrc = runnerDir.resolve("Run.java");
            Files.writeString(runnerSrc, """
                    public class Run {
                        public static void main(String[] args) throws Exception {
                            Class.forName(args[0]).getMethod("main", String[].class)
                                .invoke(null, (Object) new String[0]);
                        }
                    }
                    """);
            Process pCompile = new ProcessBuilder(TestJdk.javacBin(), "-d", runnerDir.toString(),
                    runnerSrc.toString()).start();
            assertEquals(0, pCompile.waitFor(), "runner javac");
            Process p = new ProcessBuilder(TestJdk.javaBin(),
                    "-cp", outDir.toString() + java.io.File.pathSeparator + runnerDir.toString(), "Run", "Default.Main")
                    .redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            assertEquals(0, p.waitFor(), "JVM oracle exit, output: " + output);
            return output;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }

    private static String runNative(Path tmp, Target t, String label) throws IOException {
        CompilationResult result = compile(tmp, label, t);
        assertTrue(result.success(), t + " compile should succeed: " + result.diagnostics().getDiagnostics());
        Path bin = tmp.resolve("out-" + label + "-" + t.name()).resolve("Default/Main");
        assertTrue(Files.exists(bin), t + " binary should exist");
        ProcessBuilder pb = new ProcessBuilder(bin.toString());
        if (t == Target.NATIVE_RISCV64) {
            pb = NativeRiscv64E2ETest.qemu("riscv64", bin);
        } else if (t == Target.NATIVE_AARCH64) {
            pb = NativeRiscv64E2ETest.qemu("aarch64", bin);
        }
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        int ec;
        try {
            ec = p.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
        assertEquals(0, ec, t + " exit code should be 0, output: '" + output + "'");
        return output;
    }

    private static String jvmOracle(Path tmp) throws IOException {
        Path outDir = tmp.resolve("out-jvm-" + Target.JVM.name());
        CompilationResult r = compile(tmp, "jvm", Target.JVM);
        assertTrue(r.success(), "JVM oracle compile should succeed: " + r.diagnostics().getDiagnostics());
        return runJvm(tmp, outDir);
    }

    @Test
    void refReturnBridgeMatchesJvmOnNativeX86(@TempDir Path tmp) throws IOException {
        assertEquals(EXPECTED, jvmOracle(tmp), "JVM oracle golden drift");
        assertEquals(EXPECTED, runNative(tmp, Target.NATIVE, "x86"),
                "Native x86 must match the JVM oracle");
    }

    @Test
    void refReturnBridgeMatchesJvmOnRiscv64(@TempDir Path tmp) throws IOException {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                NativeRiscv64E2ETest.hasToolchain("riscv64"),
                "toolchain riscv64/qemu ausente — pulando (NATIVE002)");
        assertEquals(EXPECTED, jvmOracle(tmp), "JVM oracle golden drift");
        assertEquals(EXPECTED, runNative(tmp, Target.NATIVE_RISCV64, "riscv"),
                "riscv64 must match the JVM oracle");
    }

    @Test
    void refReturnBridgeMatchesJvmOnAarch64(@TempDir Path tmp) throws IOException {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                NativeRiscv64E2ETest.hasToolchain("aarch64"),
                "toolchain aarch64/qemu ausente — pulando (NATIVE002)");
        assertEquals(EXPECTED, jvmOracle(tmp), "JVM oracle golden drift");
        assertEquals(EXPECTED, runNative(tmp, Target.NATIVE_AARCH64, "aarch"),
                "aarch64 must match the JVM oracle");
    }
}
