package dev.kof.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #636 — as diagnosticas do `kof lsp` devem bater com o `kof check` no MESMO
 * projeto multi-arquivo. A causa era o `analyze` compiar o buffer SOZINHO num
 * `kof-lsp-XXXX` (sem kof.toml nem irmaos): a raiz do modulo virava o temp e
 * todo import local morria PKG006/PKG004. Prova: espelhar a arvore do projeto
 * (LspProject) com o buffer por cima do disco.
 */
class LspProjectDiagnosticsE2ETest {

    private static byte[] frame(String json) {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        byte[] header = ("Content-Length: " + body.length + "\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[header.length + body.length];
        System.arraycopy(header, 0, out, 0, header.length);
        System.arraycopy(body, 0, out, header.length, body.length);
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> publishedDiagnostics(String raw, String uri) {
        // byte-safe (mesma licao do irmao: Content-Length e em BYTES)
        byte[] all = raw.getBytes(StandardCharsets.UTF_8);
        String ascii = new String(all, StandardCharsets.ISO_8859_1);
        for (int i = ascii.indexOf("{"); i >= 0; ) {
            int end = ascii.indexOf("\r\n\r\n", i);
            if (end < 0) break;
            int len = Integer.parseInt(ascii.substring(ascii.lastIndexOf("Content-Length: ", end) + 16, end).trim());
            String body = ascii.substring(end + 4, end + 4 + len);
            if (body.contains("publishDiagnostics") && body.contains("\"" + uri + "\"")) {
                int start = body.indexOf("\"diagnostics\":[");
                if (start >= 0) {
                    int close = body.indexOf(']', start);
                    String inner = body.substring(start + "\"diagnostics\":[".length(), close).trim();
                    if (inner.isEmpty()) return List.of();
                    // uma diagnostica por {} de nivel 1 (os payloads tem range
                    // aninhado — extrai so os CODES, que e o que o teste cobra)
                    List<Map<String, Object>> out = new java.util.ArrayList<>();
                    for (String code : codesOf(inner)) {
                        out.add(Map.of("code", code));
                    }
                    return out;
                }
            }
            i = ascii.indexOf("{", end + 4 + len);
        }
        throw new AssertionError("nenhuma publishDiagnostics para " + uri + " na saida:\n" + raw);
    }

    private static List<String> codesOf(String diagnosticsJson) {
        List<String> codes = new java.util.ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"code\":\"([A-Z0-9]+)\"").matcher(diagnosticsJson);
        while (m.find()) codes.add(m.group(1));
        return codes;
    }

    private static String uriOf(Path p) {
        return p.toUri().toString();
    }

    private static String didOpen(Path file, String text) {
        return "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":"
                + "{\"textDocument\":{\"uri\":\"" + uriOf(file) + "\",\"text\":"
                + str(text) + "}}}";
    }

    private static String initReq(Path root) {
        String ru = root == null ? "{}" : "{\"rootUri\":\"" + root.toUri() + "\"}";
        return "{\"jsonrpc\":\"2.0\",\"id\":0,\"method\":\"initialize\",\"params\":" + ru + "}";
    }

    private static String str(String s) {
        String esc = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        return "\"" + esc + "\"";
    }

    private static final String SAY_HI = "package core\nsayHi() {\n}\n";
    private static final String MAIN = "import core.say_hi\n\nmain() {\n    sayHi()\n}\n";

    private static Path project(Path dir) throws Exception {
        Files.writeString(dir.resolve("kof.toml"), "[project]\nname = \"repro\"\n");
        Path core = Files.createDirectories(dir.resolve("core"));
        Files.writeString(core.resolve("say_hi.kf"), SAY_HI);
        Path src = Files.createDirectories(dir.resolve("src"));
        Files.writeString(src.resolve("Main.kf"), MAIN);
        return dir;
    }

    private static String run(byte[]... in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new LspServer(new ByteArrayInputStream(all(in)), out).run();
        return out.toString(StandardCharsets.UTF_8);
    }

    private static byte[] all(byte[]... parts) {
        int len = 0;
        for (byte[] p : parts) len += p.length;
        byte[] out = new byte[len];
        int off = 0;
        for (byte[] p : parts) { System.arraycopy(p, 0, out, off, p.length); off += p.length; }
        return out;
    }

    @Test
    void importedLocalPackageHasNoDiagnosticsLikeKofCheck(@TempDir Path dir) throws Exception {
        // reproducao VERBATIM do #636 (sem aspas no fonte Kof — a licao dos
        // escapes em text block nao se repete aqui)
        Path root = project(dir);
        String uri = uriOf(root.resolve("src/Main.kf"));
        String out = run(frame(initReq(root)), frame(didOpen(root.resolve("src/Main.kf"), MAIN)));
        List<Map<String, Object>> diags = publishedDiagnostics(out, uri);
        assertEquals(List.of(), diags,
                "kof lsp deve dar as MESMAS diagnostique do kof check (zero) para Main.kf do projeto");
    }

    @Test
    void packageDeclarationInsideProjectDirIsAccepted(@TempDir Path dir) throws Exception {
        Path root = project(dir);
        String uri = uriOf(root.resolve("core/say_hi.kf"));
        String out = run(frame(initReq(root)), frame(didOpen(root.resolve("core/say_hi.kf"), SAY_HI)));
        assertEquals(List.of(), publishedDiagnostics(out, uri),
                "package core dentro de core/ e aceito pelo CLI — PKG004 no LSP era o temp sem raiz");
    }

    @Test
    void projectResolvesEvenWithoutRootUriViaKofToml(@TempDir Path dir) throws Exception {
        // neovim com arquivo solto (sem workspace): a subida por kof.toml ainda
        // acha a raiz — e EXATAMENTE o que o kof check faz
        Path root = project(dir);
        String uri = uriOf(root.resolve("src/Main.kf"));
        String out = run(frame(initReq(null)), frame(didOpen(root.resolve("src/Main.kf"), MAIN)));
        assertEquals(List.of(), publishedDiagnostics(out, uri),
                "raiz derivada do kof.toml ancestral, nao so do rootUri");
    }

    @Test
    void bufferWithRealErrorStillReportsItAndNoImportNoise(@TempDir Path dir) throws Exception {
        Path root = project(dir);
        String broken = "import core.say_hi\n\nmain() {\n    sayHi(";
        String uri = uriOf(root.resolve("src/Main.kf"));
        String out = run(frame(initReq(root)), frame(didOpen(root.resolve("src/Main.kf"), broken)));
        List<String> codes = publishedDiagnostics(out, uri).stream()
                .map(d -> String.valueOf(d.get("code"))).toList();
        assertFalse(codes.isEmpty(), "erro de sintaxe no buffer continua reportado (o buffer vence o disco)");
        assertFalse(codes.contains("PKG006"), "com o projeto espelhado o import local resolve: " + codes);
    }

    @Test
    void siblingErrorNeverLeaksIntoThisBuffer(@TempDir Path dir) throws Exception {
        // irmao quebrado no disco: as linhas dele NAO podem virar diagnostico
        // do Main (posicao de outro arquivo dentro deste buffer e lixo)
        Path root = project(dir);
        Files.writeString(root.resolve("core/say_hi.kf"), "package core\nbroken(( {\n");
        String uri = uriOf(root.resolve("src/Main.kf"));
        String out = run(frame(initReq(root)), frame(didOpen(root.resolve("src/Main.kf"), MAIN)));
        List<String> codes = publishedDiagnostics(out, uri).stream()
                .map(d -> String.valueOf(d.get("code"))).toList();
        assertTrue(codes.stream().noneMatch(c -> c.startsWith("PARSE")),
                "diagnostico do irmao vazou no buffer do Main: " + codes);
    }

    @Test
    void looseFileOutsideProjectKeepsTheSingleFileMode(@TempDir Path dir) throws Exception {
        // pin do comportamento antigo: arquivo SEM projeto (sem kof.toml
        // ancestral, sem rootUri) permanece modo arquivo-unico — o import
        // inexistente deve morrer PKG006 honesto, nunca silenciar
        Path loose = dir.resolve("solto.kf");
        Files.writeString(loose, "import nao.existe\n\nmain() {\n}\n");
        String uri = uriOf(loose);
        String out = run(frame(initReq(null)), frame(didOpen(loose, "import nao.existe\n\nmain() {\n}\n")));
        List<String> codes = publishedDiagnostics(out, uri).stream()
                .map(d -> String.valueOf(d.get("code"))).toList();
        assertTrue(codes.contains("PKG006"),
                "arquivo solto fora de projeto mantem o modo antigo (PKG006): " + codes);
    }
}
