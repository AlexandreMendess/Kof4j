package dev.kof.compiler.runtime;

/**
 * F2d4b (D-DB-GAPS DB-3, 21/09): whitelist/validacao do operador das faces
 * {@code where}/{@code where_op} no wire MySQL — extraida do
 * RuntimeOrmMysqlWhere para manter a fatia abaixo de 500 linhas (mesmo
 * criterio do split Find/KeyLit do F2d3b). ABI interna (nao e simbolo C):
 * {@code .Lorm_my_op(rdi=op|0) -> rax=opPtr, rdx=opLen}; op 0 (face de
 * igualdade) e {@code "=="} devolvem o literal {@code "="}; {@code >}
 * {@code <} {@code >=} {@code <=} {@code !=} {@code LIKE} devolvem o proprio
 * corpo; o resto throw {@code "ORM operator not allowed: " + op} —
 * whitelist IDENTICA a medida no host ({@code JvmOrmRuntime},
 * case-sensitive inclusive).
 */
public final class RuntimeOrmMysqlOp {

    private RuntimeOrmMysqlOp() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            # ============ orm where op whitelist mysql (F2d4b) ===========

            # .Lorm_my_op(rdi=op|0) -> rax=opPtr, rdx=opLen
            .Lorm_my_op:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                subq $24, %rsp
                testq %rdi, %rdi
                jz .Lorm_op_eq
                movq %rdi, %rcx
                movl 16(%rcx), %eax
                cmpl $1, %eax
                jne .Lorm_op_2
                movzbl 24(%rcx), %edx
                cmpl $62, %edx                   # '>'
                je .Lorm_op_use
                cmpl $60, %edx                   # '<'
                je .Lorm_op_use
                jmp .Lorm_op_bad
            .Lorm_op_2:
                cmpl $2, %eax
                jne .Lorm_op_4
                movzbl 24(%rcx), %edx
                movzbl 25(%rcx), %r8d
                cmpl $61, %edx                   # '=' -> "==" vira "="
                je .Lorm_op_eq
                cmpl $62, %edx
                jne .Lorm_op_2b
                cmpl $61, %r8d
                je .Lorm_op_use
                jmp .Lorm_op_bad
            .Lorm_op_2b:
                cmpl $60, %edx
                jne .Lorm_op_2c
                cmpl $61, %r8d
                je .Lorm_op_use
                jmp .Lorm_op_bad
            .Lorm_op_2c:
                cmpl $33, %edx                   # '!'
                jne .Lorm_op_bad
                cmpl $61, %r8d
                je .Lorm_op_use
                jmp .Lorm_op_bad
            .Lorm_op_4:
                cmpl $4, %eax
                jne .Lorm_op_bad
                movzbl 24(%rcx), %edx
                movzbl 25(%rcx), %r8d
                movzbl 26(%rcx), %r9d
                movzbl 27(%rcx), %r11d
                cmpl $76, %edx                   # 'L'
                jne .Lorm_op_bad
                cmpl $73, %r8d                   # 'I'
                jne .Lorm_op_bad
                cmpl $75, %r9d                   # 'K'
                jne .Lorm_op_bad
                cmpl $69, %r11d                  # 'E'
                jne .Lorm_op_bad
            .Lorm_op_use:
                leaq 24(%rcx), %rax
                movl 16(%rcx), %edx
                jmp .Lorm_op_ret
            .Lorm_op_eq:
                leaq .Lorm_op_eqstr(%rip), %rax
                movl $1, %edx
                jmp .Lorm_op_ret
            # ---- op recusado -> throw "ORM operator not allowed: <op>" ----
            .Lorm_op_bad:
                movq %rcx, 0(%rsp)
                movl 16(%rcx), %eax
                movl %eax, 8(%rsp)
                movl $26, %edi                   # len da mensagem
                addl %eax, %edi
                call .Lorm_bbegin
                leaq .Lorm_op_msg(%rip), %rsi
                movl $26, %ecx
                call .Lorm_bp
                movq 0(%rsp), %rax
                leaq 24(%rax), %rsi
                movl 8(%rsp), %ecx
                call .Lorm_bp
                call .Lorm_bfin
                movq %rbx, %rdi
                call kof_throw_string
                ud2
            .Lorm_op_ret:
                addq $24, %rsp
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret

            # ---------------------- literais --------------------------------
            .Lorm_op_eqstr:
                .ascii "="
            .Lorm_op_msg:
                .ascii "ORM operator not allowed: "
            """);
    }
}
