package dev.kof.compiler;

import java.util.List;

/**
 * Runs the registered {@link CodegenStep}s in registration order (R4,
 * `DECISIONS.md` §D-CODEGEN-STEP 21/09). The empty (or null) registry is the
 * identity: the caller's module is returned untouched, so the compiler's
 * observable behavior is unchanged by construction.
 */
public final class CodegenStepPipeline {

    private CodegenStepPipeline() {
    }

    /**
     * Applies every step in order and returns the final module. A {@code null}
     * or empty {@code steps} list (or a {@code null} module) is the identity.
     *
     * @throws IllegalStateException if a step returns {@code null} — a codegen
     *         step must never silently drop the module (R6).
     */
    public static IRModule run(List<CodegenStep> steps, IRModule module, CompilerDriver driver) {
        if (steps == null || steps.isEmpty() || module == null) {
            return module;
        }
        IRModule current = module;
        for (CodegenStep step : steps) {
            IRModule next = step.apply(current, driver);
            if (next == null) {
                throw new IllegalStateException("codegen step '" + step.name() + "' returned a null module");
            }
            current = next;
        }
        return current;
    }
}
