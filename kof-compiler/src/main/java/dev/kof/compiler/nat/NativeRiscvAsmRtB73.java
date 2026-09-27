package dev.kof.compiler.nat;

// S5.4 (db-parity-plan, gaps-db lane, 24/09): o connect MySQL/MariaDB REAL no
// cross — parser de URL (`mysql://`/`mariadb://` + host[:port][/db] +
// [user[:pass]@]), sockaddr IPv4 (dotted; fallback 127.0.0.1 como o x86),
// socket+connect pela HAL (kof_plat_net_*), handshake/auth da B66 e o registro
// do fd como type 2 nas tabelas `.Ldb_slots`/`.Ldb_types`/`.Ldb_count` da B47.
//
// É a peça que o connect da B47 chama quando o esquema não é "sqlite:" — com
// ela o `db.connect("mysql://...")`/`db.connect("mariadb://...")` deixa de cair
// no DB001 no cross e passa a ser o wire real (fecha o S1 cross-`DB001`).
//
// Contrato riscv:
//   kof_db_connect_mysql(url@a0, user2@a1, pass2@a2) -> handle KofString*|0
//   (user2/pass2 só valem quando a URL não traz userinfo — forma host-only,
//    espelho do kof_db_connect2 do x86; esquema fora do contrato -> DB001).
public final class NativeRiscvAsmRtB73 {

    private NativeRiscvAsmRtB73() {}

    static String RISCV_RUNTIME_ASM_B_73 = """
            .section .text
            # ---------------------------------------------------------------
            # kof_db_connect_mysql(a0=url,a1=user2,a2=pass2) -> handle|0
            # Frame 192: s0..s11 (0..88) | sockaddr 16B (96..111) | ra (184);
            #            buffer itoa do handle @~175.
            # ---------------------------------------------------------------
            .globl kof_db_connect_mysql
            .type kof_db_connect_mysql, @function
            kof_db_connect_mysql:
                addi sp, sp, -192
                sd   ra, 184(sp)
                sd   s0, 0(sp)
                sd   s1, 8(sp)
                sd   s2, 16(sp)
                sd   s3, 24(sp)
                sd   s4, 32(sp)
                sd   s5, 40(sp)
                sd   s6, 48(sp)
                sd   s7, 56(sp)
                sd   s8, 64(sp)
                sd   s9, 72(sp)
                sd   s10, 80(sp)
                sd   s11, 88(sp)
                mv   s0, a0              # url
                mv   s1, a1              # user2
                mv   s2, a2              # pass2
                addi s11, s0, 24         # data base da URL
                # esquema: mysql:// (8) | mariadb:// (10) | outro -> DB001
                lbu  t0, 0(s11)
                li   t1, 109             # 'm'
                bne  t0, t1, .L73_unsupported
                lbu  t0, 1(s11)          # 'y'
                li   t1, 121
                bne  t0, t1, .L73_maybe_maria
                lbu  t0, 2(s11)          # 's'
                li   t1, 115
                bne  t0, t1, .L73_unsupported
                lbu  t0, 3(s11)          # 'q'
                li   t1, 113
                bne  t0, t1, .L73_unsupported
                lbu  t0, 4(s11)          # 'l'
                li   t1, 108
                bne  t0, t1, .L73_unsupported
                lbu  t0, 5(s11)          # ':'
                li   t1, 58
                bne  t0, t1, .L73_unsupported
                lbu  t0, 6(s11)          # '/'
                li   t1, 47
                bne  t0, t1, .L73_unsupported
                lbu  t0, 7(s11)          # '/'
                li   t1, 47
                bne  t0, t1, .L73_unsupported
                addi t0, s11, 8
                j    .L73_scheme_ok
            .L73_maybe_maria:
                lbu  t0, 1(s11)          # 'a'
                li   t1, 97
                bne  t0, t1, .L73_unsupported
                lbu  t0, 2(s11)          # 'r'
                li   t1, 114
                bne  t0, t1, .L73_unsupported
                lbu  t0, 3(s11)          # 'i'
                li   t1, 105
                bne  t0, t1, .L73_unsupported
                lbu  t0, 4(s11)          # 'a'
                li   t1, 97
                bne  t0, t1, .L73_unsupported
                lbu  t0, 5(s11)          # 'd'
                li   t1, 100
                bne  t0, t1, .L73_unsupported
                lbu  t0, 6(s11)          # 'b'
                li   t1, 98
                bne  t0, t1, .L73_unsupported
                lbu  t0, 7(s11)          # ':'
                li   t1, 58
                bne  t0, t1, .L73_unsupported
                lbu  t0, 8(s11)          # '/'
                li   t1, 47
                bne  t0, t1, .L73_unsupported
                lbu  t0, 9(s11)          # '/'
                li   t1, 47
                bne  t0, t1, .L73_unsupported
                addi t0, s11, 10
            .L73_scheme_ok:
                mv   s10, t0             # cursor pós-esquema
                # acha '@' antes de '/' ou NUL (userinfo)
                mv   t2, s10
                li   s11, 0              # @ ptr (0 = ausente)
            .L73_find_at:
                lbu  t0, 0(t2)
                beqz t0, .L73_at_done
                li   t1, 47              # '/'
                beq  t0, t1, .L73_at_done
                li   t1, 64              # '@'
                bne  t0, t1, .L73_at_next
                mv   s11, t2
            .L73_at_next:
                addi t2, t2, 1
                j    .L73_find_at
            .L73_at_done:
                beqz s11, .L73_no_at
                # user=[s10, colon), pass=[colon+1, @)
                mv   t2, s10
            .L73_find_colon:
                beq  t2, s11, .L73_colon_done
                lbu  t0, 0(t2)
                li   t1, 58              # ':'
                beq  t0, t1, .L73_colon_done
                addi t2, t2, 1
                j    .L73_find_colon
            .L73_colon_done:
                mv   s3, t2              # colon (== user end)
                mv   a0, s10
                sub  a1, t2, s10
                call kof_io_make_string
                mv   s4, a0              # user
                beq  s3, s11, .L73_up_nopass
                addi a0, s3, 1
                sub  a1, s11, a0
                call kof_io_make_string
                mv   s5, a0              # pass
                j    .L73_host_from_at
            .L73_up_nopass:
                li   s5, 0
                j    .L73_host_from_at
            .L73_no_at:
                mv   s4, s1              # user2
                mv   s5, s2              # pass2
                mv   s7, s10             # host = cursor
                j    .L73_host_scan
            .L73_host_from_at:
                addi s7, s11, 1          # host = pós-'@'
            .L73_host_scan:
                mv   t2, s7
                li   s9, 0               # port
            .L73_hs_host:
                lbu  t0, 0(t2)
                beqz t0, .L73_hs_nul
                li   t1, 47              # '/'
                beq  t0, t1, .L73_hs_slash_noport
                li   t1, 58              # ':'
                beq  t0, t1, .L73_hs_host_colon
                addi t2, t2, 1
                j    .L73_hs_host
            .L73_hs_nul:
                sub  s8, t2, s7          # hostLen
                li   s6, 0               # sem db
                j    .L73_host_done
            .L73_hs_slash_noport:
                sub  s8, t2, s7
                addi a0, t2, 1
                j    .L73_db_parse
            .L73_hs_host_colon:
                sub  s8, t2, s7
                addi t2, t2, 1
            .L73_hs_port:
                lbu  t0, 0(t2)
                beqz t0, .L73_hs_port_nodb
                li   t1, 47              # '/'
                beq  t0, t1, .L73_hs_port_slash
                addi t0, t0, -48
                li   t1, 10
                mul  t4, s9, t1
                add  t4, t4, t0
                mv   s9, t4
                addi t2, t2, 1
                j    .L73_hs_port
            .L73_hs_port_nodb:
                li   s6, 0
                j    .L73_host_done
            .L73_hs_port_slash:
                addi a0, t2, 1
            .L73_db_parse:
                mv   t1, a0
            .L73_db_parse_loop:
                lbu  t0, 0(t1)
                beqz t0, .L73_db_parse_done
                addi t1, t1, 1
                j    .L73_db_parse_loop
            .L73_db_parse_done:
                sub  a1, t1, a0
                call kof_io_make_string
                mv   s6, a0              # db
            .L73_host_done:
                bnez s9, .L73_port_ok
                li   s9, 3306
            .L73_port_ok:
                # sockaddr_in em 96..111: family=AF_INET, port BE (byte a byte)
                li   t0, 2
                sh   t0, 96(sp)
                srli t0, s9, 8
                andi t0, t0, 0xff
                sb   t0, 98(sp)
                andi t0, s9, 0xff
                sb   t0, 99(sp)
                li   t0, 127             # default 127.0.0.1 (fallback x86)
                sb   t0, 100(sp)
                sb   zero, 101(sp)
                sb   zero, 102(sp)
                li   t0, 1
                sb   t0, 103(sp)
                lbu  t0, 0(s7)
                li   t1, 48
                bltu t0, t1, .L73_ip_done
                li   t1, 57
                bltu t1, t0, .L73_ip_done
                mv   t2, s7
                li   t3, 0               # octeto
                li   t4, 0               # acumulador
                mv   t5, s8              # restante = hostLen
            .L73_ip_loop:
                beqz t5, .L73_ip_last
                lbu  t0, 0(t2)
                li   t1, 46              # '.'
                beq  t0, t1, .L73_ip_store
                addi t0, t0, -48
                li   t1, 10
                mul  t4, t4, t1
                add  t4, t4, t0
                addi t2, t2, 1
                addi t5, t5, -1
                j    .L73_ip_loop
            .L73_ip_store:
                add  t6, sp, t3
                sb   t4, 100(t6)
                addi t3, t3, 1
                li   t4, 0
                addi t2, t2, 1
                addi t5, t5, -1
                j    .L73_ip_loop
            .L73_ip_last:
                add  t6, sp, t3
                sb   t4, 100(t6)
            .L73_ip_done:
                li   a0, 2               # AF_INET
                li   a1, 1               # SOCK_STREAM
                li   a2, 0
                call kof_plat_net_socket
                bltz a0, .L73_lost
                mv   s3, a0              # fd
                mv   a0, s3
                addi a1, sp, 96
                li   a2, 16
                call kof_plat_net_connect
                bltz a0, .L73_lost
                mv   a0, s3
                mv   a1, s4
                mv   a2, s5
                mv   a3, s6
                call kof_db_mysql_handshake
                bnez a0, .L73_bad
                # registra: slots[count]=fd, types[count]=2, count++
                la   t0, .Ldb_count
                ld   t1, 0(t0)
                li   t2, 63
                bge  t1, t2, .L73_bad
                la   t2, .Ldb_slots
                slli t3, t1, 3
                add  t2, t2, t3
                sd   s3, 0(t2)
                la   t2, .Ldb_types
                add  t2, t2, t1
                li   t3, 2
                sb   t3, 0(t2)
                addi t1, t1, 1
                la   t2, .Ldb_count
                sd   t1, 0(t2)
                # handle "db<N>" — dígitos ao contrário em buffer @175(sp)
                addi t2, sp, 175
                li   t3, 10
            .L73_itoa:
                remu t4, t1, t3
                addi t4, t4, 48
                sb   t4, 0(t2)
                divu t1, t1, t3
                beqz t1, .L73_itoa_done
                addi t2, t2, -1
                j    .L73_itoa
            .L73_itoa_done:
                mv   t5, t2
                addi t2, t2, -1
                li   t4, 98              # 'b'
                sb   t4, 0(t2)
                addi t2, t2, -1
                li   t4, 100             # 'd'
                sb   t4, 0(t2)
                addi t4, t5, 1
                sub  t4, t4, t2          # len
                mv   s5, t2              # start
                mv   s4, t4              # len
                addi a0, s4, 25
                call kof_alloc
                mv   s6, a0              # KofString*
                li   t0, 1
                sw   t0, 0(s6)
                sw   zero, 4(s6)
                sd   zero, 8(s6)
                sw   s4, 16(s6)
                sw   zero, 20(s6)
                addi a0, s6, 24
                mv   a1, s5
                mv   a2, s4
                call kof_memcpy
                addi t0, s6, 24
                add  t0, t0, s4
                sb   zero, 0(t0)
                la   t0, .Ldb_default_handle
                sd   s6, 0(t0)
                mv   a0, s6
                j    .L73_out
            .L73_unsupported:
                la   a0, .L73_unsup_str
                call kof_throw_string
            # §523 (27/09, escopo votado): falha TCP lanca em vez de handle morto.
            .L73_lost:
                la   a0, .L73_lostv
                call kof_throw_string
            .L73_bad:
                li   a0, 0
            .L73_out:
                ld   s11, 88(sp)
                ld   s10, 80(sp)
                ld   s9, 72(sp)
                ld   s8, 64(sp)
                ld   s7, 56(sp)
                ld   s6, 48(sp)
                ld   s5, 40(sp)
                ld   s4, 32(sp)
                ld   s3, 24(sp)
                ld   s2, 16(sp)
                ld   s1, 8(sp)
                ld   s0, 0(sp)
                ld   ra, 184(sp)
                addi sp, sp, 192
                ret
            .section .data
            .align 2
            # S0/§421: recusa nomeada do esquema fora do contrato cross.
            .L73_unsup_str:
                .long 1
                .long 0
                .quad 0
                .long .L73_unsup_len
                .long 0
            .L73_unsup_body:
                .asciz "DB001: unsupported db scheme (native cross: sqlite:, mysql://, mariadb://)"
                .set .L73_unsup_len, . - .L73_unsup_body - 1
            .L73_lostv:
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
