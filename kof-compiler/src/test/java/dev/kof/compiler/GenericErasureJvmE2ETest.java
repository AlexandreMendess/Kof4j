package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §355 — rio da erasure (família 1): issues #399/#363/#368/#375.
 *
 * <p>Mesma raiz em quatro formas: o {@code T} declarado (classe ou função
 * genérica) chegava ao emit como {@code ClassType("","T")} — um leaf fantasma
 * — em posições ANINHADAS ({@code List<T>}, {@code T[]} no topo de campo
 * resolvido por outro caminho) e com o BOUND engolido pelo parser
 * ({@code <T : Animal>} virava dois type-params). Consequências no JVM:
 * {@code checkcast T}/{@code getfield LT;} → NoClassDefFoundError "T", e
 * acesso a membro do bound perdido. O parser agora grava o bound na entry
 * ({@code "T: Animal"}), {@code TypeVariable} carrega o bound, e
 * {@link TypeParams#rewrite} recursa a substituição nos argumentos/componentes.
 * Script/JS nunca sofreram (sem descritor) — continuam verdes (R7).
 */
class GenericErasureJvmE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path tempDir, String source) throws IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compile failed: " + result.diagnostics().getDiagnostics());
        try {
            Path runnerDir = tempDir.resolve("run-" + System.nanoTime());
            Files.createDirectories(runnerDir);
            Path runnerSrc = runnerDir.resolve("Run.java");
            Files.writeString(runnerSrc, """
                public class Run {
                    public static void main(String[] args) throws Exception {
                        Class.forName(args[0]).getMethod("main", String[].class)
                            .invoke(null, (Object) new String[0]);
                    }
                }
                """);
            Process pCompile = new ProcessBuilder(TestJdk.javacBin(), "-d", runnerDir.toString(), runnerSrc.toString()).start();
            assertEquals(0, pCompile.waitFor());
            Process p = new ProcessBuilder(TestJdk.javaBin(),
                    "-cp", outDir.toString() + java.io.File.pathSeparator + runnerDir.toString(), "Run", "Default.Main")
                    .redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "JVM exit code " + ec + ", output: " + output);
            return output;
        } catch (InterruptedException e) {
            throw new IOException("interrupted", e);
        }
    }

    // ---- #399: campo List<T> + peek(): T (getfield/setfield/invokes com T aninhado) ----

    @Test
    void listFieldOfGenericTypeRunsOnJvm(@TempDir Path tmp) throws IOException {
        String out = runJvm(tmp, """
                class Stack<T> {
                    List<T> items
                    constructor() { items = listOf() }
                    push(item: T) { items.add(item) }
                    peek(): T { return items.get(items.size() - 1) }
                    size(): Int { return items.size() }
                }
                main() {
                    val s: Stack<String> = Stack()
                    s.push("hello")
                    s.push("world")
                    println(s.size())
                    println(s.peek())
                }
                """);
        assertEquals("2\nworld", out, "#399: List<T> aninhado apagava para o leaf fantasma T");
    }

    // ---- #363: index operator + atribuição narrowing (var x: String = s.peek()) ----

    @Test
    void indexAccessAndNarrowedAssignOfGenericTypeVar(@TempDir Path tmp) throws IOException {
        String out = runJvm(tmp, """
                class Stack<T> {
                    List<T> items
                    constructor() { items = listOf() }
                    push(item: T) { items.add(item) }
                    peek(): T { return items[items.size() - 1] }
                }
                main() {
                    val s: Stack<String> = Stack()
                    s.push("hello")
                    val x: String = s.peek()
                    println(x)
                }
                """);
        assertEquals("hello", out, "#363: items[i] com elemento T emitia checkcast T");
    }

    // ---- #368: receiver TypeVariable sem bound — toString() apaga para Object ----

    @Test
    void methodCallOnUnboundedTypeVarReceiver(@TempDir Path tmp) throws IOException {
        String out = runJvm(tmp, """
                process<T>(item: T): String {
                    return item.toString()
                }
                main() {
                    println(process(42))
                    println(process("hello"))
                }
                """);
        assertEquals("42\nhello", out, "#368: invokevirtual com owner 'T' → NoClassDefFoundError");
    }

    // ---- #375: bound preservado — `getName<T : Animal>` apaga para Animal ----

    @Test
    void boundedTypeVarErasesToBoundAndAccessesMember(@TempDir Path tmp) throws IOException {
        String out = runJvm(tmp, """
                class Animal {
                    String name
                    constructor(n: String) { name = n }
                }
                class Dog extends Animal {
                    constructor(n: String) { super(n) }
                }
                getName<T : Animal>(item: T): String {
                    return item.name
                }
                main() {
                    println(getName(Dog("Rex")))
                }
                """);
        assertEquals("Rex", out, "#375: bound do type-param era engolido pelo parser (field em Animal, não Object)");
    }

    @Test
    void boundedTypeVarMethodCallErasesToBound(@TempDir Path tmp) throws IOException {
        // Q3/limites: membro MÉTODO (não campo) no bound, receiver vindo de
        // função genérica com type-arg subtipo (Dog : Animal).
        String out = runJvm(tmp, """
                class Animal {
                    String tag
                    constructor(t: String) { tag = t }
                    String label() { return "a:" + tag }
                }
                class Cat extends Animal {
                    constructor(t: String) { super(t) }
                }
                shout<A : Animal>(x: A): String { return x.label() }
                main() {
                    println(shout(Cat("mimi")))
                }
                """);
        assertEquals("a:mimi", out, "invokevirtual no bound (Animal.label), não no TypeVariable");
    }

    @Test
    void genericTypesStillFineOnScript(@TempDir Path tmp) throws IOException {
        // não-regressão R7: o caminho script (dinâmico) nunca teve descritor.
        Path file = tmp.resolve("S-" + System.nanoTime() + ".kf");
        Files.writeString(file, """
                class Stack<T> {
                    List<T> items
                    constructor() { items = listOf() }
                    push(item: T) { items.add(item) }
                    peek(): T { return items[items.size() - 1] }
                }
                main() {
                    val s: Stack<String> = Stack()
                    s.push("hello")
                    println(s.peek())
                }
                """);
        KofInterpreter.Result ir = driver.interpret(java.util.List.of(file), tmp, new String[0]);
        assertEquals(0, ir.exitCode(), "script stderr: " + ir.stderr());
        assertEquals("hello\n", ir.stdout());
    }
}
