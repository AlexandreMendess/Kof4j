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
        collapsed += foldScratchLoadIntoOp(lines);
        sb.setLength(from);
        sb.append(String.join("\n", lines));
        return collapsed;
    }

    /** 64-bit register name -> its 32-bit name (for substituting a source operand). */
    private static final java.util.Map<String, String> REG32 = java.util.Map.ofEntries(
            java.util.Map.entry("%rax", "%eax"), java.util.Map.entry("%rcx", "%ecx"),
            java.util.Map.entry("%rdx", "%edx"), java.util.Map.entry("%rbx", "%ebx"),
            java.util.Map.entry("%rsp", "%esp"), java.util.Map.entry("%rbp", "%ebp"),
            java.util.Map.entry("%rsi", "%esi"), java.util.Map.entry("%rdi", "%edi"),
            java.util.Map.entry("%r8", "%r8d"), java.util.Map.entry("%r9", "%r9d"),
            java.util.Map.entry("%r10", "%r10d"), java.util.Map.entry("%r11", "%r11d"),
            java.util.Map.entry("%r12", "%r12d"), java.util.Map.entry("%r13", "%r13d"),
            java.util.Map.entry("%r14", "%r14d"), java.util.Map.entry("%r15", "%r15d"));

    /** The binary ops (not shifts — the shift count must be %cl) whose second operand
     *  may be substituted from %rcx with the loading {@code movq %Y, %rcx} removed. */
    private static final java.util.Set<String> SUBSTITUTABLE = java.util.Set.of(
            "addl", "addq", "subl", "subq", "imull", "imulq", "andl", "andq",
            "orl", "orq", "xorl", "xorq", "cmpl", "cmpq");

    /**
     * Folds {@code movq %Y, %rcx} + {@code OP %ecx/%rcx, %eax/%rax} into
     * {@code OP %Y32/%Y, %eax/%rax}, dropping the scratch load. The op reads only
     * the low 32 (or the full 64) of rcx, which the movq set to exactly Y, so the
     * substitution is exact; the result and the flags are unchanged.
     *
     * <p>Conservative guard: only applied when {@code %rcx}/{@code %ecx} appears in
     * the region SOLELY as such a scratch load and its immediately-following op —
     * if rcx is used anywhere else (e.g. a register-placed local), nothing is folded.
     */
    private static int foldScratchLoadIntoOp(List<String> lines) {
        // Guard: rcx must be scratch-only in this region — every mention is either
        // a `movq X, %rcx` IMMEDIATELY followed by a substitutable op (a scratch
        // load), or that op itself. Anything else (e.g. `movq %rax, %rcx` storing
        // into a register-placed local) disables the whole fold.
        for (int i = 0; i < lines.size(); i++) {
            String t = lines.get(i).trim();
            if (!mentionsRcx(t)) continue;
            if (isSubstitutableOp(t)) continue;
            boolean isLoad = t.startsWith("movq ") && t.endsWith(", %rcx");
            int nx = i + 1;
            while (nx < lines.size() && lines.get(nx).trim().startsWith(".loc ")) nx++;
            boolean loadFollowedByOp = isLoad && nx < lines.size() && isSubstitutableOp(lines.get(nx).trim());
            if (!loadFollowedByOp) return 0;
        }
        int folded = 0;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i + 1 < lines.size(); i++) {
                String load = lines.get(i).trim();
                if (!load.startsWith("movq ") || !load.endsWith(", %rcx")) continue;
                String y = load.substring("movq ".length(), load.length() - ", %rcx".length()).trim();
                if (!y.startsWith("%") || y.equals("%rax") || y.equals("%rcx")) continue;
                int j = i + 1;
                while (j < lines.size() && lines.get(j).trim().startsWith(".loc ")) j++;
                if (j >= lines.size()) continue;
                String op = lines.get(j).trim();
                if (!isSubstitutableOp(op)) continue;
                String mnemonic = op.substring(0, op.indexOf(' '));
                String rest = op.substring(op.indexOf(' ') + 1);           // "src, dst"
                int comma = rest.indexOf(',');
                String src = rest.substring(0, comma).trim();
                String dst = rest.substring(comma + 1).trim();
                String newSrc = src.equals("%rcx") ? y : REG32.getOrDefault(y, y);
                if (!src.equals("%ecx") && !src.equals("%rcx")) continue;
                lines.set(j, "    " + mnemonic + " " + newSrc + ", " + dst);
                lines.remove(i);
                folded++;
                changed = true;
                break;
            }
        }
        return folded;
    }

    private static boolean mentionsRcx(String t) {
        return t.contains("%rcx") || t.contains("%ecx") || t.contains("%cl");
    }

    private static boolean isSubstitutableOp(String t) {
        int sp = t.indexOf(' ');
        if (sp < 0) return false;
        if (!SUBSTITUTABLE.contains(t.substring(0, sp))) return false;
        String rest = t.substring(sp + 1);
        int comma = rest.indexOf(',');
        if (comma < 0) return false;
        String src = rest.substring(0, comma).trim();
        return src.equals("%ecx") || src.equals("%rcx");
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
