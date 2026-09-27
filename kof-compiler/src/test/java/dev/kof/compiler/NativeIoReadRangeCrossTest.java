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
 * D-FULL-PARITY-050 (row 13) — {@code readRange(offset, len)} do {@code kof.io}
 * no cross riscv64/aarch64 (fatia
 * {@link dev.kof.compiler.nat.NativeRiscvAsmIoReadRange}). Escreve 5 bytes e le
 * o range (1,3), exigindo os 3 bytes do meio como Int[] (openat + pread64).
 * Q3: cobre offset != 0 e len parcial (leitura curta deterministica).
 */
class NativeIoReadRangeCrossTest {

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

    private static String program(Path file) {
        return "main() {\n"
                + "    var f = File(\"" + file + "\")\n"
                + "    var b = new Int[5]\n"
                + "    b[0] = 10\n"
                + "    b[1] = 20\n"
                + "    b[2] = 30\n"
                + "    b[3] = 40\n"
                + "    b[4] = 50\n"
                + "    println(f.writeBytes(b))\n"
                + "    var r = f.readRange(1, 3)\n"
                + "    println(r.length)\n"
                + "    println(r[0])\n"
                + "    println(r[1])\n"
                + "    println(r[2])\n"
                + "}\n";
    }

    private static final String EXPECTED = "true\n3\n20\n30\n40";

    @Test
    void jvmOracle(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, program(tempDir.resolve("jvm.bin")));
        assertEquals(EXPECTED, runJvm(driver, src, tempDir.resolve("jvm-out")));
    }

    @Test
    void riscv64ReadRangeMatchesJvmOracle(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross toolchain riscv64 + qemu ausente — pulando (NATIVE002)");
        Path srcJvm = tempDir.resolve("MainJ.kf");
        Files.writeString(srcJvm, program(tempDir.resolve("jvm.bin")));
        String oracle = runJvm(driver, srcJvm, tempDir.resolve("jvm-out"));
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, program(tempDir.resolve("riscv.bin")));
        assertEquals(oracle, runCross(driver, src, tempDir.resolve("out-riscv"), "qemu-riscv64",
                Target.NATIVE_RISCV64), "riscv64 readRange != JVM oracle (oracle=" + oracle + ")");
    }

    @Test
    void aarch64ReadRangeMatchesJvmOracle(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain aarch64 + qemu ausente — pulando (NATIVE002)");
        Path srcJvm = tempDir.resolve("MainJ.kf");
        Files.writeString(srcJvm, program(tempDir.resolve("jvm.bin")));
        String oracle = runJvm(driver, srcJvm, tempDir.resolve("jvm-out"));
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, program(tempDir.resolve("aarch.bin")));
        assertEquals(oracle, runCross(driver, src, tempDir.resolve("out-aarch"), "qemu-aarch64",
                Target.NATIVE_AARCH64), "aarch64 readRange != JVM oracle (oracle=" + oracle + ")");
    }
}
