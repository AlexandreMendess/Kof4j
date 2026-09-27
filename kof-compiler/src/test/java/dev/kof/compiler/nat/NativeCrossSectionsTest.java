package dev.kof.compiler.nat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S-5 (#97 T1b) — a sectionização por função do runtime cross. §445 (22/09):
 * a transformação cria branches cross-section para labels locais `.L*`
 * (stub `j .Lcorpo` com corpo em outro range de função, forma do B23) e isso
 * é LEGAL porque o GAS emite a relocação; o hang do `base64Encode` vinha do
 * `as -mno-relax`, que ligava o salto sem `.rela` — o fix foi a flag do
 * assembler (ver `NativeCrossSections` + `known-bugs.md §445`). Aqui trava-se
 * a transformação em si: injeção no par `.globl`+label, passthrough do resto.
 */
class NativeCrossSectionsTest {

    /** Forma real do B23: entrada A é um stub que salta para o corpo
     *  compartilhado definido no range da entrada B. */
    private static final String B23_SHAPE = """
            .section .text
            .globl kof_encoding_base64Encode
            kof_encoding_base64Encode:
                li   a1, 0
                j    .Lv_b64_enc
            .globl kof_encoding_base64UrlEncode
            kof_encoding_base64UrlEncode:
                li   a1, 1
            .Lv_b64_enc:
                addi sp, sp, -80
                ret
            """;

    @Test
    void everyGloblPairGetsItsOwnSection() {
        String out = NativeCrossSections.sectionizeTextFunctions(B23_SHAPE);
        assertTrue(out.contains(".section .text.kof_encoding_base64Encode,\"ax\""),
                "entrada A deve abrir seção própria");
        assertTrue(out.contains(".section .text.kof_encoding_base64UrlEncode,\"ax\""),
                "entrada B deve abrir seção própria (o corpo compartilhado fica nela)");
        assertTrue(out.contains(".Lv_b64_enc:"), "labels locais passam ilesos");
    }

    @Test
    void dataSectionLabelsAreNotFunctionSections() {
        String text = """
                .section .data
                .globl kof_data_thing
                kof_data_thing:
                    .quad 0
                .section .text
                .globl kof_fn
                kof_fn:
                    ret
                """;
        String out = NativeCrossSections.sectionizeTextFunctions(text);
        assertFalse(out.contains(".section .text.kof_data_thing"),
                "par .globl+label fora de .text não é candidato");
        assertTrue(out.contains(".section .text.kof_fn,\"ax\""));
    }

    @Test
    void sameFunctionLocalLoopIsUntouched() {
        String text = """
                .section .text
                .globl kof_loop
                kof_loop:
                    li   t0, 0
                .Lv_loop:
                    addi t0, t0, 1
                    blt  t0, a0, .Lv_loop
                    ret
                """;
        String out = NativeCrossSections.sectionizeTextFunctions(text);
        assertTrue(out.contains(".section .text.kof_loop,\"ax\""));
        assertTrue(out.contains("    blt  t0, a0, .Lv_loop"), "instrução/corpo intactos");
    }

    @Test
    void noCandidateFunctionsKeepsTextByteIdentical() {
        // sem `\n` final a transformação é byte-idêntica (o quirk do newline
        // extra com `\n` final é do contrato antigo, preservado na mudança)
        String text = ".section .data\n.Lstr:\n    .asciz \"hi\"";
        assertEquals(text, NativeCrossSections.sectionizeTextFunctions(text));
    }
}
