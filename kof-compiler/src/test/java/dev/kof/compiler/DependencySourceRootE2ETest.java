package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #566 opção (b) (DECISIONS.md, D-RELEASE-0.5.0-GATE addendum): um pacote
 * publicado é consumido como MÓDULO-FONTE. O compilador resolve `import` também
 * nas raízes de fonte de dependências (`setDependencySourceRoots`) — depois do
 * módulo local e da stdlib oficial (dependência nunca sombreia a stdlib), em
 * todos os alvos. Nenhuma sintaxe/semântica nova: só onde o `import` procura.
 */
class DependencySourceRootE2ETest {

    private static final String GREETER = """
        package regsmoke

        class Greeter {
            String greet(String who) { return "hello, " + who }
        }
        """;

    private static Path write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return file;
    }

    private static CompilationResult compile(Path consumerRoot, Path main, List<Path> depRoots,
                                             Path out, Target target) {
        CompilerDriver driver = new CompilerDriver();
        if (depRoots != null) driver.setDependencySourceRoots(depRoots);
        return driver.compileSources(List.of(main), out, target, consumerRoot);
    }

    private static String run(Path out) throws Exception {
        Process p = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", out.toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String s = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), "run timeout:\n" + s);
        assertEquals(0, p.exitValue(), "run exit:\n" + s);
        return s.trim();
    }

    private static String diags(CompilationResult r) {
        return r.diagnostics().getDiagnostics().toString();
    }

    @Test
    void importFromDependencySourceRootCompilesAndRunsWithAndWithoutNew(@TempDir Path tmp)
            throws Exception {
        Path deps = tmp.resolve("deps");
        write(deps.resolve("regsmoke/Greeter.kf"), GREETER);
        Path root = tmp.resolve("app");
        Path main = write(root.resolve("main.kf"), """
            import regsmoke.Greeter
            main() {
                println(Greeter().greet("implicit"))
                println(new Greeter().greet("explicit"))
            }
            """);
        Path out = tmp.resolve("out");
        CompilationResult r = compile(root, main, List.of(deps), out, Target.JVM);
        assertTrue(r.success(), () -> "fonte de dependencia deve resolver: " + diags(r));
        assertEquals("hello, implicit\nhello, explicit", run(out));
    }

    @Test
    void withoutDependencyRootTheImportStaysAnHonestPkg006(@TempDir Path tmp) throws Exception {
        Path root = tmp.resolve("app");
        Path main = write(root.resolve("main.kf"), """
            import regsmoke.Greeter
            main() { println(Greeter().greet("x")) }
            """);
        CompilationResult r = compile(root, main, null, tmp.resolve("out"), Target.JVM);
        assertFalse(r.success(), "sem raiz de dependencia o import nao resolve");
        assertTrue(diags(r).contains("PKG006"), "PKG006 honesto (nada silencioso): " + diags(r));
        CompilationResult empty = compile(root, main, List.of(), tmp.resolve("out2"), Target.JVM);
        assertTrue(diags(empty).contains("PKG006"), "lista vazia = comportamento de hoje");
    }

    @Test
    void localModuleWinsOverDependencySource(@TempDir Path tmp) throws Exception {
        Path deps = tmp.resolve("deps");
        write(deps.resolve("regsmoke/Greeter.kf"), GREETER);
        Path root = tmp.resolve("app");
        write(root.resolve("regsmoke/Greeter.kf"), """
            package regsmoke

            class Greeter {
                String greet(String who) { return "LOCAL, " + who }
            }
            """);
        Path main = write(root.resolve("main.kf"), """
            import regsmoke.Greeter
            main() { println(Greeter().greet("x")) }
            """);
        Path out = tmp.resolve("out");
        CompilationResult r = compile(root, main, List.of(deps), out, Target.JVM);
        assertTrue(r.success(), () -> diags(r));
        assertEquals("LOCAL, x", run(out), "o modulo local vence a dependencia");
    }

    @Test
    void directoryImportAndNestedImportsResolveInsideTheDependency(@TempDir Path tmp)
            throws Exception {
        Path deps = tmp.resolve("deps");
        write(deps.resolve("regsmoke/util/Text.kf"), """
            package regsmoke.util

            class Text {
                String shout(String s) { return s + "!" }
            }
            """);
        write(deps.resolve("regsmoke/Greeter.kf"), """
            package regsmoke

            import regsmoke.util.Text

            class Greeter {
                String greet(String who) { return new Text().shout("hello, " + who) }
            }
            """);
        Path root = tmp.resolve("app");
        Path main = write(root.resolve("main.kf"), """
            import regsmoke.*
            main() { println(Greeter().greet("dir")) }
            """);
        Path out = tmp.resolve("out");
        CompilationResult r = compile(root, main, List.of(deps), out, Target.JVM);
        assertTrue(r.success(), () -> "import de diretorio + import aninhado na dep: " + diags(r));
        assertEquals("hello, dir!", run(out));
    }

    @Test
    void dependencyPackageMismatchIsPkg004(@TempDir Path tmp) throws Exception {
        Path deps = tmp.resolve("deps");
        write(deps.resolve("regsmoke/Greeter.kf"), GREETER.replace("package regsmoke", "package evil"));
        Path root = tmp.resolve("app");
        Path main = write(root.resolve("main.kf"), """
            import regsmoke.Greeter
            main() { println(Greeter().greet("x")) }
            """);
        CompilationResult r = compile(root, main, List.of(deps), tmp.resolve("out"), Target.JVM);
        assertFalse(r.success(), "pacote declarado != diretorio do import e erro");
        assertTrue(diags(r).contains("PKG004"), "PKG004 honesto: " + diags(r));
    }

    @Test
    void dependencyNeverShadowsTheOfficialLibrary(@TempDir Path tmp) throws Exception {
        Path official = tmp.resolve("install/lib/kof-libs");
        write(official.resolve("pkgx/Thing.kf"), """
            package pkgx

            class Thing {
                String who() { return "official" }
            }
            """);
        Path deps = tmp.resolve("deps");
        write(deps.resolve("pkgx/Thing.kf"), """
            package pkgx

            class Thing {
                String who() { return "DEPENDENCY" }
            }
            """);
        Path root = tmp.resolve("app");
        Path main = write(root.resolve("main.kf"), """
            import pkgx.Thing
            main() { println(Thing().who()) }
            """);
        String old = System.getProperty("kof.install.dir");
        System.setProperty("kof.install.dir", tmp.resolve("install").toString());
        try {
            Path out = tmp.resolve("out");
            CompilationResult r = compile(root, main, List.of(deps), out, Target.JVM);
            assertTrue(r.success(), () -> diags(r));
            assertEquals("official", run(out), "dependencia nao pode sombrear a stdlib oficial");
        } finally {
            if (old == null) System.clearProperty("kof.install.dir");
            else System.setProperty("kof.install.dir", old);
        }
    }

    @Test
    void sourceModuleIsTargetIndependent(@TempDir Path tmp) throws Exception {
        // o motivo de (b): as MESMAS fontes servem a qualquer alvo (aqui JS, sem exigir node)
        Path deps = tmp.resolve("deps");
        write(deps.resolve("regsmoke/Greeter.kf"), GREETER);
        Path root = tmp.resolve("app");
        Path main = write(root.resolve("main.kf"), """
            import regsmoke.Greeter
            main() { println(Greeter().greet("js")) }
            """);
        CompilationResult r = compile(root, main, List.of(deps), tmp.resolve("outjs"), Target.JS);
        assertTrue(r.success(), () -> "o mesmo modulo-fonte compila para JS: " + diags(r));
    }
}
