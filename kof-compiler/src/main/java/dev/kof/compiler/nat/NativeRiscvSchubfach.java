package dev.kof.compiler.nat;

import dev.kof.compiler.runtime.RuntimeDtoaSchubfach;

/**
 * FLT001 §448 cross (rv/aa): Schubfach libc-free para {@code Double} no
 * riscv64, herdado pelo aarch64 via {@link NativeAarch64Translator}. Espelha o
 * algoritmo do JDK {@code DoubleToDecimal} byte-a-byte (mesmas tabelas
 * {@code .Lschub_g}/`.Lschub_pow10} geradas por
 * {@link RuntimeDtoaSchubfach}), substituindo o antigo loop
 * {@code snprintf("%.*e")}+{@code strtod} — que escolhia o decimal MAIS CURTO
 * e divergia do JVM no subnormal ({@code 5.0E-324} vs {@code 4.9E-324}).
 *
 * <p>ABI cross preservada: {@code kof_double_to_string(a0=bits) -> a0=String*}.
 * {@code to_decimal} devolve {@code a0=f, a1=e} (o x86 devolve rax/rcx). Só
 * instruções que o tradutor aarch64 cobre (a base do §448: {@code mulhu},
 * {@code sltu}, {@code xori} e shifts por registrador entraram junto).
 *
 * <p>Float segue pelo caminho libc da peça B45 (fatia seguinte) — honesto: um
 * programa que imprime Float continua linkando dinâmico; um que imprime só
 * Double linka estático.
 */
final class NativeRiscvSchubfach {

    private NativeRiscvSchubfach() {}

    static String emit() {
        StringBuilder sb = new StringBuilder();
        sb.append("            .section .rodata\n");
        sb.append(".Lschub_zero:  .asciz \"0.0\"\n");
        sb.append(".Lschub_nzero: .asciz \"-0.0\"\n");
        RuntimeDtoaSchubfach.emitTables(sb);
        sb.append(CORE);
        return sb.toString();
    }

    private static final String CORE = """
.section .text

# ---- kof_schub_flog10pow2(a0=e) -> a0 : floor(log10(2^e)) ----
.globl kof_schub_flog10pow2
kof_schub_flog10pow2:
    li   t0, 0x9A209A84FB
    mul  a0, a0, t0
    srai a0, a0, 41
    ret

# ---- kof_schub_flog10threequarters(a0=e) -> a0 ----
.globl kof_schub_flog10threequarters
kof_schub_flog10threequarters:
    li   t0, 0x9A209A84FB
    mul  t0, a0, t0
    li   t1, 0x3FF7F85779
    sub  t0, t0, t1
    srai a0, t0, 41
    ret

# ---- kof_schub_flog2pow10(a0=e) -> a0 ----
.globl kof_schub_flog2pow10
kof_schub_flog2pow10:
    li   t0, 0xD49A784BCD
    mul  a0, a0, t0
    srai a0, a0, 38
    ret

# ---- kof_schub_g1(a0=k) -> a0 ----
.globl kof_schub_g1
kof_schub_g1:
    addi a0, a0, 324
    slli a0, a0, 4
    la   t0, .Lschub_g
    add  t0, t0, a0
    ld   a0, 0(t0)
    ret

# ---- kof_schub_g0(a0=k) -> a0 ----
.globl kof_schub_g0
kof_schub_g0:
    addi a0, a0, 324
    slli a0, a0, 4
    addi a0, a0, 8
    la   t0, .Lschub_g
    add  t0, t0, a0
    ld   a0, 0(t0)
    ret

# ---- kof_schub_pow10(a0=e) -> a0 ----
.globl kof_schub_pow10
kof_schub_pow10:
    slli a0, a0, 3
    la   t0, .Lschub_pow10
    add  t0, t0, a0
    ld   a0, 0(t0)
    ret

# ---- kof_schub_rop(a0=g1,a1=g0,a2=cp) -> a0 ----
.globl kof_schub_rop
kof_schub_rop:
    mul   t0, a1, a2
    mulhu t1, a1, a2
    mul   t2, a0, a2
    mulhu t3, a0, a2
    srli  t2, t2, 1
    add   t2, t2, t1
    srli  t4, t2, 63
    add   t4, t4, t3
    li    t5, 0x7FFFFFFFFFFFFFFF
    and   t2, t2, t5
    add   t2, t2, t5
    srli  t2, t2, 63
    or    a0, t4, t2
    ret

# ---- kof_schub_to_decimal(a0=q,a1=c,a2=dk) -> a0=f, a1=e ----
.globl kof_schub_to_decimal
kof_schub_to_decimal:
    addi sp, sp, -144
    sd   ra, 136(sp)
    sd   s0, 128(sp)
    sd   s1, 120(sp)
    sd   s2, 112(sp)
    sd   s3, 104(sp)
    sd   s4, 96(sp)
    sd   s5, 88(sp)
    sd   s6, 80(sp)
    sd   s7, 72(sp)
    sd   s8, 64(sp)
    sd   s9, 56(sp)
    sd   s10, 48(sp)
    mv   s0, a0
    mv   s1, a1
    mv   s2, a2
    andi s3, s1, 1
    li   t0, 0x10000000000000
    bne  s1, t0, .Ltd_reg
    li   t0, -1074
    beq  s0, t0, .Ltd_reg
    slli s4, s1, 2
    addi s5, s4, -1
    mv   a0, s0
    call kof_schub_flog10threequarters
    j    .Ltd_kdone
.Ltd_reg:
    slli s4, s1, 2
    addi s5, s4, -2
    mv   a0, s0
    call kof_schub_flog10pow2
.Ltd_kdone:
    mv   s7, a0
    addi s6, s4, 2
    neg  a0, s7
    call kof_schub_flog2pow10
    add  a0, a0, s0
    addi s8, a0, 2
    mv   a0, s7
    call kof_schub_g1
    mv   s9, a0
    mv   a0, s7
    call kof_schub_g0
    mv   s10, a0
    mv   a0, s9
    mv   a1, s10
    sll  a2, s4, s8
    call kof_schub_rop
    sd   a0, 0(sp)
    mv   a0, s9
    mv   a1, s10
    sll  a2, s5, s8
    call kof_schub_rop
    sd   a0, 8(sp)
    mv   a0, s9
    mv   a1, s10
    sll  a2, s6, s8
    call kof_schub_rop
    sd   a0, 16(sp)
    ld   t0, 0(sp)
    srli t0, t0, 2
    sd   t0, 24(sp)
    li   t1, 100
    blt  t0, t1, .Ltd_cmp
    li   t2, 0x19999999999999A0
    mulhu t3, t0, t2
    li   t4, 10
    mul  t3, t3, t4
    sd   t3, 40(sp)
    ld   t5, 8(sp)
    add  t5, t5, s3
    ld   t4, 40(sp)
    slli t4, t4, 2
    sltu t5, t4, t5
    xori t5, t5, 1
    ld   t4, 40(sp)
    addi t4, t4, 10
    slli t4, t4, 2
    add  t4, t4, s3
    ld   t6, 16(sp)
    sltu t4, t6, t4
    xori t4, t4, 1
    beq  t5, t4, .Ltd_cmp
    beqz t5, .Ltd_use_tp
    ld   a0, 40(sp)
    j    .Ltd_retd_k
.Ltd_use_tp:
    ld   a0, 40(sp)
    addi a0, a0, 10
.Ltd_retd_k:
    mv   a1, s7
    j    .Ltd_ret
.Ltd_cmp:
    ld   t0, 24(sp)
    addi t1, t0, 1
    ld   t2, 8(sp)
    add  t2, t2, s3
    slli t3, t0, 2
    sltu t2, t3, t2
    xori t2, t2, 1
    slli t3, t1, 2
    add  t3, t3, s3
    ld   t4, 16(sp)
    sltu t3, t4, t3
    xori t3, t3, 1
    beq  t2, t3, .Ltd_final
    beqz t2, .Ltd_use_t
    ld   a0, 24(sp)
    j    .Ltd_retd_kdk
.Ltd_use_t:
    addi a0, t0, 1
.Ltd_retd_kdk:
    mv   a1, s7
    add  a1, a1, s2
    j    .Ltd_ret
.Ltd_final:
    ld   t4, 24(sp)
    addi t1, t4, 1
    add  t2, t4, t1
    slli t2, t2, 1
    ld   t3, 0(sp)
    sub  t3, t3, t2
    bltz t3, .Ltd_pick_s
    bnez t3, .Ltd_pick_t
    andi t2, t4, 1
    beqz t2, .Ltd_pick_s
.Ltd_pick_t:
    mv   a0, t1
    j    .Ltd_retd_kdk2
.Ltd_pick_s:
    mv   a0, t4
.Ltd_retd_kdk2:
    mv   a1, s7
    add  a1, a1, s2
.Ltd_ret:
    ld   ra, 136(sp)
    ld   s0, 128(sp)
    ld   s1, 120(sp)
    ld   s2, 112(sp)
    ld   s3, 104(sp)
    ld   s4, 96(sp)
    ld   s5, 88(sp)
    ld   s6, 80(sp)
    ld   s7, 72(sp)
    ld   s8, 64(sp)
    ld   s9, 56(sp)
    ld   s10, 48(sp)
    addi sp, sp, 144
    ret

            """ + NativeRiscvSchubfachFormat.CORE;
}
