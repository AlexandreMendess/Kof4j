package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Audit 0.6.0 HTTP-01: `http.status(401)` matched NO kof.http overload
 *  (the client form is `status(String url) -> Int`), but the namespace
 *  typer branch silently returned `Type.UnknownType.UNKNOWN` — `check`
 *  printed "no errors" and the lowering invented a phantom
 *  `invokestatic Default/Main.status(I)` (JVM VerifyError). Root (measured):
 *  `KofHttp.staticCall` bound by ARITY ALONE — `status(401)` bound
 *  `kof_http_status(String)` with an Int argument (the MATH-02 shape). The
 *  overloads now carry type guards (isStr/isInt, R6); a known member with no
 *  matching overload falls to the EXISTING namespace resolution diagnostic
 *  (SEM025 "Cannot resolve method ... on namespace 'http'"). The SERVER
 *  `status(401, "body")` face lives in kof.web (KofWeb), untouched here. */
class HttpNamespaceOverloadDiagnosticTest {

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
    void statusWithIntArgIsDiagnosed() throws Exception {
        String d = diagOf("status401", "main() { var s = http.status(401)  println(s) }");
        assertTrue(d.contains("SEM025"), "esperava SEM025, veio: " + d);
        assertTrue(d.contains("'status' on namespace 'http'"),
                "a mensagem deve nomear o membro e o namespace: " + d);
    }

    @Test
    void getWithIntArgIsDiagnosed() throws Exception {
        String d = diagOf("get401", "main() { var s = http.get(401)  println(s) }");
        assertTrue(d.contains("SEM025"), "esperava SEM025, veio: " + d);
    }

    @Test
    void timeoutWithStringArgIsDiagnosed() throws Exception {
        String d = diagOf("timeoutStr", "main() { http.timeout(\"abc\") }");
        assertTrue(d.contains("SEM025"), "esperava SEM025, veio: " + d);
    }

    @Test
    void validFacesStayClean() throws Exception {
        assertTrue(diagOf("getOk", "main() { var s = http.get(\"https://x\")  println(s) }").isEmpty(),
                "http.get(String) keeps compiling");
        assertTrue(diagOf("statusOk", "main() { var s = http.status(\"https://x\")  println(s) }").isEmpty(),
                "http.status(String) keeps compiling");
    }
}
