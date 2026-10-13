package dev.kof.compiler;

import dev.kof.compiler.backend.ControlFlowGraph;
import dev.kof.compiler.backend.EffectAnalysis;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase A of the assembly-optimization plan: effect + local def/use analysis.
 * The classification is conservative (unknown ops are barriers) and the
 * analysis is a pure read — these tests pin the classes and the block summary.
 */
class EffectAnalysisTest {

    private static ControlFlowGraph.Block block(List<KofOperation> ops) {
        return ControlFlowGraph.ofOps(ops).blocks().get(0);
    }

    @Test
    void pureOpsArePure() {
        assertEquals(EffectAnalysis.Effect.PURE, EffectAnalysis.effectOf(KofLoadLiteral.ofInt(1)));
        assertEquals(EffectAnalysis.Effect.PURE,
                EffectAnalysis.effectOf(new KofBinary(KofBinaryOp.ADD, Type.PrimitiveType.INT)));
        assertEquals(EffectAnalysis.Effect.PURE, EffectAnalysis.effectOf(new KofPop()));
    }

    @Test
    void loadsReadStoresWrite() {
        assertEquals(EffectAnalysis.Effect.READ,
                EffectAnalysis.effectOf(new KofLoadLocal(Type.PrimitiveType.INT, 0)));
        assertEquals(EffectAnalysis.Effect.WRITE,
                EffectAnalysis.effectOf(new KofStoreLocal(Type.PrimitiveType.INT, 0)));
        assertEquals(EffectAnalysis.Effect.READ,
                EffectAnalysis.effectOf(new KofGetStatic(Type.PrimitiveType.INT, "X", Type.PrimitiveType.INT)));
    }

    @Test
    void callsAllocsAndThrowAreBarriers() {
        assertEquals(EffectAnalysis.Effect.CALL, EffectAnalysis.effectOf(new KofThrow()));
        assertEquals(EffectAnalysis.Effect.CALL,
                EffectAnalysis.effectOf(new KofCall(null, "m", List.of(), Type.PrimitiveType.INT,
                        KofCallKind.STATIC)));
        assertEquals(EffectAnalysis.Effect.CALL,
                EffectAnalysis.effectOf(new KofNewObject(Type.PrimitiveType.INT, List.of())));
    }

    @Test
    void terminatorsAreControlAndLabelsStructural() {
        assertEquals(EffectAnalysis.Effect.CONTROL, EffectAnalysis.effectOf(new KofReturnVoid()));
        assertEquals(EffectAnalysis.Effect.CONTROL, EffectAnalysis.effectOf(new KofJump(new LabelId(0))));
        assertEquals(EffectAnalysis.Effect.STRUCTURAL, EffectAnalysis.effectOf(new KofLabel(new LabelId(0))));
    }

    @Test
    void blockSummarySeparatesDefsFromUpwardExposedUses() {
        // load(0); store(1); load(1); load(0)
        // uses = {0,1} (slot 1 read AFTER its store is NOT upward-exposed -> only 0 stays a use;
        // 1 is read after being written so it is not in uses)
        var b = block(List.of(
                new KofLoadLocal(Type.PrimitiveType.INT, 0),
                new KofStoreLocal(Type.PrimitiveType.INT, 1),
                new KofLoadLocal(Type.PrimitiveType.INT, 1),
                new KofLoadLocal(Type.PrimitiveType.INT, 0)));
        var s = EffectAnalysis.summarize(b);
        assertEquals(java.util.Set.of(1), s.defs(), "slot 1 is defined by the store");
        assertEquals(java.util.Set.of(0), s.uses(), "slot 0 is read before being written; slot 1 is not");
        assertFalse(s.hasCall());
        assertTrue(s.hasWrite(), "the store marks the block as writing");
    }

    @Test
    void blockSummaryFlagsCallAndControl() {
        var b = block(List.of(
                new KofCall(null, "m", List.of(), Type.PrimitiveType.INT, KofCallKind.STATIC),
                new KofReturnVoid()));
        var s = EffectAnalysis.summarize(b);
        assertTrue(s.hasCall());
        assertTrue(s.hasControl());
    }
}
