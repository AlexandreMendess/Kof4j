package dev.kof.compiler.runtime;
import dev.kof.compiler.NativeRuntime;

/**
 * Emissão do ASM de enum do runtime nativo.
 * Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
public final class RuntimeEnum {

    private RuntimeEnum() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            .globl kof_enum_value_of
            .type kof_enum_value_of, @function
            kof_enum_value_of:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx              # list
                movq %rsi, %r12              # name
                testq %rbx, %rbx
                jz .Lenum_fail
                testq %r12, %r12
                jz .Lenum_fail
                xorq %r13, %r13              # i = 0
            .Lenum_loop:
                cmpl 16(%rbx), %r13d
                jge .Lenum_fail
                movq 24(%rbx), %rdi          # data array
                movq (%rdi,%r13,8), %rsi     # item
                testq %rsi, %rsi
                jz .Lenum_next
                movq %r12, %rdi
                call kof_string_equals
                testl %eax, %eax
                jnz .Lenum_found
            .Lenum_next:
                incq %r13
                jmp .Lenum_loop
            .Lenum_found:
                movq 24(%rbx), %rax
                movq (%rax,%r13,8), %rax     # retorna o próprio item (internado)
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lenum_fail:
                xorl %eax, %eax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_enum_ordinal
            .type kof_enum_ordinal, @function
            kof_enum_ordinal:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %r12              # name
                movq %rsi, %rbx              # list
                testq %rbx, %rbx
                jz .Lenum_ord_fail
                testq %r12, %r12
                jz .Lenum_ord_fail
                xorq %r13, %r13              # i = 0
            .Lenum_ord_loop:
                cmpl 16(%rbx), %r13d
                jge .Lenum_ord_fail
                movq 24(%rbx), %rdi          # data array
                movq (%rdi,%r13,8), %rsi     # item
                testq %rsi, %rsi
                jz .Lenum_ord_next
                movq %r12, %rdi
                call kof_string_equals
                testl %eax, %eax
                jnz .Lenum_ord_found
            .Lenum_ord_next:
                incq %r13
                jmp .Lenum_ord_loop
            .Lenum_ord_found:
                movl %r13d, %eax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lenum_ord_fail:
                movl $-1, %eax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .section .text

            .section .text

            # ── kof.collections: Map<String,V> nativo (P1) ──────────────
            # Layout Map (64B): [0]=magic 100, [16]=count, [20]=cap,
            #   [24]=ptr keys (array KofString*), [32]=ptr vals (array ptr)

            # interno: kof_map_find(rdi=map, rsi=key) -> idx|-1
            # §123: tag da chave no header do map (off 40): 1=String
            # (kof_string_equals), 0=raw cmpq (Int/Long/Bool/Char/Double/
            # ponteiro). Antes era hardcoded string-equals e chave Int virava
            # PONTEIRO → SIGSEGV (mapOf(1,2).get(1) ec=139). O emitter escreve
            # a tag a partir do tipo do 1º arg (Unknown mantém o default 1).
            kof_map_find:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx             # map
                movq %rsi, %r12             # key
                movl 40(%rbx), %r13d        # tag
                xorq %r14, %r14             # i = 0
            .Lkmf_loop:
                cmpl 16(%rbx), %r14d
                jge .Lkmf_miss
                movq 24(%rbx), %rax         # array de chaves
                movq (%rax,%r14,8), %rdi    # candidato
                cmpl $1, %r13d
                je .Lkmf_str
                cmpl $2, %r13d
                je .Lkmf_obj
            .Lkmf_raw:
                cmpq %r12, %rdi             # chave 0 é legítima no modo raw
                je .Lkmf_hit
                jmp .Lkmf_next
            .Lkmf_str:
                testq %rdi, %rdi            # null-String não casa (skip)
                jz .Lkmf_next
                movq %r12, %rsi
                call kof_string_equals
                testl %eax, %eax
                jnz .Lkmf_hit
                jmp .Lkmf_next
            .Lkmf_obj:                      # §104b-ii: tag 2 = objeto Kof
                testq %rdi, %rdi            # null não casa (skip)
                jz .Lkmf_next
                movq %r12, %rsi
                call kof_obj_equals
                testl %eax, %eax
                jnz .Lkmf_hit
                jmp .Lkmf_next
            .Lkmf_next:
                incq %r14
                jmp .Lkmf_loop
            .Lkmf_hit:
                movq %r14, %rax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkmf_miss:
                movq $-1, %rax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            """);
    }
}