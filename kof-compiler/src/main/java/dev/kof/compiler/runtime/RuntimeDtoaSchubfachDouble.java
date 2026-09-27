package dev.kof.compiler.runtime;

/** B-1c: kof_double_to_string x86 libc-free (decode + dispatch + format H=17). */
final class RuntimeDtoaSchubfachDouble {

    private RuntimeDtoaSchubfachDouble() {}

    static void emit(StringBuilder sb) {
        sb.append(CORE);
    }

    private static final String CORE = """
# ---- kof_double_to_string(xmm0: double) -> rax: String* ----
.globl kof_double_to_string
.type kof_double_to_string, @function
kof_double_to_string:
    pushq %rbp
    movq %rsp, %rbp
    pushq %rbx
    pushq %r12
    pushq %r13
    pushq %r14
    pushq %r15
    subq $56, %rsp
    movq %xmm0, %rax
    movq %rax, %rbx
    movq %rbx, %r13
    shrq $63, %r13
    movq %rbx, %rcx
    shrq $52, %rcx
    andl $0x7ff, %ecx
    cmpl $0x7ff, %ecx
    je .Ld2s_naninf
    testl %ecx, %ecx
    jz .Ld2s_sub
    movl $1075, %r12d
    subl %ecx, %r12d
    movq %rbx, %r14
    movabsq $0xFFFFFFFFFFFFF, %rax
    andq %rax, %r14
    movabsq $0x10000000000000, %rax
    orq %rax, %r14
    testl %r12d, %r12d
    jle .Ld2s_dec
    cmpl $53, %r12d
    jge .Ld2s_dec
    movl %r12d, %ecx
    movq %r14, %rax
    shrq %cl, %rax
    movq %rax, %rdx
    shlq %cl, %rdx
    cmpq %r14, %rdx
    jne .Ld2s_dec
    movq %rax, %rdi
    xorl %esi, %esi
    movl %r13d, %edx
    movl $17, %ecx
    call kof_schub_format
    jmp .Ld2s_done
.Ld2s_dec:
    movl %r12d, %edi
    negl %edi
    movq %r14, %rsi
    xorl %edx, %edx
    call kof_schub_to_decimal
    movq %rax, %rdi
    movl %ecx, %esi
    movl %r13d, %edx
    movl $17, %ecx
    call kof_schub_format
    jmp .Ld2s_done
.Ld2s_sub:
    movq %rbx, %rax
    movabsq $0xFFFFFFFFFFFFF, %rcx
    andq %rcx, %rax
    testq %rax, %rax
    jz .Ld2s_zero
    cmpq $3, %rax
    jae .Ld2s_sub_c
    imulq $10, %rax, %rsi
    movl $-1074, %edi
    movl $-1, %edx
    call kof_schub_to_decimal
    jmp .Ld2s_after
.Ld2s_sub_c:
    movq %rax, %rsi
    movl $-1074, %edi
    xorl %edx, %edx
    call kof_schub_to_decimal
.Ld2s_after:
    movq %rax, %rdi
    movl %ecx, %esi
    movl %r13d, %edx
    movl $17, %ecx
    call kof_schub_format
    jmp .Ld2s_done
.Ld2s_zero:
    testl %r13d, %r13d
    jz .Ld2s_zero_p
    leaq .Lschub_nzero(%rip), %rdi
    movl $4, %esi
    call kof_string_from_literal
    jmp .Ld2s_done
.Ld2s_zero_p:
    leaq .Lschub_zero(%rip), %rdi
    movl $3, %esi
    call kof_string_from_literal
    jmp .Ld2s_done
.Ld2s_naninf:
    movq %rbx, %rax
    shlq $12, %rax
    jnz .Ld2s_nan
    testq %rbx, %rbx
    js .Ld2s_ninf
    leaq .Lschub_inf(%rip), %rdi
    movl $8, %esi
    call kof_string_from_literal
    jmp .Ld2s_done
.Ld2s_ninf:
    leaq .Lschub_ninf(%rip), %rdi
    movl $9, %esi
    call kof_string_from_literal
    jmp .Ld2s_done
.Ld2s_nan:
    leaq .Lschub_nan(%rip), %rdi
    movl $3, %esi
    call kof_string_from_literal
.Ld2s_done:
    leaq -40(%rbp), %rsp
    popq %r15
    popq %r14
    popq %r13
    popq %r12
    popq %rbx
    popq %rbp
    ret
""";
}
