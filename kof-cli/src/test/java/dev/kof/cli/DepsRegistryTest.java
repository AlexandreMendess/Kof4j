package dev.kof.cli;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 1.5.3-S2 (D2-A, DECISIONS.md) — o lado PULL do registry: `kof deps` resolve
 * `owner/repo[@ver]` lendo releases no formato exato do `kof deploy --publish`
 * (tar.gz empacotado pelo MESMO `CmdDeploy.writeTarGz`, jar + RELEASE.md +
 * SHA256SUMS conferido). Fake server local — nunca rede real no gate
 * (padrão CmdDeployTest). Faces Q3: happy+classpath, idempotência (2ª resolve
 * não re-baixa), latest→pin, 404=REG001, soma violada=REG002 e nada instalado,
 * pacote sem jar=REG003, sem SHA256SUMS=REG004.
 */
class DepsRegistryTest {

    static CliResult runWithEnv(Path workDir, Map<String, String> env, String... cliArgs)
            throws Exception {
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add("-Duser.home=" + env.get("HOMEOF"));
        cmd.add("dev.kof.cli.Main");
        cmd.addAll(java.util.List.of(cliArgs));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workDir.toFile()).redirectErrorStream(true);
        pb.environment().put("GH_TOKEN", "");        // hermetico: nunca herda/vaza token real
        pb.environment().put("GITHUB_TOKEN", "");
        env.forEach(pb.environment()::put);
        pb.environment().remove("HOMEOF");
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        p.waitFor(180, java.util.concurrent.TimeUnit.SECONDS);
        return new CliResult(p.exitValue(), out);
    }

    record CliResult(int exit, String out) {}



    /** Release D2-A real: jar valido + RELEASE.md + SHA256SUMS + tar.gz do proprio writer. */
    static byte[] buildPackage(Path dir, String repo, String version,
                                       boolean withJar, boolean goodSums, boolean withSums)
            throws Exception {
        Files.createDirectories(dir);
        Path jar = dir.resolve(repo + "-" + version + ".jar");
        if (withJar) {
            try (JarOutputStream jo = new JarOutputStream(Files.newOutputStream(jar))) {
                jo.putNextEntry(new JarEntry("Hello.txt"));
                jo.write("hello-kof".getBytes(StandardCharsets.UTF_8));
                jo.closeEntry();
            }
        } else {
            Files.writeString(dir.resolve("README.txt"), "sem jar");
        }
        Files.writeString(dir.resolve("RELEASE.md"), "# " + repo + " " + version + "\n");
        if (withSums) {
            String sha = (goodSums && withJar)
                    ? CmdDeploy.sha256Hex(jar)
                    : "0".repeat(64);
            Files.writeString(dir.resolve("SHA256SUMS"),
                    sha + "  " + jar.getFileName() + "\n");
        }
        Path tgz = dir.resolve(repo + "-" + version + ".tar.gz");
        List<Path> files = new java.util.ArrayList<>();
        files.add(withJar ? jar.getFileName() : Path.of("README.txt"));
        files.add(Path.of("RELEASE.md"));
        if (withSums) files.add(Path.of("SHA256SUMS"));
        CmdDeploy.writeTarGz(tgz, dir, files, 0644);
        return Files.readAllBytes(tgz);
    }

    // ---- fake registry com o SHAPE REAL da API de Releases do GitHub (#564) ----
    // O fixture antigo era um asset plano {"name","download_url"} — formato que a
    // API real nunca devolve; por isso o pull ficou verde só contra o mock. O real
    // tem `uploader{...}` aninhado (com `url` proprio) ANTES de `browser_download_url`,
    // sem `download_url`, e o binario se baixa pelo `url` do asset (API) com
    // `Accept: application/octet-stream`.

    /** Asset servido pelo fake: id na API + nome publicado. */
    record FakeAsset(long id, String name) {}

    /** Ordem dos campos do asset: a do GitHub, ou invertida (o parser nao pode depender dela). */
    enum Order { GITHUB, REVERSED }

    /** Requisicoes vistas por cada fake (headers relevantes) — prova de contrato HTTP. */
    private static final Map<HttpServer, List<String>> SEEN =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static List<String> seen(HttpServer s) { return SEEN.get(s); }

    private static String hdr(com.sun.net.httpserver.HttpExchange ex, String name) {
        String v = ex.getRequestHeaders().getFirst(name);
        return v == null ? "-" : v;
    }

    /** Release no formato real (chaves e aninhamento medidos na API oficial). */
    private static String githubReleaseJson(String addr, String repo, String version,
                                            List<FakeAsset> assets, Order order) {
        String user = "{\"login\":\"octocat\",\"id\":1,\"node_id\":\"MDQ6VXNlcjE=\","
                + "\"url\":\"https://api.github.com/users/octocat\",\"type\":\"User\"}";
        StringBuilder as = new StringBuilder();
        for (FakeAsset a : assets) {
            java.util.LinkedHashMap<String, String> f = new java.util.LinkedHashMap<>();
            f.put("url", "\"" + addr + "/repos/acme/" + repo + "/releases/assets/" + a.id() + "\"");
            f.put("id", String.valueOf(a.id()));
            f.put("node_id", "\"RA_kwDOx\"");
            f.put("name", "\"" + a.name() + "\"");
            // delimitadores e aspas DENTRO de string (T3): scanner artesanal se desalinha
            f.put("label", "\"texto com } e \\\"aspas\\\" e ] dentro\"");
            f.put("uploader", user);
            f.put("content_type", "\"application/octet-stream\"");
            f.put("state", "\"uploaded\"");
            f.put("size", "1920");
            f.put("digest", "\"sha256:" + "0".repeat(64) + "\"");
            f.put("browser_download_url", "\"" + addr + "/browser/" + a.name() + "\"");
            List<String> keys = new java.util.ArrayList<>(f.keySet());
            if (order == Order.REVERSED) java.util.Collections.reverse(keys);
            StringBuilder one = new StringBuilder("{");
            for (String k : keys) {
                if (one.length() > 1) one.append(',');
                one.append('"').append(k).append("\":").append(f.get(k));
            }
            if (as.length() > 0) as.append(',');
            as.append(one).append('}');
        }
        // `author` (objeto aninhado) vem ANTES de `tag_name`, como na API real
        return "{\"url\":\"" + addr + "/repos/acme/" + repo + "/releases/1\",\"id\":1,"
                + "\"author\":" + user + ",\"tag_name\":\"" + repo + "-" + version + "\","
                + "\"name\":\"" + repo + " " + version + "\",\"draft\":false,\"assets\":["
                + as + "]}";
    }

    static HttpServer serveFakeRegistry(Path dir, String repo, String version,
                                                byte[] tgz, boolean found) throws Exception {
        return serveFakeRegistry(repo, version, tgz, found,
                List.of(new FakeAsset(123, repo + "-" + version + ".tar.gz")),
                Order.GITHUB, false, false);
    }

    static HttpServer serveFakeRegistry(String repo, String version, byte[] tgz,
                                                boolean found, List<FakeAsset> assets,
                                                Order order, boolean viaRedirect,
                                                boolean malformedRelease) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        String addr = "http://127.0.0.1:" + port;
        String cdn = "http://localhost:" + port;   // OUTRO host: o token nao pode segui-lo
        List<String> log = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        SEEN.put(server, log);
        String json = malformedRelease ? "{\"tag_name\":"
                : githubReleaseJson(addr, repo, version, assets, order);
        String meta = "/repos/acme/" + repo + "/releases/";
        server.createContext("/", ex -> {
            String p = ex.getRequestURI().getPath();
            String common = "accept=" + hdr(ex, "Accept") + "|auth=" + hdr(ex, "Authorization")
                    + "|ua=" + hdr(ex, "User-Agent") + "|ver=" + hdr(ex, "X-GitHub-Api-Version");
            try {
                if (found && (p.startsWith(meta + "tags/") || p.startsWith(meta + "latest"))) {
                    log.add("META|" + common);
                    byte[] b = json.getBytes(StandardCharsets.UTF_8);
                    ex.sendResponseHeaders(200, b.length);
                    try (OutputStream os = ex.getResponseBody()) { os.write(b); }
                } else if (found && p.startsWith(meta + "assets/")) {
                    String id = p.substring((meta + "assets/").length());
                    log.add("ASSET|" + id + "|" + common);
                    if (!"application/octet-stream".equals(ex.getRequestHeaders().getFirst("Accept"))) {
                        // sem octet-stream a API real devolve o JSON do asset, nao o binario
                        byte[] b = ("{\"id\":" + id + "}").getBytes(StandardCharsets.UTF_8);
                        ex.sendResponseHeaders(200, b.length);
                        try (OutputStream os = ex.getResponseBody()) { os.write(b); }
                    } else if (viaRedirect) {
                        ex.getResponseHeaders().add("Location", cdn + "/cdn/" + id);
                        ex.sendResponseHeaders(302, -1);
                    } else {
                        ex.sendResponseHeaders(200, tgz.length);
                        try (OutputStream os = ex.getResponseBody()) { os.write(tgz); }
                    }
                } else if (found && p.startsWith("/cdn/")) {
                    log.add("CDN|" + p.substring(5) + "|" + common);
                    ex.sendResponseHeaders(200, tgz.length);
                    try (OutputStream os = ex.getResponseBody()) { os.write(tgz); }
                } else {
                    log.add("OTHER|" + p);   // inclui /browser/...: o cliente de API nao o usa
                    ex.sendResponseHeaders(404, -1);
                }
            } catch (Exception e) {
                ex.sendResponseHeaders(500, -1);
            } finally {
                ex.close();
            }
        });
        server.start();
        return server;
    }

    static Map<String, String> envOf(HttpServer server, Path fakeHome) {
        return Map.of(
                "KOF_REGISTRY_API", "http://127.0.0.1:" + server.getAddress().getPort(),
                "HOMEOF", fakeHome.toString());
    }

    @Test
    void pullResolvesKofReleaseClasspathSeesTheJarAndIsIdempotent(@TempDir Path tmp)
            throws Exception {
        byte[] tgz = buildPackage(tmp.resolve("rel"), "hello", "1.2.3", true, true, true);
        HttpServer server = serveFakeRegistry(tmp, "hello", "1.2.3", tgz, true);
        try {
            Path proj = tmp.resolve("proj");
            Files.createDirectories(proj);
            Path home = tmp.resolve("home");
            Map<String, String> env = envOf(server, home);

            CliResult add = runWithEnv(proj, env, "deps", "add", "acme/hello@1.2.3");
            assertEquals(0, add.exit(), "add aceita formato kof:\n" + add.out());
            assertTrue(Files.readString(proj.resolve("kofdeps")).contains("acme/hello@1.2.3"),
                    "linha kof registrada em kofdeps");

            CliResult r = runWithEnv(proj, env, "deps", "resolve");
            assertEquals(0, r.exit(), "resolve feliz:\n" + r.out());
            Path cached = home.resolve(".kof/deps/kof/acme/hello/1.2.3/hello-1.2.3.jar");
            assertTrue(Files.exists(cached), "jar instalado no cache kof:\n" + r.out());
            String cp = r.out().lines()
                    .filter(l -> l.contains("hello-1.2.3.jar") && !l.startsWith("baixado"))
                    .findFirst().orElse(null);
            assertNotNull(cp, "classpath final traz o jar:\n" + r.out());
            assertTrue(cp.startsWith(cached.toString()), "classpath = caminho do cache: " + cp);

            CliResult r2 = runWithEnv(proj, env, "deps", "resolve");
            assertEquals(0, r2.exit(), r2.out());
            assertFalse(r2.out().contains("baixado"), "2ª resolve NAO re-baixa:\n" + r2.out());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void latestResolvesAndPinsConcreteVersion(@TempDir Path tmp) throws Exception {
        byte[] tgz = buildPackage(tmp.resolve("rel"), "hello", "2.0.0", true, true, true);
        HttpServer server = serveFakeRegistry(tmp, "hello", "2.0.0", tgz, true);
        try {
            Path proj = tmp.resolve("proj");
            Files.createDirectories(proj);
            Path home = tmp.resolve("home");
            Map<String, String> env = envOf(server, home);
            CliResult a = runWithEnv(proj, env, "deps", "add", "acme/hello");
            assertEquals(0, a.exit(), "add aceita formato kof sem versao:\n" + a.out());
            CliResult r = runWithEnv(proj, env, "deps", "resolve");
            assertEquals(0, r.exit(), "latest resolve:\n" + r.out());
            String declared = Files.readString(proj.resolve("kofdeps"));
            assertTrue(declared.contains("acme/hello@2.0.0"),
                    "latest pinou a versao concreta no kofdeps: " + declared);
            assertTrue(Files.exists(home.resolve(".kof/deps/kof/acme/hello/2.0.0/hello-2.0.0.jar")),
                    "jar 2.0.0 instalado");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void missingReleaseIsHonestReg001(@TempDir Path tmp) throws Exception {
        byte[] tgz = buildPackage(tmp.resolve("rel"), "hello", "1.2.3", true, true, true);
        HttpServer server = serveFakeRegistry(tmp, "hello", "1.2.3", tgz, false);
        try {
            Path proj = tmp.resolve("proj");
            Files.createDirectories(proj);
            Map<String, String> env = envOf(server, tmp.resolve("home"));
            runWithEnv(proj, env, "deps", "add", "acme/hello@9.9.9");
            CliResult r = runWithEnv(proj, env, "deps", "resolve");
            assertEquals(1, r.exit(), "tag inexistente falha com exit!=0");
            assertTrue(r.out().contains("REG001"), "diagnostico REG001:\n" + r.out());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void tamperedChecksumRefusesInstallReg002(@TempDir Path tmp) throws Exception {
        byte[] tgz = buildPackage(tmp.resolve("rel"), "hello", "1.2.3", true, false, true);
        HttpServer server = serveFakeRegistry(tmp, "hello", "1.2.3", tgz, true);
        try {
            Path proj = tmp.resolve("proj");
            Files.createDirectories(proj);
            Path home = tmp.resolve("home");
            Map<String, String> env = envOf(server, home);
            runWithEnv(proj, env, "deps", "add", "acme/hello@1.2.3");
            CliResult r = runWithEnv(proj, env, "deps", "resolve");
            assertEquals(1, r.exit(), "soma violada falha");
            assertTrue(r.out().contains("REG002") && r.out().contains("SHA256 mismatch"),
                    "diagnostico REG002 por SHA256 (nao por asset ausente):\n" + r.out());
            assertFalse(Files.exists(home.resolve(".kof/deps/kof/acme/hello/1.2.3/hello-1.2.3.jar")),
                    "nada instalado com checksum quebrado");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void releaseWithoutJarIsReg003(@TempDir Path tmp) throws Exception {
        byte[] tgz = buildPackage(tmp.resolve("rel"), "hello", "1.2.3", false, true, true);
        HttpServer server = serveFakeRegistry(tmp, "hello", "1.2.3", tgz, true);
        try {
            Path proj = tmp.resolve("proj");
            Files.createDirectories(proj);
            Map<String, String> env = envOf(server, tmp.resolve("home"));
            runWithEnv(proj, env, "deps", "add", "acme/hello@1.2.3");
            CliResult r = runWithEnv(proj, env, "deps", "resolve");
            assertEquals(1, r.exit());
            assertTrue(r.out().contains("REG003"), "diagnostico REG003:\n" + r.out());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void releaseWithoutSumsIsReg004(@TempDir Path tmp) throws Exception {
        byte[] tgz = buildPackage(tmp.resolve("rel"), "hello", "1.2.3", true, true, false);
        HttpServer server = serveFakeRegistry(tmp, "hello", "1.2.3", tgz, true);
        try {
            Path proj = tmp.resolve("proj");
            Files.createDirectories(proj);
            Map<String, String> env = envOf(server, tmp.resolve("home"));
            runWithEnv(proj, env, "deps", "add", "acme/hello@1.2.3");
            CliResult r = runWithEnv(proj, env, "deps", "resolve");
            assertEquals(1, r.exit());
            assertTrue(r.out().contains("REG004"), "integrity obrigatoria (REG004):\n" + r.out());
        } finally {
            server.stop(0);
        }
    }

    // ---- #564: contrato contra o shape REAL da API de Releases do GitHub ----

    /** Uma resolve limpa (add + resolve) contra o fake; devolve o CliResult do resolve. */
    private static CliResult pull(Path tmp, HttpServer server, String spec,
                                  Map<String, String> extraEnv) throws Exception {
        Path proj = tmp.resolve("proj");
        Files.createDirectories(proj);
        java.util.HashMap<String, String> env = new java.util.HashMap<>(
                envOf(server, tmp.resolve("home")));
        env.putAll(extraEnv);
        CliResult a = runWithEnv(proj, env, "deps", "add", spec);
        assertEquals(0, a.exit(), "add:\n" + a.out());
        return runWithEnv(proj, env, "deps", "resolve");
    }

    private static boolean jarInstalled(Path tmp, String version) {
        return Files.exists(tmp.resolve("home/.kof/deps/kof/acme/hello/" + version
                + "/hello-" + version + ".jar"));
    }

    @Test
    void realGithubShapeWithNestedUploaderResolves(@TempDir Path tmp) throws Exception {
        // T1: `uploader{...}` (com `url` proprio) vem ANTES de browser_download_url; sem download_url.
        byte[] tgz = buildPackage(tmp.resolve("rel"), "hello", "1.2.3", true, true, true);
        HttpServer server = serveFakeRegistry(tmp, "hello", "1.2.3", tgz, true);
        try {
            CliResult r = pull(tmp, server, "acme/hello@1.2.3", Map.of());
            assertEquals(0, r.exit(), "release no shape real do GitHub deve resolver:\n" + r.out());
            assertTrue(jarInstalled(tmp, "1.2.3"), "jar instalado:\n" + r.out());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void fieldOrderAndDelimitersInsideStringsDoNotMatter(@TempDir Path tmp) throws Exception {
        // T2 (ordem dos campos invertida) + T3 (`}` `]` e aspas escapadas dentro de string).
        byte[] tgz = buildPackage(tmp.resolve("rel"), "hello", "1.2.3", true, true, true);
        HttpServer server = serveFakeRegistry("hello", "1.2.3", tgz, true,
                List.of(new FakeAsset(123, "hello-1.2.3.tar.gz")), Order.REVERSED, false, false);
        try {
            CliResult r = pull(tmp, server, "acme/hello@1.2.3", Map.of());
            assertEquals(0, r.exit(), "ordem invertida deve resolver igual:\n" + r.out());
            assertTrue(jarInstalled(tmp, "1.2.3"), r.out());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void assetIsDownloadedFromApiUrlWithOctetStreamAndPinnedHeaders(@TempDir Path tmp)
            throws Exception {
        // T4 (Accept octet-stream no binario) + T7 (User-Agent e versao da API pinados).
        byte[] tgz = buildPackage(tmp.resolve("rel"), "hello", "1.2.3", true, true, true);
        HttpServer server = serveFakeRegistry(tmp, "hello", "1.2.3", tgz, true);
        try {
            CliResult r = pull(tmp, server, "acme/hello@1.2.3", Map.of());
            assertEquals(0, r.exit(), r.out());
            List<String> log = seen(server);
            String meta = log.stream().filter(l -> l.startsWith("META|")).findFirst().orElse("");
            String asset = log.stream().filter(l -> l.startsWith("ASSET|")).findFirst().orElse("");
            assertTrue(meta.contains("accept=application/vnd.github+json"), "metadata: " + log);
            assertTrue(asset.contains("|123|accept=application/octet-stream"),
                    "binario pelo `url` do asset (API) com octet-stream: " + log);
            for (String l : List.of(meta, asset)) {
                assertTrue(l.contains("ua=kof-cli"), "User-Agent kof-cli: " + l);
                assertTrue(l.contains("ver=2022-11-28"), "X-GitHub-Api-Version pinada: " + l);
            }
            assertTrue(log.stream().noneMatch(l -> l.contains("/browser/")),
                    "cliente de API nao usa browser_download_url: " + log);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void redirectIsFollowedAndTokenNeverLeavesTheApiHost(@TempDir Path tmp) throws Exception {
        // T5 (asset API -> 302 -> CDN -> 200) + T6 (Authorization so no host da API).
        byte[] tgz = buildPackage(tmp.resolve("rel"), "hello", "1.2.3", true, true, true);
        HttpServer server = serveFakeRegistry("hello", "1.2.3", tgz, true,
                List.of(new FakeAsset(123, "hello-1.2.3.tar.gz")), Order.GITHUB, true, false);
        try {
            CliResult r = pull(tmp, server, "acme/hello@1.2.3",
                    Map.of("GH_TOKEN", "kof-test-token-564"));
            assertEquals(0, r.exit(), "redirect deve ser seguido:\n" + r.out());
            assertTrue(jarInstalled(tmp, "1.2.3"), r.out());
            List<String> log = seen(server);
            String meta = log.stream().filter(l -> l.startsWith("META|")).findFirst().orElse("");
            String asset = log.stream().filter(l -> l.startsWith("ASSET|")).findFirst().orElse("");
            String cdn = log.stream().filter(l -> l.startsWith("CDN|")).findFirst().orElse("");
            assertTrue(meta.contains("auth=Bearer kof-test-token-564"), "metadata autenticada: " + meta);
            assertTrue(asset.contains("auth=Bearer kof-test-token-564"), "asset API autenticada: " + asset);
            assertFalse(cdn.isEmpty(), "o redirect chegou ao CDN: " + log);
            assertTrue(cdn.contains("auth=-"), "token NAO vai para outro host no redirect: " + cdn);
            assertFalse(r.out().contains("kof-test-token-564"), "token nunca aparece na saida");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void tarballSelectionKeepsExactThenJvmThenFirstTarGz(@TempDir Path tmp) throws Exception {
        // T8: politica preservada — <repo>-<ver>.tar.gz > <repo>-<ver>-jvm.tar.gz > 1o *.tar.gz.
        byte[] tgz = buildPackage(tmp.resolve("rel"), "hello", "1.2.3", true, true, true);
        List<FakeAsset> all = List.of(new FakeAsset(101, "notes.txt"),
                new FakeAsset(102, "other.tar.gz"),
                new FakeAsset(103, "hello-1.2.3-jvm.tar.gz"),
                new FakeAsset(104, "hello-1.2.3.tar.gz"));
        String[][] cases = {
                {"exact", "104", "0,1,2,3"}, {"jvm", "103", "0,1,2"}, {"first", "102", "0,1"}};
        for (String[] c : cases) {
            List<FakeAsset> offered = new java.util.ArrayList<>();
            for (String i : c[2].split(",")) offered.add(all.get(Integer.parseInt(i)));
            Path t = tmp.resolve(c[0]);
            Files.createDirectories(t);
            HttpServer server = serveFakeRegistry("hello", "1.2.3", tgz, true, offered,
                    Order.GITHUB, false, false);
            try {
                CliResult r = pull(t, server, "acme/hello@1.2.3", Map.of());
                assertEquals(0, r.exit(), c[0] + ":\n" + r.out());
                String asset = seen(server).stream().filter(l -> l.startsWith("ASSET|"))
                        .findFirst().orElse("");
                assertTrue(asset.startsWith("ASSET|" + c[1] + "|"),
                        "selecao " + c[0] + " deve baixar o asset " + c[1] + ": " + seen(server));
            } finally {
                server.stop(0);
            }
        }
    }

    @Test
    void releaseWithoutTarGzAssetIsHonestReg002(@TempDir Path tmp) throws Exception {
        byte[] tgz = buildPackage(tmp.resolve("rel"), "hello", "1.2.3", true, true, true);
        HttpServer server = serveFakeRegistry("hello", "1.2.3", tgz, true,
                List.of(new FakeAsset(101, "notes.txt")), Order.GITHUB, false, false);
        try {
            CliResult r = pull(tmp, server, "acme/hello@1.2.3", Map.of());
            assertEquals(1, r.exit(), "sem .tar.gz falha");
            assertTrue(r.out().contains("REG002") && r.out().contains("no .tar.gz asset"),
                    "REG002 honesto (asset genuinamente ausente):\n" + r.out());
            assertFalse(jarInstalled(tmp, "1.2.3"), "nada instalado");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void malformedReleaseJsonIsHonestReg001(@TempDir Path tmp) throws Exception {
        // Q3 erro esperado: JSON invalido nao pode virar "sem asset" silencioso nem excecao crua.
        byte[] tgz = buildPackage(tmp.resolve("rel"), "hello", "1.2.3", true, true, true);
        HttpServer server = serveFakeRegistry("hello", "1.2.3", tgz, true,
                List.of(new FakeAsset(123, "hello-1.2.3.tar.gz")), Order.GITHUB, false, true);
        try {
            CliResult r = pull(tmp, server, "acme/hello@1.2.3", Map.of());
            assertEquals(1, r.exit(), "JSON invalido falha");
            assertTrue(r.out().contains("REG001") && r.out().contains("invalid"),
                    "REG001 com causa JSON invalido:\n" + r.out());
            assertFalse(r.out().contains("Exception"), "sem stack crua:\n" + r.out());
            assertFalse(jarInstalled(tmp, "1.2.3"), "nada instalado");
        } finally {
            server.stop(0);
        }
    }
}
