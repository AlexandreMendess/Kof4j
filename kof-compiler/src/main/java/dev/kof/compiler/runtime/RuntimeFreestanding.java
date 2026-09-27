package dev.kof.compiler.runtime;

/**
 * B-1 (PLAN-BAREMETAL-BOOT): perfil {@code freestanding} x86_64 — a costura
 * {@code kof_plat_heap_grow} sobre a ARENA DE HEAP reservada pelo linker
 * script ({@code __kof_heap_start..__kof_heap_end}).
 *
 * <p>O perfil {@code host} cresce o heap por {@code mmap} (syscall 9); no
 * freestanding não há SO para isso. A arena vem do script ({@code .bss}
 * NOBITS de tamanho configurável no link, env {@code KOF_HEAP_SIZE} / prop
 * {@code kof.heap.size}) e o crescimento é um bump sob o MESMO lock de
 * {@code kof_alloc} (o seam é chamado já com o lock tomado — não precisa de
 * lock próprio).
 *
 * <p>O cursor é guardado como OFFSET (inteiro pequeno) e não como ponteiro:
 * um ponteiro cru na {@code .bss} seria uma raiz falsa para o GC conservador
 * (que varre {@code kof_heap_root_start.._end}) e o mark leria memória não
 * alocada. Arena esgotada devolve {@code -1} — o alocador chama
 * {@code kof_panic} ("out of memory"), nunca um ponteiro inválido silencioso
 * (R6).
 */
public final class RuntimeFreestanding {

    private RuntimeFreestanding() {}

    public static void emitHeapGrow(StringBuilder sb) {
        sb.append("""
            .section .bss
            .balign 8
            .Lkof_fs_heap_off: .quad 0

            .section .text
            .globl kof_plat_heap_grow
            .type kof_plat_heap_grow, @function
            kof_plat_heap_grow:
                # rdi=tamanho -> rax=ptr | -1 (mesma semantica do mmap do host)
                movq .Lkof_fs_heap_off(%rip), %rax
                leaq __kof_heap_start(%rip), %rcx
                addq %rax, %rcx              # ptr = start + off
                leaq (%rcx,%rdi), %rdx       # topo novo = ptr + tamanho
                leaq __kof_heap_end(%rip), %r8
                cmpq %r8, %rdx
                ja .Lkof_fs_hg_fail
                addq %rdi, %rax
                movq %rax, .Lkof_fs_heap_off(%rip)
                movq %rcx, %rax
                ret
            .Lkof_fs_hg_fail:
                movq $-1, %rax
                ret
            """);
    }
}
