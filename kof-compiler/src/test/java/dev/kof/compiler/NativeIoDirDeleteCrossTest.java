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
 * D-FULL-PARITY-050 (row 13) — {@code Directory.delete()} RECURSIVO no cross
 * riscv64/aarch64 (fatia {@link dev.kof.compiler.nat.NativeRiscvAsmIoDirDelete}).
 * Arvore nao-vazia (dir + arquivo + subdir/arquivo) deve ser removida por
 * inteiro, como o contrato JVM (oraculo medido no mesmo programa). Path
 * ABSOLUTO injetado (independe do cwd).
 * Q3: cobre dir nao-vazio recursivo (sucesso), pos-condicao exists=false e
 * dir inexistente -> false.
 */
class NativeIoDirDeleteCrossTest {

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

    private static final String EXPECTED = "true\nfalse\nfalse";

    private static Path writeProgram(Path dir, String tag) throws IOException {
        Path probe = dir.resolve("probe-" + tag).toAbsolutePath();
        String p = probe.toString();
        String program = """
                main() {
                    Directory("%1$s").create()
                    File("%1$s/f.txt").writeText("x")
                    Directory("%1$s/sub").create()
                    File("%1$s/sub/g.txt").writeText("y")
                    println(Directory("%1$s").delete())
                    println(File("%1$s").exists())
                    println(Directory("%1$s/none").delete())
                }
                """.formatted(p);
        Path src = dir.resolve("Main-" + tag + ".kf");
        Files.writeString(src, program);
        return src;
    }

    @Test
    void jvmOracle(@TempDir Path tempDir) throws IOException {
        assertEquals(EXPECTED, runJvm(driver, writeProgram(tempDir, "jvm"), tempDir.resolve("jvm-out")));
    }

    @Test
    void riscv64DirDeleteMatchesJvmOracle(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross toolchain riscv64 + qemu ausente — pulando (NATIVE002)");
        String oracle = runJvm(driver, writeProgram(tempDir, "o1"), tempDir.resolve("jvm-out1"));
        assertEquals(oracle, runCross(driver, writeProgram(tempDir, "r1"), tempDir.resolve("out-riscv"),
                "qemu-riscv64", Target.NATIVE_RISCV64), "riscv64 dir_delete != JVM oracle");
        assertFalse(Files.exists(tempDir.resolve("probe-r1")), "tree must be gone (riscv64)");
    }

    @Test
    void aarch64DirDeleteMatchesJvmOracle(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain aarch64 + qemu ausente — pulando (NATIVE002)");
        String oracle = runJvm(driver, writeProgram(tempDir, "o2"), tempDir.resolve("jvm-out2"));
        assertEquals(oracle, runCross(driver, writeProgram(tempDir, "a2"), tempDir.resolve("out-aarch"),
                "qemu-aarch64", Target.NATIVE_AARCH64), "aarch64 dir_delete != JVM oracle");
        assertFalse(Files.exists(tempDir.resolve("probe-a2")), "tree must be gone (aarch64)");
    }
}
