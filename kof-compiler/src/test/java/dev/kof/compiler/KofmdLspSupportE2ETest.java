package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-KOFMD Fase 3 fatia 3.7 — the surfaces the LSP hook consumes: keyLabel
 * (§9 kind per special key, empty for plain TypedFields) and the already
 * line-carrying vocabulary diagnostics (§14) resolved per construct line so
 * publishDiagnostics can place a range without re-scanning the buffer.
 */
class KofmdLspSupportE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    @Test
    void keyLabelCoversVocabularyKinds() throws Exception {
        runKof("""
            import kofmd.Kofmd

            main() {
                var tool = KofmdTool()
                if (tool.keyLabel("doing") != "continuity" || tool.keyLabel("last") != "continuity") {
                    throw "continuity keys mislabeled"
                }
                if (tool.keyLabel("state") != "status" || tool.keyLabel("instructions") != "action") {
                    throw "status/action mislabeled"
                }
                if (tool.keyLabel("reason") != "modifier" || tool.keyLabel("symbol") != "modifier") {
                    throw "modifiers mislabeled"
                }
                if (tool.keyLabel("question") != "dialogue" || tool.keyLabel("answer") != "dialogue") {
                    throw "dialogue mislabeled"
                }
                if (tool.keyLabel("name") != "" || tool.keyLabel("") != "") {
                    throw "plain keys must have no special label"
                }
                println("kofmd-3.7-label-ok")
            }
            """);
    }

    @Test
    void hoverInferenceAndDiagnosticLinesAlign() throws Exception {
        runKof("""
            import kofmd.Kofmd

            main() {
                var tool = KofmdTool()
                var doc = tool.parse("doing: parser\\n\\nretries: 3\\n@todo\\nflag: \\"42\\"\\n")

                var doing = doc.blocks().get(0).fields().get(0)
                if (doing.line() != 1) {
                    throw "doing line: " + doing.line().toString()
                }
                if (tool.keyLabel(doing.name()) != "continuity") {
                    throw "hover label doing"
                }

                var second = doc.blocks().get(1).fields().get(0)
                if (second.name() != "retries" || second.line() != 3) {
                    throw "retries line: " + second.line().toString()
                }
                if (tool.inferField(second) != "Int") {
                    throw "retries inference"
                }
                if (tool.keyLabel(second.name()) != "") {
                    throw "retries must be plain"
                }

                var flag = doc.blocks().get(1).fields().get(1)
                if (flag.name() != "flag" || flag.line() != 5) {
                    throw "flag line: " + flag.line().toString()
                }
                if (tool.inferField(flag) != "String") {
                    throw "quoted 42 must infer String for hover"
                }

                var diags = tool.validateVocabulary(doc)
                if (diags.size != 1) {
                    throw "diags: " + diags.size.toString()
                }
                if (diags.get(0) != "MD002:4:@todo") {
                    throw "diag line golden: " + diags.get(0)
                }
                println("kofmd-3.7-hover-ok")
            }
            """);
    }

    private void runKof(String code) throws Exception {
        Path installRoot = tmp.resolve("kof-install");
        copyLibrary(installRoot.resolve("lib/kof-libs"));
        Path source = tmp.resolve("Main.kf");
        Files.writeString(source, code);
        Path out = Files.createTempDirectory(tmp, "kofmd-out-");
        String previousInstallDir = System.getProperty("kof.install.dir");
        CompilationResult result;
        System.setProperty("kof.install.dir", installRoot.toString());
        try {
            result = driver.compile(source, out, Target.JVM);
        } finally {
            if (previousInstallDir == null) System.clearProperty("kof.install.dir");
            else System.setProperty("kof.install.dir", previousInstallDir);
        }
        assertTrue(result.success(), () -> result.diagnostics().getDiagnostics().toString());

        try (var loader = new URLClassLoader(
                new java.net.URL[]{out.toUri().toURL()}, getClass().getClassLoader())) {
            Class.forName("Default.Main", true, loader)
                    .getMethod("main", String[].class)
                    .invoke(null, (Object) new String[0]);
        }
    }

    private static void copyLibrary(Path destinationRoot) throws Exception {
        Path sourceRoot = findLibraryRoot();
        try (var files = Files.walk(sourceRoot)) {
            for (Path source : files.filter(Files::isRegularFile).toList()) {
                Path destination = destinationRoot.resolve("kofmd")
                        .resolve(sourceRoot.relativize(source));
                Files.createDirectories(destination.getParent());
                Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static Path findLibraryRoot() {
        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        Path fromRepository = workingDirectory.resolve("libs/kofmd");
        if (Files.isRegularFile(fromRepository.resolve("Kofmd.kf"))) return fromRepository;

        Path fromModule = workingDirectory.resolve("../libs/kofmd").normalize();
        if (Files.isRegularFile(fromModule.resolve("Kofmd.kf"))) return fromModule;

        throw new IllegalStateException("libs/kofmd not found from " + workingDirectory);
    }
}
