package dev.kof.compiler.runtime;

/**
 * Fatia do encode JSON nativo (§516): kof_json_encode_object — anda a
 * tabela de schema (.Lsch_type_registry) pelo typeId no header, com
 * recursao para campo-objeto. Split do RuntimeJsonEncode (regra <=500;
 * R3 do freeze: estrutura, nao comportamento).
 */
public final class RuntimeJsonObjectEncode {

    private RuntimeJsonObjectEncode() {}

    public static void emitJsonObjectEncode(StringBuilder sb) {
        sb.append("""
            .globl kof_json_encode_object
            .type kof_json_encode_object, @function
            # §516 (26/09): encode de um objeto record/classe em runtime.
            # rdi = ponteiro do objeto; rax = Kof string JSON. O typeId no
            # header (offset 0) resolve a tabela de schema via
            # .Lsch_type_registry (emitida por NativeJsonSchema). Tabela:
            # [0]=totalSize [8]=count [16+i*32]{token,off,code,aux}.
            # code: 1=int 2=long 3=bool 4=string 5=objeto (recursao).
            # Estado em registradores callee-saved (rbx,r12..r15) — a
            # recursao do code-5 preserva tudo. Sem schema (ex.: campo
            # Float — JSN001) -> "null" cru, visivel e estavel.
            kof_json_encode_object:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                movslq (%rbx), %rax
                leaq .Lsch_type_registry(%rip), %r9
            .Ljo_lookup:
                movq (%r9), %rcx
                testq %rcx, %rcx
                jz .Ljo_notfound
                cmpq %rax, %rcx
                je .Ljo_found
                addq $16, %r9
                jmp .Ljo_lookup
            .Ljo_found:
                movq 8(%r9), %r13
                call kof_json_builder_new
                movq %rax, %r12
                movq %r12, %rdi
                movl $123, %esi
                call kof_json_builder_char
                movq 8(%r13), %r14
                xorq %r15, %r15
            .Ljo_loop:
                cmpq %r14, %r15
                jge .Ljo_done
                testq %r15, %r15
                jz .Ljo_nocomma
                movq %r12, %rdi
                movl $44, %esi
                call kof_json_builder_char
            .Ljo_nocomma:
                movq %r15, %rax
                shlq $5, %rax
                leaq 16(%r13,%rax), %rcx
                movq (%rcx), %rsi
                xorq %r8, %r8
            .Ljo_toklen:
                movzbl (%rsi,%r8), %eax
                testl %eax, %eax
                jz .Ljo_tokdone
                incq %r8
                jmp .Ljo_toklen
            .Ljo_tokdone:
                movq %rsi, %rdi
                movl %r8d, %esi
                call kof_string_from_literal
                movq %r12, %rdi
                movq %rax, %rsi
                call kof_json_builder_str
                movq %r15, %rax
                shlq $5, %rax
                leaq 16(%r13,%rax), %rcx
                movq 8(%rcx), %rax
                movq (%rbx,%rax), %rdi
                movq 16(%rcx), %rsi
                cmpq $1, %rsi
                je .Ljo_int
                cmpq $2, %rsi
                je .Ljo_long
                cmpq $3, %rsi
                je .Ljo_bool
                cmpq $4, %rsi
                je .Ljo_str
                call kof_json_encode_object
                jmp .Ljo_vapp
            .Ljo_int:
                call kof_json_encode_int
                jmp .Ljo_vapp
            .Ljo_long:
                call kof_json_encode_long
                jmp .Ljo_vapp
            .Ljo_bool:
                call kof_json_encode_bool
                jmp .Ljo_vapp
            .Ljo_str:
                call kof_json_encode_string
            .Ljo_vapp:
                movq %r12, %rdi
                movq %rax, %rsi
                call kof_json_builder_str
                incq %r15
                jmp .Ljo_loop
            .Ljo_done:
                movq %r12, %rdi
                movl $125, %esi
                call kof_json_builder_char
                movq %r12, %rdi
                call kof_json_builder_result
                jmp .Ljo_exit
            .Ljo_notfound:
                leaq .Ljo_nullk(%rip), %rax
            .Ljo_exit:
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret


            .section .data
            .balign 8
            .Ljo_nullk:
                .int 1
                .int 0
                .int 0
                .int 0
                .int 4
                .int 0
                .ascii "null"
                .section .text

        """);
    }
}
