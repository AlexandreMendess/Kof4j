package dev.kof.compiler;

/**
 * Tipo de resultado de expressões binárias, extraído do TypeChecker
 * (REFACTOR-500 fase 6 / split §446): SEM053 (ordem String), SEM062
 * (enum×String, D-ENUM207), SEM100 (`as`-parse, §188), SEM001/SEM002
 * (aritmética em tipo errado) e o resultado da aritmética de primitivos.
 * Sem estado — diagnostics por parâmetro.
 */
public final class SemBinaryResultTyper {

    private SemBinaryResultTyper() {}

    /** String OU Nullable(String) — para o guard de ordem em String (bug 98). */
    private static boolean isMaybeString(Type t) {
        if (t instanceof Type.NullableType nt) return BuiltinTypes.isString(nt.inner());
        return BuiltinTypes.isString(t);
    }

    /** Enum OU Nullable(enum) — D-ENUM207 (a comparação enum×String é SEM062). */
    private static boolean isEnumRef(Type t) {
        if (t instanceof Type.NullableType nt) return isEnumRef(nt.inner());
        return BuiltinTypes.isEnumType(t);
    }

    static Type inferBinaryResultType(DiagnosticCollector diagnostics, String operator, Type left, Type right) {
        // bug 98 (paridade absoluta JVM=JS=X86=ARM=RISC, opção B da mantenedora):
        // `<`/`<=`/`>`/`>=` entre Strings — a ordem era UNspecified no reference
        // (expressions.md:56-58) e cada target dava lixo DIFERENTE: JVM tudo
        // false (if_acmp em referência), Native comparava PONTEIRO (ordem de
        // alocação), Script dava lexicográfico — paridade quebrada em silêncio
        // (R6). REJEITAR em compile-time com SEM053 (o MESMO erro nos 5 alvos:
        // este typer é o frontend único) apontando para o idiom do corpus —
        // `s.compareTo(t) < 0` (ordem lexicográfica, paridade §97). `==`/`!=`
        // (conteúdo, congelado) e `+` (concat) NÃO são afetados.
        if (("<".equals(operator) || "<=".equals(operator) || ">".equals(operator)
                || ">=".equals(operator))
                && (isMaybeString(left) || isMaybeString(right))) {
            if (diagnostics != null) {
                String rel = switch (operator) {
                    case "<" -> "< 0";
                    case "<=" -> "<= 0";
                    case ">" -> "> 0";
                    default -> ">= 0";
                };
                diagnostics.error("", 0, 0, 0,
                        "Kof has no operator '" + operator + "' for String "
                                + "(lexicographic order is Unspecified — it diverges per target); "
                                + "use: s.compareTo(t) " + rel,
                        "SEM053");
            }
            return Type.UnknownType.UNKNOWN;
        }
        // §211 / D-ENUM207: um valor de enum NÃO é uma String. `Dir.N == "N"`
        // compilava e devolvia `true` (o valor era `ldc "N"`), quebrando a
        // identidade que a issue #207 exige. A mantenedora ratificou o erro de
        // tipo (DECISIONS D-ENUM207). Rejeitado no frontend COMPARTILHADO —
        // o mesmo SEM062 nos 4 alvos (sem divergência, freeze regra 5).
        if (("==".equals(operator) || "!=".equals(operator))
                && (isEnumRef(left) && isMaybeString(right) || isMaybeString(left) && isEnumRef(right))) {
            if (diagnostics != null) {
                diagnostics.error("", 0, 0, 0,
                        "Cannot compare an enum value to a String: an enum constant is not "
                                + "a String (D-ENUM207). Compare two enum values, or use "
                                + ".name() explicitly to get the name",
                        "SEM062");
            }
            return Type.PrimitiveType.BOOL;
        }
        if ("==".equals(operator) || "!=".equals(operator) || "<".equals(operator) ||
                ">".equals(operator) || "<=".equals(operator) || ">=".equals(operator)) {
            return Type.PrimitiveType.BOOL;
        }

        // D-TROOL (19/09): com um `Troolean` num dos lados, `&&`/`||`/`!`
        // produzem tres estados (Kleene, DECISIONS.md) — o tipo semantico
        // precisa casar com o da pilha do lowering (caixa Boolean|null),
        // senão o slot do consumidor mente (classe do §462).
        if ("&&".equals(operator) || "||".equals(operator) || "!".equals(operator)) {
            if (Type.isTroolean(left) || Type.isTroolean(right)) {
                return new Type.NullableType(Type.PrimitiveType.BOOL);
            }
            return Type.PrimitiveType.BOOL;
        }
        if ("instanceof".equals(operator)) {
            return Type.PrimitiveType.BOOL;
        }
        if ("as".equals(operator)) {
            // §188 (voto (A), D-CLOSEALL-BATCH, mantenedora 21/09):
            // `String as <numérico|char|bool>` era um PARSE encubierto —
            // compilava no cheque e dava VerifyError no load (checkcast
            // Integer sobre String). `as` = cast, sem conversão implícita;
            // o caminho canônico de texto→valor já existe no stdlib
            // (math.parseInt / math.parseFloat / math.parseDouble /
            // math.parseChar).
            if (diagnostics != null && isStringParseCast(left, right)) {
                diagnostics.error("", 0, 0, 0,
                        "an 'as' cast is not a parse: '" + left + "' does not cast to '"
                                + right + "' (an implicit String conversion would be a "
                                + "hidden parse) — use the stdlib parsers "
                                + "(math.parseInt / math.parseFloat / math.parseDouble / math.parseChar)",
                        "SEM100");
                return Type.UnknownType.UNKNOWN;
            }
            return right;
        }
        if (Type.isString(left) || Type.isString(right)) {
            if ("+".equals(operator)) {
                return BuiltinTypes.STRING;
            }
            if (diagnostics != null) {
                diagnostics.error("", 0, 0, 0,
                        "Cannot apply '" + operator + "' to String and " + right, "SEM001");
            }
            return Type.UnknownType.UNKNOWN;
        }
        if (left instanceof Type.PrimitiveType lp && right instanceof Type.PrimitiveType rp) {
            if ("int".equals(lp.name())) {
                if ("long".equals(rp.name()) || "Long".equals(rp.name())) return Type.PrimitiveType.LONG;
                if ("float".equals(rp.name()) || "Float".equals(rp.name())) return Type.PrimitiveType.FLOAT;
                if ("double".equals(rp.name()) || "Double".equals(rp.name())) return Type.PrimitiveType.DOUBLE;
                return Type.PrimitiveType.INT;
            }
            if ("long".equals(lp.name()) || "Long".equals(rp.name())) {
                if ("float".equals(rp.name()) || "Float".equals(rp.name())) return Type.PrimitiveType.FLOAT;
                if ("double".equals(rp.name()) || "Double".equals(rp.name())) return Type.PrimitiveType.DOUBLE;
                return Type.PrimitiveType.LONG;
            }
            if ("float".equals(lp.name()) || "Float".equals(lp.name())) {
                if ("double".equals(rp.name()) || "Double".equals(rp.name())) return Type.PrimitiveType.DOUBLE;
                return Type.PrimitiveType.FLOAT;
            }
            if ("double".equals(lp.name()) || "Double".equals(rp.name())) {
                return Type.PrimitiveType.DOUBLE;
            }
                if ("bool".equals(lp.name()) || "bool".equals(rp.name())) {
                    if ("+".equals(operator) || "-".equals(operator) || "*".equals(operator) ||
                            "/".equals(operator) || "%".equals(operator)) {
                        if (diagnostics != null) {
                            diagnostics.error("", 0, 0, 0,
                                    "Cannot apply '" + operator + "' to boolean types. Use == or != for comparison.", "SEM002");
                        }
                        return Type.UnknownType.UNKNOWN;
                    }
                }
            return left;
        }
        if (left instanceof Type.ArrayType || right instanceof Type.ArrayType) {
            return Type.UnknownType.UNKNOWN;
        }
        if (left instanceof Type.UnknownType || right instanceof Type.UnknownType) {
            return Type.UnknownType.UNKNOWN;
        }
        // Aritmética sobre tipo referência (ex.: param de lambda sem anotação
        // → Object) não tem opcode: o emit cairia em IADD sobre referência e a
        // JVM rejeitaria o bytecode (VerifyError). Diagnóstico explícito, nunca
        // fallback silencioso (R6). String + já foi tratado acima.
        if (TypeChecker.isArithmeticOp(operator) && (TypeChecker.isReferenceType(left) || TypeChecker.isReferenceType(right))) {
            if (diagnostics != null) {
                diagnostics.error("", 0, 0, 0,
                        "Cannot apply '" + operator + "' to non-numeric type "
                                + (TypeChecker.isReferenceType(left) ? left : right)
                                + " (declare the parameter type, e.g. (x: Int) -> ...)",
                        "SEM001");
            }
            return Type.UnknownType.UNKNOWN;
        }
        return left;
    }

    /** §188: cast de origem STRING para destino primitivo (não-String) =
     *  parse encubierto → SEM100 (nulo-nullable incluso nos dois lados). */
    private static boolean isStringParseCast(Type src, Type dst) {
        Type s = src instanceof Type.NullableType nt ? nt.inner() : src;
        Type d = dst instanceof Type.NullableType nt ? nt.inner() : dst;
        if (!Type.isString(s)) return false;
        if (!(d instanceof Type.PrimitiveType dt)) return false;
        switch (dt.name()) {
            case "int", "long", "byte", "short", "float", "double", "char", "bool": return true;
            default: return false;
        }
    }
}
