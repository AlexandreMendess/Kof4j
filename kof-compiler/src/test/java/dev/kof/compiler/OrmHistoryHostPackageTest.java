package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** `entity-history-plan` slice 2c-parte-1: the `kof.orm.history` virtual
 *  package — the LOCKED query-return records (`Revision`, `FieldChange`,
 *  §9.2) injected FLAT on the explicit `import kof.orm.history`
 *  (the pagination-precedent mechanism). Without the import the types do
 *  not exist; a user-defined `Revision` is a collision signal (no silent
 *  injection). The query RUNTIME (`User.history`) is the next slice. */
class OrmHistoryHostPackageTest {

    @TempDir
    Path tmp;

    private String compile(String tag, String src, Target target) throws Exception {
        Path file = tmp.resolve(tag + "/Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, src);
        return String.join("\n", new CompilerDriver()
                .compile(file, tmp.resolve("out-" + tag), target)
                .diagnostics().getDiagnostics().stream().map(Object::toString).toList());
    }

    @Test
    void importInjectsTheLockedRecords() throws Exception {
        String d = compile("inj", """
            import kof.orm.history
            record Pedido(String nome, Int clienteId)
            main() {
                var r: Revision = Revision(1, 1700000000000, "INSERT")
                println(r.revision)
                var f: FieldChange = FieldChange(2, "nome", "a", "b")
                println(f.field)
            }
            """, Target.JVM);
        assertTrue(d.isEmpty(), "os records travados devem existir com o import, veio: " + d);
    }

    @Test
    void withoutImportTheTypesDoNotExist() throws Exception {
        String d = compile("noinj", """
            main() {
                var r: Revision = Revision(1, 1700000000000, "INSERT")
                println(r.revision)
            }
            """, Target.JVM);
        assertFalse(d.isEmpty(), "sem o import, Revision não existe (nunca auto-injetado)");
    }

    @Test
    void userDefinedRevisionIsACollisionSignal() throws Exception {
        String d = compile("collision", """
            import kof.orm.history
            record Revision(Long x)
            main() {
                var r = Revision(1)
                println(r.x)
            }
            """, Target.JVM);
        assertTrue(d.isEmpty(), "o Revision do USUÁRIO vence (colisão é sinal, não silêncio): " + d);
    }
}
