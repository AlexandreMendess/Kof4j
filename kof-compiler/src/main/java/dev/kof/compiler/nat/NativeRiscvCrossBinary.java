package dev.kof.compiler.nat;

import dev.kof.compiler.KofBinary;
import dev.kof.compiler.KofBinaryOp;
import dev.kof.compiler.Type;

/**
 * Operações binárias do backend cross riscv64 (extraído de
 * {@code NativeRiscvCrossOps} para manter a classe abaixo de 600 linhas,
 * REFACTOR-500 — o §698 empurrou a original para 619). O aarch64 herda via
 * tradução linha-a-linha do texto riscv.
 */
final class NativeRiscvCrossBinary {

    private final NativeBackend nb;

    private final NativeRiscvCrossEmit other;

    NativeRiscvCrossBinary(NativeBackend nb, NativeRiscvCrossEmit other) { this.nb = nb; this.other = other; }

    void emit(StringBuilder sb, KofBinary kb) {

        // b = topo, a = abaixo; resultado = a OP b
        Type opTy = kb.operandType();
        boolean isFloat = NativeTypeKinds.isFloatType(opTy);
        boolean isDouble = NativeTypeKinds.isDoubleType(opTy);
        if (isFloat || isDouble) {
            // §146 cross (12/09, #101): MOD de Float/Double variável caía no
            // `default` vazio e devolvia o dividendo (mesma raiz do x86).
            // Double: fmod via kof_double_mod (B40 — a - trunc(a/b)*b, NaN p/
            // 0/Inf/NaN e |q|>=2^63, paridade JVM). Float: promove p/ double,
            // chama o MESMO helper, trunca de volta (fcvt.s.d; o caller
            // espera bits de float no push).
            if (kb.op() == KofBinaryOp.MOD) {
                String s = isFloat ? "s" : "d";
                sb.append("    pop t0\n    fmv.").append(s).append(".x f1, t0\n");
                sb.append("    pop t1\n    fmv.").append(s).append(".x f0, t1\n");
                if (isFloat) sb.append("    fcvt.d.s f0, f0\n    fcvt.d.s f1, f1\n");
                sb.append("    fmv.x.d a0, f0\n    fmv.x.d a1, f1\n");
                sb.append("    call kof_double_mod\n");
                sb.append("    fmv.d.x f0, a0\n");
                if (isFloat) sb.append("    fcvt.s.d f0, f0\n");
                sb.append("    fmv.x.").append(s).append(" t0, f0\n");
                other.pushRiscv(sb, "t0");
                return;
            }
            String s = isFloat ? "s" : "d";
            sb.append("    pop t0\n    fmv.").append(s).append(".x f1, t0\n");
            sb.append("    pop t1\n    fmv.").append(s).append(".x f0, t1\n");
            switch (kb.op()) {
                case ADD -> sb.append("    fadd.").append(s).append(" f0, f0, f1\n");
                case SUB -> sb.append("    fsub.").append(s).append(" f0, f0, f1\n");
                case MUL -> sb.append("    fmul.").append(s).append(" f0, f0, f1\n");
                case DIV -> sb.append("    fdiv.").append(s).append(" f0, f0, f1\n");
                case EQ -> { sb.append("    feq.").append(s).append(" t1, f0, f1\n    mv t0, t1\n"); }
                // NE = NOT(EQ): feq dá 0 p/ NaN (IEEE) e seqz inverte — o
                // antigo fle+snez dizia NaN != NaN falso (divergia do x86/
                // JVM/JS = true; achado na prova MATH001 11/09).
                case NE -> { sb.append("    feq.").append(s).append(" t1, f0, f1\n    seqz t0, t1\n"); }
                case LT -> { sb.append("    flt.").append(s).append(" t0, f0, f1\n"); }
                case LE -> { sb.append("    fle.").append(s).append(" t0, f0, f1\n"); }
                case GT -> { sb.append("    fgt.").append(s).append(" t0, f0, f1\n"); }
                case GE -> { sb.append("    fge.").append(s).append(" t0, f0, f1\n"); }
                default -> { }
            }
            if (kb.op() == KofBinaryOp.EQ || kb.op() == KofBinaryOp.NE) {
                other.pushRiscv(sb, "t0");
            } else if (kb.op() == KofBinaryOp.LT || kb.op() == KofBinaryOp.LE
                    || kb.op() == KofBinaryOp.GT || kb.op() == KofBinaryOp.GE) {
                other.pushRiscv(sb, "t0");
            } else {
                sb.append("    fmv.x.").append(s).append(" t0, f0\n");
                other.pushRiscv(sb, "t0");
            }
            return;
        }
        sb.append("    pop t0\n");   // b
        sb.append("    pop t1\n");   // a
        boolean int32 = NativeTypeKinds.isInt32Type(opTy);
        switch (kb.op()) {
            case ADD -> sb.append("    add t1, t1, t0\n");
            case SUB -> sb.append("    sub t1, t1, t0\n");
            case MUL -> sb.append("    mul t1, t1, t0\n");
            // §699: divisor de RUNTIME zero — o `div`/`rem` do riscv NÃO
            // levanta exceção (devolve o "indefinido" -1, ou 0 no aarch64),
            // então o `try`/`catch` do Kof nunca disparava e o valor era um
            // lixo silencioso. Mesma guarda do x86 (§693): checa ANTES e
            // desvia para o `kof_throw_string`, virando um throw capturável.
            case DIV -> {
                String sfx = divZeroLabel();
                emitDivZeroGuard(sb, sfx);
                sb.append("    div t1, t1, t0\n");
                emitDivZeroGuardEnd(sb, sfx);
            }
            case MOD -> {
                String sfx = divZeroLabel();
                emitDivZeroGuard(sb, sfx);
                sb.append("    rem t1, t1, t0\n");
                emitDivZeroGuardEnd(sb, sfx);
            }
            case EQ -> { sb.append("    sub t2, t1, t0\n"); sb.append("    seqz t1, t2\n"); }
            case NE -> { sb.append("    sub t2, t1, t0\n"); sb.append("    snez t1, t2\n"); }
            case LT -> sb.append("    slt t1, t1, t0\n");
            // sle/sge NÃO existem na ISA riscv — só slt. a<=b = !(b<a);
            // a>=b = !(a<b). seqz já é usado por EQ/NE (tradutor conhece).
            case LE -> { sb.append("    slt t2, t0, t1\n"); sb.append("    seqz t1, t2\n"); }
            case GT -> sb.append("    slt t1, t0, t1\n");
            case GE -> { sb.append("    slt t2, t1, t0\n"); sb.append("    seqz t1, t2\n"); }
            case AND -> sb.append("    and t1, t1, t0\n");
            case OR -> sb.append("    or t1, t1, t0\n");
            case XOR -> sb.append("    xor t1, t1, t0\n");
            // §698: os shifts de Int precisam da MESMA semântica do JVM
            // (32 bits, contagem mod 32). O riscv usa 64 bits e conta mod 64,
            // então sem isto `1 << 31` dava 2147483648 (vs -2147483648),
            // `1 << 33` dava 2^33 (vs 2) e `-5 >>> 1` vazava um resultado de
            // 64 bits. `andi` mascara a contagem; o `sext.w` do fim (abaixo)
            // trunca o resultado.
            case SHL -> {
                if (int32) sb.append("    andi t0, t0, 31\n");
                sb.append("    sll t1, t1, t0\n");
            }
            case SHR -> {
                if (int32) sb.append("    andi t0, t0, 31\n");
                sb.append("    sra t1, t1, t0\n");
            }
            case USHR -> {
                if (int32) {
                    sb.append("    andi t0, t0, 31\n");
                    // zero-extend a metade baixa ANTES: o `srl` sobre um valor
                    // sign-extended deslocaria os 64 bits. Com o zero-extend o
                    // resultado é o `iushr` de 32 bits e o `sext.w` final o
                    // reinterpreta como signed (contagem 0 preserva o valor).
                    sb.append("    slli t1, t1, 32\n");
                    sb.append("    srli t1, t1, 32\n");
                }
                sb.append("    srl t1, t1, t0\n");
            }
        }
        if (int32 && (kb.op() == KofBinaryOp.ADD || kb.op() == KofBinaryOp.SUB || kb.op() == KofBinaryOp.MUL
                || kb.op() == KofBinaryOp.SHL || kb.op() == KofBinaryOp.SHR || kb.op() == KofBinaryOp.USHR)) {
            sb.append("    sext.w t1, t1\n");
        }
        other.pushRiscv(sb, "t1");
    
}

    /** §699: rótulo único por emissão (dois `div` no MESMO método gerariam
     *  labels duplicados e o GNU as falharia — lição do §181). */
    private static final java.util.concurrent.atomic.AtomicLong DIVZ_SEQ =
            new java.util.concurrent.atomic.AtomicLong();

    private static String divZeroLabel() {
        return Long.toString(DIVZ_SEQ.incrementAndGet());
    }

    private void emitDivZeroGuard(StringBuilder sb, String sfx) {
        sb.append("    beqz t0, .Ldivz_").append(sfx).append("\n");
    }

    private void emitDivZeroGuardEnd(StringBuilder sb, String sfx) {
        sb.append("    j .Ldivz_done_").append(sfx).append("\n");
        sb.append(".Ldivz_").append(sfx).append(":\n");
        // §693: a mensagem vive num `.pushsection .rodata` NO PONTO DE USO
        // (padrão provado no x86). Um `internString` aqui é TARDE DEMAIS — a
        // seção de dados do backend já foi escrita quando os métodos são
        // emitidos, então o label ficava sem definição (undefined reference).
        sb.append("    la a0, .Ldivz_msg_").append(sfx).append("\n");
        sb.append("    li a1, 9\n");
        sb.append("    call kof_string_from_literal\n");
        sb.append("    call kof_throw_string\n");
        sb.append(".Ldivz_done_").append(sfx).append(":\n");
        sb.append(".pushsection .rodata\n");
        sb.append(".Ldivz_msg_").append(sfx).append(": .asciz \"/ by zero\"\n");
        sb.append(".popsection\n");
    }
}
