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
    @DisplayName("D-KOF-NET: cross targets still refuse the socket contract with NET002 (no silent drop)")
    void crossStillRefuses() throws Exception {
        // Fatia 4a abre o JS; riscv64/aarch64 seguem sem os símbolos no runtime
        // cross (fatia 4b) e recusam com NET002 — nunca drop silencioso. O
        // contrato INTEIRO recusa no primeiro verbo estático (net.listen) já
        // com o código nomeado; os verbos de membro não têm handle válido sem
        // um listen/connect/bind que compile, então a recusa é pelo gate.
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            CompilationResult r = compile(CONTRACT, t);
            assertFalse(r.success(), t + " must refuse the socket contract: "
                    + r.diagnostics().getDiagnostics());
            assertTrue(r.diagnostics().getDiagnostics().toString().contains("NET002"),
                    t + " must name NET002: " + r.diagnostics().getDiagnostics());
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
    @DisplayName("D-KOF-NET JS: the frozen contract BINDS now that slice 4a emits the host bridge")
    void jsSurfaceCompiles() throws Exception {
        // Fatia 4a: `JsRuntimeUiNet` exporta os wrappers kofNet* sobre a ponte
        // `KofJsNetBridge` (host GraalJS, mesmo java.net do runtime JVM). O E2E
        // `NetJsE2ETest` prova que os verbos RESOLVEM e rodam.
        CompilationResult r = compile(CONTRACT, Target.JS);
        assertTrue(r.success(), "js must bind now: " + r.diagnostics().getDiagnostics());
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
