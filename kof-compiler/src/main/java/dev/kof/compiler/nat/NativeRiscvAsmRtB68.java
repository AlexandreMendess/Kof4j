package dev.kof.compiler.nat;

// S5.2 (db-parity-plan, gaps-db lane, 23/09): reader de pacotes MySQL no cross
// (port de RuntimeDb2 `kof_db_mysql_reset`/`kof_db_mysql_next`, x86). Mantem
// buffer+ppos/pend em globais (uma conexao por vez, como o x86) e entrega o
// proximo pacote do stream: a0=ptr do payload (buf+4), a1=len do payload;
// a0=0 em fim de stream/erro. Depende da HAL `kof_plat_read` (B0/Rt0).
//
// Contrato riscv:
//   kof_db_mysql_reset(a0=fd)              -> zera o reader para ler do fd
//   kof_db_mysql_next()                    -> a0=payload ptr | 0; a1=payload len
public final class NativeRiscvAsmRtB68 {

    private NativeRiscvAsmRtB68() {}

    static String RISCV_RUNTIME_ASM_B_68 = """
            .section .data
            .align 3
            .L68_fd:
                .dword 0
            .L68_ppos:
                .dword 0
            .L68_pend:
                .dword 0
            .L68_buf:
                .zero 16384
            .section .text
            # ---------------------------------------------------------------
            # kof_db_mysql_reset(a0=fd)
            # ---------------------------------------------------------------
            .globl kof_db_mysql_reset
            .type kof_db_mysql_reset, @function
            kof_db_mysql_reset:
                la   t0, .L68_fd
                sd   a0, 0(t0)
                la   t1, .L68_buf
                la   t2, .L68_ppos
                sd   t1, 0(t2)
                la   t2, .L68_pend
                sd   t1, 0(t2)
                ret
            .section .text
            # ---------------------------------------------------------------
            # kof_db_mysql_next() -> a0=payload ptr | 0, a1=len
            # ---------------------------------------------------------------
            .globl kof_db_mysql_next
            .type kof_db_mysql_next, @function
            kof_db_mysql_next:
                addi sp, sp, -48
                sd   ra, 0(sp)
                sd   s0, 8(sp)
                sd   s1, 16(sp)
                sd   s2, 24(sp)
                la   t0, .L68_ppos
                ld   s0, 0(t0)              # ppos
                la   t1, .L68_pend
                ld   s1, 0(t1)              # pend
                blt  s0, s1, .L68_extract
            .L68_read:
                la   t0, .L68_fd
                ld   a0, 0(t0)
                la   a1, .L68_buf
                li   a2, 16384
                call kof_plat_read
                blez a0, .L68_fail
                la   s0, .L68_buf
                add  s1, s0, a0             # pend = buf + n
                la   t0, .L68_pend
                sd   s1, 0(t0)
            .L68_extract:
                lbu  t0, 0(s0)
                lbu  t1, 1(s0)
                slli t1, t1, 8
                or   t0, t0, t1
                lbu  t1, 2(s0)
                slli t1, t1, 16
                or   t0, t0, t1             # payload len
                li   t1, 0xFFFFFF
                beq  t0, t1, .L68_fail
                addi a0, s0, 4              # payload ptr
                addi s0, s0, 4
                add  s0, s0, t0             # ppos = ptr + len
                la   t1, .L68_buf
                li   t2, 16384
                add  t1, t1, t2
                bgt  s0, t1, .L68_fail
                la   t1, .L68_ppos
                sd   s0, 0(t1)
                mv   a1, t0
                j    .L68_ret
            .L68_fail:
                li   a0, 0
                li   a1, 0
            .L68_ret:
                ld   ra, 0(sp)
                ld   s0, 8(sp)
                ld   s1, 16(sp)
                ld   s2, 24(sp)
                addi sp, sp, 48
                ret
            .section .text
            """;
}
