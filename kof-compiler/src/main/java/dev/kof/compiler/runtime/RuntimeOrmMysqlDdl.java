package dev.kof.compiler.runtime;

/**
 * F2d7 (D-DB-GAPS DB-3, 22/09): DDL no wire MySQL do runtime Native x86-64 —
 * {@code kof_orm_create} (variante {@code .Lorm2my_create} emitida por
 * {@link RuntimeOrm2#emitMysql}) e {@code kof_orm_migrate}
 * ({@code .Lorm_mig_my}) usam {@code .Lorm_ddl_exec}: COM_QUERY cru com
 * contrato do host ({@code kof_db_execute}): OK devolve affectedRows; pacote
 * ERR do servidor -> throw {@code "mysql: <msg>"} (o host deixa a
 * SQLException propagar — {@code kof_db_execute_n} nao captura); conexao
 * perdida -> {@code "mysql: connection lost"}; id invalido ->
 * {@code .Lorm_bad_conn} (mesma string do host).
 *
 * <p>O {@code .Lorm_mig_my} espelha {@code JvmOrmRuntime.kof_orm_migrate}:
 * CREATE da tabela de historico (rc ignorado, como la), SELECT COUNT(*)
 * WHERE name = &lt;literal&gt; no executor ja medido
 * ({@code .Lorm_count_mysql}), ja aplicada -> true; exec do sql do usuario;
 * INSERT (name, ms desde epoch via clock_gettime syscall 228, mesmo padrao
 * dos spans) -> true. Dialeto backtick do host
 * ({@code kof_orm_q}/mysql), incluindo {@code kof_migrations}.
 *
 * <p>Emitido sempre sob {@code usesOrm} (o dispatch de
 * {@code kof_orm_create}/{@code kof_orm_migrate} e incondicional; ha
 * programa que so cria migra tabelas, sem ctor classes). Contrato de pilha
 * (F1c): todo {@code call} de C sai com rsp ≡ 0.
 */
public final class RuntimeOrmMysqlDdl {

    private RuntimeOrmMysqlDdl() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            # `.Lorm_ddl_exec(rdi=id*, rsi=sqlPtr, edx=sqlLen) -> eax rc ---
            .Lorm_ddl_exec:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %r12
                pushq %r13
                subq $32, %rsp
                movq %rdi, 0(%rsp)
                movq %rsi, 8(%rsp)
                movl %edx, 16(%rsp)
                call kof_db_resolve
                testq %rax, %rax
                jz .Lddl_badconn
                movq %rax, %r12              # fd
                leaq .Ldb_mysql_buf(%rip), %r13
                movb $0x03, 4(%r13)
                movq 8(%rsp), %rsi
                movl 16(%rsp), %ecx
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
                jle .Lddl_lost
                cmpb $0xFF, (%rsi)
                je .Lddl_err
                cmpb $0x00, (%rsi)
                jne .Lddl_lost
                incq %rsi
                call kof_db_mysql_lenenc      # eax = affectedRows
                addq $32, %rsp
                popq %r13
                popq %r12
                movq %rbp, %rsp
                popq %rbp
                ret
            .Lddl_err:
                movq %rax, %r13              # packet len
                leaq 9(%rsi), %r12           # msg = apos ff+errno+#+state
                cmpq $9, %r13
                jle .Lddl_errempty
                movq %r13, %rdx
                subq $9, %rdx
                cmpq $400, %rdx
                jle .Lddl_errlen
                movq $400, %rdx
                jmp .Lddl_errlen
            .Lddl_errempty:
                movq $0, %rdx
            .Lddl_errlen:
                movq %rdx, 16(%rsp)
                movl $407, %edi              # 7 + teto
                addl %edx, %edi
                call .Lorm_bbegin
                leaq .Lddl_mypfx(%rip), %rsi
                movl $7, %ecx
                call .Lorm_bp
                movq 16(%rsp), %rcx
                movq %r12, %rsi
                call .Lorm_bp
                call .Lorm_bfin
                movq %rbx, %rdi
                call kof_throw_string
                ud2
            .Lddl_lost:
                leaq .Lddl_lostv(%rip), %rdi
                call kof_throw_string
                ud2
            .Lddl_badconn:
                movq 0(%rsp), %rdi
                call .Lorm_bad_conn
                ud2

            # ---------------------------------------------------------------
            # .Lorm_mig_my(rdi=id*, rsi=name*, rdx=sql*) -> eax Bool
            #   slots: 0 id | 8 name | 16 sql | 24 fd | 32 timespec | 48 ms
            # ---------------------------------------------------------------
            .Lorm_mig_my:
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
                movq 0(%rsp), %rdi
                leaq .Lmig_ddl(%rip), %rsi
                movl $.Lmig_ddl_len, %edx
                call .Lorm_ddl_exec          # rc ignorado (host)
                movq 0(%rsp), %rdi
                call kof_db_resolve
                testq %rax, %rax
                jz .Lmig_badconn
                movq %rax, 24(%rsp)          # fd
                movl $88, %edi
                movq 8(%rsp), %rax
                addl 16(%rax), %edi
                call .Lorm_bbegin
                leaq .Lmig_sel(%rip), %rsi
                movl $.Lmig_sel_len, %ecx
                call .Lorm_bp
                movq 8(%rsp), %rdi
                call kof_db_mysql_render
                leaq 24(%rax), %rsi
                movl 16(%rax), %ecx
                call .Lorm_bp
                call .Lorm_bfin
                movq 24(%rsp), %rdi
                movq %rbx, %rsi
                call .Lorm_count_mysql
                cmpq $0, %rax
                jg .Lmig_true                # ja aplicada
                movq 0(%rsp), %rdi
                movq 16(%rsp), %rax
                leaq 24(%rax), %rsi
                movl 16(%rax), %edx
                call .Lorm_ddl_exec          # sql do usuario
                leaq 32(%rsp), %rdi
                call kof_plat_time
                movq 32(%rsp), %rax
                imulq $1000, %rax, %r12      # sec -> ms
                movq 40(%rsp), %rax
                xorl %edx, %edx
                movq $1000000, %rcx
                divq %rcx
                addq %rax, %r12
                movq %r12, 48(%rsp)
                movl $160, %edi
                movq 8(%rsp), %rax
                addl 16(%rax), %edi
                call .Lorm_bbegin
                leaq .Lmig_ins(%rip), %rsi
                movl $.Lmig_ins_len, %ecx
                call .Lorm_bp
                movq 8(%rsp), %rdi
                call kof_db_mysql_render
                leaq 24(%rax), %rsi
                movl 16(%rax), %ecx
                call .Lorm_bp
                leaq .Lmig_ins2(%rip), %rsi
                movl $.Lmig_ins2_len, %ecx
                call .Lorm_bp
                movq 48(%rsp), %rdi
                call kof_long_to_string
                leaq 24(%rax), %rsi
                movl 16(%rax), %ecx
                call .Lorm_bp
                movl $41, %r8d               # ')'
                call .Lorm_bh
                call .Lorm_bfin
                movq 0(%rsp), %rdi
                leaq 24(%rbx), %rsi
                movl 16(%rbx), %edx
                call .Lorm_ddl_exec          # rc ignorado (host)
            .Lmig_true:
                movl $1, %eax
                addq $72, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret
            .Lmig_badconn:
                movq 0(%rsp), %rdi
                call .Lorm_bad_conn
                ud2

            # ---------------------- literais --------------------------------
            .Lddl_mypfx:
                .ascii "mysql: "
            .Lddl_lostv:
                .long 1
                .long 0
                .quad 0
                .long .Lddl_lostv_len
                .long 0
            .Lddl_lostv_body:
                .ascii "mysql: connection lost"
                .byte 0
                .set .Lddl_lostv_len, . - .Lddl_lostv_body - 1
            .Lmig_ddl:
                .ascii "CREATE TABLE IF NOT EXISTS `kof_migrations` (`name` VARCHAR(255) PRIMARY KEY, `applied_at` BIGINT)"
                .set .Lmig_ddl_len, . - .Lmig_ddl
            .Lmig_sel:
                .ascii "SELECT COUNT(*) FROM `kof_migrations` WHERE `name` = "
                .set .Lmig_sel_len, . - .Lmig_sel
            .Lmig_ins:
                .ascii "INSERT INTO `kof_migrations` (`name`, `applied_at`) VALUES ("
                .set .Lmig_ins_len, . - .Lmig_ins
            .Lmig_ins2:
                .ascii ", "
                .set .Lmig_ins2_len, . - .Lmig_ins2
            """);
    }
}
