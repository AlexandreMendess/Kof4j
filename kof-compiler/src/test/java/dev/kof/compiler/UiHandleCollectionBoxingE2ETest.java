package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #632 — kof.ui handles keep their integer erasure through a nullable wrapper.
 * They must still be boxed as {@link Integer} when crossing an Object slot.
 */
class UiHandleCollectionBoxingE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path source, Path outDir, String program) throws Exception {
        Files.writeString(source, program);
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "Compilation failed: " + result.diagnostics().getDiagnostics());
        PrintStream oldOut = System.out;
        PrintStream oldErr = System.err;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(buf, true, StandardCharsets.UTF_8));
            URLClassLoader loader = new URLClassLoader(
                    new URL[]{outDir.toUri().toURL()},
                    UiHandleCollectionBoxingE2ETest.class.getClassLoader());
            Class<?> main = Class.forName("Default.Main", true, loader);
            main.getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } catch (Throwable failure) {
            Throwable cause = failure;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            buf.write(("THROW: " + cause + "\n").getBytes(StandardCharsets.UTF_8));
        } finally {
            System.setOut(oldOut);
            System.setErr(oldErr);
        }
        return buf.toString(StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
    }

    @Test
    void nonNullableHandleListCrossesObjectSlots(@TempDir Path tempDir) throws Exception {
        String output = runJvm(tempDir.resolve("handles.kf"), tempDir.resolve("out-handles"), """
                View painel(Int n) { return View(Label("p" + n)) }

                main() {
                    var paineis = listOf<View>(painel(1), painel(2))
                    paineis.add(painel(3))
                    paineis.set(0, painel(4))
                    println(paineis.size())
                    println(paineis.contains(painel(4)))
                    println("ok")
                }
                """);
        assertEquals("3\ntrue\nok", output, "List<View> must box handles through Integer");
    }

    @Test
    void nullableHandleListStillErasesToInteger(@TempDir Path tempDir) throws Exception {
        String output = runJvm(tempDir.resolve("nullable.kf"), tempDir.resolve("out-nullable"), """
                View painel(Int n) { return View(Label("p" + n)) }

                main() {
                    var a = painel(1)
                    var b = painel(2)
                    var paineis = listOf<View?>()
                    paineis.add(a)
                    paineis.set(0, b)
                    println(paineis.size())
                    println(paineis.contains(b))
                    println("ok")
                }
                """);
        assertEquals("1\ntrue\nok", output, "List<View?> must box the inner handle");
    }

    @Test
    void nullableHandleSetStillErasesToInteger(@TempDir Path tempDir) throws Exception {
        String output = runJvm(tempDir.resolve("set.kf"), tempDir.resolve("out-set"), """
                View painel(Int n) { return View(Label("p" + n)) }

                main() {
                    var paineis = setOf<View?>()
                    paineis.add(painel(1))
                    println(paineis.size())
                    println(paineis.contains(painel(1)))
                }
                """);
        assertEquals("1\ntrue", output, "Set<View?> must box the inner handle");
    }

    @Test
    void nullableHandleMapValueStillErasesToInteger(@TempDir Path tempDir) throws Exception {
        String output = runJvm(tempDir.resolve("map.kf"), tempDir.resolve("out-map"), """
                View painel(Int n) { return View(Label("p" + n)) }

                main() {
                    var paineis = mapOf<String, View?>()
                    paineis.put("a", painel(1))
                    println(paineis.size())
                    println(paineis.containsValue(painel(1)))
                }
                """);
        assertEquals("1\ntrue", output, "Map<String, View?> must box the inner handle");
    }

    @Test
    void issueHandleCrossesLocalStoreAndDirectMethod(@TempDir Path tempDir) throws Exception {
        String output = runJvm(tempDir.resolve("local-cross.kf"), tempDir.resolve("out-local-cross"), """
                Color fundo() { return Color(1, 2, 3) }

                main() {
                    var cor = fundo()
                    println(fundo().toCss())
                    println(cor.toCss())
                    println(cor == fundo())
                }
                """);
        assertEquals("rgb(1, 2, 3)\nrgb(1, 2, 3)\ntrue", output,
                "handle returned by a function must store locally and accept direct methods");
    }

    @Test
    void issueComposedViewListAndRowLoad(@TempDir Path tempDir) throws Exception {
        String output = runJvm(tempDir.resolve("issue-composition.kf"), tempDir.resolve("out-issue-composition"), """
                Color fundo() { return Color(10, 20, 30) }

                View painel(Int n) { return View(Label("p" + n)) }

                View desenhar(Component raiz) {
                    var paineis = listOf<View>(painel(1), painel(2), painel(3))
                    var v = View(Style(fundo(), Palette.white, 4, 2))
                    v.bind(Row(paineis))
                    return v
                }

                main() {
                    var tela = desenhar(Component(0))
                    println(tela == tela)
                    println("ok")
                }
                """);
        assertEquals("true\nok", output, "issue-style composition must load on the JVM");
    }
}
