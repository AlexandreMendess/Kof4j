package dev.kof.cli;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Satélite do REFACTOR-500 (check_500, BytecodeStatements 625→<600): a
 * recuperação de SWITCH (recoverSwitch + contCond) separada da família
 * principal. O `refuse` é o sink compartilhado de BytecodeStatements.
 */
final class BytecodeStatementsSwitch {

    private BytecodeStatementsSwitch() {}

    private static void refuse(String reason) {
        if (BytecodeStatements.refusalSink != null)
            BytecodeStatements.refusalSink.merge(reason, 1, Integer::sum);
    }

    static List<String> recoverSwitch(byte[] code, List<BytecodeReader.Insn> insns,
                                              String[] cp, BytecodeFrame frame) {
        // localiza o switch
        int swOff = -1;
        for (int pc = 0; pc < code.length; ) {
            int op = code[pc] & 0xFF;
            if (op == 0xaa || op == 0xab) { swOff = pc; break; }
            int l = BytecodeReader.length(op);
            pc += (l == -1) ? 1 : l;
        }
        if (swOff < 0) return null;
        BytecodeSwitch.SwitchInfo si = BytecodeSwitch.parseSwitch(code, swOff);
        if (si == null) return null;

        // expressão do switch = topo da pilha antes do switch
        String expr = BytecodeDecoder.linearReturn(BytecodeSwitch.range(insns, 0, swOff), cp, frame);
        if (expr == null) return null;

        // limites (targets ordenados) p/ reconstruir cada corpo
        java.util.TreeSet<Integer> bounds = new java.util.TreeSet<>();
        bounds.add(si.dflt);
        for (int t : si.targets) bounds.add(t);
        int maxOff = insns.get(insns.size() - 1).offset() + 1;

        List<String> out = new ArrayList<>();
        out.add("switch (" + expr + ") {");
        for (int i = 0; i < si.targets.length; i++) {
            int bodyEnd = BytecodeSwitch.nextBound(bounds, si.targets[i], maxOff);
            String val = BytecodeDecoder.linearReturn(BytecodeSwitch.range(insns, si.targets[i], bodyEnd), cp, frame);
            if (val == null) return null;
            out.add("case " + si.values[i] + ": return " + val);
        }
        int dfltEnd = BytecodeSwitch.nextBound(bounds, si.dflt, maxOff);
        String dfltVal = BytecodeDecoder.linearReturn(BytecodeSwitch.range(insns, si.dflt, dfltEnd), cp, frame);
        if (dfltVal == null) return null;
        out.add("default: return " + dfltVal);
        out.add("}");
        return out;
    }

    /** Condição de CONTINUAÇÃO (lado do fallthrough) — não invertida. */
}
