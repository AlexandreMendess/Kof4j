package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * D-KOF-NET fatia 4a (plan {@code docs/stdlib/network-kofnet-plan.md}): o
 * front {@code kof.net} roda no alvo JS (GraalJS embarcado no JVM) sobre a
 * ponte de host {@link dev.kof.runtime.KofJsNetBridge}, que usa o MESMO
 * {@code java.net} do runtime JVM. O oráculo é o da fatia 2/3: eco de bytes
 * sobre loopback real.
 *
 * <p>O alvo JS não expõe concorrência real: {@code spawn} é uma Promise de
 * mesma thread ({@code JsRuntimeUiLayout.kofSpawn}), então um servidor TCP com
 * {@code accept()} bloqueante dentro de {@code spawn} NÃO corre em paralelo —
 * por isso a prova JS é UDP round-trip (sem concorrência) + os casos de recusa
 * nomeada (NET003/NET004) + EOF/resolve. O servidor TCP real é coberto pelo
 * {@code NetTcpE2ETest} (JVM) e {@code NetNativeE2ETest} (Native).
 */
class NetJsE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJs(String src, Path dir, String name) throws Exception {
        Path s = dir.resolve(name + ".kf");
        Files.writeString(s, src);
        Path out = dir.resolve("out-" + name);
        CompilationResult r = driver.compile(s, out, Target.JS);
        assertTrue(r.success(), name + " must compile: " + r.diagnostics().getDiagnostics());
        Path entry;
        try (var walk = Files.walk(out)) {
            entry = walk.filter(q -> q.getFileName().toString().equals("Default.mjs"))
                    .findFirst().orElseThrow();
        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(entry, buf,
                new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream());
        assertEquals(0, ec, name + " js exit " + ec + " out: " + buf);
        return buf.toString(StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
    }

    @Test
    @DisplayName("JS UDP: bind + sendTo + receive + peer() over REAL loopback (host bridge)")
    void jsUdpDatagramRoundTrip(@TempDir Path dir) throws Exception {
        String out = runJs("""
                main() {
                    var server = net.bind(19932)
                    var client = net.bind(19933)
                    var datagram = new Byte[3]
                    datagram[0] = 9
                    datagram[1] = 8
                    datagram[2] = 7
                    client.sendTo("127.0.0.1:19932", datagram)
                    var got = server.receive(4096)
                    print("server-got=")
                    for (var b in got) {
                        print("" + b + ",")
                    }
                    println("")
                    println("server-peer=" + server.peer())
                    server.sendTo(server.peer(), datagram)
                    var reply = client.receive(4096)
                    println("reply-len=" + reply.length)
                    client.close()
                    server.close()
                }
                """, dir, "udp");

        assertTrue(out.contains("server-got=9,8,7,"), "datagram must arrive intact: " + out);
        assertTrue(out.contains("server-peer=127.0.0.1:19933"),
                "peer() must be the real source host:port: " + out);
        assertTrue(out.contains("reply-len=3"), "reply must come back: " + out);
    }

    @Test
    @DisplayName("JS UDP 64 KiB bound: oversized datagram REFUSES with NET003")
    void jsOversizedDatagramRefused(@TempDir Path dir) throws Exception {
        String out = runJs("""
                main() {
                    var e = net.bind(19934)
                    try {
                        e.sendTo("127.0.0.1:9", new Byte[65508])
                        println("NO-REFUSAL")
                    } catch (String m) {
                        println("refused:" + m)
                    }
                    e.close()
                }
                """, dir, "toobig");

        assertTrue(out.contains("refused"), "oversized datagram must refuse: " + out);
        assertTrue(out.contains("NET003"), "refusal must name NET003: " + out);
        assertTrue(!out.contains("NO-REFUSAL"), "no transparent fragmentation: " + out);
    }

    @Test
    @DisplayName("JS UDP: a malformed address is refused by name (NET004)")
    void jsBadAddressRefused(@TempDir Path dir) throws Exception {
        String out = runJs("""
                main() {
                    var e = net.bind(19935)
                    try {
                        e.sendTo("no-colon-here", new Byte[1])
                        println("NO-REFUSAL")
                    } catch (String m) {
                        println("refused:" + m)
                    }
                    e.close()
                }
                """, dir, "badaddr");

        assertTrue(out.contains("refused"), "a malformed host:port must refuse: " + out);
        assertTrue(out.contains("NET004"), "refusal must name NET004: " + out);
    }

    @Test
    @DisplayName("JS: every verb resolves at host-call time (no undefined kofNet* export)")
    void everyVerbResolves(@TempDir Path dir) throws Exception {
        // Se algum wrapper kofNet* não existisse no runtime JS emitido, o módulo
        // morreria em ReferenceError na avaliação. `peer()` antes de qualquer
        // receive devolve "" (nunca null) e close é polimórfico.
        String out = runJs("""
                main() {
                    var e = net.bind(19936)
                    println("peer-empty=[" + e.peer() + "]")
                    e.close()
                    println("verbs-live")
                }
                """, dir, "live");

        assertTrue(out.contains("peer-empty=[]"),
                "peer() before any receive is the empty string, never null: " + out);
        assertTrue(out.contains("verbs-live"), "every verb must resolve: " + out);
    }

    @Test
    @DisplayName("JS TCP: connect to a closed port fails by name, never silently")
    void jsConnectRefusedIsNotSilent(@TempDir Path dir) throws Exception {
        // Porta efêmera improvável: connect deve LANÇAR (erro do host), nunca
        // devolver handle falso. Prova o caminho connect do bridge.
        String out = runJs("""
                main() {
                    try {
                        var c = net.connect("127.0.0.1", 1)
                        println("NO-REFUSAL")
                        c.close()
                    } catch (String m) {
                        println("refused")
                    }
                }
                """, dir, "connect");

        assertTrue(out.contains("refused"), "connect to a dead port must refuse: " + out);
        assertTrue(!out.contains("NO-REFUSAL"), "connect must not fabricate a handle: " + out);
    }

    @Test
    @DisplayName("JS: net.* URI accessors keep the NET001 oracle (no regression from the bridge)")
    void jsUriAccessorsUnchanged(@TempDir Path dir) throws Exception {
        String out = runJs("""
                main() {
                    println(net.scheme("http://example.com/a/b?x=1#frag") + "|" + net.host("http://example.com/a/b?x=1#frag"))
                }
                """, dir, "uri");
        assertEquals("http|example.com", out);
    }
}
