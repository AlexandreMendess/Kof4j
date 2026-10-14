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
        String body = "    pushq %r13\n    popq %rax\n    shll $4, %eax\n    pushq %rax\n    popq %rcx\n";
        assertEquals("    movq %r13, %rax\n    shll $4, %eax\n    movq %rax, %rcx\n", result(body));
    }

    @Test
    void aMulByTwoBecomesLea() {
        // movq %r13, %rax; shll $1, %eax  ->  leal (%r13d,%r13d), %eax
        assertEquals("    leal (%r13d,%r13d), %eax\n",
                result("    movq %r13, %rax\n    shll $1, %eax\n"));
    }

    @Test
    void aMulByFourBecomesLeaWithScale() {
        assertEquals("    leal 0(,%r13d,4), %eax\n",
                result("    movq %r13, %rax\n    shll $2, %eax\n"));
    }

    @Test
    void aMulByEightBecomesLeaWithScale() {
        assertEquals("    leal 0(,%r13d,8), %eax\n",
                result("    movq %r13, %rax\n    shll $3, %eax\n"));
    }

    @Test
    void aLongMulByTwoUsesLeaq() {
        assertEquals("    leaq (%r13,%r13), %rax\n",
                result("    movq %r13, %rax\n    shlq $1, %rax\n"));
    }

    @Test
    void aMulBySixteenIsNotFolded() {
        // scale 16 has no lea form (max scale 8).
        String body = "    movq %r13, %rax\n    shll $4, %eax\n";
        assertEquals(body, result(body));
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
        // movq %r12, %rcx; addl %ecx, %eax  ->  (scratch fold) movq %r12, %rax;
        //   addl %r13d, %eax  ->  (lea-add) leal (%r12d,%r13d), %eax
        assertEquals("    leal (%r12d,%r13d), %eax\n",
                result("    movq %r12, %rax\n    movq %r13, %rcx\n    addl %ecx, %eax\n"));
    }

    @Test
    void aLongScratchLoadIsFolded() {
        assertEquals("    leaq (%r12,%r13), %rax\n",
                result("    movq %r12, %rax\n    movq %r13, %rcx\n    addq %rcx, %rax\n"));
    }

    @Test
    void anAddOfTwoRegistersBecomesLea() {
        assertEquals("    leal (%ebx,%r13d), %eax\n",
                result("    movq %rbx, %rax\n    addl %r13d, %eax\n"));
    }

    @Test
    void anAddOfAnImmediateBecomesLea() {
        assertEquals("    leal 7(%ebx), %eax\n",
                result("    movq %rbx, %rax\n    addl $7, %eax\n"));
    }

    @Test
    void aLongAddOfTwoRegistersBecomesLeaq() {
        assertEquals("    leaq (%rbx,%r13), %rax\n",
                result("    movq %rbx, %rax\n    addq %r13, %rax\n"));
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

    @Test
    void aScratchLoadIntoACompareDestinationIsFolded() {
        // The loop-condition shape: the rcx load feeds the DESTINATION of a compare,
        // with an independent rax operand load in between.
        assertEquals("    movq %rbx, %rax\n    cmpl %eax, %r13d\n",
                result("    movq %r13, %rcx\n    movq %rbx, %rax\n    cmpl %eax, %ecx\n"));
    }

    @Test
    void aLongCompareDestinationUsesThe64BitName() {
        assertEquals("    movq %rbx, %rax\n    cmpq %rax, %r13\n",
                result("    movq %r13, %rcx\n    movq %rbx, %rax\n    cmpq %rax, %rcx\n"));
    }

    @Test
    void aLoadIntoACompareImmediateIsFolded() {
        // movq %rbx, %rax; cmpl $2, %eax  ->  cmpl $2, %ebx
        assertEquals("    cmpl $2, %ebx\n",
                result("    movq %rbx, %rax\n    cmpl $2, %eax\n"));
    }

    @Test
    void aLoadIntoATestIsFolded() {
        // movq %rbx, %rax; testl %eax, %eax  ->  testl %ebx, %ebx
        assertEquals("    testl %ebx, %ebx\n",
                result("    movq %rbx, %rax\n    testl %eax, %eax\n"));
    }

    @Test
    void aLongLoadIntoACompareUsesThe64BitName() {
        assertEquals("    cmpq $5, %rbx\n",
                result("    movq %rbx, %rax\n    cmpq $5, %rax\n"));
    }

    @Test
    void aLoadIntoANonFoldableOpIsKept() {
        // `imull $7, %eax` (7 has no lea form) is neither a compare nor an add -> the movq must stay.
        String body = "    movq %rbx, %rax\n    imull $7, %eax\n";
        assertEquals(body, result(body));
    }

    @Test
    void aMultiplyByThreeBecomesLea() {
        assertEquals("    leal (%ebx,%ebx,2), %eax\n",
                result("    movq %rbx, %rax\n    imull $3, %eax\n"));
    }

    @Test
    void aMultiplyByFiveBecomesLea() {
        assertEquals("    leal (%ebx,%ebx,4), %eax\n",
                result("    movq %rbx, %rax\n    imull $5, %eax\n"));
    }

    @Test
    void aMultiplyByNineBecomesLea() {
        assertEquals("    leal (%ebx,%ebx,8), %eax\n",
                result("    movq %rbx, %rax\n    imull $9, %eax\n"));
    }

    @Test
    void aLoadIntoACompareRegisterSourceIsNotFolded() {
        // The compared value must be rax; here rax is the SOURCE, so leave it alone.
        String body = "    movq %rbx, %rax\n    cmpl %eax, %ecx\n";
        assertEquals(body, result(body));
    }

    @Test
    void anImmediateAddIntoALeaBecomesTheDisplacement() {
        // leal (%ebx,%ebx,2), %eax; addl $1, %eax  ->  leal 1(%ebx,%ebx,2), %eax
        assertEquals("    leal 1(%ebx,%ebx,2), %eax\n",
                result("    leal (%ebx,%ebx,2), %eax\n    addl $1, %eax\n"));
    }

    @Test
    void anImmediateAddIntoALeaBaseBecomesTheDisplacement() {
        assertEquals("    leal 7(%ebx), %eax\n",
                result("    leal (%ebx), %eax\n    addl $7, %eax\n"));
    }

    @Test
    void anAddIntoALeaWithAnExistingDisplacementIsNotFolded() {
        // Keep the match unambiguous: a lea with a displacement is left alone.
        String body = "    leal 3(%ebx), %eax\n    addl $1, %eax\n";
        assertEquals(body, result(body));
    }

    @Test
    void aNonImmediateAddIntoALeaIsNotFolded() {
        String body = "    leal (%ebx,%ebx,2), %eax\n    addl %ecx, %eax\n";
        assertEquals(body, result(body));
    }
}
