package dev.kof.compiler;

import dev.kof.compiler.backend.CopyPropagation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase B of the assembly-optimization plan: copy propagation. A copy
 * {@code load(x); store(y)} makes later {@code load(y)} read {@code x}; the
 * rewrite is stack-effect-preserving and conservative at control boundaries.
 */
class CopyPropagationTest {

    private final CopyPropagation pass = new CopyPropagation();

    private List<KofOperation> run(List<KofOperation> ops) {
        return pass.run(ops, new java.util.IdentityHashMap<>());
    }

    private static KofLoadLocal load(int slot, Type t) { return new KofLoadLocal(t, slot); }
    private static KofStoreLocal store(int slot, Type t) { return new KofStoreLocal(t, slot); }

    @Test
    void redirectsALaterLoadToTheCopiedSource() {
        // load(0); store(1); load(1)  -> load(1) becomes load(0)
        List<KofOperation> out = run(List.of(
                load(0, Type.PrimitiveType.INT),
                store(1, Type.PrimitiveType.INT),
                load(1, Type.PrimitiveType.INT)));
        assertEquals(3, out.size());
        assertEquals(load(0, Type.PrimitiveType.INT), out.get(2), "load(1) redirects to load(0)");
    }

    @Test
    void storeToTheSourceInvalidatesTheCopy() {
        // load(0); store(1); [redefine slot 0]; load(1) -> NOT redirected
        List<KofOperation> out = run(List.of(
                load(0, Type.PrimitiveType.INT),
                store(1, Type.PrimitiveType.INT),
                KofLoadLiteral.ofInt(5),
                store(0, Type.PrimitiveType.INT),   // slot 0 re-written
                load(1, Type.PrimitiveType.INT)));
        assertInstanceOf(KofLoadLocal.class, out.get(out.size() - 1));
        assertEquals(load(1, Type.PrimitiveType.INT), out.get(out.size() - 1),
                "a copy of a re-written source is stale");
    }

    @Test
    void controlBoundaryClearsTheCopyMap() {
        LabelId L = new LabelId(0);
        List<KofOperation> out = run(List.of(
                load(0, Type.PrimitiveType.INT),
                store(1, Type.PrimitiveType.INT),
                new KofLabel(L),
                load(1, Type.PrimitiveType.INT)));
        assertEquals(load(1, Type.PrimitiveType.INT), out.get(3),
                "a copy crossing a label is not propagated");
    }

    @Test
    void typeMismatchBlocksTheRedirect() {
        List<KofOperation> out = run(List.of(
                load(0, Type.PrimitiveType.INT),
                store(1, Type.PrimitiveType.INT),
                load(1, Type.PrimitiveType.LONG)));   // type differs from the copy
        assertEquals(load(1, Type.PrimitiveType.LONG), out.get(2));
    }

    @Test
    void chainResolvesToTheRootSource() {
        // load(0); store(1); load(1); store(2); load(2) -> load(2) becomes load(0)
        List<KofOperation> out = run(List.of(
                load(0, Type.PrimitiveType.INT),
                store(1, Type.PrimitiveType.INT),
                load(1, Type.PrimitiveType.INT),
                store(2, Type.PrimitiveType.INT),
                load(2, Type.PrimitiveType.INT)));
        assertEquals(load(0, Type.PrimitiveType.INT), out.get(4), "the chain resolves to slot 0");
    }
}
