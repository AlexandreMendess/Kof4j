package dev.kof.compiler.nat;

// S5.2 (db-parity-plan, gaps-db lane, 23/09): COM_QUERY + classificacao da
// resposta no cross. Envia um comando texto (framing 3-byte len LE + seq 0 +
// payload [0x03][sql]) e le o primeiro pacote de resposta, devolvendo o
// payload para o chamador classificar (>=1 = column count de um resultset,
// 0x00 = OK, 0xFF = ERR). Port do framing de RuntimeDb2 (`kof_db_mysql_next`
// le um pacote) + o envio de RuntimeDb3, para riscv64; aarch64 herda via
// tradutor. Depende do B66 (handshake) para a conexao estar autenticada.
//
// Contrato riscv:
//   kof_db_mysql_command(a0=fd, a1=sql KofString, a2=buf, a3=buflen)
//       -> a0 = payloadLen (>=0) | -1 falha de I/O / sql longo; a1 = buf+4
//   (o payload comeca no byte [4] do pacote lido; seq do request = 0)
public final class NativeRiscvAsmRtB67 {

    private NativeRiscvAsmRtB67() {}

    static String RISCV_RUNTIME_ASM_B_67 = """
            .section .text
            # ---------------------------------------------------------------
            # kof_db_mysql_command(a0=fd,a1=sql,a2=buf,a3=buflen) -> a0=len|-1, a1=buf+4
            # Frame 8368: ra+s0..s4 (0..79) | request 8192 (128..8319)
            # ---------------------------------------------------------------
            .globl kof_db_mysql_command
            .type kof_db_mysql_command, @function
            kof_db_mysql_command:
                li   t6, 8368
                sub  sp, sp, t6
                sd   ra, 0(sp)
                sd   s0, 8(sp)
                sd   s1, 16(sp)
                sd   s2, 24(sp)
                sd   s3, 32(sp)
                sd   s4, 40(sp)
                mv   s0, a0
                mv   s1, a1
                mv   s2, a2
                mv   s3, a3
                lw   s4, 16(s1)          # sqllen
                li   t0, 8000
                bgt  s4, t0, .L67_fail
                # header: len = sqllen + 1, seq 0
                addi t0, s4, 1
                andi t1, t0, 0xff
                sb   t1, 128(sp)
                srli t1, t0, 8
                andi t1, t1, 0xff
                sb   t1, 129(sp)
                srli t1, t0, 16
                andi t1, t1, 0xff
                sb   t1, 130(sp)
                sb   zero, 131(sp)
                li   t1, 3
                sb   t1, 132(sp)         # COM_QUERY
                # copia o sql para o request
                addi t1, s1, 24
                li   t2, 0
            .L67_copy:
                bge  t2, s4, .L67_copy_done
                add  t3, t1, t2
                lbu  t4, 0(t3)
                add  t5, sp, t2
                addi t5, t5, 133
                sb   t4, 0(t5)
                addi t2, t2, 1
                j    .L67_copy
            .L67_copy_done:
                # write(fd, req, sqllen+5)
                mv   a0, s0
                addi a1, sp, 128
                addi a2, s4, 5
                call kof_plat_write
                # read(fd, buf, buflen)
                mv   a0, s0
                mv   a1, s2
                mv   a2, s3
                call kof_plat_read
                blez a0, .L67_fail
                # payloadLen = buf[0] | buf[1]<<8 | buf[2]<<16
                lbu  t0, 0(s2)
                lbu  t1, 1(s2)
                slli t1, t1, 8
                or   t0, t0, t1
                lbu  t1, 2(s2)
                slli t1, t1, 16
                or   t0, t0, t1
                addi a1, s2, 4
                mv   a0, t0
                j    .L67_ret
            .L67_fail:
                li   a0, -1
                mv   a1, s2
            .L67_ret:
                ld   ra, 0(sp)
                ld   s0, 8(sp)
                ld   s1, 16(sp)
                ld   s2, 24(sp)
                ld   s3, 32(sp)
                ld   s4, 40(sp)
                li   t6, 8368
                add  sp, sp, t6
                ret
            .section .text
            """;
}
