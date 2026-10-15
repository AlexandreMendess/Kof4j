package dev.kof.cli;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

/**
 * Reconstrução de corpo por statements (loops/stores/multi-statement/switch/
 * try) a partir do bytecode — metade statement do decompiler (Fase C/E).
 * Compartilha os helpers de expressão com {@link BytecodeDecoder}.
 */
final class BytecodeStatements {

    private BytecodeStatements() {
    }

    /** Medidor offline (probe-only): razão da recusa do struct. */
    static java.util.Map<String, Integer> refusalSink;
    /**
     * Fase C (13/10, if/else com valor propagado): blocos cuja PRIMEIRA
     * instrução é consumida pela emissão propagada (o astore/istore do join
     * vira `var/if-expression` no braço) — o struct emite o bloco PULANDO-a.
     * Por-rodada (limpo no fim de recoverStatements).
     */
    static java.util.Set<Integer> skipFirst;

    // Fase E (14/10): os slots de StringBuilder desta recuperação (o idioma
    // Kof é concat; StringBuilder não existe — SEM015). Reset por método.
    static java.util.Set<Integer> builderSlots = new HashSet<>();

    // Fase E (14/10): quantas insns iniciais do bloco já foram consumidas
    // (o binding do for-each: aload it; next; checkcast; astore x = 4).
    static java.util.Map<Integer, Integer> skipCount = new HashMap<>();
    /**
     * Fase C (13/10): o alvo da SAÍDA do loop aberto — um bloco de goto único
     * para ele É o `break`. Por-rodada (salvo/restaurado por loop aninhado).
     */
    static int currentLoopExit = -1;
    /** Atribuição com declaração `var` na 1ª escrita do slot. */
    static String assign(int slot, String value, BytecodeFrame frame, Set<Integer> declared) {
        String name = BytecodeDecoderStack.slotName(slot, frame);
        // Fase E (14/10): slot NOMEADO (parâmetro/this) NUNCA redeclara — o
        // `var arg1 = ...` criava local de BLOCO e o parâmetro nunca
        // atualizava (medido: o gcd decompilado rodava para sempre, R6).
        if (!frame.isNamedSlot(slot) && !declared.contains(slot)) {
            declared.add(slot);
            return "var " + name + " = " + value;
        }
        return name + " = " + value;
    }

    /**
     * O valor pendente do braço: avalia as insns (pulando o `goto` final
     * quando o braço salta para o join) — exatamente 1 valor ou null.
     */
    static String stackValueStrip(List<BytecodeReader.Insn> seq, BytecodeFrame frame, String[] cp) {
        if (seq.isEmpty()) return null;
        if (seq.get(seq.size() - 1).opcode() == 0xa7) {
            return BytecodeDecoder.stackValue(seq.subList(0, seq.size() - 1), frame, cp);
        }
        return BytecodeDecoder.stackValue(seq, frame, cp);
    }

    /** Insns do bloco, pulando a primeira quando o skip-first marca (o join propagado). */
    static List<BytecodeReader.Insn> blkSeq(BytecodeReader.Block b, List<BytecodeReader.Insn> insns) {
        List<BytecodeReader.Insn> seq = BytecodeDecoder.insnsWithin(b, insns);
        if (skipFirst != null && skipFirst.contains(b.start) && !seq.isEmpty()) {
            return seq.subList(1, seq.size());
        }
        if (skipCount != null && skipCount.containsKey(b.start)) {
            int n = Math.min(skipCount.get(b.start), seq.size());
            return seq.subList(n, seq.size());
        }
        return seq;
    }

    static void refuse(String reason) {
        if (refusalSink != null) refusalSink.merge(reason, 1, Integer::sum);
    }

    static List<String> recoverStatements(byte[] code, String[] cp, BytecodeFrame frame, int[][] handlers) {
        List<BytecodeReader.Insn> insns = BytecodeReader.decode(code);
        List<String> sw = BytecodeStatementsSwitch.recoverSwitch(code, insns, cp, frame);
        if (sw != null) return sw;
        // Fase C (statement-switch): só tenta se o de expressão recusou
        // (saída do de expressão preservada byte-idêntica onde aceitava).
        // Fase E (14/10): o switch de STRING (o desdobramento hashCode/
        // equals do javac) antes do switch comum — o hashCode recusaria
        // no linearExpr e o método inteiro stubaria.
        List<String> ssw = BytecodeSwitch.stringSwitchExpr(code, insns, cp, frame);
        if (ssw != null) return ssw;
        // Fase E (14/10): a comparação por sinal COM prólogo (o inCircle:
        //  com d computado no prólogo)
        List<String> cmpP = BytecodeDecoderCond.comparisonReturnPrologue(insns, cp, frame);
        if (cmpP != null) return cmpP;
        List<String> sws = BytecodeSwitch.recoverSwitchStmt(code, insns, cp, frame);
        if (sws != null) return sws;
        if (handlers != null && handlers.length > 0) {
            List<String> fin = recoverFinally(insns, cp, frame, handlers);
            if (fin != null) return fin;
            List<String> tc = tryCatch(insns, cp, frame, handlers);
            if (tc != null) return tc;
            // Fase E (15/10): o try/catch NÃO-LINEAR (o if dentro do try —
            // a fatia nomeada)
            List<String> tcs = tryCatchStored(insns, cp, frame, handlers);
            if (tcs != null) return tcs;
            // Fase E (15/10): handlers NÃO-recuperados → stub honesto. O
            // walk cego (que ignora a tabela de handlers) emitia o catch
            // DERRUBADO e a cauda absorvida no else — código compilável
            // ERRADO (medido no Res.describe: `describe(-3)` THREW onde o
            // Java imprime `bad!`; e o Res.withFinally perdeu o return).
            // R6: recusa antes do código errado.
            refuse("handlers-unrecovered");
            return null;
        }
        skipFirst = null;   // por-método: nenhum skip vaza entre corpos
        currentLoopExit = -1;
        builderSlots = new HashSet<>();
        BytecodeKofTypes.resetArrTmps();
        if (skipCount == null) skipCount = new HashMap<>();
        skipCount.clear();
        List<BytecodeReader.Block> blocks = BytecodeReader.cfg(insns, new int[0]);
        if (blocks.isEmpty()) { refuse("cfg-empty"); return null; }
        if (refusalSink != null) {
            StringBuilder dbg = new StringBuilder();
            for (BytecodeReader.Block b : blocks)
                dbg.append("B@").append(b.start).append(" succ").append(b.succ)
                   .append(" pred").append(b.pred).append("; ");
            refusalSink.merge("CFG[" + dbg + "]", 1, Integer::sum);
        }
        Map<Integer, BytecodeReader.Block> byStart = new HashMap<>();
        for (BytecodeReader.Block b : blocks) byStart.put(b.start, b);
        List<String> out = new ArrayList<>();
        Set<Integer> emitted = new HashSet<>();
        Set<Integer> declared = new HashSet<>();
        if (!struct(blocks.get(0), insns, byStart, cp, frame, out, emitted, declared, -1, new HashSet<>())) {
            if (refusalSink != null) {
                refusalSink.merge("struct-false | out-so-far: " + String.join(" | ", out), 1, Integer::sum);
            }
            return null;
        }
        return out;
    }

    /** Simple try/catch (único handler): reconstroi try + catch como statement. */
    static List<String> tryCatch(List<BytecodeReader.Insn> insns, String[] cp,
                                         BytecodeFrame frame, int[][] handlers) {
        if (handlers.length != 1) return null;
        int start = handlers[0][0];
        int end = handlers[0][1];
        int handler = handlers[0][2];
        List<BytecodeReader.Insn> trySeq = BytecodeSwitch.range(insns, start, end);
        List<BytecodeReader.Insn> handlerSeq = BytecodeSwitch.range(insns, handler, Integer.MAX_VALUE);
        // handler começa com astore/astore_N (exceção → bind implícito no catch)
        if (!handlerSeq.isEmpty()) {
            int op = handlerSeq.get(0).opcode();
            if (op == 0x4b || op == 0x4c || op == 0x4d || op == 0x4e        // astore_0..3
                    || op == 0x3a || op == 0x36 || op == 0x37 || op == 0x38 || op == 0x39) { // astore/istore...
                handlerSeq = handlerSeq.subList(1, handlerSeq.size());
            }
        }
        List<String> tryStmts;
        String tryExpr = BytecodeDecoder.linearReturn(trySeq, cp, frame);
        if (tryExpr == null) {
            return null;
        }
        tryStmts = tryExpr.isEmpty() ? new ArrayList<>() : new ArrayList<>(List.of("return " + tryExpr));
        List<String> handlerStmts = BytecodeStatementsEmit.emitLinear(handlerSeq, cp, frame, new HashSet<>());
        if (handlerStmts == null) return null;
        List<String> out = new ArrayList<>();
        out.add("try {");
        out.addAll(tryStmts);
        out.add("} catch (String e) {");
        out.addAll(handlerStmts);
        out.add("}");
        return out;
    }

    /**
     * Fase E (15/10): o try/catch NÃO-LINEAR — os DOIS lados terminam na
     * store do MESMO slot (o local hoisted `var v = ""`), o handler termina
     * em goto J e o tail [J..) usa o slot. Shape medido no Res.describe
     * (`String base; try { if (c<0) throw ..; base = "ok:"+c; } catch (..)
     * { base = "bad"; } return base + "!"`). R6: o valor do handler tem que
     * ser ldc-String e ambos os stores astore — qualquer desvio → null (o
     * stub honesto do caller).
     */
    static List<String> tryCatchStored(List<BytecodeReader.Insn> insns, String[] cp,
                                              BytecodeFrame frame, int[][] handlers) {
        if (handlers.length != 1) return null;
        int start = handlers[0][0];
        int end = handlers[0][1];
        int handler = handlers[0][2];
        List<BytecodeReader.Insn> trySeq = BytecodeSwitch.range(insns, start, end);
        List<BytecodeReader.Insn> handlerSeq = BytecodeSwitch.range(insns, handler, Integer.MAX_VALUE);
        if (trySeq.isEmpty() || handlerSeq.size() < 3) return null;
        // o strip do bind implícito da exceção
        int hop = handlerSeq.get(0).opcode();
        if (hop == 0x4b || hop == 0x4c || hop == 0x4d || hop == 0x4e || hop == 0x3a) {
            handlerSeq = handlerSeq.subList(1, handlerSeq.size());
        } else return null;
        // o try termina na store do slot S; o handler em [valor, store S, goto J]
        BytecodeReader.Insn tryStore = trySeq.get(trySeq.size() - 1);
        int top = tryStore.opcode();
        if (top != 0x3a && !(top >= 0x4b && top <= 0x4e)) return null;
        int slot = top == 0x3a ? tryStore.operands()[0] : top - 0x4b;
        int hLast = handlerSeq.get(handlerSeq.size() - 1).opcode();
        if (hLast != 0xa7) return null;
        int join = handlerSeq.get(handlerSeq.size() - 1).target();
        BytecodeReader.Insn hStore = handlerSeq.get(handlerSeq.size() - 2);
        int hop2 = hStore.opcode();
        if (hop2 != 0x3a && !(hop2 >= 0x4b && hop2 <= 0x4e)) return null;
        int hSlot = hop2 == 0x3a ? hStore.operands()[0] : hop2 - 0x4b;
        if (hSlot != slot) return null;
        // o valor do handler = ldc-String (o R6 do default)
        if (handlerSeq.size() < 3) return null;
        BytecodeReader.Insn hVal = handlerSeq.get(handlerSeq.size() - 3);
        if (hVal.opcode() != 0x12 && hVal.opcode() != 0x13) return null;
        String lit = BytecodeCp.ldc(cp, hVal.operands()[0]);
        if (lit == null || !lit.startsWith("\"")) return null;
        // o tail [J..): não-vazio e emittable
        List<BytecodeReader.Insn> tailSeq = BytecodeSwitch.range(insns, join, Integer.MAX_VALUE);
        if (tailSeq.isEmpty()) return null;
        var declared = new HashSet<Integer>();
        List<String> tailStmts = BytecodeStatementsEmit.emitLinear(tailSeq, cp, frame, declared);
        if (tailStmts == null) return null;
        // o corpo do try (com a store — ela converge os braços do if)
        List<String> tryStmts = BytecodeStatementsRecover.recoverRegionStruct(trySeq, cp, frame, declared);
        if (tryStmts == null) return null;
        String name = BytecodeDecoderStack.slotName(slot, frame);
        List<String> out = new ArrayList<>();
        out.add("var " + name + " = \"\"");
        out.add("try {");
        out.addAll(tryStmts);
        out.add("} catch (String e) {");
        out.add("    " + name + " = " + lit);
        out.add("}");
        out.addAll(tailStmts);
        return out;
    }

    // ── try/finally (bloco duplicado + handler catch-all) ────────────────

    static List<String> recoverFinally(List<BytecodeReader.Insn> insns, String[] cp,
                                               BytecodeFrame frame, int[][] handlers) {
        if (handlers.length != 1 || handlers[0].length < 4 || handlers[0][3] != 1) return null;
        int start = handlers[0][0];
        int to = handlers[0][1];
        List<BytecodeReader.Insn> tryInsns = BytecodeSwitch.range(insns, start, to);
        // último store da região try guarda o resultado num temp
        int lastStoreIdx = -1;
        int tempSlot = -1;
        for (int i = tryInsns.size() - 1; i >= 0; i--) {
            BytecodeReader.Insn in = tryInsns.get(i);
            if (isStore(in.opcode())) { lastStoreIdx = i; tempSlot = storeSlot(in); break; }
        }
        if (lastStoreIdx <= 0) return null;
        String tryResult = BytecodeDecoder.linearReturn(tryInsns.subList(0, lastStoreIdx), cp, frame);
        if (tryResult == null) return null;

        // retOff = load do temp (após o finally), delimita o corpo do finally
        int retOff = -1;
        for (BytecodeReader.Insn in : insns) {
            if (in.offset() < to) continue;
            int op = in.opcode();
            if ((op == 0x15 || op == 0x19) && in.operands()[0] == tempSlot) { retOff = in.offset(); break; }
            if (op >= 0x1a && op <= 0x1d && (op - 0x1a) == tempSlot) { retOff = in.offset(); break; }
            if (op >= 0x2a && op <= 0x2d && (op - 0x2a) == tempSlot) { retOff = in.offset(); break; }
        }
        if (retOff < 0) return null;
        List<String> finStmts = BytecodeStatementsEmit.emitLinear(BytecodeSwitch.range(insns, to, retOff), cp, frame, new HashSet<>());
        if (finStmts == null) return null;

        List<String> out = new ArrayList<>();
        out.add("try {");
        out.add("return " + tryResult);
        out.add("} finally {");
        out.addAll(finStmts);
        out.add("}");
        return out;
    }

    static boolean isStore(int op) {
        return (op >= 0x36 && op <= 0x3a) || (op >= 0x3b && op <= 0x4e);
    }

    static int storeSlot(BytecodeReader.Insn in) {
        int op = in.opcode();
        if (op >= 0x36 && op <= 0x3a) return in.operands()[0];
        if (op >= 0x3b && op <= 0x3e) return op - 0x3b;  // istore_0..3
        if (op >= 0x3f && op <= 0x42) return op - 0x3f;  // lstore_0..3
        if (op >= 0x43 && op <= 0x46) return op - 0x43;  // fstore_0..3
        if (op >= 0x47 && op <= 0x4a) return op - 0x47;  // dstore_0..3
        return op - 0x4b;                                // astore_0..3
    }

    // ── switch (tableswitch/lookupswitch) ─────────────────────────────────

    static String contCond(int op) {
        return switch (op) {
            case 0x99 -> "== 0"; case 0x9a -> "!= 0"; case 0x9b -> "< 0";
            case 0x9c -> ">= 0"; case 0x9d -> "> 0"; case 0x9e -> "<= 0";
            case 0x9f -> "=="; case 0xa0 -> "!="; case 0xa1 -> "<";
            case 0xa2 -> ">="; case 0xa3 -> ">"; case 0xa4 -> "<=";
            default -> null;
        };
    }

    static boolean struct(BytecodeReader.Block b, List<BytecodeReader.Insn> insns,
            Map<Integer, BytecodeReader.Block> byStart, String[] cp,
            BytecodeFrame frame, List<String> out,
            Set<Integer> emitted, Set<Integer> declared, int header, Set<Integer> stops) {
        return BytecodeStatementsRecover.struct(b, insns, byStart, cp, frame, out, emitted, declared, header, stops);
    }

    /** Emite statements lineares de um bloco (para em branch/goto/return). */
    static List<String> emitLinear(List<BytecodeReader.Insn> seq,
                                           String[] cp, BytecodeFrame frame,
                                           Set<Integer> declared) {
        List<String> stmts = new ArrayList<>();
        Deque<String> stack = new ArrayDeque<>();
        for (BytecodeReader.Insn in : seq) {
            int op = in.opcode();
            String opcodeTag = "emitLinear-null:0x" + String.format("%02x", op);
            switch (op) {
                case 0x01 -> stack.push("null");
                case 0x02 -> stack.push("-1");
                case 0x03, 0x04, 0x05, 0x06, 0x07, 0x08 -> stack.push(String.valueOf(op - 0x03));
                // lconst/dconst carregam o TIPO no próprio opcode (lição do
                // bug 62: só emitir quando o tipo não pode driftar). Kof tem
                // literal Long (42L) e Double (2.25) — fconst fica recusado.
                case 0x09 -> stack.push("0L");
                case 0x0a -> stack.push("1L");
                case 0x0b, 0x0c, 0x0d -> { { refuse(opcodeTag); return null; } }  // fconst: sem literal float em Kof
                case 0x0e -> stack.push("0.0");
                case 0x0f -> stack.push("1.0");
                case 0x10 -> stack.push(String.valueOf((byte) in.operands()[0]));
                case 0x11 -> stack.push(String.valueOf((short) in.operands()[0]));
                case 0x12, 0x13 -> {                            // ldc, ldc_w
                    String c = BytecodeCp.ldc(cp, in.operands()[0]);
                    if (c == null) { refuse(opcodeTag); return null; }
                    stack.push(c);
                }
                case 0x14 -> {
                    String c = BytecodeCp.ldc2(cp, in.operands()[0]);
                    if (c == null) { refuse(opcodeTag); return null; }
                    stack.push(c);
                }
                case 0xba -> { // invokedynamic — só String concat (CONCAT: no CP)
                    String rec = BytecodeConcat.recipe(cp, in.operands()[0]);
                    if (rec == null) { refuse(opcodeTag); return null; }
                    String expr = BytecodeConcat.apply(stack, rec);
                    if (expr == null) { refuse(opcodeTag); return null; }
                    stack.push(expr);
                }
                case 0x1a, 0x1b, 0x1c, 0x1d -> stack.push(BytecodeDecoderStack.slotName(op - 0x1a, frame));
                case 0x1e, 0x1f, 0x20, 0x21 -> stack.push(BytecodeDecoderStack.slotName(op - 0x1e, frame));   // lload_0..3
                case 0x26, 0x27, 0x28, 0x29 -> stack.push(BytecodeDecoderStack.slotName(op - 0x26, frame));   // dload_0..3
                case 0x2a, 0x2b, 0x2c, 0x2d -> stack.push(BytecodeDecoderStack.slotName(op - 0x2a, frame));
                case 0x15, 0x19 -> stack.push(BytecodeDecoderStack.slotName(in.operands()[0], frame));
                case 0x16, 0x17, 0x18 -> stack.push(BytecodeDecoderStack.slotName(in.operands()[0], frame));   // lload/fload/dload
                case 0x36, 0x37, 0x38, 0x39, 0x3a -> { // istore..astore idx
                    if (stack.isEmpty()) { refuse(opcodeTag); return null; }
                    String val = stack.pop();
                    int slot = in.operands()[0];
                    stmts.add(assign(slot, val, frame, declared));
                }
                case 0x3b, 0x3c, 0x3d, 0x3e -> { // istore_0..3
                    if (stack.isEmpty()) { refuse(opcodeTag); return null; }
                    String val = stack.pop();
                    stmts.add(assign(op - 0x3b, val, frame, declared));
                }
                case 0x3f, 0x40, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49, 0x4a -> {
                    // lstore_0..3 / fstore_0..3 / dstore_0..3: 4 opcodes POR tipo
                    // → slot = (op - 0x3f) % 4 (dstore_1 = 0x48 → slot 1, não 9)
                    if (stack.isEmpty()) { refuse(opcodeTag); return null; }
                    String val = stack.pop();
                    stmts.add(assign((op - 0x3f) % 4, val, frame, declared));
                }
                case 0x4b, 0x4c, 0x4d, 0x4e -> { // astore_0..3
                    if (stack.isEmpty()) { refuse(opcodeTag); return null; }
                    String val = stack.pop();
                    stmts.add(assign(op - 0x4b, val, frame, declared));
                }
                case 0x84 -> { // iinc: byte1=index, byte2=const (signed)
                    int w = in.operands()[0];
                    int slot = (w >> 8) & 0xFF;
                    int k = (byte) (w & 0xFF);
                    String name = BytecodeDecoderStack.slotName(slot, frame);
                    declared.add(slot);
                    stmts.add(name + " = " + name + " + " + k);
                }
                case 0x60 -> { if (!BytecodeDecoderStack.bin(stack, "+")) { refuse(opcodeTag); return null; } }
                case 0x64 -> { if (!BytecodeDecoderStack.bin(stack, "-")) { refuse(opcodeTag); return null; } }
                case 0x68 -> { if (!BytecodeDecoderStack.bin(stack, "*")) { refuse(opcodeTag); return null; } }
                case 0x6c -> { if (!BytecodeDecoderStack.bin(stack, "/")) { refuse(opcodeTag); return null; } }
                case 0x70 -> { if (!BytecodeDecoderStack.bin(stack, "%")) { refuse(opcodeTag); return null; } }
                case 0x74 -> { if (stack.isEmpty()) { refuse(opcodeTag); return null; } stack.push("-" + stack.pop()); }
                // Fase E: construção/teste-emit (0xbb new, 0xbd anewarray,
                // 0x59 dup, 0xb7 init, 0x57 pop, 0xc0 cast, 0xc1 instanceof)
                // — semântica em BytecodeKofTypes (gate ≤500); false = stub.
                case 0x57, 0xc0, 0xc1, 0xbb, 0xbc, 0xbd, 0x59, 0xb7,
                     0x2e, 0x2f, 0x30, 0x31, 0x32, 0x33, 0x34, 0x35,
                     0x4f, 0x50, 0x51, 0x52, 0x53, 0x54, 0x55, 0x56, 0xbe -> {
                    if (!BytecodeKofTypes.statementOp(op, in, stack, cp, stmts, frame)) { refuse(opcodeTag); return null; }
                }
                case 0xb8 -> { // invokestatic
                    String[] m = BytecodeCp.resolveMethodRef(cp, in.operands()[0]);
                    if (m == null) { refuse(opcodeTag); return null; }
                    String a = BytecodeCp.callArgs(stack, BytecodeCp.argCount(m[2]));
                    if (a == null) { refuse(opcodeTag); return null; }
                    String mapped = BytecodeStdlib.statics(m[0], m[1], a, m[2]);
                    if (mapped == null && BytecodeCp.isJdkOwner(m[0])) { refuse(opcodeTag); return null; }   // R6: owner JDK
                    String call = mapped != null ? mapped : BytecodeCp.simpleOwner(m[0]) + "." + m[1] + "(" + a + ")";
                    if (BytecodeCp.isVoidDesc(m[2])) { stmts.add(call); } else { stack.push(call); }
                }
                case 0xb6, 0xb9 -> { // invokevirtual / invokeinterface
                    String[] m = BytecodeCp.resolveMethodRef(cp, in.operands()[0]);
                    if (m == null) { refuse(opcodeTag); return null; }
                    String a = BytecodeCp.callArgs(stack, BytecodeCp.argCount(m[2]));
                    if (a == null || stack.isEmpty()) { refuse(opcodeTag); return null; }
                    String recv = stack.pop();
                    String mapped = BytecodeStdlib.virtual(recv, m[0], m[1], a);
                    if (mapped == null && recv.startsWith("⟦new⟧")) { refuse(opcodeTag); return null; }  // R6: método em novo Objeto JDK
                    String call = mapped != null ? mapped : recv + "." + m[1] + "(" + a + ")";
                    if (BytecodeCp.isVoidDesc(m[2])) { stmts.add(call); } else { stack.push(call); }
                }
                case 0xb4 -> { // getfield
                    String[] f = BytecodeCp.resolveMethodRef(cp, in.operands()[0]);
                    if (f == null || stack.isEmpty()) { refuse(opcodeTag); return null; }
                    String obj = stack.pop();
                    stack.push(obj + "." + f[1]);
                }
                case 0xb2 -> { // getstatic
                    String[] f = BytecodeCp.resolveMethodRef(cp, in.operands()[0]);
                    if (f == null) { refuse(opcodeTag); return null; }
                    stack.push(BytecodeCp.simpleOwner(f[0]) + "." + f[1]);
                }
                case 0xb5 -> { // putfield
                    String[] f = BytecodeCp.resolveMethodRef(cp, in.operands()[0]);
                    if (f == null || stack.isEmpty()) { refuse(opcodeTag); return null; }
                    String val = stack.pop();
                    if (stack.isEmpty()) { refuse(opcodeTag); return null; }
                    String obj = stack.pop();
                    stmts.add(obj + "." + f[1] + " = " + val);
                }
                case 0xb3 -> { // putstatic
                    String[] f = BytecodeCp.resolveMethodRef(cp, in.operands()[0]);
                    if (f == null || stack.isEmpty()) { refuse(opcodeTag); return null; }
                    String val = stack.pop();
                    stmts.add(BytecodeCp.simpleOwner(f[0]) + "." + f[1] + " = " + val);
                }
                case 0xac, 0xad, 0xae, 0xaf -> { if (stack.isEmpty()) { refuse(opcodeTag); return null; } stmts.add("return " + stack.pop()); return stmts; }
                case 0xb0 -> { if (stack.isEmpty()) { refuse(opcodeTag); return null; } stmts.add("return " + stack.pop()); return stmts; }
                case 0xb1 -> { stmts.add("return"); return stmts; }
                case 0xbf -> { // athrow → `throw x` (o Kof lança a exceção/String)
                    if (refusalSink != null) refusalSink.merge("ATHROW-STACK=" + stack, 1, Integer::sum);
                    if (stack.isEmpty()) return null;
                    stmts.add("throw " + stack.pop());
                    return stmts;
                }
                // terminadores de bloco: branch/goto — paramos sem emitir
                // (0xc6/0xc7 ifnull/ifnonnull entram: são branches como os
                // demais — o struct cuida dos joins)
                case 0x99, 0x9a, 0x9b, 0x9c, 0x9d, 0x9e, 0x9f, 0xa0, 0xa1, 0xa2, 0xa3, 0xa4, 0xa5, 0xa6, 0xa7, 0xc6, 0xc7 -> { return stmts; }
                default -> {
                    if (BytecodeDecoder.blockerSink != null) BytecodeDecoder.blockerSink.accept(op);
                    { refuse(opcodeTag); return null; }
                }
            }
        }
        return stmts;
    }

}
