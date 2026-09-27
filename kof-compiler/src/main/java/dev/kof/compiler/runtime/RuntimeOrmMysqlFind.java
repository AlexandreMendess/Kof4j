package dev.kof.compiler.runtime;

/**
 * F2d3b (D-DB-GAPS DB-3, 21/09): {@code kof_orm_find} row-object sobre o
 * wire MySQL do Native x86-64. Le o resultset do COM_QUERY (mesmo padrao
 * de pacotes do F2d2/F2d3a) e constroi o record pelo resolver
 * {@code kof_orm_ctors}, com as colunas casadas por NOME e os valores
 * convertidos pelo typeCode do schema (int/long/string/bool/double/float;
 * NULL -> 0/null/false). Miss -> null (rax=0), como o host. ERR do
 * servidor -> throw {@code "mysql: <mensagem>"} e campo sem coluna ->
 * throw {@code "mysql: no column <nome>"} (espelho do sqlite do
 * RuntimeOrm5; R6 — nunca silencio).
 *
 * <p>O key vira literal SQL pelo {@code .Lorm_key_lit} e o {@code '?'} do
 * "SELECT * FROM `t` WHERE `pk` = ?" e trocado por
 * {@code kof_db_mysql_replace_q}; os helpers de leitura de valor
 * ({@code .Lorm_rd_*}) vivem no RuntimeOrmMysqlKeyLit. Depois do primeiro
 * row o restante do resultset e drenado ate o EOF (o socket nunca fica
 * dessincronizado). Labels resolvidos no mesmo .s, emitidos juntos por
 * NativeOrmEmit; o dispatch de tipo mora no {@code kof_orm_find}
 * (RuntimeOrm5), que tail-chama {@code .Lorm_find_my} quando
 * {@code kof_db_type(id)==2}.
 *
 * <p>Frame 552 (8 mod 16): 0 id | 8 key | 16 table | 24 schema |
 * 32 className | 40 fd | 48 ftab | 56 nFields | 64 pkIndex | 72 pkEntry |
 * 80 sql | 88 dst | 96 rowcur | 104 nCols | 112 i | 120 entry | 128 (livre)
 * | 136 nameLen | 144 vtab | 152 typeId | 160 totalSize | 168 (livre) |
 * 176 vallen (-1 = NULL) | 192 colField[64] (192..447) | 456 msglen |
 * 464 scratch numerico[64] (464..527).
 */
public final class RuntimeOrmMysqlFind {

    private RuntimeOrmMysqlFind() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            # ============ kof.orm find mysql wire (F2d3b) ================

            # .Lorm_find_my(rdi=id, rsi=key, rdx=table, rcx=schema,
            #               r8=className) -> rax record*|0
            .Lorm_find_my:
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
                movq %r8, 32(%rsp)
                movq 0(%rsp), %rdi
                call kof_db_resolve
                testq %rax, %rax
                jz .Lorm_fm_badconn
                movq %rax, 40(%rsp)
                movq 24(%rsp), %rdi
                call kof_orm_parse_schema
                movq %rax, 48(%rsp)               # ftab
                movq %rcx, 56(%rsp)               # nFields
                movq %r8, 64(%rsp)                # pkIndex
                movq 64(%rsp), %rax
                shlq $5, %rax
                addq 48(%rsp), %rax
                movq %rax, 72(%rsp)               # pkEntry
            # ---- SQL: SELECT * FROM `t` WHERE `pk` = ? -------------------
                movq 16(%rsp), %rax
                movl 16(%rax), %eax
                movq 72(%rsp), %rdx
                movl 8(%rdx), %ecx
                leal (%rax,%rcx,2), %edi
                addl $48, %edi
                call .Lorm_bbegin
                leaq .Lorm_fm_s1(%rip), %rsi
                movl $14, %ecx
                call .Lorm_bp
                movl $96, %r8d                    # '`'
                call .Lorm_bh
                movq 16(%rsp), %r13
                leaq 24(%r13), %rsi
                movl 16(%r13), %ecx
                call .Lorm_bp
                movl $96, %r8d
                call .Lorm_bh
                leaq .Lorm_fm_s2(%rip), %rsi
                movl $7, %ecx
                call .Lorm_bp
                movl $96, %r8d
                call .Lorm_bh
                movq 72(%rsp), %r13
                movq 0(%r13), %rsi
                movl 8(%r13), %ecx
                call .Lorm_bp
                movl $96, %r8d
                call .Lorm_bh
                leaq .Lorm_fm_s3(%rip), %rsi
                movl $4, %ecx
                call .Lorm_bp
                call .Lorm_bfin
                movq %rbx, 80(%rsp)               # sql
            # ---- literal do key (.Lorm_key_lit em RuntimeOrmMysqlKeyLit) --
                movq 8(%rsp), %rdi
                call .Lorm_key_lit
                movq 80(%rsp), %rdi
                movq %rax, %rsi
                call kof_db_mysql_replace_q
                movq %rax, 80(%rsp)
            # ---- COM_QUERY -----------------------------------------------
                movq 40(%rsp), %rbx
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
                jle .Lorm_fm_miss
                cmpb $0xFF, (%rsi)
                je .Lorm_fm_err
                call kof_db_mysql_lenenc         # col count
                movl %eax, 104(%rsp)
                cmpl $64, %eax
                jle .Lorm_fm_cc_ok
                movl $64, 104(%rsp)              # excedente de 64: ignorado
            .Lorm_fm_cc_ok:
                xorl %ebx, %ebx                  # colj
            .Lorm_fm_cols:
                cmpl 104(%rsp), %ebx
                jge .Lorm_fm_cols_done
                call kof_db_mysql_next
                testq %rax, %rax
                jle .Lorm_fm_miss
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
            .Lorm_fm_match:
                cmpq 56(%rsp), %rcx
                jge .Lorm_fm_nextcol
                movq %rcx, %rdx
                shlq $5, %rdx
                addq 48(%rsp), %rdx              # entry
                movl 8(%rdx), %r8d               # field name len
                cmpl %r8d, 136(%rsp)
                jne .Lorm_fm_matchnext
                movq 0(%rdx), %r9                # field name ptr
                xorl %r10d, %r10d
            .Lorm_fm_cmp:
                cmpl %r8d, %r10d
                jge .Lorm_fm_matched
                movzbl (%r12,%r10), %r11d
                movzbl (%r9,%r10), %eax
                cmpl %eax, %r11d
                jne .Lorm_fm_matchnext
                incl %r10d
                jmp .Lorm_fm_cmp
            .Lorm_fm_matched:
                movl %ecx, 192(%rsp,%rbx,4)
                jmp .Lorm_fm_nextcol
            .Lorm_fm_matchnext:
                incq %rcx
                jmp .Lorm_fm_match
            .Lorm_fm_nextcol:
                call kof_db_mysql_lenenc         # skip org_name
                addq %rax, %rsi
                incl %ebx
                jmp .Lorm_fm_cols
            .Lorm_fm_cols_done:
                # pacote apos colunas: 0x00 (OK, sem resultset) ou 0xFE (EOF)
                call kof_db_mysql_next
                testq %rax, %rax
                jle .Lorm_fm_miss
                cmpb $0x00, (%rsi)
                je .Lorm_fm_miss
            # ---- campo do schema sem coluna -> throw ---------------------
                movq $0, 112(%rsp)
            .Lorm_fm_chkfld:
                movq 112(%rsp), %rax
                cmpq 56(%rsp), %rax
                jge .Lorm_fm_rows
                xorl %ecx, %ecx
            .Lorm_fm_chkcol:
                cmpl 104(%rsp), %ecx
                jge .Lorm_fm_noch
                movl 192(%rsp,%rcx,4), %edx
                cmpl %eax, %edx
                je .Lorm_fm_chkok
                incl %ecx
                jmp .Lorm_fm_chkcol
            .Lorm_fm_chkok:
                incq 112(%rsp)
                jmp .Lorm_fm_chkfld
            .Lorm_fm_noch:
                movq 112(%rsp), %rax
                shlq $5, %rax
                addq 48(%rsp), %rax
                movq 0(%rax), %r12               # name ptr
                movl 8(%rax), %r13d              # name len
                movl $25, %edi                   # 7 + 18
                addl %r13d, %edi
                call .Lorm_bbegin
                leaq .Lorm_fm_nc(%rip), %rsi
                movl $18, %ecx
                call .Lorm_bp
                movq %r12, %rsi
                movl %r13d, %ecx
                call .Lorm_bp
                call .Lorm_bfin
                movq %rbx, %rdi
                call kof_throw_string
                ud2
            # ---- primeira linha ------------------------------------------
            .Lorm_fm_rows:
                call kof_db_mysql_next
                testq %rax, %rax
                jle .Lorm_fm_miss
                movzbl (%rsi), %eax
                cmpb $0xFF, %al                  # ERR
                je .Lorm_fm_err
                cmpb $0xFE, %al                  # EOF: zero rows
                je .Lorm_fm_miss
                movq %rsi, 96(%rsp)              # rowcur
                movq 32(%rsp), %rax
                leaq 24(%rax), %rdi              # className inline no +24
                movl 16(%rax), %esi
                call kof_orm_ctors
                testq %rax, %rax
                jz .Lorm_fm_noface
                movq %rax, 144(%rsp)             # vtab
                movq %rdx, 152(%rsp)             # typeId
                movq %rcx, 160(%rsp)             # totalSize
                movq 160(%rsp), %rdi
                call kof_alloc
                movq %rax, 88(%rsp)              # dst
                movq 88(%rsp), %rdi
                movl 152(%rsp), %esi
                movq 144(%rsp), %rdx
                call kof_init_object
                xorl %ebx, %ebx                  # colj
            .Lorm_fm_rcol:
                cmpl 104(%rsp), %ebx
                jge .Lorm_fm_drain
                movq 96(%rsp), %rsi
                movzbl (%rsi), %eax
                cmpb $0xFB, %al                  # NULL
                je .Lorm_fm_rnull
                call kof_db_mysql_lenenc
                movl %eax, 176(%rsp)             # vallen
                leaq (%rsi,%rax), %r10
                movq %r10, 96(%rsp)              # cursor apos o valor
                jmp .Lorm_fm_rconv
            .Lorm_fm_rnull:
                incq %rsi
                movq %rsi, 96(%rsp)
                movl $-1, 176(%rsp)              # -1 = NULL
            .Lorm_fm_rconv:
                movl 192(%rsp,%rbx,4), %eax      # campo casado
                cmpl $0, %eax
                jl .Lorm_fm_rnext
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
                jl .Lorm_fm_vnull
                movq 120(%rsp), %rax
                movl 12(%rax), %eax              # typeCode
                cmpl $2, %eax
                je .Lorm_fm_vstr
                cmpl $3, %eax
                je .Lorm_fm_vbool
                cmpl $4, %eax
                je .Lorm_fm_vdbl
                cmpl $5, %eax
                je .Lorm_fm_vflt
                cmpl $1, %eax
                je .Lorm_fm_vlng
                # int (e default)
                movq %rsi, %rdi
                movl 176(%rsp), %esi
                call .Lorm_rd_atoi
                movq %rax, (%r14)
                jmp .Lorm_fm_rnext
            .Lorm_fm_vlng:
                movq %rsi, %rdi
                movl 176(%rsp), %esi
                call .Lorm_rd_atoi
                movq %rax, (%r14)
                jmp .Lorm_fm_rnext
            .Lorm_fm_vstr:
                movq %rsi, %rdi
                movl 176(%rsp), %esi
                call kof_io_make_string
                movq %rax, (%r14)
                jmp .Lorm_fm_rnext
            .Lorm_fm_vbool:
                movq %rsi, %rdi
                movl 176(%rsp), %esi
                call .Lorm_rd_bool
                movq %rax, (%r14)
                jmp .Lorm_fm_rnext
            .Lorm_fm_vdbl:
                movq %rsi, %rdi
                movl 176(%rsp), %esi
                leaq 464(%rsp), %rdx
                call .Lorm_rd_copy_num
                movq %rax, %rdi
                call strtod
                movq %xmm0, (%r14)
                jmp .Lorm_fm_rnext
            .Lorm_fm_vflt:
                movq %rsi, %rdi
                movl 176(%rsp), %esi
                leaq 464(%rsp), %rdx
                call .Lorm_rd_copy_num
                movq %rax, %rdi
                call strtod
                cvtsd2ss %xmm0, %xmm0
                movss %xmm0, (%r14)
                jmp .Lorm_fm_rnext
            .Lorm_fm_vnull:
                movq $0, (%r14)
            .Lorm_fm_rnext:
                incl %ebx
                jmp .Lorm_fm_rcol
            # ---- drena o resto do resultset ate o EOF --------------------
            .Lorm_fm_drain:
                call kof_db_mysql_next
                testq %rax, %rax
                jle .Lorm_fm_ok
                movzbl (%rsi), %eax
                cmpb $0xFE, %al
                je .Lorm_fm_ok
                cmpb $0x00, %al
                je .Lorm_fm_ok
                jmp .Lorm_fm_drain
            .Lorm_fm_ok:
                movq 88(%rsp), %rax
                jmp .Lorm_fm_ret
            # ---- ERR do servidor -> throw "mysql: <msg>" -----------------
            .Lorm_fm_err:
                movq %rax, %r13                  # packet len
                leaq 9(%rsi), %r12               # msg = apos ff+errno+#+state
                cmpq $9, %r13
                jle .Lorm_fm_errempty
                movq %r13, %rdx
                subq $9, %rdx
                cmpq $400, %rdx
                jle .Lorm_fm_errlen
                movq $400, %rdx
                jmp .Lorm_fm_errlen
            .Lorm_fm_errempty:
                movq $9, %r13
                movq $0, %rdx
            .Lorm_fm_errlen:
                movq %rdx, 456(%rsp)
                movl $407, %edi                  # 7 + teto
                addl %edx, %edi
                call .Lorm_bbegin
                leaq .Lorm_fm_my(%rip), %rsi
                movl $7, %ecx
                call .Lorm_bp
                movq 456(%rsp), %rcx
                movq %r12, %rsi
                call .Lorm_bp
                call .Lorm_bfin
                movq %rbx, %rdi
                call kof_throw_string
                ud2

            .Lorm_fm_badconn:
                movq 0(%rsp), %rdi
                call .Lorm_bad_conn
                ud2
            .Lorm_fm_noface:
                leaq .Lorm_fm_face(%rip), %rdi
                call kof_throw_string
                ud2
            .Lorm_fm_miss:
                xorl %eax, %eax
            .Lorm_fm_ret:
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
            .Lorm_fm_s1:
                .ascii "SELECT * FROM "
            .Lorm_fm_s2:
                .ascii " WHERE "
            .Lorm_fm_s3:
                .ascii " = ?"
            .Lorm_fm_my:
                .ascii "mysql: "
            .Lorm_fm_nc:
                .ascii "mysql: no column "
            .Lorm_fm_face:
                .long 1
                .long 0
                .quad 0
                .long .Lorm_fm_face_len
                .long 0
            .Lorm_fm_face_body:
                .ascii "orm.find: entity class not registered (ORM001)"
                .byte 0
                .set .Lorm_fm_face_len, . - .Lorm_fm_face_body - 1
            """);
    }
}
