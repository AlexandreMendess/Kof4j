package dev.kof.c;

import java.util.List;

/**
 * Alvo de emissão do subconjunto C (plano em fatias,
 * {@code docs/development/kof-c-cross.md}).
 *
 * <p>{@link #X86_64} é o alvo histórico: freestanding, montado por
 * {@code as --64} e ligado por {@code ld} no próprio host. {@link #RISCV64} e
 * {@link #AARCH64} usam os binutils cross (prefixados) e o binário freestanding
 * roda sob {@code qemu-<arch>} — nenhuma libc/linker dinâmico é necessário
 * porque o subconjunto só fala syscalls cruas.
 */
public enum KofCTarget {
    X86_64(List.of("as", "--64"), "ld", null),
    RISCV64(List.of("riscv64-linux-gnu-as"), "riscv64-linux-gnu-ld", "qemu-riscv64"),
    AARCH64(List.of("aarch64-linux-gnu-as"), "aarch64-linux-gnu-ld", "qemu-aarch64");

    private final List<String> assembler;
    private final String linker;
    private final String qemu;

    KofCTarget(List<String> assembler, String linker, String qemu) {
        this.assembler = assembler;
        this.linker = linker;
        this.qemu = qemu;
    }

    /** Comando do assembler (sem os arquivos de entrada/saída). */
    public List<String> assembler() { return assembler; }

    /** Binário do linker. */
    public String linker() { return linker; }

    /** Emulador user-mode do alvo, ou {@code null} quando roda nativo (x86_64). */
    public String qemu() { return qemu; }

    /** Aceita os nomes usados pela CLI ({@code kof c --target ...}). */
    public static KofCTarget parse(String s) {
        return switch (s) {
            case "x86_64", "x86", "amd64", "native" -> X86_64;
            case "riscv64", "riscv" -> RISCV64;
            case "aarch64", "arm64" -> AARCH64;
            default -> throw new IllegalArgumentException("unknown target: " + s);
        };
    }
}
