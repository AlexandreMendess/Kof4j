package dev.kof.compiler;

import java.util.List;

/**
 * Fallback de lowering de {@code BinaryExpr}: operador aplicado sobre o tipo de
 * operando inferido (Unknown/primitivo boxado refletido em {@code operandType}).
 * Extraido de {@link ExpressionBinaryLowerer} (regra &lt;=500/600) — a
 * responsabilidade "escolher a operacao de fallback e o tipo de resultado" e
 * coesa e nao conhece o resto do fluxo de cast/igualdade.
 */
final class ExpressionBinaryFallbackOps {

    private ExpressionBinaryFallbackOps() {}

    /**
     * Emite a operacao binaria de fallback e devolve o novo {@code accType}
     * (relacionais e igualdade viram {@code Bool}).
     */
    static Type emit(List<KofOperation> ops, String operator, Type operandType, Type accType) {
        switch (operator) {
            case "+" -> ops.add(new KofBinary(KofBinaryOp.ADD, operandType));
            case "-" -> ops.add(new KofBinary(KofBinaryOp.SUB, operandType));
            case "*" -> ops.add(new KofBinary(KofBinaryOp.MUL, operandType));
            case "/" -> ops.add(new KofBinary(KofBinaryOp.DIV, operandType));
            case "%" -> ops.add(new KofBinary(KofBinaryOp.MOD, operandType));
            case "==" -> ops.add(new KofBinary(KofBinaryOp.EQ, operandType));
            case "!=" -> ops.add(new KofBinary(KofBinaryOp.NE, operandType));
            case "<" -> ops.add(new KofBinary(KofBinaryOp.LT, operandType));
            case "<=" -> ops.add(new KofBinary(KofBinaryOp.LE, operandType));
            case ">" -> ops.add(new KofBinary(KofBinaryOp.GT, operandType));
            case ">=" -> ops.add(new KofBinary(KofBinaryOp.GE, operandType));
            case "&&" -> ops.add(new KofBinary(KofBinaryOp.AND, operandType));
            case "||" -> ops.add(new KofBinary(KofBinaryOp.OR, operandType));
            case "&" -> ops.add(new KofBinary(KofBinaryOp.AND, operandType));
            case "|" -> ops.add(new KofBinary(KofBinaryOp.OR, operandType));
            case "^" -> ops.add(new KofBinary(KofBinaryOp.XOR, operandType));
            case "<<" -> ops.add(new KofBinary(KofBinaryOp.SHL, operandType));
            case ">>" -> ops.add(new KofBinary(KofBinaryOp.SHR, operandType));
            case ">>>" -> ops.add(new KofBinary(KofBinaryOp.USHR, operandType));
            default -> ops.add(new KofBinary(KofBinaryOp.ADD, operandType));
        }
        return switch (operator) {
            case "==", "!=", "<", "<=", ">", ">=" -> Type.PrimitiveType.BOOL;
            default -> accType;
        };
    }
}
