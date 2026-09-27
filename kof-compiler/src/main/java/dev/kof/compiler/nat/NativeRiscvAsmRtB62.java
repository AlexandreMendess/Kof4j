package dev.kof.compiler.nat;

// S5.1 (db-parity-plan, gaps-db lane, 23/09): primeiras primitivas do wire
// MySQL no cross — SHA1 (curto, len<56) + bswap. Port de RuntimeDb1
// (`kof_sec_sha1_block`/`kof_sec_sha1_internal`) do x86 para riscv64; aarch64
// herda via tradutor. Sem isto o handshake/auth (`mysql_native_password`) do
// S5.1 nao existe no cross — era o bloqueio medido (kof.security recusa no
// cross por SECN000, entao a prova vem por harness asm, nao por superficie
// Kof).
//
// Contrato riscv (mirror do x86):
//   kof_bswap32(a0) -> a0        (zero-extend 32 bits)
//   kof_bswap64(a0) -> a0
//   kof_sec_sha1_block(a0=h5 ptr, a1=bloco 64B) — escreve h in-place
//   kof_sec_sha1_internal(a0=out20, a1=src, a2=len) — digere < 56 bytes
// Numeros sao mantidos em registradores callee-saved (s0..s8); NUNCA s10
// (x16 caller-saved no aarch64 — ver RtB55). Rotacoes sintetizadas por
// slli/srli/or (nao ha `rol` no base RV64G).
public final class NativeRiscvAsmRtB62 {

    private NativeRiscvAsmRtB62() {}

    static String RISCV_RUNTIME_ASM_B_62 = """
            .section .text
            # ---------------------------------------------------------------
            # kof_bswap32(a0) -> a0 (32-bit, zero-extended)
            # ---------------------------------------------------------------
            .globl kof_bswap32
            .type kof_bswap32, @function
            kof_bswap32:
                andi t0, a0, 0xff
                slli t0, t0, 24
                srli t1, a0, 8
                andi t1, t1, 0xff
                slli t1, t1, 16
                or   t0, t0, t1
                srli t1, a0, 16
                andi t1, t1, 0xff
                slli t1, t1, 8
                or   t0, t0, t1
                srli t1, a0, 24
                andi t1, t1, 0xff
                or   a0, t0, t1
                ret

            # ---------------------------------------------------------------
            # kof_bswap64(a0) -> a0
            # ---------------------------------------------------------------
            .globl kof_bswap64
            .type kof_bswap64, @function
            kof_bswap64:
                li   t2, 0xff
                and  t0, a0, t2
                slli t0, t0, 56
                srli t1, a0, 8
                and  t1, t1, t2
                slli t1, t1, 48
                or   t0, t0, t1
                srli t1, a0, 16
                and  t1, t1, t2
                slli t1, t1, 40
                or   t0, t0, t1
                srli t1, a0, 24
                and  t1, t1, t2
                slli t1, t1, 32
                or   t0, t0, t1
                srli t1, a0, 32
                and  t1, t1, t2
                slli t1, t1, 24
                or   t0, t0, t1
                srli t1, a0, 40
                and  t1, t1, t2
                slli t1, t1, 16
                or   t0, t0, t1
                srli t1, a0, 48
                and  t1, t1, t2
                slli t1, t1, 8
                or   t0, t0, t1
                srli t1, a0, 56
                and  t1, t1, t2
                or   a0, t0, t1
                ret

            # ---------------------------------------------------------------
            # kof_sec_sha1_block(a0=h[5] ptr LE, a1=bloco 64B)
            # w[80] em sp+0..319; h de trabalho em sp+320..339. Frame 352. Folha (sem call).
            # a2..a7/t0..t6 sao caller-saved — livres aqui (nao ha call).
            # ---------------------------------------------------------------
            .globl kof_sec_sha1_block
            .type kof_sec_sha1_block, @function
            kof_sec_sha1_block:
                addi sp, sp, -352
                # `lw` no RV64 SIGN-EXTENDE (o `movl` x86 zerava o topo) — zext
                # para as rotacoes (srli/slli) nao lerem os bits de sinal.
                lw   a2, 0(a0)
                lw   a3, 4(a0)
                lw   a4, 8(a0)
                lw   a5, 12(a0)
                lw   a6, 16(a0)
                slli a2, a2, 32
                srli a2, a2, 32
                slli a3, a3, 32
                srli a3, a3, 32
                slli a4, a4, 32
                srli a4, a4, 32
                slli a5, a5, 32
                srli a5, a5, 32
                slli a6, a6, 32
                srli a6, a6, 32
                sw   a2, 320(sp)
                sw   a3, 324(sp)
                sw   a4, 328(sp)
                sw   a5, 332(sp)
                sw   a6, 336(sp)
                # w[0..15] = bswap32(bloco)
                li   t0, 0
            .L62_load:
                li   t1, 16
                bge  t0, t1, .L62_load_done
                slli t2, t0, 2
                add  t2, a1, t2
                lw   t3, 0(t2)
                slli t3, t3, 32
                srli t3, t3, 32
                andi t4, t3, 0xff
                slli t4, t4, 24
                srli t5, t3, 8
                andi t5, t5, 0xff
                slli t5, t5, 16
                or   t4, t4, t5
                srli t5, t3, 16
                andi t5, t5, 0xff
                slli t5, t5, 8
                or   t4, t4, t5
                srli t5, t3, 24
                andi t5, t5, 0xff
                or   t4, t4, t5
                slli t2, t0, 2
                add  t2, sp, t2
                sw   t4, 0(t2)
                addi t0, t0, 1
                j    .L62_load
            .L62_load_done:
                # w[16..79] = rol1(w[i-3]^w[i-8]^w[i-14]^w[i-16])
                li   t0, 16
            .L62_w:
                li   t1, 80
                bge  t0, t1, .L62_w_done
                slli t2, t0, 2
                add  t2, sp, t2
                lw   t3, -12(t2)
                slli t3, t3, 32
                srli t3, t3, 32
                lw   t4, -32(t2)
                slli t4, t4, 32
                srli t4, t4, 32
                xor  t3, t3, t4
                lw   t4, -56(t2)
                slli t4, t4, 32
                srli t4, t4, 32
                xor  t3, t3, t4
                lw   t4, -64(t2)
                slli t4, t4, 32
                srli t4, t4, 32
                xor  t3, t3, t4
                slli t4, t3, 1
                srli t3, t3, 31
                or   t3, t4, t3
                sw   t3, 0(t2)
                addi t0, t0, 1
                j    .L62_w
            .L62_w_done:
                li   t6, 0
            .L62_round:
                li   t0, 80
                bge  t6, t0, .L62_round_done
                li   t0, 20
                bge  t6, t0, .L62_p2
                and  t1, a3, a4
                xori t2, a3, -1
                and  t2, t2, a5
                or   a7, t1, t2
                li   t5, 0x5A827999
                j    .L62_f_done
            .L62_p2:
                li   t0, 40
                bge  t6, t0, .L62_p3
                xor  a7, a3, a4
                xor  a7, a7, a5
                li   t5, 0x6ED9EBA1
                j    .L62_f_done
            .L62_p3:
                li   t0, 60
                bge  t6, t0, .L62_p4
                and  t1, a3, a4
                and  t2, a3, a5
                or   t1, t1, t2
                and  t2, a4, a5
                or   a7, t1, t2
                li   t5, 0x8F1BBCDC
                j    .L62_f_done
            .L62_p4:
                xor  a7, a3, a4
                xor  a7, a7, a5
                li   t5, 0xCA62C1D6
            .L62_f_done:
                slli t1, a2, 5
                srli t0, a2, 27
                or   t1, t1, t0
                add  t1, t1, a7
                add  t1, t1, a6
                add  t1, t1, t5
                slli t0, t6, 2
                add  t0, sp, t0
                lw   t0, 0(t0)
                slli t0, t0, 32
                srli t0, t0, 32
                add  t1, t1, t0
                mv   a6, a5
                mv   a5, a4
                slli t0, a3, 30
                srli a4, a3, 2
                or   a4, t0, a4
                # 32-bit truncation (RV64G nao tem registrador de 32 bits como o
                # x86): zext a..c para as rotacoes nao lerem bits altos.
                slli t2, a4, 32
                srli a4, t2, 32
                mv   a3, a2
                slli t2, t1, 32
                srli a2, t2, 32
                addi t6, t6, 1
                j    .L62_round
            .L62_round_done:
                lw   t0, 320(sp)
                add  t0, t0, a2
                sw   t0, 320(sp)
                lw   t0, 324(sp)
                add  t0, t0, a3
                sw   t0, 324(sp)
                lw   t0, 328(sp)
                add  t0, t0, a4
                sw   t0, 328(sp)
                lw   t0, 332(sp)
                add  t0, t0, a5
                sw   t0, 332(sp)
                lw   t0, 336(sp)
                add  t0, t0, a6
                sw   t0, 336(sp)
                lw   t0, 320(sp)
                sw   t0, 0(a0)
                lw   t0, 324(sp)
                sw   t0, 4(a0)
                lw   t0, 328(sp)
                sw   t0, 8(a0)
                lw   t0, 332(sp)
                sw   t0, 12(a0)
                lw   t0, 336(sp)
                sw   t0, 16(a0)
                addi sp, sp, 352
                ret

            # ---------------------------------------------------------------
            # kof_sec_sha1_internal(a0=out20, a1=src, a2=len) — len < 56
            # Frame 240: ra+s0..s8 (0..79) | h[5] (80..99) | pad 128B (104..231)
            # ---------------------------------------------------------------
            .globl kof_sec_sha1_internal
            .type kof_sec_sha1_internal, @function
            kof_sec_sha1_internal:
                addi sp, sp, -240
                sd   ra, 0(sp)
                sd   s0, 8(sp)
                sd   s1, 16(sp)
                sd   s2, 24(sp)
                sd   s3, 32(sp)
                sd   s4, 40(sp)
                sd   s5, 48(sp)
                sd   s6, 56(sp)
                sd   s7, 64(sp)
                sd   s8, 72(sp)
                mv   s0, a0
                mv   s1, a1
                mv   s2, a2
                li   t0, 0x67452301
                sw   t0, 80(sp)
                li   t0, 0xEFCDAB89
                sw   t0, 84(sp)
                li   t0, 0x98BADCFE
                sw   t0, 88(sp)
                li   t0, 0x10325476
                sw   t0, 92(sp)
                li   t0, 0xC3D2E1F0
                sw   t0, 96(sp)
                li   s3, 0
            .L62_full:
                sub  t0, s2, s3
                li   t1, 64
                blt  t0, t1, .L62_final
                addi a0, sp, 80
                add  a1, s1, s3
                call kof_sec_sha1_block
                addi s3, s3, 64
                j    .L62_full
            .L62_final:
                sub  s4, s2, s3
                addi s5, sp, 104
                li   t2, 0
            .L62_copy:
                bge  t2, s4, .L62_copy_done
                add  t3, s1, s3
                add  t3, t3, t2
                lbu  t4, 0(t3)
                add  t5, s5, t2
                sb   t4, 0(t5)
                addi t2, t2, 1
                j    .L62_copy
            .L62_copy_done:
                add  t5, s5, s4
                li   t4, 0x80
                sb   t4, 0(t5)
                addi t2, s4, 1
                li   t4, 0
            .L62_pad:
                li   t3, 128
                bge  t2, t3, .L62_pad_done
                add  t5, s5, t2
                sb   t4, 0(t5)
                addi t2, t2, 1
                j    .L62_pad
            .L62_pad_done:
                slli t0, s2, 3
                mv   a0, t0
                call kof_bswap64
                addi t5, s5, 56
                sd   a0, 0(t5)
                addi a0, sp, 80
                mv   a1, s5
                call kof_sec_sha1_block
                lw   a0, 80(sp)
                call kof_bswap32
                sw   a0, 0(s0)
                lw   a0, 84(sp)
                call kof_bswap32
                sw   a0, 4(s0)
                lw   a0, 88(sp)
                call kof_bswap32
                sw   a0, 8(s0)
                lw   a0, 92(sp)
                call kof_bswap32
                sw   a0, 12(s0)
                lw   a0, 96(sp)
                call kof_bswap32
                sw   a0, 16(s0)
                ld   ra, 0(sp)
                ld   s0, 8(sp)
                ld   s1, 16(sp)
                ld   s2, 24(sp)
                ld   s3, 32(sp)
                ld   s4, 40(sp)
                ld   s5, 48(sp)
                ld   s6, 56(sp)
                ld   s7, 64(sp)
                ld   s8, 72(sp)
                addi sp, sp, 240
                ret
            .section .text
            """;
}
