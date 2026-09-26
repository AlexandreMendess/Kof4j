package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #634 — a user function whose parameter/return is declared {@code Map<K,V>}
 * was emitted with the CONCRETE descriptor {@code Ljava/util/HashMap;}, while
 * the JSON decoder returns a value typed as the {@code java/util/Map} interface
 * (a {@code LinkedHashMap}). The JVM verifier rejects the call
 * ({@code Map} is not a subtype of {@code HashMap}), and OpenJDK's launcher
 * hides the real {@code VerifyError} behind the "JavaFX runtime components not
 * found" message (AGENTS: that message is never benign).
 *
 * <p>Root cause: {@code JvmTypeMapper} erased Kof {@code Map} to
 * {@code java/util/HashMap} in descriptors, but every {@code Map} operation is
 * emitted with {@code INVOKEINTERFACE java/util/Map} (JvmOpMap) and the JSON
 * decoders are declared {@code Ljava/util/Map;}. The erasure is now the
 * interface, matching the operations and the decoder contract. The
 * {@code mapOf}-built control proves the previously-working path is preserved.
 *
 * <p>JVM-only by construction: the bug is bytecode verification
 * ({@code Map} vs {@code HashMap} in a descriptor). Script/JS/Native have no
 * verifier and erase {@code Map} differently; their parity for these
 * collections is covered by their own suites.
 */
class MapParamErasureE2ETest {

    private static final String PROGRAM = """
            record LocaleRef(String code, String name)
            record ApiResposta(String code, Map<String, String> ui, List<LocaleRef> locales)

            Map<String, String> copiar(Map<String, String> origem) {
                return origem
            }

            main() {
                var decoded = json.decode<Map<String, String>>("{\\"a\\":\\"b\\"}")
                var fresh = copiar(decoded)
                println(fresh.get("a"))
                var refs = listOf()
                refs.add(LocaleRef("en", "English"))
                var corpo = ApiResposta("en", fresh, refs)
                println(json.encode(corpo))
                var built = mapOf<String, String>()
                built.put("a", "b")
                println(copiar(built).get("a"))
            }
            """;

    private static final String GOLDEN =
            "b\n"
                    + "{\"code\":\"en\",\"ui\":{\"a\":\"b\"},"
                    + "\"locales\":[{\"code\":\"en\",\"name\":\"English\"}]}\n"
                    + "b\n";

    private final CompilerDriver driver = new CompilerDriver();

    private List<Path> sources(Path tmp) throws IOException {
        Files.writeString(tmp.resolve("Main.kf"), PROGRAM);
        return List.of(tmp.resolve("Main.kf"));
    }

    @Test
    void decodedMapPassedToTypedFunctionRunsOnJvm(@TempDir Path tmp) throws Exception {
        CompilationResult r = driver.compileSources(sources(tmp), tmp.resolve("classes"),
                Target.JVM, tmp);
        assertTrue(r.success(), "JVM build: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder("java", "-cp", r.outputDir().toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), "JVM run timeout");
        assertEquals(0, p.exitValue(), "JVM exit (#634, VerifyError hidden as JavaFX msg): " + out);
        assertEquals(GOLDEN, out, "JVM golden");
    }
}
