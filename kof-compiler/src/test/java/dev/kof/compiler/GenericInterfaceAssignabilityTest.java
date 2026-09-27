package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #400 — `class IntToString implements Converter<Int, String>` then
 * `val cv: Converter<Int, String> = c` was rejected with SEM021 even though
 * the assignment is statically valid. Root cause: `parseTypeRef` stores the
 * implements entry WITH the type arguments ("Converter<Int, String>") while
 * the nominal BFS (SG-009) compared it against the bare name "Converter" —
 * the interface was never found. Fix: the BFS erases the declaration-site
 * `<...>` before comparing (same erasure face the JDK uses; precedent
 * MemberResolver:356). The fine-grained type-ARGUMENT check between distinct
 * instantiations is SG-013/#401's own queue — this unit only turns the false
 * rejection into the correct acceptance.
 */
class GenericInterfaceAssignabilityTest {

    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compile(Path tempDir, String name, String program, Target t) throws Exception {
        Path source = tempDir.resolve(name + ".kf");
        Files.writeString(source, program);
        return driver.compile(source, tempDir.resolve("out-" + name + t), t);
    }

    private static final String VERBATIM = """
            interface Converter<A, B> {
                convert(input: A): B
            }
            class IntToString implements Converter<Int, String> {
                convert(input: Int): String { return input.toString() }
            }
            main() {
                val c: IntToString = IntToString()
                println(c.convert(42))
                val cv: Converter<Int, String> = c
                println(cv.convert(99))
            }
            """;

    @Test
    void verbatimAssignToGenericInterfaceCompiles(@TempDir Path tempDir) throws Exception {
        CompilationResult r = compile(tempDir, "V", VERBATIM, Target.JVM);
        assertTrue(r.success(), "#400 verbatim: valid implements must assign (old code: SEM021 false positive): "
                + r.diagnostics().getDiagnostics());
        // SCOPED HONESTLY: the runtime dispatch through `cv.convert(99)` still
        // dies with NoSuchMethodError (Converter.convert(Object) bridge never
        // emitted) — measured 17/09: `42` then `NoSuchMethodError:
        // 'java.lang.Object Converter.convert(java.lang.Object)'`. That is
        // §271 / Cluster A (erasure+bridge modeling in the 4 emitters, rule 6),
        // NOT this unit. Do not assert run until §271 lands — asserting the
        // crash would document the bug as expected (freeze rule 4).
    }

    @Test
    void inheritedGenericInterfaceThroughSuperclass(@TempDir Path tempDir) throws Exception {
        // BFS leg 2: Sub has no direct implements — the ANCESTOR's generic
        // entry must still resolve (superClass + interfaces both carry <...>).
        CompilationResult r = compile(tempDir, "H", """
                interface Runner<T> {
                    run(item: T): Int
                }
                class Base implements Runner<Int> {
                    run(item: Int): Int { return item + 1 }
                }
                class Sub extends Base {
                }
                main() {
                    var s = Sub()
                    var rv: Runner<Int> = s
                    println(rv.run(41))
                }
                """, Target.JVM);
        assertTrue(r.success(), "implements inherited through superclass must assign: "
                + r.diagnostics().getDiagnostics());
    }

    @Test
    void unrelatedClassStillRejectedSem021(@TempDir Path tempDir) throws Exception {
        // control (SG-009 must NOT loosen): no implements anywhere.
        CompilationResult r = compile(tempDir, "N", """
                interface Runner<T> {
                    run(item: T): Int
                }
                class Unrelated {
                    run(item: Int): Int { return item }
                }
                main() {
                    var u = Unrelated()
                    var rv: Runner<Int> = u
                    println(rv.run(1))
                }
                """, Target.JVM);
        assertFalse(r.success(), "a class that never implements the interface stays rejected");
        assertTrue(r.diagnostics().getDiagnostics().toString().contains("SEM021"),
                "must still be SEM021: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void nonGenericImplementsPathUnchanged(@TempDir Path tempDir) throws Exception {
        // control (freeze rule 2): the plain (no type args) nominal path.
        CompilationResult r = compile(tempDir, "P", """
                interface Speak {
                    speak(): String
                }
                class Dog implements Speak {
                    speak(): String { return "woof" }
                }
                main() {
                    var d = Dog()
                    var s: Speak = d
                    println(s.speak())
                }
                """, Target.JVM);
        assertTrue(r.success(), "plain interface implements keeps working: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void acceptanceIsUniversalAcrossTargets(@TempDir Path tempDir) throws Exception {
        // rule 5: the fix is in the shared semantic pass — the verdict gate is
        // one for all targets (NATIVE positive codegen rides the full suite;
        // precedent ReduceSeedArityTest only pins negatives across the three).
        for (Target t : new Target[]{Target.JVM, Target.JS}) {
            CompilationResult r = compile(tempDir, "U", VERBATIM, t);
            assertTrue(r.success(), t + ": #400 verbatim must compile on every target: "
                    + r.diagnostics().getDiagnostics());
        }
    }

    // ---- §271 — runtime dispatch through the generic interface (bridge) ----

    private String runJvm(Path tempDir, String source) throws IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compile failed: " + result.diagnostics().getDiagnostics());
        try {
            Path runnerDir = tempDir.resolve("run-" + System.nanoTime());
            Files.createDirectories(runnerDir);
            Path runnerSrc = runnerDir.resolve("Run.java");
            Files.writeString(runnerSrc, """
                public class Run {
                    public static void main(String[] args) throws Exception {
                        Class.forName(args[0]).getMethod("main", String[].class)
                            .invoke(null, (Object) new String[0]);
                    }
                }
                """);
            Process pCompile = new ProcessBuilder(TestJdk.javacBin(), "-d", runnerDir.toString(), runnerSrc.toString()).start();
            assertEquals(0, pCompile.waitFor());
            Process p = new ProcessBuilder(TestJdk.javaBin(),
                    "-cp", outDir.toString() + java.io.File.pathSeparator + runnerDir.toString(), "Run", "Default.Main")
                    .redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "JVM exit code " + ec + ", output: " + output);
            return output;
        } catch (InterruptedException e) {
            throw new IOException("interrupted", e);
        }
    }

    @Test
    void genericInterfaceDispatchRunsOnJvm(@TempDir Path tempDir) throws Exception {
        String out = runJvm(tempDir, VERBATIM);
        assertEquals("42\n99", out,
                "§271: dispatch through Converter<Int,String> must reach the impl (erased bridge)");
    }

    @Test
    void genericInterfaceDispatchScriptAndJsParity(@TempDir Path tempDir) throws Exception {
        // Script/JS are dynamic and dispatch correctly; the Native face (§483)
        // is proven by genericInterfaceDispatchRunsOnNative below.
        Path src = tempDir.resolve("P.kf");
        Files.writeString(src, VERBATIM);
        KofInterpreter.Result ir = driver.interpret(java.util.List.of(src), src.getParent(), new String[0]);
        assertEquals(0, ir.exitCode(), "Script: " + ir.stdout() + " " + ir.stderr());
        assertEquals("42\n99", ir.stdout().trim(), "Script");
        Path jsOut = tempDir.resolve("P-js");
        assertTrue(driver.compile(src, jsOut, Target.JS).success(), "JS compile");
        Process jp = new ProcessBuilder(TestJdk.onPath("node"), jsOut.resolve("Default.mjs").toString())
                .redirectErrorStream(true).start();
        String jout = new String(jp.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, jp.waitFor(), "JS exit, output: " + jout);
        assertEquals("42\n99", jout, "JS");
    }

    @Test
    void inheritedGenericInterfaceDispatchRunsOnJvm(@TempDir Path tempDir) throws Exception {
        String out = runJvm(tempDir, """
                interface Runner<T> {
                    run(item: T): Int
                }
                class Base implements Runner<Int> {
                    run(item: Int): Int { return item + 1 }
                }
                class Sub extends Base {
                }
                main() {
                    var s = Sub()
                    var rv: Runner<Int> = s
                    println(rv.run(41))
                }
                """);
        assertEquals("42", out, "§271: bridge through the superclass chain");
    }

    private String runNative(Path tempDir, String source) throws IOException {
        Path file = tempDir.resolve("N-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("outn-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.NATIVE);
        assertTrue(result.success(), "native compile failed: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        assertTrue(Files.exists(bin), "ELF produced");
        try {
            Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "native exit " + ec + ", output: " + output);
            return output;
        } catch (InterruptedException e) {
            throw new IOException("interrupted", e);
        }
    }

    @Test
    void genericInterfaceDispatchRunsOnNative(@TempDir Path tempDir) throws Exception {
        assertEquals("42\n99", runNative(tempDir, VERBATIM),
                "§483: the erased bridge must occupy the interface vtable slot on Native");
    }

    @Test
    void inheritedGenericInterfaceDispatchRunsOnNative(@TempDir Path tempDir) throws Exception {
        assertEquals("42", runNative(tempDir, """
                interface Runner<T> {
                    run(item: T): Int
                }
                class Base implements Runner<Int> {
                    run(item: Int): Int { return item + 1 }
                }
                class Sub extends Base {
                }
                main() {
                    var s = Sub()
                    var rv: Runner<Int> = s
                    println(rv.run(41))
                }
                """), "§483: erased bridge through the superclass chain on Native");
    }
}
