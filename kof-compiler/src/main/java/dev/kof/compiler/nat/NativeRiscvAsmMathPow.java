package dev.kof.compiler.nat;

// D-FULL-PARITY-050 row 10 (27/09, D-DECISION-BATCH-2709B #3): math.pow no
// cross — shim p/ a libm (pow@PLT), ligado POR USO (-lm em NativeCrossLink,
// quando o texto PODADO contem `call pow`). Peca PROPRIA (nao B32) para a poda
// manter libm-free quem nunca chama pow (B32 = sqrt/lerp/percentage/... sem
// libm). aarch64 herda via NativeAarch64Translator.
//
// Convencao riscv: args em a0/a1 (bits IEEE 64), retorno em a0 (o caller faz
// pushRiscv a0). A pilha de operandos (sp) tem paridade imprevisivel (1 push =
// 8B): auto-alinha como o shim x86 (andq $-16/%rbx), aqui por um scratch antes
// do `call` — pow e callee-saved-safe e nao toca t0/t1/t2 (caller-saved).
public final class NativeRiscvAsmMathPow {

    private NativeRiscvAsmMathPow() {}

    static String RISCV_ASM_MATH_POW = """

            .section .text

            # kof_math_pow(a0=base, a1=exp) -> a0=bits de pow(base,exp) (libm)
            .globl kof_math_pow
            kof_math_pow:
                fmv.d.x fa0, a0
                fmv.d.x fa1, a1
                mv   t0, sp
                mv   t1, sp
                andi t1, t1, -16
                addi t1, t1, -16
                mv   sp, t1
                sd   ra, 8(sp)
                sd   t0, 0(sp)
                call pow
                fmv.x.d a0, fa0
                ld   ra, 8(sp)
                ld   t2, 0(sp)
                mv   sp, t2
                ret
            """;
}
