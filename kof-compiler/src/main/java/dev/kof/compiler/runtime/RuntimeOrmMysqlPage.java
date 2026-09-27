package dev.kof.compiler.runtime;

/**
 * F2d4c (D-DB-GAPS DB-3, 21/09): {@code kof_orm_page} row-object em
 * {@code List} sobre o wire MySQL do Native x86-64. SQL do host
 * ({@code JvmOrmRuntime}): {@code SELECT * FROM `t` LIMIT ? OFFSET ?} com
 * lim/off convertidos como o host ({@code ((Number) x).intValue()} — o
 * classificador {@code .Lorm8_pv} do RuntimeOrm8, ja medido no F2c3, cobre
 * box int/long/double/float, KofString do coerce = atoi e null = 0) e
 * renderizados como literal numerico no SQL ({@code kof_long_to_string}).
 * O resultset e percorrido pacote a pacote (colunas casadas por NOME, um
 * record por linha por {@code kof_orm_ctors}, lista VAZIA se a pagina nao
 * tiver linhas — nunca null, como o host). ERR do servidor -&gt; throw
 * {@code "mysql: <msg>"}; campo sem coluna -&gt; throw
 * {@code "mysql: no column <nome>"} (R6).
 *
 * <p>O dispatch de tipo mora no {@code kof_orm_page} (RuntimeOrm8), que
 * tail-chama {@code .Lorm_page_my} quando {@code kof_db_type(id)==2} (mesma
 * ABI de 6 registradores da face sqlite); os helpers {@code .Lorm_rd_*}
 * vivem no RuntimeOrmMysqlKeyLit, emitido no mesmo .s por NativeOrmEmit.
 *
 * <p>Frame 552 (8 mod 16): 0 id | 8 limit | 16 offset | 24 limstr |
 * 32 table | 40 schema | 48 className | 56 fd | 64 ftab | 72 nFields |
 * 80 sql | 88 list | 96 dst | 104 rowcur | 112 nCols | 120 i | 128 entry |
 * 136 colj | 144 nameLen | 152 vtab | 160 typeId | 168 totalSize |
 * 176 vallen (-1 = NULL) | 184 offstr | 200 msglen |
 * 208 colField[64] (208..463) | 464 scratch[64] (464..527).
 */
public final class RuntimeOrmMysqlPage {

    private RuntimeOrmMysqlPage() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            # ============ kof.orm page mysql wire (F2d4c) ================

            # .Lorm_page_my(rdi=id, rsi=limit, rdx=offset, rcx=table,
            #               r8=schema, r9=className) -> rax list*
            .Lorm_page_my:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $552, %rsp
                movq %rdi, 0(%rsp)
                movq %rsi, 8(%rsp)
                movq %rdx, 16(%rsp)
                movq %rcx, 32(%rsp)
                movq %r8, 40(%rsp)
                movq %r9, 48(%rsp)
                movq 0(%rsp), %rdi
                call kof_db_resolve
                testq %rax, %rax
                jz .Lorm_wp_badconn
                movq %rax, 56(%rsp)
                movq 40(%rsp), %rdi
                call kof_orm_parse_schema
                movq %rax, 64(%rsp)              # ftab
                movq %rcx, 72(%rsp)              # nFields
            # ---- resolve ctor UMA vez (className -> vtab/typeId/totalSize) --
                movq 48(%rsp), %rax
                leaq 24(%rax), %rdi              # corpo INLINE do KofString
                movl 16(%rax), %esi              # len
                call kof_orm_ctors
                testq %rax, %rax
                jz .Lorm_wp_noface
                movq %rax, 152(%rsp)             # vtab
                movq %rdx, 160(%rsp)             # typeId
                movq %rcx, 168(%rsp)             # totalSize
                call kof_list_new
                movq %rax, 88(%rsp)              # lista destino
            # ---- lim/off: .Lorm8_pv (host intValue) -> literal numerico ----
                movq 8(%rsp), %rdi
                call .Lorm8_pv
                movq %rax, %rdi
                call kof_long_to_string
                movq %rax, 24(%rsp)              # limstr
                movq 16(%rsp), %rdi
                call .Lorm8_pv
                movq %rax, %rdi
                call kof_long_to_string
                movq %rax, 184(%rsp)             # offstr
            # ---- SQL: SELECT * FROM `t` LIMIT <lim> OFFSET <off> ----------
                movq 32(%rsp), %rax
                movl 16(%rax), %edi              # tblLen
                movq 24(%rsp), %rcx
                addl 16(%rcx), %edi              # + limLen
                movq 184(%rsp), %rcx
                addl 16(%rcx), %edi              # + offLen
                addl $64, %edi
                call .Lorm_bbegin
                leaq .Lorm_wp_s1(%rip), %rsi
                movl $14, %ecx
                call .Lorm_bp
                movl $96, %r8d                   # '`'
                call .Lorm_bh
                movq 32(%rsp), %r13
                leaq 24(%r13), %rsi
                movl 16(%r13), %ecx
                call .Lorm_bp
                movl $96, %r8d
                call .Lorm_bh
                leaq .Lorm_wp_s2(%rip), %rsi     # " LIMIT "
                movl $7, %ecx
                call .Lorm_bp
                movq 24(%rsp), %r13
                leaq 24(%r13), %rsi
                movl 16(%r13), %ecx
                call .Lorm_bp
                leaq .Lorm_wp_s3(%rip), %rsi     # " OFFSET "
                movl $8, %ecx
                call .Lorm_bp
                movq 184(%rsp), %r13
                leaq 24(%r13), %rsi
                movl 16(%r13), %ecx
                call .Lorm_bp
                call .Lorm_bfin
                movq %rbx, 80(%rsp)              # sql
            # ---- COM_QUERY ------------------------------------------------
                movq 56(%rsp), %rbx
                movq 80(%rsp), %r12
                leaq .Ldb_mysql_buf(%rip), %r13
                movb $0x03, 4(%r13)
                leaq 24(%r12), %rsi
                movl 16(%r12), %ecx
                movq %rcx, %rdx
                leaq 5(%r13), %rdi
                call kof_memcpy
                leal 1(%ecx), %eax
                movb %al, 0(%r13)
                shrl $8, %eax
                movb %al, 1(%r13)
                shrl $8, %eax
                movb %al, 2(%r13)
                movb $0, 3(%r13)
                movq %rbx, %rdi
                movq %r13, %rsi
                leaq 5(%rcx), %rdx
                call kof_net_write
                movq %rbx, %rdi
                call kof_db_mysql_reset
                call kof_db_mysql_next
                testq %rax, %rax
                jle .Lorm_wp_dead
                cmpb $0xFF, (%rsi)
                je .Lorm_wp_err
                call kof_db_mysql_lenenc         # col count
                movl %eax, 112(%rsp)
                cmpl $64, %eax
                jle .Lorm_wp_cc_ok
                movl $64, 112(%rsp)              # excedente de 64: ignorado
            .Lorm_wp_cc_ok:
                xorl %ebx, %ebx                  # colj
            .Lorm_wp_cols:
                cmpl 112(%rsp), %ebx
                jge .Lorm_wp_cols_done
                call kof_db_mysql_next
                testq %rax, %rax
                jle .Lorm_wp_dead
                # pula catalog, schema, table, org_table (lenenc: len + addq)
                call kof_db_mysql_lenenc
                addq %rax, %rsi
                call kof_db_mysql_lenenc
                addq %rax, %rsi
                call kof_db_mysql_lenenc
                addq %rax, %rsi
                call kof_db_mysql_lenenc
                addq %rax, %rsi
                call kof_db_mysql_lenenc         # name
                movq %rsi, %r12
                movl %eax, 144(%rsp)
                movl $-1, 208(%rsp,%rbx,4)       # match default
                # casa o nome contra os campos do schema
                xorl %ecx, %ecx
            .Lorm_wp_match:
                cmpq 72(%rsp), %rcx
                jge .Lorm_wp_nextcol
                movq %rcx, %rdx
                shlq $5, %rdx
                addq 64(%rsp), %rdx              # entry
                movl 8(%rdx), %r8d               # field name len
                cmpl %r8d, 144(%rsp)
                jne .Lorm_wp_matchnext
                movq 0(%rdx), %r9                # field name ptr
                xorl %r10d, %r10d
            .Lorm_wp_cmp:
                cmpl %r8d, %r10d
                jge .Lorm_wp_matched
                movzbl (%r12,%r10), %r11d
                movzbl (%r9,%r10), %eax
                cmpl %eax, %r11d
                jne .Lorm_wp_matchnext
                incl %r10d
                jmp .Lorm_wp_cmp
            .Lorm_wp_matched:
                movl %ecx, 208(%rsp,%rbx,4)
                jmp .Lorm_wp_nextcol
            .Lorm_wp_matchnext:
                incq %rcx
                jmp .Lorm_wp_match
            .Lorm_wp_nextcol:
                call kof_db_mysql_lenenc         # skip org_name
                addq %rax, %rsi
                incl %ebx
                jmp .Lorm_wp_cols
            .Lorm_wp_cols_done:
                # pacote apos colunas: 0xFE (EOF) ou 0x00 (OK, sem resultset)
                call kof_db_mysql_next
                testq %rax, %rax
                jle .Lorm_wp_dead
                cmpb $0x00, (%rsi)
                je .Lorm_wp_ok                   # sem resultset: lista vazia
            # ---- campo do schema sem coluna -> throw ----------------------
                movq $0, 120(%rsp)
            .Lorm_wp_chkfld:
                movq 120(%rsp), %rax
                cmpq 72(%rsp), %rax
                jge .Lorm_wp_rows
                xorl %ecx, %ecx
            .Lorm_wp_chkcol:
                cmpl 112(%rsp), %ecx
                jge .Lorm_wp_noch
                movl 208(%rsp,%rcx,4), %edx
                cmpl %eax, %edx
                je .Lorm_wp_chkok
                incl %ecx
                jmp .Lorm_wp_chkcol
            .Lorm_wp_chkok:
                incq 120(%rsp)
                jmp .Lorm_wp_chkfld
            .Lorm_wp_noch:
                movq 120(%rsp), %rax
                shlq $5, %rax
                addq 64(%rsp), %rax
                movq 0(%rax), %r12               # name ptr
                movl 8(%rax), %r13d              # name len
                movl $28, %edi                   # 10 + 18
                addl %r13d, %edi
                call .Lorm_bbegin
                leaq .Lorm_wp_nc(%rip), %rsi
                movl $18, %ecx
                call .Lorm_bp
                movq %r12, %rsi
                movl %r13d, %ecx
                call .Lorm_bp
                call .Lorm_bfin
                movq %rbx, %rdi
                call kof_throw_string
                ud2
            # ---- loop de linhas -------------------------------------------
            .Lorm_wp_rows:
                call kof_db_mysql_next
                testq %rax, %rax
                jle .Lorm_wp_dead
                movzbl (%rsi), %eax
                cmpb $0xFF, %al                  # ERR
                je .Lorm_wp_err
                cmpb $0xFE, %al                  # EOF: fim das linhas
                je .Lorm_wp_ok
                movq %rsi, 104(%rsp)             # rowcur
                movq 168(%rsp), %rdi
                call kof_alloc
                movq %rax, 96(%rsp)              # dst (record novo por linha)
                movq 96(%rsp), %rdi
                movl 160(%rsp), %esi
                movq 152(%rsp), %rdx
                call kof_init_object
                xorl %ebx, %ebx                  # colj
            .Lorm_wp_rcol:
                cmpl 112(%rsp), %ebx
                jge .Lorm_wp_rowdone
                movq 104(%rsp), %rsi
                movzbl (%rsi), %eax
                cmpb $0xFB, %al                  # NULL
                je .Lorm_wp_rnull
                call kof_db_mysql_lenenc
                movl %eax, 176(%rsp)             # vallen
                leaq (%rsi,%rax), %r10
                movq %r10, 104(%rsp)             # cursor apos o valor
                jmp .Lorm_wp_rconv
            .Lorm_wp_rnull:
                incq %rsi
                movq %rsi, 104(%rsp)
                movl $-1, 176(%rsp)              # -1 = NULL
            .Lorm_wp_rconv:
                movl 208(%rsp,%rbx,4), %eax      # campo casado
                cmpl $0, %eax
                jl .Lorm_wp_rnext
                movq %rax, %rdx
                shlq $5, %rdx
                addq 64(%rsp), %rdx              # entry
                movq %rdx, 128(%rsp)
                movq 96(%rsp), %rcx              # dst
                shlq $3, %rax
                addq $16, %rax
                addq %rcx, %rax
                movq %rax, %r14                  # slot do campo
                cmpl $0, 176(%rsp)
                jl .Lorm_wp_vnull
                movq 128(%rsp), %rax
                movl 12(%rax), %eax              # typeCode
                cmpl $2, %eax
                je .Lorm_wp_vstr
                cmpl $3, %eax
                je .Lorm_wp_vbool
                cmpl $4, %eax
                je .Lorm_wp_vdbl
                cmpl $5, %eax
                je .Lorm_wp_vflt
                cmpl $1, %eax
                je .Lorm_wp_vlng
                # int (e default)
                movq %rsi, %rdi
                movl 176(%rsp), %esi
                call .Lorm_rd_atoi
                movq %rax, (%r14)
                jmp .Lorm_wp_rnext
            .Lorm_wp_vlng:
                movq %rsi, %rdi
                movl 176(%rsp), %esi
                call .Lorm_rd_atoi
                movq %rax, (%r14)
                jmp .Lorm_wp_rnext
            .Lorm_wp_vstr:
                movq %rsi, %rdi
                movl 176(%rsp), %esi
                call kof_io_make_string
                movq %rax, (%r14)
                jmp .Lorm_wp_rnext
            .Lorm_wp_vbool:
                movq %rsi, %rdi
                movl 176(%rsp), %esi
                call .Lorm_rd_bool
                movq %rax, (%r14)
                jmp .Lorm_wp_rnext
            .Lorm_wp_vdbl:
                movq %rsi, %rdi
                movl 176(%rsp), %esi
                leaq 464(%rsp), %rdx
                call .Lorm_rd_copy_num
                movq %rax, %rdi
                call strtod
                movq %xmm0, (%r14)
                jmp .Lorm_wp_rnext
            .Lorm_wp_vflt:
                movq %rsi, %rdi
                movl 176(%rsp), %esi
                leaq 464(%rsp), %rdx
                call .Lorm_rd_copy_num
                movq %rax, %rdi
                call strtod
                cvtsd2ss %xmm0, %xmm0
                movss %xmm0, (%r14)
                jmp .Lorm_wp_rnext
            .Lorm_wp_vnull:
                movq $0, (%r14)
            .Lorm_wp_rnext:
                incl %ebx
                jmp .Lorm_wp_rcol
            .Lorm_wp_rowdone:
                movq 88(%rsp), %rdi
                movq 96(%rsp), %rsi
                call kof_list_add
                jmp .Lorm_wp_rows
            .Lorm_wp_ok:
                movq 88(%rsp), %rax
                jmp .Lorm_wp_ret
            # ---- ERR do servidor -> throw "mysql: <msg>" ------------------
            .Lorm_wp_err:
                movq %rax, %r13                  # packet len
                leaq 9(%rsi), %r12               # msg = apos ff+errno+#+state
                cmpq $9, %r13
                jle .Lorm_wp_errempty
                movq %r13, %rdx
                subq $9, %rdx
                cmpq $400, %rdx
                jle .Lorm_wp_errlen
                movq $400, %rdx
                jmp .Lorm_wp_errlen
            .Lorm_wp_errempty:
                movq $9, %r13
                movq $0, %rdx
            .Lorm_wp_errlen:
                movq %rdx, 200(%rsp)
                movl $407, %edi                  # 7 + teto
                addl %edx, %edi
                call .Lorm_bbegin
                leaq .Lorm_wp_my(%rip), %rsi
                movl $7, %ecx
                call .Lorm_bp
                movq 200(%rsp), %rcx
                movq %r12, %rsi
                call .Lorm_bp
                call .Lorm_bfin
                movq %rbx, %rdi
                call kof_throw_string
                ud2
            .Lorm_wp_dead:
                leaq .Lorm_wp_lost(%rip), %rdi
                call kof_throw_string
                ud2

            .Lorm_wp_badconn:
                movq 0(%rsp), %rdi
                call .Lorm_bad_conn
                ud2
            .Lorm_wp_noface:
                leaq .Lorm_wp_face(%rip), %rdi
                call kof_throw_string
                ud2
            .Lorm_wp_ret:
                addq $552, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret

            # ---------------------- literais --------------------------------
            .Lorm_wp_s1:
                .ascii "SELECT * FROM "
            .Lorm_wp_s2:
                .ascii " LIMIT "
            .Lorm_wp_s3:
                .ascii " OFFSET "
            .Lorm_wp_my:
                .ascii "mysql: "
            .Lorm_wp_nc:
                .ascii "mysql: no column "
            .Lorm_wp_lost:
                .long 1
                .long 0
                .quad 0
                .long .Lorm_wp_lost_len
                .long 0
            .Lorm_wp_lost_body:
                .ascii "mysql: connection lost"
                .byte 0
                .set .Lorm_wp_lost_len, . - .Lorm_wp_lost_body - 1
            .Lorm_wp_face:
                .long 1
                .long 0
                .quad 0
                .long .Lorm_wp_face_len
                .long 0
            .Lorm_wp_face_body:
                .ascii "orm.page: entity class not registered (ORM001)"
                .byte 0
                .set .Lorm_wp_face_len, . - .Lorm_wp_face_body - 1
            """);
    }
}
