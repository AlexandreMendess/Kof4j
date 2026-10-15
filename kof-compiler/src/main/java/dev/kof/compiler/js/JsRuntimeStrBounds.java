package dev.kof.compiler.js;

/** §730: `String.substring`/`charAt` FORA de faixa no backend JS.
 *
 *  <p>O JS delegava ao `String.prototype` do HOST, que por definição CLAMPA
 *  (`"abc".substring(9)` → `""`) e TROCA os argumentos de `substring`
 *  (`"abc".substring(2, 1)` → `"b"`), além de `charCodeAt` fora de faixa
 *  devolver `NaN` (→ string vazia). O JVM/Script LANÇAM a mensagem nomeada
 *  (`Range [..) out of bounds for length N` / `Index i out of bounds for
 *  length N`) e um `try`/`catch (String e)` do Kof a recupera — esse é o
 *  contrato (a variante SILENCIOSA é a pior classe, regra 5).
 *
 *  <p>Estes helpers fazem o range check e LANÇAM a MESMA string do JVM. O
 *  IR do JS não tem nó de EXPRESSÃO condicional, então a guarda vive num
 *  helper e num bloco PRÓPRIO (seeded só quando a face é emitida) — a mesma
 *  forma de {@link JsRuntimeDivZero} e {@link JsRuntimeCastFail}.
 */
public final class JsRuntimeStrBounds {

    private JsRuntimeStrBounds() {
    }

    static String STR_BOUNDS_RUNTIME = """
            // §730: substring/charAt fora de faixa — o host CLAMPA e troca os
            // args do substring; o JVM/Script lançam a mensagem nomeada, que o
            // `catch (String e)` do Kof recupera. Mesma string do JVM.
            export function kofStrCharAt(s, i) {
                if (i < 0 || i >= s.length) throw "Index " + i + " out of bounds for length " + s.length;
                return s.charCodeAt(i);
            }
            export function kofStrSubstring(s, start, end) {
                if (end === undefined) end = s.length;
                if (start < 0 || start > end || end > s.length) throw "Range [" + start + ", " + end + ") out of bounds for length " + s.length;
                return s.substring(start, end);
            }

            """;
}
