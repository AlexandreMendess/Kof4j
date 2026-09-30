package dev.kof.compiler;

import java.util.List;

/**
 * Lowering do método {@code List.zip} (D-MULTIPARADIGMA-PHASE1A slice 1i,
 * split do gate 500 — o bloco cruzou a linha vermelha de 600 em
 * {@link CollectionCallLowerer}).
 *
 * <p>Library-first: reescreve {@code xs.zip(ys)} para o helper Kof injetado
 * {@code zipPairs(xs, ys)} (record {@code Pair<A,B>} + função de
 * {@code dev/kof/pairs.kf} via {@link CompilerPairs}) e reemite pelo pipeline
 * normal de chamada — sem runtime por backend, sem ABI novo, sem sintaxe
 * nova.</p>
 *
 * <p>O caller ({@link ExpressionInstanceCallLowerer}) já empilhou o receiver:
 * a pilha é {@code [recv]}. Emitimos só o argumento e guardamos os dois em
 * temps ({@code $kw*} do padrão P4) para avaliar exatamente uma vez. Emitir o
 * receiver de novo construía a lista em dobro e desbalanceava a pilha
 * (medido com javap 30/09: três listas num zip de duas).</p>
 */
final class CollectionZipLowerer {

    private CollectionZipLowerer() {}

    static int lower(CompilerDriver driver, Type recvType, MethodCallExpr mc, List<KofOperation> ops,
                     String owner, int localIdx, List<IRLocalVariable> locals) {
        var pos = mc.position();
        if (mc.arguments().size() != 1 && driver.currentDiagnostics != null) {
            driver.currentDiagnostics.error(pos != null ? pos.file() : "",
                    pos != null ? pos.line() : 0, pos != null ? pos.column() : 0, 0,
                    "List.zip takes exactly one List argument",
                    "SEM025");
            return localIdx;
        }
        Type argListType = mc.arguments().isEmpty() ? Type.UnknownType.UNKNOWN
                : ExpressionTyper.inferExprType(driver, mc.arguments().get(0), locals);
        if (!BuiltinTypes.isList(argListType)
                && !(argListType instanceof Type.UnknownType)
                && driver.currentDiagnostics != null) {
            driver.currentDiagnostics.error(pos != null ? pos.file() : "",
                    pos != null ? pos.line() : 0, pos != null ? pos.column() : 0, 0,
                    "List.zip takes a List argument; '"
                            + CollectionWrites.typeNameFor(argListType) + "' is not a List",
                    "SEM025");
            return localIdx;
        }
        localIdx = ExpressionLowerer.emitExpression(driver, mc.arguments().get(0), ops, owner, localIdx, locals);
        int argIdx = localIdx++;
        locals.add(new IRLocalVariable(argIdx, "$kwziparg" + argIdx, argListType));
        ops.add(new KofStoreLocal(argListType, argIdx));
        int recvIdx = localIdx++;
        locals.add(new IRLocalVariable(recvIdx, "$kwziprecv" + recvIdx, recvType));
        ops.add(new KofStoreLocal(recvType, recvIdx));
        // Type arguments ride empty (P4 precedent): the re-emitted call
        // infers A/B from the temp types through the normal pipeline.
        MethodCallExpr rebuilt = new MethodCallExpr(pos, null, CompilerPairs.FN,
                List.of(),
                List.of(new IdentifierExpr(pos, "$kwziprecv" + recvIdx),
                        new IdentifierExpr(pos, "$kwziparg" + argIdx)));
        return ExpressionLowerer.emitExpression(driver, rebuilt, ops, owner, localIdx, locals);
    }
}
