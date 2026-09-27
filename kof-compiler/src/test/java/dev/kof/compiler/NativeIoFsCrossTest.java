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
 * D-FULL-PARITY-050 (row 13) — mutacoes de FS do {@code kof.io}
 * ({@code delete()}/{@code create()}) no cross riscv64/aarch64 (fatia
 * {@link dev.kof.compiler.nat.NativeRiscvAsmIoFs}). O oraculo e medido no JVM
 * no MESMO programa (caminhos distintos por alvo) e o cross tem de ser
 * byte-identico. createDirectories/bytes/list seguem com gate NAT006 (§427).
 */
class NativeIoFsCrossTest {

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

    // create=1, exists=1, isDirectory=1, create again=0 (EEXIST),
    // writeText=1, delete=1, exists=0, delete missing=0.
    private static final String EXPECTED = "true\ntrue\ntrue\nfalse\ntrue\ntrue\nfalse\nfalse";

    private static String program(Path dir, Path file) {
        return "main() {\n"
                + "    var d = Directory(\"" + dir + "\")\n"
                + "    println(d.create())\n"
                + "    println(File(\"" + dir + "\").exists())\n"
                + "    println(File(\"" + dir + "\").isDirectory())\n"
                + "    println(d.create())\n"
                + "    var f = File(\"" + file + "\")\n"
                + "    println(f.writeText(\"x\"))\n"
                + "    println(f.delete())\n"
                + "    println(f.exists())\n"
                + "    println(f.delete())\n"
                + "}\n";
    }

    @Test
    void jvmOracle(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, program(tempDir.resolve("jvmd"), tempDir.resolve("jvmf.txt")));
        assertEquals(EXPECTED, runJvm(driver, src, tempDir.resolve("jvm-out")));
    }

    @Test
    void riscv64IoFsMatchesJvmOracle(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross toolchain riscv64 + qemu ausente — pulando (NATIVE002)");
        Path srcJvm = tempDir.resolve("MainJ.kf");
        Files.writeString(srcJvm, program(tempDir.resolve("jvmd"), tempDir.resolve("jvmf.txt")));
        String oracle = runJvm(driver, srcJvm, tempDir.resolve("jvm-out"));
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, program(tempDir.resolve("riscvd"), tempDir.resolve("riscvf.txt")));
        assertEquals(oracle, runCross(driver, src, tempDir.resolve("out-riscv"), "qemu-riscv64",
                Target.NATIVE_RISCV64), "riscv64 != JVM oracle (oracle=" + oracle + ")");
    }

    @Test
    void aarch64IoFsMatchesJvmOracle(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain aarch64 + qemu ausente — pulando (NATIVE002)");
        Path srcJvm = tempDir.resolve("MainJ.kf");
        Files.writeString(srcJvm, program(tempDir.resolve("jvmd"), tempDir.resolve("jvmf.txt")));
        String oracle = runJvm(driver, srcJvm, tempDir.resolve("jvm-out"));
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, program(tempDir.resolve("aarchd"), tempDir.resolve("aarchf.txt")));
        assertEquals(oracle, runCross(driver, src, tempDir.resolve("out-aarch"), "qemu-aarch64",
                Target.NATIVE_AARCH64), "aarch64 != JVM oracle (oracle=" + oracle + ")");
    }
}
