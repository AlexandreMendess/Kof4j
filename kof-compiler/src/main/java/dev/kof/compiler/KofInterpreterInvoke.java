package dev.kof.compiler;

import java.lang.reflect.Method;

/**
 * Satélite do REFACTOR-500 (check_500, KofInterpreter 623→<600): a invocação
 * de lambdas Kof/Java (a precedência invoke/run/call e o fallback reflexivo
 * viajam com o código).
 */
final class KofInterpreterInvoke {

    private KofInterpreterInvoke() {}

    static Object lambda(KofInterpreter self, Object lambda, Object[] args) throws Throwable {
        if (lambda instanceof KofInterpreter.KofObj ko) {
            IRClass c = self.members.classByInternal(ko.internalName());
            IRMethod m = self.findKofMethod(c, "invoke", args.length);
            if (m == null) m = self.findKofMethod(c, "run", args.length);
            if (m == null) m = self.findKofMethod(c, "call", args.length);
            if (m == null) throw new NoSuchMethodError(ko.internalName() + ".invoke/" + args.length);
            return self.evalKof(c, m, KofInterpreter.prepend(ko, args), true);
        }
        for (Method mm : lambda.getClass().getMethods()) {
            if (mm.getParameterCount() == args.length
                    && (mm.getName().equals("invoke") || mm.getName().equals("run"))) {
                return mm.invoke(lambda, args);
            }
        }
        throw new NoSuchMethodError("lambda invoke(" + args.length + " args)");
    }
}
