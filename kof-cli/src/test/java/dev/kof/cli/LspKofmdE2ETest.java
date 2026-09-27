package dev.kof.cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * D-KOFMD fatia 3.7 — o gancho `.md` no LspServer existente (spec §16):
 * didOpen de um Kofmd publica diagnostics MDxxx com a LINHA do construto
 * (§14) e o hover de uma chave do vocabulario devolve kind + tipo inferido
 * — tudo decidido pela lib Kof pura carregada como motor; o `interop-kofmd.kf`
 * empacotado deve ser BYTE-A-BYTE o `libs/kofmd/Kofmd.kf` do repositorio
 * (sync-guard: a copia nunca diverge em silencio).
 */
@SuppressWarnings("unchecked")
class LspKofmdE2ETest {

    private static final String MD_URI = "file:///kof-lsp-selftest/note.md";

    @Test
    void didOpenMdPublishesMd002WithConstructLine() throws Exception {
        String text = "doing: parser\n\n@todo\nfix it\n";
        String didOpen = "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{"
                + "\"textDocument\":{\"uri\":\"" + MD_URI + "\",\"text\":\"" + Json.escape(text) + "\"}}}";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new LspServer(new ByteArrayInputStream(frame(didOpen)), out).run();
        Map<String, Object> note = null;
        for (Map<String, Object> m : messages(out.toString(StandardCharsets.UTF_8))) {
            if ("textDocument/publishDiagnostics".equals(m.get("method"))) {
                note = m;
            }
        }
        assertNotNull(note, "didOpen .md deve publicar diagnostics: "
                + messages(out.toString(StandardCharsets.UTF_8)));
        Map<String, Object> params = (Map<String, Object>) note.get("params");
        List<Object> diags = (List<Object>) params.get("diagnostics");
        assertEquals(1, diags.size(), "so o @todo fora do vocabulario: " + diags);
        Map<Object, Object> diag = (Map<Object, Object>) diags.get(0);
        assertEquals("MD002: " + "intent 'todo' outside reserved vocabulary (spec kofmd App.A)",
                diag.get("message"));
        assertEquals("kofmd", diag.get("source"));
        Map<Object, Object> range = (Map<Object, Object>) diag.get("range");
        Map<Object, Object> start = (Map<Object, Object>) range.get("start");
        assertEquals(2L, ((Number) start.get("line")).longValue(),
                "@todo esta na linha 4 do arquivo (0-based => 2)");
    }

    @Test
    void hoverOnVocabularyKeyAnswersLabelAndInferredType() throws Exception {
        String text = "doing: parser\n";
        String didOpen = "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{"
                + "\"textDocument\":{\"uri\":\"" + MD_URI + "\",\"text\":\"" + Json.escape(text) + "\"}}}";
        String req = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"textDocument/hover\",\"params\":{"
                + "\"textDocument\":{\"uri\":\"" + MD_URI + "\"},"
                + "\"position\":{\"line\":0,\"character\":2}}}";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new LspServer(new ByteArrayInputStream(all(frame(didOpen), frame(req))), out).run();
        Map<String, Object> resp = byId(messages(out.toString(StandardCharsets.UTF_8)), 1);
        Map<String, Object> result = (Map<String, Object>) resp.get("result");
        assertNotNull(result, "hover em chave Kofmd nao pode ser null");
        Map<String, Object> contents = (Map<String, Object>) result.get("contents");
        String value = (String) contents.get("value");
        assertTrue(value.contains("continuity | String"), value);
    }

    @Test
    void engineResolvesTheWholePackageAndNoStaleMonoFileCopyExists() throws Exception {
        assertNull(LspKofmdE2ETest.class.getResourceAsStream("/dev/kof/interop-kofmd.kf"),
                "a copia mono-arquivo esta proibida: o split em facade+partes a deixaria poeira no "
                + "primeiro commit (regra 12 — fonte de verdade unica via KofmdLibrary)");
        Path repoLib = findLibrary();
        assumeTrue(repoLib != null, "libs/kofmd ausente (modo instalacao)");
        try (var files = java.nio.file.Files.list(repoLib.getParent())) {
            assertTrue(files.anyMatch(f -> f.getFileName().toString().equals("KofmdVocab.kf")),
                    "motor deve enxergar o pacote completo, nao so o facade");
        }
        // hoverFor percorre facade + Vocab + Infer: prova de que o compile do
        // pacote inteiro aconteceu de fato (arquivo unico teria dado MD001).
        assertEquals("continuity | String", LspKofmd.hoverAt("doing: parser\n", 1, "doing"));
    }

    @Test
    void kfDocumentsKeepUsingTheCompilerNotTheKofmdHook() throws Exception {
        String text = "main() { val x: Int = \"errado\" }\n";
        String uri = "file:///kof-lsp-selftest/Bad.kf";
        String didOpen = "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{"
                + "\"textDocument\":{\"uri\":\"" + uri + "\",\"text\":\"" + Json.escape(text) + "\"}}}";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new LspServer(new ByteArrayInputStream(frame(didOpen)), out).run();
        List<Map<String, Object>> msgs = messages(out.toString(StandardCharsets.UTF_8));
        boolean sawKofmd = msgs.stream()
                .map(m -> (Map<String, Object>) m.get("params"))
                .filter(java.util.Objects::nonNull)
                .flatMap(p -> ((List<Object>) p.getOrDefault("diagnostics", List.of())).stream())
                .map(d -> (Map<Object, Object>) d)
                .anyMatch(d -> "kofmd".equals(d.get("source")));
        assertFalse(sawKofmd, "fonte .kf nunca passa pelo gancho Kofmd");
    }

    private static Path findLibrary() {
        Path working = Path.of("").toAbsolutePath().normalize();
        for (Path base : List.of(working, working.getParent(),
                working.getParent() == null ? working : working.getParent().getParent())) {
            Path candidate = base.resolve("libs").resolve("kofmd").resolve("Kofmd.kf");
            if (Files.isRegularFile(candidate)) return candidate;
        }
        return null;
    }

    private static byte[] frame(String json) {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        byte[] header = ("Content-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[header.length + body.length];
        System.arraycopy(header, 0, out, 0, header.length);
        System.arraycopy(body, 0, out, header.length, body.length);
        return out;
    }

    private static byte[] all(byte[]... parts) {
        int len = 0;
        for (byte[] p : parts) len += p.length;
        byte[] out = new byte[len];
        int off = 0;
        for (byte[] p : parts) { System.arraycopy(p, 0, out, off, p.length); off += p.length; }
        return out;
    }

    private static List<Map<String, Object>> messages(String raw) {
        byte[] all = raw.getBytes(StandardCharsets.UTF_8);
        String ascii = new String(all, StandardCharsets.ISO_8859_1);
        List<Map<String, Object>> out = new ArrayList<>();
        int pos = 0;
        while (true) {
            int header = ascii.indexOf("Content-Length:", pos);
            if (header < 0) break;
            int eol = ascii.indexOf('\n', header);
            int length = Integer.parseInt(ascii.substring(header + "Content-Length:".length(), eol).trim());
            int body = eol + 1;
            while (body < ascii.length() && (ascii.charAt(body) == '\r' || ascii.charAt(body) == '\n')) body++;
            byte[] json = new byte[length];
            System.arraycopy(all, body, json, 0, length);
            out.add((Map<String, Object>) Json.parse(new String(json, StandardCharsets.UTF_8)));
            pos = body + length;
        }
        return out;
    }

    private static Map<String, Object> byId(List<Map<String, Object>> msgs, long id) {
        return msgs.stream()
                .filter(m -> m.get("id") instanceof Number n && n.longValue() == id)
                .findFirst()
                .orElseThrow(() -> new AssertionError("sem resposta id=" + id + " em " + msgs));
    }
}
