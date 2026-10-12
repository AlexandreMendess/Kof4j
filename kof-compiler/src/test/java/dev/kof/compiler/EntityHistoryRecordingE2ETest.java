package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** `entity-history-plan` Phase 2 slice 2b (D-ENTITY-HISTORY): the RECORDING.
 *  For an `audited entity`, `orm.save`/`orm.delete` write the `<table>_history`
 *  rows (per-entity monotonic revision via COALESCE(MAX)+1, operation
 *  INSERT/UPDATE/DELETE, full snapshot payload) — after the EXPLICIT
 *  `orm.createHistory<T>(db)` step (never implicit DDL, §10). Plain entities
 *  record NOTHING (zero overhead) and `orm.createHistory` on one is the
 *  honest compile-time refusal. */
class EntityHistoryRecordingE2ETest {

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
    void auditedSaveUpdateDeleteRecordsRevisions() throws Exception {
        String out = runJvm("recorded", """
            audited entity User {
                id: Long generated
                name: String
            }
            main() {
                var db = db.connect("jdbc:h2:mem:eh1;DB_CLOSE_DELAY=-1")
                orm.create<User>(db)
                orm.createHistory<User>(db)
                var u = orm.save(db, User(0, "Mel"))
                u = orm.save(db, User(u.id, "Melissa"))
                orm.delete<User>(db, u.id)
                var rows = db.query(db, "select operation from \\"user_history\\" order by revision")
                for (var r in rows) {
                    println(r)
                }
                db.close(db)
            }
            """);
        assertEquals("{\"operation\":\"INSERT\"}\n{\"operation\":\"UPDATE\"}\n{\"operation\":\"DELETE\"}", out,
                "uma entity audited grava uma revisão por save + DELETE");
    }

    @Test
    void plainEntityRecordsNothing() throws Exception {
        String out = runJvm("plain", """
            entity User {
                id: Long generated
                name: String
            }
            main() {
                var db = db.connect("jdbc:h2:mem:eh2;DB_CLOSE_DELAY=-1")
                orm.create<User>(db)
                var u = orm.save(db, User(0, "Mel"))
                orm.delete<User>(db, u.id)
                var t = db.query(db, "select count(*) as c from information_schema.tables where table_name = 'USER_HISTORY'")
                println(t.get(0))
                db.close(db)
            }
            """);
        assertEquals("{\"c\":0}", out, "entity simples: nenhuma tabela de histórico, nenhuma gravação");
    }

    @Test
    void createHistoryOnPlainEntityIsRefused() throws Exception {
        Path file = tmp.resolve("refused/Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
            entity User {
                id: Long generated
            }
            main() {
                var db = db.connect("jdbc:h2:mem:eh3;DB_CLOSE_DELAY=-1")
                orm.createHistory<User>(db)
                db.close(db)
            }
            """);
        CompilationResult result = driver.compile(file, tmp.resolve("out-refused"), Target.JVM);
        assertFalse(result.success(), "createHistory em entity não-audited deve falhar");
        String d = result.diagnostics().getDiagnostics().toString();
        assertTrue(d.contains("HIST002") || d.contains("ORM002"),
                "recusa honesta (HIST002/ORM002), veio: " + d);
    }
}
