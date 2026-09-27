package dev.kof.compiler.nat;

/**
 * FLT001 §448 cross (rv/aa): formatação Schubfach (kof_schub_format) e o driver
 * kof_double_to_string, separados de {@link NativeRiscvSchubfach} para manter
 * cada classe ≤500 (regra 7: o nome diz a responsabilidade — aqui, formatar e
 * despachar o Double). Mesma peça B45: os rótulos `kof_schub_*` resolvem no
 * arquivo montado concatenado.
 */
final class NativeRiscvSchubfachFormat {

    private NativeRiscvSchubfachFormat() {}

    static final String CORE = """
# ---- kof_schub_format(a0=f>0, a1=e, a2=sign, a3=H) -> a0: String* ----
.globl kof_schub_format
kof_schub_format:
    addi sp, sp, -320
    sd   ra, 312(sp)
    sd   s0, 304(sp)
    sd   s1, 296(sp)
    sd   s2, 288(sp)
    sd   s3, 280(sp)
    sd   s4, 272(sp)
    sd   s5, 264(sp)
    sd   s6, 256(sp)
    mv   s0, a0
    mv   s1, a1
    mv   s2, a2
    mv   s3, a3
    # bitlen = 64 - clz(f): loop conta os bits significativos
    mv   t0, s0
    li   t1, -1
.Lschubf_clz:
    addi t1, t1, 1
    srli t0, t0, 1
    bnez t0, .Lschubf_clz
    addi a0, t1, 1
    call kof_schub_flog10pow2
    mv   s4, a0
    mv   a0, s4
    call kof_schub_pow10
    bltu s0, a0, .Lschubf_len_ok
    addi s4, s4, 1
.Lschubf_len_ok:
    sub  a0, s3, s4
    call kof_schub_pow10
    mul  s0, s0, a0
    add  s1, s1, s4
    addi t0, sp, 248
    mv   t1, s0
    mv   t2, s3
    li   t3, 10
.Lschubf_dig:
    remu t4, t1, t3
    addi t4, t4, 48
    addi t0, t0, -1
    sb   t4, 0(t0)
    divu t1, t1, t3
    addi t2, t2, -1
    bnez t2, .Lschubf_dig
    addi s5, sp, 0
    li   s6, 0
    beqz s2, .Lschubf_nosign
    li   t1, 45
    sb   t1, 0(s5)
    addi s6, s6, 1
.Lschubf_nosign:
    blez s1, .Lschubf_le0
    li   t1, 7
    bgt  s1, t1, .Lschubf_sci
    mv   t2, s1
.Lschubf_p1:
    lbu  t3, 0(t0)
    add  t4, s5, s6
    sb   t3, 0(t4)
    addi t0, t0, 1
    addi s6, s6, 1
    addi t2, t2, -1
    bnez t2, .Lschubf_p1
    li   t3, 46
    add  t4, s5, s6
    sb   t3, 0(t4)
    addi s6, s6, 1
    sub  t2, s3, s1
.Lschubf_p2:
    lbu  t3, 0(t0)
    add  t4, s5, s6
    sb   t3, 0(t4)
    addi t0, t0, 1
    addi s6, s6, 1
    addi t2, t2, -1
    bnez t2, .Lschubf_p2
    j    .Lschubf_strip
.Lschubf_le0:
    li   t1, -3
    bgt  s1, t1, .Lschubf_le0b
    j    .Lschubf_sci
.Lschubf_le0b:
    li   t3, 48
    add  t4, s5, s6
    sb   t3, 0(t4)
    addi s6, s6, 1
    li   t3, 46
    add  t4, s5, s6
    sb   t3, 0(t4)
    addi s6, s6, 1
    neg  t2, s1
    beqz t2, .Lschubf_zskip
.Lschubf_z0:
    li   t3, 48
    add  t4, s5, s6
    sb   t3, 0(t4)
    addi s6, s6, 1
    addi t2, t2, -1
    bnez t2, .Lschubf_z0
.Lschubf_zskip:
    mv   t2, s3
.Lschubf_z1:
    lbu  t3, 0(t0)
    add  t4, s5, s6
    sb   t3, 0(t4)
    addi t0, t0, 1
    addi s6, s6, 1
    addi t2, t2, -1
    bnez t2, .Lschubf_z1
    j    .Lschubf_strip
.Lschubf_sci:
    lbu  t3, 0(t0)
    add  t4, s5, s6
    sb   t3, 0(t4)
    addi t0, t0, 1
    addi s6, s6, 1
    li   t3, 46
    add  t4, s5, s6
    sb   t3, 0(t4)
    addi s6, s6, 1
    addi t2, s3, -1
.Lschubf_s1:
    lbu  t3, 0(t0)
    add  t4, s5, s6
    sb   t3, 0(t4)
    addi t0, t0, 1
    addi s6, s6, 1
    addi t2, t2, -1
    bnez t2, .Lschubf_s1
.Lschubf_strip:
    beqz s6, .Lschubf_strip_done
    add  t4, s5, s6
    lbu  t3, -1(t4)
    li   t5, 48
    bne  t3, t5, .Lschubf_strip_done
    li   t1, 1
    ble  s6, t1, .Lschubf_strip_dec
    add  t4, s5, s6
    lbu  t3, -2(t4)
    li   t5, 46
    beq  t3, t5, .Lschubf_strip_done
.Lschubf_strip_dec:
    addi s6, s6, -1
    j    .Lschubf_strip
.Lschubf_strip_done:
    li   t1, 7
    bgt  s1, t1, .Lschubf_exp
    blez s1, .Lschubf_le0c
    j    .Lschubf_emit
.Lschubf_le0c:
    li   t1, -3
    bgt  s1, t1, .Lschubf_emit
.Lschubf_exp:
    li   t3, 69
    add  t4, s5, s6
    sb   t3, 0(t4)
    addi s6, s6, 1
    addi t2, s1, -1
    bgez t2, .Lschubf_exp_p
    li   t3, 45
    add  t4, s5, s6
    sb   t3, 0(t4)
    addi s6, s6, 1
    neg  t2, t2
.Lschubf_exp_p:
    addi t0, sp, 128
    li   t5, 0
    li   t6, 10
    beqz t2, .Lschubf_ezero
.Lschubf_ediv:
    remu t3, t2, t6
    divu t2, t2, t6
    addi t3, t3, 48
    add  t4, t0, t5
    sb   t3, 0(t4)
    addi t5, t5, 1
    bnez t2, .Lschubf_ediv
    j    .Lschubf_erev
.Lschubf_ezero:
    li   t3, 48
    sb   t3, 0(t0)
    li   t5, 1
.Lschubf_erev:
    addi t5, t5, -1
.Lschubf_erev1:
    add  t4, t0, t5
    lbu  t3, 0(t4)
    add  t4, s5, s6
    sb   t3, 0(t4)
    addi s6, s6, 1
    addi t5, t5, -1
    bgez t5, .Lschubf_erev1
.Lschubf_emit:
    mv   a0, s5
    mv   a1, s6
    call kof_string_from_literal
    ld   ra, 312(sp)
    ld   s0, 304(sp)
    ld   s1, 296(sp)
    ld   s2, 288(sp)
    ld   s3, 280(sp)
    ld   s4, 272(sp)
    ld   s5, 264(sp)
    ld   s6, 256(sp)
    addi sp, sp, 320
    ret

# ---- kof_double_to_string(a0=bits) -> a0: String* ----
.globl kof_double_to_string
kof_double_to_string:
    addi sp, sp, -48
    sd   ra, 40(sp)
    sd   s0, 32(sp)
    sd   s1, 24(sp)
    sd   s2, 16(sp)
    mv   s0, a0
    srli s1, s0, 63
    srli t0, s0, 52
    andi t0, t0, 0x7ff
    li   t1, 0x7ff
    beq  t0, t1, .Ld2s_naninf
    beqz t0, .Ld2s_sub
    li   s2, 1075
    sub  s2, s2, t0
    li   t1, 0xFFFFFFFFFFFFF
    and  t2, s0, t1
    li   t1, 0x10000000000000
    or   t2, t2, t1
    blez s2, .Ld2s_dec
    li   t1, 53
    bge  s2, t1, .Ld2s_dec
    srl  t3, t2, s2
    sll  t4, t3, s2
    bne  t4, t2, .Ld2s_dec
    mv   a0, t3
    li   a1, 0
    mv   a2, s1
    li   a3, 17
    call kof_schub_format
    j    .Ld2s_done
.Ld2s_dec:
    neg  a0, s2
    mv   a1, t2
    li   a2, 0
    call kof_schub_to_decimal
    mv   a2, s1
    li   a3, 17
    call kof_schub_format
    j    .Ld2s_done
.Ld2s_sub:
    li   t1, 0xFFFFFFFFFFFFF
    and  t2, s0, t1
    beqz t2, .Ld2s_zero
    li   t1, 3
    bgeu t2, t1, .Ld2s_sub_c
    li   t1, 10
    mul  a1, t2, t1
    li   a0, -1074
    li   a2, -1
    call kof_schub_to_decimal
    j    .Ld2s_after
.Ld2s_sub_c:
    mv   a1, t2
    li   a0, -1074
    li   a2, 0
    call kof_schub_to_decimal
.Ld2s_after:
    mv   a2, s1
    li   a3, 17
    call kof_schub_format
    j    .Ld2s_done
.Ld2s_zero:
    beqz s1, .Ld2s_zero_p
    la   a0, .Lschub_nzero
    li   a1, 4
    call kof_string_from_literal
    j    .Ld2s_done
.Ld2s_zero_p:
    la   a0, .Lschub_zero
    li   a1, 3
    call kof_string_from_literal
    j    .Ld2s_done
.Ld2s_naninf:
    slli t0, s0, 12
    bnez t0, .Ld2s_nan
    bltz s0, .Ld2s_ninf
    la   a0, .Ldtf_str_inf
    li   a1, 8
    call kof_string_from_literal
    j    .Ld2s_done
.Ld2s_ninf:
    la   a0, .Ldtf_str_ninf
    li   a1, 9
    call kof_string_from_literal
    j    .Ld2s_done
.Ld2s_nan:
    la   a0, .Ldtf_str_nan
    li   a1, 3
    call kof_string_from_literal
.Ld2s_done:
    ld   ra, 40(sp)
    ld   s0, 32(sp)
    ld   s1, 24(sp)
    ld   s2, 16(sp)
    addi sp, sp, 48
    ret
""";
}
