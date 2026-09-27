package dev.kof.compiler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * #639 face 2 (D-DECISION-BATCH-2709B): resolução de membros de um tipo
 * QUALIFICADO por caminho (`pkg.Type`). Extraído de {@code MemberResolver}
 * (gate ≤500: o arquivo estava a 6 linhas do crítico). A diferença para a
 * resolução por nome simples é o PRIMEIRO hop: aqui ele parte do
 * {@code ClassSymbol} já resolvido por FQN (índice aditivo), então duas
 * packages com o mesmo nome simples deixam de colidir no last-write.
 */
final class QualifiedTypeResolver {

    private QualifiedTypeResolver() {}

    /**
     * ClassSymbol de um ClassType pelo CAMINHO quando ele existe (preserva
     * colisão de nome simples); cai no nome simples (comportamento anterior)
     * quando o tipo não tem pacote. Aditivo.
     */
    static SymbolTable.ClassSymbol classOfType(SemanticAnalyzer sa, Type.ClassType ct) {
        if (ct == null) return null;
        String pkg = ct.packageName();
        if (pkg != null && !pkg.isEmpty()) {
            SymbolTable.ClassSymbol q = sa.findQualifiedClass(pkg + "." + ct.name());
            if (q != null) return q;
        }
        return sa.getClass(ct.name());
    }

    /** BFS pela hierarquia partindo de um ClassSymbol JÁ resolvido por FQN;
     *  o primeiro hop não passa por {@code getClass(nome simples)}. */
    static SymbolTable.Symbol resolveInHierarchy(SemanticAnalyzer sa,
            SymbolTable.ClassSymbol start, String memberName) {
        if (start == null) return null;
        SymbolTable.Symbol s0 = start.members().resolve(memberName);
        if (s0 != null) return s0;
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        visited.add(start.name());
        MemberResolver.enqueueAncestors(start, visited, queue);
        while (!queue.isEmpty()) {
            SymbolTable.ClassSymbol cs = sa.getClass(queue.poll());
            if (cs == null) continue;
            SymbolTable.Symbol s = cs.members().resolve(memberName);
            if (s != null) return s;
            MemberResolver.enqueueAncestors(cs, visited, queue);
        }
        return null;
    }

    /** Idem {@link MemberResolver#resolveMethodsInHierarchy}, partindo do
     *  ClassSymbol FQN (coleta os overloads de toda a hierarquia). */
    static SymbolTable.Symbol resolveMethodsInHierarchy(SemanticAnalyzer sa,
            SymbolTable.ClassSymbol start, String methodName) {
        if (start == null) return null;
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        LinkedHashMap<String, SymbolTable.MethodSymbol> bySig = new LinkedHashMap<>();
        visited.add(start.name());
        collectMethods(start.members().resolve(methodName), bySig);
        MemberResolver.enqueueAncestors(start, visited, queue);
        while (!queue.isEmpty()) {
            SymbolTable.ClassSymbol cs = sa.getClass(queue.poll());
            if (cs == null) continue;
            collectMethods(cs.members().resolve(methodName), bySig);
            MemberResolver.enqueueAncestors(cs, visited, queue);
        }
        if (bySig.isEmpty()) return null;
        if (bySig.size() == 1) return bySig.values().iterator().next();
        return new SymbolTable.MethodSet(new ArrayList<>(bySig.values()));
    }

    private static void collectMethods(SymbolTable.Symbol s,
            LinkedHashMap<String, SymbolTable.MethodSymbol> bySig) {
        List<SymbolTable.MethodSymbol> methods = new ArrayList<>();
        if (s instanceof SymbolTable.MethodSymbol m) methods.add(m);
        else if (s instanceof SymbolTable.MethodSet set) methods.addAll(set.methods());
        for (SymbolTable.MethodSymbol m : methods) bySig.putIfAbsent(m.parameterTypes().toString(), m);
    }
}
