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
 * D-FULL-PARITY-050 (row 9) — {@code kof.cache} nos cross-arch
 * (riscv64/aarch64). O runtime asm do cross já existe
 * ({@code NativeRiscvAsmRtB1} set/set_ttl, {@code RtB2}
 * get/ttl/delete/clear); faltava o golden. Golden = oracle JVM medido no MESMO
 * programa (mesmos strings que o {@link KofCacheE2ETest} já fixa em x86/JS).
 * A expiração usa {@code kof_time_now} real do cross (não o stub antigo).
 */
class KofCacheCrossTest {

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

    private String runCross(Path tempDir, String source, String qemu, String archFlag)
            throws IOException {
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, source);
        Path outDir = tempDir.resolve("out-" + archFlag);
        CompilationResult result = driver.compile(src, outDir, Target.valueOf(archFlag));
        assertTrue(result.success(), "compile: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        assertTrue(Files.exists(bin), "binary should exist");
        ProcessBuilder pb = NativeRiscv64E2ETest.qemu(qemu.substring(5), bin);
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
        assertEquals(0, ec, "exit code, output: " + output);
        return output;
    }

    // Mesmos casos do KofCacheE2ETest (JVM oracle), num só programa:
    // roundtrip, missing, overwrite, delete/clear, sem-ttl.
    private static final String FACES = """
            main() {
                cache.set("name", "Mel")
                println(cache.get("name"))
                println(cache.get("missing"))
                cache.set("k", "v1")
                cache.set("k", "v2")
                println(cache.get("k"))
                cache.set("a", "1")
                cache.set("b", "2")
                cache.delete("a")
                println(cache.get("a"))
                println(cache.get("b"))
                cache.clear()
                println(cache.get("b"))
                cache.set("k2", "v")
                println(cache.ttl("k2") == -1)
            }
            """;

    private static final String FACES_GOLDEN = "Mel\nnull\nv2\nnull\n2\nnull\ntrue";

    private static final String TTL = """
            main() {
                cache.set("t", "x", 1)
                println(cache.ttl("t") >= 0 && cache.ttl("t") <= 1)
                println(cache.get("t"))
                time.sleep(1300)
                println(cache.get("t"))
                println(cache.ttl("t") == -1)
            }
            """;

    private static final String TTL_GOLDEN = "true\nx\nnull\ntrue";

    @Test
    void riscv64CacheFaces(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross toolchain riscv64 + qemu ausente — pulando (NATIVE002)");
        assertEquals(FACES_GOLDEN, runCross(tempDir, FACES, "qemu-riscv64", "NATIVE_RISCV64"));
    }

    @Test
    void aarch64CacheFaces(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain aarch64 + qemu ausente — pulando (NATIVE002)");
        assertEquals(FACES_GOLDEN, runCross(tempDir, FACES, "qemu-aarch64", "NATIVE_AARCH64"));
    }

    @Test
    void riscv64CacheTtlExpiry(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross toolchain riscv64 + qemu ausente — pulando (NATIVE002)");
        assertEquals(TTL_GOLDEN, runCross(tempDir, TTL, "qemu-riscv64", "NATIVE_RISCV64"));
    }

    @Test
    void aarch64CacheTtlExpiry(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain aarch64 + qemu ausente — pulando (NATIVE002)");
        assertEquals(TTL_GOLDEN, runCross(tempDir, TTL, "qemu-aarch64", "NATIVE_AARCH64"));
    }
}
