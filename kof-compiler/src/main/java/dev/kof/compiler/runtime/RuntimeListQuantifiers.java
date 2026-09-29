package dev.kof.compiler.runtime;

/**
 * D-MULTIPARADIGMA-PHASE1A (slice 1a) — eager short-circuit quantifiers
 * {@code any}/{@code all}/{@code none} on x86_64. Loop shape mirrors
 * {@code kof_list_filter} (element reload per iteration, lambda invoke via
 * the vtable slot); truth test is the same {@code testq} (nonzero = true);
 * Bool result is raw 0/1 in {@code eax} (like {@code kof_list_is_empty}).
 * Frame keeps the 5-push shape of the map/filter loops (alignment).
 */
public final class RuntimeListQuantifiers {

    private RuntimeListQuantifiers() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            .text
            # kof_list_any(list, fn) -> eax 1/0
            .globl kof_list_any
            .type kof_list_any, @function
            kof_list_any:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %r12
                movq %rsi, %r13
                xorl %r15d, %r15d
            .Lkof_list_any_loop:
                movl 16(%r12), %eax
                cmpl %eax, %r15d
                jge .Lkof_list_any_false
                movq 24(%r12), %rax
                movslq %r15d, %rcx
                movq (%rax,%rcx,8), %rsi
                movq %r13, %rdi
                movq 8(%rdi), %rax
                movq (%rax), %rax
                call *%rax
                testq %rax, %rax
                jnz .Lkof_list_any_true
                incl %r15d
                jmp .Lkof_list_any_loop
            .Lkof_list_any_true:
                movl $1, %eax
                jmp .Lkof_list_any_done
            .Lkof_list_any_false:
                xorl %eax, %eax
            .Lkof_list_any_done:
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_list_all(list, fn) -> eax 1/0
            .globl kof_list_all
            .type kof_list_all, @function
            kof_list_all:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %r12
                movq %rsi, %r13
                xorl %r15d, %r15d
            .Lkof_list_all_loop:
                movl 16(%r12), %eax
                cmpl %eax, %r15d
                jge .Lkof_list_all_true
                movq 24(%r12), %rax
                movslq %r15d, %rcx
                movq (%rax,%rcx,8), %rsi
                movq %r13, %rdi
                movq 8(%rdi), %rax
                movq (%rax), %rax
                call *%rax
                testq %rax, %rax
                jz .Lkof_list_all_false
                incl %r15d
                jmp .Lkof_list_all_loop
            .Lkof_list_all_false:
                xorl %eax, %eax
                jmp .Lkof_list_all_done
            .Lkof_list_all_true:
                movl $1, %eax
            .Lkof_list_all_done:
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_list_none(list, fn) -> eax 1/0
            .globl kof_list_none
            .type kof_list_none, @function
            kof_list_none:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %r12
                movq %rsi, %r13
                xorl %r15d, %r15d
            .Lkof_list_none_loop:
                movl 16(%r12), %eax
                cmpl %eax, %r15d
                jge .Lkof_list_none_true
                movq 24(%r12), %rax
                movslq %r15d, %rcx
                movq (%rax,%rcx,8), %rsi
                movq %r13, %rdi
                movq 8(%rdi), %rax
                movq (%rax), %rax
                call *%rax
                testq %rax, %rax
                jnz .Lkof_list_none_false
                incl %r15d
                jmp .Lkof_list_none_loop
            .Lkof_list_none_false:
                xorl %eax, %eax
                jmp .Lkof_list_none_done
            .Lkof_list_none_true:
                movl $1, %eax
            .Lkof_list_none_done:
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }
}
