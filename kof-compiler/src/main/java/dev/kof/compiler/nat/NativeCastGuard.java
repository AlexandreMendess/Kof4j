package dev.kof.compiler.nat;

import dev.kof.compiler.BuiltinTypes;
import dev.kof.compiler.IRClass;
import dev.kof.compiler.NativeRuntime;
import dev.kof.compiler.Type;

/**
 * §678 face B (D-SEM-REJECTS-1210): o cast de referência SIMPLES (`x as T`) é
 * ESTRITO nos alvos nativos. Até aqui `KofCheckCast` era NO-OP nos DOIS
 * emissores nativos (x86 e cross), então um cast que falha devolvia o objeto do
 * tipo errado e o programa seguia — enquanto o JVM sempre derrubou (CHECKCAST,
 * o oráculo) e o Script/JS fecharam a face em 12-13/10.
 *
 * <p>O escopo espelha a face do interpretador (`KofInterpreter`, o MESMO
 * lowering que o Script usa), para os dois concordarem por construção: alvo de
 * CLASSE de usuário ou `String` — os únicos com type id em runtime; `null`
 * PASSA (a semântica CHECKCAST do JVM); genéricos/erasure e os contêineres do
 * pacote `kof` passam (o checador de compilação governa). As caixas
 * `java.lang` (Integer/Long/Double/…) NÃO têm type id no runtime nativo, então
 * ficam pass-through — medido e registrado como fronteira no ledger, nunca um
 * fallback silencioso.
 */
final class NativeCastGuard {

    private NativeCastGuard() {}

    /** O alvo exige checagem em runtime? (o `builtins.instanceOf` do Script). */
    static boolean isStrict(Type t) {
        if (BuiltinTypes.isString(t)) return true;
        if (!(t instanceof Type.ClassType ct)) return false;
        if (!ct.typeArguments().isEmpty()) return false;
        if ("kof".equals(ct.packageName())) return false;
        if ("java.lang".equals(ct.packageName())) return false;
        return true;
    }

    /**
     * Type id do alvo — a resolução que os dois emissores tinham INLINE no
     * `KofInstanceOf` (agora uma cópia só). 0 = sem id (não checável em
     * runtime; as caixas já saíram em {@link #isStrict}).
     */
    static int typeId(NativeBackend nb, Type t) {
        if (BuiltinTypes.isString(t)) return NativeRuntime.KOF_STRING_TYPE_ID;
        if (!(t instanceof Type.ClassType ct)) return 0;
        for (IRClass clazz : nb.allClassesMap.values()) {
            if (clazz.name().equals(ct.name()) || clazz.name().endsWith("/" + ct.name())
                    || ct.name().endsWith("/" + clazz.name())
                    || ct.name().equals(nb.sanitizeName(clazz.name()))) {
                return clazz.typeId();
            }
        }
        return 0;
    }

    /**
     * x86-64: o valor (erased) sai da pilha de operandos e volta — o helper
     * `kof_cast_check` devolve o MESMO objeto quando passa e derruba
     * (`kof_panic`) quando falha, então o cast não deixa valor no caminho do
     * erro.
     */
    static void emitX86(StringBuilder sb, NativeBackend nb, Type target) {
        int id = typeId(nb, target);
        if (!isStrict(target) || id == 0) return;
        sb.append("    popq %rdi\n");
        sb.append("    movl $").append(id).append(", %esi\n");
        sb.append("    call kof_cast_check\n");
        sb.append("    pushq %rax\n");
    }

    /**
     * riscv64 (e aarch64, que sai da tradução deste mesmo texto): o push é
     * explícito (`addi` + `sd`) porque este helper é estático — o par é
     * exatamente o que `pushRiscv` emite.
     */
    static void emitRiscv(StringBuilder sb, NativeBackend nb, Type target) {
        int id = typeId(nb, target);
        if (!isStrict(target) || id == 0) return;
        sb.append("    pop a0\n");
        sb.append("    li a1, ").append(id).append("\n");
        sb.append("    call kof_cast_check\n");
        sb.append("    addi sp, sp, -8\n");
        sb.append("    sd a0, 0(sp)\n");
    }
}
