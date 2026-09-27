package dev.kof.compiler.nat;

// FLT001 §448 (NATIVE002, 15/09 → 23/09): Double/Float -> String no runtime
// riscv64/aarch64. A partir de 23/09 o dtoa cross e 100% libc-free — o antigo
// loop `snprintf("%.*e")`+`strtod` (que escolhia o decimal MAIS CURTO e
// divergia do JVM nos subnormais, §448) virou o Schubfach do JDK, em asm
// riscv64, repartido por responsabilidade:
//   - NativeRiscvSchubfach      — tabelas g/pow10 + helpers + to_decimal;
//   - NativeRiscvSchubfachFormat— kof_schub_format (H=17/9) + Double driver;
//   - NativeRiscvSchubfachFloat — rop_f + to_decimal_f + Float driver.
// Esta peca e o agregador da fatia B45 (o registro de slices resolve por
// reflexao o campo RISCV_RUNTIME_ASM_B_45) e guarda so as strings .rodata.
//
// ABI cross (convencao da lane: FP trafega como BITS CRUS em registrador
// INTEIRO — B31/B40): `kof_double_to_string(a0=bits)`,
// `kof_float_to_string(a0=low32)`. Contrato §180 preservado: limiar cientifico
// do Java (|v| < 1e-3 ou >= 1e7), 'E' maiusculo, expoente sem '+' nem zeros,
// mantissa sempre com '.', NaN/±Inf normalizados.
//
// aarch64 herda via NativeAarch64Translator. Sem snprintf/strtod: um programa
// que imprime FP agora linka ESTATICO no cross (needsLibc nao detecta mais
// `call snprintf`).
public final class NativeRiscvAsmRtB45 {

    private NativeRiscvAsmRtB45() {}

    static  String RISCV_RUNTIME_ASM_B_45 = """
            .section .rodata
            .Ldtf_str_inf:  .asciz "Infinity"
            .Ldtf_str_ninf: .asciz "-Infinity"
            .Ldtf_str_nan:  .asciz "NaN"
            """ + NativeRiscvSchubfach.emit() + NativeRiscvSchubfachFloat.emit();
}
