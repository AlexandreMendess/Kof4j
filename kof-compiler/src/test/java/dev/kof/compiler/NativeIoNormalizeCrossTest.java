package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D-FULL-PARITY-050 (row 13) — {@code Path.normalize} no cross riscv64/aarch64
 * (fatia {@link dev.kof.compiler.nat.NativeRiscvAsmIoNormalize}). Sem IO de disco;
 * oraculo JVM medido no mesmo programa.
 * Q3: cobre "." / ".." / separador duplo / barra final / raiz "/" / resultado
 * vazio relativo ("").
 */
class NativeIoNormalizeCrossTest {

    private final CompilerDriver driver = new CompilerDriver();

    private static boolean has(String... cmds) {
        for (String c : cmds) {
            try {
                Process p = new ProcessBuilder("sh", "-c", "command -v " + c)
                        .redirectErrorStream(true).start();
                if (p.waitFor() != 0) return false;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    private static String capture(Process p) throws IOException, InterruptedException {
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "exit code, output: " + out);
        return out;
    }

    private static String runJvm(CompilerDriver driver, Path src, Path outDir) throws IOException {
        CompilationResult result = driver.compile(src, outDir, Target.JVM);
        assertTrue(result.success(), "jvm compile: " + result.diagnostics().getDiagnostics());
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-Dfile.encoding=UTF-8",
                    "-Dstdout.encoding=UTF-8", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            return capture(pb.start());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }

    private static String runCross(CompilerDriver driver, Path src, Path outDir,
                                   String qemu, Target target) throws IOException {
        CompilationResult result = driver.compile(src, outDir, target);
        assertTrue(result.success(), "cross compile: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        assertTrue(Files.exists(bin), "binary should exist");
        ProcessBuilder pb = NativeRiscv64E2ETest.qemu(qemu.substring(5), bin);
        pb.redirectErrorStream(true);
        try {
            return capture(pb.start());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }

    private static final String PROGRAM = """
            main() {
                println(Path("/a/b/../c").normalize())
                println(Path("a/./b").normalize())
                println(Path("a/b/../../c").normalize())
                println(Path("//a//b").normalize())
                println(Path("/..").normalize())
                println(Path("a/..").normalize())
                println(Path("x/").normalize())
            }
            """;

    private static final String EXPECTED = "/a/c\na/b\nc\n/a/b\n/\n.\nx";

    private static Path writeProgram(Path dir) throws IOException {
        Path src = dir.resolve("Main.kf");
        Files.writeString(src, PROGRAM);
        return src;
    }

    @Test
    void jvmOracle(@TempDir Path tempDir) throws IOException {
        assertEquals(EXPECTED, runJvm(driver, writeProgram(tempDir), tempDir.resolve("jvm-out")));
    }

    @Test
    void riscv64NormalizeMatchesJvmOracle(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross toolchain riscv64 + qemu ausente — pulando (NATIVE002)");
        String oracle = runJvm(driver, writeProgram(tempDir), tempDir.resolve("jvm-out"));
        assertEquals(oracle, runCross(driver, writeProgram(tempDir), tempDir.resolve("out-riscv"),
                "qemu-riscv64", Target.NATIVE_RISCV64), "riscv64 normalize != JVM oracle");
    }

    @Test
    void aarch64NormalizeMatchesJvmOracle(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain aarch64 + qemu ausente — pulando (NATIVE002)");
        String oracle = runJvm(driver, writeProgram(tempDir), tempDir.resolve("jvm-out"));
        assertEquals(oracle, runCross(driver, writeProgram(tempDir), tempDir.resolve("out-aarch"),
                "qemu-aarch64", Target.NATIVE_AARCH64), "aarch64 normalize != JVM oracle");
    }
}
