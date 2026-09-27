package dev.kof.compiler.nat;

import dev.kof.compiler.IRClass;
import dev.kof.compiler.IRMethod;
import dev.kof.compiler.TopLevelOverload;
import dev.kof.compiler.Type;

import java.util.List;
import java.util.Map;

/**
 * Mangling de símbolos do backend Native (SG-011B + §131).
 *
 * <p>Extraído de {@code NativeBackend} (split ≤500, 13/09): a responsabilidade
 * é só NOMEAR símbolos — funções top-level sobrecarregáveis por assinatura
 * (SG-011B) e métodos de classe sobrecarregados (§131, decisão 10a). Não
 * carrega estado do backend: recebe o mapa de classes quando precisa saber se
 * um nome está sobrecarregado.
 *
 * <p>Regra de ouro preservada: classes/métodos SEM overload mantêm o símbolo
 * cru (vtables byte-idênticas, zero churn); só o sobrecarregado ganha sufixo
 * de assinatura — e sempre nos DOIS lados (registro e call site).
 */
final class NativeSymbolMangling {

    private NativeSymbolMangling() {}

    /** true quando o dono é o recipiente sintético de funções top-level. */
    static boolean isTopLevelOwner(String className) {
        return "Main".equals(className) || className.endsWith("/Main");
    }

    /** true quando (clazz,name) é uma função top-level sobrecarregável. */
    static boolean sigMangles(String className, String name) {
        return sigMangles(className, name, Map.of());
    }

    /** true quando (clazz,name) leva sufixo de assinatura: função top-level
     *  OU método de classe SOBRECARREGADO (§131, 10a). */
    static boolean sigMangles(String className, String name, Map<String, IRClass> classes) {
        if ("<init>".equals(name) || "main".equals(name)) return false;
        if (isTopLevelOwner(className)) return true;
        return classHasOverload(className, name, classes);
    }

    /** §131: (clazz,name) tem 2+ métodos com o mesmo nome? */
    static boolean classHasOverload(String className, String name, Map<String, IRClass> classes) {
        String simple = className.substring(className.lastIndexOf('/') + 1);
        for (IRClass clazz : classes.values()) {
            if (clazz.name().equals(simple) || clazz.name().endsWith("/" + simple)) {
                int count = 0;
                for (IRMethod m : clazz.methods()) {
                    if (m.name().equals(name)) count++;
                }
                return count > 1;
            }
        }
        return false;
    }

    static String sigTag(List<Type> ps) {
        return TopLevelOverload.sigTag(ps);
    }

    /** internal name (pkg/Name) do dono de um KofCall, ou "" se não-Classe. */
    static String internalOwner(Type owner) {
        if (owner instanceof Type.ClassType ct) {
            return ct.packageName() != null && !ct.packageName().isEmpty()
                    ? ct.packageName().replace('.', '/') + "/" + ct.name() : ct.name();
        }
        return "";
    }

    /** Chave do functionMangleMap para (clazz,name,pts): com assinatura só p/
     *  funções top-level; caso contrário o nome cru (comportamento antigo). */
    static String fnKey(String className, String name, List<Type> pts, Map<String, IRClass> classes) {
        return sigMangles(className, name, classes) ? name + "#" + sigTag(pts) : name;
    }

    /** Símbolo assembly de (clazz,name,pts). */
    static String fnSymbol(String className, String name, List<Type> pts, Map<String, IRClass> classes) {
        String m = sanitizeNameStatic(className) + "_" + sanitizeNameStatic(name);
        if ("<init>".equals(name)) m += "_" + pts.size();
        else if (sigMangles(className, name, classes)) m += sigTag(pts);
        return m;
    }

    /** #613: forma COMPLETA (método do IR) — o bridge de erasure desempata do
     *  método concreto pelo descritor quando a tag de params coincide. O caso
     *  que expôs isso é param-less: `T get()` (bridge `Object get()`) e
     *  `Int get()` dão a MESMA tag (`sigTag([])` == "") e o Native gerava dois
     *  `.globl IntBox_get` (assembler: "symbol already defined") ou, sem bridge,
     *  o slot da vtable caía no concreto cru (int lido como Object → SIGSEGV).
     *  A identidade que faltava é a do JVM: o descritor inclui o RETORNO —
     *  o bridge (sintético, ACC_BRIDGE) ganha o sufixo do retorno apagado. */
    static String fnSymbol(String className, IRMethod m, Map<String, IRClass> classes) {
        String base = sanitizeNameStatic(className) + "_" + sanitizeNameStatic(m.name());
        if ("<init>".equals(m.name())) return base + "_" + m.parameterTypes().size();
        if (!sigMangles(className, m.name(), classes)) return base;
        return base + fnTag(m);
    }

    /** #613: chave do mapa de mangle na forma COMPLETA (o bridge não sobrescreve
     *  a entrada do concreto no functionMangleMap — as tags diferem). */
    static String fnKey(String className, IRMethod m, Map<String, IRClass> classes) {
        return sigMangles(className, m.name(), classes) ? m.name() + "#" + fnTag(m) : m.name();
    }

    private static String fnTag(IRMethod m) {
        String tag = sigTag(m.parameterTypes());
        if ((m.accessFlags() & dev.kof.compiler.AccessFlags.BRIDGE) != 0) {
            tag += "_B" + sigTag(List.of(m.returnType()));
        }
        return tag;
    }

    static String sanitizeNameStatic(String name) {
        return name.replace("/", "_").replace(".", "_").replace("-", "_")
                .replace("<", "").replace(">", "");
    }
}
