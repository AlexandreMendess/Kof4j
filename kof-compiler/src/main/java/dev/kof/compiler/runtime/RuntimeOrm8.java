package dev.kof.compiler.runtime;

/**
 * F2c3 (D-DB-GAPS, 21/09): face {@code page} da leitura row-object no runtime
 * Native x86-64 — {@code kof_orm_page} espelha
 * {@code JvmOrmRuntime.kof_orm_page}: SQL {@code SELECT * FROM "t" LIMIT ?
 * OFFSET ?} com os dois params bindados (host {@code intValue()}: long/dbl
 * truncam; KofString do coerce do call-site = atoi — fora do contrato
 * Number, superset honesto; null = 0) e o MESMO loop de campos do
 * {@code RuntimeOrm7} (397 + GC-safe inclusos) acumulado em
 * {@code kof_list_new}/{@code kof_list_add}; vazio = lista VAZIA, como o
 * host. Falha de prepare/step/linha-errada lança {@code "sqlite: " +
 * sqlite3_errmsg}; MySQL segue {@code .Lorm_conn} honesto (ORM001).
 *
 * <p>Contrato de pilha (F1c): prologo com {@code andq}, frame 184
 * (≡8 antes do {@code subq}, como 168/152 da familia), todo {@code call} de
 * C sai com rsp ≡ 0; stmt em {@code r12}, SQL em {@code rbx}.
 */
public final class RuntimeOrm8 {

    private RuntimeOrm8() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            # ---------------------------------------------------------------
# ---------------------------------------------------------------
            # F2c3 (D-DB-GAPS, 21/09): kof_orm_page no Native x86-64 -
            #   kof_orm_page(id,limit,offset,table,schema,className) -> List
            # SQL do host MEDIDO: SELECT * FROM "t" LIMIT ? OFFSET ? com os
            # DOIS params bindados (host intValue(): box int/long truncam p/
            # int32, dbl/flt truncam p/ (int), KofString do coerce do
            # call-site = atoi - fora do contrato Number, superset honesto
            # documentado; null/nao-recognizado = 0). Loop de campos =
            # RuntimeOrm7 verbatim (397 + GC-safe inclusos); frame 184
            # (mod 16 = 8, slots 168/176 = lim/off lidos pelo epilogue).
            # slots: 0 id | 8 limit | 16 offset | 32 table | 40 schema |
            #   48 className | 56 conn | 64 ftab | 72 nFields | 80 stmt |
            #   88 list | 96 dst | 104 nCols | 112 i | 120 entry |
            #   128 nameLen | 136 colj | 144 vtab | 152 typeId |
            #   160 totalSize | 168 lim | 176 off
            # ---------------------------------------------------------------
                .globl kof_orm_page
                .type kof_orm_page, @function
            kof_orm_page:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $184, %rsp
                movq %rdi, 0(%rsp)
                movq %rsi, 8(%rsp)
                movq %rdx, 16(%rsp)
                movq %rcx, 32(%rsp)
                movq %r8, 40(%rsp)
                movq %r9, 48(%rsp)
.Lorm8_bodystart:
            # F2d4c: mysql -> restaura o frame e tail-chama .Lorm_page_my
                movq (%rsp), %rdi
                call kof_db_type
                cmpl $2, %eax
                je .Lorm8_my_dispatch
                movq (%rsp), %rdi
                call .Lorm_conn
                movq %rax, 56(%rsp)
                movq 40(%rsp), %rdi
                call kof_orm_parse_schema
                movq %rax, 64(%rsp)          # ftab
                movq %rcx, 72(%rsp)          # nFields
                movq 48(%rsp), %rax
                leaq 24(%rax), %rdi
                movl 16(%rax), %esi
                call kof_orm_ctors
                testq %rax, %rax
                jz .Lorm8_noface
                movq %rax, 144(%rsp)         # vtab
                movq %rdx, 152(%rsp)         # typeId
                movq %rcx, 160(%rsp)         # totalSize
                call kof_list_new
                movq %rax, 88(%rsp)          # lista destino
                        # ---- SQL: SELECT * FROM "t" LIMIT ? OFFSET ? ------------------
                movq 32(%rsp), %rax
                movl 16(%rax), %edx          # tblLen
                addl $35, %edx               # 14 + 17 + 2 quotes + folga
                movl %edx, %edi
                call .Lorm_bbegin
                leaq .Lorm8_s1(%rip), %rsi
                movl $14, %ecx
                call .Lorm_bp
                movq 32(%rsp), %rdi
                call .Lorm2_qq
                leaq .Lorm8_lo(%rip), %rsi
                movl $17, %ecx
                call .Lorm_bp
                call .Lorm_bfin
            # ---- prepare + bind LIMIT (1) e OFFSET (2) ----------------------
                movq 56(%rsp), %rdi
                leaq 24(%rbx), %rsi
                movq $-1, %rdx
                leaq 80(%rsp), %rcx
                xorl %r8d, %r8d
                call sqlite3_prepare_v2
                movq 80(%rsp), %r12
                testq %r12, %r12
                jz .Lorm8_prep_fail
                movq 8(%rsp), %rdi
                call .Lorm8_pv
                movq %rax, 168(%rsp)
                movq %r12, %rdi
                movl $1, %esi
                movq 168(%rsp), %rdx
                call sqlite3_bind_int64
                movq 16(%rsp), %rdi
                call .Lorm8_pv
                movq %rax, 176(%rsp)
                movq %r12, %rdi
                movl $2, %esi
                movq 176(%rsp), %rdx
                call sqlite3_bind_int64
        # ---- loop de linhas --------------------------------------------
            .Lorm8_row:
                movq %r12, %rdi
                call sqlite3_step
                cmpl $100, %eax
                jne .Lorm8_end
                movq 160(%rsp), %rdi
                call kof_alloc
                movq %rax, 96(%rsp)          # dst (record novo por linha)
                movq 96(%rsp), %rdi
                movl 152(%rsp), %esi
                movq 144(%rsp), %rdx
                call kof_init_object
            # ---- loop de campos: casar coluna por NOME, ler por typeCode ---
                movq %r12, %rdi
                call sqlite3_column_count
                movl %eax, 104(%rsp)
                movq $0, 112(%rsp)
            .Lorm8_fld:
                movq 112(%rsp), %rax
                cmpq 72(%rsp), %rax
                jge .Lorm8_rowdone
                movq 112(%rsp), %rax
                shlq $5, %rax
                addq 64(%rsp), %rax
                movq %rax, 120(%rsp)          # entry
                movl 8(%rax), %eax
                movl %eax, 128(%rsp)         # nameLen
                movq $0, 136(%rsp)           # colj
            .Lorm8_col:
                movl 136(%rsp), %eax
                cmpl 104(%rsp), %eax
                jge .Lorm8_nomatch
                movq %r12, %rdi
                movl 136(%rsp), %esi
                call sqlite3_column_name
                movq %rax, %r13              # colname (C string)
                movq 120(%rsp), %rsi
                movq 0(%rsi), %rsi           # field name
                movl 128(%rsp), %edx
                xorl %ecx, %ecx
            .Lorm8_cmp:
                cmpl %edx, %ecx
                jge .Lorm8_cmp_end
                movzbl (%r13,%rcx), %r8d
                movzbl (%rsi,%rcx), %r9d
                cmpl %r9d, %r8d
                jne .Lorm8_nextcol
                incl %ecx
                jmp .Lorm8_cmp
            .Lorm8_cmp_end:
                cmpb $0, (%r13,%rcx)
                jne .Lorm8_nextcol
                jmp .Lorm8_read              # casou: colj atual
            .Lorm8_nextcol:
                incq 136(%rsp)
                jmp .Lorm8_col
            .Lorm8_read:
                movq 120(%rsp), %rax
                movl 12(%rax), %ecx          # typeCode
                movq 96(%rsp), %rdx
                movq 112(%rsp), %rax
                shlq $3, %rax
                addq $16, %rax
                addq %rdx, %rax              # slot do campo i
                movq %rax, %r14              # slot (callee-saved)
                movq 136(%rsp), %r15         # colj casada
                cmpl $2, %ecx
                je .Lorm8_rstr
                cmpl $3, %ecx
                je .Lorm8_rbool
                cmpl $4, %ecx
                je .Lorm8_rdbl
                cmpl $5, %ecx
                je .Lorm8_rflt
                cmpl $1, %ecx
                je .Lorm8_rlng
                movq %r12, %rdi
                movl %r15d, %esi
                call sqlite3_column_int
                movslq %eax, %rax
                movq %rax, (%r14)
                jmp .Lorm8_fldnext
            .Lorm8_rlng:
                movq %r12, %rdi
                movl %r15d, %esi
                call sqlite3_column_int64
                movq %rax, (%r14)
                jmp .Lorm8_fldnext
            .Lorm8_rdbl:
                movq %r12, %rdi
                movl %r15d, %esi
                call sqlite3_column_double
                movsd %xmm0, (%r14)
                jmp .Lorm8_fldnext
            .Lorm8_rflt:
                movq %r12, %rdi
                movl %r15d, %esi
                call sqlite3_column_double
                cvtsd2ss %xmm0, %xmm0
                movss %xmm0, (%r14)
                jmp .Lorm8_fldnext
            .Lorm8_rstr:
                movq %r12, %rdi
                movl %r15d, %esi
                call sqlite3_column_type
                cmpl $5, %eax                # SQLITE_NULL -> null
                jne .Lorm8_rstr1
                movq $0, (%r14)
                jmp .Lorm8_fldnext
            .Lorm8_rstr1:
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
                jmp .Lorm8_fldnext
            .Lorm8_rbool:
                # §397 (mesma paridade do binder do host, via Orm5): NULL->
                # false, INTEGER/FLOAT->numero!=0, TEXT->"true" literal.
                movq %r12, %rdi
                movl %r15d, %esi
                call sqlite3_column_type
                cmpl $5, %eax
                je .Lorm8_rboolnull
                cmpl $1, %eax
                je .Lorm8_rboolint
                cmpl $2, %eax
                je .Lorm8_rbooldbl
                jmp .Lorm8_rbool1            # TEXT/BLOB: literal "true"
            .Lorm8_rboolnull:
                movq $0, (%r14)
                jmp .Lorm8_fldnext
            .Lorm8_rboolint:
                movq %r12, %rdi
                movl %r15d, %esi
                call sqlite3_column_int64
                testq %rax, %rax
                setne %al
                movzbl %al, %eax
                movq %rax, (%r14)
                jmp .Lorm8_fldnext
            .Lorm8_rbooldbl:
                movq %r12, %rdi
                movl %r15d, %esi
                call sqlite3_column_double
                cvtsd2si %xmm0, %rax         # truncate = intValue() do binder
                testq %rax, %rax
                setne %al
                movzbl %al, %eax
                movq %rax, (%r14)
                jmp .Lorm8_fldnext
            .Lorm8_rbool1:
                movq %r12, %rdi
                movl %r15d, %esi
                call sqlite3_column_text
                testq %rax, %rax
                jnz .Lorm8_rbool2
                movq $0, (%r14)
                jmp .Lorm8_fldnext
            .Lorm8_rbool2:
                movzbl (%rax), %r13d
                orl $32, %r13d
                cmpl $116, %r13d             # 't'
                jne .Lorm8_rboolf
                movzbl 1(%rax), %r13d
                orl $32, %r13d
                cmpl $114, %r13d             # 'r'
                jne .Lorm8_rboolf
                movzbl 2(%rax), %r13d
                orl $32, %r13d
                cmpl $117, %r13d             # 'u'
                jne .Lorm8_rboolf
                movzbl 3(%rax), %r13d
                orl $32, %r13d
                cmpl $101, %r13d             # 'e'
                jne .Lorm8_rboolf
                movq $1, (%r14)
                jmp .Lorm8_fldnext
            .Lorm8_rboolf:
                movq $0, (%r14)              # texto != "true" -> false
            .Lorm8_fldnext:
                incq 112(%rsp)
                jmp .Lorm8_fld
            .Lorm8_rowdone:
                movq 88(%rsp), %rdi
                movq 96(%rsp), %rsi
                call kof_list_add
                jmp .Lorm8_row
            .Lorm8_end:
                movq %r12, %rdi
                call sqlite3_finalize
                movq 88(%rsp), %rax
                jmp .Lorm8_ret
            # ---- fail: throw "sqlite: " + errmsg ----------------------------
            .Lorm8_prep_fail:
                movl $512, %edi
                call .Lorm_bbegin
                leaq .Lorm8_pre(%rip), %rsi
                movl $8, %ecx
                call .Lorm_bp
                movq 56(%rsp), %rdi
                call sqlite3_errmsg
                testq %rax, %rax
                jz .Lorm8_pfin
                movq %rax, %rsi
                call .Lorm_pcstr
            .Lorm8_pfin:
                call .Lorm_bfin
                movq %rbx, %rdi
                call kof_throw_string
                ud2
            .Lorm8_nomatch:
                movl $256, %edi
                call .Lorm_bbegin
                leaq .Lorm8_nc(%rip), %rsi
                movl $18, %ecx
                call .Lorm_bp
                movq 120(%rsp), %rax
                movq 0(%rax), %rsi
                movl 8(%rax), %ecx
                call .Lorm_qraw
                call .Lorm_bfin
                movq %rbx, %rdi
                call kof_throw_string
                ud2
            .Lorm8_noface:
                leaq .Lorm8_face(%rip), %rdi
                call kof_throw_string
                ud2
            .Lorm8_ret:
                addq $184, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret

            # F2d4c: mysql -> restaura o frame e tail-chama .Lorm_page_my
            # (args originais nos slots; a asm do mysql vive no
            # RuntimeOrmMysqlPage, emitido junto no mesmo .s).
            .Lorm8_my_dispatch:
                movq 0(%rsp), %rdi
                movq 8(%rsp), %rsi
                movq 16(%rsp), %rdx
                movq 32(%rsp), %rcx
                movq 40(%rsp), %r8
                movq 48(%rsp), %r9
                addq $184, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                jmp .Lorm_page_my


            # ---- parse limit/offset: host ((Number) x).intValue() ----------
            #   box: int direto; long/dbl/flt truncam p/ int32 (intValue);
            #   KofString (coerce do call-site): atoi com sinal, para no 1o
            #   nao-digito (fora do contrato Number - superset honesto);
            #   null/desconhecido = 0. rax = int64 (sempre = (int) do valor).
            .Lorm8_pv:
                testq %rdi, %rdi
                jz .Lorm8_pv0
                movq (%rdi), %rax
                movabsq $@@MAGIC@@, %rcx
                cmpq %rcx, %rax
                jne .Lorm8_pv_str
                movl 8(%rdi), %eax
                cmpl $0, %eax
                je .Lorm8_pvi
                cmpl $1, %eax
                je .Lorm8_pvq
                cmpl $2, %eax
                je .Lorm8_pvq
                cmpl $4, %eax
                je .Lorm8_pvd
                cmpl $5, %eax
                je .Lorm8_pvf
                jmp .Lorm8_pv0
            .Lorm8_pvi:
                movslq 16(%rdi), %rax
                ret
            .Lorm8_pvq:
                movq 16(%rdi), %rax
                movslq %eax, %rax
                ret
            .Lorm8_pvd:
                movq 16(%rdi), %rax
                movq %rax, %xmm0
                cvttsd2si %xmm0, %eax
                movslq %eax, %rax
                ret
            .Lorm8_pvf:
                movl 16(%rdi), %eax
                movd %eax, %xmm0
                cvtss2sd %xmm0, %xmm0
                cvttsd2si %xmm0, %eax
                movslq %eax, %rax
                ret
            .Lorm8_pv_str:
                cmpl $1, (%rdi)
                jne .Lorm8_pv0
                cmpl $0, 4(%rdi)
                jne .Lorm8_pv0
                cmpl $0, 8(%rdi)
                jne .Lorm8_pv0
                movl 16(%rdi), %ecx          # len
                leaq 24(%rdi), %rsi          # body
                xorl %eax, %eax              # val (int32 - wrap = host CCE fora)
                xorl %r8d, %r8d              # i
                xorl %r9d, %r9d              # neg
                testl %ecx, %ecx
                jle .Lorm8_pv_ret0
                movzbl (%rsi), %r10d
                cmpl $45, %r10d              # '-'
                jne .Lorm8_pv_plus
                movl $1, %r9d
                incl %r8d
                jmp .Lorm8_pv_loop
            .Lorm8_pv_plus:
                cmpl $43, %r10d              # '+'
                jne .Lorm8_pv_loop
                incl %r8d
            .Lorm8_pv_loop:
                cmpl %ecx, %r8d
                jge .Lorm8_pv_done
                movzbl (%rsi,%r8), %r10d
                cmpl $48, %r10d
                jb .Lorm8_pv_done
                cmpl $57, %r10d
                ja .Lorm8_pv_done
                imull $10, %eax, %eax
                subl $48, %r10d
                addl %r10d, %eax
                incl %r8d
                jmp .Lorm8_pv_loop
            .Lorm8_pv_done:
                testl %r9d, %r9d
                jz .Lorm8_pv_ret
                negl %eax
            .Lorm8_pv_ret:
                movslq %eax, %rax
                ret
            .Lorm8_pv_ret0:
                xorl %eax, %eax
                ret
            .Lorm8_pv0:
                xorl %eax, %eax
                ret

            .Lorm8_s1:
                .ascii "SELECT * FROM "
            .Lorm8_lo:
                .ascii " LIMIT ? OFFSET ?"
            .Lorm8_pre:
                .ascii "sqlite: "
            .Lorm8_nc:
                .ascii "sqlite: no column "
            .Lorm8_face:
                .long 1
                .long 0
                .quad 0
                .long .Lorm8_face_len
                .long 0
            .Lorm8_face_body:
                .ascii "orm.page: entity class not registered (ORM001)"
                .byte 0
                .set .Lorm8_face_len, . - .Lorm8_face_body - 1
        """);
    }
}