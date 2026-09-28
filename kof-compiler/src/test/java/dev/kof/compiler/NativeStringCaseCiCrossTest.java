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
 * D-STR-UNICODE (linha 11, fatia-5) — {@code String.compareToIgnoreCase} no
 * cross (riscv64/aarch64): fold DUPLO por code unit = algoritmo EXATO do JDK
 * ({@code RuntimeStringCaseCi} no x86; aqui via {@code NativeRiscvAsmCaseCi},
 * aarch64 pelo tradutor). Golden = o oráculo JVM medido no MESMO programa
 * (12 arestas: ß/S, İ/i (toLowerCase(0x130)=0x69), ǰ/J+caron, sigma, ẛ/ẞ,
 * prefixo, vazia, dígrafos ǅ/ǆ, ligatura ﬁ, par surrogate astral com cauda
 * dobrada, acentuadas iguais). Mesma disciplina de
 * {@link NativeStringCaseCrossTest}; x86+JS travados em
 * {@link StringUnicodeFacesMeasuredTest}.
 */
class NativeStringCaseCiCrossTest {

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

    private static final String PROGRAM = """
            main() {
                println("straße".compareToIgnoreCase("STRASSE"))
                println("İ".compareToIgnoreCase("i"))
                println("Hello".compareToIgnoreCase("hello"))
                println("ǰ".compareToIgnoreCase("J̌"))
                println("Σ".compareToIgnoreCase("σ"))
                println("ẛ".compareToIgnoreCase("ẞ"))
                println("abc".compareToIgnoreCase("abcd"))
                println("".compareToIgnoreCase(""))
                println("ǅ".compareToIgnoreCase("ǆ"))
                println("ﬁ".compareToIgnoreCase("fi"))
                println("😀x".compareToIgnoreCase("😀Y"))
                println("ÉCOLE".compareToIgnoreCase("école"))
            }
            """;

    private static final String GOLDEN =
            "108\n0\n0\n390\n0\n7554\n-1\n0\n0\n64155\n-1\n0";

    @Test
    void riscv64CompareToIgnoreCaseJdkFold(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross toolchain riscv64 + qemu ausente — pulando (NATIVE002)");
        assertEquals(GOLDEN, runCross(tempDir, PROGRAM, "qemu-riscv64", "NATIVE_RISCV64"));
    }

    @Test
    void aarch64CompareToIgnoreCaseJdkFold(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain aarch64 + qemu ausente — pulando (NATIVE002)");
        assertEquals(GOLDEN, runCross(tempDir, PROGRAM, "qemu-aarch64", "NATIVE_AARCH64"));
    }
}
