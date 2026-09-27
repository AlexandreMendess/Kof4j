package dev.kof.compiler.nat;

// D-FULL-PARITY-050 (row 13, native cross lane, 24/09): fatia 5 — kof.io
// size() no cross (riscv64 + aarch64; aarch64 herda pelo tradutor).
// `kof_io_file_size(path)` -> Long. Port do x86 (runtime/RuntimeIo2): newfstatat
// (AT_FDCWD, path+24, statbuf, 0); em erro LANÇA (nao retorna sentinela — o
// contrato e exceçao recuperavel, `catch (String e)`), no sucesso st_size.
// st_size fica em +48 no stat do riscv64/aarch64 (igual ao x86_64).
// Syscall newfstatat=79 (riscv64/aarch64).
//
// §494/D-IO-SIZE-JVM-LAW (27/09): a lei e o JVM — "file not found: " (16
// bytes), sem o prefixo "size: " que o x86 usava antes.
public final class NativeRiscvAsmIoSize {

    private NativeRiscvAsmIoSize() {}

    static String RISCV_RUNTIME_ASM_IO_SIZE = """
            .section .rodata
            .Lstr_io_size_msg:
                .byte 102,105,108,101,32,110,111,116,32,102,111,117,110,100,58,32
            .section .text
            .globl kof_io_file_size
            .type kof_io_file_size, @function
            kof_io_file_size:
                addi sp, sp, -192
                sd   ra, 184(sp)
                sd   s0, 176(sp)
                mv   s0, a0                 # path (KofStr)
                li   a0, -100               # AT_FDCWD
                addi a1, s0, 24             # path bytes
                mv   a2, sp                 # statbuf (144 bytes)
                li   a3, 0
                li   a7, 79                 # newfstatat
                ecall
                bltz a0, .Lkof_iofsz_err
                ld   a0, 48(sp)             # st_size
                ld   s0, 176(sp)
                ld   ra, 184(sp)
                addi sp, sp, 192
                ret
            .Lkof_iofsz_err:
                la   a0, .Lstr_io_size_msg
                li   a1, 16
                call kof_string_from_literal   # a0 = "file not found: "
                mv   a1, s0                    # path (KofStr)
                call kof_string_concat         # a0 = prefixo + path
                call kof_throw_string          # longjmp p/ o try; panic se nao houver
            """;
}
