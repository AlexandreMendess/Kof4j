package dev.kof.cli;

/**
 * Expressão `new` Java→Kof do {@code kof translate} (extraído de
 * {@link TranslateExpr} p/ o gate ≤500): `new Box&lt;Integer&gt;(...)` →
 * `Box(...)`, `new int[n]` → `new Int[n]`, exceções → String, e os gaps
 * honestos R6 (tipo qualificado, array initializer, classe anônima).
 */
final class TranslateNew {

    private TranslateNew() {}

    static String parse(TranslateExpr ctx) {
        Parser p = ctx.p;
        String typeName = p.next().text;
        // Nome qualificado `new java.util.ArrayList<...>()`: o translator
        // ignora imports e não resolve FQN. Mapear coleções Java →
        // stdlib Kof (`ArrayList`→`listOf`/`List`, `HashMap`→`Map`) é
        // decisão de design (regra 6) → manual review (R6), nunca parse
        // error confuso nem Kof inválido.
        if (p.at(".")) {
            throw new TranslateException(
                    "qualified type (`new pkg.Class(...)`) is not resolved by the "
                    + "translator (imports are ignored) — manual review");
        }
        // `new Box<Integer>(...)` — Kof infere o tipo na chamada
        // (`Box(5)`); os argumentos de tipo Java são descartados.
        if (p.at("<")) {
            int depth = 0;
            do {
                if (p.at("<")) depth++;
                else if (p.at(">")) depth--;
                p.next();
            } while (depth > 0 && !p.at(T.EOF));
        }
        if (p.at("[")) {
            // array creation: `new int[n]` → `new Int[n]`.
            // Bug latente (achado 13/09): o código consumia `[` E o
            // primeiro token da dimensão antes do parseExpr, então
            // `new int[3]` saía `new Int[]]` (PARSE041 no Kof gerado).
            p.next(); // [
            if (p.at("]")) {
                // `new int[]{...}` — array initializer no equivalent
                // direct in Kof (manual review, R6).
                throw new TranslateException(
                        "array initializer `new T[]{...}` has no direct equivalent in Kof "
                        + "(use `new Int[n]` + assignments) — manual review");
            }
            String size = ctx.parseExpr();
            p.expect("]");
            return "new " + TranslateTypes.kofType(typeName) + "[" + size + "]";
        }
        String args = ctx.parseCallArgs();
        if (p.at("{")) {
            // Fase F (decisão da mantenedora 14/10): anônima de interface
            // funcional SAM → lambda Kof; multi-método/estado → recusa
            // nomeada.
            return ctx.parseAnonymousAsLambda();
        }
        if (typeName.equals("RuntimeException") || typeName.equals("IllegalStateException")
                || typeName.equals("IllegalArgumentException") || typeName.equals("Exception")) {
            // Exceções Java → String Kof (idiom errors.md: `throw "msg"`).
            // `new RuntimeException("boom " + k)` → `"boom " + k`.
            // Sem args → string vazia (throw exige String, SEM026).
            return args.isEmpty() ? "\"\"" : args;
        }
        // Tabela JDK→stdlib (mantenedora aprovou 14/10): fechar o FALSO OK —
        // antes `new ArrayList<>()` emitia `ArrayList()` que NÃO compila
        // (SEM015 silencioso). Vazio → listOf()/mapOf(); com args → recusa
        // nomeada (semântica de cópia não é 1:1).
        if (typeName.equals("ArrayList")) {
            if (!args.isEmpty()) {
                throw new TranslateException(
                        "`new ArrayList<>(...)` with arguments has no 1:1 Kof equivalent (copying "
                        + "semantics) — use `listOf(...)` explicitly — manual review");
            }
            return "listOf()";
        }
        if (typeName.equals("HashMap")) {
            if (!args.isEmpty()) {
                throw new TranslateException(
                        "`new HashMap<>(...)` with arguments has no 1:1 Kof equivalent (copying "
                        + "semantics) — use `mapOf(...)` explicitly — manual review");
            }
            return "mapOf()";
        }
        if (typeName.equals("StringBuilder")) {
            // Tabela aprovada: a cadeia `.append(e)...toString()` baixa para
            // concatenação `e1 + e2` (o idioma Kof). Sem arg inicial; cadeia
            // que não termina em toString → recusa nomeada.
            if (!args.isEmpty()) {
                throw new TranslateException(
                        "`new StringBuilder(initial)` has no direct equivalent — start the Kof "
                        + "concatenation with the initial value (empty-string + x for non-String) — manual review");
            }
            java.util.List<String> parts = new java.util.ArrayList<>();
            while (p.at(".") && p.peek(1) != null && "append".equals(p.peek(1).text)) {
                p.next(); // .
                p.next(); // append
                parts.add(ctx.parseSingleArg());
            }
            if (parts.isEmpty() || !(p.at(".") && p.peek(1) != null && "toString".equals(p.peek(1).text))) {
                throw new TranslateException(
                        "StringBuilder chain: only the `new StringBuilder().append(...).append(...)"
                        + ".toString()` shape lowers to Kof concatenation — manual review");
            }
            p.next(); // .
            p.next(); // toString
            p.expect("("); p.expect(")");
            if (parts.size() == 1) {
                // `append(e).toString()` de UM valor: `"" + e` normaliza o
                // não-String para String (String.valueOf em Java) — medido:
                // `"" + 42` compila e imprime 42
                return "\"\"" + " + " + parts.get(0);
            }
            return String.join(" + ", parts);
        }
        if (JDK_UNMAPPED_TYPES.contains(typeName)) {
            // fora da tabela aprovada: recusa NOMEADA (nunca `X()` que não
            // compila — o falso OK fechado)
            throw new TranslateException(
                    "JDK type `" + typeName + "` is outside the approved JDK→stdlib table "
                    + "(ArrayList/HashMap/StringBuilder mapped) — map it explicitly in Kof — manual review");
        }
        return TranslateTypes.kofType(typeName) + "(" + args + ")";
    }

    /** Tipos do JDK conhecidos SEM mapeamento aprovado — recusa nomeada. */
    private static final java.util.Set<String> JDK_UNMAPPED_TYPES = java.util.Set.of(
            "HashSet", "LinkedHashSet", "TreeMap", "TreeSet", "LinkedList", "Vector", "Hashtable",
            "StringBuffer", "Stack", "PriorityQueue", "ArrayDeque", "ConcurrentHashMap");
}
