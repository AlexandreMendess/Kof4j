package dev.kof.compiler.runtime;

/**
 * Fatia do encode JSON nativo (§520): kof_json_encode_string — writer
 * de string JSON com escapes de virgula E de controle (< 0x20: \b \t
 * \n \f \r backslash-u 00XX), a superfice byte-identica ao Jackson do JVM e do
 * JSON.stringify do JS. Split do RuntimeJsonEncode (regra <=500; R3:
 * estrutura, nao comportamento).
 */
public final class RuntimeJsonEncodeString {

    private RuntimeJsonEncodeString() {}

    public static void emitJsonEncodeString(StringBuilder sb) {
        sb.append("""
            .globl kof_json_encode_string
            .type kof_json_encode_string, @function
            kof_json_encode_string:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                call kof_json_builder_new
                movq %rax, %r12
                movq %r12, %rdi
                movl $34, %esi
                call kof_json_builder_char
                movl 16(%rbx), %r13d
                xorq %r14, %r14
            .Lkof_json_esc_loop:
                cmpl %r13d, %r14d
                jge .Lkof_json_esc_done
                leaq 24(%rbx), %rax
                movzbl (%rax,%r14), %eax
                cmpb $34, %al
                je .Lkof_json_esc_quote
                cmpb $92, %al
                je .Lkof_json_esc_backslash
                cmpb $32, %al
                jb .Lkof_json_esc_ctrl
                movq %r12, %rdi
                movl %eax, %esi
                call kof_json_builder_char
                incq %r14
                jmp .Lkof_json_esc_loop
            .Lkof_json_esc_quote:
                movq %r12, %rdi
                movl $92, %esi
                call kof_json_builder_char
                movq %r12, %rdi
                movl $34, %esi
                call kof_json_builder_char
                incq %r14
                jmp .Lkof_json_esc_loop
            .Lkof_json_esc_backslash:
                movq %r12, %rdi
                movl $92, %esi
                call kof_json_builder_char
                movq %r12, %rdi
                movl $92, %esi
                call kof_json_builder_char
                incq %r14
                jmp .Lkof_json_esc_loop
            .Lkof_json_esc_ctrl:
                # §516: byte < 0x20 precisa sair ESCAPADO — String crua no
                # meio do JSON quebra o wire (Jackson escapa; nativo nao
                # escapava -> JVM != x86 em valor com newline, medido 26/09).
                movb %al, %r15b
                movq %r12, %rdi
                movl $92, %esi
                call kof_json_builder_char
                cmpb $8, %r15b
                je .Lkjc_b
                cmpb $9, %r15b
                je .Lkjc_t
                cmpb $10, %r15b
                je .Lkjc_n
                cmpb $12, %r15b
                je .Lkjc_f
                cmpb $13, %r15b
                je .Lkjc_r
                jmp .Lkjc_u
            .Lkjc_b:
                movl $98, %esi
                jmp .Lkjc_emit
            .Lkjc_t:
                movl $116, %esi
                jmp .Lkjc_emit
            .Lkjc_n:
                movl $110, %esi
                jmp .Lkjc_emit
            .Lkjc_f:
                movl $102, %esi
                jmp .Lkjc_emit
            .Lkjc_r:
                movl $114, %esi
                jmp .Lkjc_emit
            .Lkjc_u:
                movq %r12, %rdi
                movl $117, %esi
                call kof_json_builder_char
                movq %r12, %rdi
                movl $48, %esi
                call kof_json_builder_char
                movq %r12, %rdi
                movl $48, %esi
                call kof_json_builder_char
                movzbl %r15b, %eax
                shrl $4, %eax
                call .Lkjc_hex
                movzbl %r15b, %eax
                andq $15, %rax
                call .Lkjc_hex
                incq %r14
                jmp .Lkof_json_esc_loop
            .Lkjc_hex:
                cmpq $10, %rax
                jb .Lkjc_h9
                addq $55, %rax
                jmp .Lkjc_hc
            .Lkjc_h9:
                addq $48, %rax
            .Lkjc_hc:
                movq %r12, %rdi
                movl %eax, %esi
                call kof_json_builder_char
                ret
            .Lkjc_emit:
                movq %r12, %rdi
                call kof_json_builder_char
                incq %r14
                jmp .Lkof_json_esc_loop
            .Lkof_json_esc_done:
                movq %r12, %rdi
                movl $34, %esi
                call kof_json_builder_char
                movq %r12, %rdi
                call kof_json_builder_result
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

                """);
    }
}
