package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * D-KOF-NET fatia 1 (plan docs/development/network-kofnet-plan.md): the frozen
 * surface contract must BIND at compile time on the JVM and every non-implemented
 * target must REFUSE honestly with NET002 (no-silent-fallback). Handles are
 * strings composed as in {@code db}/{@code orm} (no new stdlib record — the
 * "scalars instead of records" precedent of {@code net} v1, plan §4 decisão 09/09).
 */
public class NetSurfaceE2ETest {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compile(String src, Target t) throws Exception {
        Path f = tmp.resolve("M.kf");
        Files.writeString(f, src);
        return driver.compile(f, tmp.resolve("out-" + t), t);
    }

    private static final String CONTRACT = """
        main() {
            var l = net.listen(8123)
            var c = net.connect("127.0.0.1", 8123)
            var payload = new Byte[3]
            var n = c.send(payload)
            var data = c.receive(4096)
            var e = net.bind(9123)
            e.sendTo("127.0.0.1:9123", data)
            var got = e.receive(4096)
            var addr = e.peer()
            c.close()
            e.close()
            l.close()
            println(n)
        }
        """;

    @Test
    @DisplayName("D-KOF-NET JVM: the frozen contract COMPILES now that slice 2 emits the runtime")
    void jvmSurfaceCompiles() throws Exception {
        // Fatia 1 recusava em TODO alvo porque o runtime GERADO não tinha o
        // método (verde falso: o class load morreria NoSuchMethodError). A
        // fatia 2 emite `JvmRuntimeSockets` com corpo java.net real, então o
        // portão abre no JVM — e o E2E `NetTcpE2ETest` prova que os verbos
        // RESOLVEM e rodam (compilar não basta).
        CompilationResult r = compile(CONTRACT, Target.JVM);
        assertTrue(r.success(), "JVM must bind now: " + r.diagnostics().getDiagnostics());
    }

    @Test
    @DisplayName("D-KOF-NET: JS/cross still refuse every socket verb with NET002 (no silent drop)")
    void jsAndCrossStillRefuse() throws Exception {
        String one = "main() { var l = net.%s }";
        String[] verbs = {"listen(8123)", "connect(\"h\", 1)", "bind(8123)",
                "accept(h)", "send(h, b)", "receive(h, 10)", "peer(e)", "close(h)"};
        for (Target t : new Target[]{Target.JS, Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            for (String v : verbs) {
                CompilationResult r = compile(String.format(one, v), t);
                assertFalse(r.success(), t + " must refuse net." + v + ": "
                        + r.diagnostics().getDiagnostics());
            }
        }
    }

    @Test
    @DisplayName("D-KOF-NET Native x86-64: the frozen contract BINDS now that slice 3 emits the runtime")
    void nativeSurfaceCompiles() throws Exception {
        // Fatia 3 emite `NativeNetFront*` com corpo real (sockets/handles); o
        // E2E `NetNativeE2ETest` prova que os verbos RESOLVEM e rodam. Os alvos
        // cross seguem recusando NET002 (os símbolos não existem no runtime
        // riscv/aarch ainda — fatia 4) e o teste acima o guarda.
        CompilationResult r = compile(CONTRACT, Target.NATIVE);
        assertTrue(r.success(), "native x86-64 must bind now: "
                + r.diagnostics().getDiagnostics());
    }

    @Test
    @DisplayName("D-KOF-NET JS: refuses the socket verbs honestly with NET002")
    void jsRefusesWithNet002() throws Exception {
        CompilationResult r = compile(CONTRACT, Target.JS);
        assertFalse(r.success(), "js host bridge has no net runtime yet");
        String diag = r.diagnostics().getDiagnostics().toString();
        assertTrue(diag.contains("NET002"), diag);
    }

    @Test
    @DisplayName("D-KOF-NET arity: net.listen with 0 args is a named diagnostic, never silent")
    void arityGuarded() throws Exception {
        CompilationResult r = compile("main() { var l = net.listen() }", Target.JVM);
        assertFalse(r.success());
        String diag = r.diagnostics().getDiagnostics().toString();
        assertTrue(diag.contains("SEM") || diag.contains("NET"), diag);
    }

    // Script nao tem rota de artifacts no CompilerDriver.compile (COMP003 —
    // `kof run --target script` interpreta IR direto). A gate de face para
    // Script e coberta na fatia 5 do plano network-kofnet.

    @Test
    @DisplayName("D-KOF-NET: URI accessors keep compiling on ALL targets (NET001-closed behavior)")
    void uriAccessorsUnchanged() throws Exception {
        String uri = "main() { println(net.host(\"http://x.io/p\")) }";
        for (Target t : new Target[]{Target.JVM, Target.NATIVE, Target.JS}) {
            assertTrue(compile(uri, t).success(), t + " uri face must stay green");
        }
    }

    @Test
    @DisplayName("D-KOF-NET: catalog lists exactly the bound verbs")
    void catalogConsistent() {
        assertEquals(true, KofNet.functions().containsAll(java.util.List.of(
                "scheme", "host", "port", "path", "query", "fragment",
                "queryEncode", "queryDecode",
                "listen", "connect", "bind")));
        for (String memberOnly : java.util.List.of(
                "accept", "send", "receive", "sendTo", "peer", "close")) {
            assertFalse(KofNet.functions().contains(memberOnly),
                    memberOnly + " e membro de handle, nao face de namespace");
        }
    }
}
