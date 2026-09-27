package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §245 — issues #268/#269: dois furos na resolução/emissão de campos.
 *
 * <ul>
 *   <li><b>#268</b>: campo cujo tipo declarado é um parâmetro genérico
 *       (`T wrapped`) não era substituído pelo type-argument do receiver
 *       (`Wrapper&lt;Point&gt;` → `Point`); o acesso encadeado (`w.wrapped.x`)
 *       emitia owner `?`/`""` → `NoClassDefFoundError`/`ClassFormatError` e,
 *       após o tipo certo, faltava o `checkcast` do valor apagado (`Object`)
 *       → `VerifyError`.</li>
 *   <li><b>#269</b>: escrita em campo por receiver NULLABLE (`a.child.num = v`)
 *       era aceita em silêncio e emitia `putfield` com descritor errado →
 *       `VerifyError`; o READ equivalente já era SEM049. Agora a escrita
 *       também é SEM049 (consistência com o contrato de null-safety, SG-005).</li>
 * </ul>
 */
class GenericFieldAccessE2ETest {

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
            Process pCompile = new ProcessBuilder("javac", "-d", runnerDir.toString(), runnerSrc.toString()).start();
            assertEquals(0, pCompile.waitFor());
            Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                    "-cp", outDir.toString() + java.io.File.pathSeparator + runnerDir.toString(), "Run", "Default.Main")
                    .redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "JVM exit code, output: " + output);
            return output;
        } catch (InterruptedException e) {
            throw new IOException("interrupted", e);
        }
    }

    // ---- #268: campo genérico substituído pelo type-argument do receiver ----

    @Test
    void genericFieldChainFieldAccess(@TempDir Path tmp) throws IOException {
        String out = runJvm(tmp, """
                class Wrapper<T> { T wrapped }
                class Point { Int x }
                main() {
                    var pt = new Point()
                    pt.x = 42
                    var w = new Wrapper<Point>()
                    w.wrapped = pt
                    println(w.wrapped.x)
                }
                """);
        assertEquals("42", out, "campo genérico deve ser substituído por Point e o .x acessado");
    }

    @Test
    void genericFieldChainMethodCall(@TempDir Path tmp) throws IOException {
        String out = runJvm(tmp, """
                class Wrapper<T> { T wrapped }
                class Msg {
                    String text
                    String shout() { return text.toUpperCase() }
                }
                main() {
                    var m = new Msg()
                    m.text = "hello"
                    var w = new Wrapper<Msg>()
                    w.wrapped = m
                    println(w.wrapped.shout())
                }
                """);
        assertEquals("HELLO", out, "método no resultado do campo genérico deve resolver com owner Msg");
    }

    @Test
    void genericFieldViaLocalThenAccess(@TempDir Path tmp) throws IOException {
        // a issue menciona "storing in a local first" — mesma raiz, mesmo fix
        String out = runJvm(tmp, """
                class Wrapper<T> { T wrapped }
                class Point { Int x }
                main() {
                    var w = new Wrapper<Point>()
                    var pt = new Point()
                    pt.x = 7
                    w.wrapped = pt
                    var got = w.wrapped
                    println(got.x)
                }
                """);
        assertEquals("7", out, "campo genérico lido para local e depois acessado");
    }

    // ---- #269: escrita por receiver nullable é SEM049 (consistente com o read) ----

    @Test
    void writeThroughNullableReceiverIsSem049(@TempDir Path tmp) throws IOException {
        CompilationResult r = driver.compile(write(tmp, """
                class A { B? child }
                class B { Int num }
                main() {
                    var a = new A()
                    a.child = new B()
                    a.child.num = 99
                    println("done")
                }
                """), tmp.resolve("out-269"), Target.JVM);
        assertFalse(r.success(), "escrita por receiver nullable deve ser rejeitada");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM049".equals(d.code())),
                "esperava SEM049 (mesmo do read), foi: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void readThroughNullableReceiverStaysSem049(@TempDir Path tmp) throws IOException {
        // não-regressão: o read já era SEM049 e continua
        CompilationResult r = driver.compile(write(tmp, """
                class A { B? child }
                class B { Int num }
                main() {
                    var a = new A()
                    println(a.child.num)
                }
                """), tmp.resolve("out-read"), Target.JVM);
        assertFalse(r.success(), "read por receiver nullable deve ser rejeitado");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM049".equals(d.code())),
                "esperava SEM049 no read, foi: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void narrowedNullableReceiverWriteCompilesAndRuns(@TempDir Path tmp) throws IOException {
        // o caminho correto (narrow de LOCAL primeiro) continua funcionando.
        // O narrowing de `a.child` (field expression) não é suportado — é o
        // #159, aberto e de regra 6; aqui narrowamos um local.
        String out = runJvm(tmp, """
                class A { B? child }
                class B { Int num }
                main() {
                    var a = new A()
                    a.child = new B()
                    var c = a.child
                    if (c != null) {
                        c.num = 99
                        println(c.num)
                    }
                }
                """);
        assertEquals("99", out, "escrita com narrowing de local deve compilar e rodar");
    }

    private Path write(Path tmp, String src) throws IOException {
        Path f = tmp.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(f, src);
        return f;
    }
}
