package dev.kof.compiler;

import java.util.List;

/**
 * Emissão de operações de tipo (boxing/erasure/widening) sobre a lista de
 * ops IR. Puro — recebe o que precisa por parâmetro.
 */
public final class TypeEmitter {

    private TypeEmitter() {}

    /** Boxa um primitivo via static valueOf (equivalente Kof de autoboxing). */
    static void boxPrimitive(List<KofOperation> ops, Type type) {
        if (type instanceof Type.NullableType nt) {
            boxPrimitive(ops, nt.inner());
            return;
        }
        if (type instanceof Type.PrimitiveType pt) {
            String name = Type.canonicalPrimitiveName(pt.name());
            Type boxed = switch (name) {
                case "int" -> new Type.ClassType("java.lang", "Integer", List.of());
                case "long" -> new Type.ClassType("java.lang", "Long", List.of());
                case "float" -> new Type.ClassType("java.lang", "Float", List.of());
                case "double" -> new Type.ClassType("java.lang", "Double", List.of());
                case "bool" -> new Type.ClassType("java.lang", "Boolean", List.of());
                case "char" -> new Type.ClassType("java.lang", "Integer", List.of());
                case "byte" -> new Type.ClassType("java.lang", "Byte", List.of());
                case "short" -> new Type.ClassType("java.lang", "Short", List.of());
                default -> Type.UnknownType.UNKNOWN;
            };
            Type boxParam = "char".equals(name) ? Type.PrimitiveType.INT : type;
            ops.add(new KofCall(boxed, "valueOf", List.of(boxParam), boxed, KofCallKind.STATIC));
            return;
        }
        // §519/#632 (UIW050): handle de kof.ui/midia e int-apagado no stack.
        // Na borda de referencia (Object slot, valueOf, receiver invokevirtual,
        // par Object do RecordEqualityLowerer) precisa virar `Integer.valueOf(I)`
        // sobre o INT cru — o valor fisicamente empilhado e um int, nunca a
        // Classe nominal View/Color (senao String.valueOf(Object) recebe int cru
        // e o verifier JVM rejeita: VerifyError "integer not assignable Object").
        if (type instanceof Type.ClassType && (KofUi.isUiType(type) || KofMedia.isHandleType(type))) {
            Type boxed = new Type.ClassType("java.lang", "Integer", List.of());
            ops.add(new KofCall(boxed, "valueOf", List.of(Type.PrimitiveType.INT), boxed, KofCallKind.STATIC));
        }
    }
}