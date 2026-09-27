package dev.kof.compiler.runtime;

/**
 * D-FULL-PARITY-050 row 3 (ssh.*) slice A — x86-64 runtime piece.
 *
 * <p>{@code kof_ssh_argv(host, command)} is the exact JVM oracle
 * ({@code JvmRuntimeCore.kof_ssh_argv}):
 * {@code ["ssh","-o","BatchMode=yes","-o","ConnectTimeout=5",host,command]} —
 * host and command stay ONE argv element each (the {@code sh -c} injection
 * class, same discipline as {@code kof.shell}).
 * {@code kof_ssh_run} reuses {@code kof_process_run} with {@code "ssh"} as the
 * program and the six remaining elements as args (the JVM lowers onto
 * {@code kof_shell_runwith → kof_process_run}), so connection failures are an
 * honest {@code Result} (never a silent success, R6).
 */
public final class RuntimeSsh {

    private RuntimeSsh() {
    }

    public static void emit(StringBuilder sb) {
        sb.append("""
            # ── kof.ssh (D-FULL-PARITY-050 row 3 slice A) — x86-64 ──────────
            .section .rodata
            .Lkof_ssh_lit_ssh:       .asciz "ssh"
            .Lkof_ssh_lit_dasho:     .asciz "-o"
            .Lkof_ssh_lit_batchmode: .asciz "BatchMode=yes"
            .Lkof_ssh_lit_timeout:   .asciz "ConnectTimeout=5"
            .section .text
            # .Lkof_ssh_push(rdi=list, rsi=ptr, edx=len): list.add(from_literal(ptr,len))
            .Lkof_ssh_push:
                pushq %rbx
                movq %rdi, %rbx
                movq %rsi, %rdi
                movl %edx, %esi
                call kof_string_from_literal
                movq %rbx, %rdi
                movq %rax, %rsi
                call kof_list_add
                popq %rbx
                ret

            # .Lkof_ssh_build(rdi=host, rsi=command, edx=include_ssh) -> List
            .Lkof_ssh_build:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %r12              # host
                movq %rsi, %r13              # command
                movl %edx, %r14d             # include_ssh
                call kof_list_new
                movq %rax, %rbx              # argv
                testl %r14d, %r14d
                jz .Lkof_ssh_no_ssh
                movq %rbx, %rdi
                leaq .Lkof_ssh_lit_ssh(%rip), %rsi
                movl $3, %edx
                call .Lkof_ssh_push
            .Lkof_ssh_no_ssh:
                movq %rbx, %rdi
                leaq .Lkof_ssh_lit_dasho(%rip), %rsi
                movl $2, %edx
                call .Lkof_ssh_push
                movq %rbx, %rdi
                leaq .Lkof_ssh_lit_batchmode(%rip), %rsi
                movl $13, %edx
                call .Lkof_ssh_push
                movq %rbx, %rdi
                leaq .Lkof_ssh_lit_dasho(%rip), %rsi
                movl $2, %edx
                call .Lkof_ssh_push
                movq %rbx, %rdi
                leaq .Lkof_ssh_lit_timeout(%rip), %rsi
                movl $16, %edx
                call .Lkof_ssh_push
                movq %rbx, %rdi
                movq %r12, %rsi
                call kof_list_add            # host
                movq %rbx, %rdi
                movq %r13, %rsi
                call kof_list_add            # command
                movq %rbx, %rax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_ssh_argv
            .type kof_ssh_argv, @function
            kof_ssh_argv:
                movl $1, %edx                # include "ssh"
                jmp .Lkof_ssh_build

            .globl kof_ssh_run
            .type kof_ssh_run, @function
            kof_ssh_run:
                # rdi = host, rsi = command -> Result (via kof_process_run)
                pushq %rbx
                pushq %r12
                movq %rdi, %r12              # host
                movq %rsi, %rbx              # command
                movq %r12, %rdi
                movq %rbx, %rsi
                xorl %edx, %edx              # no "ssh" prefix
                call .Lkof_ssh_build         # rax = 6-element args
                movq %rax, %rbx              # args
                leaq .Lkof_ssh_lit_ssh(%rip), %rdi
                movl $3, %esi
                call kof_string_from_literal # rax = "ssh"
                movq %rax, %rdi
                movq %rbx, %rsi
                call kof_process_run
                popq %r12
                popq %rbx
                ret
            """);
    }
}
