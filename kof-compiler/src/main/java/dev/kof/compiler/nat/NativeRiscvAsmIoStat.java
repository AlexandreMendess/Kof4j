package dev.kof.compiler.nat;

// D-FULL-PARITY-050 (row 13, native cross lane, 24/09): faces de ESTAT de
// kof.io no cross (riscv64 + aarch64; aarch64 herda pelo tradutor). Primeira
// fatia da linha 13: `File/Path/Directory.exists()/isFile()/isDirectory()`.
// A tabela generica de syscalls do Linux e IDENTICA em riscv64 e aarch64
// (openat=56, read=63, write=64, close=57, newfstatat=79) -> uma fatia serve
// as duas archs. O `st_mode` fica no offset 16 do `struct stat` nas duas.
//
// Contrato riscv (ABI do HAL/render): args em a0..; retorno em a0.
//   kof_io_stat_mode(path@a0) -> mode | -1
//   kof_io_file_exists(path@a0) -> 1|0
//   kof_io_file_is_file(path@a0) -> 1|0
//   kof_io_file_is_dir(path@a0)  -> 1|0
public final class NativeRiscvAsmIoStat {

    private NativeRiscvAsmIoStat() {}

    static String RISCV_RUNTIME_ASM_IO_STAT = """
            .section .text
            # ---------------------------------------------------------------
            # kof_io_stat_mode(path@a0) -> st_mode | -1
            # newfstatat(AT_FDCWD=-100, path, statbuf, flags=0); struct stat
            # (128B nas duas archs) na pilha, st_mode em +16 (riscv64/aarch64;
            # x86_64 e +24). a0 = KofString OBJETO -> os bytes UTF-8 ficam em
            # a0+24 (mesmo contrato do x86, RuntimeIo1).
            # ---------------------------------------------------------------
            .globl kof_io_stat_mode
            .type kof_io_stat_mode, @function
            kof_io_stat_mode:
                addi sp, sp, -144
                addi a1, a0, 24             # pathname (bytes da KofString)
                li   a0, -100               # AT_FDCWD
                mv   a2, sp                 # statbuf
                li   a3, 0                  # flags
                li   a7, 79                 # newfstatat
                ecall
                bltz a0, .Lkof_istat_err
                lw   a0, 16(sp)             # st_mode
                addi sp, sp, 144
                ret
            .Lkof_istat_err:
                li   a0, -1
                addi sp, sp, 144
                ret

            # kof_io_file_exists(path@a0) -> 1|0 (stat OK, qualquer tipo)
            .globl kof_io_file_exists
            .type kof_io_file_exists, @function
            kof_io_file_exists:
                addi sp, sp, -16
                sd   ra, 8(sp)
                call kof_io_stat_mode
                ld   ra, 8(sp)
                addi sp, sp, 16
                bltz a0, .Lkof_iexists_no
                li   a0, 1
                ret
            .Lkof_iexists_no:
                li   a0, 0
                ret

            # kof_io_file_is_file(path@a0) -> 1|0 (S_IFMT == S_IFREG)
            .globl kof_io_file_is_file
            .type kof_io_file_is_file, @function
            kof_io_file_is_file:
                addi sp, sp, -16
                sd   ra, 8(sp)
                call kof_io_stat_mode
                ld   ra, 8(sp)
                addi sp, sp, 16
                bltz a0, .Lkof_iisfile_no
                li   t0, 61440              # S_IFMT
                and  a0, a0, t0
                li   t0, 32768              # S_IFREG
                bne  a0, t0, .Lkof_iisfile_no
                li   a0, 1
                ret
            .Lkof_iisfile_no:
                li   a0, 0
                ret

            # kof_io_file_is_dir(path@a0) -> 1|0 (S_IFMT == S_IFDIR)
            .globl kof_io_file_is_dir
            .type kof_io_file_is_dir, @function
            kof_io_file_is_dir:
                addi sp, sp, -16
                sd   ra, 8(sp)
                call kof_io_stat_mode
                ld   ra, 8(sp)
                addi sp, sp, 16
                bltz a0, .Lkof_iisdir_no
                li   t0, 61440              # S_IFMT
                and  a0, a0, t0
                li   t0, 16384              # S_IFDIR
                bne  a0, t0, .Lkof_iisdir_no
                li   a0, 1
                ret
            .Lkof_iisdir_no:
                li   a0, 0
                ret
            """;
}
