package dev.kof.compiler;

import java.util.List;

/**
 * X5.1 (`D-X5-SURFACE`) — a declaração `sealed` fecha o conjunto de subtipos
 * de um `sealed class`/`record`/`interface` em tempo de compilação. O conjunto
 * é o das declarações da MESMA unidade de compilação (arquivo) do tipo
 * selado: um subtipo direto declarado em outra unidade é SEM080 (o compilador
 * não o conhece — nunca um conjunto aberto silencioso, R6).
 *
 * Compile-time apenas: o modificador é apagado na emissão (`computeAccess`
 * ignora modificadores desconhecidos), então os bytes JVM/Native/JS seguem
 * idênticos. A exaustividade de `switch` sobre o sujeito selado é X5.2.
 */
final class SealedTypeChecks {

    private SealedTypeChecks() {}

    /** Checa o `extends` + `implements` de um class/record contra tipos `sealed`. */
    static void checkSubtype(SemanticAnalyzer sa, SourcePosition pos, String subtypeName,
                             String superClass, List<String> interfaces) {
        checkOne(sa, pos, subtypeName, superClass);
        if (interfaces != null) {
            for (String iface : interfaces) {
                checkOne(sa, pos, subtypeName, iface);
            }
        }
    }

    private static void checkOne(SemanticAnalyzer sa, SourcePosition pos, String subtypeName,
                                 String supertype) {
        if (sa.diagnostics() == null) return;
        String erased = ClassShapeChecks.eraseGenerics(supertype);
        if (erased == null) return;
        String simple = ClassShapeChecks.simpleName(erased);
        if (!sa.isSealedType(simple)) return;
        String sealedUnit = sa.sealedTypeUnit(simple);
        String subUnit = pos != null ? pos.file() : "";
        // Unidade vazia (declaração sintética/injetada) não é comparável —
        // não inventa erro (nem silencia um caso observável).
        if (sealedUnit == null || sealedUnit.isEmpty() || subUnit == null || subUnit.isEmpty()) {
            return;
        }
        if (!sealedUnit.equals(subUnit)) {
            sa.diagnostics().error(subUnit, pos.line(), pos.column(), 0,
                    "subtype '" + subtypeName + "' of sealed type '" + simple
                            + "' must be declared in the same compilation unit as '" + simple
                            + "' — a sealed type's subtype set is closed, and subtypes declared"
                            + " elsewhere are not known to the compiler",
                    "SEM080");
        }
    }
}
