package dev.kof.compiler.nat;

/**
 * FASE 3 (REFACTOR-500): request core HTTP riscv64 + verbos (get/post/put/
 * patch/delete/options/status) e strings de wire-format. Extraído verbatim
 * de NativeBackend.emitRiscvHttp (parte B) — asm byte-idêntico.
 */
public final class NativeRiscvHttpCore {

    private NativeRiscvHttpCore() {}

    static void emit(StringBuilder sb) {
        sb.append("""

            .globl kof_http_core
            kof_http_core:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)
                sd   s1, 40(sp)
                sd   s2, 32(sp)
                sd   s3, 24(sp)
                sd   s4, 16(sp)
                sd   s5, 8(sp)
                sd   s6, 0(sp)
                mv   s0, a0
                mv   s1, a1
                mv   s2, a2
                mv   s4, a4
                la   t0, .Lhttp_urlptr
                sd   s0, 0(t0)             # §259: url p/ msgs "HTTP n from"/circuit
                la   t0, .Lhttp_methodp
                sd   s1, 0(t0)             # s1 vira cursor do status-parse; restaurar por tentativa
                la   t0, .Lhttp_retry_n
                ld   t1, 0(t0)
                addi t1, t1, 1             # N+1 tentativas (JVM: attempts = retries+1)
                la   t0, .Lhttp_attempts
                sd   t1, 0(t0)
                call kof_http_circuit_open # §259 fatia 3: aberto -> fail-fast SEM conectar
                bnez a0, .Lhr_copen
                mv   a0, s0
                call kof_http_parse_url
            .Lhr_attempt:
                la   t0, .Lhttp_methodp
                ld   s1, 0(t0)
                li   a0, 2
                li   a1, 1
                li   a2, 0
                call kof_plat_net_socket
                bltz a0, .Lhr_fail
                mv   s5, a0
                # §259: connect nao-bloqueante p/ deadline (medido probe rtmo.s)
                mv   a0, s5
                li   a1, 4
                li   a2, 2048
                li   a7, 25                # fcntl
                ecall
                la   t0, .Lhttp_sock
                li   t1, 2
                sw   t1, 0(t0)
                la   t1, .Lhttp_portbin
                lh   t1, 0(t1)
                sh   t1, 2(t0)
                la   t1, .Lhttp_ipbin
                lw   t1, 0(t1)
                sw   t1, 4(t0)
                li   t1, 0
                sd   t1, 8(t0)
                la   a1, .Lhttp_sock
                mv   a0, s5
                li   a2, 16
                call kof_plat_net_connect
                bgez a0, .Lhr_conn_done
                neg  a1, a0
                li   t1, 115               # EINPROGRESS (medido probe riscv)
                bne  a1, t1, .Lhr_fail_cl
                # ppoll(pf, 1, ts|NULL, NULL) a7=73 — ts em SEGUNDOS (sem ms/clamp)
                la   t0, .Lhttp_pf
                sw   s5, 0(t0)
                li   t1, 4
                sh   t1, 4(t0)             # events POLLOUT @+4 (struct pollfd medido)
                la   t2, .Lhttp_timeout_s
                ld   t3, 0(t2)
                beqz t3, .Lhr_poll_null    # 0 = sem deadline -> ts NULL
                la   t0, .Lhttp_tv
                sd   t3, 0(t0)
                li   t1, 0
                sd   t1, 8(t0)
                la   a2, .Lhttp_tv
                j    .Lhr_poll_go
            .Lhr_poll_null:
                li   a2, 0
            .Lhr_poll_go:
                la   a0, .Lhttp_pf
                li   a1, 1
                li   a3, 0
                li   a7, 73
                ecall
                bltz a0, .Lhr_fail_cl
                beqz a0, .Lhr_tmo_cl
                # getsockopt(fd, SOL=1, SO_ERROR=4, &err, &len) a7=209; 5o arg a4 (riscv)
                la   t0, .Lhttp_serr
                li   t1, 0
                sw   t1, 0(t0)
                li   t1, 4
                sw   t1, 4(t0)
                mv   a0, s5
                li   a1, 1
                li   a2, 4
                mv   a3, t0
                addi a4, t0, 4
                li   a7, 209
                ecall
                lw   t1, 0(t0)
                bnez t1, .Lhr_fail_cl      # ECONNREFUSED -> rapido (nao engole timeout)
            .Lhr_conn_done:
                # restaura blocking (read so' EAGAIN apos tv_sec — medido) e
                # SO_RCVTIMEO/SO_SNDTIMEO = timeout_s
                mv   a0, s5
                li   a1, 4
                li   a2, 0
                li   a7, 25
                ecall
                la   t2, .Lhttp_timeout_s
                ld   t3, 0(t2)
                beqz t3, .Lhr_build
                la   t0, .Lhttp_tv
                sd   t3, 0(t0)
                li   t1, 0
                sd   t1, 8(t0)
                mv   a0, s5
                li   a1, 1
                li   a2, 20                # SO_RCVTIMEO
                mv   a3, t0
                li   a4, 16
                li   a7, 208
                ecall
                mv   a0, s5
                li   a1, 1
                li   a2, 21                # SO_SNDTIMEO
                mv   a3, t0
                li   a4, 16
                li   a7, 208
                ecall
            .Lhr_build:
                la   t0, .Lhttp_reqbuf
                mv   a0, t0
                mv   a1, s1
                call kof_http_append_cstr
                li   t0, 32
                sb   t0, 0(a0)
                addi a0, a0, 1
                la   a1, .Lhttp_pathbuf
                call kof_http_append_cstr
                la   a1, .Lhttp_str_ver
                call kof_http_append_cstr
                la   a1, .Lhttp_crlfb
                li   a2, 2
                call kof_http_append_n
                la   a1, .Lhttp_str_host
                call kof_http_append_cstr
                la   a1, .Lhttp_hostbuf
                call kof_http_append_cstr
                la   t0, .Lhttp_port_host
                ld   t0, 0(t0)
                li   t1, 80
                beq  t0, t1, .Lhr_nohostport
                li   t0, 58
                sb   t0, 0(a0)
                addi a0, a0, 1
                la   t0, .Lhttp_port_host
                ld   a1, 0(t0)
                call kof_http_append_dec
            .Lhr_nohostport:
                la   a1, .Lhttp_crlfb
                li   a2, 2
                call kof_http_append_n
                beqz s4, .Lhr_body_hdrs
                lw   t0, 16(s4)
                addi a1, s4, 24
                li   t1, 0
            .Lhr_hl:
                bltu t0, t1, .Lhr_hl_done
                lbu  t2, 0(a1)
                li   t3, 10
                beq  t2, t3, .Lhr_hc
                sb   t2, 0(a0)
                addi a0, a0, 1
                j    .Lhr_hn
            .Lhr_hc:
                li   t2, 13
                sb   t2, 0(a0)
                addi a0, a0, 1
                sb   t3, 0(a0)
                addi a0, a0, 1
            .Lhr_hn:
                addi t1, t1, 1
                addi a1, a1, 1
                j    .Lhr_hl
            .Lhr_hl_done:
                la   a1, .Lhttp_crlfb
                li   a2, 2
                call kof_http_append_n
            .Lhr_body_hdrs:
                beqz s2, .Lhr_closing
                la   a1, .Lhttp_str_clen
                call kof_http_append_cstr
                lw   t0, 16(s2)
                mv   a1, t0
                call kof_http_append_dec
                la   a1, .Lhttp_crlfb
                li   a2, 2
                call kof_http_append_n
            .Lhr_closing:
                la   a1, .Lhttp_str_conn
                call kof_http_append_cstr
                la   a1, .Lhttp_crlfb
                li   a2, 2
                call kof_http_append_n
                la   a1, .Lhttp_crlfb
                li   a2, 2
                call kof_http_append_n
                beqz s2, .Lhr_send
                addi a1, s2, 24
                lw   a2, 16(s2)
                call kof_http_append_n
            .Lhr_send:
                la   t0, .Lhttp_reqbuf
                sub  a2, a0, t0
                mv   a1, t0
                mv   a0, s5
                call kof_plat_write
                li   s3, 0
            .Lhr_rd:
                la   t0, .Lhttp_respbuf
                add  a1, t0, s3
                li   t0, 262144
                sub  a2, t0, s3
                mv   a0, s5
                call kof_plat_read
                bltz a0, .Lhr_rd_err
                beqz a0, .Lhr_rd_done
                add  s3, s3, a0
                li   t0, 262144
                bltu s3, t0, .Lhr_rd
            .Lhr_rd_err:
                neg  t1, a0
                li   t2, 11                # EAGAIN: SO_RCVTIMEO expirou (medido)
                beq  t1, t2, .Lhr_tmo_cl
            .Lhr_rd_done:
                la   s1, .Lhttp_respbuf
                li   t0, 0
            .Lhr_st_space:
                lbu  a0, 0(s1)
                li   t1, 32
                beq  a0, t1, .Lhr_st_sp_hit
                addi s1, s1, 1
                j    .Lhr_st_space
            .Lhr_st_sp_hit:
                addi s1, s1, 1
            .Lhr_st_loop:
                lbu  a0, 0(s1)
                li   t1, 48
                sub  a0, a0, t1
                li   t2, 9
                bltu t2, a0, .Lhr_st_ok
                li   t1, 10
                mul  t0, t0, t1
                add  t0, t0, a0
                addi s1, s1, 1
                j    .Lhr_st_loop
            .Lhr_st_ok:
                la   t1, .Lhttp_last_status
                sd   t0, 0(t1)
                li   t1, 500
                bltu t0, t1, .Lhr_body     # §259: 5xx = falha retryavel (paridade JVM)
                la   t2, .Lhttp_serr
                sd   t0, 0(t2)
                la   a0, .Lhttp_errbuf
                la   a1, .Lhttp_str_5xx
                call kof_http_append_cstr
                la   t2, .Lhttp_serr
                ld   a1, 0(t2)
                call kof_http_append_dec
                la   a1, .Lhttp_str_from
                call kof_http_append_cstr
                la   t2, .Lhttp_urlptr
                ld   a1, 0(t2)
                lw   a2, 16(a1)
                addi a1, a1, 24
                call kof_http_append_n
                li   t2, 0
                sb   t2, 0(a0)
                la   a0, .Lhttp_errbuf
                j    .Lhr_rtry_cl
            .Lhr_body:
                call kof_http_circuit_record_success  # <500 reseta o circuito (JVM)
                la   s1, .Lhttp_respbuf
                mv   s6, s3
            .Lhr_bscan:
                li   t0, 4
                bltu s6, t0, .Lhr_bnone
                lbu  a0, 0(s1)
                li   t1, 13
                bne  a0, t1, .Lhr_bn
                lbu  a0, 1(s1)
                li   t1, 10
                bne  a0, t1, .Lhr_bn
                lbu  a0, 2(s1)
                li   t1, 13
                bne  a0, t1, .Lhr_bn
                lbu  a0, 3(s1)
                li   t1, 10
                bne  a0, t1, .Lhr_bn
                addi s1, s1, 4
                addi s6, s6, -4
                mv   a0, s1
                mv   a1, s6
                call kof_string_from_literal
                mv   s0, a0
                mv   a0, s5
                call kof_plat_close
                mv   a0, s0
                j    .Lhr_out
            .Lhr_bn:
                addi s1, s1, 1
                addi s6, s6, -1
                j    .Lhr_bscan
            .Lhr_bnone:
                la   a0, .Lhttp_empty
                li   a1, 0
                call kof_string_from_literal
                mv   s0, a0
                mv   a0, s5
                call kof_plat_close
                mv   a0, s0
            .Lhr_fail_cl:                  # erro c/ fd aberto -> fecha
                mv   a0, s5
                call kof_plat_close
                la   a0, .Lhttp_err_conn
                j    .Lhr_rtry
            .Lhr_tmo_cl:                   # deadline de connect (ppoll=0) ou read
                mv   a0, s5
                call kof_plat_close
                la   a0, .Lhttp_err_tmo
                j    .Lhr_rtry
            .Lhr_rtry_cl:                  # 5xx: msg salva, fecha fd, registra
                la   t0, .Lhttp_last_err
                sd   a0, 0(t0)
                mv   a0, s5
                call kof_plat_close
                call kof_http_circuit_record_fail
                j    .Lhr_rtry_chk
            .Lhr_rtry:                     # excecao/timeout: guarda msg + registra
                la   t0, .Lhttp_last_err
                sd   a0, 0(t0)
                call kof_http_circuit_record_fail
            .Lhr_rtry_chk:
                la   t0, .Lhttp_attempts
                ld   t1, 0(t0)
                beqz t1, .Lhr_throw_last
                addi t1, t1, -1
                sd   t1, 0(t0)
                j    .Lhr_attempt
            .Lhr_throw_last:               # esgotou N+1: throw da ultima mensagem
                la   t0, .Lhttp_last_err
                ld   a0, 0(t0)
                call kof_http_cstrlen
                mv   a1, a0
                la   t0, .Lhttp_last_err
                ld   a0, 0(t0)
                call kof_string_from_literal
                call kof_throw_string
                j    .Lhr_out
            .Lhr_copen:                    # circuito aberto: msg + throw SEM socket
                la   a0, .Lhttp_errbuf
                la   a1, .Lhttp_str_copen
                call kof_http_append_cstr
                la   t2, .Lhttp_urlptr
                ld   a1, 0(t2)
                lw   a2, 16(a1)
                addi a1, a1, 24
                call kof_http_append_n
                li   t2, 0
                sb   t2, 0(a0)
                la   a0, .Lhttp_errbuf
                call kof_http_cstrlen
                mv   a1, a0
                la   a0, .Lhttp_errbuf
                call kof_string_from_literal
                call kof_throw_string
                j    .Lhr_out
            .Lhr_fail:                     # socket() sem fd: nao ha o que fechar
                la   a0, .Lhttp_err_conn
                j    .Lhr_rtry
            .Lhr_out:
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

            .section .data
            .Lhttp_hostbuf:   .space 256
            .Lhttp_pathbuf:   .space 1024
            .Lhttp_portbin:   .space 2
            .Lhttp_port_host: .quad 0
            .Lhttp_ipbin:     .space 4
            .Lhttp_reqbuf:    .space 16384
            .Lhttp_respbuf:   .space 262144
            .Lhttp_sock:      .space 16
            .Lhttp_last_status: .quad 0
            .Lhttps_err:   .asciz "kof.http: https not supported on Native (TLS pending); use http://"
            .Lhttp_fallback: .asciz "127.0.0.1"
            .Lhttp_str_host: .asciz "Host: "
            .Lhttp_str_clen: .asciz "Content-Length: "
            .Lhttp_str_conn: .asciz "Connection: close"
            .Lhttp_str_ver:  .asciz " HTTP/1.1"
            .Lhttp_crlfb:    .byte 13, 10
            .Lhttp_err_conn: .asciz "kof.http: connect failed"
            .Lhttp_empty: .space 1
            .Lhttp_m_get: .asciz "GET"
            .Lhttp_m_post: .asciz "POST"
            .Lhttp_m_put: .asciz "PUT"
            .Lhttp_m_patch: .asciz "PATCH"
            .Lhttp_m_delete: .asciz "DELETE"
            .Lhttp_m_options: .asciz "OPTIONS"
            .section .text
            """);
    }
}
