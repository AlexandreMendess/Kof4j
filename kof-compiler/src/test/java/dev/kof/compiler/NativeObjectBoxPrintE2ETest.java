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
 * N2 / §205 slice 2 (tagged-box ABI, D-NULL-INTENT) — impressao de um valor
 * tipado {@code Object} que NAO chega pela sintaxe direta do if/switch:
 * {@code <prim> as Object}, {@code <record> as Object}, objeto guardado em
 * local {@code Object}. O oracle e o JVM, rodado no proprio teste.
 */
class NativeObjectBoxPrintE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String PROGRAM = """
            record Point(Int x, Int y)
            void direct(Bool pick) {
                var x = if (pick) 1 else "s"
                println(x)
            }
            void viaVar() {
                var o: Object = Point(3, 4)
                println(o)
            }
            main() {
                direct(true)
                direct(false)
                viaVar()
                println(7 as Object)
                println(1.5 as Object)
                println(true as Object)
                println(1234567890123 as Object)
                println(Point(1, 2) as Object)
                println("hi" as Object)
            }
            """;

    private static String run(Path tempDir, Target target, String program) throws IOException {
        CompilerDriver driver = new CompilerDriver();
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, program);
        Path outDir = tempDir.resolve("out-" + target.name());
        CompilationResult result = driver.compile(source, outDir, target);
        assertTrue(result.success(), target + " compile should succeed: "
                + result.diagnostics().getDiagnostics());
        if (target == Target.JVM) {
            return runJvm(tempDir, outDir);
        }
        Path binFile = outDir.resolve("Default/Main");
        assertTrue(Files.exists(binFile), target + " binary should exist");
        ProcessBuilder pb = new ProcessBuilder(binFile.toString());
        if (target == Target.NATIVE_RISCV64) {
            pb = NativeRiscv64E2ETest.qemu("riscv64", binFile);
        } else if (target == Target.NATIVE_AARCH64) {
            pb = NativeRiscv64E2ETest.qemu("aarch64", binFile);
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
            throw new IOException("Interrupted", e);
        }
        assertEquals(0, ec, target + " exit code should be 0, output: '" + output + "'");
        return output;
    }

    private static String runJvm(Path tempDir, Path outDir) throws IOException {
        try {
            Path runnerDir = Files.createDirectory(tempDir.resolve("runner"));
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
            int ec = p.waitFor();
            assertEquals(0, ec, "JVM exit code " + ec + ", output: " + output);
            return output;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }

    @Test
    void objectPrintMatchesJvmOnNativeX86(@TempDir Path tempDir) throws IOException {
        Path jvmDir = Files.createDirectory(tempDir.resolve("jvmdir"));
        String oracle = run(jvmDir, Target.JVM, PROGRAM);
        Path nativeDir = Files.createDirectory(tempDir.resolve("natdir"));
        String native1 = run(nativeDir, Target.NATIVE, PROGRAM);
        assertEquals(oracle, native1, "Native Object-typed print must match the JVM oracle");
    }

    @Test
    void objectPrintMatchesJvmOnRiscv64(@TempDir Path tempDir) throws IOException {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                NativeRiscv64E2ETest.hasToolchain("riscv64"),
                "toolchain riscv64/qemu ausente — pulando (NATIVE002)");
        Path jvmDir = Files.createDirectory(tempDir.resolve("jvmdir"));
        String oracle = run(jvmDir, Target.JVM, PROGRAM);
        Path nativeDir = Files.createDirectory(tempDir.resolve("natdir"));
        String native1 = run(nativeDir, Target.NATIVE_RISCV64, PROGRAM);
        assertEquals(oracle, native1, "riscv64 Object-typed print must match the JVM oracle");
    }

    @Test
    void objectPrintMatchesJvmOnAarch64(@TempDir Path tempDir) throws IOException {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                NativeRiscv64E2ETest.hasToolchain("aarch64"),
                "toolchain aarch64/qemu ausente — pulando (NATIVE002)");
        Path jvmDir = Files.createDirectory(tempDir.resolve("jvmdir"));
        String oracle = run(jvmDir, Target.JVM, PROGRAM);
        Path nativeDir = Files.createDirectory(tempDir.resolve("natdir"));
        String native1 = run(nativeDir, Target.NATIVE_AARCH64, PROGRAM);
        assertEquals(oracle, native1, "aarch64 Object-typed print must match the JVM oracle");
    }
}
