package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Auditoria de paridade (frente de revisão, 21/09) — Fatia 10: os símbolos de
 * coleção ({@code kof_list_*}/{@code kof_map_*}/{@code kof_set_*}) emitidos pelo
 * lowering têm de ter handler em TODOS os alvos (JVM via
 * {@code JvmOpCollections}/runtime, JS e Native x86/riscv). Diferente do §424 do
 * {@code String}, aqui o lowering é um mapa explícito de nome→símbolo, então a
 * presença por alvo é verificável mecanicamente.
 *
 * <p>É um ratchet de PRESENÇA (o token existe no alvo), não uma prova semântica:
 * pega a regressão mais cara (símbolo novo no lowering sem handler num alvo, que
 * vira link-fail silencioso). O golden é MEDIDO da fonte (Q3), nunca de memória.
 */
class CollectionSymbolTargetCoverageTest {

    private static final List<String> LOWERING_FILES = List.of(
            "CollectionCallLowerer.java",
            "CollectionMethodGates.java",
            "CollectionValueOps.java");

    private static String read(String rel) throws Exception {
        return Files.readString(Path.of("src/main/java/dev/kof/compiler/" + rel));
    }

    /** Símbolos de coleção que o lowering pode emitir. */
    private static Set<String> emittedSymbols() throws Exception {
        var syms = new LinkedHashSet<String>();
        for (String f : LOWERING_FILES) {
            Matcher m = Pattern.compile("\"(kof_(?:list|map|set)_[a-z0-9_]+)\"").matcher(read(f));
            while (m.find()) {
                syms.add(m.group(1));
            }
        }
        return syms;
    }

    /** Todos os fontes de um backend (pasta) + os tokens de coleção que citam. */
    private static Set<String> tokensIn(String backendDir) throws Exception {
        var found = new LinkedHashSet<String>();
        Path root = Path.of("src/main/java/dev/kof/compiler/" + backendDir);
        try (var walk = Files.walk(root)) {
            for (Path p : walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher m = Pattern.compile("\\b(kof_(?:list|map|set)_[a-z0-9_]+)\\b")
                        .matcher(Files.readString(p));
                while (m.find()) {
                    found.add(m.group(1));
                }
            }
        }
        return found;
    }

    @Test
    @DisplayName("todo símbolo de coleção emitido tem handler JVM, JS e Native (presença)")
    void everyCollectionSymbolIsHandledOnAllTargets() throws Exception {
        Set<String> emitted = emittedSymbols();
        assertTrue(emitted.size() >= 25,
                "parse do lowering vazio/curto (" + emitted.size()
                        + ") — arquivos-fonte renomeados? o ratchet falharia em vacuo");

        Set<String> jvm = tokensIn("jvm");
        Set<String> js = tokensIn("js");
        Set<String> nat = tokensIn("nat");

        assertCovered("JVM", emitted, jvm);
        assertCovered("JS", emitted, js);
        assertCovered("NATIVE", emitted, nat);
    }

    private static void assertCovered(String target, Set<String> emitted, Set<String> handled) {
        var missing = new TreeSet<>(emitted);
        missing.removeAll(handled);
        assertEquals(Set.of(), missing, () -> target
                + ": símbolo de coleção emitido SEM handler (link-fail silencioso): " + missing
                + " — adicione o handler no alvo ou catalogue o gap honesto (R6)");
    }
}
