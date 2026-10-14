package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * known-bugs §664 (the CAPTURED twin of the audit `JVM-02` fix) — a
 * FUNCTION-typed local that is MUTATED inside a closure was boxed with the
 * wrong value type, and the call inside the closure emitted a PHANTOM
 * {@code invokestatic}: the JVM died {@code VerifyError} at the closure's
 * {@code invoke}. The captured box now holds the DECLARED function type (the
 * synthetic interface, so any same-signature lambda fits) and a box-typed
 * function local invokes via that interface.
 *
 * <p>The regression guards: a captured function-typed local only READ, and a
 * captured MUTATED primitive, both worked before and must keep working.
 */
class CapturedFunctionTypeE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String SOURCE = """
            main() {
                var f: (Int) -> Int
                var g = () -> { f = (x: Int) -> { return x * 10 }  return f(2) }
                println(g())
                var h: (Int) -> Int = (x: Int) -> { return x + 1 }
                var k = () -> { h = (x: Int) -> { return x + 100 }  return h(2) }
                println(k())
                var read: (Int) -> Int = (x: Int) -> { return x + 1 }
                var r = () -> { return read(2) }
                println(r())
                var n = 0
                var bump = () -> { n = n + 1  return n }
                println(bump())
                println(bump())
            }
            """;

    // noinit -> 20, init -> 102, read -> 3, bump -> 1, bump -> 2
    private static final String EXPECTED = "20\n102\n3\n1\n2";

    private String runJvm(Path outDir) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(TestJdk.javaBin(),
                "-cp", outDir.toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "JVM exit code, output: " + out);
        return out;
    }

    @Test
    void capturedFunctionTypeLocalMutatedInsideClosure(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("capfn.kf");
        Files.writeString(src, SOURCE);

        Path jvm = tmp.resolve("capfn-jvm");
        CompilationResult rj = driver.compile(src, jvm, Target.JVM);
        assertTrue(rj.success(), () -> "JVM compile: " + rj.diagnostics().getDiagnostics());
        assertEquals(EXPECTED, runJvm(jvm),
                "§664 twin: o local de tipo função capturado+mutado emitia VerifyError");

        KofInterpreter.Result i = driver.interpret(java.util.List.of(src), src.getParent(), new String[0]);
        assertEquals(0, i.exitCode(), "Script exit/stderr: " + i.stdout() + " " + i.stderr());
        assertEquals(EXPECTED, i.stdout().trim(), "Script");

        Path js = tmp.resolve("capfn-js");
        CompilationResult rjs = driver.compile(src, js, Target.JS);
        assertTrue(rjs.success(), () -> "JS compile: " + rjs.diagnostics().getDiagnostics());
        ProcessBuilder jb = new ProcessBuilder(TestJdk.onPath("node"),
                js.resolve("Default.mjs").toString());
        jb.redirectErrorStream(true);
        Process jp = jb.start();
        String jout = new String(jp.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, jp.waitFor(), "JS exit, output: " + jout);
        assertEquals(EXPECTED, jout, "JS");
    }

    @Test
    void capturedFunctionTypeLocalOnNative(@TempDir Path tmp) throws Exception {
        Assumptions.assumeTrue(dev.kof.compiler.nat.NativeToolchainGate.present(),
                "x86-64 toolchain (as/ld) ausente — pulando");
        Path src = tmp.resolve("capfnn.kf");
        Files.writeString(src, SOURCE);
        Path nat = tmp.resolve("capfnn-native");
        CompilationResult rn = driver.compile(src, nat, Target.NATIVE);
        assertTrue(rn.success(), () -> "native compile: " + rn.diagnostics().getDiagnostics());
        ProcessBuilder nb = new ProcessBuilder(nat.resolve("Default/Main").toString());
        nb.redirectErrorStream(true);
        Process p = nb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "native exit, output: " + out);
        assertEquals(EXPECTED, out, "native");
    }
}
