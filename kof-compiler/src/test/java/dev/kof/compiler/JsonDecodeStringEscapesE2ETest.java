package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Known-bugs §667 (the JVM+Script faces): `json.decode<String>` left JSON
 *  string escapes RAW on the JVM (the runtime only stripped the outer
 *  quotes) while JS was correct (`JSON.parse`) — a three-way divergence on a
 *  cross-target contract. The fix routes the JVM `kof_json_decode_string`
 *  through `kof_json_parse` (the fully-unescaped String). The native
 *  backslash-uXXXX face is a separate asm unit (§667's Boundary).
 *
 *  Escape-hygiene: every backslash in the generated Kof source is built via
 *  (char) 92 — the JAVA unicode-escape preprocessor would otherwise eat the
 *  backslash-u spellings before the compiler ever saw them (they are ILLEGAL
 *  even inside comments). */
class JsonDecodeStringEscapesE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String BS = String.valueOf((char) 92);

    private String runJvm(String tag, String jsonLiteral) throws Exception {
        Path file = tmp.resolve(tag + "/Main.kf");
        Files.createDirectories(file.getParent());
        String src = "main() {\n"
                + "    var s = json.decode<String>(\"" + jsonLiteral + "\")\n"
                + "    println(s)\n"
                + "}";
        Files.writeString(file, src);
        Path outDir = tmp.resolve("out-" + tag);
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), () -> "compile failed: " + result.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder("java", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8",
                "-cp", outDir.toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), () -> "run falhou: " + out);
        return out;
    }

    @Test
    void tabEscapeIsUnescapedOnJvm() throws Exception {
        // the AUDIT's repro: the JSON text is QUOTED — "x<BS>ty" → x<TAB>y
        String quote = String.valueOf((char) 34);
        String out = runJvm("tab", BS + quote + "x" + BS + BS + "ty" + BS + quote);
        assertEquals("x\ty", out, "o \\t deve virar TAB de verdade");
    }

    @Test
    void unicodeEscapeIsUnescapedOnJvm() throws Exception {
        // JSON text <BS>u0041 → 'A' (the native's broken face: "u0041")
        String quote2 = String.valueOf((char) 34);
        String out = runJvm("uni", BS + quote2 + BS + BS + "u0041" + BS + quote2);
        assertEquals("A", out, "\\u0041 deve virar A e as aspas JSON caem");
    }

    @Test
    void newlineEscapeIsUnescapedOnJvm() throws Exception {
        // JSON text a<BS>nb → a real newline (2 lines) + length 3
        String out = runJvm("nl", "a" + BS + "nb");
        assertEquals("a\nb", out, "o \\n deve virar newline");
    }

    @Test
    void backslashEscapeIsUnescapedOnJvm() throws Exception {
        // JSON text a<BS><BS>nb → one literal backslash + nb
        String out = runJvm("bs", "a" + BS + BS + "nb");
        assertEquals("a" + BS + "nb", out, "o \\\\ deve virar UMA barra");
    }
}
