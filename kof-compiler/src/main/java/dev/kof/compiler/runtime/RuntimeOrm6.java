package dev.kof.compiler.runtime;

/**
 * F2c (D-DB-GAPS, 21/09): {@code kof_orm_all} row-object de LEITURA em
 * {@code List} no runtime Native x86-64 — {@code SELECT * FROM "t"} com
 * construção de um record por linha (mesmo loop de casamento por NOME e
 * leitura por typeCode/campo-dinâmico do {@code RuntimeOrm5}, §397 incluído)
 * acumulados num {@code kof_list_new}/{@code kof_list_add} — vazio retorna a
 * LISTA VAZIA (não null), como o host {@code kof_orm_all} que devolve
 * {@code ArrayList} sempre.
 *
 * <p>O ctor (vtable/typeId/totalSize) é resolvido UMA vez pelo
 * {@code kof_orm_ctors} antes do loop (className é constante do programa —
 * entrada emitida pelo backend para a entidade usada em {@code all}).
 *
 * <p>Contrato de pilha (F1c): prólogo com {@code andq}, frame 168, todo
 * {@code call} de C sai com rsp ≡ 0; stmt em {@code r12}, SQL construído em
 * {@code rbx} (padrões Orm4/Orm5).
 */
public final class RuntimeOrm6 {

    private RuntimeOrm6() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            # ---------------------------------------------------------------
            # kof_orm_all(id*, table*, schema*, className*) -> list*
            #   slots: 0 id | 8 table | 16 schema | 24 className | 32 conn |
            #   40 ftab | 48 nFields | 56 stmt | 64 list | 72 dst | 80 nCols |
            #   88 i | 96 entry | 104 nameLen | 112 colj | 120 vtab |
            #   128 typeId | 136 totalSize
            # ---------------------------------------------------------------
                .globl kof_orm_all
                .type kof_orm_all, @function
            kof_orm_all:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $168, %rsp
                movq %rdi, (%rsp)
                movq %rsi, 8(%rsp)
                movq %rdx, 16(%rsp)
                movq %rcx, 24(%rsp)
                movq (%rsp), %rdi
                call kof_db_type
                cmpl $2, %eax
                je .Lorm6_my_dispatch
                movq (%rsp), %rdi
                call .Lorm_conn
                movq %rax, 32(%rsp)
                movq 16(%rsp), %rdi
                call kof_orm_parse_schema
                movq %rax, 40(%rsp)          # ftab
                movq %rcx, 48(%rsp)          # nFields
            # ---- resolve ctor UMA vez (className -> vtab/typeId/totalSize) --
                movq 24(%rsp), %rax
                leaq 24(%rax), %rdi          # corpo INLINE do KofString (+24)
                movl 16(%rax), %esi          # len
                call kof_orm_ctors
                testq %rax, %rax
                jz .Lorm6_noface
                movq %rax, 120(%rsp)         # vtab
                movq %rdx, 128(%rsp)         # typeId
                movq %rcx, 136(%rsp)         # totalSize
                call kof_list_new
                movq %rax, 64(%rsp)          # lista destino
            # ---- SQL: SELECT * FROM "t" ------------------------------------
                movq 8(%rsp), %rax
                movl 16(%rax), %eax          # tblLen
                movl %eax, %edi
                addl $16, %edi
                call .Lorm_bbegin
                leaq .Lorm6_s1(%rip), %rsi
                movl $14, %ecx
                call .Lorm_bp
                movq 8(%rsp), %rdi
                call .Lorm2_qq
                call .Lorm_bfin
            # ---- prepare (sem bind) ----------------------------------------
                movq 32(%rsp), %rdi
                leaq 24(%rbx), %rsi
                movq $-1, %rdx
                leaq 56(%rsp), %rcx
                xorl %r8d, %r8d
                call sqlite3_prepare_v2
                movq 56(%rsp), %r12
                testq %r12, %r12
                jz .Lorm6_prep_fail
            # ---- loop de linhas --------------------------------------------
            .Lorm6_row:
                movq %r12, %rdi
                call sqlite3_step
                cmpl $100, %eax
                jne .Lorm6_end
                movq 136(%rsp), %rdi
                call kof_alloc
                movq %rax, 72(%rsp)          # dst (record novo por linha)
                movq 72(%rsp), %rdi
                movl 128(%rsp), %esi
                movq 120(%rsp), %rdx
                call kof_init_object
            # ---- loop de campos: casar coluna por NOME, ler por typeCode ---
                movq %r12, %rdi
                call sqlite3_column_count
                movl %eax, 80(%rsp)
                movq $0, 88(%rsp)
            .Lorm6_fld:
                movq 88(%rsp), %rax
                cmpq 48(%rsp), %rax
                jge .Lorm6_rowdone
                movq 88(%rsp), %rax
                shlq $5, %rax
                addq 40(%rsp), %rax
                movq %rax, 96(%rsp)          # entry
                movl 8(%rax), %eax
                movl %eax, 104(%rsp)         # nameLen
                movq $0, 112(%rsp)           # colj
            .Lorm6_col:
                movl 112(%rsp), %eax
                cmpl 80(%rsp), %eax
                jge .Lorm6_nomatch
                movq %r12, %rdi
                movl 112(%rsp), %esi
                call sqlite3_column_name
                movq %rax, %r13              # colname (C string)
                movq 96(%rsp), %rsi
                movq 0(%rsi), %rsi           # field name
                movl 104(%rsp), %edx
                xorl %ecx, %ecx
            .Lorm6_cmp:
                cmpl %edx, %ecx
                jge .Lorm6_cmp_end
                movzbl (%r13,%rcx), %r8d
                movzbl (%rsi,%rcx), %r9d
                cmpl %r9d, %r8d
                jne .Lorm6_nextcol
                incl %ecx
                jmp .Lorm6_cmp
            .Lorm6_cmp_end:
                cmpb $0, (%r13,%rcx)
                jne .Lorm6_nextcol
                jmp .Lorm6_read              # casou: colj atual
            .Lorm6_nextcol:
                incq 112(%rsp)
                jmp .Lorm6_col
            .Lorm6_read:
                movq 96(%rsp), %rax
                movl 12(%rax), %ecx          # typeCode
                movq 72(%rsp), %rdx
                movq 88(%rsp), %rax
                shlq $3, %rax
                addq $16, %rax
                addq %rdx, %rax              # slot do campo i
                movq %rax, %r14              # slot (callee-saved)
                movq 112(%rsp), %r15         # colj casada
                cmpl $2, %ecx
                je .Lorm6_rstr
                cmpl $3, %ecx
                je .Lorm6_rbool
                cmpl $4, %ecx
                je .Lorm6_rdbl
                cmpl $5, %ecx
                je .Lorm6_rflt
                cmpl $1, %ecx
                je .Lorm6_rlng
                movq %r12, %rdi
                movl %r15d, %esi
                call sqlite3_column_int
                movslq %eax, %rax
                movq %rax, (%r14)
                jmp .Lorm6_fldnext
            .Lorm6_rlng:
                movq %r12, %rdi
                movl %r15d, %esi
                call sqlite3_column_int64
                movq %rax, (%r14)
                jmp .Lorm6_fldnext
            .Lorm6_rdbl:
                movq %r12, %rdi
                movl %r15d, %esi
                call sqlite3_column_double
                movsd %xmm0, (%r14)
                jmp .Lorm6_fldnext
            .Lorm6_rflt:
                movq %r12, %rdi
                movl %r15d, %esi
                call sqlite3_column_double
                cvtsd2ss %xmm0, %xmm0
                movss %xmm0, (%r14)
                jmp .Lorm6_fldnext
            .Lorm6_rstr:
                movq %r12, %rdi
                movl %r15d, %esi
                call sqlite3_column_type
                cmpl $5, %eax                # SQLITE_NULL -> null
                jne .Lorm6_rstr1
                movq $0, (%r14)
                jmp .Lorm6_fldnext
            .Lorm6_rstr1:
                movq %r12, %rdi
                movl %r15d, %esi
                call sqlite3_column_text
                movq %rax, %r13              # ptr
                movq %r13, %rdi
                call kof_io_strlen
                movq %rax, %rsi
                movq %r13, %rdi
                call kof_io_make_string
                movq %rax, (%r14)
                jmp .Lorm6_fldnext
            .Lorm6_rbool:
                # §397 (mesma paridade do binder do host, via Orm5): NULL->
                # false, INTEGER/FLOAT->numero!=0, TEXT->"true" literal.
                movq %r12, %rdi
                movl %r15d, %esi
                call sqlite3_column_type
                cmpl $5, %eax
                je .Lorm6_rboolnull
                cmpl $1, %eax
                je .Lorm6_rboolint
                cmpl $2, %eax
                je .Lorm6_rbooldbl
                jmp .Lorm6_rbool1            # TEXT/BLOB: literal "true"
            .Lorm6_rboolnull:
                movq $0, (%r14)
                jmp .Lorm6_fldnext
            .Lorm6_rboolint:
                movq %r12, %rdi
                movl %r15d, %esi
                call sqlite3_column_int64
                testq %rax, %rax
                setne %al
                movzbl %al, %eax
                movq %rax, (%r14)
                jmp .Lorm6_fldnext
            .Lorm6_rbooldbl:
                movq %r12, %rdi
                movl %r15d, %esi
                call sqlite3_column_double
                cvtsd2si %xmm0, %rax         # truncate = intValue() do binder
                testq %rax, %rax
                setne %al
                movzbl %al, %eax
                movq %rax, (%r14)
                jmp .Lorm6_fldnext
            .Lorm6_rbool1:
                movq %r12, %rdi
                movl %r15d, %esi
                call sqlite3_column_text
                testq %rax, %rax
                jnz .Lorm6_rbool2
                movq $0, (%r14)
                jmp .Lorm6_fldnext
            .Lorm6_rbool2:
                movzbl (%rax), %r13d
                orl $32, %r13d
                cmpl $116, %r13d             # 't'
                jne .Lorm6_rboolf
                movzbl 1(%rax), %r13d
                orl $32, %r13d
                cmpl $114, %r13d             # 'r'
                jne .Lorm6_rboolf
                movzbl 2(%rax), %r13d
                orl $32, %r13d
                cmpl $117, %r13d             # 'u'
                jne .Lorm6_rboolf
                movzbl 3(%rax), %r13d
                orl $32, %r13d
                cmpl $101, %r13d             # 'e'
                jne .Lorm6_rboolf
                movq $1, (%r14)
                jmp .Lorm6_fldnext
            .Lorm6_rboolf:
                movq $0, (%r14)              # texto != "true" -> false
            .Lorm6_fldnext:
                incq 88(%rsp)
                jmp .Lorm6_fld
            .Lorm6_rowdone:
                movq 64(%rsp), %rdi
                movq 72(%rsp), %rsi
                call kof_list_add
                jmp .Lorm6_row
            .Lorm6_end:
                movq %r12, %rdi
                call sqlite3_finalize
                movq 64(%rsp), %rax
                jmp .Lorm6_ret
            # ---- fail: throw "sqlite: " + errmsg ----------------------------
            .Lorm6_prep_fail:
                movl $512, %edi
                call .Lorm_bbegin
                leaq .Lorm6_pre(%rip), %rsi
                movl $8, %ecx
                call .Lorm_bp
                movq 32(%rsp), %rdi
                call sqlite3_errmsg
                testq %rax, %rax
                jz .Lorm6_pfin
                movq %rax, %rsi
                call .Lorm_pcstr
            .Lorm6_pfin:
                call .Lorm_bfin
                movq %rbx, %rdi
                call kof_throw_string
                ud2
            .Lorm6_nomatch:
                movl $256, %edi
                call .Lorm_bbegin
                leaq .Lorm6_nc(%rip), %rsi
                movl $18, %ecx
                call .Lorm_bp
                movq 96(%rsp), %rax
                movq 0(%rax), %rsi
                movl 8(%rax), %ecx
                call .Lorm_qraw
                call .Lorm_bfin
                movq %rbx, %rdi
                call kof_throw_string
                ud2
            .Lorm6_noface:
                leaq .Lorm6_face(%rip), %rdi
                call kof_throw_string
                ud2
            .Lorm6_ret:
                addq $168, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret

            # F2d4a: mysql -> restaura o frame e tail-chama .Lorm_all_my
            # (args originais nos slots; a asm do mysql vive no
            # RuntimeOrmMysqlAll, emitido junto no mesmo .s).
            .Lorm6_my_dispatch:
                movq (%rsp), %rdi
                movq 8(%rsp), %rsi
                movq 16(%rsp), %rdx
                movq 24(%rsp), %rcx
                addq $168, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                jmp .Lorm_all_my

            # ---------------------- literais --------------------------------
            .Lorm6_s1:
                .ascii "SELECT * FROM "
            .Lorm6_pre:
                .ascii "sqlite: "
            .Lorm6_nc:
                .ascii "sqlite: no column "
            .Lorm6_face:
                .long 1
                .long 0
                .quad 0
                .long .Lorm6_face_len
                .long 0
            .Lorm6_face_body:
                .ascii "orm.all: entity class not registered (ORM001)"
                .byte 0
                .set .Lorm6_face_len, . - .Lorm6_face_body - 1
            """);
    }
}
