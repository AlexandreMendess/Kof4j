package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression (found by the 0.6.2 stamped safe-suite, 12 instant failures): the
 * §669 web-context type guard intercepts ANY bare call to a context NAME —
 * including a LAMBDA PARAMETER named `body` being invoked, which is exactly
 * what the injected `kof.test.db` helper does (`testdb.kf:31 body(conn)` inside
 * `withDb(String url, (String) -> Void body)`). A local/parameter name ALWAYS
 * wins over the web-context name table (the same precedence as the #786
 * `db` rule); the context function is only reachable when the name is NOT a
 * local.
 */
class WebContextNameLambdaParamTest extends SemanticResolutionSupport {

    private static final String SRC = """
            Void withBody(String url, (String) -> Void body) {
                body(url)
            }

            main() {
                withBody("hello", (s: String) -> {
                    println(s)
                })
            }
            """;

    @Test
    void lambdaParamNamedLikeWebContextIsInvoked(@TempDir Path tmp) throws Exception {
        Path source = tmp.resolve("W669.kf");
        Files.writeString(source, SRC);
        CompilationResult r = driver.compile(source, tmp.resolve("out-run"), Target.JVM);
        assertTrue(r.success(), "um parâmetro-lambda chamado 'body' é uma chamada "
                + "comum de lambda — o guard do contexto web não pode capturá-la: "
                + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder("java", "-cp", tmp.resolve("out-run").toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String os = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0, p.waitFor(), "run deve sair limpo: " + os);
        assertTrue(os.contains("hello"), "o lambda deve ter sido invocado: " + os);
    }
}
