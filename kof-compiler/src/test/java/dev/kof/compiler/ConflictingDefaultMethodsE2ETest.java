package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Issue #610 — a concrete class inheriting two UNRELATED {@code default}
 * methods with the same name and parameter count compiled cleanly and then
 * crashed at class-LOAD with
 * {@code IncompatibleClassChangeError: Conflicting default methods}
 * (the JVM's diamond check, JLS 9.4.1.3). Kof must diagnose it at COMPILE
 * time (R6: never a silent class-load crash), naming the two interfaces and
 * the method, and must keep working when the class overrides the method or
 * when one interface is a subtype of the other (most-specific default wins).
 */
class ConflictingDefaultMethodsE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path outDir) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            assertEquals(0, p.waitFor(), "JVM exit code, output: " + output);
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
    }

    private static String messages(CompilationResult r) {
        return r.diagnostics().getDiagnostics().toString();
    }

    @Test
    void unresolvedDiamondDefaultIsCompileError(@TempDir Path tempDir) throws IOException {
        // verbatim #610: compile must FAIL naming both interfaces + the method.
        Path src = tempDir.resolve("diamond.kf");
        Files.writeString(src, """
                interface Greeter { default String greet() { return "hi from Greeter" } }
                interface Waver { default String greet() { return "hi from Waver" } }
                class Both implements Greeter, Waver {
                }
                main() {
                    println(Both().greet())
                }
                """);
        Path out = tempDir.resolve("diamond-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertFalse(r.success(), "unresolved default diamond must not compile clean (#610): " + messages(r));
        String diag = messages(r);
        assertTrue(diag.contains("greet"), "diagnostic must name the method: " + diag);
        assertTrue(diag.contains("Greeter") && diag.contains("Waver"),
                "diagnostic must name both conflicting interfaces: " + diag);
    }

    @Test
    void explicitOverrideResolvesDiamond(@TempDir Path tempDir) throws IOException {
        // edge: an explicit concrete override resolves the conflict — compiles and runs.
        Path src = tempDir.resolve("override.kf");
        Files.writeString(src, """
                interface Greeter { default String greet() { return "hi from Greeter" } }
                interface Waver { default String greet() { return "hi from Waver" } }
                class Both implements Greeter, Waver {
                    String greet() { return "hi from Both" }
                }
                main() {
                    println(Both().greet())
                }
                """);
        Path out = tempDir.resolve("override-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "explicit override must compile (#610): " + messages(r));
        assertEquals("hi from Both", runJvm(out));
    }

    @Test
    void relatedInterfacesMostSpecificDefaultWins(@TempDir Path tempDir) throws IOException {
        // edge: B extends A — the most specific default (B) wins, no conflict, no error.
        Path src = tempDir.resolve("related.kf");
        Files.writeString(src, """
                interface A { default String greet() { return "A" } }
                interface B extends A { default String greet() { return "B" } }
                class C implements B {
                }
                main() {
                    println(C().greet())
                }
                """);
        Path out = tempDir.resolve("related-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "related defaults must not conflict (#610): " + messages(r));
        assertEquals("B", runJvm(out));
    }

    @Test
    void sameNameDifferentArityIsNotAConflict(@TempDir Path tempDir) throws IOException {
        // edge: same name, different parameter count => no diamond conflict.
        Path src = tempDir.resolve("arity.kf");
        Files.writeString(src, """
                interface A { default String greet() { return "A0" } }
                interface B { default String greet(String who) { return "B:" + who } }
                class C implements A, B {
                }
                main() {
                    println(C().greet())
                    println(C().greet("mel"))
                }
                """);
        Path out = tempDir.resolve("arity-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "same-name different-arity must not conflict (#610): " + messages(r));
        assertEquals("A0\nB:mel", runJvm(out));
    }

    @Test
    void abstractClassDefersConflictToConcreteSubclass(@TempDir Path tempDir) throws IOException {
        // edge (#322): an abstract implementor may leave the conflict unresolved;
        // the concrete subclass that does not override it is the one diagnosed.
        Path src = tempDir.resolve("abstract.kf");
        Files.writeString(src, """
                interface Greeter { default String greet() { return "hi from Greeter" } }
                interface Waver { default String greet() { return "hi from Waver" } }
                abstract class Mid implements Greeter, Waver {
                }
                class Concrete extends Mid {
                }
                main() {
                    println(Concrete().greet())
                }
                """);
        Path out = tempDir.resolve("abstract-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertFalse(r.success(), "concrete subclass must be diagnosed (#610/#322): " + messages(r));
        String diag = messages(r);
        assertTrue(diag.contains("Concrete") && diag.contains("greet"),
                "diagnostic must point at the concrete subclass + method: " + diag);
    }

    @Test
    void arityDispatchCompilesOnNativeAndJsToo(@TempDir Path tempDir) throws IOException {
        // rule 5: the arity dispatch fix lives in the shared typer — the
        // different-arity program must also compile for Native and JS.
        Path src = tempDir.resolve("arity-targets.kf");
        Files.writeString(src, """
                interface A { default String greet() { return "A0" } }
                interface B { default String greet(String who) { return "B:" + who } }
                class C implements A, B {
                }
                main() {
                    println(C().greet())
                    println(C().greet("mel"))
                }
                """);
        for (Target t : new Target[] { Target.NATIVE, Target.JS }) {
            Path out = tempDir.resolve("arity-" + t);
            CompilationResult r = driver.compile(src, out, t);
            assertTrue(r.success(), t + " compile failed (#610b dispatch): " + messages(r));
        }
    }
}
