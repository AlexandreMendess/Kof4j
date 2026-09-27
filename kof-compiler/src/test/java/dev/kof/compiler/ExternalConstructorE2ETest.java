package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §393 — #568: o construtor IMPLICITO (`Greeter()`) de classe EXTERNA vinda de
 * `--classpath`/`--deps` caía no resolver de função top-level e morria em
 * `SEM015: Undefined function: 'Greeter'` mesmo com o emit resolvendo e o
 * artefato rodando (bug (ii) do #566). O §134 (`ExternalClasspathE2ETest`)
 * cobria só a estática `Greeter.hello` e o `new Greeter()` explícito — a face
 * sem `new` nunca foi alcançada pelo typer. Estes testes usam um jar FORA da
 * whitelist (pacote `ext.*`) gerado no @TempDir via JavaCompiler API e provam:
 * compila + roda com o valor real do construtor externo, o receiver fica
 * TIPADO (narrowing p/ `var` e chamada de membro), e a recusa continua honesta
 * quando a classe existe mas NÃO há construtor público compatível (SEM023 com
 * mensagem de construtor, nunca "Undefined function") — SEM015 segue valendo
 * para nome que não é função nem classe externa (R6, zero bypass).
 */
class ExternalConstructorE2ETest {

    private static final String PRODUCER = """
        package ext;
        public class Greeter {
            private final String prefix;
            public Greeter() { this("hi"); }
            public Greeter(String prefix) { this.prefix = prefix; }
            public String greet(String who) { return prefix + " " + who; }
        }
        """;

    private static final String LOCKED = """
        package ext;
        public class Locked {
            private Locked() { }
            public static Locked make() { return new Locked(); }
        }
        """;

    /** Compila os .class de teste com a API do JDK (sem processo externo) e empacota em jar. */
    private Path buildJar(Path tempDir) throws IOException {
        Path srcRoot = tempDir.resolve("extsrc/ext");
        Files.createDirectories(srcRoot);
        Files.writeString(srcRoot.resolve("Greeter.java"), PRODUCER);
        Files.writeString(srcRoot.resolve("Locked.java"), LOCKED);
        Path classes = tempDir.resolve("extcls");
        Files.createDirectories(classes);
        JavaCompiler jc = ToolProvider.getSystemJavaCompiler();
        assertNotNull(jc, "JavaCompiler do JDK indisponível (surefire precisa rodar em JDK)");
        int rc = jc.run(null, null, null, "--release", "21", "-d", classes.toString(),
                srcRoot.resolve("Greeter.java").toString(),
                srcRoot.resolve("Locked.java").toString());
        assertEquals(0, rc, "os .class do producer devem compilar");
        Path jar = tempDir.resolve("ext-greeter-ctor.jar");
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(jar))) {
            for (Path cls : Files.walk(classes).filter(f -> f.toString().endsWith(".class")).toList()) {
                z.putNextEntry(new ZipEntry(classes.relativize(cls).toString().replace('\\', '/')));
                z.write(Files.readAllBytes(cls));
            }
        }
        return jar;
    }

    private CompilationResult compile(Path jar, Path source, Path out) throws IOException {
        CompilerDriver d = new CompilerDriver();
        d.setExternalClasspath(List.of(jar));
        return d.compile(source, out, Target.JVM);
    }

    private void compileAndRun(Path tempDir, Path jar, String kf, String expect, String outName)
            throws Exception {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, kf);
        CompilationResult r = compile(jar, source, tempDir.resolve(outName));
        assertTrue(r.success(), "compilação deve passar (SEM015 falso não pode aparecer): "
                + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder("java", "-cp",
                tempDir.resolve(outName).toString() + java.io.File.pathSeparator + jar, "Default.Main");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes()).trim();
        assertEquals(0, p.waitFor(), "execução falhou: " + out);
        assertEquals(expect, out);
    }

    // ============ RED antes do fix: SEM015 falso ============

    @Test
    void implicitNoArgCtorFromExternalJarCompilesAndRuns(@TempDir Path tempDir) throws Exception {
        // repro verbatim da issue: println(Greeter().greet("nobody")) → "hi nobody"
        Path jar = buildJar(tempDir);
        compileAndRun(tempDir, jar, """
            import ext.Greeter
            main() { println(Greeter().greet("nobody")) }
            """, "hi nobody", "out-implicit0");
    }

    @Test
    void implicitCtorWithArgsFromExternalJarCompilesAndRuns(@TempDir Path tempDir) throws Exception {
        Path jar = buildJar(tempDir);
        compileAndRun(tempDir, jar, """
            import ext.Greeter
            main() { println(Greeter("yo").greet("mel")) }
            """, "yo mel", "out-implicit1");
    }

    @Test
    void implicitCtorNarrowsVarReceiver(@TempDir Path tempDir) throws Exception {
        // o receiver do `var` tem que chegar TIPADO (ClassType externo) —
        // sem isto `g.greet` perde o tipo na cadeia (face SEM011/Unknown)
        Path jar = buildJar(tempDir);
        compileAndRun(tempDir, jar, """
            import ext.Greeter
            main() {
                var g = Greeter("sup")
                println(g.greet("a"))
            }
            """, "sup a", "out-narrow");
    }

    @Test
    void newFormStillCompilesAndRuns(@TempDir Path tempDir) throws Exception {
        // freeze regra 1/2: a face `new` (§134) não pode regredir
        Path jar = buildJar(tempDir);
        compileAndRun(tempDir, jar, """
            import ext.Greeter
            main() {
                var g = new Greeter()
                println(g.greet("x"))
            }
            """, "hi x", "out-new");
    }

    // ============ honestidade (R6) — nada de bypass silencioso ============

    @Test
    void externalClassWithoutCompatibleCtorFailsHonestlyNotSEM015(@TempDir Path tempDir)
            throws Exception {
        // a classe EXISTE fora e não tem construtor com essa aridade:
        // mensagem honesta de construtor (SEM023), nunca "Undefined function"
        Path jar = buildJar(tempDir);
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            import ext.Greeter
            main() { println(Greeter(1, 2, 3)) }
            """);
        CompilationResult r = compile(jar, source, tempDir.resolve("out-noarity"));
        assertFalse(r.success(), "aridade inexistente não pode compilar");
        List<String> codes = r.diagnostics().getDiagnostics().stream()
                .map(d -> d.code()).toList();
        assertFalse(codes.contains("SEM015"),
                "classe externa conhecida não é função indefinida: " + codes);
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> d.code().equals("SEM023")
                                && d.message().contains("Greeter")),
                "esperado SEM023 citando 'Greeter', veio: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void nonPublicCtorIsRejectedHonestly(@TempDir Path tempDir) throws Exception {
        // `Locked` só tem construtor PRIVATE — instanciar é erro honesto,
        // nunca SUCESSO falso (Q7: sem facade) nem SEM015
        Path jar = buildJar(tempDir);
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            import ext.Locked
            main() { println(Locked()) }
            """);
        CompilationResult r = compile(jar, source, tempDir.resolve("out-private"));
        assertFalse(r.success(), "construtor privado não pode ser aceito");
        List<String> codes = r.diagnostics().getDiagnostics().stream()
                .map(d -> d.code()).toList();
        assertFalse(codes.contains("SEM015"),
                "classe externa conhecida não é função indefinida: " + codes);
        assertTrue(codes.contains("SEM023"),
                "esperado SEM023 de construtor, veio: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void wrongArgTypeIsDiagnosedNotSilent(@TempDir Path tempDir) throws Exception {
        // `Greeter(5)` — só existe (String): o tipo do arg tem que ser
        // conferido (SEM014 do typer); soltar virava <init>(I)V fantasma
        // (VerifyError no load, R6)
        Path jar = buildJar(tempDir);
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            import ext.Greeter
            main() { println(Greeter(5).greet("a")) }
            """);
        CompilationResult r = compile(jar, source, tempDir.resolve("out-badtype"));
        assertFalse(r.success(), "arg de tipo errado não pode compilar");
        List<String> codes = r.diagnostics().getDiagnostics().stream()
                .map(d -> d.code()).toList();
        assertFalse(codes.contains("SEM015"), "não é função indefinida: " + codes);
        assertTrue(codes.stream().anyMatch(c -> c.startsWith("SEM0")),
                "algum diagnóstico semântico honesto é obrigatório (R6)");
    }

    @Test
    void unknownBareCallStillSEM015(@TempDir Path tempDir) throws Exception {
        // R6: a correção não pode virar silêncio — nome que NÃO é função
        // top-level nem classe externa continua SEM015
        Path jar = buildJar(tempDir);
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            import ext.Greeter
            main() { println(Absent()) }
            """);
        CompilationResult r = compile(jar, source, tempDir.resolve("out-absent"));
        assertFalse(r.success(), "nome inexistente não pode compilar");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> d.code().equals("SEM015")),
                "esperado SEM015, veio: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void wildcardImportImplicitCtorRuns(@TempDir Path tempDir) throws Exception {
        // §134 residual coveria a estatica com wildcard; o construtor
        // implicito pela MESMA face wildcard (qualifica porque a classe
        // existe no entry) tambem tem que resolver
        Path jar = buildJar(tempDir);
        compileAndRun(tempDir, jar, """
            import ext.*
            main() { println(Greeter("w").greet("c")) }
            """, "w c", "out-wildcard");
    }

    @Test
    void statementFormImplicitCtorRuns(@TempDir Path tempDir) throws Exception {
        // `Greeter();` como statement (valor descartado) — mesma resolucao,
        // sem diagnostico fantasma e sem VERRIFY no drop do objeto
        Path jar = buildJar(tempDir);
        compileAndRun(tempDir, jar, """
            import ext.Greeter
            main() {
                Greeter("s")
                println("ok")
            }
            """, "ok", "out-stmt");
    }

    @Test
    void topLevelFunctionBeatsExternalConstructor(@TempDir Path tempDir) throws Exception {
        // precedência (backward compat): o typer já resolvia `Greeter()` como
        // função top-level DECLARADA; o fix não pode sequestrar o call-site
        Path jar = buildJar(tempDir);
        compileAndRun(tempDir, jar, """
            import ext.Greeter
            String Greeter() { return "fn wins" }
            main() { println(Greeter()) }
            """, "fn wins", "out-fn");
    }
}
