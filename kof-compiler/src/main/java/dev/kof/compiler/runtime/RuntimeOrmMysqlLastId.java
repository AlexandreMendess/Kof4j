package dev.kof.compiler.runtime;

/**
 * F2d5 (D-DB-GAPS DB-3, 22/09): {@code SELECT LAST_INSERT_ID()} no wire
 * MySQL do Native x86-64 (extraido por responsabilidade do
 * {@code RuntimeOrmMysqlSave}): envia a query via COM_QUERY, caminha
 * colunas + EOF + primeira linha e devolve o VALOR da celula como long
 * (digitos; NULL/sem linha/sem resultset -> 0, como o
 * {@code getGeneratedKeys} que nao avanca no host). Pacote ERR do servidor
 * -> throw {@code "mysql: <msg>"}; conexao perdida -> throw
 * {@code "mysql: connection lost"}; id invalido ->
 * {@code .Lorm_bad_conn} (R6 — nunca silencio).
 *
 * <p>Clobbera o builder de KofString de {@code RuntimeOrm1}
 * (rbx/r14/r15): o chamador ({@code .Lorm_save_my}) so chama depois do
 * bfin do INSERT, quando o SQL ja foi usado.
 *
 * <p>Contrato de pilha (F1c): moldura ≡8 mod 16 antes do {@code subq},
 * todo {@code call} de C sai com rsp ≡ 0.
 */
public final class RuntimeOrmMysqlLastId {

    private RuntimeOrmMysqlLastId() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            # ---------------------------------------------------------------
            # F2d5: .Lorm_sm_lastid(rdi=id*) -> rax=long (LAST_INSERT_ID)
            #   slots: 0 id | 8 fd | 16 bufp | 24 cols | 32 colj | 40 rowcur |
            #          48 tmp
            # ---------------------------------------------------------------
            .Lorm_sm_lastid:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $56, %rsp
                movq %rdi, 0(%rsp)
                # ---- SQL: SELECT LAST_INSERT_ID() ------------------------
                movl $27, %edi
                call .Lorm_bbegin
                leaq .Lsm_li(%rip), %rsi
                movl $23, %ecx
                call .Lorm_bp
                call .Lorm_bfin
                # ---- COM_QUERY no fd ------------------------------------
                movq 0(%rsp), %rdi
                call kof_db_resolve
                testq %rax, %rax
                jz .Lsm_li_badconn
                movq %rax, %r12
                leaq .Ldb_mysql_buf(%rip), %r13
                movb $0x03, 4(%r13)
                leaq 24(%rbx), %rsi
                movl 16(%rbx), %ecx
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
                movq %r12, %rdi
                movq %r13, %rsi
                leaq 5(%rcx), %rdx
                call kof_net_write
                movq %r12, %rdi
                call kof_db_mysql_reset
                call kof_db_mysql_next
                testq %rax, %rax
                jle .Lsm_li_lost
                cmpb $0xFF, (%rsi)
                je .Lsm_li_err
                call kof_db_mysql_lenenc        # col count
                cmpl $1, %eax
                jl .Lsm_li_lost
                movl %eax, 24(%rsp)
                movq $0, 32(%rsp)
            .Lsm_li_cold:
                movl 32(%rsp), %eax
                cmpl 24(%rsp), %eax
                jge .Lsm_li_cdone
                call kof_db_mysql_next
                testq %rax, %rax
                jle .Lsm_li_lost
                call kof_db_mysql_lenenc        # catalog
                addq %rax, %rsi
                call kof_db_mysql_lenenc        # schema
                addq %rax, %rsi
                call kof_db_mysql_lenenc        # table
                addq %rax, %rsi
                call kof_db_mysql_lenenc        # org_table
                addq %rax, %rsi
                call kof_db_mysql_lenenc        # name
                addq %rax, %rsi
                incl 32(%rsp)
                jmp .Lsm_li_cold
            .Lsm_li_cdone:
                call kof_db_mysql_next          # pacote apos colunas
                testq %rax, %rax
                jle .Lsm_li_lost
                cmpb $0x00, (%rsi)
                je .Lsm_li_zero                 # OK: sem resultset
                call kof_db_mysql_next          # primeira linha
                testq %rax, %rax
                jle .Lsm_li_lost
                movzbl (%rsi), %eax
                cmpb $0xFF, %al
                je .Lsm_li_err
                cmpb $0xFE, %al
                je .Lsm_li_zero                 # sem linhas
                cmpb $0xFB, %al
                je .Lsm_li_zero                 # NULL
                call kof_db_mysql_lenenc        # len da celula
                movl %eax, %ecx
                xorl %r8d, %r8d                 # valor
                xorl %edx, %edx                 # j
            .Lsm_li_dig:
                cmpl %ecx, %edx
                jge .Lsm_li_have
                imulq $10, %r8, %r8
                movzbl (%rsi,%rdx), %eax
                subl $48, %eax                  # '0'
                addq %rax, %r8
                incl %edx
                jmp .Lsm_li_dig
            .Lsm_li_have:
                movq %r8, %rax
                jmp .Lsm_li_ret
            .Lsm_li_zero:
                xorl %eax, %eax
            .Lsm_li_ret:
                addq $56, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret
            # ---- ERR do servidor -> throw "mysql: <msg>" -----------------
            .Lsm_li_err:
                movq %rax, %r13
                leaq 9(%rsi), %r12
                cmpq $9, %r13
                jle .Lsm_li_errempty
                movq %r13, %rdx
                subq $9, %rdx
                cmpq $400, %rdx
                jle .Lsm_li_errlen
                movq $400, %rdx
                jmp .Lsm_li_errlen
            .Lsm_li_errempty:
                movq $0, %rdx
            .Lsm_li_errlen:
                movq %rdx, 48(%rsp)
                movl $407, %edi
                addl %edx, %edi
                call .Lorm_bbegin
                leaq .Lsm_li_my(%rip), %rsi
                movl $7, %ecx
                call .Lorm_bp
                movq 48(%rsp), %rcx
                movq %r12, %rsi
                call .Lorm_bp
                call .Lorm_bfin
                movq %rbx, %rdi
                call kof_throw_string
                ud2
            .Lsm_li_lost:
                leaq .Lsm_li_lostv(%rip), %rdi
                call kof_throw_string
                ud2
            .Lsm_li_badconn:
                movq 0(%rsp), %rdi
                call .Lorm_bad_conn
                ud2

            # ---------------------- literais --------------------------------
            .Lsm_li:
                .ascii "SELECT LAST_INSERT_ID()"
            .Lsm_li_my:
                .ascii "mysql: "
            .Lsm_li_lostv:
                .long 1
                .long 0
                .quad 0
                .long .Lsm_li_lostv_len
                .long 0
            .Lsm_li_lostv_body:
                .ascii "mysql: connection lost"
                .byte 0
                .set .Lsm_li_lostv_len, . - .Lsm_li_lostv_body - 1
            """);
    }
}
