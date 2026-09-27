package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-KOFMD Fase 3 fatia 3.4 — Markdown round-trip. {@code renderMarkdown} emits
 * canonical bytes (§11: one space after ":", two-space list items, blocks
 * separated by one blank line, fence delimiters stay glued to their content)
 * and {@code parse(render(parse(x)))} is semantically equal to {@code parse(x)}
 * with prose bytes untouched (§13). A canonical input must round-trip to
 * IDENTICAL bytes. Quoted values keep their quotes so the inferred type
 * survives (§11.7).
 */
class KofmdRoundTripE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    @Test
    void canonicalTextRoundTripsByteIdentical() throws Exception {
        runKof("""
            import kofmd.Kofmd

            main() {
                var tool = KofmdTool()
                var canonical = "@decision\\nAdopt narrowing.\\n\\n"
                canonical += "doing: parser\\n\\n"
                canonical += "instructions:\\n  - inspect\\n  - preserve-api\\n\\n"
                canonical += "state: \\"done\\"\\n\\n"
                canonical += "```\\nkey: raw\\n```\\n\\n"
                canonical += "next: 3.4\\n"
                var doc = tool.parse(canonical)
                var rendered = tool.renderMarkdown(doc)
                if (rendered != canonical) {
                    throw "canonical bytes changed:\\n[" + rendered + "]\\nvs\\n[" + canonical + "]"
                }
                if (!docsEqual(tool.parse(rendered), doc)) {
                    throw "semantic drift on canonical round-trip"
                }
                var twice = tool.renderMarkdown(tool.parse(tool.renderMarkdown(doc)))
                if (twice != rendered) {
                    throw "render not stable: [" + twice + "]"
                }
                println("kofmd-3.4-canonical-ok")
            }

            docsEqual(KofmdDoc a, KofmdDoc b): Bool {
                if (a.blocks().size != b.blocks().size) {
                    return false
                }
                var index = 0
                while (index < a.blocks().size) {
                    if (!blockEq(a.blocks().get(index), b.blocks().get(index))) {
                        return false
                    }
                    index = index + 1
                }
                return true
            }

            blockEq(KofmdBlock a, KofmdBlock b): Bool {
                if (!strListEq(a.intents(), b.intents())) {
                    return false
                }
                if (!strListEq(a.prose(), b.prose())) {
                    return false
                }
                if (a.fields().size != b.fields().size) {
                    return false
                }
                var index = 0
                while (index < a.fields().size) {
                    KofmdField x = a.fields().get(index)
                    KofmdField y = b.fields().get(index)
                    if (x.name() != y.name() || x.value() != y.value()) {
                        return false
                    }
                    if (x.quoted() != y.quoted()) {
                        return false
                    }
                    if (!strListEq(x.items(), y.items())) {
                        return false
                    }
                    index = index + 1
                }
                return true
            }

            strListEq(List<String> a, List<String> b): Bool {
                if (a.size != b.size) {
                    return false
                }
                var index = 0
                while (index < a.size) {
                    if (a.get(index) != b.get(index)) {
                        return false
                    }
                    index = index + 1
                }
                return true
            }
            """);
    }

    @Test
    void degradedInputKeepsSemanticsAndQuotedTypes() throws Exception {
        runKof("""
            import kofmd.Kofmd

            main() {
                var tool = KofmdTool()
                var degraded = "instructions:\\n- inspect\\n- test\\n\\n\\ncount: \\"123\\"\\nname: raw text\\n"
                var doc = tool.parse(degraded)
                var rendered = tool.renderMarkdown(doc)
                if (rendered == degraded) {
                    throw "degraded text must be normalized"
                }
                if (!rendered.contains("  - inspect")) {
                    throw "list not canonicalized: [" + rendered + "]"
                }
                if (!rendered.contains("count: \\"123\\"")) {
                    throw "quoted value lost its quotes: [" + rendered + "]"
                }
                var reparsed = tool.parse(rendered)
                var counts = 0
                var lists = 0
                var index = 0
                while (index < reparsed.blocks().size) {
                    var field = 0
                    while (field < reparsed.blocks().get(index).fields().size) {
                        KofmdField f = reparsed.blocks().get(index).fields().get(field)
                        if (f.name() == "count") {
                            counts = counts + 1
                            if (!f.quoted()) {
                                throw "count must stay quoted"
                            }
                            if (tool.inferField(f) != "String") {
                                throw "count re-typed to " + tool.inferField(f)
                            }
                        }
                        if (f.name() == "name" && f.value() != "raw text") {
                            throw "name value drifted"
                        }
                        if (f.name() == "instructions") {
                            lists = lists + 1
                            if (f.items().size != 2 || f.items().get(0) != "inspect" || f.items().get(1) != "test") {
                                throw "instructions items lost"
                            }
                        }
                        field = field + 1
                    }
                    index = index + 1
                }
                if (counts != 1 || lists != 1) {
                    throw "fields lost in round-trip: counts=" + counts.toString() + " lists=" + lists.toString()
                }
                if (!docsEqual(reparsed, doc)) {
                    throw "degraded round-trip semantic drift vs original parse"
                }
                println("kofmd-3.4-degraded-ok")
            }

            docsEqual(KofmdDoc a, KofmdDoc b): Bool {
                if (a.blocks().size != b.blocks().size) {
                    return false
                }
                var index = 0
                while (index < a.blocks().size) {
                    KofmdBlock x = a.blocks().get(index)
                    KofmdBlock y = b.blocks().get(index)
                    if (x.intents().size != y.intents().size || x.prose().size != y.prose().size) {
                        return false
                    }
                    if (x.fields().size != y.fields().size) {
                        return false
                    }
                    var field = 0
                    while (field < x.fields().size) {
                        KofmdField fx = x.fields().get(field)
                        KofmdField fy = y.fields().get(field)
                        if (fx.name() != fy.name() || fx.value() != fy.value()) {
                            return false
                        }
                        if (fx.quoted() != fy.quoted() || fx.items().size != fy.items().size) {
                            return false
                        }
                        field = field + 1
                    }
                    index = index + 1
                }
                return true
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
        if (isKofmdLibraryDir(fromRepository)) return fromRepository;

        Path fromModule = workingDirectory.resolve("../libs/kofmd").normalize();
        if (isKofmdLibraryDir(fromModule)) return fromModule;

        throw new IllegalStateException("libs/kofmd not found from " + workingDirectory);
    }

    private static boolean isKofmdLibraryDir(Path dir) {
        // Accepts both layouts: the monolith (Kofmd.kf alone) and the split
        // (KofmdTypes.kf + sibling responsibility files + Kofmd.kf facade).
        return Files.isRegularFile(dir.resolve("Kofmd.kf"))
                || Files.isRegularFile(dir.resolve("KofmdTypes.kf"));
    }
}
