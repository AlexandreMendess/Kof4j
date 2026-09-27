package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §134 — regressão do external classpath (0.3.1→0.4.x): PKG006 (e7005c69)
 * passou a rejeitar TODO import não-whitelisted SEM consultar os entries
 * carregados pelo --classpath/--deps, e a análise semântica marcava SEM011
 * no receiver de chamada estática externa (Greeter.hello) — o lowering
 * (ExpressionMethodCallLowerer) já resolvia via ExternalClasspath, mas
 * nunca era alcançado. Estes testes usam um pacote FORA da whitelist
 * (ext.*, como qualquer gson/postgresql/lib interna) e provam:
 * compila + roda com o valor real do jar externo.
 */
class ExternalClasspathE2ETest {

    private Path buildJar(Path tempDir) throws IOException, InterruptedException {
        Path srcRoot = tempDir.resolve("extsrc");
        Files.createDirectories(srcRoot.resolve("ext"));
        Files.writeString(srcRoot.resolve("ext/Greeter.java"), """
            package ext;
            public class Greeter {
                private final String name;
                public Greeter() { this("nobody"); }
                public Greeter(String name) { this.name = name; }
                public static String hello(String n) { return "hi " + n; }
                public String greet(String who) { return "hi " + who + " from " + name; }
            }
            """);
        Path classes = tempDir.resolve("extcls");
        ProcessBuilder pb = new ProcessBuilder("javac", "--release", "21", "-d",
                classes.toString(), srcRoot.resolve("ext/Greeter.java").toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes());
        assertEquals(0, p.waitFor(), "javac falhou: " + out);
        Path jar = tempDir.resolve("ext-greeter.jar");
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(jar))) {
            for (Path cls : Files.walk(classes).filter(f -> f.toString().endsWith(".class")).toList()) {
                z.putNextEntry(new ZipEntry(classes.relativize(cls).toString().replace('\\', '/')));
                z.write(Files.readAllBytes(cls));
            }
        }
        return jar;
    }

    private void compileAndRun(Path tempDir, Path jar, String kf, String expect) throws Exception {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, kf);
        CompilerDriver d = new CompilerDriver();
        d.setExternalClasspath(List.of(jar));
        CompilationResult r = d.compile(source, tempDir.resolve("out"), Target.JVM);
        assertTrue(r.success(), "compilação deve passar (PKG006/SEM011 não podem aparecer): "
                + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder("java", "-cp",
                tempDir.resolve("out").toString() + java.io.File.pathSeparator + jar, "Default.Main");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes()).trim();
        assertEquals(0, p.waitFor(), "execução falhou: " + out);
        assertEquals(expect, out);
    }

    @Test
    void staticCallOnExternalClassFromJarCompilesAndRuns(@TempDir Path tempDir) throws Exception {
        Path jar = buildJar(tempDir);
        compileAndRun(tempDir, jar, """
            import ext.Greeter
            main() { println(Greeter.hello("mel")) }
            """, "hi mel");
    }

    @Test
    void instancePathStillWorksWithExternalJar(@TempDir Path tempDir) throws Exception {
        Path jar = buildJar(tempDir);
        compileAndRun(tempDir, jar, """
            import ext.Greeter
            main() {
                var g = new Greeter()
                println(g != null)
            }
            """, "true");
    }

    @Test
    void implicitConstructorOnExternalClassFromJarCompilesAndRuns(@TempDir Path tempDir) throws Exception {
        // #568 (defeito (ii) do #566): o construtor IMPLICITO `Greeter()`
        // (sem `new`) caía no resolver de funções e disparava SEM015 falso,
        // embora o emit/lowering funcionem e o programa rode. O teste §134
        // cobria só a chamada estática (`Greeter.hello`) e o `new Greeter()`.
        Path jar = buildJar(tempDir);
        compileAndRun(tempDir, jar, """
            import ext.Greeter
            main() {
                var g = Greeter()
                println(g != null)
            }
            """, "true");
    }

    @Test
    void implicitConstructorWithArgsAndInstanceCallOnExternalClass(@TempDir Path tempDir) throws Exception {
        // #568 — caso exato do relator (#566): `Greeter("producer").greet("consumer")`.
        // Prova que o <init> com ARGUMENTOS resolve pelo descritor real do
        // classpath externo (não só o default) e que o método de instância
        // encadeado no objeto recém-construído despacha certo.
        Path jar = buildJar(tempDir);
        compileAndRun(tempDir, jar, """
            import ext.Greeter
            main() { println(Greeter("producer").greet("consumer")) }
            """, "hi consumer from producer");
    }

    @Test
    void implicitConstructorOnUnknownExternalClassStillFailsNotSilent(@TempDir Path tempDir) throws Exception {
        // R6: o fix não pode virar bypass — um nome que NÃO existe nos entries
        // (nem é classe do módulo) continua SEM015 honesto.
        Path jar = buildJar(tempDir);
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            import ext.Greeter
            main() {
                var g = Absent()
                println(g != null)
            }
            """);
        CompilerDriver d = new CompilerDriver();
        d.setExternalClasspath(List.of(jar));
        CompilationResult r = d.compile(source, tempDir.resolve("out"), Target.JVM);
        assertFalse(r.success(), "nome inexistente não pode compilar");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                .anyMatch(x -> x.code().equals("SEM015")),
                "esperado SEM015, veio: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void wildcardStaticCallOnExternalClassFromJarCompilesAndRuns(@TempDir Path tempDir) throws Exception {
        // §134 residual: `import ext.*` (wildcard de pacote FORA da whitelist)
        // qualifica o nome simples quando a classe existe num entry. Antes só
        // o import pontual resolvia; o wildcard dava SEM011.
        Path jar = buildJar(tempDir);
        compileAndRun(tempDir, jar, """
            import ext.*
            main() { println(Greeter.hello("mel")) }
            """, "hi mel");
    }

    @Test
    void wildcardUnknownNameStillRejectedNotSilent(@TempDir Path tempDir) throws Exception {
        // R6: o wildcard não pode virar bypass — um nome do pacote que NÃO
        // existe no jar continua SEM011 (sem baixar o descritor p/ um .class
        // fantasma e crashar em runtime).
        Path jar = buildJar(tempDir);
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            import ext.*
            main() { println(Absent.hello("mel")) }
            """);
        CompilerDriver d = new CompilerDriver();
        d.setExternalClasspath(List.of(jar));
        CompilationResult r = d.compile(source, tempDir.resolve("out"), Target.JVM);
        assertFalse(r.success(), "wildcard com nome inexistente não pode compilar");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                .anyMatch(x -> x.code().equals("SEM011")),
                "esperado SEM011, veio: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void unknownImportOutsideClasspathStillFailsPKG006(@TempDir Path tempDir) throws Exception {
        // R6: a correção não pode virar silêncio — import que NÃO está na
        // whitelist nem nos entries continua PKG006.
        Path jar = buildJar(tempDir);
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            import nope.Absent
            main() { println(Absent.x()) }
            """);
        CompilerDriver d = new CompilerDriver();
        d.setExternalClasspath(List.of(jar));
        CompilationResult r = d.compile(source, tempDir.resolve("out"), Target.JVM);
        assertFalse(r.success());
        assertTrue(r.diagnostics().getDiagnostics().stream()
                .anyMatch(x -> x.code().equals("PKG006")),
                "esperado PKG006, veio: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void externalJvmClassIsRejectedOnNativeAndJs(@TempDir Path tempDir) throws Exception {
        // §134 (R6 cross-target honesto): interop com .class JVM só faz
        // sentido nos targets JVM-family (JVM/ANDROID). Em NATIVE o linker
        // não tem o símbolo (undefined reference); em JS o lowering emitiria
        // `ext_Greeter` pendurado e rodaria null em runtime. Deixar o import
        // passar nesses targets seria regressão de silêncio — os dois
        // continuam PKG006 mesmo com o jar carregado.
        Path jar = buildJar(tempDir);
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            import ext.Greeter
            main() { println(Greeter.hello("mel")) }
            """);
        for (Target t : new Target[] { Target.NATIVE, Target.JS }) {
            CompilerDriver d = new CompilerDriver();
            d.setExternalClasspath(List.of(jar));
            CompilationResult r = d.compile(source, tempDir.resolve("out-" + t), t);
            assertFalse(r.success(), t + " não pode aceitar classe externa JVM: "
                    + r.diagnostics().getDiagnostics());
            assertTrue(r.diagnostics().getDiagnostics().stream()
                    .anyMatch(x -> x.code().equals("PKG006")),
                    t + ": esperado PKG006, veio " + r.diagnostics().getDiagnostics());
        }
    }
}
