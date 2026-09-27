package dev.kof.compiler.runtime;

/**
 * B-5 (D-BAREMETAL-BODIES): corpo do {@code kof_plat_random} para as faces
 * bare-metal (BIOS e UEFI). O contrato é o mesmo da face Linux
 * ({@code getrandom(buf=rdi, len=rsi) -> bytes}): preenche {@code len} bytes.
 *
 * <p>Entropia: <b>RDRAND</b> quando a CPU expõe ({@code CPUID.01H:ECX[30]}),
 * senão o <b>TSC</b> ({@code rdtsc}); em ambos os casos a semente alimenta um
 * <b>xorshift64</b> (não-criptográfico — exatamente o contrato do namespace
 * {@code random.*}, distinto de {@code security.*}, que segue sem corpo bare).
 * Nunca um stub: bytes reais, distintos entre execuções.
 *
 * <p>É o MESMO asm para as duas faces (rdtsc/RDRAND existem em x86_64 tanto em
 * modo SeaBIOS quanto como app UEFI) — por isso vive em classe própria e o
 * podador de fatias o trata como uma fatia só.
 */
public final class RuntimeBareRandom {

    private RuntimeBareRandom() {}

    static void emit(StringBuilder sb) {
        sb.append("""
            .section .data
            .globl kof_plat_rng_state
            kof_plat_rng_state: .quad 0
            kof_plat_rng_seeded: .byte 0

            .section .text
            .type kof_plat_rng_seed, @function
            kof_plat_rng_seed:
                cmpb $0, kof_plat_rng_seeded(%rip)
                jne .Lrng_seed_done
                pushq %rbx
                movl $1, %eax
                cpuid
                bt $30, %ecx                 # RDRAND disponivel?
                jnc .Lrng_seed_tsc
                rdrand %rax
                jnc .Lrng_seed_tsc
                movq %rax, %rbx
                jmp .Lrng_seed_mix
            .Lrng_seed_tsc:
                rdtsc                        # edx:eax = contador de tempo
                shlq $32, %rdx
                orq %rdx, %rax
                movq %rax, %rbx
            .Lrng_seed_mix:
                movq %rbx, %rax
                movq %rax, %rcx
                shlq $13, %rcx
                xorq %rcx, %rax
                movabsq $0x9E3779B97F4A7C15, %rcx
                xorq %rcx, %rax
                testq %rax, %rax
                jnz .Lrng_seed_store
                movabsq $0x2545F4914F6CDD1D, %rax
            .Lrng_seed_store:
                movq %rax, kof_plat_rng_state(%rip)
                movb $1, kof_plat_rng_seeded(%rip)
                popq %rbx
            .Lrng_seed_done:
                ret

            .type kof_plat_rng_next, @function
            kof_plat_rng_next:
                movq kof_plat_rng_state(%rip), %rax
                movq %rax, %rcx
                shlq $13, %rcx
                xorq %rcx, %rax
                movq %rax, %rcx
                shrq $7, %rcx
                xorq %rcx, %rax
                movq %rax, %rcx
                shlq $17, %rcx
                xorq %rcx, %rax
                movq %rax, kof_plat_rng_state(%rip)
                ret

            .globl kof_plat_random
            .type kof_plat_random, @function
            kof_plat_random:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx              # buf
                movq %rsi, %r12              # restante
                movq %rsi, %r13              # total (retorno)
                call kof_plat_rng_seed
            .Lrng_fill:
                testq %r12, %r12
                jz .Lrng_fill_done
                call kof_plat_rng_next
                movq %rax, %r14
                movq $8, %rcx
                cmpq %rcx, %r12
                cmovbq %r12, %rcx            # n = min(8, restante)
                xorl %edx, %edx
            .Lrng_store:
                cmpq %rcx, %rdx
                jae .Lrng_stored
                movb %r14b, (%rbx,%rdx)
                shrq $8, %r14
                incq %rdx
                jmp .Lrng_store
            .Lrng_stored:
                addq %rcx, %rbx
                subq %rcx, %r12
                jmp .Lrng_fill
            .Lrng_fill_done:
                movq %r13, %rax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }
}
