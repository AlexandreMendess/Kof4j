package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** §665 Boundary item 1: bare `status(401)` compiled SILENTLY —
 *  `isTopLevelCandidate` excludes web context-function names from the
 *  top-level resolution (and from SEM015), and the context branch only
 *  typed when `KofWeb.contextCall(name, argc)` matched (`status` is
 *  arity-2 only). A known context name with no matching arity now falls
 *  to the namespace-resolution diagnostic (SEM025). The VALID arity-2
 *  server face is untouched, and truly-unknown names keep the honest
 *  SEM015. */
class WebContextNoMatchDiagnosticTest {

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
    void bareStatusWithIntArgIsDiagnosed() throws Exception {
        String d = diagOf("status401", "main() { status(401) }");
        assertTrue(d.contains("SEM025"), "esperava SEM025, veio: " + d);
        assertTrue(d.contains("'status'"), "a mensagem deve nomear o membro: " + d);
    }

    @Test
    void unknownNamesKeepSem015() throws Exception {
        String d = diagOf("nope1", "main() { nope(1) }");
        assertTrue(d.contains("SEM015"), "esperava SEM015, veio: " + d);
    }

    @Test
    void nonContextWebMemberNamesKeepSem015() throws Exception {
        String d = diagOf("bareGet", "main() { get(\"https://x\") }");
        assertTrue(d.contains("SEM015"), "esperava SEM015, veio: " + d);
    }
}
