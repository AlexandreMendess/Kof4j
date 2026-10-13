package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Known-bugs §678 (D-SEM-REJECTS-1210, maintainer 12/10): the failing
 *  reference `as` cast gets ONE cross-target meaning — plain `as` THROWS on
 *  failure on all four targets; the nullable spelling `as T?` is the
 *  ALWAYS-NULL soft cast. Pre-fix: JVM `as Dog` threw while
 *  Script/native/JS softened; `as Dog?` softened everywhere EXCEPT the JVM.
 *  The nullability of the KofCheckCast type carries the semantics (no new
 *  op, no divergent stream): strict for the non-null target, soft-null for
 *  the nullable one. (The NATIVE backend face remains open — the native
 *  lane's runtime.) */
class CastFailSemanticsE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String SRC = """
            class Animal { }
            class Dog extends Animal { }
            main() {
                var a: Animal = Animal()
                var dn = a as Dog?
                println(dn == null)
            }
            """;

    private static final String THROW_SRC = """
            class Animal { }
            class Dog extends Animal { }
            main() {
                var a: Animal = Animal()
                var d = a as Dog
                println(d != null)
            }
            """;

    private static final String OK_SRC = """
            class Animal { }
            class Dog extends Animal { }
            main() {
                var a: Animal = Dog()
                var d = a as Dog
                println(d != null)
                var dn = a as Dog?
                println(dn != null)
            }
            """;

    @Test
    void jvmNullableTargetYieldsNullOnFailure() throws Exception {
        Path file = tmp.resolve("jvmnull/Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, SRC);
        Path out = tmp.resolve("out-jvmnull");
        CompilationResult r = driver.compile(file, out, Target.JVM);
        assertTrue(r.success(), () -> "compile: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder("java", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8",
                "-cp", out.toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String o = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), () -> "o as T? NULL na falha (o JVM estourava): " + o);
        assertEquals("true", o, "as T? = sempre-null no falho");
    }

    @Test
    void scriptFailingPlainCastThrows() throws Exception {
        Path file = tmp.resolve("scriptthrow/Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, THROW_SRC);
        KofInterpreter.Result i = driver.interpret(java.util.List.of(file),
                file.getParent(), new String[0]);
        assertNotEquals(0, i.exitCode(),
                "o as T falho ESTOURA no Script (amolecia antes): " + i.stdout() + " / " + i.stderr());
    }

    @Test
    void jsFailingPlainCastThrows() throws Exception {
        Path file = tmp.resolve("jsthrow/Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, THROW_SRC);
        Path jsOut = tmp.resolve("out-jsthrow");
        CompilationResult r = driver.compile(file, jsOut, Target.JS);
        assertTrue(r.success(), () -> "JS compile: " + r.diagnostics().getDiagnostics());
        String node = TestJdk.onPath("node");
        ProcessBuilder pb = new ProcessBuilder(node, jsOut.resolve("Default.mjs").toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String o = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertNotEquals(0, p.waitFor(),
                "o as T falho ESTOURA no JS (amolecia antes): " + o);
        assertTrue(o.contains("type cast failed"), "o erro é o cast error decidido: " + o);
    }

    @Test
    void jsSuccessfulFacesStayClean() throws Exception {
        Path file = tmp.resolve("jsok/Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, OK_SRC);
        Path jsOut = tmp.resolve("out-jsok");
        CompilationResult r = driver.compile(file, jsOut, Target.JS);
        assertTrue(r.success(), () -> "JS compile: " + r.diagnostics().getDiagnostics());
        String node = TestJdk.onPath("node");
        ProcessBuilder pb = new ProcessBuilder(node, jsOut.resolve("Default.mjs").toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String o = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), () -> "JS exit, output: " + o);
        assertEquals("true\ntrue", o, "as faces bem-sucedidas intocadas no JS");
    }

    @Test
    void successfulFacesAreByteIdenticalOnScript() throws Exception {
        Path file = tmp.resolve("ok/Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, OK_SRC);
        KofInterpreter.Result i = driver.interpret(java.util.List.of(file),
                file.getParent(), new String[0]);
        assertEquals(0, i.exitCode(), () -> "Script exit/stderr: " + i.stdout() + " " + i.stderr());
        assertEquals("true\ntrue", i.stdout().trim(), "as faces bem-sucedidas intocadas");
    }
}
