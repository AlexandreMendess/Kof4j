package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * REFACTOR-500 (espelha {@link DeclaredTypeChecker}): verificadores de
 * CONTRATO de classe — implementacao de interfaces (SG-015/SEM043 + as
 * faces #322/#311: deferral em abstratas e obrigacao transitiva pelo pai
 * abstrato) e compatibilidade de retorno em override (#326/SEM059) —
 * extraidos do SemanticAnalyzer para manter o arquivo quente longe do
 * limite de 500 linhas (regra 7; o gate CI trava >= 600).
 *
 * <p>Puro read-only sobre o estado ja resolvido do analyzer; toda a
 * mutacao continua no dono. Roda DEPOIS do fixpoint de corpo de
 * `analyzeClass` porque o retorno pode ser refinado na analise de corpo.
 */
final class ImplementationChecker {

    private ImplementationChecker() {}

    static void checkInterfaceImplementation(SemanticAnalyzer sa,
            ClassDeclarationNode cls, SymbolTable classScope) {
        DiagnosticCollector diagnostics = sa.diagnostics();
        if (diagnostics == null) return;
        Map<String, SymbolTable.ClassSymbol> knownClasses = sa.allClasses();
        java.util.Set<String> interfaceNames = sa.interfaceNames();
        java.util.Set<String> abstractClasses = sa.abstractClasses();
        // #322: classe ABSTRATA que implementa uma interface pode DEIXAR
        // métodos da interface sem implementação (o contrato é o mesmo do
        // Java/JLS 8.4.8.1): quem deve implementá-los é a subclasse CONCRETA.
        // A obrigação de NÃO ficar em silêncio é transitiva: para classe
        // concreta, cobramos também as interfaces herdadas dos pais
        // ABSTRATOS (senão o deferral viraria um AbstractMethodError mudo —
        // mesma classe de bug da #311, vetada pela regra 6 do freeze/R6).
        boolean clsAbstract = cls.modifiers().contains("abstract");
        List<ClassDeclIfaces> obligations = new ArrayList<>();
        obligations.add(new ClassDeclIfaces(cls.interfaces(), null));
        if (!clsAbstract) {
            // sobe a cadeia de super-ABSTRATOS (mesma normalização do
            // checkAbstractClassImplementation: nome simples, sem generics)
            String curSuper = cls.superClass();
            java.util.Set<String> seen = new java.util.HashSet<>();
            seen.add(cls.name());
            while (curSuper != null && !curSuper.isEmpty() && !"Object".equals(curSuper)) {
                String simple = curSuper.contains("/")
                        ? curSuper.substring(curSuper.lastIndexOf("/") + 1) : curSuper;
                if (simple.contains("<")) simple = simple.substring(0, simple.indexOf("<"));
                if (!seen.add(simple)) break;
                SymbolTable.ClassSymbol superSym = knownClasses.get(simple);
                if (superSym == null) break;
                if (!abstractClasses.contains(simple)) break;
                obligations.add(new ClassDeclIfaces(superSym.interfaces(), simple));
                curSuper = superSym.superClass();
            }
        }
        for (ClassDeclIfaces ob : obligations) {
            for (String ifaceName : ob.ifaces()) {
                SymbolTable.ClassSymbol ifaceSym = knownClasses.get(ifaceName);
                if (ifaceSym == null || !interfaceNames.contains(ifaceName)) continue;
                for (Map.Entry<String, SymbolTable.Symbol> e
                        : ifaceSym.members().localSymbols().entrySet()) {
                    if (!(e.getValue() instanceof SymbolTable.MethodSymbol im)) continue;
                    // #213: métodos default (com corpo) já têm implementação na
                    // interface — a classe implementadora não precisa declará-los.
                    if ((im.accessFlags() & AccessFlags.ABSTRACT) == 0) continue;
                    SymbolTable.Symbol local = clsAbstract
                            ? classScope.resolve(im.name())
                            : MemberResolver.resolveInHierarchy(sa, cls.name(), im.name());
                    // #322: só um método CONCRETO satisfaz a obrigação. O
                    // walk transitivo via resolveInHierarchy pode devolver a
                    // PRÓPRIA declaração abstrata da interface (ou um abstract
                    // de um super abstrato) — isso NÃO implementa nada.
                    boolean implemented = false;
                    if (local instanceof SymbolTable.MethodSymbol cm) {
                        if ((cm.accessFlags() & AccessFlags.ABSTRACT) == 0) {
                            implemented = true;
                            if (cm.parameterTypes().size() != im.parameterTypes().size()) {
                                diagnostics.error(cls,
                                        "method '" + im.name() + "' of interface '" + ifaceName
                                                + "' expects " + im.parameterTypes().size()
                                                + " parameter(s) but implementation has "
                                                + cm.parameterTypes().size(),
                                        "SEM043");
                            }
                        }
                    } else if (local instanceof SymbolTable.MethodSet ms) {
                        for (SymbolTable.MethodSymbol cm : ms.methods()) {
                            if ((cm.accessFlags() & AccessFlags.ABSTRACT) == 0) {
                                implemented = true;
                                break;
                            }
                        }
                    }
                    if (!implemented && !clsAbstract) {
                        diagnostics.error(cls,
                                "class '" + cls.name() + "' does not implement method '" + im.name()
                                        + "' of interface '" + ifaceName + "'"
                                        + (ob.via() != null ? " inherited via '" + ob.via() + "'" : ""),
                                "SEM043");
                    }
                }
            }
        }
    }

    /**
     * #610: uma classe CONCRETA que herda dois {@code default} com a MESMA
     * assinatura (nome + parâmetros) de interfaces NAO-RELACIONADAS (nenhuma
     * eh superinterface da outra) e nao declara override compila limpo e
     * estoura no class-LOAD com {@code IncompatibleClassChangeError:
     * Conflicting default methods} (checagem de diamante da JVM, JLS 9.4.1.3).
     * R6: nao pode passar em silencio — erro de compile nomeando as duas
     * interfaces e o metodo. Override explicito da classe (ou de um super)
     * resolve (nao ha erro); se uma interface eh subtipo da outra, o default
     * mais especifico vence e tambem nao ha conflito.
     */
    static void checkConflictingDefaults(SemanticAnalyzer sa,
            ClassDeclarationNode cls, SymbolTable classScope) {
        DiagnosticCollector diagnostics = sa.diagnostics();
        if (diagnostics == null) return;
        // #322: a classe ABSTRATA pode deferir a resolucao para a subclasse concreta.
        if (cls.modifiers().contains("abstract")) return;
        Map<String, SymbolTable.ClassSymbol> known = sa.allClasses();
        java.util.Set<String> ifaceNames = sa.interfaceNames();
        if (ifaceNames.contains(cls.name())) return;

        // Fecho de interfaces da classe (declaradas + herdadas dos super).
        java.util.LinkedHashMap<String, SymbolTable.ClassSymbol> ifaces = new java.util.LinkedHashMap<>();
        java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>();
        java.util.Set<String> seenIface = new java.util.HashSet<>();
        for (String s : cls.interfaces()) if (s != null && !s.isEmpty()) queue.add(s);
        String curSuper = cls.superClass();
        java.util.Set<String> seenClass = new java.util.HashSet<>();
        seenClass.add(cls.name());
        while (curSuper != null && !curSuper.isEmpty() && !"Object".equals(curSuper)) {
            String simple = HierarchyResolver.simpleOfStored(curSuper);
            if (simple == null || simple.isEmpty() || !seenClass.add(simple)) break;
            SymbolTable.ClassSymbol cs = known.get(simple);
            if (cs == null) break;
            for (String s : cs.interfaces()) if (s != null && !s.isEmpty()) queue.add(s);
            curSuper = cs.superClass();
        }
        while (!queue.isEmpty()) {
            String simple = HierarchyResolver.simpleOfStored(queue.poll());
            if (simple == null || simple.isEmpty() || !seenIface.add(simple)) continue;
            SymbolTable.ClassSymbol is = known.get(simple);
            if (is == null || !ifaceNames.contains(simple)) continue;
            ifaces.put(simple, is);
            for (String s : is.interfaces()) if (s != null && !s.isEmpty()) queue.add(s);
        }

        // assinatura (nome/aridade) -> interfaces que declaram um default.
        java.util.LinkedHashMap<String, List<String>> sigToIfaces = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, SymbolTable.ClassSymbol> e : ifaces.entrySet()) {
            for (Map.Entry<String, SymbolTable.Symbol> me
                    : e.getValue().members().localSymbols().entrySet()) {
                List<SymbolTable.MethodSymbol> methods = new ArrayList<>();
                if (me.getValue() instanceof SymbolTable.MethodSymbol m) methods.add(m);
                else if (me.getValue() instanceof SymbolTable.MethodSet ms) methods.addAll(ms.methods());
                for (SymbolTable.MethodSymbol m : methods) {
                    int f = m.accessFlags();
                    if ((f & AccessFlags.ABSTRACT) != 0) continue;
                    if ((f & (AccessFlags.STATIC | AccessFlags.PRIVATE)) != 0) continue;
                    sigToIfaces.computeIfAbsent(m.name() + "/" + m.parameterTypes().size(),
                            k -> new ArrayList<>()).add(e.getKey());
                }
            }
        }

        for (Map.Entry<String, List<String>> e : sigToIfaces.entrySet()) {
            List<String> decl = e.getValue();
            if (decl.size() < 2) continue;
            // interfaces "maximais" (nao dominadas por outra): se >= 2, colidem.
            List<String> maximal = new ArrayList<>();
            for (String x : decl) {
                boolean dominated = false;
                for (String y : decl) {
                    if (!x.equals(y) && isSubInterfaceOf(known, ifaceNames, y, x)) { dominated = true; break; }
                }
                if (!dominated && !maximal.contains(x)) maximal.add(x);
            }
            if (maximal.size() < 2) continue;
            String name = e.getKey().substring(0, e.getKey().lastIndexOf('/'));
            int arity = Integer.parseInt(e.getKey().substring(e.getKey().lastIndexOf('/') + 1));
            if (declaresConcreteOverride(sa, known, cls, classScope, name, arity)) continue;
            diagnostics.error(cls,
                    "class '" + cls.name() + "' inherits conflicting default method '" + name
                            + "' from unrelated interfaces '" + maximal.get(0) + "' and '"
                            + maximal.get(1) + "' (add an explicit override in '" + cls.name() + "')",
                    "SEM101");
        }
    }

    /** x eh superinterface (ancestral) de y — usado para a dominancia do default. */
    private static boolean isSubInterfaceOf(Map<String, SymbolTable.ClassSymbol> known,
            java.util.Set<String> ifaceNames, String sub, String sup) {
        java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        queue.add(sub);
        while (!queue.isEmpty()) {
            String simple = HierarchyResolver.simpleOfStored(queue.poll());
            if (simple == null || simple.isEmpty() || !seen.add(simple)) continue;
            if (simple.equals(sup)) return true;
            SymbolTable.ClassSymbol is = known.get(simple);
            if (is == null || !ifaceNames.contains(simple)) continue;
            for (String s : is.interfaces()) if (s != null && !s.isEmpty()) queue.add(s);
        }
        return false;
    }

    /** A classe (ou um super concreto) declara um metodo concreto com a assinatura? */
    private static boolean declaresConcreteOverride(SemanticAnalyzer sa,
            Map<String, SymbolTable.ClassSymbol> known, ClassDeclarationNode cls,
            SymbolTable classScope, String name, int arity) {
        if (classScope != null && hasConcrete(classScope, name, arity)) return true;
        SymbolTable.ClassSymbol cur = known.get(cls.name());
        String superName = cur == null ? null : HierarchyResolver.simpleOfStored(cur.superClass());
        java.util.Set<String> seen = new java.util.HashSet<>();
        while (superName != null && !superName.isEmpty() && !"Object".equals(superName)
                && seen.add(superName)) {
            SymbolTable scope = sa.classMemberScopes().get(superName);
            if (scope != null && hasConcrete(scope, name, arity)) return true;
            SymbolTable.ClassSymbol cs = known.get(superName);
            superName = cs == null ? null : HierarchyResolver.simpleOfStored(cs.superClass());
        }
        return false;
    }

    private static boolean hasConcrete(SymbolTable scope, String name, int arity) {
        SymbolTable.Symbol s = scope.resolve(name);
        if (s instanceof SymbolTable.MethodSymbol m) {
            return (m.accessFlags() & AccessFlags.ABSTRACT) == 0
                    && (m.accessFlags() & (AccessFlags.STATIC | AccessFlags.PRIVATE)) == 0
                    && m.parameterTypes().size() == arity;
        }
        if (s instanceof SymbolTable.MethodSet ms) {
            for (SymbolTable.MethodSymbol m : ms.methods()) {
                if ((m.accessFlags() & AccessFlags.ABSTRACT) == 0
                        && (m.accessFlags() & (AccessFlags.STATIC | AccessFlags.PRIVATE)) == 0
                        && m.parameterTypes().size() == arity) return true;
            }
        }
        return false;
    }

    /** #322: par (interfaces-declaradas, pai-abstrato-que-as-declarou). */
    private record ClassDeclIfaces(List<String> ifaces, String via) {}

    /**
     * #326: overriding method deve ter retorno COMPATIVEL com o do
     * sobrescrito (JLS 8.4.8.3 / JVM invokevdispatch). Retorno COVARIANTE
     * (subtipo) e legal — gerado como bridge por
     * `generateCovariantReturnBridges` (#248). Retorno INCOMPATIVEL (nem
     * igual, nem atribuivel ao do pai) e a causa do bug: o emit gera o metodo
     * com o descritor do FILHO, a JVM nao ve override, e a chamada via
     * referencia do pai faz dispatch ao pai (saida errada, silenciosa). Re-
     * jeitar em compile-time (R6), apontando o metodo, o pai e os dois tipos.
     */
    static void checkOverrideReturnCompatibility(SemanticAnalyzer sa,
            ClassDeclarationNode cls) {
        DiagnosticCollector diagnostics = sa.diagnostics();
        if (diagnostics == null) return;
        String curSuper = cls.superClass();
        if (curSuper == null || curSuper.isEmpty() || "Object".equals(curSuper)) return;
        SymbolTable classScope = sa.classMemberScopes().get(cls.name());
        if (classScope == null) return;
        Map<String, SymbolTable.ClassSymbol> knownClasses = sa.allClasses();
        java.util.Set<String> visited = new java.util.HashSet<>();
        visited.add(cls.name());
        while (curSuper != null && !curSuper.isEmpty() && !"Object".equals(curSuper)) {
            String simple = curSuper.contains("/")
                    ? curSuper.substring(curSuper.lastIndexOf("/") + 1) : curSuper;
            if (simple.contains("<")) simple = simple.substring(0, simple.indexOf("<"));
            if (!visited.add(simple)) break;
            SymbolTable.ClassSymbol superSym = knownClasses.get(simple);
            if (superSym == null) break;
            for (Map.Entry<String, SymbolTable.Symbol> e
                    : classScope.localSymbols().entrySet()) {
                List<SymbolTable.MethodSymbol> childMethods = new ArrayList<>();
                if (e.getValue() instanceof SymbolTable.MethodSymbol c) childMethods.add(c);
                else if (e.getValue() instanceof SymbolTable.MethodSet cs) childMethods.addAll(cs.methods());
                for (SymbolTable.MethodSymbol child : childMethods) {
                    if ((child.accessFlags() & (AccessFlags.STATIC | AccessFlags.PRIVATE)) != 0) continue;
                    SymbolTable.Symbol parent = superSym.members().resolve(child.name());
                    List<SymbolTable.MethodSymbol> parents = new ArrayList<>();
                    if (parent instanceof SymbolTable.MethodSymbol p) parents.add(p);
                    else if (parent instanceof SymbolTable.MethodSet pset) parents.addAll(pset.methods());
                    for (SymbolTable.MethodSymbol pm : parents) {
                        if ((pm.accessFlags() & (AccessFlags.STATIC | AccessFlags.PRIVATE)) != 0) continue;
                        if (pm.parameterTypes().size() != child.parameterTypes().size()) continue;
                        boolean paramsMatch = true;
                        for (int i = 0; i < child.parameterTypes().size(); i++) {
                            if (!child.parameterTypes().get(i).equals(pm.parameterTypes().get(i))) {
                                paramsMatch = false;
                                break;
                            }
                        }
                        if (!paramsMatch) continue;
                        Type parentRet = pm.returnType();
                        Type childRet = child.returnType();
                        if (childRet.equals(parentRet)) continue;
                        if (TypeChecker.isAssignable(sa, childRet, parentRet)) continue;
                        diagnostics.error(cls,
                                "method '" + child.name() + "' in class '" + cls.name()
                                        + "' overrides '" + simple + "' but return type " + childRet
                                        + " is not compatible with the overridden return type " + parentRet,
                                "SEM059");
                    }
                }
            }
            curSuper = superSym.superClass();
        }
    }
}
