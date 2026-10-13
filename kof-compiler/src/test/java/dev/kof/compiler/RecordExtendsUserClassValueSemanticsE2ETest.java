package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Issue #818: um `record` que ESTENDE uma classe do usuário perde a superclasse
 * sintética `java/lang/Record`, então o backend JVM não roda o
 * `JvmRecordEmitter` (que emite por `superName == java/lang/Record`) e a classe
 * ficava com `toString`/`equals` de IDENTIDADE — degradação silenciosa de uma
 * forma documentada (a gramática aceita `record-declaration … extends`).
 * Fix: sintetiza os métodos de valor no IR (a peça que o target native já
 * usava, e que por isso já estava correto).
 */
class RecordExtendsUserClassValueSemanticsE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path tmp, String source) throws IOException, InterruptedException {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, source);
        Path out = tmp.resolve("out");
        CompilationResult res = driver.compile(src, out, Target.JVM);
        assertTrue(res.success(), "compile must be clean: " + res.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                "-cp", out.toString() + ":kof-runtime/target/classes", "Default.Main")
                .redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "JVM exit, output was:\n" + output);
        return output;
    }

    private String runJs(Path tmp, String source) throws IOException, InterruptedException {
        Path src = tmp.resolve("MainJs.kf");
        Files.writeString(src, source);
        Path out = tmp.resolve("out-js");
        CompilationResult res = driver.compile(src, out, Target.JS);
        assertTrue(res.success(), "JS compile must be clean: " + res.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder("node", out.resolve("Default.mjs").toString())
                .redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "JS exit, output was:\n" + output);
        return output;
    }

    @Test
    void recordExtendingUserClassKeepsValueSemanticsOnJs(@TempDir Path tmp) throws Exception {
        String out = runJs(tmp, """
            sealed class B
            record S extends B(Int x)
            main() {
                var a = S(7)
                var b = S(7)
                println(a)
                println(a == b)
                println(a.hashCode() == b.hashCode())
            }
            """);
        assertEquals("S[x=7]\ntrue\ntrue", out,
                "the JS target must keep the same CONTENT semantics as the JVM");
    }

    @Test
    void recordExtendingUserClassKeepsValueSemantics(@TempDir Path tmp) throws Exception {
        String out = runJvm(tmp, """
            sealed class B
            record S extends B(Int x)
            main() {
                var a = S(7)
                var b = S(7)
                println(a)
                println(a == b)
                println(a.hashCode() == b.hashCode())
            }
            """);
        assertEquals("S[x=7]\ntrue\ntrue", out,
                "a record extending a user class must keep CONTENT toString/equals/hashCode");
    }

    @Test
    void recordExtendingUserClassKeepsValueSemanticsOnScript(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("MainScript.kf");
        Files.writeString(src, """
            sealed class B
            record S extends B(Int x)
            main() {
                var a = S(7)
                var b = S(7)
                println(a)
                println(a == b)
                println(a.hashCode() == b.hashCode())
            }
            """);
        KofInterpreter.Result ir = driver.interpret(java.util.List.of(src), tmp, new String[0]);
        assertEquals(0, ir.exitCode(), "script stderr: " + ir.stderr());
        assertEquals("S[x=7]\ntrue\ntrue", ir.stdout().replace("\r\n", "\n").trim(),
                "the Script target must keep the same CONTENT semantics as the JVM");
    }

    @Test
    void plainRecordStillKeepsValueSemantics(@TempDir Path tmp) throws Exception {
        String out = runJvm(tmp, """
            record Ponto(Int x, Int y)
            main() {
                println(Ponto(1, 2))
                println(Ponto(1, 2) == Ponto(1, 2))
            }
            """);
        assertEquals("Ponto[x=1, y=2]\ntrue", out, "the plain record face must not regress");
    }

    /**
     * Issue #823 (regressão do fix #818): um componente category-2 (Double/Long)
     * num record que estende uma classe do usuário. O fix #818 sintetizava os
     * métodos de valor no IR TAMBÉM para o JVM; aqueles builders são orientados
     * ao native e tratavam o slot de 64 bits como int — `Long` → VerifyError
     * (`long_2nd` em `hashCode`), `Double` → ClassFormatError (owner primitivo no
     * call de hash, gerando referência a classe ""). A correção é o JVM voltar a
     * usar o `JvmRecordEmitter` (que já trata category-2) via `clazz.isRecord()`.
     */
    @Test
    void sealedSubclassWithDoubleComponentRunsOnJvm(@TempDir Path tmp) throws Exception {
        String out = runJvm(tmp, """
            sealed class B
            class D(Double v) extends B
            main() {
                var d = D(2.5)
                println(d.v())
                println(D(2.5) == D(2.5))
            }
            """);
        assertEquals("2.5\ntrue", out, "a Double component must load and compare by content");
    }

    @Test
    void sealedSubclassWithLongComponentRunsOnJvm(@TempDir Path tmp) throws Exception {
        String out = runJvm(tmp, """
            sealed class B
            class L(Long v) extends B
            main() {
                var l = L(5)
                println(l.v())
                println(L(5) == L(5))
                println(L(5).hashCode() == L(5).hashCode())
            }
            """);
        assertEquals("5\ntrue\ntrue", out, "a Long component must hash without the category-2 overflow");
    }
}
