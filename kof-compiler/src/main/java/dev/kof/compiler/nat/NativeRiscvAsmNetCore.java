package dev.kof.compiler.nat;

// D-KOF-NET fatia 4b (plan docs/stdlib/network-kofnet-plan.md): port do
// front `kof.net` x86-64 (NativeNetFront) para riscv64. O aarch64 herda
// linha-a-linha pelo NativeAarch64Translator (mesmos syscalls asm-generic).
//
// Núcleo TCP: helpers (throw/novo handle/socket) + a HAL de socket que faltava
// no riscv (bind/listen/accept/send) + listen/accept/connect/close. Layout do
// handle IDENTICO ao x86: tag@0 (1=Listener 2=Conn 3=Endpoint), fd@8,
// hasPeer@16, ip[4]@24..27, port@28 (host order); bloco de 48 bytes.
//
// Syscalls riscv64/aarch64 (asm-generic): socket=198 connect=203 bind=200
// listen=201 accept=202 sendto=206 setsockopt=208 close=57. connect v1 = IPv4
// dotted-quad literal (hostname recusado, nunca resolvido em silencio).
public final class NativeRiscvAsmNetCore {

    private NativeRiscvAsmNetCore() {}

    static String RISCV_RUNTIME_ASM_NET_CORE = """
            .section .rodata
            .Lnet_msg_listen:   .asciz "kof.net: cannot listen (NET002)"
            .Lnet_msg_accept:   .asciz "kof.net: accept failed (NET002)"
            .Lnet_msg_connect:  .asciz "kof.net: cannot connect to IPv4 literal (NET002)"
            .Lnet_msg_bind:     .asciz "kof.net: cannot bind (NET002)"
            .Lnet_msg_handle:   .asciz "kof.net: not a net handle (NET005)"
            .Lnet_msg_addr:     .asciz "kof.net: address needs host:port (NET004)"
            .Lnet_msg_toobig:   .asciz "kof.net: datagram exceeds 65507 byte UDP payload bound (NET003)"
            .Lnet_msg_send:     .asciz "kof.net: send failed (NET002)"
            .Lnet_msg_recv:     .asciz "kof.net: receive failed (NET002)"
            .Lnet_empty:        .asciz ""
            .Lnet_dot:          .asciz "."
            .Lnet_colon:        .asciz ":"
            .section .text

            # ---- HAL de socket (familia kof_plat_net_* que o front consome) ----
            .globl kof_plat_net_bind
            kof_plat_net_bind:
                li   a7, 200
                ecall
                ret

            .globl kof_plat_net_listen
            kof_plat_net_listen:
                li   a7, 201
                ecall
                ret

            .globl kof_plat_net_accept
            kof_plat_net_accept:
                li   a7, 202
                ecall
                ret

            # sendto(fd,buf,len,flags,0,0) — MSG_NOSIGNAL (0x4000) posto pelo
            # chamador em a3.
            .globl kof_plat_net_send
            kof_plat_net_send:
                li   a7, 206
                ecall
                ret

            # kof_net_throw(a0=cstr): KofString + throw (try/catch Kof).
            .globl kof_net_throw
            .type kof_net_throw, @function
            kof_net_throw:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                mv   s0, a0
                li   t0, 0
            .Lnet_throw_len:
                add  t1, s0, t0
                lbu  t1, 0(t1)
                beqz t1, .Lnet_throw_len_done
                addi t0, t0, 1
                j    .Lnet_throw_len
            .Lnet_throw_len_done:
                mv   a0, s0
                mv   a1, t0
                call kof_string_from_literal
                call kof_throw_string
                ld   s0, 16(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            # kof_net_new_handle(a0=tag, a1=fd) -> a0=handle
            .globl kof_net_new_handle
            .type kof_net_new_handle, @function
            kof_net_new_handle:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                mv   s0, a0
                mv   s1, a1
                li   a0, 48
                call kof_alloc
                sw   s0, 0(a0)
                sd   s1, 8(a0)
                sd   zero, 16(a0)
                sd   zero, 24(a0)
                sd   zero, 32(a0)
                sw   zero, 40(a0)
                ld   s0, 32(sp)
                ld   s1, 24(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # kof_net_tcp_socket() -> a0=fd (SOCK_STREAM + SO_REUSEADDR)
            .globl kof_net_tcp_socket
            .type kof_net_tcp_socket, @function
            kof_net_tcp_socket:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                li   a0, 2
                li   a1, 1
                li   a2, 0
                call kof_plat_net_socket
                mv   s0, a0
                bltz s0, .Lnet_tcp_sock_done
                li   t0, 1
                sw   t0, 8(sp)          # optval
                li   t0, 4
                sw   t0, 12(sp)         # optlen
                mv   a0, s0
                li   a1, 1              # SOL_SOCKET
                li   a2, 2              # SO_REUSEADDR
                addi a3, sp, 8
                li   a4, 8
                li   a7, 208
                ecall
            .Lnet_tcp_sock_done:
                mv   a0, s0
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # net.listen(a0=port) -> Listener
            .globl kof_net_listen
            .type kof_net_listen, @function
            kof_net_listen:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)
                sd   s1, 40(sp)
                sd   s2, 32(sp)
                mv   s1, a0
                call kof_net_tcp_socket
                mv   s0, a0
                bltz s0, .Lnet_listen_fail
                li   t0, 2
                sh   t0, 16(sp)
                addi t0, sp, 18
                slli t1, s1, 8
                srli t2, s1, 8
                andi t2, t2, 255
                or   t1, t1, t2
                sh   t1, 0(t0)
                sw   zero, 20(sp)
                sw   zero, 24(sp)
                sw   zero, 28(sp)
                mv   a0, s0
                addi a1, sp, 16
                li   a2, 16
                call kof_plat_net_bind
                bltz a0, .Lnet_listen_fail
                mv   a0, s0
                li   a1, 64
                call kof_plat_net_listen
                bltz a0, .Lnet_listen_fail
                li   a0, 1
                mv   a1, s0
                call kof_net_new_handle
                ld   s2, 32(sp)
                ld   s1, 40(sp)
                ld   s0, 48(sp)
                ld   ra, 56(sp)
                addi sp, sp, 64
                ret
            .Lnet_listen_fail:
                mv   a0, s0
                call kof_plat_close
                la   a0, .Lnet_msg_listen
                call kof_net_throw

            # listener.accept(a0=handle) -> Conn
            .globl kof_net_accept
            .type kof_net_accept, @function
            kof_net_accept:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                mv   s0, a0
                beqz s0, .Lnet_accept_bad
                lw   t0, 0(s0)
                li   t1, 1
                bne  t0, t1, .Lnet_accept_bad
                ld   a0, 8(s0)
                addi a1, sp, 0
                addi a2, sp, 16
                li   t0, 16
                sw   t0, 16(sp)
                call kof_plat_net_accept
                bltz a0, .Lnet_accept_fail
                mv   s1, a0
                li   a0, 2
                mv   a1, s1
                call kof_net_new_handle
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret
            .Lnet_accept_bad:
                la   a0, .Lnet_msg_handle
                call kof_net_throw
            .Lnet_accept_fail:
                la   a0, .Lnet_msg_accept
                call kof_net_throw

            # net.connect(a0=host String, a1=port) -> Conn (IPv4 dotted-quad v1)
            .globl kof_net_connect
            .type kof_net_connect, @function
            kof_net_connect:
                addi sp, sp, -96
                sd   ra, 88(sp)
                sd   s0, 80(sp)
                sd   s1, 72(sp)
                sd   s2, 64(sp)
                sd   s3, 56(sp)
                sd   s4, 48(sp)
                sd   s5, 40(sp)
                mv   s0, a0
                mv   s1, a1
                call kof_net_tcp_socket
                mv   s2, a0
                bltz s2, .Lnet_connect_fail
                li   t0, 2
                sh   t0, 16(sp)
                addi t0, sp, 18
                slli t1, s1, 8
                srli t2, s1, 8
                andi t2, t2, 255
                or   t1, t1, t2
                sh   t1, 0(t0)
                sw   zero, 20(sp)
                sw   zero, 24(sp)
                sw   zero, 28(sp)
                beqz s0, .Lnet_connect_bad
                lw   s4, 16(s0)          # len
                beqz s4, .Lnet_connect_bad
                addi s3, s0, 24          # data
                li   s5, 0               # octet count
                li   t0, 0               # accumulator
            .Lnet_connect_parse:
                beqz s4, .Lnet_connect_parse_end
                lbu  t1, 0(s3)
                li   t2, 46              # '.'
                beq  t1, t2, .Lnet_connect_dot
                li   t2, 48
                bltu t1, t2, .Lnet_connect_bad
                li   t2, 57
                bltu t2, t1, .Lnet_connect_bad
                li   t2, 10
                mul  t0, t0, t2
                addi t1, t1, -48
                add  t0, t0, t1
                addi s3, s3, 1
                addi s4, s4, -1
                j    .Lnet_connect_parse
            .Lnet_connect_dot:
                li   t2, 3
                bgeu s5, t2, .Lnet_connect_bad
                add  t2, sp, s5
                sb   t0, 20(t2)
                li   t0, 0
                addi s5, s5, 1
                addi s3, s3, 1
                addi s4, s4, -1
                j    .Lnet_connect_parse
            .Lnet_connect_parse_end:
                li   t2, 3
                bne  s5, t2, .Lnet_connect_bad
                add  t2, sp, s5
                sb   t0, 20(t2)
                mv   a0, s2
                addi a1, sp, 16
                li   a2, 16
                call kof_plat_net_connect
                bltz a0, .Lnet_connect_bad
                li   a0, 2
                mv   a1, s2
                call kof_net_new_handle
                ld   s5, 40(sp)
                ld   s4, 48(sp)
                ld   s3, 56(sp)
                ld   s2, 64(sp)
                ld   s1, 72(sp)
                ld   s0, 80(sp)
                ld   ra, 88(sp)
                addi sp, sp, 96
                ret
            .Lnet_connect_bad:
                mv   a0, s2
                call kof_plat_close
                la   a0, .Lnet_msg_connect
                call kof_net_throw
            .Lnet_connect_fail:
                la   a0, .Lnet_msg_connect
                call kof_net_throw

            # handle.close(a0) — polimorfico por tag; tag desconhecida = NET005.
            .globl kof_net_close
            .type kof_net_close, @function
            kof_net_close:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                mv   s0, a0
                beqz s0, .Lnet_close_done
                lw   t0, 0(s0)
                li   t1, 1
                beq  t0, t1, .Lnet_close_fd
                li   t1, 2
                beq  t0, t1, .Lnet_close_fd
                li   t1, 3
                beq  t0, t1, .Lnet_close_fd
                la   a0, .Lnet_msg_handle
                call kof_net_throw
            .Lnet_close_fd:
                ld   a0, 8(s0)
                call kof_plat_close
            .Lnet_close_done:
                ld   s0, 16(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret
            """;
}
