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
        String cmp = BytecodeDecoderCond.comparisonReturn(insns, frame);
        if (cmp != null) return cmp;
        return BytecodeDecoderCond.ifElseReturn(insns, cp, frame);
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

    /**
     * Fase C (13/10, `||`): a condição com o operador TAKEN (não-invertido) —
     * para o teste cujo JUMP é o braço VERDADEIRO (a orientação do `||`).
     * Espelho do blockCondition (que inverte).
     */
    static String blockConditionTaken(BytecodeReader.Block entry, List<BytecodeReader.Insn> insns,
                                      BytecodeFrame frame, String[] cp) {
        List<BytecodeReader.Insn> block = insnsWithin(entry, insns);
        if (block.isEmpty()) return null;
        BytecodeReader.Insn last = block.get(block.size() - 1);
        if (!last.isCond()) return null;
        String op = BytecodeCp.takenCond(last.opcode());
        if (op == null) return null;
        int arity = (last.opcode() >= 0x9f && last.opcode() <= 0xa4) ? 2 : 1;
        if (block.size() - 1 < arity) return null;
        if (block.size() - 1 == arity) {
            List<String> operands = new java.util.ArrayList<>();
            for (int i = 0; i < block.size() - 1; i++) {
                String v = loadValue(block.get(i), frame);
                if (v == null) return null;
                operands.add(v);
            }
            return operands.get(0) + " " + op + (arity == 2 ? " " + operands.get(1) : "");
        }
        return testExpr(block.subList(0, block.size() - 1), frame, arity, op, cp);
    }

    static String blockCondition(BytecodeReader.Block entry, List<BytecodeReader.Insn> insns,
                                         BytecodeFrame frame, String[] cp) {
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
        if (block.size() - 1 < arity) return null;
        // Path EXATO original: o bloco do teste é SÓ os loads (aridade igual)
        if (block.size() - 1 == arity) {
            java.util.List<String> operands = new java.util.ArrayList<>();
            for (int i = 0; i < block.size() - 1; i++) {
                String v = loadValue(block.get(i), frame);
                if (v == null) return null;
                operands.add(v);
            }
            return operands.get(0) + " " + inv + (arity == 2 ? " " + operands.get(1) : "");
        }
        // Fase E (fatia array-cond, 14/10): o teste tem CÁLCULO simples —
        // mini-avaliador de pilha com conjunto FECHADO de opcodes (cada um
        // modelado exatamente; qualquer outro → null = stub honesto). No fim
        // a pilha tem EXATAMENTE os `arity` operandos em ordem.
        java.util.Deque<String> st = new java.util.ArrayDeque<>();
        if (!evalInto(block.subList(0, block.size() - 1), frame, cp, st)) return null;
        if (st.size() != arity) return null;
        if (arity == 1) return st.pop() + " " + inv;   // ifne→`== 0` etc.
        String b = st.pop();
        String a = st.pop();
        return a + " " + inv + " " + b;
    }

    /**
     * Avalia a expressão do teste com conjunto FECHADO: loads, pushes de
     * constante, `arraylength`, binárias + - * / %. Exato por construção —
     * os opcodes são modelados 1:1; qualquer outro recusa (R6: nunca código
     * errado compilável — a lição da regressão do irem acima).
     */
    /**
     * Fase C (13/10): a condição do teste por BUSCA DE SUFIXO — o bloco pode
     * ter um PRÓLOGO (stores/chamadas) antes da expressão; tenta sufixos
     * crescentes com o avaliador fechado e aceita o que deixar EXATAMENTE os
     * `arity` operandos. Exato por construção (o mesmo conjunto fechado);
     * SÓ use onde o prólogo É emitido pelo chamador (o path do prologue no
     * struct) — em outro lugar o prólogo seria descartado em silêncio.
     */
    record TestSplit(String cond, int prologueLen) {}

    /**
     * Fase C (13/10): o SPLIT seguro do bloco de teste em
     * (prólogo-statements, expressão-da-condição). O prólogo DEVE terminar
     * num terminador de statement (store/pop) ou ser vazio — senão o sufixo
     * seria um fragmento de expressão (o risco MEDIDO: `[ldc "(", at]`
     * avalia mas reaproveita o `(` como receptor — código ERRADO). A
     * expressão (sufixo) tem de avaliar para EXATAMENTE `arity` valores.
     */
    static TestSplit testSplit(java.util.List<BytecodeReader.Insn> blk, BytecodeFrame frame,
                               String[] cp, int arity, String inv) {
        int max = Math.min(blk.size() - 1, 24);
        for (int len = arity; len <= max; len++) {
            java.util.List<BytecodeReader.Insn> pre = blk.subList(0, blk.size() - 1 - len);
            java.util.List<BytecodeReader.Insn> suf = blk.subList(blk.size() - 1 - len, blk.size() - 1);
            if (!pre.isEmpty()) {
                int lo = pre.get(pre.size() - 1).opcode();
                boolean stmtEnd = (lo >= 0x36 && lo <= 0x4e) || lo == 0x57;
                if (!stmtEnd) continue;
            }
            java.util.Deque<String> st = new java.util.ArrayDeque<>();
            if (!evalInto(suf, frame, cp, st)) continue;
            if (st.size() != arity) continue;
            String cond;
            if (arity == 1) {
                cond = st.pop() + " " + inv;
            } else {
                String b = st.pop();
                String a = st.pop();
                cond = a + " " + inv + " " + b;
            }
            return new TestSplit(cond, blk.size() - 1 - len);
        }
        return null;
    }

    static String testExpr(java.util.List<BytecodeReader.Insn> seq, BytecodeFrame frame,
                           int arity, String inv, String[] cp) {
        java.util.Deque<String> st = new java.util.ArrayDeque<>();
        if (!evalInto(seq, frame, cp, st)) return null;
        if (st.size() != arity) return null;
        if (arity == 1) return st.pop() + " " + inv;
        String b = st.pop();
        String a = st.pop();
        return a + " " + inv + " " + b;
    }

    /**
     * Fase C (13/10): avalia a EXPRESSÃO de um braço curto (os pushes antes
     * do goto/xreturn) — o valor pendente que o join consome. O MESMO
     * conjunto fechado do testExpr; exatamente 1 valor ou null.
     */
    static String stackValue(java.util.List<BytecodeReader.Insn> seq, BytecodeFrame frame, String[] cp) {
        java.util.Deque<String> st = new java.util.ArrayDeque<>();
        if (!evalInto(seq, frame, cp, st)) return null;
        return st.size() == 1 ? st.pop() : null;
    }

    /**
     * O avaliador de conjunto FECHADO compartilhado (testExpr/stackValue).
     * loads, pushes de constante, `arraylength`, binárias + - * / %,
     * chamadas (os MESMOS gates R6 do emitLinear: JDK-owner recusa; método
     * em objeto novo recusa), getstatic/getfield, checkcast. Exato por
     * construção; qualquer outro opcode → false.
     */
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
                case 0x1a, 0x1b, 0x1c, 0x1d -> st.push(slotName(op - 0x1a, frame));
                case 0x1e, 0x1f, 0x20, 0x21 -> st.push(slotName(op - 0x1e, frame));
                case 0x26, 0x27, 0x28, 0x29 -> st.push(slotName(op - 0x26, frame));
                case 0x2a, 0x2b, 0x2c, 0x2d -> st.push(slotName(op - 0x2a, frame));
                case 0x15, 0x19 -> st.push(slotName(in.operands()[0], frame));
                case 0x16, 0x17, 0x18 -> st.push(slotName(in.operands()[0], frame));
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
                case 0x70 -> { if (!bin2(st, "%")) return false; }
                case 0x60 -> { if (!bin2(st, "+")) return false; }
                case 0x64 -> { if (!bin2(st, "-")) return false; }
                case 0x68 -> { if (!bin2(st, "*")) return false; }
                case 0x6c -> { if (!bin2(st, "/")) return false; }
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

    private static boolean bin2(java.util.Deque<String> st, String op) {
        if (st.size() < 2) return false;
        String b = st.pop();
        String a = st.pop();
        st.push("(" + a + " " + op + " " + b + ")");
        return true;
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
