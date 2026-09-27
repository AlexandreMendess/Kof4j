package dev.kof.compiler.nat;

// S5.1 (db-parity-plan, gaps-db lane, 23/09): auth helper do wire MySQL no
// cross — `mysql_native_password` scramble + length-encoded integer. Port de
// RuntimeDb1 (`kof_db_mysql_scramble`/`kof_db_mysql_lenenc`) do x86 para
// riscv64; aarch64 herda via tradutor. Depende da peca B62 (SHA1 + bswap).
//
// Contrato riscv (mirror do x86):
//   kof_db_mysql_scramble(a0=out20, a1=seed, a2=seedlen, a3=pass KofString)
//       stage1 = SHA1(pass); stage2 = SHA1(stage1);
//       stage3 = SHA1(seed || stage2); out = stage1 XOR stage3 (20 bytes)
//   kof_db_mysql_lenenc(a0=buf) -> a0=valor, a1=proxima pos
//       < 0xFC: 1 byte | 0xFC: 2 bytes LE | 0xFD: 3 bytes LE
// O caminho de auth nunca hasheia >= 56 bytes (pass curto, seed 20 + 20), por
// isso B62 cobre. Estado em s0..s8 (NUNCA s10 = x16 caller-saved no aarch64).
public final class NativeRiscvAsmRtB63 {

    private NativeRiscvAsmRtB63() {}

    static String RISCV_RUNTIME_ASM_B_63 = """
            .section .text
            # ---------------------------------------------------------------
            # kof_db_mysql_scramble(a0=out20, a1=seed, a2=seedlen, a3=pass)
            # Frame 320: ra+s0..s8 (0..79) | stage1 (80..99) | stage2 (100..119)
            #            | stage3 (120..139) | seed||stage2 (144..303)
            # ---------------------------------------------------------------
            .globl kof_db_mysql_scramble
            .type kof_db_mysql_scramble, @function
            kof_db_mysql_scramble:
                addi sp, sp, -320
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
                mv   s0, a0              # out
                mv   s1, a1              # seed
                mv   s2, a2              # seedlen
                mv   s3, a3              # pass (KofString)
                # stage1 = SHA1(pass payload, passlen)
                addi a0, sp, 80
                add  a1, s3, 24
                lw   a2, 16(s3)
                call kof_sec_sha1_internal
                # stage2 = SHA1(stage1, 20)
                addi a0, sp, 100
                addi a1, sp, 80
                li   a2, 20
                call kof_sec_sha1_internal
                # buf = seed || stage2
                addi s4, sp, 144
                li   s5, 0
            .L63_cp_seed:
                bge  s5, s2, .L63_cp_seed_done
                add  t0, s1, s5
                lbu  t1, 0(t0)
                add  t2, s4, s5
                sb   t1, 0(t2)
                addi s5, s5, 1
                j    .L63_cp_seed
            .L63_cp_seed_done:
                li   s5, 0
            .L63_cp_st2:
                li   t0, 20
                bge  s5, t0, .L63_cp_st2_done
                addi t1, sp, 100
                add  t1, t1, s5
                lbu  t2, 0(t1)
                add  t3, s4, s2
                add  t3, t3, s5
                sb   t2, 0(t3)
                addi s5, s5, 1
                j    .L63_cp_st2
            .L63_cp_st2_done:
                # stage3 = SHA1(buf, seedlen + 20)
                addi a0, sp, 120
                mv   a1, s4
                addi a2, s2, 20
                call kof_sec_sha1_internal
                # out = stage1 XOR stage3
                li   s5, 0
            .L63_xor:
                li   t0, 20
                bge  s5, t0, .L63_xor_done
                addi t1, sp, 80
                add  t1, t1, s5
                lbu  t2, 0(t1)
                addi t1, sp, 120
                add  t1, t1, s5
                lbu  t3, 0(t1)
                xor  t2, t2, t3
                add  t1, s0, s5
                sb   t2, 0(t1)
                addi s5, s5, 1
                j    .L63_xor
            .L63_xor_done:
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
                addi sp, sp, 320
                ret

            # ---------------------------------------------------------------
            # kof_db_mysql_lenenc(a0=buf) -> a0=valor, a1=proxima pos
            # ---------------------------------------------------------------
            .globl kof_db_mysql_lenenc
            .type kof_db_mysql_lenenc, @function
            kof_db_mysql_lenenc:
                lbu  a2, 0(a0)
                li   t0, 0xFC
                beq  a2, t0, .L63_len_2
                li   t0, 0xFD
                beq  a2, t0, .L63_len_3
                addi a1, a0, 1
                mv   a0, a2
                ret
            .L63_len_2:
                lbu  t1, 1(a0)
                lbu  t2, 2(a0)
                slli t2, t2, 8
                or   a2, t1, t2
                addi a1, a0, 3
                mv   a0, a2
                ret
            .L63_len_3:
                lbu  t1, 1(a0)
                lbu  t2, 2(a0)
                slli t2, t2, 8
                or   t1, t1, t2
                lbu  t2, 3(a0)
                slli t2, t2, 16
                or   t1, t1, t2
                addi a1, a0, 4
                mv   a0, t1
                ret
            .section .text
            """;
}
