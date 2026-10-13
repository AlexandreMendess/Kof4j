package dev.kof.compiler.backend;

import dev.kof.compiler.KofFunctionAddress;
import dev.kof.compiler.KofLoadLiteral;
import dev.kof.compiler.KofLoadLocal;
import dev.kof.compiler.KofOperation;
import dev.kof.compiler.KofStoreLocal;
import dev.kof.compiler.SourcePosition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Liveness-driven dead-store elimination (Phase C of
 * {@code docs/development/assembly-optimization-plan.md} §5: "allocation" /
 * §10).
 *
 * <p>This pass CONSUMES the Phase C {@link LivenessAnalysis}: a store to a slot
 * is dead when the slot is not live immediately after the store — the stored
 * value is never read before being overwritten (or never read at all). That is
 * strictly more general than the Phase B overwrite-only rule: it also removes a
 * store whose slot is never read anywhere in its block's live range.
 *
 * <p>A dead store is removed together with its producer ONLY when the producer
 * is a pure, zero-input, one-output op ({@link KofLoadLiteral},
 * {@link KofLoadLocal}, {@link KofFunctionAddress}) — removing a store whose
 * value came from an opaque producer would leave that pushed value on the stack
 * (an unbalanced op stream → ASM frame crash, the Phase B lesson). The read-back
 * {@code store(slot); load(slot)} pair is never touched (the backends
 * pattern-match on it).
 *
 * <p>Pure computation over the IR plus the liveness it derives; it changes no
 * behavior until a caller opts in.
 */
public final class LivenessDeadStores implements IrPass {

    @Override
    public String name() {
        return "liveness-dead-stores";
    }

    @Override
    public List<KofOperation> run(List<KofOperation> ops,
                                  Map<KofOperation, SourcePosition> positions) {
        LivenessAnalysis live = LivenessAnalysis.ofOps(ops);
        Set<KofOperation> dead = Collections.newSetFromMap(new IdentityHashMap<>());
        KofOperation prev = null;
        for (KofOperation op : ops) {
            if (op instanceof KofStoreLocal sl) {
                int slot = sl.index();
                // A store is dead when its slot is not live after it. The
                // store;load read-back needs no special case: a following
                // load(slot) makes the slot live, so it is never removed here.
                // A store is only removable together with a pure producer (an
                // opaque producer would leave its pushed value on the stack).
                if (!live.isLiveAfter(op, slot) && isPureProducer(prev)) {
                    dead.add(prev);
                    dead.add(op);
                }
            }
            prev = op;
        }
        if (dead.isEmpty()) return ops;
        List<KofOperation> out = new ArrayList<>(ops.size());
        for (KofOperation op : ops) {
            if (dead.contains(op)) {
                positions.remove(op);
                continue;
            }
            out.add(op);
        }
        return out;
    }

    private static boolean isPureProducer(KofOperation op) {
        return op instanceof KofLoadLiteral || op instanceof KofLoadLocal
                || op instanceof KofFunctionAddress;
    }
}
