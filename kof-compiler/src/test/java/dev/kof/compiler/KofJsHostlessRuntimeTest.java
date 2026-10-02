package dev.kof.compiler;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * #104 — kof_platform só existe no host GraalJS. Os blocos do kof-runtime.mjs
 * (uuid/random/security) referenciavam o global cru, dando ReferenceError fora
 * do Graal (Node, navegador). O core agora carrega o mesmo shim do
 * kof-runtime-io.mjs (globalThis.kof_platform || Proxy). Estes testes rodam o
 * artefato JS num Context que NÃO injeta kof_platform (simula Node/browser) e
 * provam o erro claro em vez do ReferenceError.
 */
class KofJsHostlessRuntimeTest {

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void uuidV4OutsideHostGivesClearErrorNotReference(@TempDir Path tmp) throws Exception {
        String err = runWithoutHost(tmp, """
            main() {
                println(uuid.v4().length)
            }
            """);
        assertFalse(err.contains("ReferenceError"),
                "esperado shim claro, veio ReferenceError: " + err);
        assertTrue(err.contains("not available"),
                "esperado erro 'not available', veio: " + err);
    }

    @Test
    void randomOutsideHostGivesClearErrorNotReference(@TempDir Path tmp) throws Exception {
        String err = runWithoutHost(tmp, """
            main() {
                println(security.randomHex(8))
            }
            """);
        assertFalse(err.contains("ReferenceError"),
                "esperado shim claro, veio ReferenceError: " + err);
        assertTrue(err.contains("not available"),
                "esperado erro 'not available', veio: " + err);
    }

    // D-KOF-NET fatia 4a: o socket JS depende do host kof_platform (a ponte
    // java.net). Fora dele (browser puro) o shim tem de nomear o gap — nunca
    // ReferenceError nem drop silencioso (R7, mesma família de uuid/security).
    @Test
    void netOutsideHostGivesClearErrorNotReference(@TempDir Path tmp) throws Exception {
        String err = runWithoutHost(tmp, """
            main() {
                var l = net.listen(19940)
                println("listening")
            }
            """);
        assertFalse(err.contains("ReferenceError"),
                "esperado shim claro, veio ReferenceError: " + err);
        assertTrue(err.contains("not available"),
                "esperado erro 'not available', veio: " + err);
    }

    private String runWithoutHost(Path tempDir, String source) throws Exception {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JS);
        assertTrue(result.success(), "JS compile failed: " + result.diagnostics().getDiagnostics());
        Path entry = findJsEntry(outDir);
        try (Context context = Context.newBuilder("js")
                .allowAllAccess(true)
                .option("engine.WarnInterpreterOnly", "false")
                .out(new ByteArrayOutputStream())
                .err(new ByteArrayOutputStream())
                .build()) {
            // SEM exposePlatform: é isto que distingue de KofJsRunner — nenhum
            // kof_platform no global, como em Node/browser puro.
            Source src = Source.newBuilder("js", entry.toFile())
                    .mimeType("application/javascript+module")
                    .build();
            try {
                context.eval(src);
                fail("esperado o shim lançar erro fora do host");
            } catch (PolyglotException pe) {
                return pe.getMessage();
            }
            return "";
        }
    }

    private static Path findJsEntry(Path dir) throws java.io.IOException {
        try (var s = Files.walk(dir)) {
            return s.filter(p -> p.getFileName().toString().equals("Default.mjs"))
                    .findFirst().orElseThrow();
        }
    }
}
