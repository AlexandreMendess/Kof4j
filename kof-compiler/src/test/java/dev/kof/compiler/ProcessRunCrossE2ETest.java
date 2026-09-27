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
 * D-FULL-PARITY-050 (row 1, slice C) — {@code process.run} no CROSS
 * riscv64/aarch64 ({@link dev.kof.compiler.nat.NativeRiscvAsmProcess}, clone
 * SIGCHLD + execvp + pipes + ppoll + wait4). Golden byte-a-byte contra o
 * oráculo JVM (a mesma fonte roda na JVM e nos 2 alvos cross).
 * Q3: echo/PATH, exit≠0, separação stdout/stderr, stdout grande (drain sem
 * deadlock) e exec-falho (−1, stdout vazio, stderr não-vazio).
 */
class ProcessRunCrossE2ETest {

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

    private static Path write(Path dir, String tag, String program) throws IOException {
        Path src = dir.resolve("Main-" + tag + ".kf");
        Files.writeString(src, program);
        return src;
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

    private void assertAllTargets(Path tempDir, String tag, String program, String expected)
            throws IOException {
        Path src = write(tempDir, tag, program);
        assertEquals(expected, runJvm(src, tempDir.resolve(tag + "-jvm")), "JVM oracle " + tag);
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

    @Test
    void runEchoPathResolutionAndArgs(@TempDir Path tmp) throws IOException {
        assertAllTargets(tmp, "echo", """
            main() {
                val r = process.run("echo", "vivo")
                println(r.stdout)
                println(r.exitCode)
            }
            """, "vivo\n\n0\n");
    }

    @Test
    void runNonZeroExitCodeAndStderrSeparation(@TempDir Path tmp) throws IOException {
        assertAllTargets(tmp, "stderr", """
            main() {
                val r = process.run("sh", "-c", "echo fora >&2; echo dentro")
                println(r.stdout)
                println("|" + r.stderr + "|")
                println(r.exitCode)
            }
            """, "dentro\n\n|fora\n|\n0\n");
    }

    @Test
    void runLargeStdoutDrainsWithoutDeadlock(@TempDir Path tmp) throws IOException {
        assertAllTargets(tmp, "large", """
            main() {
                val r = process.run("sh", "-c", "seq 1 5000")
                println(r.stdout.contains("5000"))
                println(r.stdout.length)
                println(r.exitCode)
            }
            """, "true\n23893\n0\n");
    }

    @Test
    void runMissingProgramIsNegativeOne(@TempDir Path tmp) throws IOException {
        assertAllTargets(tmp, "missing", """
            main() {
                val r = process.run("no-such-binary-9f3c7a")
                println(r.stdout == "")
                println(r.stderr != "")
                println(r.exitCode)
            }
            """, "true\ntrue\n-1\n");
    }

    /** C2/Q4: os DOIS pipes enchem ao mesmo tempo — se o drain não for
     *  concorrente (ppoll), um filho bloqueado no write do stderr trava o
     *  read do stdout e o `wait4` nunca chega (deadlock clássico de pipe). */
    @Test
    void runBothStreamsLargeDrainsConcurrently(@TempDir Path tmp) throws IOException {
        assertAllTargets(tmp, "both", """
            main() {
                val r = process.run("sh", "-c", "seq 1 4000; seq 1 2000 >&2")
                println(r.stdout.length)
                println(r.stderr.length)
                println(r.exitCode)
            }
            """, "18893\n8893\n0\n");
    }

    /** C3: o argv é elemento-a-elemento (nunca reinterpretado por shell); um
     *  argumento com espaços permanece UM elemento. */
    @Test
    void runKeepsSpacedArgumentsAsSingleElement(@TempDir Path tmp) throws IOException {
        assertAllTargets(tmp, "spaces", """
            main() {
                val r = process.run("echo", "a b", "c")
                println(r.stdout)
                println(r.exitCode)
            }
            """, "a b c\n\n0\n");
    }
}
