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
                entityName + "." + mc.methodName() + ": entity '" + entityName
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
    if (!"history".equals(mc.methodName())) {
        return lowerAtAtTimeChanges(driver, mc, ops, owner, localIdx, locals,
                entityName, table, schema);
    }
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

    /**
     * entity-history fatia 2d (D-ENTITY-HISTORY §9.2): `E.at(db, id, rev)`,
     * `E.atTime(db, id, iso)` e `E.changes(db, id[, from, to])` — HIST005
     * para revisão <= 0 / faixa inválida / ISO malformado CONSTANTES (o
     * runtime valida os não-constantes); a materialização usa o binding
     * JSON do §565; revisão DELETE materializa null.
     */
    private static int lowerAtAtTimeChanges(CompilerDriver driver, MethodCallExpr mc,
            List<KofOperation> ops, String owner, int localIdx,
            List<IRLocalVariable> locals, String entityName, String table, String schema) {
        String method = mc.methodName();
        int n = mc.arguments().size();
        // HIST005 em compile-time: constantes claramente inválidas
        for (int ai = 1; ai < n; ai++) {
            boolean negativeConst = (mc.arguments().get(ai) instanceof LiteralExpr lit
                    && lit.value() != null && lit.value().matches("-\\d+"))
                    || (mc.arguments().get(ai) instanceof UnaryExpr ue
                    && "-".equals(ue.operator())
                    && ue.operand() instanceof LiteralExpr lit2
                    && lit2.value() != null && lit2.value().matches("\\d+"));
            if (negativeConst && driver.currentDiagnostics != null) {
                var pos = mc.position();
                driver.currentDiagnostics.error(pos != null ? pos.file() : "",
                        pos != null ? pos.line() : 0, pos != null ? pos.column() : 0, 0,
                        entityName + "." + method
                                + ": revision/offset must be >= 0 (HIST005)",
                        "HIST005");
                return localIdx;
            }
        }
        boolean isChanges = "changes".equals(method);
        // args: (db, key[, third[, fourth]]) — o key boxeia; no changes o
        // from/to alimentam params INT crus (7 params; o descritor curto
        // cravava o NegativeArraySize no COMPUTE_FRAMES).
        List<Type> params = new ArrayList<>();
        params.add(BuiltinTypes.STRING); // db
        params.add(Type.UnknownType.UNKNOWN); // key
        if (isChanges) {
            params.add(Type.PrimitiveType.INT); // from
            params.add(Type.PrimitiveType.INT); // to
        }
        int emitted = 0;
        for (ExpressionNode arg : mc.arguments()) {
            localIdx = ExpressionLowerer.emitExpression(driver, arg, ops, owner, localIdx, locals);
            Type at = ExpressionTyper.inferExprType(driver, arg, locals);
            // entity-history: SÓ o key (posição 1) boxeia no changes (from/to
            // alimentam params INT crus — o boxeado cravava o NegativeArraySize
            // no COMPUTE_FRAMES); no at, key E revision são params Object.
            boolean boxed = emitted == 1; // só o key boxeia; revision/from/to são INT crus
            if (boxed && TypeMetrics.isPrimitiveType(at)) {
                TypeEmitter.boxPrimitive(ops, at);
            }
            emitted++;
        }
        String fn;
        Type retType;
        if ("at".equals(method)) {
            fn = "kof_orm_history_at";
            params.add(Type.PrimitiveType.INT); // revision
            params.add(BuiltinTypes.STRING); // table
            params.add(BuiltinTypes.STRING); // schema
            params.add(BuiltinTypes.STRING); // className
            // retType = o TIPO INTERNO (User), não Nullable(User): o slot
            // local mapeia para User (checkcast no retorno) e o null segue
            // fluindo por referência — o slot Object sem cast cravava o
            // VerifyError no acesso pós-narrowing (v.name).
            retType = CompilerTypes.toType(entityName, driver.currentUnit,
                    driver.semanticAnalyzer);
        } else if ("atTime".equals(method)) {
            fn = "kof_orm_history_at_time";
            params.add(BuiltinTypes.STRING); // table
            params.add(BuiltinTypes.STRING); // schema
            params.add(BuiltinTypes.STRING); // className
            retType = CompilerTypes.toType(entityName, driver.currentUnit,
                    driver.semanticAnalyzer);
        } else {
            fn = "kof_orm_history_changes";
            params.add(BuiltinTypes.STRING); // table
            params.add(BuiltinTypes.STRING); // schema
            params.add(BuiltinTypes.STRING); // FieldChange className
            retType = new Type.ClassType("kof", "List",
                    List.of(CompilerTypes.toType("FieldChange", driver.currentUnit,
                            driver.semanticAnalyzer)));
        }
        ops.add(new KofLoadLiteral(BuiltinTypes.STRING, table));
        ops.add(new KofLoadLiteral(BuiltinTypes.STRING, schema));
        ops.add(new KofLoadLiteral(BuiltinTypes.STRING,
                CompilerOrmSupport.classNameFor(driver,
                        isChanges ? "FieldChange" : entityName)));
        ops.add(new KofCall(new Type.ClassType("kof.orm", "Orm", List.of()),
                fn, params, retType, KofCallKind.FUNCTION));
        return localIdx;
    }
}
