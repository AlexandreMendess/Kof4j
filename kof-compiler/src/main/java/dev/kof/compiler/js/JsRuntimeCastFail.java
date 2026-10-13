package dev.kof.compiler.js;

/** §678 / D-SEM-REJECTS-1210: o `as T` NÃO-nulo que falha ESTOURA — o soft
 *  era a divergência §678 (o JVM já estourava via CHECKCAST). O helper vive
 *  num bloco PRÓPRIO (seeded só quando a face estrita é emitida): o mesmo
 *  helper no bloco CORE quebrava o slicing do runner (67 falhas, ciclo 50 —
 *  o runner executa o runtime inteiro do core e o módulo UI morria cedo). */
public final class JsRuntimeCastFail {
    private JsRuntimeCastFail() {
    }

    static  String CASTFAIL_RUNTIME = """
            // §678 / D-SEM-REJECTS-1210: o `as T` NÃO-nulo que falha ESTOURA
            // (nunca retorna; o condicional inline da face estrita chama).
            export function kofCastFail(name) {
                throw new Error('type cast failed: expected ' + name);
            }

            """;

}
