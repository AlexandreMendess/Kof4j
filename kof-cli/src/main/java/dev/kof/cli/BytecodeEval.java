package dev.kof.cli;

import java.util.List;

/**
 * Satélite do REFACTOR-500 (check_500, BytecodeDecoder 626→<600): a avaliação
 * de uma sequência de instruções numa pilha simbólica (evalInto) movida
 * verbatim; os helpers de conv/bin/slot ficam em BytecodeDecoder.
 */
final class BytecodeEval {

    private BytecodeEval() {}

static boolean evalInto(java.util.List<BytecodeReader.Insn> seq, BytecodeFrame frame,
                        String[] cp, java.util.Deque<String> st) {
    for (BytecodeReader.Insn in : seq) {
        int op = in.opcode();
        switch (op) {
            case 0x01 -> st.push("null");
            case 0x02 -> st.push("-1");
            case 0x03, 0x04, 0x05, 0x06, 0x07, 0x08 -> st.push(String.valueOf(op - 0x03));
            case 0x09 -> st.push("0L");
            case 0x0a -> st.push("1L");
            case 0x0e -> st.push("0.0");
            case 0x0f -> st.push("1.0");
            case 0x10 -> st.push(String.valueOf((byte) in.operands()[0]));
            case 0x11 -> st.push(String.valueOf((short) in.operands()[0]));
            case 0x12, 0x13 -> {
                String c = BytecodeCp.ldc(cp, in.operands()[0]);
                if (c == null) return false;
                st.push(c);
            }
            case 0x14 -> {
                String c = BytecodeCp.ldc2(cp, in.operands()[0]);
                if (c == null) return false;
                st.push(c);
            }
            case 0x1a, 0x1b, 0x1c, 0x1d -> st.push(BytecodeDecoder.slotName(op - 0x1a, frame));
            case 0x1e, 0x1f, 0x20, 0x21 -> st.push(BytecodeDecoder.slotName(op - 0x1e, frame));
            case 0x26, 0x27, 0x28, 0x29 -> st.push(BytecodeDecoder.slotName(op - 0x26, frame));
            case 0x2a, 0x2b, 0x2c, 0x2d -> st.push(BytecodeDecoder.slotName(op - 0x2a, frame));
            case 0x15, 0x19 -> st.push(BytecodeDecoder.slotName(in.operands()[0], frame));
            case 0x16, 0x17, 0x18 -> st.push(BytecodeDecoder.slotName(in.operands()[0], frame));
            case 0xbe -> {                                 // arraylength
                if (st.isEmpty()) return false;
                st.push(st.pop() + ".length");
            }
            // binárias (+ %): ABERTAS desde 14/10 — o contFor do join do
            // `continue` emite `if (cond) { } else { body }` + o
            // incremento comum, e o ZERO-DRIFT foi provado contra o
            // ORÁCULO JAVA REAL (n=0..20 idênticos). A lista de oráculo
            // antiga da face de guarda (`0 0 1 3 3 7 12`) estava
            // matematicamente errada para esse corpo — foi ela, não o
            // join, que fechava esta porta.
            case 0x60, 0x61, 0x62, 0x63 -> { if (!BytecodeDecoder.bin2(st, "+")) return false; }
            case 0x64, 0x65, 0x66, 0x67 -> { if (!BytecodeDecoder.bin2(st, "-")) return false; }
            case 0x68, 0x69, 0x6a, 0x6b -> { if (!BytecodeDecoder.bin2(st, "*")) return false; }
            case 0x6c, 0x6d, 0x6e, 0x6f -> { if (!BytecodeDecoder.bin2(st, "/")) return false; }
            case 0x70, 0x71, 0x72, 0x73 -> { if (!BytecodeDecoder.bin2(st, "%")) return false; }
            case 0x85, 0x86, 0x87, 0x88, 0x89, 0x8a, 0x8b, 0x8c, 0x8d, 0x8e, 0x8f, 0x90 -> {
                if (!BytecodeDecoder.conv(st, op)) return false;
            }
            case 0x78, 0x79 -> { if (!BytecodeDecoder.bin2(st, "<<")) return false; }
            case 0x7a, 0x7b -> { if (!BytecodeDecoder.bin2(st, ">>")) return false; }
            case 0x7c, 0x7d -> { if (!BytecodeDecoder.bin2(st, ">>>")) return false; }
            // Fase C (13/10): CHAMADAS em condição (`s.isEmpty()`) — o
            // MESMO gate R6 do emitLinear (JDK-owner recusa; método em
            // objeto novo recusa; void em condição não existe).
            case 0xb6, 0xb9 -> {                           // invokevirtual/interface
                String[] m = BytecodeCp.resolveMethodRef(cp, in.operands()[0]);
                if (m == null) return false;
                String a = BytecodeCp.callArgs(st, BytecodeCp.argCount(m[2]));
                if (a == null || st.isEmpty()) return false;
                String recv = st.pop();
                String mapped = BytecodeStdlib.virtual(recv, m[0], m[1], a);
                String NEW = new String(new char[]{'\u27e6', 'n', 'e', 'w', '\u27e7'});
                if (mapped == null && recv.startsWith(NEW)) return false;
                st.push(mapped != null ? mapped : recv + "." + m[1] + "(" + a + ")");
            }
            case 0xb8 -> {                                 // invokestatic
                String[] m = BytecodeCp.resolveMethodRef(cp, in.operands()[0]);
                if (m == null) return false;
                String a = BytecodeCp.callArgs(st, BytecodeCp.argCount(m[2]));
                if (a == null) return false;
                String mapped = BytecodeStdlib.statics(m[0], m[1], a, m[2]);
                if (mapped == null && BytecodeCp.isJdkOwner(m[0])) return false;
                st.push(mapped != null ? mapped : BytecodeCp.simpleOwner(m[0]) + "." + m[1] + "(" + a + ")");
            }
            case 0xb2 -> {                                 // getstatic
                String[] f = BytecodeCp.resolveMethodRef(cp, in.operands()[0]);
                if (f == null) return false;
                st.push(BytecodeCp.simpleOwner(f[0]) + "." + f[1]);
            }
            case 0xb4 -> {                                 // getfield
                String[] f = BytecodeCp.resolveMethodRef(cp, in.operands()[0]);
                if (f == null || st.isEmpty()) return false;
                String obj = st.pop();
                st.push(obj + "." + f[1]);
            }
            case 0xc0 -> {                                 // checkcast
                String ct = BytecodeKofTypes.inlineKofType(cp, in.operands()[0]);
                if (ct == null) {
                    ct = BytecodeKofTypes.indexKofType(cp, in.operands()[0], frame);
                    if (ct == null) return false;
                }
                if (st.isEmpty()) return false;
                String v = st.pop();
                st.push(ct.equals("Object") ? v : "(" + v + " as " + ct + ")");
            }
            default -> { return false; }
        }
    }
    return true;
}
}
