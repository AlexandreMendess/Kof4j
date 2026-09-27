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
 * D-FULL-PARITY-050 (row 11) — {@code String.toCharArray()} nos cross-arch
 * (B74): o array devolvido tem os CODE UNITS UTF-16 na mesma ordem do JVM/x86
 * (astral = 2 elementos: high + low surrogate), nunca bytes UTF-8 nem code
 * points. {@code c[i] as Int} isola a unit (um surrogate solto não é um
 * caractere exibível). Arquivo NOVO e isolado de propósito (mesma disciplina do
 * {@link NativeStringUtf16CrossTest}) para não disputar arquivo com a lane do
 * sweep. Golden = oracle JVM medido no MESMO programa.
 */
class NativeStringToCharArrayCrossTest {

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
        CompilationResult result = driver.compile(src, outDir,
                dev.kof.compiler.Target.valueOf(archFlag));
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

    // "café" → 4 units (99,97,102,233); "a😀b" → 4 units (97,D83D,DE00,98);
    // "" → 0 (array vazio). Oracle JVM: 4/99/97/102/233/4/97/55357/56832/98/0.
    private static final String PROGRAM = """
            main() {
                var a = "café".toCharArray()
                println(a.length)
                for (var i = 0; i < a.length; i++) {
                    println(a[i] as Int)
                }
                var e = "a😀b".toCharArray()
                println(e.length)
                for (var i = 0; i < e.length; i++) {
                    println(e[i] as Int)
                }
                println("".toCharArray().length)
            }
            """;

    private static final String GOLDEN =
            "4\n99\n97\n102\n233\n4\n97\n55357\n56832\n98\n0";

    @Test
    void riscv64ToCharArrayUtf16(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross toolchain riscv64 + qemu ausente — pulando (NATIVE002)");
        assertEquals(GOLDEN, runCross(tempDir, PROGRAM, "qemu-riscv64", "NATIVE_RISCV64"));
    }

    @Test
    void aarch64ToCharArrayUtf16(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain aarch64 + qemu ausente — pulando (NATIVE002)");
        assertEquals(GOLDEN, runCross(tempDir, PROGRAM, "qemu-aarch64", "NATIVE_AARCH64"));
    }
}
