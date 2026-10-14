package dev.kof.compiler.backend;

import dev.kof.compiler.IRBasicBlock;
import dev.kof.compiler.IRLocalVariable;
import dev.kof.compiler.IRMethod;
import dev.kof.compiler.KofOperation;
import dev.kof.compiler.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Per-method register-placement driver (Phase D of
 * {@code docs/development/assembly-optimization-plan.md}; decision
 * {@code D-CODEGEN-REWRITE}, maintainer 13/10).
 *
 * <p>Chains the Phase C analyses and the Phase D mapping for one method: build
 * the linear op stream, run {@link LivenessAnalysis} → {@link InterferenceGraph}
 * → {@link RegisterAllocator} (with {@link SpillCost}) → {@link RegisterAssignment},
 * yielding the physical register (or memory) each local slot is placed in. It is
 * the single entry point the codegen rewrite will call.
 *
 * <p>ADDITIVE — it computes a placement and returns it; it does not touch the
 * method or any emission. The same additive discipline as the Phase A–C analyses.
 */
public final class RegisterPlanner {

    /** The placement for one method: the assignment plus the allocator's result. */
    public record Placement(RegisterAssignment.Assignment assignment,
                            RegisterAllocator.Allocation allocation,
                            int registerCount) {

        /** The physical register for a slot, or {@code null} if it is in memory. */
        public String register(int slot) {
            return assignment.register(slot);
        }

        /** True when the slot is placed in a physical register. */
        public boolean isRegister(int slot) {
            return assignment.isRegister(slot);
        }
    }

    private RegisterPlanner() {
    }

    /**
     * Plans register placement for a method, using {@code registerCount} integer
     * and the same number of SSE registers (a simple, deterministic budget).
     */
    public static Placement plan(IRMethod method, int registerCount) {
        List<KofOperation> ops = flatten(method);
        LivenessAnalysis live = LivenessAnalysis.ofOps(ops);
        InterferenceGraph graph = InterferenceGraph.of(ops, live);
        SpillCost cost = SpillCost.ofOps(ops);
        RegisterAllocator.Allocation alloc = RegisterAllocator.allocate(graph, registerCount, cost);
        Map<Integer, Type> slotTypes = slotTypes(method);
        RegisterAssignment.Assignment assignment = RegisterAssignment.of(slotTypes, alloc);
        return new Placement(assignment, alloc, registerCount);
    }

    /** The declared type of each local slot of the method. */
    private static Map<Integer, Type> slotTypes(IRMethod method) {
        Map<Integer, Type> types = new TreeMap<>();
        for (IRLocalVariable lv : method.localVariables()) {
            types.put(lv.index(), lv.type());
        }
        return types;
    }

    private static List<KofOperation> flatten(IRMethod method) {
        List<KofOperation> ops = new ArrayList<>();
        for (IRBasicBlock b : method.basicBlocks()) {
            ops.addAll(b.operations());
        }
        return ops;
    }
}
