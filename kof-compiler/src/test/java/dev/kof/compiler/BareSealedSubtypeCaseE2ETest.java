package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Issue #817: num switch sobre um tipo `sealed`, o caso BARE (`case Subtype ->`,
 * sem binding) era aceito pelo parser mas não contava como cobertura (SEM081
 * acusando um caso presente) — e, pior, o lowering o emitia como comparação de
 * VALOR, de modo que ele NUNCA casava (resultado silenciosamente errado).
 * A forma bare agora é reconhecida como TYPE TEST quando o nome é um subtipo
 * DIRETO do sujeito selado; um identificador que NÃO é subtipo (constante/valor)
 * segue com a semântica de valor de sempre.
 */
class BareSealedSubtypeCaseE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path tmp, String source) throws IOException, InterruptedException {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, source);
        Path out = tmp.resolve("out");
        CompilationResult res = driver.compile(src, out, Target.JVM);
        assertTrue(res.success(), "compile must be clean: " + res.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                "-cp", out.toString() + ":kof-runtime/target/classes", "Default.Main")
                .redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "JVM exit, output was:\n" + output);
        return output;
    }

    @Test
    void bareSubtypeCaseInSwitchExpressionCompilesAndMatches(@TempDir Path tmp) throws Exception {
        String out = runJvm(tmp, """
            sealed class ResultadoTaxa
            class TaxaEncontrada extends ResultadoTaxa { Double valor
                public constructor(Double valor) { this.valor = valor } }
            class SemTaxaContratada extends ResultadoTaxa

            String descreve(ResultadoTaxa r) {
                return switch (r) {
                    case TaxaEncontrada t -> "taxa " + t.valor
                    case SemTaxaContratada -> "sem taxa"
                }
            }
            main() {
                println(descreve(TaxaEncontrada(2.5)))
                println(descreve(SemTaxaContratada()))
            }
            """);
        assertEquals("taxa 2.5\nsem taxa", out, "the bare case must MATCH, not fall through");
    }

    @Test
    void bareSubtypeCaseInSwitchStatementCompilesAndMatches(@TempDir Path tmp) throws Exception {
        String out = runJvm(tmp, """
            sealed class S
            class P extends S { }
            class Q extends S
            main() {
                var s: S = Q()
                switch (s) {
                    case P: println("p")
                    case Q: println("q")
                }
            }
            """);
        assertEquals("q", out, "the bare case must MATCH in the statement form too");
    }

    @Test
    void bareConstantIdentifierCaseKeepsValueSemantics(@TempDir Path tmp) throws Exception {
        String out = runJvm(tmp, """
            main() {
                var k = 7
                var x = 7
                println(switch (x) { case k -> "hit" default -> "miss" })
            }
            """);
        assertEquals("hit", out, "a NON-subtype identifier stays a value comparison");
    }
}
