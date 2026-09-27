package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Fase 7 (docs/ui/architecture.md §2.9): Route/Router. */
class RouterE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJs(Path tempDir, String name, String program) throws Exception {
        Path source = tempDir.resolve(name + "-js.kf");
        Files.writeString(source, program);
        CompilationResult js = driver.compile(source, tempDir.resolve("js-" + name), Target.JS);
        assertTrue(js.success(), "JS compilation should succeed: " + js.diagnostics().getDiagnostics());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int code = dev.kof.runtime.KofJsRunner.run(
                tempDir.resolve("js-" + name).resolve("Default.mjs"), out,
                new java.io.ByteArrayInputStream(new byte[0]), out);
        assertEquals(0, code, "JS run should succeed: " + out);
        return out.toString().trim();
    }










    @Test
    void concurrentSpawnAwaitNativeRuns(@TempDir Path tempDir) throws Exception {
        // §430: used to be a zero-assertion debug probe (`debugConc001`, always
        // green). Now a real Native E2E with an honest toolchain gate: spawn +
        // await must compile and the awaited value must print.
        org.junit.jupiter.api.Assumptions.assumeTrue(
                dev.kof.compiler.nat.NativeToolchainGate.present(),
                "Native toolchain (as/ld) ausente no host — pulando honesto");
        Path source = tempDir.resolve("sp.kf");
        Files.writeString(source, """
            Int work(Int x) { return x * 2 }
            main() {
                spawn {
                    println("inside")
                }
                val r = spawn work(21)
                println(await r)
                println("after")
            }
            """);
        CompilationResult rn = driver.compile(source, tempDir.resolve("nout"), Target.NATIVE);
        assertTrue(rn.success(), "compilação Native de spawn/await: "
                + rn.diagnostics().getDiagnostics());
        Path bin = tempDir.resolve("nout/Default/Main");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "exit nativo, saida: " + out);
        assertTrue(out.contains("42"), "await r deve imprimir 42: " + out);
        assertTrue(out.contains("after"), "main continua apos await: " + out);
    }

    @Test
    void goSwapsRootComponentWithLifecycle(@TempDir Path tempDir) throws Exception {
        String program = """
            main() {
                var home = Component(0)
                var detail = Component(0)
                var win = Window("App")
                home.view((s: Int) -> { return Label("home") })
                home.onMount(() -> { println("mount:home") })
                home.onDispose(() -> { println("dispose:home") })
                win.bind(home)
                detail.view((s: Int) -> { return Label("detail:" + Router.param()) })
                detail.onMount(() -> { println("mount:detail") })
                detail.onDispose(() -> { println("dispose:detail") })

                Router.route("home", home)
                Router.route("detail", detail)
                Router.go("detail", "42")
                println("route=" + Router.current() + " param=" + Router.param())
            }
            """;
        String out = runJs(tempDir, "router", program);
        assertTrue(out.contains("mount:home"), "rota inicial monta home: " + out);
        assertTrue(out.contains("dispose:home"), "navegar desmonta home: " + out);
        assertTrue(out.contains("mount:detail"), "navegar monta detail: " + out);
        assertTrue(out.contains("route=detail param=42"),
                "current/param refletem a rota ativa: " + out);
    }

    @Test
    void backForwardUseHistoryStacks2(@TempDir Path tempDir) throws Exception {
        String program = """
            main() {
                var a = Component(0)
                var b = Component(0)
                a.view((s: Int) -> { return Label("A") })
                b.view((s: Int) -> { return Label("B") })
                Router.route("a", a)
                Router.route("b", b)
                Router.go("b")
                Router.go("a")
                Router.back()
                println("cur=" + Router.current())
                Router.forward()
                println("after=" + Router.current())
            }
            """;
        String out = runJs(tempDir, "nav2", program);
        assertTrue(out.contains("cur=b"), "back() restaura: " + out);
        assertTrue(out.contains("after=a"), "forward() refaz: " + out);
    }

    @Test
    void unknownRouteIsRejected2(@TempDir Path tempDir) throws Exception {
        String program = """
            main() {
                var a = Component(0)
                a.view((s: Int) -> { return Label("A") })
                Router.route("a", a)
                var ok = Router.go("nao.existe")
                println(ok)
            }
            """;
        String out = runJs(tempDir, "bad", program);
        assertTrue(out.contains("false"), "go() para rota desconhecida retorna false: " + out);
    }
}
