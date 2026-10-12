package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** entity-history-plan Phase 5 slice 5a (D-ENTITY-HISTORY; the maintainer's
 *  13/10 "ponta a ponta" order): `orm.createHistory<User>(db)` on the NATIVE
 *  x86-64 backend — the {@code kof_orm_create_history} asm emits the fixedschema {@code "user_history"} DDL (mirroring the JVM host). The proof:
 *  the table EXISTS with the 8 columns (read back via raw SQL on the SAME
 *  program). MySQL connections keep the honest runtime ORM001 (the native
 *  ORM's SQLite-only contract); the cross targets keep the compile-time
 *  ORM001 (unchanged). */
class EntityHistoryNativeCreateE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void nativeCreateHistoryMakesTheTable() throws Exception {
        assumeTrue(dev.kof.compiler.nat.NativeToolchainGate.present(),
                "x86-64 toolchain (as/ld) ausente — pulando");
        String source = """
                import kof.orm.history
                audited entity User {
                    id: Long generated
                    name: String
                }
                main() {
                    var db = db.connect("sqlite:%s/hist.db")
                    println(orm.createHistory<User>(db))
                    var cols = db.query(db, "select count(*) as c from pragma_table_info('user_history')")
                    for (var r in cols) {
                        println(r)
                    }
                    var n = db.query(db, "select count(*) as c from sqlite_master where name = 'user_history' and type = 'table'")
                    for (var r in n) {
                        println(r)
                    }
                    db.close(db)
                }
                """.formatted(tmp);
        Path out = tmp.resolve("out-native");
        CompilationResult r = driver.compile(
                writeFile("Main.kf", source), out, Target.NATIVE);
        assertTrue(r.success(), () -> "native compile: " + r.diagnostics().getDiagnostics());
        String got = runNative(out.resolve("Default/Main"));
        assertEquals("true\n{\"c\":8}\n{\"c\":1}", got,
                "a tabela user_history existe com as 8 colunas (DDL Fase 5)");
    }

    @Test
    void crossTargetsKeepTheHonestOrm001() throws Exception {
        String source = """
                import kof.orm.history
                audited entity User {
                    id: Long generated
                    name: String
                }
                main() {
                    var db = db.connect("sqlite:x.db")
                    println(orm.createHistory<User>(db))
                    db.close(db)
                }
                """;
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            CompilationResult r = driver.compile(writeFile("Cross.kf", source),
                    tmp.resolve("out-" + t.name().toLowerCase(java.util.Locale.ROOT)), t);
            assertFalse(r.success(), t + " mantém a recusa honesta");
            assertTrue(r.diagnostics().getDiagnostics().stream()
                            .anyMatch(d -> String.valueOf(d).contains("ORM001")),
                    t + " recusa com ORM001: " + r.diagnostics().getDiagnostics());
        }
    }

    private Path writeFile(String name, String content) throws Exception {
        Path f = tmp.resolve(name);
        Files.createDirectories(f.getParent());
        Files.writeString(f, content);
        return f;
    }

    private static String runNative(Path binary) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(binary.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), () -> "native exit, output: " + out);
        return out;
    }
}
