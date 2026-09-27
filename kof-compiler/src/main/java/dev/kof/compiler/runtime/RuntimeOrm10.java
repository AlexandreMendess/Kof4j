package dev.kof.compiler.runtime;

/**
 * F2c3 (D-DB-GAPS, 21/09): face {@code saveAll} da escrita row-object no
 * runtime Native x86-64 — {@code kof_orm_save_all} espelha
 * {@code JvmOrmRuntime.kof_orm_save_all}: loop {@code kof_list_size}/
 * {@code kof_list_get} acumulando {@code kof_orm_save} por item (a instância
 * patchada do save é DESCARTADA pelo host — mesma semântica aqui; a List de
 * entrada continua com os objetos originais, lidos de volta por
 * {@code all}/{@code where}). Retorno {@code true} sempre que o loop termina
 * (erro de SQL lança {@code "sqlite: " + errmsg} pelo próprio save — R6).
 * Sem className: os itens já carregam vtable/typeId (o save é row-object).
 *
 * <p>GC-safe: o item fica só em registrador durante o call do save, mas é
 * sempre alcançável pela List no slot 8 (stack), como no host.
 *
 * <p>Contrato de pilha (F1c): prologo com {@code andq}, frame 56 (≡8 antes
 * do {@code subq}, como a família), todo {@code call} de C sai com rsp ≡ 0.
 */
public final class RuntimeOrm10 {

    private RuntimeOrm10() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            # ---------------------------------------------------------------
            # F2c3 (D-DB-GAPS, 21/09): kof_orm_save_all(id*,items*,table*,
            #   schema*) -> Bool (rax=1) — loop kof_list_get -> kof_orm_save
            #   (retorno do save descartado, como o host). slots: 0 id |
            #   8 items | 16 table | 24 schema | 32 i | 40 n
            # ---------------------------------------------------------------
                .globl kof_orm_save_all
                .type kof_orm_save_all, @function
            kof_orm_save_all:
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
                movq %rsi, 8(%rsp)
                movq %rdx, 16(%rsp)
                movq %rcx, 24(%rsp)
                movq 0(%rsp), %rdi
                call kof_db_type
                cmpl $2, %eax
                je .Lorm10_my_dispatch
                movq $0, 32(%rsp)
                movq 8(%rsp), %rdi
                call kof_list_size
                movq %rax, 40(%rsp)
            .Lorm10_loop:
                movq 32(%rsp), %rax
                cmpq 40(%rsp), %rax
                jge .Lorm10_done
                movq 8(%rsp), %rdi
                movl 32(%rsp), %esi
                call kof_list_get
                movq %rax, %rsi
                movq 0(%rsp), %rdi
                movq 16(%rsp), %rdx
                movq 24(%rsp), %rcx
                call kof_orm_save
                incq 32(%rsp)
                jmp .Lorm10_loop
            .Lorm10_done:
                movq $1, %rax
                addq $56, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret

            # F2d4d: mysql -> restaura o frame e tail-chama .Lorm_saveall_my
            # (args originais nos slots; a asm mysql vive no
            # RuntimeOrmMysqlSaveAll, emitido junto no mesmo .s).
            .Lorm10_my_dispatch:
                movq 0(%rsp), %rdi
                movq 8(%rsp), %rsi
                movq 16(%rsp), %rdx
                movq 24(%rsp), %rcx
                addq $56, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                jmp .Lorm_saveall_my
        """);
    }
}
