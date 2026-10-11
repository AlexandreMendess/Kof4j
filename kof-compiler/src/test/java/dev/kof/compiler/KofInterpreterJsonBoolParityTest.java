package dev.kof.compiler;

import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Paridade interpretador × JVM para o encode JSON de campos {@code Bool} de
 * record (known-bugs §658a). Vive fora de {@code KofInterpreterParityTest}
 * (Fase 3 da arquitetura de testes, {@code D-TEST-ARCHITECTURE-GO}): a classe
 * original estava no teto do ratchet de higiene (489/500 linhas) e o caso novo
 * a ultrapassaria — a extração por responsabilidade é o corte prescrito.
 */
class KofInterpreterJsonBoolParityTest extends KofInterpreterParitySupport {

    // §658a — campo Bool de record no encode: o interpretador guardava Integer
    // 0/1 (§108) e emitia {"ok":1} onde o JVM emite {"ok":true}; o encode agora
    // normaliza 1/0 → true/false pelo tipo declarado do campo.
    @Test
    void jsonEncodeRecordBoolField() throws IOException {
        parity("json-encode-bool-field", """
                import kof.json
                record Flag(Bool ok)
                record Two(Bool f, Bool g)
                main() {
                    println(json.encode(Flag(true)))
                    println(json.encode(Flag(false)))
                    println(json.encode(Two(true, false)))
                    println(json.encode(listOf(Flag(true), Flag(false))))
                }
                """);
    }
}
