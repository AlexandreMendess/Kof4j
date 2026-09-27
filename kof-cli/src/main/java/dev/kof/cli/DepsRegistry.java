package dev.kof.cli;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * 1.5.3-S2 (D2-A, D-POLL-19) — o lado PULL do registry: `kof deps` consome
 * releases publicadas por `kof deploy --publish` no GitHub Releases
 * (host oficial do MVP). Uma linha `owner/repo[@ver]` no {@code kofdeps}
 * resolve para o tar.gz da release {@code <repo>-<ver>} (ou a mais recente
 * sem @ver), extrai o jar, CONFERE o {@code SHA256SUMS} embutido no pacote
 * e instala em {@code <cache>/kof/<owner>/<repo>/<ver>/}. Nada é silencioso
 * (R6): os códigos {@code REG00x} marcam cada face não-atendida.
 */
final class DepsRegistry {

    private DepsRegistry() {}

    static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15)).build();

    static String apiBase() {
        String o = System.getenv("KOF_REGISTRY_API");
        return (o == null || o.isBlank()) ? "https://api.github.com" : o;
    }

    /** `owner/repo[@ver]` — o formato kof do registry (distinto do `g:a:v` Maven). */
    static boolean isRegistrySpec(String s) {
        return s != null && s.matches("[A-Za-z0-9._-]+/[A-Za-z0-9._-]+(@[A-Za-z0-9._+-]+)?");
    }

    /** Valida e normaliza a forma kof; null se não é `owner/repo[@ver]`. */
    static String normalize(String spec) {
        if (!isRegistrySpec(spec)) return null;
        return spec.trim();
    }

    static String ownerOf(String spec) { return spec.substring(0, spec.indexOf('/')); }

    static String repoOf(String spec) {
        String r = spec.substring(spec.indexOf('/') + 1);
        int at = r.indexOf('@');
        return at < 0 ? r : r.substring(0, at);
    }

    /** null = "latest". */
    static String versionOf(String spec) {
        int at = spec.indexOf('@');
        return at < 0 ? null : spec.substring(at + 1);
    }

    /** Seam de teste: {@code kof.deps.home} aponta o cache p/ fora do $HOME. */
    static Path kofCacheRoot() {
        return Deps.cacheDir().resolve("kof");
    }

    static Path jarPath(String owner, String repo, String version) {
        return kofCacheRoot().resolve(owner).resolve(repo).resolve(version)
                .resolve(repo + "-" + version + ".jar");
    }

    /**
     * Resolve a spec kof para o jar no cache (idempotente — segunda chamada
     * não toca na rede). Lança IOException com código REG00x honesto.
     */
    static Path fetch(String spec) throws IOException {
        String owner = ownerOf(spec);
        String repo = repoOf(spec);
        String ver = versionOf(spec);
        if (ver != null) {
            Path cached = jarPath(owner, repo, ver);
            if (installed(cached)) return cached;
        }
        String base = apiBase();
        String releaseUrl = ver == null
                ? base + "/repos/" + owner + "/" + repo + "/releases/latest"
                : base + "/repos/" + owner + "/" + repo + "/releases/tags/" + repo + "-" + ver;
        HttpResponse<String> res = getJson(releaseUrl);
        if (res.statusCode() == 404) {
            throw new IOException("REG001: release not found on registry: " + owner + "/" + repo
                    + (ver == null ? " (latest)" : " tag " + repo + "-" + ver)
                    + " (private repo without GITHUB_TOKEN? unpublished?)");
        }
        require2xx(res.statusCode(), res.body(), "read release " + owner + "/" + repo);
        ReleaseMeta release = parseRelease(res.body(), owner + "/" + repo);
        String tag = release.tag();
        String version = ver != null ? ver : stripTagPrefix(tag, repo);
        if (version == null || version.isEmpty()) {
            throw new IOException("REG001: release has no parsable version tag: " + tag);
        }
        Path cached = jarPath(owner, repo, version);
        if (installed(cached)) return cached;   // latest aponta p/ já instalado
        Asset asset = pickTarball(release, repo, version);
        if (asset == null) {
            throw new IOException("REG002: release " + tag + " has no .tar.gz asset (publish a"
                    + " `kof deploy --publish` release first)");
        }
        Path tmpDir = Files.createTempDirectory("kofdep-reg");
        Path dl = null;
        try {
            dl = downloadAsset(asset.apiUrl());
            verifyProvenance(owner, repo, tag, release, asset, dl);   // D-ARTIFACT-TRUST: antes de tocar no conteudo
            extractTarGz(dl, tmpDir);
            Path sums = tmpDir.resolve("SHA256SUMS");
            if (!Files.exists(sums)) {
                throw new IOException("REG004: package has no SHA256SUMS (integrity is not"
                        + " optional) — refusing to install " + owner + "/" + repo);
            }
            Path jar = findJar(tmpDir);
            // #566 (b): o pacote e consumido como MODULO-FONTE — o jar e opcional numa biblioteca
            if (jar == null && !Files.isDirectory(tmpDir.resolve(DepsSources.DIR))) {
                throw new IOException("REG003: package has no .jar and no sources (non-JVM face?) — "
                        + owner + "/" + repo + "@" + version);
            }
            if (jar != null) verifyChecksum(sums, jar);
            DepsSources.install(sums, tmpDir, cached.getParent());   // fontes verificadas; nada se falha
            if (jar != null) {
                Files.createDirectories(cached.getParent());
                Files.move(jar, cached, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            if (dl != null) Files.deleteIfExists(dl);
            deleteTree(tmpDir);
        }
        System.out.println("baixado " + owner + "/" + repo + "@" + version);
        return cached;
    }

    // ---- release do GitHub: JSON ESTRUTURAL (Json.parse, o mesmo leitor do resto do CLI) ----
    // #564: o scanner anterior fatiava o texto por posição (1ª `}` e chave `download_url`) e só
    // funcionava contra um mock plano; a API real aninha `uploader{...}` no asset e não tem
    // `download_url`. Campos desconhecidos e a ordem das chaves são irrelevantes aqui.

    /** Asset publicado: nome + `url` da API (o binário sai daí, com `Accept: octet-stream`). */
    record Asset(String name, String apiUrl) {}

    record ReleaseMeta(String tag, List<Asset> assets) {}

    /** Lê `tag_name` e `assets[*].{name,url}`; JSON inválido/inesperado = REG001 honesto. */
    static ReleaseMeta parseRelease(String json, String what) throws IOException {
        Object root;
        try {
            root = Json.parse(json);
        } catch (IllegalArgumentException e) {
            throw new IOException("REG001: invalid release JSON from registry for " + what
                    + " (" + e.getMessage() + ")", e);
        }
        if (!(root instanceof Map<?, ?> rel)) {
            throw new IOException("REG001: invalid release JSON from registry for " + what
                    + " (expected an object)");
        }
        String tag = rel.get("tag_name") instanceof String t ? t : null;
        List<Asset> assets = new ArrayList<>();
        if (rel.get("assets") instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> a && a.get("name") instanceof String n
                        && a.get("url") instanceof String u) {
                    assets.add(new Asset(n, u));
                }
            }
        }
        return new ReleaseMeta(tag, assets);
    }

    /** O tar.gz da face: `<repo>-<ver>.tar.gz` exato; senão `-jvm`; senão o primeiro .tar.gz. */
    static Asset pickTarball(ReleaseMeta release, String repo, String version) {
        List<Asset> assets = release.assets();
        String exact = repo + "-" + version + ".tar.gz";
        for (Asset a : assets) if (a.name().equals(exact)) return a;
        String multi = repo + "-" + version + "-jvm.tar.gz";
        for (Asset a : assets) if (a.name().equals(multi)) return a;
        for (Asset a : assets) if (a.name().endsWith(".tar.gz")) return a;
        return null;
    }

    /** Sufixos aceitos do bundle de atestacao ao lado do tar.gz (nome exato; a confirmar com a lane CI). */
    private static final List<String> EVIDENCE_SUFFIXES = List.of(".sigstore.json", ".intoto.jsonl", ".jsonl");

    static Asset pickEvidence(ReleaseMeta release, Asset tarball) {
        for (String suffix : EVIDENCE_SUFFIXES) {
            for (Asset a : release.assets()) if (a.name().equals(tarball.name() + suffix)) return a;
        }
        return null;
    }

    /**
     * D-ARTIFACT-TRUST (c): confere a proveniencia do tar.gz baixado contra o PEDIDO (owner/repo@tag),
     * nao contra o que a release declara. Oficial sem evidencia valida = REG005..REG008 e nada instala;
     * comunitario = aviso honesto. O commit da tag so e resolvido (rede) quando ha evidencia a amarrar.
     */
    private static void verifyProvenance(String owner, String repo, String tag, ReleaseMeta release,
                                         Asset tarball, Path downloaded) throws IOException {
        Asset ev = pickEvidence(release, tarball);
        Path bundle = null;
        try {
            if (ev != null) bundle = downloadAsset(ev.apiUrl());
            String commit = ev == null ? null : RegistryTags.commitOf(owner, repo, tag);
            TrustGate.check(owner, repo, tag, downloaded, bundle, TrustGate.verifier(), commit,
                    TrustGate.enforcing());
        } finally {
            if (bundle != null) Files.deleteIfExists(bundle);
        }
    }

    private static String stripTagPrefix(String tag, String repo) {
        if (tag == null) return null;
        String p = repo + "-";
        return tag.startsWith(p) ? tag.substring(p.length()) : tag;
    }

    // ---- tar.gz (leitor simétrico ao CmdDeploy.writeTarGz — ustar mínimo) ----

    private static void extractTarGz(Path tgz, Path dir) throws IOException {
        try (InputStream in = new GZIPInputStream(Files.newInputStream(tgz))) {
            byte[] header = new byte[512];
            while (true) {
                if (!readFully(in, header)) break;
                boolean blank = true;
                for (byte b : header) if (b != 0) { blank = false; break; }
                if (blank) break;
                String name = cstr(header, 0, 100);
                long size = octal(header, 124, 12);
                char type = (char) (header[156] & 0xff);
                long blocks = (size + 511) / 512;
                if (type == '0' || type == 0) {
                    byte[] data = readN(in, (int) size);
                    Path out = dir.resolve(name).normalize();
                    if (!out.startsWith(dir)) {
                        throw new IOException("REG004: unsafe path in package: " + name);
                    }
                    Files.createDirectories(out.getParent());
                    Files.write(out, data);
                } else {
                    in.skipNBytes(blocks * 512);
                }
                long rest = blocks * 512 - size;
                if (rest > 0) in.skipNBytes(rest);
            }
        }
    }

    /** Versão já instalada: o jar (pacote de aplicação) OU as fontes (pacote-biblioteca, #566). */
    private static boolean installed(Path jar) {
        return Files.exists(jar) || DepsSources.hasSources(jar.getParent());
    }

    private static Path findJar(Path dir) throws IOException {
        Path sources = dir.resolve(DepsSources.DIR);
        try (var walk = Files.walk(dir)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> !p.startsWith(sources))     // um .jar dentro de src/ nunca e "o" jar
                    .filter(p -> p.getFileName().toString().endsWith(".jar"))
                    .sorted().findFirst().orElse(null);
        }
    }

    /** Linha do jar em SHA256SUMS (formato `coreutils`: `<hex>  <nome>`). */
    private static void verifyChecksum(Path sumsFile, Path jar) throws IOException {
        String base = jar.getFileName().toString();
        for (String line : Files.readAllLines(sumsFile, StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            String[] parts = line.split("\\s+", 2);
            if (parts.length != 2) continue;
            String name = parts[1].trim().replaceFirst("^\\*?", "");
            int slash = name.lastIndexOf('/');
            if (slash >= 0) name = name.substring(slash + 1);
            if (!name.equals(base)) continue;
            String actual = CmdDeploy.sha256Hex(jar);
            if (!actual.equalsIgnoreCase(parts[0].trim())) {
                throw new IOException("REG002: SHA256 mismatch for " + base + " (package is"
                        + " corrupt or was tampered after publish — expected " + parts[0].trim()
                        + ", got " + actual + ")");
            }
            return;
        }
        throw new IOException("REG004: " + base + " not listed in SHA256SUMS (integrity"
                + " unverifiable) — refusing to install");
    }

    // ---- http/dirs helpers ----

    /** Versão da API de Releases pinada: uma mudança implícita não altera o cliente sem revisão. */
    private static final String API_VERSION = "2022-11-28";

    /** Sem redirect automático: o salto para o CDN é manual, para o token não segui-lo. */
    private static final HttpClient NO_REDIRECT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(15)).build();

    private static String token() {
        String token = System.getenv("GH_TOKEN");
        if (token == null || token.isBlank()) token = System.getenv("GITHUB_TOKEN");
        return (token == null || token.isBlank()) ? null : token;
    }

    /** Cabeçalhos exigidos/pinados pela API de Releases (User-Agent é obrigatório). */
    private static HttpRequest.Builder request(URI uri, String accept, boolean withToken) {
        HttpRequest.Builder rb = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(60))
                .header("Accept", accept)
                .header("User-Agent", "kof-cli")
                .header("X-GitHub-Api-Version", API_VERSION);
        String token = token();
        if (withToken && token != null) rb.header("Authorization", "Bearer " + token);
        return rb;
    }

    /** Metadata da release (JSON). */
    static HttpResponse<String> getJson(String url) throws IOException {
        try {
            return HTTP.send(request(URI.create(url), "application/vnd.github+json", true).build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("registry fetch interrupted", e);
        } catch (java.net.ConnectException e) {
            throw new IOException("cannot reach the registry endpoint: " + e.getMessage(), e);
        }
    }

    /**
     * Baixa o binário pelo `url` do asset (API) com `Accept: application/octet-stream`: a API
     * responde 200 direto ou 302 para o armazenamento. O redirect é seguido à mão e o
     * {@code Authorization} só vai para a origem da API — nunca para o host do CDN.
     */
    private static Path downloadAsset(String apiUrl) throws IOException {
        URI uri = URI.create(apiUrl);
        String apiOrigin = originOf(uri);
        for (int hop = 0; hop < 5; hop++) {
            Path tmp = Files.createTempFile("kofdep-dl", ".bin");
            try {
                HttpResponse<Path> res = NO_REDIRECT.send(
                        request(uri, "application/octet-stream", originOf(uri).equals(apiOrigin))
                                .build(),
                        HttpResponse.BodyHandlers.ofFile(tmp));
                int sc = res.statusCode();
                if (sc == 200) return tmp;
                Files.deleteIfExists(tmp);
                if (sc == 301 || sc == 302 || sc == 303 || sc == 307 || sc == 308) {
                    String loc = res.headers().firstValue("Location").orElse(null);
                    if (loc == null) {
                        throw new IOException("REG002: asset redirect without Location: " + apiUrl);
                    }
                    uri = uri.resolve(loc);
                    continue;
                }
                throw new IOException("REG002: asset download failed: HTTP " + sc + " " + apiUrl);
            } catch (InterruptedException e) {
                Files.deleteIfExists(tmp);
                Thread.currentThread().interrupt();
                throw new IOException("registry fetch interrupted", e);
            } catch (java.net.ConnectException e) {
                Files.deleteIfExists(tmp);
                throw new IOException("cannot reach the registry endpoint: " + e.getMessage(), e);
            } catch (IOException e) {
                Files.deleteIfExists(tmp);
                throw e;
            }
        }
        throw new IOException("REG002: too many redirects downloading asset " + apiUrl);
    }

    private static String originOf(URI u) {
        return u.getScheme() + "://" + u.getHost() + ":" + u.getPort();
    }

    private static void require2xx(int status, String body, String what) throws IOException {
        if (status / 100 != 2) {
            String s = body == null ? "" : body.replace("\n", " ");
            throw new IOException("registry " + what + " failed: HTTP " + status
                    + (s.length() > 160 ? s.substring(0, 160) + "…" : " " + s));
        }
    }

    private static boolean readFully(InputStream in, byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) {
            int r = in.read(buf, off, buf.length - off);
            if (r < 0) return off == 0 ? false : throwEof();
            off += r;
        }
        return true;
    }

    private static boolean throwEof() throws IOException {
        throw new IOException("REG002: truncated tar.gz (bad download)");
    }

    private static byte[] readN(InputStream in, int n) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(n);
        int off = 0;
        byte[] chunk = new byte[Math.min(n, 8192)];
        while (off < n) {
            int r = in.read(chunk, 0, Math.min(chunk.length, n - off));
            if (r < 0) throw new IOException("REG002: truncated tar entry");
            bos.write(chunk, 0, r);
            off += r;
        }
        return bos.toByteArray();
    }

    private static String cstr(byte[] b, int off, int len) {
        int end = off;
        while (end < off + len && b[end] != 0) end++;
        return new String(b, off, end - off, StandardCharsets.UTF_8);
    }

    private static long octal(byte[] b, int off, int len) {
        long v = 0;
        for (int i = off; i < off + len; i++) {
            byte c = b[i];
            if (c == 0 || c == ' ') { if (v > 0) break; else continue; }
            if (c < '0' || c > '7') continue;
            v = v * 8 + (c - '0');
        }
        return v;
    }

    private static void deleteTree(Path dir) throws IOException {
        try (var walk = Files.walk(dir)) {
            for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }
}
