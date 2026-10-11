package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class UiIconNameE2ETest extends ComponentCoreSupport {

    private CompilationResult compile(Path tempDir, String name, String program, Target target) throws IOException {
        Path src = tempDir.resolve(name + ".kf");
        Files.writeString(src, program);
        return driver.compile(src, tempDir.resolve("out-" + name + "-" + target), target);
    }

    private static void assertOneSem079(CompilationResult result, String badName) {
        assertFalse(result.success(), "an unknown literal icon name must not compile");
        String diags = result.diagnostics().getDiagnostics().toString();
        assertEquals(1, diags.split("SEM079", -1).length - 1, "exactly one SEM079, was: " + diags);
        assertTrue(diags.contains("'" + badName + "' is not a kof.ui icon"), "names the bad icon: " + diags);
        assertTrue(diags.contains("trash") && diags.contains("star"), "lists the valid icons: " + diags);
    }

    @Test
    void literalUnknownIconIsDiagnosedOnEveryTarget(@TempDir Path tempDir) throws IOException {
        String program = """
                main() {
                    var w = Window("A")
                    w.bind(Icon("tarsh"))
                    w.show()
                }
                """;
        assertOneSem079(compile(tempDir, "ctor", program, Target.JS), "tarsh");
        assertOneSem079(compile(tempDir, "ctor", program, Target.JVM), "tarsh");
    }

    @Test
    void literalUnknownSizedIconIsDiagnosed(@TempDir Path tempDir) throws IOException {
        assertOneSem079(compile(tempDir, "sized", """
                main() {
                    var i = Icon("hom", 16)
                    println(i.name())
                }
                """, Target.JS), "hom");
    }

    @Test
    void literalUnknownSetNameIsDiagnosedOnEveryTarget(@TempDir Path tempDir) throws IOException {
        String program = """
                main() {
                    var i = Icon("star")
                    i.setName("strr")
                    println(i.name())
                }
                """;
        assertOneSem079(compile(tempDir, "setname", program, Target.JS), "strr");
        assertOneSem079(compile(tempDir, "setname", program, Target.JVM), "strr");
    }

    @Test
    void everyKnownIconStillCompiles(@TempDir Path tempDir) throws IOException {
        StringBuilder body = new StringBuilder();
        for (String name : KofUiIcons.NAMES) {
            body.append("    println(Icon(\"").append(name).append("\").name())\n");
        }
        CompilationResult result = compile(tempDir, "known", "main() {\n" + body + "}\n", Target.JS);
        assertTrue(result.success(), "valid icon names compile: " + result.diagnostics().getDiagnostics());
    }

    @Test
    void compilerListMatchesTheRuntimeIconMap() throws IOException {
        String runtime = Files.readString(Path.of(
                "src/main/java/dev/kof/compiler/js/JsRuntimeUiLinkImageIcon.java"));
        int start = runtime.indexOf("const KOF_ICONS = {");
        int end = runtime.indexOf("};", start);
        assertTrue(start >= 0 && end > start, "runtime icon map found");
        List<String> runtimeNames = new ArrayList<>();
        Matcher m = Pattern.compile("(?m)^\\s*([a-zA-Z]+):").matcher(runtime.substring(start, end));
        while (m.find()) runtimeNames.add(m.group(1));
        assertEquals(KofUiIcons.NAMES, runtimeNames,
                "the compiler's icon list and the KofJS runtime map must be the same set, in order");
    }

    private static final String COMPUTED = """
            String pick(String a, String b) { return a + b }
            main() {
                var w = Window("A")
                var bad = Icon(pick("tar", "sh"))
                println("bad=[" + bad.name() + "]")
                var fav = Icon("star")
                fav.setName(pick("st", "rr"))
                println("fav=" + fav.name())
                w.bind(bad)
                w.bind(fav)
                w.show()
            }
            """;

    @Test
    void computedUnknownNameFailsVisiblyAtRuntime(@TempDir Path tempDir) throws IOException {
        String probe = """
                const paths = Object.values(window.__kofNodes)
                    .filter((n) => String(n.tagName).toUpperCase() === "SVG")
                    .map((n) => n.children[0].getAttribute("d").length > 0);
                console.log("drawn=" + paths.join(","));
                """;
        String out = runJsProbe(tempDir, "computed", COMPUTED, probe);
        assertTrue(out.contains("[kof] Icon: 'tarsh' is not a kof.ui icon"), "constructor reports: " + out);
        assertTrue(out.contains("[kof] Icon.setName: 'strr' is not a kof.ui icon"), "setName reports: " + out);
        assertTrue(out.contains("bad=[]"), "an unknown icon draws nothing and name() says so: " + out);
        assertTrue(out.contains("fav=star"), "setName with an unknown name keeps the previous icon: " + out);
        assertTrue(out.contains("drawn=false,true"), "no silent 'close' glyph; the star stays drawn: " + out);
    }

    @Test
    void computedNamesCompileAndRunOnJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("iconjvm.kf");
        Files.writeString(source, COMPUTED);
        runJvm(source, tempDir.resolve("jvm-icon"), "bad=[]\nfav=");
    }

    @Test
    void computedNamesCompileAndRunOnNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("iconnative.kf");
        Files.writeString(source, COMPUTED);
        runNative(source, tempDir.resolve("native-icon"), "bad=[]\nfav=");
    }
}
