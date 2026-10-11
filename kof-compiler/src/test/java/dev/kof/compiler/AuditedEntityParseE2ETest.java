package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** `entity-history-plan` Phase 2 slice 2a (D-ENTITY-HISTORY, Q1–Q15): the
 *  `audited` declaration modifier is accepted before `entity` (additive —
 *  programs not using it compile unchanged; `audited` before anything other
 *  than `entity` stays an error). The AUDITED marker flows into the
 *  declaration's modifier list; the history recording itself is slice 2b. */
class AuditedEntityParseE2ETest {

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
    void auditedEntityParsesAndBehavesAsEntity() throws Exception {
        // full CRUD round-trip on the canonical ORM surface (h2, the
        // KofOrmE2ETest pattern): slice 2a is PARSE-ONLY — the audited
        // entity behaves EXACTLY like a plain entity until slice 2b lands
        // the recording.
        Path file = tmp.resolve("crud/Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
            audited entity User {
                id: Long generated
                name: String
            }
            main() {
                var db = db.connect("jdbc:h2:mem:audited1;DB_CLOSE_DELAY=-1")
                orm.create<User>(db)
                var mel = orm.save(db, User(0, "Mel"))
                println(mel.id)
                var u = orm.find<User>(db, mel.id)
                println(u.name)
                println(orm.count<User>(db))
                db.close(db)
            }
            """);
        Path outDir = tmp.resolve("out-crud");
        CompilationResult result = new CompilerDriver().compile(file, outDir, Target.JVM);
        assertTrue(result.success(), () -> "audited entity deve compilar limpo, veio: "
                + result.diagnostics().getDiagnostics());
        String h2 = findH2Jar();
        String cp = outDir + java.io.File.pathSeparator + h2;
        Process p = new ProcessBuilder("java", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8",
                "-cp", cp, "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "run falhou: " + out);
        assertEquals("1\nMel\n1", out, "o CRUD da audited entity é idêntico ao da entity simples");
    }

    private static String findH2Jar() {
        for (String entry : System.getProperty("java.class.path")
                .split(java.io.File.pathSeparator)) {
            if (entry.contains("h2") && entry.endsWith(".jar")) return entry;
        }
        throw new IllegalStateException("h2 jar not found on test classpath");
    }

    @Test
    void auditedBeforeNonEntityStaysAnError() throws Exception {
        String d = compile("auditedClass", """
            audited class Box {
                Int v
            }
            main() { }
            """, Target.JVM);
        assertTrue(d.contains("PARSE"), "audited fora de entity segue erro, veio: " + d);
    }

    @Test
    void plainEntitiesUnaffected() throws Exception {
        String d = compile("plain", """
            entity P {
                id: Long generated
            }
            main() { }
            """, Target.JVM);
        assertTrue(d.isEmpty(), "entity simples inalterado, veio: " + d);
    }
}
