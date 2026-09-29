package dev.kof.compiler.runtime;

/**
 * D-MULTIPARADIGMA-PHASE1A (slices 1a+1b) — eager short-circuit search ops
 * {@code any}/{@code all}/{@code none} + {@code find}/{@code count(pred)} on
 * x86_64. Loop shape mirrors {@code kof_list_filter} (element reload per
 * iteration, lambda invoke via the vtable slot); truth test is the same
 * {@code testq} (nonzero = true); Bool/Int results are raw in {@code eax}
 * (like {@code kof_list_is_empty}); missing {@code find} returns 0 (the
 * Map.get-missing contract). Frame keeps the 5-push shape of the map/filter
 * loops (alignment).
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

            # kof_list_find(list, fn, tag) -> rax element-boxed | 0.
            # Native list slots hold RAW primitives but T? consumers expect
            # boxed values (Map slots are boxed); the tag (NativeBoxTags
            # numbering) selects the box on the hit path, miss stays 0.
            .globl kof_list_find
            .type kof_list_find, @function
            kof_list_find:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %r12
                movq %rsi, %r13
                movl %edx, %r14d
                xorl %r15d, %r15d
            .Lkof_list_find_loop:
                movl 16(%r12), %eax
                cmpl %eax, %r15d
                jge .Lkof_list_find_miss
                movq 24(%r12), %rax
                movslq %r15d, %rcx
                movq (%rax,%rcx,8), %rsi
                movq %r13, %rdi
                movq 8(%rdi), %rax
                movq (%rax), %rax
                call *%rax
                testq %rax, %rax
                jnz .Lkof_list_find_hit
                incl %r15d
                jmp .Lkof_list_find_loop
            .Lkof_list_find_hit:
                cmpl $1, %r14d
                je .Lkof_list_find_pass
                cmpl $6, %r14d
                je .Lkof_list_find_pass
                movq 24(%r12), %rax
                movslq %r15d, %rcx
                movq (%rax,%rcx,8), %rdi
                cmpl $2, %r14d
                je .Lkof_list_find_long
                cmpl $3, %r14d
                je .Lkof_list_find_bool
                cmpl $4, %r14d
                je .Lkof_list_find_double
                cmpl $5, %r14d
                je .Lkof_list_find_float
                call kof_box_int
                jmp .Lkof_list_find_done
            .Lkof_list_find_long:
                call kof_box_long
                jmp .Lkof_list_find_done
            .Lkof_list_find_bool:
                call kof_box_bool
                jmp .Lkof_list_find_done
            .Lkof_list_find_double:
                call kof_box_double
                jmp .Lkof_list_find_done
            .Lkof_list_find_float:
                call kof_box_float
                jmp .Lkof_list_find_done
            .Lkof_list_find_pass:
                movq 24(%r12), %rax
                movslq %r15d, %rcx
                movq (%rax,%rcx,8), %rax
                jmp .Lkof_list_find_done
            .Lkof_list_find_miss:
                xorl %eax, %eax
            .Lkof_list_find_done:
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_list_count_pred(list, fn) -> eax count
            .globl kof_list_count_pred
            .type kof_list_count_pred, @function
            kof_list_count_pred:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %r12
                movq %rsi, %r13
                xorl %r14d, %r14d
                xorl %r15d, %r15d
            .Lkof_list_count_pred_loop:
                movl 16(%r12), %eax
                cmpl %eax, %r15d
                jge .Lkof_list_count_pred_done
                movq 24(%r12), %rax
                movslq %r15d, %rcx
                movq (%rax,%rcx,8), %rsi
                movq %r13, %rdi
                movq 8(%rdi), %rax
                movq (%rax), %rax
                call *%rax
                testq %rax, %rax
                jz .Lkof_list_count_pred_next
                incl %r14d
            .Lkof_list_count_pred_next:
                incl %r15d
                jmp .Lkof_list_count_pred_loop
            .Lkof_list_count_pred_done:
                movl %r14d, %eax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }
}
