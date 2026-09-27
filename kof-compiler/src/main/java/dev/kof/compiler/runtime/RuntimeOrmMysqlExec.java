package dev.kof.compiler.runtime;

/**
 * F2d4d (D-DB-GAPS DB-3, 21/09): exec COM_QUERY com throw no wire MySQL do
 * Native x86-64 (extraido do {@code RuntimeOrmMysqlSaveAll} pelo gate 500
 * — responsabilidade: enviar SQL e classificar a resposta). OK devolve
 * affectedRows (lenenc); pacote ERR do servidor -> throw
 * {@code "mysql: <msg>"} (msg apos ff+errno+#+state, teto 400); conexao
 * perdida/resposta nao-OK -> throw {@code "mysql: connection lost"};
 * id invalido -> throw {@code "unknown db connection: <id>"} (R6 — nunca
 * silencio).
 */
public final class RuntimeOrmMysqlExec {

    private RuntimeOrmMysqlExec() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            # `.Lorm_sa_exec(rdi=id*, rsi=sql*) -> eax=affectedRows -------
            .Lorm_sa_exec:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %r12
                pushq %r13
                subq $32, %rsp
                movq %rdi, 0(%rsp)
                movq %rsi, 8(%rsp)
                call kof_db_resolve
                testq %rax, %rax
                jz .Lsa_ex_badconn
                movq %rax, %r12              # fd
                leaq .Ldb_mysql_buf(%rip), %r13
                movb $0x03, 4(%r13)
                movq 8(%rsp), %rax
                leaq 24(%rax), %rsi
                movl 16(%rax), %ecx
                movl %ecx, 16(%rsp)
                movq %rcx, %rdx
                leaq 5(%r13), %rdi
                call kof_memcpy
                movl 16(%rsp), %ecx
                leal 1(%ecx), %eax
                movb %al, 0(%r13)
                shrl $8, %eax
                movb %al, 1(%r13)
                shrl $8, %eax
                movb %al, 2(%r13)
                movb $0, 3(%r13)
                movq %r12, %rdi
                movq %r13, %rsi
                leaq 5(%rcx), %rdx
                call kof_net_write
                movq %r12, %rdi
                call kof_db_mysql_reset
                call kof_db_mysql_next
                testq %rax, %rax
                jle .Lsa_ex_lost
                cmpb $0xFF, (%rsi)
                je .Lsa_ex_err
                cmpb $0x00, (%rsi)
                jne .Lsa_ex_lost
                incq %rsi
                call kof_db_mysql_lenenc      # eax = affectedRows
                addq $32, %rsp
                popq %r13
                popq %r12
                movq %rbp, %rsp
                popq %rbp
                ret
            .Lsa_ex_err:
                movq %rax, %r13              # packet len
                leaq 9(%rsi), %r12           # msg = apos ff+errno+#+state
                cmpq $9, %r13
                jle .Lsa_ex_errempty
                movq %r13, %rdx
                subq $9, %rdx
                cmpq $400, %rdx
                jle .Lsa_ex_errlen
                movq $400, %rdx
                jmp .Lsa_ex_errlen
            .Lsa_ex_errempty:
                movq $0, %rdx
            .Lsa_ex_errlen:
                movq %rdx, 16(%rsp)
                movl $407, %edi              # 7 + teto
                addl %edx, %edi
                call .Lorm_bbegin
                leaq .Lsa_mypfx(%rip), %rsi
                movl $7, %ecx
                call .Lorm_bp
                movq 16(%rsp), %rcx
                movq %r12, %rsi
                call .Lorm_bp
                call .Lorm_bfin
                movq %rbx, %rdi
                call kof_throw_string
                ud2
            .Lsa_ex_lost:
                leaq .Lsa_lostv(%rip), %rdi
                call kof_throw_string
                ud2
            .Lsa_ex_badconn:
                movq 0(%rsp), %rdi
                call .Lorm_bad_conn
                ud2

            .Lsa_mypfx:
                .ascii "mysql: "
            .Lsa_lostv:
                .long 1
                .long 0
                .quad 0
                .long .Lsa_lostv_len
                .long 0
            .Lsa_lostv_body:
                .ascii "mysql: connection lost"
                .byte 0
                .set .Lsa_lostv_len, . - .Lsa_lostv_body - 1
                        """);
    }
}
