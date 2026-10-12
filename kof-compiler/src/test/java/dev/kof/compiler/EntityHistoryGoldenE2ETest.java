package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** `entity-history-plan` Phase 3 GOLDEN (plan §23: byte-stable query results
 *  + named diagnostics per target; D-ENTITY-HISTORY §9.2): ONE pinned
 *  timeline (INSERT → UPDATE → UPDATE via the real recording path) and the
 *  byte-exact output of every runtime query face — `history` (ascending),
 *  pagination windows, `at` per revision (+ out-of-range null), `atTime`
 *  (before-all null / after-all last payload), `changes` (consecutive
 *  payload diffs), the one-revision face. The expected capture lives in
 *  `entity-history-golden.txt` (test resources); the `at` epochs are NOT in
 *  the capture (the runtime clock is not deterministic) — the relative
 *  faces are pinned with fixed instants instead. */
class EntityHistoryGoldenE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String GOLDEN_RESOURCE = "/dev/kof/entity-history-golden.txt";

    private static final String PROGRAM = """
            import kof.orm.history
            audited entity User {
                id: Long generated
                name: String
            }
            main() {
                var db = db.connect("jdbc:h2:mem:ehgolden1;DB_CLOSE_DELAY=-1")
                orm.create<User>(db)
                orm.createHistory<User>(db)
                var u = orm.save(db, User(0, "Mel"))
                u = orm.save(db, User(u.id, "Melissa"))
                u = orm.save(db, User(u.id, "Mel Santos"))
                var rs = User.history(db, u.id)
                println(rs.size)
                for (var r in rs) {
                    println(r.revision + ":" + r.operation + ":" + (r.actor == null))
                }
                var page = User.history(db, u.id, 1, 1)
                println(page.size)
                println(page.get(0).operation)
                var v1 = User.at(db, u.id, 1)
                if (v1 != null) { println(v1.name) }
                var v3 = User.at(db, u.id, 3)
                if (v3 != null) { println(v3.name) }
                println(User.at(db, u.id, 9) == null)
                println(User.atTime(db, u.id, "1970-01-01T00:00:00Z") == null)
                var vt = User.atTime(db, u.id, "2999-01-01T00:00:00Z")
                if (vt != null) { println(vt.name) }
                var ch = User.changes(db, u.id)
                println(ch.size)
                for (var c in ch) {
                    println(c.revision + ":" + c.field + ":" + (c.previous == null) + "->" + c.current)
                }
                var one = User.history(db, u.id, 2)
                if (one != null) { println(one.operation) }
                db.close(db)
            }
            """;

    private String runGoldenProgram() throws Exception {
        Path file = tmp.resolve("golden/Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, PROGRAM);
        Path outDir = tmp.resolve("out-golden");
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

    private static String goldenText() throws Exception {
        try (InputStream in = EntityHistoryGoldenE2ETest.class.getResourceAsStream(GOLDEN_RESOURCE)) {
            assertNotNull(in, "o golden " + GOLDEN_RESOURCE + " não está nos recursos de teste");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        }
    }

    private static String findH2Jar() {
        for (String entry : System.getProperty("java.class.path")
                .split(File.pathSeparator)) {
            if (entry.contains("h2") && entry.endsWith(".jar")) return entry;
        }
        throw new IllegalStateException("h2 jar not found on test classpath");
    }

    @Test
    void theHistoryFacesMatchTheGoldenCapture() throws Exception {
        assertEquals(goldenText(), runGoldenProgram(),
                "as faces de consulta batem byte a byte com o golden commitado");
    }

    @Test
    void theGoldenCaptureIsByteStableAcrossRuns() throws Exception {
        String first = runGoldenProgram();
        String second = runGoldenProgram();
        assertEquals(first, second, "a captura é byte-estável entre runs (o determinismo do golden)");
    }
}
