package dev.kof.cli;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Satélite do REFACTOR-500 (check_500, BytecodeStatements 713→<600): o
 * reconhecedor recursivo de ESTRUTURAS (struct — if/else, while, do-while,
 * try/catch, break de saída de laço, orientação de valor) move-se verbatim.
 */
final class BytecodeStatementsRecover {

    private BytecodeStatementsRecover() {}

static boolean struct(BytecodeReader.Block b, List<BytecodeReader.Insn> insns,
                              Map<Integer, BytecodeReader.Block> byStart, String[] cp,
                              BytecodeFrame frame, List<String> out,
                              Set<Integer> emitted, Set<Integer> declared, int header,
                              Set<Integer> stops) {
    // Fase C: bloco do PÓS-IF (join de if-sem-else) que ainda NÃO foi
    // emitido = borda do braço: para AQUI sem emitir (a sequela cuida do
    // join). If-aninhado sem else no fim do braço para natural (join do
    // interno cai no mesmo lugar do externo — verificado por tipos).
    if (!stops.isEmpty() && stops.contains(b.start) && !emitted.contains(b.start)) {
        return true;
    }
    // Re-entrância de bloco: só a aresta de volta ao header do loop
    // ATUALMENTE ABERTO é legítima (back-edge normal; continue pode cair
    // no próprio header/incremento do loop). Re-entrar em bloco já emitido
    // que NÃO é o header aberto = ponto de junção compartilhado entre
    // braços de diamond (break, continue de loop externo, &&/||/?: com
    // braços que caem no mesmo bloco) — re-emiti-lo dentro de um ramo
    // perde o fluxo do outro ramo (ex.: `for` com `continue`: incremento
    // emitido só num dos caminhos; `&&`: return final sugado p/ o else
    // interno). Recovery de join estruturado é trabalho futuro; recusar →
    // stub UNKNOWN honesto (R6: nunca código errado compilável).
    if (emitted.contains(b.start)) return b.start == header;
    emitted.add(b.start);

    if (b.succ.isEmpty()) {
        List<String> stmts = BytecodeStatementsEmit.emitLinear(BytecodeDecoder.insnsWithin(b, insns), cp, frame, declared);
        if (stmts == null) { BytecodeStatements.refuse("emitLinear-null"); return false; }
        out.addAll(stmts);
        return true;
    }
    if (b.succ.size() == 2) {
        // Bottom-tested loop (do-while): o bloco cond tem aresta para si
        // MESMO ou para um bloco ANTERIOR (teste embaixo; o corpo fica
        // fundido no próprio bloco). Em loop top-tested (while/for) o
        // bloco de teste é o menor offset do laço e ambos os sucessores
        // são > start (o back-edge vem de um bloco posterior).
        int back = -1;
        int exit = -1;
        for (int s : b.succ) {
            if (s <= b.start) back = s; else exit = s;
        }
        if (back >= 0) {
            if (back != b.start) { BytecodeStatements.refuse("loop-body-separate"); return false; }  // corpo separado do teste — não recuperar
            List<BytecodeReader.Insn> block = BytecodeDecoder.insnsWithin(b, insns);
            if (block.isEmpty()) { BytecodeStatements.refuse("empty-body"); return false; }
            BytecodeReader.Insn last = block.get(block.size() - 1);
            if (!last.isCond()) { BytecodeStatements.refuse("do-while-no-cond"); return false; }
            int op = last.opcode();
            String cond;
            List<BytecodeReader.Insn> body;
            if (op >= 0x99 && op <= 0x9e) {
                if (block.size() < 2) { BytecodeStatements.refuse("do-while-short"); return false; }
                String a = BytecodeDecoder.loadValue(block.get(block.size() - 2), frame);
                if (a == null) { BytecodeStatements.refuse("do-while-load-null"); return false; }
                cond = a + " " + BytecodeStatements.contCond(op);
                body = block.subList(0, block.size() - 2);
            } else if (op >= 0x9f && op <= 0xa4) {
                if (block.size() < 3) { BytecodeStatements.refuse("do-while-short"); return false; }
                String x = BytecodeDecoder.loadValue(block.get(block.size() - 3), frame);
                String y = BytecodeDecoder.loadValue(block.get(block.size() - 2), frame);
                if (x == null || y == null) { BytecodeStatements.refuse("do-while-load-null"); return false; }
                cond = x + " " + BytecodeStatements.contCond(op) + " " + y;
                body = block.subList(0, block.size() - 3);
            } else {
                { BytecodeStatements.refuse("struct-generic"); return false; }
            }
            if (body.isEmpty()) { BytecodeStatements.refuse("loop-body-empty"); return false; }
            List<String> stmts = BytecodeStatementsEmit.emitLinear(body, cp, frame, declared);
            if (stmts == null) { BytecodeStatements.refuse("emitLinear-null"); return false; }
            out.add("do {");
            out.addAll(stmts);
            out.add("} while (" + cond + ")");
            return struct(byStart.get(exit), insns, byStart, cp, frame, out, emitted, declared, header, stops);
        }
        String cond = BytecodeDecoder.blockCondition(b, insns, frame, cp);
        int exitStart = b.succ.get(0);   // alvo do branch (falso)
        int thenStart = b.succ.get(1);   // fall-through (verdadeiro)
        boolean loop0 = BytecodeReader.isLoopHeader(b);
        if (cond == null && !loop0) {
            // Fase C (prologue no mesmo bloco do teste): javac funde
            // `int r = 100; if (x > 5)` num bloco so — o blockCondition
            // classico (so loads) recusa e stubava o metodo inteiro.
            // Conservador: o PREFIXO vai p/ emitLinear (recusa o que nao
            // souber emitir) e o cond sao os ULTIMOS K insns (loads
            // diretos p/ o condicional; pilha do cond vem deles). Path
            // classico (cond != null) NAO toca aqui = byte-identico.
            List<BytecodeReader.Insn> blk = BytecodeStatements.blkSeq(b, insns);
            BytecodeReader.Insn last = blk.get(blk.size() - 1);
            int lop = last.opcode();
            int k = (lop >= 0x9f && lop <= 0xa4) ? 2 : 1;   // if_acmp* = 2 ops; demais 1
            String inv = BytecodeCp.invCond(lop);
            // Fase C (13/10): o SPLIT seguro (prólogo-statements +
            // expressão) — o prólogo termina em store/pop; a expressão
            // avalia EXATAMENTE os `arity` operandos. Fecha o risco do
            // sufixo-fragmento reaproveitar um valor como receptor
            // (medido: `[ldc "(", at]` avaliava mas era código ERRADO).
            BytecodeDecoder.TestSplit split = (inv != null && last.isCond())
                    ? BytecodeDecoder.testSplit(blk, frame, cp, k, inv) : null;
            if (split != null) {
                List<BytecodeReader.Insn> preInsns = blk.subList(0, split.prologueLen());
                // emitLinear PARA e retorna parcial em branch (linha do
                // 0xa7) — prefixo com fluxo interno truncaria código em
                // silêncio (código errado compilável). Recusa → stub.
                for (BytecodeReader.Insn pi : preInsns) {
                    int po = pi.opcode();
                    boolean fluxo = (po >= 0x99 && po <= 0xa7) || (po >= 0xaa && po <= 0xb1) || po == 0xbf;
                    if (fluxo) { BytecodeStatements.refuse("prologue-has-flow"); return false; }
                }
                List<String> pre = BytecodeStatementsEmit.emitLinear(preInsns, cp, frame, declared);
                if (pre == null) { BytecodeStatements.refuse("emitLinear-null"); return false; }
                out.addAll(pre);
                cond = split.cond();
            }
        }
        if (cond == null) { BytecodeStatements.refuse("cond-null"); return false; }
        // Fase C (13/10, short-circuit &&): o THEN-bloco é OUTRO teste
        // cujo falso-alvo é o MESMO else → as condições combinam com
        // `&&` (a avaliação em cadeia do bytecode É o curto-circuit do
        // &&; os braços passam a ser os do teste interno).
        if (!loop0) {
            int guard = 0;
            BytecodeReader.Block tb = byStart.get(thenStart);
            while (guard < 4 && tb != null && tb.succ.size() == 2
                    && tb.succ.get(0) == exitStart && !BytecodeReader.isLoopHeader(tb)) {
                // succ.size() == 2 EXIGIDO: o && clássico tem then-de-teste
                // com DOIS sucessores (o falso-alvo compartilhado + a
                // queda); um teste com succ único (braços que caem no
                // mesmo join) NÃO combina — o get(1) crashava IOOBE
                // (medido no TranslateStatements real, 13/10).
                List<BytecodeReader.Insn> tblk = BytecodeDecoder.insnsWithin(tb, insns);
                if (tblk.isEmpty() || !tblk.get(tblk.size() - 1).isCond()) break;
                String cond2 = BytecodeDecoder.blockCondition(tb, insns, frame, cp);
                if (cond2 == null) break;
                cond = cond + " && " + cond2;
                thenStart = tb.succ.get(1);
                tb = byStart.get(thenStart);
                guard++;
            }
        }
        // Fase C (13/10, return-join ?:): o ELSE-bloco é [push, xreturn]
        // e o THEN termina em goto→[xreturn] — cada braço deixa UM valor
        // na pilha → if-expression Kof: `return if (cond) tVal else eVal`.
        {
            BytecodeReader.Block elseBlk = byStart.get(exitStart);
            BytecodeReader.Block thenBlk = byStart.get(thenStart);
            if (elseBlk != null && thenBlk != null && !elseBlk.succ.isEmpty()) {
                List<BytecodeReader.Insn> eIns = BytecodeDecoder.insnsWithin(elseBlk, insns);
                int joinAfterElse = elseBlk.succ.get(elseBlk.succ.size() - 1);
                BytecodeReader.Block joinBlk = byStart.get(joinAfterElse);
                // o else-bloco é SÓ o push (o xreturn é o bloco do join)
                boolean eShape = eIns.size() == 1;
                boolean tShape = thenBlk.succ.size() == 1 && thenBlk.succ.get(0) == joinAfterElse
                        && joinBlk != null && joinBlk.succ.isEmpty();
                if (eShape && tShape) {
                    List<BytecodeReader.Insn> tIns = BytecodeDecoder.insnsWithin(thenBlk, insns);
                    List<BytecodeReader.Insn> jIns = BytecodeDecoder.insnsWithin(joinBlk, insns);
                    if (tIns.size() >= 2 && tIns.get(tIns.size() - 1).opcode() == 0xa7
                            && jIns.size() == 1 && jIns.get(0).opcode() >= 0xac
                            && jIns.get(0).opcode() <= 0xb1) {
                        String tVal = BytecodeDecoder.stackValue(
                                tIns.subList(0, tIns.size() - 1), frame, cp);
                        String eVal = BytecodeDecoder.stackValue(eIns, frame, cp);
                        if (tVal != null && eVal != null) {
                            out.add("    return if (" + cond + ") " + tVal + " else " + eVal);
                            emitted.add(exitStart);
                            emitted.add(joinAfterElse);
                            return true;
                        }
                    }
                }
            }
        }
        boolean loop = loop0;
        if (loop) {
            out.add("while (" + cond + ") {");
            int savedExit = BytecodeStatements.currentLoopExit;
            BytecodeStatements.currentLoopExit = exitStart;   // `break` = goto para a saída
            boolean bodyOk = struct(byStart.get(thenStart), insns, byStart, cp, frame,
                    out, emitted, declared, b.start, stops);
            BytecodeStatements.currentLoopExit = savedExit;
            if (!bodyOk) { BytecodeStatements.refuse("struct-generic"); return false; }
            out.add("}");
            return struct(byStart.get(exitStart), insns, byStart, cp, frame, out, emitted, declared, header, stops);
        }
        // Fase C (join de if-sem-else): when o alvo do branch FALSO é um
        // join cujos únicos predecessors são o próprio if e o bloco then,
        // o `goto` implícito do then cai no pós-if — emite if SEM else e
        // o braço para no join via `stops` (linhas acima); a sequela
        // emite o join UMA vez (caminho falso = queda natural do if).
        BytecodeReader.Block join = byStart.get(exitStart);
        boolean pureIfThen = join != null
                && join.pred.size() == 2 && join.pred.contains(b.start)
                && join.pred.contains(thenStart)
                && !BytecodeReader.isLoopHeader(join)
                && byStart.get(thenStart) != null
                && byStart.get(thenStart).succ.equals(List.of(exitStart));
        BytecodeReader.Block thenB = byStart.get(thenStart);
        BytecodeReader.Block elseB = join;
        int tail = thenB != null && thenB.succ.size() == 1 ? thenB.succ.get(0) : -1;
        boolean pureIfElse = tail >= 0 && elseB != null && tail != exitStart && tail != b.start
                && elseB.succ.equals(List.of(tail))
                && !BytecodeReader.isLoopHeader(byStart.get(tail) != null ? byStart.get(tail) : elseB)
                && byStart.get(tail) != null
                && byStart.get(tail).pred.size() == 2
                && byStart.get(tail).pred.contains(thenStart)
                && byStart.get(tail).pred.contains(exitStart);
        // §238 (degrau 2c): um local escrito nos DOIS ramos e lido apos o
        // join sai com `var` escopado dentro do if -> SEM000 nao-compilavel
        // (anti-R6). Ica a declaracao (default por tipo da store) ANTES do
        // if; so no pureIfElse (o pureIfThen nao tem else nem init
        // escapante — Java valido nao o produz). Se um local escapante for
        // float/ref (sem default seguro) -> RECUSAR p/ stub honesto.
        // Fase C (13/10): VALOR PROPAGADO — os braços deixam exatamente 1
        // valor na pilha e o join começa com a store desse valor →
        // if-expression Kof: `var/if name = if (cond) tVal else eVal` (o
        // astore do join é CONSUMIDO pela atribuição — o skip-first).
        {
            BytecodeReader.Block joinB = byStart.get(tail);
            // guardas de null: o tail nem sempre é fronteira de bloco e os
            // blocos dos braços podem não existir no byStart — sem guarda
            // o insnsWithin crashava NPE (medido no corpus real, 13/10)
            if (joinB != null && thenB != null && elseB != null) {
            List<BytecodeReader.Insn> jPre = BytecodeDecoder.insnsWithin(joinB, insns);
            List<BytecodeReader.Insn> tSeq = BytecodeDecoder.insnsWithin(thenB, insns);
            List<BytecodeReader.Insn> eSeq = BytecodeDecoder.insnsWithin(elseB, insns);
            boolean jStore = !jPre.isEmpty() && BytecodeStatements.isStore(jPre.get(0).opcode());
            boolean bothReach = thenB.succ.contains(tail) && elseB.succ.contains(tail);
            if (pureIfElse && bothReach && jStore) {
                String tVal = BytecodeStatements.stackValueStrip(tSeq, frame, cp);
                String eVal = BytecodeStatements.stackValueStrip(eSeq, frame, cp);
                if (tVal != null && eVal != null) {
                    int slot = BytecodeStatements.storeSlot(jPre.get(0));
                    String name = BytecodeDecoder.slotName(slot, frame);
                    boolean first = !declared.contains(slot);
                    declared.add(slot);
                    String assign0 = (first ? "var " + name + " = " : name + " = ")
                            + "if (" + cond + ") " + tVal + " else " + eVal;
                    out.add("    " + assign0);
                    emitted.add(thenStart);
                    emitted.add(exitStart);
                    // NAO marcar tail como emitido: o struct PRECISA
                    // processa-lo (o BytecodeStatements.skipFirst consome o store) — marca-lo
                    // batia na guarda de re-entrancia e stubava o metodo
                    if (BytecodeStatements.skipFirst == null) BytecodeStatements.skipFirst = new HashSet<>();
                    BytecodeStatements.skipFirst.add(tail);
                    return struct(byStart.get(tail), insns, byStart, cp, frame,
                            out, emitted, declared, header, stops);
                }
            }
            }
        }
        if (pureIfElse) {
            List<String> hoist = StructWalker.hoistEscapingLocals(thenB, elseB, insns, frame, declared);
            if (hoist == null) {
                // Fase C (13/10): o fallback do hoist-null É o VALOR
                // PROPAGADO — os braços terminando com a store do MESMO
                // slot e valores extraíveis → `var/if name = if (cond)
                // tVal else eVal` (sem default seguro necessário: cada
                // braço tem o seu valor). Refshape estrito; senão stub.
                List<BytecodeReader.Insn> tI = BytecodeDecoder.insnsWithin(thenB, insns);
                List<BytecodeReader.Insn> eI = BytecodeDecoder.insnsWithin(elseB, insns);
                boolean tJump = !tI.isEmpty() && tI.get(tI.size() - 1).opcode() == 0xa7;
                boolean eJump = !eI.isEmpty() && eI.get(eI.size() - 1).opcode() == 0xa7;
                List<BytecodeReader.Insn> tCore = tJump ? tI.subList(0, tI.size() - 1) : tI;
                List<BytecodeReader.Insn> eCore = eJump ? eI.subList(0, eI.size() - 1) : eI;
                boolean shapeOk = !tCore.isEmpty() && !eCore.isEmpty()
                        && BytecodeStatements.isStore(tCore.get(tCore.size() - 1).opcode())
                        && BytecodeStatements.isStore(eCore.get(eCore.size() - 1).opcode());
                if (shapeOk) {
                    int tSlot = BytecodeStatements.storeSlot(tCore.get(tCore.size() - 1));
                    int eSlot = BytecodeStatements.storeSlot(eCore.get(eCore.size() - 1));
                    if (tSlot == eSlot) {
                        String tVal = BytecodeDecoder.stackValue(
                                tCore.subList(0, tCore.size() - 1), frame, cp);
                        String eVal = BytecodeDecoder.stackValue(
                                eCore.subList(0, eCore.size() - 1), frame, cp);
                        if (tVal != null && eVal != null) {
                            String name = BytecodeDecoder.slotName(tSlot, frame);
                            boolean first = !declared.contains(tSlot);
                            declared.add(tSlot);
                            out.add("    " + (first ? "var " + name + " = " : name + " = ")
                                    + "if (" + cond + ") " + tVal + " else " + eVal);
                            emitted.add(thenStart);
                            emitted.add(exitStart);
                            return struct(byStart.get(tail), insns, byStart, cp, frame,
                                    out, emitted, declared, header, stops);
                        }
                    }
                }
                BytecodeStatements.refuse("hoist-null"); return false;
            }
            out.addAll(hoist);
        }
        out.add("if (" + cond + ") {");
        if (pureIfThen) {
            Set<Integer> jstops = new HashSet<>(stops);
            jstops.add(exitStart);
            if (!struct(byStart.get(thenStart), insns, byStart, cp, frame, out, emitted, declared, header, jstops))
                { BytecodeStatements.refuse("struct-generic"); return false; }
            out.add("}");
            return struct(join, insns, byStart, cp, frame, out, emitted, declared, header, stops);
        }
        // Fase C degrau 2a (if-else LINEAR com sequela): ambos os ramos
        // terminam no MESMO join P (preds exatos {then,else}, nao-loop) —
        // cada braco para na borda de P (copia propria; dono emite P no
        // fim). SEM a borda o primeiro ramo ANDA p/ P e o segundo cai na
        // recusa 214 = stub do metodo inteiro. O trap 1 (sequela sugada
        // p/ dentro do else) NAO e possivel aqui: preds(P)=={then,else}
        // NAO contem o if — P nao e alvo de branch, e o join dos ramos.
        if (pureIfElse) {
            Set<Integer> bstops = new HashSet<>(stops);
            bstops.add(tail);
            if (!struct(thenB, insns, byStart, cp, frame, out, emitted, declared, header, bstops))
                { BytecodeStatements.refuse("struct-generic"); return false; }
            out.add("} else {");
            if (!struct(elseB, insns, byStart, cp, frame, out, emitted, declared, header, bstops))
                { BytecodeStatements.refuse("struct-generic"); return false; }
            out.add("}");
            return struct(byStart.get(tail), insns, byStart, cp, frame, out, emitted, declared, header, stops);
        }
        if (!struct(byStart.get(thenStart), insns, byStart, cp, frame, out, emitted, declared, header, stops))
            { BytecodeStatements.refuse("struct-generic"); return false; }
        out.add("} else {");
        if (!struct(byStart.get(exitStart), insns, byStart, cp, frame, out, emitted, declared, header, stops))
            { BytecodeStatements.refuse("struct-generic"); return false; }
        out.add("}");
        return true;
    }
    if (b.succ.size() == 1) {
        List<String> stmts = BytecodeStatementsEmit.emitLinear(BytecodeStatements.blkSeq(b, insns), cp, frame, declared);
        if (stmts == null) { BytecodeStatements.refuse("emitLinear-null"); return false; }
        out.addAll(stmts);
        // Fase C (13/10): goto para a SAÍDA do loop aberto = `break`
        if (BytecodeStatements.currentLoopExit >= 0 && b.succ.get(0) == BytecodeStatements.currentLoopExit) {
            out.add("break");
            return true;
        }
        return struct(byStart.get(b.succ.get(0)), insns, byStart, cp, frame, out, emitted, declared, header, stops);
    }
    { BytecodeStatements.refuse("struct-generic"); return false; }
}
}
