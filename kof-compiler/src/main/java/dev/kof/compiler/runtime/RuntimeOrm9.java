package dev.kof.compiler.runtime;

/**
 * F2c3 (D-DB-GAPS, 21/09): face {@code delete} da escrita row-object no
 * runtime Native x86-64 — {@code kof_orm_delete} espelha
 * {@code JvmOrmRuntime.kof_orm_delete}: {@code DELETE FROM "t" WHERE "pk" = ?}
 * com a PK resolvida pelo {@code kof_orm_parse_schema} (r8 = pkIndex, mesmo
 * critério do host) e o bind do key pelo MESMO classificador do value no
 * {@code RuntimeOrm7} (box 284 / KofString do coerce do call-site / null).
 * O host retorna {@code execute1(...) >= 0}: SQLITE_DONE implica sempre
 * {@code true}, inclusive no miss (oracle JVM medido 21/09: delete de PK
 * inexistente devolve true e o count não muda). Falha de prepare/step lança
 * {@code "sqlite: " + sqlite3_errmsg} (R6); MySQL segue
 * {@code .Lorm_conn} honesto (ORM001). Sem className (não precisa de ctors).
 *
 * <p>Contrato de pilha (F1c): prologo com {@code andq}, frame 104 (≡8 antes
 * do {@code subq}, como a família), todo {@code call} de C sai com rsp ≡ 0;
 * stmt em {@code r12}, SQL em {@code rbx}.
 */
public final class RuntimeOrm9 {

    private RuntimeOrm9() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            # ---------------------------------------------------------------
            # F2c3 (D-DB-GAPS, 21/09): kof_orm_delete(id*,key*,table*,schema*)
            #   -> Bool (rax) — sempre true no DONE (host execute1 >= 0,
            #   medido). slots: 0 id | 8 key | 16 table | 24 schema |
            #   32 conn | 40 ftab | 56 nFields | 64 pkIndex | 80 stmt |
            #   88 pkEntry
            # ---------------------------------------------------------------
                .globl kof_orm_delete
                .type kof_orm_delete, @function
            kof_orm_delete:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $104, %rsp
                movq %rdi, 0(%rsp)
                movq %rsi, 8(%rsp)
                movq %rdx, 16(%rsp)
                movq %rcx, 24(%rsp)
                movq (%rsp), %rdi
                call kof_db_type
                cmpl $2, %eax
                je .Lorm9_mysql                   # F2d3a: wire mysql
                movq (%rsp), %rdi
                call .Lorm_conn
                movq %rax, 32(%rsp)
                movq 24(%rsp), %rdi
                call kof_orm_parse_schema
                movq %rax, 40(%rsp)          # ftab
                movq %rcx, 56(%rsp)          # nFields
                movq %r8, 64(%rsp)           # pkIndex
                movq 64(%rsp), %rax
                shlq $5, %rax
                addq 40(%rsp), %rax
                movq %rax, 88(%rsp)          # entry da PK
            # ---- SQL: DELETE FROM "t" WHERE "pk" = ? ------------------------
                movq 88(%rsp), %rax
                movl 8(%rax), %edx           # pkLen
                movq 16(%rsp), %rcx
                addl 16(%rcx), %edx          # + tblLen
                addl $33, %edx               # 12+7+4 + 2 quotes + folga
                movl %edx, %edi
                call .Lorm_bbegin
                leaq .Lorm9_d1(%rip), %rsi
                movl $12, %ecx
                call .Lorm_bp
                movq 16(%rsp), %rdi
                call .Lorm2_qq
                leaq .Lorm9_w(%rip), %rsi
                movl $7, %ecx
                call .Lorm_bp
                movq 88(%rsp), %rax
                movq 0(%rax), %rsi
                movl 8(%rax), %ecx
                call .Lorm_qraw
                leaq .Lorm9_q(%rip), %rsi
                movl $4, %ecx
                call .Lorm_bp
                call .Lorm_bfin
            # ---- prepare + bind key (param 1, classificador do Orm7) --------
                movq 32(%rsp), %rdi
                leaq 24(%rbx), %rsi
                movq $-1, %rdx
                leaq 80(%rsp), %rcx
                xorl %r8d, %r8d
                call sqlite3_prepare_v2
                movq 80(%rsp), %r12
                testq %r12, %r12
                jz .Lorm9_prep_fail
                movq %r12, %rdi
                movl $1, %esi
                movq 8(%rsp), %r13
                testq %r13, %r13
                jz .Lorm9_bnull
                movq (%r13), %rax
                movabsq $@@MAGIC@@, %rcx
                cmpq %rcx, %rax
                jne .Lorm9_strchk
                movl 8(%r13), %eax
                cmpl $0, %eax
                je .Lorm9_bint
                cmpl $1, %eax
                je .Lorm9_bquad
                cmpl $2, %eax
                je .Lorm9_bquad
                cmpl $4, %eax
                je .Lorm9_bdbl
                cmpl $5, %eax
                je .Lorm9_bflt
                jmp .Lorm9_bnull
            .Lorm9_bint:
                movl 16(%r13), %eax
                movslq %eax, %rdx
                jmp .Lorm9_bq
            .Lorm9_bquad:
                movq 16(%r13), %rdx
            .Lorm9_bq:
                call sqlite3_bind_int64
                jmp .Lorm9_bstep
            .Lorm9_bdbl:
                movq 16(%r13), %rax
                movq %rax, %xmm0
                call sqlite3_bind_double
                jmp .Lorm9_bstep
            .Lorm9_bflt:
                movl 16(%r13), %eax
                movd %eax, %xmm0
                cvtss2sd %xmm0, %xmm0
                call sqlite3_bind_double
                jmp .Lorm9_bstep
            .Lorm9_strchk:
                cmpl $1, (%r13)
                jne .Lorm9_bnull
                cmpl $0, 4(%r13)
                jne .Lorm9_bnull
                cmpl $0, 8(%r13)
                jne .Lorm9_bnull
                movq 8(%r13), %rax
                testq %rax, %rax
                jnz .Lorm9_bnull
                leaq 24(%r13), %rdx
                movl 16(%r13), %ecx
                movslq %ecx, %rcx
                movq $-1, %r8
                call sqlite3_bind_text
                jmp .Lorm9_bstep
            .Lorm9_bnull:
                call sqlite3_bind_null
            .Lorm9_bstep:
                movq %r12, %rdi
                call sqlite3_step
                cmpl $101, %eax              # SQLITE_DONE
                jne .Lorm9_sql_fail
                movq %r12, %rdi
                call sqlite3_finalize
                movq $1, %rax                # execute1 >= 0 = sempre true
                jmp .Lorm9_ret
            .Lorm9_sql_fail:
                movq %r12, %rdi
                call sqlite3_finalize
            .Lorm9_prep_fail:
                movl $512, %edi
                call .Lorm_bbegin
                leaq .Lorm9_pre(%rip), %rsi
                movl $8, %ecx
                call .Lorm_bp
                movq 32(%rsp), %rdi
                call sqlite3_errmsg
                testq %rax, %rax
                jz .Lorm9_pfin
                movq %rax, %rsi
                call .Lorm_pcstr
            .Lorm9_pfin:
                call .Lorm_bfin
                movq %rbx, %rdi
                call kof_throw_string
                ud2
            .Lorm9_mysql:                    # F2d3a: delega ao wire mysql
                movq 0(%rsp), %rdi
                movq 8(%rsp), %rsi
                movq 16(%rsp), %rdx
                movq 24(%rsp), %rcx
                call .Lorm_del_my
                jmp .Lorm9_ret
            .Lorm9_ret:
                addq $104, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret

            .Lorm9_d1:
                .ascii "DELETE FROM "
            .Lorm9_w:
                .ascii " WHERE "
            .Lorm9_q:
                .ascii " = ?"
            .Lorm9_pre:
                .ascii "sqlite: "
        """);
    }
}
