package dev.kof.script;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * X2 fatia 2 (26/09) — o motor R do `kof.interop` roda no SCRIPT por
 * PARIDADE DE CONSTRUCAO (mesma maquina `kof_process_spawn`/`kof_json_*`
 * do InteropPyScriptE2ETest). Golden = o MESMO do motor py no JVM
 * (simetria de engines); salta honesto sem Rscript/jsonlite no host.
 */
class InteropRScriptE2ETest {

    // Fonte UNICA do programa (o bug real do CI 4b63fda19: esta copia
    // divergiu da do InteropRE2ETest — \n cru no text block Java come a 1a
    // camada de escape e grava newline dentro da string Kof -> PARSE043).
    private static final String PROGRAM = """
            import kof.interop
            main() {
                var r = KofR("sq <- function(n) n*n\\nhi <- function(n) paste0(\\"oi \\", n)")
                println(r.callInt("sq", listOf(5)))
                println(r.callString("hi", listOf("mel")))
            }
            """;

    @Test
    void scriptProgramParsesWithoutR() throws Exception {
        // NAO pula: o parse do PROGRAMA (nao so do host) deve valer em
        // qualquer host — sem R o motor falha NOMEADO (INTEROP004), nunca
        // com PARSE; e assim que o drift de escape e pego ANTES do CI.
        Path tmp = Files.createTempDirectory("kofrparse");
        Path f = tmp.resolve("p.kf");
        Files.writeString(f, PROGRAM);
        var script = KofScript.runFile(f, dev.kof.compiler.Target.SCRIPT);
        assertTrue(!script.stderr().contains("PARSE"),
                "programa deve parsear mesmo sem R: " + script.stderr());
    }

    @Test
    void rEngineScriptMatchesJvmGolden() throws Exception {
        boolean ready = false;
        try {
            Process p = new ProcessBuilder("Rscript", "--vanilla", "-e", "library(jsonlite)")
                    .redirectErrorStream(true).start();
            ready = p.waitFor(120, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception ignore) {}
        assumeTrue(ready, "Rscript/jsonlite ausentes — gate de ambiente, nao regressao");
        Path tmp = Files.createTempDirectory("kofrscript");
        Path f = tmp.resolve("p.kf");
        Files.writeString(f, PROGRAM);
        var script = KofScript.runFile(f, dev.kof.compiler.Target.SCRIPT);
        assertTrue(script.success(), "SCRIPT: " + script.stderr());
        assertEquals("25\noi mel", script.stdout().replace("\r\n", "\n").trim(),
                "motor R interpretado == golden JVM");
    }
}
