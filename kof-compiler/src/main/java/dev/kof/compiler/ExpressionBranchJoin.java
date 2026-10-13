package dev.kof.compiler;

import java.util.List;

/**
 * Satélite do REFACTOR-500 (check_500, ExpressionTyper 620→<600): a família de
 * JOIN de braços (differ/unify/supertype — §284/JVM-03/§673/#819, os comentários
 * viajam). Statics package-private com as mesmas assinaturas; zero mudança de
 * comportamento.
 */
final class ExpressionBranchJoin {

    private ExpressionBranchJoin() {}

    static Type joinBranchSupertype(CompilerDriver driver, List<Type> ts) {
        if (ts.isEmpty()) return null;
        Type acc = ts.get(0);
        for (int i = 1; i < ts.size(); i++) {
            Type next = ts.get(i);
            if (!(acc instanceof Type.ClassType) || !(next instanceof Type.ClassType)) return null;
            if (driver.semanticAnalyzer == null) return null;
            Type joined = HierarchyResolver.commonSupertype(driver.semanticAnalyzer, acc, next);
            if (!(joined instanceof Type.ClassType)) return null;
            acc = joined;
        }
        return acc;
    }

    static boolean branchTypesDiffer(List<Type> ts) {
        for (Type t : ts) if (!isConcreteBranchType(t)) return false;
        return ts.stream().distinct().count() > 1;
    }

    /**
     * JVM-03 (audit 0.6.0, §673): two lambda branches of the SAME signature
     * (differing only in the concrete synthetic className) unify to the
     * className-less interface, NOT commonSupertype -> Object. A Lambda0/
     * Lambda1 pair is not equal, so the join yielded Object and the call site
     * emitted a phantom {@code invokestatic} returning Object -> VerifyError.
     * Gated OFF when the lambda's own return type is a FunctionType (the
     * lambda-returns-lambda family needs the concrete class for its chained
     * invoke, mirroring §672's gate). Returns null when the shape does not match.
     */
    static Type unifyLambdaBranches(Type a, Type b) {
        if (a instanceof Type.FunctionType fa && b instanceof Type.FunctionType fb
                && fa.className() != null && fb.className() != null
                && !(fa.returnType() instanceof Type.FunctionType)
                && fa.parameterTypes().equals(fb.parameterTypes())
                && fa.returnType().equals(fb.returnType())) {
            return new Type.FunctionType(fa.parameterTypes(), fa.returnType(), null);
        }
        return null;
    }

    /** Switch twin of {@link #unifyLambdaBranches}: every branch a same-signature lambda. */
    static Type unifyAllLambdaBranches(List<Type> ts) {
        if (ts.size() < 2) return null;
        for (Type t : ts) if (!(t instanceof Type.FunctionType ft) || ft.className() == null) return null;
        Type.FunctionType first = (Type.FunctionType) ts.get(0);
        for (Type t : ts) {
            Type.FunctionType ft = (Type.FunctionType) t;
            if (!ft.parameterTypes().equals(first.parameterTypes())
                    || !ft.returnType().equals(first.returnType())) return null;
        }
        return new Type.FunctionType(first.parameterTypes(), first.returnType(), null);
    }

    static boolean isConcreteBranchType(Type t) {
        if (t instanceof Type.PrimitiveType pt) return !"void".equals(pt.name());
        if (t instanceof Type.ClassType) return true;
        if (t instanceof Type.ArrayType) return true;
        if (t instanceof Type.NullableType nt) return isConcreteBranchType(nt.inner());
        return false;
    }

    /** Tipos dos ramos do if (then, else) p/ `branchTypesDiffer`. */
    static List<Type> ifBranchTypes(CompilerDriver driver, IfExpr ie,
                                    List<IRLocalVariable> locals) {
        return List.of(branchTypeOrNullAsRef(driver, ie.thenExpr(), locals),
                branchTypeOrNullAsRef(driver, ie.elseExpr(), locals));
    }

    /** Tipos dos corpos do switch (+ default; sem default, o sintético tem o
     *  tipo do switch — igual ao lowering, que emite `defaultValueOp`). */
    static List<Type> switchBranchTypes(CompilerDriver driver, List<SwitchExprCase> cases,
                                        ExpressionNode defaultValue, Type switchFallbackType,
                                        List<IRLocalVariable> locals) {
        var ts = new java.util.ArrayList<Type>();
        for (SwitchExprCase c : cases) ts.add(branchTypeOrNullAsRef(driver, c.body(), locals));
        ts.add(defaultValue != null ? branchTypeOrNullAsRef(driver, defaultValue, locals) : switchFallbackType);
        return ts;
    }

    /**
     * Tipo de um ramo p/ o predicado — com uma exceção honesta: literal
     * `null` infere `UnknownType` (não-concreto → predicado desligaria), mas
     * `null` é referência 1-word no JVM. Sem isso, `if (c) 1 else null`
     * seguia VerifyError mesmo com o mecanismo pronto.
     */
    static Type branchTypeOrNullAsRef(CompilerDriver driver, ExpressionNode e,
                                      List<IRLocalVariable> locals) {
        if (e instanceof LiteralExpr lit && lit.kind() == ConcreteLiteralKind.NULL) {
            return new Type.ClassType("java.lang", "Object", List.of());
        }
        return ExpressionTyper.inferExprType(driver, e, locals);
    }
}
