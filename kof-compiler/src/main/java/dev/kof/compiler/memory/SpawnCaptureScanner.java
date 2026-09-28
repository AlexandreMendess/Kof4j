package dev.kof.compiler.memory;

import dev.kof.compiler.ArrayAccessExpr;
import dev.kof.compiler.AssignmentExpr;
import dev.kof.compiler.BinaryExpr;
import dev.kof.compiler.BlockStmt;
import dev.kof.compiler.ExpressionNode;
import dev.kof.compiler.ExpressionStmt;
import dev.kof.compiler.FieldAccessExpr;
import dev.kof.compiler.IdentifierExpr;
import dev.kof.compiler.IfStmt;
import dev.kof.compiler.LambdaExpr;
import dev.kof.compiler.MethodCallExpr;
import dev.kof.compiler.ReturnStmt;
import dev.kof.compiler.SpawnStmt;
import dev.kof.compiler.StatementNode;
import dev.kof.compiler.UnaryExpr;
import dev.kof.compiler.VarDeclStmt;
import dev.kof.compiler.WhileStmt;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * D-MEMORY-SAFETY Fase 3 (fatia 3.2) — responsabilidade separada do
 * {@link OwnershipPass}: coletar, da sub-arvore do corpo de um {@code spawn},
 * os NOMES dos bindings cujo MUTADOR mudador-de-tamanho/indice
 * ({@link #MUTATORS}) e chamado diretamente. O {@code OwnershipPass} cruza
 * esses nomes com as mutacoes do corpo-mae (e entre spawns) para decidir a
 * corrida clara B-04/C-03/{@code MEM021}.
 *
 * <p>Caminhada puramente estrutural e CONSERVADORA: um formato de AST que este
 * scanner nao conhece e ignorado (nunca heuristica silenciosa que invente uma
 * corrida). {@code MUTATORS} e a MESMA fonte usada pela B-05/{@code MEM022}
 * para que as duas faces jamais divirjam sobre o que conta como mutacao.
 */
final class SpawnCaptureScanner {

    /** Mutadores de tamanho/indice da List compartilhados com a B-05. */
    static final Set<String> MUTATORS = Set.of("add", "remove", "clear", "addAll");

    private SpawnCaptureScanner() {
    }

    /** Binding (nome cru) mutado diretamente na sub-arvore do spawn. */
    static Set<String> captured(ExpressionNode spawnBody) {
        Set<String> out = new LinkedHashSet<>();
        expr(spawnBody, out);
        return out;
    }

    private static void expr(ExpressionNode e, Set<String> out) {
        if (e == null) {
            return;
        }
        switch (e) {
            case MethodCallExpr mc -> {
                if (MUTATORS.contains(mc.methodName())
                        && mc.receiver() instanceof IdentifierExpr recv) {
                    out.add(recv.name());
                }
                expr(mc.receiver(), out);
                for (ExpressionNode a : mc.arguments()) {
                    expr(a, out);
                }
            }
            case LambdaExpr lam -> stmts(lam.body(), out);
            case BinaryExpr be -> {
                expr(be.left(), out);
                expr(be.right(), out);
            }
            case UnaryExpr ue -> expr(ue.operand(), out);
            case FieldAccessExpr fa -> expr(fa.receiver(), out);
            case ArrayAccessExpr aa -> {
                expr(aa.receiver(), out);
                expr(aa.index(), out);
            }
            case AssignmentExpr ae -> expr(ae.value(), out);
            default -> {
            }
        }
    }

    private static void stmts(List<StatementNode> body, Set<String> out) {
        if (body == null) {
            return;
        }
        for (StatementNode s : body) {
            stmt(s, out);
        }
    }

    private static void stmt(StatementNode s, Set<String> out) {
        if (s == null) {
            return;
        }
        switch (s) {
            case ExpressionStmt es -> expr(es.expression(), out);
            case VarDeclStmt vds -> expr(vds.initializer(), out);
            case ReturnStmt r -> expr(r.value(), out);
            case BlockStmt b -> stmts(b.statements(), out);
            case IfStmt i -> {
                expr(i.condition(), out);
                stmt(i.thenBranch(), out);
                stmt(i.elseBranch(), out);
            }
            case WhileStmt w -> {
                expr(w.condition(), out);
                stmt(w.body(), out);
            }
            case SpawnStmt ss -> expr(ss.expression(), out);
            default -> {
            }
        }
    }
}
