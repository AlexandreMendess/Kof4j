package dev.kof.cli;

/**
 * Satélite do REFACTOR-500 (check_500, TranslateEmitter 670→<600): o fold do
 * static init block (fase F — a recusa honesta de reordenar execução viaja
 * com o código) + o reconhecedor de literal puro.
 */
final class TranslateStaticInit {

    private TranslateStaticInit() {}

    static boolean isPureLiteral(String v) {
        if (v == null || v.isEmpty()) return false;
        String s = v.startsWith("-") ? v.substring(1) : v;
        if (s.equals("true") || s.equals("false") || s.equals("null")) return true;
        if (s.startsWith("\"")) {
            // string: abre, fecha, e nenhum escape complicado no meio
            return s.length() >= 2 && s.endsWith("\"") && s.indexOf('"', 1) == s.length() - 1;
        }
        if (s.startsWith("'")) {
            return s.length() == 3 && s.endsWith("'");
        }
        return s.matches("[0-9_]+") || s.toLowerCase().matches("0x[0-9a-f_]+")
                || s.matches("\\d*\\.?\\d*([eE][-+]?\\d+)?[fFdDlL]?") && s.matches(".*\\d.*");
    }

    /**
     * Fase F: `static { V = 42; }` → o campo ganha `= 42`. Aceita SÓ
     * `field = <literal>;` para campo estático DECLARADO; qualquer outra
     * instrução (chamada, loop, leitura de campo, expressão) recusa o
     * bloco inteiro — a ordem de execução do Java não é recriável num
     * inicializador de campo sem mudar o observável.
     */
    static void foldStaticInitBlock(TranslateEmitter em) {
        em.p.expect("{");
        java.util.LinkedHashMap<String, String> inits = new java.util.LinkedHashMap<>();
        while (!em.p.at("}")) {
            if (!em.p.at(T.IDENT)) {
                throw new TranslateException(
                        "static initialization block: only `field = <literal>;` folds into the "
                        + "field initializer (Kof has no static init block); this statement is not a "
                        + "simple literal assignment — manual review");
            }
            String field = em.p.next().text;
            if (!em.staticFields.contains(field) || !em.p.at("=")) {
                throw new TranslateException(
                        "static initialization block: only `field = <literal>;` folds into the "
                        + "field initializer (Kof has no static init block); `" + field
                        + "` is not a declared static field or is not a simple assignment — manual review");
            }
            em.p.next(); // =
            String value = em.parseExpr();
            if (!isPureLiteral(value)) {
                throw new TranslateException(
                        "static initialization block: `" + field + " = " + value + "` is not a pure "
                        + "literal — folding it into the field initializer would reorder execution "
                        + "(Kof has no static init block) — manual review");
            }
            if (!em.p.at(";")) {
                throw new TranslateException(
                        "static initialization block: expected `;` after `" + field + " = " + value
                        + "` — manual review");
            }
            em.p.next();
            inits.put(field, value);
        }
        em.p.expect("}");
        em.pendingStaticInits.putAll(inits);
    }

}
