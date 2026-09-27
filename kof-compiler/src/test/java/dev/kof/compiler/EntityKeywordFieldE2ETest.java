package dev.kof.compiler;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §381 — entity com nome de campo PALAVRA-RESERVADA (`val`) entrava o loop de
 * campos em progresso-zero: `expectId` reporta e nao consome, entao cada
 * iteracao aloca um diagnostico + um EntityFieldNode ate o heap morrer
 * (medido: -Xmx256m morre em ~2s). Fix = diagnostico limpo, sem mudar a
 * gramatica (decisao do maintainer 20/09). Executado em SUBPROCESSO com heap
 * pequeno para o RED nao derrubar o fork do surefire — o verde prova que o
 * parse termina rapido com o mesmo budget do repro original.
 */
class EntityKeywordFieldE2ETest {

    /** main() do harness — roda so no filho. */
    public static class Harness {
        public static void main(String[] args) throws Exception {
            CompilerDriver driver = new CompilerDriver();
            Path source = Path.of(args[0]);
            try {
                CompilationResult r = driver.compile(source, source.resolveSibling("out"), Target.JVM);
                System.out.println("SUCCESS=" + r.success());
                System.out.println("DIAGS=" + r.diagnostics().getDiagnostics());
            } catch (Throwable t) {
                System.out.println("CRASHED=" + t.getClass().getName());
            }
            System.out.println("DONE");
        }
    }

    private String runHarness(Path kf) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilder pb = new ProcessBuilder(java, "-Xmx256m",
                "-cp", System.getProperty("java.class.path"),
                Harness.class.getName(), kf.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (InputStream in = p.getInputStream()) {
            in.transferTo(buf);
        }
        assertTrue(p.waitFor(120, TimeUnit.SECONDS), "compilador nao terminou (loop de progresso-zero?)");
        assertEquals(0, p.exitValue(), "filho morreu: " + buf.toString(StandardCharsets.UTF_8));
        return buf.toString(StandardCharsets.UTF_8);
    }

    @Test
    void reservedFieldNameFailsWithCleanDiagnosticNoOom(@TempDir Path tmp) throws Exception {
        Path kf = tmp.resolve("E.kf");
        Files.writeString(kf, """
                entity E { val: String }
                main() { println("nao chega") }
                """);
        String out = runHarness(kf);
        assertFalse(out.contains("OutOfMemoryError"), "OOM de novo — loop nao curado: " + out);
        assertTrue(out.contains("Expected field name in entity"), "sem diagnostico limpo: " + out);
        assertTrue(out.contains("PARSE024"), "codigo PARSE024 faltando: " + out);
        assertTrue(out.contains("SUCCESS=false"), "deve falhar a compilacao: " + out);
        assertTrue(out.contains("DONE"), "processo nao chegou ao fim: " + out);
    }

    @Test
    void reservedNameErrorIsBoundedNotPerTokenCascade(@TempDir Path tmp) throws Exception {
        Path kf = tmp.resolve("C.kf");
        Files.writeString(kf, """
                entity C {
                    val: String
                    as: Int
                }
                main() { println("x") }
                """);
        String out = runHarness(kf);
        long reports = out.lines().filter(l -> l.startsWith("DIAGS="))
                .flatMap(l -> java.util.regex.Pattern.compile("Expected field name in entity").matcher(l).results())
                .count();
        assertTrue(reports >= 1, "esperava ao menos 1 reporte: " + out);
        assertTrue(reports <= 8, "cascata por token sem limites (" + reports + "): " + out);
    }

    @Test
    void validEntityFieldsStillCompile(@TempDir Path tmp) throws Exception {
        Path kf = tmp.resolve("V.kf");
        Files.writeString(kf, """
                entity V {
                    id: String unique
                    v: Int
                }
                main() { println("ok") }
                """);
        String out = runHarness(kf);
        assertTrue(out.contains("SUCCESS=true"), "parser ficou restrito demais: " + out);
    }
}
