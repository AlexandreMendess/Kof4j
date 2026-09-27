package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §288 (#396, D-RULE6-BATCH opção (b)) — um type-variable do dono dentro de
 * um tipo de FUNÇÃO declarado (`mapItems(transform: (T) -> T)`) era
 * scope-blind em duas camadas: o checker SEM014 comparava `(Int)->Int`
 * contra `FunctionType[ClassType("","T"), ...]` ("expected 'function' but
 * got 'function'"), e o lowering sintetizava a interface `Function1_CT_CT`
 * com descriptor `LT;` — o programa só morria no load
 * (`NoClassDefFoundError: T` / `NoSuchMethodError: ...Function1_CT_CT`).
 *
 * <p>Entregue (contrato 0.5.0 da decisão):
 * (1) fonte ÚNICA de TypeVariable — `TypeParams.rewrite` agora recursa no
 * miolo de FunctionType/WildcardType, então `resolveType` (checker) e
 * `resolveWithTypeParams` (lowering) veem o MESMO `TypeVariable` com bound;
 * o mangle da interface sintética apaga TV para Object (o `Function1_CT_CT`
 * fantasma morre) e o round-trip por string do retorno da lambda preserva o
 * TV; `isAssignable` ganha a regra função×função por componente (erasure).
 * (2) Rejeição interina SEM085: a forma que ainda CRASHA no load — função
 * com type-param do dono no parâmetro/retorno de um tipo declarado (a
 * lambda do call site sai com a assinatura CONCRETA `Function1_int_int`
 * enquanto o dispatch usa a apagada `Function1_O_O` =
 * `IncompatibleClassChangeError`, medido no tip) — é rejeitada no compile.
 * O conserto completo (lambda contextualizada na assinatura apagada +
 * box/unbox no corpo) é a ABI de erasure da linha 1.0 (§271).
 *
 * <p>Controles (o que JÁ funciona não regride): type-param simples em
 * retorno/parâmetro/campo (`T get(): T`), genérico sem função
 * (`Pipeline<T>`/`List<T>`), tipo-função CONCRETO (`(Int) -> Int`) —
 * paridade JVM/Script/JS/Native com o MESMO output.
 */
class FnTypeVarSignatureE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path outDir) throws Exception {
        Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                        "-cp", outDir.toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "JVM exit code; output:\n" + out);
        return out;
    }

    private void runAll4(Path tempDir, String name, String source, String expected) throws Exception {
        Path src = tempDir.resolve(name + ".kf");
        Files.writeString(src, source);
        Path out = tempDir.resolve(name + "-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), name + " JVM compile: " + r.diagnostics().getDiagnostics());
        assertEquals(expected, runJvm(out), name + " JVM");
        KofInterpreter.Result i = driver.interpret(java.util.List.of(src), src.getParent(), new String[0]);
        assertEquals(0, i.exitCode(), name + " Script exit/stderr: " + i.stdout() + " " + i.stderr());
        assertEquals(expected, i.stdout().trim(), name + " Script");
        Path jsOut = tempDir.resolve(name + "-js");
        CompilationResult js = driver.compile(src, jsOut, Target.JS);
        assertTrue(js.success(), name + " JS compile: " + js.diagnostics().getDiagnostics());
        ProcessBuilder jb = new ProcessBuilder("node", jsOut.resolve("Default.mjs").toString());
        jb.redirectErrorStream(true);
        Process jp = jb.start();
        String jout = new String(jp.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, jp.waitFor(), name + " JS exit, output: " + jout);
        assertEquals(expected, jout, name + " JS");
        runNative(tempDir, name + "-nat", source, expected);
    }

    private void runNative(Path tempDir, String name, String source, String expected) throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                dev.kof.compiler.nat.NativeToolchainGate.present(),
                "Native toolchain (as/ld) ausente no host — pulando honesto (NATIVE002)");
        Path src = tempDir.resolve(name + ".kf");
        Files.writeString(src, source);
        Path out = tempDir.resolve(name + "-out");
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        assertTrue(r.success(), name + " NATIVE compile: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(out.resolve("Default/Main").toString())
                .redirectErrorStream(true).start();
        String outTxt = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), name + " NATIVE exit, output: " + outTxt);
        assertEquals(expected, outTxt, name + " NATIVE");
    }

    /** #396 verbatim — `Pipeline<T>` com `mapItems(transform: (T) -> T)`. */
    private static final String PIPELINE = """
            class Pipeline<T> {
                T first
                public constructor(T first) {
                    this.first = first
                }
                Pipeline<T> mapItems(transform: (T) -> T): Pipeline<T> {
                    var r = Pipeline(transform(this.first))
                    return r
                }
            }
            main() {
                var p = Pipeline(21)
                var q = p.mapItems((x: Int) -> x * 2)
                println(q.first)
            }
            """;

    @Test
    void issue396PipelineFnTypeParamIsSem085NotSem014OrCrash(@TempDir Path tempDir) throws Exception {
        // Q0: no tip antigo o verbatim dava SEM014 no compile (ou, com o
        // checker só corrigido, `IncompatibleClassChangeError` no load).
        // Agora: rejeição interina SEM085, SEM014 ausente, nos dois targets.
        Path src = tempDir.resolve("pipe396.kf");
        Files.writeString(src, PIPELINE);
        for (Target t : new Target[] { Target.JVM, Target.NATIVE }) {
            var result = driver.compile(src, tempDir.resolve("pipe396-" + t), t);
            assertFalse(result.success(), t + ": the composite fn-type with T must not compile yet");
            assertTrue(result.diagnostics().getDiagnostics().stream()
                            .anyMatch(d -> "SEM085".equals(d.code())),
                    t + ": expected SEM085, got: " + result.diagnostics().getDiagnostics());
            assertTrue(result.diagnostics().getDiagnostics().stream()
                            .noneMatch(d -> "SEM014".equals(d.code())),
                    t + ": SEM014 must be gone (it was the self-contradiction), got: "
                            + result.diagnostics().getDiagnostics());
        }
    }

    @Test
    void useTwiceMinimalFnTypeParamIsSem085(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("usetwice396.kf");
        Files.writeString(src, """
                class Box<T> {
                    T value
                    public constructor(T value) {
                        this.value = value
                    }
                    T useTwice(f: (T) -> T): T {
                        var a = f(this.value)
                        var b = f(a)
                        return b
                    }
                }
                main() {
                    var b = Box(21)
                    println(b.useTwice((x: Int) -> x + 1))
                }
                """);
        var result = driver.compile(src, tempDir.resolve("usetwice396-out"), Target.JVM);
        assertFalse(result.success(), "Box.useTwice((T) -> T) must not compile yet");
        assertTrue(result.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM085".equals(d.code())),
                "expected SEM085, got: " + result.diagnostics().getDiagnostics());
    }

    @Test
    void genericFunctionWithFnTypeParamIsSem085(@TempDir Path tempDir) throws Exception {
        // A mesma doença no dono FUNÇÃO (não só classe): rejeitada em todo
        // target (a validação é do frontend compartilhado — regra 5).
        Path src = tempDir.resolve("fn396.kf");
        Files.writeString(src, """
                apply2<T>(f: (T) -> T): T {
                    return f(f(1))
                }
                main() {
                    var r = apply2((x: Int) -> x + 1)
                    println(r)
                }
                """);
        for (Target t : new Target[] { Target.JVM, Target.NATIVE }) {
            var result = driver.compile(src, tempDir.resolve("fn396-" + t), t);
            assertFalse(result.success(), t + ": generic function with fn-type param must not compile yet");
            assertTrue(result.diagnostics().getDiagnostics().stream()
                            .anyMatch(d -> "SEM085".equals(d.code())),
                    t + ": expected SEM085, got: " + result.diagnostics().getDiagnostics());
        }
    }

    @Test
    void fnTypeReturningOwnerTypeParamIsSem085(@TempDir Path tempDir) throws Exception {
        // `(Int) -> T`: o retorno TV chega no mangle do invoke
        // (`Function1_int_O` vs a lambda `Function1_int_int`) — MESMA face.
        Path src = tempDir.resolve("fnret396.kf");
        Files.writeString(src, """
                class Box<T> {
                    T value
                    public constructor(T value) {
                        this.value = value
                    }
                    T useOnce(f: (Int) -> T): T {
                        return f(0)
                    }
                }
                main() { println("never") }
                """);
        var result = driver.compile(src, tempDir.resolve("fnret396-out"), Target.JVM);
        assertFalse(result.success(), "(Int) -> T must not compile yet (same load-crash face)");
        assertTrue(result.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM085".equals(d.code())),
                "expected SEM085, got: " + result.diagnostics().getDiagnostics());
    }

    @Test
    void plainTypeVariableReturnStillWorks(@TempDir Path tempDir) throws Exception {
        // Controle — a face SIMPLE (`get(): T`) já funcionava e não regride.
        // (forma do `NativeE2ETest.execGenericClass`: constructor com ARG
        // T em classe genérica = gap nativo pré-existente §444, fora daqui)
        runAll4(tempDir, "getCtrl", """
                class Box<T> {
                    T value
                    set(T v) {
                        value = v
                    }
                    T get(): T {
                        return this.value
                    }
                }
                main() {
                    var b = new Box<Int>()
                    b.set(7)
                    println(b.get())
                }
                """, "7");
    }

    @Test
    void genericWithoutFnTypeDoesNotTripSem085(@TempDir Path tempDir) throws Exception {
        // Sem falso-positivo: T em ARGUMENTO genérico (`List<T>`, rio da
        // erasure §355) NÃO é função — o SEM085 não pode acusar. Só
        // compile (JVM+Native): as faces de runtime nativo de classe
        // genérica têm gaps próprios pré-existentes (§444), fora deste escopo.
        Path src = tempDir.resolve("noFalse.kf");
        Files.writeString(src, """
                class Pipe<T> {
                    List<T> items
                    List<T> copyAll(): List<T> {
                        var r = new List<T>()
                        for (var it in this.items) {
                            r.add(it)
                        }
                        return r
                    }
                }
                main() {
                    var p = new Pipe<Int>()
                    p.items = listOf(1, 2)
                    var c = p.copyAll()
                    println(c.size)
                }
                """);
        for (Target t : new Target[] { Target.JVM, Target.NATIVE }) {
            var result = driver.compile(src, tempDir.resolve("noFalse-" + t), t);
            assertTrue(result.success(), t + ": generic args without fn must not be rejected: "
                    + result.diagnostics().getDiagnostics());
            assertTrue(result.diagnostics().getDiagnostics().stream()
                            .noneMatch(d -> "SEM085".equals(d.code())),
                    t + ": SEM085 false positive: " + result.diagnostics().getDiagnostics());
        }
    }

    @Test
    void concreteFnTypeStillWorks(@TempDir Path tempDir) throws Exception {
        // Controle — tipo-função CONCRETO (sem type-param do dono) é feature
        // existente e não pode cair no SEM085.
        runAll4(tempDir, "concreteFn", """
                class Doubler {
                    Int run(f: (Int) -> Int): Int {
                        return f(21)
                    }
                }
                main() {
                    var d = Doubler()
                    println(d.run((x: Int) -> x * 2))
                }
                """, "42");
    }

    @Test
    void fnTypeInsideGenericArgWithOwnerParamIsSem085(@TempDir Path tempDir) throws Exception {
        // `List<(T) -> T>`: o TV dentro do argumento genérico que é função —
        // mesma face de crash, rejeitada.
        Path src = tempDir.resolve("listfn.kf");
        Files.writeString(src, """
                class Box<T> {
                    T value
                    public constructor(T value) {
                        this.value = value
                    }
                    T applyAll(fs: List<(T) -> T>): T {
                        var acc = this.value
                        for (var f in fs) {
                            acc = f(acc)
                        }
                        return acc
                    }
                }
                main() { println("never") }
                """);
        var result = driver.compile(src, tempDir.resolve("listfn-out"), Target.JVM);
        assertFalse(result.success(), "List<(T) -> T> must not compile yet");
        assertTrue(result.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM085".equals(d.code())),
                "expected SEM085, got: " + result.diagnostics().getDiagnostics());
    }

    @Test
    void undefinedNameInsideFnTypeStillSem011(@TempDir Path tempDir) throws Exception {
        // Regressão: o miolo do tipo-função continua validado (SEM011) —
        // o SEM085 não pode mascarar nem aceitar nomes indefinidos.
        Path src = tempDir.resolve("sem011fn.kf");
        Files.writeString(src, """
                class A {
                    void run(f: (Zebra) -> Int) {
                    }
                }
                main() { println("never") }
                """);
        var result = driver.compile(src, tempDir.resolve("sem011fn-out"), Target.JVM);
        assertFalse(result.success(), "Zebra inside a fn-type must not compile");
        assertTrue(result.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM011".equals(d.code())),
                "expected SEM011 for Zebra in the fn-type, got: "
                        + result.diagnostics().getDiagnostics());
    }
}
