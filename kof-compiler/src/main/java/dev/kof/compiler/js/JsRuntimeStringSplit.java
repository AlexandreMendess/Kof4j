package dev.kof.compiler.js;

/**
 * The String.split runtime slice (REFACTOR-500 split out of
 * {@link JsRuntimeCore} to keep it under the 500-line target). Concatenated
 * into the SAME "core" block by {@link JsRuntimeSlices}, so name resolution
 * is unchanged.
 */
final class JsRuntimeStringSplit {

    static  String SPLIT_RUNTIME = """
            // §111: split com a regra Java (não JS): remove vazios TRAILING,
            // exceto input "" → [""]. JS nativo preserva trailing ("a,"→["a",""]).
            // §655: a sobrecarga 2-arg (String.split(String, Int)) segue o
            // contrato JDK — limit>0 = no máximo `limit` elementos com o RESTO
            // não dividido no último (o JS nativo TRUNCA — não é o contrato),
            // limit==0 = poda os vazios finais (o comportamento histórico),
            // limit<0 = mantém tudo. O JS nativo NÃO é usado para limit>0.
            export function kofSplit(s, sep, limit) {
                if (s === "") return [""];
                const parts = String(s).split(sep === undefined ? "" : sep);
                const lim = (limit === undefined) ? 0 : Number(limit);
                if (lim > 0) {
                    if (parts.length <= lim) return parts;
                    return parts.slice(0, lim - 1)
                        .concat(parts.slice(lim - 1).join(sep === undefined ? "" : sep));
                }
                if (lim < 0) return parts;
                let end = parts.length;
                while (end > 0 && parts[end - 1] === "") end--;
                return parts.slice(0, end);
            }
            """;

    private JsRuntimeStringSplit() {}
}
