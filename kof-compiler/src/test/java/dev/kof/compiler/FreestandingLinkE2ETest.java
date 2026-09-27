package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.kof.compiler.nat.NativeProfile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * B-1 (PLAN-BAREMETAL-BOOT) — perfil de link {@code freestanding} no x86_64:
 * sem {@code -dynamic-linker}/-lc, o ELF não tem {@code PT_INTERP} nem
 * {@code DT_NEEDED} e ainda assim imprime o hello (a costura {@code kof_plat_*}
 * do B-0 cobre SO). O controle com o perfil {@code host} (dinâmico) prova que o
 * detector enxerga INTERP/NEEDED — nada de falso-verde. Capacidade libc
 * (concurrency) em freestanding é RECUSADA com diagnóstico NATIVE003.
 */
class FreestandingLinkE2ETest {

    private static final String PLAIN = """
            main() {
                val x = 3
                println("plain " + x)
            }
            """;

    private static final String CONCURRENT = """
            Int compute() { return 42 }
            main() {
                val r = spawn compute()
                println(await r)
            }
            """;

    private static final String MULTIFN = """
            Int twice(Int x) { return x * 2 }
            Int thrice(Int x) { return x * 3 }
            main() {
                println(twice(21))
                println(thrice(14))
            }
            """;

    private static final String FLOAT = """
            main() {
                println(1.5f)
            }
            """;

    private static boolean hasTool(String tool) {
        try {
            Process p = new ProcessBuilder(tool, "--version").start();
            return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private Path build(Path tempDir, String program, NativeProfile profile, boolean expectSuccess)
            throws IOException {
        CompilerDriver driver = new CompilerDriver();
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, program);
        Path outDir = tempDir.resolve("out-" + profile);
        CompilationResult result = driver.compile(source, outDir, Target.NATIVE, profile);
        assertEquals(expectSuccess, result.success(),
                "compile " + profile + " inesperado: " + result.diagnostics().getDiagnostics());
        return outDir.resolve("Default/Main");
    }

    private String readelf(String flag, Path bin) throws IOException, InterruptedException {
        Process p = new ProcessBuilder("readelf", flag, bin.toString())
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(30, TimeUnit.SECONDS), "readelf nao terminou");
        return out;
    }

    private String runBinary(Path bin) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "saida: " + out);
        return out;
    }

    /** Golden MEDIDO no oracle JVM do MESMO programa (regra: golden real). */
    private String jvmOracle(Path tempDir, String program) throws Exception {
        CompilerDriver driver = new CompilerDriver();
        Path source = tempDir.resolve("Oracle.kf");
        Files.writeString(source, program);
        Path outDir = tempDir.resolve("out-jvm");
        CompilationResult r = driver.compile(source, outDir, Target.JVM);
        assertTrue(r.success(), "jvm compile: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main", "run")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "saida jvm: " + out);
        return out;
    }

    @Test
    void helloFreestandingHasNoInterpOrNeededAndRuns(@TempDir Path dir) throws Exception {
        assumeTrue(hasTool("as") && hasTool("ld") && hasTool("readelf"),
                "x86 toolchain/readelf ausentes");
        Path bin = build(dir, PLAIN, NativeProfile.FREESTANDING, true);
        String dyn = readelf("--dynamic", bin);
        String ph = readelf("--program-headers", bin);
        assertFalse(ph.contains("INTERP"),
                "B-1: freestanding nao pode ter PT_INTERP:\n" + ph);
        assertFalse(dyn.contains("NEEDED"),
                "B-1: freestanding nao pode ter DT_NEEDED:\n" + dyn);
        assertEquals(jvmOracle(dir, PLAIN), runBinary(bin), "saida freestanding != oracle JVM");
    }

    @Test
    void freestandingHelloCarriesNoLibcFormatRefs(@TempDir Path dir) throws Exception {
        // B-1b face (i): o panic genérico nao pode mais arrastar dtoa
        // (snprintf/strtod) para o objeto freestanding — hello não usa float.
        assumeTrue(hasTool("as") && hasTool("ld") && hasTool("readelf"),
                "x86 toolchain/readelf ausentes");
        Path bin = build(dir, PLAIN, NativeProfile.FREESTANDING, true);
        String dyn = readelf("--dyn-syms", bin);
        assertFalse(dyn.contains("snprintf"),
                "freestanding hello: snprintf vivo no dynsym (dtoa arrastado):\n" + dyn);
        assertFalse(dyn.contains("strtod"),
                "freestanding hello: strtod vivo no dynsym (dtoa arrastado):\n" + dyn);
        assertEquals(jvmOracle(dir, PLAIN), runBinary(bin), "saida freestanding != oracle JVM");
    }

    @Test
    void hostProfileStaysDynamic(@TempDir Path dir) throws Exception {
        assumeTrue(hasTool("as") && hasTool("ld") && hasTool("readelf"),
                "x86 toolchain/readelf ausentes");
        Path bin = build(dir, PLAIN, NativeProfile.HOST, true);
        String dyn = readelf("--dynamic", bin);
        String ph = readelf("--program-headers", bin);
        assertTrue(ph.contains("INTERP"),
                "controle: o perfil host DEVE ter PT_INTERP (senao o detector nao enxerga):\n" + ph);
        assertTrue(dyn.contains("NEEDED"),
                "controle: o perfil host DEVE ter DT_NEEDED:\n" + dyn);
    }

    @Test
    void freestandingMultiFunctionProgramLinksAndRuns(@TempDir Path dir) throws Exception {
        // B-1b (medido 23/09): o sectionize por funcao movia tambem as funcoes
        // do PROGRAMA; o `as` rejeitava com "can't resolve .text.<fn> -
        // <nextFn>" porque o DWARF .debug do x86 expressa o range como
        // "simbolo_fim - proximo_simbolo" e a diferenca passava a cruzar
        // secoes. Com 1 funcao (PLAIN) passava; com 2+ quebrava. O fix filtra
        // por "kof_" (so o runtime poda). Regressao Q1: 2 funcoes de programa.
        assumeTrue(hasTool("as") && hasTool("ld"), "x86 toolchain ausente");
        Path bin = build(dir, MULTIFN, NativeProfile.FREESTANDING, true);
        assertEquals(jvmOracle(dir, MULTIFN), runBinary(bin),
                "multi-funcao freestanding != oracle JVM");
    }

    @Test
    void freestandingRefusesLibcCapabilityWithDiagnostic(@TempDir Path dir) throws Exception {
        assumeTrue(hasTool("as") && hasTool("ld") && hasTool("readelf"),
                "x86 toolchain/readelf ausentes");
        CompilerDriver driver = new CompilerDriver();
        Path source = dir.resolve("Main.kf");
        Files.writeString(source, CONCURRENT);
        Path outDir = dir.resolve("out-bad");
        CompilationResult result = driver.compile(source, outDir, Target.NATIVE, NativeProfile.FREESTANDING);
        assertFalse(result.success(), "freestanding+concurrency deve ser recusado");
        String diags = result.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("NATIVE003") && diags.contains("freestanding"),
                "recusa precisa ser honesta e diagnostica (NATIVE003): " + diags);
    }

    @Test
    void freestandingFloatPrintMatchesJvmOracle(@TempDir Path dir) throws Exception {
        // B-1c (23/09): o Float de 32 bits tambem passou ao Schubfach libc-free
        // (FloatToDecimal, H=9) — a antiga recusa NATIVE003 de float-print
        // deixou de existir e `1.5f` linka e imprime como o oraculo JVM.
        assumeTrue(hasTool("as") && hasTool("ld"), "x86 toolchain ausente");
        Path bin = build(dir, FLOAT, NativeProfile.FREESTANDING, true);
        assertEquals(jvmOracle(dir, FLOAT), runBinary(bin), "Float freestanding != oracle JVM");
    }

    private static final String GROWS = """
            main() {
                var l = listOf(0)
                var i = 0
                while (i < 200000) {
                    l.add(i)
                    i = i + 1
                }
                println(l.size)
            }
            """;

    /** Valor (hex) de um simbolo no `readelf -s`, ou null se ausente. */
    private Long symValue(String syms, String name) {
        for (String line : syms.split("\n")) {
            String[] tok = line.strip().split("\\s+");
            if (tok.length >= 8 && tok[tok.length - 1].equals(name)) {
                try {
                    return Long.parseLong(tok[1], 16);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }

    @Test
    void freestandingLinkerScriptSizesHeapAndStackFromEnv(@TempDir Path dir) throws Exception {
        // B-1 (23/09): o linker script do freestanding reserva arena de heap +
        // pilha e as dimensiona por KOF_HEAP_SIZE/KOF_STACK_SIZE (ou props).
        // `_end` fecha a .bss REAL antes da arena (topo da varredura de raizes
        // estaticas do GC). Prova: os 4 simbolos existem e os deltas == config.
        assumeTrue(hasTool("as") && hasTool("ld") && hasTool("readelf"),
                "x86 toolchain/readelf ausentes");
        System.setProperty("kof.heap.size", "65536");
        System.setProperty("kof.stack.size", "131072");
        Path bin;
        try {
            bin = build(dir, PLAIN, NativeProfile.FREESTANDING, true);
        } finally {
            System.clearProperty("kof.heap.size");
            System.clearProperty("kof.stack.size");
        }
        String syms = readelf("-s", bin);
        Long heapStart = symValue(syms, "__kof_heap_start");
        Long heapEnd = symValue(syms, "__kof_heap_end");
        Long stackBottom = symValue(syms, "__kof_stack_bottom");
        Long stackTop = symValue(syms, "__kof_stack_top");
        Long end = symValue(syms, "_end");
        assertTrue(heapStart != null && heapEnd != null && stackBottom != null
                && stackTop != null && end != null, "simbolos do script ausentes:\n" + syms);
        assertEquals(65536L, heapEnd - heapStart, "arena de heap != KOF_HEAP_SIZE");
        assertEquals(131072L, stackTop - stackBottom, "pilha != KOF_STACK_SIZE");
        assertEquals(heapStart.longValue(), end.longValue(),
                "_end deve fechar a .bss REAL antes da arena");
        assertEquals(jvmOracle(dir, PLAIN), runBinary(bin), "saida freestanding != oraculo JVM");
    }

    @Test
    void freestandingTinyHeapFailsHonestly(@TempDir Path dir) throws Exception {
        // B-1: a alocacao freestanding vem da arena do script (nao de mmap).
        // Com arena minuscula, um programa que RETEM alocacoes a esgota ->
        // kof_panic("out of memory") + exit != 0 (R6: nunca ponteiro invalido
        // silencioso). Prova falsificavel de que o heap e o que o script deu.
        assumeTrue(hasTool("as") && hasTool("ld"), "x86 toolchain ausente");
        System.setProperty("kof.heap.size", "4096");
        Path bin;
        try {
            bin = build(dir, GROWS, NativeProfile.FREESTANDING, true);
        } finally {
            System.clearProperty("kof.heap.size");
        }
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertTrue(ec != 0, "arena de 4 KiB deveria esgotar (exit=" + ec + ", saida=" + out + ")");
        assertTrue(out.contains("out of memory"), "panic honesto esperado, veio: " + out);
    }
}
