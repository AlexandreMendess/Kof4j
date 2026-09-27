package dev.kof.compiler.runtime;

/** B-1c: asm x86 libc-free do Schubfach — helpers flog/pow10, g1/g0, rop, kof_schub_to_decimal e kof_schub_format (parametrizado por H=17/9). */
final class RuntimeDtoaSchubfachCore {

    private RuntimeDtoaSchubfachCore() {}

    static void emit(StringBuilder sb) {
        sb.append(CORE);
    }

    private static final String CORE = """
.section .text

# ---- kof_schub_flog10pow2(edi) -> eax : floor(log10(2^e)) ----
.globl kof_schub_flog10pow2
kof_schub_flog10pow2:
    movslq %edi, %rax
    movabsq $0x9A209A84FB, %rcx
    imulq %rcx, %rax
    sarq $41, %rax
    ret

# ---- kof_schub_flog10threequarters(edi) -> eax ----
.globl kof_schub_flog10threequarters
kof_schub_flog10threequarters:
    movslq %edi, %rax
    movabsq $0x9A209A84FB, %rcx
    imulq %rcx, %rax
    movabsq $0x3FF7F85779, %rcx
    subq %rcx, %rax
    sarq $41, %rax
    ret

# ---- kof_schub_flog2pow10(edi) -> eax ----
.globl kof_schub_flog2pow10
kof_schub_flog2pow10:
    movslq %edi, %rax
    movabsq $0xD49A784BCD, %rcx
    imulq %rcx, %rax
    sarq $38, %rax
    ret

# ---- kof_schub_g1(edi=k) -> rax ----
.globl kof_schub_g1
kof_schub_g1:
    addl $324, %edi
    movslq %edi, %rcx
    shlq $1, %rcx
    leaq .Lschub_g(%rip), %rax
    movq (%rax,%rcx,8), %rax
    ret

# ---- kof_schub_g0(edi=k) -> rax ----
.globl kof_schub_g0
kof_schub_g0:
    addl $324, %edi
    movslq %edi, %rcx
    shlq $1, %rcx
    incq %rcx
    leaq .Lschub_g(%rip), %rax
    movq (%rax,%rcx,8), %rax
    ret

# ---- kof_schub_pow10(edi=e em 0..17) -> rax ----
.globl kof_schub_pow10
kof_schub_pow10:
    movslq %edi, %rdi
    leaq .Lschub_pow10(%rip), %rax
    movq (%rax,%rdi,8), %rax
    ret

# ---- kof_schub_rop(rdi=g1, rsi=g0, rdx=cp) -> rax ----
.globl kof_schub_rop
kof_schub_rop:
    movq %rdx, %rcx
    movq %rsi, %rax
    mulq %rcx
    movq %rdx, %r8
    movq %rdi, %rax
    mulq %rcx
    movq %rax, %r9
    movq %rdx, %r10
    shrq $1, %r9
    addq %r8, %r9
    movq %r9, %rax
    shrq $63, %rax
    addq %r10, %rax
    movq %r9, %rdx
    movabsq $0x7FFFFFFFFFFFFFFF, %rcx
    andq %rcx, %rdx
    addq %rcx, %rdx
    shrq $63, %rdx
    orq %rdx, %rax
    ret

# ---- kof_schub_to_decimal(edi=q, rsi=c, edx=dk) -> rax=f, ecx=e ----
.globl kof_schub_to_decimal
kof_schub_to_decimal:
    pushq %rbp
    movq %rsp, %rbp
    pushq %rbx
    pushq %r12
    pushq %r13
    pushq %r14
    pushq %r15
    subq $168, %rsp
    movl %edi, %ebx
    movq %rsi, %r12
    movl %edx, %r13d
    movl %r12d, %r14d
    andl $1, %r14d
    movabsq $0x10000000000000, %rax
    cmpq %rax, %r12
    jne .Ltd_reg
    cmpl $-1074, %ebx
    je .Ltd_reg
    movq %r12, %rax
    shlq $2, %rax
    movq %rax, -48(%rbp)
    leaq -1(%rax), %rax
    movq %rax, -64(%rbp)
    movl %ebx, %edi
    call kof_schub_flog10threequarters
    jmp .Ltd_kdone
.Ltd_reg:
    movq %r12, %rax
    shlq $2, %rax
    movq %rax, -48(%rbp)
    leaq -2(%rax), %rax
    movq %rax, -64(%rbp)
    movl %ebx, %edi
    call kof_schub_flog10pow2
.Ltd_kdone:
    movl %eax, -120(%rbp)
    movq -48(%rbp), %rax
    addq $2, %rax
    movq %rax, -56(%rbp)
    movl -120(%rbp), %edi
    negl %edi
    call kof_schub_flog2pow10
    addl %ebx, %eax
    addl $2, %eax
    movl %eax, -72(%rbp)
    movl -120(%rbp), %edi
    call kof_schub_g1
    movq %rax, -80(%rbp)
    movl -120(%rbp), %edi
    call kof_schub_g0
    movq %rax, -88(%rbp)
    movq -80(%rbp), %rdi
    movq -88(%rbp), %rsi
    movq -48(%rbp), %rdx
    movl -72(%rbp), %ecx
    shlq %cl, %rdx
    call kof_schub_rop
    movq %rax, -96(%rbp)
    movq -80(%rbp), %rdi
    movq -88(%rbp), %rsi
    movq -64(%rbp), %rdx
    movl -72(%rbp), %ecx
    shlq %cl, %rdx
    call kof_schub_rop
    movq %rax, -104(%rbp)
    movq -80(%rbp), %rdi
    movq -88(%rbp), %rsi
    movq -56(%rbp), %rdx
    movl -72(%rbp), %ecx
    shlq %cl, %rdx
    call kof_schub_rop
    movq %rax, -112(%rbp)
    movq -96(%rbp), %rax
    shrq $2, %rax
    movq %rax, -128(%rbp)
    cmpq $100, %rax
    jl .Ltd_cmp
    movq -128(%rbp), %rax
    movabsq $0x19999999999999A0, %rcx
    mulq %rcx
    imulq $10, %rdx, %rdx
    movq %rdx, -136(%rbp)
    leaq 10(%rdx), %rax
    movq %rax, -144(%rbp)
    movq -104(%rbp), %rax
    addq %r14, %rax
    movq -136(%rbp), %rdx
    shlq $2, %rdx
    cmpq %rdx, %rax
    setbe %r8b
    movq -144(%rbp), %rax
    shlq $2, %rax
    addq %r14, %rax
    movq -112(%rbp), %rdx
    cmpq %rdx, %rax
    setbe %r9b
    cmpb %r9b, %r8b
    je .Ltd_cmp
    testb %r8b, %r8b
    jz .Ltd_use_tp
    movq -136(%rbp), %rdi
    jmp .Ltd_retd_k
.Ltd_use_tp:
    movq -144(%rbp), %rdi
.Ltd_retd_k:
    movl -120(%rbp), %esi
    jmp .Ltd_ret
.Ltd_cmp:
    movq -128(%rbp), %rax
    leaq 1(%rax), %rax
    movq %rax, -144(%rbp)
    movq -104(%rbp), %rcx
    addq %r14, %rcx
    movq -128(%rbp), %rdx
    shlq $2, %rdx
    cmpq %rdx, %rcx
    setbe %r8b
    movq -144(%rbp), %rcx
    shlq $2, %rcx
    addq %r14, %rcx
    movq -112(%rbp), %rdx
    cmpq %rdx, %rcx
    setbe %r9b
    cmpb %r9b, %r8b
    je .Ltd_final
    testb %r8b, %r8b
    jz .Ltd_use_t
    movq -128(%rbp), %rdi
    jmp .Ltd_retd_kdk
.Ltd_use_t:
    movq -144(%rbp), %rdi
.Ltd_retd_kdk:
    movl -120(%rbp), %esi
    addl %r13d, %esi
    jmp .Ltd_ret
.Ltd_final:
    movq -128(%rbp), %rax
    movq -144(%rbp), %rcx
    addq %rcx, %rax
    shlq $1, %rax
    movq -96(%rbp), %rdx
    subq %rax, %rdx
    js .Ltd_pick_s
    jnz .Ltd_pick_t
    testb $1, -128(%rbp)
    jz .Ltd_pick_s
.Ltd_pick_t:
    movq -144(%rbp), %rdi
    jmp .Ltd_retd_kdk2
.Ltd_pick_s:
    movq -128(%rbp), %rdi
.Ltd_retd_kdk2:
    movl -120(%rbp), %esi
    addl %r13d, %esi
.Ltd_ret:
    movq %rdi, %rax
    movl %esi, %ecx
    leaq -40(%rbp), %rsp
    popq %r15
    popq %r14
    popq %r13
    popq %r12
    popq %rbx
    popq %rbp
    ret

# ---- kof_schub_format(rdi=f>0, esi=e, edx=sign, ecx=H) -> rax: String* ----
.globl kof_schub_format
kof_schub_format:
    pushq %rbp
    movq %rsp, %rbp
    pushq %rbx
    pushq %r12
    pushq %r13
    pushq %r14
    pushq %r15
    subq $104, %rsp
    movq %rdi, %rbx
    movl %esi, %r12d
    movl %edx, %r13d
    movl %ecx, -132(%rbp)
    bsrq %rbx, %rax
    movl $63, %edi
    subl %eax, %edi
    movl $64, %eax
    subl %edi, %eax
    movl %eax, %edi
    call kof_schub_flog10pow2
    movl %eax, %esi
    movl %esi, %edi
    call kof_schub_pow10
    cmpq %rax, %rbx
    jb .Lschubf_len_ok
    incl %esi
.Lschubf_len_ok:
    movl -132(%rbp), %edi
    subl %esi, %edi
    call kof_schub_pow10
    imulq %rax, %rbx
    addl %esi, %r12d
    leaq -48(%rbp), %r10
    movq %rbx, %rax
    movl -132(%rbp), %ecx
    movl $10, %r8d
.Lschubf_dig:
    xorl %edx, %edx
    divq %r8
    addb $48, %dl
    decq %r10
    movb %dl, (%r10)
    decl %ecx
    jnz .Lschubf_dig
    leaq -112(%rbp), %r14
    xorl %r15d, %r15d
    testl %r13d, %r13d
    jz .Lschubf_nosign
    movb $45, (%r14)
    incq %r15
.Lschubf_nosign:
    cmpl $0, %r12d
    jle .Lschubf_le0
    cmpl $7, %r12d
    jg .Lschubf_sci
    movl %r12d, %ecx
.Lschubf_p1:
    movzbl (%r10), %eax
    movb %al, (%r14,%r15)
    incq %r10
    incq %r15
    decl %ecx
    jnz .Lschubf_p1
    movb $46, (%r14,%r15)
    incq %r15
    movl -132(%rbp), %ecx
    subl %r12d, %ecx
.Lschubf_p2:
    movzbl (%r10), %eax
    movb %al, (%r14,%r15)
    incq %r10
    incq %r15
    decl %ecx
    jnz .Lschubf_p2
    jmp .Lschubf_strip
.Lschubf_le0:
    cmpl $-3, %r12d
    jle .Lschubf_sci
    movb $48, (%r14,%r15)
    incq %r15
    movb $46, (%r14,%r15)
    incq %r15
    movl %r12d, %ecx
    negl %ecx
    testl %ecx, %ecx
    jz .Lschubf_zskip
.Lschubf_z0:
    movb $48, (%r14,%r15)
    incq %r15
    decl %ecx
    jnz .Lschubf_z0
.Lschubf_zskip:
    movl -132(%rbp), %ecx
.Lschubf_z1:
    movzbl (%r10), %eax
    movb %al, (%r14,%r15)
    incq %r10
    incq %r15
    decl %ecx
    jnz .Lschubf_z1
    jmp .Lschubf_strip
.Lschubf_sci:
    movzbl (%r10), %eax
    movb %al, (%r14,%r15)
    incq %r10
    incq %r15
    movb $46, (%r14,%r15)
    incq %r15
    movl -132(%rbp), %ecx
    decl %ecx
.Lschubf_s1:
    movzbl (%r10), %eax
    movb %al, (%r14,%r15)
    incq %r10
    incq %r15
    decl %ecx
    jnz .Lschubf_s1
.Lschubf_strip:
    testq %r15, %r15
    jz .Lschubf_strip_done
    movzbl -1(%r14,%r15), %eax
    cmpb $48, %al
    jne .Lschubf_strip_done
    cmpq $1, %r15
    jle .Lschubf_strip_dec
    movzbl -2(%r14,%r15), %eax
    cmpb $46, %al
    je .Lschubf_strip_done
.Lschubf_strip_dec:
    decq %r15
    jmp .Lschubf_strip
.Lschubf_strip_done:
    cmpl $7, %r12d
    jg .Lschubf_exp
    cmpl $0, %r12d
    jg .Lschubf_emit
    cmpl $-3, %r12d
    jg .Lschubf_emit
.Lschubf_exp:
    movb $69, (%r14,%r15)
    incq %r15
    movl %r12d, %eax
    decl %eax
    testl %eax, %eax
    jns .Lschubf_exp_p
    movb $45, (%r14,%r15)
    incq %r15
    negl %eax
.Lschubf_exp_p:
    leaq -128(%rbp), %rdi
    xorl %r9d, %r9d
    movl $10, %esi
    testl %eax, %eax
    jnz .Lschubf_ediv
    movb $48, (%rdi)
    movl $1, %r9d
    jmp .Lschubf_erev
.Lschubf_ediv:
    xorl %edx, %edx
    divl %esi
    addb $48, %dl
    movb %dl, (%rdi,%r9)
    incq %r9
    testl %eax, %eax
    jnz .Lschubf_ediv
.Lschubf_erev:
    decq %r9
.Lschubf_erev1:
    movzbl (%rdi,%r9), %eax
    movb %al, (%r14,%r15)
    incq %r15
    decq %r9
    jns .Lschubf_erev1
.Lschubf_emit:
    movq %r14, %rdi
    movl %r15d, %esi
    call kof_string_from_literal
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
