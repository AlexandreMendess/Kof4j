package dev.kof.cli;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;

/**
 * Satélite do REFACTOR-500 (check_500, BytecodeStatements 615→<600): a emissão
 * LINEAR das statements recuperadas (emitLinear + assign) separada da fase de
 * recuperação (recoverStatements/tryCatch/recoverSwitch). Zero mudança de
 * comportamento.
 */
final class BytecodeStatementsEmit {

    private BytecodeStatementsEmit() {}

    static List<String> emitLinear(List<BytecodeReader.Insn> seq,
                                           String[] cp, BytecodeFrame frame,
                                           Set<Integer> declared) {
        List<String> stmts = new ArrayList<>();
        Deque<String> stack = new ArrayDeque<>();
        for (BytecodeReader.Insn in : seq) {
            int op = in.opcode();
            switch (op) {
                case 0x01 -> stack.push("null");
                case 0x02 -> stack.push("-1");
                case 0x03, 0x04, 0x05, 0x06, 0x07, 0x08 -> stack.push(String.valueOf(op - 0x03));
                // lconst/dconst carregam o TIPO no próprio opcode (lição do
                // bug 62: só emitir quando o tipo não pode driftar). Kof tem
                // literal Long (42L) e Double (2.25) — fconst fica recusado.
                case 0x09 -> stack.push("0L");
                case 0x0a -> stack.push("1L");
                case 0x0b, 0x0c, 0x0d -> { return null; }  // fconst: sem literal float em Kof
                case 0x0e -> stack.push("0.0");
                case 0x0f -> stack.push("1.0");
                case 0x10 -> stack.push(String.valueOf((byte) in.operands()[0]));
                case 0x11 -> stack.push(String.valueOf((short) in.operands()[0]));
                case 0x12, 0x13 -> {                            // ldc, ldc_w
                    String c = BytecodeCp.ldc(cp, in.operands()[0]);
                    if (c == null) return null;
                    stack.push(c);
                }
                case 0x14 -> {
                    String c = BytecodeCp.ldc2(cp, in.operands()[0]);
                    if (c == null) return null;
                    stack.push(c);
                }
                case 0xba -> { // invokedynamic — só String concat (CONCAT: no CP)
                    String rec = BytecodeConcat.recipe(cp, in.operands()[0]);
                    if (rec == null) return null;
                    String expr = BytecodeConcat.apply(stack, rec);
                    if (expr == null) return null;
                    stack.push(expr);
                }
                case 0x1a, 0x1b, 0x1c, 0x1d -> stack.push(BytecodeDecoder.slotName(op - 0x1a, frame));
                case 0x1e, 0x1f, 0x20, 0x21 -> stack.push(BytecodeDecoder.slotName(op - 0x1e, frame));   // lload_0..3
                case 0x26, 0x27, 0x28, 0x29 -> stack.push(BytecodeDecoder.slotName(op - 0x26, frame));   // dload_0..3
                case 0x2a, 0x2b, 0x2c, 0x2d -> stack.push(BytecodeDecoder.slotName(op - 0x2a, frame));
                case 0x15, 0x19 -> stack.push(BytecodeDecoder.slotName(in.operands()[0], frame));
                case 0x16, 0x17, 0x18 -> stack.push(BytecodeDecoder.slotName(in.operands()[0], frame));   // lload/fload/dload
                case 0x36, 0x37, 0x38, 0x39, 0x3a -> { // istore..astore idx
                    if (stack.isEmpty()) return null;
                    String val = stack.pop();
                    int slot = in.operands()[0];
                    stmts.add(assign(slot, val, frame, declared));
                }
                case 0x3b, 0x3c, 0x3d, 0x3e -> { // istore_0..3
                    if (stack.isEmpty()) return null;
                    String val = stack.pop();
                    stmts.add(assign(op - 0x3b, val, frame, declared));
                }
                case 0x3f, 0x40, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49, 0x4a -> {
                    // lstore_0..3 / fstore_0..3 / dstore_0..3: 4 opcodes POR tipo
                    // → slot = (op - 0x3f) % 4 (dstore_1 = 0x48 → slot 1, não 9)
                    if (stack.isEmpty()) return null;
                    String val = stack.pop();
                    stmts.add(assign((op - 0x3f) % 4, val, frame, declared));
                }
                case 0x4b, 0x4c, 0x4d, 0x4e -> { // astore_0..3
                    if (stack.isEmpty()) return null;
                    String val = stack.pop();
                    stmts.add(assign(op - 0x4b, val, frame, declared));
                }
                case 0x84 -> { // iinc: byte1=index, byte2=const (signed)
                    int w = in.operands()[0];
                    int slot = (w >> 8) & 0xFF;
                    int k = (byte) (w & 0xFF);
                    String name = BytecodeDecoder.slotName(slot, frame);
                    declared.add(slot);
                    stmts.add(name + " = " + name + " + " + k);
                }
                case 0x60 -> { if (!BytecodeDecoder.bin(stack, "+")) return null; }
                case 0x64 -> { if (!BytecodeDecoder.bin(stack, "-")) return null; }
                case 0x68 -> { if (!BytecodeDecoder.bin(stack, "*")) return null; }
                case 0x6c -> { if (!BytecodeDecoder.bin(stack, "/")) return null; }
                case 0x70 -> { if (!BytecodeDecoder.bin(stack, "%")) return null; }
                case 0x74 -> { if (stack.isEmpty()) return null; stack.push("-" + stack.pop()); }
                // Fase E: construção/teste-emit (0xbb new, 0xbd anewarray,
                // 0x59 dup, 0xb7 init, 0x57 pop, 0xc0 cast, 0xc1 instanceof)
                // — semântica em BytecodeKofTypes (gate ≤500); false = stub.
                case 0x57, 0xc0, 0xc1, 0xbb, 0xbc, 0xbd, 0x59, 0xb7,
                     0x2e, 0x2f, 0x30, 0x31, 0x32, 0x33, 0x34, 0x35,
                     0x4f, 0x50, 0x51, 0x52, 0x53, 0x54, 0x55, 0x56, 0xbe -> {
                    if (!BytecodeKofTypes.statementOp(op, in, stack, cp, stmts, frame)) return null;
                }
                case 0xb8 -> { // invokestatic
                    String[] m = BytecodeCp.resolveMethodRef(cp, in.operands()[0]);
                    if (m == null) return null;
                    String a = BytecodeCp.callArgs(stack, BytecodeCp.argCount(m[2]));
                    if (a == null) return null;
                    String mapped = BytecodeStdlib.statics(m[0], m[1], a, m[2]);
                    if (mapped == null && BytecodeCp.isJdkOwner(m[0])) return null;   // R6: owner JDK
                    String call = mapped != null ? mapped : BytecodeCp.simpleOwner(m[0]) + "." + m[1] + "(" + a + ")";
                    if (BytecodeCp.isVoidDesc(m[2])) { stmts.add(call); } else { stack.push(call); }
                }
                case 0xb6, 0xb9 -> { // invokevirtual / invokeinterface
                    String[] m = BytecodeCp.resolveMethodRef(cp, in.operands()[0]);
                    if (m == null) return null;
                    String a = BytecodeCp.callArgs(stack, BytecodeCp.argCount(m[2]));
                    if (a == null || stack.isEmpty()) return null;
                    String recv = stack.pop();
                    String mapped = BytecodeStdlib.virtual(recv, m[0], m[1], a);
                    if (mapped == null && recv.startsWith("⟦new⟧")) return null;  // R6: método em novo Objeto JDK
                    String call = mapped != null ? mapped : recv + "." + m[1] + "(" + a + ")";
                    if (BytecodeCp.isVoidDesc(m[2])) { stmts.add(call); } else { stack.push(call); }
                }
                case 0xb4 -> { // getfield
                    String[] f = BytecodeCp.resolveMethodRef(cp, in.operands()[0]);
                    if (f == null || stack.isEmpty()) return null;
                    String obj = stack.pop();
                    stack.push(obj + "." + f[1]);
                }
                case 0xb2 -> { // getstatic
                    String[] f = BytecodeCp.resolveMethodRef(cp, in.operands()[0]);
                    if (f == null) return null;
                    stack.push(BytecodeCp.simpleOwner(f[0]) + "." + f[1]);
                }
                case 0xb5 -> { // putfield
                    String[] f = BytecodeCp.resolveMethodRef(cp, in.operands()[0]);
                    if (f == null || stack.isEmpty()) return null;
                    String val = stack.pop();
                    if (stack.isEmpty()) return null;
                    String obj = stack.pop();
                    stmts.add(obj + "." + f[1] + " = " + val);
                }
                case 0xb3 -> { // putstatic
                    String[] f = BytecodeCp.resolveMethodRef(cp, in.operands()[0]);
                    if (f == null || stack.isEmpty()) return null;
                    String val = stack.pop();
                    stmts.add(BytecodeCp.simpleOwner(f[0]) + "." + f[1] + " = " + val);
                }
                case 0xac, 0xad, 0xae, 0xaf -> { if (stack.isEmpty()) return null; stmts.add("return " + stack.pop()); return stmts; }
                case 0xb0 -> { if (stack.isEmpty()) return null; stmts.add("return " + stack.pop()); return stmts; }
                case 0xb1 -> { stmts.add("return"); return stmts; }
                // terminadores de bloco: branch/goto — paramos sem emitir
                case 0x99, 0x9a, 0x9b, 0x9c, 0x9d, 0x9e, 0x9f, 0xa0, 0xa1, 0xa2, 0xa3, 0xa4, 0xa5, 0xa6, 0xa7 -> { return stmts; }
                default -> {
                    if (BytecodeDecoder.blockerSink != null) BytecodeDecoder.blockerSink.accept(op);
                    return null;
                }
            }
        }
        return stmts;
    }

    private static String assign(int slot, String value, BytecodeFrame frame, Set<Integer> declared) {
        String name = BytecodeDecoder.slotName(slot, frame);
        if (!declared.contains(slot)) {
            declared.add(slot);
            return "var " + name + " = " + value;
        }
        return name + " = " + value;
    }
}
