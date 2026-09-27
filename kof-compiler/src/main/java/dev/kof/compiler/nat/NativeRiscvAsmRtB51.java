package dev.kof.compiler.nat;

// DB-3/DB-1 cross, slice B (22/09, lane gaps-db): kof_orm_create no riscv64 —
// F1d de RuntimeOrm2 x86 (parser do schema + DDL + exec), sobre o kof.db
// SQLite de RtB46/RtB47. aarch64 herda via tradutor.
//
// Semântica espelhada do x86 (a referência do contrato, D-DB-GAPS):
// - schema no formato de KofOrm.schemaString: campos por ',', partes por ':'
//   — name:dbType[:generated][:unique]; tokens de tipo comparados byte a
//   byte (int/long/bool/double/float; resto/ausente -> VARCHAR(255)).
// - DDL: CREATE TABLE IF NOT EXISTS "t" (...); generated vira
//   INTEGER PRIMARY KEY AUTOINCREMENT (e suprime o UNIQUE separado);
//   unique vira " UNIQUE" no fim da coluna.
// - exec direto por sqlite3_exec (mesma syscall do .Lorm_exec x86: rc==0 ->
//   true, qualquer erro -> false); duas chamadas seguidas devolvem true
//   (IF NOT EXISTS).
// - id ruim/nulo -> throw "unknown db connection: " + id (mesma mensagem do
//   .Lorm_conn; MySQL inalcançável no cross — kof_db_connect recusa o scheme
//   no connect, então não carregamos o dispatch mysql).
//
// ABI: kof_orm_create(id@a0, table@a1, schema@a2) -> Bool a0. Rótulos
// .L51_* (namespace por peça, disciplina do RiscvSlices; nenhum .L50_* é
// referenciado de fora — lição §445).
public final class NativeRiscvAsmRtB51 {

    private NativeRiscvAsmRtB51() {}

    static String RISCV_RUNTIME_ASM_B_51 = """
            .section .text
            # ---------------------------------------------------------------
            # .L51_conn(a0=id*) -> a0=handle sqlite | lança (port .Lorm_conn)
            # ---------------------------------------------------------------
            .L51_conn:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                mv   s0, a0
                beqz a0, .L51_conn_bad
                call kof_db_type                  # 1=sqlite (0=null/ruim)
                li   t0, 1
                bne  a0, t0, .L51_conn_bad
                mv   a0, s0
                call kof_db_resolve
                beqz a0, .L51_conn_bad
                ld   ra, 24(sp)
                ld   s0, 16(sp)
                addi sp, sp, 32
                ret
            .L51_conn_bad:
                beqz s0, .L51_conn_bad_null
                la   a0, .L51_bc_pre
                li   a1, 23
                call kof_string_from_literal
                mv   a1, s0
                call kof_string_concat            # "unknown db connection: "+id
                j    .L51_conn_throw
            .L51_conn_bad_null:
                la   a0, .L51_bc_pre
                li   a1, 23
                call kof_string_from_literal
            .L51_conn_throw:
                call kof_throw_string             # nunca volta (chain/pânico)
                ld   ra, 24(sp)
                ld   s0, 16(sp)
                addi sp, sp, 32
                ret

            # ---------------------------------------------------------------
            # Builder do DDL (cursor@app s4) — ap/ch e qq copiam p/ o buffer
            # ---------------------------------------------------------------
            # .L51_ap(a0=ptr, a1=len): copia o bloco e avança o cursor
            .L51_ap:
                addi sp, sp, -16
                sd   ra, 8(sp)
                sd   a1, 0(sp)
                mv   a2, a1
                mv   a1, a0
                mv   a0, s4
                call kof_memcpy
                ld   t0, 0(sp)
                add  s4, s4, t0
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret

            # .L51_ch(a0=byte): emite 1 byte e avança o cursor
            .L51_ch:
                sb   a0, 0(s4)
                addi s4, s4, 1
                ret

            # .L51_qq(a0=KofString*): emite "payload" com aspas duplas
            .L51_qq:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                mv   s0, a0
                li   a0, 34
                call .L51_ch
                addi a0, s0, 24
                lw   a1, 16(s0)
                call .L51_ap
                li   a0, 34
                call .L51_ch
                ld   ra, 24(sp)
                ld   s0, 16(sp)
                addi sp, sp, 32
                ret

            # .L51_tis(a0=tok,a1=len,a2=lit,a3=litLen) -> a0 1/0
            .L51_tis:
                bne  a1, a3, .L51_tis_x
                li   t0, 0
            .L51_tis_i:
                bge  t0, a3, .L51_tis_o
                add  t1, a0, t0
                lbu  t2, 0(t1)
                add  t1, a2, t0
                lbu  t3, 0(t1)
                bne  t2, t3, .L51_tis_x
                addi t0, t0, 1
                j    .L51_tis_i
            .L51_tis_o:
                li   a0, 1
                ret
            .L51_tis_x:
                li   a0, 0
                ret

            # ---------------------------------------------------------------
            # kof_orm_create(id*, table*, schema*) -> Bool
            # slots: 0 f1 | 8 gen | 16 uniq
            # ---------------------------------------------------------------
            .globl kof_orm_create
            .type kof_orm_create, @function
            kof_orm_create:
                addi sp, sp, -128
                sd   ra, 120(sp)
                sd   s0, 112(sp)
                sd   s1, 104(sp)
                sd   s2, 96(sp)
                sd   s3, 88(sp)
                sd   s4, 80(sp)
                sd   s5, 72(sp)
                sd   s6, 64(sp)
                sd   s7, 56(sp)
                sd   s8, 48(sp)
                sd   s9, 40(sp)
                sd   s10, 32(sp)
                sd   s11, 24(sp)
                mv   s0, a0
                mv   s1, a1
                mv   s2, a2
                call .L51_conn
                mv   s3, a0                        # conn
                # cap = 128 + tblLen + 2*schemaLen
                lw   t0, 16(s1)
                addi t0, t0, 128
                lw   t1, 16(s2)
                slli t1, t1, 1
                add  t0, t0, t1
                mv   a0, t0
                call kof_alloc
                mv   s5, a0                        # base
                mv   s4, a0                        # cursor
                li   t0, 1
                sd   t0, 0(sp)                     # f1
                sd   zero, 8(sp)                   # gen
                sd   zero, 16(sp)                  # uniq
                la   a0, .L51_cre
                li   a1, 27
                call .L51_ap
                mv   a0, s1
                call .L51_qq                       # "table"
                li   a0, 32                        # ' ' — o host emite `"t" (`
                call .L51_ch
                li   a0, 40                        # '('
                call .L51_ch
                addi s6, s2, 24                    # corpo do schema
                lw   t0, 16(s2)
                add  s7, s6, t0                    # fim
            .L51_field:
                bgeu s6, s7, .L51_last
                ld   t0, 0(sp)
                bnez t0, .L51_nosep
                la   a0, .L51_comma
                li   a1, 2
                call .L51_ap
            .L51_nosep:
                sd   zero, 0(sp)                   # f1 = false daqui em diante
                mv   s8, s6                        # nameStart
            .L51_nscan:
                bgeu s6, s7, .L51_nend
                lbu  t0, 0(s6)
                li   t1, 58                        # ':'
                beq  t0, t1, .L51_nend
                li   t1, 44                        # ','
                beq  t0, t1, .L51_nend
                addi s6, s6, 1
                j    .L51_nscan
            .L51_nend:
                sub  s9, s6, s8                    # nameLen
                li   a0, 34                        # '"'
                call .L51_ch
                mv   a0, s8
                mv   a1, s9
                call .L51_ap
                li   a0, 34
                call .L51_ch
                li   a0, 32
                call .L51_ch
                sd   zero, 8(sp)
                sd   zero, 16(sp)
                mv   s10, zero                     # typeStart = NULL
            .L51_tok:
                bgeu s6, s7, .L51_emit
                lbu  t0, 0(s6)
                li   t1, 44
                beq  t0, t1, .L51_commaf
                li   t1, 58
                bne  t0, t1, .L51_tokadv
                addi s6, s6, 1
                mv   s11, s6                       # ts
            .L51_tscan:
                bgeu s6, s7, .L51_tdone
                lbu  t0, 0(s6)
                li   t1, 58
                beq  t0, t1, .L51_tdone
                li   t1, 44
                beq  t0, t1, .L51_tdone
                addi s6, s6, 1
                j    .L51_tscan
            .L51_tdone:
                sub  s9, s6, s11                   # len do token
                bnez s10, .L51_flag
                mv   s10, s11                      # typeStart
                j    .L51_toknext
            .L51_flag:
                mv   a0, s11
                mv   a1, s9
                la   a2, .L51_gen
                li   a3, 9
                call .L51_tis
                bnez a0, .L51_fgen
                mv   a0, s11
                mv   a1, s9
                la   a2, .L51_uq
                li   a3, 6
                call .L51_tis
                beqz a0, .L51_toknext
                li   t0, 1
                sd   t0, 16(sp)
                j    .L51_toknext
            .L51_fgen:
                li   t0, 1
                sd   t0, 8(sp)
            .L51_toknext:
                bgeu s6, s7, .L51_emit
                lbu  t0, 0(s6)
                li   t1, 44
                beq  t0, t1, .L51_commaf
                addi s6, s6, 1                     # consome ':'; palavra
                mv   s11, s6                       #   começa AQUI (bug das
                j    .L51_tscan                    #   flags, medido no x86)
            .L51_tokadv:
                addi s6, s6, 1
                j    .L51_toknext
            .L51_commaf:
                addi s6, s6, 1
            .L51_emit:
                beqz s10, .L51_emitvc
                ld   t0, 8(sp)                     # generated?
                beqz t0, .L51_notgen
                la   a0, .L51_pk
                li   a1, 33
                call .L51_ap
                j    .L51_sep
            .L51_notgen:
                mv   a0, s10
                mv   a1, s9
                la   a2, .L51_tint
                li   a3, 3
                call .L51_tis
                bnez a0, .L51_tyint
                mv   a0, s10
                mv   a1, s9
                la   a2, .L51_tlong
                li   a3, 4
                call .L51_tis
                bnez a0, .L51_tylong
                mv   a0, s10
                mv   a1, s9
                la   a2, .L51_tbool
                li   a3, 4
                call .L51_tis
                bnez a0, .L51_tybool
                mv   a0, s10
                mv   a1, s9
                la   a2, .L51_tdouble
                li   a3, 6
                call .L51_tis
                bnez a0, .L51_tydouble
                mv   a0, s10
                mv   a1, s9
                la   a2, .L51_tfloat
                li   a3, 5
                call .L51_tis
                bnez a0, .L51_tyfloat
                j    .L51_emitvc
            .L51_tyint:
                la   a0, .L51_integer
                li   a1, 7
                call .L51_ap
                j    .L51_uniq
            .L51_tylong:
                la   a0, .L51_longsql
                li   a1, 7
                call .L51_ap
                j    .L51_uniq
            .L51_tybool:
                la   a0, .L51_boolean
                li   a1, 7
                call .L51_ap
                j    .L51_uniq
            .L51_tydouble:
                la   a0, .L51_dbl
                li   a1, 6
                call .L51_ap
                j    .L51_uniq
            .L51_tyfloat:
                la   a0, .L51_real
                li   a1, 4
                call .L51_ap
                j    .L51_uniq
            .L51_emitvc:
                la   a0, .L51_vc
                li   a1, 12
                call .L51_ap
            .L51_uniq:
                ld   t0, 16(sp)
                beqz t0, .L51_sep
                ld   t0, 8(sp)                     # generated nunca ganha
                bnez t0, .L51_sep                  #   UNIQUE separado
                la   a0, .L51_unique
                li   a1, 7
                call .L51_ap
            .L51_sep:
                j    .L51_field
            .L51_last:
                li   a0, 41                        # ')'
                call .L51_ch
                sb   zero, 0(s4)                   # NUL p/ o sqlite3_exec
                mv   a0, s3
                mv   a1, s5
                li   a2, 0
                li   a3, 0
                li   a4, 0
                call sqlite3_exec
                beqz a0, .L51_true
                li   a0, 0
                j    .L51_out
            .L51_true:
                li   a0, 1
            .L51_out:
                ld   ra, 120(sp)
                ld   s0, 112(sp)
                ld   s1, 104(sp)
                ld   s2, 96(sp)
                ld   s3, 88(sp)
                ld   s4, 80(sp)
                ld   s5, 72(sp)
                ld   s6, 64(sp)
                ld   s7, 56(sp)
                ld   s8, 48(sp)
                ld   s9, 40(sp)
                ld   s10, 32(sp)
                ld   s11, 24(sp)
                addi sp, sp, 128
                ret

            .section .rodata
            .L51_bc_pre:
                .ascii "unknown db connection: "
            .L51_cre:
                .ascii "CREATE TABLE IF NOT EXISTS "
            .L51_comma:
                .ascii ", "
            .L51_pk:
                .ascii "INTEGER PRIMARY KEY AUTOINCREMENT"
            .L51_integer:
                .ascii "INTEGER"
            .L51_longsql:
                .ascii "INTEGER"
            .L51_boolean:
                .ascii "BOOLEAN"
            .L51_dbl:
                .ascii "DOUBLE"
            .L51_real:
                .ascii "REAL"
            .L51_vc:
                .ascii "VARCHAR(255)"
            .L51_unique:
                .ascii " UNIQUE"
            .L51_gen:
                .ascii "generated"
            .L51_uq:
                .ascii "unique"
            .L51_tint:
                .ascii "int"
            .L51_tlong:
                .ascii "long"
            .L51_tbool:
                .ascii "bool"
            .L51_tdouble:
                .ascii "double"
            .L51_tfloat:
                .ascii "float"
            .section .text
            """;
}
