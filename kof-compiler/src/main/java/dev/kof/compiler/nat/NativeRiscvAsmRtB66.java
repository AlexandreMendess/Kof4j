package dev.kof.compiler.nat;

// S5.1 (db-parity-plan, gaps-db lane, 23/09): fecha o handshake/auth do MySQL
// no cross — le o greeting, extrai o seed, calcula o scramble, monta e envia o
// handshake response e le o OK/ERR. Port do fluxo de RuntimeDb3 (x86), agora
// sobre as pecas B62 (SHA1), B63 (scramble/lenenc), B64 (greeting) e B65
// (auth response) + a HAL de socket (kof_plat_net_*). A prova e o round-trip
// real contra o MariaDB sob qemu.
//
// Contrato riscv:
//   kof_db_mysql_handshake(a0=fd, a1=user KofString, a2=pass KofString,
//                          a3=db KofString) -> a0 = 0 ok | -1 falha
public final class NativeRiscvAsmRtB66 {

    private NativeRiscvAsmRtB66() {}

    static String RISCV_RUNTIME_ASM_B_66 = """
            .section .text
            # ---------------------------------------------------------------
            # kof_db_mysql_handshake(a0=fd,a1=user,a2=pass,a3=db) -> 0 ok|-1
            # Frame 8368: ra+s0..s8 (0..79) | greeting 4096 (128..4223) |
            #             auth 4096 (4224..8319) | seed20 (8320..8339) |
            #             scramble20 (8340..8359)
            # ---------------------------------------------------------------
            .globl kof_db_mysql_handshake
            .type kof_db_mysql_handshake, @function
            kof_db_mysql_handshake:
                li   t6, 8368
                sub  sp, sp, t6
                sd   ra, 0(sp)
                sd   s0, 8(sp)
                sd   s1, 16(sp)
                sd   s2, 24(sp)
                sd   s3, 32(sp)
                sd   s4, 40(sp)
                sd   s5, 48(sp)
                sd   s6, 56(sp)
                sd   s7, 64(sp)
                sd   s8, 72(sp)
                mv   s0, a0
                mv   s1, a1
                mv   s2, a2
                mv   s3, a3
                # le o greeting
                mv   a0, s0
                addi a1, sp, 128
                li   a2, 4096
                call kof_plat_read
                blez a0, .L66_lost
                # §523: ERR ja no 1o pacote (host bloqueado/conexoes esgotadas)
                lbu  t1, 132(sp)
                li   t2, 255
                beq  t1, t2, .L66_greet_err
                # extrai o seed (20 bytes)
                addi a0, sp, 128
                li   t0, 8320
                add  a1, sp, t0
                call kof_db_mysql_parse_greeting
                beqz a0, .L66_fail
                # passLen
                li   s4, 0
                beqz s2, .L66_nopass
                lw   s4, 16(s2)
            .L66_nopass:
                beqz s4, .L66_skip_scr
                li   t0, 8340
                add  a0, sp, t0
                li   t0, 8320
                add  a1, sp, t0
                li   a2, 20
                mv   a3, s2
                call kof_db_mysql_scramble
            .L66_skip_scr:
                # monta o handshake response
                li   t0, 4224
                add  a0, sp, t0
                li   t0, 8340
                add  a1, sp, t0
                mv   a2, s1
                mv   a3, s3
                mv   a4, s4
                call kof_db_mysql_build_auth_response
                mv   s5, a0
                # envia
                mv   a0, s0
                li   t0, 4224
                add  a1, sp, t0
                addi a2, s5, 4
                call kof_plat_write
                # le a resposta (OK/ERR)
                mv   a0, s0
                addi a1, sp, 128
                li   a2, 4096
                call kof_plat_read
                blez a0, .L66_lost
                lbu  t1, 132(sp)
                beqz t1, .L66_ok
                li   t2, 255
                beq  t1, t2, .L66_resp_err
                j    .L66_fail
            .L66_resp_err:
                addi a0, sp, 132
                lbu  t0, 128(sp)
                lbu  t1, 129(sp)
                slli t1, t1, 8
                or   t0, t0, t1
                lbu  t1, 130(sp)
                slli t1, t1, 16
                or   a1, t0, t1
                j    .L66_throw_err
            .L66_greet_err:
                addi a0, sp, 132
                lbu  t0, 128(sp)
                lbu  t1, 129(sp)
                slli t1, t1, 8
                or   t0, t0, t1
                lbu  t1, 130(sp)
                slli t1, t1, 16
                or   a1, t0, t1
                j    .L66_throw_err
            .L66_lost:
                la   a0, .L66_lostv
                call kof_throw_string
            # §523 (27/09): ERR do handshake lanca "mysql: <msg>" — espelha o
            # `.Lsa_ex_err`/B76. a0 = payload, a1 = pktlen; msg = payload+9,
            # len = pktlen-9, teto 400. Nunca retorna (s4-s7 como scratch).
            .L66_throw_err:
                addi t0, a1, -9
                blez t0, .L66_te0
                li   t1, 400
                ble  t0, t1, .L66_te1
                li   t0, 400
                j    .L66_te1
            .L66_te0:
                li   t0, 0
            .L66_te1:
                mv   s6, t0              # msglen
                addi t0, t0, 7
                mv   s5, t0              # total
                mv   s4, a0              # payload
                addi a0, t0, 25
                call kof_alloc
                mv   s7, a0              # str
                li   t0, 1
                sw   t0, 0(s7)
                sw   zero, 4(s7)
                sd   zero, 8(s7)
                sw   s5, 16(s7)
                sw   zero, 20(s7)
                addi a0, s7, 24
                la   a1, .L66_pfx
                li   a2, 7
                call kof_memcpy
                beqz s6, .L66_te2
                addi a0, s7, 31
                addi a1, s4, 9
                mv   a2, s6
                call kof_memcpy
            .L66_te2:
                add  t0, s7, s5
                addi t0, t0, 24
                sb   zero, 0(t0)
                mv   a0, s7
                call kof_throw_string
            .L66_ok:
                li   a0, 0
                j    .L66_ret
            .L66_fail:
                li   a0, -1
            .L66_ret:
                ld   ra, 0(sp)
                ld   s0, 8(sp)
                ld   s1, 16(sp)
                ld   s2, 24(sp)
                ld   s3, 32(sp)
                ld   s4, 40(sp)
                ld   s5, 48(sp)
                ld   s6, 56(sp)
                ld   s7, 64(sp)
                ld   s8, 72(sp)
                li   t6, 8368
                add  sp, sp, t6
                ret
            .section .data
            .align 3
            .L66_pfx:
                .ascii "mysql: "
            .L66_lostv:
                .long 1
                .long 0
                .quad 0
                .long 22
                .long 0
                .ascii "mysql: connection lost"
                .byte 0
            .section .text
            """;
}
