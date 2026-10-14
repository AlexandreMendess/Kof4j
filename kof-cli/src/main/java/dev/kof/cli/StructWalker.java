package dev.kof.cli;

import java.util.List;
import java.util.Set;

/**
 * Recuperacao estrutural do walker (Fase C, degrau 2c).
 *
 * <p>O bug §238: o caminho `pureIfElse`/`pureIfThen` do
 * {@link BytecodeStatements#struct} emite o `var` de um local na SUA PRIMEIRA
 * atribuicao. Quando essa primeira escrita fica DENTRO de um ramo do
 * `if`/`else` e o valor e lido DEPOIS do join, o `var` sai escopado dentro do
 * ramo e o pos-join vira `SEM000 Undefined variable` — saida que NAO
 * recompila, violando a lei R6 (nunca codigo errado/nao-compilavel). Exemplo
 * javac: {@code int s; if (m==0) { s=10; } else { s=20; } return s;}.
 *
 * <p>A correcao e icar (hoist) a declaracao para ANTES do `if`, com um
 * default pelo TIPO da store, apenas para slots que (a) sao escritos por um
 * `xstore` dentro dos ramos e (b) AINDA NAO estao em `declared` (nao foram
 * inicializados antes do if). Slots ja declarados (init pre-if, como o
 * `int r = 1` do teste E.java) nao sao tocados = saida byte-identica.
 *
 * <p>Honestidade R6 (sem falso-verde): so icamos tipos com default
 * SEMANTICO seguro — {@code Int→0}, {@code Long→0L}, {@code Double→0.0}. Um
 * local escrito como {@code float} (fstore) ou referencia (astore) NAO tem
 * default seguro a emitir aqui (float literal drifta — licao bug 62; e o tipo
 * de referencia e desconhecido) — nesses casos RECUSAMOS o shape inteiro, e o
 * chamador degrada p/ o stub honesto (melhor UNKNOWN que nao-compilavel).
 */
final class StructWalker {

    private StructWalker() {}

    /** Resultado do hoist: null = recusar (ha local nao-seguro escapando);
     *  lista (mesmo vazia) = linha(s) `var ...` a emitir antes do `if` ja
     *  com os slots marcados em {@code declared} (efeito colateral intencional:
     *  o subsequent struct() dos ramos passa a so atribuir, sem re-`var`). */
    static List<String> hoistEscapingLocals(BytecodeReader.Block thenB,
                                            BytecodeReader.Block elseB,
                                            List<BytecodeReader.Insn> insns,
                                            BytecodeFrame frame,
                                            Set<Integer> declared) {
        java.util.List<String> hoisted = new java.util.ArrayList<>();
        java.util.LinkedHashSet<Integer> seen = new java.util.LinkedHashSet<>();
        for (BytecodeReader.Block b : new BytecodeReader.Block[]{thenB, elseB}) {
            if (b == null) continue;
            for (BytecodeReader.Insn in : BytecodeDecoder.insnsWithin(b, insns)) {
                int slot = storeSlot(in);
                if (slot < 0 || declared.contains(slot) || seen.contains(slot)) continue;
                // Fase E (14/10): slot NOMEADO (parâmetro/this) NUNCA hoista —
                // o `var arg0 = 0` do default sombreava o parâmetro e o loop
                // rodava para sempre (medido no collatz; R6)
                if (frame.isNamedSlot(slot)) continue;
                String def = storeDefault(in.opcode());
                if (def == null) return null;                 // float/ref: recusar (R6)
                seen.add(slot);
                hoisted.add("var " + BytecodeDecoderStack.slotName(slot, frame) + " = " + def);
            }
        }
        declared.addAll(seen);
        return hoisted;
    }

    /** Slot que o insn escreve (store), ou -1 se nao e store. */
    private static int storeSlot(BytecodeReader.Insn in) {
        int op = in.opcode();
        int[] o = in.operands();
        if (op == 0x36) return o[0];                          // istore idx
        if (op == 0x37) return o[0];                          // lstore idx
        if (op == 0x38) return o[0];                          // fstore idx
        if (op == 0x39) return o[0];                          // dstore idx
        if (op == 0x3a) return o[0];                          // astore idx
        if (op >= 0x3b && op <= 0x3e) return op - 0x3b;       // istore_0..3
        if (op >= 0x3f && op <= 0x42) return op - 0x3f;       // lstore_0..3
        if (op >= 0x43 && op <= 0x46) return op - 0x43;       // fstore_0..3
        if (op >= 0x47 && op <= 0x4a) return op - 0x47;       // dstore_0..3
        if (op >= 0x4b && op <= 0x4e) return op - 0x4b;       // astore_0..3
        return -1;
    }

    /** Default literal seguro por categoria de store; null = nao icar. */
    private static String storeDefault(int op) {
        if (op == 0x36 || (op >= 0x3b && op <= 0x3e)) return "0";      // int
        if (op == 0x37 || (op >= 0x3f && op <= 0x42)) return "0L";     // long
        if (op == 0x39 || (op >= 0x47 && op <= 0x4a)) return "0.0";    // double
        return null;                                                   // fstore/astore
    }
}
