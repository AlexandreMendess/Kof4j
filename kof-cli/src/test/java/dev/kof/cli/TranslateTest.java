package dev.kof.cli;

import dev.kof.compiler.CompilationResult;
import dev.kof.compiler.CompilerDriver;
import dev.kof.compiler.Target;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * kof translate — Java subset → Kof (docs/development/TRANSLATOR.md, Fase F).
 * Static main becomes a top-level main(); println/equals map to idiomatic Kof.
 */
class TranslateTest {

    @Test
    void helloWorldMainBecomesTopLevelMain(@TempDir Path dir) throws Exception {
        String kof = Translate.translateJava("""
                public class Main {
                    public static void main(String[] args) {
                        System.out.println("Hello");
                    }
                }
                """);

        assertTrue(kof.contains("main(String[] args)"),
                "should preserve main params (corpo pode usar args):\n" + kof);
        assertTrue(kof.contains("println(\"Hello\")"), "should map println:\n" + kof);
        assertFalse(kof.contains("System.out"), "must drop System.out:\n" + kof);

        assertCompiles(dir, kof, "Hello");
    }

    @Test
    void methodsAndFieldsTranslate(@TempDir Path dir) throws Exception {
        String kof = Translate.translateJava("""
                public class Calc {
                    int total = 0;
                    public int add(int a, int b) {
                        return a + b;
                    }
                    public boolean isPositive(int x) {
                        return x > 0;
                    }
                }
                """);

        assertTrue(kof.contains("class Calc"), "should emit class Calc:\n" + kof);
        assertTrue(kof.contains("Int total"), "field should become typed field:\n" + kof);
        assertTrue(kof.contains("Int add"), "method return type should be Int:\n" + kof);
        assertTrue(kof.contains("Int add(Int a, Int b) = a + b"), "single-return method becomes expression body:\n" + kof);
        assertTrue(kof.contains("Bool isPositive"), "boolean → Bool:\n" + kof);
        assertFalse(kof.contains("public"), "must drop public modifier:\n" + kof);

        assertCompiles(dir, kof, null);
    }

    @Test
    void equalsAndControlFlowTranslate(@TempDir Path dir) throws Exception {
        String kof = Translate.translateJava("""
                public class Flow {
                    public static void main(String[] args) {
                        String s = "hello";
                        if (s.equals("hello")) {
                            System.out.println("match");
                        } else {
                            System.out.println("no");
                        }
                        int i = 0;
                        while (i < 3) {
                            i = i + 1;
                        }
                        System.out.println(i);
                    }
                }
                """);

        assertTrue(kof.contains("s == \"hello\""), "equals should become ==:\n" + kof);
        assertTrue(kof.contains("while (i < 3)"), "while loop preserved:\n" + kof);
        assertTrue(kof.contains("if ("), "if preserved:\n" + kof);

        assertCompiles(dir, kof, "match\n3");
    }

    @Test
    void staticInitBlockFoldsIntoFieldInitializer() {
        // Fase F: `static { V = 42; }` folds into the field initializer —
        // the block's value WINS over a field initializer (Java runs the
        // block after the field inits). The refusal used to be the only
        // outcome (TranslateException).
        String kof = Translate.translateJava("""
                class T {
                    static int V;
                    static int W = 1;
                    static { V = 42; W = 7; }
                    public static void main(String[] args) {
                        System.out.println(V);
                    }
                }
                """);
        assertTrue(kof.contains("static Int V = 42"), kof);
        assertTrue(kof.contains("static Int W = 7"), kof);
    }

    @Test
    void staticInitBlockRefusesNonLiteralBody() {
        // a method call in the block cannot fold without reordering
        // execution — the named refusal stays
        assertThrows(TranslateException.class, () -> Translate.translateJava("""
                class T {
                    static int V;
                    static { V = compute(); }
                    static int compute() { return 1; }
                }
                """));
    }

    @Test
    void instanceInitBlockFoldsIntoConstructors() {
        // Fase F: `{ x = 5; }` is prefixed into every constructor (Java runs
        // it before the constructor body); a class without constructors gains
        // the default one carrying the init body
        String kof = Translate.translateJava("""
                class T {
                    int x;
                    { x = 5; }
                    T(int v) { x = v; }
                }
                """);
        assertTrue(kof.contains("constructor(Int v) {"), kof);
        assertTrue(kof.indexOf("x = 5") < kof.indexOf("x = v"), kof);
    }

    @Test
    void instanceInitAfterConstructorRefuses() {
        assertThrows(TranslateException.class, () -> Translate.translateJava("""
                class T {
                    int x;
                    T() { }
                    { x = 5; }
                }
                """));
    }

    @Test
    void recordTranslates(@TempDir Path dir) throws Exception {
        String kof = Translate.translateJava("""
                public record Point(int x, int y) {
                }
                """);

        assertTrue(kof.contains("record Point(Int x, Int y)"), "record Java vira record Kof:\n" + kof);
        assertFalse(kof.contains("class Point"), "não deve virar class:\n" + kof);

        assertCompiles(dir, kof, null);
    }

    @Test
    void enumTranslates(@TempDir Path dir) throws Exception {
        String kof = Translate.translateJava("""
                public enum Color { RED, GREEN, BLUE }
                """);

        assertTrue(kof.contains("enum Color"), "enum Java vira enum Kof:\n" + kof);
        assertTrue(kof.contains("RED"), "constante RED:\n" + kof);
        assertTrue(kof.contains("GREEN"), "constante GREEN:\n" + kof);
        assertTrue(kof.contains("BLUE"), "constante BLUE:\n" + kof);

        assertCompiles(dir, kof, null);
    }

    @Test
    void ternaryTranslates(@TempDir Path dir) throws Exception {
        String kof = Translate.translateJava("""
                public class T {
                    public static int max(int a, int b) { return a > b ? a : b; }
                }
                """);

        assertTrue(kof.contains("if (a > b) a else b"),
                "ternário deve virar if-expression:\n" + kof);
    }

    @Test
    void stringLengthProperty(@TempDir Path dir) throws Exception {
        String kof = Translate.translateJava("""
                public class S {
                    public static int size(String s) { return s.length(); }
                }
                """);

        assertTrue(kof.contains("s.length"), "length() vira propriedade .length:\n" + kof);
        assertFalse(kof.contains("s.length()"), "não deve manter length():\n" + kof);
    }

    @Test
    void lambdaTranslates(@TempDir Path dir) throws Exception {
        String kof = Translate.translateJava("""
                interface F { int run(int n); }
                class L {
                    static int go(int n) { F f = (int x) -> x + 1; return f.run(n); }
                }
                """);

        assertTrue(kof.contains("(x: Int) -> x + 1"), "lambda tipada deve traduzir:\n" + kof);
    }

    @Test
    void boxedTypesMapToPrimitives(@TempDir Path dir) throws Exception {
        String kof = Translate.translateJava("""
                import java.util.List;
                public class G {
                    List<Integer> integers;
                    public Integer get(List<Boolean> flags) { return integers.get(0); }
                }
                """);

        assertTrue(kof.contains("List<Int> integers"), "Integer vira Int:\n" + kof);
        assertTrue(kof.contains("Int get"), "Integer retorno vira Int:\n" + kof);
        assertTrue(kof.contains("List<Bool>"), "Boolean vira Bool:\n" + kof);
    }

    @Test
    void switchStatementTranslates(@TempDir Path dir) throws Exception {
        String kof = Translate.translateJava("""
                public class SW {
                    public static void main(String[] args) {
                        int x = 1;
                        switch (x) {
                            case 1:
                                System.out.println("one");
                                break;
                            case 2:
                                System.out.println("two");
                                break;
                            default:
                                System.out.println("other");
                                break;
                        }
                        String s = "b";
                        switch (s) {
                            case "a", "b":
                                System.out.println("ab");
                                break;
                            default:
                                System.out.println("zz");
                                break;
                        }
                    }
                }
                """);

        assertTrue(kof.contains("switch (x) { case 1: println(\"one\") case 2: println(\"two\") default: println(\"other\") }"),
                "switch Java deve virar switch Kof statement (antes: expected ';' but found '('):\n" + kof);
        assertFalse(kof.contains("break"), "break Java é opcional em Kof (sem fallthrough) — dropar:\n" + kof);
        assertTrue(kof.contains("case \"a\": case \"b\": println(\"ab\")"),
                "labels multiple init `case \"a\", \"b\":` viram cases separados:\n" + kof);

        assertCompiles(dir, kof, "one\nab");
    }

    @Test
    void tryCatchFinallyTranslates(@TempDir Path dir) throws Exception {
        String kof = Translate.translateJava("""
                public class ER {
                    static void boom(String k) {
                        throw new RuntimeException("boom " + k);
                    }
                    public static void main(String[] args) {
                        try {
                            boom("x");
                            System.out.println("no");
                        } catch (RuntimeException e) {
                            System.out.println("caught");
                        } finally {
                            System.out.println("done");
                        }
                        try {
                            System.out.println("t2");
                        } catch (IllegalStateException | IllegalArgumentException e) {
                            System.out.println("multi");
                        }
                    }
                }
                """);

        assertTrue(kof.contains("try { boom(\"x\") println(\"no\") } catch (String e) { println(\"caught\") } finally { println(\"done\") }"),
                "try/catch/finally Java deve virar try/catch(String)/finally Kof (antes: expected ';' but found '{'):\n" + kof);
        assertTrue(kof.contains("throw \"boom \" + k"),
                "`throw new RuntimeException(msg)` deve virar `throw msg` (exceções são Strings, errors.md; antes: found 'new'):\n" + kof);
        assertTrue(kof.contains("} catch (String e) { println(\"multi\") }"),
                "multi-catch `catch (A | B e)` vira catch único (Kof sem união):\n" + kof);
        assertFalse(kof.contains("RuntimeException("), "sem construtor de exceção no output:\n" + kof);

        assertCompiles(dir, kof, "caught\ndone\nt2");
    }

    @Test
    void constructorTranslatesWithBody(@TempDir Path dir) throws Exception {
        String kof = Translate.translateJava("""
                public class User {
                    String name;
                    int age;
                    public User(String n, int a) {
                        this.name = n;
                        this.age = a;
                    }
                    public String greet() { return "Hello " + name; }
                    public static void main(String[] args) {
                        User u = new User("Mel", 26);
                        System.out.println(u.greet());
                        System.out.println(u.age);
                    }
                }
                """);

        assertTrue(kof.contains("constructor(String n, Int a) {"),
                "construtor Java deve virar constructor Kof (antes: loop infinito/parse error):\n" + kof);
        assertTrue(kof.contains("this.name = n"),
                "corpo do construtor NÃO pode ser descartado (era bug latente `{}`):\n" + kof);
        assertTrue(kof.contains("var u = User(\"Mel\", 26)"), "new X(...) → X(...):\n" + kof);

        assertCompiles(dir, kof, "Hello Mel\n26");
    }

    @Test
    void genericsTranslate(@TempDir Path dir) throws Exception {
        String kof = Translate.translateJava("""
                class Box<T> {
                    T value;
                    public Box(T v) { this.value = v; }
                    public T get() { return value; }
                }
                class GM {
                    static <T> T id(T x) { return x; }
                    public static void main(String[] args) {
                        Box<Integer> b = new Box<Integer>(5);
                        System.out.println(b.get());
                        System.out.println(id(7));
                    }
                }
                """);

        assertTrue(kof.contains("class Box<T>"), "classe genérica Java → Kof (antes: expected class/... found 'T'):\n" + kof);
        assertTrue(kof.contains("T id<T>(T x)"), "método genérico `<T> T id` → `T id<T>`:\n" + kof);
        assertTrue(kof.contains("var b = Box(5)"),
                "`new Box<Integer>(5)` → `Box(5)` (Kof infere o tipo):\n" + kof);

        assertCompiles(dir, kof, "5\n7");
    }

    @Test
    void enumMultiDeclTranslate(@TempDir Path dir) throws Exception {
        String kof = Translate.translateJava("""
                enum Color {
                    RED, GREEN;
                }
                public class ED {
                    public static void main(String[] args) {
                        int x = 1, y = 2;
                        System.out.println(x + y);
                    }
                }
                """);

        assertTrue(kof.contains("enum Color { RED, GREEN }"),
                "enum com `;` de fechamento (method with no body) → só constantes:\n" + kof);
        assertTrue(kof.contains("var x = 1 var y = 2"),
                "multi-declaração `int x = 1, y = 2` → statements separados (antes: expected ';' but found ','):\n" + kof);

        assertCompiles(dir, kof, "3");
    }

    @Test
    void enumBodyIsHonestGap() {
        // Enum com corpo (campos/métodos/construtor): Kof enum é SÓ
        // constantes — antes o corpo era pulado em SILÊNCIO (perda de
        // comportamento, Q7) → gap honesto R6 (Q4 13/09).
        TranslateException e = assertThrows(TranslateException.class, () ->
                Translate.translateJava("""
                        enum Color {
                            RED, GREEN;
                            int code() { return 1; }
                        }
                        """));
        assertTrue(e.getMessage().contains("enum with a body") && e.getMessage().contains("manual review"),
                "enum with a body → gap explícito (R6), foi: " + e.getMessage());
    }

    @Test
    void recordBodyIsHonestGap() {
        // Record com corpo (construtor compacto/accessors/métodos): Kof
        // record é SÓ componentes — antes era pulado em SILÊNCIO (validação
        // sumia, Q7) → gap honesto R6. Corpo VAZIO `{}` segue ok.
        TranslateException e = assertThrows(TranslateException.class, () ->
                Translate.translateJava("""
                        record P(int x) {
                            P { if (x < 0) throw new RuntimeException("neg"); }
                        }
                        """));
        assertTrue(e.getMessage().contains("record with a body") && e.getMessage().contains("manual review"),
                "record with a body → gap explícito (R6), foi: " + e.getMessage());
    }

    @Test
    void abstractMethodIsHonestGap() {
        // Método method with no body (`abstract`) em classe: Kof não tem — antes era
        // dropado em SILÊNCIO (a chamada virava SEM011) → gap honesto R6.
        TranslateException e = assertThrows(TranslateException.class, () ->
                Translate.translateJava("""
                        abstract class A {
                            abstract int f();
                            int g() { return f(); }
                        }
                        """));
        assertTrue(e.getMessage().contains("method with no body") && e.getMessage().contains("manual review"),
                "método abstract → gap explícito (R6), foi: " + e.getMessage());
    }

    @Test
    void qualifiedTypeNamesAreStripped(@TempDir Path dir) throws Exception {
        String kof = Translate.translateJava("""
                public class QN {
                    static void f(java.util.Map<String, java.util.List<Integer>> m) { }
                    public static void main(String[] args) {
                        System.out.println("ok");
                    }
                }
                """);

        assertTrue(kof.contains("Map<String, List<Int>>"),
                "qualified type `java.util.Map` → `Map` (builtin Kof; antes: expected ')' but found 'util'):\n" + kof);

        assertCompiles(dir, kof, "ok");
    }

    @Test
    void bitwiseCompoundAssignmentsTranslate(@TempDir Path dir) throws Exception {
        String kof = Translate.translateJava("""
                public class BCA {
                    public static void main(String[] args) {
                        int x = 6;
                        x &= 3;
                        x |= 8;
                        x ^= 1;
                        x <<= 2;
                        x >>= 1;
                        x >>>= 1;
                        System.out.println(x);
                    }
                }
                """);

        for (String op : new String[]{"&=", "|=", "^=", "<<=", ">>=", ">>>="}) {
            assertTrue(kof.contains(op),
                    "composto `" + op + "` preservado (antes: expected ';'):\n" + kof);
        }

        // x=6; &=3 → 2; |=8 → 10; ^=1 → 11; <<=2 → 44; >>=1 → 22; >>>=1 → 11
        assertCompiles(dir, kof, "11");
    }

    @Test
    void prefixIncrementTranslate(@TempDir Path dir) throws Exception {
        String kof = Translate.translateJava("""
                public class Pre {
                    public static void main(String[] args) {
                        int x = 1;
                        int y = ++x;
                        int z = --x;
                        System.out.println(y);
                        System.out.println(z);
                    }
                }
                """);

        assertTrue(kof.contains("++x"), "prefixo `++x` preservado (antes: expected ';'):\n" + kof);
        assertTrue(kof.contains("--x"), "prefixo `--x` preservado:\n" + kof);

        assertCompiles(dir, kof, "2\n1");
    }

    @Test
    void qualifiedLocalTypeTranslates(@TempDir Path dir) throws Exception {
        String kof = Translate.translateJava("""
                public class QLT {
                    public static void main(String[] args) {
                        java.util.List<Integer> xs;
                        java.util.Map<String, Integer> m;
                        java.lang.String s = "ok";
                        System.out.println(s);
                    }
                }
                """);

        assertFalse(kof.contains("java.util"),
                "pacote qualificado descartado na decl local (antes: parse error):\n" + kof);
        assertFalse(kof.contains("java.lang"),
                "pacote qualificado descartado:\n" + kof);

        assertCompiles(dir, kof, "ok");
    }

    @Test
    void varLocalTranslates(@TempDir Path dir) throws Exception {
        String kof = Translate.translateJava("""
                public class V {
                    public static void main(String[] args) {
                        var x = 1;
                        var s = "hi";
                        System.out.println(x);
                        System.out.println(s);
                    }
                }
                """);

        assertTrue(kof.contains("var x = 1"),
                "`var x = 1` Java → `var x = 1` Kof (antes: expected ';' but found 'x'):\n" + kof);

        assertCompiles(dir, kof, "1\nhi");
    }

    @Test
    void staticFieldsTranslateAndQualifyInHoistedFns(@TempDir Path dir) throws Exception {
        // Java `static` fields → `static` em Kof. Métodos `static` (e main)
        // viram funções top-level (hoisted) → refs a campo estático ficam fora
        // de escopo; o translator qualifica `X` → `Classe.X` (Kof aceita).
        String kof = Translate.translateJava("""
                public class SF {
                    static final int X = 5;
                    static String S = "hi";
                    int get() { return X; }
                    public static void main(String[] args) {
                        System.out.println(new SF().get());
                        System.out.println(S);
                        System.out.println(X);
                    }
                }
                """);
        assertTrue(kof.contains("static Int X = 5"), "campo estático emitido:\n" + kof);
        assertTrue(kof.contains("println(SF.S)"), "ref hoisted qualificada:\n" + kof);
        assertTrue(kof.contains("println(SF.X)"), "ref hoisted qualificada:\n" + kof);
        assertCompiles(dir, kof, "5\nhi\n5");
    }

    @Test
    void interfaceDefaultMethodIsHonestGap() {
        // Kof NÃO tem default method: o corpo em interface é ignorado e o
        // implementador falha com SEM043 (re-verificado 13/09 no binário — a
        // nota anterior "Kof aceita corpo" era verificação falsa, Q5).
        TranslateException e = assertThrows(TranslateException.class, () ->
                Translate.translateJava("""
                        public interface I {
                            default int f() { return 1; }
                            int g();
                        }
                        """));
        assertTrue(e.getMessage().contains("manual review"),
                "default method sem equivalente Kof → gap explícito (R6), foi: " + e.getMessage());
    }

    @Test
    void interfaceAbstractSignatureTranslates(@TempDir Path dir) throws Exception {
        String kof = Translate.translateJava("""
                public interface I {
                    int g();
                }
                public class C implements I {
                    public int g() { return 7; }
                }
                """);
        assertTrue(kof.contains("Int g(): Int"),
                "assinatura abstrata `Type m(): Type`:\n" + kof);
        assertCompiles(dir, kof + "\nmain() { println(C().g()) }", "7");
    }

    @Test
    void interfaceConstantIsHonestGap() {
        TranslateException e = assertThrows(TranslateException.class, () ->
                Translate.translateJava("""
                        public interface I {
                            int X = 1;
                            int g();
                        }
                        """));
        assertTrue(e.getMessage().contains("interface constant") && e.getMessage().contains("manual review"),
                "interface constant não resolvível em Kof (SEM025) → gap explícito (R6), foi: " + e.getMessage());
    }

    @Test
    void constructorDelegationIsHonestGap() {
        // Java `this(...)` delega ao outro construtor; Kof não tem (probe:
        // `variable 'this' is not a function` = SEM015) → gap honesto (R6).
        TranslateException e = assertThrows(TranslateException.class, () ->
                Translate.translateJava("""
                        public class CD {
                            int x;
                            CD(int x) { this.x = x; }
                            CD() { this(5); }
                        }
                        """));
        assertTrue(e.getMessage().contains("this(...)") && e.getMessage().contains("manual review"),
                "delegação `this(...)` sem equivalente Kof → gap explícito (R6), foi: " + e.getMessage());
    }

    @Test
    void wildcardGenericIsHonestGap() {
        // Kof rejeita wildcard genérico (PARSE086: use tipo concreto ou `T?`)
        // → gap honesto em vez de emitir `? extends Number` (parse error).
        TranslateException e = assertThrows(TranslateException.class, () ->
                Translate.translateJava("""
                        import java.util.List;
                        public class WG {
                            void go(List<? extends Number> xs) { }
                        }
                        """));
        assertTrue(e.getMessage().contains("wildcard") && e.getMessage().contains("manual review"),
                "wildcard genérico sem equivalente Kof → gap explícito (R6), foi: " + e.getMessage());
    }

    @Test
    void lambdaBlockBodyTranslates(@TempDir Path dir) throws Exception {
        // Lambda Java com corpo em BLOCO `() -> { ... }` → Kof aceita bloco
        // (antes: parse error `expected ';' but found ...`, bug latente Q4).
        // O call-site Java (`f.applyAsInt`) não tem equivalente Kof; a prova de
        // que o BLOCO emitido é Kof válido é chamar a lambda como função.
        // (A local `y` fica declarada mas o `return` usa expressão: retornar a
        // local dispara bug do COMPILADOR §174 — fora da lane do translator.)
        String kof = Translate.translateJava("""
                public class LB {
                    public static void main(String[] args) {
                        int base = 10;
                        java.util.function.IntUnaryOperator f = (int n) -> { int y = n + 1; return n + 1; };
                        System.out.println(f.applyAsInt(3) + base);
                    }
                }
                """);
        assertTrue(kof.contains("-> {"),
                "corpo de lambda em bloco preservado:\n" + kof);
        assertTrue(kof.contains("var y = n + 1"),
                "statements do bloco preservados:\n" + kof);
        assertCompiles(dir, kof.replace("f.applyAsInt(3)", "f(3)"), "14");
    }

    @Test
    void methodReferenceIsHonestGap() {
        // `Tipo::metodo` / `obj::metodo` — Kof não tem method reference (só
        // lambda); antes dava `expected ')' but found ':'` confuso (Q4 13/09).
        TranslateException e = assertThrows(TranslateException.class, () ->
                Translate.translateJava("""
                        import java.util.List;
                        public class MR {
                            int f() {
                                List<String> xs = List.of("a");
                                xs.forEach(System.out::println);
                                return xs.size();
                            }
                        }
                        """));
        assertTrue(e.getMessage().contains("method reference") && e.getMessage().contains("manual review"),
                "method reference sem equivalente Kof → gap explícito (R6), foi: " + e.getMessage());
    }

    @Test
    void textBlockIsHonestGap() {
        // Text block `"""..."""` — Kof não tem; antes o lexer lia `""` vazio e
        // reabria (parse error confuso) — Q4 13/09.
        TranslateException e = assertThrows(TranslateException.class, () ->
                Translate.translateJava("""
                        public class TB {
                            String s() {
                                return \"\"\"
                                    hello
                                    \"\"\";
                            }
                        }
                        """));
        assertTrue(e.getMessage().contains("text block") && e.getMessage().contains("manual review"),
                "text block sem equivalente Kof → gap explícito (R6), foi: " + e.getMessage());
    }

    @Test
    void instanceofBindingPatternIsHonestGap() {
        // `o instanceof String s` (binding) — Kof não tem binding de pattern;
        // antes dava `expected ')' but found 's'` confuso (Q4 13/09).
        TranslateException e = assertThrows(TranslateException.class, () ->
                Translate.translateJava("""
                        public class IP {
                            int f(Object o) {
                                if (o instanceof String s) { return s.length(); }
                                return 0;
                            }
                        }
                        """));
        assertTrue(e.getMessage().contains("pattern matching") && e.getMessage().contains("manual review"),
                "binding pattern sem equivalente Kof → gap explícito (R6), foi: " + e.getMessage());
    }

    @Test
    void qualifiedTypeInExpressionIsHonestGap() {
        // `java.util.List.of(...)` em posição de expressão — o translator
        // ignora imports e não resolve FQN; antes emitia Kof inválido
        // (`java` undefined = SEM011) — Q4 13/09.
        TranslateException e = assertThrows(TranslateException.class, () ->
                Translate.translateJava("""
                        public class QT {
                            int f() {
                                for (String s : java.util.List.of("a")) { }
                                return 1;
                            }
                        }
                        """));
        assertTrue(e.getMessage().contains("qualified type") && e.getMessage().contains("manual review"),
                "qualified type em expressão → gap explícito (R6), foi: " + e.getMessage());
    }

    @Test
    void switchExpressionTranslates(@TempDir Path dir) throws Exception {
        // `return switch (x) { case 1 -> 10; default -> 0; }` → switch-expr
        // Kof (`case L -> expr`); antes `expected ';' but found '{'` (Q4).
        // Multi-label `case 1, 2 ->` expande em cases separados; a forma
        // `case L: yield v;` vira `case L -> v`.
        String kof = Translate.translateJava("""
                public class SW {
                    int f(int x) {
                        return switch (x) {
                            case 1, 2 -> 10;
                            default -> 0;
                        };
                    }
                }
                """);
        assertTrue(kof.contains("switch (x)") && kof.contains("case 1 -> 10") && kof.contains("case 2 -> 10"),
                "switch-expressão + multi-label:\n" + kof);
        assertCompiles(dir, kof + "\nmain() { println(SW().f(2)); println(SW().f(9)) }", "10\n0");

        String yieldKof = Translate.translateJava("""
                public class YS {
                    int f(int x) {
                        return switch (x) { case 1: yield 7; default: yield 0; };
                    }
                }
                """);
        assertTrue(yieldKof.contains("case 1 -> 7"), "forma `yield` → `case L -> expr`:\n" + yieldKof);
    }

    @Test
    void jdkStaticImportIsHonestGap() {
        // `import static java.lang.Math.max` + `max(3,4)`: Kof não mapeia a
        // stdlib JDK; antes emitia `max(3, 4)` (SEM011) silenciosamente — Q4.
        TranslateException e = assertThrows(TranslateException.class, () ->
                Translate.translateJava("""
                        import static java.lang.Math.max;
                        public class SI {
                            int f() { return max(3, 4); }
                        }
                        """));
        assertTrue(e.getMessage().contains("import static") && e.getMessage().contains("manual review"),
                "import static de JDK → gap explícito (R6), foi: " + e.getMessage());
    }

    @Test
    void mathReceiverIsHonestGap() {
        // `Math.max(3,4)` / `Math.PI`: antes emitia `Math.max(...)` / `Math.PI`
        // (Kof inválido = SEM011 silencioso). Kof expõe `math.*`, mas
        // `math.min/max/abs` são Int-only e o translator não tem tipos p/
        // escolher o overload → gap honesto R6 (Q4 13/09).
        TranslateException call = assertThrows(TranslateException.class, () ->
                Translate.translateJava("""
                        public class MM {
                            int f() { return Math.max(3, 4); }
                        }
                        """));
        assertTrue(call.getMessage().contains("Math.max") && call.getMessage().contains("manual review"),
                "Math.<fn> → gap explícito (R6), foi: " + call.getMessage());

        TranslateException constant = assertThrows(TranslateException.class, () ->
                Translate.translateJava("""
                        public class MP {
                            double f() { return Math.PI; }
                        }
                        """));
        assertTrue(constant.getMessage().contains("Math.PI") && constant.getMessage().contains("manual review"),
                "Math.<const> → gap explícito (R6), foi: " + constant.getMessage());
    }

    @Test
    void ownStaticImportPassesThrough() throws Exception {
        // `import static mypkg.Util.max` + `max(3,4)`: o static method vira
        // função top-level Kof, então a chamada nua resolve — não é gap.
        String kof = Translate.translateJava("""
                import static mypkg.Util.max;
                public class OS {
                    int f() { return max(3, 4); }
                }
                """);
        assertTrue(kof.contains("max(3, 4)"), "static import próprio passa:\n" + kof);
    }

    @Test
    void leadingDotLiteralIsNormalized(@TempDir Path dir) throws Exception {
        // `.5` (Java) → `0.5` (Kof exige o zero; `.5` é PARSE041) — Q4 13/09.
        String kof = Translate.translateJava("""
                public class LD {
                    double f() { return .5; }
                }
                """);
        assertTrue(kof.contains("0.5"), "literal `.5` normalizado p/ `0.5`:\n" + kof);
        assertCompiles(dir, kof + "\nmain() { println(LD().f()) }", "0.5");
    }

    @Test
    void bitComplementTranslates(@TempDir Path dir) throws Exception {
        // `~x` (complemento bit a bit): Kof não tem `~` (PARSE041). A
        // identidade `~x == -x - 1` é exata em complemento de dois → emite
        // `(-x - 1)`. Antes o lexer DROPAVA o `~` em silêncio (output
        // truncado) — bug latente Q4 13/09.
        String kof = Translate.translateJava("""
                public class BC {
                    int f(int x) { return ~x; }
                }
                """);
        assertTrue(kof.contains("(-x - 1)"), "~x → (-x - 1):\n" + kof);
        assertCompiles(dir, kof + "\nmain() { println(BC().f(5)); println(BC().f(0)) }", "-6\n-1");
    }

    @Test
    void emptyStatementIsSkipped(@TempDir Path dir) throws Exception {
        // Instrução vazia Java (`;`) → descartada (antes: `expected ';' but
        // found 'return'` confuso) — Q4 13/09.
        String kof = Translate.translateJava("""
                public class ES {
                    int f() { ; ; return 1; }
                }
                """);
        assertCompiles(dir, kof + "\nmain() { println(ES().f()) }", "1");
    }

    @Test
    void localClassIsHonestGap() {
        // Classe LOCAL dentro de método — Kof não tem tipos aninhados
        // (SEM042) → gap honesto R6 (antes: `expected ';' but found 'B'`).
        TranslateException e = assertThrows(TranslateException.class, () ->
                Translate.translateJava("""
                        public class LC {
                            int f() {
                                class B { int g() { return 1; } }
                                return 0;
                            }
                        }
                        """));
        assertTrue(e.getMessage().contains("LOCAL") && e.getMessage().contains("manual review"),
                "classe local → gap explícito (R6), foi: " + e.getMessage());
    }

    @Test
    void synchronizedBlockIsHonestGap() {
        // `synchronized (obj) { ... }` — Kof não tem monitor explícito
        // (concorrência é `spawn`/`await`) → gap honesto R6 (antes:
        // `expected ';' but found '{'` confuso) — Q4 13/09.
        TranslateException e = assertThrows(TranslateException.class, () ->
                Translate.translateJava("""
                        public class Sy {
                            int f(Object o) {
                                synchronized (o) { return 1; }
                            }
                        }
                        """));
        assertTrue(e.getMessage().contains("synchronized") && e.getMessage().contains("manual review"),
                "bloco synchronized → gap explícito (R6), foi: " + e.getMessage());
    }

    @Test
    void mainArgsArePreserved(@TempDir Path dir) throws Exception {
        // `main(String[] args)` tinha os params DESCARTADOS (`main()`), mas o
        // corpo podia referenciar `args` → Kof inválido (SEM011 silencioso).
        // Kof aceita `main(String[] args)` (verificado no binário) — Q4.
        String kof = Translate.translateJava("""
                public class MA {
                    public static void main(String[] args) {
                        System.out.println(args.length);
                    }
                }
                """);
        assertTrue(kof.contains("main(String[] args)"), "params de main preservados:\n" + kof);
        assertCompiles(dir, kof, "0");
    }

    @Test
    void annotationsAreDiscarded(@TempDir Path dir) throws Exception {
        String kof = Translate.translateJava("""
                @Deprecated
                public class An {
                    @Override
                    public String toString() { return "x"; }
                    @SuppressWarnings("unchecked")
                    public void f() { System.out.println("f"); }
                    public static void main(String[] args) {
                        An a = new An();
                        System.out.println(a.toString());
                        a.f();
                    }
                }
                """);
        assertTrue(kof.contains("class An"),
                "anotação de tipo descartada (antes: expected class... found '@'):\n" + kof);
        assertTrue(kof.contains("String toString()"),
                "anotação de método descartada:\n" + kof);
        assertFalse(kof.contains("@"), "nenhuma anotação no output:\n" + kof);

        assertCompiles(dir, kof, "x\nf");
    }

    @Test
    void assertTranslates(@TempDir Path dir) throws Exception {
        String kof = Translate.translateJava("""
                public class AS {
                    public static void main(String[] args) {
                        int x = 1;
                        assert x > 0;
                        assert x > 0 : "neg";
                        System.out.println(x);
                    }
                }
                """);

        assertTrue(kof.contains("assert(x > 0)"),
                "`assert cond;` → `assert(cond)` Kof (antes: expected ';' but found 'x'):\n" + kof);
        assertTrue(kof.contains("assert(x > 0, \"neg\")"),
                "`assert cond : msg;` → `assert(cond, msg)`:\n" + kof);

        assertCompiles(dir, kof, "1");
    }

    @Test
    void forMultipleInitIncrIsHonestGap() {
        TranslateException e = assertThrows(TranslateException.class, () ->
                Translate.translateJava("""
                        public class FM {
                            public static void main(String[] args) {
                                for (int i = 0, j = 3; i < j; i++, j--) { System.out.println(i); }
                            }
                        }
                        """));
        assertTrue(e.getMessage().contains("multiple init") && e.getMessage().contains("manual review"),
                "`for` com vírgula sem equivalente Kof → gap explícito (R6), foi: " + e.getMessage());
    }

    @Test
    void bitwiseAndShiftTranslate(@TempDir Path dir) throws Exception {
        String kof = Translate.translateJava("""
                public class BS {
                    public static void main(String[] args) {
                        int x = 6;
                        int y = 3;
                        System.out.println(x & y);
                        System.out.println(x | y);
                        System.out.println(x ^ y);
                        System.out.println(x << 2);
                        System.out.println(x >> 1);
                        System.out.println(x >>> 1);
                        System.out.println(-8 >>> 1);
                    }
                }
                """);

        assertTrue(kof.contains("x & y"), "bitwise AND preservado (antes: operador dropado silenciosamente):\n" + kof);
        assertTrue(kof.contains("x | y"), "bitwise OR preservado:\n" + kof);
        assertTrue(kof.contains("x ^ y"), "bitwise XOR preservado (antes: dropado):\n" + kof);
        assertTrue(kof.contains("x << 2"), "shift left preservado (antes: dropado):\n" + kof);
        assertTrue(kof.contains("x >> 1"), "shift right preservado (antes: dropado):\n" + kof);
        assertTrue(kof.contains("x >>> 1"), "unsigned shift preservado:\n" + kof);

        assertCompiles(dir, kof, "2\n7\n5\n24\n3\n3\n2147483644");
    }

    @Test
    void parenthesesPreservePrecedence(@TempDir Path dir) throws Exception {
        String kof = Translate.translateJava("""
                public class P {
                    public static void main(String[] args) {
                        int x = (1 + 2) * 3;
                        System.out.println(x);
                        System.out.println(-(1 + 2));
                    }
                }
                """);

        assertTrue(kof.contains("var x = (1 + 2) * 3"),
                "parênteses NÃO podem ser descartados (mudam a semântica: 9 vs 7) — bug latente 13/09:\n" + kof);
        assertTrue(kof.contains("-(1 + 2)"),
                "agrupamento sob unário preservado:\n" + kof);

        assertCompiles(dir, kof, "9\n-3");
    }

    @Test
    void interfaceExtendsTranslates(@TempDir Path dir) throws Exception {
        String kof = Translate.translateJava("""
                interface A { void g(); }
                interface B extends A { void f(); }
                class C implements B {
                    public void g() { System.out.println("g"); }
                    public void f() { System.out.println("f"); }
                    public static void main(String[] args) {
                        C c = new C();
                        c.g();
                        c.f();
                    }
                }
                """);

        assertTrue(kof.contains("interface B extends A"),
                "interface Java `extends A` → Kof (antes: expected '{' but found 'extends'):\n" + kof);

        assertCompiles(dir, kof, "g\nf");
    }

    @Test
    void varargsAndNestedTypeAreHonestGaps() {
        TranslateException varargs = assertThrows(TranslateException.class, () ->
                Translate.translateJava("""
                        public class V {
                            static int sum(int... xs) { return xs.length; }
                        }
                        """));
        assertTrue(varargs.getMessage().contains("varargs") && varargs.getMessage().contains("manual review"),
                "varargs `T...` sem equivalente Kof → gap explícito (R6), foi: " + varargs.getMessage());

        TranslateException nested = assertThrows(TranslateException.class, () ->
                Translate.translateJava("""
                        public class N {
                            static class Inner { int x = 1; }
                        }
                        """));
        assertTrue(nested.getMessage().contains("nested type") && nested.getMessage().contains("manual review"),
                "nested type sem equivalente Kof (SEM042) → gap explícito (R6), foi: " + nested.getMessage());

        TranslateException twr = assertThrows(TranslateException.class, () ->
                Translate.translateJava("""
                        public class T {
                            static void f() {
                                try (java.io.StringReader r = new java.io.StringReader("x")) {
                                    System.out.println(r.read());
                                }
                            }
                        }
                        """));
        assertTrue(twr.getMessage().contains("try-with-resources") && twr.getMessage().contains("manual review"),
                "try-with-resources sem equivalente Kof → gap explícito (R6), foi: " + twr.getMessage());

        TranslateException fqn = assertThrows(TranslateException.class, () ->
                Translate.translateJava("""
                        public class Q {
                            static Object make() { return new java.util.ArrayList<String>(); }
                        }
                        """));
        assertTrue(fqn.getMessage().contains("qualified type") && fqn.getMessage().contains("manual review"),
                "qualified type não resolvido → gap explícito (R6), foi: " + fqn.getMessage());

        TranslateException label = assertThrows(TranslateException.class, () ->
                Translate.translateJava("""
                        public class L {
                            public static void main(String[] args) {
                                outer: for (int i = 0; i < 3; i++) {
                                    for (int j = 0; j < 3; j++) { break outer; }
                                }
                            }
                        }
                        """));
        assertTrue(label.getMessage().contains("labeled") && label.getMessage().contains("manual review"),
                "labeled statement sem equivalente Kof → gap explícito (R6), foi: " + label.getMessage());

        TranslateException anon = assertThrows(TranslateException.class, () ->
                Translate.translateJava("""
                        public class A {
                            public static void main(String[] args) {
                                Runnable r = new Runnable() {
                                    public void run() { System.out.println("x"); }
                                };
                                r.run();
                            }
                        }
                        """));
        assertTrue(anon.getMessage().contains("anonymous class") && anon.getMessage().contains("manual review"),
                "anonymous class sem equivalente Kof → gap explícito (R6), foi: " + anon.getMessage());
    }

    @Test
    void throwsClauseIsDropped(@TempDir Path dir) throws Exception {
        String kof = Translate.translateJava("""
                public class TH {
                    static void fail() throws java.io.IOException {
                        throw new RuntimeException("boom");
                    }
                    public static void main(String[] args) {
                        try {
                            fail();
                        } catch (RuntimeException e) {
                            System.out.println("caught");
                        }
                    }
                }
                """);

        assertTrue(kof.contains("void fail() {"),
                "`throws IOException` deve ser descartado (Kof não declara throws; antes: expected '{' but found 'throws'):\n" + kof);
        assertFalse(kof.contains("throws"), "sem cláusula throws no output:\n" + kof);
        assertTrue(kof.contains("throw \"boom\""), "throw new Exc(msg) → throw msg:\n" + kof);

        assertCompiles(dir, kof, "caught");
    }

    @Test
    void castAndInstanceofTranslate(@TempDir Path dir) throws Exception {
        String kof = Translate.translateJava("""
                public class CI {
                    public static void main(String[] args) {
                        Object o = "x";
                        String s = (String) o;
                        System.out.println(s);
                        if (o instanceof String) {
                            System.out.println("is-str");
                        }
                        double d = 3.5;
                        int n = (int) d;
                        System.out.println(n);
                    }
                }
                """);

        assertTrue(kof.contains("var s = o as String"),
                "`(String) o` deve virar `o as String` (antes: expected ';' but found 'o'):\n" + kof);
        assertTrue(kof.contains("o instanceof String"),
                "instanceof preservado (Kof tem nativo; antes: expected ')' but found 'instanceof'):\n" + kof);
        assertTrue(kof.contains("var n = d as Int"),
                "cast primitivo `(int) d` → `d as Int`:\n" + kof);

        assertCompiles(dir, kof, "x\nis-str\n3");
    }

    @Test
    void arrayDeclarationTranslates(@TempDir Path dir) throws Exception {
        String kof = Translate.translateJava("""
                public class AR {
                    public static void main(String[] args) {
                        int[] xs = new int[3];
                        xs[0] = 10;
                        for (int i = 0; i < xs.length; i++) {
                            System.out.println(xs[i]);
                        }
                    }
                }
                """);

        assertTrue(kof.contains("var xs = new Int[3]"),
                "new int[3] deve virar new Int[3] (bug latente: saía `new Int[]]`, PARSE041):\n" + kof);
        assertFalse(kof.contains("[]]"), "não pode sobrar `[]]` do size consumido errado:\n" + kof);
        assertTrue(kof.contains("for (var i = 0; i < xs.length; i++)"),
                "for C-style preservado (Kof suporta):\n" + kof);

        assertCompiles(dir, kof, "10\n0\n0");
    }

    @Test
    void arrayInitializerIsHonestGap() {
        TranslateException e = assertThrows(TranslateException.class, () ->
                Translate.translateJava("""
                        public class AI {
                            public static void main(String[] args) {
                                int[] xs = {1, 2, 3};
                                System.out.println(xs[0]);
                            }
                        }
                        """));
        assertTrue(e.getMessage().contains("manual review"),
                "array initializer `{...}` sem equivalente Kof → gap explícito (R6), foi: " + e.getMessage());

        // Campo (não-local): mesmo gap — antes o output saía TRUNCADO
        // (`Int[] xs = {`) = Kof inválido (bug latente achado 13/09, Q4).
        TranslateException e2 = assertThrows(TranslateException.class, () ->
                Translate.translateJava("""
                        public class AF {
                            int[] xs = {1, 2, 3};
                            int first() { return xs[0]; }
                        }
                        """));
        assertTrue(e2.getMessage().contains("manual review"),
                "array initializer em CAMPO também é gap honesto (R6), foi: " + e2.getMessage());
    }

    @Test
    void instanceInitializerBlockFoldsIntoDefaultConstructor() {
        // Bloco de instância `{ ... }` roda antes do construtor — Fase F: o
        // corpo é prefixado no construtor (ou no construtor default gerado),
        // NUNCA pulado em silêncio (R6): toda construção de II imprime "init"
        // no Java e imprime "init" no Kof traduzido.
        String kof = Translate.translateJava("""
                        public class II {
                            { System.out.println("init"); }
                            void m() { System.out.println(1); }
                        }
                        """);
        assertTrue(kof.contains("constructor() {"), kof);
        assertTrue(kof.contains("println(\"init\")"), kof);
    }

    @Test
    void finalLocalAndParamTranslate(@TempDir Path dir) throws Exception {
        // Kof não tem `final` em local/parâmetro (vars são mutáveis) — o
        // modificador é descartado; antes `final int y = 2;` dava parse error
        // (bug latente Q4 13/09).
        String kof = Translate.translateJava("""
                public class Fin {
                    int p(final int x) { return x + 1; }
                    public static void main(String[] args) {
                        final int y = 2;
                        final String s = "hi";
                        System.out.println(new Fin().p(y) + s);
                    }
                }
                """);
        assertFalse(kof.contains("final"), "`final` local/param descartado:\n" + kof);
        assertCompiles(dir, kof, "3hi");
    }

    @Test
    void stringEscapesRoundTripToValidKof(@TempDir Path dir) throws Exception {
        // O lexer decodifica `\"`/`\\`/`\n`/`\t` p/ chars reais; emitir cru
        // quebrava o Kof: `\"` fechava a string (PARSE043) e `\\` sumia
        // (`"path\x"` → `pathx`). Re-escapa no emit (bug latente Q4 13/09).
        String kof = Translate.translateJava("""
                public class Esc {
                    public static void main(String[] args) {
                        String quote = "say \\"hi\\"";
                        String slash = "path\\\\x";
                        String tab = "a\\tb";
                        System.out.println(quote);
                        System.out.println(slash);
                        System.out.println(tab);
                    }
                }
                """);
        assertTrue(kof.contains("\\\"hi\\\""), "aspas re-escapadas:\n" + kof);
        assertTrue(kof.contains("path\\\\x"), "barra re-escapada:\n" + kof);
        assertCompiles(dir, kof, "say \"hi\"\npath\\x\na\tb");
    }

    @Test
    void unicodeAndControlEscapesRoundTrip(@TempDir Path dir) throws Exception {
        // Escape unicode do Java era decodificado ERRADO: o lexer dropava a
        // barra e emitia o texto cru (Kof inválido) — bug latente Q4 13/09.
        // Backspace/formfeed também iam crus. Agora o escape vira char real e
        // o emit re-escapa controle como escape unicode (Kof suporta).
        String kof = Translate.translateJava("""
                public class Uni {
                    public static void main(String[] args) {
                        String a = "\\u0041";
                        String bs = "a\\bb";
                        char nl = '\\n';
                        char ua = '\\u0042';
                        char oc = '\\101';
                        char q = '\\'';
                        System.out.println(a);
                        System.out.println(a == "A");
                        System.out.println(bs.length);
                        System.out.println(nl == 10);
                        System.out.println(ua == 66);
                        System.out.println(oc == 65);
                        System.out.println(q == 39);
                    }
                }
                """);
        assertTrue(kof.contains("\"A\""), "`\\u0041` → `A`:\n" + kof);
        assertTrue(kof.contains("\\u0008"), "`\\b` re-escapa como `\\u0008`:\n" + kof);
        assertCompiles(dir, kof, "A\ntrue\n3\ntrue\ntrue\ntrue\ntrue");
    }

    @Test
    void singleParamLambdaWithoutParensTranslates() throws Exception {
        // Java `x -> x + 1` (lambda de 1 param sem parênteses): Kof exige
        // parênteses (`x -> x` é PARSE041) → emite `(x) -> x + 1`. Antes dava
        // `expected ';' but found '->'` (bug latente Q4 13/09). Aqui provamos a
        // FORMA (o tipo do param vem do contexto `map`/alvo, como no
        // `lambdaTranslates`) — a inferência contextual é da suite do
        // compilador, não do translator.
        String kof = Translate.translateJava("""
                public class Lam {
                    public static void main(String[] args) {
                        java.util.function.IntUnaryOperator f = x -> x + 1;
                    }
                }
                """);
        assertTrue(kof.contains("(x) -> x + 1"), "lambda 1-param ganha parênteses:\n" + kof);
    }

    @Test
    void javaNumericLiteralsTranslate(@TempDir Path dir) throws Exception {
        // Java numeric literals que o lexer antes partia em tokens separados
        // (`10L` → `10` `L`, `1.5e3` → `1.5` `e3`, `0x1F` → `0` `x1F`) =
        // parse error. Kof aceita as mesmas formas (probe `kof check`), exceto
        // o separador `_` (PARSE043) — esse é removido (mesmo valor).
        String kof = Translate.translateJava("""
                public class Num {
                    public static void main(String[] args) {
                        long l = 10L;
                        double d = 1.5e3;
                        float f = 2.5f;
                        double d2 = 10d;
                        int hex = 0x1F;
                        int sep = 1_000;
                        int sepHex = 0xFF_FF;
                        System.out.println(l + d + f + d2 + hex + sep + sepHex);
                    }
                }
                """);
        assertTrue(kof.contains("10L"), "sufixo long preservado:\n" + kof);
        assertTrue(kof.contains("1.5e3"), "expoente preservado:\n" + kof);
        assertTrue(kof.contains("0x1F"), "hex preservado:\n" + kof);
        assertFalse(kof.contains("1_000"), "separador `_` removido (Kof não aceita):\n" + kof);
        assertTrue(kof.contains("1000"), "valor de `1_000` preservado:\n" + kof);
        assertTrue(kof.contains("0xFFFF"), "`0xFF_FF` → `0xFFFF`:\n" + kof);

        assertCompiles(dir, kof, "68088.5");
    }

    @Test
    void doWhileTranslates(@TempDir Path dir) throws Exception {
        String kof = Translate.translateJava("""
                public class DW {
                    public static void main(String[] args) {
                        int i = 0;
                        do {
                            System.out.println(i);
                            i = i + 1;
                        } while (i < 3);
                    }
                }
                """);

        assertTrue(kof.contains("do { println(i) i = i + 1 } while (i < 3)"),
                "do-while Java deve virar do-while Kof (antes: expected ';' but found '{'):\n" + kof);
        assertFalse(kof.contains("do { {"), "sem chaves duplas:\n" + kof);

        assertCompiles(dir, kof, "0\n1\n2");
    }

    private void assertCompiles(Path dir, String kof, String expected) throws Exception {
        Path src = dir.resolve("T.kf");
        Files.writeString(src, kof);
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(src, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "translated Kof must compile:\n" + kof + "\n" + result.diagnostics().getDiagnostics());

        if (expected != null) {
            ProcessBuilder pb = new ProcessBuilder(
                    javaHomeJava(), "-cp", dir.resolve("out").toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            String output = new String(proc.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
            assertEquals(0, proc.waitFor(), "run exit code\n" + output);
            assertEquals(expected.trim(), output.trim(), "runtime output mismatch");
        }
    }

    private static String javaHomeJava() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }
}