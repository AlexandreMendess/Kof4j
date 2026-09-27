package dev.kof.compiler.nat;

// D-FULL-PARITY-050 (row 13, native cross lane, 26/09): fatia 16b — kof_io_file_copy_to
// no cross (riscv64 + aarch64). Contrato JVM: Files.copy(src,dst,COPY_ATTRIBUTES)
// -> 1; IOException -> 0; SEM REPLACE_EXISTING -> destino existente = 0.
// Implementacao: newfstatat(src); openat(src,O_RDONLY=56); openat(dst,
// O_WRONLY|O_CREAT|O_EXCL=193,0644=420) (O_EXCL garante o no-overwrite);
// loop read(63)/write(64); fchmod(52) (modo); utimensat(88) (atime/mtime);
// close(57). KofStr bytes@24; struct stat cross: st_mode@16, st_atime@72,
// atime_nsec@80, st_mtime@88, mtime_nsec@96; AT_FDCWD=-100.
public final class NativeRiscvAsmIoCopy {

    private NativeRiscvAsmIoCopy() {}

    static String RISCV_RUNTIME_ASM_IO_COPY = """
            .section .text
            # kof_io_file_copy_to(src@a0, dst@a1) -> 1|0
            .globl kof_io_file_copy_to
            .type kof_io_file_copy_to, @function
            kof_io_file_copy_to:
                addi sp, sp, -720
                sd   ra, 704(sp)
                sd   s0, 696(sp)
                sd   s1, 688(sp)
                sd   s2, 680(sp)
                sd   s3, 672(sp)
                mv   s0, a0
                mv   s1, a1
                li   a7, 79                 # newfstatat(AT_FDCWD, src, sp, 0)
                li   a0, -100
                addi a1, s0, 24
                mv   a2, sp
                li   a3, 0
                ecall
                bltz a0, .Lkof_iocp_zero
                ld   t0, 72(sp)             # times[0] = atime
                sd   t0, 128(sp)
                ld   t0, 80(sp)
                sd   t0, 136(sp)
                ld   t0, 88(sp)             # times[1] = mtime
                sd   t0, 144(sp)
                ld   t0, 96(sp)
                sd   t0, 152(sp)
                li   a7, 56                 # openat(AT_FDCWD, src, O_RDONLY, 0)
                li   a0, -100
                addi a1, s0, 24
                li   a2, 0
                li   a3, 0
                ecall
                bltz a0, .Lkof_iocp_zero
                mv   s2, a0
                li   a7, 56                 # openat(AT_FDCWD, dst, O_WRONLY|O_CREAT|O_EXCL, 0644)
                li   a0, -100
                addi a1, s1, 24
                li   a2, 193
                li   a3, 420
                ecall
                bltz a0, .Lkof_iocp_closesrc
                mv   s3, a0
            .Lkof_iocp_loop:
                li   a7, 63                 # read(srcd, sp+160, 512)
                mv   a0, s2
                addi a1, sp, 160
                li   a2, 512
                ecall
                blez a0, .Lkof_iocp_attrs
                mv   a2, a0
                li   a7, 64                 # write(dstd, sp+160, n)
                mv   a0, s3
                addi a1, sp, 160
                ecall
                bltz a0, .Lkof_iocp_fail
                j    .Lkof_iocp_loop
            .Lkof_iocp_attrs:
                lw   t0, 16(sp)             # st_mode
                li   t1, 4095
                and  t0, t0, t1
                li   a7, 52                 # fchmod(dstd, mode)
                mv   a0, s3
                mv   a1, t0
                ecall
                li   a7, 88                 # utimensat(AT_FDCWD, dst, sp+128, 0)
                li   a0, -100
                addi a1, s1, 24
                addi a2, sp, 128
                li   a3, 0
                ecall
                li   a7, 57
                mv   a0, s3
                ecall
                li   a7, 57
                mv   a0, s2
                ecall
                li   a0, 1
                j    .Lkof_iocp_ret
            .Lkof_iocp_fail:
                li   a7, 57
                mv   a0, s3
                ecall
            .Lkof_iocp_closesrc:
                li   a7, 57
                mv   a0, s2
                ecall
            .Lkof_iocp_zero:
                li   a0, 0
            .Lkof_iocp_ret:
                ld   ra, 704(sp)
                ld   s0, 696(sp)
                ld   s1, 688(sp)
                ld   s2, 680(sp)
                ld   s3, 672(sp)
                addi sp, sp, 720
                ret
            """;
}
