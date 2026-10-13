package dev.kof.compiler;

import java.util.List;

/**
 * Declaração de variável. {@code type} carrega a anotação de tipo quando há
 * (`Int`), ou a palavra de mutabilidade quando não há (`"var"`/`"val"`).
 *
 * <p>#816: a MUTABILIDADE vive no campo próprio {@code isVal} — antes ela era
 * derivada de {@code "val".equals(type)}, e a anotação sobrescrevia o `"val"`
 * em {@code parseVarDecl}, de modo que `val x: Int = 1` perdia a imutabilidade
 * (SEM037 só disparava na forma inferida). O construtor de 4 argumentos
 * preserva o comportamento legado para as declarações sintéticas/encaminhadas.
 */
public record VarDeclStmt(SourcePosition position, String type, String name,
                   ExpressionNode initializer, boolean isVal) implements StatementNode {

    /** Compatibilidade: mutabilidade derivada da palavra (declarações sintéticas). */
    public VarDeclStmt(SourcePosition position, String type, String name,
                       ExpressionNode initializer) {
        this(position, type, name, initializer, "val".equals(type));
    }
}
