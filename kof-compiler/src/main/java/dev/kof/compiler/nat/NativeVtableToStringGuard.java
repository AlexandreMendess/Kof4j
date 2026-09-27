package dev.kof.compiler.nat;

/**
 * §396-cross (21/09): guard de NULL no call-site do dispatch
 * {@code String.valueOf(objeto com toString na vtable)} no riscv64 — o
 * {@code ld t0, 8(a0)} desreferenciava o ponteiro sem teste e um
 * {@code T?}-API devolvendo {@code null} SIGSEGVava no {@code println}
 * cru (a JVM imprime "null" — {@code String.valueOf(null-ref)}). O guard
 * roda no ENTRY: null → {@code kof_string_from_literal("null",4)}; não
 * null → o dispatch vtable original, byte-a-byte. Resíduo portado da
 * face x86 corrigida pela lane gaps-db ({@code NativeX86ValueOf} — a
 * solução x86 NÃO é duplicada aqui). aarch64 herda o guard via tradutor
 * riscv→aarch (beqz/la/li/jal + {@code .section .rodata}/{@code .asciz}
 * já tratados lá). Extraído para classe própria pelo gate check_500.
 */
public final class NativeVtableToStringGuard {

    private NativeVtableToStringGuard() {}

    /** riscv64: pilha com receiver em topo; saída = String em {@code a0}
     *  empurrada pelo CALLER (pushRiscv) — este método devolve com o
     *  resultado em {@code a0}. */
    static void emitRiscv(StringBuilder sb, NativeBackend nb, int tosIdx) {
        int ln = nb.printDescriptorCounter++;
        sb.append("    pop a0\n");
        sb.append("    beqz a0, .Lkvtn").append(ln).append("\n");
        sb.append("    ld t0, 8(a0)\n");
        sb.append("    addi t0, t0, ").append(tosIdx * 8).append("\n");
        sb.append("    ld t0, 0(t0)\n");
        sb.append("    jalr t0\n");
        sb.append("    j .Lkvte").append(ln).append("\n");
        sb.append(".Lkvtn").append(ln).append(":\n");
        sb.append("    la a0, .Lkvtl").append(ln).append("\n");
        sb.append("    li a1, 4\n");
        sb.append("    call kof_string_from_literal\n");
        sb.append(".Lkvte").append(ln).append(":\n");
        sb.append("    .section .rodata\n");
        sb.append(".Lkvtl").append(ln).append(": .asciz \"null\"\n");
        sb.append("    .section .text\n");
    }
}
