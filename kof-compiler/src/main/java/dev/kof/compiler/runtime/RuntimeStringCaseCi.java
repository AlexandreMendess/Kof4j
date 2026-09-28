package dev.kof.compiler.runtime;

/**
 * D-STR-UNICODE (27/09, row 11 fatia-5) — Native {@code String.compareToIgnoreCase}:
 * o algoritmo EXATO do JDK, fold DUPLO por code unit UTF-16
 * ({@code Character.toUpperCase} primeiro; desempate {@code toLowerCase}; senao a
 * diferenca crua das units dobradas), sobre o STREAM de code units decodificado do
 * store UTF-8 (code point astral = par surrogate, como no {@code String} do JVM —
 * surrogados NAO dobram, identidade pela tabela). Terminadores: unidades esgotadas
 * => {@code lenA - lenB} em UNIDADES. Nulls: ambos => 0; {@code a} => -1;
 * {@code b} => +1 (ordem do JDK).
 *
 * <p>Retorna o INTEIRO exato do JVM (a diferenca, nao so o sinal) — a battery
 * {@code StringUnicodeFacesMeasuredTest} trava {@code compareToIgnoreCase} =
 * oraculo medido (108/0/0/390/0/7554/-1/0/0/64155) nos quatro targets.
 *
 * <p>Tabelas: as MESMAS tuplas (from,to) por code point BMP geradas de
 * {@code Character} do JDK ({@link RuntimeStringCase#data}), com labels proprios
 * para o slice ser autonomo sob pruning (padrao {@code NativeRiscvAsmCase}).
 * ASCII fica inline (a tabela comeca em 0x80), igual ao fold de caixa.
 */
public final class RuntimeStringCaseCi {

    private RuntimeStringCaseCi() {}

    public static void emit(StringBuilder sb) {
        sb.append(TEXT
                .replace("@UPN@", Integer.toString(RuntimeStringCase.count(true)))
                .replace("@LON@", Integer.toString(RuntimeStringCase.count(false))))
                .append(RuntimeStringCase.data(true, ".Lkof_ci_up_tab"))
                .append(RuntimeStringCase.data(false, ".Lkof_ci_lo_tab"));
    }

    private static final String TEXT = """
            # kof_ci_units(str@rdi) -> rax = buffer de code units UTF-16
            # (header: unidades @16; dados @24, 2 bytes por unit; astral => par).
            .globl kof_ci_units
            .type kof_ci_units, @function
            kof_ci_units:
                testq %rdi, %rdi
                jz .Lkof_ci_u_null
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                movl 16(%rbx), %r12d
                leal 24(,%r12,2), %edi
                call kof_alloc
                movq %rax, %r13
                movl $1, (%r13)
                movl $0, 4(%r13)
                movq $0, 8(%r13)
                movl $0, 16(%r13)
                movl $0, 20(%r13)
                xorl %r14d, %r14d
                xorl %r15d, %r15d
            .Lkof_ci_u_loop:
                cmpl %r12d, %r14d
                jge .Lkof_ci_u_done
                movzbl 24(%rbx,%r14), %eax
                cmpl $0x80, %eax
                jb .Lkof_ci_u_ascii
                cmpl $0xE0, %eax
                jb .Lkof_ci_u_two
                cmpl $0xF0, %eax
                jb .Lkof_ci_u_three
                movzbl 24(%rbx,%r14), %eax
                andl $0x07, %eax
                shll $18, %eax
                movzbl 25(%rbx,%r14), %ecx
                andl $0x3F, %ecx
                shll $12, %ecx
                orl %ecx, %eax
                movzbl 26(%rbx,%r14), %ecx
                andl $0x3F, %ecx
                shll $6, %ecx
                orl %ecx, %eax
                movzbl 27(%rbx,%r14), %ecx
                andl $0x3F, %ecx
                orl %ecx, %eax
                subl $0x10000, %eax
                movl %eax, %ecx
                shrl $10, %ecx
                addl $0xD800, %ecx
                movw %cx, 24(%r13,%r15,2)
                andl $0x3FF, %eax
                addl $0xDC00, %eax
                movw %ax, 26(%r13,%r15,2)
                addq $1, %r15
                addq $1, %r15
                addq $4, %r14
                jmp .Lkof_ci_u_loop
            .Lkof_ci_u_two:
                andl $0x1F, %eax
                shll $6, %eax
                movzbl 25(%rbx,%r14), %ecx
                andl $0x3F, %ecx
                orl %ecx, %eax
                movw %ax, 24(%r13,%r15,2)
                addq $1, %r15
                addq $2, %r14
                jmp .Lkof_ci_u_loop
            .Lkof_ci_u_three:
                andl $0x0F, %eax
                shll $12, %eax
                movzbl 25(%rbx,%r14), %ecx
                andl $0x3F, %ecx
                shll $6, %ecx
                orl %ecx, %eax
                movzbl 26(%rbx,%r14), %ecx
                andl $0x3F, %ecx
                orl %ecx, %eax
                movw %ax, 24(%r13,%r15,2)
                addq $1, %r15
                addq $3, %r14
                jmp .Lkof_ci_u_loop
            .Lkof_ci_u_ascii:
                movw %ax, 24(%r13,%r15,2)
                addq $1, %r15
                addq $1, %r14
                jmp .Lkof_ci_u_loop
            .Lkof_ci_u_done:
                movl %r15d, 16(%r13)
                movq %r13, %rax
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkof_ci_u_null:
                xorl %eax, %eax
                ret

            # kfci_fu(unit@edi) -> eax (Character.toUpperCase por code unit)
            kfci_fu:
                cmpl $0x80, %edi
                jae kfci_fu_bmp
                cmpl $97, %edi
                jb kfci_fu_id
                cmpl $122, %edi
                ja kfci_fu_id
                leal -32(%edi), %eax
                ret
            kfci_fu_bmp:
                pushq %r9
                pushq %r10
                pushq %r11
                movl %edi, %r9d
                leaq .Lkof_ci_up_tab(%rip), %r10
                xorl %esi, %esi
                movl $@UPN@, %edi
                decl %edi
            kfci_fu_bs:
                cmpl %edi, %esi
                jg kfci_fu_nf
                leal (%rsi,%rdi), %eax
                shrl %eax
                movzwl (%r10,%rax,4), %ecx
                cmpl %ecx, %r9d
                je kfci_fu_found
                jl kfci_fu_hi
                leal 1(%rax), %esi
                jmp kfci_fu_bs
            kfci_fu_hi:
                leal -1(%rax), %edi
                jmp kfci_fu_bs
            kfci_fu_found:
                movzwl 2(%r10,%rax,4), %eax
                popq %r11
                popq %r10
                popq %r9
                ret
            kfci_fu_nf:
                movl %r9d, %eax
                popq %r11
                popq %r10
                popq %r9
                ret
            kfci_fu_id:
                movl %edi, %eax
                ret

            # kfci_fl(unit@edi) -> eax (Character.toLowerCase por code unit)
            kfci_fl:
                cmpl $0x80, %edi
                jae kfci_fl_bmp
                cmpl $65, %edi
                jb kfci_fl_id
                cmpl $90, %edi
                ja kfci_fl_id
                leal 32(%edi), %eax
                ret
            kfci_fl_bmp:
                pushq %r9
                pushq %r10
                pushq %r11
                movl %edi, %r9d
                leaq .Lkof_ci_lo_tab(%rip), %r10
                xorl %esi, %esi
                movl $@LON@, %edi
                decl %edi
            kfci_fl_bs:
                cmpl %edi, %esi
                jg kfci_fl_nf
                leal (%rsi,%rdi), %eax
                shrl %eax
                movzwl (%r10,%rax,4), %ecx
                cmpl %ecx, %r9d
                je kfci_fl_found
                jl kfci_fl_hi
                leal 1(%rax), %esi
                jmp kfci_fl_bs
            kfci_fl_hi:
                leal -1(%rax), %edi
                jmp kfci_fl_bs
            kfci_fl_found:
                movzwl 2(%r10,%rax,4), %eax
                popq %r11
                popq %r10
                popq %r9
                ret
            kfci_fl_nf:
                movl %r9d, %eax
                popq %r11
                popq %r10
                popq %r9
                ret
            kfci_fl_id:
                movl %edi, %eax
                ret

            # kof_string_compare_to_ignore_case(a@rdi, b@rsi) -> Int
            .globl kof_string_compare_to_ignore_case
            .type kof_string_compare_to_ignore_case, @function
            kof_string_compare_to_ignore_case:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                movq %rsi, %r12
                testq %rbx, %rbx
                jne .Lkof_ci_nb
                testq %r12, %r12
                jz .Lkof_ci_zero
                movq $-1, %rax
                jmp .Lkof_ci_epi
            .Lkof_ci_zero:
                xorl %eax, %eax
                jmp .Lkof_ci_epi
            .Lkof_ci_nb:
                testq %r12, %r12
                jne .Lkof_ci_go
                movl $1, %eax
                jmp .Lkof_ci_epi
            .Lkof_ci_go:
                movq %rbx, %rdi
                call kof_ci_units
                movq %rax, %r13
                movq %r12, %rdi
                call kof_ci_units
                movq %rax, %r14
                movl 16(%r13), %r15d
                movl 16(%r14), %ebx
                subq $24, %rsp
                movl %r15d, 0(%rsp)
                movl %ebx, 4(%rsp)
                cmpl %ebx, %r15d
                jge .Lkof_ci_nmin
                movl %r15d, 8(%rsp)
                jmp .Lkof_ci_zi
            .Lkof_ci_nmin:
                movl %ebx, 8(%rsp)
            .Lkof_ci_zi:
                xorl %ebx, %ebx
            .Lkof_ci_loop:
                cmpl 8(%rsp), %ebx
                jae .Lkof_ci_tail
                movzwl 24(%r13,%rbx,2), %edi
                call kfci_fu
                movl %eax, 12(%rsp)
                movzwl 24(%r14,%rbx,2), %edi
                call kfci_fu
                cmpl 12(%rsp), %eax
                je .Lkof_ci_next
                movl %eax, 16(%rsp)
                movl 12(%rsp), %edi
                call kfci_fl
                movl %eax, 12(%rsp)
                movl 16(%rsp), %edi
                call kfci_fl
                movl %eax, %edx
                movl 12(%rsp), %eax
                cmpl %edx, %eax
                jne .Lkof_ci_diff
            .Lkof_ci_next:
                incl %ebx
                jmp .Lkof_ci_loop
            .Lkof_ci_diff:
                subl %edx, %eax
                movl %eax, %ecx
                addq $24, %rsp
                movq %rcx, %rax
                jmp .Lkof_ci_epi
            .Lkof_ci_tail:
                movl 0(%rsp), %eax
                subl 4(%rsp), %eax
                movl %eax, %ecx
                addq $24, %rsp
                movq %rcx, %rax
            .Lkof_ci_epi:
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """;
}
