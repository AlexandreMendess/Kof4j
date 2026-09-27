package dev.kof.compiler.nat.mcu;

/**
 * B-4 follow-up (a) (PLAN-BAREMETAL-BOOT B-4 + {@code D-BAREMETAL-MCU-GC}): o
 * coletor G-4/G-5 espelhado no <b>Cortex-M3 (Thumb-2)</b> — mesmo algoritmo do
 * {@link NativeMcuGcRiscv32}, ISA diferente.
 *
 * <p>Port fiel do RV32I para Thumb-2/AAPCS: header de <b>16 B</b> idêntico
 * (size/free_next/gc_next/flags), payload {@code user = block+16}, free-list
 * first-fit LIFO, mark conservador (raízes estáticas + pilha
 * {@code [sp,_stack_top)}) e OOM com collect+retry UMA vez. A arena vem do
 * linker script ({@code _kof_heap_start}..{@code _kof_heap_end}).
 *
 * <p>Diferenças de arquitetura, explícitas: registradores callee-saved r4-r11 no
 * lugar de s0-s11; sem {@code udiv} no CM3 (divisão decimal por subtração
 * repetida); ponteiros de 32 bits; e o guard de alinhamento não usa o limiar
 * fixo de 4096 do RV32I — no Cortex-M3 o heap pode começar abaixo dele, então a
 * validade vem só do par {@code [heap_start,heap_end)} + alinhamento de 4.
 *
 * <p>Fatias: <b>alloc/free/gc-list/dump/memstats</b> + <b>mark conservador</b>
 * aqui; o <b>sweep</b> vive em {@link NativeMcuArmGcSweep}. Prova:
 * {@code NativeMcuArmGcTest}, harness asm cru sob {@code qemu-system-arm -M
 * mps2-an385}, no mesmo padrão do {@code NativeMcuGcTest} do riscv32.
 */
public final class NativeMcuArmGc {

    private NativeMcuArmGc() {}

    /** Blocos de header da ABI MCU: 4 words de 32 bits (idêntico ao riscv32). */
    public static final int HEADER_BYTES = 16;

    public static String runtimeAsm() {
        return """
                .syntax unified
                .thumb
                .cpu cortex-m3

                .section .data
                .align 2
                .globl kof_alloc_ptr
                kof_alloc_ptr: .word _kof_heap_start

                .section .bss
                .align 2
                .Lkof_free_head: .word 0
                .Lkof_gc_head: .word 0
                .Lkof_alloc_count: .word 0
                .Lkof_free_count: .word 0
                .Lkof_alloc_bytes: .word 0
                .Lkof_free_bytes: .word 0

                .section .text
                .thumb
                # kof_alloc(size@r0) -> user ptr. Header 16B (size/free_next/
                # gc_next/flags); free-list first-fit (LIFO), senão bump com guard
                # OOM em _kof_heap_end. Bloco novo entra na gc-list (LIFO, flags=0).
                .thumb_func
                .globl kof_alloc
                kof_alloc:
                    push {r4-r11, lr}
                    add r4, r0, #15
                    bic r4, r4, #15
                    add r4, r4, #16
                    movs r5, #0
                .Lalloc_retry:
                    ldr r6, =.Lkof_free_head
                    ldr r6, [r6]
                    movs r7, #0
                .Lalloc_fsearch:
                    cbz r6, .Lalloc_bump
                    ldr r8, [r6]
                    cmp r8, r4
                    blo .Lalloc_fnext
                    ldr r9, [r6, #4]
                    cbz r7, .Lalloc_fhead
                    str r9, [r7, #4]
                    b .Lalloc_found
                .Lalloc_fhead:
                    ldr r10, =.Lkof_free_head
                    str r9, [r10]
                .Lalloc_found:
                    movs r8, #0
                    str r8, [r6, #12]
                    ldr r10, =.Lkof_alloc_count
                    ldr r8, [r10]
                    add r8, r8, #1
                    str r8, [r10]
                    ldr r10, =.Lkof_alloc_bytes
                    ldr r8, [r10]
                    add r8, r8, r4
                    str r8, [r10]
                    add r0, r6, #16
                    b .Lalloc_done
                .Lalloc_fnext:
                    mov r7, r6
                    ldr r6, [r6, #4]
                    b .Lalloc_fsearch
                .Lalloc_bump:
                    ldr r8, =kof_alloc_ptr
                    ldr r9, [r8]
                    add r10, r9, r4
                    ldr r11, =_kof_heap_end
                    cmp r10, r11
                    bhi .Lalloc_oom
                    str r10, [r8]
                    str r4, [r9]
                    movs r10, #0
                    str r10, [r9, #4]
                    ldr r10, =.Lkof_gc_head
                    ldr r11, [r10]
                    str r11, [r9, #8]
                    str r9, [r10]
                    movs r10, #0
                    str r10, [r9, #12]
                    ldr r10, =.Lkof_alloc_count
                    ldr r11, [r10]
                    add r11, r11, #1
                    str r11, [r10]
                    ldr r10, =.Lkof_alloc_bytes
                    ldr r11, [r10]
                    add r11, r11, r4
                    str r11, [r10]
                    add r0, r9, #16
                    b .Lalloc_done
                .Lalloc_oom:
                    # B4-GC-3: UM collect (mark+sweep) e UMA nova tentativa; ainda
                    # sem espaço -> panic nomeado (R6). Seguro porque o
                    # kof_gc_collect_now derrama r4-r11 e o mark varre a pilha:
                    # todo temporário vivo é visto.
                    cbnz r5, .Lalloc_panic
                    movs r5, #1
                    bl kof_gc_collect_now
                    b .Lalloc_retry
                .Lalloc_panic:
                    ldr r0, =.Lgc_oom
                    bl kof_panic
                .Lalloc_done:
                    pop {r4-r11, pc}
                .pool

                # kof_free(ptr@r0) — devolve à free-list (LIFO), flags=2 (bit1).
                .thumb_func
                .globl kof_free
                kof_free:
                    cbz r0, .Lfree_done
                    sub r0, r0, #16
                    ldr r1, [r0]
                    movs r2, #2
                    str r2, [r0, #12]
                    ldr r3, =.Lkof_free_head
                    ldr r2, [r3]
                    str r2, [r0, #4]
                    str r0, [r3]
                    ldr r3, =.Lkof_free_count
                    ldr r2, [r3]
                    add r2, r2, #1
                    str r2, [r3]
                    ldr r3, =.Lkof_free_bytes
                    ldr r2, [r3]
                    add r2, r2, r1
                    str r2, [r3]
                .Lfree_done:
                    bx lr

                # kof_gc_dump() — uma linha por bloco: `gc <size_total> <flags>`.
                .thumb_func
                .globl kof_gc_dump
                kof_gc_dump:
                    push {r4, lr}
                    ldr r4, =.Lkof_gc_head
                    ldr r4, [r4]
                .Lgcd_loop:
                    cbz r4, .Lgcd_done
                    ldr r0, =.Lgc_lbl
                    bl .Lput
                    ldr r0, [r4]
                    bl .Lput_uint
                    ldr r0, =.Lgc_sp
                    bl .Lput
                    ldr r0, [r4, #12]
                    bl .Lput_uint
                    ldr r0, =.Lgc_nl
                    bl .Lput
                    ldr r4, [r4, #8]
                    b .Lgcd_loop
                .Lgcd_done:
                    pop {r4, pc}
                .pool

                # kof_memstats() — allocs/frees/live bytes em decimal.
                .thumb_func
                .globl kof_memstats
                kof_memstats:
                    push {r4, lr}
                    ldr r0, =.Lms_alloc
                    bl .Lput
                    ldr r4, =.Lkof_alloc_count
                    ldr r0, [r4]
                    bl .Lput_uint
                    ldr r0, =.Lgc_nl
                    bl .Lput
                    ldr r0, =.Lms_free
                    bl .Lput
                    ldr r4, =.Lkof_free_count
                    ldr r0, [r4]
                    bl .Lput_uint
                    ldr r0, =.Lgc_nl
                    bl .Lput
                    ldr r0, =.Lms_live
                    bl .Lput
                    ldr r4, =.Lkof_alloc_bytes
                    ldr r0, [r4]
                    ldr r4, =.Lkof_free_bytes
                    ldr r1, [r4]
                    sub r0, r0, r1
                    bl .Lput_uint
                    ldr r0, =.Lgc_nl
                    bl .Lput
                    pop {r4, pc}
                .pool

                # kof_panic(msg@r0): imprime e sai(1); se exit retornar, gira.
                .thumb_func
                .globl kof_panic
                kof_panic:
                    push {r4, lr}
                    bl .Lput
                    ldr r0, =.Lgc_nl
                    bl .Lput
                    movs r0, #1
                    bl kof_plat_exit
                .Lpanic_halt:
                    b .Lpanic_halt
                .pool

                # .Lput(r0=asciz): kof_plat_write(buf,len). Não aloca.
                .thumb_func
                .Lput:
                    push {r4, r5, lr}
                    mov r4, r0
                    movs r5, #0
                .Lput_len:
                    add r1, r4, r5
                    ldrb r1, [r1]
                    cbz r1, .Lput_w
                    add r5, r5, #1
                    b .Lput_len
                .Lput_w:
                    mov r0, r4
                    mov r1, r5
                    bl kof_plat_write
                    pop {r4, r5, pc}

                # .Lput_uint(r0): decimal via subtração repetida (CM3 não tem udiv).
                .thumb_func
                .Lput_uint:
                    push {r4, r5, lr}
                    sub sp, sp, #16
                    add r5, sp, #16
                    mov r4, r0
                    cbz r4, .Lpu_zero
                .Lpu_loop:
                    mov r0, r4
                    bl .Ldiv10
                    mov r4, r0
                    add r1, r1, #48
                    sub r5, r5, #1
                    strb r1, [r5]
                    cmp r4, #0
                    bne .Lpu_loop
                    b .Lpu_write
                .Lpu_zero:
                    sub r5, r5, #1
                    movs r1, #48
                    strb r1, [r5]
                .Lpu_write:
                    add r1, sp, #16
                    sub r1, r1, r5
                    mov r0, r5
                    bl kof_plat_write
                    add sp, sp, #16
                    pop {r4, r5, pc}

                # .Ldiv10(r0) -> r0=quot, r1=resto (CM3 não tem udiv).
                .thumb_func
                .Ldiv10:
                    movs r1, #0
                    movs r2, #10
                .Ld10_loop:
                    cmp r0, r2
                    blo .Ld10_done
                    sub r0, r0, r2
                    add r1, r1, #1
                    b .Ld10_loop
                .Ld10_done:
                    mov r2, r0
                    mov r0, r1
                    mov r1, r2
                    bx lr

                # --- B-4 follow-up (a): mark conservador (port RV32I -> Thumb-2) ---
                # kof_gc_try_mark(ptr@r0): se ptr cai no PAYLOAD de um bloco e está
                # no heap, seta bit0 (mark). Sem clobber de r4-r11.
                .thumb_func
                .globl kof_gc_try_mark
                kof_gc_try_mark:
                    cmp r0, #0
                    beq .Ltm_done
                    movs r1, #3
                    ands r1, r0
                    bne .Ltm_done
                    ldr r1, =_kof_heap_start
                    cmp r0, r1
                    blo .Ltm_done
                    ldr r1, =_kof_heap_end
                    cmp r0, r1
                    bhs .Ltm_done
                    ldr r1, =.Lkof_gc_head
                    ldr r1, [r1]
                    ldr r2, =100000
                .Ltm_loop:
                    cbz r1, .Ltm_done
                    sub r2, r2, #1
                    cbz r2, .Ltm_done
                    add r3, r1, #16
                    ldr r12, [r1]
                    sub r12, r12, #16
                    cmp r0, r3
                    blo .Ltm_next
                    add r3, r3, r12
                    cmp r0, r3
                    bhs .Ltm_next
                    b .Ltm_found
                .Ltm_next:
                    ldr r1, [r1, #8]
                    b .Ltm_loop
                .Ltm_found:
                    ldrb r3, [r1, #12]
                    ands r3, r3, #1
                    bne .Ltm_done
                    movs r3, #1
                    strb r3, [r1, #12]
                .Ltm_done:
                    bx lr
                .pool

                # kof_gc_mark_transitive(ptr@r0): como try_mark, mas ao marcar um
                # bloco NOVO varre os campos (4 em 4 pelo payload) e recorre.
                .thumb_func
                .globl kof_gc_mark_transitive
                kof_gc_mark_transitive:
                    push {r4-r8, lr}
                    cmp r0, #0
                    beq .Lmt_done
                    movs r1, #3
                    ands r1, r0
                    bne .Lmt_done
                    ldr r1, =_kof_heap_start
                    cmp r0, r1
                    blo .Lmt_done
                    ldr r1, =_kof_heap_end
                    cmp r0, r1
                    bhs .Lmt_done
                    ldr r1, =.Lkof_gc_head
                    ldr r1, [r1]
                    ldr r2, =100000
                .Lmt_loop:
                    cbz r1, .Lmt_done
                    sub r2, r2, #1
                    cbz r2, .Lmt_done
                    add r3, r1, #16
                    ldr r4, [r1]
                    sub r4, r4, #16
                    cmp r0, r3
                    blo .Lmt_next
                    add r3, r3, r4
                    cmp r0, r3
                    bhs .Lmt_next
                    b .Lmt_found
                .Lmt_next:
                    ldr r1, [r1, #8]
                    b .Lmt_loop
                .Lmt_found:
                    ldrb r3, [r1, #12]
                    ands r3, r3, #1
                    bne .Lmt_done
                    movs r3, #1
                    strb r3, [r1, #12]
                    mov r4, r1
                    ldr r7, [r4]
                    sub r7, r7, #16
                    add r5, r4, #16
                    add r6, r5, r7
                    cbz r7, .Lmt_done
                .Lmt_fields:
                    cmp r5, r6
                    bhs .Lmt_done
                    ldr r0, [r5]
                    cbz r0, .Lmt_fnext
                    bl kof_gc_mark_transitive
                .Lmt_fnext:
                    add r5, r5, #4
                    b .Lmt_fields
                .Lmt_done:
                    pop {r4-r8, pc}
                .pool

                # kof_gc_mark(): marca raízes de PILHA [sp,_stack_top) e ESTÁTICAS
                # (.Lkof_heap_root_start..end), com r4-r11 derramados no frame para
                # que temporários em registrador apareçam na varredura.
                .thumb_func
                .globl kof_gc_mark
                kof_gc_mark:
                    push {r4-r11, lr}
                    mov r4, sp
                    ldr r5, =_stack_top
                .Lgm_stack:
                    cmp r4, r5
                    bhs .Lgm_static
                    ldr r0, [r4]
                    bl kof_gc_mark_transitive
                    add r4, r4, #4
                    b .Lgm_stack
                .Lgm_static:
                    ldr r4, =.Lkof_heap_root_start
                    ldr r5, =.Lkof_heap_root_end
                .Lgm_roots:
                    cmp r4, r5
                    bhs .Lgm_done
                    ldr r0, [r4]
                    bl kof_gc_mark_transitive
                    add r4, r4, #4
                    b .Lgm_roots
                .Lgm_done:
                    pop {r4-r11, pc}
                .pool

                .section .rodata
                .Lgc_lbl: .asciz "gc "
                .Lgc_sp: .asciz " "
                .Lgc_nl: .asciz "\\n"
                .Lms_alloc: .asciz "allocs: "
                .Lms_free: .asciz "frees: "
                .Lms_live: .asciz "live bytes: "
                .Lgc_oom: .asciz "out of memory"

                .section .text
                """;
    }

    /** Runtime COMPLETO do coletor MCU ARM (core + sweep) — entry point p/ link. */
    public static String all() {
        return runtimeAsm() + NativeMcuArmGcSweep.runtimeAsm();
    }

    /** Linker script do harness ARM: heap entre os símbolos do B-4; pilha fixa. */
    public static String linkerScript(long heapBytes) {
        return """
                ENTRY(Reset_Handler)
                SECTIONS {
                  . = 0x00000000;
                  .vectors : { KEEP(*(.vectors)) }
                  .text : { *(.text*) }
                  .rodata : { *(.rodata*) }
                  .data : { *(.data*) }
                  .bss : { *(.bss*) *(COMMON) }
                  . = ALIGN(16);
                  _kof_heap_start = .;
                  . = . + %d;
                  _kof_heap_end = .;
                  . = ALIGN(16);
                  _stack_top = 0x00080000;
                }
                """.formatted(heapBytes);
    }
}
