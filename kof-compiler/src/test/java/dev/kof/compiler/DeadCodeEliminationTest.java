package dev.kof.compiler;

import dev.kof.compiler.backend.DeadCodeElimination;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase B of the assembly-optimization plan: dead store elimination. A store
 * whose slot is overwritten before any read is dead; its pure producer is
 * removed with it. Conservative at control boundaries and opaque producers.
 */
class DeadCodeEliminationTest {

    private final DeadCodeElimination pass = new DeadCodeElimination();

    private List<KofOperation> run(List<KofOperation> ops) {
        return pass.run(ops, new java.util.IdentityHashMap<>());
    }

    private static KofLoadLiteral lit(int v) { return KofLoadLiteral.ofInt(v); }
    private static KofLoadLocal load(int s) { return new KofLoadLocal(Type.PrimitiveType.INT, s); }
    private static KofStoreLocal store(int s) { return new KofStoreLocal(Type.PrimitiveType.INT, s); }

    @Test
    void overwrittenStoreWithPureProducerIsRemoved() {
        // lit(1); store(0); lit(2); store(0)  -> the first lit+store are dead.
        List<KofOperation> out = run(List.of(lit(1), store(0), lit(2), store(0)));
        assertEquals(2, out.size(), "the dead producer + store are removed");
        assertEquals(lit(2), out.get(0));
        assertEquals(store(0), out.get(1));
    }

    @Test
    void aReadBetweenStoresKeepsTheFirstStoreAlive() {
        // lit(1); store(0); load(0); lit(2); store(0)  -> nothing removed.
        List<KofOperation> out = run(List.of(lit(1), store(0), load(0), lit(2), store(0)));
        assertEquals(5, out.size(), "a read keeps the earlier store alive");
    }

    @Test
    void storeToAnotherSlotKeepsThisStoreLive() {
        // lit(1); store(0); lit(2); store(1); lit(3); store(0) -> first store dead
        // (overwritten by the last), but the store to slot 1 is untouched.
        List<KofOperation> out = run(List.of(
                lit(1), store(0), lit(2), store(1), lit(3), store(0)));
        // lit(1)+store(0) removed -> 4 ops remain.
        assertEquals(4, out.size());
    }

    @Test
    void controlBoundaryClearsPendingStores() {
        LabelId L = new LabelId(0);
        // lit(1); store(0); label L; lit(2); store(0) -> the label clears; nothing removed.
        List<KofOperation> out = run(List.of(lit(1), store(0), new KofLabel(L), lit(2), store(0)));
        assertEquals(5, out.size(), "a store crossing a label is not assumed dead");
    }

    @Test
    void opaqueProducerIsNotRemovedEvenIfStoreIsDead() {
        // [call]; store(0); lit(2); store(0) -> the store(0) after the call is dead,
        // but its producer is opaque, so only the STORE is removed, not the call.
        List<KofOperation> out = run(List.of(
                new KofCall(null, "m", List.of(), Type.PrimitiveType.INT, KofCallKind.STATIC),
                store(0), lit(2), store(0)));
        assertTrue(out.stream().anyMatch(o -> o instanceof KofCall),
                "the opaque producer (call) is never removed");
        assertEquals(3, out.size(), "only the dead store is removed");
    }
}
