package dev.kof.compiler.runtime;

/**
 * B-5 (D-BAREMETAL-BODIES): conversão civil (ano/mês/dia) → dias desde
 * 1970-01-01, compartilhada pelas faces bare-metal — BIOS (RTC CMOS) e UEFI
 * ({@code RuntimeServices->GetTime}) — para não duplicar a tabela de dias por
 * mês nem a regra de ano bissexto.
 *
 * <p>Emite {@code kof_civil_to_epoch}: {@code rdi=ano, rsi=mês(1..12),
 * rdx=dia(1..31) -> rax=dias}. Toca só registradores voláteis (rax/rcx/rdx) —
 * nunca os callee-saved dos chamadores ({@code rbx/r12-r15}).
 */
public final class RuntimeCivilEpoch {

    private RuntimeCivilEpoch() {}

    static void emit(StringBuilder sb) {
        sb.append("""
            .section .rodata
            .align 4
            .Lkof_dbom: .long 0,0,31,59,90,120,151,181,212,243,273,304,334
            .section .text
            .type kof_civil_to_epoch, @function
            kof_civil_to_epoch:
                leaq .Lkof_dbom(%rip), %rcx
                movl (%rcx,%rsi,4), %eax     # dbom[mes]
                leal -1(%rdx), %edx
                addl %edx, %eax              # doy = dbom[mes] + (dia-1)
                cmpl $2, %esi
                jle .Lkof_civil_noleap
                testl $3, %edi
                jnz .Lkof_civil_noleap
                incl %eax                    # +1 dia se bissexto e mes>2
            .Lkof_civil_noleap:
                movl %edi, %ecx
                subl $1970, %ecx
                imull $365, %ecx, %ecx
                addl %eax, %ecx
                movl %edi, %eax
                decl %eax
                shrl $2, %eax
                subl $492, %eax
                addl %eax, %ecx              # ecx = dias desde 1970-01-01
                movslq %ecx, %rax
                ret
            """);
    }
}
