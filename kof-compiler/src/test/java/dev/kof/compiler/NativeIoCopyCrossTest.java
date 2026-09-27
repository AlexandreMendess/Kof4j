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
 * D-FULL-PARITY-050 (row 13) — {@code File.copyTo()} em todos os alvos:
 * x86-64 ({@link dev.kof.compiler.runtime.RuntimeIoCopy}) e cross
 * ({@link dev.kof.compiler.nat.NativeRiscvAsmIoCopy}). Contrato JVM G-ORG-002:
 * Files.copy com COPY_ATTRIBUTES, sem sobrescrever (destino existente -> false).
 * Q3: cobre copia OK (src permanece, dst com conteudo) e dest existente -> false
 * com dst intacto.
 */
class NativeIoCopyCrossTest {

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

    private static Path writeProgram(Path dir, String tag, String base) throws IOException {
        String program = """
                main() {
                    File("%1$s/c1").writeText("hello")
                    println(File("%1$s/c1").copyTo("%1$s/c2"))
                    println(File("%1$s/c2").exists())
                    println(File("%1$s/c2").readText())
                    println(File("%1$s/c1").exists())
                    File("%1$s/d1").writeText("old")
                    println(File("%1$s/c1").copyTo("%1$s/d1"))
                    println(File("%1$s/d1").readText())
                }
                """.formatted(base);
        Path src = dir.resolve("Main-" + tag + ".kf");
        Files.writeString(src, program);
        return src;
    }

    private static final String EXPECTED = "true\ntrue\nhello\ntrue\nfalse\nold";

    private String runJvm(Path src, Path outDir) throws IOException {
        CompilationResult r = driver.compile(src, outDir, Target.JVM);
        assertTrue(r.success(), "jvm compile: " + r.diagnostics().getDiagnostics());
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

    private String runNative(Path src, Path outDir) throws IOException {
        CompilationResult r = driver.compile(src, outDir, Target.NATIVE);
        assertTrue(r.success(), "x86-64 compile: " + r.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        assertTrue(Files.exists(bin), "binary should exist");
        ProcessBuilder pb = new ProcessBuilder(bin.toString());
        pb.redirectErrorStream(true);
        try {
            return capture(pb.start());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }

    private String runCross(Path src, Path outDir, String qemu, Target target) throws IOException {
        CompilationResult r = driver.compile(src, outDir, target);
        assertTrue(r.success(), "cross compile: " + r.diagnostics().getDiagnostics());
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

    private Path base(Path tempDir, String tag) throws IOException {
        Path b = tempDir.resolve("base-" + tag);
        Files.createDirectories(b);
        return b;
    }

    @Test
    void jvmCopyToMatchesContract(@TempDir Path tempDir) throws IOException {
        assertEquals(EXPECTED, runJvm(writeProgram(tempDir, "jvm", base(tempDir, "jvm").toString()),
                tempDir.resolve("jvm-out")));
    }

    @Test
    void x86CopyToMatchesContract(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("linux"),
                "x86-64 native runs on Linux");
        assertEquals(EXPECTED, runNative(writeProgram(tempDir, "x86", base(tempDir, "x86").toString()),
                tempDir.resolve("x86-out")));
    }

    @Test
    void riscv64CopyToMatchesContract(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross toolchain riscv64 + qemu ausente — pulando (NATIVE002)");
        assertEquals(EXPECTED, runCross(writeProgram(tempDir, "r", base(tempDir, "r").toString()),
                tempDir.resolve("riscv-out"), "qemu-riscv64", Target.NATIVE_RISCV64));
    }

    @Test
    void aarch64CopyToMatchesContract(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain aarch64 + qemu ausente — pulando (NATIVE002)");
        assertEquals(EXPECTED, runCross(writeProgram(tempDir, "a", base(tempDir, "a").toString()),
                tempDir.resolve("aarch-out"), "qemu-aarch64", Target.NATIVE_AARCH64));
    }
}
