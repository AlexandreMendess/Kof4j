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
    void anImmediatePushIsNotCollapsed() {
        // pushq $imm cannot become a move.
        assertEquals("    pushq $5\n    popq %rax\n", result("    pushq $5\n    popq %rax\n"));
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
}
