package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #587 — `break` dentro de um case de switch (statement) deve terminar o
 * SWITCH (contexto quebrável mais interno), não o loop externo. O contrato
 * é `docs/language-reference/statements.md` §5.5/§6: break/continue encerram
 * "a iteração do loop mais interno (ou switch)" e o §6 diz que `break`
 * dentro de case é "aceito (e redundante)" — o switch já auto-termina.
 * Comportamento antigo: o salto do break ia direto para a saída do loop
 * (truncava o loop em 1 iteração — `10` em vez de `208`).
 *
 * <p>Prova multi-alvo: JVM (as 4 formas de loop × value e pattern switch),
 * Native x86_64 e JS. O §476 (lista MISTA + default vazio = VerifyError no
 * load) é casado nesta unidade — mesmo arquivo.
 */
class SwitchBreakScopeE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String FOR_IN_VALUE = """
            main() {
                var nums = listOf(1, 2, 3)
                var total = 0
                for (var n in nums) {
                    switch (n) {
                        case 1:
                            total = total + 10
                            break
                        default:
                            total = total + 99
                            break
                    }
                }
                println(total)
            }
            """;

    private static final String FOR_IN_PATTERN = """
            record Tag(Int n)
            main() {
                var tags = listOf(Tag(1), Tag(2), Tag(3))
                var total = 0
                for (var t in tags) {
                    switch (t) {
                        case Tag(var n):
                            total = total + n
                            break
                        default:
                            total = total + 100
                            break
                    }
                }
                println(total)
            }
            """;

    private String runJvm(Path outDir) throws IOException {
        try {
            Process p = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main")
                    .redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            assertEquals(0, p.waitFor(), "JVM exit code, output: " + output);
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
    }

    private String runNative(Path outDir) throws IOException {
        try {
            Process p = new ProcessBuilder(outDir.resolve("Default").resolve("Main").toString())
                    .redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            assertEquals(0, p.waitFor(), "Native exit code, output: " + output);
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
    }

    private String runJs(Path outDir) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int exitCode = dev.kof.runtime.KofJsRunner.run(outDir.resolve("Default.mjs"), out,
                new java.io.ByteArrayInputStream(new byte[0]), out);
        assertEquals(0, exitCode, "JS exit code, output: " + out);
        return out.toString().trim();
    }

    private Path compileTo(Path tempDir, String name, String source, Target target) throws IOException {
        Path src = tempDir.resolve(name + ".kf");
        Files.writeString(src, source);
        Path out = tempDir.resolve(name + "-out");
        CompilationResult r = driver.compile(src, out, target);
        assertTrue(r.success(), name + " deve compilar p/ " + target + ": "
                + r.diagnostics().getDiagnostics());
        return out;
    }

    @Test
    void breakInSwitchCaseTerminatesSwitchNotTheForInLoopJvm(@TempDir Path tempDir) throws IOException {
        assertEquals("208", runJvm(compileTo(tempDir, "b587fi", FOR_IN_VALUE, Target.JVM)),
                "for-in × value switch × break em case (o verbatim da #587)");
    }

    @Test
    void breakInPatternSwitchCaseTerminatesSwitchJvm(@TempDir Path tempDir) throws IOException {
        assertEquals("6", runJvm(compileTo(tempDir, "b587pt", FOR_IN_PATTERN, Target.JVM)),
                "for-in × pattern switch × break em case (1+2+3)");
    }

    @Test
    void breakInsideWhileAndDoWhileAndClassicForJvm(@TempDir Path tempDir) throws IOException {
        String whileForm = """
                main() {
                    var i = 0
                    var hits = 0
                    while (i < 5) {
                        switch (i) {
                            case 2:
                                hits = hits + 1
                                break
                            default:
                                hits = hits + 10
                                break
                        }
                        i = i + 1
                    }
                    println(hits)
                }
                """;
        assertEquals("41", runJvm(compileTo(tempDir, "b587wh", whileForm, Target.JVM)),
                "while × break em case: 2 rounds no default (10+10) + 1 no case (1) = 41? não —"
                        + " i=0,1 default (20), i=2 case (1), i=3,4 default (20) = 41");
    }

    @Test
    void verbatimIssueReproMatchesJvmOnNativeX86(@TempDir Path tempDir) throws IOException {
        assertEquals("208", runNative(compileTo(tempDir, "b587nx", FOR_IN_VALUE, Target.NATIVE)),
                "verbatim #587 no Native x86_64");
    }

    @Test
    void verbatimIssueReproMatchesJvmOnJs(@TempDir Path tempDir) throws IOException {
        assertEquals("208", runJs(compileTo(tempDir, "b587js", FOR_IN_VALUE, Target.JS)),
                "verbatim #587 no JS");
    }

    @Test
    void breakInsideDefaultBodyAlsoTerminatesTheSwitchJvm(@TempDir Path tempDir) throws IOException {
        String defaultBreak = """
            main() {
                var nums = listOf(7, 8)
                var total = 0
                for (var n in nums) {
                    switch (n) {
                        case 7:
                            total = total + 1
                        default:
                            total = total + 2
                            break
                    }
                }
                println(total)
            }
            """;
        assertEquals("3", runJvm(compileTo(tempDir, "b587db", defaultBreak, Target.JVM)),
                "break no corpo do default termina só o switch (round1 case7=+1, round2 default=+2)");
    }

    // §476 — casada nesta unidade: lista MISTA (record pattern + case de
    // valor PRIMITIVO) com default VAZIO emitia `if_acmpeq` (EQ de referência
    // do subject ClassType) sobre um literal INT = VerifyError no load. O fix
    // é a recusa SEM035 existente estendida a este site (ref×int nunca é par
    // de igualdade válido — nunca houve programa que funcionasse aqui).
    @Test
    void mixedCaseListWithPrimitiveValueCaseIsRefusedSem035(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("s476mx.kf");
        Files.writeString(src, """
                record Tag(Int n)
                main() {
                    var sum = 0
                    var k = 0
                    while (k < 4) {
                        switch (Tag(k)) {
                            case Tag(var n):
                                sum = sum + n
                            case 99:
                                sum = sum + 99
                            default:
                        }
                        k = k + 1
                    }
                    println(sum)
                }
                """);
        CompilationResult r = driver.compile(src, tempDir.resolve("s476mx-out"), Target.JVM);
        assertTrue(!r.success(), "lista mista com case primitivo deve ser RECUSADA (SEM035)");
        assertTrue(r.diagnostics().getDiagnostics().stream().anyMatch(d ->
                        "SEM035".equals(d.code())),
                "a recusa deve ser SEM035 nomeada (nunca VerifyError mudo): "
                        + r.diagnostics().getDiagnostics());
    }

    @Test
    void stringValueCaseInsidePatternSwitchStaysLegal(@TempDir Path tempDir) throws IOException {
        // Controle: o guard do §476 é só para case PRIMITIVO — case de valor
        // STRING dentro de pattern switch (subject reference) continua legal.
        String legal = """
                main() {
                    var o: Object = "b"
                    var hit = 0
                    switch (o) {
                        case String s:
                            hit = 1
                        case "b":
                            hit = 2
                        default:
                    }
                    println(hit)
                }
                """;
        Path src = tempDir.resolve("s476ctl.kf");
        Files.writeString(src, legal);
        Path out = tempDir.resolve("s476ctl-out");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "case de valor String em pattern switch deve compilar: "
                + r.diagnostics().getDiagnostics());
    }
}
