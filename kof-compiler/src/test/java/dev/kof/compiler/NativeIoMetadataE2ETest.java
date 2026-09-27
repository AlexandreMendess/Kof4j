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
 * D-FULL-PARITY-050 (row 13) — metadados de arquivo em TODOS os alvos:
 * {@code File.modifiedTime()} (millis) e {@code File.isSymlink()} no x86-64
 * ({@link dev.kof.compiler.runtime.RuntimeIoMeta}) e no cross riscv64/aarch64
 * ({@link dev.kof.compiler.nat.NativeRiscvAsmIoMeta}). O arquivo+link sao
 * criados UMA vez (path absoluto) e lidos por JVM e nativo, entao o oraculo e
 * exatamente o valor medido em Java (Files.getLastModifiedTime(...).toMillis),
 * nao um chute.
 * Q3: cobre mtime != 0 e symlink true/regular false.
 */
class NativeIoMetadataE2ETest {

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

    private static Path writeProgram(Path dir, String tag, String file, String link) throws IOException {
        String program = """
                main() {
                    println(File("%1$s").modifiedTime())
                    println(File("%2$s").isSymlink())
                    println(File("%1$s").isSymlink())
                }
                """.formatted(file, link);
        Path src = dir.resolve("Main-" + tag + ".kf");
        Files.writeString(src, program);
        return src;
    }

    private static String expected(Path file) throws IOException {
        return Files.getLastModifiedTime(file).toMillis() + "\ntrue\nfalse";
    }

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

    private Path file;
    private Path link;
    private String expected;

    private void setup(Path tempDir) throws IOException {
        file = tempDir.resolve("meta.txt");
        Files.writeString(file, "x");
        link = tempDir.resolve("meta-link.txt");
        try {
            Files.deleteIfExists(link);
            Files.createSymbolicLink(link, file.getFileName());
        } catch (UnsupportedOperationException | IOException e) {
            Assumptions.abort("symlinks unsupported: " + e);
        }
        expected = expected(file);
    }

    @Test
    void jvmMetadataMatchesMeasurement(@TempDir Path tempDir) throws IOException {
        setup(tempDir);
        assertEquals(expected, runJvm(writeProgram(tempDir, "jvm", file.toString(), link.toString()),
                tempDir.resolve("jvm-out")));
    }

    @Test
    void x86MetadataMatchesMeasurement(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("linux"),
                "x86-64 native runs on Linux");
        setup(tempDir);
        assertEquals(expected, runNative(writeProgram(tempDir, "x86", file.toString(), link.toString()),
                tempDir.resolve("x86-out")));
    }

    @Test
    void riscv64MetadataMatchesMeasurement(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross toolchain riscv64 + qemu ausente — pulando (NATIVE002)");
        setup(tempDir);
        assertEquals(expected, runCross(writeProgram(tempDir, "r", file.toString(), link.toString()),
                tempDir.resolve("riscv-out"), "qemu-riscv64", Target.NATIVE_RISCV64));
    }

    @Test
    void aarch64MetadataMatchesMeasurement(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain aarch64 + qemu ausente — pulando (NATIVE002)");
        setup(tempDir);
        assertEquals(expected, runCross(writeProgram(tempDir, "a", file.toString(), link.toString()),
                tempDir.resolve("aarch-out"), "qemu-aarch64", Target.NATIVE_AARCH64));
    }
}
