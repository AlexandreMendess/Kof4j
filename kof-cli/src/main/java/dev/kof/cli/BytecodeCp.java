package dev.kof.cli;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Utilitários de constant-pool, descriptor JVM e condição de salto do
 * decompiler (REFACTOR-500, §140 — extraído de {@link BytecodeDecoder},
 * que estourava as 500 linhas). Mesma semântica, movimento puro: resolve
 * entradas do CP (ldc/ldc2/MethodRef/ClassRef), conta args e lê o tipo de
 * retorno de um descriptor, inverte opcode de condição e formata a lista de
 * argumentos popada da pilha. Chamado pelo decoder e por
 * {@link BytecodeStatements}/{@link BytecodeKofTypes}.
 */
final class BytecodeCp {

    private BytecodeCp() {}

    /** Pop n argumentos da pilha e devolve a lista "a, b, ..." (ou null). */
    static String callArgs(Deque<String> stack, int n) {
        List<String> args = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (stack.isEmpty()) return null;
            args.add(0, stack.pop());
        }
        return String.join(", ", args);
    }

    /**
     * Fase C (13/10, `||`): o operador NÃO-invertido — para o teste cujo JUMP
     * é o braço VERDADEIRO (a orientação do `||`, espelho do `&&`/if-else).
     */
    static String takenCond(int op) {
        return switch (op) {
            case 0x9f -> "=="; case 0xa0 -> "!="; case 0xa1 -> "<"; case 0xa2 -> ">=";
            case 0xa3 -> ">"; case 0xa4 -> "<="; case 0x99 -> "== 0"; case 0x9a -> "!= 0";
            case 0x9b -> "< 0"; case 0x9c -> ">= 0"; case 0x9d -> "> 0"; case 0x9e -> "<= 0";
            case 0xc6 -> "== null"; case 0xc7 -> "!= null";
            default -> null;
        };
    }

    static String invCond(int op) {
        return switch (op) {
            case 0x9f -> "!="; case 0xa0 -> "=="; case 0xa1 -> ">="; case 0xa2 -> "<";
            case 0xa3 -> "<="; case 0xa4 -> ">";  case 0x99 -> "!= 0"; case 0x9a -> "== 0";
            case 0x9b -> ">= 0"; case 0x9c -> "< 0"; case 0x9d -> "<= 0"; case 0x9e -> "> 0";
            // narrowing canônico da linguagem (idiom §Null safety): ifnull/ifnonnull
            case 0xc6 -> "!= null"; case 0xc7 -> "== null";
            default -> null;
        };
    }

    /**
     * ldc2_w (0x14) — só Long/Double no CP. Classificação por FORMA (lição
     * bug 62: só emitir quando o tipo não pode driftar): String.valueOf(long)
     * é sempre dígitos ([sinal]) → literal Long com sufixo `L`; o verificador
     * garante const tipo == uso. Double.toString SEMPRE traz '.'/E (mesmo
     * 100.0 → "100.0") → literal Double; NaN/Infinity ficam sem literal em
     * Kof → recusar (stub honesto).
     */
    static String ldc2(String[] cp, int idx) {
        if (idx <= 0 || idx >= cp.length || cp[idx] == null) return null;
        String e = cp[idx];
        if (e.startsWith("#")) return null;           // só String ref — nunca ldc2
        if (e.equals("NaN") || e.equals("Infinity") || e.equals("-Infinity")) return null;
        if (e.indexOf('.') >= 0 || e.indexOf('e') >= 0 || e.indexOf('E') >= 0) return e;
        return e + "L";
    }

    /** Tipo JVM resultante de um descriptor: método "(..)T" → char pós-')';
     *  campo "T"/"Ljava…;" → primeiro char. null se vazio/quebrado. */
    static String retOf(String desc) {
        if (desc == null || desc.isEmpty()) return null;
        int close = desc.lastIndexOf(')');
        if (close >= 0) return close + 1 < desc.length() ? String.valueOf(desc.charAt(close + 1)) : null;
        return String.valueOf(desc.charAt(0));
    }

    static String ldc(String[] cp, int idx) {
        if (idx <= 0 || idx >= cp.length || cp[idx] == null) return null;
        String e = cp[idx];
        if (e.startsWith("#")) {
            try {
                int ref = Integer.parseInt(e.substring(1));
                if (ref <= 0 || ref >= cp.length || cp[ref] == null) return null;
                // ESCAPAR (regra R6): a string do CP é o valor REAL; emitida
                // crua, `\b`/`\n`/`"` estouravam o lexer do .kf (prova de
                // drift 09/09: LEX002/LEX004/'\' inesperado). O escape do
                // concat (BytecodeConcat) é o canonical — reusado aqui.
                return "\"" + BytecodeConcat.escape(cp[ref]) + "\"";
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        // Constante float (CP tag 4, via ldc): o parser agora guarda o valor
        // float (ex. "3.5"), mas Kof não tem literal float inline — "3.5" é
        // Double e drifta o tipo do método (SEM010). Recusar → stub honesto
        // (irmão de Double/Long, que já caem em ldc2_w → default → null).
        if (looksLikeFloatLiteral(e)) return null;
        return e;
    }

    /** Float.toString → sempre contém '.', 'e'/'E', ou NaN/Infinity. */
    private static boolean looksLikeFloatLiteral(String s) {
        if (s.indexOf('.') >= 0 || s.indexOf('e') >= 0 || s.indexOf('E') >= 0) return true;
        return s.equals("NaN") || s.equals("Infinity") || s.equals("-Infinity");
    }

    // ── chamadas de método ───────────────────────────────────────────────

    static String[] resolveMethodRef(String[] cp, int idx) {
        if (idx <= 0 || idx >= cp.length || cp[idx] == null) return null;
        String e = cp[idx];
        if (!e.startsWith("#") || e.indexOf('#', 1) < 0) return null;
        int split = e.indexOf('#', 1);
        Integer classIdx = parseCp(e.substring(1, split));
        Integer natIdx = parseCp(e.substring(split + 1));
        if (classIdx == null || natIdx == null || classIdx >= cp.length || natIdx >= cp.length) return null;
        String classE = cp[classIdx];
        if (classE == null || !classE.startsWith("#")) return null;
        Integer nameIdx = parseCp(classE.substring(1));
        if (nameIdx == null || nameIdx >= cp.length || cp[nameIdx] == null) return null;
        String owner = cp[nameIdx];
        String nat = cp[natIdx];
        if (nat == null || !nat.startsWith("#") || nat.indexOf('#', 1) < 0) return null;
        int split2 = nat.indexOf('#', 1);
        Integer mNameIdx = parseCp(nat.substring(1, split2));
        Integer mDescIdx = parseCp(nat.substring(split2 + 1));
        if (mNameIdx == null || mDescIdx == null || mNameIdx >= cp.length || mDescIdx >= cp.length) return null;
        if (cp[mNameIdx] == null || cp[mDescIdx] == null) return null;
        return new String[]{owner, cp[mNameIdx], cp[mDescIdx]};
    }

    static Integer parseCp(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Nº de argumentos do descriptor de método. */
    static int argCount(String desc) {
        int end = desc.indexOf(')');
        int count = 0;
        int i = 1;
        while (i < end) {
            char c = desc.charAt(i);
            if (c == 'L') { i = desc.indexOf(';', i) + 1; }
            else if (c == '[') {
                while (i < end && desc.charAt(i) == '[') i++;
                if (i < end && desc.charAt(i) == 'L') i = desc.indexOf(';', i) + 1;
                else i++;
            } else { i++; }
            count++;
        }
        return count;
    }

    static String simpleOwner(String internal) {
        int s = internal.lastIndexOf('/');
        return s >= 0 ? internal.substring(s + 1) : internal;
    }

    static boolean isVoidDesc(String desc) {
        int idx = desc.indexOf(')');
        return idx >= 0 && idx + 1 < desc.length() && desc.charAt(idx + 1) == 'V';
    }

    /** Mapeia chamada de stdlib Java → idiom Kof (decompiler, TRANSLATOR-equivalente). */
    /** Owner (interno) é de plataforma JDK? (R6: new/java.X(...) e estáticas
     *  java.X(...) sem mapeamento nunca são idiomáticos Kof.) */
    static boolean isJdkOwner(String internal) {
        return internal != null && (internal.startsWith("java/") || internal.startsWith("jdk/"));
    }

    /** Class entry do CP aponta p/ owner de plataforma? */
    static boolean isJdkClass(String[] cp, int classIdx) {
        if (classIdx <= 0 || classIdx >= cp.length || cp[classIdx] == null) return false;
        String e = cp[classIdx];
        if (!e.startsWith("#")) return false;
        Integer nameIdx = parseCp(e.substring(1));
        if (nameIdx == null || nameIdx >= cp.length || cp[nameIdx] == null) return false;
        return isJdkOwner(cp[nameIdx]);
    }

    /** Resolve um nome de classe (Class CP entry) → nome simples. */
    static String resolveClassName(String[] cp, int idx) {
        if (idx <= 0 || idx >= cp.length || cp[idx] == null) return null;
        String e = cp[idx];
        if (!e.startsWith("#")) return null;
        Integer nameIdx = parseCp(e.substring(1));
        if (nameIdx == null || nameIdx >= cp.length || cp[nameIdx] == null) return null;
        return simpleOwner(cp[nameIdx]);
    }
}
