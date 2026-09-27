package dev.kof.compiler.nat.mcu;

/**
 * B-4 follow-up (a): SWEEP + COLLECT do coletor MCU no <b>Cortex-M3 (Thumb-2)</b>
 * — espelho do {@link NativeMcuGcRiscv32Sweep} (RV32I), sobre o mesmo header de
 * 16 B (flags @12, gc_next @8, free_next @4) e o mark do {@link NativeMcuArmGc}.
 *
 * <p>O sweep percorre a gc-list: mark==1 → limpa o bit (sobreviveu); mark==0 e
 * !free → insere na free-list (bit1) e conta (frees/free_bytes). O
 * {@code kof_alloc} (core) chama {@code kof_gc_collect_now} no OOM e re-tenta
 * UMA vez — é o gancho que recicla o heap num programa longo.
 *
 * <p><b>Sem lock</b> (diferença explícita do cross, igual ao riscv32): o MCU é
 * monothread por construção — {@code spawn}/threads são não-metas do §6 do plano
 * ({@code PLAN-BAREMETAL-BOOT}), então alloc e sweep nunca correm concorrentes.
 *
 * <p>Separado do core só pelo gate ≤500 linhas/arquivo; o nome diz o que o
 * arquivo contém (o sweep), não a posição.
 */
public final class NativeMcuArmGcSweep {

    private NativeMcuArmGcSweep() {}

    public static String runtimeAsm() {
        return """
                .syntax unified
                .thumb
                .cpu cortex-m3

                .section .data
                .align 2
                .Lkof_gc_tick: .word 0
                .section .text
                .thumb

                # kof_gc_sweep(): percorre a gc-list e recupera mortos (G-4).
                # mark bit0==1 -> sobreviveu: limpa o bit.
                # mark bit0==0 e bit1==0 -> morto: free-list (bit1) + contagem.
                .thumb_func
                .globl kof_gc_sweep
                kof_gc_sweep:
                    push {r4, r5, lr}
                    ldr r4, =.Lkof_gc_head
                    ldr r4, [r4]
                .Lg4sw_loop:
                    cbz r4, .Lg4sw_done
                    ldrb r1, [r4, #12]
                    movs r2, #1
                    ands r2, r1
                    beq .Lg4sw_free
                    bic r1, r1, #1
                    strb r1, [r4, #12]
                    b .Lg4sw_next
                .Lg4sw_free:
                    movs r2, #2
                    ands r2, r1
                    bne .Lg4sw_next
                    ldr r3, [r4]
                    ldr r5, =.Lkof_free_head
                    ldr r2, [r5]
                    str r2, [r4, #4]
                    str r4, [r5]
                    orr r1, r1, #2
                    strb r1, [r4, #12]
                    ldr r5, =.Lkof_free_count
                    ldr r2, [r5]
                    add r2, r2, #1
                    str r2, [r5]
                    ldr r5, =.Lkof_free_bytes
                    ldr r2, [r5]
                    add r2, r2, r3
                    str r2, [r5]
                .Lg4sw_next:
                    ldr r4, [r4, #8]
                    b .Lg4sw_loop
                .Lg4sw_done:
                    pop {r4, r5, pc}
                .pool

                # kof_gc_collect_now(): mark + sweep incondicional (sem tick).
                # Derrama r4-r11 para que ponteiros vivos em registrador do caller
                # apareçam na varredura de pilha do mark.
                .thumb_func
                .globl kof_gc_collect_now
                kof_gc_collect_now:
                    push {r4-r11, lr}
                    bl kof_gc_mark
                    bl kof_gc_sweep
                    pop {r4-r11, pc}
                .pool

                # kof_gc_collect(): versão tick-guarded (tick & 4095 == 0), mesma
                # ordem do cross/riscv32 (checa ANTES de incrementar). Gancho de
                # paridade; o MCU coleta no OOM do kof_alloc.
                .thumb_func
                .globl kof_gc_collect
                kof_gc_collect:
                    push {r4, lr}
                    ldr r4, =.Lkof_gc_tick
                    ldr r1, [r4]
                    ldr r2, =4095
                    ands r2, r1
                    cbnz r2, .Lg4cc_skip
                    bl kof_gc_collect_now
                .Lg4cc_skip:
                    ldr r1, [r4]
                    add r1, r1, #1
                    str r1, [r4]
                    pop {r4, pc}
                .pool

                # kof_gc_tick() -> r0 = tick atual (observabilidade, paridade).
                .thumb_func
                .globl kof_gc_tick
                kof_gc_tick:
                    ldr r1, =.Lkof_gc_tick
                    ldr r0, [r1]
                    bx lr
                .pool
                """;
    }
}
