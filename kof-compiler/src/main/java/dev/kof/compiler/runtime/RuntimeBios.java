package dev.kof.compiler.runtime;

/**
 * B-5 (D-BAREMETAL-BODIES): corpos da costura {@code kof_plat_*} da face BIOS
 * (x86_64, bare legacy — SeaBIOS/MPC). Mesma ABI SysV dos corpos Linux de
 * {@code RuntimePlat}; o que muda é a fala com a plataforma: no BIOS não há
 * syscalls — o relógio de parede vem do RTC CMOS (portas 0x70/0x71), o
 * monotônico do TSC calibrado pelo PIT, e a saída de diagnóstico sai em ASCII
 * pelo COM1. Famílias ainda sem corpo (I/O, rede, threads) recusam de forma
 * NOMEADA (R6), nunca stub.
 *
 * <p>Extraído de {@code RuntimePlat} para manter o gate ≤500 (o corpo do RTC é
 * grande); a classe+método continua sendo a granularidade de fatia do podador
 * de {@code RuntimeSlices}.
 */
public final class RuntimeBios {

    private RuntimeBios() {}

    /** Recusa NOMEADA (R6) escolhida por perfil: UEFI fala UTF-16 via
     *  {@code OutputString} (RuntimeUefi); BIOS fala ASCII no COM1 — a forma
     *  UTF-16 no BIOS sairia como lixo com NULs intercalados. */
    static void refuse(StringBuilder sb, String symbols) {
        if (dev.kof.compiler.nat.NativeProfile.activeIsBios()) {
            emitRefuse(sb, symbols);
        } else {
            RuntimeUefi.emitUefiRefuse(sb, symbols);
        }
    }

    /** Recusa NOMEADA legível na face BIOS — escreve a mensagem ASCII pela
     *  própria costura de write (COM1) e sai por {@code kof_plat_exit}
     *  ({@code cli;hlt}). Nunca stub silencioso (R6). */
    static void emitRefuse(StringBuilder sb, String symbols) {
        String msg = "KOF BIOS: capacidade nao suportada nesta fatia (B-5): " + symbols + "\\r\\n";
        String tag = symbols.split(", ")[0].replace("kof_plat_", "");
        String lbl = ".Lkof_bios_refuse_" + tag;
        sb.append("        .section .rodata\n")
          .append(lbl).append(": .ascii \"").append(msg).append("\"\n")
          .append(lbl).append("_end:\n")
          .append("        .section .text\n");
        for (String sym : symbols.split(", ")) {
            sb.append("        .globl ").append(sym).append('\n')
              .append("        .type ").append(sym).append(", @function\n")
              .append(sym).append(":\n");
        }
        sb.append("        subq $8, %rsp\n")
          .append("        movl $1, %edi\n")
          .append("        leaq ").append(lbl).append("(%rip), %rsi\n")
          .append("        movq $").append(lbl).append("_end - ").append(lbl).append(", %rdx\n")
          .append("        call kof_plat_write\n")
          .append("        xorl %edi, %edi\n")
          .append("        call kof_plat_exit\n");
    }

    /** Relógio de parede no BIOS a partir do RTC CMOS (portas 0x70/0x71):
     *  aguarda UIP limpar, lê os registradores, converte BCD→binário (bit DM do
     *  status B), trata 12h/24h, resolve o ano (século 0x32, senão infere
     *  19xx/20xx) e devolve {@code ts[0]=tv_sec} (epoch) +
     *  {@code ts[1]=tv_nsec=0} — a ABI que {@code kof_now}→{@code time.now()}
     *  já consome. Mono/sleep têm corpo próprio (TSC/PIT) nesta fatia. */
    static void emitTime(StringBuilder sb) {
        RuntimeCivilEpoch.emit(sb);
        sb.append("""
            .section .text
            .type kof_plat_cmos_read, @function
            kof_plat_cmos_read:
                movw $0x70, %dx
                outb %al, %dx
                movw $0x71, %dx
                inb %dx, %al
                ret
            .type kof_plat_bcd, @function
            kof_plat_bcd:
                testb $0x04, %r10b
                jz .Lkof_bcd_conv
                movzbl %al, %eax
                ret
            .Lkof_bcd_conv:
                movzbl %al, %eax
                movl %eax, %ecx
                andl $0x0F, %eax
                shrl $4, %ecx
                imull $10, %ecx, %ecx
                addl %ecx, %eax
                ret

            .globl kof_plat_time
            .type kof_plat_time, @function
            kof_plat_time:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $8, %rsp
                movq %rdi, %rbx                 # ts
                movb $0x0B, %al                 # status B (DM/24h)
                call kof_plat_cmos_read
                movb %al, %r10b
            .Lkof_rtc_uip:
                movb $0x0A, %al                 # status A: bit7 = UIP
                call kof_plat_cmos_read
                testb $0x80, %al
                jnz .Lkof_rtc_uip
                movb $0x00, %al
                call kof_plat_cmos_read
                call kof_plat_bcd
                movl %eax, %r12d                # sec
                movb $0x02, %al
                call kof_plat_cmos_read
                call kof_plat_bcd
                movl %eax, %r13d                # min
                movb $0x04, %al                 # hour (12h/PM abaixo)
                call kof_plat_cmos_read
                movzbl %al, %r11d
                xorl %edi, %edi
                testb $0x02, %r10b              # bit1: 1 = 24h
                jnz .Lkof_h_pm_ready
                testl $0x80, %r11d              # bit7 = PM em modo 12h
                jz .Lkof_h_pm_ready
                movl $1, %edi
            .Lkof_h_pm_ready:
                andl $0x7F, %r11d
                movl %r11d, %eax
                call kof_plat_bcd
                testb $0x02, %r10b
                jnz .Lkof_h_done
                cmpl $12, %eax
                jne .Lkof_h_not12
                testl %edi, %edi
                jnz .Lkof_h_twelve
                xorl %eax, %eax
                jmp .Lkof_h_done
            .Lkof_h_twelve:
                movl $12, %eax
                jmp .Lkof_h_done
            .Lkof_h_not12:
                testl %edi, %edi
                jz .Lkof_h_done
                addl $12, %eax
            .Lkof_h_done:
                movl %eax, %r14d                # hour
                movb $0x07, %al
                call kof_plat_cmos_read
                call kof_plat_bcd
                movl %eax, %r15d                # day
                movb $0x08, %al
                call kof_plat_cmos_read
                call kof_plat_bcd
                movl %eax, %r8d                 # month
                movb $0x09, %al
                call kof_plat_cmos_read
                call kof_plat_bcd
                movl %eax, %r11d                # yy (2 digitos)
                movb $0x32, %al                 # seculo (pode faltar)
                call kof_plat_cmos_read
                call kof_plat_bcd
                testl %eax, %eax
                jz .Lkof_year_infer
                imull $100, %eax, %eax
                addl %r11d, %eax
                movl %eax, %r9d
                jmp .Lkof_year_ready
            .Lkof_year_infer:
                movl %r11d, %r9d
                cmpl $70, %r9d
                jb .Lkof_year_20
                addl $1900, %r9d
                jmp .Lkof_year_ready
            .Lkof_year_20:
                addl $2000, %r9d
            .Lkof_year_ready:
                # dias = kof_civil_to_epoch(ano, mes, dia)
                movl %r9d, %edi
                movl %r8d, %esi
                movl %r15d, %edx
                call kof_civil_to_epoch
                # epoch_s = dias*86400 + hora*3600 + min*60 + sec
                movq %rax, %rcx
                movq $86400, %rdx
                imulq %rdx, %rcx
                movslq %r14d, %rdx
                imulq $3600, %rdx, %rdx
                addq %rdx, %rcx
                movslq %r13d, %rdx
                imulq $60, %rdx, %rdx
                addq %rdx, %rcx
                movslq %r12d, %rdx
                addq %rdx, %rcx
                movq %rcx, 0(%rbx)              # ts[0] = tv_sec
                movq $0, 8(%rbx)                # ts[1] = tv_nsec
                addq $8, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
        emitSleep(sb);
        emitMono(sb);
    }

    /** B-5 (D-BAREMETAL-BODIES): {@code kof_plat_sleep} no BIOS pelo PIT
     *  (canal 0, 1.193182 MHz). Reprograma o canal 0 para modo 2 (rate
     *  generator, reload 65536) — o contador passa a decrescer 1 por clock e
     *  "dá a volta" a cada 54.9 ms; a leitura é latch (0x43←0x00) + lo/hi
     *  (0x40). O laço acumula ticks com detecção de wrap (cur > prev) e para
     *  no alvo ({@code us*1193182/1000000}). Sleep real, sem libc.
     *
     *  <p>Mono ainda sem corpo → recusa legível (fatia seguinte). */
    static void emitSleep(StringBuilder sb) {
        sb.append("""
            .section .data
            .globl kof_plat_pit_ready
            kof_plat_pit_ready: .byte 0

            .section .text
            .type kof_plat_pit_init, @function
            kof_plat_pit_init:
                cmpb $0, kof_plat_pit_ready(%rip)
                jne .Lkof_pit_init_done
                movb $1, kof_plat_pit_ready(%rip)
                movw $0x43, %dx
                movb $0x34, %al          # canal 0, lo/hi, modo 2, binario
                outb %al, %dx
                movw $0x40, %dx
                xorl %eax, %eax
                outb %al, %dx            # reload lo = 0
                outb %al, %dx            # reload hi = 0 -> 65536
            .Lkof_pit_init_done:
                ret

            .type kof_plat_pit_read, @function
            kof_plat_pit_read:
                movw $0x43, %dx
                xorl %eax, %eax
                outb %al, %dx            # latch canal 0
                movw $0x40, %dx
                inb %dx, %al
                movzbl %al, %ecx
                inb %dx, %al
                movzbl %al, %eax
                shll $8, %eax
                orl %ecx, %eax           # eax = contador (16 bits)
                ret

            .globl kof_plat_sleep
            .type kof_plat_sleep, @function
            kof_plat_sleep:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq (%rdi), %r12        # tv_sec
                movq 8(%rdi), %r13       # tv_nsec
                movq $1000000, %rax
                imulq %rax, %r12         # sec*1e6
                movq %r13, %rax
                xorl %edx, %edx
                movq $1000, %rcx
                divq %rcx                # nsec/1000
                addq %rax, %r12          # us total
                movq $1193182, %rax
                imulq %r12, %rax
                xorl %edx, %edx
                movq $1000000, %rcx
                divq %rcx
                movq %rax, %r13          # alvo (ticks)
                testq %r13, %r13
                jz .Lkof_sleep_done
                call kof_plat_pit_init
                call kof_plat_pit_read
                movl %eax, %r14d         # prev
                xorq %r12, %r12          # total
            .Lkof_sleep_loop:
                call kof_plat_pit_read
                movl %r14d, %ecx
                movl %eax, %r14d         # prev = cur
                movl %ecx, %edx
                subl %eax, %edx          # delta = prev - cur
                cmpl %eax, %ecx
                jae .Lkof_sleep_nowrap
                addl $65536, %edx        # deu a volta
            .Lkof_sleep_nowrap:
                movl %edx, %edx
                addq %rdx, %r12
                cmpq %r13, %r12
                jb .Lkof_sleep_loop
            .Lkof_sleep_done:
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }

    /** B-5 (D-BAREMETAL-BODIES): {@code kof_plat_time_mono} no BIOS.
     *  O PIT de 16 bits dá a volta a cada 54.9 ms — um delta único entre duas
     *  chamadas distantes perderia wraps. A fonte monotônica é o <b>TSC</b>
     *  ({@code rdtsc}, 64 bits, sem wrap na prática); a frequência é
     *  <b>calibrada uma vez</b> contra o PIT (mede-se o delta do TSC sobre
     *  100000 ticks = ~83.8 ms) e o resultado vira {@code ts[0]=tv_sec} +
     *  {@code ts[1]=tv_nsec} — a ABI que {@code kof_obs_mono_nanos} consome.
     *  Nunca stub: o primeiro call calibra, os seguintes usam a base. */
    static void emitMono(StringBuilder sb) {
        sb.append("""
            .section .data
            kof_plat_mono_base: .quad 0
            kof_plat_mono_freq: .quad 0
            kof_plat_mono_ready: .byte 0

            .section .text
            .globl kof_plat_time_mono
            .type kof_plat_time_mono, @function
            kof_plat_time_mono:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx                 # ts
                cmpb $0, kof_plat_mono_ready(%rip)
                jne .Lkof_mono_go
                call kof_plat_pit_init
                call kof_plat_pit_read
                movl %eax, %r14d                # prev
                xorl %r15d, %r15d               # acumulado
                rdtsc
                shlq $32, %rdx
                orq %rdx, %rax
                movq %rax, %r12                 # t0
            .Lkof_mono_cal_loop:
                call kof_plat_pit_read
                movl %r14d, %ecx
                movl %eax, %r14d
                movl %ecx, %edx
                subl %eax, %edx                 # delta = prev - cur
                cmpl %eax, %ecx
                jae .Lkof_mono_cal_nowrap
                addl $65536, %edx
            .Lkof_mono_cal_nowrap:
                movl %edx, %edx
                addq %rdx, %r15
                cmpq $100000, %r15              # ~83.8 ms de PIT
                jb .Lkof_mono_cal_loop
                rdtsc
                shlq $32, %rdx
                orq %rdx, %rax
                subq %r12, %rax                 # delta TSC na janela
                movq %rax, %rcx
                movq $1193182, %rax
                imulq %rcx, %rax                # delta*1193182
                xorl %edx, %edx
                movq $100000, %rcx
                divq %rcx                       # freq = TSC por segundo
                movq %rax, kof_plat_mono_freq(%rip)
                rdtsc
                shlq $32, %rdx
                orq %rdx, %rax
                movq %rax, kof_plat_mono_base(%rip)
                movb $1, kof_plat_mono_ready(%rip)
            .Lkof_mono_go:
                rdtsc
                shlq $32, %rdx
                orq %rdx, %rax
                subq kof_plat_mono_base(%rip), %rax   # delta TSC
                movq $1000000000, %rcx
                mulq %rcx                       # rdx:rax = delta*1e9
                movq kof_plat_mono_freq(%rip), %rcx
                divq %rcx                       # rax = ns totais
                xorl %edx, %edx
                movq $1000000000, %rcx
                divq %rcx                       # rax=sec, rdx=nsec
                movq %rax, 0(%rbx)
                movq %rdx, 8(%rbx)
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }
}
