package dev.kof.compiler.nat.mcu;

/**
 * B4-GC-3 (PLAN-BAREMETAL-BOOT B-4 + {@code D-BAREMETAL-MCU-GC}): SWEEP + COLLECT
 * do coletor MCU — o degrau que de fato RECUPERA. Port do
 * {@code NativeRiscvAsmRtB44} (riscv64) para RV32I puro, sobre o header de 16 B
 * (flags @12, gc_next @8, free_next @4) e o mark do B4-GC-2.
 *
 * <p>O sweep percorre a gc-list: mark==1 → limpa o bit (sobreviveu); mark==0 e
 * !free → insere na free-list (bit1) e conta (frees/free_bytes). O
 * {@code kof_alloc} (core) chama {@code kof_gc_collect_now} no OOM e re-tenta
 * UMA vez — é o gancho que recicla o heap num programa longo.
 *
 * <p><b>Sem lock</b> (diferença explícita do cross): o MCU é monothread por
 * construção — {@code spawn}/threads são não-metas do §6 do plano
 * (`PLAN-BAREMETAL-BOOT`), então alloc e sweep nunca correm concorrentes. O
 * cross usa {@code amoswap.w} porque lá o {@code spawn} existe.
 *
 * <p>Separado do core só pelo gate ≤500 linhas/arquivo (o core já tem 480): o
 * nome diz o que o arquivo contém (o sweep), não a posição.
 */
public final class NativeMcuGcRiscv32Sweep {

    private NativeMcuGcRiscv32Sweep() {}

    public static String runtimeAsm() {
        return """
                .section .data
                .align 2
                .Lkof_gc_tick: .word 0
                .section .text

                # kof_gc_sweep(): percorre a gc-list e recupera mortos (G-4).
                # mark bit0==1 -> sobreviveu: limpa o bit.
                # mark bit0==0 e bit1==0 -> morto: free-list (bit1) + contagem.
                .globl kof_gc_sweep
                kof_gc_sweep:
                    addi sp, sp, -16
                    sw   ra, 12(sp)
                    sw   s0, 8(sp)
                    la   s0, .Lkof_gc_head
                    lw   s0, 0(s0)
                .Lg4sw_loop:
                    beqz s0, .Lg4sw_done
                    lbu  t0, 12(s0)
                    andi t1, t0, 1
                    beqz t1, .Lg4sw_free
                    andi t0, t0, -2          # limpa bit0 (mark)
                    sb   t0, 12(s0)
                    j    .Lg4sw_next
                .Lg4sw_free:
                    andi t1, t0, 2
                    bnez t1, .Lg4sw_next       # já está na free-list
                    lw   t2, 0(s0)           # size total
                    la   t3, .Lkof_free_head
                    lw   t4, 0(t3)
                    sw   t4, 4(s0)           # free_next = head antiga
                    sw   s0, 0(t3)           # head = bloco morto
                    ori  t0, t0, 2           # bit1: na free-list
                    sb   t0, 12(s0)
                    la   t3, .Lkof_free_count
                    lw   t4, 0(t3)
                    addi t4, t4, 1
                    sw   t4, 0(t3)
                    la   t3, .Lkof_free_bytes
                    lw   t4, 0(t3)
                    add  t4, t4, t2
                    sw   t4, 0(t3)
                .Lg4sw_next:
                    lw   s0, 8(s0)           # gc_next
                    j    .Lg4sw_loop
                .Lg4sw_done:
                    lw   s0, 8(sp)
                    lw   ra, 12(sp)
                    addi sp, sp, 16
                    ret

                # kof_gc_collect_now(): mark + sweep incondicional (sem tick).
                # Derrama s0-s11 para que ponteiros vivos em registrador do
                # caller apareçam na varredura de pilha do mark.
                .globl kof_gc_collect_now
                kof_gc_collect_now:
                    addi sp, sp, -64
                    sw   ra, 60(sp)
                    sw   s0, 56(sp)
                    sw   s1, 52(sp)
                    sw   s2, 48(sp)
                    sw   s3, 44(sp)
                    sw   s4, 40(sp)
                    sw   s5, 36(sp)
                    sw   s6, 32(sp)
                    sw   s7, 28(sp)
                    sw   s8, 24(sp)
                    sw   s9, 20(sp)
                    sw   s10, 16(sp)
                    sw   s11, 12(sp)
                    call kof_gc_mark
                    call kof_gc_sweep
                    lw   s11, 12(sp)
                    lw   s10, 16(sp)
                    lw   s9, 20(sp)
                    lw   s8, 24(sp)
                    lw   s7, 28(sp)
                    lw   s6, 32(sp)
                    lw   s5, 36(sp)
                    lw   s4, 40(sp)
                    lw   s3, 44(sp)
                    lw   s2, 48(sp)
                    lw   s1, 52(sp)
                    lw   s0, 56(sp)
                    lw   ra, 60(sp)
                    addi sp, sp, 64
                    ret

                # kof_gc_collect(): versão tick-guarded (tick & 4095 == 0),
                # mesma ordem do cross (checa ANTES de incrementar; tick=0
                # coleta). Gancho de paridade; o MCU coleta no OOM do kof_alloc.
                .globl kof_gc_collect
                kof_gc_collect:
                    la   t0, .Lkof_gc_tick
                    lw   t1, 0(t0)
                    li   t2, 4095
                    and  t2, t1, t2
                    bnez t2, .Lg4cc_skip
                    addi sp, sp, -16
                    sw   ra, 12(sp)
                    call kof_gc_collect_now
                    lw   ra, 12(sp)
                    addi sp, sp, 16
                .Lg4cc_skip:
                    la   t0, .Lkof_gc_tick
                    lw   t1, 0(t0)
                    addi t1, t1, 1
                    sw   t1, 0(t0)
                .Lg4cc_ret:
                    ret

                # kof_gc_tick() -> a0 = tick atual (observabilidade, paridade).
                .globl kof_gc_tick
                kof_gc_tick:
                    la   t0, .Lkof_gc_tick
                    lw   a0, 0(t0)
                    ret
                """;
    }
}
