package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins de ARESTAS da família #382/#386 nos cross-archs (o port em si landou
 * no tip pela §359/`6818ca8c`: `kof_list_add_all` riscv em
 * {@code NativeRiscvAsmLookups0} + `kof_list_cmp` redirecionado ao
 * `String_compareTo` de {@code NativeRiscvAsmRtB36}; aarch64 herda ambos via
 * tradutor). O golden da fatia (CollectionMethodsStdlibE2ETest) exerce
 * addAll só em dst vazio e sort só de strings distintas — as arestas abaixo
 * NÃO estavam travadas em lugar nenhum:
 *
 * <ul>
 *   <li>{@code kof_list_add_all}: dst JÁ populado e CRESCENTE (true +
 *       tamanho somado) e addAll-de-vazio em dst populado (false, invariante
 *       — a flag `mudou` é tamanho final != inicial, medidos pelos SLOTS
 *       CRUS, §253).</li>
 *   <li>{@code String_compareTo} consumido via `kof_list_cmp` (caso 1 do
 *       tag): PREFIXO ("app" &lt; "apple" por contagem de UNITS — byte-offset
 *       mentiria −2 vs o sinal correto) e IGUAL (duas "apple" → cmp 0, o
 *       sort de seleção mantém ambas) — o contrato negativo/zero/positivo
 *       que o chamador consome por sinal.</li>
 * </ul>
 *
 * Nota de projeto medida 20/09 (por que NÃO expor um free-function
 * `kof_string_compare_to` alias de label no riscv): sectionizeTextFunctions
 * dá a cada par `.globl`+label a própria `.section .text.<nome>` e o
 * `--gc-sections` do ld DELETA o corpo quando só o nome do alias é
 * referenciado — o alias vira seção VAZIA e o jump cai no lixo (medido:
 * String.sort() → "out of memory"). A redirected call-site da §359 é a face
 * correta; este arquivo é a prova que faltava nela.
 *
 * Golden = MEDICAO no oraculo JVM + confirmacao no x86_64 nativo; riscv64/
 * aarch64 consomem PROGRAM/GOLDEN e rodam sob qemu (parity rule 5).
 */
class CrossRuntimePortsE2ETest {

    static final String PROGRAM = """
            main() {
                val d: List<String> = listOf("pear", "apple")
                val more: List<String> = listOf("fig", "apple", "app")
                println(d.addAll(more))
                println(d.size)
                val e: List<String> = listOf("x")
                val nothing: List<String> = listOf()
                println(e.addAll(nothing))
                println(e.size)
                d.sort()
                println(d.get(0))
                println(d.get(1))
                println(d.get(2))
                println(d.get(3))
                println(d.get(4))
                val dup: List<Int> = listOf(2, 2, 1)
                dup.sort()
                println(dup.get(0))
                println(dup.get(2))
            }
            """;

    // Golden MEDIDO no oraculo JVM + igual no x86_64 nativo, 20/09.
    static final String GOLDEN =
            "true\n5\nfalse\n1\napp\napple\napple\nfig\npear\n1\n2";

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void portsEdgesRunOnJvm(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("P.kf");
        Files.writeString(source, PROGRAM);
        Path outDir = tempDir.resolve("outJVM");
        CompilationResult r = driver.compile(source, outDir, Target.JVM);
        assertTrue(r.success(), "must compile: " + r.diagnostics().getDiagnostics());
        String javaCmd = TestJdk.javaBin();
        Process p = new ProcessBuilder(javaCmd, "-cp", outDir.toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes()).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "JVM run must exit 0, got:\n" + out);
        assertEquals(GOLDEN, out, "JVM oracle golden (medido)");
    }

    @Test
    void portsEdgesRunOnNativeX86(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("N.kf");
        Files.writeString(source, PROGRAM);
        Path outDir = tempDir.resolve("outN");
        CompilationResult r = driver.compile(source, outDir, Target.NATIVE);
        assertTrue(r.success(), "native x86_64 compile: " + r.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        assertTrue(Files.exists(bin), "binary should exist");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes()).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "native x86_64 run must exit 0, got:\n" + out);
        assertEquals(GOLDEN, out, "native x86_64 output must match the JVM oracle (rule 5)");
    }
}
