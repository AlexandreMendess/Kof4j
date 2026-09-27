package dev.kof.compiler.nat;

// DB-3/DB-1 cross, slice E (22/09, lane gaps-db): kof_orm_delete no riscv64
// — F2c3 de RuntimeOrm9 x86, sobre o kof.db SQLite de RtB46/RtB47, com o
// parser de schema (port de RuntimeOrmSchema) embutido: é a pedra-chave das
// faces row-object (delete resolve a PK; save/find/all/where reusam o mesmo
// parser). aarch64 herda via tradutor.
//
// Semântica espelhada do x86 (a referência do contrato, D-DB-GAPS):
// `kof_orm_delete(id*, key*, table*, schema*) -> Bool`. A PK é o PRIMEIRO
// campo com bit0 (`:generated`), senão 0 — mesmo critério do
// kof_orm_pkIndex do host; SQL `DELETE FROM "t" WHERE "pk" = ?` (tabela
// pelo KofString, PK pela entrada da ftab — ambos com aspas duplas), key
// pelo MESMO classificador do count_where/where (box §284 / KofString /
// null; tag fora da tabela → bind_null, como o x86). SQLITE_DONE (101) →
// true SEMPRE (host `execute1(...) >= 0`: miss também true — medido no
// oráculo 21/09); prepare/step fora do DONE → throw `sqlite: <errmsg>` (R6).
//
// ABI local (peças futuras reusam): `kof_orm_parse_schema` -> a0=ftab,
// a1=nbuf, a2=nFields, a3=pkIndex (x86 devolve rax/rdx/rcx/r8). Ftabela:
// 32B/campo [0 namePtr | 8 nameLen | 12 typeCode | 16 flags bit0 generated
// bit1 unique]; typeCode 0=int,1=long,2=string,3=bool,4=double,5=float.
// Rótulos .L54_* (namespace por peça; zero ref cross-peça, §445).
public final class NativeRiscvAsmRtB54 {

    private NativeRiscvAsmRtB54() {}

    static String RISCV_RUNTIME_ASM_B_54 = """
            .section .text
            # ---------------------------------------------------------------
            # .L54_conn(a0=id*) -> a0=handle sqlite | lança (port .Lorm_conn)
            # ---------------------------------------------------------------
            .L54_conn:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                mv   s0, a0
                beqz a0, .L54_conn_bad
                call kof_db_type                  # 1=sqlite (0=null/ruim)
                li   t0, 1
                bne  a0, t0, .L54_conn_bad
                mv   a0, s0
                call kof_db_resolve
                beqz a0, .L54_conn_bad
                ld   ra, 24(sp)
                ld   s0, 16(sp)
                addi sp, sp, 32
                ret
            .L54_conn_bad:
                beqz s0, .L54_conn_bad_null
                la   a0, .L54_bc_pre
                li   a1, 23
                call kof_string_from_literal
                mv   a1, s0
                call kof_string_concat            # "unknown db connection: "+id
                j    .L54_conn_throw
            .L54_conn_bad_null:
                la   a0, .L54_bc_pre
                li   a1, 23
                call kof_string_from_literal
            .L54_conn_throw:
                call kof_throw_string             # nunca volta (chain/pânico)
                ld   ra, 24(sp)
                ld   s0, 16(sp)
                addi sp, sp, 32
                ret

            # ---------------------------------------------------------------
            # Builder do SQL (cursor@s9) — helpers locais (padrão RtB51/RtB53)
            # ---------------------------------------------------------------
            # .L54_ap(a0=ptr, a1=len): copia o bloco e avança o cursor
            .L54_ap:
                addi sp, sp, -16
                sd   ra, 8(sp)
                sd   a1, 0(sp)
                mv   a2, a1
                mv   a1, a0
                mv   a0, s9
                call kof_memcpy
                ld   t0, 0(sp)
                add  s9, s9, t0
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret

            # .L54_ch(a0=byte): emite 1 byte e avança o cursor
            .L54_ch:
                sb   a0, 0(s9)
                addi s9, s9, 1
                ret

            # .L54_qq(a0=KofString*): emite "payload" com aspas duplas
            .L54_qq:
                addi sp, sp, -16
                sd   ra, 8(sp)
                sd   s0, 0(sp)
                mv   s0, a0
                li   a0, 34
                call .L54_ch
                addi a0, s0, 24
                lw   a1, 16(s0)
                call .L54_ap
                li   a0, 34
                call .L54_ch
                ld   ra, 8(sp)
                ld   s0, 0(sp)
                addi sp, sp, 16
                ret

            # .L54_qraw(a0=ptr, a1=len): emite "ptr" (nome cru da ftab)
            .L54_qraw:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   a0, 16(sp)
                sd   a1, 8(sp)
                li   a0, 34
                call .L54_ch
                ld   a0, 16(sp)
                ld   a1, 8(sp)
                call .L54_ap
                li   a0, 34
                call .L54_ch
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            # ---------------------------------------------------------------
            # kof_orm_parse_schema(schema*) -> a0=ftab, a1=nbuf, a2=nFields,
            #   a3=pkIndex (port RuntimeOrmSchema; ABI igual ao x86 rax/rdx/
            #   rcx/r8; global desde a fatia E-parte-2 para as peças
            #   row-object reusarem — ',' separa campos, ':' separa partes
            #   name:dbType[:generated][:unique], names copiados p/ buffer
            #    adjacente à ftab)
            # ---------------------------------------------------------------
            .globl kof_orm_parse_schema
            .type kof_orm_parse_schema, @function
            kof_orm_parse_schema:
                addi sp, sp, -96
                sd   ra, 88(sp)
                sd   s0, 80(sp)
                sd   s1, 72(sp)
                sd   s2, 64(sp)
                sd   s3, 56(sp)
                sd   s4, 48(sp)
                sd   s5, 40(sp)
                sd   s6, 32(sp)
                sd   s7, 24(sp)
                sd   s8, 16(sp)
                sd   s9, 8(sp)
                mv   s0, a0                        # schema
                lw   t0, 16(s0)                    # schLen
                slli t1, t0, 5                     # schLen*32
                add  t1, t1, t0
                addi t1, t1, 64                    # + folga
                mv   a0, t1
                call kof_alloc
                mv   s2, a0                        # ftab
                lw   t0, 16(s0)
                slli t0, t0, 5
                add  s3, a0, t0                    # nbp (buffer dos nomes)
                li   s1, 0                         # fi
                addi s4, s0, 24                    # cursor = body
                lw   t0, 16(s0)
                add  s5, s4, t0                    # fim
            .L54_ps_fld:
                mv   s9, s4                        # inicio do nome
            .L54_ps_nscan:
                bge  s4, s5, .L54_ps_nend
                lbu  t0, 0(s4)
                li   t1, 58                        # ':'
                beq  t0, t1, .L54_ps_nend
                li   t1, 44                        # ','
                beq  t0, t1, .L54_ps_nend
                addi s4, s4, 1
                j    .L54_ps_nscan
            .L54_ps_nend:
                sub  t0, s4, s9                    # nameLen
                slli t1, s1, 5
                add  s6, s2, t1                    # entry
                sd   s3, 0(s6)                     # namePtr
                sw   t0, 8(s6)                     # nameLen
                li   t2, 0
            .L54_ps_ncopy:
                bge  t2, t0, .L54_ps_ncopied
                add  t3, s9, t2
                lbu  t4, 0(t3)
                add  t5, s3, t2
                sb   t4, 0(t5)
                addi t2, t2, 1
                j    .L54_ps_ncopy
            .L54_ps_ncopied:
                add  s3, s3, t0                    # nbp += nameLen
                li   s7, 0                         # flags
                li   s8, 2                         # typeCode default string
                bge  s4, s5, .L54_ps_fldend
                lbu  t0, 0(s4)
                li   t1, 58
                bne  t0, t1, .L54_ps_fldend
                addi s4, s4, 1
                lbu  t0, 0(s4)
                li   t1, 105                       # 'i' -> int(0)
                bne  t0, t1, .L54_ps_t1
                li   s8, 0
                j    .L54_ps_tscan
            .L54_ps_t1:
                li   t1, 108                       # 'l' -> long(1)
                bne  t0, t1, .L54_ps_t2
                li   s8, 1
                j    .L54_ps_tscan
            .L54_ps_t2:
                li   t1, 115                       # 's' -> string(2, default)
                beq  t0, t1, .L54_ps_tscan
                li   t1, 98                        # 'b' -> bool(3)
                bne  t0, t1, .L54_ps_t3
                li   s8, 3
                j    .L54_ps_tscan
            .L54_ps_t3:
                li   t1, 100                       # 'd' -> double(4)
                bne  t0, t1, .L54_ps_t4
                li   s8, 4
                j    .L54_ps_tscan
            .L54_ps_t4:
                li   t1, 102                       # 'f' -> float(5)
                bne  t0, t1, .L54_ps_tscan
                li   s8, 5
            .L54_ps_tscan:                         # ate ':' (flags) | ',' | fim
                bge  s4, s5, .L54_ps_fldend
                lbu  t0, 0(s4)
                li   t1, 58
                beq  t0, t1, .L54_ps_flg
                li   t1, 44
                beq  t0, t1, .L54_ps_fldend
                addi s4, s4, 1
                j    .L54_ps_tscan
            .L54_ps_flg:
                bge  s4, s5, .L54_ps_fldend
                lbu  t0, 0(s4)
                li   t1, 58
                bne  t0, t1, .L54_ps_fldend
                addi s4, s4, 1
                lbu  t0, 0(s4)
                li   t1, 103                       # 'g' -> generated
                bne  t0, t1, .L54_ps_flu
                ori  s7, s7, 1
                j    .L54_ps_seg
            .L54_ps_flu:
                li   t1, 117                       # 'u' -> unique
                bne  t0, t1, .L54_ps_seg
                ori  s7, s7, 2
            .L54_ps_seg:
                bge  s4, s5, .L54_ps_fldend
                lbu  t0, 0(s4)
                li   t1, 44
                beq  t0, t1, .L54_ps_fldend
                addi s4, s4, 1
                j    .L54_ps_seg
            .L54_ps_fldend:
                sw   s8, 12(s6)
                sw   s7, 16(s6)
                bge  s4, s5, .L54_ps_done
                lbu  t0, 0(s4)
                li   t1, 44
                bne  t0, t1, .L54_ps_done
                addi s4, s4, 1
                addi s1, s1, 1
                j    .L54_ps_fld
            .L54_ps_done:
                addi a2, s1, 1                     # nFields
                mv   a0, s2                        # ftab
                mv   a1, s3                        # nbuf
                li   a3, 0                         # pkIndex default 0
                li   t0, 0
            .L54_ps_pk:
                bge  t0, a2, .L54_ps_pk_done
                slli t1, t0, 5
                add  t1, s2, t1
                lw   t2, 16(t1)
                andi t2, t2, 1
                beqz t2, .L54_ps_pk_next
                mv   a3, t0                        # primeiro :generated
                j    .L54_ps_pk_done
            .L54_ps_pk_next:
                addi t0, t0, 1
                j    .L54_ps_pk
            .L54_ps_pk_done:
                ld   ra, 88(sp)
                ld   s0, 80(sp)
                ld   s1, 72(sp)
                ld   s2, 64(sp)
                ld   s3, 56(sp)
                ld   s4, 48(sp)
                ld   s5, 40(sp)
                ld   s6, 32(sp)
                ld   s7, 24(sp)
                ld   s8, 16(sp)
                ld   s9, 8(sp)
                addi sp, sp, 96
                ret

            # ---------------------------------------------------------------
            # kof_orm_delete(id*, key*, table*, schema*) -> Bool (a0)
            # slot: 0 &stmt
            # ---------------------------------------------------------------
            .globl kof_orm_delete
            .type kof_orm_delete, @function
            kof_orm_delete:
                addi sp, sp, -112
                sd   ra, 104(sp)
                sd   s0, 96(sp)
                sd   s1, 88(sp)
                sd   s2, 80(sp)
                sd   s3, 72(sp)
                sd   s4, 64(sp)
                sd   s5, 56(sp)
                sd   s6, 48(sp)
                sd   s7, 40(sp)
                sd   s8, 32(sp)
                sd   s9, 24(sp)
                sd   s10, 16(sp)
                mv   s2, a1                        # key (box/KofString/null)
                mv   s3, a2                        # table
                mv   s4, a3                        # schema
                sd   a0, 8(sp)                     # id (S5.5 fatia 3)
                call kof_db_type                   # 2 -> wire mysql
                li   t0, 2
                beq  a0, t0, .L54_del_mysql
                ld   a0, 8(sp)
                call .L54_conn
                mv   s0, a0                        # conn
                mv   a0, s4
                call kof_orm_parse_schema                       # a0=ftab a1=nbuf a2=nF a3=pkIdx
                mv   s5, a0                        # ftab
                mv   s1, a3                        # pkIndex
                slli t0, s1, 5
                add  s6, s5, t0                    # entry da PK
                # cap = 28 + tblLen + pkLen
                lw   t0, 16(s3)
                lw   t1, 8(s6)
                add  t0, t0, t1
                addi t0, t0, 28
                mv   a0, t0
                call kof_alloc
                mv   s8, a0                        # base
                mv   s9, a0                        # cursor
                la   a0, .L54_d1
                li   a1, 12
                call .L54_ap
                mv   a0, s3
                call .L54_qq                       # "t"
                la   a0, .L54_w
                li   a1, 7
                call .L54_ap
                ld   a0, 0(s6)                     # pk namePtr
                lw   a1, 8(s6)                     # pk nameLen
                call .L54_qraw                     # "pk"
                la   a0, .L54_q
                li   a1, 4
                call .L54_ap
                sb   zero, 0(s9)                   # NUL p/ o prepare
                # ---- prepare --------------------------------------------------
                sd   zero, 0(sp)
                mv   a0, s0
                mv   a1, s8
                li   a2, -1
                mv   a3, sp
                li   a4, 0
                call sqlite3_prepare_v2
                bnez a0, .L54_prep_fail
                ld   s7, 0(sp)                     # stmt (0 = falhou)
                beqz s7, .L54_prep_fail
                # ---- bind key (classificador do Orm7/count_where) --------------
                beqz s2, .L54_bnull
                la   t0, .L54_magic
                ld   t0, 0(t0)
                ld   t1, 0(s2)
                bne  t0, t1, .L54_strchk
                lw   t1, 8(s2)                     # tag
                beqz t1, .L54_bint
                li   t2, 2
                beq  t1, t2, .L54_bquad
                li   t2, 3
                beq  t1, t2, .L54_bquad
                li   t2, 4
                beq  t1, t2, .L54_bdbl
                li   t2, 5
                beq  t1, t2, .L54_bflt
                j    .L54_bnull                    # tag fora -> bind_null (x86)
            .L54_bint:
                lw   a2, 16(s2)                    # sign-extend (movslq do x86)
                mv   a0, s7
                li   a1, 1
                call sqlite3_bind_int64
                j    .L54_go
            .L54_bquad:
                ld   a2, 16(s2)
                mv   a0, s7
                li   a1, 1
                call sqlite3_bind_int64
                j    .L54_go
            .L54_bdbl:
                ld   t0, 16(s2)
                fmv.d.x fa0, t0
                mv   a0, s7
                li   a1, 1
                call sqlite3_bind_double
                j    .L54_go
            .L54_bflt:
                lw   t0, 16(s2)
                fmv.w.x fa0, t0
                fcvt.d.s fa0, fa0                  # widen p/ REAL como o host
                mv   a0, s7
                li   a1, 1
                call sqlite3_bind_double
                j    .L54_go
            .L54_strchk:
                lw   t0, 0(s2)                     # KofString tag (1,0,0)?
                li   t1, 1
                bne  t0, t1, .L54_bnull
                lw   t0, 4(s2)
                bnez t0, .L54_bnull
                ld   t0, 8(s2)
                bnez t0, .L54_bnull
                addi a2, s2, 24
                lw   a3, 16(s2)
                li   a4, -1                        # SQLITE_TRANSIENT
                mv   a0, s7
                li   a1, 1
                call sqlite3_bind_text
                j    .L54_go
            .L54_bnull:
                mv   a0, s7
                li   a1, 1
                call sqlite3_bind_null
            # ---- step: DONE(101) -> true sempre (execute1 >= 0 do host) -----
            .L54_go:
                mv   a0, s7
                call sqlite3_step
                li   t0, 101                       # SQLITE_DONE
                bne  a0, t0, .L54_sql_fail
                mv   a0, s7
                call sqlite3_finalize
                li   a0, 1
                j    .L54_out
            .L54_sql_fail:
                mv   a0, s7
                call sqlite3_finalize
            .L54_prep_fail:
                mv   a0, s0
                call sqlite3_errmsg
                mv   s10, a0
                beqz s10, .L54_pre_only
                li   t1, 0
                mv   t0, s10
            .L54_slen:
                lbu  t2, 0(t0)
                beqz t2, .L54_slen_done
                addi t0, t0, 1
                addi t1, t1, 1
                j    .L54_slen
            .L54_slen_done:
                mv   a0, s10
                mv   a1, t1
                call kof_string_from_literal
                mv   s10, a0                       # msg KofString
                la   a0, .L54_pre_c
                li   a1, 8
                call kof_string_from_literal
                mv   a1, s10
                call kof_string_concat             # "sqlite: "+errmsg
                call kof_throw_string
            .L54_pre_only:
                la   a0, .L54_pre_c
                li   a1, 8
                call kof_string_from_literal
                call kof_throw_string
            .L54_out:
                ld   ra, 104(sp)
                ld   s0, 96(sp)
                ld   s1, 88(sp)
                ld   s2, 80(sp)
                ld   s3, 72(sp)
                ld   s4, 64(sp)
                ld   s5, 56(sp)
                ld   s6, 48(sp)
                ld   s7, 40(sp)
                ld   s8, 32(sp)
                ld   s9, 24(sp)
                ld   s10, 16(sp)
                addi sp, sp, 112
                ret
            # S5.5 fatia 3: DELETE FROM `t` WHERE `pk` = <lit> no wire mysql
            # (peça B75; o literal do key sai do renderizador compartilhado).
            .L54_del_mysql:
                ld   t0, 8(sp)                     # id (spill pós-atribuição)
                mv   t1, s2                        # key (vivo; o spill em 80(sp)
                mv   t2, s3                        # é do s2 do CALLER)
                mv   t3, s4                        # table / schema vivos
                ld   ra, 104(sp)
                ld   s0, 96(sp)
                ld   s1, 88(sp)
                ld   s2, 80(sp)
                ld   s3, 72(sp)
                ld   s4, 64(sp)
                ld   s5, 56(sp)
                ld   s6, 48(sp)
                ld   s7, 40(sp)
                ld   s8, 32(sp)
                ld   s9, 24(sp)
                ld   s10, 16(sp)
                addi sp, sp, 112
                mv   a0, t0
                mv   a1, t1
                mv   a2, t2
                mv   a3, t3
                j    kof_orm_delete_mysql

            .section .rodata
            .L54_bc_pre:
                .ascii "unknown db connection: "
            .L54_d1:
                .ascii "DELETE FROM "
            .L54_w:
                .ascii " WHERE "
            .L54_q:
                .ascii " = ?"
            .L54_pre_c:
                .ascii "sqlite: "
            .L54_magic:
                .quad 0x4B4F46425F425801
            .section .text
            """;
}
