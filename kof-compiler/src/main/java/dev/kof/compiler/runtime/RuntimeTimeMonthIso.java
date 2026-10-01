package dev.kof.compiler.runtime;

/**
 * Emissão do ASM de kof.time addMonths em data ISO (STDLIB S7a-ext) do runtime
 * nativo x86. Domínio isolado de RuntimeTimeIso (regra <=500 linhas/classe) —
 * reusa .Lka_parse2/.Lka_put4/.Lka_put2 emitidos por RuntimeTimeIso e
 * .Lkd_valid + kof_time_daysInMonth emitidos por RuntimeTime no MESMO arquivo
 * .s (mesmo StringBuilder em NativeRuntime).
 */
public final class RuntimeTimeMonthIso {

    private RuntimeTimeMonthIso() {}

    public static void emitAddMonths(StringBuilder sb) {
        sb.append("""
            # ── kof_time (STDLIB S7a-ext) — ISO date + N meses ─────────────
            # kof_time_addMonths(rdi=iso, esi=months) -> String | "" (alloc)
            # MESMA aritmética inteira dos demais alvos: t = y*12 + (m-1) + n;
            # y1 = t/12; m1 = t%12 + 1; d1 = min(d, daysInMonth(y1,m1)). Pré-guarda
            # t em [12,119999] => divisão 64-bit sempre POSITIVA (não usa o
            # caminho .Lkd_epoch de dias). Reusa .Lka_parse2 + .Lkd_valid +
            # kof_time_daysInMonth + .Lka_put4/.Lka_put2 (a cauda de render é a
            # cópia exata de addDays). Inválida/out-of-range => "" (String len 0).
            .globl kof_time_addMonths
            .type kof_time_addMonths, @function
            kof_time_addMonths:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $48, %rsp
                xorl %r14d, %r14d               # present = 0
                movslq %esi, %rbx               # n (sign-extended)
                movq %rsp, %rsi                 # slots = [y,m,d]
                call .Lka_parse2                # edi=iso, rsi=slots -> eax=1 ok
                testl %eax, %eax
                jz .Lka_am_render
                movl 0(%rsp), %eax              # y
                imull $12, %eax, %eax           # y*12
                cltq
                movslq 4(%rsp), %rcx            # m (32-bit slot, sign-extended)
                addq %rcx, %rax                 # + m
                addq %rbx, %rax                 # + n
                decq %rax                       # t = y*12 + (m-1) + n
                cmpq $12, %rax
                jl .Lka_am_render
                cmpq $119999, %rax
                jg .Lka_am_render
                xorl %edx, %edx
                movq $12, %rcx
                divq %rcx                       # rax = y1, rdx = m1-1
                movl %eax, 0(%rsp)              # y1
                addl $1, %edx
                movl %edx, 4(%rsp)              # m1
                movl 0(%rsp), %edi              # y1
                movl 4(%rsp), %esi              # m1
                call kof_time_daysInMonth       # eax = dim(y1,m1)
                movl 8(%rsp), %edx              # d
                cmpl %eax, %edx                 # d - dim
                jge .Lka_am_keepdim             # d >= dim -> d1 = dim
                movl %edx, %eax                 # d < dim -> d1 = d
            .Lka_am_keepdim:
                movl %eax, 8(%rsp)              # d1
                movl $1, %r14d                  # present
                movl $10, %r12d                 # len
            .Lka_am_render:
                testl %r14d, %r14d
                jz .Lka_am_len0
                movl $10, %r12d
                jmp .Lka_am_alloc
            .Lka_am_len0:
                xorl %r12d, %r12d
            .Lka_am_alloc:
                leal 25(%r12), %edi
                call kof_alloc
                movq %rax, %r15
                movl $1, 0(%r15)
                movl $0, 4(%r15)
                movq $0, 8(%r15)
                movl %r12d, 16(%r15)
                movl $0, 20(%r15)
                movb $0, 24(%r15)
                testl %r14d, %r14d
                jz .Lka_am_done
                leaq 24(%r15), %rdi
                movl 0(%rsp), %r13d
                call .Lka_put4
                movb $45, (%rdi)
                incq %rdi
                movl 4(%rsp), %r13d
                call .Lka_put2
                movb $45, (%rdi)
                incq %rdi
                movl 8(%rsp), %r13d
                call .Lka_put2
                movb $0, (%rdi)
            .Lka_am_done:
                movq %r15, %rax
                addq $48, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }
}
