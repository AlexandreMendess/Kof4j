package dev.kof.compiler;

import dev.kof.compiler.runtime.RuntimeDb1;
import dev.kof.compiler.runtime.RuntimeDb2;
import dev.kof.compiler.runtime.RuntimeDb3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §421 / S0 — scheme fora do contrato nativo ({@code sqlite:}/{@code mysql://})
 * deve ser recusado no {@code kof_db_connect} com diagnóstico NOMEADO (DB001) —
 * nunca um handle nulo silencioso que só explode tarde no {@code .Lorm_conn}
 * como {@code unknown db connection: } (R6 violado).
 *
 * <p>Prova em nível de codegen: determinística, não depende de
 * {@code libmariadb}/qemu do host (o E2E {@code KofDbE2ETest} cobre a
 * execução onde a lib existe). Falha no código antigo, que desviava para
 * {@code .Ldb_connect_bad} (handle nulo).
 */
class NativeDbSchemeRefusalAsmTest {

    private static String emitDb2() {
        StringBuilder sb = new StringBuilder();
        RuntimeDb2.emit(sb);
        return sb.toString();
    }

    @Test
    void dispatchOfUnsupportedSchemeGoesToNamedRefusalNotSilentNull() {
        String asm2 = emitDb2();
        assertTrue(asm2.contains("jne .Ldb_connect_unsupported"),
                "dispatch de scheme deve desviar para a recusa nomeada");
        assertFalse(asm2.contains(".Ldb_connect_bad"),
                "RuntimeDb2 nao deve mais cair no caminho silencioso (handle nulo)");
    }

    @Test
    void namedRefusalThrowsDb001KofString() {
        StringBuilder b1 = new StringBuilder();
        RuntimeDb1.emit(b1);
        StringBuilder b3 = new StringBuilder();
        RuntimeDb3.emit(b3);
        assertTrue(b1.toString().contains("DB001: unsupported db scheme"),
                "constante do diagnostico DB001 ausente no runtime");
        assertTrue(b3.toString().contains(".Ldb_connect_unsupported:"),
                "handler de recusa ausente");
        assertTrue(b3.toString().contains("call kof_throw_string"),
                "handler deve LANCAR (kof_throw_string), nao retornar handle nulo");
    }
}
