package dev.kof.compiler.nat;

// D-FULL-PARITY-050 (row 13, native cross lane, 24/09): fatia 12 — kof_io_path_to_absolute
// no cross (riscv64 + aarch64). Port de RuntimeIo1:
//   path absoluto (path[0]=='/') -> path
//   senao getcwd(buf,4096) (__NR_getcwd=17) e resolve(cwd, path)
//   getcwd falhou -> devolve path
// KofStr: len@16, bytes@24. kof_io_strlen/kof_io_make_string/kof_io_path_resolve ja no cross.
public final class NativeRiscvAsmIoToAbsolute {

    private NativeRiscvAsmIoToAbsolute() {}

    static String RISCV_RUNTIME_ASM_IO_TOABSOLUTE = """
            .section .text
            # kof_io_path_to_absolute(path@a0) -> KofStr*
            .globl kof_io_path_to_absolute
            .type kof_io_path_to_absolute, @function
            kof_io_path_to_absolute:
                li   t0, -4112
                add  sp, sp, t0
                sd   ra, 0(sp)
                sd   s0, 8(sp)
                mv   s0, a0
                lbu  t0, 24(s0)
                li   t1, 47
                beq  t0, t1, .Lkof_iotoabs_ret
                addi a0, sp, 16             # getcwd(buf, 4096)
                li   a1, 4096
                li   a7, 17
                ecall
                bltz a0, .Lkof_iotoabs_ret
                addi a0, sp, 16
                call kof_io_strlen
                mv   a1, a0
                addi a0, sp, 16
                call kof_io_make_string
                mv   a1, s0
                call kof_io_path_resolve
                j    .Lkof_iotoabs_done
            .Lkof_iotoabs_ret:
                mv   a0, s0
            .Lkof_iotoabs_done:
                ld   ra, 0(sp)
                ld   s0, 8(sp)
                li   t0, 4112
                add  sp, sp, t0
                ret
            """;
}
