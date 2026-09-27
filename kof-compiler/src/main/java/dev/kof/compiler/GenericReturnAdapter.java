package dev.kof.compiler;

import java.util.List;

/**
 * Ajuste do valor devolvido por uma chamada cujo retorno declarado é uma
 * variável de tipo {@code T} (apagada a {@code Object} no descritor JVM).
 *
 * <p>Quando o tipo EFETIVO do retorno é conhecido pela substituição
 * ({@code Box<String>} → {@code T=String}), o call-site precisa adaptar o
 * valor que saiu como {@code Object}:
 * <ul>
 *   <li>primitivo ({@code T=Int}) → unbox ({@code kof_unbox});</li>
 *   <li>referência concreta ({@code T=String}) → {@code checkcast} (#161);
 *       sem ele o {@code invokevirtual} seguinte recebe {@code Object} na
 *       pilha → {@code VerifyError: Bad type on operand stack}.</li>
 * </ul>
 * JS/Native ignoram {@code KofCheckCast} (no-op) — o ajuste é inofensivo lá.
 */
final class GenericReturnAdapter {

    private GenericReturnAdapter() {}

    static void emit(CompilerDriver driver, MethodCallExpr mc, List<KofOperation> ops,
            List<IRLocalVariable> locals, Type declaredReturn) {
        if (!(declaredReturn instanceof Type.TypeVariable)) return;
        Type effective = ExpressionTyper.inferExprType(driver, mc, locals);
        // §355: efetivo ainda é uma variável de tipo COM bound (`T: Animal`
        // passado como `T` doutro genérico) — o alvo concreto do cast é o
        // bound; unbounded segue sem cast (Object cru na pilha é legal).
        if (effective instanceof Type.TypeVariable tv && tv.bound() != null) {
            effective = tv.bound();
        }
        emitBound(driver, ops, effective);
    }

    /**
     * §479 — mesma decisão primitivo/referência do {@link #emit}, mas para um
     * tipo EFETIVO já LIGADO pelo witness explícito do call-site
     * (`idf<Point>(...)`): a decisão independe de {@code inferExprType}, que
     * no path de módulo/CLI não registra o tipo da chamada top-level genérica
     * e deixava o checkcast de referência de fora (VerifyError no load).
     */
    static void emitBound(CompilerDriver driver, List<KofOperation> ops, Type effective) {
        if (effective == null || effective instanceof Type.UnknownType) return;
        if (TypeMetrics.isPrimitiveType(effective)) {
            driver.emitErasureUnbox(ops, effective);
            return;
        }
        Type ref = effective instanceof Type.NullableType nt ? nt.inner() : effective;
        boolean concrete = ref instanceof Type.ClassType || ref instanceof Type.ArrayType;
        if (concrete && !(ref instanceof Type.ClassType ct && "Object".equals(ct.name()))) {
            ops.add(new KofCheckCast(ref));
        }
    }

    /**
     * §482 — substitui as variáveis de tipo da DECLARAÇÃO pelos argumentos
     * EXPLÍCITOS do call-site (`idf<Point>` com `T idf<T>(T x)` → `Point`),
     * recursivo (List<T>, T?, arrays). Dono natural: a adaptação de retorno
     * genérico desta classe (o binding alimenta o {@link #emitBound};
     * NÃO alimenta o descritor do KofCall, que fica apagado/erasure).
     */
    static Type bindTypeVariables(Type t, List<String> typeParams, List<Type> witness) {
        if (t instanceof Type.TypeVariable tv) {
            for (int i = 0; i < typeParams.size(); i++) {
                if (i < witness.size() && TypeParams.name(typeParams.get(i)).equals(tv.name())) {
                    return witness.get(i);
                }
            }
            return t;
        }
        if (t instanceof Type.ClassType ct && !ct.typeArguments().isEmpty()) {
            return new Type.ClassType(ct.packageName(), ct.name(),
                    ct.typeArguments().stream().map(ta -> bindTypeVariables(ta, typeParams, witness)).toList());
        }
        if (t instanceof Type.ArrayType at) {
            return new Type.ArrayType(bindTypeVariables(at.componentType(), typeParams, witness));
        }
        if (t instanceof Type.NullableType nt) {
            return new Type.NullableType(bindTypeVariables(nt.inner(), typeParams, witness));
        }
        return t;
    }
}

