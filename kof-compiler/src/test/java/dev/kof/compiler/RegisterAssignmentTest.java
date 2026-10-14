package dev.kof.compiler;

import dev.kof.compiler.backend.RegisterAllocator;
import dev.kof.compiler.backend.RegisterAssignment;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase D of the assembly-optimization plan ({@code D-CODEGEN-REWRITE}): the
 * local→physical-register mapping. Pure data — these tests pin the colour→register
 * resolution per register class and the spill/in-memory cases; compiler behavior
 * is unchanged.
 */
class RegisterAssignmentTest {

    private static RegisterAllocator.Allocation alloc(Map<Integer, Integer> colors, Set<Integer> spills) {
        return new RegisterAllocator.Allocation(colors, spills);
    }

    @Test
    void anIntegerColorMapsToTheIntegerFile() {
        var a = RegisterAssignment.of(
                Map.of(1, Type.PrimitiveType.INT),
                alloc(Map.of(1, 0), Set.of()));
        assertEquals("rbx", a.register(1), "colour 0 in INTEGER is the first allocatable (callee-saved)");
        assertTrue(a.isRegister(1));
    }

    @Test
    void anSseColorMapsToTheSseFile() {
        var a = RegisterAssignment.of(
                Map.of(2, Type.PrimitiveType.DOUBLE),
                alloc(Map.of(2, 0), Set.of()));
        assertEquals("xmm0", a.register(2), "colour 0 in SSE is xmm0");
    }

    @Test
    void distinctColorsMapToDistinctRegisters() {
        var a = RegisterAssignment.of(
                Map.of(1, Type.PrimitiveType.INT, 2, Type.PrimitiveType.INT),
                alloc(Map.of(1, 0, 2, 1), Set.of()));
        assertNotEquals(a.register(1), a.register(2));
        assertEquals("rbx", a.register(1));
        assertEquals("r12", a.register(2));
    }

    @Test
    void aSpilledSlotIsInMemory() {
        var a = RegisterAssignment.of(
                Map.of(1, Type.PrimitiveType.INT),
                alloc(Map.of(1, -1), Set.of(1)));
        assertTrue(a.isInMemory(1));
        assertFalse(a.isRegister(1));
        assertNull(a.register(1));
    }

    @Test
    void anUnknownTypeGoesToMemory() {
        var a = RegisterAssignment.of(
                Map.of(),   // no known type for slot 5
                alloc(Map.of(5, 0), Set.of()));
        assertTrue(a.isInMemory(5));
    }

    @Test
    void aColorBeyondTheRegisterCountGoesToMemory() {
        // INTEGER has 14 allocatable registers; colour 99 is out of range.
        var a = RegisterAssignment.of(
                Map.of(1, Type.PrimitiveType.INT),
                alloc(Map.of(1, 99), Set.of()));
        assertTrue(a.isInMemory(1));
    }

    @Test
    void composesWithTheAllocatorEndToEnd() {
        // The full Phase C→D chain: two interfering int slots, 2 registers.
        java.util.List<KofOperation> ops = java.util.List.of(
                KofLoadLiteral.ofInt(7),
                new KofStoreLocal(Type.PrimitiveType.INT, 1),
                KofLoadLiteral.ofInt(8),
                new KofStoreLocal(Type.PrimitiveType.INT, 2),
                new KofLoadLocal(Type.PrimitiveType.INT, 1),
                new KofLoadLocal(Type.PrimitiveType.INT, 2),
                new KofReturn(Type.PrimitiveType.INT));
        var alloc = RegisterAllocator.allocate(
                dev.kof.compiler.backend.InterferenceGraph.ofOps(ops), 2);
        var a = RegisterAssignment.of(
                Map.of(1, Type.PrimitiveType.INT, 2, Type.PrimitiveType.INT), alloc);
        assertTrue(a.isRegister(1) && a.isRegister(2),
                "both slots fit in registers");
        assertNotEquals(a.register(1), a.register(2),
                "interfering slots get distinct physical registers");
    }
}
