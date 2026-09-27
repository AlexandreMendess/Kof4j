package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * known-bugs §519 (#632) — um valor de `kof.ui`/midia e APAGADO para int no
 * bytecode JVM (UIW050), mas as bordas de REFERENCIA tratavam o handle como
 * objeto e empilhavam o int cru: a classe INTEIRA nao carregava
 * ({@code VerifyError: Bad type on operand stack}), mesmo em modulo de linha
 * de comando que so importa a UI — violando o contrato learn/35 ("nos alvos
 * sem render os handles sao no-ops, o programa RODA"). Faces medidas no tip
 * pre-fix (comentario da issue): resultado de funcao entrando em
 * {@code listOf}, {@code println(cor)} (String.valueOf(Object) sobre int),
 * {@code m.get(k) == m.get(k)} (if_icmpeq sobre Integer boxed) e
 * {@code a == null} (if_icmpeq contra aconst_null). O fix espelha a familia
 * D-NULL-INTENT: handle cru boxa p/ Integer na borda (TypeEmitter.boxPrimitive,
 * boxedTypeFor), Nullable(handle) de slot (get de map) cai no `.equals`
 * null-safe do wrapper, e handle-vs-null e o mesmo fold do primitivo cru
 * (nunca null → false/true). Native NAO tocado (representacao propria).
 * Golden = medido 26/09 no motor JVM e comparado byte a byte em JS e Script.
 */
class UiListHandlesJvmE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private static final String PROGRAM = """
            main() {
                val s = Style(Palette.black, Palette.white, 16, 8)
                val a = View(s)
                val l = listOf<View>(a, View(s))
                println(l.size)
                val cor = Color(10, 20, 30)
                println(cor)
                println(cor == cor)
                val m = mapOf("home", View(s))
                println(m.get("home") == m.get("home"))
                println(a == null)
                println(a != null)
            }
            """;

    // Oracle = medido 26/09 no motor JVM; JS e Script batem byte a byte
    // (handle impresso = o numero; == null = false; != null = true;
    // get==get e cor==cor por VALOR = true; size 2).
    private static final String EXPECTED =
            "2\n169090815\ntrue\ntrue\nfalse\ntrue\n";

    private String runJvm(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        var old = System.out;
        var buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true));
        try {
            var cl = new URLClassLoader(new java.net.URL[]{out.toUri().toURL()},
                    getClass().getClassLoader());
            Class.forName("Default.Main", true, cl)
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw new AssertionError("#632/§519: VerifyError na carga — handle de kof.ui "
                    + "cruzou borda de referencia sem box", e.getCause());
        } finally {
            System.setOut(old);
        }
        return buf.toString();
    }

    private String runJs(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.JS);
        assertTrue(r.success(), "JS compile failed: " + r.diagnostics().getDiagnostics());
        var buf = new ByteArrayOutputStream();
        int rc = dev.kof.runtime.KofJsRunner.run(
                out.resolve("Default.mjs"), buf,
                new ByteArrayInputStream(new byte[0]), buf);
        assertEquals(0, rc, "JS run rc=" + rc + ": " + buf);
        return buf.toString();
    }

    @Test
    void uiHandleBoundaryFacesLoadAndMatchJvmOracle() throws Exception {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, PROGRAM);
        assertEquals(EXPECTED, runJvm(src, tmp.resolve("o-jvm")),
                "JVM oracle drifted (golden = measured 26/09)");
        assertEquals(EXPECTED, runJs(src, tmp.resolve("o-js")),
                "§519/#632: paridade JS×JVM nos handles de kof.ui");
        KofInterpreter.Result r = driver.interpret(List.of(src), tmp, new String[0]);
        assertEquals(0, r.exitCode(), "Script run failed: " + r.stderr());
        assertEquals(EXPECTED, r.stdout(),
                "§519: o alvo Script deve imprimir/comparar handles identico a JVM");
    }

    @Test
    void issueReproWidgetListLoadsAndRunsOnJvm() throws Exception {
        Path src = tmp.resolve("Repro.kf");
        Files.writeString(src, """
                estiloFundo(): Style {
                    return Style(Palette.black, Palette.white, 16, 8)
                }
                areaDoCliente(raiz: Component): View {
                    var v = View(estiloFundo())
                    v.bind(Label("area"))
                    return v
                }
                painelDeClientes(raiz: Component): View {
                    return areaDoCliente(raiz)
                }
                barraDeNavegacao(raiz: Component): View {
                    return areaDoCliente(raiz)
                }
                desenhar(raiz: Component): View {
                    var paineis = listOf<View>(
                        barraDeNavegacao(raiz),
                        painelDeClientes(raiz),
                        areaDoCliente(raiz)
                    )
                    var v = View(estiloFundo())
                    v.bind(Row(paineis))
                    return v
                }
                main() {
                    val r = Component(0)
                    val paineis = listOf<View>(barraDeNavegacao(r), areaDoCliente(r))
                    println(paineis.size)
                }
                """);
        assertEquals("2\n", runJvm(src, tmp.resolve("o-repro")),
                "#632: a lista de handles do reproducer nao pode derrubar a carga da classe");
    }
}
