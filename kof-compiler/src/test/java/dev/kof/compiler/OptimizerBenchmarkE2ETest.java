package dev.kof.compiler;

import dev.kof.compiler.backend.IrMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase F/§17 of the assembly-optimization plan: the cross-target benchmark
 * suite (first slice — JVM target).
 *
 * <p>§17 requires each relevant optimization to have a minimal case, expected
 * behavior, and a before/after comparison; Phase F asks for a suite covering
 * arithmetic, loops, branches, calls, etc. This test compiles a small fixture
 * per category to the JVM, runs it (asserting the program's output — the
 * correctness half), and REPORTS the production optimizer's effect on the
 * instruction metrics ({@link IrMetrics}) — the measured half. Timing is printed
 * as an OBSERVATION only (never asserted): smaller assembly is not necessarily
 * faster code (§17), so a wall-clock number is evidence, not a gate.
 */
class OptimizerBenchmarkE2ETest {

    /** One benchmark: a name, a category, the Kof source and its expected output. */
    private record Bench(String name, String category, String source, String expected) {
    }

    private static final List<Bench> BENCHES = List.of(
            new Bench("arithmetic", "arithmetic", """
                    main() {
                        var acc = 0
                        for (var i = 0; i < 200; i++) { acc = acc + i * 3 - 1 }
                        println(acc)
                    }
                    """, "59500"),
            new Bench("nestedLoop", "loops", """
                    main() {
                        var s = 0
                        for (var i = 0; i < 50; i++) {
                            for (var j = 0; j < 50; j++) { s = s + i * j }
                        }
                        println(s)
                    }
                    """, "1500625"),
            new Bench("branches", "branches", """
                    main() {
                        var x = 0
                        for (var i = 0; i < 100; i++) {
                            if (i % 2 == 0) { x = x + 1 } else { x = x + 2 }
                        }
                        println(x)
                    }
                    """, "150"),
            new Bench("calls", "calls", """
                    Int square(Int n) { return n * n }
                    main() {
                        var t = 0
                        for (var i = 0; i < 100; i++) { t = t + square(i) }
                        println(t)
                    }
                    """, "328350"),
            new Bench("constants", "constants", """
                    main() {
                        var a = 2 * 3
                        var b = a + 4 * 5
                        var c = 100
                        c = 7
                        println(b + c)
                    }
                    """, "33"),
            new Bench("bitwise", "bitwise", """
                    main() {
                        var x = 0
                        for (var i = 0; i < 8; i++) { x = x | (1 << i) }
                        println(x)
                    }
                    """, "255"));

    @Test
    void benchmarkSuiteRunsAndReportsMetrics(@TempDir Path dir) throws Exception {
        StringBuilder report = new StringBuilder("\n=== Phase F benchmark ===\n");
        for (Bench b : BENCHES) {
            Path src = dir.resolve(b.name() + ".kf");
            Files.writeString(src, b.source());

            // JVM: correctness (run + assert output) + measured metrics.
            Path outJvm = dir.resolve(b.name() + "-jvm");
            CompilerDriver driver = new CompilerDriver();
            IRModule[] unopt = new IRModule[1];
            IRModule[] opt = new IRModule[1];
            driver.irObserver = (u, o) -> { unopt[0] = u; opt[0] = o; };
            CompilationResult r = driver.compile(src, outJvm, Target.JVM);
            assertTrue(r.success(), b.name() + " JVM compile failed: " + r.diagnostics().getDiagnostics());
            long start = System.nanoTime();
            String jvmOut = runJvm(outJvm);
            long jvmMs = (System.nanoTime() - start) / 1_000_000;
            assertEquals(b.expected(), jvmOut, b.name() + " JVM output mismatch");

            IrMetrics before = IrMetrics.of(flatten(unopt[0]));
            IrMetrics after = IrMetrics.of(flatten(opt[0]));
            report.append(String.format("%-12s [%-10s] JVM instr %d -> %d  loads %d -> %d  stores %d -> %d  (%d ms)%n",
                    b.name(), b.category(),
                    before.instructions(), after.instructions(),
                    before.loads(), after.loads(),
                    before.stores(), after.stores(), jvmMs));

            // Native x86-64: correctness (run + assert output) on the available target.
            Path outNat = dir.resolve(b.name() + "-nat");
            CompilationResult rn = driver.compile(src, outNat, Target.NATIVE);
            if (rn.success()) {
                Path bin = outNat.resolve("Default").resolve("Main");
                assertTrue(Files.exists(bin), b.name() + " native binary missing");
                long nStart = System.nanoTime();
                String natOut = runNative(bin);
                long natMs = (System.nanoTime() - nStart) / 1_000_000;
                assertEquals(b.expected(), natOut, b.name() + " native output mismatch");
                report.append(String.format("%-12s [%-10s] NATIVE runs, matches JVM  (%d ms)%n",
                        b.name(), b.category(), natMs));
            } else {
                report.append(String.format("%-12s [%-10s] NATIVE not built: %s%n",
                        b.name(), b.category(), rn.diagnostics().getDiagnostics()));
            }
        }
        // Observation only — never a gate (§17: smaller assembly is not necessarily faster).
        System.out.println(report);
        assertTrue(report.length() > 0);
    }

    private static List<KofOperation> flatten(IRModule module) {
        java.util.ArrayList<KofOperation> ops = new java.util.ArrayList<>();
        for (IRClass c : module.classes()) {
            for (IRMethod m : c.methods()) {
                for (IRBasicBlock blk : m.basicBlocks()) ops.addAll(blk.operations());
            }
        }
        return ops;
    }

    private static String runJvm(Path outDir) throws Exception {
        return run(new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main"), "JVM");
    }

    private static String runNative(Path bin) throws Exception {
        return run(new ProcessBuilder(bin.toString()), "NATIVE");
    }

    private static String run(ProcessBuilder pb, String tag) throws Exception {
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), tag + " exit code, output: " + output);
        return output;
    }
}
