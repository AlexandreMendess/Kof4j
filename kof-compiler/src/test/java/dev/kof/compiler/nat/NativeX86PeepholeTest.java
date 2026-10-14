package dev.kof.compiler.nat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §7 x86_64 of {@code docs/development/assembly-optimization-plan.md}
 * ("lower-cost instruction selection"): the {@code pushq %X; popq %Y} pair
 * collapses to {@code movq %X, %Y} (or vanishes when {@code X == Y}).
 */
class NativeX86PeepholeTest {

    private static int collapse(String body) {
        StringBuilder sb = new StringBuilder(body);
        return NativeX86Peephole.collapsePushPopPairs(sb, 0);
    }

    private static String result(String body) {
        StringBuilder sb = new StringBuilder(body);
        NativeX86Peephole.collapsePushPopPairs(sb, 0);
        return sb.toString();
    }

    @Test
    void aPushPopPairBecomesAMove() {
        assertEquals("    movq %rbx, %rax\n", result("    pushq %rbx\n    popq %rax\n"));
        assertEquals(1, collapse("    pushq %rbx\n    popq %rax\n"));
    }

    @Test
    void aSelfMovePairIsDropped() {
        assertEquals("", result("    pushq %rax\n    popq %rax\n"));
        assertEquals(1, collapse("    pushq %rax\n    popq %rax\n"));
    }

    @Test
    void aLocBetweenDoesNotBlockTheCollapse() {
        assertEquals("    .loc 1 1 0\n    movq %rbx, %rax\n    .loc 1 2 0\n",
                result("    .loc 1 1 0\n    pushq %rbx\n    .loc 1 2 0\n    popq %rax\n"));
    }

    @Test
    void anImmediatePushBecomesAMove() {
        // `pushq $imm; popq %reg` is exactly `movq $imm, %reg` (imm32, sign-extended).
        assertEquals("    movq $5, %rax\n", result("    pushq $5\n    popq %rax\n"));
    }

    @Test
    void aMemoryPushIsNotCollapsed() {
        // A memory source has no `movq mem, %reg` equivalent guaranteed here; leave it.
        assertEquals("    pushq -8(%rbp)\n    popq %rax\n", result("    pushq -8(%rbp)\n    popq %rax\n"));
    }

    @Test
    void onlyTheRegionFromIsTouched() {
        // A pair BEFORE `from` (outside the method body) is left alone.
        StringBuilder sb = new StringBuilder("    pushq %rbx\n    popq %rax\n");
        NativeX86Peephole.collapsePushPopPairs(sb, sb.length());
        assertEquals("    pushq %rbx\n    popq %rax\n", sb.toString());
    }

    @Test
    void chainedPairsAllCollapse() {
        String body = "    pushq %r13\n    popq %rax\n    shll $1, %eax\n    pushq %rax\n    popq %rcx\n";
        assertEquals("    movq %r13, %rax\n    shll $1, %eax\n    movq %rax, %rcx\n", result(body));
    }

    @Test
    void aSpannedPushPopBecomesTwoIndependentMoves() {
        // pushq %rbx; movq %r12, %rsi; popq %rax  ->  movq %rbx, %rax; movq %r12, %rsi
        assertEquals("    movq %rbx, %rax\n    movq %r12, %rsi\n",
                result("    pushq %rbx\n    movq %r12, %rsi\n    popq %rax\n"));
    }

    @Test
    void aSpannedSelfPopDropsOnlyThePushAndPop() {
        // pushq %rax; movq %r12, %rsi; popq %rax  ->  movq %r12, %rsi
        // (regression: removing the push before the pop shifted the index and
        //  deleted the wrong line, leaving a stray pop -> SIGSEGV)
        assertEquals("    movq %r12, %rsi\n",
                result("    pushq %rax\n    movq %r12, %rsi\n    popq %rax\n"));
    }

    @Test
    void aSpannedClobberHazardIsNotCollapsed() {
        // The middle move reads %rax, which the new `movq %rbx, %rax` would clobber.
        assertEquals("    pushq %rbx\n    movq %rax, %rsi\n    popq %rax\n",
                result("    pushq %rbx\n    movq %rax, %rsi\n    popq %rax\n"));
    }

    @Test
    void aScratchLoadIsFoldedIntoTheOp() {
        // movq %r12, %rcx; addl %ecx, %eax  ->  addl %r12d, %eax
        assertEquals("    movq %r12, %rax\n    addl %r13d, %eax\n",
                result("    movq %r12, %rax\n    movq %r13, %rcx\n    addl %ecx, %eax\n"));
    }

    @Test
    void aLongScratchLoadIsFolded() {
        assertEquals("    movq %r12, %rax\n    addq %r13, %rax\n",
                result("    movq %r12, %rax\n    movq %r13, %rcx\n    addq %rcx, %rax\n"));
    }

    @Test
    void aScratchLoadIsNotFoldedWhenRcxIsUsedElsewhere() {
        // rcx used as a real destination (a register-placed local) -> leave the whole region.
        String body = "    movq %r12, %rax\n    movq %r13, %rcx\n    addl %ecx, %eax\n    movq %rax, %rcx\n";
        assertEquals(body, result(body));
    }

    @Test
    void aShiftCountIsNotSubstituted() {
        // A shift must keep the count in %cl.
        String body = "    movq %r12, %rax\n    movq %r13, %rcx\n    shll %cl, %eax\n";
        assertEquals(body, result(body));
    }
}
