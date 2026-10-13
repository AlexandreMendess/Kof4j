package dev.kof.compiler.runtime;

/**
 * kof.orm history no Native x86-64 — Fase 5 fatia 5d-2 do
 * entity-history-plan (D-ENTITY-HISTORY; ordem da mantenedora 13/10 "ponta
 * a ponta"): as variantes {@code kof_orm_history_page} (a janela
 * LIMIT/OFFSET, a ordem do runtime: id, key, offset, limit, table, schema,
 * className) e {@code kof_orm_history_one} (a revisão única: id, key,
 * revision, table, schema, className → o Revision ou null). O build
 * posicional dos 5 campos do Revision (o mesmo da 5d-1). SQLite only.
 *
 * <p>O frame ALINHADO (a lição do ciclo 65): 40 de pushes + o subq múltiplo
 * de 16. Os slots não tocam a região dos pushes.
 */
public final class RuntimeOrmHistory5 {

    private RuntimeOrmHistory5() {}

    public static void emit(StringBuilder sb) {
        String b = """
            # kof_orm_history_page(id*, key*, offset, limit, table*, schema*,
            #                      className*) -> list*
            #   slots: 0 id | 8 key | 16 offset | 24 limit | 32 table |
            #   40 schema | 48 className | 56 conn | 64 stmt | 72 list |
            #   80 dst | 88 vtab | 96 typeId | 104 totalSize
            # ---------------------------------------------------------------
                @@SYMBOL@@
            kof_orm_history_page:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $120, %rsp
                movq %rdi, (%rsp)
                movq %rsi, 8(%rsp)
                movl %edx, 16(%rsp)
                movl %ecx, 24(%rsp)
                movq %r8, 32(%rsp)
                movq %r9, 40(%rsp)
                movq 16(%rbp), %rax          # className (o 7º arg: na pilha;
                                             #   8(%rbp) = o endereço de retorno)
                movq %rax, 48(%rsp)
            @@CONN@@
                movq 48(%rsp), %rax
                leaq 24(%rax), %rdi
                movl 16(%rax), %esi
                call kof_orm_ctors
                testq %rax, %rax
                jz .Lormh5p_noface
                movq %rax, 88(%rsp)
                movq %rdx, 96(%rsp)
                movq %rcx, 104(%rsp)
                call kof_list_new
                movq %rax, 72(%rsp)
                movq 32(%rsp), %rax
                movl 16(%rax), %eax
                addl $300, %eax
                movl %eax, %edi
                call .Lorm_bbegin
                leaq .Lormh4_sel(%rip), %rsi
                movl $.Lormh4_sel_len, %ecx
                call .Lorm_bp
                movl $34, %r8d
                call .Lorm_bh
                movq 32(%rsp), %rdi
                movl 16(%rdi), %r12d
                leaq 24(%rdi), %rsi
                movl %r12d, %ecx
                call .Lorm_bp
                leaq .Lormh3_suf(%rip), %rsi
                movl $.Lormh3_suf_len, %ecx
                call .Lorm_bp
                movl $34, %r8d
                call .Lorm_bh
                leaq .Lormh4_where(%rip), %rsi
                movl $.Lormh4_where_len, %ecx
                call .Lorm_bp
                call .Lorm_bfin
                movq 56(%rsp), %rdi
                leaq 24(%rbx), %rsi
                movq $-1, %rdx
                leaq 64(%rsp), %rcx
                xorl %r8d, %r8d
                call sqlite3_prepare_v2
                movq 64(%rsp), %r12
                testq %r12, %r12
                jz .Lormh5p_done
                movq 8(%rsp), %rdi
                call kof_box_to_string
                movq %rax, %r13
                testq %r13, %r13
                jz .Lormh5p_bn
                movq %r12, %rdi
                movl $1, %esi
                leaq 24(%r13), %rdx
                movl 16(%r13), %ecx
                movslq %ecx, %rcx
                movq $-1, %r8
                call sqlite3_bind_text
                jmp .Lormh5p_b23
            .Lormh5p_bn:
                movq %r12, %rdi
                movl $1, %esi
                call sqlite3_bind_null
            .Lormh5p_b23:
                movq %r12, %rdi
                movl $2, %esi
                movslq 24(%rsp), %rdx
                call sqlite3_bind_int64
                movq %r12, %rdi
                movl $3, %esi
                movslq 16(%rsp), %rdx
                call sqlite3_bind_int64
                jmp .Lormh5p_row
            .Lormh5p_row:
                movq %r12, %rdi
                call sqlite3_step
                cmpl $101, %eax
                jne .Lormh5p_rb
                movq %r12, %rdi
                call sqlite3_finalize
                jmp .Lormh5p_done
            .Lormh5p_rb:
                movq 104(%rsp), %rdi
                call kof_alloc
                movq %rax, 80(%rsp)
                movq 80(%rsp), %rdi
                movl 96(%rsp), %esi
                movq 88(%rsp), %rdx
                call kof_init_object
                movq %r12, %rdi
                xorl %esi, %esi
                call sqlite3_column_int64
                movq 80(%rsp), %rdx
                movq %rax, 16(%rdx)
                movq %r12, %rdi
                movl $1, %esi
                call sqlite3_column_int64
                movq 80(%rsp), %rdx
                movq %rax, 24(%rdx)
                movq %r12, %rdi
                xorl %esi, %esi
                call kof_ormh4_colstr
                movq 80(%rsp), %rdx
                movq %rax, 32(%rdx)
                movq %r12, %rdi
                movl $3, %esi
                call kof_ormh4_colstr
                movq 80(%rsp), %rdx
                movq %rax, 40(%rdx)
                movq %r12, %rdi
                movl $4, %esi
                call kof_ormh4_colstr
                movq 80(%rsp), %rdx
                movq %rax, 48(%rdx)
                movq 72(%rsp), %rdi
                movq 80(%rsp), %rsi
                call kof_list_add
                jmp .Lormh5p_row
            .Lormh5p_noface:
                movl $512, %edi
                call .Lorm_bbegin
                leaq .Lormh4_nofacemsg(%rip), %rsi
                movl $.Lormh4_nofacemsg_len, %ecx
                call .Lorm_bp
                call .Lorm_bfin
                movq %rbx, %rdi
                call kof_throw_string
                ud2
            .Lormh5p_done:
                movq 72(%rsp), %rax
                addq $120, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret

            # kof_orm_history_one(id*, key*, revision, table*, schema*,
            #                     className*) -> Revision*
            #   slots: 0 id | 8 key | 16 revision | 24 table | 32 schema |
            #   40 className | 48 conn | 56 stmt | 64 dst | 72 vtab |
            #   80 typeId | 88 totalSize | 96 pks
            # ---------------------------------------------------------------
                @@SYMBOL@@
            kof_orm_history_one:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $120, %rsp
                movq %rdi, (%rsp)
                movq %rsi, 8(%rsp)
                movl %edx, 16(%rsp)
                movq %rcx, 24(%rsp)
                movq %r8, 32(%rsp)
                movq %r9, 40(%rsp)
            @@CONN@@
                movq 40(%rsp), %rax
                leaq 24(%rax), %rdi
                movl 16(%rax), %esi
                call kof_orm_ctors
                testq %rax, %rax
                jz .Lormh41_noface
                movq %rax, 72(%rsp)
                movq %rdx, 80(%rsp)
                movq %rcx, 88(%rsp)
                movq 24(%rsp), %rax
                movl 16(%rax), %eax
                addl $300, %eax
                movl %eax, %edi
                call .Lorm_bbegin
                leaq .Lormh4_sel(%rip), %rsi
                movl $.Lormh4_sel_len, %ecx
                call .Lorm_bp
                movl $34, %r8d
                call .Lorm_bh
                movq 24(%rsp), %rdi
                movl 16(%rdi), %r12d
                leaq 24(%rdi), %rsi
                movl %r12d, %ecx
                call .Lorm_bp
                leaq .Lormh3_suf(%rip), %rsi
                movl $.Lormh3_suf_len, %ecx
                call .Lorm_bp
                movl $34, %r8d
                call .Lorm_bh
                leaq .Lormh41_where(%rip), %rsi
                movl $.Lormh41_where_len, %ecx
                call .Lorm_bp
                call .Lorm_bfin
                movq 48(%rsp), %rdi
                leaq 24(%rbx), %rsi
                movq $-1, %rdx
                leaq 56(%rsp), %rcx
                xorl %r8d, %r8d
                call sqlite3_prepare_v2
                movq 56(%rsp), %r12
                testq %r12, %r12
                jz .Lormh41_done0
                movq 8(%rsp), %rdi
                call kof_box_to_string
                movq %rax, %r13
                testq %r13, %r13
                jz .Lormh41_bn
                movq %r12, %rdi
                movl $1, %esi
                leaq 24(%r13), %rdx
                movl 16(%r13), %ecx
                movslq %ecx, %rcx
                movq $-1, %r8
                call sqlite3_bind_text
                jmp .Lormh41_b2
            .Lormh41_bn:
                movq %r12, %rdi
                movl $1, %esi
                call sqlite3_bind_null
            .Lormh41_b2:
                movq %r12, %rdi
                movl $2, %esi
                movslq 16(%rsp), %rdx
                call sqlite3_bind_int64
                movq %r12, %rdi
                call sqlite3_step
                cmpl $101, %eax
                jne .Lormh41_rb
                xorl %eax, %eax               # ausente → null
                jmp .Lormh41_ret
            .Lormh41_rb:
                movq 88(%rsp), %rdi
                call kof_alloc
                movq %rax, 64(%rsp)
                movq 64(%rsp), %rdi
                movl 80(%rsp), %esi
                movq 72(%rsp), %rdx
                call kof_init_object
                movq %r12, %rdi
                xorl %esi, %esi
                call sqlite3_column_int64
                movq 64(%rsp), %rdx
                movq %rax, 16(%rdx)
                movq %r12, %rdi
                movl $1, %esi
                call sqlite3_column_int64
                movq 64(%rsp), %rdx
                movq %rax, 24(%rdx)
                movq %r12, %rdi
                xorl %esi, %esi
                call kof_ormh4_colstr
                movq 64(%rsp), %rdx
                movq %rax, 32(%rdx)
                movq %r12, %rdi
                movl $3, %esi
                call kof_ormh4_colstr
                movq 64(%rsp), %rdx
                movq %rax, 40(%rdx)
                movq %r12, %rdi
                movl $4, %esi
                call kof_ormh4_colstr
                movq 64(%rsp), %rdx
                movq %rax, 48(%rdx)
                movq 64(%rsp), %rax
            .Lormh41_ret:
                movq %r12, %rdi
                call sqlite3_finalize
                addq $120, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret
            .Lormh41_noface:
                movl $512, %edi
                call .Lorm_bbegin
                leaq .Lormh4_nofacemsg(%rip), %rsi
                movl $.Lormh4_nofacemsg_len, %ecx
                call .Lorm_bp
                call .Lorm_bfin
                movq %rbx, %rdi
                call kof_throw_string
                ud2
            .Lormh41_done0:
                xorl %eax, %eax
                jmp .Lormh41_ret

            .Lormh41_where:
                .ascii " WHERE entity_pk = ? AND revision = ?"
                .set .Lormh41_where_len, . - .Lormh41_where
            """;
        sb.append(b
                .replace("@@SYMBOL@@",
                        ".globl kof_orm_history_page\n            .type kof_orm_history_page, @function\n"
                      + "            .globl kof_orm_history_one\n            .type kof_orm_history_one, @function")
                .replace("@@CONN@@",
                        "                movq (%rsp), %rdi\n                call .Lorm_conn\n"
                      + "                movq %rax, 56(%rsp)             # conn (page)\n"
                      + "                movq (%rsp), %rdi\n                call .Lorm_conn\n"
                      + "                movq %rax, 48(%rsp)             # conn (one)"));
    }
}
