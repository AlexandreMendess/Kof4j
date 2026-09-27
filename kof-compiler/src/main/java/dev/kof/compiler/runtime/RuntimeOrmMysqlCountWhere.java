package dev.kof.compiler.runtime;

/**
 * F2d6 (D-DB-GAPS DB-3, 22/09): face {@code count} com filtro
 * ({@code orm.count<T>(db, field, value)}) no wire MySQL do runtime Native
 * x86-64 — {@code kof_orm_count_where} com {@code kof_db_type(id)==2}
 * despacha para {@code .Lorm_cw_my}, que espelha
 * {@code JvmOrmRuntime.kof_orm_count_where}: {@code SELECT COUNT(*) FROM
 * `t` WHERE `f` = <literal>} (dialeto backtick do host; o schema nao entra
 * no SQL, como no host) com o value em LITERAL SQL — box §284
 * (int/long/bool/double/float), KofString via {@code kof_db_mysql_render},
 * null → {@code NULL}; tipo fora do contrato → ORM001 (R6). <b>Corr. 24/09
 * (S5.5 fatia 2):</b> o check do bool usava o tag 1 (String, inalcançável no
 * box) — o contrato §284 é tag 3 ({@code kof_box_bool}); com o tag errado o
 * bind bool caía no ORM001 (paridade x86 mysql × sqlite quebrada). A query roda no
 * executor de 1 coluna ja medido do {@code RuntimeOrmMysql}
 * ({@code .Lorm_count_mysql}: erro/NULL/sem linha → 0). id invalido →
 * {@code .Lorm_bad_conn} (mesma string do host). Chama de
 * {@code .Lorm_sa_qp} (backtick de nome, RuntimeOrmMysqlFieldLit).
 *
 * <p>Emitido sempre sob {@code usesOrm} (fora do gate de
 * {@code ormCtorClasses}): o dispatch e incondicional e o programa que so
 * usa count nao registra ctors. GC-safe (value no slot de stack durante a
 * montagem; o literal vira KofString do builder).
 *
 * <p>Contrato de pilha (F1c): moldura ≡8 mod 16 antes do {@code subq},
 * todo {@code call} de C sai com rsp ≡ 0.
 */
public final class RuntimeOrmMysqlCountWhere {

    private RuntimeOrmMysqlCountWhere() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            # ---------------------------------------------------------------
            # F2d6: .Lorm_cw_my(rdi=id*, rsi=field*, rdx=value*, rcx=table*,
            #   r8=schema*) -> rax=Long (COUNT com igualdade)
            #   slots: 0 id | 8 field | 16 value | 24 table | 32 schema |
            #          40 sql | 48 fd | 56 tmp
            # ---------------------------------------------------------------
            .Lorm_cw_my:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $72, %rsp
                movq %rdi, 0(%rsp)
                movq %rsi, 8(%rsp)
                movq %rdx, 16(%rsp)
                movq %rcx, 24(%rsp)
                movq %r8, 32(%rsp)
            # ---- SQL: SELECT COUNT(*) FROM `t` WHERE `f` = <lit> ---------
                movq 24(%rsp), %rax
                movl 16(%rax), %edi
                movq 8(%rsp), %rax
                addl 16(%rax), %edi
                addl $288, %edi
                call .Lorm_bbegin
                leaq .Lcw_s1(%rip), %rsi
                movl $21, %ecx
                call .Lorm_bp
                movq 24(%rsp), %rax
                leaq 24(%rax), %rsi
                movl 16(%rax), %ecx
                call .Lorm_sa_qp             # `table`
                leaq .Lcw_s2(%rip), %rsi
                movl $7, %ecx
                call .Lorm_bp
                movq 8(%rsp), %rax
                leaq 24(%rax), %rsi
                movl 16(%rax), %ecx
                call .Lorm_sa_qp             # `field`
                leaq .Lcw_s3(%rip), %rsi
                movl $3, %ecx
                call .Lorm_bp
            # ---- value -> literal (box §284 / KofString / null) ----------
                movq 16(%rsp), %r13
                testq %r13, %r13
                jz .Lcw_null
                movq (%r13), %rax
                movabsq $@@MAGIC@@, %rcx
                cmpq %rcx, %rax
                jne .Lcw_strchk
                movl 8(%r13), %eax           # tag do box §284
                cmpl $0, %eax
                je .Lcw_bint
                cmpl $3, %eax
                je .Lcw_bbool
                cmpl $2, %eax
                je .Lcw_bquad
                cmpl $4, %eax
                je .Lcw_bdbl
                cmpl $5, %eax
                je .Lcw_bflt
                jmp .Lcw_bad
            .Lcw_bint:
                movslq 16(%r13), %rdi
                call kof_long_to_string
                jmp .Lcw_append
            .Lcw_bquad:
                movq 16(%r13), %rdi
                call kof_long_to_string
                jmp .Lcw_append
            .Lcw_bbool:
                movl 16(%r13), %edi
                testl %edi, %edi
                setne %dil
                movzbl %dil, %edi
                call kof_long_to_string
                jmp .Lcw_append
            .Lcw_bdbl:
                movq 16(%r13), %rax
                movq %rax, %xmm0
                call kof_double_to_string
                jmp .Lcw_append
            .Lcw_bflt:
                movd 16(%r13), %xmm0
                cvtss2sd %xmm0, %xmm0
                call kof_double_to_string
                jmp .Lcw_append
            .Lcw_strchk:
                cmpl $1, (%r13)              # KofString (1,0,0)?
                jne .Lcw_bad
                cmpl $0, 4(%r13)
                jne .Lcw_bad
                cmpq $0, 8(%r13)
                jne .Lcw_bad
                movq %r13, %rdi
                call kof_db_mysql_render
                jmp .Lcw_append
            .Lcw_null:
                leaq .Lcw_nullv(%rip), %rsi
                movl $4, %ecx
                call .Lorm_bp
                jmp .Lcw_go
            .Lcw_append:                      # rax = KofString*
                leaq 24(%rax), %rsi
                movl 16(%rax), %ecx
                call .Lorm_bp
            .Lcw_go:
                call .Lorm_bfin
                movq %rbx, 40(%rsp)
            # ---- resolve + executor de 1 coluna --------------------------
                movq 0(%rsp), %rdi
                call kof_db_resolve
                testq %rax, %rax
                jz .Lcw_badconn
                movq %rax, %rdi
                movq 40(%rsp), %rsi
                call .Lorm_count_mysql
                addq $72, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret
            .Lcw_bad:
                leaq .Lcw_badv(%rip), %rdi
                call kof_throw_string
                ud2
            .Lcw_badconn:
                movq 0(%rsp), %rdi
                call .Lorm_bad_conn
                ud2

            # ---------------------- literais --------------------------------
            .Lcw_s1:
                .ascii "SELECT COUNT(*) FROM "
            .Lcw_s2:
                .ascii " WHERE "
            .Lcw_s3:
                .ascii " = "
            .Lcw_nullv:
                .ascii "NULL"
            .Lcw_badv:
                .long 1
                .long 0
                .quad 0
                .long .Lcw_badv_len
                .long 0
            .Lcw_badv_body:
                .ascii "orm.count bind value: unsupported type on Native (ORM001)"
                .byte 0
                .set .Lcw_badv_len, . - .Lcw_badv_body - 1
            """);
    }
}
