package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** `entity-history-plan` Phase 2 slice 2d (D-ENTITY-HISTORY §9.2): the
 *  remaining query faces — `E.at(db, id, revision)` (the snapshot payload
 *  materialized via the JSON binding; a DELETE revision materializes null),
 *  `E.atTime(db, id, iso)` (the last revision at-or-before the ISO instant)
 *  and `E.changes(db, id)` (consecutive-payload field diffs as
 *  `FieldChange`s). HIST002 on non-audited; HIST005 on constant negatives.
 *  The `changes` face: the 2-arg call underflowed the stack (5 pushed for 7
 *  params — the from/to defaults `from=1, to=MAX_INT` are emitted since this
 *  slice; that underflow WAS the NegativeArraySize -2 root). */
class EntityHistoryAtChangesE2ETest {

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

    private static final String SETUP = """
            import kof.orm.history
            audited entity User {
                id: Long generated
                name: String
            }
            main() {
                var db = db.connect("jdbc:h2:mem:ehd1;DB_CLOSE_DELAY=-1")
                orm.create<User>(db)
                orm.createHistory<User>(db)
                var u = orm.save(db, User(0, "Mel"))
                u = orm.save(db, User(u.id, "Melissa"))
                var v = User.at(db, u.id, 1)
                if (v != null) { println(v.name) }
                var v2 = User.at(db, u.id, 2)
                if (v2 != null) { println(v2.name) }
                db.close(db)
            }
            """;

    @Test
    void atMaterializesTheSnapshotState() throws Exception {
        String out = runJvm("at", SETUP.replace("ehd1", "ehd-at"));
        assertEquals("Mel\nMelissa", out,
                "at(1) e at(2) materializam os snapshots de cada revisão");
    }

    @Test
    void deleteRevisionMaterializesNull() throws Exception {
        String out = runJvm("atdel", """
            import kof.orm.history
            audited entity User {
                id: Long generated
                name: String
            }
            main() {
                var db = db.connect("jdbc:h2:mem:ehd2;DB_CLOSE_DELAY=-1")
                orm.create<User>(db)
                orm.createHistory<User>(db)
                var u = orm.save(db, User(0, "Mel"))
                orm.delete<User>(db, u.id)
                var gone = User.at(db, u.id, 2)
                println(gone == null)
                var old = User.at(db, u.id, 1)
                if (old != null) { println(old.name) }
                db.close(db)
            }
            """);
        assertEquals("true\nMel", out,
                "a revisão DELETE materializa null; a revisão anterior mantém o estado");
    }

    @Test
    void changesDiffsConsecutivePayloads() throws Exception {
        String out = runJvm("changes", """
            import kof.orm.history
            audited entity User {
                id: Long generated
                name: String
            }
            main() {
                var db = db.connect("jdbc:h2:mem:ehd3;DB_CLOSE_DELAY=-1")
                orm.create<User>(db)
                orm.createHistory<User>(db)
                var u = orm.save(db, User(0, "Mel"))
                u = orm.save(db, User(u.id, "Melissa"))
                var cs = User.changes(db, u.id)
                println(cs.size)
                println(cs.get(0).field)
                println(cs.get(0).previous)
                println(cs.get(0).current)
                db.close(db)
            }
            """);
        assertEquals("1\nname\nMel\nMelissa", out,
                "o diff campo-a-campo entre os payloads consecutivos");
    }

    @Test
    void atTimeReturnsTheStateAtOrBefore() throws Exception {
        String out = runJvm("attime", """
            import kof.orm.history
            audited entity User {
                id: Long generated
                name: String
            }
            main() {
                var db = db.connect("jdbc:h2:mem:ehd6;DB_CLOSE_DELAY=-1")
                orm.create<User>(db)
                orm.createHistory<User>(db)
                var u = orm.save(db, User(0, "Mel"))
                var v = User.atTime(db, u.id, "2026-01-01T00:00:00Z")
                println(v == null)
                var w = User.atTime(db, u.id, "2100-01-01T00:00:00Z")
                if (w != null) { println(w.name) }
                db.close(db)
            }
            """);
        assertEquals("true\nMel", out,
                "atTime antes da existência = null; depois = o estado corrente");
    }

    @Test
    void nonAuditedAtIsHist002() throws Exception {
        Path file = tmp.resolve("hist2/Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
            entity User {
                id: Long generated
            }
            main() {
                var db = db.connect("jdbc:h2:mem:ehd4;DB_CLOSE_DELAY=-1")
                var v = User.at(db, 1, 1)
                db.close(db)
            }
            """);
        CompilationResult result = driver.compile(file, tmp.resolve("out-hist2"), Target.JVM);
        assertFalse(result.success(), "at em não-audited deve falhar");
        String d = result.diagnostics().getDiagnostics().toString();
        assertTrue(d.contains("HIST002"), "esperava HIST002, veio: " + d);
    }

    @Test
    void constantNegativeRevisionIsHist005() throws Exception {
        Path file = tmp.resolve("hist5/Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
            import kof.orm.history
            audited entity User {
                id: Long generated
            }
            main() {
                var db = db.connect("jdbc:h2:mem:ehd5;DB_CLOSE_DELAY=-1")
                var v = User.at(db, 1, -2)
                db.close(db)
            }
            """);
        CompilationResult result = driver.compile(file, tmp.resolve("out-hist5"), Target.JVM);
        assertFalse(result.success(), "revisão negativa constante deve falhar");
        String d = result.diagnostics().getDiagnostics().toString();
        assertTrue(d.contains("HIST005"), "esperava HIST005, veio: " + d);
    }
}
