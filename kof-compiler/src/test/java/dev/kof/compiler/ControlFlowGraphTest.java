package dev.kof.compiler;

import dev.kof.compiler.backend.ControlFlowGraph;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase A of the assembly-optimization plan: the structured CFG built over the
 * linear op stream. The graph is a pure read of the IR — these tests pin the
 * partition and the successor edges; the compiler behavior is unchanged
 * (pinned by the golden suite + {@link OptimizerTest}).
 */
class ControlFlowGraphTest {

    private static KofLoadLiteral lit(int v) {
        return KofLoadLiteral.ofInt(v);
    }

    @Test
    void straightLineIsOneBlock() {
        ControlFlowGraph g = ControlFlowGraph.ofOps(List.of(lit(1), lit(2), new KofReturnVoid()));
        assertEquals(1, g.blocks().size());
        assertTrue(g.blocks().get(0).successors().isEmpty(), "a return terminator has no successors");
    }

    @Test
    void unconditionalJumpSplitsBlocksAndLinksTarget() {
        LabelId L = new LabelId(0);
        // 0: lit; 1: jump L; 2: lit; 3: label L; 4: return
        ControlFlowGraph g = ControlFlowGraph.ofOps(List.of(
                lit(1), new KofJump(L), lit(2), new KofLabel(L), new KofReturnVoid()));
        assertEquals(3, g.blocks().size(), "jump + label create boundaries");
        // block 0 ends at the jump; its single successor is the block starting at L.
        assertEquals(List.of(g.blockOf(L)), g.blocks().get(0).successors());
        assertEquals(2, g.blockOf(L), "the label starts block 2");
    }

    @Test
    void conditionalJumpHasTwoSuccessors() {
        LabelId t = new LabelId(0);
        LabelId f = new LabelId(1);
        // 0: lit; 1: cjump(t,f); 2: label t; 3: return; 4: label f; 5: return
        ControlFlowGraph g = ControlFlowGraph.ofOps(List.of(
                lit(1),
                new KofConditionalJump(KofComparison.EQ, Type.PrimitiveType.INT, t, f),
                new KofLabel(t), new KofReturnVoid(),
                new KofLabel(f), new KofReturnVoid()));
        var b0 = g.blocks().get(0);
        assertEquals(2, b0.successors().size(), "a conditional jump branches two ways");
        assertTrue(b0.successors().contains(g.blockOf(t)));
        assertTrue(b0.successors().contains(g.blockOf(f)));
    }

    @Test
    void fallThroughLinksToNextBlock() {
        // A block whose last op is NOT a terminator falls through to the next
        // block (whose start is a jump-target label, hence a leader).
        LabelId L = new LabelId(0);
        LabelId R = new LabelId(1);
        // 0: cjump(L,R) [block0, terminator]; 1: lit [block1, no terminator]
        // 2: label L [block2, jump target]; 3: lit; 4: label R [block3]; 5: return
        ControlFlowGraph g = ControlFlowGraph.ofOps(List.of(
                new KofConditionalJump(KofComparison.EQ, Type.PrimitiveType.INT, L, R),
                lit(1),
                new KofLabel(L), lit(2),
                new KofLabel(R), new KofReturnVoid()));
        // block1 is [lit(1)] with no terminator -> falls through to block2.
        assertEquals(2, g.blockOf(L));
        assertEquals(List.of(g.blockOf(L)), g.blocks().get(1).successors(),
                "a non-terminator block falls through to the next block");
    }

    @Test
    void blocksPartitionEveryOpInOrder() {
        LabelId L = new LabelId(0);
        List<KofOperation> ops = List.of(
                lit(1), new KofJump(L), lit(2), new KofLabel(L), lit(3), new KofReturnVoid());
        ControlFlowGraph g = ControlFlowGraph.ofOps(ops);
        List<KofOperation> flat = new java.util.ArrayList<>();
        for (var b : g.blocks()) flat.addAll(b.operations());
        assertEquals(ops, flat, "the blocks partition the op stream in order (no op lost/dup)");
    }
}
