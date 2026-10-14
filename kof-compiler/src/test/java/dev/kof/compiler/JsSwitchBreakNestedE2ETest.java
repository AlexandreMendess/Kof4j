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
 * known-bugs §701: a {@code break} NESTED inside an {@code if} inside a
 * {@code switch} case emitted WRONG JS on the JS backend — the default body
 * leaked into the case and the {@code break} became an EMPTY block, so
 * {@code swBreakInLoop} returned {@code 32} instead of {@code 2}.
 *
 * <p>Two structural defects in the JS reconstruction, both fixed together:
 * (1) the switch body was parsed with an empty {@code endLabels} set, so a
 * {@code break} jumping to the switch's end label fell into
 * {@code parseStatements}'s generic "region exit" path and TRUNCATED the case
 * body; (2) {@code parseIfBody} consumed the switch's {@code default} label as
 * if it were the {@code if}'s end label, so the default's body was pulled
 * inside the preceding case.
 *
 * <p>The oracle is the JVM. RED-first: the JS face was RED pre-fix.
 */
class JsSwitchBreakNestedE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String SRC = """
            Int swBreakInLoop() {
                var r = 0
                for (var i in listOf(0, 1, 2)) {
                    switch (1) {
                        case 1:
                            if (i == 1) { break }
                            r += 1
                        default:
                            r += 10
                    }
                }
                return r
            }

            Int swNoBreak() {
                var r = 0
                for (var i in listOf(0, 1, 2)) {
                    switch (1) {
                        case 1:
                            r += 1
                        default:
                            r += 10
                    }
                }
                return r
            }

            Int swBreakDirect() {
                var r = 0
                for (var i in listOf(0, 1, 2)) {
                    switch (i) {
                        case 1:
                            break
                        default:
                            r += 10
                    }
                    r += 1
                }
                return r
            }

            Int swBreakWhile() {
                var r = 0
                var i = 0
                while (i < 3) {
                    switch (1) {
                        case 1:
                            if (i == 1) { break }
                            r += 1
                        default:
                            r += 10
                    }
                    i++
                }
                return r
            }

            Int nestedLoopInCase() {
                var r = 0
                switch (1) {
                    case 1:
                        for (var j in listOf(1, 2, 3)) {
                            if (j == 2) { break }
                            r += j
                        }
                    default:
                        r += 100
                }
                return r
            }

            String multiCase() {
                var out = ""
                for (var i in listOf(0, 1, 2, 3)) {
                    switch (i) {
                        case 0:
                            out += "z"
                        case 1:
                            if (i == 1) { out += "o" }
                        case 2:
                            out += "t"
                        default:
                            out += "-"
                    }
                }
                return out
            }

            main() {
                println(swBreakInLoop())
                println(swNoBreak())
                println(swBreakDirect())
                println(swBreakWhile())
                println(nestedLoopInCase())
                println(multiCase())
                println("end")
            }
            """;

    private static final String GOLDEN = String.join("\n",
            "2", "3", "23", "2", "1", "zot-", "end");

    @Test
    void jvmIsTheContractForNestedSwitchBreak() throws Exception {
        assertEquals(GOLDEN, runJvm("oracle"), "o oráculo JVM mudou — revise o caso");
    }

    @Test
    void scriptMatchesTheJvmOnNestedSwitchBreak() throws Exception {
        Path dir = tmp.resolve("script");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        KofInterpreter.Result r = driver.interpret(
                java.util.List.of(dir.resolve("Main.kf")), dir, new String[0]);
        assertEquals(0, r.exitCode(), () -> "script stderr: " + r.stderr());
        assertEquals(GOLDEN, r.stdout().strip(), "§701: o Script já compilava");
    }

    @Test
    void jsMatchesTheJvmOnNestedSwitchBreak() throws Exception {
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
                "§701: o `break` aninhado num if dentro de um case emitia JS errado (default vazava)");
    }

    @Test
    void nativeX86MatchesTheJvmOnNestedSwitchBreak() throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        assertEquals(GOLDEN, runNative("x86", Target.NATIVE, null));
    }

    @Test
    void nativeRiscv64MatchesTheJvmOnNestedSwitchBreak() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        assertEquals(GOLDEN, runNative("riscv64", Target.NATIVE_RISCV64, "riscv64"));
    }

    @Test
    void nativeAarch64MatchesTheJvmOnNestedSwitchBreak() throws Exception {
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
