package dev.kof.compiler.nat;

// S5.5 fatia 5c (db-parity-plan, gaps-db lane, 24/09): whitelist do operador
// mysql no cross — port de RuntimeOrmMysqlOp (.Lorm_my_op) para
// `kof_orm_where`/`where_op` sobre o wire MySQL. A face sqlite tem a sua
// whitelist inline (B59); esta e' a da face mysql, como no x86.
//
// kof_orm_mysql_op(a0=op KofString* | 0) -> a0=opPtr, a1=opLen
//   op 0 (face `where`) devolve "="; `==` normaliza p/ "="; `>` `<` `>=`
//   `<=` `!=` `LIKE` devolvem o proprio corpo; qualquer outro ->
//   throw "ORM operator not allowed: <op>" (R6).
public final class NativeRiscvAsmRtB80Helpers {

    private NativeRiscvAsmRtB80Helpers() {}

    static String RISCV_RUNTIME_ASM_B_80H = """
            .section .text
            # ---------------------------------------------------------------
            # kof_orm_mysql_op(a0=op*|0) -> a0=opPtr, a1=opLen
            # ---------------------------------------------------------------
            .globl kof_orm_mysql_op
            .type kof_orm_mysql_op, @function
            kof_orm_mysql_op:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                mv   s0, a0                     # op*
                beqz s0, .L80h_eq
                lw   t2, 16(s0)                 # opLen
                li   t3, 1
                beq  t2, t3, .L80h_len1
                li   t3, 2
                beq  t2, t3, .L80h_len2
                li   t3, 4
                beq  t2, t3, .L80h_len4
                j    .L80h_bad
            .L80h_len1:
                lbu  t3, 24(s0)
                li   t4, 62                     # '>'
                beq  t3, t4, .L80h_use
                li   t4, 60                     # '<'
                beq  t3, t4, .L80h_use
                j    .L80h_bad
            .L80h_len2:
                lbu  t3, 24(s0)
                lbu  t4, 25(s0)
                li   t5, 61                     # '='
                beq  t3, t5, .L80h_eq           # ==
                li   t5, 62
                bne  t3, t5, .L80h_len2b
                li   t5, 61
                beq  t4, t5, .L80h_use          # >=
                j    .L80h_bad
            .L80h_len2b:
                li   t5, 60
                bne  t3, t5, .L80h_len2c
                li   t5, 61
                beq  t4, t5, .L80h_use          # <=
                j    .L80h_bad
            .L80h_len2c:
                li   t5, 33                     # '!'
                bne  t3, t5, .L80h_bad
                li   t5, 61
                beq  t4, t5, .L80h_use          # !=
                j    .L80h_bad
            .L80h_len4:
                lbu  t3, 24(s0)
                li   t4, 76                     # 'L'
                bne  t3, t4, .L80h_bad
                lbu  t3, 25(s0)
                li   t4, 73                     # 'I'
                bne  t3, t4, .L80h_bad
                lbu  t3, 26(s0)
                li   t4, 75                     # 'K'
                bne  t3, t4, .L80h_bad
                lbu  t3, 27(s0)
                li   t4, 69                     # 'E'
                bne  t3, t4, .L80h_bad
            .L80h_use:
                addi a0, s0, 24
                lw   a1, 16(s0)
                j    .L80h_ret
            .L80h_eq:
                la   a0, .L80h_eqstr
                li   a1, 1
                j    .L80h_ret
            # ---- op recusado -> throw "ORM operator not allowed: <op>" ----
            .L80h_bad:
                ld   s1, 16(s0)                 # opLen
                addi a0, s1, 26
                call kof_alloc
                mv   s1, a0
                la   a1, .L80h_msg
                li   a2, 26
                call kof_memcpy
                addi a0, s1, 26
                addi a1, s0, 24
                lw   a2, 16(s0)
                call kof_memcpy
                mv   a0, s1
                lw   a1, 16(s0)
                addi a1, a1, 26
                call kof_io_make_string
                call kof_throw_string
            .L80h_ret:
                ld   ra, 24(sp)
                ld   s0, 16(sp)
                ld   s1, 8(sp)
                addi sp, sp, 32
                ret

            .section .rodata
            .L80h_eqstr:
                .ascii "="
            .L80h_msg:
                .ascii "ORM operator not allowed: "
            .section .text
            """;
}
