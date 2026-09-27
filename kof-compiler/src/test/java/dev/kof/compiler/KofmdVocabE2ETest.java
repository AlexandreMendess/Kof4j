package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-KOFMD Fase 3 fatia 3.3 — agent-memory vocabulary validation against the
 * closed sets of {@code docs/spec/kofmd.md} Appendix A. Intents outside the
 * reserved set are a named diagnostic ({@code MD002:<line>:@<name>}, §6.3 "never a
 * silent extension"); free-prose {@code instructions} are {@code MD002:<line>:instructions}
 * (§10 "MUST NOT be free prose"); {@code state}/{@code result} advisory sets
 * are predicates only — enforcement deferred by §9, so {@code validateVocabulary}
 * never flags them. JVM-first (other targets: honest {@code MD001}).
 */
class KofmdVocabE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    @Test
    void closedSetPredicates() throws Exception {
        runKof("""
            import kofmd.Kofmd

            main() {
                var tool = KofmdTool()
                for (var name in listOf("information", "decision", "requirement", "task",
                        "question", "answer", "result", "warning", "configuration",
                        "example", "api", "message", "explanation", "release")) {
                    if (!tool.isKnownIntent(name)) {
                        throw "intent missing from closed set: " + name
                    }
                }
                if (tool.isKnownIntent("todo")) {
                    throw "todo must not be a reserved intent"
                }
                if (tool.isKnownIntent("TODO")) {
                    throw "intent vocabulary is case-sensitive"
                }

                if (!tool.isKnownInstruction("preserve-api") || !tool.isKnownInstruction("run-tests")) {
                    throw "known instruction rejected"
                }
                if (tool.isKnownInstruction("yolo")) {
                    throw "unknown instruction accepted"
                }

                if (!tool.isAdvisoryValue("state", "blocked") || !tool.isAdvisoryValue("result", "pass")) {
                    throw "advisory token rejected"
                }
                if (tool.isAdvisoryValue("result", "yolo")) {
                    throw "advisory unknown accepted"
                }
                if (tool.isAdvisoryValue("doing", "blocked")) {
                    throw "advisory set must be state/result only"
                }
                println("kofmd-3.3-predicates-ok")
            }
            """);
    }

    @Test
    void vocabularyDiagnosticsGoldens() throws Exception {
        runKof("""
            import kofmd.Kofmd

            main() {
                var tool = KofmdTool()

                var clean = tool.parse("@decision\\nAdopt narrowing.\\n\\ninstructions:\\n- inspect\\n- preserve-api\\n\\nstate: in-review\\n")
                var cleanDiags = tool.validateVocabulary(clean)
                if (cleanDiags.size != 0) {
                    throw "clean doc flagged: " + joinDiags(cleanDiags)
                }

                var mixed = tool.parse("@decision\\n@todo\\n@zzz\\nkeep prose\\n")
                var diags = tool.validateVocabulary(mixed)
                if (diags.size != 2) {
                    throw "unknown-intent count: " + joinDiags(diags)
                }
                if (diags.get(0) != "MD002:2:@todo" || diags.get(1) != "MD002:3:@zzz") {
                    throw "unknown-intent goldens: " + joinDiags(diags)
                }

                if (tool.validateVocabulary(tool.parse("instructions: inspect\\n")).size != 0) {
                    throw "inline single instruction flagged"
                }
                var inlineProse = tool.parse("instructions: read the entire file\\n")
                if (joinDiags(tool.validateVocabulary(inlineProse)) != "MD002:1:instructions") {
                    throw "inline prose: " + joinDiags(tool.validateVocabulary(inlineProse))
                }
                var listProse = tool.parse("instructions:\\n- inspect\\n- read the entire file\\n")
                if (joinDiags(tool.validateVocabulary(listProse)) != "MD002:1:instructions") {
                    throw "list prose: " + joinDiags(tool.validateVocabulary(listProse))
                }
                if (tool.validateVocabulary(tool.parse("instructions:\\n")).size != 0) {
                    throw "empty instructions must not be prose"
                }

                if (tool.validateVocabulary(tool.parse("state: totally-unknown\\n")).size != 0) {
                    throw "advisory value must not be flagged in 0.5.0 (deferred, spec sec 9)"
                }

                var again = tool.validateVocabulary(mixed)
                if (again.size != diags.size) {
                    throw "validator leaked state between calls"
                }
                println("kofmd-3.3-goldens-ok")
            }

            joinDiags(List<String> diags): String {
                var joined = ""
                var index = 0
                while (index < diags.size) {
                    if (index > 0) {
                        joined = joined + ","
                    }
                    joined = joined + diags.get(index)
                    index = index + 1
                }
                return joined
            }
            """);
    }

    @Test
    void fenceAndProseNeverProduceIntents() throws Exception {
        runKof("""
            import kofmd.Kofmd

            main() {
                var tool = KofmdTool()
                var fenced = tool.parse("```\\n@todo\\nkey: value\\n```\\n")
                if (tool.validateVocabulary(fenced).size != 0) {
                    throw "fence content must stay prose"
                }
                if (tool.validateVocabulary(tool.parse("# Title\\n\\njust prose\\n")).size != 0) {
                    throw "prose flagged"
                }
                println("kofmd-3.3-fence-ok")
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
