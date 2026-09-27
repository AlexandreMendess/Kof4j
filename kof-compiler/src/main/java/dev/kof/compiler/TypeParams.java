package dev.kof.compiler;

import java.util.List;

/**
 * §355 — ponto único de leitura dos entries de {@code typeParameters()} do
 * AST. O parser (§355) passou a gravar o BOUND na própria entrada:
 * {@code "T"} (sem bound) ou {@code "T: Animal"} (com). Antes o bound era
 * ENGOLIDO e o nome do bound entrava como um segundo type-param fantasma
 * (["T","Animal"]), perdendo a informação que a erasure JVM precisa
 * (`<T: Animal>` apaga para {@code Animal}, não para {@code Object}).
 *
 * <p>Qualquer comparação de nome de type-param CONTRA a lista crua deve
 * passar por aqui ({@link #contains}, {@link #indexOf}, {@link #names});
 * quem constrói um {@link Type.TypeVariable} usa {@link #variable} para não
 * perder o bound.
 */
final class TypeParams {

    private TypeParams() {}

    /**
     * Nome limpo da entrada (`"T: Animal"` → `"T"`; `"out T: Animal"` → `"T"`).
     * X5.3 (D-TYPE-VARIANCE): a variância (`out`/`in`) precede o nome e é
     * removida aqui — todo consumidor de descritor/erasure passa pelo nome.
     */
    static String name(String entry) {
        if (entry == null) return "";
        int c = entry.indexOf(':');
        String n = (c < 0 ? entry : entry.substring(0, c)).trim();
        if (n.startsWith("out ")) return n.substring(4).trim();
        if (n.startsWith("in ")) return n.substring(3).trim();
        return n;
    }

    /**
     * X5.3 (D-TYPE-VARIANCE): variância declarada — `"out"`, `"in"` ou `""`
     * (invariante; default compatível com todo tipo genérico já existente).
     */
    static String variance(String entry) {
        if (entry == null) return "";
        int c = entry.indexOf(':');
        String n = (c < 0 ? entry : entry.substring(0, c)).trim();
        if (n.startsWith("out ")) return "out";
        if (n.startsWith("in ")) return "in";
        return "";
    }

    /** Texto do bound (após o ':'), ou null quando não há. */
    static String boundText(String entry) {
        if (entry == null) return null;
        int c = entry.indexOf(':');
        if (c < 0) return null;
        String b = entry.substring(c + 1).trim();
        return b.isEmpty() ? null : b;
    }

    static List<String> names(List<String> entries) {
        if (entries == null || entries.isEmpty()) return List.of();
        return entries.stream().map(TypeParams::name).toList();
    }

    /** A lista contém o type-param {@code n} (comparando pelo nome limpo)? */
    static boolean contains(List<String> entries, String n) {
        return indexOf(entries, n) >= 0;
    }

    static int indexOf(List<String> entries, String n) {
        if (entries == null) return -1;
        for (int i = 0; i < entries.size(); i++) {
            if (name(entries.get(i)).equals(n)) return i;
        }
        return -1;
    }

    /**
     * O TypeVariable do type-param {@code n} na lista, com o bound resolvido
     * pelo mesmo contexto de {@code CompilerTypes.resolveWithTypeParams}
     * (imports/unidade). Null quando {@code n} não é um type-param.
     */
    static Type.TypeVariable variable(String n, List<String> entries,
                                      CompilationUnitNode unit, SemanticAnalyzer sa) {
        int i = indexOf(entries, n);
        if (i < 0) return null;
        String bound = boundText(entries.get(i));
        if (bound == null) return new Type.TypeVariable(n);
        return new Type.TypeVariable(n, CompilerTypes.toType(bound, unit, sa));
    }

    /**
     * O symbol do type-param a partir da entry crua do AST (`"T"` ou
     * `"T: Animal"`) — os 4 sítios de define (classe/record/interface/função)
     * passam por aqui para o bound chegar ao TypeVariable do escopo.
     */
    static SymbolTable.TypeParameterSymbol symbol(String entry, SemanticAnalyzer sa) {
        String n = name(entry);
        String bound = boundText(entry);
        if (bound == null) return new SymbolTable.TypeParameterSymbol(n);
        return new SymbolTable.TypeParameterSymbol(n,
                CompilerTypes.toType(bound, sa != null ? sa.unit() : null, sa));
    }

    /**
     * §355 (rio da erasure) — varredura RECURSIVA de um tipo resolvido:
     * todo leaf que {@code leaf} reconhece como type-param (nome simples sem
     * pacote, ex. o `T` dentro de `List<T>` ou de `T[]`) vira
     * {@link Type.TypeVariable} COM bound. Sem isto, {@code MemberResolver.resolveType}
     * / {@code CompilerTypes.resolveWithTypeParams} entregavam
     * {@code ClassType("", "T")} no ARGUMENTO/COMPONENTE — a checagem de nome
     * só valia no topo — e o emit saía `checkcast T` (#399/#363), campo `T[]`
     * com descritor `[LT;` (#295), signature fantasma. Onde {@code leaf}
     * devolve null o tipo passa intacto (nada que compilava muda).
     */
    static Type rewrite(Type t, java.util.function.Function<String, Type> leaf) {
        if (t == null) return null;
        if (t instanceof Type.ClassType ct) {
            Type direct = ct.packageName().isEmpty() && ct.typeArguments().isEmpty()
                    ? leaf.apply(ct.name()) : null;
            if (direct != null) return direct;
            if (ct.typeArguments().isEmpty()) return t;
            java.util.List<Type> args = new java.util.ArrayList<>(ct.typeArguments().size());
            boolean changed = false;
            for (Type a : ct.typeArguments()) {
                Type r = rewrite(a, leaf);
                if (r != a) changed = true;
                args.add(r);
            }
            return changed ? new Type.ClassType(ct.packageName(), ct.name(), args) : t;
        }
        if (t instanceof Type.ArrayType at) {
            Type c = rewrite(at.componentType(), leaf);
            return c == at.componentType() ? t : new Type.ArrayType(c);
        }
        if (t instanceof Type.NullableType nt) {
            Type c = rewrite(nt.inner(), leaf);
            return c == nt.inner() ? t : new Type.NullableType(c);
        }
        // §288 (#396, D-RULE6-BATCH opção (b)): o miolo de um TIPO-FUNÇÃO
        // declarado também carrega type-params do escopo — `mapItems(f: (T) -> T)`
        // deixava `ClassType("","T")` nos params/retorno da FunctionType (o
        // rewrite só varria ClassType/Array/Nullable), o checker comparava
        // `(Int)->Int` contra a função fantasma e SEM014, e o lowering
        // sintetizava a interface `Function1_CT_CT` com descriptor `LT;`
        // (crash no load). Recursar aqui = fonte ÚNICA: checker (resolveType)
        // e lowering (resolveWithTypeParams) passam pelo MESMO ponto.
        if (t instanceof Type.FunctionType ft) {
            java.util.List<Type> ps = new java.util.ArrayList<>(ft.parameterTypes().size());
            boolean changed = false;
            for (Type p : ft.parameterTypes()) {
                Type rp = rewrite(p, leaf);
                if (rp != p) changed = true;
                ps.add(rp);
            }
            Type rr = rewrite(ft.returnType(), leaf);
            if (changed || rr != ft.returnType()) return new Type.FunctionType(ps, rr, ft.className());
            return t;
        }
        if (t instanceof Type.WildcardType wt) {
            Type rb = rewrite(wt.bound(), leaf);
            return rb == wt.bound() ? t : new Type.WildcardType(rb, wt.upper());
        }
        return t;
    }

    /**
     * §357/#295: o slot apaga para array de REFERENCIA (`T[]` → `Object[]`)
     * e o valor é array de PRIMITIVO (`Int[]` → `[I`)? No JVM isso é
     * irreparável — `int[]` NÃO é subtipo de `Object[]` (JVMS 4.10.1; o
     * javac rejeita `Object[] o = new int[10]`), e boxar elemento a elemento
     * no assign criaria cópia com aliasing divergente (silencioso, pior que
     * o crash). Script/JS/Native aceitam (arrays dinâmicos) — por isso o
     * gate é do ALVO (precedente: SEM092 só em NATIVE*, NAT001 só no cross).
     */
    static boolean primitiveArrayIntoErasedRefArray(Type slot, Type value) {
        Type sc = slot instanceof Type.NullableType n ? n.inner() : slot;
        Type vc = value instanceof Type.NullableType n ? n.inner() : value;
        if (!(sc instanceof Type.ArrayType sa) || !(vc instanceof Type.ArrayType va)) return false;
        Type elem = sa.componentType() instanceof Type.NullableType nt ? nt.inner() : sa.componentType();
        if (!(elem instanceof Type.TypeVariable || elem instanceof Type.WildcardType)) return false;
        return va.componentType() instanceof Type.PrimitiveType pt && !Type.isVoid(pt);
    }
}
