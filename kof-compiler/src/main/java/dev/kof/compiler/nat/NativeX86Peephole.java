package dev.kof.compiler.nat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * §7 x86_64 of {@code docs/development/assembly-optimization-plan.md}
 * ("lower-cost instruction selection"): a text peephole over an emitted method
 * body. The x86-64 emitter is a stack machine — a value produced by one op is
 * {@code push}ed and consumed by the next op's {@code pop}. When a push is
 * IMMEDIATELY followed by a pop (only {@code .loc} debug lines between), the two
 * instructions are a register move:
 *
 * <pre>
 *   pushq %rbx          movq %rbx, %rax      (or: nothing, when src == dst)
 *   popq  %rax
 * </pre>
 *
 * <p>This is unconditionally safe: nothing runs between adjacent lines, so the
 * pair's net effect is exactly {@code dst = src} (the stack pointer is unchanged
 * either way). It removes one instruction and one stack round-trip per match —
 * common around binary operands and register-placed locals.
 */
final class NativeX86Peephole {

    private NativeX86Peephole() {
    }

    /**
     * Collapses {@code pushq}/{@code popq} pairs in {@code sb} from {@code from}
     * onward (the region one method appended), in place. Returns the number of
     * pairs collapsed (for tests/metrics).
     */
    static int collapsePushPopPairs(StringBuilder sb, int from) {
        String region = sb.substring(from);
        List<String> lines = new ArrayList<>(Arrays.asList(region.split("\n", -1)));
        int collapsed = 0;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i + 1 < lines.size(); i++) {
                String push = lines.get(i).trim();
                if (!push.startsWith("pushq ")) continue;
                // find the next non-.loc line
                int j = i + 1;
                while (j < lines.size() && lines.get(j).trim().startsWith(".loc ")) j++;
                if (j >= lines.size()) continue;
                String pop = lines.get(j).trim();
                if (!pop.startsWith("popq ")) continue;
                String src = push.substring("pushq ".length()).trim();
                String dst = pop.substring("popq ".length()).trim();
                // Only register-register pairs: `movq %reg, %reg` is exact and
                // avoids immediate/memory encoding edge cases.
                if (!src.startsWith("%") || !dst.startsWith("%")) continue;
                if (src.equals(dst)) {
                    lines.remove(j);        // no-op: drop the pop and the push
                    lines.remove(i);
                } else {
                    lines.set(i, "    movq " + src + ", " + dst);
                    lines.remove(j);
                }
                collapsed++;
                changed = true;
                break; // restart the scan after a change
            }
        }
        sb.setLength(from);
        sb.append(String.join("\n", lines));
        return collapsed;
    }
}
