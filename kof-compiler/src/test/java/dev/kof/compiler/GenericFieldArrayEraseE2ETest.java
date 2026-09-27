package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §357 — rio da erasure (família 3): issue #295.
 *
 * <p>Campo {@code T[]}: o componente era o leaf fantasma {@code ClassType
 * ("","T")} (mesma raiz §355, posição array) → descritor de campo
 * {@code [LT;} — classe inexistente → NoClassDefFoundError na load do
 * construtor. Com o componente apagado, o slot vira {@code Object[]} e
 * a atribuição {@code s.items = new Int[10]} passa a ser uma decisão de
 * TIPO real do JVM ({@code int[]} não é subtipo de {@code Object[]},
 * JVMS 4.10.1 — o javac rejeita igual): SEM098 no alvo JVM, gate de
 * ALVO (precedente SEM092/NAT001) — Script mantém o comportamento
 * dinâmico anterior (regra 2 do freeze, nunca rejeitar o que rodava).
 * O caminho bom ({@code String[]}/{@code as T[]} com cast) funciona, e o
 * cast {@code x as T[]} deixava {@code checkcast [LT;} — o alvo do cast
 * agora passa pelos type-params em lowering ({@code currentTypeParams}).
 */
class GenericFieldArrayEraseE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private Path write(Path tmp, String src) throws IOException {
        Path f = tmp.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(f, src);
        return f;
    }

    private String runJvm(Path tempDir, String source) throws IOException {
        Path file = write(tempDir, source);
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

    // ---- o caso bom: array de REFERÊNCIA no slot apagado, via `as T[]` ----

    @Test
    void refArrayIntoTypeVarArraySlotRunsOnJvm(@TempDir Path tmp) throws IOException {
        String out = runJvm(tmp, """
                class Stack<T> {
                    T[] items
                    Int size
                    public constructor(Int cap) {
                        this.items = new String[cap] as T[]
                        this.size = 0
                    }
                    push(T v) {
                        this.items[this.size] = v
                        this.size = this.size + 1
                    }
                    pop(): T {
                        this.size = this.size - 1
                        return this.items[this.size]
                    }
                }
                main() {
                    val s = Stack<String>(4)
                    s.push("a")
                    s.push("b")
                    println(s.pop())
                    println(s.pop())
                }
                """);
        assertEquals("b\na", out,
                "#295: campo T[] apagava para [LT; (NoClassDefFoundError 'T'); `as T[]` emitia checkcast fantasma");
    }

    // ---- a decisão de tipo honesta no JVM: Int[] → slot T[] é SEM098 (R6, nunca VerifyError) ----

    @Test
    void primitiveArrayIntoTypeVarArraySlotIsSem098OnJvm(@TempDir Path tmp) throws IOException {
        CompilationResult r = driver.compile(write(tmp, """
                class Stack<T> {
                    T[] items
                    Int size
                }
                main() {
                    var s = new Stack<Int>()
                    s.items = new Int[10]
                    println("ok")
                }
                """), tmp.resolve("out-295"), Target.JVM);
        assertFalse(r.success(), "int[] não é subtipo de Object[] — deve ser rejeitado no JVM, não VerifyError");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM098".equals(d.code())),
                "esperava SEM098, foi: " + r.diagnostics().getDiagnostics());
    }

    // ---- gate de ALVO: o mesmo programa roda no script (comportamento histórico, regra 2) ----

    @Test
    void primitiveArrayIntoTypeVarArrayStillRunsOnScript(@TempDir Path tmp) throws IOException {
        Path file = write(tmp, """
                class Stack<T> {
                    T[] items
                    Int size
                }
                main() {
                    var s = new Stack<Int>()
                    s.items = new Int[10]
                    println("ok")
                }
                """);
        KofInterpreter.Result ir = driver.interpret(java.util.List.of(file), tmp, new String[0]);
        assertEquals(0, ir.exitCode(), "script stderr: " + ir.stderr());
        assertEquals("ok\n", ir.stdout(), "array dinâmico: o script sempre aceitou; SEM098 é gate de alvo JVM");
    }

    @Test
    void typeVarArrayFieldDescriptorErasesToObjectArray(@TempDir Path tmp) throws IOException {
        // O descritor do CAMPO é a prova central do #295: sem o checkcast do
        // componente, `javap` de Stack.class mostra [LT; — aqui verificamos
        // via compilação do caso bom (carregou e rodou sem VerifyError) +
        // bytes: o constant pool do construtor referencia Object[].
        Path file = write(tmp, """
                class Bag<T> {
                    T[] things
                    public constructor(Int cap) { this.things = new String[cap] as T[] }
                }
                main() {
                    val b = Bag<Int>(2)
                    println("loaded")
                }
                """);
        Path outDir = tmp.resolve("out-desc");
        CompilationResult r = driver.compile(file, outDir, Target.JVM);
        assertTrue(r.success(), "compile: " + r.diagnostics().getDiagnostics());
        byte[] cls = Files.readAllBytes(outDir.resolve("Bag.class"));
        String cp = new String(cls, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(cp.contains("[Ljava/lang/Object;"), "descritor do campo deve ser [Ljava/lang/Object;");
        assertFalse(cp.contains("[LT;"), "descritor fantasma [LT; não pode aparecer no .class");
    }

    @Test
    void instanceofTypeVarErasesToBoundObject(@TempDir Path tmp) throws IOException {
        // Q3/expected-error inverso: `x instanceof T` (T sem bound) apaga para
        // Object — sempre true p/ não-null; deve compilar e rodar, sem
        // ClassNotFound pelo leaf T.
        String out = runJvm(tmp, """
                check<T>(x: T): String {
                    if (x instanceof String) { return "str" }
                    return "other"
                }
                main() {
                    println(check("a"))
                    println(check(42))
                }
                """);
        assertEquals("str\nother", out, "instanceof com pattern concreto sobre receiver T");
    }
}
