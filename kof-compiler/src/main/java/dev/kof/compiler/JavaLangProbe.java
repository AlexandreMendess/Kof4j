package dev.kof.compiler;

/**
 * §268 (D-RULE6-BATCH opção A): probe CACHEADO de `java.lang` — um nome simples
 * que existe no JDK do compilador resolve para `java.lang.&lt;nome&gt;` sem
 * import (`Thread`, `Runnable`, `Object`…). Os wrappers/aliases de superfície de
 * Kof (`Boolean`, `Long`, `Double`, `String`, `Object`…) NUNCA passam pelo
 * probe: quem decide primeiro é o `Type.of`/builtins (senão `save(): Boolean`
 * virava `java.lang.Boolean` e o call-site esperava `boolean` — regressão
 * medida em {@code MultipleInterfaceReturnTypeE2ETest}).
 *
 * <p>Regra 7: responsabilidade extraída de {@code CompilerTypes} para manter os
 * arquivos quentes longe do limite do gate (≤500/600).
 */
final class JavaLangProbe {

    private JavaLangProbe() {}

    /** `java.lang.&lt;nome&gt;` quando o nome é classe real de `java.lang`;
     *  null para builtin/wrapper, nome composto ou miss. O cache guarda "" no
     *  miss (nunca re-proba). Sem inicializar a classe (`initialize=false`). */
    static String qualifiedOrNull(String simpleName) {
        if (simpleName == null || simpleName.isEmpty()) return null;
        if (simpleName.contains(".") || simpleName.contains("<") || simpleName.contains("?")
                || simpleName.contains("[") || simpleName.contains("(") || simpleName.contains("/")) {
            return null;
        }
        if (WRAPPERS.contains(simpleName)) return null;
        String cached = CACHE.computeIfAbsent(simpleName, name -> {
            try {
                Class.forName("java.lang." + name, false, JavaLangProbe.class.getClassLoader());
                return "java.lang." + name;
            } catch (Throwable miss) {
                return "";
            }
        });
        return cached.isEmpty() ? null : cached;
    }

    private static final java.util.Map<String, String> CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static final java.util.Set<String> WRAPPERS = java.util.Set.of(
            "Boolean", "Byte", "Short", "Integer", "Long", "Float", "Double",
            "Character", "String", "Object", "Number", "Void");
}
