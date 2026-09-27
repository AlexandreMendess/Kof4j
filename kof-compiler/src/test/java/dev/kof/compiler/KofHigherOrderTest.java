package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class KofHigherOrderTest {
    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void mapFilterReduceJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
                main() {
                    var nums = listOf(1, 2, 3, 4)

                    var doubled = nums.map((x: Int) -> x * 2)
                    assert(doubled.get(0) == 2)
                    assert(doubled.get(3) == 8)
                    println(doubled.size())

                    var evens = nums.filter((x: Int) -> x % 2 == 0)
                    assert(evens.size() == 2)
                    assert(evens.get(0) == 2)
                    println(evens.size())

                    var sum = nums.reduce(0, (acc: Int, x: Int) -> acc + x)
                    assert(sum == 10)
                    println(sum)
                }
                """, "4\n2\n10");
    }

    @Test
    void mapStringsJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
                main() {
                    var names = listOf("ana", "bob")
                    var caps = names.map((s: String) -> s.toUpperCase())
                    println(caps.get(0))
                    println(caps.get(1))
                }
                """, "ANA\nBOB");
    }

    @Test
    void chainedJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
                main() {
                    var nums = listOf(1, 2, 3, 4, 5, 6)
                    var sq = nums.filter((x: Int) -> x % 2 == 0).map((x: Int) -> x * x)
                    // evens: 2,4,6 → squares 4,16,36
                    assert(sq.size() == 3)
                    assert(sq.get(2) == 36)
                    println("ok")
                }
                """, "ok");
    }

    @Test
    void exceptionInLambdaIsCaughtJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
                main() {
                    var nums = listOf(1, 2, 3)
                    try {
                        var m = nums.map((x: Int) -> { if (x == 2) { throw "map-bad" } return x })
                        println("no map throw")
                    } catch (String e) { println("caught: " + e) }
                    try {
                        var f = nums.filter((x: Int) -> { if (x == 2) { throw "filter-bad" } return true })
                        println("no filter throw")
                    } catch (String e) { println("caught: " + e) }
                    try {
                        var r = nums.reduce(0, (acc: Int, x: Int) -> { if (x == 2) { throw "reduce-bad" } return acc + x })
                        println("no reduce throw")
                    } catch (String e) { println("caught: " + e) }
                    println("done")
                }
                """, "caught: map-bad\ncaught: filter-bad\ncaught: reduce-bad\ndone");
    }

    @Test
    void exceptionInLambdaJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
                main() {
                    var nums = listOf(1, 2, 3)
                    try {
                        var m = nums.map((x: Int) -> { if (x == 2) { throw "map-bad" } return x })
                        println("no map throw")
                    } catch (String e) { println("caught: " + e) }
                    try {
                        var f = nums.filter((x: Int) -> { if (x == 2) { throw "filter-bad" } return true })
                        println("no filter throw")
                    } catch (String e) { println("caught: " + e) }
                    try {
                        var r = nums.reduce(0, (acc: Int, x: Int) -> { if (x == 2) { throw "reduce-bad" } return acc + x })
                        println("no reduce throw")
                    } catch (String e) { println("caught: " + e) }
                    println("done")
                }
                """, "caught: map-bad\ncaught: filter-bad\ncaught: reduce-bad\ndone");
    }

    @Test
    void exceptionInLambdaNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, """
                main() {
                    var nums = listOf(1, 2, 3)
                    try {
                        var m = nums.map((x: Int) -> { if (x == 2) { throw "map-bad" } return x })
                        println("no map throw")
                    } catch (String e) { println("caught: " + e) }
                    try {
                        var f = nums.filter((x: Int) -> { if (x == 2) { throw "filter-bad" } return true })
                        println("no filter throw")
                    } catch (String e) { println("caught: " + e) }
                    try {
                        var r = nums.reduce(0, (acc: Int, x: Int) -> { if (x == 2) { throw "reduce-bad" } return acc + x })
                        println("no reduce throw")
                    } catch (String e) { println("caught: " + e) }
                    println("done")
                }
                """, "caught: map-bad\ncaught: filter-bad\ncaught: reduce-bad\ndone");
    }

    @Test
    void mapFilterJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
                main() {
                    var nums = listOf(1, 2, 3, 4)
                    println(nums.map((x: Int) -> x * 3).get(1))
                    println(nums.filter((x: Int) -> x > 2).size())
                    println(nums.reduce(0, (acc: Int, x: Int) -> acc + x))
                }
                """, "6\n2\n10");
    }

    @Test
    void mapNativeGap(@TempDir Path tmp) throws Exception {
        runNative(tmp, """
                main() {
                    var nums = listOf(1, 2, 3, 4)
                    var doubled = nums.map((x: Int) -> x * 2)
                    println(doubled.get(1))
                    println(nums.filter((x: Int) -> x > 2).size())
                    println(nums.reduce(0, (acc: Int, x: Int) -> acc + x))
                }
                """, "4\n2\n10");
    }

    // ── helpers ──
    private String runJvm(Path tempDir, String source, String expected) throws java.io.IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compile failed: " + result.diagnostics().getDiagnostics());
        try {
            Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                    "-cp", outDir.toString(), "Default.Main").redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "JVM exit code, output: " + output);
            assertEquals(expected, output, "JVM output");
            return output;
        } catch (InterruptedException e) {
            throw new java.io.IOException("interrupted", e);
        }
    }

    private String runJs(Path tempDir, String source, String expected) throws java.io.IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JS);
        assertTrue(result.success(), "JS compile failed: " + result.diagnostics().getDiagnostics());
        try (java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream()) {
            int ec = dev.kof.runtime.KofJsRunner.run(findJsEntry(outDir), buf,
                    java.io.InputStream.nullInputStream(), new java.io.ByteArrayOutputStream());
            String output = buf.toString(java.nio.charset.StandardCharsets.UTF_8).trim();
            assertEquals(0, ec, "JS exit code, output: " + output);
            assertEquals(expected, output, "JS output");
            return output;
        }
    }

    private String runNative(Path tempDir, String source, String expected) throws java.io.IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.NATIVE);
        assertTrue(result.success(), "Native compile failed: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        try {
            Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Native exit code, output: " + output);
            assertEquals(expected, output, "Native output");
            return output;
        } catch (InterruptedException e) {
            throw new java.io.IOException("interrupted", e);
        }
    }

    private static Path findJsEntry(Path dir) throws java.io.IOException {
        try (var s = Files.walk(dir)) {
            var opt = s.filter(p -> p.getFileName().toString().equals("Default.mjs")).findFirst();
            if (opt.isPresent()) return opt.get();
        }
        try (var s = Files.walk(dir)) {
            return s.filter(p -> p.toString().endsWith(".mjs"))
                    .findFirst().orElseThrow(() -> new java.io.IOException("no .mjs in " + dir));
        }
    }
}
