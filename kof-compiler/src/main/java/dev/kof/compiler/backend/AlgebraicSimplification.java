package dev.kof.compiler.backend;

import dev.kof.compiler.KofBinary;
import dev.kof.compiler.KofBinaryOp;
import dev.kof.compiler.KofFunctionAddress;
import dev.kof.compiler.KofLoadLiteral;
import dev.kof.compiler.KofLoadLocal;
import dev.kof.compiler.KofOperation;
import dev.kof.compiler.SourcePosition;
import dev.kof.compiler.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Algebraic simplification over the linear op stream (Phase B/§3 O1 of
 * {@code docs/development/assembly-optimization-plan.md}).
 *
 * <p>Folds the ABSORBING elements of int/long arithmetic — the identities the
 * constant folder's {@code foldIdentity} does not cover, because the result is a
 * constant even when the other operand is a runtime value:
 * {@code x * 0 → 0}, {@code x & 0 → 0}, {@code x % 1 → 0}, {@code x % -1 → 0}.
 *
 * <p>On the stack IR the pattern is {@code <producer>; LoadLiteral(0|1|-1);
 * Binary(op)}. The rewrite keeps only a {@code LoadLiteral(0)}, so the stack
 * depth is unchanged (push,push,pop2push1 → push1).
 *
 * <p><b>Safety (conservative by construction):</b>
 * <ul>
 *   <li>int/long only — never float/double ({@code x * 0.0} is NaN when {@code x}
 *       is NaN or infinite, and {@code -0.0} otherwise);</li>
 *   <li>the left producer must be a pure, zero-input op
 *       ({@link KofLoadLiteral}, {@link KofLoadLocal}, {@link KofFunctionAddress})
 *       — removing a call would drop its side effects;</li>
 *   <li>only non-trapping ops ({@code *}, {@code &}, {@code %}) — {@code /} by
 *       zero would trap and is never touched;</li>
 *   <li>{@code % 1} and {@code % -1} are exactly 0 for every int/long value
 *       (no overflow), so the fold is total.</li>
 * </ul>
 *
 * <p>Additive: nothing in the production {@link Optimizer} calls it yet (see
 * {@link PassPipeline#standard()}); emitted code is unchanged until a caller opts
 * in.
 */
public final class AlgebraicSimplification implements IrPass {

    @Override
    public String name() {
        return "algebraic-simplification";
    }

    @Override
    public List<KofOperation> run(List<KofOperation> ops,
                                  Map<KofOperation, SourcePosition> positions) {
        List<KofOperation> out = new ArrayList<>(ops.size());
        KofOperation prev = null;
        for (KofOperation op : ops) {
            if (op instanceof KofBinary b && absorbs(b) && prev instanceof KofLoadLiteral lit
                    && isAbsorbingValue(b, lit) && out.size() >= 2) {
                KofOperation producer = out.get(out.size() - 2);
                if (isPureProducer(producer)) {
                    // producer; lit; OP  ->  lit(0). Drop the producer and the
                    // binary, keep a literal zero (stack depth unchanged).
                    KofLoadLiteral zero = b.operandType().equals(Type.PrimitiveType.LONG)
                            ? KofLoadLiteral.ofLong(0L)
                            : KofLoadLiteral.ofInt(0);
                    SourcePosition p = positions.remove(lit);
                    positions.remove(producer);
                    out.remove(out.size() - 1);   // the literal
                    out.remove(out.size() - 1);   // the producer
                    if (p != null) positions.put(zero, p);
                    out.add(zero);
                    prev = zero;
                    continue;
                }
            }
            out.add(op);
            prev = op;
        }
        return out;
    }

    /** True when {@code x OP literal} collapses to 0 for the literal on the stack. */
    private static boolean absorbs(KofBinary b) {
        if (!isIntOrLong(b.operandType())) return false;
        return switch (b.op()) {
            case MUL, AND -> true;        // x * 0, x & 0  (literal must be 0)
            case MOD -> true;             // x % 1, x % -1 (literal must be 1 or -1)
            default -> false;
        };
    }

    /** Whether the preceding literal is the absorbing value for the op. */
    private static boolean isAbsorbingValue(KofBinary b, KofLoadLiteral lit) {
        if (!(lit.value() instanceof Number n)) return false;
        long v = n.longValue();
        return switch (b.op()) {
            case MUL, AND -> v == 0;
            case MOD -> v == 1 || v == -1;
            default -> false;
        };
    }

    private static boolean isIntOrLong(Type t) {
        return t.equals(Type.PrimitiveType.INT) || t.equals(Type.PrimitiveType.LONG);
    }

    private static boolean isPureProducer(KofOperation op) {
        return op instanceof KofLoadLiteral || op instanceof KofLoadLocal
                || op instanceof KofFunctionAddress;
    }
}
