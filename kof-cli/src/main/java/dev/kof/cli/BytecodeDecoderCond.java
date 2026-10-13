package dev.kof.cli;

import java.util.List;

/**
 * Satélite do REFACTOR-500 (check_500, BytecodeDecoder 618→<600): a família de
 * CONDIÇÕES de retorno (comparisonReturn/ifElseReturn + condFromSuffix usa os
 * mesmos helpers via BytecodeStatements). Zero mudança de comportamento.
 */
final class BytecodeDecoderCond {

    private BytecodeDecoderCond() {}

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
            String v = BytecodeDecoder.loadValue(insns.get(i), frame);
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
        String cond = BytecodeDecoder.blockCondition(entry, insns, frame, cp);
        if (cond == null) return null;
        // succ[0] = alvo do branch (falso/else); succ[1] = fall-through (verdadeiro/then)
        int elseStart = entry.succ.get(0);
        int thenStart = entry.succ.get(1);
        BytecodeReader.Block thenB = BytecodeDecoder.find(blocks, thenStart);
        BytecodeReader.Block elseB = BytecodeDecoder.find(blocks, elseStart);
        if (thenB == null || elseB == null) return null;
        String thenE = BytecodeDecoder.blockReturnExpr(thenB, insns, cp, frame);
        String elseE = BytecodeDecoder.blockReturnExpr(elseB, insns, cp, frame);
        if (thenE == null || elseE == null) return null;
        return "if (" + cond + ") " + thenE + " else " + elseE;
    }
}
