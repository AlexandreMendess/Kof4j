package dev.kof.compiler.runtime;

/** B-1c: kof_float_to_string x86 libc-free — FloatToDecimal (Schubfach, P=24, H=9) reusando a tabela g/helpers do Double. */
final class RuntimeDtoaSchubfachFloat {

    private RuntimeDtoaSchubfachFloat() {}

    static void emit(StringBuilder sb) {
        sb.append(CORE);
    }

    private static final String CORE = """
.section .text

# ---- kof_schub_rop_f(rdi=g, rsi=cp) -> eax : rop(cp g 2^-95) ----
.globl kof_schub_rop_f
kof_schub_rop_f:
    movq %rdi, %rax
    mulq %rsi
    movq %rdx, %rcx
    shrq $31, %rcx
    movq %rdx, %r8
    movabsq $0xFFFFFFFF, %r9
    andq %r9, %r8
    addq %r9, %r8
    shrq $32, %r8
    orq %rcx, %r8
    movl %r8d, %eax
    ret

# ---- kof_schub_to_decimal_f(edi=q, esi=c, edx=dk) -> eax=f, ecx=e ----
.globl kof_schub_to_decimal_f
kof_schub_to_decimal_f:
    pushq %rbp
    movq %rsp, %rbp
    pushq %rbx
    pushq %r12
    pushq %r13
    pushq %r14
    pushq %r15
    subq $168, %rsp
    movl %edi, %ebx
    movl %esi, %r12d
    movl %edx, %r13d
    movl %r12d, %r14d
    andl $1, %r14d
    cmpl $0x800000, %r12d
    jne .Ltdf_reg
    cmpl $-149, %ebx
    je .Ltdf_reg
    movl %r12d, %eax
    shll $2, %eax
    movq %rax, -48(%rbp)
    decq %rax
    movq %rax, -64(%rbp)
    movl %ebx, %edi
    call kof_schub_flog10threequarters
    jmp .Ltdf_kdone
.Ltdf_reg:
    movl %r12d, %eax
    shll $2, %eax
    movq %rax, -48(%rbp)
    subq $2, %rax
    movq %rax, -64(%rbp)
    movl %ebx, %edi
    call kof_schub_flog10pow2
.Ltdf_kdone:
    movl %eax, -120(%rbp)
    movq -48(%rbp), %rax
    addq $2, %rax
    movq %rax, -56(%rbp)
    movl -120(%rbp), %edi
    negl %edi
    call kof_schub_flog2pow10
    addl %ebx, %eax
    addl $33, %eax
    movl %eax, -72(%rbp)
    movl -120(%rbp), %edi
    call kof_schub_g1
    incq %rax
    movq %rax, -80(%rbp)
    movq -80(%rbp), %rdi
    movq -48(%rbp), %rsi
    movl -72(%rbp), %ecx
    shlq %cl, %rsi
    call kof_schub_rop_f
    movl %eax, -96(%rbp)
    movq -80(%rbp), %rdi
    movq -64(%rbp), %rsi
    movl -72(%rbp), %ecx
    shlq %cl, %rsi
    call kof_schub_rop_f
    movl %eax, -104(%rbp)
    movq -80(%rbp), %rdi
    movq -56(%rbp), %rsi
    movl -72(%rbp), %ecx
    shlq %cl, %rsi
    call kof_schub_rop_f
    movl %eax, -112(%rbp)
    movl -96(%rbp), %eax
    shrl $2, %eax
    movl %eax, -128(%rbp)
    cmpl $100, %eax
    jl .Ltdf_cmp
    movl -128(%rbp), %eax
    movslq %eax, %rax
    movabsq $1717986919, %rcx
    imulq %rcx, %rax
    shrq $34, %rax
    imull $10, %eax, %eax
    movl %eax, -136(%rbp)
    addl $10, %eax
    movl %eax, -144(%rbp)
    movl -104(%rbp), %eax
    addl %r14d, %eax
    movl -136(%rbp), %edx
    shll $2, %edx
    cmpl %edx, %eax
    setbe %r8b
    movl -144(%rbp), %eax
    shll $2, %eax
    addl %r14d, %eax
    movl -112(%rbp), %edx
    cmpl %edx, %eax
    setbe %r9b
    cmpb %r9b, %r8b
    je .Ltdf_cmp
    testb %r8b, %r8b
    jz .Ltdf_use_tp
    movl -136(%rbp), %edi
    jmp .Ltdf_retd_k
.Ltdf_use_tp:
    movl -144(%rbp), %edi
.Ltdf_retd_k:
    movl -120(%rbp), %esi
    jmp .Ltdf_ret
.Ltdf_cmp:
    movl -128(%rbp), %eax
    leal 1(%rax), %ecx
    movl %ecx, -144(%rbp)
    movl -104(%rbp), %eax
    addl %r14d, %eax
    movl -128(%rbp), %edx
    shll $2, %edx
    cmpl %edx, %eax
    setbe %r8b
    movl -144(%rbp), %eax
    shll $2, %eax
    addl %r14d, %eax
    movl -112(%rbp), %edx
    cmpl %edx, %eax
    setbe %r9b
    cmpb %r9b, %r8b
    je .Ltdf_final
    testb %r8b, %r8b
    jz .Ltdf_use_t
    movl -128(%rbp), %edi
    jmp .Ltdf_retd_kdk
.Ltdf_use_t:
    movl -144(%rbp), %edi
.Ltdf_retd_kdk:
    movl -120(%rbp), %esi
    addl %r13d, %esi
    jmp .Ltdf_ret
.Ltdf_final:
    movl -128(%rbp), %eax
    movl -144(%rbp), %ecx
    addl %ecx, %eax
    shll $1, %eax
    movl -96(%rbp), %edx
    subl %eax, %edx
    js .Ltdf_pick_s
    jnz .Ltdf_pick_t
    testb $1, -128(%rbp)
    jz .Ltdf_pick_s
.Ltdf_pick_t:
    movl -144(%rbp), %edi
    jmp .Ltdf_retd_kdk2
.Ltdf_pick_s:
    movl -128(%rbp), %edi
.Ltdf_retd_kdk2:
    movl -120(%rbp), %esi
    addl %r13d, %esi
.Ltdf_ret:
    movl %edi, %eax
    movl %esi, %ecx
    leaq -40(%rbp), %rsp
    popq %r15
    popq %r14
    popq %r13
    popq %r12
    popq %rbx
    popq %rbp
    ret

# ---- kof_float_to_string(xmm0: float) -> rax: String* ----
.globl kof_float_to_string
.type kof_float_to_string, @function
kof_float_to_string:
    pushq %rbp
    movq %rsp, %rbp
    pushq %rbx
    pushq %r12
    pushq %r13
    pushq %r14
    pushq %r15
    subq $56, %rsp
    movd %xmm0, %eax
    movl %eax, %ebx
    movl %ebx, %r13d
    shrl $31, %r13d
    movl %ebx, %ecx
    shrl $23, %ecx
    andl $0xff, %ecx
    cmpl $0xff, %ecx
    je .Lf2s_naninf
    testl %ecx, %ecx
    jz .Lf2s_sub
    movl $150, %r12d
    subl %ecx, %r12d
    movl %ebx, %r14d
    andl $0x7fffff, %r14d
    orl $0x800000, %r14d
    testl %r12d, %r12d
    jle .Lf2s_dec
    cmpl $24, %r12d
    jge .Lf2s_dec
    movl %r12d, %ecx
    movl %r14d, %eax
    shrl %cl, %eax
    movl %eax, %edx
    shll %cl, %edx
    cmpl %r14d, %edx
    jne .Lf2s_dec
    movl %eax, %edi
    xorl %esi, %esi
    movl %r13d, %edx
    movl $9, %ecx
    call kof_schub_format
    jmp .Lf2s_done
.Lf2s_dec:
    movl %r12d, %edi
    negl %edi
    movl %r14d, %esi
    xorl %edx, %edx
    call kof_schub_to_decimal_f
    movl %eax, %edi
    movl %ecx, %esi
    movl %r13d, %edx
    movl $9, %ecx
    call kof_schub_format
    jmp .Lf2s_done
.Lf2s_sub:
    movl %ebx, %eax
    andl $0x7fffff, %eax
    testl %eax, %eax
    jz .Lf2s_zero
    cmpl $8, %eax
    jae .Lf2s_sub_c
    imull $10, %eax, %esi
    movl $-149, %edi
    movl $-1, %edx
    call kof_schub_to_decimal_f
    jmp .Lf2s_after
.Lf2s_sub_c:
    movl %eax, %esi
    movl $-149, %edi
    xorl %edx, %edx
    call kof_schub_to_decimal_f
.Lf2s_after:
    movl %eax, %edi
    movl %ecx, %esi
    movl %r13d, %edx
    movl $9, %ecx
    call kof_schub_format
    jmp .Lf2s_done
.Lf2s_zero:
    testl %r13d, %r13d
    jz .Lf2s_zero_p
    leaq .Lschub_nzero(%rip), %rdi
    movl $4, %esi
    call kof_string_from_literal
    jmp .Lf2s_done
.Lf2s_zero_p:
    leaq .Lschub_zero(%rip), %rdi
    movl $3, %esi
    call kof_string_from_literal
    jmp .Lf2s_done
.Lf2s_naninf:
    movl %ebx, %eax
    shll $9, %eax
    jnz .Lf2s_nan
    testl %ebx, %ebx
    js .Lf2s_ninf
    leaq .Lschub_inf(%rip), %rdi
    movl $8, %esi
    call kof_string_from_literal
    jmp .Lf2s_done
.Lf2s_ninf:
    leaq .Lschub_ninf(%rip), %rdi
    movl $9, %esi
    call kof_string_from_literal
    jmp .Lf2s_done
.Lf2s_nan:
    leaq .Lschub_nan(%rip), %rdi
    movl $3, %esi
    call kof_string_from_literal
.Lf2s_done:
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
