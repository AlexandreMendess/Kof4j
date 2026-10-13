package dev.kof.compiler.js;

/** §693 face JS: a divisão INTEIRA por um zero de RUNTIME.
 *
 *  <p>No JVM e no Script `10 / zero()` lança `ArithmeticException: / by zero` e
 *  um `try`/`catch` do Kof RECUPERA — esse é o contrato (a própria tabela de
 *  paridade do ledger já registrava o cenário). No JS o `/` de `Int` era a
 *  divisão IEEE do host: `10 / 0` dava `Infinity`, e guardar isso num slot
 *  `Int` (o `| 0` do `intWrap`) produzia um `0` SILENCIOSO — valor errado, R6,
 *  não falha.
 *
 *  <p>O IR do JS não tem nó de EXPRESSÃO condicional (`JsIf` é statement)
 *  enquanto o `binaryExpr` do emissor devolve uma expressão, então a guarda
 *  não cabe como ternário inline: ela é este helper, e vive num bloco PRÓPRIO
 *  (seeded só quando a face é emitida) — a MESMA forma que a face estrita do
 *  cast precisou (`JsRuntimeCastFail`; o helper no bloco CORE quebrava o
 *  slicing do runner, medido no ciclo 50).
 *
 *  <p>A mensagem é a do JVM (`/ by zero`), e o helper LANÇA o mesmo valor que
 *  o `throw "msg"` do Kof produz (uma string), então o `catch (String e)` do
 *  usuário a recebe — paridade com o JVM/Script, que é o oráculo.
 */
public final class JsRuntimeDivZero {

    private JsRuntimeDivZero() {
    }

    static String DIVZERO_RUNTIME = """
            // §693: divisor de RUNTIME zero na divisão INTEIRA — lança com a
            // mensagem do JVM em vez de devolver o `Infinity` do host (que o
            // `| 0` do intWrap transformava num `0` silencioso).
            export function kofDivZero(v) {
                if (v === 0) throw "/ by zero";
                return v;
            }

            """;
}
