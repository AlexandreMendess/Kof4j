package dev.kof.compiler.nat;

/**
 * FLT001 §448 cross (rv/aa), face FLOAT: {@code FloatToDecimal} (Schubfach,
 * P=24, H=9) libc-free no riscv64, herdado pelo aarch64 via
 * {@link NativeAarch64Translator}. Reusa as tabelas/helpers de
 * {@link NativeRiscvSchubfach} ({@code flog*}, {@code g1}, {@code rop_f},
 * {@code format}), espelhando o {@code RuntimeDtoaSchubfachFloat} x86. Com esta
 * face o dtoa cross deixa de tocar {@code snprintf}/{@code strtod} por completo.
 */
final class NativeRiscvSchubfachFloat {

    private NativeRiscvSchubfachFloat() {}

    static String emit() {
        return CORE;
    }

    private static final String CORE = """
.section .text

# ---- kof_schub_rop_f(a0=g, a1=cp) -> a0 : rop(cp g 2^-95) ----
.globl kof_schub_rop_f
kof_schub_rop_f:
    mulhu t1, a0, a1
    srli  t2, t1, 31
    li    t3, 0xFFFFFFFF
    and   t1, t1, t3
    add   t1, t1, t3
    srli  t1, t1, 32
    or    a0, t2, t1
    ret

# ---- kof_schub_to_decimal_f(a0=q, a1=c, a2=dk) -> a0=f, a1=e ----
.globl kof_schub_to_decimal_f
kof_schub_to_decimal_f:
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
    li   t0, 0x800000
    bne  s1, t0, .Ltdf_reg
    li   t0, -149
    beq  s0, t0, .Ltdf_reg
    slli s4, s1, 2
    addi s5, s4, -1
    mv   a0, s0
    call kof_schub_flog10threequarters
    j    .Ltdf_kdone
.Ltdf_reg:
    slli s4, s1, 2
    addi s5, s4, -2
    mv   a0, s0
    call kof_schub_flog10pow2
.Ltdf_kdone:
    mv   s7, a0
    addi s6, s4, 2
    neg  a0, s7
    call kof_schub_flog2pow10
    add  a0, a0, s0
    addi s8, a0, 33
    mv   a0, s7
    call kof_schub_g1
    addi s9, a0, 1
    mv   a0, s9
    sll  a1, s4, s8
    call kof_schub_rop_f
    sd   a0, 0(sp)
    mv   a0, s9
    sll  a1, s5, s8
    call kof_schub_rop_f
    sd   a0, 8(sp)
    mv   a0, s9
    sll  a1, s6, s8
    call kof_schub_rop_f
    sd   a0, 16(sp)
    ld   t0, 0(sp)
    srli t0, t0, 2
    sd   t0, 24(sp)
    li   t1, 100
    blt  t0, t1, .Ltdf_cmp
    li   t2, 10
    divu t3, t0, t2
    mul  t3, t3, t2
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
    beq  t5, t4, .Ltdf_cmp
    beqz t5, .Ltdf_use_tp
    ld   a0, 40(sp)
    j    .Ltdf_retd_k
.Ltdf_use_tp:
    ld   a0, 40(sp)
    addi a0, a0, 10
.Ltdf_retd_k:
    mv   a1, s7
    j    .Ltdf_ret
.Ltdf_cmp:
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
    beq  t2, t3, .Ltdf_final
    beqz t2, .Ltdf_use_t
    ld   a0, 24(sp)
    j    .Ltdf_retd_kdk
.Ltdf_use_t:
    addi a0, t0, 1
.Ltdf_retd_kdk:
    mv   a1, s7
    add  a1, a1, s2
    j    .Ltdf_ret
.Ltdf_final:
    ld   t4, 24(sp)
    addi t1, t4, 1
    add  t2, t4, t1
    slli t2, t2, 1
    ld   t3, 0(sp)
    sub  t3, t3, t2
    bltz t3, .Ltdf_pick_s
    bnez t3, .Ltdf_pick_t
    andi t2, t4, 1
    beqz t2, .Ltdf_pick_s
.Ltdf_pick_t:
    mv   a0, t1
    j    .Ltdf_retd_kdk2
.Ltdf_pick_s:
    mv   a0, t4
.Ltdf_retd_kdk2:
    mv   a1, s7
    add  a1, a1, s2
.Ltdf_ret:
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

# ---- kof_float_to_string(a0=low32 bits) -> a0: String* ----
.globl kof_float_to_string
kof_float_to_string:
    addi sp, sp, -48
    sd   ra, 40(sp)
    sd   s0, 32(sp)
    sd   s1, 24(sp)
    sd   s2, 16(sp)
    slli s0, a0, 32
    srli s0, s0, 32
    srli s1, s0, 31
    srli t0, s0, 23
    andi t0, t0, 0xff
    li   t1, 0xff
    beq  t0, t1, .Lf2s_naninf
    beqz t0, .Lf2s_sub
    li   s2, 150
    sub  s2, s2, t0
    li   t1, 0x7fffff
    and  t2, s0, t1
    li   t1, 0x800000
    or   t2, t2, t1
    blez s2, .Lf2s_dec
    li   t1, 24
    bge  s2, t1, .Lf2s_dec
    srl  t3, t2, s2
    sll  t4, t3, s2
    bne  t4, t2, .Lf2s_dec
    mv   a0, t3
    li   a1, 0
    mv   a2, s1
    li   a3, 9
    call kof_schub_format
    j    .Lf2s_done
.Lf2s_dec:
    neg  a0, s2
    mv   a1, t2
    li   a2, 0
    call kof_schub_to_decimal_f
    mv   a2, s1
    li   a3, 9
    call kof_schub_format
    j    .Lf2s_done
.Lf2s_sub:
    li   t1, 0x7fffff
    and  t2, s0, t1
    beqz t2, .Lf2s_zero
    li   t1, 8
    bgeu t2, t1, .Lf2s_sub_c
    li   t1, 10
    mul  a1, t2, t1
    li   a0, -149
    li   a2, -1
    call kof_schub_to_decimal_f
    j    .Lf2s_after
.Lf2s_sub_c:
    mv   a1, t2
    li   a0, -149
    li   a2, 0
    call kof_schub_to_decimal_f
.Lf2s_after:
    mv   a2, s1
    li   a3, 9
    call kof_schub_format
    j    .Lf2s_done
.Lf2s_zero:
    beqz s1, .Lf2s_zero_p
    la   a0, .Lschub_nzero
    li   a1, 4
    call kof_string_from_literal
    j    .Lf2s_done
.Lf2s_zero_p:
    la   a0, .Lschub_zero
    li   a1, 3
    call kof_string_from_literal
    j    .Lf2s_done
.Lf2s_naninf:
    li   t1, 0x7fffff
    and  t0, s0, t1
    bnez t0, .Lf2s_nan
    srli t0, s0, 31
    bnez t0, .Lf2s_ninf
    la   a0, .Ldtf_str_inf
    li   a1, 8
    call kof_string_from_literal
    j    .Lf2s_done
.Lf2s_ninf:
    la   a0, .Ldtf_str_ninf
    li   a1, 9
    call kof_string_from_literal
    j    .Lf2s_done
.Lf2s_nan:
    la   a0, .Ldtf_str_nan
    li   a1, 3
    call kof_string_from_literal
.Lf2s_done:
    ld   ra, 40(sp)
    ld   s0, 32(sp)
    ld   s1, 24(sp)
    ld   s2, 16(sp)
    addi sp, sp, 48
    ret
""";
}
