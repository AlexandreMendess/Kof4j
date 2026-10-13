package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Known-bugs §675 (D-SEM-REJECTS-1210, maintainer 12/10): the enum
 *  relational operators (`Color.RED < Color.GREEN`) were ACCEPTED and
 *  DIVERGED across targets (JVM/JS `false`, native `true`, Script
 *  "not comparable"). DECIDED: reject with `SEM105` in the shared frontend
 *  (the SEM053 String precedent) — explicit `compareTo` is the ordering
 *  spelling. The identity `==`/`!=` faces (D-ENUM207) and the enum×String
 *  refusal (SEM062) are untouched. */
class EnumRelationalRejectE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private String diagOf(String tag, String body) throws Exception {
        Path file = tmp.resolve(tag + "/Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "enum Color { RED, GREEN, BLUE }\nmain() {\n" + body + "\n}");
        return String.join("\n", driver.compile(file, tmp.resolve("out-" + tag), Target.JVM)
                .diagnostics().getDiagnostics().stream().map(Object::toString).toList());
    }

    @Test
    void relationalEnumFacesAreRefusedWithSem105() throws Exception {
        for (String op : new String[]{"<", ">", "<=", ">="}) {
            String d = diagOf("rel" + op.hashCode(), "    println(Color.RED " + op + " Color.GREEN)");
            assertTrue(d.contains("SEM105"), "esperava SEM105 para '" + op + "', veio: " + d);
        }
    }

    @Test
    void compareToStaysTheOrderingSpelling() throws Exception {
        String d = diagOf("cmp", """
                var r = Color.RED.compareTo(Color.GREEN)
                println(r < 0)
                """);
        assertTrue(d.isEmpty(), "o compareTo explícito segue limpo, veio: " + d);
    }

    @Test
    void identityAndEqualityFacesStayClean() throws Exception {
        String d = diagOf("id", """
                println(Color.RED == Color.RED)
                println(Color.RED != Color.GREEN)
                var c = Color.BLUE
                println(c == Color.BLUE)
                """);
        assertTrue(d.isEmpty(), "as faces de identidade D-ENUM207 seguem limpas, veio: " + d);
    }
}
