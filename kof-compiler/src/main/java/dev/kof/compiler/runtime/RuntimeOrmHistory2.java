package dev.kof.compiler.runtime;

/**
 * kof.orm history no Native x86-64 — Fase 5 fatia 5b do entity-history-plan
 * (D-ENTITY-HISTORY; ordem da mantenedora 13/10 "ponta a ponta"):
 * {@code kof_orm_delete_recorded} — o DELETE gravando a revisão
 * (espelha o host JVM {@code JvmOrmHistoryRuntime.kof_orm_delete_recorded}):
 * chama o {@code kof_orm_delete} existente; se ok, INSERT da linha de
 * histórico (rev = COALESCE(MAX)+1 por entity_pk; operation='DELETE';
 * payload={@code {"deleted":true}}; at = epoch-ms; actor NULL;
 * actor_kind='system'; correlation_id NULL — o nativo não tem stack de
 * auth para a face, o default honesto do host fora de request).
 * SQLite only: conexão MySQL cai no {@code .Lorm_conn} honesto (ORM001).
 *
 * <p>Reaproveita RuntimeOrm1 (bbegin/bp/bh/bfin/conn/exec), o bind direto
 * sqlite3_* (o padrão RuntimeOrm8) e {@code kof_box_to_string} (o pk →
 * texto, o String.valueOf do host).
 */
public final class RuntimeOrmHistory2 {

    private RuntimeOrmHistory2() {}

    public static void emit(StringBuilder sb) {
        String b = """
            # kof_orm_delete_recorded(id*, key*, table*, schema*) -> Bool
            #   delete + a revisão DELETE no "t_history" (espelha o host JVM).
            #   slots: 0 id | 8 key | 16 table | 24 schema | 32 conn |
            #   40 stmt | 48 pks | 56 rev | 64 ok
            # ---------------------------------------------------------------
                @@SYMBOL@@
            kof_orm_delete_recorded:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $80, %rsp
                movq %rdi, (%rsp)
                movq %rsi, 8(%rsp)
                movq %rdx, 16(%rsp)
                movq %rcx, 24(%rsp)
            @@CONN@@
                # ---- o DML: o kof_orm_delete existente ---------------------
                movq (%rsp), %rdi
                movq 8(%rsp), %rsi
                movq 16(%rsp), %rdx
                movq 24(%rsp), %rcx
                call kof_orm_delete
                movzbl %al, %eax
                movq %rax, 64(%rsp)
                cmpq $0, 64(%rsp)
                je .Lormh2_done               # o delete falhou → false (sem linha)
                movl $256, %edi
                call kof_alloc
                leaq .Lormh2_s1(%rip), %rsi
                movl $.Lormh2_s1_len, %ecx
                movq %rax, %rbx
                movq %rax, %r14
                xorl %r15d, %r15d
                call .Lorm_bp
                call .Lorm_bfin
                movq %rbx, %rdi
                call kof_throw_string
                ud2
                # ---- BISECT: o record, rev SELECT apenas -------------------
                movq 16(%rsp), %rax
                movl 16(%rax), %edi
                addl $220, %edi
                call .Lorm_bbegin
                leaq .Lormh2_revpre(%rip), %rsi
                movl $.Lormh2_revpre_len, %ecx
                call .Lorm_bp
                movl $34, %r8d
                call .Lorm_bh
                movq 16(%rsp), %rdi
                movl 16(%rdi), %r12d
                leaq 24(%rdi), %rsi
                movl %r12d, %ecx
                call .Lorm_bp
                leaq .Lormh2_suf(%rip), %rsi
                movl $.Lormh2_suf_len, %ecx
                call .Lorm_bp
                movl $34, %r8d
                call .Lorm_bh
                leaq .Lormh2_revwhere(%rip), %rsi
                movl $.Lormh2_revwhere_len, %ecx
                call .Lorm_bp
                call .Lorm_bfin
                movq 32(%rsp), %rdi
                leaq 24(%rbx), %rsi
                movq $-1, %rdx
                leaq 40(%rsp), %rcx
                xorl %r8d, %r8d
                call sqlite3_prepare_v2
                movq 40(%rsp), %r12
                testq %r12, %r12
                jz .Lormh2_done
                movq %r12, %rdi
                call sqlite3_step
                movq %r12, %rdi
                call sqlite3_finalize
                jmp .Lormh2_done
                # ---- pk → texto (o String.valueOf do host) -----------------
                movq 8(%rsp), %rdi
                call kof_box_to_string
                movq %rax, 48(%rsp)
                # ---- rev = SELECT COALESCE(MAX(revision),0)+1 ... ----------
                movq 16(%rsp), %rax
                movl 16(%rax), %edi
                addl $220, %edi
                call .Lorm_bbegin
                leaq .Lormh2_revpre(%rip), %rsi
                movl $.Lormh2_revpre_len, %ecx
                call .Lorm_bp
                movl $34, %r8d
                call .Lorm_bh
                movq 16(%rsp), %rdi
                movl 16(%rdi), %r12d
                leaq 24(%rdi), %rsi
                movl %r12d, %ecx
                call .Lorm_bp
                leaq .Lormh2_suf(%rip), %rsi
                movl $.Lormh2_suf_len, %ecx
                call .Lorm_bp
                movl $34, %r8d
                call .Lorm_bh
                leaq .Lormh2_revwhere(%rip), %rsi
                movl $.Lormh2_revwhere_len, %ecx
                call .Lorm_bp
                call .Lorm_bfin
                movq 32(%rsp), %rdi
                leaq 24(%rbx), %rsi
                movq $-1, %rdx
                leaq 40(%rsp), %rcx
                xorl %r8d, %r8d
                call sqlite3_prepare_v2
                movq 40(%rsp), %r12
                testq %r12, %r12
                jz .Lormh2_done0
                movq %r12, %rdi
                movl $1, %esi
                movq 48(%rsp), %rdx
                movq %rdx, %rax
                testq %rax, %rax
                jz .Lormh2_revnull
                leaq 24(%rax), %rdx
                movl 16(%rax), %ecx
                movslq %ecx, %rcx
                movq $-1, %r8
                call sqlite3_bind_text
                jmp .Lormh2_revstep
            .Lormh2_revnull:
                movq %r12, %rdi
                movl $1, %esi
                call sqlite3_bind_null
            .Lormh2_revstep:
                movq %r12, %rdi
                call sqlite3_step
                movq %r12, %rdi
                call sqlite3_column_int64
                movq %rax, 56(%rsp)           # rev
                movq %r12, %rdi
                call sqlite3_finalize
                # ---- INSERT da linha DELETE (8 binds fixos) ----------------
                movq 16(%rsp), %rax
                movl 16(%rax), %edi
                addl $340, %edi
                call .Lorm_bbegin
                leaq .Lormh2_inspre(%rip), %rsi
                movl $.Lormh2_inspre_len, %ecx
                call .Lorm_bp
                movl $34, %r8d
                call .Lorm_bh
                movq 16(%rsp), %rdi
                movl 16(%rdi), %r12d
                leaq 24(%rdi), %rsi
                movl %r12d, %ecx
                call .Lorm_bp
                leaq .Lormh2_suf(%rip), %rsi
                movl $.Lormh2_suf_len, %ecx
                call .Lorm_bp
                movl $34, %r8d
                call .Lorm_bh
                leaq .Lormh2_inssuf(%rip), %rsi
                movl $.Lormh2_inssuf_len, %ecx
                call .Lorm_bp
                call .Lorm_bfin
                movq 32(%rsp), %rdi
                leaq 24(%rbx), %rsi
                movq $-1, %rdx
                leaq 40(%rsp), %rcx
                xorl %r8d, %r8d
                call sqlite3_prepare_v2
                movq 40(%rsp), %r12
                testq %r12, %r12
                jz .Lormh2_done0
                # bind 1: rev
                movq %r12, %rdi
                movl $1, %esi
                movq 56(%rsp), %rdx
                call sqlite3_bind_int64
                # bind 2: pk (texto)
                movq %r12, %rdi
                movl $2, %esi
                movq 48(%rsp), %rdx
                testq %rdx, %rdx
                jz .Lormh2_i2n
                leaq 24(%rdx), %rcx
                movl 16(%rdx), %r8d
                movslq %r8d, %r8
                movq $-1, %r9
                call sqlite3_bind_text
                jmp .Lormh2_i3
            .Lormh2_i2n:
                movq %r12, %rdi
                movl $2, %esi
                call sqlite3_bind_null
            .Lormh2_i3:
                # bind 3: 'DELETE'
                movq %r12, %rdi
                movl $3, %esi
                leaq .Lormh2_delop(%rip), %rdx
                movl $.Lormh2_delop_len, %ecx
                movq $-1, %r8
                call sqlite3_bind_text
                # bind 4: at = epoch-ms (kof_time_now() -> rax; sem args)
                call kof_time_now
                movq %r12, %rdi
                movl $4, %esi
                movq %rax, %rdx
                call sqlite3_bind_int64
                # bind 5: payload '{"deleted":true}'
                movq %r12, %rdi
                movl $5, %esi
                leaq .Lormh2_payload(%rip), %rdx
                movl $.Lormh2_payload_len, %ecx
                movq $-1, %r8
                call sqlite3_bind_text
                # bind 6: actor NULL
                movq %r12, %rdi
                movl $6, %esi
                call sqlite3_bind_null
                # bind 7: actor_kind 'system'
                movq %r12, %rdi
                movl $7, %esi
                leaq .Lormh2_system(%rip), %rdx
                movl $.Lormh2_system_len, %ecx
                movq $-1, %r8
                call sqlite3_bind_text
                # bind 8: correlation_id NULL
                movq %r12, %rdi
                movl $8, %esi
                call sqlite3_bind_null
                movq %r12, %rdi
                call sqlite3_step
                movq %r12, %rdi
                call sqlite3_finalize
            .Lormh2_done:
                movq 64(%rsp), %rax
                testq %rax, %rax
                sete %al
                movzbl %al, %eax
                addq $80, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret
            .Lormh2_done0:
                movq $0, 64(%rsp)
                jmp .Lormh2_done

            .Lormh2_revpre:
                .ascii "SELECT COALESCE(MAX(revision), 0) + 1 FROM "
                .set .Lormh2_revpre_len, . - .Lormh2_revpre
            .Lormh2_suf:
                .ascii "_history"
                .set .Lormh2_suf_len, . - .Lormh2_suf
            .Lormh2_revwhere:
                .ascii " WHERE entity_pk = ?"
                .set .Lormh2_revwhere_len, . - .Lormh2_revwhere
            .Lormh2_inspre:
                .ascii "INSERT INTO "
                .set .Lormh2_inspre_len, . - .Lormh2_inspre
            .Lormh2_inssuf:
                .ascii " (revision, entity_pk, operation, at, payload, actor, actor_kind, correlation_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
                .set .Lormh2_inssuf_len, . - .Lormh2_inssuf
            .Lormh2_delop:
                .ascii "DELETE"
                .set .Lormh2_delop_len, . - .Lormh2_delop
            .Lormh2_payload:
                .ascii "{\\"deleted\\":true}"
                .set .Lormh2_payload_len, . - .Lormh2_payload
            .Lormh2_system:
                .ascii "system"
                .set .Lormh2_system_len, . - .Lormh2_system
            .Lormh2_s1:
                .ascii "STAGE1-DELETE-OK"
                .set .Lormh2_s1_len, . - .Lormh2_s1
            """;
        sb.append(b
                .replace("@@SYMBOL@@", ".globl kof_orm_delete_recorded\n            .type kof_orm_delete_recorded, @function")
                .replace("@@CONN@@",
                        "                movq (%rsp), %rdi\n                call .Lorm_conn\n                movq %rax, 32(%rsp)             # conn no slot (o schema fica em 24)"));
    }
}
