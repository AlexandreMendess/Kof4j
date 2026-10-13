package dev.kof.compiler.backend;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Register allocation over the {@link InterferenceGraph} (Phase C of
 * {@code docs/development/assembly-optimization-plan.md} §5: "allocation").
 *
 * <p>The initial strategy is deliberately simple and deterministic, as §5
 * allows: greedy graph coloring in descending-degree order (ties broken by
 * ascending slot index, §20). Each node takes the lowest colour not used by any
 * of its neighbours. A node with no free colour within the register budget is
 * SPILLED (colour {@code -1}) — the allocator never assigns two interfering
 * slots the same register, so the result is always correct, at worst
 * sub-optimal.
 *
 * <p>Colors are abstract register numbers ({@code 0..registerCount-1}); mapping
 * them to a real ISA's registers is Phase D/E work (target lowering), and the
 * spill mechanism (reloading a spilled slot around its uses) is the follow-on
 * slice. This class only computes the assignment; nothing consumes it yet, so
 * emitted code is unchanged (the same additive discipline as Phases A and B).
 */
public final class RegisterAllocator {

    /** The allocation result: a colour per slot ({@code -1} = spilled) plus the
     *  set of spilled slots. */
    public record Allocation(Map<Integer, Integer> colorOf, Set<Integer> spills) {

        /** The colour assigned to a slot, or {@code -1} if spilled/unseen. */
        public int color(int slot) {
            return colorOf.getOrDefault(slot, -1);
        }

        /** True when the slot could not be coloured and must spill to memory. */
        public boolean isSpilled(int slot) {
            return spills.contains(slot);
        }

        /** The number of distinct colours actually used. */
        public int colorsUsed() {
            Set<Integer> used = new HashSet<>();
            for (int c : colorOf.values()) {
                if (c >= 0) used.add(c);
            }
            return used.size();
        }
    }

    private RegisterAllocator() {
    }

    /** Allocates {@code registerCount} colours over the graph. */
    public static Allocation allocate(InterferenceGraph graph, int registerCount) {
        if (registerCount < 1) {
            throw new IllegalArgumentException("registerCount must be >= 1");
        }
        List<Integer> nodes = new ArrayList<>(graph.nodes());
        nodes.sort(Comparator.<Integer>comparingInt(graph::degree).reversed()
                .thenComparingInt(Integer::intValue));

        Map<Integer, Integer> colorOf = new TreeMap<>();
        Set<Integer> spills = new java.util.TreeSet<>();
        for (int slot : nodes) {
            Set<Integer> used = new HashSet<>();
            for (int n : graph.neighbors(slot)) {
                Integer c = colorOf.get(n);
                if (c != null && c >= 0) used.add(c);
            }
            int chosen = -1;
            for (int c = 0; c < registerCount; c++) {
                if (!used.contains(c)) {
                    chosen = c;
                    break;
                }
            }
            colorOf.put(slot, chosen);
            if (chosen < 0) spills.add(slot);
        }
        return new Allocation(Map.copyOf(colorOf), Set.copyOf(spills));
    }
}
