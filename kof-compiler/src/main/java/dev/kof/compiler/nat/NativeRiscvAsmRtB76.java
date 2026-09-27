package dev.kof.compiler.nat;

// S5.5 fatia 4a (db-parity-plan, gaps-db lane, 24/09): o exec que LANÇA no
// erro do servidor — pré-requisito do `orm.save` no wire MySQL cross.
// `kof_orm_mysql_exec(fd, sql)` envia o COM_QUERY (B67/B72) e classifica a
// resposta; port do x86 `RuntimeOrmMysqlExec`/`.Lorm_sa_exec`:
//   OK (first byte 0x00)      -> affectedRows (lenenc: 1B / 0xFC+2LE / 0xFD+3LE)
//   ERR (first byte 0xFF)     -> throw `mysql: <msg>` (msg em payload+9, teto 400)
//   resposta inesperada/curta -> throw `mysql: connection lost`
//   conexão perdida (<=0)     -> throw `mysql: connection lost`
//   id não resolvido          -> throw `unknown db connection: <id>`
//
// Diferença do `kof_db_mysql_execute` (B72): a B72 devolve 0 no ERR porque a
// semântica do `db.execute` exige; o `orm.save` x86 LANÇA (R6), então este exec
// é o que o `save` usa. Só o `save` (e não `delete`/`deleteAll`, que espelham o
// exec genérico x86) precisa deste corpo — ver a nota de design da fatia 3.
//
// aarch64 herda via tradutor. Contrato riscv:
//   kof_orm_mysql_exec(a0=fd, a1=sql KofString) -> a0 = affected rows | throw
public final class NativeRiscvAsmRtB76 {

    private NativeRiscvAsmRtB76() {}

    static String RISCV_RUNTIME_ASM_B_76 = """
            .section .data
            .align 3
            .L76_buf:
                .zero 4096
            .section .text
            # ---------------------------------------------------------------
            # kof_orm_mysql_exec(a0=fd, a1=sql) -> a0 = affected | throw
            # ---------------------------------------------------------------
            .globl kof_orm_mysql_exec
            .type kof_orm_mysql_exec, @function
            kof_orm_mysql_exec:
                addi sp, sp, -48
                sd   ra, 0(sp)
                sd   s0, 8(sp)
                sd   s1, 16(sp)
                sd   s2, 24(sp)
                mv   s0, a0
                mv   s1, a1
                la   a2, .L76_buf
                li   a3, 4096
                call kof_db_mysql_command
                blez a0, .L76_lost
                mv   s1, a1                        # payload
                mv   s2, a0                        # payloadLen
                lbu  t0, 0(s1)
                li   t1, 255
                beq  t0, t1, .L76_err
                bnez t0, .L76_lost
                lbu  a0, 1(s1)                     # lenenc affected
                li   t1, 252
                beq  a0, t1, .L76_afc
                li   t1, 253
                beq  a0, t1, .L76_afd
                j    .L76_out
            .L76_afc:
                lbu  t0, 2(s1)
                lbu  t1, 3(s1)
                slli t1, t1, 8
                or   a0, t0, t1
                j    .L76_out
            .L76_afd:
                lbu  t0, 2(s1)
                lbu  t1, 3(s1)
                slli t1, t1, 8
                or   t0, t0, t1
                lbu  t1, 4(s1)
                slli t1, t1, 16
                or   a0, t0, t1
                j    .L76_out
            .L76_err:                              # msg = payload+9, teto 400
                addi t0, s1, 9
                addi t1, s2, -9
                bgtz t1, .L76_e1
                li   t1, 0
            .L76_e1:
                li   t2, 400
                ble  t1, t2, .L76_e2
                li   t1, 400
            .L76_e2:
                mv   a0, t0
                mv   a1, t1
                call kof_string_from_literal
                mv   s2, a0
                la   a0, .L76_pfx
                li   a1, 7
                call kof_string_from_literal
                mv   a1, s2
                call kof_string_concat
                call kof_throw_string
                j    .L76_out
            .L76_lost:
                la   a0, .L76_lostv
                li   a1, 22
                call kof_string_from_literal
                call kof_throw_string
            .L76_out:
                ld   ra, 0(sp)
                ld   s0, 8(sp)
                ld   s1, 16(sp)
                ld   s2, 24(sp)
                addi sp, sp, 48
                ret
            .section .rodata
            .L76_pfx:
                .ascii "mysql: "
            .L76_lostv:
                .ascii "mysql: connection lost"
            .section .text
            """;
}
