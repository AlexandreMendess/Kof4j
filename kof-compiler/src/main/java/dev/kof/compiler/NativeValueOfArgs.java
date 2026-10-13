package dev.kof.compiler;

import java.util.List;

/**
 * §686: a assinatura JDK `String.valueOf(Object)` APAGA o tipo Kof do
 * argumento. No JVM isso é o contrato (o `valueOf(Object)` chama `toString`);
 * no NATIVO o emissor decide o formatador pelo tipo do parâmetro, então o valor
 * apagado caía no `kof_box_to_string`, que passa não-box CRU — o ponteiro do
 * contêiner saía impresso como lixo (`println(String.valueOf(listOf(1,2,3)))`
 * → bytes crus vs `[1, 2, 3]` no JVM/Script).
 *
 * <p>O runtime NÃO pode recuperar o tipo: a lista guarda elementos crus e sem
 * tag (`kof_list_new` é o mesmo do Set; o Map só tem o key tag em [40]) e um
 * `List<Int>` apagado é indistinguível de ponteiros. A recuperação é AQUI, do
 * tipo ESTÁTICO que o typer já conhece — o MESMO tratamento que o caminho de
 * `println` dá ao contêiner (`ExpressionPrintLowerer`, `nativeArg`).
 *
 * <p>Gate: só o alvo nativo e só quando a assinatura resolvida apagou para
 * `Object`. O tipo do argumento entra como está (o emissor cobre contêiner,
 * array, Buffer, classe de usuário por vtable e String por identidade); tipo
 * desconhecido devolve o formal original — nunca um palpite.
 */
final class NativeValueOfArgs {

    private NativeValueOfArgs() {}

    static List<Type> recover(CompilerDriver driver, MethodCallExpr mc, List<Type> formal,
                              List<IRLocalVariable> locals) {
        if (!driver.target.isNative() || !"valueOf".equals(mc.methodName()) || formal.size() != 1
                || mc.arguments().size() != 1) {
            return formal;
        }
        if (!(formal.get(0) instanceof Type.ClassType erasure) || !"Object".equals(erasure.name())) {
            return formal;
        }
        Type argT = ExpressionTyper.inferExprType(driver, mc.arguments().get(0), locals);
        Type inner = argT instanceof Type.NullableType nt ? nt.inner() : argT;
        if (inner instanceof Type.UnknownType || inner instanceof Type.TypeVariable
                || inner instanceof Type.NullableType) {
            return formal;
        }
        if (inner instanceof Type.PrimitiveType) {
            return formal; // primitivo já teria resolvido um overload próprio
        }
        return List.of(inner);
    }
}
