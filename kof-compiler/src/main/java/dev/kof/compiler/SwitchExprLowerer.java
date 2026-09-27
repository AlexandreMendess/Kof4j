package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Lowering de switch-expression (emitSwitchExpr/Chain/Binding).
 */
public final class SwitchExprLowerer {

    private SwitchExprLowerer() {}

    static int emitSwitchExpr(CompilerDriver driver, SwitchExpr se, List<KofOperation> ops, String owner,
                               int localIdx, List<IRLocalVariable> locals) {
        Type switchType = ExpressionTyper.inferExprType(driver, se.expression(), locals);
        // §601: a inferência do resultado corre ANTES do lowering — um primeiro
        // case `Lit(var v) -> v` infere Unknown (o id bound pelo pattern não
        // está em `locals` ainda) e o fallback sintético nasce BOXADO
        // (defaultValueOp(Unknown)) contra corpos int = merge int×ref =
        // VerifyError no load. Projeta os bindings dos patterns num
        // locals-descartável só para a inferência (os slots reais continuam
        // nascendo no emitPatternBinding, com os índices do emit).
        Type resultType = ExpressionTyper.inferExprType(driver, se,
                projectPatternBindings(driver, se.cases(), locals));
        // §149: switch exaustivo sobre enum NÃO tem default explícito; o fallback
        // sintético precisa ter o tipo do RESULTADO (corpo dos casos), não o tipo
        // do subject. Sem isso, `switch(c){case Color.Red -> 1 ...}` fazia o merge
        // int-vs-referência do enum → VerifyError (Integer vs int) no JVM.
        boolean differ = ExpressionTyper.branchTypesDiffer(ExpressionTyper.switchBranchTypes(
                driver, se.cases(), se.defaultValue(), resultType, locals));
        Type fallbackType = differ ? new Type.ClassType("java.lang", "Object", List.of()) : resultType;
        int switchTmp = localIdx++;
        localIdx = ExpressionLowerer.emitExpression(driver, se.expression(), ops, owner, localIdx, locals);
        ops.add(new KofStoreLocal(switchType, switchTmp));
        locals.add(new IRLocalVariable(switchTmp, "#switchExpr", switchType));
        return emitSwitchChain(driver, se.cases(), 0, se.defaultValue(), switchType, fallbackType, switchTmp,
                ops, owner, localIdx, locals);
    }

    static int emitSwitchChain(CompilerDriver driver, List<SwitchExprCase> cases, int i, ExpressionNode defaultValue,
                                Type switchType, Type fallbackType, int switchTmp, List<KofOperation> ops, String owner,
                                int localIdx, List<IRLocalVariable> locals) {
        if (i >= cases.size()) {
            if (defaultValue != null) {
                localIdx = ExpressionLowerer.emitExpression(driver, defaultValue, ops, owner, localIdx, locals);
                // #57/§70: corpos com tipos distintos → boxa ramo primitivo
                // in-branch (join só de referências); callers pulam pós-box.
                if (ExpressionTyper.branchTypesDiffer(ExpressionTyper.switchBranchTypes(
                        driver, cases, defaultValue, fallbackType, locals))) {
                    ExpressionTyper.boxPrimitiveBranch(driver, ops,
                            ExpressionTyper.inferExprType(driver, defaultValue, locals));
                }
                return localIdx;
            }
            ops.add(CompilerTypes.defaultValueOp(fallbackType));
            return localIdx;
        }
        SwitchExprCase sc = cases.get(i);
        LabelId bodyLabel = LabelId.create();
        LabelId elseLabel = LabelId.create();
        LabelId endLabel = LabelId.create();
        if (sc.value() instanceof PatternExpr pe) {
            Type patType = CompilerTypes.toType(pe.typeName(), driver.currentUnit);
            if (patType instanceof Type.UnknownType) patType = BuiltinTypes.STRING;
            if (TypeMetrics.isPrimitiveType(patType)) {
                // case de PRIMITIVO é ilegal no JVM (instanceof sobre int):
                // diagnóstico em compile-time, nunca VerifyError (bug 37).
                if (driver.currentDiagnostics != null) {
                    SourcePosition pp = pe.position();
                    driver.currentDiagnostics.error(pp != null ? pp.file() : "",
                            pp != null ? pp.line() : 0, pp != null ? pp.column() : 0, 0,
                            "case of primitive type is not supported in pattern matching "
                                    + "(use a reference type or the value directly)",
                            "SEM035");
                }
                return localIdx;
            }
            ops.add(new KofLoadLocal(switchType, switchTmp));
            ops.add(new KofInstanceOf(patType));
            ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
            // #199: com GUARDA, o alvo do instanceof-true é o PROLOGO do
            // binding (bindingLabel), NÃO o corpo — senão o mesmo Label é
            // visitado antes (instanceof) e depois (guarda true) e todas as
            // resoluções de label caem na ULTIMA visita: o instanceof-true
            // pulava o `astore` da var bound → VerifyError "Bad local
            // variable type" no corpo (slot nunca escrito). Sem guarda,
            // bindingLabel == bodyLabel (queda direta, uma visita só — como
            // antes). Um ajuste na IR compartilhada cura os 4 backends
            // (regra 5: a raiz é do lowerer, não do backend JVM).
            boolean guarded = pe.guard() != null;
            LabelId bindingLabel = guarded ? LabelId.create() : bodyLabel;
            ops.add(new KofConditionalJump(KofComparison.NE, bindingLabel, elseLabel));
            ops.add(new KofLabel(bindingLabel));
            localIdx = emitPatternBinding(driver, pe, patType, switchType, switchTmp, ops, localIdx, locals);
            // SG-014: guarda — avaliada com a var JÁ bound; false → próximo braço
            if (guarded) {
                localIdx = ExpressionLowerer.emitExpression(driver, pe.guard(), ops, owner, localIdx, locals);
                ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
                ops.add(new KofConditionalJump(KofComparison.EQ, elseLabel, bodyLabel));
                ops.add(new KofLabel(bodyLabel));
            }
        } else {
            ops.add(new KofLoadLocal(switchType, switchTmp));
            localIdx = ExpressionLowerer.emitExpression(driver, sc.value(), ops, owner, localIdx, locals);
            // #473: promove o literal do case ao tipo do subject (Int → Long/
            // Double/Float) antes do EQ largo — sem isto o LCMP/DCMP operava
            // sobre largura errada e o frame do ASM estourava.
            Type caseValType = ExpressionTyper.inferExprType(driver, sc.value(), locals);
            driver.emitWideningIfNeeded(ops, caseValType, switchType);
            if (Type.isString(switchType)) {
                // igualdade de String é por conteúdo (bug 4 do statement)
                ops.add(new KofCall(BuiltinTypes.STRING, "kof_string_equals",
                        List.of(BuiltinTypes.STRING, BuiltinTypes.STRING),
                        Type.PrimitiveType.BOOL, KofCallKind.FUNCTION));
            } else {
                // D-ENUM207: enum = instâncias (singletons) → identidade (if_acmp);
                // primitivo/outros → EQ. kof_string_equals sobre Dir seria CCE/SIGSEGV.
                ops.add(new KofBinary(KofBinaryOp.EQ, switchType));
            }
            ops.add(new KofLoadLiteral(Type.PrimitiveType.INT, 0));
            ops.add(new KofConditionalJump(KofComparison.NE, bodyLabel, elseLabel));
            ops.add(new KofLabel(bodyLabel));
        }
        localIdx = ExpressionLowerer.emitExpression(driver, sc.body(), ops, owner, localIdx, locals);
        // #57/§70: corpos com tipos distintos → boxa corpo primitivo in-branch.
        if (ExpressionTyper.branchTypesDiffer(ExpressionTyper.switchBranchTypes(
                driver, cases, defaultValue, fallbackType, locals))) {
            ExpressionTyper.boxPrimitiveBranch(driver, ops,
                    ExpressionTyper.inferExprType(driver, sc.body(), locals));
        }
        ops.add(new KofJump(endLabel));
        ops.add(new KofLabel(elseLabel));
        localIdx = emitSwitchChain(driver, cases, i + 1, defaultValue, switchType, fallbackType, switchTmp,
                ops, owner, localIdx, locals);
        ops.add(new KofLabel(endLabel));
        return localIdx;
    }

    /**
     * Prologue de binding de um case pattern de switch-expressão:
     * {@code case T v ->} → {@code v = (T)#switchExpr};
     * {@code case T(var x, var y) ->} → cast p/ {@code #patCast} + um
     * {@code getfield} por componente. No JS os slots são pré-declarados no
     * topo da função, então o {@code store} vira atribuição na sequência do
     * braço (ver parseExpressionFragment).
     */
    static int emitPatternBinding(CompilerDriver driver, PatternExpr pe, Type patType, Type switchType, int switchTmp,
                                   List<KofOperation> ops, int localIdx, List<IRLocalVariable> locals) {
        if (pe.varName() != null) {
            ops.add(new KofLoadLocal(switchType, switchTmp));
            ops.add(new KofCheckCast(patType));
            int varIdx = localIdx++;
            locals.add(new IRLocalVariable(varIdx, pe.varName(), patType));
            ops.add(new KofStoreLocal(patType, varIdx));
            return localIdx;
        }
        int castTmp = localIdx++;
        locals.add(new IRLocalVariable(castTmp, "#patCast", patType));
        ops.add(new KofLoadLocal(switchType, switchTmp));
        ops.add(new KofCheckCast(patType));
        ops.add(new KofStoreLocal(patType, castTmp));
        String simple = patType instanceof Type.ClassType ct ? ct.name() : pe.typeName();
        for (int fi = 0; fi < pe.fieldVars().size(); fi++) {
            String fieldVar = pe.fieldVars().get(fi);
            Type fieldType = Type.UnknownType.UNKNOWN;
            String fieldName = fieldVar;
            if (driver.currentUnit != null) {
                for (AstNode d : driver.currentUnit.declarations()) {
                    if (d instanceof RecordDeclarationNode rec && rec.name().equals(simple)) {
                        if (fi < rec.components().size()) {
                            fieldType = CompilerTypes.toType(rec.components().get(fi).type(), driver.currentUnit);
                            fieldName = rec.components().get(fi).name();
                        }
                        break;
                    }
                }
            }
            if (fieldType instanceof Type.UnknownType) fieldType = BuiltinTypes.STRING;
            ops.add(new KofLoadLocal(patType, castTmp));
            ops.add(new KofLoadField(patType, fieldName, fieldType));
            int varIdx = localIdx;
            localIdx += TypeMetrics.isDoubleWidth(fieldType) ? 2 : 1;
            locals.add(new IRLocalVariable(varIdx, fieldVar, fieldType));
            ops.add(new KofStoreLocal(fieldType, varIdx));
        }
        return localIdx;
    }

    /**
     * §601 — cópia DESCARTÁVEL de `locals` com os bindings de pattern dos
     * cases já projetados (nome+tipo, os mesmos que o {@link #emitPatternBinding}
     * criará), para a inferência do tipo do resultado da switch-expr correr
     * com os identificadores bound. Sem emit; os índices são fictícios — a
     * alocação real acontece no lowering, com a numeração do emit.
     */
    static List<IRLocalVariable> projectPatternBindings(CompilerDriver driver,
            List<SwitchExprCase> cases, List<IRLocalVariable> locals) {
        List<IRLocalVariable> proj = new ArrayList<>(locals);
        int ghost = -1;
        for (SwitchExprCase c : cases) {
            if (!(c.value() instanceof PatternExpr pe)) continue;
            Type patType = CompilerTypes.toType(pe.typeName(), driver.currentUnit);
            if (patType instanceof Type.UnknownType) patType = BuiltinTypes.STRING;
            if (TypeMetrics.isPrimitiveType(patType)) continue;
            if (pe.varName() != null) {
                proj.add(new IRLocalVariable(ghost--, pe.varName(), patType));
                continue;
            }
            proj.add(new IRLocalVariable(ghost--, "#patCast", patType));
            String simple = patType instanceof Type.ClassType ct ? ct.name() : pe.typeName();
            for (int fi = 0; fi < pe.fieldVars().size(); fi++) {
                Type fieldType = Type.UnknownType.UNKNOWN;
                if (driver.currentUnit != null) {
                    for (AstNode d : driver.currentUnit.declarations()) {
                        if (d instanceof RecordDeclarationNode rec && rec.name().equals(simple)) {
                            if (fi < rec.components().size()) {
                                fieldType = CompilerTypes.toType(rec.components().get(fi).type(), driver.currentUnit);
                            }
                            break;
                        }
                    }
                }
                if (fieldType instanceof Type.UnknownType) fieldType = BuiltinTypes.STRING;
                proj.add(new IRLocalVariable(ghost--, pe.fieldVars().get(fi), fieldType));
            }
        }
        return proj;
    }
}
