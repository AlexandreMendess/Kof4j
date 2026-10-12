package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Known-bugs §677 (D-SEM-REJECTS-1210, maintainer 12/10): `instanceof`
 *  against a PRIMITIVE target (`o instanceof Int`) compiled and DIVERGED
 *  2-2 across targets (Script/native `false`, JVM/JS `true`). DECIDED:
 *  reject with `SEM106` in the shared frontend. The class/record/String
 *  targets stay clean (the measured §677 boundary). */
class InstanceofPrimitiveRejectE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private String diagOf(String tag, String body) throws Exception {
        Path file = tmp.resolve(tag + "/Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "main() {\n" + body + "\n}");
        return String.join("\n", driver.compile(file, tmp.resolve("out-" + tag), Target.JVM)
                .diagnostics().getDiagnostics().stream().map(Object::toString).toList());
    }

    @Test
    void primitiveTargetsAreRefusedWithSem106() throws Exception {
        for (String t : new String[]{"Int", "Long", "Bool", "Char", "Double", "Float"}) {
            String d = diagOf("prim" + t,
                    "    var o: Object = 42\n    println(o instanceof " + t + ")");
            assertTrue(d.contains("SEM106"), "esperava SEM106 para '" + t + "', veio: " + d);
        }
    }

    @Test
    void classTargetStaysClean() throws Exception {
        // the hierarchy needs top-level classes — compile a dedicated source
        Path file = tmp.resolve("hier/Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                class Animal { }
                class Dog extends Animal { }
                main() {
                    var a: Animal = Dog()
                    println(a instanceof Dog)
                }
                """);
        String ok = String.join("\n", driver.compile(file, tmp.resolve("out-hier"), Target.JVM)
                .diagnostics().getDiagnostics().stream().map(Object::toString).toList());
        assertTrue(ok.isEmpty(), "a face de classe segue limpa, veio: " + ok);
    }

    @Test
    void stringTargetStaysClean() throws Exception {
        String d = diagOf("str", """
                var o: Object = "hi"
                println(o instanceof String)
                """);
        assertTrue(d.isEmpty(), "`String` é classe — a face segue limpa, veio: " + d);
    }
}
