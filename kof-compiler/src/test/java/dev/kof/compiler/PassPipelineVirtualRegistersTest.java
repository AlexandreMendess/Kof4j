package dev.kof.compiler;

import dev.kof.compiler.backend.IrPass;
import dev.kof.compiler.backend.PassPipeline;
import dev.kof.compiler.backend.VirtualRegisters;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase A of the assembly-optimization plan: the pass pipeline + virtual
 * registers. Both are additive — these tests pin the ordering, the fixpoint
 * determinism, and the (slot,type) vreg numbering.
 */
class PassPipelineVirtualRegistersTest {

    private static IRMethod method(List<Type> params, List<IRLocalVariable> locals) {
        return new IRMethod("m", Type.PrimitiveType.INT, params, AccessFlags.PUBLIC,
                List.of(), List.of(new IRBasicBlock(0, List.of(new KofReturnVoid()))),
                locals, KofDebugInfo.EMPTY);
    }

    // ── Virtual registers ────────────────────────────────────────

    @Test
    void sameSlotSameTypeIsOneVreg() {
        VirtualRegisters vr = VirtualRegisters.of(method(
                List.of(Type.PrimitiveType.INT),
                List.of(new IRLocalVariable(0, "x", Type.PrimitiveType.INT))));
        assertEquals(vr.vregOf(0, Type.PrimitiveType.INT),
                vr.vregOf(0, Type.PrimitiveType.INT), "the same (slot,type) is one vreg");
        assertEquals(1, vr.count());
    }

    @Test
    void slotReusedWithDifferentTypeIsADifferentVreg() {
        // slot 0 as Int (param) and as Long (a reused local) -> two vregs.
        VirtualRegisters vr = VirtualRegisters.of(method(
                List.of(Type.PrimitiveType.INT),
                List.of(new IRLocalVariable(0, "x", Type.PrimitiveType.LONG))));
        int asInt = vr.vregOf(0, Type.PrimitiveType.INT);
        int asLong = vr.vregOf(0, Type.PrimitiveType.LONG);
        assertNotEquals(asInt, asLong, "a slot reused with a different type is a distinct vreg");
        assertEquals(2, vr.count());
    }

    @Test
    void numberingIsDeterministicFirstSeenOrder() {
        VirtualRegisters a = VirtualRegisters.of(method(
                List.of(Type.PrimitiveType.INT, Type.PrimitiveType.LONG),
                List.of(new IRLocalVariable(2, "t", Type.PrimitiveType.INT))));
        VirtualRegisters b = VirtualRegisters.of(method(
                List.of(Type.PrimitiveType.INT, Type.PrimitiveType.LONG),
                List.of(new IRLocalVariable(2, "t", Type.PrimitiveType.INT))));
        assertEquals(a.vregOf(0, Type.PrimitiveType.INT), b.vregOf(0, Type.PrimitiveType.INT));
        assertEquals(a.vregOf(1, Type.PrimitiveType.LONG), b.vregOf(1, Type.PrimitiveType.LONG));
        assertEquals(a.vregOf(2, Type.PrimitiveType.INT), b.vregOf(2, Type.PrimitiveType.INT));
        assertEquals(0, a.vregOf(0, Type.PrimitiveType.INT), "param 0 is vreg 0");
    }

    @Test
    void unknownSlotIsMinusOne() {
        VirtualRegisters vr = VirtualRegisters.of(method(List.of(), List.of()));
        assertEquals(-1, vr.vregOf(9, Type.PrimitiveType.INT));
    }

    // ── Pass pipeline ────────────────────────────────────────────

    /** A pass that appends a literal once; used to test ordering + fixpoint. */
    private static IrPass appendPass(String name) {
        return new IrPass() {
            @Override public String name() { return name; }
            @Override public List<KofOperation> run(List<KofOperation> ops,
                                                    java.util.Map<KofOperation, SourcePosition> pos) {
                List<KofOperation> out = new ArrayList<>(ops);
                out.add(KofLoadLiteral.ofInt(0));
                return out;
            }
        };
    }

    @Test
    void pipelineRunsInOrder() {
        PassPipeline p = new PassPipeline(List.of(appendPass("a"), appendPass("b")));
        assertEquals(List.of("a", "b"), p.passes().stream().map(IrPass::name).toList());
        List<KofOperation> out = p.runOnce(List.of(), new java.util.IdentityHashMap<>());
        assertEquals(2, out.size(), "each pass appends once");
    }

    @Test
    void standardPipelineFoldsConstants() {
        var pos = new java.util.IdentityHashMap<KofOperation, SourcePosition>();
        // 1 + 2 -> folded to a single literal by the seeded constant-fold pass.
        List<KofOperation> ops = List.of(
                KofLoadLiteral.ofInt(1), KofLoadLiteral.ofInt(2),
                new KofBinary(KofBinaryOp.ADD, Type.PrimitiveType.INT));
        List<KofOperation> out = PassPipeline.standard().runOnce(ops, pos);
        assertEquals(1, out.size());
        assertInstanceOf(KofLoadLiteral.class, out.get(0));
    }

    @Test
    void fixpointStopsWhenNothingChanges() {
        // the standard pipeline on already-folded input is a fixpoint in one sweep.
        var pos = new java.util.IdentityHashMap<KofOperation, SourcePosition>();
        List<KofOperation> ops = List.of(KofLoadLiteral.ofInt(42));
        List<KofOperation> out = PassPipeline.standard().runToFixpoint(ops, pos, 8);
        assertEquals(ops, out, "a folded input is a fixpoint");
    }
}
