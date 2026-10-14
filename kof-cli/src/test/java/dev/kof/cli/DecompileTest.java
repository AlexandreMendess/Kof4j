package dev.kof.cli;

import dev.kof.compiler.CompilationResult;
import dev.kof.compiler.CompilerDriver;
import dev.kof.compiler.Target;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * kof decompile — structural skeleton .class → .kf (docs/development/DECOMPILER.md).
 * Round-trips a javac-compiled class into Kof source that itself compiles.
 */
class DecompileTest {

    @Test
    void decompileProducesCompilableKof(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Calc.java");
        Files.writeString(javaFile, """
                public class Calc {
                    int total;
                    public int add(int a, int b) { return a + b; }
                    public String greet(String name) { return "hi " + name; }
                    public static float noLit() { return 1.5f; }
                }
                """);
        Path classFile = dir.resolve("Calc.class");
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(classFile);

        assertTrue(kof.contains("class Calc"), "should emit class name:\n" + kof);
        assertTrue(kof.contains("Int total"), "should emit field with type:\n" + kof);
        assertTrue(kof.contains("Int add"), "should emit add method:\n" + kof);
        assertTrue(kof.contains("String greet"), "should emit greet method:\n" + kof);
        // noLit (float ldc, sem literal float em Kof) degrada p/ stub honesto
        // enquanto add/greet recuperam — o smoke valida os dois juntos.
        assertTrue(kof.contains("throw \"body not recovered\""), "bodies must be honest stubs:\n" + kof);
        assertTrue(kof.contains("// unknown"), "bodies must be marked UNKNOWN:\n" + kof);
        assertTrue(kof.contains("// exact"), "fields must be marked EXACT:\n" + kof);

        Path out = dir.resolve("Calc.kf");
        Files.writeString(out, kof);

        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "decompiled Kof must compile:\n" + result.diagnostics().getDiagnostics());
    }

    @Test
    void decompileGenericSignaturesAreExact(@TempDir Path dir) throws Exception {
        // Fase D (Type Recovery): genéricos só existem no atributo Signature
        // (o descriptor apaga por erasure). O esqueleto deve sair EXACT.
        Path javaFile = dir.resolve("Bag.java");
        Files.writeString(javaFile, """
                import java.util.List;
                import java.util.Map;
                public class Bag {
                    List<String> items;
                    public List<String> get(Map<String, Integer> counts, String key) { return items; }
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("Bag.class"));
        assertTrue(kof.contains("List<String> items"), "field genérico EXACT:\n" + kof);
        assertTrue(kof.contains("List<String> get"), "retorno genérico EXACT:\n" + kof);
        assertTrue(kof.contains("Map<String, Integer> arg0"), "param genérico EXACT:\n" + kof);
    }

    @Test
    void recoversSimpleArithmeticBody(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Arith.java");
        Files.writeString(javaFile, """
                public class Arith {
                    public static int add(int a, int b) { return a + b; }
                    public int twice(int x) { return x * 2; }
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("Arith.class"));

        assertTrue(kof.contains("add(Int arg0, Int arg1) = ("), "add must be recovered as expression body:\n" + kof);
        assertTrue(kof.contains("+"), "must contain arithmetic:\n" + kof);
        assertTrue(kof.contains("twice(Int arg0) = ("), "twice must be recovered (instance, arg shifted by this):\n" + kof);
        assertFalse(kof.contains("throw \"body not recovered\""), "no stub expected for recovered bodies:\n" + kof);

        Path out = dir.resolve("Arith.kf");
        Files.writeString(out, kof);
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "decompiled must compile:\n" + kof + "\n" + result.diagnostics().getDiagnostics());
    }

    @Test
    void recoversComparisonBodies(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Cmp.java");
        Files.writeString(javaFile, """
                public class Cmp {
                    public static boolean isPos(int x) { return x > 0; }
                    public static boolean eq(int a, int b) { return a == b; }
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("Cmp.class"));

        assertTrue(kof.contains("isPos(Int arg0) = arg0 > 0"), "isPos deve virar comparação:\n" + kof);
        assertTrue(kof.contains("eq(Int arg0, Int arg1) = arg0 == arg1"), "eq deve virar comparação:\n" + kof);
        assertFalse(kof.contains("throw \"body not recovered\""), "não deve haver stub:\n" + kof);

        Path out = dir.resolve("Cmp.kf");
        Files.writeString(out, kof);
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "decompiled deve compilar:\n" + kof + "\n" + result.diagnostics().getDiagnostics());
    }

    @Test
    void comparisonReturnRespectsBoolVsIntReturnType(@TempDir Path dir) throws Exception {
        // §236 (14/09): o shape cmp/iconst_1/goto/iconst_0/ireturn e AMBIGUO na
        // raiz — `return x > 0` (Bool) e `return a < b ? 1 : 0` (Int) temo MESMO
        // bytecode. O fold antigo sempre devolvia o Bool cru: num corpo Int a
        // saida decompilada virava Bool e o typer rejeitava (SEM010 —
        // nao-compilavel, anti-R6). O fold agora PORTA pelo retType: Z → cru,
        // I → if-expression de inteiros. Prova: as DUAS formas no MESMO arquivo
        // + RECOMPILACAO (string so nao basta — foi o pino errado que escondeu
        // o bug por um dia).
        Path javaFile = dir.resolve("Cmp2.java");
        Files.writeString(javaFile, """
                public class Cmp2 {
                    public static boolean gt(int x) { return x > 0; }
                    public static int oneIfLt(int a, int b) { return a < b ? 1 : 0; }
                    public static int oneIfNe(int x) { return x != 0 ? 1 : 0; }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Cmp2.class"));

        assertTrue(kof.contains("Bool gt(Int arg0) = arg0 > 0"),
                "corpo Z preserva o Bool cru (byte-identico ao que ja passava):\n" + kof);
        assertTrue(kof.contains("Int oneIfLt(Int arg0, Int arg1) = if (arg0 < arg1) 1 else 0"),
                "corpo I dobra ternario como if-expression (nao Bool cru):\n" + kof);
        assertTrue(kof.contains("Int oneIfNe(Int arg0) = if (arg0 != 0) 1 else 0"),
                "face unaria (ifne) tambem porta p/ Int:\n" + kof);
        assertFalse(kof.contains("= arg0 != 0\n"), "nunca emitir `= <bool>` num corpo Int:\n" + kof);

        Path out = dir.resolve("Cmp2.kf");
        Files.writeString(out, kof);
        CompilationResult result = new CompilerDriver().compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "as 3 formas decompiladas devem compilar:\n" + kof
                + "\n" + result.diagnostics().getDiagnostics());
    }

    @Test
    void recoversIfElseReturn(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Max.java");
        Files.writeString(javaFile, """
                public class Max {
                    public static int max(int a, int b) { if (a > b) return a; return b; }
                    public static int abs(int x) { if (x >= 0) return x; return -x; }
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("Max.class"));

        assertTrue(kof.contains("max(Int arg0, Int arg1) = if (arg0 > arg1) arg0 else arg1"),
                "max deve virar if-expression:\n" + kof);
        assertTrue(kof.contains("abs(Int arg0) = if (arg0 >= 0) arg0 else -arg0"),
                "abs deve virar if-expression com negação:\n" + kof);

        Path out = dir.resolve("Max.kf");
        Files.writeString(out, kof);
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "decompiled deve compilar:\n" + kof + "\n" + result.diagnostics().getDiagnostics());
    }

    @Test
    void recoversWhileLoop(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Loop.java");
        Files.writeString(javaFile, """
                public class Loop {
                    public static int downto(int n) { int i = n; while (i > 0) { i = i - 1; } return i; }
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("Loop.class"));

        assertTrue(kof.contains("while (v1 > 0)"), "deve ter while:\n" + kof);
        assertTrue(kof.contains("var v1 = arg0"), "deve ter var inicial:\n" + kof);
        assertTrue(kof.contains("return v1"), "deve retornar v1:\n" + kof);
        assertFalse(kof.contains("throw \"body not recovered\""), "não deve ter stub:\n" + kof);

        Path out = dir.resolve("Loop.kf");
        Files.writeString(out, kof);
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "decompiled deve compilar:\n" + kof + "\n" + result.diagnostics().getDiagnostics());
    }

    @Test
    void bottomTestedLoopRecoversAsDoWhile(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("DoLoop.java");
        Files.writeString(javaFile, """
                public class DoLoop {
                    public static int dec(int i) { do { i = i - 1; } while (i > 0); return i; }
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("DoLoop.class"));

        // Bottom-tested loop (do-while): corpo + teste embaixo — o recovery
        // distingue por back-edge self/para-trás no bloco cond e emite
        // `do { } while (c)` (NUNCA um while de corpo vazio — "never invent
        // silently"). Kof tem do-while nativo (training/idioms/control-flow).
        assertTrue(kof.contains("do {"), "deve ter do-while:\n" + kof);
        assertTrue(kof.contains("} while (arg0 > 0)"), "teste embaixo, sem inversão:\n" + kof);
        assertFalse(kof.contains("throw \"body not recovered\""), "não deve ter stub:\n" + kof);
        assertFalse(kof.contains("while (arg0 <= 0)"), "não deve inverter p/ while vazio:\n" + kof);

        Path out = dir.resolve("DoLoop.kf");
        Files.writeString(out, kof);
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "decompiled deve compilar:\n" + kof + "\n" + result.diagnostics().getDiagnostics());
    }

    @Test
    void recoversNestedWhileLoops(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Nest.java");
        Files.writeString(javaFile, """
                public class Nest {
                    public static int grid(int n) {
                        int s = 0; int i = 0;
                        while (i < n) { int j = 0; while (j < n) { s = s + i * j; j = j + 1; } i = i + 1; }
                        return s;
                    }
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("Nest.class"));

        // while dentro de while (top-tested, back-edge de bloco posterior p/
        // o header externo): o struct() recursa pelo corpo do loop externo
        // incluindo o header interno — recovery já lida, sem código errado.
        assertTrue(kof.contains("while (v2 < arg0)"), "loop externo:\n" + kof);
        assertTrue(kof.contains("while (v3 < arg0)"), "loop interno:\n" + kof);
        assertFalse(kof.contains("throw \"body not recovered\""), "não deve ter stub:\n" + kof);

        Path out = dir.resolve("Nest.kf");
        Files.writeString(out, kof);
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "decompiled deve compilar:\n" + kof + "\n" + result.diagnostics().getDiagnostics());
    }

    @Test
    void bottomTestedLoopWithBranchInsideStaysHonestStub(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("DoBr.java");
        Files.writeString(javaFile, """
                public class DoBr {
                    static int OUT = 0;
                    public static int sep(int i) {
                        do { OUT = i; if (i > 100) { OUT = i * 2; } i = i - 3; } while (i > 0);
                        return OUT;
                    }
                    public static int brk(int i) {
                        do { if (i == 3) break; i = i - 1; } while (i > 0);
                        return i;
                    }
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("DoBr.class"));

        // do-while com corpo ramificado (diamond/break): o teste fica em bloco
        // SEPARADO do corpo (back-edge p/ bloco anterior) — recuperar isso
        // exige análise de merge estruturado (o corpo com if-join sairia com o
        // ponto de junção emitido dentro de um ramo; break escaparia p/ o pós-
        // loop dentro do corpo). Enquanto isso: stub UNKNOWN honesto — NUNCA
        // while de corpo vazio (código errado, anti-R6).
        assertTrue(kof.contains("throw \"body not recovered\""), "deve degradar p/ stub:\n" + kof);
        assertFalse(kof.contains("do {"), "não deve emitir do-while errado:\n" + kof);
        assertFalse(kof.contains("while ("), "não deve inventar while:\n" + kof);
    }

    @Test
    void shortcircuitAndTernaryRecoveredZeroDrift(@TempDir Path dir) throws Exception {
        // Fase C (13/10): `&&` (combinação de condições — a cadeia de
        // branches do bytecode É o curto-circuito) e o ternário
        // (if-expression Kof) RECUPERAM — a face antiga os travava como stub
        // com um oráculo errado (corrigido na rodada do contFor).
        Path javaFile = dir.resolve("Join.java");
        Files.writeString(javaFile, """
                public class Join {
                    public static int shortc(int a, int b) { if (a > 0 && b > a) return 1; return 0; }
                    public static int tern(int a) { return a > 0 ? 1 : -1; }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Join.class"));
        assertFalse(kof.contains("body not recovered"),
                "&& e ?: recuperam (nao stub):\n" + kof);
        assertTrue(kof.contains("arg0 > 0 && arg1 > arg0"), kof);
        assertTrue(kof.contains("return if (arg0 > 0) 1 else -1"), kof);
        // zero-drift executado contra o oráculo Java
        String kofRun = kof + "\nmain() {\n    var sc = Join()\n    println(Join.shortc(2, 5))\n    println(Join.shortc(2, 1))\n    println(Join.tern(4))\n    println(Join.tern(-4))\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        assertEquals("1\n0\n1\n-1", output, "zero-drift: igual ao Java");
    }
    @Test
    void floatConstantsDegradeNotDrift(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Fl.java");
        Files.writeString(javaFile, """
                public class Fl {
                    public static float f() { return 3.5f; }
                    public static double d() { return 2.25; }
                    public static int i() { return 42; }
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("Fl.class"));

        // CP tag-4 (Float) agora decodifica o VALOR (3.5, não os bits crus) —
        // mas Kof não tem literal float inline: emitir "3.5" num método Float
        // drifta p/ Double (SEM010). ldc recusa literais float → stub honesto,
        // igual a Double/Long (ldc2_w). Int constante segue recuperando.
        assertTrue(kof.contains("Int i() = 42"), "int constante deve recuperar:\n" + kof);
        assertFalse(kof.contains("= 3.5"), "float não pode virar literal Double:\n" + kof);
        assertTrue(kof.contains("Float f()"), "assinatura Float exata:\n" + kof);
        assertTrue(kof.contains("throw \"body not recovered\""), "f/d degradam p/ stub:\n" + kof);
    }

    @Test
    void recoversLongDoubleConstBodies(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("LC.java");
        Files.writeString(javaFile, """
                public class LC {
                    public static long zl() { return 0L; }
                    public static long one() { return 1L; }
                    public static double zero() { return 0.0; }
                    public static double uno() { return 1.0; }
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("LC.class"));

        // lconst_0/1 (0x09/0x0a) e dconst_0/1 (0x0e/0x0f) carregam o TIPO no
        // opcode — emitir "0L"/"1L"/"0.0"/"1.0" não pode driftar (Kof tem os
        // literais; e o verificador JVM garante const tipo == retorno). Lição
        // aplicada do bug 62 (float ldc ficou recusado — sem literal em Kof).
        assertTrue(kof.contains("= 0L"), "long const deve recuperar com sufixo L:\n" + kof);
        assertTrue(kof.contains("= 1L"), "lconst_1:\n" + kof);
        assertTrue(kof.contains("= 0.0"), "dconst_0:\n" + kof);
        assertTrue(kof.contains("= 1.0"), "dconst_1:\n" + kof);
        assertFalse(kof.contains("body not recovered"), "nenhum deve degradar:\n" + kof);

        Path out = dir.resolve("LC.kf");
        Files.writeString(out, kof);
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "decompiled deve compilar:\n" + kof + "\n" + result.diagnostics().getDiagnostics());
    }

    @Test
    void recoversLdc2LongDoubleConstants(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("L2.java");
        Files.writeString(javaFile, """
                public class L2 {
                    public static long big() { return 9999999999L; }
                    public static double frac() { return 2.25; }
                    public static double expo() { return 1.0E-5; }
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("L2.class"));

        // ldc2_w (0x14) só aponta p/ CP Long/Double — classificação por FORMA
        // (lição bug 62): dígitos → sufixo L; '.'/E → Double literal (Kof
        // aceita "1.0E-5"); NaN/Infinity recusados (sem literal em Kof).
        assertTrue(kof.contains("= 9999999999L"), "long ldc2:\n" + kof);
        assertTrue(kof.contains("= 2.25"), "double ldc2:\n" + kof);
        assertTrue(kof.contains("= 1.0E-5"), "double expo ldc2:\n" + kof);

        Path out = dir.resolve("L2.kf");
        Files.writeString(out, kof);
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "decompiled deve compilar:\n" + kof + "\n" + result.diagnostics().getDiagnostics());
    }

    @Test
    void multiArgConcatKeepsArgumentOrder(@TempDir Path dir) throws Exception {
        // REGRESSÃO (13/10): a concat com 2+ placeholders saía com a ORDEM
        // INVERTIDA ("cba" p/ `a + b + c`) — o caller re-invertia a pilha e
        // o BytecodeConcat.apply re-invertia de novo (double-reverse); a face
        // antiga só tinha 1 placeholder, então nunca pegou. Zero-drift
        // executado: o Kof decompilado RODA e dá o mesmo do Java.
        Path javaFile = dir.resolve("Mc.java");
        Files.writeString(javaFile, """
                public class Mc {
                    static String three(String a, String b, String c) { return a + b + c; }
                    static String four(String a, String b, String c, String d) { return a + "-" + b + c + d; }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Mc.class"));
        assertTrue(kof.contains("arg0 + arg1 + arg2"),
                "concat de 3 args mant\u00e9m a ordem:\n" + kof);
        assertFalse(kof.contains("body not recovered"), kof);
        String kofRun = kof + "\nmain() {\n    println(Mc.three(\"a\", \"b\", \"c\"))\n    println(Mc.four(\"w\", \"x\", \"y\", \"z\"))\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        assertEquals("abc\nw-xyz", output, "zero-drift: ordem da concat correta");
    }

    @Test
    void recoversStringConcatInvokedynamic(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Cat.java");
        Files.writeString(javaFile, """
                public class Cat {
                    public static String greet(String n) { return "v=" + n + 42; }
                    public static String simple(String a) { return a + "x"; }
                    public static String mid(int i) { return "i" + i + "j"; }
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("Cat.class"));

        // String + em Java moderno (9+) é invokedynamic makeConcatWithConstants
        // — a forma de corpo String MAIS comum. Sem recovery de receita, TODO
        // corpo com concat caía em stub. O parser agora lê BootstrapMethods e
        // o decoder aplica a receita (\u0001 = placeholder) → `a + "x" + b`.
        assertTrue(kof.contains("greet(String arg0) = \"v=\" + arg0 + \"42\""),
                "concat 'v=' + n + 42:\n" + kof);
        assertTrue(kof.contains("simple(String arg0) = arg0 + \"x\""),
                "concat 'a' + \"x\":\n" + kof);
        assertTrue(kof.contains("\"i\" + arg0 + \"j\""),
                "concat no meio:\n" + kof);
        assertFalse(kof.contains("body not recovered"), "nenhum deve degradar:\n" + kof);

        Path out = dir.resolve("Cat.kf");
        Files.writeString(out, kof);
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "decompiled deve compilar:\n" + kof + "\n" + result.diagnostics().getDiagnostics());
    }

    @Test
    void recoversLdcWAndAconstNull() {
        // Corpus-real coverage (histograma de opcodes do kof-compiler): ldc_w
        // (0x13, índice u2 p/ CP>255) era o opcode NÃO-tratado mais frequente
        // (~3k ocorrências) e aconst_null (0x01) ~800. ldc_w é o MESMO ldc já
        // tratado (BytecodeReader já lê o índice de 2 bytes) — teste manual:
        // bytecode `ldc_w #300; areturn` com cp[300]="#301", cp[301]="oi".
        String[] cp = new String[302];
        cp[300] = "#301";
        cp[301] = "oi";
        byte[] code = {0x13, 0x01, 0x2C, (byte) 0xb0};            // ldc_w 300; areturn
        String e = BytecodeDecoder.recoverExpression(code, cp,
                new BytecodeFrame("()Ljava/lang/String;", true));
        assertEquals("\"oi\"", e, "ldc_w recupera igual ao ldc");

        byte[] nul = {0x01, (byte) 0xb0};                          // aconst_null; areturn
        assertEquals("null", BytecodeDecoder.recoverExpression(nul, cp,
                new BytecodeFrame("()Ljava/lang/String;", true)), "aconst_null → null");
    }

    @Test
    void decompileTreeEmitsPackageAndResolvesCrossFileReference(@TempDir Path dir) throws Exception {
        // §7 multi-classe degrau 1: `kof decompile <dir>` espelha a árvore com
        // `package`, e o frontend resolve tipo de MESMO pacote entre arquivos
        // SEM import (probe PKG004) → referência cross-file deixa de ser
        // "Undefined variable or type" (as 4 drifts restantes no corpus).
        Path src = dir.resolve("classes");
        Path pkg = src.resolve("p");
        Files.createDirectories(pkg);
        Path b = pkg.resolve("B.java");
        Files.writeString(b, """
                package p;
                public class B { public int v; public B(int v) { this.v = v; } }
                """);
        Path c = pkg.resolve("C.java");
        Files.writeString(c, """
                package p;
                public class C {
                    public static int use(B b) { return b.v; }
                }
                """);
        runJavac(List.of(b, c), pkg);
        Path out = dir.resolve("gen");
        Decompile.decompileTree(src, out);
        String bSrc = Files.readString(out.resolve("p/B.kf"));
        String cSrc = Files.readString(out.resolve("p/C.kf"));
        assertTrue(bSrc.contains("\npackage p\n"), "decompileTree deve emitir package:\n" + bSrc);
        assertTrue(cSrc.contains("use(B"), "C referencia B por nome simples:\n" + cSrc);
        // prova de fogo: o PAR compilado junto (mesmo pacote) resolve B em C —
        // sem o modo multi-arquivo, C.kf sozinho daria SEM011.
        CompilationResult r = new CompilerDriver().compileSources(List.of(
                out.resolve("p/B.kf"), out.resolve("p/C.kf")), dir.resolve("o"), Target.JVM, out);
        assertTrue(r.success(), "cross-file mesmo pacote deve compilar:\n" + cSrc + "\n" + r.diagnostics().getDiagnostics());
    }

    @Test
    void decompileTreeResolvesSamePackageInstanceofAndCast(@TempDir Path dir) throws Exception {
        // §7 degrau 2: com o índice da árvore, instanceof/checkcast de classe
        // de DOMÍNIO do MESMO pacote recuperam (antes: stub honesto); o par
        // decompilado junto compila (zero drift). Fora do índice → stub.
        Path src = dir.resolve("classes");
        Path pkg = src.resolve("p");
        Files.createDirectories(pkg);
        Path b = pkg.resolve("B.java");
        Files.writeString(b, """
                package p;
                public class B { public int v; public B(int v) { this.v = v; } }
                """);
        Path c = pkg.resolve("C.java");
        Files.writeString(c, """
                package p;
                public class C {
                    public static boolean isB(Object o) { boolean x = o instanceof B; return x; }
                    public static int getV(Object o) { B bb = (B) o; int v = bb.v; return v; }
                }
                """);
        runJavac(java.util.List.of(b, c), pkg);
        Path out = dir.resolve("gen");
        assertEquals(0, Decompile.decompileTree(src, out));
        String cSrc = Files.readString(out.resolve("p/C.kf"));
        assertTrue(cSrc.contains("arg0 instanceof B"),
                "instanceof de domínio same-package deve recuperar:\n" + cSrc);
        assertTrue(cSrc.contains("(arg0 as B)"),
                "checkcast de domínio same-package deve recuperar:\n" + cSrc);
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                out.resolve("p/B.kf"), out.resolve("p/C.kf")), dir.resolve("o"), Target.JVM, out);
        assertTrue(r.success(), "par cross-file deve compilar (zero drift):\n" + cSrc + "\n" + r.diagnostics().getDiagnostics());
    }

    @Test
    void decompileTreeStillStubsOutOfTreeDomainTypes(@TempDir Path dir) throws Exception {
        // Recusa R6 preservada: classe de domínio que NÃO está na árvore
        // (outro pacote, sem índice) continua stub honesto — nunca inventa.
        Path src = dir.resolve("classes");
        Path pkg = src.resolve("p");
        Files.createDirectories(pkg);
        Path b = pkg.resolve("B.java");
        Files.writeString(b, """
                package p;
                public class B { public int v; }
                """);
        Path q = src.resolve("q");
        Files.createDirectories(q);
        Path c = q.resolve("C.java");
        Files.writeString(c, """
                package q;
                import p.B;
                public class C {
                    public static boolean isB(Object o) { boolean x = o instanceof B; return x; }
                }
                """);
        runJavac(java.util.List.of(b), pkg);
        runJavac(java.util.List.of(b, c), src);
        Path out = dir.resolve("gen");
        // decompila SÓ q (B fora da árvore) → instanceof p.B não resolve
        Path onlyQ = dir.resolve("onlyQ");
        Files.createDirectories(onlyQ.resolve("q"));
        Files.copy(src.resolve("q/C.class"), onlyQ.resolve("q/C.class"));
        assertEquals(0, Decompile.decompileTree(onlyQ, out));
        String cSrc = Files.readString(out.resolve("q/C.kf"));
        assertTrue(cSrc.contains("body not recovered"),
                "instanceof cross-package fora do índice fica stub:\n" + cSrc);
    }

    @Test
    void decompileTreeEmitsImportsForCrossPackageDomainRefs(@TempDir Path dir) throws Exception {
        // §7 degrau 3: referência de domínio cross-package (extends,
        // instanceof, new) sai com `import` e o par compila junto
        // (antes: nome simples sem import → SEM011).
        Path src = dir.resolve("classes");
        Path p = src.resolve("p");
        Path q = src.resolve("q");
        Files.createDirectories(p);
        Files.createDirectories(q);
        Path b = p.resolve("B.java");
        Files.writeString(b, """
                package p;
                public class B { public int v; public B(int v) { this.v = v; } }
                """);
        Path c = q.resolve("C.java");
        Files.writeString(c, """
                package q;
                import p.B;
                public class C extends B {
                    public C(int v) { super(v); }
                    public static boolean isB(Object o) { boolean x = o instanceof B; return x; }
                    public static B make() { return new B(3); }
                }
                """);
        runJavac(java.util.List.of(b, c), src);
        Path out = dir.resolve("gen");
        assertEquals(0, Decompile.decompileTree(src, out));
        String cSrc = Files.readString(out.resolve("q/C.kf"));
        assertTrue(cSrc.contains("import p.B"),
                "uso cross-package deve gerar import:\n" + cSrc);
        assertTrue(cSrc.contains("arg0 instanceof B") && cSrc.contains("= B(3)"),
                "extends/instanceof/new devem recuperar:\n" + cSrc);
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                out.resolve("p/B.kf"), out.resolve("q/C.kf")), dir.resolve("o"), Target.JVM, out);
        assertTrue(r.success(), "árvore com import deve compilar (zero drift):\n" + cSrc + "\n" + r.diagnostics().getDiagnostics());
    }

    @Test
    void superCallRecoveredNotStubbed(@TempDir Path dir) throws Exception {
        // Fase G (diamond super.m(), mantenedora 14/10): `invokespecial` de
        // superclasse emitia stub ("body not recovered"); `super.m()` É
        // expressável em Kof (medido rodando) — o corpo recupera o call.
        Path src = dir.resolve("classes");
        Path b = dir.resolve("B.java");
        Files.writeString(b, """
                public class B { public int base() { return 10; } }
                """);
        Path c = dir.resolve("C.java");
        Files.writeString(c, """
                public class C extends B {
                    public int viaSuper() { return super.base(); }
                }
                """);
        runJavac(java.util.List.of(b, c), src);
        Path out = dir.resolve("gen");
        assertEquals(0, Decompile.decompileTree(src, out));
        String cSrc = Files.readString(out.resolve("C.kf"));
        assertTrue(cSrc.contains("return super.base()"),
                "super.m() deve ser RECUPERADO (nao stub):\n" + cSrc);
        assertFalse(cSrc.contains("body not recovered"),
                "nenhum stub no arquivo:\n" + cSrc);
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                out.resolve("B.kf"), out.resolve("C.kf")), dir.resolve("o"), Target.JVM, out);
        assertTrue(r.success(), "arvore com super.m() deve compilar (zero drift):\n" + cSrc
                + "\n" + r.diagnostics().getDiagnostics());
    }

    @Test
    void interfaceSuperDefaultInlinedNotStubbed(@TempDir Path dir) throws Exception {
        // Fase G (diamond, mantenedora 14/10): `return A.super.m();` — o
        // corpo do default da interface é lido do classfile irmão e INLINADO
        // no call site (mesmo pipeline); stub nunca é opção (Q7).
        Path src = dir.resolve("classes");
        Path a = dir.resolve("A.java");
        Files.writeString(a, """
                public interface A {
                    default int m() { return 1; }
                    default int twice(int x) { return x * 2; }
                }
                """);
        Path c = dir.resolve("C.java");
        Files.writeString(c, """
                public class C implements A {
                    public int viaInterface() { return A.super.m(); }
                    public int viaArgs(int x) { return A.super.twice(x); }
                }
                """);
        runJavac(java.util.List.of(a, c), src);
        Path out = dir.resolve("gen");
        assertEquals(0, Decompile.decompileTree(src, out));
        String cSrc = Files.readString(out.resolve("C.kf"));
        assertTrue(cSrc.contains("viaInterface() = 1"),
                "o default deve ser INLINADO (valor do corpo de A.m()):\n" + cSrc);
        assertTrue(cSrc.contains("viaArgs(Int arg0) = (arg0 * 2)"),
                "o default COM argumento deve ser INLINADO ligando o arg:\n" + cSrc);
        assertFalse(cSrc.contains("body not recovered"),
                "nenhum stub no arquivo:\n" + cSrc);
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                out.resolve("A.kf"), out.resolve("C.kf")), dir.resolve("o"), Target.JVM, out);
        assertTrue(r.success(), "arvore com o default inlinado deve compilar (zero drift):\n" + cSrc
                + "\n" + r.diagnostics().getDiagnostics());
    }

    @Test
    void callConditionsRecoverNotStubbed(@TempDir Path dir) throws Exception {
        // Fase C (13/10): condições com CHAMADA (`s.isEmpty()`) e com
        // aritmética (`a + 1 > b`) recuperam; o `isEmpty` saiu das
        // propriedades da stdlib (medido: `.isEmpty` compilava e quebrava em
        // runtime com NoSuchField — a chamada é fiel e `Bool != 0` funciona).
        Path javaFile = dir.resolve("Cd.java");
        Files.writeString(javaFile, """
                public class Cd {
                    int check(String s) {
                        if (s.isEmpty()) { return 1; }
                        return 0;
                    }
                    int gt(int a, int b) {
                        if (a + 1 > b) { return 2; }
                        return 3;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Cd.class"));
        assertFalse(kof.contains("body not recovered"),
                "condicoes com chamada/aritmetica recuperam:\n" + kof);
        assertTrue(kof.contains("arg0.isEmpty() != 0"), kof);
        assertTrue(kof.contains("(arg0 + 1) > arg1"), kof);
        // zero-drift executado
        String kofRun = kof + "\nmain() {\n    var cd = Cd()\n    println(cd.check(\"\"))\n    println(cd.check(\"x\"))\n    println(cd.gt(2, 1))\n    println(cd.gt(1, 2))\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        assertEquals("1\n0\n2\n3", output, "zero-drift executado");
    }

    @Test
    void staticFieldsAndClinitFoldNotSilentlyLost(@TempDir Path dir) throws Exception {
        // Fase E (fatia static-init, mantenedora 14/10): o clinit em forma
        // literal-putstatic dobra nos inicializadores — antes os campos
        // estáticos NEM eram emitidos (o main referenciava `St.V` que não
        // existia = Kof sem compilar, falso OK medido).
        Path src = dir.resolve("classes");
        Path s = dir.resolve("St.java");
        Files.writeString(s, """
                public class St {
                    static int V;
                    static String S;
                    static { V = 42; S = "hi"; }
                    public static void main(String[] args) {
                        System.out.println(V);
                        System.out.println(S);
                    }
                }
                """);
        runJavac(java.util.List.of(s), src);
        Path out = dir.resolve("gen");
        assertEquals(0, Decompile.decompileTree(src, out));
        String stSrc = Files.readString(out.resolve("St.kf"));
        assertTrue(stSrc.contains("static Int V = 42"), stSrc);
        assertTrue(stSrc.contains("static String S = \"hi\""), stSrc);
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                out.resolve("St.kf")), dir.resolve("o"), Target.JVM, out);
        assertTrue(r.success(), "a arvore com os campos dobrados deve compilar:\n" + stSrc
                + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "St");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        assertEquals("42\nhi", output, "os valores dobrados devem RODAR");
    }


    @Test
    void decompileStaticFieldEmittedWithFoldedInit(@TempDir Path dir) throws Exception {
        // Fase E (fatia static-init, mantenedora 14/10): o campo estático era
        // PULADO (perda silenciosa — o main referenciava `Const.MAX` que não
        // existia = falso OK); agora é EMITIDO e o clinit literal dobra.
        Path javaFile = dir.resolve("Const.java");
        Files.writeString(javaFile, """
                public class Const {
                    public static final int MAX = 100;
                    public String label = "x";
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("Const.class"));
        assertTrue(kof.contains("static Int MAX = 100"),
                "static field emitido com o clinit dobrado:\n" + kof);
        assertTrue(kof.contains("String label"), "instance field should be kept:\n" + kof);
    }


    @Test
    void primitiveArrayBuildRecoveredNotStubbed(@TempDir Path dir) throws Exception {
        // Fase E: `newarray` (0xbc) faltava — arrays primitivos (`int[]`)
        // stubavam o metodo inteiro. Agora `new int[n]` + xastore recuperam.
        Path javaFile = dir.resolve("Arr.java");
        Files.writeString(javaFile, """
                public class Arr {
                    int[] build(int n) {
                        int[] a = new int[n];
                        for (int i = 0; i < n; i++) { a[i] = i; }
                        return a;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Arr.class"));
        assertFalse(kof.contains("body not recovered"),
                "arrays primitivos devem recuperar (nao stub):\n" + kof);
        assertTrue(kof.contains("new Int[arg0]"), "newarray → new Int[n]:\n" + kof);
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kof)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "o Kof dos arrays deve compilar (zero drift):\n" + kof
                + "\n" + r.diagnostics().getDiagnostics());
    }

    @Test
    void castConditionRecoversNotStubbed(@TempDir Path dir) throws Exception {
        // Fase C: checkcast no testExpr — `if ((Integer) o > 3)` recupera
        // como `(arg0 as Int) > 3` (o intValue e UNBOX → identidade).
        Path javaFile = dir.resolve("Cn.java");
        Files.writeString(javaFile, """
                public class Cn {
                    int v;
                    int viaCast(Object o) {
                        if ((Integer) o > 3) { return 1; }
                        return 0;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Cn.class"));
        assertFalse(kof.contains("body not recovered"),
                "cast na condicao recupera:\n" + kof);
        assertTrue(kof.contains("(arg0 as Int) > 3"), kof);
        // zero-drift executado: build(5) -> 1, cast 2 -> 0
        String kofRun = kof + "\nmain() {\n    var cn = Cn()\n    var o1: Object = 5\n    var o2: Object = 2\n    println(cn.viaCast(o1))\n    println(cn.viaCast(o2))\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        assertEquals("1\n0", output, "zero-drift executado");
    }

    @Test
    void throwExceptionConstructionRecoversNotStubbed(@TempDir Path dir) throws Exception {
        // Fase E (o idioma errors.md no caminho de statements): a construção
        // de exceção É a String do message — `throw new RuntimeException(msg)`
        // recupera como `throw msg` e o zero-drift é executado (o catch pega
        // a mensagem igual ao Java).
        Path javaFile = dir.resolve("Th.java");
        Files.writeString(javaFile, """
                public class Th {
                    void kaboom(String msg) { throw new RuntimeException(msg); }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Th.class"));
        assertFalse(kof.contains("body not recovered"),
                "o throw com construção recupera:\n" + kof);
        assertTrue(kof.contains("throw arg0"), kof);
        // zero-drift executado: o catch pega a mensagem como no Java
        String kofRun = kof + "\nmain() {\n    var th = Th()\n    try {\n        th.kaboom(\"boom-42\")\n        println(\"no-throw\")\n    } catch (String e) {\n        println(\"caught: \" + e)\n    }\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        assertEquals("caught: boom-42", output, "zero-drift: a mensagem lançada e pega");
    }

    @Test
    void andCombinationNoCrashOnSucc1Then(@TempDir Path dir) throws Exception {
        // Fase C (regressão 13/10): o loop de combinação && chamava
        // succ.get(1) sem exigir succ.size() == 2 — um then-de-teste com
        // succ ÚNICO (braços que caem no mesmo join) crashava IOOBE
        // (medido no TranslateStatements REAL). Sem crash + rc=0.
        Path real = Path.of("target/classes/dev/kof/cli/TranslateStatements.class");
        if (!Files.exists(real)) {
            real = Path.of("kof-cli/target/classes/dev/kof/cli/TranslateStatements.class");
        }
        final Path realF = real;
        assertDoesNotThrow(() -> Decompile.decompile(realF),
                "o decompile não pode CRASHAR (degrada para stub, nunca lança)");
    }

    @Test
    void abstractMethodEmitsSignatureNotStub(@TempDir Path dir) throws Exception {
        // Fase E: método ABSTRATO não tem corpo NO BYTECODE — o throw-stub
        // era mentira (não há informação perdida); emite a assinatura nua.
        Path javaFile = dir.resolve("Sh.java");
        Files.writeString(javaFile, """
                public interface Sh {
                    String shape(int kind);
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Sh.class"));
        // O Kof NÃO tem métodos abstratos (a interface decompilada vira
        // class): o corpo do abstrato é o throw-stub INÓCUO (nunca
        // alcançável numa interface) — catalogado, nunca código real
        // inventado; a assinatura fica EXACT.
        assertTrue(kof.contains("shape"), kof);
        assertTrue(kof.contains("body not recovered"),
                "o abstrato degrada para o stub inócuo catalogado:\n" + kof);
    }

    @Test
    void valuePropagatingIfElseRecovers(@TempDir Path dir) throws Exception {
        // Fase C (13/10): os braços do if/else deixando 1 valor na pilha e o
        // join começando com a store → if-expression propagada
        // (`var v2 = if (cond) thenVal else eVal`; o astore do join é
        // consumido pela atribuição — o skip-first).
        Path javaFile = dir.resolve("Vp.java");
        Files.writeString(javaFile, """
                public class Vp {
                    String pick(boolean big) {
                        String r;
                        if (big) { r = "B"; } else { r = "S"; }
                        return r;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Vp.class"));
        assertFalse(kof.contains("body not recovered"),
                "o if/else com valor propagado recupera:\n" + kof);
        assertTrue(kof.contains("if ("), kof);
        String kofRun = kof + "\nmain() {\n    var vp = Vp()\n    println(vp.pick(true))\n    println(vp.pick(false))\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        assertEquals("B\nS", output, "zero-drift executado");
    }

    @Test
    void jdkCollectionConstructionMapsToStdlib(@TempDir Path dir) throws Exception {
        // Fase E (13/10): mapeamento JDK→stdlib de COLEÇÕES (o mesmo do
        // tradutor): `new ArrayList()` → `listOf()` etc. Sem o mapa, o
        // `new ArrayList()` no corpo recusava (stub) — destravou o
        // parseRecord e muitos métodos.
        Path javaFile = dir.resolve("Col.java");
        Files.writeString(javaFile, """
                import java.util.ArrayList;
                public class Col {
                    static ArrayList<String> mk() {
                        ArrayList<String> xs = new ArrayList<String>();
                        xs.add("a");
                        xs.add("b");
                        return xs;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Col.class"));
        assertFalse(kof.contains("body not recovered"),
                "new ArrayList() mapeia para listOf() (n\u00e3o stub):\n" + kof);
        assertTrue(kof.contains("listOf()"), kof);
        // zero-drift executado
        String kofRun = kof + "\nmain() {\n    var xs = Col.mk()\n    println(xs.size)\n    println(xs.get(0))\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        assertEquals("2\na", output, "zero-drift executado");
    }

    @Test
    void breakInLoopRecoversNotStubbed(@TempDir Path dir) throws Exception {
        // Fase C (13/10): `break` é um goto para a SAÍDA do loop aberto —
        // um bloco de goto único para ela emite `break` (o corpo antes já
        // emitido). Antes: stub do método inteiro.
        Path javaFile = dir.resolve("Bk.java");
        Files.writeString(javaFile, """
                public class Bk {
                    int brk(int n) {
                        int s = 0;
                        for (int i = 0; i < n; i++) { if (i == 3) break; s += i; }
                        return s;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Bk.class"));
        assertFalse(kof.contains("body not recovered"),
                "o break recupera:\n" + kof);
        assertTrue(kof.contains("break"), kof);
        // zero-drift executado
        String kofRun = kof + "\nmain() {\n    var bk = Bk()\n    println(bk.brk(0))\n    println(bk.brk(3))\n    println(bk.brk(6))\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        assertEquals("0\n3\n3", output, "zero-drift executado");
    }

    @Test
    void orCombinationAndBoolCoercionRecover(@TempDir Path dir) throws Exception {
        // Fase C (13/10): o `||` (orientação ESPELHADA do &&) recupera +
        // coerção Bool (braços 1/0 num método `Z` → true/false; antes o
        // `&&` emitia `return if (..) 1 else 0` num Bool = SEM010).
        Path javaFile = dir.resolve("Or.java");
        Files.writeString(javaFile, """
                public class Or {
                    boolean or2(int a, int b) { return a > 0 || b > 0; }
                    boolean and2(int a, int b) { return a > 0 && b > 0; }
                    int orInIf(int a, int b) { if (a > 0 || b > 0) { return 1; } return 0; }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Or.class"));
        assertFalse(kof.contains("body not recovered"),
                "|| e && recuperam:\n" + kof);
        assertTrue(kof.contains("true") || kof.contains("false"),
                "braços Bool viram true/false:\n" + kof);
        // zero-drift executado
        String kofRun = kof + "\nmain() {\n    var o = Or()\n    println(o.or2(1, 0))\n    println(o.or2(0, 0))\n    println(o.and2(1, 1))\n    println(o.and2(1, 0))\n    println(o.orInIf(0, 5))\n    println(o.orInIf(0, 0))\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        assertEquals("true\nfalse\ntrue\nfalse\n1\n0", output, "zero-drift executado");
    }

    @Test
    void shiftOperatorsRecoverNotStubbed(@TempDir Path dir) throws Exception {
        // Fase E (13/10): os opcodes de SHIFT (0x78-0x7d) faltavam no emitLinear
        // (e no avaliador) — qualquer corpo com `<<`/`>>`/`>>>` stubava. A
        // semântica por-target é do COMPILADOR (corrigida no §698).
        Path javaFile = dir.resolve("Sh.java");
        Files.writeString(javaFile, """
                public class Sh {
                    static int shl(int a, int b) { return a << b; }
                    static int shr(int a, int b) { return a >> b; }
                    static int ushr(int a, int b) { return a >>> b; }
                    static long lshl(long a, int b) { return a << b; }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Sh.class"));
        assertFalse(kof.contains("body not recovered"),
                "os shifts recuperam:\n" + kof);
        assertTrue(kof.contains("<<") && kof.contains(">>") && kof.contains(">>>"), kof);
        // zero-drift executado (contagens VARIÁVEIS — literais são const-folded)
        String kofRun = kof + "\nmain() {\n    var sh = Sh()\n    println(Sh.shl(1, 4))\n    println(Sh.shr(-16, 2))\n    println(Sh.ushr(-5, 1))\n    println(Sh.lshl(1L, 40))\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        // oracle Java: 1<<4=16, -16>>2=-4, -5>>>1=2147483645, 1L<<40=1099511627776
        assertEquals("16\n-4\n2147483645\n1099511627776", output, "zero-drift dos shifts");
    }

    @Test
    void orChain3TermsRecoversNotStubbed(@TempDir Path dir) throws Exception {
        // Fase C (13/10): a cadeia `||` de 3+ termos (orChain) — os testes
        // NAO-finais saltam para o VERDADEIRO (takenCond) e o ULTIMO cai no
        // verdadeiro (invCond), diferente do `||` de 2 termos (assimetria
        // medida). Antes: stub do método inteiro.
        Path javaFile = dir.resolve("Oc.java");
        Files.writeString(javaFile, """
                public class Oc {
                    boolean or3(int a, int b, int c) { return a > 0 || b > 0 || c > 0; }
                    boolean or4(int a, int b, int c, int d) { return a > 0 || b > 0 || c > 0 || d > 0; }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Oc.class"));
        assertFalse(kof.contains("body not recovered"),
                "a cadeia || de 3/4 termos recupera:\n" + kof);
        assertTrue(kof.contains("arg0 > 0 || arg1 > 0 || arg2 > 0"), kof);
        String kofRun = kof + "\nmain() {\n    var oc = Oc()\n    println(oc.or3(0, 0, 1))\n    println(oc.or3(0, 0, 0))\n    println(oc.or4(0, 0, 0, 5))\n    println(oc.or4(0, 0, 0, 0))\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        assertEquals("true\nfalse\ntrue\nfalse", output, "zero-drift executado");
    }

    private Path writeKof(Path dir, String kof) throws Exception {
        Path f = dir.resolve("Arr.kf");
        Files.writeString(f, kof);
        return f;
    }


    @Test
    void stringCastRecoversNotStubbed(@TempDir Path dir) throws Exception {
        // Fase E (fatia cast-String, 14/10): checkcast com alvo String era
        // recusado ("redundante") — medido que `as String` é FIEL (o cast
        // errado lança a CCE): os métodos com cast recuperam agora.
        Path javaFile = dir.resolve("Ca.java");
        Files.writeString(javaFile, """
                public class Ca {
                    String cast(Object o) { return (String) o; }
                    int len(Object o) { String s = (String) o; return s.length(); }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Ca.class"));
        assertFalse(kof.contains("body not recovered"),
                "cast String deve recuperar (nao stub):\n" + kof);
        assertTrue(kof.contains("as String"), "checkcast → as String:\n" + kof);
        // zero-drift executado
        String kofRun = kof + "\nmain() {\n    var ca = Ca()\n    println(ca.cast(\"ok\"))\n    println(ca.len(\"abcd\"))\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        assertEquals("ok\n4", output, "zero-drift: cast + len como no Java");
    }



    @Test
    void decompileTreeRefusesAmbiguousSimpleNames(@TempDir Path dir) throws Exception {
        // Recusa R6: simples duplicado em 2+ pacotes do índice nunca ganha
        // import (ambíguo) → stub honesto, mesmo com o pacote atual tendo um.
        Path src = dir.resolve("classes");
        for (String pkg : new String[]{"p", "q", "r"}) Files.createDirectories(src.resolve(pkg));
        Path dp = src.resolve("p/Dup.java");
        Files.writeString(dp, "package p;\npublic class Dup { public int v; }\n");
        Path dq = src.resolve("q/Dup.java");
        Files.writeString(dq, "package q;\npublic class Dup { public int w; }\n");
        Path c = src.resolve("r/C.java");
        Files.writeString(c, """
                package r;
                import p.Dup;
                public class C {
                    public static boolean isD(Object o) { boolean x = o instanceof Dup; return x; }
                }
                """);
        runJavac(java.util.List.of(dp, dq, c), src);
        Path out = dir.resolve("gen");
        assertEquals(0, Decompile.decompileTree(src, out));
        String cSrc = Files.readString(out.resolve("r/C.kf"));
        assertTrue(cSrc.contains("body not recovered") && !cSrc.contains("instanceof Dup"),
                "simples ambíguo (p.Dup + q.Dup) fica stub, sem import:\n" + cSrc);
        assertTrue(!cSrc.contains("import p.Dup"),
                "nenhum import ambíguo deve ser emitido:\n" + cSrc);
    }

    @Test
    void decompileTreeEmitsImportsForSignatureTypes(@TempDir Path dir) throws Exception {
        // §7 degrau 4: tipos de ASSINATURA (field, ctor-param, param, return)
        // cross-package registram import (emissão de nomes inalterada).
        Path src = dir.resolve("classes");
        Path p = src.resolve("p");
        Path q = src.resolve("q");
        Files.createDirectories(p);
        Files.createDirectories(q);
        Path b = p.resolve("B.java");
        Files.writeString(b, """
                package p;
                public class B { public int v; public B(int v) { this.v = v; } }
                """);
        Path c = q.resolve("C.java");
        Files.writeString(c, """
                package q;
                import p.B;
                public class C {
                    public B held;
                    public C(B b) { this.held = b; }
                    public static int take(B b) { return b.v; }
                    public static B make() { return new B(3); }
                }
                """);
        runJavac(java.util.List.of(b, c), src);
        Path out = dir.resolve("gen");
        assertEquals(0, Decompile.decompileTree(src, out));
        String cSrc = Files.readString(out.resolve("q/C.kf"));
        assertTrue(cSrc.contains("import p.B"),
                "tipos de assinatura cross-package devem gerar import:\n" + cSrc);
        assertTrue(cSrc.contains("B held") && cSrc.contains("take(B arg0")
                && cSrc.contains("B make()"),
                "field/param/return devem sair com o simples:\n" + cSrc);
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                out.resolve("p/B.kf"), out.resolve("q/C.kf")), dir.resolve("o"), Target.JVM, out);
        assertTrue(r.success(), "árvore com import deve compilar (zero drift):\n" + cSrc + "\n" + r.diagnostics().getDiagnostics());
    }

    @Test
    void recoversNewInStatementBody(@TempDir Path dir) throws Exception {
        // Fase E: `new` em corpo multi-statement (`var n = new N(x); ...`)
        // não existia no emitLinear (só no path linear) → stub certo.
        // Mirror 0xbb/0x59/0xb7; JDK recusa (R6); cross-package registra import.
        Path src = dir.resolve("classes");
        Path p = src.resolve("p");
        Path q = src.resolve("q");
        Files.createDirectories(p);
        Files.createDirectories(q);
        Path n = p.resolve("N.java");
        Files.writeString(n, """
                package p;
                public class N {
                    public int v;
                    public N(int v) { this.v = v; }
                    public static int build(int x) {
                        N n = new N(x);
                        int v = n.v;
                        return v;
                    }
                }
                """);
        Path m = q.resolve("M.java");
        Files.writeString(m, """
                package q;
                import p.N;
                public class M {
                    public static int build(int x) {
                        N n = new N(x);
                        int v = n.v;
                        return v;
                    }
                }
                """);
        runJavac(java.util.List.of(n, m), src);
        Path out = dir.resolve("gen");
        assertEquals(0, Decompile.decompileTree(src, out));
        String nSrc = Files.readString(out.resolve("p/N.kf"));
        assertTrue(nSrc.contains("var v1 = N(arg0)") || nSrc.contains("= N(arg0)"),
                "`new` same-package em statement deve recuperar:\n" + nSrc);
        String mSrc = Files.readString(out.resolve("q/M.kf"));
        assertTrue(mSrc.contains("import p.N"),
                "`new` cross-package deve gerar import:\n" + mSrc);
        assertTrue(mSrc.contains("N(arg0)"),
                "`new` cross-package em statement deve recuperar:\n" + mSrc);
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                out.resolve("p/N.kf"), out.resolve("q/M.kf")), dir.resolve("o"), Target.JVM, out);
        assertTrue(r.success(), "árvore com new cross-package deve compilar:\n" + mSrc + "\n" + r.diagnostics().getDiagnostics());
    }

    @Test
    void recoversArrayCreateAndAccess(@TempDir Path dir) throws Exception {
        // Fase E arrays: `anewarray` + `aaload`/`aastore`/`arraylength`.
        // Nenhum existia nos decoders → stub certo. Elemento ESTRITO
        // (String/Object/domínio; wrappers como Integer[] recusam — `new
        // Int[n]` é int[], semântica distinta). `arr[i]`/`arr[i] = v`/
        // `arr.length` são os idioms Kof (training + probes JVM/script).
        Path src = dir.resolve("classes");
        Path p = src.resolve("p");
        Files.createDirectories(p);
        Path a = p.resolve("A.java");
        Files.writeString(a, """
                package p;
                public class A {
                    public static String first(int n) {
                        String[] arr = new String[n];
                        arr[0] = "hi";
                        String s = arr[0];
                        return s;
                    }
                    public static int len(String[] a) {
                        return a.length;
                    }
                }
                """);
        runJavac(java.util.List.of(a), src);
        String kof = Decompile.decompile(src.resolve("p/A.class"));
        assertTrue(kof.contains("new String[arg0]"),
                "anewarray deve recuperar `new String[n]`:\n" + kof);
        assertTrue(kof.contains("v1[0] = \"hi\"") && kof.contains("v1[0]"),
                "aastore/aaload devem recuperar `a[i] = v` / `a[i]`:\n" + kof);
        assertTrue(kof.contains("arg0.length"),
                "arraylength deve recuperar `a.length`:\n" + kof);
        Path out = dir.resolve("gen");
        Files.createDirectories(out.resolve("p"));
        Files.writeString(out.resolve("p/A.kf"), kof);
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                out.resolve("p/A.kf")), dir.resolve("o"), Target.JVM, out);
        assertTrue(r.success(), "array decompilado deve compilar (zero drift):\n" + kof + "\n" + r.diagnostics().getDiagnostics());
    }

    @Test
    void recoversStatementSwitchAndRunsIt(@TempDir Path dir) throws Exception {
        // Fase C: switch-statement (cases com side-effect + break). Prova
        // FORTE: não basta compilar — executa os 3 caminhos (braço errado
        // silencioso é a pior falha). `break` sai do switch (probe) e o
        // epílogo vai após `}` (dentro executaria o próximo braço).
        Path src = dir.resolve("classes");
        Files.createDirectories(src);
        Path s = src.resolve("S.java");
        Files.writeString(s, """
                public class S {
                    public static String grade(int v) {
                        String r;
                        switch (v) {
                            case 1: r = "one"; break;
                            case 2: r = "two"; break;
                            default: r = "other"; break;
                        }
                        return r;
                    }
                    public static void main(String[] a) {
                        System.out.println(grade(1));
                        System.out.println(grade(2));
                        System.out.println(grade(9));
                    }
                }
                """);
        runJavac(java.util.List.of(s), src);
        String kof = Decompile.decompile(src.resolve("S.class"));
        assertTrue(kof.contains("switch (arg0) {"), "switch deve recuperar:\n" + kof);
        assertTrue(kof.contains("case 1:") && kof.contains("break"),
                "cases com break explícito:\n" + kof);
        Path out = dir.resolve("gen");
        Files.createDirectories(out);
        Path kf = out.resolve("S.kf");
        Files.writeString(kf, kof);
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(kf),
                dir.resolve("o"), Target.JVM, out);
        assertTrue(r.success(), "switch decompilado deve compilar:\n" + kof + "\n" + r.diagnostics().getDiagnostics());
        // Entry = a classe decompilada (main estático dentro de `class S`),
        // não `Default.Main` (que só existe p/ programa Kof de main top-level).
        ProcessBuilder pb = new ProcessBuilder("java", "-cp", dir.resolve("o").toString(), "S");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String o = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "run: " + o);
        assertEquals("one\ntwo\nother", o, "os 3 caminhos devem executar certo:\n" + kof);
    }

    @Test
    void recoversIfThenJoinAndRunsIt(@TempDir Path dir) throws Exception {
        // Fase C (join de if-sem-else): `if (x > 5) { r = r + x }` seguida de
        // sequela — o braço then cai no join (preds {b, then}). O walker atual
        // PARA o braço no join e o struct recusava re-entrar (linha 206),
        // stubando o método INTEIRO. Prova FORTE: executa os 2 caminhos
        // (oracle JVM medido: g(6)=107, g(1)=101) — sem o else o fluxo do
        // braço falso é justamente o que um join errado quebraria.
        Path src = dir.resolve("classes");
        Files.createDirectories(src);
        Path s = src.resolve("G.java");
        Files.writeString(s, """
                public class G {
                    public static int g(int x) {
                        int r = 100;
                        if (x > 5) { r = r + x; }
                        r = r + 1;
                        return r;
                    }
                    public static void main(String[] a) {
                        System.out.println(g(6));
                        System.out.println(g(1));
                    }
                }
                """);
        runJavac(java.util.List.of(s), src);
        String kof = Decompile.decompile(src.resolve("G.class"));
        assertTrue(kof.contains("if (arg0 > 5) {"), "if-sem-else deve recuperar:\n" + kof);
        assertFalse(kof.contains("} else {"), "sem else (join estruturado):\n" + kof);
        Path out = dir.resolve("gen");
        Files.createDirectories(out);
        Path kf = out.resolve("G.kf");
        Files.writeString(kf, kof);
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(kf),
                dir.resolve("o"), Target.JVM, out);
        assertTrue(r.success(), "if-sem-else decompilado deve compilar:\n" + kof + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder("java", "-cp", dir.resolve("o").toString(), "G");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String o = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "run: " + o);
        assertEquals("107\n101", o, "os 2 caminhos do join devem executar certo:\n" + kof);
    }

    @Test
    void hoistsEscapingLocalOutOfIfElseBranchesAndRunsIt(@TempDir Path dir) throws Exception {
        // §238 (Fase C degrau 2c): um local cuja PRIMEIRA escrita fica dentro
        // de um ramo do if-else e e lida DEPOIS do join saia com `var` escopado
        // dentro do if -> o pos-join referenciava um nome nao-declarado e a
        // saida NAO recompilava (SEM000, anti-R6). O hoist icar a declaracao
        // (default por tipo da store: Int->0) ANTES do if. Prova FORTE:
        // recompila E executa os 3 caminhos (oracle JVM medido 10 21 12).
        Path src = dir.resolve("classes");
        Files.createDirectories(src);
        Path s = src.resolve("Hl.java");
        Files.writeString(s, """
                public class Hl {
                    public static int both(int n) {
                        int m = n % 2; int s;
                        if (m == 0) { s = 10; } else { s = 20; }
                        return s + n;
                    }
                }
                """);
        runJavac(java.util.List.of(s), src);
        String kof = Decompile.decompile(src.resolve("Hl.class"));

        assertTrue(kof.contains("var v2 = 0") && kof.contains("if (v1 == 0) {"),
                "declaracao do local escapante deve ser içada p/ antes do if:\n" + kof);
        assertTrue(kof.indexOf("var v2 = 0") < kof.indexOf("if (v1 == 0)"),
                "o var do local vem ANTES do if, nao dentro do ramo:\n" + kof);
        assertFalse(kof.contains("throw \"body not recovered\""), "nao deve stubar:\n" + kof);

        Path out = dir.resolve("gen");
        Files.createDirectories(out);
        Path kf = out.resolve("Hl.kf");
        Files.writeString(kf, kof);
        Path mainKf = out.resolve("Main.kf");
        Files.writeString(mainKf, "main() {\n    println(Hl.both(0))\n    println(Hl.both(1))"
                + "\n    println(Hl.both(2))\n}\n");
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(kf, mainKf),
                dir.resolve("o"), Target.JVM, out);
        assertTrue(r.success(), "içado decompilado deve COMPILAR (era o bug):\n" + kof
                + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder("java", "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String o = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "run: " + o);
        assertEquals("10\n21\n12", o, "os 3 caminhos do join içado:\n" + kof);
    }

    @Test
    void sipushConstantInTestIsRecoveredAndRuns(@TempDir Path dir) throws Exception {
        // §238-face (sipush no loadValue): `if (a == 30000)` usa sipush (const
        // fora do range de bipush). O loadValue nao tratava 0x11 e o teste
        // computado ficava null = stub, APEMBAR que o machineRun ja dobrava
        // (divergencia de passada). Seguranca: so icar o local escapante com o
        // hoist §238 no lugar (sem ele viraria saida nao-compilavel). Prova:
        // decompila `if (arg0 == 30000)` COM o var içado + recompila + roda os
        // 3 caminhos (oracle JVM medido 1/2/1).
        Path src = dir.resolve("classes");
        Files.createDirectories(src);
        Path s = src.resolve("Sp.java");
        Files.writeString(s, """
                public class Sp {
                    public static int big(int a) {
                        int r; if (a == 30000) { r = 1; } else { r = 2; } return r;
                    }
                }
                """);
        runJavac(java.util.List.of(s), src);
        String kof = Decompile.decompile(src.resolve("Sp.class"));

        assertTrue(kof.contains("if (arg0 == 30000)"), "sipush deve dobrar no teste:\n" + kof);
        assertFalse(kof.contains("throw \"body not recovered\""), "nao deve stubar (era o gap):\n" + kof);

        Path out = dir.resolve("gen");
        Files.createDirectories(out);
        Path kf = out.resolve("Sp.kf");
        Files.writeString(kf, kof);
        Path mainKf = out.resolve("Main.kf");
        Files.writeString(mainKf, "main() {\n    println(Sp.big(30000))\n    println(Sp.big(1))\n"
                + "    println(Sp.big(-30000))\n}\n");
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(kf, mainKf),
                dir.resolve("o"), Target.JVM, out);
        assertTrue(r.success(), "sipush içado deve COMPILAR:\n" + kof + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder("java", "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String o = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "run: " + o);
        assertEquals("1\n2\n2", o, "3 caminhos do teste sipush:\n" + kof);
    }

    @Test
    void refLocalEscapingRecoversValuePropagated(@TempDir Path dir) throws Exception {
        // §238 (13/10, evoluído): o local de REFERÊNCIA escrito nos 2 ramos e
        // lido depois AGORA RECUPERA — o valor propagado declara o `var` UMA
        // vez FORA do if/else (`var v2 = if (cond) "a" else "b"`), exatamente
        // o que a face antiga exigia (sem `var` escopado no ramo = o
        // não-compilável do bug §238). Antes: stub honesto (sem default
        // seguro a içar). Zero-drift executado.
        Path src = dir.resolve("classes");
        Files.createDirectories(src);
        Path s = src.resolve("Hr.java");
        Files.writeString(s, """
                public class Hr {
                    public static String ref(int n) {
                        int m = n % 2; String s;
                        if (m == 0) { s = "a"; } else { s = "b"; }
                        return s;
                    }
                }
                """);
        runJavac(java.util.List.of(s), src);
        String kof = Decompile.decompile(src.resolve("Hr.class"));
        assertFalse(kof.contains("body not recovered"),
                "o local de referencia escapante recupera (valor propagado):\n" + kof);
        assertTrue(kof.contains("if ("), kof);
        // o `var` é declarado FORA do ramo (o contrato do §238)
        assertTrue(kof.indexOf("var ") < kof.indexOf("if ("),
                "o var do local escapante fica FORA do if/else:\n" + kof);
        String kofRun = kof + "\nmain() {\n    println(Hr.ref(0))\n    println(Hr.ref(1))\n}\n";
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(
                writeKof(dir, kofRun)), dir.resolve("o"), Target.JVM, dir);
        assertTrue(r.success(), "compila zero drift:\n" + kofRun + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java")
                .toString(), "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, proc.waitFor(), "run exit\n" + output);
        assertEquals("a\nb", output, "zero-drift executado");
    }
    @Test
    void recoversNullNarrowAndRunsIt(@TempDir Path dir) throws Exception {
        // Fase C: ifnull/ifnonnull (0xc6/0xc7) — narrowing CANONICO da
        // linguagem (idiom Null safety; ROI medido: 308 testes sobre load
        // puro no corpus). len vira if-expression ternaria (linear path),
        // nul vira if-sem-else com join (degrau 1). Prova por EXECUCAO dos
        // 4 caminhos (oracle JVM medido 3 0 5 9).
        Path src = dir.resolve("classes");
        Files.createDirectories(src);
        Path s = src.resolve("Nl.java");
        Files.writeString(s, """
                public class Nl {
                    public static int len(String x) { if (x != null) { return x.length(); } return 0; }
                    public static int nul(String x) { int r = 5; if (x == null) { r = 9; } return r; }
                    public static void main(String[] a) { }
                }
                """);
        runJavac(java.util.List.of(s), src);
        String kof = Decompile.decompile(src.resolve("Nl.class"));
        assertTrue(kof.contains("if (arg0 != null)"), "ifnonnull vira `!= null`:\n" + kof);
        assertTrue(kof.contains("if (arg0 == null) {"), "ifnull vira `== null`:\n" + kof);
        assertFalse(kof.contains("body not recovered"), "ambos recuperados:\n" + kof);
        Path out = dir.resolve("gen");
        Files.createDirectories(out);
        Path kf = out.resolve("Nl.kf");
        Files.writeString(kf, kof);
        Path mainKf = out.resolve("Main.kf");
        Files.writeString(mainKf, "main() {\n    println(Nl.len(\"abc\"))\n    println(Nl.len(null))\n    println(Nl.nul(\"x\"))\n    println(Nl.nul(null))\n}\n");
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(kf, mainKf),
                dir.resolve("o"), Target.JVM, out);
        assertTrue(r.success(), "narrowing decompilado deve compilar:\n" + kof + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder("java", "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String o = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "run: " + o);
        assertEquals("3\n0\n5\n9", o, "os 4 caminhos do narrow:\n" + kof);
    }

    @Test
    void recoversContinueAsEmptyThenJoinAndRunsIt(@TempDir Path dir) throws Exception {
        // Fase C degrau 2a: `for` com `continue` vira while + if de condicao
        // INVERTIDA com then VAZIO (o continue pula o corpo; o incremento
        // fica na sequela do join — preservado nos DOIS caminhos). Medido
        // por execucao 13/09: contFor(0..6) = 0 0 1 3 3 7 12 == oracle JVM.
        // (A variante `i % 2 == 0` fica em stub honesto — blockCondition nao
        // recupera test-expr com calculo; diamondJoinShapesStayHonestStub.)
        Path src = dir.resolve("classes");
        Files.createDirectories(src);
        Path s = src.resolve("Jn.java");
        Files.writeString(s, """
                public class Jn {
                    public static int contFor(int n) {
                        int s = 0;
                        for (int i = 0; i < n; i++) { if (i == 3) continue; s = s + i; }
                        return s;
                    }
                    public static void main(String... args) { }
                }
                """.replace("String... args", "String[] a"));
        runJavac(java.util.List.of(s), src);
        String kof = Decompile.decompile(src.resolve("Jn.class"));
        assertTrue(kof.contains("while (") , "for vira while:\n" + kof);
        assertFalse(kof.contains("throw \"body not recovered\""), "corpo recuperado:\n" + kof);
        Path out = dir.resolve("gen");
        Files.createDirectories(out);
        Path kf = out.resolve("Jn.kf");
        Files.writeString(kf, kof);
        Path mainKf = out.resolve("Main.kf");
        StringBuilder calls = new StringBuilder("main() {\n");
        for (int n = 0; n < 7; n++) calls.append("    println(Jn.contFor(").append(n).append("))\n");
        calls.append("}\n");
        Files.writeString(mainKf, calls.toString());
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(kf, mainKf),
                dir.resolve("o"), Target.JVM, out);
        assertTrue(r.success(), "decompilado deve compilar:\n" + kof + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder("java", "-cp", dir.resolve("o").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String o = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "run: " + o);
        assertEquals("0\n0\n1\n3\n3\n7\n12", o, "continue==then vazio, incremento no join:\n" + kof);
    }

    @Test
    void recoversIfElseWithTailJoinAndRunsIt(@TempDir Path dir) throws Exception {
        // Fase C degrau 2a: if-else LINEAR com sequela pos-if (join P com
        // preds exatos {then,else}). Hoje o naive path anda o then p/ dentro
        // do join e a recusa 214 stuba o metodo INTEIRO (E.class stubou
        // honesto na medicao 13/09 — nunca codigo errado). Prova FORTE:
        // executa os 2 caminhos (oracle JVM medido 13/14) — o caminho do
        // else e justamente o que um join duplicado/perdido quebraria.
        Path src = dir.resolve("classes");
        Files.createDirectories(src);
        Path s = src.resolve("E.java");
        Files.writeString(s, """
                public class E {
                    public static int e(int a) {
                        int r = 1;
                        if (a > 0) { r = r + 2; } else { r = r + 3; }
                        return r + 10;
                    }
                    public static void main(String[] a) { }
                }
                """.replace("String[]", "String[]"));
        runJavac(java.util.List.of(s), src);
        String kof = Decompile.decompile(src.resolve("E.class"));
        assertTrue(kof.contains("} else {"), "if-else deve recuperar:\n" + kof);
        Path out = dir.resolve("gen");
        Files.createDirectories(out);
        Path kf = out.resolve("E.kf");
        Files.writeString(kf, kof);
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(kf),
                dir.resolve("o"), Target.JVM, out);
        assertTrue(r.success(), "if-else decompilado deve compilar:\n" + kof + "\n" + r.diagnostics().getDiagnostics());
        Path mainKf = out.resolve("Main.kf");
        Files.writeString(mainKf, "main() {\n    println(E.e(1))\n    println(E.e(-1))\n}\n");
        CompilationResult r2 = new CompilerDriver().compileSources(java.util.List.of(kf, mainKf),
                dir.resolve("o2"), Target.JVM, out);
        assertTrue(r2.success(), "programa deve compilar:\n" + r2.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder("java", "-cp", dir.resolve("o2").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String o = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "run: " + o);
        assertEquals("13\n14", o, "os 2 caminhos do join if-else:\n" + kof);
    }

    @Test
    void recoversIfThenChainAndRunsIt(@TempDir Path dir) throws Exception {
        // Fase C degrau 1 (Q3 idempotencia/irmas): DOIS if-sem-else seguidos.
        // Prova que a borda de um naoo vaza p/ o irmao (stops consumido
        // localmente): os 4 caminhos executam (oracle JVM medido 6/4/5/3).
        Path src = dir.resolve("classes");
        Files.createDirectories(src);
        Path s = src.resolve("C.java");
        Files.writeString(s, """
                public class C {
                    public static int c(int a, int b) {
                        int x = 0;
                        if (a > 0) { x = x + 1; }
                        if (b > 0) { x = x + 2; }
                        x = x + 3;
                        return x;
                    }
                    public static void main(String[] a) {
                        System.out.println(c(1, 1));
                        System.out.println(c(1, 0));
                        System.out.println(c(0, 1));
                        System.out.println(c(0, 0));
                    }
                }
                """);
        runJavac(java.util.List.of(s), src);
        String kof = Decompile.decompile(src.resolve("C.class"));
        assertTrue(kof.contains("if (arg0 > 0) {") && kof.contains("if (arg1 > 0) {"),
                "os dois ifs devem recuperar:\n" + kof);
        assertFalse(kof.contains("} else {"), "nenhum com else (joins puros):\n" + kof);
        Path out = dir.resolve("gen");
        Files.createDirectories(out);
        Path kf = out.resolve("C.kf");
        Files.writeString(kf, kof);
        CompilationResult r = new CompilerDriver().compileSources(java.util.List.of(kf),
                dir.resolve("o"), Target.JVM, out);
        assertTrue(r.success(), "corrente decompilada deve compilar:\n" + kof + "\n" + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder("java", "-cp", dir.resolve("o").toString(), "C");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String o = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "run: " + o);
        assertEquals("6\n4\n5\n3", o, "os 4 caminhos da corrente:\n" + kof);
    }

    @Test
    void nestedIfWithoutElseStaysHonestStub(@TempDir Path dir) throws Exception {
        // Fase C (Q4): o if-sem-else NAO-puro (then com sequela propria +
        // join externo) stuba HONESTO. Medido 13/09: a variante ingenua
        // (borda de stop tambem no braco do else) produzia CODIGO ERRADO
        // COMPILAVEL — a sequela do pos-if era sugada p/ dentro do else e o
        // caminho falso pulava statements. Recusar > errar (R6/portao Q0).
        Path src = dir.resolve("classes");
        Files.createDirectories(src);
        Path s = src.resolve("N2.java");
        Files.writeString(s, """
                public class N2 {
                    public static int n(int a, int b) {
                        int x = 0;
                        if (a > 0) {
                            if (b > 0) { x = x + 1; }
                            x = x + 2;
                        }
                        x = x + 3;
                        return x;
                    }
                }
                """);
        runJavac(java.util.List.of(s), src);
        String kof = Decompile.decompile(src.resolve("N2.class"));
        assertTrue(kof.contains("throw \"body not recovered\""),
                "if-aninhado sem else deve stubar honesto (nao virar codigo errado):\n" + kof);
    }

    @Test
    void switchFallthroughStaysHonestStub(@TempDir Path dir) throws Exception {
        // Kof não tem fallthrough: case sem `break` caindo no próximo braço
        // NÃO tem forma válida → stub honesto (nunca código errado).
        Path src = dir.resolve("classes");
        Files.createDirectories(src);
        Path s = src.resolve("F.java");
        Files.writeString(s, """
                public class F {
                    public static String grade(int v) {
                        String r;
                        switch (v) {
                            case 1: r = "one";
                            case 2: r = "two"; break;
                            default: r = "other"; break;
                        }
                        return r;
                    }
                }
                """);
        runJavac(java.util.List.of(s), src);
        String kof = Decompile.decompile(src.resolve("F.class"));
        assertTrue(kof.contains("body not recovered"),
                "fallthrough (case 1 sem break) deve ficar stub:\n" + kof);
        assertTrue(!kof.contains("switch ("), "nenhum switch parcial:\n" + kof);
    }

    @Test
    void escapesStringConstantsInDecompiledSource(@TempDir Path dir) throws Exception {
        // R6 (prova de drift 09/09): o ldc emitia a string do CP CRUA — `\b`,
        // newline real e `"` estouravam o lexer do .kf (LEX002/LEX004). Agora
        // escape canônico (BytecodeConcat); round-trip: decompilar → compilar.
        Path javaFile = dir.resolve("E.java");
        Files.writeString(javaFile, """
                public class E {
                    public static String s1() { return "a\\\\b"; }
                    public static String s2() { return "q\\"t"; }
                    public static String s3() { return "nl\\nend"; }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("E.class"));
        assertTrue(kof.contains("\"a\\\\b\""), "barra dupla escapada:\n" + kof);
        assertTrue(kof.contains("\"q\\\"t\""), "aspas escapadas:\n" + kof);
        assertTrue(kof.contains("\"nl\\nend\""), "newline escapado:\n" + kof);
        Path out = dir.resolve("E.kf");
        Files.writeString(out, kof);
        CompilationResult result = new CompilerDriver().compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "decompiled deve compilar:\n" + kof + "\n" + result.diagnostics().getDiagnostics());
    }

    @Test
    void whitelistInstanceofRecoversAndCompiles(@TempDir Path dir) throws Exception {
        // Fase E (09/09): `x instanceof String` é idiomático Kof e o nome
        // resolve SEM import — seguro recuperar. Tipo de domínio fica stub
        // (prova de drift: `instanceof DiagnosticCollector` gerava .kf que não
        // compila; whitelist = String/primitivos/Object).
        Path javaFile = dir.resolve("Is.java");
        Files.writeString(javaFile, """
                public class Is {
                    public static boolean isStr(Object o) {
                        boolean b = o instanceof String;
                        return b;
                    }
                    public static boolean isCs(Object o) {
                        boolean b = o instanceof CharSequence;
                        return b;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Is.class"));
        assertTrue(kof.contains("arg0 instanceof String"),
                "instanceof String (whitelist) deve recuperar:\n" + kof);
        assertTrue(kof.contains("isCs(Object arg0) {\n        throw \"body not recovered\""),
                "instanceof CharSequence (domínio/JDK não-whitelist) fica stub:\n" + kof);
        Path out = dir.resolve("Is.kf");
        Files.writeString(out, kof);
        CompilationResult result = new CompilerDriver().compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "decompiled deve compilar:\n" + kof + "\n" + result.diagnostics().getDiagnostics());
    }

    @Test
    void whitelistCastRecoversPrimitiveTargetsOnly(@TempDir Path dir) throws Exception {
        // checkcast p/ primitivo vira `(x as Int)` (útil p/ downcast de Object);
        // p/ String/Object é recusado (cast trivial/ambíguo na verificação).
        Path javaFile = dir.resolve("Cs.java");
        Files.writeString(javaFile, """
                public class Cs {
                    public static int take(Object o) {
                        Integer i = (Integer) o;
                        int v = i;
                        return v;
                    }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Cs.class"));
        assertTrue(kof.contains("as Int"), "checkcast Integer → `as Int`:\n" + kof);
        assertFalse(kof.contains("intValue"), "#362: unbox do wrapper = identidade — nunca emitir `x.intValue()` (SEM074):\n" + kof);
        Path out = dir.resolve("Cs.kf");
        Files.writeString(out, kof);
        CompilationResult result = new CompilerDriver().compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "decompiled deve compilar:\n" + kof + "\n" + result.diagnostics().getDiagnostics());
    }

    @Test
    void invokedynamicIsFiveBytes() {
        // REGRESSÃO PEGO POR TRUNCATION-MARKER: length(0xba) era 7 —
        // INVÁLIDO (JVMS 4.9.3: opcode + u2 index + 2 zero-bytes = 5). Com 7,
        // o pc saltava a instrução SEGUINTE (o `areturn`): o teste do concat
        // passava por ACIDENTE (caía no fallback "fim sem return → topo").
        // Bytes reais de javac: `lload_0; invokedynamic #7; areturn`
        // → offsets 0,1..5,6. Com len=5, o 0xb0 em offset 6 É alcançado.
        String[] cp = new String[9];
        cp[7] = "CONCAT:v=\u000142";
        byte[] code = {0x1e, (byte) 0xba, 0x00, 0x07, 0x00, 0x00, (byte) 0xb0};
        String e = BytecodeDecoder.recoverExpression(code, cp,
                new BytecodeFrame("(J)Ljava/lang/String;", true));
        // o long vira toString no concat do javac; com o índice correto o
        // decoder enxerga o areturn e recupera o valor (não o fallback):
        assertEquals("\"v=\" + arg0 + \"42\"", e, "invokedynamic len=5: areturn alcançável:\n" + e);
    }

    @Test
    void truncatedLastInstructionBecomesHonestStub() {
        // BytecodeReader nunca lança em código truncado: marcador → decoder
        // default → null → stub honesto (ferramenta sobre .class real).
        String[] cp = new String[0];
        byte[] trunc = {0x1a, 0x13, 0x01};   // iload_0; ldc_w com índice incompleto
        assertNull(BytecodeDecoder.recoverExpression(trunc, cp,
                new BytecodeFrame("()I", true)), "truncado → null (stub), nunca exceção");
    }

    @Test
    void wideIincConsumesSixBytes() {
        // skipVariable comparava op == 0x84 — IMPOSSÍVEL (o op é 0xc4; 0x84 é
        // o SUB-opcode lido depois). Consequência: `wide iinc` (6 bytes) era
        // pulado como 3 e TODO opcode seguinte driftava (ldc/invokes errados
        // ou truncamento fantasma). wide normal = 4; wide iinc = 6.
        byte[] code = {(byte) 0xc4, (byte) 0x84, 0x00, 0x65, 0x00, 0x03, (byte) 0xb1};
        var insns = BytecodeReader.decode(code);
        // wide é OPAQUE (1 insn, skipVariable consome os 6) + return = 2
        assertEquals(2, insns.size());
        assertEquals(6, insns.get(1).offset(), "return deve começar em 6 (não 3+1!)");
        // wide SEM iinc (iinc = 4 bytes)
        var w = BytecodeReader.decode(new byte[]{(byte) 0xc4, 0x15, 0x01, 0x23, (byte) 0xac});
        assertEquals(2, w.size());
        assertEquals(4, w.get(1).offset());
    }

    @Test
    void wideParamsMapToCorrectSlots(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("V.java");
        Files.writeString(javaFile, """
                public class V {
                    public static int m(long x, int a, int b) { return a < b ? 1 : 0; }
                    public static long twoLongs(long a, long b) { return a; }
                    public static double sq(double x) { double y = x; return y; }
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("V.class"));

        // R6 (lição bug 62): Long/Double ocupam DOIS slots (JVMS 2.6.1). Com
        // mapeamento de 1-slot, `iload_2` (segundo int, slot 2) virava nome de
        // parâmetro ERRADO (arg2) / local inexistente (v3) → decompilado que
        // NÃO compila (SEM011). BytecodeFrame resolve pelo descriptor.
        // §236 (14/09): corpo Int com shape cmp/iconst_1/goto/iconst_0/ireturn
        // (ternario `a < b ? 1 : 0` do javac) NAo pode dobrar p/ Bool cru
        // (SEM010, nao-compilavel) — o pino antigo guardava a saida errada que o
        // compilador tolerava; agora = if-expression de inteiros, e o teste
        // compila a saida (a linha 1230 sempre tentou — era o pino que estava
        // errado, nao o fold).
        assertTrue(kof.contains("Int m(Long arg0, Int arg1, Int arg2) = if (arg1 < arg2) 1 else 0"),
                "wide long empurra slots + ternario int (nao Bool cru):\n" + kof);
        assertFalse(kof.contains(" v3") && kof.contains("Int m("),
                "slot 2 não é mais arg0-shift:\n" + kof);
        // lload_0 devolve arg0 (não v0): corpo de 2 slots wide
        assertTrue(kof.contains("Long twoLongs(Long arg0, Long arg1) = arg0"),
                "lload_0 = arg0:\n" + kof);
        // dstore_2 (0x49): slot = (op-0x3f)%4 = 2 (o bug antigo dava slot 9→v10
        // inconsistente com dload_1); y local no slot 2 (arg0 wide=0,1)
        assertTrue(kof.contains("Double sq(Double arg0)") && kof.contains("var v2 = arg0")
                        && kof.contains("return v2"),
                "dstore_2 → slot 2:\n" + kof);

        Path out = dir.resolve("V.kf");
        Files.writeString(out, kof);
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "decompiled deve compilar:\n" + kof + "\n" + result.diagnostics().getDiagnostics());
    }

    @Test
    void recoversLongDoubleArithmeticAndCasts(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Num.java");
        Files.writeString(javaFile, """
                public class Num {
                    public static long ladd(long a, long b) { return a + b; }
                    public static long ldiv(long a, long b) { return a / b; }
                    public static double dmul(double a, double b) { return a * b; }
                    public static long i2l(int a) { return a; }
                    public static int l2i(long a) { return (int) a; }
                    public static int d2i(double a) { return (int) a; }
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("Num.class"));

        // Unit B (Fase C/E): ladd/ldiv/dmul e casts têm opcode que é a FONTE do
        // tipo (verificador JVM garante); a pilha valor+tipo (BytecodeTypes.TStack)
        // só emite quando os operandos/retorno batem (lição bug 62: opcode
        // "linear" ainda pode driftar). / long trunc, casts truncam p/ Int.
        assertTrue(kof.contains("Long ladd(Long arg0, Long arg1) = (arg0 + arg1)"),
                "ladd:\n" + kof);
        assertTrue(kof.contains("Long ldiv(Long arg0, Long arg1) = (arg0 / arg1)"),
                "ldiv:\n" + kof);
        assertTrue(kof.contains("Double dmul(Double arg0, Double arg1) = (arg0 * arg1)"),
                "dmul:\n" + kof);
        assertTrue(kof.contains("Long i2l(Int arg0) = (arg0 as Long)"), "i2l:\n" + kof);
        assertTrue(kof.contains("Int l2i(Long arg0) = (arg0 as Int)"), "l2i:\n" + kof);
        assertTrue(kof.contains("Int d2i(Double arg0) = (arg0 as Int)"), "d2i:\n" + kof);
        assertFalse(kof.contains("body not recovered"), "nenhum deve degradar:\n" + kof);

        Path out = dir.resolve("Num.kf");
        Files.writeString(out, kof);
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "decompiled deve compilar:\n" + kof + "\n" + result.diagnostics().getDiagnostics());
    }

    @Test
    void mixedTypeAndLoopShapesRecoverOrDegradeHonest(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("S2.java");
        Files.writeString(javaFile, """
                public class S2 {
                    public static long mixed(long a, int b) { return a + b; }
                    public static int nestCast(long a, long b) { return (int) (a + b); }
                    public static String longStr(long a) { return "v" + a; }
                    public static int callRet(int a) { return a + Math.abs(a); }
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("S2.class"));

        // R6 sweep pós-Unit B: widening implícito de Java (long + int) é
        // i2l+ladd no bytecode — recupera como `(arg0 + (arg1 as Long))`
        // (cast explícito, tipo bate p/ o ladd). Cast aninhado e concat de
        // long (invokedynamic) preservam o tipo. Não drifta (lição 62).
        assertTrue(kof.contains("Long mixed(Long arg0, Int arg1) = (arg0 + (arg1 as Long))"),
                "widening long+int:\n" + kof);
        assertTrue(kof.contains("Int nestCast(Long arg0, Long arg1) = ((arg0 + arg1) as Int)"),
                "cast aninhado:\n" + kof);
        assertTrue(kof.contains("String longStr(Long arg0) = \"v\" + arg0"),
                "concat de long (invokedynamic):\n" + kof);
        assertTrue(kof.contains("(arg0 + math.abs(arg0))"),
                "Math.abs (I)I → math.abs — sem owner JDK (R6: SEM011):\n" + kof);

        Path out = dir.resolve("S2.kf");
        Files.writeString(out, kof);
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "decompiled deve compilar:\n" + kof + "\n" + result.diagnostics().getDiagnostics());
    }

    @Test
    void recoversMethodCall(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Call.java");
        Files.writeString(javaFile, """
                public class Call {
                    public int add(int a, int b) { return a + b; }
                    public int add4(int x) { return add(x, x); }
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("Call.class"));

        assertTrue(kof.contains("add4(Int arg0) = this.add(arg0, arg0)"),
                "add4 deve virar chamada de método:\n" + kof);

        Path out = dir.resolve("Call.kf");
        Files.writeString(out, kof);
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "decompiled deve compilar:\n" + kof + "\n" + result.diagnostics().getDiagnostics());
    }

    @Test
    void recoversCharCastButStaysHonestOnByteShort(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Cast.java");
        Files.writeString(javaFile, """
                public class Cast {
                    public static char toChar(int a) { return (char) (a + 65); }
                    public static byte toByte(int a) { return (byte) a; }
                    public static short toShort(int a) { return (short) a; }
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("Cast.class"));

        // i2c → `as Char`: ÚNICO narrowing de Kof fiel ao Java (o codegen do
        // Kof emite i2c real p/ `x as Char`, wrap 16 bits igual (char)).
        assertTrue(kof.contains("Char toChar(Int arg0) = ((arg0 + 65) as Char)"),
                "i2c deve recuperar as Char:\n" + kof);
        // i2b/i2s NÃO têm equivalente fiel: Kof `as Byte`/`as Short` são no-op
        // (256 as Byte = 256 ≠ (byte) 256 = 0). Recusa → stub honesto (R6).
        assertTrue(kof.contains("Byte toByte(Int arg0) {\n        throw \"body not recovered\""),
                "i2b deve ficar stub (sem cast fiel em Kof):\n" + kof);
        assertTrue(kof.contains("Short toShort(Int arg0) {\n        throw \"body not recovered\""),
                "i2s deve ficar stub (sem cast fiel em Kof):\n" + kof);

        Path out = dir.resolve("Cast.kf");
        Files.writeString(out, kof);
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "decompiled deve compilar:\n" + kof + "\n" + result.diagnostics().getDiagnostics());
    }

    @Test
    void recoversFieldAccess(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Box.java");
        Files.writeString(javaFile, """
                public class Box {
                    int value;
                    public int getValue() { return value; }
                    public void setValue(int v) { value = v; }
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("Box.class"));

        assertTrue(kof.contains("getValue() = this.value"), "getfield deve virar this.value:\n" + kof);
        assertTrue(kof.contains("this.value = arg0"), "putfield deve virar this.value = arg0:\n" + kof);

        Path out = dir.resolve("Box.kf");
        Files.writeString(out, kof);
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "decompiled deve compilar:\n" + kof + "\n" + result.diagnostics().getDiagnostics());
    }

    @Test
    void recoversObjectCreation(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Node.java");
        Files.writeString(javaFile, """
                public class Node {
                    public Node make() { return new Node(); }
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("Node.class"));

        assertTrue(kof.contains("make() = Node()"), "new deve virar Node():\n" + kof);

        Path out = dir.resolve("Node.kf");
        Files.writeString(out, kof);
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "decompiled deve compilar:\n" + kof + "\n" + result.diagnostics().getDiagnostics());
    }

    @Test
    void recoversTryCatch(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Safe.java");
        Files.writeString(javaFile, """
                public class Safe {
                    public static int div(int a, int b) {
                        try { return a / b; }
                        catch (ArithmeticException e) { return 0; }
                    }
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("Safe.class"));

        assertTrue(kof.contains("try {"), "deve ter try:\n" + kof);
        assertTrue(kof.contains("return (arg0 / arg1)"), "try deve retornar divisão:\n" + kof);
        assertTrue(kof.contains("} catch (String e) {"), "deve ter catch String:\n" + kof);
        assertTrue(kof.contains("return 0"), "catch deve retornar 0:\n" + kof);
        assertFalse(kof.contains("throw \"body not recovered\""), "não deve ter stub:\n" + kof);

        Path out = dir.resolve("Safe.kf");
        Files.writeString(out, kof);
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "decompiled deve compilar:\n" + kof + "\n" + result.diagnostics().getDiagnostics());
    }

    @Test
    void recoversSwitch(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Sw.java");
        Files.writeString(javaFile, """
                public class Sw {
                    public static int sign(int n) {
                        switch (n) {
                            case 0: return 5;
                            case 1: return 7;
                            default: return 9;
                        }
                    }
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("Sw.class"));

        assertTrue(kof.contains("switch (arg0)"), "deve ter switch:\n" + kof);
        assertTrue(kof.contains("case 0: return 5"), "case 0:\n" + kof);
        assertTrue(kof.contains("case 1: return 7"), "case 1:\n" + kof);
        assertTrue(kof.contains("default: return 9"), "default:\n" + kof);
        assertFalse(kof.contains("throw \"body not recovered\""), "não deve ter stub:\n" + kof);

        Path out = dir.resolve("Sw.kf");
        Files.writeString(out, kof);
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "decompiled deve compilar:\n" + kof + "\n" + result.diagnostics().getDiagnostics());
    }

    @Test
    void recoversFinally(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Ctr.java");
        Files.writeString(javaFile, """
                public class Ctr {
                    int calls;
                    public int work(int n) {
                        try { return n * 2; }
                        finally { calls = calls + 1; }
                    }
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("Ctr.class"));

        assertTrue(kof.contains("try {"), "deve ter try:\n" + kof);
        assertTrue(kof.contains("return (arg0 * 2)"), "try deve retornar expressão:\n" + kof);
        assertTrue(kof.contains("} finally {"), "deve ter finally:\n" + kof);
        assertTrue(kof.contains("this.calls = (this.calls + 1)"), "finally deve incrementar campo:\n" + kof);
        assertFalse(kof.contains("throw \"body not recovered\""), "não deve ter stub:\n" + kof);

        Path out = dir.resolve("Ctr.kf");
        Files.writeString(out, kof);
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "decompiled deve compilar:\n" + kof + "\n" + result.diagnostics().getDiagnostics());
    }

    @Test
    void mapsStdlib(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Std.java");
        Files.writeString(javaFile, """
                public class Std {
                    public static void hello() { System.out.println("hi"); }
                    public static int size(String s) { return s.length(); }
                    public static boolean same(String a, String b) { return a.equals(b); }
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("Std.class"));

        assertTrue(kof.contains("println(\"hi\")"), "System.out.println deve virar println:\n" + kof);
        assertFalse(kof.contains("System.out"), "não deve manter System.out:\n" + kof);
        assertTrue(kof.contains("arg0.length"), "String.length() deve virar .length:\n" + kof);
        assertTrue(kof.contains("arg0 == arg1"), "equals deve virar ==:\n" + kof);

        Path out = dir.resolve("Std.kf");
        Files.writeString(out, kof);
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "decompiled deve compilar:\n" + kof + "\n" + result.diagnostics().getDiagnostics());
    }

    @Test
    void mapsCollectionSize(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Col.java");
        Files.writeString(javaFile, """
                import java.util.List;
                public class Col {
                    public static int count(List<String> xs) { return xs.size(); }
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("Col.class"));

        assertTrue(kof.contains("arg0.size"), "size() vira propriedade .size:\n" + kof);
        assertFalse(kof.contains("arg0.size()"), "não deve manter size():\n" + kof);
    }

    @Test
    void mapsParseAndClock(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Parse.java");
        Files.writeString(javaFile, """
                public class Parse {
                    public static int toN(String s) { return Integer.parseInt(s); }
                    public static long when() { return System.currentTimeMillis(); }
                }
                """);
        runJavac(javaFile, dir);

        String kof = Decompile.decompile(dir.resolve("Parse.class"));

        assertTrue(kof.contains("arg0.toInt()"), "Integer.parseInt vira .toInt():\\n" + kof);
        assertTrue(kof.contains("now()"), "currentTimeMillis vira now():\\n" + kof);
    }

    @Test
    void recoversPureJavaRecordAsKofRecord(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Point.java");
        Files.writeString(javaFile, """
                public record Point(int x, int y) { }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Point.class"));
        assertTrue(kof.contains("record Point(Int x, Int y)"), "record puro → record Kof:\n" + kof);
        assertFalse(kof.contains("extends Record"), "sem esqueleto class+extends:\n" + kof);
        assertFalse(kof.contains("body not recovered"), "sem stub sintético:\n" + kof);
        Path out = dir.resolve("Point.kf");
        Files.writeString(out, kof);
        CompilationResult result = new CompilerDriver().compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "decompiled deve compilar:\n" + kof + "\n" + result.diagnostics().getDiagnostics());
    }

    @Test
    void recoversPureRecordWithGenericsAndObjects(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Bag2.java");
        Files.writeString(javaFile, """
                import java.util.List;
                public record Bag2(List<String> items, String label) { }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Bag2.class"));
        assertTrue(kof.contains("record Bag2(List<String> items, String label)"),
                "componentes genéricos EXACT:\n" + kof);
        assertFalse(kof.contains("body not recovered"), "sem stub:\n" + kof);
        Path out = dir.resolve("Bag2.kf");
        Files.writeString(out, kof);
        CompilationResult result = new CompilerDriver().compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "decompiled deve compilar:\n" + kof + "\n" + result.diagnostics().getDiagnostics());
    }

    @Test
    void extraMethodInRecordKeepsHonestSkeleton(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Twice.java");
        Files.writeString(javaFile, """
                public record Twice(int x) {
                    public int twice() { return x * 2; }
                }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Twice.class"));
        // método extra (não-acessor) → não-puro → esqueleto de hoje (zero drift)
        assertTrue(kof.contains("class Twice extends Record"), "extra → skeleton atual:\n" + kof);
        assertTrue(kof.contains("Int twice() = (this.x * 2)"), "método extra ainda recuperado como body:\n" + kof);
    }

    @Test
    void reservedComponentNameKeepsHonestSkeleton(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("ValR.java");
        Files.writeString(javaFile, """
                public record ValR(int val) { }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("ValR.class"));
        // `val` é reservada no frontend (PARSE015) → emitir record DRIFTARIA;
        // deve permanecer no skeleton atual (honesto, compila).
        assertTrue(kof.contains("class ValR extends Record"), "nome reservado → skeleton atual:\n" + kof);
    }

    @Test
    void recordWithInterfaceKeepsHonestSkeleton(@TempDir Path dir) throws Exception {
        Path iface = dir.resolve("Named.java");
        Files.writeString(iface, "public interface Named { String name(); }\n");
        Path javaFile = dir.resolve("NamedR.java");
        Files.writeString(javaFile, """
                public record NamedR(String name) implements Named { }
                """);
        runJavac(java.util.List.of(iface, javaFile), dir);
        String kof = Decompile.decompile(dir.resolve("NamedR.class"));
        // `record X implements Y` → PARSE007 no frontend (probe 13/09) → skeleton atual
        assertTrue(kof.contains("class NamedR extends Record"), "implements → skeleton atual:\n" + kof);
    }

    @Test
    void recoversGenericRecordWithExactTypeParams(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Gp.java");
        Files.writeString(javaFile, """
                public record Gp<T>(T value, int tag) { }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Gp.class"));
        assertTrue(kof.contains("record Gp<T>(T value, Int tag)"),
                "type-param EXATO na forma Kof:\n" + kof);
        assertFalse(kof.contains("body not recovered"), "sem stub:\n" + kof);
        Path out = dir.resolve("Gp.kf");
        Files.writeString(out, kof);
        CompilationResult result = new CompilerDriver().compile(out, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "decompiled deve compilar:\n" + kof + "\n" + result.diagnostics().getDiagnostics());
    }

    @Test
    void genericBoundRecordKeepsHonestSkeleton(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Bnd.java");
        Files.writeString(javaFile, """
                public record Bnd<T extends Comparable<T>>(T value) { }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("Bnd.class"));
        // bound genérico (`<T:Ljava/lang/Comparable<...>;>`) não é a forma que
        // emitimos: RECUSAR (skeleton atual, compila) — nunca `record Bnd<T>` errado.
        assertTrue(kof.contains("class Bnd extends Record"), "bound não-suportado → skeleton atual:\n" + kof);
    }

    @Test
    void recoversRecordImplementingSamePackageInterface(@TempDir Path dir) throws Exception {
        Path root = dir.resolve("classes");
        Files.createDirectories(root);
        Path iface = root.resolve("Named.java");
        Files.writeString(iface, "public interface Named { String name(); }\n");
        Path javaFile = root.resolve("NamedR.java");
        Files.writeString(javaFile, "public record NamedR(String name) implements Named { }\n");
        runJavac(java.util.List.of(iface, javaFile), root);
        Path out = dir.resolve("gen");
        Decompile.decompileTree(root, out);
        String kof = Files.readString(out.resolve("NamedR.kf"));
        assertTrue(kof.contains("record NamedR(String name) implements Named"),
                "interface top do MESMO pacote resolve (#325: forma Kof canonical = comps ANTES do implementsntes):\n" + kof);
        assertFalse(kof.contains("body not recovered"), "sem stub:\n" + kof);
        CompilationResult r = new CompilerDriver().compileSources(
                List.of(out.resolve("NamedR.kf").toAbsolutePath().normalize(),
                        out.resolve("Named.kf").toAbsolutePath().normalize()),
                dir.resolve("kout"), Target.JVM, out);
        assertTrue(r.success(), "record implements irmão deve compilar junto:\n" + kof + "\n" + r.diagnostics().getDiagnostics());
    }

    @Test
    void recoversRecordWithInterfaceFromTreeScope(@TempDir Path dir) throws Exception {
        Path root = dir.resolve("classes");
        Path p1 = root.resolve("a");
        Path p2 = root.resolve("b");
        Files.createDirectories(p1);
        Files.createDirectories(p2);
        Path iface = p1.resolve("Iface.java");
        Files.writeString(iface, "package a; public interface Iface { String name(); }\n");
        Path rec = p2.resolve("CrossR.java");
        Files.writeString(rec, "package b;\nimport a.Iface;\npublic record CrossR(String name) implements Iface { }\n");
        runJavac(java.util.List.of(iface, rec), root);
        Path out = dir.resolve("gen");
        Decompile.decompileTree(root, out);
        String kof = Files.readString(out.resolve("b/CrossR.kf"));
        assertTrue(kof.contains("record CrossR(String name) implements Iface"),
                "cross-package com import (#325 forma canonical):\n" + kof);
        assertTrue(kof.contains("import a.Iface"), "import emitido:\n" + kof);
        assertFalse(kof.contains("body not recovered"), "sem stub:\n" + kof);
        CompilationResult r = new CompilerDriver().compileSources(
                List.of(out.resolve("b/CrossR.kf").toAbsolutePath().normalize(),
                        out.resolve("a/Iface.kf").toAbsolutePath().normalize()),
                dir.resolve("out"), Target.JVM, out);
        assertTrue(r.success(), "árvore cross-pkg compila:\n" + kof + "\n" + r.diagnostics().getDiagnostics());
    }

    @Test
    void recordWithOutOfTreeInterfaceKeepsHonestSkeleton(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("SerR.java");
        Files.writeString(javaFile, """
                import java.io.Serializable;
                public record SerR(int x) implements Serializable { }
                """);
        runJavac(javaFile, dir);
        String kof = Decompile.decompile(dir.resolve("SerR.class"));
        // JDK fora da árvore → `implements Serializable` é SEM015/PKG006 = drift
        // → skeleton atual (compila hoje; Serializable era resolveSuperName fallback)
        assertTrue(kof.contains("class SerR extends Record"), "fora-da-árvore → skeleton:\n" + kof);
    }

    @Test
    void recoversRecordImplementingSamePackageInnerInterface(@TempDir Path dir) throws Exception {
        Path root = dir.resolve("classes");
        Files.createDirectories(root);
        Path outer = root.resolve("Box.java");
        Files.writeString(outer, """
                public class Box {
                    public interface Expr { }
                }
                """);
        Path rec = root.resolve("NumR.java");
        Files.writeString(rec, "public record NumR(int v) implements Box.Expr { }\n");
        runJavac(java.util.List.of(outer, rec), root);
        Path out = dir.resolve("gen");
        Decompile.decompileTree(root, out);
        String numr = Files.readString(out.resolve("NumR.kf"));
        assertTrue(numr.contains("record NumR(Int v) implements Box$Expr"),
                "interna do MESMO pacote: frontend aceita nome com `$` como top (#325 forma canonical):\n" + numr);
        assertFalse(numr.contains("body not recovered"), "sem stub:\n" + numr);
        CompilationResult r = new CompilerDriver().compileSources(
                List.of(out.resolve("NumR.kf").toAbsolutePath().normalize(),
                        out.resolve("Box.kf").toAbsolutePath().normalize(),
                        out.resolve("Box$Expr.kf").toAbsolutePath().normalize()),
                dir.resolve("kout"), Target.JVM, out);
        assertTrue(r.success(), "record implements interna compila junto:\n"
                + numr + "\n" + Files.readString(out.resolve("Box.kf")) + "\n"
                + Files.readString(out.resolve("Box$Expr.kf")) + "\n"
                + r.diagnostics().getDiagnostics());
    }

    @Test
    void recordWithCrossPackageInnerInterfaceKeepsHonestSkeleton(@TempDir Path dir) throws Exception {
        Path root = dir.resolve("classes");
        Files.createDirectories(root.resolve("a"));
        Path iface = root.resolve("a/Box.java");
        Files.writeString(iface, """
                package a;
                public class Box {
                    public interface Expr { }
                }
                """);
        Path rec = root.resolve("CrossI.java");
        Files.writeString(rec, """
                import a.Box;
                public record CrossI(int v) implements Box.Expr { }
                """);
        runJavac(java.util.List.of(iface, rec), root);
        Path out = dir.resolve("gen");
        Decompile.decompileTree(root, out);
        String kof = Files.readString(out.resolve("CrossI.kf"));
        // interna CROSS-pacote: `import a.Box$Expr` NÃO PROVADO (SEM015?) →
        // NÃO vira record. O `implements Box$Expr` que aparece vem do
        // fallback de skeleton PRÉ-EXISTENTE (não mudei o fallback); o gate
        // desta unidade é record-vs-skeleton.
        assertTrue(kof.contains("class CrossI extends Record"),
                "interna cross-pacote continua honesta (skeleton):\n" + kof);
        assertFalse(kof.contains("record CrossI"), "não virou record (cross-pkg interna):\n" + kof);
    }

    private void runJavac(Path javaFile, Path dir) throws IOException, InterruptedException {
        runJavac(java.util.List.of(javaFile), dir);
    }

    private void runJavac(java.util.List<Path> javaFiles, Path dir) throws IOException, InterruptedException {
        String javaHome = System.getProperty("java.home");
        Path javac = Path.of(javaHome, "bin", "javac");
        var cmd = new java.util.ArrayList<String>();
        cmd.add(javac.toString()); cmd.add("-d"); cmd.add(dir.toString());
        for (Path f : javaFiles) cmd.add(f.toString());
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        int rc = p.waitFor();
        if (rc != 0) {
            throw new IOException("javac failed: " + new String(p.getInputStream().readAllBytes()));
        }
    }
}