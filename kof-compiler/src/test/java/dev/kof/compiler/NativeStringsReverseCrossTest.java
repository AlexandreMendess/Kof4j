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
 * D-FULL-PARITY-050 (row 11) — {@code strings.reverse} em texto não-ASCII nos
 * cross-arch. O JVM ({@code new StringBuilder(v).reverse()}, que preserva pares
 * surrogate) e o JS ({@code [...v].reverse()}) invertem por CODE POINT; a
 * implementação nativa invertia BYTES UTF-8, quebrando qualquer sequência
 * multi-byte (o resultado nem é UTF-8 válido). Golden = oracle JVM medido no
 * MESMO programa; as unidades UTF-16 são impressas via {@code toCharArray()}
 * (a face char já provada) para não depender de encoding do stdout.
 * Arquivo NOVO e isolado (mesma disciplina do {@link NativeStringUtf16CrossTest}).
 */
class NativeStringsReverseCrossTest {

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
                Target.valueOf(archFlag));
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

    // café (63 61 66 C3 A9) -> "éfac" = 233,102,97,99
    // a😀b -> "b😀a" = 98,D83D,DE00,97 (par surrogate preservado)
    // áç -> "çá" = 231,225 ; "" -> 0
    private static final String PROGRAM = """
            main() {
                var r = strings.reverse("café")
                var a = r.toCharArray()
                println(a.length)
                for (var i = 0; i < a.length; i++) {
                    println(a[i] as Int)
                }
                var r2 = strings.reverse("a😀b")
                var b = r2.toCharArray()
                println(b.length)
                for (var i = 0; i < b.length; i++) {
                    println(b[i] as Int)
                }
                var r3 = strings.reverse("áç")
                var c = r3.toCharArray()
                println(c.length)
                for (var i = 0; i < c.length; i++) {
                    println(c[i] as Int)
                }
                var r4 = strings.reverse("€á😀")
                var d = r4.toCharArray()
                println(d.length)
                for (var i = 0; i < d.length; i++) {
                    println(d[i] as Int)
                }
                println(strings.reverse("").length)
            }
            """;

    private static final String GOLDEN =
            "4\n233\n102\n97\n99\n4\n98\n55357\n56832\n97\n2\n231\n225\n"
                    + "4\n55357\n56832\n225\n8364\n0";

    @Test
    void riscv64ReverseByCodePoint(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross toolchain riscv64 + qemu ausente — pulando (NATIVE002)");
        assertEquals(GOLDEN, runCross(tempDir, PROGRAM, "qemu-riscv64", "NATIVE_RISCV64"));
    }

    @Test
    void aarch64ReverseByCodePoint(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain aarch64 + qemu ausente — pulando (NATIVE002)");
        assertEquals(GOLDEN, runCross(tempDir, PROGRAM, "qemu-aarch64", "NATIVE_AARCH64"));
    }
}
