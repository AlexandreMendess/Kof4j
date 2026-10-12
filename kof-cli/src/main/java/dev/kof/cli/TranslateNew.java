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
        return TranslateTypes.kofType(typeName) + "(" + args + ")";
    }
}
