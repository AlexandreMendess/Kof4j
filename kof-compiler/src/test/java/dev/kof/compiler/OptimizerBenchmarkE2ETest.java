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
                    """, "328350"));

    @Test
    void benchmarkSuiteRunsAndReportsMetrics(@TempDir Path dir) throws Exception {
        StringBuilder report = new StringBuilder("\n=== Phase F benchmark (JVM) ===\n");
        for (Bench b : BENCHES) {
            Path src = dir.resolve(b.name() + ".kf");
            Files.writeString(src, b.source());
            Path out = dir.resolve(b.name() + "-out");

            CompilerDriver driver = new CompilerDriver();
            IRModule[] unopt = new IRModule[1];
            IRModule[] opt = new IRModule[1];
            driver.irObserver = (u, o) -> { unopt[0] = u; opt[0] = o; };

            CompilationResult r = driver.compile(src, out, Target.JVM);
            assertTrue(r.success(), b.name() + " compile failed: " + r.diagnostics().getDiagnostics());

            // Correctness half: run and assert the output.
            long start = System.nanoTime();
            String output = runJvm(out);
            long millis = (System.nanoTime() - start) / 1_000_000;
            assertEquals(b.expected(), output, b.name() + " output mismatch");

            // Measured half: the optimizer's effect on the instruction metrics.
            IrMetrics before = IrMetrics.of(flatten(unopt[0]));
            IrMetrics after = IrMetrics.of(flatten(opt[0]));
            report.append(String.format("%-12s [%-10s] instr %d -> %d  loads %d -> %d  stores %d -> %d  (%d ms)%n",
                    b.name(), b.category(),
                    before.instructions(), after.instructions(),
                    before.loads(), after.loads(),
                    before.stores(), after.stores(), millis));
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
        ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "JVM exit code, output: " + output);
        return output;
    }
}
