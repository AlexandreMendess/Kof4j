package dev.kof.compiler.runtime;

/**
 * kof.orm no Native x86-64 — ramo MySQL do wire (D-DB-GAPS DB-3, fatias
 * F2d1/F2d2, 21/09; §493 26/09). Extraido de RuntimeOrm1 no gate 500 com a
 * responsabilidade: as faces ORM delegam aqui quando o id e mysql
 * (kof_db_type(id)==2); o dialect backtick espelha o host kof_orm_q; count usa
 * os readers de resultset kof_db_mysql_reset/next/lenenc, e delete/deleteAll
 * usam o exec que lanca no ERR do servidor, como a lei D-DECISION-BATCH-2609.
 * Entradas .L chamadas por kof_orm_delete_all/kof_orm_count (labels resolvidos
 * no mesmo .s, slices emitidas juntas por NativeOrmEmit).
 */
public final class RuntimeOrmMysql {

    private RuntimeOrmMysql() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            .text
            # =============== kof.orm mysql wire (F2d1/F2d2) ===============

            # .Lorm_da_my(rdi=id, rsi=table) -> rax Bool (affectedRows>=0)
            .Lorm_da_my:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $24, %rsp
                movq %rdi, (%rsp)               # id
                movq %rsi, 8(%rsp)              # table
                # mesmo builder, prefixo/sufixo backtick (dialect do host)
                movq 8(%rsp), %rax
                movl 16(%rax), %eax
                addl $22, %eax
                movl %eax, %edi
                call .Lorm_bbegin
                movq %rbx, 16(%rsp)
                leaq .Lorm_delmy(%rip), %rsi
                movl $13, %ecx
                call .Lorm_bp
                movq 8(%rsp), %r13
                leaq 24(%r13), %rsi
                movl 16(%r13), %ecx
                call .Lorm_bp
                movl $96, %r8d                  # '`'
                call .Lorm_bh
                call .Lorm_bfin
                movq (%rsp), %rdi
                movq 16(%rsp), %rsi
                call .Lorm_sa_exec             # §493: ERR do servidor LANCA como o host
                movl $1, %eax                 # OK packet: affectedRows >= 0 sempre true
                addq $24, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret

            # .Lorm_cnt_my(rdi=id, rsi=table) -> rax Long
            .Lorm_cnt_my:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $24, %rsp
                movq %rdi, (%rsp)               # id
                movq %rsi, 8(%rsp)              # table
                # mesmo builder com backtick (dialect do host kof_orm_q)
                movq 8(%rsp), %rax
                movl 16(%rax), %eax
                addl $39, %eax
                movl %eax, %edi
                call .Lorm_bbegin
                movq %rbx, 16(%rsp)
                leaq .Lorm_cnt_myit(%rip), %rsi
                movl $22, %ecx
                call .Lorm_bp
                movq 8(%rsp), %r13
                leaq 24(%r13), %rsi
                movl 16(%r13), %ecx
                call .Lorm_bp
                movl $96, %r8d                  # '`'
                call .Lorm_bh
                call .Lorm_bfin
                movq (%rsp), %rdi
                call kof_db_resolve             # fd mysql | 0
                testq %rax, %rax
                jz .Lorm_cnt_zero
                movq %rax, %rdi
                movq 16(%rsp), %rsi
                call .Lorm_count_mysql
                jmp .Lorm_cnt_ret
                addq $24, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret

            # .Lorm_count_mysql(rdi=fd, rsi=sql*) -> rax = valor unico
            #   COM_QUERY + resultset (1 coluna); erro/NULL -> 0.
            .Lorm_count_mysql:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                subq $16, %rsp
                movq %rdi, %rbx                 # fd
                movq %rsi, %r12                 # sql*
                leaq .Ldb_mysql_buf(%rip), %r13
                movb $0x03, 4(%r13)
                leaq 24(%r12), %rsi
                movl 16(%r12), %ecx
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
                movq %rbx, %rdi
                movq %r13, %rsi
                leaq 5(%rcx), %rdx
                call kof_net_write
                movq %rbx, %rdi
                call kof_db_mysql_reset
                call kof_db_mysql_next           # 1o pacote (col count ou ERR)
                testq %rax, %rax
                jle .Lorm_cm_zero
                cmpb $0xFF, (%rsi)
                je .Lorm_cm_zero
                call kof_db_mysql_lenenc         # col count
                movl %eax, %r14d
            .Lorm_cm_cols:
                testl %r14d, %r14d
                jle .Lorm_cm_row
                call kof_db_mysql_next           # pula 1 column definition
                testq %rax, %rax
                jle .Lorm_cm_zero
                decl %r14d
                jmp .Lorm_cm_cols
            .Lorm_cm_row:
                call kof_db_mysql_next           # pacote pos-colunas (EOF 0xFE)
                testq %rax, %rax
                jle .Lorm_cm_zero
                cmpb $0x00, (%rsi)               # OK = sem resultset
                je .Lorm_cm_zero
                call kof_db_mysql_next           # pacote do row
                testq %rax, %rax
                jle .Lorm_cm_zero
                movzbl (%rsi), %eax
                cmpb $0xFF, %al                 # ERR
                je .Lorm_cm_zero
                cmpb $0xFB, %al                 # NULL
                je .Lorm_cm_zero
                cmpb $0xFE, %al                 # EOF (0 rows — COUNT(*) sempre tem 1)
                je .Lorm_cm_zero
                call kof_db_mysql_lenenc         # eax=len do valor, rsi=dados
                movl %eax, %ecx
                xorl %eax, %eax
            .Lorm_cm_dig:
                testl %ecx, %ecx
                jle .Lorm_cm_ret
                movzbl (%rsi), %edx
                cmpb $'0', %dl
                jb .Lorm_cm_ret
                cmpb $'9', %dl
                ja .Lorm_cm_ret
                imulq $10, %rax, %rax
                subl $'0', %edx
                addq %rdx, %rax
                incq %rsi
                decl %ecx
                jmp .Lorm_cm_dig
            .Lorm_cm_zero:
                xorl %eax, %eax
            .Lorm_cm_ret:
                addq $16, %rsp
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret

            .Lorm_cnt_myit:
                .ascii "SELECT COUNT(*) FROM `"
            .Lorm_delmy:
                .ascii "DELETE FROM `"

            # .Lorm_del_my(rdi=id, rsi=key, rdx=table, rcx=schema) -> rax Bool
            #   F2d3a/§493: DELETE FROM `t` WHERE `pk` = literal no wire mysql.
            #   O key e renderizado/escapado por kof_db_mysql_render e trocado
            #   no '?' por kof_db_mysql_replace_q; a execucao usa .Lorm_sa_exec
            #   para que ERR do servidor LANCE (a lei D-DECISION-BATCH-2609).
            #   Host: execute1(...) >= 0 (miss tambem true). Slots: 0 id |
            #   8 key | 16 table | 24 schema | 40 ftab | 56 nFields |
            #   64 pkIndex | 88 pkEntry | 96 sql
            .Lorm_del_my:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $120, %rsp
                movq %rdi, 0(%rsp)
                movq %rsi, 8(%rsp)
                movq %rdx, 16(%rsp)
                movq %rcx, 24(%rsp)
                movq 24(%rsp), %rdi
                call kof_orm_parse_schema
                movq %rax, 40(%rsp)              # ftab
                movq %rcx, 56(%rsp)              # nFields
                movq %r8, 64(%rsp)               # pkIndex
                movq 64(%rsp), %rax
                shlq $5, %rax
                addq 40(%rsp), %rax
                movq %rax, 88(%rsp)              # entry da PK
            # ---- SQL: DELETE FROM `t` WHERE `pk` = ? ---------------------
                movq 88(%rsp), %rax
                movl 8(%rax), %edx               # pkLen
                movq 16(%rsp), %rcx
                addl 16(%rcx), %edx              # + tblLen
                addl $33, %edx
                movl %edx, %edi
                call .Lorm_bbegin
                leaq .Lorm_my_d1(%rip), %rsi
                movl $12, %ecx
                call .Lorm_bp
                movq 16(%rsp), %r13
                movl $96, %r8d                   # '`'
                call .Lorm_bh
                leaq 24(%r13), %rsi
                movl 16(%r13), %ecx
                call .Lorm_bp
                movl $96, %r8d
                call .Lorm_bh
                leaq .Lorm_my_w(%rip), %rsi
                movl $7, %ecx
                call .Lorm_bp
                movq 88(%rsp), %r13
                movl $96, %r8d
                call .Lorm_bh
                movq 0(%r13), %rsi
                movl 8(%r13), %ecx
                call .Lorm_bp
                movl $96, %r8d
                call .Lorm_bh
                leaq .Lorm_my_q(%rip), %rsi
                movl $4, %ecx
                call .Lorm_bp
                call .Lorm_bfin
                movq %rbx, 96(%rsp)
            # ---- render do bind + exec COM_QUERY que LANCA no ERR ----------
                movq 8(%rsp), %rdi
                call kof_db_mysql_render
                movq %rax, %rsi
                movq 96(%rsp), %rdi
                call kof_db_mysql_replace_q
                movq %rax, 96(%rsp)
                movq 0(%rsp), %rdi
                movq %rax, %rsi
                call .Lorm_sa_exec
                movl $1, %eax                 # OK packet: affectedRows >= 0 sempre true
                addq $120, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret

            .Lorm_my_d1:
                .ascii "DELETE FROM "
            .Lorm_my_w:
                .ascii " WHERE "
            .Lorm_my_q:
                .ascii " = ?"
            """);
    }
}
