package dev.kof.compiler;

import java.util.List;

/**
 * {@code kof.buffer} namespace + the nominal {@code Buffer(U8)} type
 * (D-R3-BUFFER / D6-3, maintainer 21/09/2026).
 *
 * <p>Incremental slice (R6-SCOPE): {@code buffer.alloc(Int) : Buffer(U8)} and
 * {@code Buffer.bytes() : Byte[]} on the JVM and, since 21/09, also on the JS
 * target ({@code JsRuntimeBuffer}, same contract as {@code KofRuntime$Buffer}).
 * The programmer never allocates or frees — the lifetime is language-managed
 * (D-R3-HANDLE-LIFETIME). Native stays an honest gap; the FFI out-buffer
 * (token {@code B}) is a separate slice and still {@code FFI002} on JS.
 */
public final class KofBuffer {
    private KofBuffer() {}

    static final Type BUFFER = new Type.ClassType("kof", "Buffer", List.of());
    private static final Type INT = Type.PrimitiveType.INT;
    private static final Type BYTES = new Type.ArrayType(Type.PrimitiveType.BYTE);

    static boolean isBufferNamespace(String name) { return "buffer".equals(name); }

    /** LSP catalogue — GUARD: StdCatalogTest locks this to the dispatch below. */
    static List<String> functions() { return List.of("alloc"); }

    static boolean isBufferType(Type t) { return BUFFER.equals(t); }

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
        return target == Target.JVM || target == Target.JS; // Native honest gap (R6/R7)
    }

    static String gapCode(Target target) {
        // Buffer binds on JVM AND JS (D-R3-BUFFER; the JS namespace landed on the
        // JS target in R57/R58) — the JS-specific FFI002 gap is gone. The only
        // remaining gap is the Native family (and other non-JVM/JS targets) → FFI001.
        return "FFI001";
    }
}
