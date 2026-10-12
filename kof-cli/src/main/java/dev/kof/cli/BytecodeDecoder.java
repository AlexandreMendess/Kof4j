package dev.kof.cli;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reconstrução de expressão Kof a partir do bytecode (decompiler, Fase C/E).
 *
 * Opera sobre a lista de instruções do {@link BytecodeReader} com uma pilha
 * simbólica: constantes, loads, aritmética → expressão; comparações booleanas
 * e if/else de retorno → if-expression idiomática. Fora do subconjunto,
 * devolve {@code null} (o decompiler cai no stub honesto — nunca inventa).
 */
 final class BytecodeDecoder {

    /**
     * Diagnóstico da fila Fase E: quando não-nulo, recebe cada opcode que faz
     * a recuperação desistir (default → null). Nulo em produção (zero custo).
     * Usado só por medidores offline (ver DECOMPILER.md §Fase E).
     */
    static java.util.function.IntConsumer blockerSink;

    /**
     * Fase G (diamond super.m(), mantenedora 14/10): o cp achatado perde o
     * TAG (Methodref vs InterfaceMethodref) — o contexto da classe corrente
     * vem do Decompile por rodada: `invokespecial` não-<init> cujo owner é a
     * SUPERCLASSE corrente emite `super.m(args)` (Kof suporta — medido);
     * owner que é interface (`A.super.m()`) é o refinamento de default.
     */
    static String currentSuperInternal;

    private BytecodeDecoder() {
    }

    /** Devolve a expressão do corpo, {@code ""} para vazio, ou {@code null} se não recuperável. */
    static String recoverExpression(byte[] code, String[] cp, BytecodeFrame frame) {
        List<BytecodeReader.Insn> insns = BytecodeReader.decode(code);
        if (insns.isEmpty()) return null;

        String lin = linearReturn(insns, cp, frame);
        if (lin != null) return lin;
        String cmp = comparisonReturn(insns, frame);
        if (cmp != null) return cmp;
        return ifElseReturn(insns, cp, frame);
    }

    /**
     * Expressão pura da pilha (Fase C, discriminante de switch): texto do topo
     * se restar EXATAMENTE 1 valor, senão null. Sem checagem contra o tipo de
     * retorno (o discriminante raramente tem o tipo do retorno — usar
     * `linearReturn` aqui recusava todo switch não-String-em-String).
     * `return`/`throw` no prefixo = switch inalcançável → recusar.
     */
    static String linearExpr(List<BytecodeReader.Insn> insns, String[] cp, BytecodeFrame frame) {
        for (BytecodeReader.Insn in : insns) {
            int op = in.opcode();
            if ((op >= 0xac && op <= 0xb1) || op == 0xbf) return null;
        }
        return linearReturn(insns, cp, frame, true);
    }

    // ── linear: pilha simbólica → value no return ────────────────────────

    static String linearReturn(List<BytecodeReader.Insn> insns, String[] cp,
                                       BytecodeFrame frame) {
        return linearReturn(insns, cp, frame, false);
    }

    /**
     * @param laxType true = ignora o tipo de retorno no final (só exige 1
     * valor na pilha); usado SÓ pelo `linearExpr` (discriminante). Chamadores
     * existentes usam o modo estrito — comportamento byte-idêntico.
     */
    static String linearReturn(List<BytecodeReader.Insn> insns, String[] cp,
                                       BytecodeFrame frame, boolean laxType) {
        MachineRun r = machineRun(insns, cp, frame);
        if (r == null) return null;
        if (r.stopped) return r.returned;
        // fim sem return: devolve o topo da pilha (região protegida de try)
        if (laxType) {
            return r.stack.size() == 1 ? r.stack.topExpr() : null;
        }
        return r.stack.size() == 1 ? r.stack.retTyped(frame.retType()) : null;
    }

    /** Estado da pilha simbólica após a máquina de expressão (degrau 3,
     *  13/09): exposto p/ o walker de pós-dominadores reusar a MESMA máquina
     *  — um segundo interpretador divergiria (lição trap 3/contFor%2).
     *  `stopped` = chegou em `return` (a pilha final NÃO reavalia: o
     *  `retTyped` do return já decidiu, inclusive por null — byte-idêntico). */
    static final class MachineRun {
        final BytecodeTypes.TStack stack;
        final String returned;
        final boolean stopped;
        MachineRun(BytecodeTypes.TStack stack, String returned, boolean stopped) {
            this.stack = stack; this.returned = returned; this.stopped = stopped;
        }
    }

    static MachineRun machineRun(List<BytecodeReader.Insn> insns, String[] cp,
                                       BytecodeFrame frame) {
        BytecodeTypes.TStack stack = new BytecodeTypes.TStack();
        for (BytecodeReader.Insn in : insns) {
            int op = in.opcode();
            switch (op) {
                case 0x01 -> stack.push("null", "L");                 // aconst_null
                case 0x02 -> stack.push("-1", "I");
                case 0x03, 0x04, 0x05, 0x06, 0x07, 0x08 -> stack.push(String.valueOf(op - 0x03), "I");
                // lconst/dconst: tipo embutido no opcode (lição bug 62 — só
                // emitir quando não pode driftar). fconst (0x0b-0x0d) recusado.
                case 0x09 -> stack.push("0L", "J");
                case 0x0a -> stack.push("1L", "J");
                case 0x0b, 0x0c, 0x0d -> { return null; }
                case 0x0e -> stack.push("0.0", "D");
                case 0x0f -> stack.push("1.0", "D");
                case 0x10 -> stack.push(String.valueOf((byte) in.operands()[0]), "I");
                case 0x11 -> stack.push(String.valueOf((short) in.operands()[0]), "I");
                case 0x12, 0x13 -> {                               // ldc, ldc_w (mesmo CP, índice u1/u2)
                    String c = BytecodeCp.ldc(cp, in.operands()[0]);
                    if (c == null) return null;
                    stack.push(c, c.startsWith("\"") ? "L" : "I");   // String vs Integer
                }
                case 0x14 -> {
                    String c = BytecodeCp.ldc2(cp, in.operands()[0]);
                    if (c == null) return null;
                    stack.push(c, c.endsWith("L") ? "J" : "D");
                }
                case 0x1a, 0x1b, 0x1c, 0x1d -> stack.push(slotName(op - 0x1a, frame), "I");
                case 0x1e, 0x1f, 0x20, 0x21 -> stack.push(slotName(op - 0x1e, frame), "J");   // lload_0..3
                case 0x26, 0x27, 0x28, 0x29 -> stack.push(slotName(op - 0x26, frame), "D");   // dload_0..3
                case 0x2a, 0x2b, 0x2c, 0x2d -> stack.push(slotName(op - 0x2a, frame), "L");
                case 0x15 -> stack.push(slotName(in.operands()[0], frame), "I");
                case 0x19 -> stack.push(slotName(in.operands()[0], frame), "L");
                case 0x16 -> stack.push(slotName(in.operands()[0], frame), "J");   // lload
                case 0x17 -> { return null; }                                       // fload (fconst já recusado)
                case 0x18 -> stack.push(slotName(in.operands()[0], frame), "D");   // dload
                case 0x60 -> { if (!stack.bin("+", "I")) return null; }
                case 0x64 -> { if (!stack.bin("-", "I")) return null; }
                case 0x68 -> { if (!stack.bin("*", "I")) return null; }
                case 0x6c -> { if (!stack.bin("/", "I")) return null; }
                case 0x70 -> { if (!stack.bin("%", "I")) return null; }
                case 0x61 -> { if (!stack.bin("+", "J")) return null; }
                case 0x65 -> { if (!stack.bin("-", "J")) return null; }
                case 0x69 -> { if (!stack.bin("*", "J")) return null; }
                case 0x6d -> { if (!stack.bin("/", "J")) return null; }
                case 0x71 -> { if (!stack.bin("%", "J")) return null; }
                case 0x63 -> { if (!stack.bin("+", "D")) return null; }
                case 0x67 -> { if (!stack.bin("-", "D")) return null; }
                case 0x6b -> { if (!stack.bin("*", "D")) return null; }
                case 0x6f -> { if (!stack.bin("/", "D")) return null; }
                case 0x73 -> { if (!stack.bin("%", "D")) return null; }
                case 0x74 -> { if (!stack.mono("-%s", "I", "I")) return null; }         // ineg (paridade: -atom)
                case 0x75 -> { if (!stack.mono("(-%s)", "J", "J")) return null; }       // lneg
                case 0x77 -> { if (!stack.mono("(-%s)", "D", "D")) return null; }       // dneg
                case 0x85 -> { if (!stack.mono("(%s as Long)", "I", "J")) return null; }    // i2l
                case 0x87 -> { if (!stack.mono("(%s as Double)", "I", "D")) return null; }  // i2d
                case 0x88 -> { if (!stack.mono("(%s as Int)", "J", "I")) return null; }     // l2i
                case 0x8a -> { if (!stack.mono("(%s as Double)", "J", "D")) return null; }  // l2d
                case 0x8e -> { if (!stack.mono("(%s as Int)", "D", "I")) return null; }     // d2i
                case 0x8f -> { if (!stack.mono("(%s as Long)", "D", "J")) return null; }    // d2l
                // i2c é o ÚNICO cast narrowing de Kof que é fiel: o codegen
                // emite i2c real para `x as Char` (16 bits, wrap igual ao
                // (char) do Java). i2b/i2s NÃO têm equivalente fiel — Kof
                // `as Byte`/`as Short` são no-op (Int 32 bits alarga; (byte)
                // 256 = 0 em Java mas 256 as Byte = 256 em Kof) — deixam no
                // default (recusa → stub honesto, bug-62 discipline).
                case 0x92 -> { if (!stack.mono("(%s as Char)", "I", "I")) return null; }    // i2c
                case 0xb8 -> { // invokestatic
                    String[] m = BytecodeCp.resolveMethodRef(cp, in.operands()[0]);
                    if (m == null) return null;
                    String a = stack.args(BytecodeCp.argCount(m[2]));
                    if (a == null) return null;
                    String mapped = BytecodeStdlib.statics(m[0], m[1], a, m[2]);
                    if (mapped == null && BytecodeCp.isJdkOwner(m[0])) return null;   // R6: owner JDK não-idiomático
                    stack.push(mapped != null ? mapped : BytecodeCp.simpleOwner(m[0]) + "." + m[1] + "(" + a + ")",
                            BytecodeCp.retOf(m[2]));
                }
                case 0xb6, 0xb9 -> { // invokevirtual / invokeinterface
                    String[] m = BytecodeCp.resolveMethodRef(cp, in.operands()[0]);
                    if (m == null) return null;
                    String a = stack.args(BytecodeCp.argCount(m[2]));
                    if (a == null) return null;
                    String recv = stack.popExpr();
                    if (recv == null) return null;
                    String mapped = BytecodeStdlib.virtual(recv, m[0], m[1], a);
                    if (mapped == null && recv.startsWith("⟦new⟧")) return null;  // R6: método em novo Objeto JDK não-mapeado
                    stack.push(mapped != null ? mapped : recv + "." + m[1] + "(" + a + ")", BytecodeCp.retOf(m[2]));
                }
                case 0xb4 -> { // getfield
                    String[] f = BytecodeCp.resolveMethodRef(cp, in.operands()[0]);
                    String obj = stack.popExpr();
                    if (f == null || obj == null) return null;
                    stack.push(obj + "." + f[1], BytecodeCp.retOf(f[2]));
                }
                case 0xb2 -> { // getstatic
                    String[] f = BytecodeCp.resolveMethodRef(cp, in.operands()[0]);
                    if (f == null) return null;
                    stack.push(BytecodeCp.simpleOwner(f[0]) + "." + f[1], BytecodeCp.retOf(f[2]));
                }
                case 0xba -> { // invokedynamic — só String concat (CONCAT: no CP)
                    String rec = BytecodeConcat.recipe(cp, in.operands()[0]);
                    if (rec == null) return null;
                    int n = 0;
                    for (int i = 0; i < rec.length(); i++) if (rec.charAt(i) == 1) n++;
                    java.util.List<String> argE = new java.util.ArrayList<>();
                    for (int i = 0; i < n; i++) argE.add(0, stack.popExpr());
                    if (argE.stream().anyMatch(java.util.Objects::isNull)) return null;
                    java.util.Deque<String> vals = new java.util.ArrayDeque<>(argE);
                    String expr = BytecodeConcat.apply(vals, rec);
                    if (expr == null) return null;
                    stack.push(expr, "L");
                }
                case 0xbb -> { // new — só p/ constructors de classes DE DOMÍNIO;
                    // new java.lang.X(...) nunca é idiomático p/ Kof (R6).
                    String cn = BytecodeCp.resolveClassName(cp, in.operands()[0]);
                    if (cn == null || BytecodeCp.isJdkClass(cp, in.operands()[0])) return null;
                    // §7 degrau 3: registra uso cross-package p/ import
                    // (emissão inalterada — nome simples como antes).
                    if (frame != null && frame.treeScope != null) {
                        String internal = BytecodeKofTypes.indexInternalName(cp, in.operands()[0]);
                        if (internal != null) frame.treeScope.resolve(internal);
                    }
                    stack.push("⟦new⟧" + cn, "L");
                }
                case 0x59 -> { // dup (só no padrão new)
                    String t = stack.topExpr();
                    if (t == null || !t.startsWith("⟦new⟧")) return null;
                    stack.dup();
                }
                case 0xb7 -> { // invokespecial (<init>)
                    String[] m = BytecodeCp.resolveMethodRef(cp, in.operands()[0]);
                    if (m == null) return null;
                    if (!"<init>".equals(m[1])) {
                        // Fase G: `super.m(args)` explícito — a invocação da
                        // superclasse é expressável em Kof (`super.m()` medido
                        // rodando); owner que NÃO é a super corrente = interface
                        // (`A.super.m()`) → não recuperado AQUI (o inline
                        // multi-classfile é a fatia seguinte — nunca fabricar).
                        if (currentSuperInternal != null && currentSuperInternal.equals(m[0])) {
                            String a = stack.args(BytecodeCp.argCount(m[2]));
                            if (a == null) return null;
                            String recv = stack.popExpr();
                            if (!"this".equals(recv)) return null;
                            stack.push("super." + m[1] + "(" + a + ")", "L");
                            continue;
                        }
                        return null;
                    }
                    String a = stack.args(BytecodeCp.argCount(m[2]));
                    if (a == null || stack.size() < 2) return null;
                    stack.pop();                       // receiver (cópia do dup)
                    String result = stack.popExpr();   // marcador do new
                    if (result == null || !result.startsWith("⟦new⟧")) return null;
                    stack.push(result.substring("⟦new⟧".length()) + "(" + a + ")", "L");
                }
                case 0xac -> { return new MachineRun(stack, stack.retTyped("I"), true); }
                case 0xad -> { return new MachineRun(stack, stack.retTyped("J"), true); }
                case 0xae -> { return new MachineRun(stack, stack.retTyped("F"), true); }
                case 0xaf -> { return new MachineRun(stack, stack.retTyped("D"), true); }
                case 0xb0 -> { return new MachineRun(stack, stack.retTyped("L"), true); }
                case 0xb1 -> {
                    // §CodeQL deref-null: guard defensivo igual ao da 0xb8
                    // (linha 216). frame nunca chega null pelos callers reais
                    // (recoverExpression/machineRun sempre recebem new
                    // BytecodeFrame), mas a API é package-private; sem frame
                    // não dá para checar o tipo de retorno, então trata-se
                    // como void-return (mesma saida do "return" simples).
                    if (frame != null && !"V".equals(frame.retType())) return null;   // return em método não-void → drift
                    return new MachineRun(stack, stack.isEmpty() ? "" : null, true);
                }
                default -> {
                    if (blockerSink != null) blockerSink.accept(op);
                    return null;
                }
            }
        }
        return new MachineRun(stack, null, false);
    }

    // ── comparação booleana de retorno ───────────────────────────────────

    static String comparisonReturn(List<BytecodeReader.Insn> insns, BytecodeFrame frame) {
        int n = insns.size();
        if (n < 5) return null;
        BytecodeReader.Insn last = insns.get(n - 1);
        if (!last.isReturn() || last.opcode() != 0xac) return null;
        BytecodeReader.Insn zero = insns.get(n - 2);
        if (zero.opcode() != 0x03) return null;
        BytecodeReader.Insn go = insns.get(n - 3);
        if (go.opcode() != 0xa7 || go.target() != last.offset()) return null;
        if (insns.get(n - 4).opcode() != 0x04) return null;
        BytecodeReader.Insn cmp = insns.get(n - 5);
        String inv = BytecodeCp.invCond(cmp.opcode());
        if (inv == null || cmp.target() != zero.offset()) return null;

        java.util.List<String> operands = new java.util.ArrayList<>();
        for (int i = 0; i < n - 5 && operands.size() < 2; i++) {
            String v = loadValue(insns.get(i), frame);
            if (v == null) return null;
            operands.add(v);
        }
        String cond;
        if (cmp.opcode() >= 0x9f && cmp.opcode() <= 0xa4) {
            if (operands.size() != 2) return null;
            cond = operands.get(0) + " " + inv + " " + operands.get(1);
        } else {
            if (operands.isEmpty()) return null;
            cond = operands.get(0) + " " + inv;
        }
        // §236 (14/09): o shape cmp/iconst_1/goto/iconst_0/ireturn e AMBIGUO na
        // raiz — `return x > 0` (Bool, Z no descriptor) e `return a < b ? 1 : 0`
        // (Int, I) temo MESMO bytecode. O fold cru `= cond` so vale p/ Bool;
        // num corpo Int virava Bool→SEM010 (nao-compilavel, anti-R6). Porta:
        // I → if-expression de inteiros (mesma forma do ifElseReturn:
        // `= if (c) 1 else 0`); Z → cond puro (byte-identico ao que passava);
        // outros (S/B/C do ireturn) → recusar (stub honesto).
        String ret = frame.retType();
        if (ret == null) return null;
        if (ret.equals("Z")) return cond;
        if (ret.equals("I")) return "if (" + cond + ") 1 else 0";
        return null;
    }

    // ── if/else de retorno (via CFG) ─────────────────────────────────────

    static String ifElseReturn(List<BytecodeReader.Insn> insns, String[] cp,
                                       BytecodeFrame frame) {
        List<BytecodeReader.Block> blocks = BytecodeReader.cfg(insns, new int[0]);
        if (blocks.isEmpty()) return null;
        BytecodeReader.Block entry = blocks.get(0);
        if (entry.succ.size() != 2) return null;
        // entrada é condicional: dois sucessores (then e else)
        String cond = blockCondition(entry, insns, frame);
        if (cond == null) return null;
        // succ[0] = alvo do branch (falso/else); succ[1] = fall-through (verdadeiro/then)
        int elseStart = entry.succ.get(0);
        int thenStart = entry.succ.get(1);
        BytecodeReader.Block thenB = find(blocks, thenStart);
        BytecodeReader.Block elseB = find(blocks, elseStart);
        if (thenB == null || elseB == null) return null;
        String thenE = blockReturnExpr(thenB, insns, cp, frame);
        String elseE = blockReturnExpr(elseB, insns, cp, frame);
        if (thenE == null || elseE == null) return null;
        return "if (" + cond + ") " + thenE + " else " + elseE;
    }

    static BytecodeReader.Block find(List<BytecodeReader.Block> blocks, int start) {
        for (BytecodeReader.Block b : blocks) if (b.start == start) return b;
        return null;
    }

    static List<BytecodeReader.Insn> insnsWithin(BytecodeReader.Block b, List<BytecodeReader.Insn> insns) {
        List<BytecodeReader.Insn> block = new java.util.ArrayList<>();
        for (BytecodeReader.Insn in : insns) {
            if (in.offset() >= b.start && in.offset() < b.end) block.add(in);
        }
        return block;
    }

    static String blockCondition(BytecodeReader.Block entry, List<BytecodeReader.Insn> insns,
                                         BytecodeFrame frame) {
        List<BytecodeReader.Insn> block = insnsWithin(entry, insns);
        if (block.isEmpty()) return null;
        BytecodeReader.Insn last = block.get(block.size() - 1);
        if (!last.isCond()) return null;
        String inv = BytecodeCp.invCond(last.opcode());
        if (inv == null) return null;
        // Aridade EXATA (regressão travada 13/09, contFor %2): bloco
        // [iload, iconst, irem, ifne] com o antigo "ate 2 loads" coletava
        // v2 e 2, IGNORAVA o irem e emitia `v2 == 0` — CODIGO ERRADO
        // COMPILAVEL (medido: 0 0 1 3 6... vs oracle 0 0 1 1 4...).
        // Se o bloco do teste tem qualquer calculo, o test-expr nao e
        // recuperavel aqui: null = stub honesto (R6).
        int arity = (last.opcode() >= 0x9f && last.opcode() <= 0xa4) ? 2 : 1;
        if (block.size() - 1 != arity) return null;
        java.util.List<String> operands = new java.util.ArrayList<>();
        for (int i = 0; i < block.size() - 1; i++) {
            String v = loadValue(block.get(i), frame);
            if (v == null) return null;
            operands.add(v);
        }
        return operands.get(0) + " " + inv + (arity == 2 ? " " + operands.get(1) : "");
    }

    static String blockReturnExpr(BytecodeReader.Block b, List<BytecodeReader.Insn> insns,
                                          String[] cp, BytecodeFrame frame) {
        return linearReturn(insnsWithin(b, insns), cp, frame);
    }

    static String slotName(int slot, BytecodeFrame frame) {
        return frame.name(slot);
    }

    static String loadValue(BytecodeReader.Insn in, BytecodeFrame frame) {
        return switch (in.opcode()) {
            case 0x1a, 0x1b, 0x1c, 0x1d -> slotName(in.opcode() - 0x1a, frame);
            case 0x1e, 0x1f, 0x20, 0x21 -> slotName(in.opcode() - 0x1e, frame);
            case 0x26, 0x27, 0x28, 0x29 -> slotName(in.opcode() - 0x26, frame);
            case 0x2a, 0x2b, 0x2c, 0x2d -> slotName(in.opcode() - 0x2a, frame);
            case 0x15, 0x19, 0x16, 0x17, 0x18 -> slotName(in.operands()[0], frame);
            case 0x03 -> "0";
            case 0x04 -> "1";
            case 0x05 -> "2";
            case 0x06 -> "3";
            case 0x07 -> "4";
            case 0x08 -> "5";
            case 0x02 -> "-1";
            case 0x01 -> "null";
            case 0x10 -> String.valueOf((byte) in.operands()[0]);
            case 0x11 -> String.valueOf((short) in.operands()[0]);   // sipush — 14/09
            // espelha o machineRun (linha 114 ja tratava 0x11): o loadValue SEM
            // sipush fazia `if (a == 30000)` (const > bipush-range) stubar em
            // blockCondition/comparisonReturn = DIVERGENCIA de passada, nao
            // forma-nova. Seguranca: so e emitido COM o hoist §238 no lugar (sem
            // ele, o `int r` escapante viraria saida nao-compilavel — o que o
            // re-medidor Roi3 marcou como pre-requisito).
            default -> null;
        };
    }

    static boolean bin(Deque<String> stack, String op) {
        if (stack.size() < 2) return false;
        String b = stack.pop();
        String a = stack.pop();
        stack.push("(" + a + " " + op + " " + b + ")");
        return true;
    }


    // ── statement-based body (loops / stores / multi-statement) ──────────
}
