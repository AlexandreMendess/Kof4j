package dev.kof.compiler;

import java.util.List;

/**
 * Slot/box rules and the enum natural-order lambda shared by
 * {@link CollectionCallLowerer} and its neighbouring collection lowering
 * helpers. Extracted (#685) to keep each class under the 500-line gate.
 */
final class CollectionLoweringSupport {

    private CollectionLoweringSupport() {}

    // §284-map (18/09): slot que comporta caixa — concreto na familia
    // Int/Long OU apagado (Unknown/Object — mapOf() sem pin, Map<_,Object>).
    static boolean mapSlotAcceptsBox(Type t) {
        Type inner = t instanceof Type.NullableType nt ? nt.inner() : t;
        if (inner instanceof Type.UnknownType || BuiltinTypes.isObject(inner)) return true;
        return mapBoxablePrim(inner);
    }

    // ...e o dominio exato de unboxFn no nativo (int/char/short/byte/long).
    // Double/Float/Bool ficam crus la e ca: sem kof_unbox para eles, o par
    // cru × no-op que existia antes do §284 permanece casado (zero regressao).
    static boolean mapBoxablePrim(Type t) {
        if (!(t instanceof Type.PrimitiveType pt)) return false;
        return switch (pt.name()) {
            case "int", "char", "short", "byte", "long" -> true;
            default -> false;
        };
    }


    /**
     * #685 — comparador sintético `(a, b) -> a.compareTo(b)` para a ordem
     * natural de enum (D-ENUM207: ordinal-based, determinístico em todos os
     * alvos). O corpo é um {@code MethodCallExpr} comum, então o lowering
     * reusa {@code ExpressionBuiltinInstanceCalls.lowerEnum} — a MESMA
     * chamada que o usuário escreveria; nenhum caminho de semântica novo.
     */
    static ExpressionNode enumOrderLambda(CompilerDriver driver, MethodCallExpr mc, Type elemType) {
        SourcePosition p = mc.position();
        String typeName = CollectionWrites.typeNameFor(
                elemType instanceof Type.NullableType nt ? nt.inner() : elemType);
        FormalParameterNode a = new FormalParameterNode(p, List.of(), typeName, "a", null);
        FormalParameterNode b = new FormalParameterNode(p, List.of(), typeName, "b", null);
        ExpressionNode call = new MethodCallExpr(p, new IdentifierExpr(p, "a"), "compareTo",
                List.of(), List.of(new IdentifierExpr(p, "b")));
        return new LambdaExpr(p, List.of(a, b), List.of(new ReturnStmt(p, call)));
    }

    /** §352 NAT002 — slot de valor REFERÊNCIA (Object): TODO primitivo é
     *  normalizado como caixa no put nativo (kof_box_* existe para
     *  Double/Float/Bool também). O runtime classifica as caixas/Strings/
     *  ponteiros no scan de containsValue (tag 6 dinâmico) — sem kind
     *  estático, sem deref cega. */
    static boolean referenceSlotPrim(Type slot, Type arg) {
        if (!(arg instanceof Type.PrimitiveType)) return false;
        Type s = slot instanceof Type.NullableType nt ? nt.inner() : slot;
        return BuiltinTypes.isObject(s);
        }
}
