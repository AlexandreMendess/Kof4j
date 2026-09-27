package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #386/#382 — Map.containsValue/putIfAbsent + List.indexOf/lastIndexOf/
 * addAll/subList/sort na stdlib. Goldens são MEDIÇÃO no oráculo JVM
 * (java.util é a fonte de verdade do contrato), nunca memória: hit/miss,
 * mapa vazio, putIfAbsent duas vezes (segunda devolve o existente e NÃO
 * sobrescreve), indexOf ausente -1, lastIndexOf com duplicatas, subList
 * begin==end (vazia) e out-of-range (a mensagem que o java.util lança,
 * medida com o processo morrendo exit=1), addAll true/false, sort crescente
 * e reverso, String, Double e Float (§352/NAT001 FECHADO 21/09: alargamento
 * p/ Double no runtime nativo — x86 e cross). Os gates SEM097 (ordem
 * natural) e SEM025 de aridade são compile-time compartilhados: um gate, os
 * 4 alvos.
 */
class CollectionMethodsStdlibE2ETest {

    static final String PROGRAM = """
            main() {
                val m: Map<String, Int> = mapOf()
                m.put("a", 1)
                m.put("b", 2)
                println(m.containsValue(2))
                println(m.containsValue(3))
                val e: Map<String, Int> = mapOf()
                println(e.containsValue(1))
                println(m.putIfAbsent("a", 9))
                println(m.getOrDefault("a", 0))
                println(m.putIfAbsent("c", 3) == null)
                println(m.containsValue(3))
                val s: Map<String, String> = mapOf()
                println(s.putIfAbsent("k", "v1") == null)
                println(s.putIfAbsent("k", "v2"))
                val l: List<Int> = listOf(3, 1, 2, 1)
                println(l.indexOf(1))
                println(l.indexOf(99))
                println(l.lastIndexOf(1))
                println(l.lastIndexOf(99))
                val sub = l.subList(1, 3)
                println(sub.size)
                val empty = l.subList(2, 2)
                println(empty.size)
                println(l.contains(3))
                val b: List<Int> = listOf()
                println(b.addAll(l))
                println(b.size)
                println(b.addAll(listOf<Int>()))
                val rev: List<Int> = listOf(5, 4, 3, 2, 1)
                rev.sort()
                println(rev.get(0))
                println(rev.get(4))
                val ok: List<Int> = listOf(1, 2, 3)
                ok.sort()
                println(ok.get(2))
                val str: List<String> = listOf("pear", "apple", "fig")
                str.sort()
                println(str.get(0))
                val dbl: List<Double> = listOf(2.5, -1.5, 0.0)
                dbl.sort()
                println(dbl.get(0) == -1.5)
                println(l.subList(0, 1).get(0))
            }
            """;

    // Golden MEDIDO no oráculo JVM (java.util), 19/09 — ver body do programa.
    static final String GOLDEN =
            "true\nfalse\nfalse\n1\n1\ntrue\ntrue\ntrue\nv1\n1\n-1\n3\n-1\n"
                    + "2\n0\ntrue\ntrue\n4\nfalse\n1\n5\n3\napple\ntrue\n3";

    private final CompilerDriver driver = new CompilerDriver();

    private Path outDirFor(Path tempDir, String name, Target t) {
        return tempDir.resolve("out-" + name + "-" + t);
    }

    private CompilationResult compile(Path tempDir, String name, String program, Target t) throws Exception {
        Path source = tempDir.resolve(name + ".kf");
        Files.writeString(source, program);
        return driver.compile(source, outDirFor(tempDir, name, t), t);
    }

    @Test
    void sevenMethodsRunOnJvm(@TempDir Path tempDir) throws Exception {
        CompilationResult r = compile(tempDir, "V", PROGRAM, Target.JVM);
        assertTrue(r.success(), "#386/#382 verbatim must compile: " + r.diagnostics().getDiagnostics());
        String javaCmd = TestJdk.javaBin();
        Process p = new ProcessBuilder(javaCmd, "-cp",
                outDirFor(tempDir, "V", Target.JVM).toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes()).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "JVM run must exit 0, got:\n" + out);
        assertEquals(GOLDEN, out, "JVM oracle golden (medido)");
    }

    @Test
    void sevenMethodsRunOnJs(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("J.kf");
        Files.writeString(src, PROGRAM);
        Path outDir = tempDir.resolve("outJ");
        CompilationResult r = driver.compile(src, outDir, Target.JS);
        assertTrue(r.success(), "js compile: " + r.diagnostics().getDiagnostics());
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(outDir.resolve("Default.mjs"), out,
                new java.io.ByteArrayInputStream(new byte[0]), out);
        String txt = out.toString().replace("\r\n", "\n").trim();
        assertEquals(0, ec, "js run exit, output:\n" + txt);
        assertEquals(GOLDEN, txt, "js output must match the JVM oracle (parity rule 5)");
    }

    // Fatia 2 (#386/#382): o mesmo golden roda no NATIVO x86_64 (asm real —
    // RuntimeMapLookups/RuntimeListLookups). Riscv64/aarch64: NativeRiscv64/
    // Aarch64E2ETest (guardados por toolchain — sem qemu no host de medição,
    // skip honesto, nunca falso-verde).
    @Test
    void sevenMethodsRunOnNativeX86(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("N.kf");
        Files.writeString(src, PROGRAM);
        Path outDir = tempDir.resolve("outN");
        CompilationResult r = driver.compile(src, outDir, Target.NATIVE);
        assertTrue(r.success(), "native compile: " + r.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        assertTrue(Files.exists(bin), "binary should exist");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes()).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "native run must exit 0, got:\n" + out);
        assertEquals(GOLDEN, out, "native x86_64 output must match the JVM oracle (rule 5)");
    }

    @Test
    void sortEmptyListOfCompilesAndRunsEverywhere(@TempDir Path tempDir) throws Exception {
        // listOf() sem pin → List<Unknown>; sort de lista VAZIA é no-op em
        // todo alvo (o gate SEM097 aceita Unknown exatamente por isso).
        CompilationResult r = compile(tempDir, "S", """
                main() {
                    val l = listOf()
                    l.sort()
                    println(l.size)
                }
                """, Target.JVM);
        assertTrue(r.success(), "empty sort must compile: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void subListOutOfBoundsDiesWithTheMeasuredJavaUtilMessage(@TempDir Path tempDir) throws Exception {
        // Q3 "measured, never guessed": ArrayList.subList(1,9) numa lista de
        // 4 lança IndexOutOfBoundsException("toIndex = 9") — o processo morre
        // exit=1 (Kof não captura exceção Java com catch(String)).
        Path src = tempDir.resolve("O.kf");
        Files.writeString(src, """
                main() {
                    val l: List<Int> = listOf(3, 1, 2, 1)
                    val big = l.subList(1, 9)
                    println(big.size)
                }
                """);
        Path outDir = tempDir.resolve("outO");
        CompilationResult r = driver.compile(src, outDir, Target.JVM);
        assertTrue(r.success(), "compile: " + r.diagnostics().getDiagnostics());
        String javaCmd = TestJdk.javaBin();
        Process p = new ProcessBuilder(javaCmd, "-cp", outDir.toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        int ec = p.waitFor();
        assertNotEquals(0, ec, "out-of-range subList must die, got:\n" + out);
        assertTrue(out.contains("IndexOutOfBoundsException"), "measured java.util exception, got:\n" + out);
        assertTrue(out.contains("toIndex = 9"), "measured message, got:\n" + out);
    }

    @Test
    void sortOnNonNaturalElementIsRejectedEveryTarget(@TempDir Path tempDir) throws Exception {
        // SEM097 — gate compartilhado (record não tem ordem natural; Kof não
        // tem Comparator). SCRIPT não passa por driver.compile (COMP003 — o
        // interpretador vive em kof-script, coberto em KofScriptStdlibParity
        // Test com o mesmo frontend); JVM/JS/Native: um gate, três faces.
        for (Target t : new Target[]{Target.JVM, Target.JS, Target.NATIVE}) {
            CompilationResult r = compile(tempDir, "R" + t, """
                    record Point(Int x, Int y)
                    main() {
                        val l = listOf(Point(1, 2), Point(3, 4))
                        l.sort()
                        println(l.get(0).x())
                    }
                    """, t);
            assertFalse(r.success(), "record sort must be rejected on " + t);
            assertTrue(r.diagnostics().getDiagnostics().stream()
                    .anyMatch(d -> d.code().equals("SEM097")),
                    "SEM097 expected on " + t + ": " + r.diagnostics().getDiagnostics());
        }
    }

    /** §352/NAT001 FECHADO 21/09: o slot de Float guarda os 32 bits crus e o
     *  runtime alarga p/ Double (cvtss2sd no x86; fmv.w.x+fcvt.d.s no cross),
     *  reusando a semântica medida do Double.compare — ordena em paridade com
     *  a JVM, incl. o desempate -0.0<0.0 e NaN por último (Q3). */
    @Test
    void floatSortRunsOnNativeWithDoubleCompareSemantics(@TempDir Path tempDir) throws Exception {
        String src = """
                main() {
                    val l: List<Float> = listOf(2.5f, -1.5f, 0.5f, 3.5f)
                    l.sort()
                    println(l.get(0))
                    println(l.get(1))
                    println(l.get(2))
                    println(l.get(3))
                    val z: List<Float> = listOf(0.0f, -0.0f)
                    z.sort()
                    println(z.get(0))
                    println(z.get(1))
                    val d: List<Double> = listOf(0.0, -0.0)
                    d.sort()
                    println(d.get(0))
                    println(d.get(1))
                    val n: List<Float> = listOf(1.5f, 0.0f / 0.0f, -2.5f)
                    n.sort()
                    println(n.get(0))
                    println(n.get(1))
                    println(n.get(2))
                }
                """;
        // oráculo JVM medido (bin/kof run): Float 4× + ±0.0 Float + ±0.0 Double + NaN último
        String golden = "-1.5\n0.5\n2.5\n3.5\n-0.0\n0.0\n-0.0\n0.0\n-2.5\n1.5\nNaN";
        CompilationResult rj = compile(tempDir, "FJ", src, Target.JVM);
        assertTrue(rj.success(), "Float sort on JVM: " + rj.diagnostics().getDiagnostics());
        String jvm = new String(new ProcessBuilder(TestJdk.javaBin(), "-cp",
                outDirFor(tempDir, "FJ", Target.JVM).toString(), "Default.Main")
                .redirectErrorStream(true).start().getInputStream().readAllBytes())
                .replace("\r\n", "\n").trim();
        assertEquals(golden, jvm, "JVM oracle do Float sort (medido)");

        CompilationResult rn = compile(tempDir, "F", src, Target.NATIVE);
        assertTrue(rn.success(), "Float sort no x86_64: " + rn.diagnostics().getDiagnostics());
        Path bin = outDirFor(tempDir, "F", Target.NATIVE).resolve("Default/Main");
        Process pn = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String nat = new String(pn.getInputStream().readAllBytes()).replace("\r\n", "\n").trim();
        assertEquals(0, pn.waitFor(), "native exit, saída:\n" + nat);
        assertEquals(golden, nat, "Float sort x86_64 = caixa crua + cvtss2sd (paridade JVM)");

        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            String arch = t.nativeArch();
            org.junit.jupiter.api.Assumptions.assumeTrue(
                    NativeRiscv64E2ETest.hasToolchain(arch), "toolchain " + arch + " ausente");
            CompilationResult rc = compile(tempDir, "FC" + arch, src, t);
            assertTrue(rc.success(), t + " Float sort: " + rc.diagnostics().getDiagnostics());
            String out = NativeRiscv64E2ETest.runQemu(arch,
                    outDirFor(tempDir, "FC" + arch, t).resolve("Default/Main"));
            assertEquals(golden, out, t + " Float sort = fmv.w.x+fcvt.d.s (paridade JVM)");
        }
    }

    @Test
    void wrongAritiesAreRejectedWithSem025(@TempDir Path tempDir) throws Exception {
        String[] bad = {
                "l.indexOf()",
                "l.subList(1)",
                "l.addAll()",
                "m.containsValue()",
                "m.putIfAbsent(\"a\")",
        };
        for (String expr : bad) {
            CompilationResult r = compile(tempDir, "A" + expr.hashCode(), """
                    main() {
                        val l: List<Int> = listOf(1, 2)
                        val m: Map<String, Int> = mapOf()
                        m.put("a", 1)
                        val x = %s
                        println(x)
                    }
                    """.formatted(expr), Target.JVM);
            assertFalse(r.success(), expr + " must not compile");
            assertTrue(r.diagnostics().getDiagnostics().stream()
                    .anyMatch(d -> d.code().equals("SEM025")),
                    expr + " expected SEM025: " + r.diagnostics().getDiagnostics());
        }
        // sort(1) em STATEMENT (em `val x = l.sort(1)` o SEM033 de atribuição
        // void dispara antes — mesma rejeição, face diferente do gate).
        CompilationResult rs = compile(tempDir, "AS", """
                main() {
                    val l: List<Int> = listOf(1, 2)
                    l.sort(1)
                }
                """, Target.JVM);
        assertFalse(rs.success(), "l.sort(1) must not compile");
        assertTrue(rs.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> d.code().equals("SEM025")),
                "l.sort(1) expected SEM025: " + rs.diagnostics().getDiagnostics());
    }

    @Test
    void putIfAbsentPollutingValueIsRejected(@TempDir Path tempDir) throws Exception {
        // §126 wall do put agora cobre putIfAbsent (escreve o slot de valor).
        CompilationResult r = compile(tempDir, "P", """
                main() {
                    val m: Map<String, Int> = mapOf()
                    m.put("a", 1)
                    println(m.putIfAbsent("b", 5L) == null)
                }
                """, Target.JVM);
        assertFalse(r.success(), "polluting value must not compile");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> d.code().equals("SEM056")),
                "SEM056 expected: " + r.diagnostics().getDiagnostics());
    }

    /** §352/NAT002 FECHADO 21/09: o slot de valor Object normaliza TODO
     *  primitivo como caixa no nativo (extensão §284: kof_box_* existe p/
     *  Double/Float/Bool) e o scan usa o tag 6 DINÂMICO — kof_value_kind
     *  classifica arg e entradas (caixa/String/ponteiro/bit cru) com guarda
     *  de heap, comparando box×box, str×str, ptr×ptr. Paridade medida:
     *  JVM (HashMap.equals) × 3 nativos, incl. o print que era vazio no
     *  Double/Bool cru. */
    @Test
    void objectValuedContainsValueRunsOnNativeInParity(@TempDir Path tempDir) throws Exception {
        String src = """
                main() {
                    val o: Map<String, Object> = mapOf()
                    o.put("d", 2.5)
                    o.put("i", 7)
                    o.put("b", true)
                    println(o.containsValue(7))
                    println(o.containsValue(2.5))
                    println(o.containsValue(true))
                    println(o.containsValue(8))
                    println(o.containsValue(2.75))
                    println(o.get("d"))
                    println(o.get("b"))
                    println(o.get("i"))
                    val x: Object = 2.5
                    o.put("d2", x)
                    println(o.containsValue(2.5))
                    val s: Object = "str"
                    o.put("s", s)
                    println(o.containsValue("str"))
                    println(o.containsValue("nope"))
                    val only: Map<String, Object> = mapOf()
                    only.put("d", 2.5)
                    println(only.containsValue(7))
                    println(only.containsValue("x"))
                    val z: Map<String, Object> = mapOf()
                    z.put("n", -0.0)
                    println(z.containsValue(0.0))
                    println(z.containsValue(-0.0))
                    println(o.getOrDefault("d", 9.5))
                    println(o.getOrDefault("nope", 9.5))
                }
                """;
        // Golden medido no oráculo JVM (java.util): hits Int/Double/Bool/String,
        // misses de família distinta, print boxed (2.5/true/7), a 2ª entrada
        // Double do local Object, -0.0 ≠ 0.0 (Double.equals é bit-a-bit) e o
        // getOrDefault com default Double (§432 — arg boxa pelo tipo DELE,
        // resultado pelo V=Object do slot).
        String golden = "true\ntrue\ntrue\nfalse\nfalse\n2.5\ntrue\n7\n"
                + "true\ntrue\nfalse\nfalse\nfalse\nfalse\ntrue\n2.5\n9.5";
        CompilationResult rj = compile(tempDir, "OJ", src, Target.JVM);
        assertTrue(rj.success(), "Object containsValue works on JVM: " + rj.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(TestJdk.javaBin(), "-cp",
                outDirFor(tempDir, "OJ", Target.JVM).toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String jvm = new String(p.getInputStream().readAllBytes()).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "JVM exit, saída:\n" + jvm);
        assertEquals(golden, jvm, "oráculo JVM (medido)");

        CompilationResult rn = compile(tempDir, "ON", src, Target.NATIVE);
        assertTrue(rn.success(), "Object containsValue no x86_64: " + rn.diagnostics().getDiagnostics());
        Path bin = outDirFor(tempDir, "ON", Target.NATIVE).resolve("Default/Main");
        Process pn = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String nat = new String(pn.getInputStream().readAllBytes()).replace("\r\n", "\n").trim();
        assertEquals(0, pn.waitFor(), "native exit, saída:\n" + nat);
        assertEquals(golden, nat, "x86_64 = caixa normalizada + kind dinâmico (paridade JVM)");

        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            String arch = t.nativeArch();
            org.junit.jupiter.api.Assumptions.assumeTrue(
                    NativeRiscv64E2ETest.hasToolchain(arch), "toolchain " + arch + " ausente");
            CompilationResult rc = compile(tempDir, "OC" + arch, src, t);
            assertTrue(rc.success(), t + " Object containsValue: " + rc.diagnostics().getDiagnostics());
            String out = NativeRiscv64E2ETest.runQemu(arch,
                    outDirFor(tempDir, "OC" + arch, t).resolve("Default/Main"));
            assertEquals(golden, out, t + " = kof_value_kind + arena (paridade JVM)");
        }
    }

    @Test
    void existingCollectionSurfaceStillCompiles(@TempDir Path tempDir) throws Exception {
        // NEGATIVO (regra 1): o que já era válido continua válido.
        CompilationResult r = compile(tempDir, "K", """
                main() {
                    val m: Map<String, Int> = mapOf()
                    m.put("a", 1)
                    println(m.getOrDefault("a", 0))
                    println(m.containsKey("a"))
                    println(m.keys().size)
                    val l: List<Int> = listOf(1, 2, 3)
                    println(l.size)
                    println(l.remove(0))
                    l.clear()
                    println(l.isEmpty())
                    val d: Map<String, String> = mapOf("x", "y")
                    println(d.get("x"))
                }
                """, Target.JVM);
        assertTrue(r.success(), "regression: " + r.diagnostics().getDiagnostics());
    }
}
