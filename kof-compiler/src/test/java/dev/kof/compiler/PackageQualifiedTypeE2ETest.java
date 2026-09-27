package dev.kof.compiler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #639 face 2 (D-DECISION-BATCH-2709B) — a superfície qualificada
 * {@code pkg.Type} não existia: em expressão ({@code p1.Item(1)}) o receiver
 * caía em SEM011 ("Undefined variable or type: 'p1'") e a anotação com
 * caminho pontuado não separava o pacote (nem no topo nem dentro de
 * genéricos). O contrato do #640/#531 é preservado (a display qualifica os
 * DOIS lados); o que muda é que o TIPO certo é alcançável quando os nomes
 * simples colidem.
 *
 * <p>Prova de paridade: o JVM constrói {@code p1.Item} e chama {@code tag()}
 * de {@code p1.Item} mesmo com {@code p2.Item} registrado por último — sem o
 * índice FQN aditivo, construção, acesso a membro e o {@code this} do
 * construtor caíam todos no last-write (VerifyError
 * {@code 'p1/Item' not assignable to 'p2/Item'}). JVM é o alvo da issue;
 * o mesmo programa tem de COMPILAR para Script (sem surface nova por alvo).
 */
class PackageQualifiedTypeE2ETest {

    private final CompilerDriver driver = new CompilerDriver();
    private PrintStream savedOut;

    @AfterEach
    void restoreOut() {
        if (savedOut != null) System.setOut(savedOut);
    }

    private static final String P1 = """
            package p1

            class Item {
                Int n
                public constructor(Int n) { this.n = n }
                tag(): String { return "p1:" + n }
            }
            """;

    private static final String P2 = """
            package p2

            class Item {
                Int n
                public constructor(Int n) { this.n = n }
                tag(): String { return "p2:" + n }
            }
            """;

    /** Grava as duas packages + Main e compila. A ORDEM decide o
     *  last-write de {@code knownClasses}; o caminho qualificado tem de vencer. */
    private CompilationResult compile(Path tmp, String main, boolean p2Last,
                                      Target target) throws IOException {
        Path d1 = tmp.resolve("p1");
        Path d2 = tmp.resolve("p2");
        Files.createDirectories(d1);
        Files.createDirectories(d2);
        Files.writeString(d1.resolve("Item.kf"), P1);
        Files.writeString(d2.resolve("Item.kf"), P2);
        Files.writeString(tmp.resolve("Main.kf"), main);
        List<Path> order = p2Last
                ? List.of(tmp.resolve("Main.kf"), d1.resolve("Item.kf"), d2.resolve("Item.kf"))
                : List.of(tmp.resolve("Main.kf"), d2.resolve("Item.kf"), d1.resolve("Item.kf"));
        return driver.compileSources(order, tmp.resolve("out"), target, tmp);
    }

    /**
     * Executa o {@code Default.Main} compilado por reflexão num
     * classloader próprio. O launcher de processo ({@code java -cp out
     * Default.Main}) engole o erro real atrás da mensagem de JavaFX (regra
     * documentada no AGENTS.md); a reflexão mostra a saída/o VerifyError
     * VERDADEIRO.
     */
    private String runJvm(Path outDir) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        savedOut = System.out;
        try {
            URLClassLoader cl = new URLClassLoader(
                    new URL[]{outDir.toUri().toURL()}, getClass().getClassLoader());
            System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
            Class<?> c = Class.forName("Default.Main", true, cl);
            Method m = c.getMethod("main", String[].class);
            m.invoke(null, (Object) new String[0]);
        } catch (InvocationTargetException e) {
            Throwable t = e.getCause();
            java.io.StringWriter sw = new java.io.StringWriter();
            t.printStackTrace(new java.io.PrintWriter(sw));
            throw new IOException("real error: " + sw);
        } catch (ReflectiveOperationException e) {
            throw new IOException(e);
        } finally {
            System.setOut(savedOut);
            savedOut = null;
        }
        return buf.toString(StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
    }

    @Test
    void qualifiedConstructionAndCallBindToThePath(@TempDir Path tmp) throws Exception {
        CompilationResult r = compile(tmp, """
                main() {
                    println(p1.Item(7).tag())
                    println(p2.Item(8).tag())
                }
                """, true, Target.JVM);
        assertTrue(r.success(), () -> r.diagnostics().getDiagnostics().toString());
        assertEquals("p1:7\np2:8", runJvm(tmp.resolve("out")),
                "cada caminho constrói e chama o SEU Item (last-write não pode vencer)");
    }

    @Test
    void orderIndependenceOfThePath(@TempDir Path tmp) throws Exception {
        // Q3 (idempotência/ordem): inverter o registro (p1 por último) não pode
        // trocar o resultado — o caminho qualificado manda, não a ordem.
        CompilationResult r = compile(tmp, """
                main() {
                    println(p1.Item(7).tag())
                    println(p2.Item(8).tag())
                }
                """, false, Target.JVM);
        assertTrue(r.success(), () -> r.diagnostics().getDiagnostics().toString());
        assertEquals("p1:7\np2:8", runJvm(tmp.resolve("out")),
                "o resultado não depende da ordem de registro");
    }

    @Test
    void qualifiedTypeAnnotation(@TempDir Path tmp) throws Exception {
        CompilationResult r = compile(tmp, """
                main() {
                    var a: p1.Item = p1.Item(7)
                    println(a.tag())
                }
                """, true, Target.JVM);
        assertTrue(r.success(), () -> r.diagnostics().getDiagnostics().toString());
        assertEquals("p1:7", runJvm(tmp.resolve("out")),
                "anotação `p1.Item` separa o pacote e casa com a construção");
    }

    @Test
    void qualifiedTypeInsideGenerics(@TempDir Path tmp) throws Exception {
        // Q3 (aninhamento): o caminho pontuado DENTRO do type-argument
        // (`List<p1.Item>`) também precisa ser separado — o top-level não basta.
        CompilationResult r = compile(tmp, """
                main() {
                    var xs: List<p1.Item> = listOf(p1.Item(1))
                    println(xs.get(0).tag())
                }
                """, true, Target.JVM);
        assertTrue(r.success(), () -> r.diagnostics().getDiagnostics().toString());
        assertEquals("p1:1", runJvm(tmp.resolve("out")),
                "List<p1.Item> casa com listOf(p1.Item(...))");
    }

    @Test
    void unknownQualifiedTypeIsStillRefused(@TempDir Path tmp) throws Exception {
        // Q3 (erro esperado, R6): um caminho que NÃO identifica tipo do módulo
        // não pode virar no-op silencioso — segue recusado.
        CompilationResult r = compile(tmp, """
                main() {
                    println(p1.Nope(1))
                }
                """, true, Target.JVM);
        assertFalse(r.success(), "p1.Nope não existe — a compilação deve recusar, não silenciar");
        String diags = r.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("SEM011"), "recusa honesta via SEM011, veio: " + diags);
    }

    // NOTA (#639 face 2 residual): a superfície qualificada em anotação
    // DECLARADA de parâmetro/campo/retorno (`use(xs: List<p1.Item>)` numa
    // outra package) ainda não propaga o elemento genérico — `xs.get(0)`
    // sai Object e o método recusa no load (VerifyError "Bad return type").
    // É um gap à parte (caminho de tipo DECLARADO + extração de type-arg),
    // registrado no DOING; a face 2 entregue cobre expressão + anotação
    // LOCAL (topo e dentro de genéricos), provado acima.
}
