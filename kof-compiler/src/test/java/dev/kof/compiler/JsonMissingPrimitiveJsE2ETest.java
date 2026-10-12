package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Audit JSN-01 (the JS face of the §565 contract): `json.decode<Record>`
 *  with a missing/JSON-null PRIMITIVE component silently yielded
 *  `undefined` on the JS target while the JVM threw the named JSN004
 *  (`D-JSON-MISSING-PRIMITIVE`, maintainer 03/10 — a silent zero-fill/undef
 *  is forbidden). The generated `__kof_decode_<Class>` helper now guards
 *  every non-nullable primitive field. Nullable/reference fields keep the
 *  null semantics. The NATIVE asm face stays open (separate unit). */
class JsonMissingPrimitiveJsE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private String runJs(String tag, String source) throws Exception {
        Path file = tmp.resolve(tag + "/Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
        Path outDir = tmp.resolve("out-" + tag);
        CompilationResult result = driver.compile(file, outDir, Target.JS);
        assertTrue(result.success(), () -> "JS compile failed: " + result.diagnostics().getDiagnostics());
        var out = new ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(outDir.resolve("Default.mjs"), out,
                new ByteArrayInputStream(new byte[0]), out);
        return "ec=" + ec + "\n" + out.toString(StandardCharsets.UTF_8);
    }

    @Test
    void missingPrimitiveFieldFailsNamedJsn004OnJs() throws Exception {
        String out = runJs("missing", """
            record Pedido(String nome, Int clienteId)
            main() {
                var p = json.decode<Pedido>("{\\"nome\\":\\"a\\"}")
                println(p.nome)
                println(p.clienteId)
            }
            """);
        assertTrue(out.contains("JSN004"), "esperava JSN004 na saída, veio: " + out);
        assertFalse(out.contains("undefined"), "undefined silencioso vazou: " + out);
    }

    @Test
    void jsonNullPrimitiveFieldFailsNamedJsn004OnJs() throws Exception {
        String out = runJs("jsonnull", """
            record Env(String id, Int done)
            main() {
                var e = json.decode<Env>("{\\"id\\":\\"x\\",\\"done\\":null}")
                println(e.id)
            }
            """);
        assertTrue(out.contains("JSN004"), "esperava JSN004 na saída, veio: " + out);
    }

    @Test
    void nullableComponentIsNotRejectedOnJs() throws Exception {
        // the GUARD never fires for nullable fields (reference semantics per
        // §565). What a missing Int? yields on JS today (the ctor default)
        // is pre-existing behavior OUT of this unit's scope — only the
        // no-JSN004 part is pinned here.
        String out = runJs("nullref", """
            record Nota(String texto, Int? cor)
            main() {
                var n = json.decode<Nota>("{\\"texto\\":\\"oi\\"}")
                println(n.texto)
            }
            """);
        assertTrue(out.contains("oi"), "texto perdido: " + out);
        assertFalse(out.contains("JSN004"), "referência ausente não é erro: " + out);
    }

    @Test
    void completePrimitiveComponentsStillDecodeOnJs() throws Exception {
        String out = runJs("complete", """
            record Pedido(String nome, Int clienteId)
            main() {
                var p = json.decode<Pedido>("{\\"nome\\":\\"a\\",\\"clienteId\\":7}")
                println(p.nome)
                println(p.clienteId)
            }
            """);
        assertTrue(out.contains("a"), "nome perdido: " + out);
        assertTrue(out.contains("7"), "clienteId perdido: " + out);
        assertFalse(out.contains("JSN004"), "decode completo não é erro: " + out);
    }
}
