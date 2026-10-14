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
                // The destination is always a register in this emitter.
                if (!dst.startsWith("%")) continue;
                if (src.startsWith("$")) {
                    // An immediate fits the push only when it is an int or a small
                    // long (see emitLoadLiteral), so `movq $imm, %reg` (imm32,
                    // sign-extended) is exact.
                    lines.set(i, "    movq " + src + ", " + dst);
                    lines.remove(j);
                } else if (src.startsWith("%")) {
                    // Register-register: exact, avoids memory encoding edge cases.
                    if (src.equals(dst)) {
                        lines.remove(j);        // no-op: drop the pop and the push
                        lines.remove(i);
                    } else {
                        lines.set(i, "    movq " + src + ", " + dst);
                        lines.remove(j);
                    }
                } else {
                    continue; // memory source: leave alone
                }
                collapsed++;
                changed = true;
                break; // restart the scan after a change
            }
        }
        collapsed += collapseSpannedPushPop(lines);
        sb.setLength(from);
        sb.append(String.join("\n", lines));
        return collapsed;
    }

    /**
     * Collapses a push/pop SPANNING exactly one {@code movq}:
     *
     * <pre>
     *   pushq %A            movq %A, %D
     *   movq  %B, %C   ==&gt;  movq %B, %C
     *   popq  %D
     * </pre>
     *
     * <p>This is the shape of a binary op whose two operands are both register
     * values: the first operand is pushed, the second is loaded into the scratch
     * register, then the first is popped back. The two moves are independent, so
     * the reorder is safe — EXCEPT when it would read a value after it was
     * clobbered: the first move writes {@code %D}, so the middle move must not
     * READ {@code %D} ({@code B != %D}) and must not WRITE {@code %D}
     * ({@code C != %D}, which the original relied on the pop to overwrite last).
     * All operands must be registers; anything else is left alone.
     */
    private static int collapseSpannedPushPop(List<String> lines) {
        int collapsed = 0;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i + 1 < lines.size(); i++) {
                String push = lines.get(i).trim();
                if (!push.startsWith("pushq ")) continue;
                int j = i + 1;
                while (j < lines.size() && lines.get(j).trim().startsWith(".loc ")) j++;
                if (j >= lines.size()) continue;
                int k = j + 1;
                while (k < lines.size() && lines.get(k).trim().startsWith(".loc ")) k++;
                if (k >= lines.size()) continue;
                String mid = lines.get(j).trim();
                String pop = lines.get(k).trim();
                if (!pop.startsWith("popq ")) continue;
                if (!mid.startsWith("movq ")) continue;
                String[] mv = mid.substring("movq ".length()).split(",");
                if (mv.length != 2) continue;
                String srcA = push.substring("pushq ".length()).trim();
                String srcB = mv[0].trim();
                String dstC = mv[1].trim();
                String dstD = pop.substring("popq ".length()).trim();
                if (!srcA.startsWith("%") || !srcB.startsWith("%") || !dstC.startsWith("%")
                        || !dstD.startsWith("%")) {
                    continue;
                }
                if (srcB.equals(dstD) || dstC.equals(dstD)) continue; // clobber hazard
                if (srcA.equals(dstD)) {
                    // pushq %D; movq %B, %C; popq %D  ->  the first move is a no-op.
                    // Remove the POP first: removing the earlier push would shift
                    // the pop's index and delete the wrong line.
                    lines.remove(k);
                    lines.remove(i);
                } else {
                    lines.set(i, "    movq " + srcA + ", " + dstD);
                    lines.remove(k);
                }
                collapsed++;
                changed = true;
                break;
            }
        }
        return collapsed;
    }
}
