package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * known-bugs §698: the Int SHIFT operators did not follow the JVM on the CROSS
 * targets, and {@code >>>} was wrong on JS.
 *
 * <p>The counts are read from a LIST so they are runtime values — a literal
 * count is constant-folded by the compiler and hides the emitter entirely
 * (that is how this went unnoticed: {@code -5 >>> 1} with literals was folded
 * and only the variable form reached the backend).
 *
 * <p>Cross faces: riscv {@code sll}/{@code srl}/{@code sra} work on 64 bits and
 * take the count mod 64, where the JVM's {@code ishl}/{@code iushr}/{@code ishr}
 * work on 32 bits and take it mod 32 — so {@code 1 << 31} gave {@code 2147483648}
 * (not {@code -2147483648}), {@code 1 << 33} gave {@code 2^33} (not {@code 2}),
 * {@code -5 >>> 1} leaked a 64-bit result and {@code -5 >> 32} gave {@code -1}
 * (not {@code -5}). The x86 emitter is correct: {@code %cl} already masks to 5
 * bits and the ops are 32-bit.
 *
 * <p>JS face: {@code >>>} returns an UNSIGNED 32-bit value, so
 * {@code -5 >>> 0} gave {@code 4294967291} instead of {@code -5}.
 *
 * <p>The oracle is the JVM. RED-first: every face below was RED pre-fix.
 */
class ShiftSemanticsE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String SRC = """
            counts(): List<Int> { return listOf(0, 1, 31, 32, 33, 63) }

            main() {
                for (var c in counts()) {
                    println((1 << c) + " " + (-5 >> c) + " " + (-5 >>> c))
                }
                for (var c in counts()) {
                    println((1L << c) + " " + (-5L >>> c))
                }
                println("end")
            }
            """;

    /** Int: `<<` / `>>` / `>>>` of 1 and -5 at the counts 0/1/31/32/33/63. */
    private static final String INT_ROWS = String.join("\n",
            "1 -5 -5",           // c=0
            "2 -3 2147483645",   // c=1
            "-2147483648 -1 1",  // c=31
            "1 -5 -5",           // c=32  (count mod 32 == 0)
            "2 -3 2147483645",   // c=33
            "-2147483648 -1 1"); // c=63

    /** Long: `<<` and `>>>` of 1L and -5L — the 64-bit counts are NOT masked to 32. */
    private static final String LONG_ROWS = String.join("\n",
            "1 -5",                          // c=0
            "2 9223372036854775805",         // c=1
            "2147483648 8589934591",         // c=31
            "4294967296 4294967295",         // c=32  (Long: contagem mod 64)
            "8589934592 2147483647",         // c=33
            "-9223372036854775808 1");       // c=63

    private static final String GOLDEN = INT_ROWS + "\n" + LONG_ROWS + "\nend";

    @Test
    void jvmIsTheContractForShifts() throws Exception {
        assertEquals(GOLDEN, runJvm("oracle"), "o oráculo JVM mudou — revise o caso");
    }

    @Test
    void scriptMatchesTheJvmOnShifts() throws Exception {
        Path dir = tmp.resolve("script");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        KofInterpreter.Result r = driver.interpret(
                java.util.List.of(dir.resolve("Main.kf")), dir, new String[0]);
        assertEquals(0, r.exitCode(), () -> "script stderr: " + r.stderr());
        assertEquals(GOLDEN, r.stdout().strip(),
                "§698: o Script já mascarava — o cross/JS são os divergentes");
    }

    @Test
    void jsMatchesTheJvmOnShifts() throws Exception {
        Path dir = tmp.resolve("js");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.JS);
        assertTrue(r.success(), () -> "js compile: " + r.diagnostics().getDiagnostics());
        var stdout = new java.io.ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(out.resolve("Default.mjs"), stdout,
                new java.io.ByteArrayInputStream(new byte[0]), stdout);
        assertEquals(0, exit, () -> "js saída: " + stdout);
        assertEquals(GOLDEN, stdout.toString(StandardCharsets.UTF_8).strip(),
                "§698 face JS: o `>>>` devolve 32 bits SEM SINAL (o `-5 >>> 0` dava 4294967291)");
    }

    @Test
    void nativeX86MatchesTheJvmOnShifts() throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        assertEquals(GOLDEN, runNative("x86", Target.NATIVE, null));
    }

    @Test
    void nativeRiscv64MatchesTheJvmOnShifts() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        assertEquals(GOLDEN, runNative("riscv64", Target.NATIVE_RISCV64, "riscv64"));
    }

    @Test
    void nativeAarch64MatchesTheJvmOnShifts() throws Exception {
        Assumptions.assumeTrue(has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross aarch64 + qemu absent — skipping (NATIVE002)");
        assertEquals(GOLDEN, runNative("aarch64", Target.NATIVE_AARCH64, "aarch64"));
    }

    // ── harness ────────────────────────────────────────────────────────────

    private String runJvm(String name) throws Exception {
        Path dir = tmp.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.JVM);
        assertTrue(r.success(), () -> name + " compile: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder("java", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8",
                "-cp", out.toString(), "Default.Main").redirectErrorStream(true).start();
        String o = NativeRiscv64E2ETest.runBounded(p, name + " jvm");
        assertEquals(0, p.exitValue(), () -> name + " (oráculo JVM): " + o);
        return o;
    }

    private String runNative(String name, Target target, String arch) throws Exception {
        Path dir = tmp.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, target);
        assertTrue(r.success(), () -> name + " compile: " + r.diagnostics().getDiagnostics());
        Path binary = out.resolve("Default/Main");
        assertTrue(Files.isRegularFile(binary), name + ": binário nativo ausente");
        ProcessBuilder pb = arch == null
                ? new ProcessBuilder(binary.toString())
                : NativeRiscv64E2ETest.qemu(arch, binary);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String o = NativeRiscv64E2ETest.runBounded(p, name + " (" + target + ")");
        assertEquals(0, p.exitValue(), () -> name + " saída: " + o);
        return o;
    }

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
}
