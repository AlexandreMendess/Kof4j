package dev.kof.compiler.runtime;
import dev.kof.compiler.NativeRuntime;

/**
 * Emissao do ASM de spans/IDs W3C do runtime nativo x86_64 (§272 faces b/c).
 * Dominio isolado do RuntimeObservability2 (metricas) -- split preserva
 * semantica (regra 3): o texto concatenado em NativeRuntime e byte-identico.
 */
public final class RuntimeObservabilitySpans {

    private RuntimeObservabilitySpans() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            # kof_observability_request_id() -> String
            .globl kof_observability_request_id
            .type kof_observability_request_id, @function
            kof_observability_request_id:
                movq $16, %rdi
                jmp kof_sec_random_hex

            # kof_observability_correlation_id() -> String
            .globl kof_observability_correlation_id
            .type kof_observability_correlation_id, @function
            kof_observability_correlation_id:
                movq $16, %rdi
                jmp kof_sec_random_hex

            # kof_observability_trace_id() -> String (16 bytes = 32 hex, W3C)
            .globl kof_observability_trace_id
            .type kof_observability_trace_id, @function
            kof_observability_trace_id:
                movq $16, %rdi
                jmp kof_sec_random_hex

            # kof_observability_span_id() -> String (8 bytes = 16 hex, W3C)
            .globl kof_observability_span_id
            .type kof_observability_span_id, @function
            kof_observability_span_id:
                movq $8, %rdi
                jmp kof_sec_random_hex

            # mono ns (CLOCK_MONOTONIC) — duracao nao-linear como o nanoTime JVM
            .globl kof_obs_mono_nanos
            .type kof_obs_mono_nanos, @function
            kof_obs_mono_nanos:
                subq $40, %rsp
                leaq 16(%rsp), %rcx
                andq $-16, %rcx            # buffer 16-alinhado (relativo ao rsp REAL, nao ao do chamador)
                movq $0, 0(%rcx)
                movq $0, 8(%rcx)
                movq %rcx, %r8               # r8 = buf (syscall clobbera rcx/r11)
                movq %rcx, %rdi              # struct timespec*
                call kof_plat_time_mono
                movq 0(%r8), %rax
                imulq $1000000000, %rax
                addq 8(%r8), %rax
                addq $40, %rsp
                ret

            # epoch us (CLOCK_REALTIME) — espelha currentTimeMillis()*1000 do JVM
            .globl kof_obs_epoch_micros
            .type kof_obs_epoch_micros, @function
            kof_obs_epoch_micros:
                subq $40, %rsp
                leaq 16(%rsp), %rcx
                andq $-16, %rcx            # buffer 16-alinhado (relativo ao rsp REAL)
                movq $0, 0(%rcx)
                movq $0, 8(%rcx)
                movq %rcx, %r8               # r8 = buf (syscall clobbera rcx/r11)
                movq %rcx, %rdi              # struct timespec*
                call kof_plat_time
                movq 8(%r8), %rax            # nsec
                xorq %rdx, %rdx
                movq $1000, %rsi
                divq %rsi                    # rax = nsec/1000 (us)
                movq %rax, 24(%rsp)          # guarda no frame proprio
                movq 0(%r8), %rax            # sec
                imulq $1000000, %rax
                addq 24(%rsp), %rax
                addq $40, %rsp
                ret

            # escape JSON de rdi = KofString* -> rax (aspas e barra, como o JVM)
            .globl kof_obs_json_escape
            .type kof_obs_json_escape, @function
            kof_obs_json_escape:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %r15
                testq %rdi, %rdi
                jz .Lobs_esc_empty
                leaq .Lstr_obs_esc_e(%rip), %rdi
                xorq %rsi, %rsi
                call kof_string_from_literal
                movq %rax, %r14
                movq %r15, %rdi
                jmp .Lobs_esc_go
            .Lobs_esc_empty:
                leaq .Lstr_obs_esc_e(%rip), %rdi
                xorq %rsi, %rsi
                call kof_string_from_literal
                movq %rax, %r14
                jmp .Lobs_esc_done
            .Lobs_esc_go:
                movl 16(%rdi), %r12d         # byteLen
                leaq 24(%rdi), %r13          # dados UTF-8
                xorq %rbx, %rbx              # byteOff
                jmp .Lobs_esc_test
            .Lobs_esc_loop:
                movzbq (%r13,%rbx), %rax
                cmpq $0x22, %rax
                je .Lobs_esc_quote
                cmpq $0x5C, %rax
                je .Lobs_esc_back
                movq %r15, %rdi              # KofString original (r13 e so o ponteiro dos bytes)
                movq %rbx, %rsi
                movq %rbx, %rdx
                incq %rdx
                call kof_string_substring
                movq %rax, %rsi
                jmp .Lobs_esc_concat
            .Lobs_esc_quote:
                leaq .Lstr_obs_esc_q(%rip), %rdi
                movq $2, %rsi
                call kof_string_from_literal
                movq %rax, %rsi
                jmp .Lobs_esc_concat
            .Lobs_esc_back:
                leaq .Lstr_obs_esc_b(%rip), %rdi
                movq $2, %rsi
                call kof_string_from_literal
                movq %rax, %rsi
            .Lobs_esc_concat:
                movq %r14, %rdi
                call kof_string_concat
                movq %rax, %r14
                incq %rbx
            .Lobs_esc_test:
                cmpq %r12, %rbx
                jl .Lobs_esc_loop
            .Lobs_esc_done:
                movq %r14, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_observability_span_start(rdi=name) -> String handle
            # handle = traceId(32 hex) + spanId(16 hex) = 48 chars
            .globl kof_observability_span_start
            .type kof_observability_span_start, @function
            kof_observability_span_start:
                pushq %rbx
                movq %rdi, %r15              # nome: rdi e clobbered pelos helpers/syscalls
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                # traceId
                movq $16, %rdi
                call kof_sec_random_hex
                movq %rax, %rbx
                # spanId
                movq $8, %rdi
                call kof_sec_random_hex
                movq %rax, %r12
                # handle = traceId + spanId
                movq %rbx, %rdi
                movq %r12, %rsi
                call kof_string_concat
                movq %rax, %r14
                # registrar handle+nome+epoch(us)+mono(ns) na tabela (stride 32)
                # (o nome ja esta em %r15; helpers clobbra rdi/r8)
                movq .Lkof_obs_span_len(%rip), %r12
                cmpq $32, %r12
                jge .Lobs_span_start_done
                subq $8, %rsp                # alinhamento 16B p/ clock_gettime
                call kof_obs_epoch_micros
                addq $8, %rsp
                movq %rax, %r13              # epoch us (r13 livre, salvo no prologo)
                subq $8, %rsp                # alinhamento 16B p/ clock_gettime
                call kof_obs_mono_nanos
                addq $8, %rsp
                movq %r12, %rcx
                imulq $32, %rcx
                leaq .Lkof_obs_span_handles(%rip), %rdx
                addq %rcx, %rdx
                movq %r14, 0(%rdx)
                movq %r15, 8(%rdx)
                movq %r13, 16(%rdx)
                movq %rax, 24(%rdx)
                incq %r12
                movq %r12, .Lkof_obs_span_len(%rip)
            .Lobs_span_start_done:
                movq %r14, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_observability_span_end(rdi=handle) -> String (JSON)
            .globl kof_observability_span_end
            .type kof_observability_span_end, @function
            kof_observability_span_end:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                pushq %rbp
                movq %rdi, %rbx
                movq .Lkof_obs_span_len(%rip), %r12
                xorq %r13, %r13
            .Lobs_span_end_search:
                cmpq %r12, %r13
                jge .Lobs_span_end_missing
                leaq .Lkof_obs_span_handles(%rip), %r14
                movq %r13, %rax
                imulq $32, %rax
                addq %rax, %r14
                movq 0(%r14), %r15
                testq %r15, %r15
                jz .Lobs_span_end_next
                movq %rbx, %rdi
                movq %r15, %rsi
                call kof_string_equals
                testl %eax, %eax
                jnz .Lobs_span_end_found
            .Lobs_span_end_next:
                incq %r13
                jmp .Lobs_span_end_search
            .Lobs_span_end_found:
                movq 16(%r14), %r13          # start epoch us
                movq 8(%r14), %rbp           # name ptr (0 = "") — rbp pois syscall clobbra r11
                movq 24(%r14), %rax          # start mono ns
                pushq %rax                   # slot 1 (prologue ja deixa rsp 16-alinhado)
                # dur = (now_ns - start_ns) / 1000  (mono, como nanoTime JVM)
                call kof_obs_mono_nanos
                popq %rcx
                subq %rcx, %rax
                xorq %rdx, %rdx
                movq $1000, %rcx
                divq %rcx
                subq $8, %rsp                # slot 2: durationMicros (emparelha p/ pushes de callee)
                pushq %rax
                # limpar a entrada
                movq $0, 0(%r14)
                movq $0, 8(%r14)
                movq $0, 16(%r14)
                movq $0, 24(%r14)
                # traceId = handle.substring(0, 32)
                movq %rbx, %rdi
                movq $0, %rsi
                movq $32, %rdx
                call kof_string_substring
                movq %rax, %r15              # traceId
                # spanId = handle.substring(32, 48)
                movq %rbx, %rdi
                movq $32, %rsi
                movq $48, %rdx
                call kof_string_substring
                movq %rax, %r14              # spanId
                # epoch end (kof_now ms * 1000)
                call kof_now
                imulq $1000, %rax
                movq %rax, %r12              # endMicros em r12: escape clobbra r9/r10/r11 (r10: sobrevive aos concat? concat usa r12-r15; substring nao e chamado daki p/ frente)
                # name escapado p/ JSON
                movq %rbp, %rdi
                call kof_obs_json_escape
                movq %rax, %rbp              # escaped name
                # monta o JSON na ordem do golden JVM
                leaq .Lstr_obs_span_1(%rip), %rdi
                movq $12, %rsi
                call kof_string_from_literal
                movq %rax, %rbx
                movq %rbx, %rdi
                movq %r15, %rsi
                call kof_string_concat
                movq %rax, %rbx
                leaq .Lstr_obs_span_2(%rip), %rdi
                movq $12, %rsi
                call kof_string_from_literal
                movq %rbx, %rdi
                movq %rax, %rsi
                call kof_string_concat
                movq %rax, %rbx
                movq %rbx, %rdi
                movq %r14, %rsi
                call kof_string_concat
                movq %rax, %rbx
                leaq .Lstr_obs_pe_1(%rip), %rdi
                movq $18, %rsi
                call kof_string_from_literal
                movq %rbx, %rdi
                movq %rax, %rsi
                call kof_string_concat
                movq %rax, %rbx
                leaq .Lstr_obs_pe_2(%rip), %rdi
                movq $10, %rsi
                call kof_string_from_literal
                movq %rbx, %rdi
                movq %rax, %rsi
                call kof_string_concat
                movq %rax, %rbx
                movq %rbx, %rdi
                movq %rbp, %rsi
                call kof_string_concat
                movq %rax, %rbx
                leaq .Lstr_obs_pe_3(%rip), %rdi
                movq $16, %rsi
                call kof_string_from_literal
                movq %rbx, %rdi
                movq %rax, %rsi
                call kof_string_concat
                movq %rax, %rbx
                movq %r13, %rdi
                call kof_long_to_string
                movq %rbx, %rdi
                movq %rax, %rsi
                call kof_string_concat
                movq %rax, %rbx
                leaq .Lstr_obs_pe_4(%rip), %rdi
                movq $13, %rsi
                call kof_string_from_literal
                movq %rbx, %rdi
                movq %rax, %rsi
                call kof_string_concat
                movq %rax, %rbx
                movq %r12, %rdi
                call kof_long_to_string
                movq %rbx, %rdi
                movq %rax, %rsi
                call kof_string_concat
                movq %rax, %rbx
                leaq .Lstr_obs_pe_5(%rip), %rdi
                movq $18, %rsi
                call kof_string_from_literal
                movq %rbx, %rdi
                movq %rax, %rsi
                call kof_string_concat
                movq %rax, %rbx
                movq 0(%rsp), %rdi
                call kof_long_to_string
                movq %rbx, %rdi
                movq %rax, %rsi
                call kof_string_concat
                movq %rax, %rbx
                addq $16, %rsp               # libera os 2 slots
                leaq .Lstr_obs_span_4(%rip), %rdi
                movq $1, %rsi
                call kof_string_from_literal
                movq %rbx, %rdi
                movq %rax, %rsi
                call kof_string_concat
                movq %rax, %rbx
                movq %rbx, %rax
                popq %rbp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lobs_span_end_missing:
                subq $8, %rsp                # paridade 16B p/ call (6 pushes = rsp%16==8)
                leaq .Lstr_obs_pe_0(%rip), %rdi
                movq $2, %rsi
                call kof_string_from_literal
                addq $8, %rsp                # JVM parity: handle desconhecido -> "{}" (nunca null)
                popq %rbp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }
}
