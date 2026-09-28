package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * D-STR-UNICODE (linha 11) — medição comportamental das faces Unicode
 * ({@code toUpperCase}/{@code toLowerCase}/{@code compareToIgnoreCase}/
 * {@code strings.reverse} não-ASCII) contra o oráculo JVM — atualizado 28/09
 * apos a tabela nativa (fatia da lane parity) pousar. Os valores são
 * MEDIDOS neste tip (27/09), não deduzidos: a bateria imprime unidades UTF-16
 * via a face {@code toCharArray} (a única face char provada — não depende de
 * encoding de stdout).
 *
 * <p>Medido: <b>JS == oráculo JVM nas 9 linhas + 10 comparações</b> (built-ins
 * Unicode-corretos, incluindo expansões {@code ß->SS}, {@code İ->i+U+0307},
 * {@code ǰ->J+caron} e sigma final {@code Σ->ς}); no native, a tabela embutida
 * ({@code RuntimeStringCase}, fatia da lane parity) faz o fold <b>SIMPLES por
 * code unit</b> — escopo ratificado do {@code D-STR-UNICODE}: as 4 linhas que
 * divergem do JVM (ß, İ, ǰ, sigma final) são exatamente as exceções de
 * full-mapping/contexto fora do escopo; Latin-1/Grego/Cirilico e o reverse
 * astral coincidem. NAT-STR01 residual = {@code compareToIgnoreCase} no
 * native. Este arquivo é CARACTERIZAÇÃO: trava o oráculo, o port JS e o valor
 * medido de cada alvo. Padrão: StringGapMeasuredTest/§424.
 */
class StringUnicodeFacesMeasuredTest {

    private final CompilerDriver driver = new CompilerDriver();

    // Unidades UTF-16 impressas por linha (length,c0,c1,...) + as 5 comparações.
    private static final String ORACLE_JVM = """
        4,98,55357,56832,97
        4,55357,56832,225,8364
        5,72,201,76,76,79
        6,103,114,252,223,101,110
        7,83,84,82,65,83,83,69
        2,105,775
        2,74,780
        2,913,931
        5,959,32,948,965,962""";

    // Estado medido do native x86_64 apos a tabela embutida (D-STR-UNICODE
    // ratificado: fold SIMPLES por code unit). As linhas que divergem do
    // oraculo JVM (straße 6-vs-7, İ 1-vs-2, ǰ 1-vs-2, sigma final 963-vs-962)
    // sao EXATAMENTE as excecoes de full-mapping/contexto fora do escopo
    // ratificado — as demais (caixa Latin-1/Grec/Cirilico, reverse) coincidem.
    private static final String NATIVE_SIMPLE_FOLD = """
        4,98,55357,56832,97
        4,55357,56832,225,8364
        5,72,201,76,76,79
        6,103,114,252,223,101,110
        6,83,84,82,65,223,69
        1,105
        1,496
        2,913,931
        5,959,32,948,965,963""";

    // compareToIgnoreCase medido no JDK (fold duplo por code unit), incluindo
    // as arestas ẛ/ẞ (7554), prefixo (-1), vazias (0), ǅ/ǆ iguais no up (0)
    // e a ligatura fi vs "fi" (64155 — sem mapeamento simples, diferenca crua).
    private static final String CIC_ORACLE =
            "108\n0\n0\n390\n0\n7554\n-1\n0\n0\n64155";

    private static final String BATTERY = """
        String Units(String s) {
            var a = s.toCharArray()
            var r = "" + (a.length as Int)
            for (var i = 0; i < a.length; i++) {
                r = r + "," + (a[i] as Int)
            }
            return r
        }
        main() {
            println(Units(strings.reverse("a😀b")))
            println(Units(strings.reverse("€á😀")))
            println(Units("héllo".toUpperCase()))
            println(Units("GRÜẞEN".toLowerCase()))
            println(Units("straße".toUpperCase()))
            println(Units("İ".toLowerCase()))
            println(Units("ǰ".toUpperCase()))
            println(Units("ΑΣ".toUpperCase()))
            println(Units("Ο ΔΥΣ".toLowerCase()))
        }
        """;

    private static final String CIC_SRC = """
        main() {
            println("straße".compareToIgnoreCase("STRASSE"))
            println("İ".compareToIgnoreCase("i"))
            println("Hello".compareToIgnoreCase("hello"))
            println("ǰ".compareToIgnoreCase("J̌"))
            println("Σ".compareToIgnoreCase("σ"))
            println("ẛ".compareToIgnoreCase("ẞ"))
            println("abc".compareToIgnoreCase("abcd"))
            println("".compareToIgnoreCase(""))
            println("ǅ".compareToIgnoreCase("ǆ"))
            println("ﬁ".compareToIgnoreCase("fi"))
        }
        """;

    @Test
    @DisplayName("linha 11: oráculo JVM medido (caixa/reverse Unicode + compareToIgnoreCase)")
    void jvmOracleMeasured(@TempDir Path tmp) throws Exception {
        assertEquals(ORACLE_JVM, run(tmp, BATTERY, Target.JVM).trim());
        assertEquals(CIC_ORACLE, run(tmp, CIC_SRC, Target.JVM).trim());
    }

    @Test
    @DisplayName("linha 11: JS == oráculo JVM (built-ins pinados + cic portado — D-STR-UNICODE)")
    void jsMatchesOracle(@TempDir Path tmp) throws Exception {
        assertEquals(ORACLE_JVM, run(tmp, BATTERY, Target.JS).trim());
        // kofStringCompareToIgnoreCase: mesmo resultado do algoritmo JDK medido.
        assertEquals(CIC_ORACLE, run(tmp, CIC_SRC, Target.JS).trim());
    }

    @Test
    @DisplayName("linha 11: native x86 = fold simples por code unit (escopo ratificado D-STR-UNICODE)")
    void nativeCharacterization(@TempDir Path tmp) throws Exception {
        assertEquals(NATIVE_SIMPLE_FOLD, run(tmp, BATTERY, Target.NATIVE).trim());
    }

    @Test
    @DisplayName("linha 11: compareToIgnoreCase — JS portado; native ainda recusa STR003 (NAT-STR01)")
    void cicGapStaysHonest(@TempDir Path tmp) throws Exception {
        for (Target t : new Target[]{Target.NATIVE}) {
            Path file = tmp.resolve("C-" + System.nanoTime() + ".kf");
            Files.writeString(file, CIC_SRC);
            CompilationResult r = driver.compile(file, tmp.resolve("o-" + t), t);
            assertFalse(r.success(), t + ": STR003 esperado");
            assertTrue(r.diagnostics().getDiagnostics().toString().contains("STR003"), t.name());
        }
    }

    private String run(Path tmp, String source, Target t) throws Exception {
        Path file = tmp.resolve("M-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path out = tmp.resolve("o-" + t + "-" + System.nanoTime());
        var r = driver.compile(file, out, t);
        assertTrue(r.success(), t + " compile: " + r.diagnostics().getDiagnostics());
        if (t == Target.JVM) {
            Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                    "-cp", out.toString(), "Default.Main").redirectErrorStream(true).start();
            String o = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(0, p.waitFor(), o);
            return o.replace("\r\n", "\n");
        }
        if (t == Target.NATIVE) {
            Process p = new ProcessBuilder(out.resolve("Default/Main").toString())
                    .redirectErrorStream(true).start();
            String o = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(0, p.waitFor(), o);
            return o.replace("\r\n", "\n");
        }
        try (java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
             java.io.ByteArrayOutputStream err = new java.io.ByteArrayOutputStream()) {
            Path entry;
            try (var w = Files.walk(out)) {
                entry = w.filter(p -> p.getFileName().toString().equals("Default.mjs"))
                        .findFirst().orElseThrow();
            }
            int ec = dev.kof.runtime.KofJsRunner.run(entry, buf,
                    java.io.InputStream.nullInputStream(), err);
            assertEquals(0, ec, buf + " " + err);
            return buf.toString(java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }
}
