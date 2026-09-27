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
 * D-FULL-PARITY-050 (row 3, slice B) — {@code ssh.cmd}/{@code ssh.run} no CROSS
 * riscv64/aarch64 ({@link dev.kof.compiler.nat.NativeRiscvAsmSsh}). O argv é o
 * oráculo JVM exato (host/comando = 1 elemento cada, nunca {@code sh -c}) e
 * {@code ssh.run} reusa {@code kof_process_run} (Result honesto).
 * Golden: a MESMA fonte roda na JVM e nos 2 alvos cross byte-a-byte.
 */
class SshCrossE2ETest {

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
                .replace("\r\n", "\n");
        assertEquals(0, p.waitFor(), "exit code, output: " + out);
        return out;
    }

    private String runJvm(Path src, Path out) throws IOException {
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "jvm compile: " + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder("java", "-Dfile.encoding=UTF-8",
                "-Dstdout.encoding=UTF-8", "-cp", out.toString(), "Default.Main");
        pb.redirectErrorStream(true);
        try {
            return capture(pb.start());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }

    private String runCross(Path src, Path out, String arch, Target target) throws IOException {
        CompilationResult r = driver.compile(src, out, target);
        assertTrue(r.success(), arch + " compile: " + r.diagnostics().getDiagnostics());
        Path bin = out.resolve("Default/Main");
        assertTrue(Files.exists(bin), arch + " binary should exist");
        ProcessBuilder pb = NativeRiscv64E2ETest.qemu(arch, bin);
        pb.redirectErrorStream(true);
        try {
            return capture(pb.start());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }

    private void assertAllTargets(Path tempDir, String tag, String program) throws IOException {
        Path src = tempDir.resolve("Main-" + tag + ".kf");
        Files.writeString(src, program);
        String expected = runJvm(src, tempDir.resolve(tag + "-jvm"));
        Assumptions.assumeTrue(
                has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64")
                        && NativeRiscv64E2ETest.qemuPrefix("riscv64") != null,
                "cross riscv64 toolchain/sysroot ausente — pulando (NATIVE002)");
        assertEquals(expected, runCross(src, tempDir.resolve(tag + "-rv"),
                "riscv64", Target.NATIVE_RISCV64), "riscv64 " + tag);
        Assumptions.assumeTrue(
                has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64")
                        && NativeRiscv64E2ETest.qemuPrefix("aarch64") != null,
                "cross aarch64 toolchain/sysroot ausente — pulando (NATIVE002)");
        assertEquals(expected, runCross(src, tempDir.resolve(tag + "-aa"),
                "aarch64", Target.NATIVE_AARCH64), "aarch64 " + tag);
    }

    /** O argv de `ssh.cmd` é o oráculo JVM exato — 7 elementos, host/comando
     *  inteiros (Q4: um comando com espaços continua UM elemento). */
    @Test
    void cmdArgvMatchesJvmOracle(@TempDir Path tmp) throws IOException {
        assertAllTargets(tmp, "argv", """
            main() {
                var a = ssh.cmd("user@host", "uname -a; rm -rf /")
                println(a.size)
                for (var i in listOf(0, 1, 2, 3, 4, 5, 6)) { println("[" + a.get(i) + "]") }
            }
            """);
    }

    /** `ssh.run` de host inexistente não pode ser crash/pânico: é um Result
     *  honesto (exitCode != 0, stderr preenchido). */
    @Test
    void runUnreachableHostIsHonestResult(@TempDir Path tmp) throws IOException {
        Assumptions.assumeTrue(has("ssh"), "ssh ausente — pulando");
        assertAllTargets(tmp, "honest", """
            main() {
                var r = ssh.run("ssh.invalid", "true")
                println(r.exitCode != 0)
                println(r.stderr != "")
            }
            """);
    }
}
