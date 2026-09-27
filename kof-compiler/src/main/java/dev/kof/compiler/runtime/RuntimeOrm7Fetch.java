package dev.kof.compiler.runtime;

/**
 * F2c2 (D-DB-GAPS, 21/09) - parte de FETCH de {@code kof_orm_where} /
 * {@code kof_orm_where_op}: loop de linhas/campos, leitura por typeCode,
 * caminhos de fail e o dispatch mysql, mais as strings {@code .ascii}.
 * Split por responsabilidade do gate <=500 (TIER 13.5, 23/09); o setup/SQL
 * vive em {@link RuntimeOrm7Setup}. Ver {@link RuntimeOrm7#emit} para a
 * ordem de concatenacao (contrato: labels {@code .Lorm7_*} atravessam).
 */
final class RuntimeOrm7Fetch {

    private RuntimeOrm7Fetch() {}

    static final String WHERE_ASM_FETCH = """
        # ---- loop de linhas --------------------------------------------
            .Lorm7_row:
                movq %r12, %rdi
                call sqlite3_step
                cmpl $100, %eax
                jne .Lorm7_end
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
            .Lorm7_fld:
                movq 112(%rsp), %rax
                cmpq 72(%rsp), %rax
                jge .Lorm7_rowdone
                movq 112(%rsp), %rax
                shlq $5, %rax
                addq 64(%rsp), %rax
                movq %rax, 120(%rsp)          # entry
                movl 8(%rax), %eax
                movl %eax, 128(%rsp)         # nameLen
                movq $0, 136(%rsp)           # colj
            .Lorm7_col:
                movl 136(%rsp), %eax
                cmpl 104(%rsp), %eax
                jge .Lorm7_nomatch
                movq %r12, %rdi
                movl 136(%rsp), %esi
                call sqlite3_column_name
                movq %rax, %r13              # colname (C string)
                movq 120(%rsp), %rsi
                movq 0(%rsi), %rsi           # field name
                movl 128(%rsp), %edx
                xorl %ecx, %ecx
            .Lorm7_cmp:
                cmpl %edx, %ecx
                jge .Lorm7_cmp_end
                movzbl (%r13,%rcx), %r8d
                movzbl (%rsi,%rcx), %r9d
                cmpl %r9d, %r8d
                jne .Lorm7_nextcol
                incl %ecx
                jmp .Lorm7_cmp
            .Lorm7_cmp_end:
                cmpb $0, (%r13,%rcx)
                jne .Lorm7_nextcol
                jmp .Lorm7_read              # casou: colj atual
            .Lorm7_nextcol:
                incq 136(%rsp)
                jmp .Lorm7_col
            .Lorm7_read:
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
                je .Lorm7_rstr
                cmpl $3, %ecx
                je .Lorm7_rbool
                cmpl $4, %ecx
                je .Lorm7_rdbl
                cmpl $5, %ecx
                je .Lorm7_rflt
                cmpl $1, %ecx
                je .Lorm7_rlng
                movq %r12, %rdi
                movl %r15d, %esi
                call sqlite3_column_int
                movslq %eax, %rax
                movq %rax, (%r14)
                jmp .Lorm7_fldnext
            .Lorm7_rlng:
                movq %r12, %rdi
                movl %r15d, %esi
                call sqlite3_column_int64
                movq %rax, (%r14)
                jmp .Lorm7_fldnext
            .Lorm7_rdbl:
                movq %r12, %rdi
                movl %r15d, %esi
                call sqlite3_column_double
                movsd %xmm0, (%r14)
                jmp .Lorm7_fldnext
            .Lorm7_rflt:
                movq %r12, %rdi
                movl %r15d, %esi
                call sqlite3_column_double
                cvtsd2ss %xmm0, %xmm0
                movss %xmm0, (%r14)
                jmp .Lorm7_fldnext
            .Lorm7_rstr:
                movq %r12, %rdi
                movl %r15d, %esi
                call sqlite3_column_type
                cmpl $5, %eax                # SQLITE_NULL -> null
                jne .Lorm7_rstr1
                movq $0, (%r14)
                jmp .Lorm7_fldnext
            .Lorm7_rstr1:
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
                jmp .Lorm7_fldnext
            .Lorm7_rbool:
                # §397 (mesma paridade do binder do host, via Orm5): NULL->
                # false, INTEGER/FLOAT->numero!=0, TEXT->"true" literal.
                movq %r12, %rdi
                movl %r15d, %esi
                call sqlite3_column_type
                cmpl $5, %eax
                je .Lorm7_rboolnull
                cmpl $1, %eax
                je .Lorm7_rboolint
                cmpl $2, %eax
                je .Lorm7_rbooldbl
                jmp .Lorm7_rbool1            # TEXT/BLOB: literal "true"
            .Lorm7_rboolnull:
                movq $0, (%r14)
                jmp .Lorm7_fldnext
            .Lorm7_rboolint:
                movq %r12, %rdi
                movl %r15d, %esi
                call sqlite3_column_int64
                testq %rax, %rax
                setne %al
                movzbl %al, %eax
                movq %rax, (%r14)
                jmp .Lorm7_fldnext
            .Lorm7_rbooldbl:
                movq %r12, %rdi
                movl %r15d, %esi
                call sqlite3_column_double
                cvtsd2si %xmm0, %rax         # truncate = intValue() do binder
                testq %rax, %rax
                setne %al
                movzbl %al, %eax
                movq %rax, (%r14)
                jmp .Lorm7_fldnext
            .Lorm7_rbool1:
                movq %r12, %rdi
                movl %r15d, %esi
                call sqlite3_column_text
                testq %rax, %rax
                jnz .Lorm7_rbool2
                movq $0, (%r14)
                jmp .Lorm7_fldnext
            .Lorm7_rbool2:
                movzbl (%rax), %r13d
                orl $32, %r13d
                cmpl $116, %r13d             # 't'
                jne .Lorm7_rboolf
                movzbl 1(%rax), %r13d
                orl $32, %r13d
                cmpl $114, %r13d             # 'r'
                jne .Lorm7_rboolf
                movzbl 2(%rax), %r13d
                orl $32, %r13d
                cmpl $117, %r13d             # 'u'
                jne .Lorm7_rboolf
                movzbl 3(%rax), %r13d
                orl $32, %r13d
                cmpl $101, %r13d             # 'e'
                jne .Lorm7_rboolf
                movq $1, (%r14)
                jmp .Lorm7_fldnext
            .Lorm7_rboolf:
                movq $0, (%r14)              # texto != "true" -> false
            .Lorm7_fldnext:
                incq 112(%rsp)
                jmp .Lorm7_fld
            .Lorm7_rowdone:
                movq 88(%rsp), %rdi
                movq 96(%rsp), %rsi
                call kof_list_add
                jmp .Lorm7_row
            .Lorm7_end:
                movq %r12, %rdi
                call sqlite3_finalize
                movq 88(%rsp), %rax
                jmp .Lorm7_ret
            # ---- fail: throw "sqlite: " + errmsg ----------------------------
            .Lorm7_prep_fail:
                movl $512, %edi
                call .Lorm_bbegin
                leaq .Lorm7_pre(%rip), %rsi
                movl $8, %ecx
                call .Lorm_bp
                movq 56(%rsp), %rdi
                call sqlite3_errmsg
                testq %rax, %rax
                jz .Lorm7_pfin
                movq %rax, %rsi
                call .Lorm_pcstr
            .Lorm7_pfin:
                call .Lorm_bfin
                movq %rbx, %rdi
                call kof_throw_string
                ud2
            .Lorm7_nomatch:
                movl $256, %edi
                call .Lorm_bbegin
                leaq .Lorm7_nc(%rip), %rsi
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
            .Lorm7_noface:
                leaq .Lorm7_face(%rip), %rdi
                call kof_throw_string
                ud2
            .Lorm7_ret:
                addq $168, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret

            # F2d4b: mysql -> restaura o frame e tail-chama .Lorm_where_my
            # (className vai em r10 — ABI interna; a asm do mysql vive no
            # RuntimeOrmMysqlWhere, emitido junto no mesmo .s).
            .Lorm7_my_dispatch:
                movq 48(%rsp), %r10
                movq 0(%rsp), %rdi
                movq 8(%rsp), %rsi
                movq 16(%rsp), %rdx
                movq 24(%rsp), %rcx
                movq 32(%rsp), %r8
                movq 40(%rsp), %r9
                addq $168, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                jmp .Lorm_where_my

                        .Lorm7_opbad:
                # GC-safe: A e B empilhados ANTES do alloc interno do concat
                # (scan conservativo veem-nos do rsp ate kof_main_stack_bottom;
                #  pushes movem os slots do frame em +16 -> op e 40(%rsp)).
                pushq 24(%rsp)                   # B = op no topo (stack-safe)
                leaq .Lorm7_bad(%rip), %rdi
                movl $26, %esi
                call kof_string_from_literal     # A = rax
                pushq %rax                       # A na stack (stack-safe)
                movq %rax, %rdi
                movq 40(%rsp), %rsi              # op (slot 24 deslocado +16)
                call kof_string_concat
                popq %rcx                        # desempilha A (rax = A + B
                popq %rcx                        #   ja nao serve: rdi e clobber)
                movq %rax, %rdi
                call kof_throw_string
                ud2

            .Lorm7_s1:
                .ascii "SELECT * FROM "
            .Lorm7_q:
                .ascii " WHERE "
            .Lorm7_sp:
                .ascii " "
            .Lorm7_qm:
                .ascii " ?"
            .Lorm7_eqop:
                .ascii "="
                .byte 0
            .Lorm7_bad:
                .ascii "ORM operator not allowed: "
            .Lorm7_pre:
                .ascii "sqlite: "
            .Lorm7_nc:
                .ascii "sqlite: no column "
            .Lorm7_face:
                .long 1
                .long 0
                .quad 0
                .long .Lorm7_face_len
                .long 0
            .Lorm7_face_body:
                .ascii "orm.where: entity class not registered (ORM001)"
                .byte 0
                .set .Lorm7_face_len, . - .Lorm7_face_body - 1
        """;
}
