package dev.kof.compiler.nat.mcu;

import java.nio.charset.StandardCharsets;

/**
 * B4-TIME (PLAN-BAREMETAL-BOOT B-4/B-5 + {@code D-BAREMETAL-MCU-GC} item 2): os
 * corpos {@code kof_plat_time*} do MCU RV32I. Num MCU sem RTC:
 *
 * <ul>
 *   <li>{@code kof_plat_time} (wall, {@code time.now()}) é uma <b>recusa
 *       NOMEADA</b> (R6/R7): escreve o diagnóstico e sai(1) — <b>nunca</b> uma
 *       epoch falsa;</li>
 *   <li>{@code kof_plat_time_mono} (monotônico) vem do contador {@code time}
 *       do RISC-V ({@code boot = 0}): {@code ts[0]=tv_sec=0},
 *       {@code ts[4]=tv_nsec=contador}. É um <b>CONTADOR</b>, não ns calibrados
 *       — a decisão sanciona exatamente isso; wrap em ~2^32 ticks,
 *       documentado.</li>
 * </ul>
 *
 * <p>{@code kof_plat_sleep} faz busy-wait nesse contador (sem interrupções
 * configuradas neste slice); {@code req[4]} são os "nsec" pedidos na mesma
 * convenção. É o mesmo formato de timespec de 32 bits consumido pelo
 * {@code kof_obs_*} do cross ({@code ts[0]=sec}, {@code ts[4]=nsec}).
 */
public final class NativeMcuTimeRiscv32 {

    private NativeMcuTimeRiscv32() {}

    /** Mensagem da recusa do wall clock (sem newline; o .byte 10 fecha a linha). */
    static final String WALL_REFUSAL = "NATIVE002: time.now() unavailable on MCU (no RTC)";

    public static String runtimeAsm() {
        int len = WALL_REFUSAL.getBytes(StandardCharsets.UTF_8).length + 1;
        return """
                .section .text
                # kof_plat_time(ts=a0): recusa NOMEADA do wall — MCU sem RTC.
                .globl kof_plat_time
                kof_plat_time:
                    addi sp, sp, -16
                    sw   ra, 12(sp)
                    la   a0, .Lmcu_wall_refuse
                    li   a1, %d
                    call kof_plat_write
                    li   a0, 1
                    call kof_plat_exit
                .Lmcu_time_halt:
                    j    .Lmcu_time_halt

                # kof_plat_time_mono(ts=a0): contador `time` (boot=0). -> a0=0.
                .globl kof_plat_time_mono
                kof_plat_time_mono:
                    csrr t0, time
                    sw   zero, 0(a0)         # tv_sec = 0
                    sw   t0, 4(a0)           # tv_nsec = contador
                    li   a0, 0
                    ret

                # kof_plat_sleep(req=a0, rem=a1): busy-wait no contador. -> a0=0.
                .globl kof_plat_sleep
                kof_plat_sleep:
                    addi sp, sp, -16
                    sw   ra, 12(sp)
                    sw   s0, 8(sp)
                    beqz a0, .Lmcu_sl_done
                    lw   s0, 4(a0)           # "nsec" pedidos
                    csrr t0, time
                    add  t0, t0, s0          # alvo
                .Lmcu_sl_spin:
                    csrr t1, time
                    bltu t1, t0, .Lmcu_sl_spin
                .Lmcu_sl_done:
                    lw   s0, 8(sp)
                    lw   ra, 12(sp)
                    addi sp, sp, 16
                    li   a0, 0
                    ret

                .section .rodata
                .Lmcu_wall_refuse:
                    .ascii "%s"
                    .byte 10
                """.formatted(len, WALL_REFUSAL);
    }
}
