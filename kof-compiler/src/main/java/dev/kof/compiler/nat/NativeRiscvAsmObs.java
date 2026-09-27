package dev.kof.compiler.nat;

/**
 * Runtime riscv64/aarch64: kof.observability spans + IDs W3C reais (§272
 * face (c), porta da face (b) x86). Peca separada do B1 (gateway <=500);
 * concatenada na cadeia de NativeRiscvAsm imediatamente apos o B1 — o .s
 * final e byte-identico ao bloco dentro do B1 (regra 3).
 */
public final class NativeRiscvAsmObs {

    private NativeRiscvAsmObs() {}

    static  String RISCV_ASM_OBS = """
            # ── §272 face (c): implementacao REAL riscv64 (aarch64 via tradutor —
            # regra 5). Espelha a face (b) x86_64 (RuntimeObservability2): IDs W3C
            # rand via getrandom (a7=278, padrao provado B25b), epoch/mono via
            # clock_gettime (a7=113, padrao provado kof_time_now), tabela stride-32
            # em .Lrkobs_span_handles, JSON com os MESMOS literais verificados
            # byte-a-byte (.Lrobs_* em B4). Sem stub, sem saida fraca silenciosa (R6).

            # kof_obs_rand_hex(a0=nbytes) -> a0=KofString* (2n hex minusculo)
            # rc<0 -> a0=0 (mesmo contrato do kof_sec_random_hex x86).
            .globl kof_obs_rand_hex
            kof_obs_rand_hex:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                add  s0, a0, zero              # nbytes
                add  a0, a0, a0
                addi a0, a0, 25                # 24 header + 2n hex + NUL (§292: NAO n+25)
                call kof_alloc
                mv   s1, a0
                li   t0, 1
                sw   t0, 0(s1)                 # header identico ao kof_string_from_literal
                sw   zero, 4(s1)
                sd   zero, 8(s1)
                add  t1, s0, s0
                sw   t1, 16(s1)                # byteLen = 2n
                sw   zero, 20(s1)
                addi a0, s1, 24                # buf = dados
                mv   a1, s0
                call kof_plat_random           # (getrandom)
                bltz a0, .Lorh_fail
                addi s2, s0, -1                # i = n-1; HEX EXPANDE IN PLACE — loop
            .Lorh_loop:                        # REVERSO (como o x86): avancar leria
                bltz s2, .Lorh_done            # os proprios hex ja escritos
                add  t3, s1, s2
                addi t3, t3, 24
                lbu  t4, 0(t3)                 # byte bruto
                mv   t5, t4
                srli t4, t4, 4                 # nibble alto
                andi t5, t5, 15                # nibble baixo
                add  t3, s1, s2
                add  t3, t3, s2
                addi t3, t3, 24                # dst = str+24+2i
                la   t6, .Lrkobs_hexchars
                add  t0, t6, t4
                lbu  t0, 0(t0)
                sb   t0, 0(t3)
                add  t1, t6, t5
                lbu  t1, 0(t1)
                sb   t1, 1(t3)
                addi s2, s2, -1
                j    .Lorh_loop
            .Lorh_done:
                add  t0, s1, s0
                add  t0, t0, s0
                addi t0, t0, 24
                sb   zero, 0(t0)               # NUL
                mv   a0, s1
                ld   s2, 16(sp)
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret
            .Lorh_fail:
                li   a0, 0
                ld   s2, 16(sp)
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # kof_obs_epoch_micros() -> a0 (CLOCK_REALTIME us; JVM currentTimeMillis*1000)
            .globl kof_obs_epoch_micros
            kof_obs_epoch_micros:
                addi sp, sp, -32
                sd   ra, 24(sp)
                addi a0, sp, 0                 # timespec no frame proprio
                call kof_plat_time
                ld   t0, 0(sp)                 # sec
                ld   t1, 8(sp)                 # nsec
                li   t2, 1000
                div  t3, t1, t2                # us residuais
                li   t4, 1000000
                mul  a0, t0, t4
                add  a0, a0, t3
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            # kof_obs_mono_nanos() -> a0 (CLOCK_MONOTONIC ns; par do nanoTime JVM)
            .globl kof_obs_mono_nanos
            kof_obs_mono_nanos:
                addi sp, sp, -32
                sd   ra, 24(sp)
                addi a0, sp, 0
                call kof_plat_time_mono
                ld   t0, 0(sp)
                ld   t1, 8(sp)
                li   t2, 1000000000
                mul  a0, t0, t2
                add  a0, a0, t1
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            # kof_obs_json_escape(a0=KofString*) -> a0 (escape " e \\; null -> "")
            .globl kof_obs_json_escape
            kof_obs_json_escape:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                sd   s3, 8(sp)
                mv   s0, a0                    # original
                la   a0, .Lrobs_esc_e
                li   a1, 0
                call kof_string_from_literal
                mv   s1, a0                    # acc
                beqz s0, .Lrje_done
                lw   s2, 16(s0)                # byteLen
                mv   s3, zero                  # i
                j    .Lrje_test
            .Lrje_loop:
                add  t0, s0, s3
                lbu  t1, 24(t0)
                li   t2, 34                    # 0x22 "
                beq  t1, t2, .Lrje_q
                li   t3, 92                    # 0x5C \
                beq  t1, t3, .Lrje_b
                mv   a0, s0
                mv   a1, s3
                addi a2, s3, 1
                call kof_string_substring
                mv   a1, a0
                mv   a0, s1
                call kof_string_concat
                mv   s1, a0
                j    .Lrje_next
            .Lrje_q:
                la   a0, .Lrobs_esc_q
                li   a1, 2
                call kof_string_from_literal
                mv   a1, a0
                mv   a0, s1
                call kof_string_concat
                mv   s1, a0
                j    .Lrje_next
            .Lrje_b:
                la   a0, .Lrobs_esc_b
                li   a1, 2
                call kof_string_from_literal
                mv   a1, a0
                mv   a0, s1
                call kof_string_concat
                mv   s1, a0
            .Lrje_next:
                addi s3, s3, 1
            .Lrje_test:
                blt  s3, s2, .Lrje_loop
            .Lrje_done:
                mv   a0, s1
                ld   s3, 8(sp)
                ld   s2, 16(sp)
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # request/correlation/trace = 16B -> 32 hex; span_id = 8B -> 16 hex (W3C)
            .globl kof_observability_request_id
            kof_observability_request_id:
                li   a0, 16
                j    kof_obs_rand_hex
            .globl kof_observability_correlation_id
            kof_observability_correlation_id:
                li   a0, 16
                j    kof_obs_rand_hex
            .globl kof_observability_trace_id
            kof_observability_trace_id:
                li   a0, 16
                j    kof_obs_rand_hex
            .globl kof_observability_span_id
            kof_observability_span_id:
                li   a0, 8
                j    kof_obs_rand_hex

            # span_start(a0=name) -> handle 48 chars (traceId32+spanId16);
            # registra [handle,name,epochUs,monoNs] na tabela se <32 (paridade x86).
            .globl kof_observability_span_start
            kof_observability_span_start:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)
                sd   s1, 40(sp)
                sd   s2, 32(sp)
                sd   s3, 24(sp)
                sd   s4, 16(sp)
                sd   s5, 8(sp)
                sd   s6, 0(sp)
                mv   s0, a0                    # nome (sobrevive aos calls — s regs)
                li   a0, 16
                call kof_obs_rand_hex
                mv   s1, a0                    # traceId
                li   a0, 8
                call kof_obs_rand_hex
                mv   s2, a0                    # spanId
                mv   a0, s1
                mv   a1, s2
                call kof_string_concat
                mv   s3, a0                    # handle
                la   t0, .Lrkobs_span_len      # so ate aqui (t* vive ate os calls)
                ld   s4, 0(t0)
                li   t1, 32
                bge  s4, t1, .Lrss_done        # tabela cheia: retorna sem registrar
                call kof_obs_epoch_micros
                mv   s5, a0                    # epoch us
                call kof_obs_mono_nanos
                mv   s6, a0                    # mono ns
                li   t2, 32
                mul  t3, s4, t2
                la   t4, .Lrkobs_span_handles
                add  t4, t4, t3
                sd   s3, 0(t4)
                sd   s0, 8(t4)
                sd   s5, 16(t4)
                sd   s6, 24(t4)
                addi s4, s4, 1
                la   t0, .Lrkobs_span_len      # RE-carrega: t0 foi clobbered pelos helpers
                sd   s4, 0(t0)
            .Lrss_done:
                mv   a0, s3
                ld   s6, 0(sp)
                ld   s5, 8(sp)
                ld   s4, 16(sp)
                ld   s3, 24(sp)
                ld   s2, 32(sp)
                ld   s1, 40(sp)
                ld   s0, 48(sp)
                ld   ra, 56(sp)
                addi sp, sp, 64
                ret

            # span_end(a0=handle) -> JSON dos 7 campos na ordem do golden JVM;
            # handle desconhecido/duplicado -> "{}" (paridade JVM, nunca null).
            .globl kof_observability_span_end
            kof_observability_span_end:
                addi sp, sp, -80
                sd   ra, 72(sp)
                sd   s0, 64(sp)
                sd   s1, 56(sp)
                sd   s2, 48(sp)
                sd   s3, 40(sp)
                sd   s4, 32(sp)
                sd   s5, 24(sp)
                sd   s6, 16(sp)
                sd   s7, 8(sp)
                mv   s0, a0                    # handle
                mv   s1, zero                  # i
            .Lrse_search:
                la   t0, .Lrkobs_span_len
                ld   t1, 0(t0)
                bge  s1, t1, .Lrse_missing
                li   t2, 32
                mul  t3, s1, t2
                la   t4, .Lrkobs_span_handles
                add  s2, t4, t3                # entrada candidata
                ld   a0, 0(s2)
                beqz a0, .Lrse_next
                mv   a1, a0
                mv   a0, s0
                call kof_string_equals
                bnez a0, .Lrse_found
            .Lrse_next:
                addi s1, s1, 1
                j    .Lrse_search
            .Lrse_found:
                ld   s3, 16(s2)                # start epoch us
                ld   s4, 8(s2)                 # nome
                ld   s5, 24(s2)                # start mono ns
                call kof_obs_mono_nanos
                sub  a0, a0, s5
                li   t2, 1000
                div  s6, a0, t2                # durationMicros (mono, como JVM)
                sd   zero, 0(s2)               # limpa a entrada
                sd   zero, 8(s2)
                sd   zero, 16(s2)
                sd   zero, 24(s2)
                mv   a0, s0
                mv   a1, zero
                li   a2, 32
                call kof_string_substring
                mv   s7, a0                    # traceId
                mv   a0, s0
                li   a1, 32
                li   a2, 48
                call kof_string_substring
                mv   s2, a0                    # spanId
                call kof_time_now
                li   t2, 1000
                mul  s5, a0, t2                # endMicros (ms*1000, como x86/JVM)
                mv   a0, s4
                call kof_obs_json_escape
                mv   s4, a0                    # nome escapado
                la   a0, .Lrobs_span_1
                li   a1, 12
                call kof_string_from_literal
                mv   s0, a0                    # acc
                mv   a0, s0
                mv   a1, s7
                call kof_string_concat
                mv   s0, a0
                la   a0, .Lrobs_span_2
                li   a1, 12
                call kof_string_from_literal
                mv   a1, a0
                mv   a0, s0
                call kof_string_concat
                mv   s0, a0
                mv   a0, s0
                mv   a1, s2
                call kof_string_concat
                mv   s0, a0
                la   a0, .Lrobs_pe_1
                li   a1, 18
                call kof_string_from_literal
                mv   a1, a0
                mv   a0, s0
                call kof_string_concat
                mv   s0, a0
                la   a0, .Lrobs_pe_2
                li   a1, 10
                call kof_string_from_literal
                mv   a1, a0
                mv   a0, s0
                call kof_string_concat
                mv   s0, a0
                mv   a0, s0
                mv   a1, s4
                call kof_string_concat
                mv   s0, a0
                la   a0, .Lrobs_pe_3
                li   a1, 16
                call kof_string_from_literal
                mv   a1, a0
                mv   a0, s0
                call kof_string_concat
                mv   s0, a0
                mv   a0, s3
                call kof_long_to_string
                mv   a1, a0
                mv   a0, s0
                call kof_string_concat
                mv   s0, a0
                la   a0, .Lrobs_pe_4
                li   a1, 13
                call kof_string_from_literal
                mv   a1, a0
                mv   a0, s0
                call kof_string_concat
                mv   s0, a0
                mv   a0, s5
                call kof_long_to_string
                mv   a1, a0
                mv   a0, s0
                call kof_string_concat
                mv   s0, a0
                la   a0, .Lrobs_pe_5
                li   a1, 18
                call kof_string_from_literal
                mv   a1, a0
                mv   a0, s0
                call kof_string_concat
                mv   s0, a0
                mv   a0, s6
                call kof_long_to_string
                mv   a1, a0
                mv   a0, s0
                call kof_string_concat
                mv   s0, a0
                la   a0, .Lrobs_span_4
                li   a1, 1
                call kof_string_from_literal
                mv   a1, a0
                mv   a0, s0
                call kof_string_concat
                mv   a0, a0
                j    .Lrse_out
            .Lrse_missing:
                la   a0, .Lstr_empty_json
                li   a1, 2
                call kof_string_from_literal
            .Lrse_out:
                ld   s7, 8(sp)
                ld   s6, 16(sp)
                ld   s5, 24(sp)
                ld   s4, 32(sp)
                ld   s3, 40(sp)
                ld   s2, 48(sp)
                ld   s1, 56(sp)
                ld   s0, 64(sp)
                ld   ra, 72(sp)
                addi sp, sp, 80
                ret
""";
}
