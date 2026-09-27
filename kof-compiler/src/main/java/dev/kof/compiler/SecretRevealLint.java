package dev.kof.compiler;

import java.util.List;

/**
 * D-SECRETS P2 (camada compile-time): avisa quando um {@code reveal()}
 * alimenta um caminho que LOG ou SERIALIZA — o único ato que expõe o valor cru
 * de um {@code Secret}.
 *
 * <p>É um WARNING (não bloqueia o build), no estilo honesto de {@code SEM099}:
 * o programador pode ter um motivo, mas a auditoria enxerga o ponto. O alcance
 * é sintático direto — o argumento é (ou contém, via {@code +}) um
 * {@code reveal()}. Atribuir o resultado a uma variável e logar depois é uma
 * face de lint com fluxo de dados, declarada como limitação (não inventada).</p>
 */
final class SecretRevealLint {

    static final String CODE = "SECN009";

    private SecretRevealLint() {}

    /** Emite no máximo um warning por chamada. {@code path} nomeia o sumidouro
     *  (ex.: {@code "json.encode"}, {@code "log.info"}). */
    static void warnIfRevealed(CompilerDriver driver, List<ExpressionNode> args, String path) {
        if (driver == null || driver.currentDiagnostics == null || args == null) return;
        for (ExpressionNode arg : args) {
            if (!containsReveal(arg)) continue;
            SourcePosition p = arg.position();
            driver.currentDiagnostics.warning(
                    p != null ? p.file() : "",
                    p != null ? p.line() : 0,
                    p != null ? p.column() : 0,
                    0,
                    "reveal() expõe o valor cru de um Secret para " + path
                            + "; passe o Secret (impresso redigido) ou use redact() no resultado ("
                            + CODE + ")",
                    CODE);
            return;
        }
    }

    private static boolean containsReveal(ExpressionNode e) {
        if (e == null) return false;
        if (e instanceof MethodCallExpr mc) {
            if ("reveal".equals(mc.methodName()) && mc.arguments().isEmpty()) return true;
            if (containsReveal(mc.receiver())) return true;
            for (ExpressionNode a : mc.arguments()) {
                if (containsReveal(a)) return true;
            }
            return false;
        }
        if (e instanceof BinaryExpr b) return containsReveal(b.left()) || containsReveal(b.right());
        if (e instanceof UnaryExpr u) return containsReveal(u.operand());
        return false;
    }
}
