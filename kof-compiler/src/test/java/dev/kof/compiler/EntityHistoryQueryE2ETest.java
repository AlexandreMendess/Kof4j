package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** `entity-history-plan` Phase 2 slice 2c-parte-2 (D-ENTITY-HISTORY §9.2):
 *  the entity-static QUERY faces — `User.history(db, id)` returns the
 *  ascending `List<Revision>` (the injected `kof.orm.history` records) and
 *  the paginated variant `User.history(db, id, offset, limit)`; HIST002 on
 *  non-audited entities; HIST005 for constant negative offset/limit. */
class EntityHistoryQueryE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(String tag, String source) throws Exception {
        Path file = tmp.resolve(tag + "/Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
        Path outDir = tmp.resolve("out-" + tag);
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), () -> "compile failed: " + result.diagnostics().getDiagnostics());
        String h2 = findH2Jar();
        String cp = outDir + File.pathSeparator + h2;
        Process p = new ProcessBuilder("java", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8",
                "-cp", cp, "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), () -> "run falhou: " + out);
        return out;
    }

    private static String findH2Jar() {
        for (String entry : System.getProperty("java.class.path")
                .split(File.pathSeparator)) {
            if (entry.contains("h2") && entry.endsWith(".jar")) return entry;
        }
        throw new IllegalStateException("h2 jar not found on test classpath");
    }

    @Test
    void historyReturnsAscendingRevisions() throws Exception {
        String out = runJvm("query", """
            import kof.orm.history
            audited entity User {
                id: Long generated
                name: String
            }
            main() {
                var db = db.connect("jdbc:h2:mem:ehq1;DB_CLOSE_DELAY=-1")
                orm.create<User>(db)
                orm.createHistory<User>(db)
                var u = orm.save(db, User(0, "Mel"))
                u = orm.save(db, User(u.id, "Melissa"))
                var rs = User.history(db, u.id)
                println(rs.size)
                for (var r in rs) {
                    println(r.operation)
                }
                db.close(db)
            }
            """);
        assertEquals("2\nINSERT\nUPDATE", out,
                "as revisões voltam ascendentes com as operações gravadas");
    }

    @Test
    void paginatedHistoryWindowsTheRevisions() throws Exception {
        String out = runJvm("paged", """
            import kof.orm.history
            audited entity User {
                id: Long generated
                name: String
            }
            main() {
                var db = db.connect("jdbc:h2:mem:ehq2;DB_CLOSE_DELAY=-1")
                orm.create<User>(db)
                orm.createHistory<User>(db)
                var u = orm.save(db, User(0, "Mel"))
                u = orm.save(db, User(u.id, "Melissa"))
                var page = User.history(db, u.id, 0, 1)
                println(page.size)
                println(page.get(0).operation)
                db.close(db)
            }
            """);
        assertEquals("1\nINSERT", out, "a janela paginada traz a primeira revisão");
    }

    @Test
    void nonAuditedEntityIsHist002() throws Exception {
        Path file = tmp.resolve("hist2/Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
            entity User {
                id: Long generated
            }
            main() {
                var db = db.connect("jdbc:h2:mem:ehq3;DB_CLOSE_DELAY=-1")
                var rs = User.history(db, 1)
                db.close(db)
            }
            """);
        CompilationResult result = driver.compile(file, tmp.resolve("out-hist2"), Target.JVM);
        assertFalse(result.success(), "consulta em não-audited deve falhar");
        String d = result.diagnostics().getDiagnostics().toString();
        assertTrue(d.contains("HIST002"), "esperava HIST002, veio: " + d);
    }

    @Test
    void constantNegativeOffsetIsHist005() throws Exception {
        Path file = tmp.resolve("hist5/Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
            import kof.orm.history
            audited entity User {
                id: Long generated
            }
            main() {
                var db = db.connect("jdbc:h2:mem:ehq4;DB_CLOSE_DELAY=-1")
                var rs = User.history(db, 1, -1, 5)
                db.close(db)
            }
            """);
        CompilationResult result = driver.compile(file, tmp.resolve("out-hist5"), Target.JVM);
        assertFalse(result.success(), "offset negativo constante deve falhar");
        String d = result.diagnostics().getDiagnostics().toString();
        assertTrue(d.contains("HIST005"), "esperava HIST005, veio: " + d);
    }
}
