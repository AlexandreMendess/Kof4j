package dev.kof.compiler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R3: locks the compiler-side scalar type -> FFM layout char -> Kof {@link Type}
 * mapping for the FULL claimed scalar ABI set {Int, Long, Float, Double, Boolean,
 * String} + void return. The runtime downcall path is type-generic (one
 * asSpreader over the {@code Object[]} boxed args + per-char {@code ValueLayout}),
 * and it is proven end-to-end in {@link FfiE2ETest} for Int/Long/Double/String/
 * void; this unit test closes the mapping claim for Float/Boolean, which the ABI
 * maps correctly but which libc offers no clean, deterministic call site for from
 * Kof source (no float/long literals; C bool-returning predicates are rare).
 */
class FfiSignatureTest {

    @Test
    void paramCharCoversAllScalars() {
        assertEquals('i', FfiSignature.paramChar("Int"));
        assertEquals('i', FfiSignature.paramChar("int"));
        assertEquals('j', FfiSignature.paramChar("Long"));
        assertEquals('j', FfiSignature.paramChar("long"));
        assertEquals('f', FfiSignature.paramChar("Float"));
        assertEquals('f', FfiSignature.paramChar("float"));
        assertEquals('d', FfiSignature.paramChar("Double"));
        assertEquals('b', FfiSignature.paramChar("Boolean"));
        assertEquals('b', FfiSignature.paramChar("bool"));
        assertEquals('b', FfiSignature.paramChar("Bool"),
                "#431: `Bool` is the Kof frontend spelling — missing until the "
                        + "Native fatia; additive (what was FFI001 now binds)");
        assertEquals('S', FfiSignature.paramChar("String"));
    }

    @Test
    void paramCharRejectsUnmappable() {
        assertNull(FfiSignature.paramChar("Int[]"), "array param is R3 3.8 (struct/array ABI), not scalar");
        assertNull(FfiSignature.paramChar("MyStruct"));
        assertNull(FfiSignature.paramChar("void"), "void is a return-only marker");
    }

    @Test
    void returnCharAddsVoid() {
        assertEquals('v', FfiSignature.returnChar("void"));        assertEquals('v', FfiSignature.returnChar(""));
        assertEquals('v', FfiSignature.returnChar(null));
        assertEquals('j', FfiSignature.returnChar("Long"));
        assertEquals('f', FfiSignature.returnChar("Float"));
        assertEquals('b', FfiSignature.returnChar("Boolean"));
        assertEquals('S', FfiSignature.returnChar("String"));
    }

    @Test
    void returnTypeMapsToKofPrimitives() {
        assertEquals(Type.PrimitiveType.INT, FfiSignature.returnType("Int"));
        assertEquals(Type.PrimitiveType.LONG, FfiSignature.returnType("Long"));
        assertEquals(Type.PrimitiveType.FLOAT, FfiSignature.returnType("Float"));
        assertEquals(Type.PrimitiveType.DOUBLE, FfiSignature.returnType("Double"));
        assertEquals(Type.PrimitiveType.BOOL, FfiSignature.returnType("Boolean"));
        assertEquals(Type.PrimitiveType.VOID, FfiSignature.returnType("void"));
        assertSame(BuiltinTypes.STRING, FfiSignature.returnType("String"));
    }

    // R3 3.4-C3.4: callback ARGUMENTS accept String (char*->String at the upcall
    // boundary); a String RETURN stays non-bindable (cbReturnChar rejects 'S').
    @Test
    void callbackDescriptorBindsScalarAndStringArgsButNotStringReturn() {
        assertEquals("iii", FfiSignature.callbackDescriptor("(Int, Int) -> Int"));
        assertEquals("iS", FfiSignature.callbackDescriptor("(String) -> Int"));
        assertEquals("iSi", FfiSignature.callbackDescriptor("(String, Int) -> Int"));
        assertEquals("jS", FfiSignature.callbackDescriptor("(String) -> Long"));
        assertEquals("vd", FfiSignature.callbackDescriptor("(Double) -> void"));
        assertNull(FfiSignature.callbackDescriptor("(Int) -> String"),
                "String RETURN from a callback is not bindable (char* ownership)");
        assertNull(FfiSignature.callbackDescriptor("(MyStruct) -> Int"),
                "non-scalar callback arg stays non-bindable");
        assertNull(FfiSignature.callbackDescriptor("(Int[]) -> Int"),
                "array callback arg is R3 3.8, not bindable");
    }

    // #431 (Native): ponte Type→char e String→Type usada pelo call-site nativo.
    @Test
    void charOfTypeAndParamTypeBridgeNativeLowering() {
        assertEquals('i', FfiSignature.charOfType(Type.PrimitiveType.INT));
        assertEquals('j', FfiSignature.charOfType(Type.PrimitiveType.LONG));
        assertEquals('f', FfiSignature.charOfType(Type.PrimitiveType.FLOAT));
        assertEquals('d', FfiSignature.charOfType(Type.PrimitiveType.DOUBLE));
        assertEquals('b', FfiSignature.charOfType(Type.PrimitiveType.BOOL));
        assertEquals('v', FfiSignature.charOfType(Type.PrimitiveType.VOID));
        assertEquals('S', FfiSignature.charOfType(BuiltinTypes.STRING));
        assertNull(FfiSignature.charOfType(Type.PrimitiveType.CHAR),
                "Char nao e do conjunto FFI — o gate nao baixa (FFI001 na declaracao)");
        assertEquals(Type.PrimitiveType.INT, FfiSignature.paramType("Int"));
        assertEquals(Type.PrimitiveType.LONG, FfiSignature.paramType("Long"));
        assertEquals(Type.PrimitiveType.FLOAT, FfiSignature.paramType("Float"));
        assertEquals(Type.PrimitiveType.DOUBLE, FfiSignature.paramType("Double"));
        assertEquals(Type.PrimitiveType.BOOL, FfiSignature.paramType("Bool"));
        assertSame(BuiltinTypes.STRING, FfiSignature.paramType("String"));
        assertNull(FfiSignature.paramType("Int[]"), "array param never lowers raw");
    }

    // M1 unidade-4 face `S`: um ARGUMENTO String do callback binda no Native
    // x86-64 E no cross riscv64/aarch64 (char* → kof_ffi_from_cstr no trampolim);
    // um RETORNO String não (ownership do char*).
    @Test
    void nativeCallbackDescBindsStringArgButNotStringReturnOrCross() {
        assertEquals("iS", FfiSignature.nativeCallbackDesc("iS"));
        assertEquals("iSi", FfiSignature.nativeCallbackDesc("iSi"));
        assertEquals("jSS", FfiSignature.nativeCallbackDesc("jSS"));
        assertEquals("iii", FfiSignature.nativeCallbackDesc("iii"));
        assertNull(FfiSignature.nativeCallbackDesc("Si"),
                "String RETURN from a callback stays non-bindable (char* ownership)");
        assertNull(FfiSignature.nativeCallbackDesc("iSSSSSS"),
                "String arg > arity 5 stays out (no stack-arg frame)");
        assertEquals("iS", FfiSignature.crossCallbackDesc("iS"));
        assertEquals("iSi", FfiSignature.crossCallbackDesc("iSi"));
        assertNull(FfiSignature.crossCallbackDesc("Si"),
                "cross String RETURN stays non-bindable (char* ownership)");
        assertNull(FfiSignature.crossCallbackDesc("iSSSSSSSS"),
                "cross String-arg arity > 7 stays out");
    }
}
