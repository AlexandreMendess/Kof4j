package dev.kof.compiler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * X5.3 (`D-X5-SURFACE`/`D-TYPE-VARIANCE`) — restrição de POSIÇÃO da variância
 * declaration-site. O cheque de atribuibilidade
 * ({@link TypeChecker#genericArgsCompatible}) sozinho NÃO preserva a solidez:
 * um `out T` num campo gravável (ou parâmetro) deixa a covariância escrever um
 * valor do tipo errado num alias, e um `in T` num retorno deixa a
 * contravariância expor um valor do tipo errado. Sem esta guarda a fatia
 * seria uma fachada insegura (Q7).
 *
 * <p>Regras v1 (conservadoras — ocorrência textual do nome do type-param em
 * qualquer ponto do tipo, inclusive aninhado):
 * <ul>
 *   <li><b>out T</b> — proibido em parâmetro de método/construtor e em campo
 *       GRAVÁVEL (classe); permitido em retorno e em componente de record/
 *       campo de interface (somente-leitura = posição de saída).</li>
 *   <li><b>in T</b> — proibido em retorno e em QUALQUER campo/componente
 *       (todo campo é lido = posição de saída); permitido em parâmetros.</li>
 * </ul>
 * Um type-param homônimo declarado pelo PRÓPRIO método sombreia o do tipo e
 * não dispara a guarda. A variança em posição de herança (type-args do
 * `extends`/`implements`) fica para X5.3b.
 */
final class VarianceChecks {

    private VarianceChecks() {}

    private static final Pattern WORD = Pattern.compile("[A-Za-z0-9_]");

    static void checkClass(SemanticAnalyzer sa, ClassDeclarationNode cls) {
        Map<String, String> vs = variances(cls.typeParameters());
        if (vs.isEmpty()) return;
        checkHeritage(sa, vs, concat(cls.superClass(), cls.interfaces()), cls.position());
        for (AstNode member : cls.members()) checkMember(sa, vs, member, false, List.of());
    }

    static void checkRecord(SemanticAnalyzer sa, RecordDeclarationNode rec) {
        Map<String, String> vs = variances(rec.typeParameters());
        if (vs.isEmpty()) return;
        checkHeritage(sa, vs, concat(rec.superClass(), rec.interfaces()), rec.position());
        // Componentes de record são somente-leitura (getter) → saída:
        // `in T` é proibido; `out T` é permitido.
        for (RecordComponentNode comp : rec.components()) {
            for (Map.Entry<String, String> e : vs.entrySet()) {
                if ("in".equals(e.getValue()) && occurs(comp.type(), e.getKey())) {
                    report(sa, comp.position(), e.getKey(), e.getValue(), "record component");
                }
            }
        }
        for (AstNode member : rec.members()) checkMember(sa, vs, member, true, List.of());
    }

    static void checkInterface(SemanticAnalyzer sa, InterfaceDeclarationNode iface) {
        Map<String, String> vs = variances(iface.typeParameters());
        if (vs.isEmpty()) return;
        checkHeritage(sa, vs, iface.interfaces(), iface.position());
        for (AstNode member : iface.members()) checkMember(sa, vs, member, true, List.of());
    }

    /**
     * X5.3b: variância em posição de HERANÇA. Se um type-param meu (`out T`/
     * `in T`) é passado a um supertipo cujo parâmetro correspondente tem
     * variância INCOMPATÍVEL (ou é invariante), a declaração é insólida: o
     * supertipo reintroduz `T` na posição oposta. Ex.: `class C<out T> extends
     * Sink<T>` com `Sink<in T>` usa `T` numa posição de entrada → SEM083.
     * Conservador: só reconhece o argumento como NOME SIMPLES de um type-param
     * meu (aninhado fica fora da v1).
     */
    private static void checkHeritage(SemanticAnalyzer sa, Map<String, String> vs,
                                      List<String> supertypes, SourcePosition pos) {
        if (supertypes == null || supertypes.isEmpty()) return;
        for (String sup : supertypes) {
            if (sup == null) continue;
            String raw = rawName(sup);
            String args = typeArgs(sup);
            if (raw == null || args == null) continue;
            List<String> supVars = sa.varianceOf(raw);
            if (supVars == null) continue;
            List<String> supArgs = splitTopLevel(args);
            for (int i = 0; i < supArgs.size() && i < supVars.size(); i++) {
                String mine = vs.get(supArgs.get(i).trim());
                if (mine == null) continue;
                String supra = supVars.get(i) == null ? "" : supVars.get(i);
                if (mine.equals(supra)) continue;
                reportHeritage(sa, pos, supArgs.get(i).trim(), mine, raw, supra);
            }
        }
    }

    private static List<String> concat(String first, List<String> rest) {
        List<String> out = new java.util.ArrayList<>();
        if (first != null) out.add(first);
        if (rest != null) out.addAll(rest);
        return out;
    }

    /** Nome simples do supertipo, sem pacote e sem type-args. */
    private static String rawName(String sup) {
        String s = sup.trim();
        int lt = s.indexOf('<');
        if (lt >= 0) s = s.substring(0, lt).trim();
        int dot = s.lastIndexOf('.');
        return dot >= 0 ? s.substring(dot + 1) : s;
    }

    /** Conteúdo do `<>` de topo, ou {@code null} se não houver type-args. */
    private static String typeArgs(String sup) {
        int lt = sup.indexOf('<');
        if (lt < 0 || !sup.endsWith(">")) return null;
        int depth = 0;
        for (int i = lt; i < sup.length(); i++) {
            char c = sup.charAt(i);
            if (c == '<') depth++;
            else if (c == '>') {
                depth--;
                if (depth == 0 && i == sup.length() - 1) return sup.substring(lt + 1, i);
            }
        }
        return null;
    }

    /** Split por vírgula em profundidade zero (fora de `<>`/`()`). */
    private static List<String> splitTopLevel(String s) {
        List<String> out = new java.util.ArrayList<>();
        int depth = 0, start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<' || c == '(') depth++;
            else if (c == '>' || c == ')') depth--;
            else if (c == ',' && depth == 0) {
                out.add(s.substring(start, i));
                start = i + 1;
            }
        }
        out.add(s.substring(start));
        return out;
    }

    private static void reportHeritage(SemanticAnalyzer sa, SourcePosition pos, String name,
                                       String mine, String supertype, String supra) {
        if (sa.diagnostics() == null) return;
        String supraLabel = supra.isEmpty() ? "invariant" : "'" + supra + "'";
        String msg = "out".equals(mine)
                ? "type parameter '" + name + "' is declared 'out' but is passed as " + supraLabel
                        + " to supertype '" + supertype + "' — a covariant parameter cannot appear"
                        + " in a supertype position that consumes it (invariant/contravariant);"
                        + " the supertype would let a covariant alias store a value of the wrong type"
                : "type parameter '" + name + "' is declared 'in' but is passed as " + supraLabel
                        + " to supertype '" + supertype + "' — a contravariant parameter cannot appear"
                        + " in a supertype position that exposes it (invariant/covariant);"
                        + " the supertype would let a contravariant alias expose a value of the wrong type";
        sa.diagnostics().error(pos != null ? pos.file() : "", pos != null ? pos.line() : 0,
                pos != null ? pos.column() : 0, 0, msg, "SEM083");
    }

    private static void checkMember(SemanticAnalyzer sa, Map<String, String> vs, AstNode member,
                                    boolean readOnlyFields, List<String> localShadows) {
        if (member instanceof FieldDeclarationNode field) {
            // Campo de classe é gravável → `out` proibido; campo de record/
            // interface é somente-leitura → `out` permitido. `in` é sempre
            // proibido (o campo é lido).
            for (Map.Entry<String, String> e : vs.entrySet()) {
                if (localShadows.contains(e.getKey())) continue;
                if ("out".equals(e.getValue()) && readOnlyFields) continue;
                if (occurs(field.type(), e.getKey())) {
                    report(sa, field.position(), e.getKey(), e.getValue(), "field");
                }
            }
        } else if (member instanceof MethodDeclarationNode method) {
            // Metodos nao declaram type-params proprios nesta versao (apenas
            // funcoes top-level) — sem sombreamento a tratar aqui.
            for (Map.Entry<String, String> e : vs.entrySet()) {
                if (localShadows.contains(e.getKey())) continue;
                if ("out".equals(e.getValue())) {
                    for (FormalParameterNode p : method.parameters()) {
                        if (occurs(p.type(), e.getKey())) {
                            report(sa, p.position(), e.getKey(), e.getValue(), "parameter");
                        }
                    }
                } else {
                    if (occurs(method.returnType(), e.getKey())) {
                        report(sa, method.position(), e.getKey(), e.getValue(), "return");
                    }
                }
            }
        } else if (member instanceof ConstructorDeclarationNode ctor) {
            for (Map.Entry<String, String> e : vs.entrySet()) {
                if (localShadows.contains(e.getKey())) continue;
                if (!"out".equals(e.getValue())) continue;
                for (FormalParameterNode p : ctor.parameters()) {
                    if (occurs(p.type(), e.getKey())) {
                        report(sa, p.position(), e.getKey(), e.getValue(), "parameter");
                    }
                }
            }
        }
    }

    /** Type-params `out`/`in` (nome → variância) de uma declaração. */
    private static Map<String, String> variances(List<String> typeParameters) {
        Map<String, String> out = new LinkedHashMap<>();
        if (typeParameters == null) return out;
        for (String tp : typeParameters) {
            String v = TypeParams.variance(tp);
            if (!v.isEmpty()) out.put(TypeParams.name(tp), v);
        }
        return out;
    }

    /** O tipo textual {@code text} menciona o identificador {@code name}? */
    private static boolean occurs(String text, String name) {
        if (text == null || name == null || name.isEmpty()) return false;
        int from = 0;
        while (true) {
            int i = text.indexOf(name, from);
            if (i < 0) return false;
            boolean leftOk = i == 0 || !WORD.matcher(text.substring(i - 1, i)).matches();
            int end = i + name.length();
            boolean rightOk = end >= text.length() || !WORD.matcher(text.substring(end, end + 1)).matches();
            if (leftOk && rightOk) return true;
            from = i + 1;
        }
    }

    private static void report(SemanticAnalyzer sa, SourcePosition pos, String name,
                               String variance, String where) {
        if (sa.diagnostics() == null) return;
        String msg = "out".equals(variance)
                ? "type parameter '" + name + "' is declared 'out' but occurs in an input position ("
                        + where + ") — an 'out' parameter is covariant, so it may appear only in"
                        + " output positions (return types, read-only components); a writable use"
                        + " would let a covariant alias store a value of the wrong type"
                : "type parameter '" + name + "' is declared 'in' but occurs in an output position ("
                        + where + ") — an 'in' parameter is contravariant, so it may appear only in"
                        + " input positions (parameters); a readable use would let a contravariant"
                        + " alias expose a value of the wrong type";
        sa.diagnostics().error(pos != null ? pos.file() : "", pos != null ? pos.line() : 0,
                pos != null ? pos.column() : 0, 0, msg, "SEM082");
    }
}
