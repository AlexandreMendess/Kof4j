package dev.kof.compiler.nat;

// S5.1 (db-parity-plan, gaps-db lane, 23/09): monta o pacote de auth
// (handshake response) do MySQL no cross — capabilities + user + auth-plugin
// data (scramble) + database + plugin name, com o header de pacote (len LE +
// seq 1). Port do trecho de RuntimeDb3 (x86) para riscv64; aarch64 herda via
// tradutor. Fecha o material do handshake (B62 SHA1, B63 scramble/lenenc,
// B64 greeting); o envio/recepcao pelo socket usa a HAL kof_plat_*.
//
// Contrato riscv (mirror do x86):
//   kof_db_mysql_build_auth_response(a0=buf, a1=scramble20, a2=user KofString,
//                                    a3=db KofString, a4=passLen) -> a0=payloadLen
//   buf[0..2]=payloadLen LE, buf[3]=seq 1, buf[4..]=payload.
//   passLen 0 => auth-response vazio (1 byte 0); senao 20 + scramble.
public final class NativeRiscvAsmRtB65 {

    private NativeRiscvAsmRtB65() {}

    static String RISCV_RUNTIME_ASM_B_65 = """
            .section .rodata
            .L65_plugin:
                .asciz "mysql_native_password"
            .section .text
            # ---------------------------------------------------------------
            # kof_db_mysql_build_auth_response(a0=buf, a1=scramble20,
            #   a2=user KofString, a3=db KofString, a4=passLen) -> a0=payloadLen
            # Folha (sem call). s0=buf s1=cursor s2=scramble s3=user s4=db s5=passLen.
            # ---------------------------------------------------------------
            .globl kof_db_mysql_build_auth_response
            .type kof_db_mysql_build_auth_response, @function
            kof_db_mysql_build_auth_response:
                addi sp, sp, -48
                sd   s0, 0(sp)
                sd   s1, 8(sp)
                sd   s2, 16(sp)
                sd   s3, 24(sp)
                sd   s4, 32(sp)
                sd   s5, 40(sp)
                mv   s0, a0
                mv   s2, a1
                mv   s3, a2
                mv   s4, a3
                mv   s5, a4
                # capabilities 0x0008820B, max-packet 0x01000000, charset 0x21
                li   t0, 0x0008820B
                sw   t0, 4(s0)
                li   t0, 0x01000000
                sw   t0, 8(s0)
                li   t0, 0x21
                sb   t0, 12(s0)
                # reservado: 23 bytes zerados em 13..35
                addi s1, s0, 13
                li   t0, 0
            .L65_zero:
                li   t1, 23
                bge  t0, t1, .L65_zero_done
                add  t2, s1, t0
                sb   zero, 0(t2)
                addi t0, t0, 1
                j    .L65_zero
            .L65_zero_done:
                addi s1, s0, 36
                # user (KofString: len@16, payload@24)
                li   t2, 0
                beqz s3, .L65_user_adv
                lw   t0, 16(s3)
                addi t1, s3, 24
            .L65_user_copy:
                bge  t2, t0, .L65_user_adv
                add  t3, t1, t2
                lbu  t4, 0(t3)
                add  t5, s1, t2
                sb   t4, 0(t5)
                addi t2, t2, 1
                j    .L65_user_copy
            .L65_user_adv:
                add  s1, s1, t2
                sb   zero, 0(s1)
                addi s1, s1, 1
                # auth response: passLen 0 => 1 byte 0; senao 20 + scramble
                beqz s5, .L65_no_pass
                li   t0, 20
                sb   t0, 0(s1)
                addi s1, s1, 1
                li   t2, 0
            .L65_scramble_copy:
                li   t0, 20
                bge  t2, t0, .L65_scramble_done
                add  t3, s2, t2
                lbu  t4, 0(t3)
                add  t5, s1, t2
                sb   t4, 0(t5)
                addi t2, t2, 1
                j    .L65_scramble_copy
            .L65_scramble_done:
                addi s1, s1, 20
                j    .L65_db
            .L65_no_pass:
                sb   zero, 0(s1)
                addi s1, s1, 1
            .L65_db:
                # db (KofString) + NUL
                li   t2, 0
                beqz s4, .L65_db_adv
                lw   t0, 16(s4)
                addi t1, s4, 24
            .L65_db_copy:
                bge  t2, t0, .L65_db_adv
                add  t3, t1, t2
                lbu  t4, 0(t3)
                add  t5, s1, t2
                sb   t4, 0(t5)
                addi t2, t2, 1
                j    .L65_db_copy
            .L65_db_adv:
                add  s1, s1, t2
                sb   zero, 0(s1)
                addi s1, s1, 1
                # plugin "mysql_native_password" + NUL
                la   t0, .L65_plugin
            .L65_plugin_copy:
                lbu  t1, 0(t0)
                sb   t1, 0(s1)
                addi t0, t0, 1
                addi s1, s1, 1
                bnez t1, .L65_plugin_copy
                # header: len = end - (buf+4), seq 1
                sub  t0, s1, s0
                addi t0, t0, -4
                andi t1, t0, 0xff
                sb   t1, 0(s0)
                srli t1, t0, 8
                andi t1, t1, 0xff
                sb   t1, 1(s0)
                srli t1, t0, 16
                andi t1, t1, 0xff
                sb   t1, 2(s0)
                li   t1, 1
                sb   t1, 3(s0)
                mv   a0, t0
                ld   s0, 0(sp)
                ld   s1, 8(sp)
                ld   s2, 16(sp)
                ld   s3, 24(sp)
                ld   s4, 32(sp)
                ld   s5, 40(sp)
                addi sp, sp, 48
                ret
            .section .text
            """;
}
