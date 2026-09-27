package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §400: uma FUNÇÃO nomeada usada como VALOR numa posição de argumento
 * (`job("e", probe)` com `Bool probe()`) era SEM011 genérico que não
 * ajudava ("Undefined variable or type: 'probe'").
 *
 * Voto (A) D-CLOSEALL-BATCH: mantém a rejeição, diagnóstico nomeia a regra
 * real e aponta o idiom lambda (`() -> probe()`) que já existe.
 */
class NamedFunctionValueDiagnosticE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compile(Path dir, String source) throws Exception {
        Path file = dir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        return driver.compile(file, dir.resolve("out-" + System.nanoTime()), Target.JVM);
    }

    private boolean hasSem011(CompilationResult r, String needle) {
        return r.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> "SEM011".equals(d.code())
                        && (needle == null || d.message().contains(needle)));
    }

    /** repro mínimo: função nomeada como valor em argumento */
    @Test
    void namedFunctionAsValueGetsLambdaHint(@TempDir Path tmp) throws Exception {
        var r = compile(tmp, """
            Bool probe() = true
            main() { print(probe) }
            """);
        assertTrue(hasSem011(r, "probe is a top-level function"),
                "esperava o novo SEM011 do §400; obtive: " + r.diagnostics());
        assertTrue(hasSem011(r, "() -> probe()"),
                "o diagnóstico deve apontar o idiom lambda");
    }

    /** forma com chamada aninhada em argumento nomeado (job("e", probe)) */
    @Test
    void namedFunctionAsNestedValue(@TempDir Path tmp) throws Exception {
        var r = compile(tmp, """
            Bool probe() = true
            main() { println(probe) }
            """);
        assertTrue(hasSem011(r, "() -> probe()"),
                "esperava o novo SEM011 do §400; obtive: " + r.diagnostics());
    }

    /** o idiom sugerido (lambda) continua aceitável — byte-paridade preservada */
    @Test
    void lambdaValueStillAccepted(@TempDir Path tmp) throws Exception {
        var r = compile(tmp, """
            Bool probe() = true
            main() { println(listOf(1,2,3).filter((n: Int) -> probe())) }
            """);
        assertFalse(hasSem011(r, null),
                "o idiom lambda não pode gerar SEM011: " + r.diagnostics());
        assertTrue(r.success(), "o lambda como valor tem que compilar");
    }

    /** regressão: identificador realmente indefinido mantém o diagnóstico antigo */
    @Test
    void plainUndefinedVarKeepsOldMessage(@TempDir Path tmp) throws Exception {
        var r = compile(tmp, """
            main() { print(zz_undeclared) }
            """);
        assertTrue(hasSem011(r, "Undefined variable or type: 'zz_undeclared'"),
                "SEM011 antigo para símbolo sem origem: " + r.diagnostics());
    }
}
