package dev.kof.compiler;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #612: guard contra o separador de classpath fixo {@code ":"} em ProcessBuilder
 * de teste — no Windows o separador é {@code ;} e o processo filho falha com
 * {@code ClassNotFoundException: Run} (o Linux nunca vê; o bug ficava invisível
 * no CI). O padrão correto é {@code java.io.File.pathSeparator} (KofOrmE2ETest
 * já o usava). O guard varre os fontes DE TESTE deste módulo e falha nomeando o
 * arquivo:linha de qualquer {@code -cp} montado com {@code ":"} literal — o
 * próximo teste não pode reintroduzir o bug.
 */
class ClasspathSeparatorGuardTest {

    private static final Pattern BAD_CP = Pattern.compile(
            "\"-(cp|classpath)\"[^;]{0,200}?\\+\\s*\":\"\\s*\\+");

    private static Path testSrc() {
        // surefire roda com basedir = módulo; sobe um nível se estiver em target/.
        Path cwd = Path.of("").toAbsolutePath();
        Path src = cwd.resolve("src/test/java");
        if (Files.isDirectory(src)) return src;
        Path parent = cwd.getParent();
        assertTrue(parent != null && Files.isDirectory(parent.resolve("src/test/java")),
                "src/test/java não encontrado a partir de " + cwd);
        return parent.resolve("src/test/java");
    }

    @Test
    void noTestClasspathUsesHardcodedColonSeparator() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(testSrc())) {
            files.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                List<String> lines;
                try {
                    lines = Files.readAllLines(p, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                for (int i = 0; i < lines.size(); i++) {
                    if (BAD_CP.matcher(lines.get(i)).find()) {
                        offenders.add(p.getFileName() + ":" + (i + 1) + "  " + lines.get(i).trim());
                    }
                }
            });
        }
        assertTrue(offenders.isEmpty(),
                "#612: classpath de teste com ':' fixo (use java.io.File.pathSeparator):\n"
                        + String.join("\n", offenders));
    }
}
