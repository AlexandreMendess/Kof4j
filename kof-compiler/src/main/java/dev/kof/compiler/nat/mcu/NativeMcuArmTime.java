package dev.kof.compiler.nat.mcu;

import java.nio.charset.StandardCharsets;

/**
 * B-4 follow-up (a) (PLAN-BAREMETAL-BOOT B-4/B-5 + {@code D-BAREMETAL-MCU-GC}
 * item 2): os corpos {@code kof_plat_time*} do MCU Cortex-M3 (Thumb-2) —
 * espelho do {@link NativeMcuTimeRiscv32}. Num MCU sem RTC:
 *
 * <ul>
 *   <li>{@code kof_plat_time} (wall, {@code time.now()}) é uma <b>recusa
 *       NOMEADA</b> (R6/R7): escreve o diagnóstico e sai(1) — <b>nunca</b> uma
 *       epoch falsa;</li>
 *   <li>{@code kof_plat_time_mono} (monotônico) é o <b>contador acumulado</b>
 *       do SysTick do Cortex-M3 ({@code boot = 0}). O SysTick decresce e recarrega
 *       a cada 2^24 ticks; ler a posição bruta ({@code 0xFFFFFF - SYST_CVR})
 *       <b>volta a zero</b> no wrap — não seria monotônica. Então o corpo mantém
 *       {@code last}/{@code acc}: a cada leitura soma o delta
 *       {@code (last - CVR) mod 2^24} a um acumulador de 32 bits
 *       ({@code ts[4]}), que só dá a volta em 2^32. É um <b>CONTADOR</b>, não ns
 *       calibrados — a decisão sanciona exatamente isso; wrap em 2^32 ticks,
 *       documentado.</li>
 * </ul>
 *
 * <p>{@code kof_plat_sleep} faz busy-wait nesse contador (sem interrupções
 * configuradas neste slice). É o mesmo formato de timespec de 32 bits consumido
 * pelo {@code kof_obs_*} do cross ({@code ts[0]=sec}, {@code ts[4]=nsec}).
 */
public final class NativeMcuArmTime {

    private NativeMcuArmTime() {}

    /** Mensagem da recusa do wall clock (sem newline; o .byte 10 fecha a linha). */
    static final String WALL_REFUSAL = "NATIVE002: time.now() unavailable on MCU (no RTC)";

    public static String runtimeAsm() {
        int len = WALL_REFUSAL.getBytes(StandardCharsets.UTF_8).length + 1;
        return """
                .syntax unified
                .thumb
                .cpu cortex-m3
                .section .data
                .align 2
                .Lmono_last: .word 0
                .Lmono_acc:  .word 0
                .Lmono_init: .word 0
                .section .text
                .thumb

                # kof_plat_time(ts=r0): recusa NOMEADA do wall — MCU sem RTC.
                .thumb_func
                .globl kof_plat_time
                kof_plat_time:
                    push {r4, lr}
                    ldr r0, =.Lmcu_wall_refuse
                    ldr r1, =%d
                    bl kof_plat_write
                    movs r0, #1
                    bl kof_plat_exit
                .Lmcu_time_halt:
                    b .Lmcu_time_halt
                .pool

                # .Lmono_read() -> r0 = contador acumulado (deltas do SysTick com
                # wrap de 2^24 tratado; acumula em 32 bits, boot=0).
                .thumb_func
                .Lmono_read:
                    ldr r1, =0xE000E010
                    ldr r2, [r1]
                    movs r3, #1
                    ands r3, r2
                    bne .Lmr_on
                    ldr r3, =0xE000E014
                    ldr r2, =0x00FFFFFF
                    str r2, [r3]
                    ldr r3, =0xE000E018
                    movs r2, #0
                    str r2, [r3]
                    movs r2, #5
                    str r2, [r1]
                .Lmr_on:
                    ldr r3, =0xE000E018
                    ldr r0, [r3]
                    ldr r1, =.Lmono_init
                    ldr r2, [r1]
                    cbnz r2, .Lmr_acc
                    movs r2, #1
                    str r2, [r1]
                    ldr r1, =.Lmono_last
                    str r0, [r1]
                    ldr r1, =.Lmono_acc
                    ldr r0, [r1]
                    bx lr
                .Lmr_acc:
                    ldr r2, =.Lmono_last
                    ldr r3, [r2]
                    subs r3, r3, r0
                    ldr r12, =0x00FFFFFF
                    ands r3, r12
                    str r0, [r2]
                    ldr r1, =.Lmono_acc
                    ldr r0, [r1]
                    add r0, r0, r3
                    str r0, [r1]
                    bx lr
                .pool

                # kof_plat_time_mono(ts=r0): contador acumulado em ts[4]
                # (tv_sec=0). -> r0=0.
                .thumb_func
                .globl kof_plat_time_mono
                kof_plat_time_mono:
                    push {r4, lr}
                    mov r4, r0
                    bl .Lmono_read
                    str r0, [r4, #4]
                    movs r0, #0
                    str r0, [r4]
                    movs r0, #0
                    pop {r4, pc}
                .pool

                # kof_plat_sleep(req=r0, rem=r1): busy-wait no contador. -> r0=0.
                .thumb_func
                .globl kof_plat_sleep
                kof_plat_sleep:
                    push {r4, r5, r6, lr}
                    cmp r0, #0
                    beq .Lsl_done
                    ldr r5, [r0, #4]
                    cmp r5, #0
                    beq .Lsl_done
                    bl .Lmono_read
                    mov r4, r0
                .Lsl_spin:
                    bl .Lmono_read
                    subs r6, r0, r4
                    cmp r6, r5
                    blo .Lsl_spin
                .Lsl_done:
                    movs r0, #0
                    pop {r4, r5, r6, pc}
                .pool

                .section .rodata
                .Lmcu_wall_refuse:
                    .ascii "%s"
                    .byte 10
                """.formatted(len, WALL_REFUSAL);
    }
}
