package dev.kof.compiler;

public enum Target {
    JVM,
    NATIVE,
    NATIVE_RISCV64,
    NATIVE_AARCH64,
    /**
     * B-4 (PLAN-BAREMETAL-BOOT): MCU RV32I (bare-metal, sem SO). Emissor de
     * slice mínimo — {@code main} com {@code print/println} de literal
     * String, saída pela UART do {@code -M virt} (0x10000000) e poweroff no
     * test device (0x100000). Ops fora desse subset → NATIVE002 (R6, nunca
     * stub silencioso). Sem superfície CLI ainda (machinery programática).
     */
    NATIVE_RISCV32,
    /**
     * B-4.3 (PLAN-BAREMETAL-BOOT): MCU ARM Cortex-M3 (Thumb-2, bare-metal,
     * sem SO). Mesmo contrato do RV32I: {@code main} com
     * {@code print/println} de literal String, saída pela UART CMSDK do
     * {@code qemu-system-arm -M mps2-an385} (0x40004000) e encerramento por
     * halt; vector table em 0x0 ({@code [0]=SP}, {@code [1]=Reset_Handler|1}).
     * Ops fora do subset → NATIVE002, concorrência → CONC003 (R6, nunca
     * stub silencioso). Sem superfície CLI ainda (machinery programática).
     */
    NATIVE_MCU_ARM,
    JS,
    ANDROID,
    /**
     * KofScript — coringa de execução (fase 2 do plano de plataforma).
     * Não emite artefatos: o programa é interpretado na IR compartilhada
     * ({@code CompilerPipeline.interpret}). {@code compile}/{@code build}
     * com este target falham com COMP003 (nunca fallback silencioso).
     */
    SCRIPT;

    public boolean isNative() {
        return this == NATIVE || this == NATIVE_RISCV64 || this == NATIVE_AARCH64
                || this == NATIVE_RISCV32 || this == NATIVE_MCU_ARM;
    }

    public boolean isScript() {
        return this == SCRIPT;
    }

    public String nativeArch() {
        return switch (this) {
            case NATIVE -> "x86_64";
            case NATIVE_RISCV64 -> "riscv64";
            case NATIVE_AARCH64 -> "aarch64";
            case NATIVE_RISCV32 -> "riscv32";
            case NATIVE_MCU_ARM -> "cortex-m";
            default -> "unknown";
        };
    }
}
