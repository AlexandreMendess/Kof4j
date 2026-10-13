package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * known-bugs §696 — a {@code Byte}/{@code Short} LOCAL, ASSIGNMENT or RETURN
 * holding an OUT-OF-RANGE value was accepted CLEAN and then DIVERGED FOUR
 * WAYS: the Script narrowed ({@code -56}), native/JS kept the raw {@code 200},
 * and the JVM CRASHED at runtime ({@code ArrayIndexOutOfBoundsException: Index
 * 328 out of bounds for length 256} inside {@code java.lang.Byte.valueOf}).
 *
 * <p>Fix (maintainer-decided): the STORE narrows, so {@code var x: Byte = 200}
 * behaves like {@code (200 as Byte)} — the semantics §695 already gave the
 * cast, on all four targets. Oracle = the JDK narrowing semantics (Q3 edges:
 * exact limits, overflow both signs, a wider source, reassignment and return).
 */
class ByteShortStoreNarrowE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String SOURCE = """
            b(): Byte { return 200 }
            s(): Short { return 40000 }
            main() {
                var x: Byte = 200
                println(x)
                var y: Short = 40000
                println(y)
                var z: Byte = 0
                z = 200
                println(z)
                println(b())
                println(s())
                var lo: Byte = 127
                println(lo)
                var hi: Byte = 128
                println(hi)
                var neg: Byte = 0 - 129
                println(neg)
                var n = 200
                var fromInt: Byte = n
                println(fromInt)
                var cb: Byte = 1
                cb += 200
                println(cb)
                var cs: Short = 1
                cs += 40000
                println(cs)
            }
            """;

    // 200->-56, 40000->-25536, 200->-56, b()->-56, s()->-25536,
    // 127->127, 128->-128, -129->127, 200->-56
    private static final String EXPECTED =
            "-56\n-25536\n-56\n-56\n-25536\n127\n-128\n127\n-56\n-55\n-25535";

    private String runJvm(Path outDir) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(TestJdk.javaBin(),
                "-cp", outDir.toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "JVM exit code, output: " + out);
        return out;
    }

    @Test
    void byteShortStoreNarrowsOnJvmScriptAndJs(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("bs.kf");
        Files.writeString(src, SOURCE);
        Path jvm = tmp.resolve("bs-jvm");
        CompilationResult rj = driver.compile(src, jvm, Target.JVM);
        assertTrue(rj.success(), () -> "JVM compile: " + rj.diagnostics().getDiagnostics());
        assertEquals(EXPECTED, runJvm(jvm), "§696: o JVM estourava o cache do Byte.valueOf");

        KofInterpreter.Result i = driver.interpret(java.util.List.of(src), src.getParent(), new String[0]);
        assertEquals(0, i.exitCode(), "Script exit/stderr: " + i.stdout() + " " + i.stderr());
        assertEquals(EXPECTED, i.stdout().trim(), "Script");

        Path js = tmp.resolve("bs-js");
        CompilationResult rjs = driver.compile(src, js, Target.JS);
        assertTrue(rjs.success(), () -> "JS compile: " + rjs.diagnostics().getDiagnostics());
        ProcessBuilder jb = new ProcessBuilder(TestJdk.onPath("node"),
                js.resolve("Default.mjs").toString());
        jb.redirectErrorStream(true);
        Process jp = jb.start();
        String jout = new String(jp.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, jp.waitFor(), "JS exit, output: " + jout);
        assertEquals(EXPECTED, jout, "JS");
    }

    @Test
    void byteShortStoreNarrowsOnNative(@TempDir Path tmp) throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                dev.kof.compiler.nat.NativeToolchainGate.present(),
                "x86-64 toolchain (as/ld) ausente — pulando");
        Path src = tmp.resolve("bsn.kf");
        Files.writeString(src, SOURCE);
        Path nat = tmp.resolve("bsn-native");
        CompilationResult rn = driver.compile(src, nat, Target.NATIVE);
        assertTrue(rn.success(), () -> "native compile: " + rn.diagnostics().getDiagnostics());
        ProcessBuilder nb = new ProcessBuilder(nat.resolve("Default/Main").toString());
        nb.redirectErrorStream(true);
        Process p = nb.start();
        String out = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "native exit, output: " + out);
        assertEquals(EXPECTED, out, "§696: o nativo mantinha o valor cru");
    }
}
