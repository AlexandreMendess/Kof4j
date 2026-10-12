package dev.kof.compiler.runtime;

/**
 * kof.orm history no Native x86-64 — Fase 5 do entity-history-plan
 * (D-ENTITY-HISTORY; ordem da mantenedora 13/10 "ponta a ponta"):
 * {@code kof_orm_create_history} — o DDL da tabela de histórico
 * ({@code "t_history"}) com o schema FIXO das 8 colunas, espelhando o host
 * JVM ({@code JvmOrmHistoryRuntime}). SQLite only: uma conexão MySQL cai no
 * {@code .Lorm_conn} honesto (ORM001 em runtime — o mesmo contrato das
 * demais faces nativas do ORM).
 *
 * <p>Convenção do esqueleto (RuntimeOrm2): r14 = cursor do corpo, r15d =
 * len acumulado, rbx = KofString* em construção; slots: 0 id | 8 table |
 * 16 conn. Retorno = Bool (rc==0), como o host.
 */
public final class RuntimeOrmHistory1 {

    private RuntimeOrmHistory1() {}

    public static void emit(StringBuilder sb) {
        String b = """
            # kof_orm_create_history(id*, table*, schema*) -> Bool
            #   CREATE TABLE IF NOT EXISTS "t_history" (revision BIGINT NOT
            #   NULL, entity_pk VARCHAR(255) NOT NULL, operation VARCHAR(16)
            #   NOT NULL, at BIGINT NOT NULL, payload CLOB, actor
            #   VARCHAR(255), actor_kind VARCHAR(16), correlation_id
            #   VARCHAR(64)) — espelha o host JVM (Fase 5).
            #   slots: 0 id | 8 table | 16 conn
            # ---------------------------------------------------------------
                @@SYMBOL@@
            kof_orm_create_history:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $40, %rsp
                movq %rdi, (%rsp)
                movq %rsi, 8(%rsp)
            @@DISPATCH@@
            @@CONN@@
                # cap = 320 + tblLen (o DDL fixo ~460 + "_history" + folga)
                movq 8(%rsp), %rax
                movl 16(%rax), %edi
                addl $540, %edi
                call .Lorm_bbegin               # rbx=buf, r14=cursor, r15d=0
                leaq .Lormh_pre(%rip), %rsi
                movl $.Lormh_pre_len, %ecx
                call .Lorm_bp
                movl $34, %r8d                  # '"'
                call .Lorm_bh
                movq 8(%rsp), %rdi
                movl 16(%rdi), %r12d            # tblLen
                leaq 24(%rdi), %rsi
                movl %r12d, %ecx
                call .Lorm_bp                   # corpo da tabela (sem quotes)
                leaq .Lormh_suf(%rip), %rsi
                movl $.Lormh_suf_len, %ecx
                call .Lorm_bp                   # "_history"
                movl $34, %r8d                  # '"'
                call .Lorm_bh
                leaq .Lormh_cols(%rip), %rsi
                movl $.Lormh_cols_len, %ecx
                call .Lorm_bp
                call .Lorm_bfin
            @@EXEC@@
                testl %eax, %eax
                @@OKBOOL@@
                movzbl %al, %eax
                addq $40, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret

            .Lormh_pre:
                .ascii "CREATE TABLE IF NOT EXISTS "
                .set .Lormh_pre_len, . - .Lormh_pre
            .Lormh_suf:
                .ascii "_history"
                .set .Lormh_suf_len, . - .Lormh_suf
            .Lormh_cols:
                .ascii " (revision BIGINT NOT NULL, entity_pk VARCHAR(255) NOT NULL, operation VARCHAR(16) NOT NULL, at BIGINT NOT NULL, payload CLOB, actor VARCHAR(255), actor_kind VARCHAR(16), correlation_id VARCHAR(64))"
                .set .Lormh_cols_len, . - .Lormh_cols
            """;
        sb.append(b
                .replace("@@SYMBOL@@", ".globl kof_orm_create_history\n            .type kof_orm_create_history, @function")
                .replace("@@DISPATCH@@", "")
                .replace("@@CONN@@",
                        "                movq (%rsp), %rdi\n                call .Lorm_conn\n                movq %rax, 16(%rsp)             # conn no slot")
                .replace("@@EXEC@@",
                        "                movq 16(%rsp), %rdi\n                movq %rbx, %rsi\n                call .Lorm_exec")
                .replace("@@OKBOOL@@", "sete %al"));
    }
}
