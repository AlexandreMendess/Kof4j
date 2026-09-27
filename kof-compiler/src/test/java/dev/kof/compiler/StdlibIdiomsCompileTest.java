package dev.kof.compiler;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 8.5 (plano universal): os exemplos de `training/idioms/stdlib.md` sao
 * compilandos aqui ANTES de virarem doc (sintaxe nao confirmada nao entra no
 * corpus). `facesPerTarget` mede a matriz honesta JVM/SCRIPT/JS/NATIVE de
 * time/cache/log/config/process/net; se um dispatcher mudar a aridade, a
 * secao do doc quebra aqui primeiro.
 */
class StdlibIdiomsCompileTest {

    private final CompilerDriver driver = new CompilerDriver();

    private void probe(String src) throws Exception {
        Path source = Files.createTempFile("probe", ".kf");
        Files.writeString(source, src);
        Path out = Files.createTempDirectory("probeout");
        var r = driver.compile(source, out, Target.JVM);
        assertTrue(r.success(), "DIAG: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void timeDates() throws Exception {
        probe("""
main() {
    val iso = time.todayIso()
    val next = time.addDays(iso, 7)
    val gap = time.diffDays("2026-01-01", "2026-09-19")
    val fmt = time.formatDateIso(2026, 9, 19)
    val leap = time.isLeapYear(2026)
    val dim = time.daysInMonth(2026, 2)
    val dow = time.dayOfWeek(2026, 9, 19)
    val wk = time.isWeekend(2026, 9, 19)
    val bt = time.daysBetween(2026, 1, 1, 2026, 9, 19)
    val td = time.isToday(2026, 9, 19)
    val off = time.tzOffsetSeconds()
    val hrs = time.hoursBetween(2026, 9, 19, 0, 2026, 9, 19, 12)
    val parsed = time.parseDateIso("2026-09-19")
    println("" + fmt + gap + next + leap + dim + dow + wk + bt + td + off + hrs + parsed)
}
""");
    }

    @Test
    void intervalAndSleep() throws Exception {
        probe("""
main() {
    val id = time.interval(1000, () -> println("tick"))
    time.cancel(id)
    time.sleep(10)
    val t = time.now()
    println(t)
}
""");
    }

    @Test
    void processRunSpawn() throws Exception {
        probe("""
main() {
    val r = process.run("echo", "hi", "there")
    println(r.stdout)
    println(r.exitCode)
    val h = process.spawn("echo", "bg")
}
""");
    }

    @Test
    void processRunNoArgsAndExit() throws Exception {
        probe("""
main() {
    val r = process.run("pwd")
    if (r.exitCode != 0) {
        process.exit(1)
    }
    println(r.stdout)
}
""");
    }

    @Test
    void netFull() throws Exception {
        probe("""
main() {
    val u = "https://api.example.com:8080/v1/items?page=2#top"
    val s = net.scheme(u)
    val h = net.host(u)
    val p = net.port(u)
    val pa = net.path(u)
    val q = net.query(u)
    val f = net.fragment(u)
    val enc = net.queryEncode("a b&c")
    val dec = net.queryDecode("a%20b%26c")
    println(s + h + p + pa + q + f + enc + dec)
}
""");
    }

    @Test
    void cacheAndLogAndConfigBasics() throws Exception {
        probe("""
main() {
    cache.set("k", "v")
    cache.set("k2", "v2", 60)
    val v = cache.get("k")
    val t = cache.ttl("k2")
    cache.delete("k")
    cache.clear()
    log.debug("d")
    log.info("boot")
    log.warn("careful")
    log.error("boom")
    println(v + t)
}
""");
    }

    @Test
    void configBasics() throws Exception {
        probe("""
main() {
    val port = config.get("server.port")
    val name = config.str("app.name", "demo")
    val debug = config.bool("app.debug", false)
    val hasIt = config.has("app.name")
    val home = config.env("HOME")
    val req = config.required("db.url")
    println(port + name + debug + hasIt + home + req)
}
""");
    }
    private String probeTarget(String tag, String src, Target t) throws Exception {
        Path source = Files.createTempFile("mt", ".kf");
        Files.writeString(source, src);
        Path out = Files.createTempDirectory("mtout");
        var r = driver.compile(source, out, t);
        return tag + " " + t + " " + (r.success() ? "OK"
                : "GATE " + r.diagnostics().getDiagnostics().stream()
                        .map(d -> d.code()).filter(c -> c != null && !c.isEmpty()).findFirst().orElse("?"));
    }

    @Test
    void facesPerTargetMatrix() throws Exception {
        String srcs = """
main() {
    cache.set("k", "v")
    val v = cache.get("k")
    log.info("x")
    val c = config.get("server.port")
    val leap = time.isLeapYear(2026)
    val n = net.host("https://x.io/a")
    println(v + c + leap + n)
}
""";
        String srcp = """
main() {
    val r = process.run("echo", "hi")
    println(r.stdout)
}
""";
        String srcis = """
main() {
    val id = time.interval(1000, () -> println("tick"))
    time.cancel(id)
    val h = process.spawn("echo", "bg")
    val iso = time.todayIso()
    println(iso)
}
""";
        for (Target t : new Target[] { Target.JVM, Target.SCRIPT, Target.JS, Target.NATIVE }) {
            System.out.println("PROBE " + probeTarget("plain", srcs, t));
            System.out.println("PROBE " + probeTarget("process", srcp, t));
            System.out.println("PROBE " + probeTarget("spawn-interval", srcis, t));
            System.out.println("PROBE " + probeTarget("cache", "main() { cache.set(\"k\", \"v\"); println(cache.get(\"k\")) }\n", t));
            System.out.println("PROBE " + probeTarget("log", "main() { log.info(\"x\") }\n", t));
            System.out.println("PROBE " + probeTarget("config", "main() { println(config.get(\"a.b\")) }\n", t));
            System.out.println("PROBE " + probeTarget("time-date", "main() { println(time.isLeapYear(2026)) }\n", t));
            System.out.println("PROBE " + probeTarget("time-clock", "main() { time.sleep(5); println(time.now()) }\n", t));
            System.out.println("PROBE " + probeTarget("time-interval", "main() { val i = time.interval(1000, () -> println(\"t\")); time.cancel(i) }\n", t));
            System.out.println("PROBE " + probeTarget("net", "main() { println(net.host(\"https://x.io/a\")) }\n", t));
        }
    }

    private static final String SECURITY_SRC = """
main() {
    val h = passwords.hash("s3nh4")
    val ok = passwords.verify("s3nh4", h)
    val digest = crypto.sha256("data")
    val mac = crypto.hmacSha256("key", "msg")
    val key = crypto.randomHex(32)
    val ct = crypto.encryptAesGcm("segredo", key)
    val pt = crypto.decryptAesGcm(ct, key)
    val nonce = crypto.randomHex(16)
    val tok = jwt.create("{\\"sub\\":\\"mel\\"}", "secret")
    val ttlTok = jwt.create("{\\"sub\\":\\"mel\\"}", "secret", 3600)
    val sub = jwt.verify(tok, "secret")
    val strict = jwt.verify(ttlTok, "secret", "iss-x", "aud-y")
    val dbPass = secrets.get("DB_PASSWORD")
    val withDef = secrets.get("PORT", "5432")
    val eq = security.constantTimeEquals(mac, digest)
    val red = security.redact("token=abc")
    val csrf = security.csrfToken()
    val good = security.csrfValid(csrf)
    val allowed = security.rateLimit("ip:1.2.3.4", 10, 60)
    val sid = security.sessionCreate("mel")
    val who = security.sessionGet(sid)
    val dead = security.sessionDestroy(sid)
    val apiKey = security.apiKeyGenerate()
    val validKey = security.apiKeyValid(apiKey)
    val cookie = security.cookieSet("sid", sid, mapOf("HttpOnly", "true"))
    val csp = security.cspHeader()
    val rnd = security.randomInt(100)
    println(h + ok + digest + mac + ct + pt + nonce + tok + ttlTok + sub + strict + dbPass + withDef + eq + red + csrf + good + allowed + who + dead + validKey + cookie + csp + rnd)
}
""";

    @Test
    void securityIdiomsCompileJvm() throws Exception {
        probe(SECURITY_SRC);
    }

    @Test
    void securityFacesPerTarget() throws Exception {
        String hash = "main() { println(passwords.hash(\"x\") + crypto.sha256(\"a\") + crypto.hmacSha256(\"k\",\"m\") + crypto.randomHex(8)) }\n";
        String aes = "main() { val k = crypto.randomHex(32); val c = crypto.encryptAesGcm(\"s\", k); println(crypto.decryptAesGcm(c, k)) }\n";
        String chacha = "main() { val k = crypto.randomHex(32); val c = crypto.encryptChacha20(\"s\", k); println(crypto.decryptChacha20(c, k)) }\n";
        String jwtSrc = "main() { val t = jwt.create(\"{}\", \"k\"); println(jwt.verify(t, \"k\")) }\n";
        String sec = "main() { val s = security.sessionCreate(\"u\"); println(s + security.csrfToken() + security.constantTimeEquals(\"a\",\"b\") + security.rateLimit(\"k\",1,60) + secrets.get(\"X\",\"y\")) }\n";
        String auth = "main() { println(auth.authenticated() + auth.hasRole(\"admin\")) }\n";
        for (Target t : new Target[] { Target.JVM, Target.SCRIPT, Target.JS, Target.NATIVE }) {
            System.out.println("PROBE " + probeTarget("sec-hash", hash, t));
            System.out.println("PROBE " + probeTarget("sec-aesgcm", aes, t));
            System.out.println("PROBE " + probeTarget("sec-chacha", chacha, t));
            System.out.println("PROBE " + probeTarget("sec-jwt", jwtSrc, t));
            System.out.println("PROBE " + probeTarget("sec-session", sec, t));
            System.out.println("PROBE " + probeTarget("sec-only-session", "main() { println(security.sessionCreate(\"u\")) }\n", t));
            System.out.println("PROBE " + probeTarget("sec-only-csrf", "main() { println(security.csrfToken()) }\n", t));
            System.out.println("PROBE " + probeTarget("sec-only-ratelimit", "main() { println(security.rateLimit(\"k\",1,60)) }\n", t));
            System.out.println("PROBE " + probeTarget("sec-only-secrets", "main() { println(secrets.get(\"X\",\"y\")) }\n", t));
            System.out.println("PROBE " + probeTarget("sec-only-cte", "main() { println(security.constantTimeEquals(\"a\",\"b\")) }\n", t));
            System.out.println("PROBE " + probeTarget("sec-only-headers", "main() { println(security.cspHeader() + security.hstsHeader() + security.frameHeader()) }\n", t));
            System.out.println("PROBE " + probeTarget("sec-auth", auth, t));
        }
    }

    @Test
    void securityLabelOrderIsRuntimeTruth() {
        String enc = dev.kof.compiler.StdCatalog.signaturesOf("crypto", "encryptAesGcm").get(0);
        assertTrue(enc.startsWith("encryptAesGcm(String plain, String keyHex64)"),
                "label AES-GCM invertida de novo (runtime = kof_sec_aesgcm_encrypt(plaintext, keyHex)): " + enc);
        String cc = dev.kof.compiler.StdCatalog.signaturesOf("crypto", "encryptChacha20").get(0);
        assertTrue(cc.startsWith("encryptChacha20(String plain, String keyHex)"), cc);
        String cg = dev.kof.compiler.StdCatalog.signaturesOf("security", "cookieGet").get(0);
        assertTrue(cg.startsWith("cookieGet(String cookieHeader, String name)"), cg);
    }

    @Test
    void securityAesGcmRoundTripRuns() throws Exception {
        Path source = Files.createTempFile("rt", ".kf");
        Files.writeString(source, """
main() {
    var key = crypto.randomHex(32)
    var ct = crypto.encryptAesGcm("segredo", key)
    println(crypto.decryptAesGcm(ct, key) == "segredo")
}
""");
        Path out = Files.createTempDirectory("rtout");
        var r = driver.compile(source, out, Target.JVM);
        assertTrue(r.success(), "" + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(TestJdk.javaBin(),
                "-cp", out.toString(), "Default.Main").redirectErrorStream(true).start();
        String o = new String(p.getInputStream().readAllBytes()).trim();
        assertEquals(0, p.waitFor(), o);
        assertEquals("true", o, "doc exemplo round-trip (golden do doc em security.md)");
    }

    // 2.5 (plano universal, fila 19/09): o idiom CI/CD "pipeline como codigo
    // Kof" (training/idioms/automation.md) — TODAS as formas do doc compilam
    // e os dois ouros do runner (verde=exit 0, vermelho=exit!=0) sao MEDIDOS,
    // nunca de memoria (Q3). Faces de schedule/ckpt entram como probe de
    // compilacao por target (os gates nativos CRON001/ORM001 sao runtime).
    private static final String WF_PIPELINE = """
import kof.workflow

main() {
    val compile = job("compile", () -> { println("build ok"); return true })
    val test = job("test", () -> { println("test ok"); return true }).after(compile)
    val flaky = job("flaky", () -> { return false })
    val d = dag(listOf(compile, test, flaky))
    d.retry(flaky, 2, exponential(100, 2))
    d.deadLetter(flaky, (nome: String, motivo: String) -> { println(nome + ": " + motivo); return true })
    val rep = d.run()
    println(rep.summary())
    if (!rep.allOk()) { throw "pipeline red: " + rep.summary() }
}
""";

    @Test
    void automationWorkflowFormsCompile() throws Exception {
        probe(WF_PIPELINE);
        for (Target t : new Target[] { Target.SCRIPT, Target.JS, Target.NATIVE }) {
            System.out.println("PROBE " + probeTarget("wf-pipeline", WF_PIPELINE, t));
        }
        String sched = "import kof.workflow\nmain() { val d = dag(listOf(job(\"j\", () -> true))); println(schedule(d, \"0 3 * * *\")) }\n";
        String ckpt = "import kof.workflow\nmain() { val d = dag(listOf(job(\"j\", () -> true))); println(checkpoint(d, \"jdbc:h2:mem:ck;DB_CLOSE_DELAY=-1\", \"ci\").run().summary()) }\n";
        for (Target t : new Target[] { Target.JVM, Target.SCRIPT, Target.JS, Target.NATIVE }) {
            System.out.println("PROBE " + probeTarget("wf-schedule", sched, t));
            System.out.println("PROBE " + probeTarget("wf-checkpoint", ckpt, t));
        }
    }

    @Test
    void greenPipelineExitsZeroRedPipelineExitsNonZero() throws Exception {
        Path outOk = Files.createTempDirectory("wfok");
        runWf(outOk, """
import kof.workflow

main() {
    val a = job("build", () -> { println("build ok"); return true })
    val b = job("ship", () -> { println("ship ok"); return true }).after(a)
    val rep = dag(listOf(a, b)).run()
    println(rep.summary())
    if (!rep.allOk()) { throw "pipeline red" }
}
""");
        Process ok = new ProcessBuilder(TestJdk.javaBin(),
                "-cp", outOk.toString(), "Default.Main").redirectErrorStream(true).start();
        String oOk = new String(ok.getInputStream().readAllBytes()).trim();
        assertEquals(0, ok.waitFor(), oOk);
        assertEquals("build ok\nship ok\nok=build,ship failed= skipped=", oOk,
                "golden do doc (automation.md) — pipeline verde: sumario + exit 0");

        Path outBad = Files.createTempDirectory("wfbad");
        runWf(outBad, WF_PIPELINE);
        Process bad = new ProcessBuilder(TestJdk.javaBin(),
                "-cp", outBad.toString(), "Default.Main").redirectErrorStream(true).start();
        String oBad = new String(bad.getInputStream().readAllBytes()).trim();
        assertTrue(bad.waitFor() != 0, "pipeline vermelho PRECISA sair com exit!=0 (contrato de CI):\n" + oBad);
        assertTrue(oBad.contains("pipeline red"), "saida do caso vermelho deve nomear a falha (R6):\n" + oBad);
        assertTrue(oBad.contains("failed=flaky") || oBad.contains("tentativas"), "Report tem que expor o job que falhou:\n" + oBad);
    }

    private void runWf(Path out, String src) throws Exception {
        Path source = Files.createTempFile("wf", ".kf");
        Files.writeString(source, src);
        var r = driver.compile(source, out, Target.JVM);
        assertTrue(r.success(), "" + r.diagnostics().getDiagnostics());
    }

    @Test
    void secretsRedactProbe() throws Exception {
        probe("main() { println(secrets.redact(\"tok\") + security.redact(\"tok\")) }\n");
    }

    @Test
    void authIdiomsCompileJvm() throws Exception {
        probe("""
main() {
    val hasSecret = auth.secret("s3m")
    val t = auth.token()
    val in = auth.authenticated()
    val c = auth.claims()
    val u = auth.user()
    val admin = auth.hasRole("admin")
    val perm = auth.hasPermission("read:items")
    val rs = auth.resourceServer("issuer", "aud", "jwks")
    println(hasSecret + t + in + c + u + admin + perm + rs)
}
""");
    }

    @Test
    void observabilityFullFaceCompiles() throws Exception {
        probe("""
main() {
    val h = observability.health()
    val rdy = observability.readiness()
    val live = observability.liveness()
    val n = observability.counter("hits")
    observability.increment("hits", 1)
    observability.gauge("queue", 3)
    observability.histogram("lat", 42)
    val dump = observability.metrics()
    val req = observability.requestId()
    val cor = observability.correlationId()
    val tr = observability.traceId()
    val sp = observability.spanId()
    println(h + rdy + live + n + dump + req + cor + tr + sp)
}
""");
    }

    @Test
    void gpuProbeAndKernelFacesCompile() throws Exception {
        probe("""
main() {
    val ok = gpu.available()
    val why = gpu.failReason()
    var a = new Int[4]
    var b = new Int[4]
    var c = new Int[4]
    val rc = gpu.dispatchMatmul(a, b, c, 2, 2, 2)
    println(ok + why + rc)
}
""");
    }

    @Test
    void mediaFacesCompile() throws Exception {
        probe("""
main() {
    val img = Image.open("x.png")
    val wav = Audio.openWav("y.wav")
    val vid = Video.open("z.mp4")
    val mic = Mic.record(1)
    val devs = Mic.list()
    println(img.toString() + wav.toString() + vid.toString() + mic.toString() + devs.size)
}
""");
    }

    @Test
    void fatiaThreeFacesPerTarget() throws Exception {
        String obs = "main() { println(observability.health() + observability.increment(\"h\",1)) }\n";
        String gpu = "main() { println(gpu.available() + gpu.failReason()) }\n";
        String gpuMat = "main() { var a = new Int[4]; println(gpu.dispatchMatmul(a, a, a, 2, 2, 2)) }\n";
        String media = "main() { val img = Image.open(\"x.png\"); println(img.toString()) }\n";
        String mic = "main() { println(Mic.list().size) }\n";
        for (Target t : new Target[] { Target.JVM, Target.SCRIPT, Target.JS, Target.NATIVE }) {
            System.out.println("PROBE " + probeTarget("obs", obs, t));
            System.out.println("PROBE " + probeTarget("gpu-probe", gpu, t));
            System.out.println("PROBE " + probeTarget("gpu-matmul", gpuMat, t));
            System.out.println("PROBE " + probeTarget("media-image", media, t));
            System.out.println("PROBE " + probeTarget("media-mic", mic, t));
        }
    }
}