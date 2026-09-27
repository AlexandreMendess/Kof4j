package dev.kof.compiler.nat;

/**
 * D-FULL-PARITY-050 linha 4 FATIA 2B (26/09): faces Audio do kof.media no
 * cross riscv64 (aarch64 herda via {@code NativeAarch64Translator}). Port
 * byte-fiel de {@code RuntimeMediaWav} x86 (WAV RIFF PCM 16-bit); a infra de
 * handles de audio ({@code .Lmed_aslots} 64x48B) entra aqui. O oraculo e
 * {@code JvmMediaCoreRuntime.kof_media_read_wav}/{@code write_wav}: mesma
 * varredura de chunks LE, "fmt " antes de "data", pad de tamanho impar, ultimo
 * "data" vence, mesmas Strings de erro. Divergencias herdadas do x86
 * (declaradas): io-fail aproxima com o path; WAV malformado que no JVM estoura
 * em AIOOBE/NegativeArraySize vira a quebra honesta do parse. §503: .balign 8
 * em todo global que guarda ponteiro de heap.
 */
public final class NativeRiscvAsmMediaWav {

    private NativeRiscvAsmMediaWav() {}

    static String RISCV_ASM_MEDIA_WAV = """
            .section .data
            .balign 8
            .Lmed_ao_pre:  .ascii "Audio.openWav failed: "
            .Lmed_nw_pre:  .ascii "not a WAV RIFF: "
            .Lmed_cc_pre:  .ascii "unsupported WAV: codec "
            .Lmed_cc_suf:  .ascii " (needs PCM 1)"
            .Lmed_bb_pre:  .ascii "unsupported WAV: needs PCM 16-bit (bits="
            .Lmed_bb_suf:  .ascii ")"
            .Lmed_ia_pre:  .ascii "invalid audio: "
            .Lmed_sw_pre:  .ascii "Audio.saveWav failed: "
            .Lmed_atm:     .ascii "media: too many open handles (max 64)"
            .section .bss
            .balign 8
            .Lmed_aslots:  .zero 3072
            .section .text

            # .Lmed_throw_pis(a0=pre,a1=preLen,a2=int,a3=suf,a4=sufLen)
            .Lmed_throw_pis:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)
                sd   s1, 40(sp)
                sd   s2, 32(sp)
                sd   s3, 24(sp)
                sd   s4, 16(sp)
                mv   s0, a3
                mv   s1, a4
                mv   s2, a2
                call kof_string_from_literal
                mv   s3, a0
                mv   a0, s2
                call kof_int_to_string
                mv   a1, a0
                mv   a0, s3
                call kof_string_concat
                mv   s3, a0
                mv   a0, s0
                mv   a1, s1
                call kof_string_from_literal
                mv   a1, a0
                mv   a0, s3
                call kof_string_concat
                call kof_throw_string
                li   a0, 1
                call kof_plat_exit

            # .Lmed_bad_a(a0=id): lanca "invalid audio: N"
            .Lmed_bad_a:
                mv   a2, a0
                la   a0, .Lmed_ia_pre
                li   a1, 15
                j    .Lmed_throw_pi

            # .Lmed_aslot() -> a0=vaga; slot 48B: in_use@0,id@8,pcm@16,
            # pcmLen@24,rate@32,ch@40
            .Lmed_aslot:
                li   t0, 0
                la   t1, .Lmed_aslots
                li   t2, 64
                li   t3, 48
            .Lmed_as:
                bge  t0, t2, .Lmed_as_full
                mul  t4, t0, t3
                add  t4, t4, t1
                ld   t5, 0(t4)
                beqz t5, .Lmed_as_yes
                addi t0, t0, 1
                j    .Lmed_as
            .Lmed_as_yes:
                mv   a0, t4
                ret
            .Lmed_as_full:
                la   a0, .Lmed_atm
                li   a1, 37
                call kof_string_from_literal
                call kof_throw_string
                li   a0, 1
                call kof_plat_exit

            # .Lmed_find_a(a0=id) -> a0=slot|0
            .Lmed_find_a:
                li   t0, 0
                la   t1, .Lmed_aslots
                li   t2, 64
                li   t3, 48
            .Lmed_fa:
                bge  t0, t2, .Lmed_fa_no
                mul  t4, t0, t3
                add  t4, t4, t1
                ld   t5, 0(t4)
                beqz t5, .Lmed_fa_next
                lw   t6, 8(t4)
                beq  t6, a0, .Lmed_fa_yes
            .Lmed_fa_next:
                addi t0, t0, 1
                j    .Lmed_fa
            .Lmed_fa_yes:
                mv   a0, t4
                ret
            .Lmed_fa_no:
                mv   a0, zero
                ret

            # ── kof_media_audio_open_wav(a0=path) -> a0=id | throw ──
            # locals: 0=pcm, 8=pcmLen, 16=ch, 20=rate, 24=bits, 28=hasData
            .globl kof_media_audio_open_wav
            .type kof_media_audio_open_wav, @function
            kof_media_audio_open_wav:
                addi sp, sp, -128
                sd   ra, 120(sp)
                sd   s0, 112(sp)
                sd   s1, 104(sp)
                sd   s2, 96(sp)
                sd   s3, 88(sp)
                sd   s4, 80(sp)
                sd   s5, 72(sp)
                mv   s0, a0
                call kof_io_read_text
                beqz a0, .Lmed_ao_fail
                mv   s1, a0
                lw   s2, 16(s1)
                addi s3, s1, 24
                li   t0, 12
                blt  s2, t0, .Lmed_ao_notwav
                lbu  t0, 0(s3)
                li   t1, 82
                bne  t0, t1, .Lmed_ao_notwav
                lbu  t0, 1(s3)
                li   t1, 73
                bne  t0, t1, .Lmed_ao_notwav
                lbu  t0, 2(s3)
                li   t1, 70
                bne  t0, t1, .Lmed_ao_notwav
                lbu  t0, 3(s3)
                li   t1, 70
                bne  t0, t1, .Lmed_ao_notwav
                lbu  t0, 8(s3)
                li   t1, 87
                bne  t0, t1, .Lmed_ao_notwav
                lbu  t0, 9(s3)
                li   t1, 65
                bne  t0, t1, .Lmed_ao_notwav
                lbu  t0, 10(s3)
                li   t1, 86
                bne  t0, t1, .Lmed_ao_notwav
                lbu  t0, 11(s3)
                li   t1, 69
                bne  t0, t1, .Lmed_ao_notwav
                sd   zero, 0(sp)
                sd   zero, 8(sp)
                sw   zero, 16(sp)
                sw   zero, 20(sp)
                sw   zero, 24(sp)
                sw   zero, 28(sp)
                li   s4, 12
            .Lmed_ao_loop:
                addi t0, s4, 8
                bgt  t0, s2, .Lmed_ao_end
                add  t2, s3, s4
                lw   t1, 4(t2)
                addi t3, s4, 8
                add  t4, t3, t1
                bgt  t4, s2, .Lmed_ao_end
                lbu  t5, 0(t2)
                li   t6, 102
                beq  t5, t6, .Lmed_ao_try_fmt
                li   t6, 100
                beq  t5, t6, .Lmed_ao_try_data
                j    .Lmed_ao_advance
            .Lmed_ao_try_fmt:
                lbu  t5, 1(t2)
                li   t6, 109
                bne  t5, t6, .Lmed_ao_advance
                lbu  t5, 2(t2)
                li   t6, 116
                bne  t5, t6, .Lmed_ao_advance
                lbu  t5, 3(t2)
                li   t6, 32
                bne  t5, t6, .Lmed_ao_advance
                addi t5, t3, 16
                bgt  t5, s2, .Lmed_ao_end
                add  a1, s3, t3
                lhu  t5, 0(a1)
                li   t6, 1
                bne  t5, t6, .Lmed_ao_codec
                lhu  t5, 2(a1)
                sw   t5, 16(sp)
                lw   t5, 4(a1)
                sw   t5, 20(sp)
                lhu  t5, 14(a1)
                sw   t5, 24(sp)
                j    .Lmed_ao_advance
            .Lmed_ao_try_data:
                lbu  t5, 1(t2)
                li   t6, 97
                bne  t5, t6, .Lmed_ao_advance
                lbu  t5, 2(t2)
                li   t6, 116
                bne  t5, t6, .Lmed_ao_advance
                lbu  t5, 3(t2)
                li   t6, 97
                bne  t5, t6, .Lmed_ao_advance
                blt  t1, zero, .Lmed_ao_end
                li   t5, 1
                sw   t5, 28(sp)
                sd   t1, 8(sp)
                beqz t1, .Lmed_ao_advance
                mv   a0, t1
                addi a0, a0, 1
                call kof_alloc
                sd   a0, 0(sp)
                mv   t4, a0
                add  t2, s3, s4
                lw   t1, 4(t2)
                addi t3, s4, 8
                add  a1, s3, t3
                mv   a0, t4
                mv   a2, t1
                call kof_memcpy
            .Lmed_ao_advance:
                add  t2, s3, s4
                lw   t1, 4(t2)
                addi t0, s4, 8
                add  s4, t0, t1
                andi t1, t1, 1
                beqz t1, .Lmed_ao_loop
                addi s4, s4, 1
                j    .Lmed_ao_loop
            .Lmed_ao_end:
                lw   t0, 28(sp)
                beqz t0, .Lmed_ao_bits
                lw   t0, 20(sp)
                blez t0, .Lmed_ao_bits
                lw   t0, 24(sp)
                li   t1, 16
                bne  t0, t1, .Lmed_ao_bits
                call .Lmed_next_seq
                mv   s5, a0
                call .Lmed_aslot
                li   t0, 1
                sd   t0, 0(a0)
                sw   s5, 8(a0)
                ld   t1, 0(sp)
                sd   t1, 16(a0)
                ld   t1, 8(sp)
                sd   t1, 24(a0)
                lw   t1, 20(sp)
                sw   t1, 32(a0)
                lw   t1, 16(sp)
                sw   t1, 40(a0)
                mv   a0, s5
                ld   ra, 120(sp)
                ld   s0, 112(sp)
                ld   s1, 104(sp)
                ld   s2, 96(sp)
                ld   s3, 88(sp)
                ld   s4, 80(sp)
                ld   s5, 72(sp)
                addi sp, sp, 128
                ret
            .Lmed_ao_codec:
                mv   a2, t5
                la   a0, .Lmed_cc_pre
                li   a1, 23
                la   a3, .Lmed_cc_suf
                li   a4, 14
                j    .Lmed_ao_err_pis
            .Lmed_ao_bits:
                lw   a2, 24(sp)
                la   a0, .Lmed_bb_pre
                li   a1, 40
                la   a3, .Lmed_bb_suf
                li   a4, 1
            .Lmed_ao_err_pis:
                ld   ra, 120(sp)
                ld   s0, 112(sp)
                ld   s1, 104(sp)
                ld   s2, 96(sp)
                ld   s3, 88(sp)
                ld   s4, 80(sp)
                ld   s5, 72(sp)
                addi sp, sp, 128
                j    .Lmed_throw_pis
            .Lmed_ao_notwav:
                la   a0, .Lmed_nw_pre
                li   a1, 16
                mv   a2, s0
                j    .Lmed_ao_err_ps
            .Lmed_ao_fail:
                la   a0, .Lmed_ao_pre
                li   a1, 22
                mv   a2, s0
            .Lmed_ao_err_ps:
                ld   ra, 120(sp)
                ld   s0, 112(sp)
                ld   s1, 104(sp)
                ld   s2, 96(sp)
                ld   s3, 88(sp)
                ld   s4, 80(sp)
                ld   s5, 72(sp)
                addi sp, sp, 128
                j    .Lmed_throw_ps

            # ── kof_media_audio_sample_rate(a0=id) -> int ──────────
            .globl kof_media_audio_sample_rate
            .type kof_media_audio_sample_rate, @function
            kof_media_audio_sample_rate:
                addi sp, sp, -16
                sd   ra, 8(sp)
                sd   s0, 0(sp)
                mv   s0, a0
                call .Lmed_find_a
                beqz a0, .Lmed_sr_bad
                lw   a0, 32(a0)
                ld   s0, 0(sp)
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret
            .Lmed_sr_bad:
                mv   a0, s0
                call .Lmed_bad_a

            # ── kof_media_audio_duration_ms(a0=id) -> int ──────────
            .globl kof_media_audio_duration_ms
            .type kof_media_audio_duration_ms, @function
            kof_media_audio_duration_ms:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                sd   s2, 0(sp)
                mv   s0, a0
                call .Lmed_find_a
                beqz a0, .Lmed_dm_bad
                mv   s1, a0
                lw   t0, 32(s1)
                blez t0, .Lmed_dm_zero
                lw   t1, 40(s1)
                blez t1, .Lmed_dm_zero
                ld   t2, 24(s1)
                slli t3, t1, 1
                divu t2, t2, t3
                li   t4, 1000
                mul  t2, t2, t4
                div  t2, t2, t0
                mv   a0, t2
                ld   s2, 0(sp)
                ld   s1, 8(sp)
                ld   s0, 16(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret
            .Lmed_dm_zero:
                li   a0, 0
                ld   s2, 0(sp)
                ld   s1, 8(sp)
                ld   s0, 16(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret
            .Lmed_dm_bad:
                mv   a0, s0
                call .Lmed_bad_a

            # ── kof_media_audio_pcm_bytes(a0=id) -> Int[] ──────────
            .globl kof_media_audio_pcm_bytes
            .type kof_media_audio_pcm_bytes, @function
            kof_media_audio_pcm_bytes:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                sd   s2, 0(sp)
                mv   s0, a0
                call .Lmed_find_a
                beqz a0, .Lmed_pb_bad
                mv   s1, a0
                lw   s2, 24(s1)
                mv   a0, s2
                li   a1, 4
                call kof_array_alloc
                mv   t3, a0
                ld   t1, 16(s1)
                li   t0, 0
            .Lmed_pb_loop:
                bge  t0, s2, .Lmed_pb_done
                add  t2, t1, t0
                lb   t4, 0(t2)
                slli t5, t0, 2
                add  t5, t3, t5
                sw   t4, 24(t5)
                addi t0, t0, 1
                j    .Lmed_pb_loop
            .Lmed_pb_done:
                mv   a0, t3
                ld   s2, 0(sp)
                ld   s1, 8(sp)
                ld   s0, 16(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret
            .Lmed_pb_bad:
                mv   a0, s0
                call .Lmed_bad_a

            # ── kof_media_audio_save_wav(a0=id,a1=path) -> 1 | throw ─
            .globl kof_media_audio_save_wav
            .type kof_media_audio_save_wav, @function
            kof_media_audio_save_wav:
                addi sp, sp, -112
                sd   ra, 104(sp)
                sd   s1, 96(sp)
                sd   s2, 88(sp)
                sd   s3, 80(sp)
                sd   s4, 72(sp)
                sd   s5, 64(sp)
                sd   s6, 56(sp)
                sd   s7, 48(sp)
                sd   s8, 40(sp)
                sd   s9, 32(sp)
                sd   s10, 24(sp)
                mv   s5, a1
                mv   s0, a0
                call .Lmed_find_a
                beqz a0, .Lmed_sw_bad
                mv   s1, a0
                lw   s2, 16(s5)
                addi s3, s5, 24
                mv   t0, s2
            .Lmed_sw_scan:
                blez t0, .Lmed_sw_write
                addi t0, t0, -1
                add  t1, s3, t0
                lbu  t2, 0(t1)
                li   t3, 47
                bne  t2, t3, .Lmed_sw_scan
                beqz t0, .Lmed_sw_slash0
                mv   s4, t0
                j    .Lmed_sw_slice
            .Lmed_sw_slash0:
                li   s4, 1
            .Lmed_sw_slice:
                mv   a0, s5
                li   a1, 0
                mv   a2, s4
                call .Lmed_str_slice
                call kof_io_dir_create_dirs
                beqz a0, .Lmed_sw_mkdir_fail
            .Lmed_sw_write:
                lw   s6, 40(s1)
                lw   s7, 32(s1)
                lw   s8, 24(s1)
                mv   a0, s8
                addi a0, a0, 69
                call kof_alloc
                mv   s9, a0
                li   t1, 1
                sw   t1, 0(s9)
                sw   zero, 4(s9)
                sd   zero, 8(s9)
                addi t1, s8, 44
                sw   t1, 16(s9)
                sw   zero, 20(s9)
                addi s10, s9, 24
                li   t1, 82
                sb   t1, 0(s10)
                li   t1, 73
                sb   t1, 1(s10)
                li   t1, 70
                sb   t1, 2(s10)
                li   t1, 70
                sb   t1, 3(s10)
                addi t1, s8, 36
                sw   t1, 4(s10)
                li   t1, 87
                sb   t1, 8(s10)
                li   t1, 65
                sb   t1, 9(s10)
                li   t1, 86
                sb   t1, 10(s10)
                li   t1, 69
                sb   t1, 11(s10)
                li   t1, 102
                sb   t1, 12(s10)
                li   t1, 109
                sb   t1, 13(s10)
                li   t1, 116
                sb   t1, 14(s10)
                li   t1, 32
                sb   t1, 15(s10)
                li   t1, 16
                sw   t1, 16(s10)
                li   t1, 1
                sh   t1, 20(s10)
                sh   s6, 22(s10)
                sw   s7, 24(s10)
                mul  t1, s6, s7
                slli t1, t1, 1
                sw   t1, 28(s10)
                slli t1, s6, 1
                sh   t1, 32(s10)
                li   t1, 16
                sh   t1, 34(s10)
                li   t1, 100
                sb   t1, 36(s10)
                li   t1, 97
                sb   t1, 37(s10)
                li   t1, 116
                sb   t1, 38(s10)
                li   t1, 97
                sb   t1, 39(s10)
                sw   s8, 40(s10)
                ld   t1, 16(s1)
                li   t2, 0
            .Lmed_sw_copy:
                bge  t2, s8, .Lmed_sw_copy_done
                add  t3, t1, t2
                lbu  t4, 0(t3)
                add  t5, s10, t2
                sb   t4, 44(t5)
                addi t2, t2, 1
                j    .Lmed_sw_copy
            .Lmed_sw_copy_done:
                mv   a0, s5
                mv   a1, s9
                call kof_io_write_text
                beqz a0, .Lmed_sw_fail
                li   a0, 1
                ld   s10, 24(sp)
                ld   s9, 32(sp)
                ld   s8, 40(sp)
                ld   s7, 48(sp)
                ld   s6, 56(sp)
                ld   s5, 64(sp)
                ld   s4, 72(sp)
                ld   s3, 80(sp)
                ld   s2, 88(sp)
                ld   s1, 96(sp)
                ld   ra, 104(sp)
                addi sp, sp, 112
                ret
            .Lmed_sw_fail:
                la   a0, .Lmed_sw_pre
                li   a1, 22
                mv   a2, s5
                j    .Lmed_sw_err
            .Lmed_sw_mkdir_fail:
                la   a0, .Lmed_sw_pre
                li   a1, 22
                mv   a2, s5
            .Lmed_sw_err:
                ld   s10, 24(sp)
                ld   s9, 32(sp)
                ld   s8, 40(sp)
                ld   s7, 48(sp)
                ld   s6, 56(sp)
                ld   s5, 64(sp)
                ld   s4, 72(sp)
                ld   s3, 80(sp)
                ld   s2, 88(sp)
                ld   s1, 96(sp)
                ld   ra, 104(sp)
                addi sp, sp, 112
                j    .Lmed_throw_ps
            .Lmed_sw_bad:
                mv   a0, s0
                call .Lmed_bad_a
            """;
}
