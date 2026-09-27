package dev.kof.compiler.nat;

// D-FULL-PARITY-050 (row 13, native cross lane, 24/09): fatia 6 — bytes do
// kof.io no cross (riscv64 + aarch64; aarch64 herda pelo tradutor).
// Port de RuntimeIo2/RuntimeIo3:
//   kof_io_read_bytes   (path)     -> Int[]|0  (openat O_RDONLY, fstat st_size,
//                                                read, kof_array_alloc(size,4))
//   kof_io_write_bytes  (path,arr) -> Bool     (openat O_TRUNC 577, low byte,
//                                                write, close)
//   kof_io_append_bytes (path,arr) -> Bool     (O_APPEND 1089)
// Syscalls riscv64/aarch64: openat=56, close=57, read=63, write=64, fstat=80.
// Int[] KofArray: len@16, elemSize@20, data@24, stride=4. Diferente do x86 (que
// usa movb sem zerar o resto do slot), o cross grava o word inteiro com lbu
// (zero-extend), batendo com o oraculo JVM (0..255).
public final class NativeRiscvAsmIoBytes {

    private NativeRiscvAsmIoBytes() {}

    static String RISCV_RUNTIME_ASM_IO_BYTES = """
            .section .text
            # kof_io_read_bytes(path@a0) -> Int[]|0
            .globl kof_io_read_bytes
            .type kof_io_read_bytes, @function
            kof_io_read_bytes:
                addi sp, sp, -192
                sd   ra, 184(sp)
                sd   s0, 176(sp)
                sd   s1, 168(sp)
                sd   s2, 160(sp)
                sd   s3, 152(sp)
                mv   s0, a0
                li   a0, -100
                addi a1, s0, 24
                li   a2, 0
                li   a3, 0
                li   a7, 56
                ecall
                bltz a0, .Lkof_iorb_err
                mv   s1, a0                 # fd
                mv   a0, s1
                mv   a1, sp
                li   a7, 80
                ecall
                ld   s2, 48(sp)             # st_size
                mv   a0, s2
                call kof_alloc
                mv   s3, a0                 # data
                mv   a0, s1
                mv   a1, s3
                mv   a2, s2
                li   a7, 63
                ecall
                mv   a0, s1
                li   a7, 57
                ecall
                mv   a0, s2
                li   a1, 4
                call kof_array_alloc
                mv   s1, a0                 # Int[]
                li   t0, 0
            .Lkof_iorb_spread:
                bgeu t0, s2, .Lkof_iorb_done
                add  t1, s3, t0
                lbu  t1, 0(t1)
                slli t2, t0, 2
                add  t2, s1, t2
                sw   t1, 24(t2)
                addi t0, t0, 1
                j    .Lkof_iorb_spread
            .Lkof_iorb_done:
                mv   a0, s1
                j    .Lkof_iorb_ret
            .Lkof_iorb_err:
                li   a0, 0
            .Lkof_iorb_ret:
                ld   s3, 152(sp)
                ld   s2, 160(sp)
                ld   s1, 168(sp)
                ld   s0, 176(sp)
                ld   ra, 184(sp)
                addi sp, sp, 192
                ret

            # helper: openat(AT_FDCWD, path@a0 + 24, flags@a1, 420)
            #   -> fd ou negativo. Preserva s0.
            .type kof_io_open_w, @function
            kof_io_open_w:
                mv   a2, a1
                addi a1, a0, 24
                li   a0, -100
                li   a3, 420
                li   a7, 56
                ecall
                ret

            # kof_io_write_bytes(path@a0, Int[]@a1) -> Bool
            .globl kof_io_write_bytes
            .type kof_io_write_bytes, @function
            kof_io_write_bytes:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)
                sd   s1, 40(sp)
                sd   s2, 32(sp)
                sd   s3, 24(sp)
                mv   s1, a1
                li   a1, 577
                call kof_io_open_w
                bltz a0, .Lkof_iowb_err
                mv   s0, a0                 # fd
                j    .Lkof_iowb_flush
            .Lkof_iowb_err:
                li   a0, 0
                j    .Lkof_iowb_ret

            # kof_io_append_bytes(path@a0, Int[]@a1) -> Bool
            .globl kof_io_append_bytes
            .type kof_io_append_bytes, @function
            kof_io_append_bytes:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)
                sd   s1, 40(sp)
                sd   s2, 32(sp)
                sd   s3, 24(sp)
                mv   s1, a1
                li   a1, 1089
                call kof_io_open_w
                bltz a0, .Lkof_ioab_err
                mv   s0, a0                 # fd
                j    .Lkof_iowb_flush
            .Lkof_ioab_err:
                li   a0, 0
                j    .Lkof_iowb_ret

            # corpo comum write/append: fd@fd, arr@s1 -> 1|0 (fd ja aberto)
            .Lkof_iowb_flush:
                lw   s2, 16(s1)             # len
                addi a0, s2, 16
                call kof_alloc
                mv   s3, a0                 # buf
                li   t0, 0
            .Lkof_iowb_copy:
                bgeu t0, s2, .Lkof_iowb_write
                slli t1, t0, 2
                add  t1, s1, t1
                lbu  t1, 24(t1)
                add  t2, s3, t0
                sb   t1, 0(t2)
                addi t0, t0, 1
                j    .Lkof_iowb_copy
            .Lkof_iowb_write:
                mv   a0, s0
                mv   a1, s3
                mv   a2, s2
                li   a7, 64
                ecall
                mv   a0, s0
                li   a7, 57
                ecall
                li   a0, 1
            .Lkof_iowb_ret:
                ld   s3, 24(sp)
                ld   s2, 32(sp)
                ld   s1, 40(sp)
                ld   s0, 48(sp)
                ld   ra, 56(sp)
                addi sp, sp, 64
                ret
            """;
}
