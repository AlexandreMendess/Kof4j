package dev.kof.compiler.nat;

import java.util.List;

/**
 * x86-64 assembly fragments for the register-mapped local model (Phase D of
 * {@code docs/development/assembly-optimization-plan.md}; decision
 * {@code D-CODEGEN-REWRITE}).
 *
 * <p>Emits the exact instruction text the native backend's prologue, epilogue and
 * local load/store use when a method's int/bool locals live in physical
 * registers instead of frame slots:
 * <ul>
 *   <li>{@link #prologueSaveCalleeSaved} — push the callee-saved registers the
 *       method uses, saving them into dedicated frame slots so they survive the
 *       {@code movq %rbp, %rsp} the epilogue performs;</li>
 *   <li>{@link #epilogueRestoreCalleeSaved} — restore them from those slots at
 *       every return, before the frame is torn down;</li>
 *   <li>{@link #loadPlacedRegister} / {@link #storePlacedRegister} — a
 *       register-placed local's load/store as a {@code push}/{@code pop} (the
 *       backend's value stack holds operands in memory, so a value in a register
 *       must be pushed/popped like any other operand).</li>
 * </ul>
 *
 * <p>This is a pure string emitter (no state); the caller passes the registers and
 * the frame offsets. ADDITIVE — nothing calls it yet; it is the fragment layer the
 * emission switch-over builds on.
 */
final class NativeRegisterLocalAsm {

    private NativeRegisterLocalAsm() {
    }

    /**
     * Save each used callee-saved register into the frame at {@code slotOffset}
     * (a byte offset from {@code %rbp}, e.g. {@code -128}). <b>The caller MUST
     * extend the frame by {@code regs.size() * 8} bytes and place the save slots
     * within that extended frame</b> (right after the locals, at
     * {@code -(localsBytes + (i+1)*8)}); an offset beyond the allocated frame
     * would write below {@code %rsp} and corrupt the stack.
     */
    static void prologueSaveCalleeSaved(StringBuilder sb, List<String> regs,
                                        java.util.function.IntFunction<Integer> slotOffset) {
        int i = 0;
        for (String reg : regs) {
            sb.append("    movq ").append(reg).append(", ")
                    .append(slotOffset.apply(i)).append("(%rbp)\n");
            i++;
        }
    }

    /** Restore each used callee-saved register from its save slot. */
    static void epilogueRestoreCalleeSaved(StringBuilder sb, List<String> regs,
                                           java.util.function.IntFunction<Integer> slotOffset) {
        int i = 0;
        for (String reg : regs) {
            sb.append("    movq ").append(slotOffset.apply(i)).append("(%rbp), ")
                    .append(reg).append("\n");
            i++;
        }
    }

    /** Load a register-placed local: push its register onto the value stack. */
    static void loadPlacedRegister(StringBuilder sb, String reg) {
        sb.append("    pushq ").append(reg).append("\n");
    }

    /** Store into a register-placed local: pop the value stack into its register. */
    static void storePlacedRegister(StringBuilder sb, String reg) {
        sb.append("    popq ").append(reg).append("\n");
    }
}
