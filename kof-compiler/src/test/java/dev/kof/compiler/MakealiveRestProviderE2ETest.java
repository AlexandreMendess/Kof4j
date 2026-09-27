package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fatia MK-1 do tracker 3.1 (D-MAKEALIVE Q2 — providers genéricos): o
 * PROVIDER REST como corpo de usuário. Contrato MEDIDO 20/09 (standalone
 * contra `com.sun.net.httpserver` no MESMO processo do teste, mesma JVM dos
 * dois motores): `http.get/put/delete(url[,body])` devolvem o BODY como
 * String (404 = corpo vazio, SEM throw; falha de conexão = throw propagado
 * nos DOIS motores — paridade honesta); o status vivo vem de
 * `http.status(url)` (requisição de sondagem à parte) — é o que o provider
 * usa para existir/não-existir. O servidor (KV em memória) É o mundo
 * compartilhado entre motores: runA JVM cria e deixa vivo; runB JS lê o
 * mundo criado pelo JVM via `status`+`get` (resettle no-op = leitura fiel
 * cross-engine) e destrói; runC JVM (mundo limpo) == runB byte a byte.
 */
class MakealiveRestProviderE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private record Run(boolean ok, String output) {}

    private Run runJvm(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.JVM);
        if (!r.success()) return new Run(false, diags(r));
        var oldOut = System.out;
        var buf = new ByteArrayOutputStream();
        System.setOut(new java.io.PrintStream(buf, true));
        try {
            var cl = new URLClassLoader(new java.net.URL[]{out.toUri().toURL()},
                    getClass().getClassLoader());
            Class.forName("Default.Main", true, cl)
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            return new Run(true, buf.toString());
        } catch (java.lang.reflect.InvocationTargetException e) {
            return new Run(false, "THROW: " + e.getCause());
        } finally {
            System.setOut(oldOut);
        }
    }

    private Run runJs(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.JS);
        if (!r.success()) return new Run(false, diags(r));
        var buf = new ByteArrayOutputStream();
        try {
            int rc = dev.kof.runtime.KofJsRunner.run(
                    out.resolve("Default.mjs"), buf,
                    new ByteArrayInputStream(new byte[0]), buf);
            return new Run(rc == 0, buf.toString());
        } catch (Exception e) {
            return new Run(false, "THROW: " + e.getMessage() + "\n" + buf);
        }
    }

    private static String diags(CompilationResult r) {
        StringBuilder sb = new StringBuilder();
        r.diagnostics().getDiagnostics().forEach(d -> sb.append(d.message()).append("\n"));
        return sb.toString();
    }

    private static final String PROGRAM = """
        import kof.http
        import kof.makealive

        class RestWorld {
            String base = ""
        }

        String restUrl(RestWorld w, String name) {
            return w.base + "/" + name
        }

        Bool restAlive(RestWorld w, String name) {
            return http.status(restUrl(w, name)) == 200
        }

        Map<String, String> restRead(RestWorld w, Resource r) {
            var m = mapOf()
            if (!restAlive(w, r.name())) { return m }
            var body = http.get(restUrl(w, r.name()))
            if (body.isEmpty()) { return m }
            var lines = body.split("\\n")
            var i = 0
            while (i < lines.length) {
                var ln = lines[i]
                if (!ln.isEmpty()) {
                    var p = ln.indexOf("=")
                    if (p > 0) { m.put(ln.substring(0, p), ln.substring(p + 1)) }
                }
                i = i + 1
            }
            return m
        }

        Bool restSet(RestWorld w, Resource r, Map<String, String> props) {
            var buf = ""
            var ks = props.keys()
            var i = 0
            while (i < ks.size) {
                var k = ks.get(i)
                buf = buf + k + "=" + props.get(k) + "\\n"
                i = i + 1
            }
            http.put(restUrl(w, r.name()), buf)
            return restAlive(w, r.name())
        }

        Bool restDelete(RestWorld w, Resource r) {
            if (!restAlive(w, r.name())) { return true }
            http.delete(restUrl(w, r.name()))
            return !restAlive(w, r.name())
        }

        main() {
            var w = RestWorld()
            w.base = "__URL__"
            var p = Provider(
                (r: Resource) -> restRead(w, r),
                (r: Resource, m: Map<String, String>) -> restSet(w, r, m),
                (r: Resource) -> restDelete(w, r)
            )
            var d = Infrastructure("rest")
            d.resource("kv", "note")
            d.prop("note", "text", "hello")
            var rep = apply(d, State(), p)
            println("applied:" + rep.created.size() + "/" + rep.updated.size() + "/" + rep.deleted.size())
            var rep2 = apply(d, rep.state, p)
            println("resettle:" + rep2.created.size() + "/" + rep2.updated.size() + "/" + rep2.deleted.size())
            if (__DOOM__ == 1) {
                var rep3 = destroy(d, rep2.state, p)
                println("destroyed:" + rep3.deleted.size())
                println("alive-after:" + restAlive(w, "note"))
            }
        }
        """;

    @Test
    void restProviderCrossEngineRoundtripParityJvmJs() throws Exception {
        var store = new ConcurrentHashMap<String, String>();
        var srv = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        srv.createContext("/", ex -> {
            try {
                var key = ex.getRequestURI().getPath();
                var m = ex.getRequestMethod();
                if (m.equals("GET")) {
                    var v = store.get(key);
                    if (v == null) { ex.sendResponseHeaders(404, -1); }
                    else {
                        var b = v.getBytes(StandardCharsets.UTF_8);
                        ex.sendResponseHeaders(200, b.length);
                        ex.getResponseBody().write(b);
                    }
                } else if (m.equals("PUT")) {
                    store.put(key, new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                    ex.sendResponseHeaders(200, -1);
                } else if (m.equals("DELETE")) {
                    ex.sendResponseHeaders(store.remove(key) == null ? 404 : 200, -1);
                } else if (m.equals("HEAD")) {
                    ex.sendResponseHeaders(store.containsKey(key) ? 200 : 404, -1);
                } else {
                    ex.sendResponseHeaders(405, -1);
                }
                ex.close();
            } catch (Exception e) { throw new RuntimeException(e); }
        });
        srv.start();
        try {
            String url = "http://127.0.0.1:" + srv.getAddress().getPort();

            // runA (JVM, sem destroy): cria o mundo REST e o deixa vivo no SERVIDOR.
            Path srcA = tmp.resolve("MkRestA.kf");
            Files.writeString(srcA, PROGRAM.replace("__URL__", url).replace("__DOOM__", "0"));
            Run a = runJvm(srcA, tmp.resolve("o-a"));
            assertTrue(a.ok(), () -> "JVM runA failed: " + a.output());
            assertTrue(a.output().contains("applied:1/0/0"), () -> a.output());
            assertTrue(a.output().contains("resettle:0/0/0"), () -> a.output());
            assertTrue(store.containsKey("/note"), () -> "runA deve deixar o recurso vivo: " + store);

            // runB (JS, com destroy): o provider READ JS vê via HTTP o que o JVM
            // escreveu por HTTP (mundo compartilhado de verdade, entre motores).
            Path srcB = tmp.resolve("MkRestB.kf");
            Files.writeString(srcB, PROGRAM.replace("__URL__", url).replace("__DOOM__", "1"));
            Run b = runJs(srcB, tmp.resolve("o-b"));
            assertTrue(b.ok(), () -> "JS runB failed: " + b.output());
            assertTrue(b.output().contains("resettle:0/0/0"),
                    () -> "JS deve ler o mundo criado pelo JVM via status+get:\\n" + b.output());
            assertTrue(b.output().contains("destroyed:1"), () -> b.output());
            assertTrue(b.output().contains("alive-after:false"), () -> b.output());
            assertTrue(store.isEmpty(), () -> "destroy deve ter esvaziado o servidor: " + store);

            // runC (JVM no MESMO programa da runB, mundo limpo): byte-parity.
            Path srcC = tmp.resolve("MkRestC.kf");
            Files.writeString(srcC, PROGRAM.replace("__URL__", url).replace("__DOOM__", "1"));
            Run c = runJvm(srcC, tmp.resolve("o-c"));
            assertTrue(c.ok(), () -> "JVM runC failed: " + c.output());
            assertEquals(c.output(), b.output(), "3.1 rest-provider golden: JVM/JS diverge");
        } finally {
            srv.stop(0);
        }
    }
}
