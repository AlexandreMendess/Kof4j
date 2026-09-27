package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #640 — SEM010 printed type names WITHOUT the package, so in a cross-package
 * collision the diagnostic was self-contradictory: {@code expected 'List<Item>'
 * but got 'List<Item>'} — the two sides are DIFFERENT types yet the message is
 * identical, exactly where readability matters (Q3: a red must be diagnosable
 * without re-running). Fix: user-package types carry their qualifier in
 * diagnostics; stdlib (kof.*) and default-package names stay verbatim — the
 * issue's own example pins {@code List<p1.Item>} for a {@code List<...>}.
 * The refusal itself (SEM010, success=false) is the pre-existing contract and
 * does NOT change (rule 6; #639 owns the consumer-side disambiguation face).
 */
class Sem010PackageQualifiedTypesE2ETest {

    private static final String P1 = """
            package p1

            record Item(Int n)

            items(): List<Item> {
                return listOf(Item(1))
            }
            """;

    private static final String P2 = """
            package p2

            record Item(Int n)

            want(xs: List<Item>) {
                println(xs.size)
            }
            """;

    private static final String MAIN = """
            import p1.Item
            import p2.Item

            main() {
                want(items())
            }
            """;

    private static final String PLAIN = """
            package solo

            record Widget(Int n)

            make(): List<Widget> {
                return listOf(Widget(1))
            }

            want(xs: List<Widget>) {
                println(xs.size)
            }

            main() {
                want(1)
            }
            """;

    private final CompilerDriver driver = new CompilerDriver();

    private List<Path> collision(Path tmp) throws IOException {
        Path d1 = tmp.resolve("p1");
        Path d2 = tmp.resolve("p2");
        Files.createDirectories(d1);
        Files.createDirectories(d2);
        Files.writeString(d1.resolve("Item.kf"), P1);
        Files.writeString(d2.resolve("Item.kf"), P2);
        Files.writeString(tmp.resolve("Main.kf"), MAIN);
        return List.of(tmp.resolve("Main.kf"), d1.resolve("Item.kf"), d2.resolve("Item.kf"));
    }

    @Test
    void collisionDiagnosisNamesThePackagesAndStillRefuses(@TempDir Path tmp) throws Exception {
        CompilationResult r = driver.compileSources(collision(tmp), tmp.resolve("out"),
                Target.JVM, tmp);
        assertEquals(false, r.success(), "o contrato de recusa (rule 6/#639) não pode mudar");
        String diags = r.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("SEM010") || diags.contains("SEM014"),
                "o mismatch deve ser SEM010/SEM014 (mesma face display), veio: " + diags);
        assertTrue(diags.contains("List<p2.Item>"),
                "o lado CONHECIDO deve citar o pacote, veio: " + diags);
        assertTrue(!diags.contains("expected 'List<Item>' but got 'List<Item>'"),
                "a mensagem auto-contraditoria do issue nao pode sobreviver, veio: " + diags);
    }


    @Test
    void crossPackageMismatchQualifiesBothSides(@TempDir Path tmp) throws Exception {
        // O contrato completo do #640 quando os DOIS lados sao conhecidos:
        // cada tipo de usuario exibe o proprio package. Site = mismatch de
        // RETURN dentro do proprio package (o mesmo codificador display do
        // #640); nomes distintos isolam a display da face de desambiguacao
        // de nomes iguais (#639, design).
        Path d1 = tmp.resolve("p1");
        Path d2 = tmp.resolve("p2");
        Files.createDirectories(d1);
        Files.createDirectories(d2);
        Files.writeString(d1.resolve("Gadget.kf"), """
                package p1

                record Gadget(Int n)

                make(): List<Gadget> {
                    return listOf(Gadget(1))
                }
                """);
        Files.writeString(d2.resolve("Widget.kf"), """
                package p2

                import p1.Gadget

                record Widget(Int n)

                fromP1(): List<Widget> {
                    return make()
                }
                """);
        Files.writeString(tmp.resolve("Main.kf"), """
                main() {
                    println(fromP1())
                }
                """);
        CompilationResult r = driver.compileSources(
                List.of(tmp.resolve("Main.kf"), d1.resolve("Gadget.kf"),
                        d2.resolve("Widget.kf")),
                tmp.resolve("out"), Target.JVM, tmp);
        assertEquals(false, r.success(),
                "mismatch real entre p1.Gadget e p2.Widget no sítio de return deve recusar");
        String diags = r.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("List<p1.Gadget>"),
                "got deve citar p1.Gadget, veio: " + diags);
        assertTrue(diags.contains("List<p2.Widget>"),
                "expected deve citar p2.Widget, veio: " + diags);
    }

    @Test
    void singleUserPackageTypeIsQualifiedToo(@TempDir Path tmp) throws Exception {
        // Q3 edge: a colisão NÃO é pré-requisito do qualificador — o pacote do
        // tipo é informação; o caso medido do verifier tinha os dois lados.
        Path d = tmp.resolve("solo");
        Files.createDirectories(d);
        Files.writeString(d.resolve("Widget.kf"), PLAIN);
        CompilationResult r = driver.compileSources(List.of(d.resolve("Widget.kf")),
                tmp.resolve("out"), Target.JVM, tmp);
        assertEquals(false, r.success());
        String diags = r.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("List<solo.Widget>"),
                "expected 'List<solo.Widget>' but got 'Int' deve citar solo.Widget, veio: " + diags);
    }

    @Test
    void stdlibAndDefaultPackageNamesStayVerbatim(@TempDir Path tmp) throws Exception {
        // Q3 edge (zero-regression): o texto que o corpus pinou não muda para
        // tipos do default package nem da stdlib — `List<Int>`, não `kof.List`.
        Files.writeString(tmp.resolve("Main.kf"), """
                use(xs: List<Int>) {
                    println(xs.size)
                }
                main() {
                    use(1)
                }
                """);
        CompilationResult r = driver.compileSources(List.of(tmp.resolve("Main.kf")),
                tmp.resolve("out"), Target.JVM, tmp);
        assertEquals(false, r.success());
        String diags = r.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("expected 'List<Int>'"),
                "stdlib deve continuar sem qualificador, veio: " + diags);
        assertTrue(!diags.contains("kof.List"),
                "kof.List jamais aparece, veio: " + diags);
    }

    @Test
    void defaultPackageUserClassStaysBare(@TempDir Path tmp) throws Exception {
        // Q3 edge: projeto sem package = o nome que o usuário escreveu (hoje
        // o único formato dos programas do corpus) — sem prefixo vazio "..X".
        Files.writeString(tmp.resolve("Main.kf"), """
                record Box(Int n)
                want(b: Box) {
                    println(b.n())
                }
                main() {
                    want(1)
                }
                """);
        CompilationResult r = driver.compileSources(List.of(tmp.resolve("Main.kf")),
                tmp.resolve("out"), Target.JVM, tmp);
        assertEquals(false, r.success());
        String diags = r.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("expected 'Box' but got 'Int'")
                        || diags.contains("expected 'Box'"),
                "default package deve imprimir o nome cru, veio: " + diags);
    }
}
