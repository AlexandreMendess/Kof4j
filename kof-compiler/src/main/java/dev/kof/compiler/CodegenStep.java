package dev.kof.compiler;

/**
 * Internal compile-time codegen hook (R4, `DECISIONS.md` §D-CODEGEN-STEP,
 * 21/09/2026 — option A: an internal hook with NO user syntax).
 *
 * <p>A step receives the OPTIMIZED IR and returns a (possibly transformed)
 * module, right before the backend emits or the interpreter runs. Steps are
 * registered by compiler internals only; the default registry is empty, so the
 * pipeline is the identity and existing behavior is unchanged (zero regression).
 *
 * <p>This is the seam that future declarative desugars (e.g. `infra "prod" {}`,
 * 3.2) plug into. It does not add language surface — any user-facing form is its
 * own later decision (rule 11 gate).
 */
public interface CodegenStep {

    /** Stable telemetry/diagnostic name. Never null, never empty. */
    String name();

    /**
     * Transforms the optimized module. Must return a non-null module; a null
     * return is a programming error and fails loudly (never silent, R6).
     */
    IRModule apply(IRModule module, CompilerDriver driver);
}
