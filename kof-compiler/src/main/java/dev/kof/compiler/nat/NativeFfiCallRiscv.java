package dev.kof.compiler.nat;

import dev.kof.compiler.AbiLayout;
import dev.kof.compiler.FfiSignature;
import dev.kof.compiler.FfiStructLayout;
import dev.kof.compiler.KofCall;
import dev.kof.compiler.Type;

import java.util.ArrayList;
import java.util.List;

/**
 * #431 (Native FFI): call-site do {@code extern} no alvo cross (riscv64 LP64 +
 * aarch64 AAPCS64). O mesmo texto riscv serve as duas archs — o
 * {@code NativeAarch64Translator} normaliza os mnemonicos (regra "um texto,
 * duas archs" do shim cross). Extraido de {@link NativeFfiCall} (regra
 * ≤500 linhas; a responsabilidade emitida aqui e o marshaling cross).
 */
final class NativeFfiCallRiscv {

    private NativeFfiCallRiscv() {}

    private static boolean isFloatClass(char c) { return c == 'f' || c == 'd'; }

    // ── riscv64 / aarch64 (LP64 duploat + AAPCS64) ───────────────────
    // O aarch64 é tradução linha-a-linha deste texto (NativeAarch64Translator
    // cobre ld/sd/mv/li/addi/and/j/beqz/call→bl/sext.w/fmv.*/lbu/ret) — um
    // shim serve as duas archs. Modelo do backend cross: `sp` É a pilha de
    // operandos; bloco de args [E, E+8n) (direita em 0(sp)); nada é popado —
    // os registradores saem por OFFSET de t0 e o sp é movido UMA vez para o
    // bloco derramado + consumo. O ponto de restauração (E+8n) mora numa
    // pilha privada ALINHADA entregue à C: a C escreve só abaixo do sp que
    // recebe e lê só [0(sp), 8·ns(sp)) — a slot salva em 8·ns+8 fica intacta.
    static void emitRiscv(NativeBackend nb, StringBuilder sb, KofCall kc) {
        String[] intRegs = {"a0", "a1", "a2", "a3", "a4", "a5", "a6", "a7"};
        int n = kc.parameterTypes().size();
        char[] cls = new char[n];
        boolean[] isStruct = new boolean[n];
        boolean[] isBuf = new boolean[n];
        boolean[] isArray = new boolean[n];
        char[] arrayElem = new char[n];
        Type[] structTypes = new Type[n];
        for (int i = 0; i < n; i++) {
            Type pt = kc.parameterTypes().get(i);
            if (FfiStructLayout.isStructType(pt)) {
                isStruct[i] = true;
                structTypes[i] = pt;
            } else if (FfiStructLayout.isArrayPtr(pt)) {
                // D-MEM-FFI-CROSS-FULL: array escalar `T[]`→ptr (copy-in por
                // chamada, pack em `kof_ffi_pack_array` riscv).
                isArray[i] = true;
                arrayElem[i] = FfiStructLayout.arrayPtrElem(pt);
            } else if (FfiStructLayout.isBufferPtr(pt)) {
                // #651 fatia B: Buffer(U8) → ponteiro do payload (obj+24), NULL→NULL
                // (mesma forma do String/'S' abaixo; o buffer cross é memória
                // contígua como no x86, então a escrita da C é o copy-back).
                isBuf[i] = true;
            } else {
                cls[i] = FfiSignature.charOfType(pt);
            }
        }
        Character retC = FfiSignature.charOfType(kc.returnType());
        boolean structRet = retC == null;   // `record` por valor (3.7 fatia 3)
        char ret = structRet ? 0 : retC.charValue();
        // ordinais POR CLASSE na ordem formal (arg0 → reg0 da sua classe). Um
        // struct INTEGER ocupa um ordinal por eightbyte (fatia 4).
        int[] ord = new int[n];
        int[][] sOrd = new int[n][];
        int nInt = 0, nFlt = 0;
        for (int i = 0; i < n; i++) {
            if (isStruct[i]) {
                int w = FfiStructLayout.crossWords(structTypes[i]);
                sOrd[i] = new int[w];
                for (int e = 0; e < w; e++) sOrd[i][e] = nInt++;
            } else if (isArray[i]) {
                ord[i] = nInt++;   // T[]→ptr: um ponteiro INTEGER (D-MEM-FFI-CROSS-FULL)
            } else if (isFloatClass(cls[i])) {
                ord[i] = nFlt++;
            } else {
                ord[i] = nInt++;
            }
        }
        int ns = (nInt > 8 ? nInt - 8 : 0) + (nFlt > 8 ? nFlt - 8 : 0);
        int seq = nb.inlineSeq++;
        // 0a) D-MEM-FFI-CROSS-FULL: arrays `T[]`→`ptr` são empacotados (copy-in)
        //     num buffer C próprio e o PONTEIRO substitui o objeto no bloco (o
        //     slot é relido no passo 1). O call do helper clobbera a0-a7, por
        //     isso corre ANTES de carregar os registradores de argumento.
        for (int i = 0; i < n; i++) {
            if (!isArray[i]) continue;
            sb.append("    ld a0, ").append(8 * (n - 1 - i)).append("(sp)\n");
            sb.append("    li a1, ").append(NativeFfiCall.arrayElemSize(arrayElem[i])).append("\n");
            sb.append("    call kof_ffi_pack_array\n");
            sb.append("    sd a0, ").append(8 * (n - 1 - i)).append("(sp)\n");
        }
        // 0) D-MEM030-BORROW-RUNTIME (B-03): cada `Buffer(U8)` INOUT adquire um
        //    borrow gravável exclusivo ANTES de consumir o bloco (o acquire é um
        //    call e clobbera t0..t6; aqui só `sp`/bloco são estado, intactos).
        for (int i = 0; i < n; i++) {
            if (!isBuf[i]) continue;
            sb.append("    ld a0, ").append(8 * (n - 1 - i)).append("(sp)\n");
            sb.append("    call kof_buffer_borrow_acquire\n");
        }
        // 1) t0 = topo E; args de registro por offset SEM popar (o bloco fica
        //    intacto p/ os derramados; String: payload no offset 24, NULL→NULL)
        sb.append("    mv t0, sp\n");
        for (int i = 0; i < n; i++) {
            if (isStruct[i]) {
                // struct INTEGER por valor: ponteiro do objeto Kof → monta cada
                // eightbyte no registrador de destino da sua classe (fatia 4).
                sb.append("    ld t4, ").append(8 * (n - 1 - i)).append("(t0)\n");
                for (int e = 0; e < sOrd[i].length; e++) {
                    if (sOrd[i][e] >= 8) continue;   // derramado: passo 3
                    FfiStructLayout.emitRiscvIntEightbyte(sb, structTypes[i], e,
                            "t4", intRegs[sOrd[i][e]], "t5");
                }
                continue;
            }
            char c = cls[i];
            boolean floatC = isFloatClass(c);
            if (floatC ? ord[i] >= 8 : ord[i] >= 8) continue; // derramado: passo 3
            String dst = floatC ? "t2" : intRegs[ord[i]];
            sb.append("    ld ").append(dst).append(", ").append(8 * (n - 1 - i)).append("(t0)\n");
            if (c == 'S' || isBuf[i]) {
                // String→payload/char* e Buffer(U8)→ponteiro do payload: offset 24
                // do objeto Kof; NULL→NULL (a C recebe o cstr/ptr cru).
                String lbl = ".Lffis" + seq + "_" + i;
                sb.append("    beqz ").append(dst).append(", ").append(lbl).append("\n");
                sb.append("    addi ").append(dst).append(", ").append(dst).append(", 24\n");
                sb.append(lbl).append(":\n");
            }
            // fa0..fa7 (NÃO f0..f7!): a ABI C riscv64 põe args FP nos apelidos
            // fa* = registradores FÍSICOS f10-f17 (ft0/f0 é só o RETORNO) —
            // medido 19/09: glibc riscv64 `exp` lê fa0; f0 passava despercebido.
            // O tradutor aarch normaliza fa0..7→f0..7→d0..d7 (AAPCS64 ✓) — o
            // MESMO texto serve as duas archs.
            if (floatC) {
                sb.append(c == 'f' ? "    fmv.w.x fa" : "    fmv.d.x fa")
                  .append(ord[i]).append(", t2\n");
            }
        }
        // 2) consome o bloco inteiro + reserva o área derramada + alinha 16.
        //    Immediato = 8n − 8ns − 16 ≥ 48 p/ n ≥ 1 (ns ≤ n−8 por classe).
        // Alinhamento via t3 (nunca `and sp,sp,..` direto: no aarch64 `and`
        // rejeita sp como Rn — o tradutor receberia instrução inválida).
        sb.append("    addi t3, t0, ").append(8 * n - 8 * ns - 16).append("\n");
        sb.append("    li t1, -16\n");
        sb.append("    and t3, t3, t1\n");
        sb.append("    mv sp, t3\n");
        // 3) derramados na ordem formal (arg0 → 0(sp) — o 1º stack-arg da C);
        //    valor cru (8 bytes) passa direto p/ o slot da C, float incluso.
        int k = 0;
        for (int i = 0; i < n; i++) {
            if (isStruct[i]) {
                sb.append("    ld t4, ").append(8 * (n - 1 - i)).append("(t0)\n");
                for (int e = 0; e < sOrd[i].length; e++) {
                    if (sOrd[i][e] < 8) continue;
                    FfiStructLayout.emitRiscvIntEightbyte(sb, structTypes[i], e, "t4", "t5", "t6");
                    sb.append("    sd t5, ").append(8 * k++).append("(sp)\n");
                }
                continue;
            }
            char c = cls[i];
            boolean floatC = isFloatClass(c);
            if (!(floatC ? ord[i] >= 8 : ord[i] >= 8)) continue;
            sb.append("    ld t2, ").append(8 * (n - 1 - i)).append("(t0)\n");
            if (c == 'S' || isBuf[i]) {
                String lbl = ".Lffis" + seq + "_" + i;
                sb.append("    beqz t2, ").append(lbl).append("\n");
                sb.append("    addi t2, t2, 24\n");
                sb.append(lbl).append(":\n");
            }
            sb.append("    sd t2, ").append(8 * k++).append("(sp)\n");
        }
        // 4) ponto de restauração (E+8n) salvo ACIMA dos args da C: a callee
        //    toca só [< sp, +8ns); a chamada devolve sp = A (ABI) — o slot é
        //    lido com sp ainda em A.
        sb.append("    addi t2, t0, ").append(8 * n).append("\n");
        sb.append("    sd t2, ").append(8 * ns + 8).append("(sp)\n");
        // 5) call direto (PLT gerado pelo ld; §61: resolve no exec sem dlopen)
        sb.append("    call ").append(NativeFfiCall.symbolOf(kc)).append("\n");
        sb.append("    ld sp, ").append(8 * ns + 8).append("(sp)\n");
        // B-03: o release usa a0 (registrador de retorno!), então ele corre
        // DEPOIS de o retorno já estar preservado na pilha. Após o push do
        // resultado, o bloco [E, E+8n) fica em sp+8; em void, em sp.
        if (structRet) {
            emitRiscvStructReturn(nb, sb, kc);
            emitRiscvBufferReleases(sb, n, isBuf, 8);
            return;
        }
        switch (ret) {
            case 'v':
                emitRiscvBufferReleases(sb, n, isBuf, 0);
                return;
            case 'i': sb.append("    sext.w a0, a0\n"); break; // canonicaliza o Int 32-bit
            case 'j': break;
            // RETORNO FP em fa0 (NÃO ft0!): medido 19/09 — o glibc riscv64
            // deste sysroot devolve double/float em fa0=f10 (a cadeia do
            // `exp` termina em fa0; o strtod do próprio runtime cross já lê
            // fa0 — testes verdes). No aarch64 o tradutor mapeia fa0→f0→d0,
            // que É o registro de retorno AAPCS64 — um texto, duas archs.
            case 'f': sb.append("    fmv.x.w a0, fa0\n"); break;
            case 'd': sb.append("    fmv.x.d a0, fa0\n"); break;
            case 'b': break; // C _Bool: 0/1 em a0 (zext pela ABI) — como o Kof guarda
            case 'S':
                sb.append("    call kof_ffi_from_cstr\n");
                break;
            default:
                emitRiscvBufferReleases(sb, n, isBuf, 0);
                return;
        }
        sb.append("    addi sp, sp, -8\n");
        sb.append("    sd a0, 0(sp)\n");
        emitRiscvBufferReleases(sb, n, isBuf, 8);
    }

    /**
     * D-MEM030-BORROW-RUNTIME (B-03, cross): libera o borrow de cada
     * {@code Buffer(U8)} INOUT. {@code resultOnStack} = 8 quando o valor de
     * retorno já foi empilhado (bloco em {@code sp+8}), 0 em void. O OBJ do
     * buffer foi preservado no bloco (a C não escreve acima do sp de entrada);
     * {@code kof_buffer_borrow_release} é leaf e não toca {@code t0}.
     */
    private static void emitRiscvBufferReleases(StringBuilder sb, int n, boolean[] isBuf,
                                                int resultOnStack) {
        boolean any = false;
        for (int i = 0; i < n; i++) if (isBuf[i]) any = true;
        if (!any) return;
        // Após o restore sp = E e só o push do resultado muda sp (E → E-8), então
        // a base do bloco é sp + resultOnStack.
        sb.append("    addi t0, sp, ").append(resultOnStack).append("\n");
        for (int i = 0; i < n; i++) {
            if (!isBuf[i]) continue;
            sb.append("    ld a0, ").append(8 * (n - 1 - i)).append("(t0)\n");
            sb.append("    call kof_buffer_borrow_release\n");
        }
    }

    /**
     * 3.7 fatia 3: materializa o `record` devolvido por valor no alvo cross
     * (register path, campos INTEGER, &le; 16 B — {@code div_t} de {@code div}).
     * Os words chegam em {@code a0}/{@code a1} ({@code x0}/{@code x1} sob
     * AAPCS64 — o tradutor mapeia) e são SALVOS na pilha antes do
     * {@code kof_alloc} (a alocação clobberaria os registradores de retorno);
     * o objeto Kof é alocado+inicializado e cada campo é extraído do seu word
     * (shift pela largura natural — mesmo packing little-endian nas duas archs).
     * Struct com campo float/HFA ou &gt; 16 B nunca chega aqui (gate FFI001, R6).
     */
    private static void emitRiscvStructReturn(NativeBackend nb, StringBuilder sb, KofCall kc) {
        NativeOpHelpers.Resolved r = NativeOpHelpers.resolveClass(nb, kc.returnType());
        List<Type> fts = new ArrayList<>();
        if (r != null) for (var f : r.layout().fields()) fts.add(f.type());
        Type st = FfiStructLayout.structType(fts);
        AbiLayout.Layout l = FfiStructLayout.layout(AbiLayout.Abi.SYSV_X86_64, st);
        int words = (l.size() + 7) / 8;
        // 1) salva os words de retorno na pilha (kof_alloc clobbera a0-a3)
        sb.append("    addi sp, sp, -").append(8 * words).append("\n");
        for (int e = 0; e < words; e++) {
            sb.append("    sd a").append(e).append(", ").append(8 * e).append("(sp)\n");
        }
        // 2) aloca+inicializa o objeto Kof (a0 = objeto)
        int size = r != null ? r.layout().totalSize()
                             : dev.kof.compiler.ClassLayout.HEADER_SIZE + 64;
        sb.append("    li a0, ").append(size).append("\n");
        sb.append("    call kof_alloc\n");
        if (r != null) {
            String mangled = nb.sanitizeName(r.name());
            sb.append("    mv a1, a0\n");
            sb.append("    li a2, ").append(r.typeId()).append("\n");
            sb.append("    la a3, ").append(mangled).append("_vtable\n");
            sb.append("    mv a0, a1\n");
            sb.append("    mv a1, a2\n");
            sb.append("    mv a2, a3\n");
            sb.append("    call kof_init_object\n");
        }
        sb.append("    mv t3, a0\n");
        // 3) cada campo: do word cru p/ o slot Kof (largura natural)
        List<FfiStructLayout.FieldInfo> fs = FfiStructLayout.fields(st);
        for (FfiStructLayout.FieldInfo f : fs) {
            int cOff = f.cOffset();
            int e = cOff / 8;
            int shift = (cOff - e * 8) * 8;
            int kofOff = 16 + 8 * f.kofSlot();
            sb.append("    ld t0, ").append(8 * e).append("(sp)\n");
            if (shift > 0) sb.append("    srli t0, t0, ").append(shift).append("\n");
            switch (f.scalar().size) {
                case 1 -> sb.append("    andi t0, t0, 255\n");
                case 2 -> sb.append("    slli t0, t0, 48\n    srli t0, t0, 48\n");
                case 4 -> sb.append("    sext.w t0, t0\n");
                default -> { }
            }
            sb.append("    sd t0, ").append(kofOff).append("(t3)\n");
        }
        // 4) remove o stash e empilha o objeto como resultado
        sb.append("    addi sp, sp, ").append(8 * words).append("\n");
        sb.append("    addi sp, sp, -8\n");
        sb.append("    sd t3, 0(sp)\n");
    }

    /**
     * D-MEM-FFI-CROSS-FULL: empacota um array Kof de escalares num buffer C
     * contíguo (copy-in por chamada; o array Kof nunca é mutado pela C). Port
     * do {@code emitX86ArrayPackHelper} para o cross. {@code a0} = objeto array,
     * {@code a1} = tamanho do elemento em bytes; retorno {@code a0} = buffer
     * ({@code kof_alloc}, ponteiro do payload). Layout Kof do array: len em
     * 16(obj), payload em 24. Definido uma vez por programa quando um extern
     * recebe array; o aarch64 o recebe por tradução linha-a-linha (todas as
     * instruções cobertas: ld/lw/sd/mv/add/addi/mul/beqz/j/call/ret).
     */
    static void emitRiscvArrayPackHelper(StringBuilder sb) {
        sb.append("""
                .globl kof_ffi_pack_array
                .type kof_ffi_pack_array, @function
                kof_ffi_pack_array:
                    addi sp, sp, -48
                    sd   ra, 40(sp)
                    sd   s0, 32(sp)
                    sd   s1, 24(sp)
                    sd   s2, 16(sp)
                    sd   s3, 8(sp)
                    mv   s0, a0              # objeto array
                    mv   s2, a1              # tamanho do elemento
                    lw   s1, 16(s0)          # len (32-bit, como o x86)
                    mul  t0, s1, s2          # bytes = len * elemsize
                    bnez t0, .Lfpa_alloc
                    li   t0, 8               # alocação mínima (≠0)
                .Lfpa_alloc:
                    mv   a0, t0
                    call kof_alloc
                    mv   s3, a0              # dst (kof_memcpy avança a0)
                    mul  a2, s1, s2
                    beqz a2, .Lfpa_done
                    mv   a0, s3
                    addi a1, s0, 24          # src = payload do array Kof
                    call kof_memcpy
                .Lfpa_done:
                    mv   a0, s3
                    ld   s3, 8(sp)
                    ld   s2, 16(sp)
                    ld   s1, 24(sp)
                    ld   s0, 32(sp)
                    ld   ra, 40(sp)
                    addi sp, sp, 48
                    ret
                """);
    }

    /** Helper char*→String no cross: strlen + kof_string_from_literal (copia
     *  UTF-8 + NUL-termina — o buffer C nunca e free'd; NULL → 0 = null Kof).
     *  Mesma forma x86; mnemonicos todos cobertos pelo tradutor aarch. */
    static void emitRiscvCstrHelper(StringBuilder sb) {
        sb.append("""
                kof_ffi_from_cstr:
                    beqz a0, .Lffc_null
                    addi sp, sp, -16
                    sd ra, 8(sp)
                    sd a0, 0(sp)
                    mv a1, a0
                    li a2, 0
                .Lffc_scan:
                    lbu a3, 0(a1)
                    beqz a3, .Lffc_got
                    addi a1, a1, 1
                    addi a2, a2, 1
                    j .Lffc_scan
                .Lffc_got:
                    ld a0, 0(sp)
                    mv a1, a2
                    call kof_string_from_literal
                    ld ra, 8(sp)
                    addi sp, sp, 16
                    ret
                .Lffc_null:
                    li a0, 0
                    ret
                """);
    }
}
