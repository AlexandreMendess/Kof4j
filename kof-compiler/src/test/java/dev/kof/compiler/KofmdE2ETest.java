package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-KOFMD Fase 3 fatia 3.1 — the pure-Kof block scanner in {@code libs/kofmd}
 * parses {@code TypedField}/{@code @intent}/list/fence/schema shapes into
 * {@code KofmdDoc} records (JVM-first; other targets = honest {@code MD001}).
 */
class KofmdE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    @Test
    void typedFieldsAndIntentAndListAndFenceParse() throws Exception {
        runKof("""
            import kofmd.Kofmd

            main() {
                var tool = KofmdTool()
                var doc = tool.parse("doing: parser\\nnext: tests\\n")
                if (doc.blocks().size != 1) {
                    throw "blocks: " + doc.blocks().size.toString()
                }
                if (doc.blocks().get(0).fields().size != 2) {
                    throw "fields: " + doc.blocks().get(0).fields().size.toString()
                }
                if (doc.blocks().get(0).fields().get(0).name() != "doing") {
                    throw "name0"
                }
                if (doc.blocks().get(0).fields().get(1).value() != "tests") {
                    throw "value1"
                }

                var intent = tool.parse("@decision\\nAdopt narrowing.\\n")
                if (intent.blocks().get(0).intents().size != 1) {
                    throw "intents"
                }
                if (intent.blocks().get(0).intents().get(0) != "decision") {
                    throw "intent-name"
                }

                var listed = tool.parse("instructions:\\n- inspect\\n- test\\n")
                if (listed.blocks().get(0).fields().get(0).items().size != 2) {
                    throw "items"
                }
                if (listed.blocks().get(0).fields().get(0).items().get(1) != "test") {
                    throw "item1"
                }

                var fenced = tool.parse("```\\nkey: value\\n```\\n")
                if (fenced.blocks().get(0).fields().size != 0) {
                    throw "fence must stay prose"
                }
                if (fenced.blocks().get(0).prose().size == 0) {
                    throw "fence prose lost"
                }

                var prose = tool.parse("# Title\\n\\nplain prose\\n")
                if (prose.blocks().size == 0) {
                    throw "prose blocks lost"
                }
                if (prose.blocks().get(0).fields().size != 0) {
                    throw "prose must carry zero fields"
                }
                println("kofmd-3.1-ok")
            }
            """);
    }

    @Test
    void quotedScalarAndIndentedLineStayVerbatim() throws Exception {
        runKof("""
            import kofmd.Kofmd

            main() {
                var tool = KofmdTool()
                var probe = tool.parse("version: \\"0.5.0\\"\\nretries: 3\\n")
                if (probe.blocks().get(0).fields().get(0).value() != "0.5.0") {
                    throw "quoted value"
                }
                if (!probe.blocks().get(0).fields().get(0).quoted()) {
                    throw "quoted flag"
                }
                if (probe.blocks().get(0).fields().get(1).quoted()) {
                    throw "bare must not be quoted"
                }
                var indented = tool.parse("  doing: parser\\n")
                if (indented.blocks().get(0).fields().size != 0) {
                    throw "indented line must stay prose"
                }
                println("kofmd-3.1-edges-ok")
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
