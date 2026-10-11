package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URLClassLoader;

import static org.junit.jupiter.api.Assertions.*;

/** Audit 0.6.0 JVM-02: a function-type local WITHOUT an initializer was
 *  defaulted to `iconst_0` on the JVM (`iconst_0; astore_1`) — `check`
 *  passed and the class died at load with
 *  {@code VerifyError: Bad type on operand stack ... integer is not
 *  assignable to reference type}. A function-typed slot holds a synthetic
 *  lambda class (a reference): the no-init default is {@code null}, in
 *  both the plain path (StatementLowererLocalBoxing) and the captured-var
 *  box path (CapturedVarBox, which hardcoded INT for the box's
 *  uninitialized default). */
class FunctionTypeNoInitDefaultE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(String tag, String program) throws Exception {
        Path root = tmp.resolve(tag);
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), program);
        Path out = root.resolve("out");
        CompilationResult result = driver.compile(root.resolve("Main.kf"), out, Target.JVM);
        assertTrue(result.success(), () -> "compile: " + result.diagnostics().getDiagnostics());
        var stdout = new ByteArrayOutputStream();
        var previousOut = System.out;
        try {
            System.setOut(new java.io.PrintStream(stdout, true, StandardCharsets.UTF_8));
            try (var loader = new URLClassLoader(
                    new java.net.URL[]{out.toUri().toURL()}, getClass().getClassLoader())) {
                Class.forName("Default.Main", true, loader)
                        .getMethod("main", String[].class)
                        .invoke(null, (Object) new String[0]);
            }
        } finally {
            System.setOut(previousOut);
        }
        return stdout.toString(StandardCharsets.UTF_8).trim();
    }

    @Test
    void auditReproRunsAndPrintsSix() throws Exception {
        String out = runJvm("plain", """
            main() { var f: (Int) -> Int
                     f = (x: Int) -> x + 1
                     println(f(5)) }
            """);
        assertEquals("6", out, "the audit's exact repro must run, not VerifyError at load");
    }

    // NOT pinned here: the CAPTURED twin (a mutated function-type local also
    // captured by a closure) routes through CapturedVarBox, whose uninitialized
    // box still hardcodes INT — and fixing the box alone then exposes the
    // deeper captured-call dispatch gap (Object vs the declared return type).
    // That is the lambda/box machinery family of audit JVM-01/03, a separate
    // medium-risk unit; §664's Boundary records it.
}
