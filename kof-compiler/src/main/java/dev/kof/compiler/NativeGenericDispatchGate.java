package dev.kof.compiler;

/**
 * §358 (21/09) — chamada de método de instância num receiver que é
 * type-parameter SEM bound (apaga para {@code Object}) nos alvos NATIVOS: o
 * runtime cross não tem dispatch genérico (a vtable é por classe, sem um
 * {@code Object} comum; os primitivos são crus). Antes o emit saía com
 * {@code call <método>} nu → {@code ld: undefined reference} (repro #368).
 * A recusa honesta em compile-time (NAT004) substitui o link-fail críptico,
 * no padrão NAT001/NAT002/NAT003 (§352/R6). Extraído do
 * {@link ExpressionInstanceCallLowerer} pelo gate ≤500.
 */
final class NativeGenericDispatchGate {

    private NativeGenericDispatchGate() {}

    /**
     * Emite o diagnóstico {@code NAT004} e devolve {@code true} quando a
     * chamada deve ser recusada (receiver {@code TypeVariable} sem bound em
     * alvo nativo); {@code false} em qualquer outro caso (JVM/JS/Script e
     * receivers com bound seguem intocados).
     */
    static boolean refuse(CompilerDriver driver, MethodCallExpr mc, Type recvType) {
        if (driver.target == null || !driver.target.isNative()
                || !(recvType instanceof Type.TypeVariable tv) || tv.bound() != null) {
            return false;
        }
        if (driver.currentDiagnostics != null) {
            var pos = mc.position();
            driver.currentDiagnostics.error(pos != null ? pos.file() : "",
                    pos != null ? pos.line() : 0, pos != null ? pos.column() : 0, 0,
                    "'" + mc.methodName() + "' on an unbounded type parameter is not supported"
                    + " on the native target yet (NAT004) — give the type parameter an upper"
                    + " bound or use JVM/JS/Script",
                    "NAT004");
        }
        return true;
    }
}
