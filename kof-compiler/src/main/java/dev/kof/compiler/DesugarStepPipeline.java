package dev.kof.compiler;

import java.util.List;

/**
 * Runs the registered {@link DesugarStep}s in registration order at the AST
 * phase (2.2.3, `DECISIONS.md` §D-DESUGAR-STEP 21/09). The default registry is
 * the four built-in desugars in their historical order
 * ({@link DesugarSteps#defaults()}), so the compiler's observable behavior is
 * unchanged (freeze rule 3).
 */
public final class DesugarStepPipeline {

    private DesugarStepPipeline() {
    }

    /**
     * Applies every step in order and returns the final unit. A {@code null}
     * or empty {@code steps} list (or a {@code null} unit) is the identity.
     *
     * @throws IllegalStateException if a step returns {@code null} — a desugar
     *         step must never silently drop the unit (R6).
     */
    public static CompilationUnitNode run(List<DesugarStep> steps, CompilationUnitNode unit, CompilerDriver driver) {
        if (steps == null || steps.isEmpty() || unit == null) {
            return unit;
        }
        CompilationUnitNode current = unit;
        for (DesugarStep step : steps) {
            CompilationUnitNode next = step.apply(current, driver);
            if (next == null) {
                throw new IllegalStateException("desugar step '" + step.name() + "' returned a null unit");
            }
            current = next;
        }
        return current;
    }
}
