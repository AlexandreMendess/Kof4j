package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** The §667 Boundary's last small silent surface: `KofWeb.contextCall`
 *  checked ARITY only — bare `status("a", "b")` bound
 *  `kof_web_status(Int, String)` with two Strings (JVM VerifyError) and
 *  bare `param(401)` bound `kof_web_param(String)` with an Int. The
 *  members now type-guard (the table's own `isString`, INT+STR for
 *  status); a known context name with no matching overload falls to the
 *  §667 SEM025 diagnostic. */
class WebContextTypeGuardDiagnosticTest {

    @TempDir
    Path tmp;

    private String diagOf(String tag, String src) throws Exception {
        Path file = tmp.resolve(tag + "/Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, src);
        return String.join("\n", new CompilerDriver()
                .compile(file, tmp.resolve("out-" + tag), Target.JVM)
                .diagnostics().getDiagnostics().stream().map(Object::toString).toList());
    }

    @Test
    void statusWithTwoStringsIsDiagnosed() throws Exception {
        String d = diagOf("statusSS", "main() { status(\"a\", \"b\") }");
        assertTrue(d.contains("SEM025"), "esperava SEM025, veio: " + d);
    }

    @Test
    void paramWithIntArgIsDiagnosed() throws Exception {
        String d = diagOf("paramInt", "main() { var s = param(401)  println(s) }");
        assertTrue(d.contains("SEM025"), "esperava SEM025, veio: " + d);
    }

    @Test
    void sseWithIntArgIsDiagnosed() throws Exception {
        String d = diagOf("sseInt", "main() { var s = sse(401)  println(s) }");
        assertTrue(d.contains("SEM025"), "esperava SEM025, veio: " + d);
    }
}
