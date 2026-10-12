package dev.kof.compiler.nat;

/** M1 unidade-4: trampolins de upcall (callback FFI) no cross riscv64/aarch64 —
 * extraídos de {@code NativeFfiCallRiscv} (gate ≤500). A face escalar salva `ra` e
 * remonta o invoke Kof; a face `S` (String-arg) monta um frame que salva as fontes
 * e chama `kof_ffi_from_cstr`. O aarch64 herda via o tradutor linha-a-linha. */
final class NativeFfiCallRiscvCallback {

    private NativeFfiCallRiscvCallback() {}

    /**
     * M1 callbacks no cross (riscv64/aarch64): trampolim de upcall de um
     * descritor {@code desc}. O C chama com os args na ABI cruzada (int a0..,
     * FP fa0..). O trampolim salva `ra` (senao o `ret` salta para lixo — o hang
     * medido), remonta para o `invoke` Kof (soft-float-safe: a0=closure, args em
     * a1..) e chama vtable[0]. Só-inteiros/FP escalar (mesmo do x86).
     */
    static void emitRiscvCallbackTrampoline(StringBuilder sb, String desc) {
        if (desc.indexOf('S') >= 0) { emitRiscvCallbackTrampolineStr(sb, desc); return; }
        int arity = desc.length() - 1;
        char rc = desc.charAt(0);
        sb.append("kof_cb_tramp_").append(desc).append(":\n");
        sb.append("    addi sp, sp, -16\n");
        sb.append("    sd ra, 8(sp)\n");
        int ip = 0, fp = 0;
        String[] src = new String[arity];
        boolean[] isFp = new boolean[arity];
        for (int i = 0; i < arity; i++) {
            char c = desc.charAt(i + 1);
            boolean f = (c == 'f' || c == 'd');
            isFp[i] = f;
            src[i] = f ? "fa" + (fp++) : "a" + (ip++);
        }
        // Empilha os args (do último p/ o 1º; FP via bits crus em t0).
        for (int i = arity - 1; i >= 0; i--) {
            if (isFp[i]) {
                sb.append(desc.charAt(i + 1) == 'f'
                        ? "    fmv.x.w t0, " + src[i] + "\n"
                        : "    fmv.x.d t0, " + src[i] + "\n");
                sb.append("    addi sp, sp, -8\n    sd t0, 0(sp)\n");
            } else {
                sb.append("    addi sp, sp, -8\n    sd ").append(src[i]).append(", 0(sp)\n");
            }
        }
        // Desempilha em a1..a7 (a0 = closure).
        for (int k = 0; k < arity; k++) {
            sb.append("    ld a").append(k + 1).append(", 0(sp)\n    addi sp, sp, 8\n");
        }
        sb.append("    la t0, kof_cb_closure_").append(desc).append("\n");
        sb.append("    ld a0, 0(t0)\n");
        sb.append("    ld t0, 8(a0)\n");
        sb.append("    ld t0, 0(t0)\n");
        sb.append("    jalr t0\n");
        sb.append("    ld ra, 8(sp)\n");
        sb.append("    addi sp, sp, 16\n");
        if (rc == 'f') sb.append("    fmv.w.x fa0, a0\n");
        else if (rc == 'd') sb.append("    fmv.d.x fa0, a0\n");
        sb.append("    ret\n");
    }

    /**
     * Trampolim cross (riscv64/aarch64) com ARGUMENTO {@code String} (M1
     * unidade-4 fatia 5): um arg String chega do C como {@code char*} e o
     * {@code invoke} Kof espera um objeto String, então cada arg {@code S} passa
     * por {@code kof_ffi_from_cstr} ANTES de remontar a chamada. Como um
     * {@code call} no meio do marshal clobberaria os registradores-fonte ainda
     * não salvos, o frame salva TODAS as fontes primeiro (offset 0..), guarda
     * {@code ra} e converte os {@code S}; a pilha fica 16-byte alinhada para o
     * {@code kof_alloc}/{@code kof_memcpy} dentro do helper. Restrito a arity ≤ 7
     * e args só em registradores (a1..a7) — sem args na pilha (gate).
     */
    static void emitRiscvCallbackTrampolineStr(StringBuilder sb, String desc) {
        int arity = desc.length() - 1;
        char rc = desc.charAt(0);
        int ip = 0, fp = 0;
        String[] src = new String[arity];
        boolean[] isFp = new boolean[arity];
        for (int i = 0; i < arity; i++) {
            char c = desc.charAt(i + 1);
            boolean f = (c == 'f' || c == 'd');
            isFp[i] = f;
            src[i] = f ? "fa" + (fp++) : "a" + (ip++);
        }
        int raOff = 8 * arity;
        int frame = 8 * arity + 16;
        if (frame % 16 != 0) frame += 8;   // 16-byte aligned for kof_alloc/kof_memcpy
        sb.append("kof_cb_tramp_").append(desc).append(":\n");
        sb.append("    addi sp, sp, -").append(frame).append("\n");
        sb.append("    sd ra, ").append(raOff).append("(sp)\n");
        // 1) Salva TODAS as fontes do C no frame (FP com os bits crus).
        for (int i = 0; i < arity; i++) {
            if (isFp[i]) {
                sb.append(desc.charAt(i + 1) == 'f'
                        ? "    fmv.x.w t0, " + src[i] + "\n"
                        : "    fmv.x.d t0, " + src[i] + "\n");
                sb.append("    sd t0, ").append(8 * i).append("(sp)\n");
            } else {
                sb.append("    sd ").append(src[i]).append(", ").append(8 * i).append("(sp)\n");
            }
        }
        // 2) Converte cada arg `S`: char* → String (fontes já salvas acima).
        for (int i = 0; i < arity; i++) {
            if (desc.charAt(i + 1) == 'S') {
                sb.append("    ld a0, ").append(8 * i).append("(sp)\n");
                sb.append("    call kof_ffi_from_cstr\n");
                sb.append("    sd a0, ").append(8 * i).append("(sp)\n");
            }
        }
        // 3) Remonta o invoke: a0=closure, args em a1.. (bits crus).
        sb.append("    la t0, kof_cb_closure_").append(desc).append("\n");
        sb.append("    ld a0, 0(t0)\n");
        for (int k = 0; k < arity; k++) {
            sb.append("    ld a").append(k + 1).append(", ").append(8 * k).append("(sp)\n");
        }
        sb.append("    ld t0, 8(a0)\n");
        sb.append("    ld t0, 0(t0)\n");
        sb.append("    jalr t0\n");
        sb.append("    ld ra, ").append(raOff).append("(sp)\n");
        sb.append("    addi sp, sp, ").append(frame).append("\n");
        if (rc == 'f') sb.append("    fmv.w.x fa0, a0\n");
        else if (rc == 'd') sb.append("    fmv.d.x fa0, a0\n");
        sb.append("    ret\n");
    }

    /** O global da closure (uma vez por programa). */
    static void emitRiscvCallbackClosure(StringBuilder sb, String desc) {
        sb.append(".section .bss\n");
        sb.append(".align 3\n");
        sb.append("kof_cb_closure_").append(desc).append(":\n");
        sb.append(".quad 0\n");
        sb.append(".section .text\n");
}
}
