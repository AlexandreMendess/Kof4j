package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * known-bugs §678 FACE B on the NATIVE targets (D-SEM-REJECTS-1210, the
 * maintainer's decision: plain `as` THROWS on all four targets, `as T?` is the
 * ALWAYS-NULL soft cast). The JVM has always thrown (CHECKCAST, the oracle) and
 * the Script + JS faces landed 12-13/10 (lane flatt); the native emitters
 * lowered {@code KofCheckCast} to a NO-OP, so {@code var a: Animal = Animal()}
 * then {@code var d = a as Dog} printed {@code true} and exited 0.
 *
 * <p>Scope of the native strict check — it mirrors the Script rule
 * ({@code KofInterpreter}'s non-null face) so the two agree by construction:
 * USER-CLASS targets and String (both carry a runtime type id); {@code null}
 * passes (the JVM CHECKCAST semantics); generics/erasure and the
 * {@code kof}-package containers pass through (the compile-time checker
 * governs). The {@code java.lang} boxes (Integer/Long/Double/…) have no runtime
 * type id on native, so they stay pass-through — measured and recorded as the
 * boundary, never a silent fallback.
 *
 * <p>RED-first: every native "throws" face below measured exit 0 pre-fix.
 */
class NativeCastFailE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    /** Failing plain cast over a USER class — the §678 face. */
    private static final String THROW_SRC = """
            class Animal { }
            class Dog extends Animal { }
            main() {
                var a: Animal = Animal()
                var d = a as Dog
                println(d != null)
            }
            """;

    /** Failing plain cast over String (its own runtime type id). */
    private static final String STR_THROW_SRC = """
            main() {
                var o: Object = 42
                var s = o as String
                println(s != null)
            }
            """;

    /** Successful faces + a null that must PASS (CHECKCAST semantics: o null
     *  vem do cast suave `as T?` — Kof não aceita literal `null`, SEM048). */
    private static final String OK_SRC = """
            class Animal { }
            class Dog extends Animal { }
            main() {
                var a: Animal = Dog()
                var d = a as Dog
                println(d != null)
                var s: Object = "ok"
                println((s as String) == "ok")
                var a2: Animal = Animal()
                var soft = a2 as Dog?
                var nn = soft as Dog
                println(nn == null)
                println("end")
            }
            """;

    @Test
    void nativeX86FailingPlainCastThrows() throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        assertThrowsLikeTheJvm("castfail-x86", THROW_SRC, Target.NATIVE, null);
    }

    @Test
    void nativeX86FailingStringCastThrows() throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        assertThrowsLikeTheJvm("castfail-str-x86", STR_THROW_SRC, Target.NATIVE, null);
    }

    @Test
    void nativeRiscv64FailingPlainCastThrows() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        assertThrowsLikeTheJvm("castfail-riscv", THROW_SRC, Target.NATIVE_RISCV64, "riscv64");
    }

    @Test
    void nativeAarch64FailingPlainCastThrows() throws Exception {
        Assumptions.assumeTrue(has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross aarch64 + qemu absent — skipping (NATIVE002)");
        assertThrowsLikeTheJvm("castfail-aarch64", THROW_SRC, Target.NATIVE_AARCH64, "aarch64");
    }

    @Test
    void nativeSuccessfulAndNullCastsMatchTheJvmOracle() throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        String oracle = runJvm("ok-jvm", OK_SRC);
        assertEquals("true\ntrue\ntrue\nend", oracle, "o oráculo JVM mudou — revise o caso");
        assertEquals(oracle, runTarget("ok-x86", OK_SRC, Target.NATIVE, null),
                "§678: as faces BEM-SUCEDIDAS e o null (que PASSA) têm de bater com o JVM");
    }

    @Test
    void nativeNullCastPassesOnTheCrossTargets() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        String oracle = runJvm("ok-null-jvm", OK_SRC);
        assertEquals(oracle, runTarget("ok-null-riscv", OK_SRC, Target.NATIVE_RISCV64, "riscv64"),
                "§678: null passa no cast (a semântica CHECKCAST) também no cross");
    }

    // ── harness ─────────────────────────────────────────────────────────────

    /** O oráculo é o JVM: um cast falho derruba lá, então os alvos têm de derrubar também. */
    private void assertThrowsLikeTheJvm(String name, String src, Target target, String arch) throws Exception {
        Path dir = tmp.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), src);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, target);
        assertTrue(r.success(), () -> name + " compile: " + r.diagnostics().getDiagnostics());
        Path binary = out.resolve("Default/Main");
        assertTrue(Files.isRegularFile(binary), name + ": binário nativo ausente");
        ProcessBuilder pb = arch == null
                ? new ProcessBuilder(binary.toString())
                : NativeRiscv64E2ETest.qemu(arch, binary);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = NativeRiscv64E2ETest.runBounded(p, name + " (" + target + ")");
        assertNotEquals(0, p.exitValue(),
                () -> "§678 face B: o cast SIMPLES que falha tem de DERRUBAR no nativo (o JVM derruba; "
                        + "o emissor nativo baixava KofCheckCast para NO-OP). saída: " + output);
    }

    private String runJvm(String name, String src) throws Exception {
        Path dir = tmp.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), src);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.JVM);
        assertTrue(r.success(), () -> name + " compile: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder("java", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8",
                "-cp", out.toString(), "Default.Main").redirectErrorStream(true).start();
        String o = NativeRiscv64E2ETest.runBounded(p, name + " jvm");
        assertEquals(0, p.exitValue(), () -> name + " (oráculo JVM) falhou: " + o);
        return o;
    }

    private String runTarget(String name, String src, Target target, String arch) throws Exception {
        Path dir = tmp.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), src);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, target);
        assertTrue(r.success(), () -> name + " compile: " + r.diagnostics().getDiagnostics());
        Path binary = out.resolve("Default/Main");
        assertTrue(Files.isRegularFile(binary), name + ": binário nativo ausente");
        ProcessBuilder pb = arch == null
                ? new ProcessBuilder(binary.toString())
                : NativeRiscv64E2ETest.qemu(arch, binary);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String o = NativeRiscv64E2ETest.runBounded(p, name + " (" + target + ")");
        assertEquals(0, p.exitValue(), () -> name + " saída: " + o);
        return o;
    }

    private static boolean has(String... cmds) {
        for (String c : cmds) {
            try {
                Process p = new ProcessBuilder("sh", "-c", "command -v " + c)
                        .redirectErrorStream(true).start();
                if (p.waitFor() != 0) return false;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }
}
