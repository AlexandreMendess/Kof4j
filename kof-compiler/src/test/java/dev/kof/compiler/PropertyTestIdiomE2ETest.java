package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SG-023 (decided: option C + iii — NO new language surface): the
 * property-based testing "runner" is the existing `test "name" { }` +
 * `kof.rng` + `assert` idiom. This proves the idiom is executable,
 * seed-reproducible and target-parity on JVM/Native-x86/JS — so a new
 * `property`/`forAll` keyword would be ceremony the platform should not need
 * (rule 11). Fixtures stay the `close()` + `try/finally` pattern (D5-B).
 */
class PropertyTestIdiomE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    /** Property that HOLDS: seeded loop over random pairs; checksum is printed so
     *  JVM/JS/Native can be compared bit-for-bit (same seed => same bits). */
    private static final String HOLDS = """
            test "addition commutes on random pairs" {
                rng.seed(42)
                var sum = 0
                var i = 0
                while (i < 200) {
                    var a = rng.int(10000) - 5000
                    var b = rng.int(10000) - 5000
                    assert(a + b == b + a, "commutativity broke")
                    sum = sum + a + b
                    i = i + 1
                }
                println("checksum=" + sum)
            }
            """;

    /** Property that FAILS on some generated input, deterministically per seed:
     *  proves the loop really consumes rng and the failure is reproducible. */
    private static final String FALSIFIABLE = """
            test "all draws are below 50" {
                rng.seed(7)
                var i = 0
                while (i < 200) {
                    var a = rng.int(100)
                    assert(a < 50, "a>=50 at seed=7")
                    i = i + 1
                }
            }
            """;

    private record Run(int exitCode, String output) {
    }

    private Run runJvm(Path source, Path outDir) throws IOException {
        CompilationResult result = driver.compileForTests(source, outDir, Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            return new Run(p.waitFor(), output);
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
    }

    private Run runNative(Path source, Path outDir) throws IOException {
        CompilationResult result = driver.compileForTests(source, outDir, Target.NATIVE);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        assertTrue(Files.exists(bin), "Binary should exist");
        try {
            ProcessBuilder pb = new ProcessBuilder(bin.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            return new Run(p.waitFor(), output);
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
    }

    private Run runJs(Path source, Path outDir) throws IOException {
        CompilationResult result = driver.compileForTests(source, outDir, Target.JS);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        Path entry = outDir.resolve("Default.mjs");
        if (!Files.exists(entry)) {
            List<Path> entries;
            try (var s = Files.walk(outDir)) {
                entries = s.filter(p -> p.toString().endsWith(".mjs"))
                        .filter(p -> !p.toString().contains("kof-runtime")).toList();
            }
            assertFalse(entries.isEmpty(), "JS entry module should exist");
            entry = entries.get(0);
        }
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(entry, out,
                java.io.InputStream.nullInputStream(), java.io.OutputStream.nullOutputStream(),
                false, new String[0]);
        return new Run(ec, out.toString(java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim());
    }

    /** Extracts the `checksum=<int>` line — the reproducible property digest. */
    private static String checksum(String output) {
        for (String line : output.split("\n")) {
            if (line.startsWith("checksum=")) {
                return line;
            }
        }
        fail("no checksum line in output: " + output);
        return null;
    }

    @Test
    void propertyHoldsAndPassesOnJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, HOLDS);
        Run r = runJvm(source, tempDir.resolve("out"));
        assertEquals(0, r.exitCode(), () -> "output: " + r.output());
        assertTrue(r.output().contains("PASS addition commutes on random pairs"), () -> "output: " + r.output());
        assertTrue(r.output().contains("0 failed of 1 tests"), () -> "output: " + r.output());
    }

    @Test
    void propertyIsDeterministicAcrossRunsOnJvm(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("Main.kf"), HOLDS);
        Run first = runJvm(tempDir.resolve("Main.kf"), tempDir.resolve("out1"));
        Run second = runJvm(tempDir.resolve("Main.kf"), tempDir.resolve("out2"));
        assertEquals(0, first.exitCode());
        assertEquals(0, second.exitCode());
        assertEquals(checksum(first.output()), checksum(second.output()),
                "same seed must yield the same property digest");
    }

    @Test
    void jsChecksumMatchesJvmBitForBit(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("Main.kf"), HOLDS);
        Run jvm = runJvm(tempDir.resolve("Main.kf"), tempDir.resolve("outjvm"));
        Run js = runJs(tempDir.resolve("Main.kf"), tempDir.resolve("outjs"));
        assertEquals(0, jvm.exitCode());
        assertEquals(0, js.exitCode());
        assertEquals(checksum(jvm.output()), checksum(js.output()),
                "rng parity: same seed => same bits on JVM and JS");
    }

    @Test
    void nativeChecksumMatchesJvm(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("Main.kf"), HOLDS);
        Run jvm = runJvm(tempDir.resolve("Main.kf"), tempDir.resolve("outjvm"));
        Run nat = runNative(tempDir.resolve("Main.kf"), tempDir.resolve("outnat"));
        assertEquals(0, nat.exitCode(), () -> "output: " + nat.output());
        assertEquals(checksum(jvm.output()), checksum(nat.output()),
                "rng parity: same seed => same bits on JVM and Native x86-64");
    }

    @Test
    void falsifiablePropertyFailsWithExitCodeOnJvm(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("Main.kf"), FALSIFIABLE);
        Run r = runJvm(tempDir.resolve("Main.kf"), tempDir.resolve("out"));
        assertEquals(1, r.exitCode(), () -> "output: " + r.output());
        assertTrue(r.output().contains("FAIL all draws are below 50: a>=50 at seed=7"), () -> "output: " + r.output());
        assertTrue(r.output().contains("1 failed of 1 tests"), () -> "output: " + r.output());
        assertFalse(r.output().contains("Exception in thread"), () -> "output: " + r.output());
    }

    @Test
    void falsifiablePropertyFailsOnJs(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("Main.kf"), FALSIFIABLE);
        Run r = runJs(tempDir.resolve("Main.kf"), tempDir.resolve("out"));
        assertEquals(1, r.exitCode(), () -> "output: " + r.output());
        assertTrue(r.output().contains("FAIL all draws are below 50: a>=50 at seed=7"), () -> "output: " + r.output());
    }

    /** Edge: a property whose loop body never executes still PASSes (vacuous truth). */
    @Test
    void zeroIterationPropertyPassesVacuously(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("Main.kf"), """
                test "vacuous property" {
                    rng.seed(1)
                    var i = 0
                    while (i < 0) {
                        assert(false, "never reached")
                        i = i + 1
                    }
                }
                """);
        Run r = runJvm(tempDir.resolve("Main.kf"), tempDir.resolve("out"));
        assertEquals(0, r.exitCode(), () -> "output: " + r.output());
        assertTrue(r.output().contains("PASS vacuous property"), () -> "output: " + r.output());
    }
}
