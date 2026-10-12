package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression (found by the release.yml CI validation refusing the 0.6.2 cut):
 * strings.padLeft/padRight with a CHAR LITERAL pad stopped resolving after the
 * silent-overload type guards (§658/§665/§666/§668/§669 family) required
 * isString on the 3rd argument. The pad idiom takes the FIRST CHAR of the 3rd
 * argument, so a char literal is valid Kof and historically bound (this is the
 * old CoreRegressionE2ETest.stringsPadWithCharLiteral face). The guard now
 * accepts isString OR isChar for the pad argument.
 */
class StringsPadCharLiteralTest extends SemanticResolutionSupport {

    private static final String SRC = """
            main() {
                println(strings.padLeft("7", 3, '0'))
                println(strings.padRight("7", 3, '0'))
            }
            """;

    @Test
    void padWithCharLiteralResolvesAndRuns(@TempDir Path tmp) throws Exception {
        Path source = tmp.resolve("padchar.kf");
        Files.writeString(source, SRC);
        CompilationResult r = driver.compile(source, tmp.resolve("out-run"), Target.JVM);
        assertTrue(r.success(), "pad com literal char é Kof válido (o runtime usa a "
                + "1ª char do 3º arg): " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder("java", "-cp", tmp.resolve("out-run").toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String os = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0, p.waitFor(), "run deve sair limpo: " + os);
        assertEquals("007\n700", os.replaceAll("\\n+$", ""), "pad deve aplicar o char");
    }
}
