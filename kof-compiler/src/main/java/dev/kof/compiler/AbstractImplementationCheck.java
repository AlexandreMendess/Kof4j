package dev.kof.compiler;

/**
 * Satélite do REFACTOR-500 (check_500, MemberResolver 601→<600): o cheque
 * SG-015 (#256) de implementação de abstratos (os comentários viajam).
 */
final class AbstractImplementationCheck {

    private AbstractImplementationCheck() {}

    /**
     * SG-015 (#256): classe concreta que estende classe abstrata deve implementar
     * todos os métodos abstratos herdados da cadeia de superclasses.
     */
    static void check(SemanticAnalyzer sa, ClassDeclarationNode cls) {
        if (sa.diagnostics() == null || cls.modifiers().contains("abstract")) return;
        String curSuper = cls.superClass();
        java.util.Set<String> checkedMethods = new java.util.HashSet<>();
        // #821: a cadeia de extends pode ser CIRCULAR (A extends A, ciclos de
        // 2/3) — sem o visited o while acima gira para sempre (DoS, sem
        // diagnóstico). O próprio ClassShapeChecks emite o SEM106; aqui a
        // caminhada apenas TERMINA no primeiro revisitado.
        java.util.Set<String> visitedSupers = new java.util.HashSet<>();
        while (curSuper != null && !curSuper.isEmpty() && !"Object".equals(curSuper)) {
            String simpleSuper = curSuper.contains("/") ? curSuper.substring(curSuper.lastIndexOf("/") + 1) : curSuper;
            if (simpleSuper.contains("<")) simpleSuper = simpleSuper.substring(0, simpleSuper.indexOf("<"));
            if (!visitedSupers.add(simpleSuper)) break;
            SymbolTable.ClassSymbol superCs = sa.allClasses().get(simpleSuper);
            if (superCs == null) break;
            for (java.util.Map.Entry<String, SymbolTable.Symbol> e : superCs.members().localSymbols().entrySet()) {
                if (!(e.getValue() instanceof SymbolTable.MethodSymbol am)) continue;
                if ((am.accessFlags() & AccessFlags.ABSTRACT) == 0) continue;
                // §242: a chave é a ASSINATURA (nome + tipos de parâmetro), não
                // a aridade — duas sobrecargas de mesma aridade mas tipos
                // diferentes são abstratos DISTINTOS. Sem isto, o dedup
                // colapsava-as e um `Int run(Int)` podia "satisfazer"
                // `abstract Int run(String)` (AbstractMethodError em runtime).
                String methodKey = am.name() + TopLevelOverload.sigTag(am.parameterTypes());
                if (!checkedMethods.add(methodKey)) continue;
                SymbolTable.Symbol local = MemberResolver.resolveInHierarchy(sa, cls.name(), am.name());
                boolean implemented = false;
                if (local instanceof SymbolTable.MethodSymbol lm) {
                    implemented = MemberResolver.satisfiesAbstract(lm, am);
                } else if (local instanceof SymbolTable.MethodSet set) {
                    for (SymbolTable.MethodSymbol lm : set.methods()) {
                        if (MemberResolver.satisfiesAbstract(lm, am)) {
                            implemented = true;
                            break;
                        }
                    }
                }
                if (!implemented) {
                    sa.diagnostics().error(cls,
                            "class '" + cls.name() + "' does not implement abstract method '"
                                    + am.name() + "()' from superclass '" + superCs.name() + "'",
                            "SEM043");
                }
            }
            curSuper = superCs.superClass();
        }
    }

    /**
     * §242: um método concreto satisfaz um abstrato quando NÃO é abstrato e a
     * assinatura casa — mesma aridade E mesmos tipos de parâmetro (o que a JVM
     * exige para o override). Antes o check comparava só a ARIDADE: um
     * `Int run(Int)` "satisfazia" `abstract Int run(String)` e o
     * `AbstractMethodError` voltava em runtime.
     */
}
