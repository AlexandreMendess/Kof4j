package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.PrintStream;
import java.net.URLClassLoader;
import dev.kof.compiler.Target;
import dev.kof.runtime.KofJsRunner;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * X2 — fatia 3 (timeout/cancel/reuse) do {@code kof.interop}. O deadline
 * CORRE NO FILHO (python se auto-interrompe via SIGALRM→TimeoutError e
 * SIGINT→KeyboardInterrupt — zero adivinhação de exit code; o handle F10 não
 * lê com tempo e handle nomeado para fora é regra 6). Provas do corte
 * declarado: INTEROP007 com espera LIMITADA no relógio do teste e paridade
 * JVM≡x86, INTEROP008 via `cancel()` de outra task (JVM — cross-task de
 * referência de objeto não é contrato no Native, só desbox de primitivos em
 * `learn/18`), REUSE do motor depois das duas falhas, `cancel()` ocioso =
 * no-op idempotente, e `timeout(0)` = sem limite (declarado R7).
 */
class InteropTimeoutE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir
    Path tmp;

    private record Run(boolean ok, String output) {}

    private static void requirePython3() {
        assumeTrue(Files.isExecutable(Path.of("/usr/bin/python3")),
                "python3 ausente — motor não executável neste host");
    }

    private Run runJvm(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.JVM);
        if (!r.success()) return new Run(false, diags(r));
        var oldOut = System.out;
        var buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true));
        try {
            var cl = new URLClassLoader(new java.net.URL[]{out.toUri().toURL()},
                    getClass().getClassLoader());
            Class.forName("Default.Main", true, cl)
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            return new Run(true, buf.toString());
        } catch (java.lang.reflect.InvocationTargetException e) {
            return new Run(false, "THROW: " + e.getCause());
        } finally {
            System.setOut(oldOut);
        }
    }

    private Run runNative(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        if (!r.success()) return new Run(false, diags(r));
        Path bin = out.resolve("Default/Main");
        assertTrue(Files.exists(bin), "binário nativo deve existir");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String o = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new Run(p.waitFor() == 0, o);
    }

    private Run runJs(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.JS);
        if (!r.success()) return new Run(false, diags(r));
        var buf = new ByteArrayOutputStream();
        try {
            int rc = KofJsRunner.run(out.resolve("Default.mjs"), buf,
                    new ByteArrayInputStream(new byte[0]), buf);
            return new Run(rc == 0, buf.toString());
        } catch (Exception e) {
            return new Run(false, "THROW: " + e.getMessage() + "\n" + buf);
        }
    }

    private static String diags(CompilationResult r) {
        StringBuilder sb = new StringBuilder();
        for (Diagnostic d : r.diagnostics().getDiagnostics()) {
            sb.append(d.code()).append(": ").append(d.message()).append('\n');
        }
        return sb.toString();
    }

    @Test
    void hangingCallIsStoppedByItsOwnDeadlineAndTheEngineIsReusable() throws Exception {
        requirePython3();
        Path src = tmp.resolve("deadline.kf");
        Files.writeString(src, """
                import kof.interop
                main() {
                    var py = KofPy("def loop():\\n    while True:\\n        pass\\ndef sq(n):\\n    return n*n")
                    py.timeout(1000)
                    val t0 = time.now()
                    try {
                        println(py.callInt("loop", listOf()))
                    } catch (String e) {
                        println(e)
                    }
                    val dt = time.now() - t0
                    println(dt >= 900)
                    println(dt < 10000)
                    println(py.callInt("sq", listOf(5)))
                }
                """);
        long start = System.currentTimeMillis();
        Run jvm = runJvm(src, tmp.resolve("out-deadline"));
        long wall = System.currentTimeMillis() - start;
        assertTrue(jvm.ok(), "JVM: " + jvm.output());
        assertTrue(jvm.output().contains("INTEROP007"),
                "deadline estourada deve ser INTEROP007 nomeada, nunca hang: " + jvm.output());
        assertTrue(jvm.output().contains("true\ntrue\n25"),
                "espera limitada no relógio do teste + motor VIVO de novo depois do 007 (reuse): "
                        + jvm.output());
        assertTrue(wall < 30000, "o teste inteiro tem de voltar limitado: " + wall + "ms");
    }

    @Test
    void timeout007MatchesJvmOnX86() throws Exception {
        requirePython3();
        Path src = tmp.resolve("deadline-nat.kf");
        Files.writeString(src, """
                import kof.interop
                main() {
                    var py = KofPy("def loop():\\n    while True:\\n        pass")
                    py.timeout(1000)
                    try {
                        println(py.callInt("loop", listOf()))
                    } catch (String e) {
                        println(e)
                    }
                }
                """);
        Run jvm = runJvm(src, tmp.resolve("out-007j"));
        assertTrue(jvm.ok(), "JVM base: " + jvm.output());
        assertTrue(jvm.output().contains("INTEROP007"), "JVM 007: " + jvm.output());
        Run nat = runNative(src, tmp.resolve("out-007n"));
        assertEquals(jvm.output(), nat.output(), "INTEROP007 JVM≡x86 broken");
        Run js = runJs(src, tmp.resolve("out-007js"));
        assertEquals(jvm.output(), js.output(),
                "INTEROP007 JVM≡JS broken (deadline é do filho python, o alvo só lê o status): " + js.output());
    }

    @Test
    void cancelFromAnotherTaskStopsTheRunningCallNamed008() throws Exception {
        requirePython3();
        Path src = tmp.resolve("cancel.kf");
        Files.writeString(src, """
                import kof.interop
                void later(KofPy py) {
                    time.sleep(700)
                    py.cancel()
                }
                main() {
                    var py = KofPy("def nap():\\n    import time\\n    time.sleep(30)\\n    return 1")
                    py.timeout(0)
                    val t = spawn later(py)
                    try {
                        println(py.callInt("nap", listOf()))
                    } catch (String e) {
                        println(e)
                    }
                    await t
                    try {
                        println(py.callInt("never", listOf()))
                    } catch (String e2) {
                        println(e2)
                    }
                }
                """);
        Run jvm = runJvm(src, tmp.resolve("out-cancel"));
        assertTrue(jvm.ok(), "JVM: " + jvm.output());
        assertTrue(jvm.output().contains("INTEROP008"),
                "cancel() de outra task deve nomear INTEROP008: " + jvm.output());
        assertTrue(jvm.output().contains("INTEROP006"),
                "motor vivo depois do 008 — erro de função inexistente é remoto (006), nunca 004/008: "
                        + jvm.output());
    }

    @Test
    void cancelIsANoopOutsideALiveCall() throws Exception {
        requirePython3();
        Path src = tmp.resolve("cancel-idle.kf");
        Files.writeString(src, """
                import kof.interop
                main() {
                    var py = KofPy("def sq(n):\\n    return n*n")
                    py.cancel()
                    println(py.callInt("sq", listOf(6)))
                    py.cancel()
                }
                """);
        Run jvm = runJvm(src, tmp.resolve("out-idle"));
        assertTrue(jvm.ok(), "JVM: " + jvm.output());
        assertEquals("36\n", jvm.output(),
                "cancel sem chamada em curso = no-op honesto, antes e depois (idempotência)");
    }

    // X2 fatia 4 (27/09): deadline/cancel/reuso no cross (riscv64+aarch64 sob
    // qemu) — o filho python corre em tempo REAL (o spawn passa ao host), entao
    // os goldens JVM valem byte a byte. 008 fica JVM-only por desenho (objeto
    // cruzando spawn nao e contrato no Native — cabecalho da classe).
    private String runCross(Path src, Path out, Target t) throws Exception {
        String arch = t == Target.NATIVE_RISCV64 ? "riscv64" : "aarch64";
        org.junit.jupiter.api.Assumptions.assumeTrue(
                NativeRiscv64E2ETest.hasToolchain(arch),
                "cross toolchain " + arch + " + qemu ausente — pulando (NATIVE002)");
        CompilationResult r = driver.compile(src, out, t);
        assertTrue(r.success(), arch + " deve compilar o motor (§514): " + diags(r));
        return NativeRiscv64E2ETest.runQemu(arch, out.resolve("Default/Main"));
    }

    @Test
    void timeout007MatchesJvmOnCross() throws Exception {
        requirePython3();
        Path src = tmp.resolve("deadline-cross.kf");
        Files.writeString(src, """
                import kof.interop
                main() {
                    var py = KofPy("def loop():\\n    while True:\\n        pass")
                    py.timeout(1000)
                    try {
                        println(py.callInt("loop", listOf()))
                    } catch (String e) {
                        println(e)
                    }
                }
                """);
        Run jvm = runJvm(src, tmp.resolve("out-007jx"));
        assertTrue(jvm.ok(), "JVM base: " + jvm.output());
        assertTrue(jvm.output().contains("INTEROP007"), "JVM 007: " + jvm.output());
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            String cross = runCross(src, tmp.resolve("out-007x-" + t), t);
            assertEquals(jvm.output().trim(), cross.trim(), "INTEROP007 JVM≡" + t);
        }
    }

    @Test
    void deadlineReuseMatchesJvmOnCross() throws Exception {
        requirePython3();
        Path src = tmp.resolve("deadline-reuse-cross.kf");
        Files.writeString(src, """
                import kof.interop
                main() {
                    var py = KofPy("def loop():\\n    while True:\\n        pass\\ndef sq(n):\\n    return n*n")
                    py.timeout(1000)
                    val t0 = time.now()
                    try {
                        println(py.callInt("loop", listOf()))
                    } catch (String e) {
                        println(e)
                    }
                    val dt = time.now() - t0
                    println(dt >= 900)
                    println(dt < 10000)
                    println(py.callInt("sq", listOf(5)))
                }
                """);
        Run jvm = runJvm(src, tmp.resolve("out-reusejx"));
        assertTrue(jvm.ok(), "JVM: " + jvm.output());
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            String cross = runCross(src, tmp.resolve("out-reusex-" + t), t);
            assertEquals(jvm.output().trim(), cross.trim(),
                    "deadline+reuso JVM≡" + t + " (espera limitada + motor vivo): " + cross);
        }
    }

    @Test
    void cancelIdleIsNoopOnCross() throws Exception {
        requirePython3();
        Path src = tmp.resolve("cancel-idle-cross.kf");
        Files.writeString(src, """
                import kof.interop
                main() {
                    var py = KofPy("def sq(n):\\n    return n*n")
                    py.cancel()
                    println(py.callInt("sq", listOf(6)))
                    py.cancel()
                }
                """);
        Run jvm = runJvm(src, tmp.resolve("out-idlejx"));
        assertTrue(jvm.ok(), "JVM: " + jvm.output());
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            String cross = runCross(src, tmp.resolve("out-idlex-" + t), t);
            assertEquals(jvm.output().trim(), cross.trim(), "cancel ocioso JVM≡" + t);
        }
    }
}
