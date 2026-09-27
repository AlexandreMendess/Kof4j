package dev.kof.compiler;

/**
 * §268 (D-RULE6-BATCH opção A): resolução de `extends`/`implements` por NOME
 * SIMPLES — import explícito &gt; throwable de `java.lang` (#313, mesmo mapa do
 * catch) &gt; probe cacheado de `java.lang` ({@link JavaLangProbe}) &gt; nome
 * CRU (o {@code DeclaredTypeChecker} emite SEM087; R6 — nunca um super raw
 * silencioso com `NoClassDefFoundError` no load). O módulo vence o probe
 * (registro/unit); `Object`/`Record` são os sentinelas de sempre.
 *
 * <p>Regra 7: responsabilidade extraída de {@code MemberResolver} para manter os
 * arquivos quentes longe do limite do gate (≤500/600).
 */
final class HeritageQualifier {

    private HeritageQualifier() {}

    static String qualify(CompilationUnitNode unit, SemanticAnalyzer sa, String declared) {
        if (declared == null) return null;
        String bare = declared;
        if (bare.contains("<")) {
            bare = bare.substring(0, bare.indexOf('<')).trim();
        }
        if (bare.isEmpty() || "Object".equals(bare) || "Record".equals(bare)) return bare;
        Type viaImports = MemberResolver.qualifyViaImports(unit, bare,
                sa != null ? sa.externalTypes() : null);
        if (viaImports instanceof Type.ClassType qt && !qt.packageName().isEmpty()) {
            return qt.packageName() + "." + qt.name();
        }
        if (CompilerTypes.JAVA_LANG_THROWABLES.contains(bare)) return "java.lang." + bare;
        if (CompilerTypes.unitDeclaresType(unit, bare)
                || (sa != null && sa.allClasses().containsKey(bare))) {
            return bare;
        }
        String javaLang = JavaLangProbe.qualifiedOrNull(bare);
        return javaLang != null ? javaLang : bare;
    }

    /** Face INTERFACE de {@link #qualify}: com genéricos o nome é preservado
     *  como hoje ({@code Comparator<Int>} — o erasure do emit já corta); sem
     *  genéricos vale a mesma resolução do super. */
    static String qualifyInterface(CompilationUnitNode unit, SemanticAnalyzer sa, String declared) {
        if (declared == null || declared.indexOf('<') >= 0) return declared;
        return qualify(unit, sa, declared);
    }
}
