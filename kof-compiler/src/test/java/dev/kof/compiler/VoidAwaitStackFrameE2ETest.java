package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §527 — um `await`/`awaitTimeout`/`selectAny` sobre uma task VAZIA empurrava
 * o `Object` que o runtime devolve SEMPRE, mas o modelo Kof da expressão é
 * void: o discard do statement não emitia POP e o objeto sobrava na pilha.
 * Qualquer try/catch DEPOIS do await ganhava um handler-frame órfão e o
 * verificador abortava (`Inconsistent stackmap frames at branch target`) — o
 * programa compilava limpo e morria em load (R6/Q7: código que não carrega).
 * Fix no único ponto onde a pilha JVM e o modelo divergem
 * ({@code JvmOpCollections.emitKofRuntimeCall}, ramo VOID). Provas: as quatro
 * combinações do probe que mediu o bug agora rodam e dão o golden; cada uma
 * falha no bytecode antigo com VerifyError.
 */
class VoidAwaitStackFrameE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir
    Path tmp;

    private String runJvm(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.JVM);
        if (!r.success()) {
            StringBuilder sb = new StringBuilder();
            for (Diagnostic d : r.diagnostics().getDiagnostics()) sb.append(d.code()).append(": ").append(d.message()).append('\n');
            return "COMPILE-FAIL\n" + sb;
        }
        var oldOut = System.out;
        var buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true));
        try {
            var cl = new URLClassLoader(new java.net.URL[]{out.toUri().toURL()}, getClass().getClassLoader());
            Class.forName("Default.Main", true, cl)
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            return buf.toString();
        } catch (java.lang.reflect.InvocationTargetException e) {
            return "THROW: " + e.getCause();
        } finally {
            System.setOut(oldOut);
        }
    }

    @Test
    void awaitVoidThenTryAssignCatchMatchesJvm() throws Exception {
        Path src = tmp.resolve("v3.kf");
        Files.writeString(src, """
                void w() { time.sleep(10) }
                main() {
                    var x = 1
                    val t = spawn w()
                    await t
                    try { x = x + 1 } catch (String e) { println(x) }
                    println(x)
                }
                """);
        assertEquals("2\n", runJvm(src, tmp.resolve("o3")),
                "await de task vazia nao pode deixar Object na pilha (try/catch depois)");
    }

    @Test
    void awaitVoidThenTwoSequentialTryCatchMatchesJvm() throws Exception {
        Path src = tmp.resolve("v4.kf");
        Files.writeString(src, """
                void w() { time.sleep(10) }
                main() {
                    val t = spawn w()
                    await t
                    try { throw "a" } catch (String e) { println(e) }
                    try { throw "b" } catch (String e) { println(e) }
                }
                """);
        assertEquals("a\nb\n", runJvm(src, tmp.resolve("o4")),
                "dois catch sequenciais depois do await vaziam carregam sem stackmap quebrado");
    }

    @Test
    void awaitTimeoutVoidThenTryCatchMatchesJvm() throws Exception {
        Path src = tmp.resolve("v5.kf");
        Files.writeString(src, """
                void w() { time.sleep(10) }
                main() {
                    val t = spawn w()
                    awaitTimeout(t, 5000)
                    try { println("ok") } catch (String e) { println(e) }
                }
                """);
        assertEquals("ok\n", runJvm(src, tmp.resolve("o5")),
                "awaitTimeout de task vazia segue a MESMA raiz que await");
    }

    @Test
    void primitiveAndStringAwaitStillUnboxAndMatch() throws Exception {
        Path src = tmp.resolve("v6.kf");
        Files.writeString(src, """
                int seven() { return 7 }
                main() {
                    val t = spawn seven()
                    println(await(t))
                    try { println("x") } catch (String e) { println(e) }
                }
                """);
        assertEquals("7\nx\n", runJvm(src, tmp.resolve("o6")),
                "o ramo VOID nao pode quebrar o unbox primitivo/class ja provado (regressao §128)");
    }
}
