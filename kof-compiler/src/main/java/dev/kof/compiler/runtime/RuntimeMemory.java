package dev.kof.compiler.runtime;
import dev.kof.compiler.NativeRuntime;

/**
 * Emissão do ASM de alocação/liberação (kof_alloc/kof_free/kof_init_object/kof_memstats)
 * do runtime nativo. Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
public final class RuntimeMemory {

    private RuntimeMemory() {}

    public static void emitInitObject(StringBuilder sb) {
        sb.append("""
            .globl kof_init_object
            .type kof_init_object, @function
            kof_init_object:
                movl %esi, 0(%rdi)
                movl $0, 4(%rdi)
                movq %rdx, 8(%rdi)
                ret
            """);
    }

    public static void emitAlloc(StringBuilder sb) {
        sb.append("""
            .section .bss
            .balign 8
            kof_alloc_lock: .space 40          # pthread_mutex_t (zero-init = default)
            kof_free_head: .quad 0
            .globl kof_gc_head
            .balign 8
            kof_gc_head: .quad 0
            .balign 8
            kof_heap_low: .quad 0
            .balign 8
            kof_heap_high: .quad 0
            .balign 8
            kof_main_tid: .quad 0              # tid do main thread p/ o GC (conservador lê a stack)
            kof_main_stack_bottom: .quad 0     # rsp do _start: topo da pilha main; o mark varre rsp..ate_isto (G-6b)
            .section .data
            .Lstr_alloc_fail: .asciz "Runtime error: out of memory"
            .section .rodata
            .Lkof_alloc_dbg: .ascii "."
            .section .text
            .globl kof_alloc
            .type kof_alloc, @function
            kof_alloc:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $24, %rsp
                movq %rdi, (%rsp)                # tamanho solicitado
                leaq kof_alloc_lock(%rip), %rsi  # &lock
                xorl %eax, %eax                  # esperado 0
            .Lkof_alloc_lock_try:
                movl $1, %edx
                lock cmpxchg %edx, (%rsi)        # 0->1 atomically?
                testl %eax, %eax
                jz .Lkof_alloc_locked
                # ocupado: futex wait
                movl $1, %edx                    # val=1
                xorq %r10, %r10
                xorq %r8, %r8
                xorq %r9, %r9
                call kof_plat_sync
                jmp .Lkof_alloc_lock_try
            .Lkof_alloc_locked:
                movq $0, 8(%rsp)                 # flag: GC ainda nao tentou
                movq (%rsp), %r12
                addq $7, %r12
                andq $~7, %r12
                addq $32, %r12
                movq kof_free_head(%rip), %r13
                xorq %r14, %r14
                movq $1048576, %r11
            .Lkof_alloc_search:
                testq %r13, %r13
                je .Lkof_alloc_maybe_gc
                decq %r11
                je .Lkof_alloc_mmap
                movq 0(%r13), %r15
                cmpq %r12, %r15
                jb .Lkof_alloc_next
                cmpq $0, %r14
                je .Lkof_alloc_found_head
                movq 8(%r13), %r15
                movq %r15, 8(%r14)
                jmp .Lkof_alloc_found
            .Lkof_alloc_found_head:
                movq 8(%r13), %rax
                movq %rax, kof_free_head(%rip)
            .Lkof_alloc_found:
                movb $0, 24(%r13)
                movq %r13, %rax
                addq $32, %rax
                incq .Lkof_alloc_count(%rip)
                addq %r12, .Lkof_alloc_bytes(%rip)
                movq %rax, (%rsp)                # preserva retorno
                leaq kof_alloc_lock(%rip), %rdi
                movl $0, (%rdi)
                movl $1, %esi                    # FUTEX_WAKE, 1 waiter
                xorl %edx, %edx
                xorq %r10, %r10
                call kof_plat_sync
                movq (%rsp), %rax
                addq $24, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkof_alloc_next:
                movq %r13, %r14
                movq 8(%r13), %r13
                jmp .Lkof_alloc_search
            .Lkof_alloc_maybe_gc:
                # G-6(a) (native-multiarch, §260): free-list exausta -> UMA
                # passada de collect_now antes do mmap (flag 8(%rsp), ja
                # zerada no prologo). Antes o trigger era INSOND (temporario
                # vivo em caller-saved invisivel ao mark -> sweep liberava
                # bloco vivo -> SIGSEGV 139 medido em KofStringParse/
                # supervisor). Agora kof_gc_collect_now derrama os 15 GPRs
                # (blanket spill) e o cursor de busca aqui e NULL (falhou) —
                # nada vivo em registrador nosso alem dos salvos. Gate
                # kof_spawn_count==0 (contador CUMULATIVO — apos qualquer
                # spawn o auto-collect fica desligado: pilhas de worker nao
                # sao varridas; face "scan de stack de worker" catalogada,
                # nunca silenciosa). Sem gate/flag o hang antigo (status.md)
                # voltava: collect reentrante com cursor vivo.
                cmpq $0, 8(%rsp)
                jne .Lkof_alloc_mmap
                cmpq $0, kof_spawn_count(%rip)
                jne .Lkof_alloc_mmap
                movq $1, 8(%rsp)
                call kof_gc_collect_now
                movq kof_free_head(%rip), %r13
                xorq %r14, %r14
                movq $1048576, %r11
                jmp .Lkof_alloc_search
            .Lkof_alloc_maybe_gc_skip:
                jmp .Lkof_alloc_mmap
            .Lkof_alloc_mmap:
                # B-0/B-2: o crescimento do heap cruza a costura
                # kof_plat_heap_grow (host = mmap syscall; UEFI =
                # AllocatePool) — o alocador não sabe de plataforma.
                movq %r12, %rdi
                subq $8, %rsp          # alinhamento p/ a chamada (frame 24+40 ≡ 8)
                call kof_plat_heap_grow
                addq $8, %rsp
                testq %rax, %rax
                js .Lkof_alloc_fail
                movq %r12, 0(%rax)
                movq $0, 8(%rax)
                movq kof_gc_head(%rip), %rcx
                movq %rcx, 16(%rax)
                movb $0, 24(%rax)
                movq %rax, kof_gc_head(%rip)
                movq kof_heap_low(%rip), %rcx
                testq %rcx, %rcx
                je .Lheap_set_low
                cmpq %rcx, %rax
                jae .Lheap_low_ok
            .Lheap_set_low:
                movq %rax, kof_heap_low(%rip)
            .Lheap_low_ok:
                movq kof_heap_high(%rip), %rcx
                movq %rax, %rdx
                addq %r12, %rdx
                cmpq %rdx, %rcx
                jae .Lheap_high_ok
                movq %rdx, kof_heap_high(%rip)
            .Lheap_high_ok:
                addq $32, %rax
                incq .Lkof_alloc_count(%rip)
                addq %r12, .Lkof_alloc_bytes(%rip)
                movq %rax, (%rsp)                # preserva retorno
                leaq kof_alloc_lock(%rip), %rdi
                movl $0, (%rdi)
                movl $1, %esi                    # FUTEX_WAKE, 1 waiter
                xorl %edx, %edx
                xorq %r10, %r10
                call kof_plat_sync
                movq (%rsp), %rax
                addq $24, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkof_alloc_fail:
                leaq kof_alloc_lock(%rip), %rdi
                movl $0, (%rdi)
                movl $1, %esi
                xorl %edx, %edx
                xorq %r10, %r10
                call kof_plat_sync
                leaq .Lstr_alloc_fail(%rip), %rdi
                call kof_panic
            """);
        // B-0/B-2: corpo da costura kof_plat_heap_grow POR PERFIL —
        // host/freestanding = mmap syscall (semântica idêntica ao que estava
        // inline); UEFI = AllocatePool via RuntimeUefi. O alocador fica
        // agnóstico de plataforma (o seam é o ponto único de plataforma).
        if (dev.kof.compiler.nat.NativeProfile.activeIsUefi()) {
            RuntimeUefi.emitUefiHeapGrow(sb);
        } else if (dev.kof.compiler.nat.NativeProfile.active == dev.kof.compiler.nat.NativeProfile.FREESTANDING
                || dev.kof.compiler.nat.NativeProfile.activeIsBios()) {
            // B-1: sem SO para mmap — a arena vem do linker script.
            // B-3b-3: no BIOS (bare, sem syscalls) o heap cresce da MESMA arena
            // do linker (__kof_heap_start..end); o corpo host (syscall mmap)
            // travava no primeiro alloc (kof_array_alloc do args array do main).
            RuntimeFreestanding.emitHeapGrow(sb);
        } else {
            sb.append("""
            .section .text
            .globl kof_plat_heap_grow
            .type kof_plat_heap_grow, @function
            kof_plat_heap_grow:
                # rdi=tamanho -> rax=ptr | -1 (semântica mmap)
                movq %rdi, %rsi           # mmap(len)
                movq $0, %rdi             # addr = NULL
                movq $3, %rdx             # PROT_READ|PROT_WRITE
                movq $0x22, %r10          # MAP_PRIVATE|MAP_ANONYMOUS
                movq $-1, %r8             # fd
                movq $0, %r9              # offset
                movq $9, %rax             # SYS_mmap
                syscall
                ret
            """);
        }
    }

    public static void emitFree(StringBuilder sb) {
        sb.append("""
            .globl kof_free
            .type kof_free, @function
            kof_free:
                testq %rdi, %rdi
                jz .Lkof_free_done
                movq -32(%rdi), %rsi
                leaq -32(%rdi), %rdi
                movb $2, 24(%rdi)           # bit1: esta na free list (sweep nao re-insere)
                movq kof_free_head(%rip), %rax
                movq %rax, 8(%rdi)
                movq %rdi, kof_free_head(%rip)
                incq .Lkof_free_count(%rip)
                addq %rsi, .Lkof_free_bytes(%rip)
            .Lkof_free_done:
                ret
            """);
    }

    public static void emitMemstats(StringBuilder sb) {
        sb.append("""
            .section .data
            .Lkof_alloc_count: .quad 0
            .Lkof_free_count: .quad 0
            .Lkof_alloc_bytes: .quad 0
            .Lkof_free_bytes: .quad 0
            .Lkof_memstats_lbl_alloc: .asciz "allocs: "
            .Lkof_memstats_lbl_free: .asciz "frees: "
            .Lkof_memstats_lbl_live: .asciz "live bytes: "
            .Lkof_memstats_nl: .asciz "\\n"
            .section .text
            .globl kof_memstats
            .type kof_memstats, @function
            kof_memstats:
                pushq %rbx
                leaq .Lkof_memstats_lbl_alloc(%rip), %rdi
                call kof_print
                movq .Lkof_alloc_count(%rip), %rdi
                call kof_long_to_string
                movq %rax, %rdi
                call kof_print_string
                leaq .Lkof_memstats_nl(%rip), %rdi
                call kof_print
                leaq .Lkof_memstats_lbl_free(%rip), %rdi
                call kof_print
                movq .Lkof_free_count(%rip), %rdi
                call kof_long_to_string
                movq %rax, %rdi
                call kof_print_string
                leaq .Lkof_memstats_nl(%rip), %rdi
                call kof_print
                leaq .Lkof_memstats_lbl_live(%rip), %rdi
                call kof_print
                movq .Lkof_alloc_bytes(%rip), %rbx
                subq .Lkof_free_bytes(%rip), %rbx
                movq %rbx, %rdi
                call kof_long_to_string
                movq %rax, %rdi
                call kof_print_string
                leaq .Lkof_memstats_nl(%rip), %rdi
                call kof_print
                popq %rbx
                ret
            """);
    }

}