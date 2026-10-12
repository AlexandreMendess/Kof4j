package dev.kof.compiler.js;

import dev.kof.compiler.IRClass;
import dev.kof.compiler.IRField;
import dev.kof.compiler.IRMethod;
import dev.kof.compiler.TopLevelOverload;
import dev.kof.compiler.Type;

import java.util.ArrayList;
import java.util.List;

/**
 * REFACTOR-500: the record SYNTHETIC methods (toString/equals/hashCode/toJSON/
 * FFI fields+from) extracted from {@link JsClassEmitter} — the class emitter
 * crossed the 600-line red line when §659d added the Double/Float sentinel
 * helper. Pure JsIr builders; the only side effect is runtime registration
 * (equals/hashCode) through the passed context.
 */
final class JsRecordSynthetics {
    private JsRecordSynthetics() {
    }

    /**
     * Records get a toString() in JS to mirror the JVM backend's synthetic
     * record toString: "Name[f1=..., f2=...]".
     */
    static JsIr.JsFunction lowerRecordToString(IRClass clazz) {
        String simpleName = clazz.name().contains("/")
                ? clazz.name().substring(clazz.name().lastIndexOf('/') + 1) : clazz.name();
        List<JsIr.JsExpression> parts = new ArrayList<>();
        parts.add(new JsIr.JsString(simpleName + "["));
        for (int i = 0; i < clazz.fields().size(); i++) {
            if (i > 0) parts.add(new JsIr.JsString(", "));
            parts.add(new JsIr.JsString(clazz.fields().get(i).name() + "="));
            parts.add(new JsIr.JsMember(new JsIr.JsThis(),
                    "_" + JsTypeMapper.sanitizeName(clazz.fields().get(i).name())));
        }
        parts.add(new JsIr.JsString("]"));
        JsIr.JsExpression joined = parts.get(0);
        for (int i = 1; i < parts.size(); i++) {
            joined = new JsIr.JsBinary(joined, "+", parts.get(i));
        }
        return new JsIr.JsFunction("toString", List.of(),
                List.of(new JsIr.JsReturn(joined)), false, false, false);
    }

    /**
     * Records: igualdade de conteúdo no JS (bug 11) — compara todos os
     * componentes. O lowering de `==` em records despacha para `.equals()`
     * em todos os targets.
     */
    static JsIr.JsFunction lowerRecordEquals(IRClass clazz, JsLoweringContext ctx) {
        // Componente objeto (record/classe com equals) compara por conteúdo,
        // como o Objects.equals da JVM — `===` só serve para primitivos.
        ctx.registerRuntime("kofValEq");
        List<JsIr.JsExpression> conds = new ArrayList<>();
        for (IRField field : clazz.fields()) {
            String backing = "_" + JsTypeMapper.sanitizeName(field.name());
            conds.add(new JsIr.JsCall(
                    new JsIr.JsIdentifier("kofValEq"),
                    List.of(new JsIr.JsMember(new JsIr.JsThis(), backing),
                            new JsIr.JsMember(new JsIr.JsIdentifier("other"), backing))));
        }
        JsIr.JsExpression body = null;
        for (int i = conds.size() - 1; i >= 0; i--) {
            body = (body == null) ? conds.get(i)
                    : new JsIr.JsBinary(conds.get(i), "&&", body);
        }
        if (body == null) body = new JsIr.JsNumber("1");
        // Kof bool é int (0/1): o equals gerado devolve 1/0 para operações
        // subsequentes (ex.: `a != c` compara com 0) não quebrarem.
        JsIr.JsExpression kofBool = new JsIr.JsConditional(
                body, new JsIr.JsNumber("1"), new JsIr.JsNumber("0"));
        return new JsIr.JsFunction("equals", List.of("other"),
                List.of(new JsIr.JsReturn(kofBool)), false, false, false);
    }

    /**
     * Records: hashCode() no JS (bug 42) espelhando o JvmRecordEmitter
     * (result = 31*result + contrib, wrap int32 via | 0). O contrib usa
     * kofHashCode (runtime) que trata number/String/record — campos
     * numéricos dão o valor, refs delegam ao runtime.
     */
    static JsIr.JsFunction lowerRecordHashCode(IRClass clazz, JsLoweringContext ctx) {
        ctx.registerRuntime("kofHashCode");
        List<JsIr.JsStatement> body = new ArrayList<>();
        body.add(new JsIr.JsVarDecl("__h", new JsIr.JsNumber("1"), false));
        for (IRField field : clazz.fields()) {
            String backing = "_" + JsTypeMapper.sanitizeName(field.name());
            JsIr.JsExpression contrib = new JsIr.JsCall(
                    new JsIr.JsIdentifier("kofHashCode"),
                    List.of(new JsIr.JsMember(new JsIr.JsThis(), backing)));
            JsIr.JsExpression next = new JsIr.JsBinary(
                    new JsIr.JsBinary(
                            new JsIr.JsBinary(new JsIr.JsNumber("31"), "*",
                                    new JsIr.JsIdentifier("__h")),
                            "+", contrib),
                    "|", new JsIr.JsNumber("0"));
            body.add(new JsIr.JsAssign("__h", next));
        }
        body.add(new JsIr.JsReturn(new JsIr.JsIdentifier("__h")));
        return new JsIr.JsFunction("hashCode", List.of(), body, false, false, false);
    }

    /**
     * Records serialize as { "f1": ..., "f2": ... } to mirror the JVM backend's
     * reflection-based JSON encoding (JSON.stringify honors toJSON()).
     */
    static JsIr.JsFunction lowerRecordToJson(IRClass clazz) {
        List<JsIr.JsObjectEntry> entries = new ArrayList<>();
        for (IRField field : clazz.fields()) {
            JsIr.JsExpression value = new JsIr.JsMember(new JsIr.JsThis(),
                    "_" + JsTypeMapper.sanitizeName(field.name()));
            // §659d (face record): um campo Double/Float INTEIRO perde o .0 no
            // JSON (JS não distingue Int de Double) — o toJSON emite a sentinela
            // que o kofJsonEncode devolve COM o .0 (a mecânica §659b).
            if (isDoubleOrFloatField(field.type())) {
                value = new JsIr.JsCall(new JsIr.JsMember(new JsIr.JsThis(), "__kofDblTxt"),
                        List.of(value));
            }
            entries.add(new JsIr.JsObjectEntry(field.name(), value));
        }
        return new JsIr.JsFunction("toJSON", List.of(),
                List.of(new JsIr.JsReturn(new JsIr.JsObjectLiteral(entries))), false, false, false);
    }

    /** §659d: o record com campo Double/Float inteiro mantém o .0 no encode JSON. */
    private static boolean isDoubleOrFloatField(Type type) {
        if (type instanceof Type.NullableType nt) return isDoubleOrFloatField(nt.inner());
        return type instanceof Type.PrimitiveType pt
                && ("double".equals(Type.canonicalPrimitiveName(pt.name()))
                    || "float".equals(Type.canonicalPrimitiveName(pt.name())));
    }

    static boolean hasDoubleOrFloatField(IRClass clazz) {
        return clazz.fields().stream().anyMatch(f -> isDoubleOrFloatField(f.type()));
    }

    /** Só emitido em records COM campo Double/Float (§659d). */
    static JsIr.JsFunction lowerRecordDblTxt() {
        // function __kofDblTxt(x) { return (typeof x === 'number'
        //     && Number.isInteger(x)) ? 'KOFDBL_SENTINEL_' + x.toFixed(1) + '_END' : x; }
        JsIr.JsExpression x = new JsIr.JsIdentifier("x");
        JsIr.JsExpression isNum = new JsIr.JsBinary(
                new JsIr.JsUnary("typeof", x), "===", new JsIr.JsString("number"));
        JsIr.JsExpression isInt = new JsIr.JsCall(
                new JsIr.JsMember(new JsIr.JsIdentifier("Number"), "isInteger"), List.of(x));
        JsIr.JsExpression cond = new JsIr.JsBinary(isNum, "&&", isInt);
        JsIr.JsExpression toFixed = new JsIr.JsBinary(
                new JsIr.JsString("KOFDBL_SENTINEL_"), "+",
                new JsIr.JsCall(new JsIr.JsMember(x, "toFixed"), List.of(new JsIr.JsNumber("1"))));
        JsIr.JsExpression sentinel = new JsIr.JsBinary(toFixed, "+", new JsIr.JsString("_END"));
        return new JsIr.JsFunction("__kofDblTxt", List.of("x"),
                List.of(new JsIr.JsReturn(new JsIr.JsConditional(cond, sentinel, x))),
                false, false, false);
    }

    /**
     * D6-1/3.8b (bridge JS): o host não reflete {@code RecordComponent} num objeto
     * GraalJS, então o record expõe os campos na ordem de declaração para o
     * {@code KofJsFfiMarshal} empacotar o struct C por valor — paridade com o
     * {@code kof_ffi_write_struct} reflexivo do JVM. Só records são bindáveis
     * como struct (o gate de {@code isExternBound} fecha o resto).
     */
    static JsIr.JsFunction lowerRecordFfiFields(IRClass clazz) {
        List<JsIr.JsExpression> values = new ArrayList<>();
        for (IRField field : clazz.fields()) {
            values.add(new JsIr.JsMember(new JsIr.JsThis(),
                    "_" + JsTypeMapper.sanitizeName(field.name())));
        }
        return new JsIr.JsFunction("__kof_ffi_fields", List.of(),
                List.of(new JsIr.JsReturn(new JsIr.JsArrayLiteral(values))), false, false, false);
    }

    /**
     * D6-1/3.8b (bridge JS, retorno 21/09): o host devolve os campos do struct
     * (por valor) como um array na ordem de declaração — o host não instancia um
     * objeto GraalJS. Este factory ESTÁTICO reconstrói o record pelo construtor
     * canônico, paridade com o {@code kof_ffi_read_struct} reflexivo do JVM. Um
     * campo `Long` vira `BigInt` (o JS representa Long como BigInt).
     */
    static JsIr.JsFunction lowerRecordFfiFrom(IRClass clazz, String jsName, JsLoweringContext ctx) {
        List<IRMethod> ctors = new ArrayList<>();
        for (IRMethod method : clazz.methods()) {
            if ("<init>".equals(method.name())) ctors.add(method);
        }
        if (ctors.size() > 1 && "java/lang/Record".equals(clazz.superName())) {
            // A record's FFI layout is the declared component order, which is
            // its canonical constructor — dispatching by `fields.length` would
            // let a 1-component record call a 2-component overload.
            IRMethod canonical = ctors.stream()
                    .filter(m -> m.parameterTypes().size() == clazz.fields().size())
                    .findFirst()
                    .orElseGet(() -> ctors.stream()
                            .max((a, b) -> Integer.compare(a.parameterTypes().size(), b.parameterTypes().size()))
                            .orElseThrow());
            return lowerRecordFfiFrom(clazz, jsName, canonical, ctx);
        }
        IRMethod canonical = ctors.isEmpty() ? null : ctors.get(0);
        if (canonical == null) {
            return new JsIr.JsFunction("__kof_ffi_from", List.of("fields"),
                    List.of(new JsIr.JsReturn(new JsIr.JsNew(new JsIr.JsIdentifier(jsName), List.of()))),
                    true, false, false);
        }
        return lowerRecordFfiFrom(clazz, jsName, canonical, ctx);
    }

    private static JsIr.JsFunction lowerRecordFfiFrom(IRClass clazz, String jsName, IRMethod canonical,
            JsLoweringContext ctx) {
        List<JsIr.JsExpression> args = new ArrayList<>();
        int idx = 0;
        for (IRField field : clazz.fields()) {
            JsIr.JsExpression elem = new JsIr.JsIndex(
                    new JsIr.JsIdentifier("fields"), new JsIr.JsNumber(String.valueOf(idx)));
            // Os valores vêm do host como Java boxed (foreign GraalJS): coage ao
            // primitivo JS do tipo do campo — Long→BigInt, bool→Boolean, o resto
            // Number — p/ o record ficar com os mesmos tipos do caminho puro JS.
            if (field.type() instanceof Type.PrimitiveType pt) {
                String prim = Type.canonicalPrimitiveName(pt.name());
                if ("long".equals(prim)) {
                    elem = new JsIr.JsCall(new JsIr.JsIdentifier("BigInt"), List.of(elem));
                } else if ("bool".equals(prim)) {
                    elem = new JsIr.JsCall(new JsIr.JsIdentifier("Boolean"), List.of(elem));
                } else {
                    elem = new JsIr.JsCall(new JsIr.JsIdentifier("Number"), List.of(elem));
                }
            }
            args.add(elem);
            idx++;
        }
        return new JsIr.JsFunction("__kof_ffi_from", List.of("fields"),
                List.of(new JsIr.JsReturn(new JsIr.JsNew(new JsIr.JsIdentifier(jsName),
                        ctx.ctorDispatch.contains(clazz.name())
                                ? appendCtorToken(args, canonical.parameterTypes())
                                : args))),
                true, false, false);
    }


    /** #757: o dispatch de construtores marca a instância com o token do ctor. */
    static List<JsIr.JsExpression> appendCtorToken(List<JsIr.JsExpression> args, List<Type> types) {
        List<JsIr.JsExpression> out = new ArrayList<>(args);
        out.add(new JsIr.JsString(TopLevelOverload.sigTag(types).replace('_', '$')));
        return out;
    }
}
