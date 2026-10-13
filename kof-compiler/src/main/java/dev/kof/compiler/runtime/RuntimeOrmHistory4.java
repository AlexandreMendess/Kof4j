package dev.kof.compiler.runtime;

/**
 * kof.orm history no Native x86-64 — Fase 5 fatia 5d-1 do
 * entity-history-plan (D-ENTITY-HISTORY; ordem da mantenedora 13/10 "ponta
 * a ponta"): {@code kof_orm_history(id*, key*, table*, schema*,
 * className*)} → a {@code List<Revision>} ascendente. O espelho do
 * {@code kof_orm_all} (RuntimeOrm6) com: o SELECT das 5 colunas na ordem
 * dos campos do record Revision (o build POSICIONAL — sem o casamento por
 * nome, que casaria contra o schema da ENTIDADE), o {@code WHERE
 * entity_pk = ?} (o bind TEXT — a afinidade INTEGER da coluna casa) e o
 * {@code ORDER BY revision}. O ctor do Revision via {@code kof_orm_ctors}
 * (o className constante do programa); os campos no record = 16+i*8 (a
 * revisão/at = os int64 crus; operation/actor/correlationId = os ponteiros
 * de string; NULL → 0). SQLite only: MySQL → .Lorm_conn honesto (ORM001).
 */
public final class RuntimeOrmHistory4 {

    private RuntimeOrmHistory4() {}

    public static void emit(StringBuilder sb) {
        String b = """
            # kof_orm_history(id*, key*, table*, schema*, className*) -> list*
            #   slots: 0 id | 8 key | 16 table | 24 schema | 32 className |
            #   40 conn | 48 stmt | 56 list | 64 dst | 72 vtab | 80 typeId |
            #   88 totalSize
            # ---------------------------------------------------------------
                @@SYMBOL@@
            kof_orm_history:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $104, %rsp
                movq %rdi, (%rsp)
                movq %rsi, 8(%rsp)
                movq %rdx, 16(%rsp)
                movq %rcx, 24(%rsp)
                movq %r8, 32(%rsp)
            @@CONN@@
                # ---- o ctor do Revision (className → vtab/typeId/totalSize) -
                movq 32(%rsp), %rax
                leaq 24(%rax), %rdi
                movl 16(%rax), %esi
                call kof_orm_ctors
                testq %rax, %rax
                jz .Lormh4_noface
                movq %rax, 72(%rsp)          # vtab
                movq %rdx, 80(%rsp)          # typeId
                movq %rcx, 88(%rsp)          # totalSize
                call kof_list_new
                movq %rax, 56(%rsp)          # a lista destino
                # ---- SQL: SELECT ... FROM "t_history" WHERE ... ORDER BY ----
                movq 16(%rsp), %rax
                movl 16(%rax), %eax          # tblLen
                addl $300, %eax
                movl %eax, %edi
                call .Lorm_bbegin
                leaq .Lormh4_sel(%rip), %rsi
                movl $.Lormh4_sel_len, %ecx
                call .Lorm_bp
                movl $34, %r8d
                call .Lorm_bh
                movq 16(%rsp), %rdi
                movl 16(%rdi), %r12d
                leaq 24(%rdi), %rsi
                movl %r12d, %ecx
                call .Lorm_bp
                leaq .Lormh4_suf(%rip), %rsi
                movl $.Lormh4_suf_len, %ecx
                call .Lorm_bp
                movl $34, %r8d
                call .Lorm_bh
                leaq .Lormh4_where(%rip), %rsi
                movl $.Lormh4_where_len, %ecx
                call .Lorm_bp
                call .Lorm_bfin
                # ---- prepare + bind 1 = key (TEXTO: a afinidade INTEGER casa) -
                movq 40(%rsp), %rdi
                leaq 24(%rbx), %rsi
                movq $-1, %rdx
                leaq 48(%rsp), %rcx
                xorl %r8d, %r8d
                call sqlite3_prepare_v2
                movq 48(%rsp), %r12
                testq %r12, %r12
                jz .Lormh4_done0
                movq 8(%rsp), %rdi
                call kof_box_to_string
                movq %rax, %r13
                testq %r13, %r13
                jz .Lormh4_bnull
                movq %r12, %rdi
                movl $1, %esi
                leaq 24(%r13), %rdx
                movl 16(%r13), %ecx
                movslq %ecx, %rcx
                movq $-1, %r8
                call sqlite3_bind_text
                jmp .Lormh4_row
            .Lormh4_bnull:
                movq %r12, %rdi
                movl $1, %esi
                call sqlite3_bind_null
            .Lormh4_row:
                # ---- o loop de linhas: o Revision posicional ----------------
                movq %r12, %rdi
                call sqlite3_step
                cmpl $101, %eax              # SQLITE_DONE
                jne .Lormh4_rowbody
                movq %r12, %rdi
                call sqlite3_finalize
                jmp .Lormh4_done
            .Lormh4_rowbody:
                movq 88(%rsp), %rdi
                call kof_alloc
                movq %rax, 64(%rsp)          # dst (o Revision novo)
                movq 64(%rsp), %rdi
                movl 80(%rsp), %esi
                movq 72(%rsp), %rdx
                call kof_init_object
                # os 5 campos: 16+i*8 — rev(0)=i64, at(1)=i64,
                # operation(2)/actor(3)/correlationId(4)=texto (NULL → 0)
                movq %r12, %rdi
                movl $0, %esi
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
                movq 56(%rsp), %rdi
                movq 64(%rsp), %rsi
                call kof_list_add
                jmp .Lormh4_row
            .Lormh4_noface:
                movl $512, %edi
                call .Lorm_bbegin
                leaq .Lormh4_nofacemsg(%rip), %rsi
                movl $.Lormh4_nofacemsg_len, %ecx
                call .Lorm_bp
                call .Lorm_bfin
                movq %rbx, %rdi
                call kof_throw_string
                ud2
            .Lormh4_done0:
                movq $0, 56(%rsp)
            .Lormh4_done:
                movq 56(%rsp), %rax
                addq $104, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret

            # kof_ormh4_colstr(r12=stmt, esi=col) -> rax = KofString* | 0 (NULL)
            kof_ormh4_colstr:
                # o col idx em r13d (CALLEE-saved — o column_type/text clobberam
                # o esi/rsi pela C ABI; o bug do ciclo 72: o esi virava lixo)
                pushq %r12
                pushq %r13
                movq %rdi, %r12
                movl %esi, %r13d
                movq %r12, %rdi
                movl %r13d, %esi
                call sqlite3_column_type
                cmpl $5, %eax                # SQLITE_NULL → 0 (o host: null)
                jne .Lormh4_cs1
                xorl %eax, %eax
                jmp .Lormh4_csret
            .Lormh4_cs1:
                movq %r12, %rdi
                movl %r13d, %esi
                call sqlite3_column_text
                movq %rax, %rdi
                call kof_io_strlen
                movq %rax, %rsi
                movq %r12, %rdi
                movl %r13d, %esi
                call sqlite3_column_text
                movq %rax, %rdi
                call kof_io_make_string
            .Lormh4_csret:
                popq %r13
                popq %r12
                ret

            .Lormh4_sel:
                .ascii "SELECT revision, at, operation, actor, correlation_id AS correlationId FROM "
                .set .Lormh4_sel_len, . - .Lormh4_sel
            .Lormh4_where:
                .ascii " WHERE entity_pk = ? ORDER BY revision"
                .set .Lormh4_where_len, . - .Lormh4_where
            .Lormh4_suf:
                .ascii "_history"
                .set .Lormh4_suf_len, . - .Lormh4_suf
            .Lormh4_nofacemsg:
                .ascii "orm.history: entity class not registered (ORM001)"
                .set .Lormh4_nofacemsg_len, . - .Lormh4_nofacemsg
            """;
        sb.append(b
                .replace("@@SYMBOL@@", ".globl kof_orm_history\n            .type kof_orm_history, @function")
                .replace("@@CONN@@",
                        "                movq (%rsp), %rdi\n                call .Lorm_conn\n                movq %rax, 40(%rsp)             # conn no slot"));
    }
}
