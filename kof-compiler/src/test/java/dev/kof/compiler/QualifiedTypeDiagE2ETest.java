package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #640 — the SEM010 message printed both sides of a collision between records
 * with the SAME simple name in DIFFERENT packages as the same text
 * ("expected 'List<Item>' but got 'List<Item>'"). Type.display omitted the
 * package entirely. The message must qualify USER packages (JVM built-ins keep
 * the spelling the user wrote — #324), so a red diagnostic is diagnosable
 * without re-running (Q3).
 */
class QualifiedTypeDiagE2ETest {

    private static final String P1 = """
            package p1

            record Item(Int n)
            """;

    private static final String P2 = """
            package p2

            record Item(Int n)

            pega(): Item { return Item(1) }

            itens(): List<Item> { return listOf(Item(2)) }
            """;

    private static final String MAIN = """
            import p1.Item

            usar(): Item { return pega() }

            listar(): List<Item> { return itens() }
            """;

    @Test
    void collisionMessageQualifiesUserPackagesOnJvm(@TempDir Path tmp) throws Exception {
        Path pkg1 = Files.createDirectories(tmp.resolve("p1"));
        Path pkg2 = Files.createDirectories(tmp.resolve("p2"));
        Files.writeString(pkg1.resolve("Item.kf"), P1);
        Files.writeString(pkg2.resolve("Item.kf"), P2);
        Files.writeString(tmp.resolve("Main.kf"), MAIN);
        CompilerDriver driver = new CompilerDriver();
        CompilationResult r = driver.compileSources(
                List.of(tmp.resolve("Main.kf"), pkg1.resolve("Item.kf"), pkg2.resolve("Item.kf")),
                tmp.resolve("classes"), Target.JVM, tmp);
        assertFalse(r.success(), "collision must stay refused (R6), never silently typed");
        String diags = r.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("SEM010"), "SEM010 expected in: " + diags);
        assertTrue(diags.contains("p1.Item"),
                "#640: message must qualify the EXPECTED package, got: " + diags);
        assertTrue(diags.contains("p2.Item"),
                "#640: message must qualify the GOT package, got: " + diags);
        assertTrue(diags.contains("List<p1.Item>") && diags.contains("List<p2.Item>"),
                "#640: generic arguments must carry their own packages, got: " + diags);
    }

    @Test
    void builtInTypesKeepTheSpellingTheUserWrote(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("Main.kf"), """
                usar(): Int { return "nao sou Int" }
                """);
        CompilerDriver driver = new CompilerDriver();
        CompilationResult r = driver.compileSources(List.of(tmp.resolve("Main.kf")),
                tmp.resolve("classes"), Target.JVM, tmp);
        assertFalse(r.success());
        String diags = r.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("expected 'Int' but got 'String'"),
                "#324: no-arg/built-in spellings must stay simple, got: " + diags);
    }
}
