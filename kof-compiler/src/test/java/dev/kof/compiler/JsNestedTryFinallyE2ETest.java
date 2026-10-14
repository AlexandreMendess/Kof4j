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
 * known-bugs §702: a try NESTED inside a try whose inner try has a {@code finally}
 * did NOT COMPILE on JS — {@code COMP002: Internal compiler error: KofJS:
 * unexpected KofCatchStart at statement level}.
 *
 * <p>Root: in {@code JsTryParser}, after the inner try's {@code finally} body was
 * parsed, the trailing-label consumption branch had NO {@code isTryEndLabel}
 * guard. When the finally body ends via the "unmatched label" path the {@code done}
 * label is null, so that branch blindly consumed the NEXT label — which in a
 * nested try/finally is the OUTER try's end label. Consuming it left the outer
 * try without its boundary, so its {@code KofCatchStart} surfaced at statement
 * level and the compiler threw. The sibling branch already carried that guard
 * (§617 face B); this one did not.
 *
 * <p>The oracle is the JVM. RED-first: the JS face was RED pre-fix (it did not
 * even compile).
 */
class JsNestedTryFinallyE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String SRC = """
            Int nestedTryFinally() {
                var r = 0
                try {
                    try {
                        throw "inner"
                    } finally {
                        r += 1
                    }
                } catch (String e) {
                    r += 10
                }
                return r
            }

            Int nestedTryCatch() {
                var r = 0
                try {
                    try {
                        throw "inner"
                    } catch (String e) {
                        r += 1
                    }
                } catch (String e) {
                    r += 10
                }
                return r
            }

            Int nestedFinallyOk() {
                var r = 0
                try {
                    try {
                        r += 1
                    } finally {
                        r += 2
                    }
                } finally {
                    r += 4
                }
                return r
            }

            Int nestedFinallyReturn() {
                var r = 0
                try {
                    try {
                        return 7
                    } finally {
                        r += 1
                    }
                } finally {
                    r += 2
                }
            }

            Int tripleNested() {
                var r = 0
                try {
                    try {
                        try {
                            throw "deep"
                        } finally {
                            r += 1
                        }
                    } finally {
                        r += 2
                    }
                } catch (String e) {
                    r += 100
                }
                return r
            }

            main() {
                println(nestedTryFinally())
                println(nestedTryCatch())
                println(nestedFinallyOk())
                println(nestedFinallyReturn())
                println(tripleNested())
                println("end")
            }
            """;

    private static final String GOLDEN = String.join("\n", "11", "1", "7", "7", "103", "end");

    @Test
    void jvmIsTheContractForNestedTry() throws Exception {
        assertEquals(GOLDEN, runJvm("oracle"), "o oráculo JVM mudou — revise o caso");
    }

    @Test
    void scriptMatchesTheJvmOnNestedTry() throws Exception {
        Path dir = tmp.resolve("script");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        KofInterpreter.Result r = driver.interpret(
                java.util.List.of(dir.resolve("Main.kf")), dir, new String[0]);
        assertEquals(0, r.exitCode(), () -> "script stderr: " + r.stderr());
        assertEquals(GOLDEN, r.stdout().strip(), "§702: o Script já compilava");
    }

    @Test
    void jsMatchesTheJvmOnNestedTry() throws Exception {
        Path dir = tmp.resolve("js");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.JS);
        assertTrue(r.success(), () -> "§702: o try aninhado com finally não compilava no JS — "
                + r.diagnostics().getDiagnostics());
        var stdout = new java.io.ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(out.resolve("Default.mjs"), stdout,
                new java.io.ByteArrayInputStream(new byte[0]), stdout);
        assertEquals(0, exit, () -> "js saída: " + stdout);
        assertEquals(GOLDEN, stdout.toString(StandardCharsets.UTF_8).strip());
    }

    @Test
    void nativeX86MatchesTheJvmOnNestedTry() throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        assertEquals(GOLDEN, runNative("x86", Target.NATIVE, null));
    }

    @Test
    void nativeRiscv64MatchesTheJvmOnNestedTry() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        assertEquals(GOLDEN, runNative("riscv64", Target.NATIVE_RISCV64, "riscv64"));
    }

    @Test
    void nativeAarch64MatchesTheJvmOnNestedTry() throws Exception {
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
