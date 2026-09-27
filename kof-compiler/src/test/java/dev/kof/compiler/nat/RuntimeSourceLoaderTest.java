package dev.kof.compiler.nat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §371 (issue #550): a ORDEM das fatias do runtime nativo deve ser derivada do
 * classpath, nao do CWD — do jar distribuido do CLI nao existe
 * {@code kof-compiler/src/main/java/...} relativo ao diretorio de trabalho, e
 * o loader antigo caia em "prune DESABILITADO" → runtime completo →
 * {@code usesDb} → {@code -lsqlite3} → COMP001 no sysroot cross (ate em
 * hello.kf). Prova:
 * <ul>
 *   <li>os dois fontes-ordem estao empacotados como recursos de build no
 *       classpath (o que o uber-jar do CLI herda via shade);</li>
 *   <li>o loader e classpath-first: le com caminho de arquivo INEXISTENTE —
 *       o cenario exato do jar shipped, que era RED antes do fix;</li>
 *   <li>o fallback dev (arquivo) continua funcionando;</li>
 *   <li>sem classpath nem arquivo → {@code IllegalStateException} nomeando as
 *       duas fontes (R6, nunca silencioso).</li>
 * </ul>
 */
class RuntimeSourceLoaderTest {

    @Test
    void runtimeOrderSourceIsPackagedAsResource() throws Exception {
        try (InputStream in = RuntimeSlices.class.getResourceAsStream(
                "/dev/kof/compiler/NativeRuntime.java")) {
            assertNotNull(in, "NativeRuntime.java deve estar no classpath (build resource do pom)");
            String src = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(src.contains("generateRuntimeAssembly()"),
                    "o recurso empacotado deve conter o corpo que deriva a ordem");
        }
    }

    @Test
    void riscvPieceOrderSourceIsPackagedAsResource() throws Exception {
        try (InputStream in = RiscvSlices.class.getResourceAsStream(
                "/dev/kof/compiler/nat/NativeRiscvAsm.java")) {
            assertNotNull(in, "NativeRiscvAsm.java deve estar no classpath (build resource do pom)");
            String src = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(src.contains("NativeRiscvAsmRt"),
                    "o recurso empacotado deve conter as concatencoes que derivam a ordem");
        }
    }

    @Test
    void loadUsesClasspathWhenCwdHasNoProjectTree() {
        // O cenário do jar shipped: caminho de arquivo que NAO existe no CWD.
        // Antes do fix isto lançava IllegalStateException (prune DESABILITADO).
        String src = RuntimeSourceLoader.read(RuntimeSlices.class,
                "/dev/kof/compiler/NativeRuntime.java",
                "no-such-dir/src/main/java/dev/kof/compiler/NativeRuntime.java");
        assertTrue(src.contains("generateRuntimeAssembly()"),
                "classpath-first: deve ler sem tocar em arquivo do CWD");
        String riscv = RuntimeSourceLoader.read(RiscvSlices.class,
                "/dev/kof/compiler/nat/NativeRiscvAsm.java",
                "no-such-dir/src/main/java/dev/kof/compiler/nat/NativeRiscvAsm.java");
        assertTrue(riscv.contains("NativeRiscvAsmRt"));
    }

    @Test
    void devFileFallbackStillWorks(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("standin.java");
        Files.writeString(file, "// dev-mode fallback content");
        String src = RuntimeSourceLoader.read(RuntimeSlices.class,
                "/dev/kof/compiler/no-such-resource-on-purpose.java",
                file.toString());
        assertEquals("// dev-mode fallback content", src,
                "sem recurso no classpath, o loader deve cair no arquivo (modo dev)");
    }

    @Test
    void missingEverywhereIsHonestFailure(@TempDir Path dir) {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> RuntimeSourceLoader.read(RuntimeSlices.class,
                        "/dev/kof/compiler/no-such-resource-on-purpose.java",
                        dir.resolve("also-missing.java").toString()));
        assertTrue(e.getMessage().contains("no-such-resource-on-purpose.java")
                        && e.getMessage().contains("also-missing.java"),
                "diagnostico deve nomear as duas fontes tentadas (R6): " + e.getMessage());
    }

    @Test
    void sliceDerivationRunsEndToEndFromClasspath() {
        // A cadeia completa (recurso → parse de ordem → reflexão das constantes)
        // é o que o prune usa; tem de funcionar sem depender de CWD.
        assertTrue(RuntimeSlices.slices().size() >= 100,
                "fatias x86 derivadas: " + RuntimeSlices.slices().size());
        assertTrue(RiscvSlices.pieces().size() >= 40,
                "peças riscv derivadas: " + RiscvSlices.pieces().size());
    }
}
