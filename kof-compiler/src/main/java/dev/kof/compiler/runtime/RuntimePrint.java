package dev.kof.compiler.runtime;
import dev.kof.compiler.NativeRuntime;

/**
 * Emissão do ASM de impressão bruta (kof_print / kof_println) do runtime
 * nativo. Domínio isolado do NativeRuntime -- a extração NÃO muda o corpo
 * (refactor preserva semântica).
 */
public final class RuntimePrint {

    private RuntimePrint() {}

    public static void emitPrint(StringBuilder sb) {
        sb.append("""
            .globl kof_print
            .type kof_print, @function
            kof_print:
                pushq %rbx
                movq %rdi, %rbx
                xorq %rdx, %rdx
            .Lkof_print_len:
                cmpb $0, (%rbx,%rdx)
                je .Lkof_print_do
                incq %rdx
                jmp .Lkof_print_len
            .Lkof_print_do:
                movq $1, %rdi
                movq %rbx, %rsi
                call kof_plat_write
                popq %rbx
                ret
            """);
    }

    public static void emitPrintln(StringBuilder sb) {
        sb.append("""
            .globl kof_println
            .type kof_println, @function
            kof_println:
                # §396: null CRU (record via T?-API, String?, objeto qualquer) ->
                # "null" + newline, paridade JVM. O teste de MAGIC abaixo LE
                # (%rdi) — sem este guard o binario SIGSEGVava (ec 139).
                # Precedente do idioma: kof_println_string no riscv ja imprime
                # "null" no mesmo caso (NativeRiscvAsmRt0 .Lpls_*).
                testq %rdi, %rdi
                jne .Lkof_println_nn
                leaq .Lkpln_null(%rip), %rdi
                call kof_print
                leaq .Lnewline(%rip), %rdi
                call kof_print
                ret
            .Lkof_println_nn:
                # §284: dispatch de box de erasure — MAGIC em [0] e o valor e
                # um BOX [magic][tag][value] (RuntimeErasureBox); o print segue
                # o golden JVM por tag. Sem MAGIC (ponteiro de objeto real ou
                # string) o caminho antigo roda INALTERADO (zero regressao).
                movabsq $0x4B4F46425F425801, %rax
                cmpq %rax, (%rdi)
                jne .Lkof_println_gen
                pushq %rbx
                movq %rdi, %rbx
                movq 8(%rbx), %rax
                cmpl $0, %eax
                je .Lkp_box_int
                cmpl $2, %eax
                je .Lkp_box_long              # long: kof_int_to_string trunca em 32-bit
                cmpl $3, %eax
                je .Lkp_box_bool
                cmpl $4, %eax
                je .Lkp_box_dbl
                cmpl $5, %eax
                je .Lkp_box_flt
                jmp .Lkp_box_gen              # tag desconhecido -> caminho antigo
            .Lkp_box_int:
                movq 16(%rbx), %rdi
                call kof_int_to_string
                movq %rax, %rdi
                call kof_println_string
                jmp .Lkp_box_end
            .Lkp_box_long:
                movq 16(%rbx), %rdi
                call kof_long_to_string
                movq %rax, %rdi
                call kof_println_string
                jmp .Lkp_box_end
            .Lkp_box_bool:
                movq 16(%rbx), %rdi
                call kof_bool_to_string
                movq %rax, %rdi
                call kof_println_string
                jmp .Lkp_box_end
            .Lkp_box_dbl:
                movq 16(%rbx), %rdi
                movq %rdi, %xmm0
                call kof_print_double
                jmp .Lkp_box_nl
            .Lkp_box_flt:
                movq 16(%rbx), %rdi
                movd %edi, %xmm0
                cvtss2sd %xmm0, %xmm0
                call kof_print_double
                jmp .Lkp_box_nl
            .Lkp_box_gen:
                movq %rbx, %rdi
                call kof_print
                jmp .Lkp_box_nl
            .Lkp_box_nl:
                leaq .Lnewline(%rip), %rdi
                call kof_print
            .Lkp_box_end:
                popq %rbx
                ret
            .Lkof_println_gen:
                call kof_print
                pushq %rbx
                leaq .Lnewline(%rip), %rdi
                call kof_print
                popq %rbx
                ret
            .Lkpln_null: .asciz "null"
            """);
    }
}