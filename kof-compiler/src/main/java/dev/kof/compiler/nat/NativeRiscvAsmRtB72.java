package dev.kof.compiler.nat;

// S5.3 (db-parity-plan, gaps-db lane, 24/09): execute COM_QUERY no cross —
// `kof_db_mysql_execute(fd, sql)` envia o comando texto via a B67 e devolve
// as affected-rows do OK-packet (1 byte / 0xFC+2LE / 0xFD+3LE). Port da cauda
// de execute do x86 (`RuntimeDb4` `.Ldb_exec_subst` + `RuntimeDb5`
// `.Ldb_exec_done`/`.Ldb_exec_afc`/`.Ldb_exec_afd`/`.Ldb_exec_bad`): erro de
// I/O, pacote ERR (0xFF) ou first-byte != 0x00 (resultset: column-count >= 1)
// devolve 0. Query com binds nao precisa de peca nova: B71 substitui os `?`
// e a B70 (`kof_db_mysql_query`) parseia o resultset — o dispatch mysql do
// executeN/queryN da B47 chega na fatia do link (S5.4 conecta o fd), mesma
// postura incremental R7 das fatias B62-B71 (prova por harness, nao pela
// superficie `db.*`, que segue DB001 honesto no cross).
//
// Contrato riscv:
//   kof_db_mysql_execute(a0=fd, a1=sql KofString) -> a0 = affected rows | 0
public final class NativeRiscvAsmRtB72 {

    private NativeRiscvAsmRtB72() {}

    static String RISCV_RUNTIME_ASM_B_72 = """
            .section .data
            .align 3
            .L72_buf:
                .zero 4096
            .section .text
            # ---------------------------------------------------------------
            # kof_db_mysql_execute(a0=fd, a1=sql) -> a0 = affected rows | 0
            # ---------------------------------------------------------------
            .globl kof_db_mysql_execute
            .type kof_db_mysql_execute, @function
            kof_db_mysql_execute:
                addi sp, sp, -32
                sd   ra, 0(sp)
                sd   s0, 8(sp)
                sd   s1, 16(sp)
                mv   s0, a0
                mv   s1, a1
                la   a2, .L72_buf
                li   a3, 4096
                call kof_db_mysql_command
                blez a0, .L72_bad
                mv   s1, a1
                lbu  t0, 0(s1)
                li   t1, 255
                beq  t0, t1, .L72_bad
                bnez t0, .L72_bad
                lbu  a0, 1(s1)
                li   t1, 252
                beq  a0, t1, .L72_afc
                li   t1, 253
                beq  a0, t1, .L72_afd
                j    .L72_out
            .L72_afc:
                lbu  t0, 2(s1)
                lbu  t1, 3(s1)
                slli t1, t1, 8
                or   a0, t0, t1
                j    .L72_out
            .L72_afd:
                lbu  t0, 2(s1)
                lbu  t1, 3(s1)
                slli t1, t1, 8
                or   t0, t0, t1
                lbu  t1, 4(s1)
                slli t1, t1, 16
                or   a0, t0, t1
                j    .L72_out
            .L72_bad:
                li   a0, 0
            .L72_out:
                ld   ra, 0(sp)
                ld   s0, 8(sp)
                ld   s1, 16(sp)
                addi sp, sp, 32
                ret
            .section .text
            """;
}
