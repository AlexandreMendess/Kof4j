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
 * known-bugs §700: a {@code switch} over {@code Int} containing a {@code case 0}
 * did NOT COMPILE on JS — the JS backend threw
 * {@code COMP002: Internal compiler error: KofJS: unexpected op in switch case}.
 *
 * <p>Root: {@code case 0} lowers as {@code (subject - 0) == 0}, and the
 * optimizer's {@code foldIdentity} ({@code x - 0 -> x}) removes BOTH the
 * {@code SUB} and the caseVal literal, leaving the DIRECT comparison
 * {@code [#switch, LoadLiteral(0), CJump(EQ)]}. The JS switch parser only knew
 * {@code [#switch, caseVal, SUB|EQ|call, LoadLiteral(0), CJump]}, so it fell
 * into its {@code unexpected op} throw. The other five targets compile and
 * agree — this is a JS-only, compile-time crash.
 *
 * <p>The oracle is the JVM. RED-first: the JS face was RED pre-fix (it did not
 * even compile).
 */
class JsSwitchCaseZeroE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String SRC = """
            String classify(Int n) {
                switch (n) {
                    case 0: return "zero"
                    case 1: return "one"
                    case 2: return "two"
                    default: return "many"
                }
            }

            Int bucket(Int n) {
                var r = -1
                switch (n) {
                    case 0: r = 100
                    case 5: r = 500
                    default: r = 0
                }
                return r
            }

            String noZero(Int n) {
                switch (n) {
                    case 3: return "three"
                    case 4: return "four"
                    default: return "other"
                }
            }

            main() {
                println(classify(0))
                println(classify(1))
                println(classify(2))
                println(classify(9))
                println(bucket(0))
                println(bucket(5))
                println(bucket(7))
                println(noZero(3))
                println(noZero(4))
                println(noZero(0))
                println("end")
            }
            """;

    /** `case 0` presente (a forma dobrada) + controle sem ele. */
    private static final String GOLDEN = String.join("\n",
            "zero", "one", "two", "many",
            "100", "500", "0",
            "three", "four", "other", "end");

    @Test
    void jvmIsTheContractForSwitchCaseZero() throws Exception {
        assertEquals(GOLDEN, runJvm("oracle"), "o oráculo JVM mudou — revise o caso");
    }

    @Test
    void scriptMatchesTheJvmOnSwitchCaseZero() throws Exception {
        Path dir = tmp.resolve("script");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        KofInterpreter.Result r = driver.interpret(
                java.util.List.of(dir.resolve("Main.kf")), dir, new String[0]);
        assertEquals(0, r.exitCode(), () -> "script stderr: " + r.stderr());
        assertEquals(GOLDEN, r.stdout().strip(), "§700: o Script já compilava");
    }

    @Test
    void jsMatchesTheJvmOnSwitchCaseZero() throws Exception {
        Path dir = tmp.resolve("js");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.JS);
        assertTrue(r.success(), () -> "§700: o switch com `case 0` não compilava no JS — "
                + r.diagnostics().getDiagnostics());
        var stdout = new java.io.ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(out.resolve("Default.mjs"), stdout,
                new java.io.ByteArrayInputStream(new byte[0]), stdout);
        assertEquals(0, exit, () -> "js saída: " + stdout);
        assertEquals(GOLDEN, stdout.toString(StandardCharsets.UTF_8).strip());
    }

    @Test
    void nativeX86MatchesTheJvmOnSwitchCaseZero() throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        assertEquals(GOLDEN, runNative("x86", Target.NATIVE, null));
    }

    @Test
    void nativeRiscv64MatchesTheJvmOnSwitchCaseZero() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        assertEquals(GOLDEN, runNative("riscv64", Target.NATIVE_RISCV64, "riscv64"));
    }

    @Test
    void nativeAarch64MatchesTheJvmOnSwitchCaseZero() throws Exception {
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
