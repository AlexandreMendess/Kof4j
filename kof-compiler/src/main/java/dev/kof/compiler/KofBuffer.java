package dev.kof.compiler;

import java.util.List;

/**
 * {@code kof.buffer} namespace + the nominal {@code Buffer(U8)} type
 * (D-R3-BUFFER / D6-3, maintainer 21/09/2026).
 *
 * <p>Incremental slice (R6-SCOPE): {@code buffer.alloc(Int) : Buffer(U8)} and
 * {@code Buffer.bytes() : Byte[]} on the JVM, JS, and — since the #651 fatia A1
 * x86 surface — the {@code Target.NATIVE} x86-64 backend. The programmer never
 * allocates or frees — the lifetime is language-managed (D-R3-HANDLE-LIFETIME).
 * The FFI out-buffer token {@code B} remains a separate later slice (fatia A2);
 * cross Native targets stay an honest gap.
 */
public final class KofBuffer {
    private KofBuffer() {}

    static final Type BUFFER = new Type.ClassType("kof", "Buffer", List.of());
    private static final Type INT = Type.PrimitiveType.INT;
    private static final Type BYTES = new Type.ArrayType(Type.PrimitiveType.BYTE);

    static boolean isBufferNamespace(String name) { return "buffer".equals(name); }

    /** LSP catalogue — GUARD: StdCatalogTest locks this to the dispatch below. */
    static List<String> functions() { return List.of("alloc"); }

    public static boolean isBufferType(Type t) { return BUFFER.equals(t); }

    /** The element type accepted in `Buffer(<elem>)` — only U8 (Kof `Byte`) in v1. */
    static boolean isBufferElement(String t) {
        return "U8".equals(t) || "u8".equals(t) || "Byte".equals(t) || "byte".equals(t);
    }

    record BufferCall(String function, Type returnType, List<Type> parameterTypes) {}

    static BufferCall staticMethod(String namespace, String name, List<Type> argTypes) {
        if (!"buffer".equals(namespace)) return null;
        return switch (name) {
            case "alloc" -> argTypes.size() == 1
                    ? new BufferCall("kof_buffer_alloc", BUFFER, List.of(INT)) : null;
            default -> null;
        };
    }

    static BufferCall instanceMethod(Type receiver, String name, int argCount) {
        if (!isBufferType(receiver)) return null;
        return switch (name) {
            case "bytes" -> argCount == 0
                    ? new BufferCall("kof_buffer_bytes", BYTES, List.of(BUFFER)) : null;
            default -> null;
        };
    }

    static boolean supportedOn(Target target) {
        // JVM/JS landed in 21/09; x86-64 native surface (alloc/bytes/println) landed in
        // #651 fatia A1. Cross native targets and the FFI B token remain honest gaps.
        return target == Target.JVM || target == Target.JS || target == Target.NATIVE;
    }

    static String gapCode(Target target) {
        // The Buffer namespace binds on JVM, JS and x86-64 Native. Cross Native targets
        // (and other non-JVM/JS targets) still report FFI001; the FFI `B` parameter is
        // separately gated by the extern binding rule and is not opened by fatia A1.
        return "FFI001";
    }
}
