package dev.kof.compiler.nat;

// D-FULL-PARITY-050 (row 11, native cross lane, 24/09): String.toCharArray() no
// riscv64/aarch64 — devolve Char[] (KofArray, elemSize 4) com os CODE UNITS
// UTF-16 na MESMA ordem/aritmética do JVM e do x86
// (RuntimeStringToCharArray). 1/2/3 bytes → 1 unit; astral (4 bytes) → par
// high/low surrogate. Fecha a face cross do STR003 (o símbolo deixa de cair no
// fallback genérico `java_lang_String_toCharArray`).
//
// Contrato riscv:
//   kof_string_to_char_array(str@a0) -> KofArray* (a0)
public final class NativeRiscvAsmStrToCharArray {

    private NativeRiscvAsmStrToCharArray() {}

    static String RISCV_RUNTIME_ASM_STR_TO_CHAR_ARRAY = """
            .section .text
            # ---------------------------------------------------------------
            # kof_string_to_char_array(str@a0) -> Char[] (elemSize 4)
            # s0=str s1=n s2=arr s3=byteLen s4=byteOff; unitIndex=t3;
            # base dos bytes=t4; temporários t0/t1/t2/t5.
            # ---------------------------------------------------------------
            .globl kof_string_to_char_array
            .type kof_string_to_char_array, @function
            kof_string_to_char_array:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                sd   s3, 8(sp)
                sd   s4, 0(sp)
                mv   s0, a0                 # str
                call kof_string_length      # a0 = nº code units UTF-16
                mv   s1, a0                 # n
                mv   a0, s1
                li   a1, 4                  # Char = 4 bytes
                call kof_array_alloc        # a0 = arr
                mv   s2, a0                 # arr
                lw   s3, 16(s0)             # byteLen (bytes UTF-8)
                li   s4, 0                  # byteOff
                li   t3, 0                  # unitIndex
                addi t4, s0, 24             # bytes base
            .Lkof_tca_walk:
                bgeu s4, s3, .Lkof_tca_done
                add  t0, t4, s4
                lbu  t0, 0(t0)              # lead byte
                andi t1, t0, 128
                beqz t1, .Lkof_tca_a1
                andi t1, t0, 224
                li   t2, 192
                beq  t1, t2, .Lkof_tca_a2
                andi t1, t0, 240
                li   t2, 224
                beq  t1, t2, .Lkof_tca_a3
                j    .Lkof_tca_astral
            .Lkof_tca_a1:
                slli t5, t3, 2
                addi t5, t5, 24
                add  t5, s2, t5
                sw   t0, 0(t5)
                addi t3, t3, 1
                addi s4, s4, 1
                j    .Lkof_tca_walk
            .Lkof_tca_a2:
                add  t0, t4, s4
                lbu  t1, 0(t0)
                andi t1, t1, 31
                slli t1, t1, 6
                lbu  t2, 1(t0)
                andi t2, t2, 63
                or   t1, t1, t2
                slli t5, t3, 2
                addi t5, t5, 24
                add  t5, s2, t5
                sw   t1, 0(t5)
                addi t3, t3, 1
                addi s4, s4, 2
                j    .Lkof_tca_walk
            .Lkof_tca_a3:
                add  t0, t4, s4
                lbu  t1, 0(t0)
                andi t1, t1, 15
                slli t1, t1, 12
                lbu  t2, 1(t0)
                andi t2, t2, 63
                slli t2, t2, 6
                or   t1, t1, t2
                lbu  t2, 2(t0)
                andi t2, t2, 63
                or   t1, t1, t2
                slli t5, t3, 2
                addi t5, t5, 24
                add  t5, s2, t5
                sw   t1, 0(t5)
                addi t3, t3, 1
                addi s4, s4, 3
                j    .Lkof_tca_walk
            .Lkof_tca_astral:
                add  t0, t4, s4
                lbu  t1, 0(t0)
                andi t1, t1, 7
                slli t1, t1, 18
                lbu  t2, 1(t0)
                andi t2, t2, 63
                slli t2, t2, 12
                or   t1, t1, t2
                lbu  t2, 2(t0)
                andi t2, t2, 63
                slli t2, t2, 6
                or   t1, t1, t2
                lbu  t2, 3(t0)
                andi t2, t2, 63
                or   t1, t1, t2
                li   t5, 65536
                sub  t1, t1, t5            # cp - 0x10000
                srli t2, t1, 10
                li   t5, 55296             # 0xD800 high surrogate
                add  t2, t2, t5
                slli t5, t3, 2
                addi t5, t5, 24
                add  t5, s2, t5
                sw   t2, 0(t5)
                addi t3, t3, 1
                andi t1, t1, 1023
                li   t5, 56320             # 0xDC00 low surrogate
                add  t1, t1, t5
                slli t5, t3, 2
                addi t5, t5, 24
                add  t5, s2, t5
                sw   t1, 0(t5)
                addi t3, t3, 1
                addi s4, s4, 4
                j    .Lkof_tca_walk
            .Lkof_tca_done:
                mv   a0, s2
                ld   s4, 0(sp)
                ld   s3, 8(sp)
                ld   s2, 16(sp)
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret
            """;
}
