package dev.kof.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Fonte de verdade unica da lib Kofmd para o CLI e o LSP (rule 12: a
 * politica vive na lib Kof pura; aqui so mecanismo). O motor e a arvore de
 * `.kf` do pacote `kofmd` — nunca uma copia mono-arquivo (o split em facade
 * + partes tornaria qualquer copia em poeira no primeiro commit, como
 * medido em 27/09). Ordem: (1) distribuicao instalada
 * `$kof.install.dir/lib/kof-libs/kofmd`; (2) arvore do repositorio
 * `libs/kofmd` andando ate a raiz (modo dev/teste); (3) resource empacotado
 * no jar (`dev/kof/kofmd/<file>.kf`, gravado pelo package.sh a partir da
 * MESMA arvore). Sem fonte = null -> o chamador emite MD001 honesto (R6).
 */
final class KofmdLibrary {

    private static final List<String> PARTS = List.of("KofmdTypes", "KofmdLists", "KofmdInfer",
            "KofmdSchemas", "KofmdScan", "KofmdVocab", "KofmdRender", "KofmdFormat", "Kofmd");

    private KofmdLibrary() {}

    static boolean available() {
        return resolveDirectory() != null || resourceUsable();
    }

    /** Copia o pacote para `destRoot/kofmd/*.kf`; retorna false se nao ha fonte. */
    static boolean installTo(Path destRoot) throws IOException {
        Path dir = resolveDirectory();
        Path target = destRoot.resolve("kofmd");
        Files.createDirectories(target);
        if (dir != null) {
            try (Stream<Path> files = Files.list(dir)) {
                for (Path kf : files.filter(f -> f.toString().endsWith(".kf")).toList()) {
                    Files.copy(kf, target.resolve(kf.getFileName().toString()),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
            return true;
        }
        boolean any = false;
        for (String part : PARTS) {
            try (InputStream in = KofmdLibrary.class.getResourceAsStream("/dev/kof/kofmd/" + part + ".kf")) {
                if (in == null) {
                    continue;
                }
                Files.copy(in, target.resolve(part + ".kf"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                any = true;
            }
        }
        return any;
    }

    /** Compila o pacote e devolve o loader vivo da lib `kofmd` (ou null). */
    static URLClassLoader compile(String sourceName, String mainSource, Path srcRoot, Path outRoot)
            throws Exception {
        Path installRoot = Files.createTempDirectory("kofmd-engine-");
        String previous = System.getProperty("kof.install.dir");
        try {
            if (!installTo(installRoot.resolve("lib").resolve("kof-libs"))) {
                return null;
            }
            Path main = srcRoot.resolve(sourceName);
            Files.writeString(main, mainSource);
            System.setProperty("kof.install.dir", installRoot.toString());
            var result = new dev.kof.compiler.CompilerDriver().compile(
                    main, outRoot, dev.kof.compiler.Target.JVM);
            if (!result.success()) {
                return null;
            }
            return new URLClassLoader(new URL[]{outRoot.toUri().toURL()},
                    KofmdLibrary.class.getClassLoader());
        } finally {
            if (previous == null) System.clearProperty("kof.install.dir");
            else System.setProperty("kof.install.dir", previous);
            deleteTree(installRoot);
        }
    }

    private static Path resolveDirectory() {
        Path install = Path.of(System.getProperty("kof.install.dir", ""),
                "lib", "kof-libs", "kofmd");
        if (hasParts(install)) {
            return install;
        }
        Path working = Path.of("").toAbsolutePath().normalize();
        for (Path base : List.of(working, working.getParent(),
                working.getParent() == null ? working : working.getParent().getParent())) {
            Path candidate = base.resolve("libs").resolve("kofmd");
            if (hasParts(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean hasParts(Path dir) {
        return Files.isRegularFile(dir.resolve("Kofmd.kf"));
    }

    private static boolean resourceUsable() {
        return KofmdLibrary.class.getResourceAsStream("/dev/kof/kofmd/Kofmd.kf") != null;
    }

    private static void deleteTree(Path root) {
        try (Stream<Path> s = Files.walk(root)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}
