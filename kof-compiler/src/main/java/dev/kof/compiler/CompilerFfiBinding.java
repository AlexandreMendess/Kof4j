package dev.kof.compiler;

/**
 * Gate de binding FFI por target (TIER 2.1.4): decide se um {@code extern}
 * tem ABI implementada no alvo corrente. Extraído do {@link CompilerPipeline}
 * (refactor behavior-preserving, TIER 13.5 / precedente {@link FfiSignature}
 * {@code structReturnBindable}) para manter o pipeline ≤500 linhas.
 *
 * <p>Nunca stub silencioso: o que não binda vira diagnóstico honesto no
 * call-site da declaração (FFI001/FFI002, R6).
 */
final class CompilerFfiBinding {

    private CompilerFfiBinding() {}

    // ── FFI (TIER 2.1.4) — binding suportado por target ──
    static boolean isExternBound(CompilerDriver driver, ExternalFunctionNode ext) {
        // JVM e JS (runner) compartilham a MESMA ABI escalar + callbacks (3.4-C3): o
        // KofJS roda no host GraalJS/node, que É uma JVM com java.lang.foreign (bridge
        // `KofJsFfiBridge` idêntico ao `kof_ffi` do target JVM; o browser não tem host e
        // degrada em runtime como o resto do kof_platform, R7). Android intocado (§278).
        if (driver.target == Target.JVM || driver.target == Target.JS) {
            // 3.8b fatia 2: retorno de `record` binda só no JVM (JS = FFI002, R6).
            if (FfiSignature.returnChar(ext.returnType()) == null
                    && !FfiSignature.structReturnBindable(driver, ext)) return false;
            for (var param : ext.parameters()) {
                if (FfiSignature.paramChar(param.type()) != null) continue;
                if (FfiSignature.callbackDescriptor(param.type()) != null) continue;
                // D6-1(A)/3.8b: um `record` de campos escalares binda por valor no
                // JVM (FFM classifica o struct) E no runner JS (bridge de struct
                // 21/09: token `@<n><chars>` + `__kof_ffi_fields` + pack no host,
                // paridade com o `kof_ffi_write_struct` reflexivo do JVM). Retorno
                // de struct segue fora do conjunto nesta fatia (FFI002 no JS).
                if (FfiSignature.structFieldChars(param.type(), driver) != null) continue;
                // D6-2 / 3.8b fatia 3: `T[]` escalar binda por valor (ptr + copies)
                // no JVM E no runner JS (copy-in 21/09: o Marshal lê o array JS e
                // copia para a arena da chamada). Native fica no gap code (R6).
                if (FfiSignature.arrayElemChar(param.type()) != null) continue;
                // D6-3 / D-R3-BUFFER: `Buffer(U8)` como param INOUT binda no JVM E
                // no runner JS (bridge de buffer 21/09: `packBuffer` copia in e o
                // copy-back pós-chamada devolve ao `Uint8Array` do guest, paridade
                // com o `kof_ffi_buffer_in/out` do JVM). Native fica no gap (R6).
                if (FfiSignature.isBufferParam(param.type())
                        && (driver.target == Target.JVM || driver.target == Target.JS)) continue;
                return false;
            }
            return true;
        }
        // #431/§61 (Native x86-64): ABI escalar direto — o link do binário traz a
        // `library()` do extern como input do ld e o call-site baixa marshaling
        // SysV + `call sym@PLT` (o mesmo caminho do consumidor SQLite/DB001, que
        // prova o PLT a partir do _start cru). Sem dlopen em runtime — a rota do
        // §61 que nunca dependeu de glibc initialized. Callback (sem mechanism de
        // upcall nativo), array/struct e `extern` sem `library()` (nada a linkar)
        // continuam FFI001 honesto na linha da declaração (R6). riscv64/aarch64:
        // mesma ABI com shim próprio — ver branch abaixo.
        if (driver.target.isNative()) {
            return nativeExternBound(driver, ext);
        }
        // NATIVE (riscv64/aarch64): o shim cross (LP64/AAPCS64) landou na
        // fatia 2 do #431 — gate+lowering+E2E qemu no MESMO commit (política
        // das fatias R3). Struct/array/callback e extern sem `library()`
        // continuam FFI001 honesto na linha da declaração (R6).
        return false;
    }

    private static boolean nativeExternBound(CompilerDriver driver, ExternalFunctionNode ext) {
        if (ext.library() == null || ext.library().isEmpty()) return false;
        // ABI ESCALAR binda em TODO alvo nativo: x86-64 SysV (fatia 1) e o shim
        // LP64/AAPCS64 do riscv64/aarch64 (#431 fatia 2, gate+lowering+E2E qemu
        // no mesmo commit). STRUCT por valor binda nos dois: x86-64 register/sret
        // (D6-1(A)/3.7 fatias 1–2b) e cross register path INTEGER ≤ 16 B (fatia 4)
        // — float/HFA/byref cross segue FFI001 honesto (R6).
        // O caller só entra aqui com `driver.target.isNative()`.
        boolean x86 = driver.target == Target.NATIVE;
        // Retorno: escalar/void, struct por valor no register path (≤ 16 B) OU
        // sret (> 16 B, ponteiro escondido — D6-4).
        boolean sret = false;
        if (FfiSignature.returnChar(ext.returnType()) == null) {
            String retFields = FfiSignature.structFieldChars(ext.returnType(), driver);
            if (retFields == null) return false;
            Type retStruct = FfiStructLayout.structTypeOfChars(retFields);
            if (x86) {
                if (FfiStructLayout.x86RegisterOnly(retStruct)) {
                    // register path
                } else if (FfiStructLayout.x86SretReturn(retStruct)) {
                    sret = true;
                } else {
                    return false;
                }
            } else if (!FfiStructLayout.crossIntRegisterOnly(driver.target, retStruct)) {
                // 3.7 fatia 3: cross struct return = INTEGER register path only;
                // floats/HFA/byref segue FFI001 honesto (R6).
                return false;
            }
        }
        java.util.List<Type> paramTypes = new java.util.ArrayList<>();
        for (var param : ext.parameters()) {
            if (FfiSignature.paramChar(param.type()) != null) {
                paramTypes.add(FfiSignature.paramType(param.type()));
                continue;
            }
            // D6-2/3.7: array escalar `T[]`→`ptr` no x86-64. A largura de slot
            // do elemento Kof == largura C (Long/Double 8 B, Int/Float 4 B,
            // Bool 1 B), então o pack é um memcpy com o tamanho do elemento.
            // Cross e `String[]` (array de ponteiros) seguem FFI001 (R6).
            Character ae = FfiSignature.arrayElemChar(param.type());
            if (ae != null) {
                if (!x86) return false;
                paramTypes.add(FfiStructLayout.arrayPtrType(ae));
                continue;
            }
            // D6-1(A)/3.7: `record` de campos escalares por valor (register path) — x86-64.
            String fc = FfiSignature.structFieldChars(param.type(), driver);
            if (fc == null) return false;
            Type st = FfiStructLayout.structTypeOfChars(fc);
            // 3.7 fatia 4: no cross o struct por valor binda só no register path
            // INTEGER (≤ 16 B) — float/HFA/byref segue FFI001 honesto (R6).
            if (!x86 && !FfiStructLayout.crossIntRegisterOnly(driver.target, st)) return false;
            paramTypes.add(st);
        }
        // Chamada puramente escalar: binda em todo nativo (o layout x86 não se
        // aplica). sret consome 1 registrador INTEGER (o ponteiro escondido) —
        // os parâmetros deslocam uma posição (rdi vira rsi…).
        if (x86) return FfiStructLayout.x86Bindable(paramTypes, sret ? 1 : 0);
        return FfiStructLayout.crossBindable(paramTypes);
    }
}
