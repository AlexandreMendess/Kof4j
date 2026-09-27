package dev.kof.compiler.runtime;

/**
 * F2d4a (D-DB-GAPS DB-3, 21/09): {@code kof_orm_all} row-object de LEITURA
 * em {@code List} sobre o wire MySQL do Native x86-64. Mesmo walk de pacotes
 * do F2d3b ({@code RuntimeOrmMysqlFind}): COM_QUERY de
 * {@code SELECT * FROM `t`}, colunas casadas por NOME contra o schema,
 * record construido por linha via {@code kof_orm_ctors} (typeCode do schema;
 * NULL -> 0/null/false, §397 do bool) e acumulado com
 * {@code kof_list_new}/{@code kof_list_add}. Lista VAZIA (nunca null) quando
 * nao ha linhas, como o host {@code kof_orm_all}. ERR do servidor ->
 * throw "mysql: &lt;msg&gt;"; campo do schema sem coluna -> throw
 * "mysql: no column &lt;nome&gt;" (R6 — nunca silencio).
 *
 * <p>O dispatch de tipo mora no {@code kof_orm_all} (RuntimeOrm6), que
 * tail-chama {@code .Lorm_all_my} quando {@code kof_db_type(id)==2}; os
 * helpers {@code .Lorm_rd_*} vivem no RuntimeOrmMysqlKeyLit, emitido no
 * mesmo .s por NativeOrmEmit.
 *
 * <p>Frame 552 (8 mod 16): 0 id | 8 table | 16 schema | 24 className |
 * 32 fd | 40 (livre) | 48 ftab | 56 nFields | 64 (livre) | 72 sql |
 * 80 list | 88 dst | 96 rowcur | 104 nCols | 112 i | 120 entry |
 * 128 (livre) | 136 nameLen | 144 vtab | 152 typeId | 160 totalSize |
 * 168 (livre) | 176 vallen (-1 = NULL) | 192 colField[64] (192..447) |
 * 456 msglen | 464 scratch numerico[64] (464..527).
 */
public final class RuntimeOrmMysqlAll {

    private RuntimeOrmMysqlAll() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            # ============ kof.orm all mysql wire (F2d4a) =================

            # .Lorm_all_my(rdi=id, rsi=table, rdx=schema, rcx=className)
            #   -> rax list* (vazia se zero linhas)
            .Lorm_all_my:
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
                movq %rcx, 24(%rsp)
                movq 0(%rsp), %rdi
                call kof_db_resolve
                testq %rax, %rax
                jz .Lorm_am_badconn
                movq %rax, 32(%rsp)
                movq 16(%rsp), %rdi
                call kof_orm_parse_schema
                movq %rax, 48(%rsp)               # ftab
                movq %rcx, 56(%rsp)               # nFields
            # ---- resolve ctor UMA vez (className -> vtab/typeId/totalSize) --
                movq 24(%rsp), %rax
                leaq 24(%rax), %rdi               # corpo INLINE do KofString
                movl 16(%rax), %esi               # len
                call kof_orm_ctors
                testq %rax, %rax
                jz .Lorm_am_noface
                movq %rax, 144(%rsp)              # vtab
                movq %rdx, 152(%rsp)              # typeId
                movq %rcx, 160(%rsp)              # totalSize
                call kof_list_new
                movq %rax, 80(%rsp)               # lista destino
            # ---- SQL: SELECT * FROM `t` -----------------------------------
                movq 8(%rsp), %rax
                movl 16(%rax), %eax               # tblLen
                movl %eax, %edi
                addl $16, %edi
                call .Lorm_bbegin
                leaq .Lorm_am_s1(%rip), %rsi
                movl $14, %ecx
                call .Lorm_bp
                movl $96, %r8d                    # '`'
                call .Lorm_bh
                movq 8(%rsp), %r13
                leaq 24(%r13), %rsi
                movl 16(%r13), %ecx
                call .Lorm_bp
                movl $96, %r8d
                call .Lorm_bh
                call .Lorm_bfin
                movq %rbx, 72(%rsp)               # sql
            # ---- COM_QUERY ------------------------------------------------
                movq 32(%rsp), %rbx
                movq 72(%rsp), %r12
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
                jle .Lorm_am_dead
                cmpb $0xFF, (%rsi)
                je .Lorm_am_err
                call kof_db_mysql_lenenc         # col count
                movl %eax, 104(%rsp)
                cmpl $64, %eax
                jle .Lorm_am_cc_ok
                movl $64, 104(%rsp)              # excedente de 64: ignorado
            .Lorm_am_cc_ok:
                xorl %ebx, %ebx                  # colj
            .Lorm_am_cols:
                cmpl 104(%rsp), %ebx
                jge .Lorm_am_cols_done
                call kof_db_mysql_next
                testq %rax, %rax
                jle .Lorm_am_dead
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
                movl %eax, 136(%rsp)
                movl $-1, 192(%rsp,%rbx,4)       # match default
                # casa o nome contra os campos do schema
                xorl %ecx, %ecx
            .Lorm_am_match:
                cmpq 56(%rsp), %rcx
                jge .Lorm_am_nextcol
                movq %rcx, %rdx
                shlq $5, %rdx
                addq 48(%rsp), %rdx              # entry
                movl 8(%rdx), %r8d               # field name len
                cmpl %r8d, 136(%rsp)
                jne .Lorm_am_matchnext
                movq 0(%rdx), %r9                # field name ptr
                xorl %r10d, %r10d
            .Lorm_am_cmp:
                cmpl %r8d, %r10d
                jge .Lorm_am_matched
                movzbl (%r12,%r10), %r11d
                movzbl (%r9,%r10), %eax
                cmpl %eax, %r11d
                jne .Lorm_am_matchnext
                incl %r10d
                jmp .Lorm_am_cmp
            .Lorm_am_matched:
                movl %ecx, 192(%rsp,%rbx,4)
                jmp .Lorm_am_nextcol
            .Lorm_am_matchnext:
                incq %rcx
                jmp .Lorm_am_match
            .Lorm_am_nextcol:
                call kof_db_mysql_lenenc         # skip org_name
                addq %rax, %rsi
                incl %ebx
                jmp .Lorm_am_cols
            .Lorm_am_cols_done:
                # pacote apos colunas: 0xFE (EOF) ou 0x00 (OK, sem resultset)
                call kof_db_mysql_next
                testq %rax, %rax
                jle .Lorm_am_dead
                cmpb $0x00, (%rsi)
                je .Lorm_am_ok                   # sem resultset: lista vazia
            # ---- campo do schema sem coluna -> throw ----------------------
                movq $0, 112(%rsp)
            .Lorm_am_chkfld:
                movq 112(%rsp), %rax
                cmpq 56(%rsp), %rax
                jge .Lorm_am_rows
                xorl %ecx, %ecx
            .Lorm_am_chkcol:
                cmpl 104(%rsp), %ecx
                jge .Lorm_am_noch
                movl 192(%rsp,%rcx,4), %edx
                cmpl %eax, %edx
                je .Lorm_am_chkok
                incl %ecx
                jmp .Lorm_am_chkcol
            .Lorm_am_chkok:
                incq 112(%rsp)
                jmp .Lorm_am_chkfld
            .Lorm_am_noch:
                movq 112(%rsp), %rax
                shlq $5, %rax
                addq 48(%rsp), %rax
                movq 0(%rax), %r12               # name ptr
                movl 8(%rax), %r13d              # name len
                movl $26, %edi                   # 8 + 18
                addl %r13d, %edi
                call .Lorm_bbegin
                leaq .Lorm_am_nc(%rip), %rsi
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
            .Lorm_am_rows:
                call kof_db_mysql_next
                testq %rax, %rax
                jle .Lorm_am_dead
                movzbl (%rsi), %eax
                cmpb $0xFF, %al                  # ERR
                je .Lorm_am_err
                cmpb $0xFE, %al                  # EOF: fim das linhas
                je .Lorm_am_ok
                movq %rsi, 96(%rsp)              # rowcur
                movq 160(%rsp), %rdi
                call kof_alloc
                movq %rax, 88(%rsp)              # dst (record novo por linha)
                movq 88(%rsp), %rdi
                movl 152(%rsp), %esi
                movq 144(%rsp), %rdx
                call kof_init_object
                xorl %ebx, %ebx                  # colj
            .Lorm_am_rcol:
                cmpl 104(%rsp), %ebx
                jge .Lorm_am_rowdone
                movq 96(%rsp), %rsi
                movzbl (%rsi), %eax
                cmpb $0xFB, %al                  # NULL
                je .Lorm_am_rnull
                call kof_db_mysql_lenenc
                movl %eax, 176(%rsp)             # vallen
                leaq (%rsi,%rax), %r10
                movq %r10, 96(%rsp)              # cursor apos o valor
                jmp .Lorm_am_rconv
            .Lorm_am_rnull:
                incq %rsi
                movq %rsi, 96(%rsp)
                movl $-1, 176(%rsp)              # -1 = NULL
            .Lorm_am_rconv:
                movl 192(%rsp,%rbx,4), %eax      # campo casado
                cmpl $0, %eax
                jl .Lorm_am_rnext
                movq %rax, %rdx
                shlq $5, %rdx
                addq 48(%rsp), %rdx              # entry
                movq %rdx, 120(%rsp)
                movq 88(%rsp), %rcx              # dst
                shlq $3, %rax
                addq $16, %rax
                addq %rcx, %rax
                movq %rax, %r14                  # slot do campo
                cmpl $0, 176(%rsp)
                jl .Lorm_am_vnull
                movq 120(%rsp), %rax
                movl 12(%rax), %eax              # typeCode
                cmpl $2, %eax
                je .Lorm_am_vstr
                cmpl $3, %eax
                je .Lorm_am_vbool
                cmpl $4, %eax
                je .Lorm_am_vdbl
                cmpl $5, %eax
                je .Lorm_am_vflt
                cmpl $1, %eax
                je .Lorm_am_vlng
                # int (e default)
                movq %rsi, %rdi
                movl 176(%rsp), %esi
                call .Lorm_rd_atoi
                movq %rax, (%r14)
                jmp .Lorm_am_rnext
            .Lorm_am_vlng:
                movq %rsi, %rdi
                movl 176(%rsp), %esi
                call .Lorm_rd_atoi
                movq %rax, (%r14)
                jmp .Lorm_am_rnext
            .Lorm_am_vstr:
                movq %rsi, %rdi
                movl 176(%rsp), %esi
                call kof_io_make_string
                movq %rax, (%r14)
                jmp .Lorm_am_rnext
            .Lorm_am_vbool:
                movq %rsi, %rdi
                movl 176(%rsp), %esi
                call .Lorm_rd_bool
                movq %rax, (%r14)
                jmp .Lorm_am_rnext
            .Lorm_am_vdbl:
                movq %rsi, %rdi
                movl 176(%rsp), %esi
                leaq 464(%rsp), %rdx
                call .Lorm_rd_copy_num
                movq %rax, %rdi
                call strtod
                movq %xmm0, (%r14)
                jmp .Lorm_am_rnext
            .Lorm_am_vflt:
                movq %rsi, %rdi
                movl 176(%rsp), %esi
                leaq 464(%rsp), %rdx
                call .Lorm_rd_copy_num
                movq %rax, %rdi
                call strtod
                cvtsd2ss %xmm0, %xmm0
                movss %xmm0, (%r14)
                jmp .Lorm_am_rnext
            .Lorm_am_vnull:
                movq $0, (%r14)
            .Lorm_am_rnext:
                incl %ebx
                jmp .Lorm_am_rcol
            .Lorm_am_rowdone:
                movq 80(%rsp), %rdi
                movq 88(%rsp), %rsi
                call kof_list_add
                jmp .Lorm_am_rows
            .Lorm_am_ok:
                movq 80(%rsp), %rax
                jmp .Lorm_am_ret
            # ---- ERR do servidor -> throw "mysql: <msg>" ------------------
            .Lorm_am_err:
                movq %rax, %r13                  # packet len
                leaq 9(%rsi), %r12               # msg = apos ff+errno+#+state
                cmpq $9, %r13
                jle .Lorm_am_errempty
                movq %r13, %rdx
                subq $9, %rdx
                cmpq $400, %rdx
                jle .Lorm_am_errlen
                movq $400, %rdx
                jmp .Lorm_am_errlen
            .Lorm_am_errempty:
                movq $9, %r13
                movq $0, %rdx
            .Lorm_am_errlen:
                movq %rdx, 456(%rsp)
                movl $407, %edi                  # 7 + teto
                addl %edx, %edi
                call .Lorm_bbegin
                leaq .Lorm_am_my(%rip), %rsi
                movl $7, %ecx
                call .Lorm_bp
                movq 456(%rsp), %rcx
                movq %r12, %rsi
                call .Lorm_bp
                call .Lorm_bfin
                movq %rbx, %rdi
                call kof_throw_string
                ud2
            .Lorm_am_dead:
                leaq .Lorm_am_lost(%rip), %rdi
                call kof_throw_string
                ud2

            .Lorm_am_badconn:
                movq 0(%rsp), %rdi
                call .Lorm_bad_conn
                ud2
            .Lorm_am_noface:
                leaq .Lorm_am_face(%rip), %rdi
                call kof_throw_string
                ud2
            .Lorm_am_ret:
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
            .Lorm_am_s1:
                .ascii "SELECT * FROM "
            .Lorm_am_my:
                .ascii "mysql: "
            .Lorm_am_nc:
                .ascii "mysql: no column "
            .Lorm_am_lost:
                .long 1
                .long 0
                .quad 0
                .long .Lorm_am_lost_len
                .long 0
            .Lorm_am_lost_body:
                .ascii "mysql: connection lost"
                .byte 0
                .set .Lorm_am_lost_len, . - .Lorm_am_lost_body - 1
            .Lorm_am_face:
                .long 1
                .long 0
                .quad 0
                .long .Lorm_am_face_len
                .long 0
            .Lorm_am_face_body:
                .ascii "orm.all: entity class not registered (ORM001)"
                .byte 0
                .set .Lorm_am_face_len, . - .Lorm_am_face_body - 1
            """);
    }
}
