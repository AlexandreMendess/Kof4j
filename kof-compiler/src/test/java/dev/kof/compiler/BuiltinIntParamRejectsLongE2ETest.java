package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Known-bugs §657 (the builtin Int-declared parameters reject a Long with
 *  the user-function refusal): `config.int("k", 5000000000)` truncated to
 *  705032704 (Script/native) and `strings.repeat("ab", 5000000000)` allocated
 *  a ~5-billion-char string — the arity-only binding let a Long slip into an
 *  Int slot. The type guards (isInt/isLong, mirroring KofMath/KofTime/KofHttp)
 *  make the mismatch fall to the existing namespace-resolution refusal. */
class BuiltinIntParamRejectsLongE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private String diagOf(String tag, String src) throws Exception {
        Path file = tmp.resolve(tag + "/Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, src);
        return String.join("\n", driver.compile(file, tmp.resolve("out-" + tag), Target.JVM)
                .diagnostics().getDiagnostics().stream().map(Object::toString).toList());
    }

    private String runJvm(String tag, String src) throws Exception {
        Path file = tmp.resolve(tag + "/Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, src);
        Path outDir = tmp.resolve("out-" + tag);
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), () -> "compile failed: " + result.diagnostics().getDiagnostics());
        String h2 = "";
        for (String e : System.getProperty("java.class.path").split(File.pathSeparator)) {
            if (e.contains("h2") && e.endsWith(".jar")) { h2 = e; break; }
        }
        String cp = outDir + (h2.isEmpty() ? "" : File.pathSeparator + h2);
        Process p = new ProcessBuilder("java", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8",
                "-cp", cp, "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), () -> "run falhou: " + out);
        return out;
    }

    @Test
    void configIntRejectsLongLiteral() throws Exception {
        // the audit's exact repro: config.int("k", 5000000000) truncated to
        // 705032704 — now the honest refusal at compile time
        String d = diagOf("cfgInt", "main() {\n"
                + "    var k = config.int(\"k\", 5000000000)\n"
                + "    println(k)\n"
                + "}");
        assertTrue(d.contains("SEM025") || d.contains("SEM014"),
                "esperava a recusa honesta, veio: " + d);
    }

    @Test
    void stringsRepeatRejectsLongCount() throws Exception {
        String d = diagOf("repLong", "main() {\n"
                + "    var s = strings.repeat(\"ab\", 5000000000)\n"
                + "    println(s.size)\n"
                + "}");
        assertTrue(d.contains("SEM025") || d.contains("SEM014"),
                "esperava a recusa honesta, veio: " + d);
    }

    @Test
    void validFacesStillBind() throws Exception {
        String out = runJvm("ok", """
                main() {
                    var k = config.int("k", 42)
                    println(k)
                    var s = strings.repeat("ab", 3)
                    println(s)
                }
                """);
        assertEquals("42\nababab", out, "as faces válidas mantêm o comportamento");
    }
}
