package dev.kof.compiler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;


/**
 * D-HTTP-POLICIES (F2): {@code app.policy(prefix, opts)} — policies de recurso.
 *
 * A policy global ({@code app.security}) permanece o default; escopos de
 * recurso casam por prefixo do path e aplicam a lei de merge (§4.3): escalar
 * mais profundo vence, listas ({@code publicPaths}/{@code roles}) somam.
 * Cada teste compila um programa Kof e o dirige por sockets reais.
 */
class KofHttpPoliciesE2ETest {

    private static final String JAVA_BIN = java.nio.file.Path.of(
            System.getProperty("java.home"), "bin", "java").toString();

    private final CompilerDriver driver = new CompilerDriver();
    private Process serverProcess;

    @AfterEach
    void stopServer() {
        if (serverProcess != null) {
            serverProcess.destroy();
            try {
                serverProcess.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
            serverProcess.destroyForcibly();
            serverProcess = null;
        }
    }

    private int startServer(Path tempDir, String kofSource) throws IOException {
        int port = freePort();
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, kofSource.replace("PORT", String.valueOf(port)));
        Path outDir = tempDir.resolve("classes");
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(JAVA_BIN, "-cp", outDir.toString(), "Default.Main");
        pb.redirectErrorStream(true);
        serverProcess = pb.start();
        int attempt = 0;
        while (attempt < 40) {
            if (!serverProcess.isAlive()) {
                String out = new String(serverProcess.getInputStream().readAllBytes(),
                        StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
                throw new IOException("server exited early: " + out);
            }
            try (Socket probe = new Socket()) {
                probe.connect(new java.net.InetSocketAddress("127.0.0.1", port), 200);
                return port;
            } catch (IOException e) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            attempt++;
        }
        throw new IOException("server did not start listening");
    }

    private int freePort() throws IOException {
        try (ServerSocket probe = new ServerSocket(0)) {
            return probe.getLocalPort();
        }
    }

    private String request(int port, String raw) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(5000);
            OutputStream out = socket.getOutputStream();
            out.write(raw.getBytes(StandardCharsets.UTF_8));
            out.flush();
            InputStream in = socket.getInputStream();
            StringBuilder response = new StringBuilder();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) != -1) {
                response.append(new String(buffer, 0, n, StandardCharsets.UTF_8));
            }
            return response.toString();
        }
    }

    private String bodyOf(String rawResponse) {
        int idx = rawResponse.indexOf("\r\n\r\n");
        return idx >= 0 ? rawResponse.substring(idx + 4) : rawResponse;
    }

    private String headerLine(String rawResponse, String name) {
        for (String line : rawResponse.split("\r\n")) {
            int colon = line.indexOf(':');
            if (colon > 0 && line.substring(0, colon).equalsIgnoreCase(name)) {
                return line.substring(colon + 1).trim();
            }
        }
        return null;
    }

    /** HS256 JWT no mesmo formato de {@code kof_sec_jwt_create}. */
    private String hs256(String claimsJson, String secret) throws Exception {
        java.util.Base64.Encoder b64 = java.util.Base64.getUrlEncoder().withoutPadding();
        long now = System.currentTimeMillis() / 1000;
        String head = claimsJson.substring(0, claimsJson.lastIndexOf('}')).trim();
        String sep = head.isEmpty() || head.endsWith("{") ? "" : ",";
        String payload = head + sep + "\"iat\":" + now + ",\"exp\":" + (now + 3600) + "}";
        String headerB64 = b64.encodeToString(
                "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payloadB64 = b64.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String sig = b64.encodeToString(mac.doFinal(
                (headerB64 + "." + payloadB64).getBytes(StandardCharsets.UTF_8)));
        return headerB64 + "." + payloadB64 + "." + sig;
    }

    // §4.2/§4.3: o escopo "/admin" adiciona o role exigido SO sob o prefixo;
    // fora dele o default global (sem auth) segue valendo.
    @Test
    void resourceScopeAppliesUnderPrefixOnly(@TempDir Path tempDir) throws Exception {
        int port = startServer(tempDir, """
                main() {
                    auth.secret("s3cret")
                    var app = web.app()
                    app.security()
                    app.policy("/admin", mapOf("roles", "admin"))
                    app.get("/admin/users") { return "admin-users" }
                    app.get("/public") { return "public" }
                    app.listen(PORT)
                }
                """);
        // global default preservado fora do prefixo: GET /public anonimo = 200
        String pub = request(port, "GET /public HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(pub.startsWith("HTTP/1.1 200 OK"), pub);
        assertEquals("public", bodyOf(pub));

        // sob /admin o role e exigido: sem token = 401
        String anon = request(port, "GET /admin/users HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(anon.startsWith("HTTP/1.1 401 Unauthorized"), anon);

        // token sem o role = 403
        String user = hs256("{\"sub\":\"u1\",\"roles\":[\"user\"]}", "s3cret");
        String forbidden = request(port,
                "GET /admin/users HTTP/1.1\r\nHost: x\r\nAuthorization: Bearer " + user + "\r\n\r\n");
        assertTrue(forbidden.startsWith("HTTP/1.1 403 Forbidden"), forbidden);

        // token com o role = 200
        String admin = hs256("{\"sub\":\"u1\",\"roles\":[\"admin\"]}", "s3cret");
        String ok = request(port,
                "GET /admin/users HTTP/1.1\r\nHost: x\r\nAuthorization: Bearer " + admin + "\r\n\r\n");
        assertTrue(ok.startsWith("HTTP/1.1 200 OK"), ok);
        assertEquals("admin-users", bodyOf(ok));
    }

    // §4.3: escalar — o escopo mais profundo vence (headers:false desliga o
    // hardening so no prefixo), o default global segue nas rotas sem match.
    @Test
    void scalarOverrideDeepestWins(@TempDir Path tempDir) throws IOException {
        int port = startServer(tempDir, """
                main() {
                    var app = web.app()
                    app.security()
                    var api = mapOf("headers", false)
                    app.policy("/api", api)
                    app.get("/api/x") { return "api" }
                    app.get("/web") { return "web" }
                    app.listen(PORT)
                }
                """);
        String web = request(port, "GET /web HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(web.startsWith("HTTP/1.1 200 OK"), web);
        assertNotNull(headerLine(web, "Content-Security-Policy"), web);

        String api = request(port, "GET /api/x HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(api.startsWith("HTTP/1.1 200 OK"), api);
        assertNull(headerLine(api, "Content-Security-Policy"),
                "scope headers:false desliga o hardening: " + api);
    }

    // §4.3: listas somam — o publicPath global sobrevive ao merge do escopo "*"
    // (senao o filho o removeria), e o path do escopo tambem fica publico.
    @Test
    void publicPathsAccumulateAcrossScopes(@TempDir Path tempDir) throws IOException {
        int port = startServer(tempDir, """
                main() {
                    var app = web.app()
                    app.security(mapOf("sessionHeader", "authorization", "publicPaths", "/open"))
                    app.policy("*", mapOf("publicPaths", "/api/open"))
                    app.get("/open") { return "open" }
                    app.get("/api/open") { return "api-open" }
                    app.get("/closed") { return "closed" }
                    app.listen(PORT)
                }
                """);
        assertTrue(request(port, "GET /open HTTP/1.1\r\nHost: x\r\n\r\n")
                .startsWith("HTTP/1.1 200 OK"));
        assertTrue(request(port, "GET /api/open HTTP/1.1\r\nHost: x\r\n\r\n")
                .startsWith("HTTP/1.1 200 OK"));
        String closed = request(port, "GET /closed HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(closed.startsWith("HTTP/1.1 401 Unauthorized"), closed);
    }

    // §4.3: roles somam — global "user" + escopo "/admin" "admin"; o escopo
    // nao remove o role global (as duas roles sao exigidas sob /admin).
    @Test
    void rolesAccumulateAcrossScopes(@TempDir Path tempDir) throws Exception {
        int port = startServer(tempDir, """
                main() {
                    auth.secret("s3cret")
                    var app = web.app()
                    app.security(mapOf("roles", "user"))
                    app.policy("/admin", mapOf("roles", "admin"))
                    app.get("/admin/x") { return "ax" }
                    app.get("/other") { return "ot" }
                    app.listen(PORT)
                }
                """);
        String bothToken = hs256("{\"sub\":\"u1\",\"roles\":[\"user\",\"admin\"]}", "s3cret");
        String both = request(port,
                "GET /admin/x HTTP/1.1\r\nHost: x\r\nAuthorization: Bearer " + bothToken + "\r\n\r\n");
        assertTrue(both.startsWith("HTTP/1.1 200 OK"), both);

        String onlyAdmin = hs256("{\"sub\":\"u1\",\"roles\":[\"admin\"]}", "s3cret");
        String missingUser = request(port,
                "GET /admin/x HTTP/1.1\r\nHost: x\r\nAuthorization: Bearer " + onlyAdmin + "\r\n\r\n");
        assertTrue(missingUser.startsWith("HTTP/1.1 403 Forbidden"), missingUser);

        String onlyUser = hs256("{\"sub\":\"u1\",\"roles\":[\"user\"]}", "s3cret");
        String missingAdmin = request(port,
                "GET /admin/x HTTP/1.1\r\nHost: x\r\nAuthorization: Bearer " + onlyUser + "\r\n\r\n");
        assertTrue(missingAdmin.startsWith("HTTP/1.1 403 Forbidden"), missingAdmin);

        // fora do escopo, so o role global vale
        String other = request(port,
                "GET /other HTTP/1.1\r\nHost: x\r\nAuthorization: Bearer " + onlyUser + "\r\n\r\n");
        assertTrue(other.startsWith("HTTP/1.1 200 OK"), other);
    }

    // F6 (gap honesto): app.policy nao existe fora do JVM -> WEB006.
    @Test
    void policyGapOnNativeAndJs(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, """
                main() {
                    var app = web.app()
                    app.policy("/admin", mapOf("roles", "admin"))
                    app.listen(8100)
                }
                """);
        for (Target target : new Target[] {Target.NATIVE, Target.JS}) {
            CompilationResult result = driver.compile(source, tempDir.resolve("out-" + target), target);
            var diagnostics = result.diagnostics().getDiagnostics();
            assertTrue(diagnostics.stream().anyMatch(d -> d.code().equals("WEB006")),
                    "app.policy() deve dar WEB006 no " + target + ", got: " + diagnostics);
        }
    }
}
