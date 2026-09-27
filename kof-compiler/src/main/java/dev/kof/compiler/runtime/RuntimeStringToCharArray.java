package dev.kof.compiler.runtime;

/**
 * D-FULL-PARITY-050 (row 11) — {@code String.toCharArray()} no Native x86-64.
 *
 * <p>O runtime guarda o {@code KofString} em bytes UTF-8; o JVM devolve
 * {@code char[]} com os CODE UNITS UTF-16 (a mesma unidade que
 * {@link RuntimeStringOps#emitStringCharAt} e {@code kof_string_length}).
 * Este helper aloca um array com {@code kof_array_alloc(n, 4)} (slot de Char =
 * 4 bytes, como o resto do runtime) e preenche numa única passada: 1/2/3 bytes
 * → 1 unit; astral (4 bytes) → par high/low surrogate, na MESMA aritmética do
 * {@code kof_string_char_at} (round-trip idêntico ao JVM).
 */
public final class RuntimeStringToCharArray {

    private RuntimeStringToCharArray() {}

    public static void emitStringToCharArray(StringBuilder sb) {
        sb.append("""
            .globl kof_string_to_char_array
            .type kof_string_to_char_array, @function
            kof_string_to_char_array:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %r13           # str
                call kof_string_length    # eax = nº de code units UTF-16
                movl %eax, %r12d          # n
                movl %r12d, %edi
                movl $4, %esi             # Char = 4 bytes (slots de array)
                call kof_array_alloc      # rax = Char[]
                movq %rax, %r14           # arr
                movl 16(%r13), %r15d      # byteLen (bytes UTF-8)
                leaq 24(%r13), %rbx       # bytes base
                xorl %r8d, %r8d           # byteOff
                xorl %r9d, %r9d           # unitIndex
            .Lkof_strtoca_walk:
                cmpl %r15d, %r8d
                jge .Lkof_strtoca_done
                movzbl (%rbx,%r8), %eax   # lead byte
                cmpb $0x80, %al
                jb .Lkof_strtoca_a1
                movb %al, %dil
                andb $0xE0, %dil
                cmpb $0xC0, %dil
                je .Lkof_strtoca_a2
                movb %al, %dil
                andb $0xF0, %dil
                cmpb $0xE0, %dil
                je .Lkof_strtoca_a3
                jmp .Lkof_strtoca_astral
            .Lkof_strtoca_a1:
                addl $1, %r8d
                jmp .Lkof_strtoca_store
            .Lkof_strtoca_a2:
                movzbl (%rbx,%r8), %eax
                andl $0x1F, %eax
                shll $6, %eax
                movzbl 1(%rbx,%r8), %ecx
                andl $0x3F, %ecx
                orl %ecx, %eax
                addl $2, %r8d
                jmp .Lkof_strtoca_store
            .Lkof_strtoca_a3:
                movzbl (%rbx,%r8), %eax
                andl $0x0F, %eax
                shll $12, %eax
                movzbl 1(%rbx,%r8), %ecx
                andl $0x3F, %ecx
                shll $6, %ecx
                orl %ecx, %eax
                movzbl 2(%rbx,%r8), %ecx
                andl $0x3F, %ecx
                orl %ecx, %eax
                addl $3, %r8d
                jmp .Lkof_strtoca_store
            .Lkof_strtoca_astral:
                movzbl (%rbx,%r8), %eax
                andl $0x07, %eax
                shll $18, %eax
                movzbl 1(%rbx,%r8), %ecx
                andl $0x3F, %ecx
                shll $12, %ecx
                orl %ecx, %eax
                movzbl 2(%rbx,%r8), %ecx
                andl $0x3F, %ecx
                shll $6, %ecx
                orl %ecx, %eax
                movzbl 3(%rbx,%r8), %ecx
                andl $0x3F, %ecx
                orl %ecx, %eax
                subl $0x10000, %eax       # cp - 0x10000
                movl %eax, %r10d
                shrl $10, %eax
                addl $0xD800, %eax        # high surrogate
                leaq 24(%r14), %rdx
                movl %eax, (%rdx,%r9,4)
                addl $1, %r9d
                movl %r10d, %eax
                andl $0x3FF, %eax
                addl $0xDC00, %eax        # low surrogate
                movl %eax, (%rdx,%r9,4)
                addl $1, %r9d
                addl $4, %r8d
                jmp .Lkof_strtoca_walk
            .Lkof_strtoca_store:
                leaq 24(%r14), %rdx
                movl %eax, (%rdx,%r9,4)
                addl $1, %r9d
                jmp .Lkof_strtoca_walk
            .Lkof_strtoca_done:
                movq %r14, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }
}
