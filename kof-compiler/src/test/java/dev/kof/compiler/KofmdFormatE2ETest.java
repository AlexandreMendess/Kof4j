package dev.kof.compiler;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-KOFMD Fase 3 fatia 3.5 — deterministic canonical formatter (§11/§12).
 * Continuity keys {@code last/doing/next/location/state} first in that order,
 * remaining fields in ascending byte order; prose, code fences and headings
 * are never touched; {@code format(format(x)) == format(x)}. The CLI
 * {@code kof md format} (slice 3.6) wraps this entry point.
 */
class KofmdFormatE2ETest extends KofmdRunSupport {



    @Test
    void continuityOrderAndByteOrder() throws Exception {
        runKof("""
            import kofmd.Kofmd

            main() {
                var tool = KofmdTool()
                var out = tool.format(tool.parse("name: a\\nstate: active\\nlast: prev\\ndoing: x\\n"))
                if (out != "last: prev\\ndoing: x\\nstate: active\\nname: a\\n") {
                    throw "order golden: [" + out + "]"
                }
                var again = tool.format(tool.parse(out))
                if (again != out) {
                    throw "format not idempotent: [" + again + "]"
                }
                println("kofmd-3.5-order-ok")
            }
            """);
    }

    @Test
    void proseFenceAndItemsUntouchedAndDeterministic() throws Exception {
        runKof("""
            import kofmd.Kofmd

            main() {
                var tool = KofmdTool()
                var source = "# Title\\n\\nzeta: 1\\nalpha: 2\\n\\ninstructions:\\n  - inspect\\n  - test\\n\\n```\\nkeep: raw\\n```\\n\\nlast: a\\nbeta: b\\n"
                var out = tool.format(tool.parse(source))
                if (!out.startsWith("# Title\\n\\n")) {
                    throw "heading touched: [" + out + "]"
                }
                if (!out.contains("```\\nkeep: raw\\n```")) {
                    throw "fence bytes changed: [" + out + "]"
                }
                if (!out.contains("alpha: 2\\nzeta: 1")) {
                    throw "field block not sorted: [" + out + "]"
                }
                if (!out.contains("instructions:\\n  - inspect\\n  - test")) {
                    throw "list item order changed: [" + out + "]"
                }
                if (!out.endsWith("last: a\\nbeta: b\\n")) {
                    throw "continuity key not first: [" + out + "]"
                }
                var twice = tool.format(tool.parse(out))
                if (twice != out) {
                    throw "second format differs: [" + twice + "]"
                }
                var sameDoc = tool.format(tool.parse("beta: b\\nlast: a\\n"))
                var otherDoc = tool.format(tool.parse("last: a\\nbeta: b\\n"))
                if (sameDoc != otherDoc) {
                    throw "same semantics, different bytes"
                }
                println("kofmd-3.5-untouched-ok")
            }
            """);
    }

    
    @Override
    protected void copyLibrary(Path destinationRoot) throws Exception {
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
