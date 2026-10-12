package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * entity-history fatia 2c-parte-2 (D-ENTITY-HISTORY): o lowering de
 * `Entity.history(db, id[, offset, limit])` — extraído do
 * ExpressionMethodCallLowerer (REFACTOR-500, §654/§653 precedente). JVM/
 * ANDROID reais; demais alvos ORM001 honesto (Fase 5 do rollout).
 */
final class ExpressionEntityHistoryLowerer {

    private ExpressionEntityHistoryLowerer() {}

    /**
 * entity-history fatia 2c-parte-2 (D-ENTITY-HISTORY): o lowering de
 * `Entity.history(db, id[, offset, limit])` — HIST002 para entity não-
 * audited (o lowerer tem o driver; a fase SEM reserva o TIPO), HIST005
 * para offset/limit negativos CONSTANTES; o runtime valida também em
 * tempo de execução (§9.3). JVM/ANDROID reais; demais alvos ORM001
 * honesto (Fase 5).
 */
static int lower(CompilerDriver driver, MethodCallExpr mc,
        List<KofOperation> ops, String owner, int localIdx,
        List<IRLocalVariable> locals, String entityName) {
    boolean audited = driver.auditedEntities.contains(entityName);
    if (!audited && driver.currentDiagnostics != null) {
        var pos = mc.position();
        driver.currentDiagnostics.error(pos != null ? pos.file() : "",
                pos != null ? pos.line() : 0, pos != null ? pos.column() : 0, 0,
                entityName + ".history: entity '" + entityName
                        + "' is not `audited` — mark it `audited entity` first (HIST002)",
                "HIST002");
        return localIdx;
    }
    if (!KofOrm.fnSupportedOn(driver.target, "kof_orm_history")) {
        if (driver.currentDiagnostics != null) {
            var pos = mc.position();
            driver.currentDiagnostics.error(pos != null ? pos.file() : "",
                    pos != null ? pos.line() : 0, pos != null ? pos.column() : 0, 0,
                    entityName + ".history: not available on the " + driver.target
                            + " target yet (" + KofOrm.gapCode() + ")",
                    KofOrm.gapCode());
        }
        return localIdx;
    }
    List<EntityFieldNode> fields = driver.entitySchemas.get(entityName);
    String table = KofOrm.tableName(entityName);
    String schema = fields == null ? "" : KofOrm.schemaString(fields);
    boolean paginated = mc.arguments().size() == 4;
    // HIST005 em compile-time: offset/limit CONSTANTES negativos
    if (paginated) {
        for (int ai = 2; ai < 4; ai++) {
            boolean negativeConst = (mc.arguments().get(ai) instanceof LiteralExpr lit
                    && lit.value() != null && lit.value().matches("-\\d+"))
                    || (mc.arguments().get(ai) instanceof UnaryExpr ue
                    && "-".equals(ue.operator())
                    && ue.operand() instanceof LiteralExpr lit2
                    && lit2.value() != null && lit2.value().matches("\\d+"));
            if (negativeConst) {
                if (driver.currentDiagnostics != null) {
                    var pos = mc.position();
                    driver.currentDiagnostics.error(pos != null ? pos.file() : "",
                            pos != null ? pos.line() : 0, pos != null ? pos.column() : 0, 0,
                            entityName + ".history: offset/limit must be >= 0 (HIST005)",
                            "HIST005");
                }
                return localIdx;
            }
        }
    }
    // args: (db, key[, offset, limit]) — primitivos boxeados (o runtime
    // espera Object para o key)
    // (id, key[, offset, limit], table, schema, className) — o key é
    // OBJ (boxeado) como em orm.save/orm.delete; offset/limit são INT.
    List<Type> params = paginated
            ? new ArrayList<>(List.of(BuiltinTypes.STRING, Type.UnknownType.UNKNOWN,
                    Type.PrimitiveType.INT, Type.PrimitiveType.INT,
                    BuiltinTypes.STRING, BuiltinTypes.STRING, BuiltinTypes.STRING))
            : new ArrayList<>(List.of(BuiltinTypes.STRING, Type.UnknownType.UNKNOWN,
                    BuiltinTypes.STRING, BuiltinTypes.STRING, BuiltinTypes.STRING));
    int emitted = 0;
    for (ExpressionNode arg : mc.arguments()) {
        localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
        Type at = ExpressionTyper.inferExprType(driver, arg, locals);
        // entity-history: SÓ o key (posição 1) boxeia — o offset/limit
        // paginado alimenta params INT crus (a descompensação cravava o
        // VerifyError "Integer not assignable to integer").
        if (emitted == 1 && TypeMetrics.isPrimitiveType(at)) {
            TypeEmitter.boxPrimitive(ops, at);
        }
        emitted++;
        if (paginated && emitted == 4) {
            // após os 4 args do usuário: a ordem do runtime é
            // (id, key, offset, limit, table, schema, className) — os
            // literais table/schema/className vêm DEPOIS do limit.
            ops.add(new KofLoadLiteral(BuiltinTypes.STRING, table));
            ops.add(new KofLoadLiteral(BuiltinTypes.STRING, schema));
            ops.add(new KofLoadLiteral(BuiltinTypes.STRING,
                    CompilerOrmSupport.classNameFor(driver, "Revision")));
            ops.add(new KofCall(new Type.ClassType("kof.orm", "Orm", List.of()),
                    "kof_orm_history_page", params,
                    new Type.ClassType("kof", "List",
                            List.of(CompilerTypes.toType("Revision", driver.currentUnit,
                                    driver.semanticAnalyzer))),
                    KofCallKind.FUNCTION));
            return localIdx;
        }
    }
    ops.add(new KofLoadLiteral(BuiltinTypes.STRING, table));
    ops.add(new KofLoadLiteral(BuiltinTypes.STRING, schema));
    ops.add(new KofLoadLiteral(BuiltinTypes.STRING,
            CompilerOrmSupport.classNameFor(driver, "Revision")));
    ops.add(new KofCall(new Type.ClassType("kof.orm", "Orm", List.of()),
            "kof_orm_history", params, Type.UnknownType.UNKNOWN,
            KofCallKind.FUNCTION));
    return localIdx;
}
}
