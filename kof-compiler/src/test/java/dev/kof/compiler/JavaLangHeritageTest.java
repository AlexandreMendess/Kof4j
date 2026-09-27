package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §268 (D-RULE6-BATCH opção A) — `extends`/`implements` por NOME SIMPLES de
 * uma classe do JDK gravava o super/interface CRU no class file:
 * `class Worker extends Thread` emitia `Thread` (não `java/lang/Thread`) e a
 * própria classe morria no load (`NoClassDefFoundError`), embora o compile
 * saísse limpo. O probe cacheado de `java.lang` (§268) qualifica o nome sem
 * import; o que NADA resolve — `IOException` (java.io), `Zebra` — vira
 * SEM087 no compile (R6) em vez de crash silencioso no load.
 *
 * Cobre: java.lang não-throwable (extends/implements), `extends Object`,
 * throwable (regressão #313), import explícito (não regride), nome
 * indefinido → SEM087 nos dois targets, e o shadowing do usuário (um
 * `class Thread` do módulo vence o probe).
 */
class JavaLangHeritageTest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path outDir) throws Exception {
        String javaCmd = System.getProperty("java.home") + "/bin/java";
        Process p = new ProcessBuilder(javaCmd, "-cp", outDir.toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "JVM exit code (NoClassDefFoundError here IS the bug); output:\n" + out);
        return out;
    }

    private CompilationResult compile(Path tempDir, String source) throws Exception {
        Path file = tempDir.resolve("Main.kf");
        Files.writeString(file, source);
        return driver.compile(file, tempDir.resolve("out"), Target.JVM);
    }

    @Test
    void classExtendsJavaLangThreadWithoutImportLoads(@TempDir Path tempDir) throws Exception {
        var result = compile(tempDir, """
                class Worker extends Thread {
                    public constructor() { }
                }
                main() {
                    var w = new Worker()
                    println("thread ok")
                }
                """);
        assertTrue(result.success(), "must compile: " + result.diagnostics().getDiagnostics());
        assertEquals("thread ok", runJvm(tempDir.resolve("out")),
                "extends Thread (no import) must emit java/lang/Thread as super");
    }

    @Test
    void classExtendsObjectExplicitlyLoads(@TempDir Path tempDir) throws Exception {
        var result = compile(tempDir, """
                class Plain extends Object {
                    public constructor() { }
                }
                main() {
                    var p = new Plain()
                    println("object ok")
                }
                """);
        assertTrue(result.success(), "must compile: " + result.diagnostics().getDiagnostics());
        assertEquals("object ok", runJvm(tempDir.resolve("out")),
                "extends Object must become java/lang/Object, not the bare sentinel");
    }

    @Test
    void classImplementsJavaLangInterfaceWithoutImportLoads(@TempDir Path tempDir) throws Exception {
        var result = compile(tempDir, """
                class Runner implements Runnable {
                    public constructor() { }
                    void run() { println("running") }
                }
                main() {
                    var r = new Runner()
                    println("runnable ok")
                }
                """);
        assertTrue(result.success(), "must compile: " + result.diagnostics().getDiagnostics());
        assertEquals("runnable ok", runJvm(tempDir.resolve("out")),
                "implements Runnable (no import) must emit java/lang/Runnable in interfaces");
    }

    @Test
    void throwableExtendsStillWorksAfterGeneralization(@TempDir Path tempDir) throws Exception {
        // #313 regression guard: the throwable face keeps working through the
        // generalized resolver (java.lang probe + explicit throwable set).
        var result = compile(tempDir, """
                class MyEx extends RuntimeException {
                    public constructor() { }
                }
                main() {
                    var e = new MyEx()
                    println("throwable ok")
                }
                """);
        assertTrue(result.success(), "must compile: " + result.diagnostics().getDiagnostics());
        assertEquals("throwable ok", runJvm(tempDir.resolve("out")),
                "extends RuntimeException (no import) must keep loading after the §268 generalization");
    }

    @Test
    void explicitImportStillWinsOverProbe(@TempDir Path tempDir) throws Exception {
        // java.io is NOT java.lang: only the explicit import resolves it.
        var result = compile(tempDir, """
                import java.io.IOException
                class MyIo extends IOException {
                    public constructor() { }
                }
                main() {
                    var e = new MyIo()
                    println("io ok")
                }
                """);
        assertTrue(result.success(), "must compile: " + result.diagnostics().getDiagnostics());
        assertEquals("io ok", runJvm(tempDir.resolve("out")),
                "import java.io.IOException must keep resolving the external super");
    }

    @Test
    void undefinedSimpleSuperIsDiagnosedInsteadOfSilentRaw(@TempDir Path tempDir) throws Exception {
        var result = compile(tempDir, """
                class Ghost extends Zebra {
                    public constructor() { }
                }
                main() { println("never") }
                """);
        assertFalse(result.success(), "an undefined super must not compile (old: silent raw super → CNFE at load)");
        assertTrue(result.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM087".equals(d.code())),
                "expected SEM087 for the undefined super, got: " + result.diagnostics().getDiagnostics());
    }

    @Test
    void nonJavaLangSimpleSuperNeedsImportAndIsDiagnosed(@TempDir Path tempDir) throws Exception {
        // IOException lives in java.io: the probe is java.lang-only, so the
        // no-import form is diagnosed; the import form (previous test) works.
        var result = compile(tempDir, """
                class MyIo extends IOException {
                    public constructor() { }
                }
                main() { println("never") }
                """);
        assertFalse(result.success(), "java.io.IOException without import must be SEM087, not a silent raw super");
        assertTrue(result.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM087".equals(d.code())),
                "expected SEM087 for java.io.IOException without import, got: "
                        + result.diagnostics().getDiagnostics());
    }

    @Test
    void undefinedInterfaceIsDiagnosedOnEveryTarget(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("Main.kf");
        Files.writeString(file, """
                class Ghost implements GhostContract {
                    public constructor() { }
                }
                main() { println("never") }
                """);
        for (Target target : new Target[] { Target.JVM, Target.NATIVE }) {
            var result = driver.compile(file, tempDir.resolve("out-" + target), target);
            assertFalse(result.success(), "target " + target + ": undefined interface must not compile");
            assertTrue(result.diagnostics().getDiagnostics().stream()
                            .anyMatch(d -> "SEM087".equals(d.code())),
                    "target " + target + ": expected SEM087, got: "
                            + result.diagnostics().getDiagnostics());
        }
    }

    @Test
    void moduleClassShadowsJavaLangProbe(@TempDir Path tempDir) throws Exception {
        // The registry wins: a module `Thread` is NOT java.lang.Thread, even
        // though the probe would hit — the stored super must stay the local class.
        var result = compile(tempDir, """
                class Widget extends Thread {
                    public constructor() { }
                }
                class Thread {
                    public constructor() { }
                }
                main() {
                    var w = new Widget()
                    println("shadow ok")
                }
                """);
        assertTrue(result.success(), "must compile: " + result.diagnostics().getDiagnostics());
        assertEquals("shadow ok", runJvm(tempDir.resolve("out")),
                "a module class named Thread must shadow the java.lang probe");
    }
}
