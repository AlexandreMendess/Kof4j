package dev.kof.compiler.nat;

// S5.1 (db-parity-plan, gaps-db lane, 23/09): parse do greeting do servidor
// MySQL no cross — extrai os 20 bytes do seed (auth-plugin-data) do pacote de
// handshake. Port do trecho de RuntimeDb3 (x86) para riscv64; aarch64 herda via
// tradutor. Depende das pecas B62/B63 (SHA1/bswap + scramble).
//
// Contrato riscv (mirror do x86):
//   kof_db_mysql_parse_greeting(a0=pacote, a1=out20) -> a0=1 ok | 0 falha
//   Pacote: [0..2] len LE | [3] seq | [4] protocolo 0x0A | [5..] versao NUL |
//   conn-id 4B | seed1 8B | filler 1B | caps 2B | charset 1B | status 2B |
//   caps 2B | auth-len 1B | reserved 10B | seed2 12B(NUL). Igual ao x86,
//   seed2 curto nao preenche o resto: o chamador zera os 20 bytes antes.
public final class NativeRiscvAsmRtB64 {

    private NativeRiscvAsmRtB64() {}

    static String RISCV_RUNTIME_ASM_B_64 = """
            .section .text
            # ---------------------------------------------------------------
            # kof_db_mysql_parse_greeting(a0=pacote, a1=out20) -> a0=1 ok | 0 falha
            # Funcao folha (sem call): t0..t5/a2 livres.
            # ---------------------------------------------------------------
            .globl kof_db_mysql_parse_greeting
            .type kof_db_mysql_parse_greeting, @function
            kof_db_mysql_parse_greeting:
                lbu  t0, 4(a0)
                li   t1, 0x0A
                bne  t0, t1, .L64_bad
                addi a2, a0, 5
                # pula versao (NUL-terminated)
            .L64_skip_ver:
                lbu  t0, 0(a2)
                beqz t0, .L64_skip_ver_done
                addi a2, a2, 1
                j    .L64_skip_ver
            .L64_skip_ver_done:
                addi a2, a2, 1
                addi a2, a2, 4
                # seed1: 8 bytes → out[0..7]
                li   t2, 0
            .L64_seed8:
                li   t3, 8
                bge  t2, t3, .L64_seed8_done
                add  t4, a2, t2
                lbu  t5, 0(t4)
                add  t4, a1, t2
                sb   t5, 0(t4)
                addi t2, t2, 1
                j    .L64_seed8
            .L64_seed8_done:
                addi a2, a2, 8
                addi a2, a2, 19
                # seed2: ate 12 bytes → out[8..19], para no NUL
                li   t2, 0
            .L64_seed12:
                li   t3, 12
                bge  t2, t3, .L64_seed12_done
                add  t4, a2, t2
                lbu  t5, 0(t4)
                beqz t5, .L64_seed12_done
                addi t4, t2, 8
                add  t4, a1, t4
                sb   t5, 0(t4)
                addi t2, t2, 1
                j    .L64_seed12
            .L64_seed12_done:
                li   a0, 1
                ret
            .L64_bad:
                li   a0, 0
                ret
            .section .text
            """;
}
