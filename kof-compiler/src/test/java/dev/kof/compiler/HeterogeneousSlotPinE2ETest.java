package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §383 (#561) — o "miss abençoado" do §126 (opção ii) REESCREVE o valor
 * armazenado pelo TIPO PINADO do slot (JVM/Script/Native: {@code listOf(1)
 * .add(true)} lê {@code 1}); o JS, untyped, gravava o original ({@code true})
 * — divergência cross-target (freeze regra 5). Resolução = opção (a) do
 * dossiê (decisão da mantenedora 20/09): o JS coage ao slot no MESMO ponto
 * dos outros alvos (o store).
 *
 * <p>Mecânica por alvo no store de List pinada (add arg 0 / set arg 1):
 * <ul>
 *   <li><b>JVM</b> — box pelo tipo do slot sobre o bit pattern do arg
 *       ({@code Integer.valueOf} sobre bool 0/1, {@code Boolean.valueOf} sobre
 *       numérico !=0). Face Long-slot + bool quebrava (COMP002:
 *       {@code Long.valueOf(J)} sobre {@code ICONST_1} width-1): agora o
 *       lowerer emite {@code I2L} antes do box ({@code coerceStoreWiden}, só
 *       site de List), gravando 1L — o que Script/Native já gravavam.</li>
 *   <li><b>Script</b> — mesma IR; {@code I2L} tolera Boolean vindo de cmp.</li>
 *   <li><b>JS</b> — {@code JsCollectionOps.slotStoreCoerce}: numérico-slot +
 *       arg Bool → {@code v ? 1 : 0}; Bool-slot + arg numérico/char →
 *       truthiness. Long-slot chega convertido pela IR (I2L→BigInt).</li>
 *   <li><b>Native</b> — cru 0/1 no qword do slot (já gravava 1/0).</li>
 * </ul>
 *
 * <p>A face NEGRITA (primitivo em slot de referência —
 * {@code listOf(listOf(1)).add(true)} — e o espelho objeto-em-slot-primitivo)
 * NÃO é "miss abençoado": quebra de verdade nos dois alvos compilados (JVM
 * VerifyError no load, Native SIGSEGV — medidos 20/09) e os dois tolerados
 * divergem entre si (Script 1 × JS true). Pela doutina do §126 ("rejeitar só
 * o que quebra") vira SEM056 nos 4 alvos — paridade pelo diagnóstico único
 * ({@code CollectionWrites.breaksPinnedList}).
 *
 * <p>Ouro = oracle JVM medido 20/09. Map/Set NÃO são tocados: lá a
 * heterogeneidade de categorias vizinhas é tolerada pelo consenso 3/4
 * (faces S2/M1 20/09 — coagir viraria JS para o lado do Native-minoria).
 */
class HeterogeneousSlotPinE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private record Run(boolean ok, String output) {}

    private Run runJvm(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.JVM);
        if (!r.success()) return new Run(false, diags(r));
        var oldOut = System.out;
        var buf = new ByteArrayOutputStream();
        System.setOut(new java.io.PrintStream(buf, true));
        try {
            var cl = new URLClassLoader(new java.net.URL[]{out.toUri().toURL()},
                    getClass().getClassLoader());
            Class.forName("Default.Main", true, cl)
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            return new Run(true, buf.toString());
        } catch (ReflectiveOperationException e) {
            Throwable c = e.getCause() != null ? e.getCause() : e;
            return new Run(false, "THROW: " + c);
        } finally {
            System.setOut(oldOut);
        }
    }

    private Run runJs(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.JS);
        if (!r.success()) return new Run(false, diags(r));
        var buf = new ByteArrayOutputStream();
        try {
            int rc = dev.kof.runtime.KofJsRunner.run(
                    out.resolve("Default.mjs"), buf,
                    new ByteArrayInputStream(new byte[0]), buf);
            return new Run(rc == 0, buf.toString());
        } catch (Exception e) {
            return new Run(false, "THROW: " + e.getMessage() + "\n" + buf);
        }
    }

    private Run runScript(Path src, Path root) {
        try {
            var r = driver.interpret(java.util.List.of(src), root, new String[0]);
            return new Run(r.exitCode() == 0, r.stdout());
        } catch (Exception e) {
            return new Run(false, "THROW: " + e.getMessage());
        }
    }

    private Run runNative(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        if (!r.success()) return new Run(false, "COMPILE-FAIL: " + diags(r));
        Process p = new ProcessBuilder(out.resolve("Default/Main").toString())
                .redirectErrorStream(true).start();
        String s = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        int rc = p.waitFor();
        return new Run(rc == 0, s);
    }

    private static String diags(CompilationResult r) {
        StringBuilder sb = new StringBuilder();
        r.diagnostics().getDiagnostics().forEach(d -> sb.append(d.message()).append("\n"));
        return sb.toString();
    }

    private static String norm(String s) {
        return s.replace("\r\n", "\n").trim();
    }

    /** JVM ≡ Script ≡ JS ≡ Native x86-64, byte-a-byte, com golden do JVM. */
    private void assertFourTargets(@TempDir Path tmp, String base, String source,
                                   String golden) throws Exception {
        Path src = tmp.resolve(base + ".kf");
        Files.writeString(src, source);
        Run jvm = runJvm(src, tmp.resolve("o-" + base + "-jvm"));
        assertTrue(jvm.ok(), () -> "JVM: " + jvm.output());
        assertEquals(golden, norm(jvm.output()), "golden JVM (oracle medido 20/09)");
        Run scr = runScript(src, tmp);
        assertTrue(scr.ok(), () -> "Script: " + scr.output());
        assertEquals(golden, norm(scr.output()), "Script == golden JVM");
        Run js = runJs(src, tmp.resolve("o-" + base + "-js"));
        assertTrue(js.ok(), () -> "JS: " + js.output());
        assertEquals(golden, norm(js.output()), "JS == golden JVM (#561 — coação ao slot no store)");
        Run nat = runNative(src, tmp.resolve("o-" + base + "-nat"));
        assertTrue(nat.ok(), () -> "Native: " + nat.output());
        assertEquals(golden, norm(nat.output()), "Native == golden JVM");
    }

    /** SEM056 nos 4 alvos (diagnóstico único = paridade da face rejeitada). */
    private void assertRejectedAllTargets(@TempDir Path tmp, String base, String source)
            throws Exception {
        Path src = tmp.resolve(base + ".kf");
        Files.writeString(src, source);
        for (Target t : new Target[]{Target.JVM, Target.JS, Target.NATIVE}) {
            CompilationResult r = driver.compile(src, tmp.resolve("o-" + base + "-" + t), t);
            assertFalse(r.success(), t + " deve recusar: " + (r.success() ? "compilou" : ""));
            assertTrue(r.diagnostics().getDiagnostics().stream()
                    .anyMatch(d -> "SEM056".equals(d.code())),
                    t + " esperado SEM056: " + r.diagnostics().getDiagnostics());
        }
        var interp = assertThrows(Exception.class,
                () -> driver.interpret(java.util.List.of(src), tmp.resolve("i-" + base),
                        new String[0]),
                "Script deve recusar a mesma escrita (SEM056)");
        assertTrue(String.valueOf(interp.getCause() != null
                        ? interp.getCause().getMessage() : interp.getMessage())
                .contains("SEM056"), "Script SEM056");
    }

    // ── faces do dossiê §383/#561 ────────────────────────────────────

    /** Int-slot + true → o slot vence: lê 1 nos 4 (era `true` só no JS). */
    @Test
    void intSlotBoolAddReadsSlotRewrittenValue(@TempDir Path tmp) throws Exception {
        assertFourTargets(tmp, "P561IntTrue", """
            main() {
              var li = listOf(1)
              li.add(true)
              println(li.get(1))
            }
            """, "1");
    }

    /** Int-slot + false → 0 nos 4. */
    @Test
    void intSlotFalseAddReadsZero(@TempDir Path tmp) throws Exception {
        assertFourTargets(tmp, "P561IntFalse", """
            main() {
              var li = listOf(1)
              li.add(false)
              println(li.get(1))
            }
            """, "0");
    }

    /** Int-slot + Char → 99 nos 4 (par benzido G2–G5 — controle de não-regresso). */
    @Test
    void intSlotCharStaysConsistent(@TempDir Path tmp) throws Exception {
        assertFourTargets(tmp, "P561IntChar", """
            main() {
              var li = listOf(1)
              li.add('c')
              println(li.get(1))
            }
            """, "99");
    }

    /** Bool-slot + 2 → true nos 4 (verdade por !=0; leitura tipada já dava true). */
    @Test
    void boolSlotIntAddReadsTruth(@TempDir Path tmp) throws Exception {
        assertFourTargets(tmp, "P561Bool2", """
            main() {
              var lb = listOf(true)
              lb.add(2)
              println(lb.get(1))
            }
            """, "true");
    }

    /** Bool-slot + 2 impresso POR INTEIRO — a face do store cru (era [true, 2] no JS). */
    @Test
    void boolSlotWholeListShowsRewritten(@TempDir Path tmp) throws Exception {
        assertFourTargets(tmp, "P561BoolWhole", """
            main() {
              var lb = listOf(true)
              lb.add(2)
              println(lb)
            }
            """, "[true, true]");
    }

    /** Int-slot + true impresso por inteiro — [1, 1] nos 4 (era [1, true] no JS). */
    @Test
    void intSlotWholeListShowsRewritten(@TempDir Path tmp) throws Exception {
        assertFourTargets(tmp, "P561IntWhole", """
            main() {
              var li = listOf(1)
              li.add(true)
              println(li)
            }
            """, "[1, 1]");
    }

    /**
     * Long-slot + true → 1 nos 4. Antes: JVM COMP002 (frame crash —
     * {@code Long.valueOf(J)} sobre {@code ICONST_1}), JS gravava `true`;
     * Script/Native já liam 1. Fix: I2L no store (site de List) + a lowering
     * JS do I2L (BigInt(true)=1n).
     */
    @Test
    void longSlotBoolAddReadsOne(@TempDir Path tmp) throws Exception {
        assertFourTargets(tmp, "P561LongTrue", """
            main() {
              var ll = listOf(1L)
              ll.add(true)
              println(ll.get(1))
            }
            """, "1");
    }

    /** Bool vindo de CMP (Boolean boxed no interpretador) no slot Long — mesma face. */
    @Test
    void computedBoolIntoLongSlotReadsOne(@TempDir Path tmp) throws Exception {
        assertFourTargets(tmp, "P561CmpLong", """
            main() {
              var ll = listOf(1L)
              var b = 2 > 1
              ll.add(b)
              println(ll.get(1))
            }
            """, "1");
    }

    /** `set(0, true)` em List<Int> — mesmo root do add: lê 1 nos 4. */
    @Test
    void setFaceIntSlotBoolReadsOne(@TempDir Path tmp) throws Exception {
        assertFourTargets(tmp, "P561Set", """
            main() {
              var li = listOf(1)
              li.set(0, true)
              println(li.get(0))
            }
            """, "1");
    }

    /** `set(0, 5)` em List<Bool> — espelho: lê true nos 4 (JS gravava 5). */
    @Test
    void setFaceBoolSlotIntReadsTruth(@TempDir Path tmp) throws Exception {
        assertFourTargets(tmp, "P561SetBool", """
            main() {
              var lb = listOf(true)
              lb.set(0, 5)
              println(lb)
            }
            """, "[true]");
    }

    /**
     * Face NEGRITA do dossiê — primitivo em slot de referência pinado
     * (aninhado). Medido 20/09 ANTES do fix: JVM VerifyError no load, Native
     * SIGSEGV, Script `1`, JS `true` (4 alvos, 4 destinos). Não é miss
     * benzido (o box pelo slot não tem categoria p/ coagir): rejeição
     * SEM056 nos 4 (doutina §126 — rejeitar só o que quebra).
     */
    @Test
    void nestedSlotPrimitiveAddRejectedEverywhere(@TempDir Path tmp) throws Exception {
        assertRejectedAllTargets(tmp, "P561Nested", """
            main() {
              var ln = listOf(listOf(1))
              ln.add(true)
              println(ln.get(1))
            }
            """);
    }

    /** Espelho da face aninhada — objeto em slot primitivo (JVM VerifyError / Native lixo medidos). */
    @Test
    void objectIntoPrimitiveSlotRejectedEverywhere(@TempDir Path tmp) throws Exception {
        assertRejectedAllTargets(tmp, "P561ObjPrim", """
            class Box {
                String hi = "h"
            }

            main() {
                var li = listOf(1)
                li.add(Box())
                println(li.get(1))
            }
            """);
    }

    // ── controles (freeze regra 1 — o que já era par não muda) ───────

    /** String em List<Int> continua SEM056 nos 4 (parelhamento com a rejeição nova). */
    @Test
    void stringIntoIntSlotStillRejected(@TempDir Path tmp) throws Exception {
        assertRejectedAllTargets(tmp, "P561Str", """
            main() {
              var li = listOf(1)
              li.add("s")
              println(li.get(1))
            }
            """);
    }

    /** widening numérico benzido §126/§143 (Int em List<Long>) intacto. */
    @Test
    void blessedIntIntoLongSlotUnchanged(@TempDir Path tmp) throws Exception {
        assertFourTargets(tmp, "P561Widen", """
            main() {
              var ll = listOf(1L)
              ll.add(2)
              println(ll)
              println(ll.get(1))
            }
            """, "[1, 2]\n2");
    }

    /** coleção BARE (§374): slot Unknown não coage nada — bool grava true nos 4. */
    @Test
    void bareListBoolAddUnchanged(@TempDir Path tmp) throws Exception {
        assertFourTargets(tmp, "P561Bare", """
            main() {
              List b = listOf()
              b.add(true)
              b.add(false)
              println(b.get(0))
              println(b.get(1))
            }
            """, "true\nfalse");
    }

    /** homogêneo tipado — caminho intocado (add/set/get Int). */
    @Test
    void homogeneousTypedListUnchanged(@TempDir Path tmp) throws Exception {
        assertFourTargets(tmp, "P561Homog", """
            main() {
              var li = listOf(1)
              li.add(2)
              li.set(0, 3)
              println(li.get(0) + li.get(1))
            }
            """, "5");
    }
}
