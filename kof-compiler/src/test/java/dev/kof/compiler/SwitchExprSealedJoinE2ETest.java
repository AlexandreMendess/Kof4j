package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for issue #819 (1.0-blocks): a switch expression returning
 * different subclasses of a sealed hierarchy must join to their common supertype
 * (not java.lang.Object), so the enclosing method's areturn is valid and the
 * JVM class loads without VerifyError.
 */
class SwitchExprSealedJoinE2ETest {

    private static final String REPRO = """
            sealed class Base
            class A(Int x) extends Base
            class B(Int x) extends Base
            sealed class Out
            class X(Int v) extends Out
            class Y(Int v) extends Out

            Out converte(Base b) {
                return switch (b) {
                    case A a -> X(a.x())
                    case B bb -> Y(bb.x())
                }
            }
            main() {
                println(converte(A(1)))
                println(converte(B(2)))
            }
            """;

    @Test
    void switchExpressionJoiningSiblingSubclassesLoadsWithoutVerifyError(@TempDir Path tmp)
            throws IOException, InterruptedException {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, REPRO);
        Path out = tmp.resolve("out");
        CompilerDriver driver = new CompilerDriver();
        CompilationResult res = driver.compile(src, out, Target.JVM);
        assertTrue(res.success(), "compile clean: " + res.diagnostics().getDiagnostics());

        // Run the generated class — pre-fix, this fails at Class.forName with
        // java.lang.VerifyError: Bad return type (areturn of Object into Out return)
        Process p = new ProcessBuilder(
                System.getProperty("java.home") + "/bin/java",
                "-cp", out.toString() + ":kof-runtime/target/classes",
                "Default.Main")
                .redirectErrorStream(true)
                .start();
        String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        int ec = p.waitFor();
        assertFalse(output.contains("VerifyError"),
                "must not produce VerifyError on class load, was:\n" + output);
        assertEquals(0, ec, "must exit cleanly, output was:\n" + output);
    }
}
