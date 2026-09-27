package dev.kof.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * #566, opção (b) (DECISIONS.md, D-RELEASE-0.5.0-GATE addendum), lado PRODUTOR: a release do
 * {@code kof deploy} carrega as FONTES do módulo em {@code src/<caminho>.kf}, cobertas pelo
 * {@code SHA256SUMS}; o consumidor ({@link DepsSources}) instala e resolve o {@code import} contra
 * elas. Só {@code .kf/.kof}, sem symlinks, sem diretórios ocultos/de teste/de saída — nada além do
 * código-fonte do módulo é publicado.
 */
final class DeploySources {

    private DeploySources() {}

    /** Diretórios que nunca fazem parte da superfície publicada (testes, saídas, dependências). */
    private static final Set<String> SKIP_DIRS = Set.of("tests", "test", "dist", "build", "target",
            "node_modules");

    /**
     * Todos os {@code .kf/.kof} sob {@code root} (recursivo, ordenado), exceto {@code SKIP_DIRS},
     * diretórios ocultos, {@code extraSkipDir} (ex.: o frontend {@code web/} de um full-stack) e o
     * diretório de saída do deploy quando ele mora dentro do módulo.
     */
    static List<Path> collectTree(Path root, Path outDir, String extraSkipDir) throws IOException {
        Path base = root.toAbsolutePath().normalize();
        Path out = outDir == null ? null : outDir.toAbsolutePath().normalize();
        List<Path> sources = new ArrayList<>();
        try (var walk = Files.walk(base)) {
            for (Path p : (Iterable<Path>) walk.sorted()::iterator) {
                if (!Files.isRegularFile(p, java.nio.file.LinkOption.NOFOLLOW_LINKS)) continue;
                if (!KofCliSupport.isKofSource(p)) continue;
                Path rel = base.relativize(p);
                boolean skip = false;
                for (int i = 0; i < rel.getNameCount() - 1; i++) {
                    String dir = rel.getName(i).toString();
                    if (dir.startsWith(".") || SKIP_DIRS.contains(dir) || dir.equals(extraSkipDir)) {
                        skip = true;
                        break;
                    }
                }
                if (skip || (out != null && p.startsWith(out))) continue;
                sources.add(p);
            }
        }
        return sources;
    }

    /**
     * Copia as fontes para {@code releaseDir/src/<rel>} e devolve os caminhos relativos da release
     * ({@code src/...}), na ordem de entrada — os que vão ao tar e ao SHA256SUMS.
     */
    static List<Path> stage(Path root, List<Path> sources, Path releaseDir) throws IOException {
        Path base = root.toAbsolutePath().normalize();
        List<Path> staged = new ArrayList<>();
        for (Path p : sources) {
            Path rel = Path.of(DepsSources.DIR).resolve(base.relativize(p.toAbsolutePath().normalize()).toString());
            String name = rel.toString().replace('\\', '/');
            if (name.length() > 99) {
                throw new IOException("source path too long for the package (tar limit): " + name);
            }
            Path dst = releaseDir.resolve(rel);
            Files.createDirectories(dst.getParent());
            Files.copy(p, dst, StandardCopyOption.REPLACE_EXISTING);
            staged.add(rel);
        }
        return staged;
    }
}
