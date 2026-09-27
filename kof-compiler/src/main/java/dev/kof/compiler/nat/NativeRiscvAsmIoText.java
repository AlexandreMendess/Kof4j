package dev.kof.compiler.nat;

// D-FULL-PARITY-050 (row 13, native cross lane, 24/09): faces de TEXTO do
// kof.io no cross (riscv64 + aarch64; aarch64 herda pelo tradutor). Fatia 2:
// readText()/writeText()/appendText(). Syscalls genericos do Linux (identicos
// nas duas archs): openat=56, close=57, read=63, write=64, fstat=80.
// Port do x86 (RuntimeTime.kof_read_file/kof_write_file + RuntimeIo2):
//   - read_text: openat(O_RDONLY) -> fstat (st_size em +48) -> alloc(size+25)
//     com header KofStr -> read para +24 -> NUL -> close. Miss = 0 (null),
//     como o JVM (kof_read_file devolve null no IOException).
//   - write_text/append_text: openat(O_WRONLY|O_CREAT|O_TRUNC|O_APPEND, 0644)
//     -> write(content+24, byteLen em +16) -> close -> 1|0.
public final class NativeRiscvAsmIoText {

    private NativeRiscvAsmIoText() {}

    static String RISCV_RUNTIME_ASM_IO_TEXT = """
            .section .text
            # ---------------------------------------------------------------
            # kof_io_read_text(path@a0) -> KofStr* | 0 (null)
            # statbuf em sp[0..127] (struct stat 128B); st_size em +48.
            # ---------------------------------------------------------------
            .globl kof_io_read_text
            .type kof_io_read_text, @function
            kof_io_read_text:
                addi sp, sp, -176
                sd   ra, 168(sp)
                sd   s0, 160(sp)
                sd   s1, 152(sp)
                sd   s2, 144(sp)
                sd   s3, 136(sp)
                mv   s0, a0
                addi a1, s0, 24             # path bytes (KofStr + 24)
                li   a0, -100               # AT_FDCWD
                li   a2, 0                  # O_RDONLY
                li   a3, 0                  # mode
                li   a7, 56                 # openat
                ecall
                bltz a0, .Lkof_rt_null
                mv   s1, a0                 # fd
                mv   a0, s1
                mv   a1, sp                 # statbuf
                li   a7, 80                 # fstat
                ecall
                ld   s2, 48(sp)             # st_size
                addi a0, s2, 25
                call kof_alloc
                mv   s3, a0
                li   t0, 1
                sw   t0, 0(s3)
                sw   zero, 4(s3)
                sd   zero, 8(s3)
                sw   s2, 16(s3)             # byteLen
                sw   zero, 20(s3)
                mv   a0, s1
                addi a1, s3, 24
                mv   a2, s2
                li   a7, 63                 # read
                ecall
                addi t0, s3, 24
                add  t0, t0, s2
                sb   zero, 0(t0)            # NUL-terminate
                mv   a0, s1
                li   a7, 57                 # close
                ecall
                mv   a0, s3
                j    .Lkof_rt_done
            .Lkof_rt_null:
                li   a0, 0
            .Lkof_rt_done:
                ld   s3, 136(sp)
                ld   s2, 144(sp)
                ld   s1, 152(sp)
                ld   s0, 160(sp)
                ld   ra, 168(sp)
                addi sp, sp, 176
                ret

            # kof_io_write_text(path@a0, content@a1) -> 1|0 (O_TRUNC)
            .globl kof_io_write_text
            .type kof_io_write_text, @function
            kof_io_write_text:
                li   t1, 577                # O_WRONLY|O_CREAT|O_TRUNC
                j    .Lkof_iot_common
            # kof_io_append_text(path@a0, content@a1) -> 1|0 (O_APPEND)
            .globl kof_io_append_text
            .type kof_io_append_text, @function
            kof_io_append_text:
                li   t1, 1089               # O_WRONLY|O_CREAT|O_APPEND
            .Lkof_iot_common:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                sd   t1, 8(sp)              # flags
                mv   s0, a0
                mv   s1, a1
                addi a1, s0, 24             # path bytes
                li   a0, -100               # AT_FDCWD
                ld   a2, 8(sp)              # flags
                li   a3, 420                # mode 0644
                li   a7, 56                 # openat
                ecall
                bltz a0, .Lkof_iot_fail
                mv   s2, a0                 # fd
                mv   a0, s2
                addi a1, s1, 24             # content bytes
                lw   a2, 16(s1)             # byteLen
                li   a7, 64                 # write
                ecall
                mv   a0, s2
                li   a7, 57                 # close
                ecall
                li   a0, 1
                j    .Lkof_iot_done
            .Lkof_iot_fail:
                li   a0, 0
            .Lkof_iot_done:
                ld   s2, 16(sp)
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret
            """;
}
