package dev.kof.compiler.runtime;

/**
 * kof.orm no Native x86-64 — fatia F1a (D-DB-GAPS DB-1, 20/09):
 * {@code delete_all} SQL-puro sobre o stack kof_db_* existente. SQLite
 * only: MySQL lanca ORM001 honesto em runtime; o cross (riscv/aarch64) e
 * as demais faces seguem ORM001 em compile-time (R6/R7 — nunca silent).
 * Id invalido lanca a MESMA string do host JVM ("unknown db connection: "
 * + id) — paridade por construcao.
 *
 * <p>Convencao dos builders (tudo privado .L): r14 = cursor do corpo,
 * r15d = len acumulado, rbx = KofString* em construcao; os helpers so
 * clobberam rax/rcx/rdx/rsi/rdi/r8-r11. KofString: tag@0=1, len@16,
 * corpo@24. Retornos espelham o host JVM: Bool=(rc==0); SQL error no
 * wrapper JDBC vira -1, logo delete_all = false (nunca excecao).
 */
public final class RuntimeOrm1 {

    private RuntimeOrm1() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            # =============== kof.orm F1 helpers (privados .L) ===============

            # .Lorm_bp: append rsi[0..ecx) no builder (r14 cursor, r15d len)
            .Lorm_bp:
                xorl %eax, %eax
            .Lorm_bp_loop:
                cmpl %ecx, %eax
                jge .Lorm_bp_done
                movzbl (%rsi,%rax), %edx
                movb %dl, (%r14)
                incq %r14
                incl %eax
                incl %r15d
                jmp .Lorm_bp_loop
            .Lorm_bp_done:
                ret

            # .Lorm_bh: append 1 byte (r8b) no builder
            .Lorm_bh:
                movb %r8b, (%r14)
                incq %r14
                incl %r15d
                ret

            # .Lorm_bfin: NUL no fim + grava len no header (rbx)
            .Lorm_bfin:
                movb $0, (%r14)
                movl %r15d, 16(%rbx)
                ret

            # .Lorm_bbegin(rdi=cap) -> rbx=KofString* grande o bastante p/ cap
            .Lorm_bbegin:
                leal 25(%rdi), %edi
                call kof_alloc
                movq %rax, %rbx
                movl $1, 0(%rbx)
                movl $0, 4(%rbx)
                movq $0, 8(%rbx)
                movl $0, 16(%rbx)
                movl $0, 20(%rbx)
                leaq 24(%rbx), %r14
                xorl %r15d, %r15d
                movq %rbx, %rax
                ret

            # .Lorm_bad_conn(rdi=id*|NULL): lanca "unknown db connection: "+id
            .Lorm_bad_conn:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %r12
                xorl %r13d, %r13d
                testq %r12, %r12
                jz .Lorm_bc_go
                movl 16(%r12), %r13d
            .Lorm_bc_go:
                movl $23, %edi
                addl %r13d, %edi
                call .Lorm_bbegin
                leaq .Lorm_bc_pre(%rip), %rsi
                movl $23, %ecx
                call .Lorm_bp
                testq %r12, %r12
                jz .Lorm_bc_throw
                leaq 24(%r12), %rsi
                movl 16(%r12), %ecx
                call .Lorm_bp
            .Lorm_bc_throw:
                call .Lorm_bfin
                movq %rbx, %rdi
                call kof_throw_string
                ud2

            # .Lorm_mysql_pending: ORM001 honesto em runtime (R6)
            .Lorm_mysql_pending:
                leaq .Lorm_mysql_msg(%rip), %rdi
                call kof_throw_string
                ud2

            # .Lorm_conn(rdi=id*) -> rax=handle sqlite; mysql/ruim -> lanca
            .Lorm_conn:
                testq %rdi, %rdi
                jz .Lorm_cn_bad
                pushq %r12
                movq %rdi, %r12
                call kof_db_type
                cmpl $2, %eax
                je .Lorm_cn_my
                cmpl $1, %eax
                jne .Lorm_cn_bad_r
                movq %r12, %rdi
                call kof_db_resolve
                testq %rax, %rax
                jz .Lorm_cn_bad_r
                popq %r12
                ret
            .Lorm_cn_my:
                movq %r12, %rdi
                popq %r12
                call .Lorm_mysql_pending
                ud2
            .Lorm_cn_bad_r:
                movq %r12, %rdi
                popq %r12
            .Lorm_cn_bad:
                call .Lorm_bad_conn
                ud2

            # .Lorm_exec(rdi=db, rsi=sql*) -> eax = 0 ok | -1 SQL error
            .Lorm_exec:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                movq %rsi, %r12
                leaq 24(%r12), %rsi
                xorl %edx, %edx
                xorl %ecx, %ecx
                xorl %r8d, %r8d
                movq %rbx, %rdi
                call sqlite3_exec
                testl %eax, %eax
                jz .Lorm_ex_ok
                movl $-1, %eax
            .Lorm_ex_ok:
                popq %r13
                popq %r12
                popq %rbx
                ret

            # .Lorm_count_cb(void*slot, int argc, char**argv, char**colv): salva argv[0] no slot
            .Lorm_count_cb:                       # cb(void*d,int argc,char**argv,char**names)
                # argv[0] so existe DURANTE o callback (sqlite libera quando
                # exec volta) -> converte aqui; so o LONG sobrevive ao ret.
                testl %esi, %esi
                jle .Lorm_cb_skip
                testq %rdx, %rdx                  # argv
                jz .Lorm_cb_skip
                movq (%rdx), %rsi                 # argv[0] = valor (ex. "2")
                testq %rsi, %rsi
                jz .Lorm_cb_skip
                call .Lorm_atol
                movq %rax, .Lorm_count_buf(%rip)
            .Lorm_cb_skip:
                xorl %eax, %eax                   # 0 = nao abortar
                ret

            # .Lorm_atol(rsi=char*) -> rax (digitos; NULL -> 0)
            .Lorm_atol:
                xorl %eax, %eax
                testq %rsi, %rsi
                jz .Lorm_al_done
            .Lorm_al_loop:
                movzbl (%rsi), %ecx
                cmpb $'0', %cl
                jb .Lorm_al_done
                cmpb $'9', %cl
                ja .Lorm_al_done
                imulq $10, %rax, %rax
                movl %ecx, %edx
                subl $'0', %edx
                addq %rdx, %rax
                incq %rsi
                jmp .Lorm_al_loop
            .Lorm_al_done:
                ret

            # .Lorm_count_sql(rdi=db, rsi=sql*) -> rax = valor numerico unico
            .Lorm_count_sql:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                movq %rsi, %r12
                movq $0, .Lorm_count_buf(%rip)  # slot estatico (cb nao toca registrantes)
                movq %rbx, %rdi
                leaq 24(%r12), %rsi
                leaq .Lorm_count_cb(%rip), %rdx
                xorl %ecx, %ecx
                xorl %r8d, %r8d
                call sqlite3_exec
                movq .Lorm_count_buf(%rip), %rax  # valor ja convertido pelo cb
                popq %r13
                popq %r12
                popq %rbx
                ret

            # ---------------------- literais / dados ----------------------
            .Lorm_count_pre:
                .ascii "SELECT COUNT(*) FROM \\""
            .Lorm_mig_ddl:                       # KofString* (o .Lorm_exec espera objeto)
                .long 1
                .long 0
                .quad 0
                .long .Lorm_mig_ddl_len
                .long 0
            .Lorm_mig_ddl_body:
                .ascii "CREATE TABLE IF NOT EXISTS \\"kof_migrations\\" (\\"name\\" VARCHAR(255) PRIMARY KEY, \\"applied_at\\" BIGINT)"
                .byte 0
                .set .Lorm_mig_ddl_len, . - .Lorm_mig_ddl_body - 1
            .Lorm_mig_sel:
                .ascii "SELECT COUNT(*) FROM \\"kof_migrations\\" WHERE \\"name\\" = ?"
                .byte 0
            .Lorm_mig_ins:
                .ascii "INSERT INTO \\"kof_migrations\\" (\\"name\\", \\"applied_at\\") VALUES (?, ?)"
                .byte 0
            .Lorm_bc_pre:
                .ascii "unknown db connection: "
            .Lorm_delit:
                .ascii "DELETE FROM \\""
            .Lorm_mysql_msg:
                .long 1
                .long 0
                .quad 0
                .long .Lorm_mysql_len
                .long 0
            .Lorm_mysql_body:
                .ascii "kof.orm on native mysql: not available yet (ORM001)"
                .byte 0
                .set .Lorm_mysql_len, . - .Lorm_mysql_body - 1
            .bss
                .align 8
            .Lorm_count_buf:
                .zero 8
            .text

            # ---------------------------------------------------------------
            # kof_orm_delete_all(id*, table*, schema*) -> Bool (rax 0/1)
            #   SQLite: DELETE FROM "table"; rc==0 -> true; SQL error -> false
            #   MySQL (F2d1, D-DB-GAPS DB-3): DELETE FROM `table` via
            #   kof_db_execute(id, sql) — espelha o host
            #   (kof_orm_q(table, dialect) + kof_db_execute >= 0; o wire
            #   devolve 0|-1, logo >=0 == ==0). Nunca excecao no SQL error.
            # ---------------------------------------------------------------
            .globl kof_orm_delete_all
            .type kof_orm_delete_all, @function
            kof_orm_delete_all:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $56, %rsp
                movq %rdi, (%rsp)               # id
                movq %rsi, 8(%rsp)              # table
                call kof_db_type                # eax: 1=sqlite 2=mysql 0=ruim
                cmpl $2, %eax
                je .Lorm_da_mysql
                # cap = 13 + tblLen + 1 + 8 (folga)
                movq 8(%rsp), %rax
                movl 16(%rax), %eax
                addl $22, %eax
                movl %eax, %edi
                call .Lorm_bbegin               # rbx=str, r14=cursor, r15d=0
                movq %rbx, 16(%rsp)
                leaq .Lorm_delit(%rip), %rsi
                movl $13, %ecx
                call .Lorm_bp
                movq 8(%rsp), %r13
                leaq 24(%r13), %rsi
                movl 16(%r13), %ecx
                call .Lorm_bp
                movl $34, %r8d
                call .Lorm_bh
                call .Lorm_bfin
                movq (%rsp), %rdi
                call .Lorm_conn                 # rax = sqlite handle | lanca
                movq %rax, 24(%rsp)
                movq 24(%rsp), %rdi
                movq 16(%rsp), %rsi
                call .Lorm_exec
                testl %eax, %eax
                sete %al
                movzbl %al, %eax
                jmp .Lorm_da_ret
            .Lorm_da_mysql:
                movq (%rsp), %rdi
                movq 8(%rsp), %rsi
                call .Lorm_da_my
                jmp .Lorm_da_ret
            .Lorm_da_ret:
                addq $56, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret

            # ---------------------------------------------------------------
            # kof_orm_count(id*, table*, schema*) -> Long (rax)
            #   SQLite: SELECT COUNT(*) FROM "table"; id invalido lanca a
            #   string do host; SQL error no SELECT: callback nunca roda ->
            #   atol(0)=0 — mesmo zero-row do host (honesto).
            #   MySQL (F2d2, D-DB-GAPS DB-3): SELECT COUNT(*) FROM `table`
            #   via COM_QUERY — le o resultset com os readers do stack
            #   (kof_db_mysql_next/lenenc) e extrai o valor do 1o row com
            #   parse bounded por len (nunca le fora do pacote); erro/NULL ->
            #   0 (mesmo zero-row honesto do ramo sqlite).
            # ---------------------------------------------------------------
            .globl kof_orm_count
            .type kof_orm_count, @function
            kof_orm_count:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $56, %rsp
                movq %rdi, (%rsp)               # id
                movq %rsi, 8(%rsp)              # table
                call kof_db_type                # eax: 1=sqlite 2=mysql 0=ruim
                cmpl $2, %eax
                je .Lorm_cnt_mysql
                movq 8(%rsp), %rax
                movl 16(%rax), %eax
                addl $39, %eax                  # 22 + tbl + 1 + folga
                movl %eax, %edi
                call .Lorm_bbegin               # rbx=str, r14=cursor, r15d=0
                movq %rbx, 16(%rsp)
                leaq .Lorm_count_pre(%rip), %rsi
                movl $22, %ecx
                call .Lorm_bp
                movq 8(%rsp), %r13
                leaq 24(%r13), %rsi
                movl 16(%r13), %ecx
                call .Lorm_bp
                movl $34, %r8d
                call .Lorm_bh
                call .Lorm_bfin
                movq (%rsp), %rdi
                call .Lorm_conn                 # handle sqlite | lanca
                movq %rax, 24(%rsp)
                movq 16(%rsp), %rsi
                movq 24(%rsp), %rdi
                call .Lorm_count_sql
                jmp .Lorm_cnt_ret
            .Lorm_cnt_mysql:
                movq (%rsp), %rdi
                movq 8(%rsp), %rsi
                call .Lorm_cnt_my
                jmp .Lorm_cnt_ret
            .Lorm_cnt_zero:
                xorl %eax, %eax
            .Lorm_cnt_ret:
                addq $56, %rsp                  # espelhos delete_all: pops na regiao andada, rbp so no fim
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret



            # ---------------------------------------------------------------
            # kof_orm_migrate(id*, name*, sql*) -> Bool
            #   espelha o host: CREATE da tabela de historico (rc ignorado,
            #   como la); SELECT name = ? ja aplicada -> true; exec sql < 0 ->
            #   false; INSERT (name, ms desde epoch via clock_gettime syscall
            #   228, mesmo padrao dos spans) -> true. id ruim lanca as
            #   mesmas strings do host (.Lorm_conn); mysql (F2d7) tail-chama
            #   .Lorm_mig_my (RuntimeOrmMysqlDdl). Pilha fica 16-alinhada
            #   (subq $88) nos calls diretos ao sqlite; epilogio espelha
            #   delete_all (pops na regiao andada, rbp so no fim).
            # ---------------------------------------------------------------
            .globl kof_orm_migrate
            .type kof_orm_migrate, @function
kof_orm_migrate:
            pushq %rbp
            movq %rsp, %rbp
            andq $-16, %rsp               # mesmo padrao de delete_all/count (calls 16-alinhados)
            pushq %rbx
            pushq %r12
            pushq %r13
            pushq %r14
            pushq %r15
            subq $88, %rsp
            movq %rdi, (%rsp)               # id
            movq %rsi, 8(%rsp)              # name
            movq %rdx, 16(%rsp)             # sql
            movq (%rsp), %rdi
            call kof_db_type
            cmpl $2, %eax
            je .Lorm_mig_my_dispatch
            movq (%rsp), %rdi
            call .Lorm_conn
            movq %rax, %rbx                 # conn (callee-saved; .Lorm_* preservam)
            movq %rbx, %rdi
            leaq .Lorm_mig_ddl(%rip), %rsi
            call .Lorm_exec                 # CREATE IF NOT EXISTS; rc ignorado (host)
            leaq .Lorm_mig_sel(%rip), %rsi
            movq %rbx, %rdi
            movq $-1, %rdx
            leaq 40(%rsp), %rcx
            xorl %r8d, %r8d
            call sqlite3_prepare_v2
            testl %eax, %eax
            jnz .Lorm_mig_false
            movq 40(%rsp), %r14
            movq %r14, %rdi
            movl $1, %esi
            movq 8(%rsp), %rax
            leaq 24(%rax), %rdx             # corpo do name (KofString*)
            movq $-1, %rcx
            xorl %r8d, %r8d
            call sqlite3_bind_text
            movq %r14, %rdi
            call sqlite3_step
            cmpl $100, %eax                 # SQLITE_ROW
            jne .Lorm_mig_norow
            movq %r14, %rdi
            xorl %esi, %esi
            call sqlite3_column_text        # "0"/"N" enquanto vivo o stmt -> atol ja
            movq %rax, %rsi
            call .Lorm_atol
            movq %rax, 48(%rsp)
            .Lorm_mig_norow:
            movq %r14, %rdi
            call sqlite3_finalize
            cmpq $0, 48(%rsp)
            jg .Lorm_mig_true               # ja aplicada
            movq %rbx, %rdi
            movq 16(%rsp), %rsi             # KofString* cru (o .Lorm_exec soma o 24)
            call .Lorm_exec
            testl %eax, %eax
            js .Lorm_mig_false              # sql falhou (host: rc<0 -> false)
            leaq 24(%rsp), %rdi             # struct timespec
            call kof_plat_time
            movq 24(%rsp), %rax
            imulq $1000, %rax, %r12         # sec -> ms
            movq 32(%rsp), %rax             # nsec
            xorl %edx, %edx
            movq $1000000, %rcx
            divq %rcx
            addq %rax, %r12                 # ms total
            leaq .Lorm_mig_ins(%rip), %rsi
            movq %rbx, %rdi
            movq $-1, %rdx
            leaq 40(%rsp), %rcx
            xorl %r8d, %r8d
            call sqlite3_prepare_v2
            testl %eax, %eax
            jnz .Lorm_mig_false
            movq 40(%rsp), %r14
            movq %r14, %rdi
            movl $1, %esi
            movq 8(%rsp), %rax
            leaq 24(%rax), %rdx
            movq $-1, %rcx
            xorl %r8d, %r8d
            call sqlite3_bind_text
            movq %r14, %rdi
            movl $2, %esi
            movq %r12, %rdx
            call sqlite3_bind_int64
            movq %r14, %rdi
            call sqlite3_step
            movq %r14, %rdi
            call sqlite3_finalize
            .Lorm_mig_true:
            movl $1, %eax
            jmp .Lorm_mig_ret
            .Lorm_mig_false:
            xorl %eax, %eax
            .Lorm_mig_ret:
            addq $88, %rsp
            popq %r15
            popq %r14
            popq %r13
            popq %r12
            popq %rbx
            movq %rbp, %rsp
            popq %rbp
            ret

            # F2d7: mysql -> restaura o frame e tail-chama .Lorm_mig_my
            # (dialeto backtick + wire mysql; RuntimeOrmMysqlDdl).
            .Lorm_mig_my_dispatch:
            movq 0(%rsp), %rdi
            movq 8(%rsp), %rsi
            movq 16(%rsp), %rdx
            addq $88, %rsp
            popq %r15
            popq %r14
            popq %r13
            popq %r12
            popq %rbx
            movq %rbp, %rsp
            popq %rbp
            jmp .Lorm_mig_my

            """);
    }
}
