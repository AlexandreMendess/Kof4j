package dev.kof.cli;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 8.3 residual — `kof profile --methods`: the in-house method-level SAMPLING
 * profiler of the JVM, built on the JVM's own JFR (`jdk.jfr`, part of the JDK —
 * no external tool). The compiler's LineNumberTable maps the bytecode back to
 * the `.kf`, so the hot Kof function and its source line are shown, never raw
 * bytecode. The JS face uses the Node CPU profiler (`--cpu-prof`, part of
 * Node) with the emitted `.mjs.map` mapping the sampled JS line back to the
 * `.kf`; Native is an honest refusal naming perf (R6/R7).
 */
class ProfileMethodsTest {

    private record Cli(int exit, String out) {}

    private static Cli cli(Path workDir, String... args) throws Exception {
        return cli(workDir, Map.of(), args);
    }

    private static Cli cli(Path workDir, Map<String, String> env, String... args) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add("dev.kof.cli.Main");
        cmd.addAll(List.of(args));
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(workDir.toFile());
        pb.environment().putAll(env);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(180, TimeUnit.SECONDS), "timeout\n" + out);
        return new Cli(p.exitValue(), out);
    }

    private static String hotProgram() {
        return """
                Int spin(Int n) {
                    var s = 0
                    var i = 0
                    while (i < n) {
                        s = s + i * i
                        i = i + 1
                    }
                    return s
                }

                main() {
                    var t = 0
                    var rounds = 0
                    while (rounds < 4000) {
                        t = t + spin(100000)
                        rounds = rounds + 1
                    }
                    println("done " + t)
                }
                """;
    }

    @Test
    void hotKofFunctionAppearsInTheJfrMethodProfile(@TempDir Path dir) throws Exception {
        Assumptions.assumeTrue(jdk.jfr.FlightRecorder.isAvailable(),
                "JFR not available on this JVM — the JVM sampling face is CI/host-gated");
        Files.writeString(dir.resolve("Hot.kf"), hotProgram());
        Cli r = cli(dir, "profile", "Hot.kf", "--methods");
        assertEquals(0, r.exit(), "profile --methods exit:\n" + r.out());
        assertTrue(r.out().contains("hot methods"), "JFR section missing:\n" + r.out());
        assertTrue(r.out().contains("spin"),
                "the hot Kof function must appear in the method profile:\n" + r.out());
        assertTrue(r.out().contains("(line "),
                "the JVM line must map back to the .kf source line:\n" + r.out());
        assertTrue(r.out().contains("JFR samples"),
                "the sample count must be shown:\n" + r.out());
    }

    @Test
    void withoutMethodsTheReportKeepsTheExternalPointers(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Main.kf"), "main() { println(\"ok\") }\n");
        Cli r = cli(dir, "profile", "Main.kf");
        assertEquals(0, r.exit(), "profile exit:\n" + r.out());
        assertFalse(r.out().contains("hot methods"),
                "no JFR section without --methods:\n" + r.out());
        assertTrue(r.out().contains("--methods"),
                "the footer must point at the new in-house flag:\n" + r.out());
    }

    @Test
    void methodsOnNativeIsAnHonestRefusal(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Main.kf"), "main() { println(\"ok\") }\n");
        Cli r = cli(dir, "profile", "Main.kf", "--target", "native", "--methods");
        assertEquals(1, r.exit(), r.out());
        assertTrue(r.out().contains("perf"),
                "native refusal must name perf (R6/R7):\n" + r.out());
    }

    @Test
    void hotKofFunctionAppearsInTheNodeCpuProfile(@TempDir Path dir) throws Exception {
        Assumptions.assumeTrue(nodeAvailable(), "node not on PATH — the JS face is host-gated");
        Files.writeString(dir.resolve("Hot.kf"), hotProgram());
        Cli r = cli(dir, "profile", "Hot.kf", "--target", "js", "--methods");
        assertEquals(0, r.exit(), "profile --methods js exit:\n" + r.out());
        assertTrue(r.out().contains("hot methods"), "Node CPU-profile section missing:\n" + r.out());
        assertTrue(r.out().contains("spin"),
                "the hot Kof function must appear in the JS method profile:\n" + r.out());
        assertTrue(r.out().contains("(line "),
                "the sampled JS line must map back to the .kf source line:\n" + r.out());
        assertTrue(r.out().contains("Node samples"),
                "the sample count must name the Node profiler, not JFR:\n" + r.out());
        assertTrue(r.out().contains("Node CPU profiler"),
                "the footer must name the in-house JS profiler:\n" + r.out());
    }

    @Test
    void methodsOnJsWithoutNodeFailsHonestly(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Main.kf"), "main() { println(\"ok\") }\n");
        Cli r = cli(dir, Map.of("PATH", "/nonexistent"), "profile", "Main.kf", "--target", "js", "--methods");
        assertEquals(1, r.exit(), "a missing Node must fail, not fake a profile:\n" + r.out());
        assertTrue(r.out().contains("node was not found"),
                "the failure must name the missing Node (R6):\n" + r.out());
    }

    private static boolean nodeAvailable() {
        String path = System.getenv("PATH");
        if (path == null) {
            return false;
        }
        for (String dir : path.split(java.io.File.pathSeparator)) {
            if (!dir.isEmpty() && Files.isExecutable(Path.of(dir, "node"))) {
                return true;
            }
        }
        return false;
    }
}
