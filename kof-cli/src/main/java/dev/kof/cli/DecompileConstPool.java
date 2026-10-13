package dev.kof.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Satélite do REFACTOR-500 (check_500, Decompile 644→<600): a família de
 * CONSTANTES do constant-pool (foldClinitLiterals/cpConstText/kofLiteral)
 * separada do decompiler principal. Zero mudança de comportamento.
 */
final class DecompileConstPool {

    private DecompileConstPool() {}

    static void foldClinitLiterals(byte[] code, String[] cp,
                                           java.util.Map<String, String> inits) {
        List<BytecodeReader.Insn> insns;
        try {
            insns = BytecodeReader.decode(code);
        } catch (Exception e) {
            return;
        }
        String pending = null;
        for (BytecodeReader.Insn in : insns) {
            int op = in.opcode();
            if (pending != null) {
                if (op != 0xb3) return;                     // putstatic
                String[] fr = BytecodeCp.resolveMethodRef(cp, in.operands()[0]);
                if (fr == null) return;
                // o descritor do CAMPO (fr[2]) tipa o literal
                String lit = kofLiteral(pending, fr[2]);
                if (lit == null) return;
                inits.put(fr[1], lit);
                pending = null;
                continue;
            }
            pending = cpConstText(op, in, cp);
            if (pending == null) return;
        }
        if (pending != null) return;                        // sem o return final
    }

    /** Texto cru do push constante (cp para ldc/ldc_w/ldc2_w). */
    static String cpConstText(int op, BytecodeReader.Insn in, String[] cp) {
        switch (op) {
            case 0x01: return "null";
            case 0x02: return "-1";
            case 0x03: case 0x04: case 0x05: case 0x06: case 0x07: case 0x08:
                return String.valueOf(op - 0x03);
            case 0x09: return "0L"; case 0x0a: return "1L";
            case 0x0b: return "0.0F"; case 0x0c: return "1.0F"; case 0x0d: return "2.0F";
            case 0x0e: return "0.0"; case 0x0f: return "1.0";
            case 0x10: // bipush
                return String.valueOf((byte) in.operands()[0]);
            case 0x11: // sipush
                return String.valueOf((short) in.operands()[0]);
            case 0x12: case 0x13: case 0x14: {              // ldc/ldc_w/ldc2_w
                int idx = in.operands()[0];
                if (idx <= 0 || idx >= cp.length || cp[idx] == null) return null;
                if (cp[idx].startsWith("#")) {
                    try {
                        int u = Integer.parseInt(cp[idx].substring(1));
                        return u < cp.length && cp[u] != null ? cp[u] : null;
                    } catch (NumberFormatException nfe) {
                        return null;
                    }
                }
                return cp[idx];
            }
            default: return null;
        }
    }

    /** Formata o raw do cp como literal Kof pelo descritor do campo. */
    static String kofLiteral(String raw, String desc) {
        if (raw == null) return null;
        if (desc != null && desc.endsWith("Ljava/lang/String;")) {
            return '"' + TranslateLexer.escapeKofString(raw) + '"';
        }
        if (desc != null && (desc.startsWith("J") || desc.startsWith("D") || desc.startsWith("F")
                || desc.startsWith("I"))) {
            // numérico: o texto do cp é o valor; sufixo para long/float
            if (desc.startsWith("J") && !raw.endsWith("L")) return raw + "L";
            if (desc.startsWith("F") && !raw.endsWith("F")) return raw + "F";
            return raw;
        }
        // sem descritor resolvido: só literais seguros (número/negativo)
        return raw.matches("-?\\d+") ? raw : null;
    }
}
