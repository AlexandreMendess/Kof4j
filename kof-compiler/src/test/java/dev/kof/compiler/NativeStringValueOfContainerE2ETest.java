package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * known-bugs §686: `String.valueOf(collection)` as a DIRECT call printed a RAW
 * POINTER on the native targets (`println(String.valueOf(listOf(1, 2, 3)))` →
 * garbage bytes) while JVM/Script/JS print the Kof container form.
 *
 * <p>Root: the JDK signature the call resolves to is `String.valueOf(Object)`,
 * which ERASES the Kof type; the native emitter picks its formatter from the
 * parameter type, so the erased value fell into `kof_box_to_string` — which
 * passes non-boxes through RAW. The runtime cannot recover the type (a list
 * stores raw, untagged elements: `kof_list_new` is the same call a Set makes
 * and only the Map carries a key tag), so the recovery happens at LOWERING
 * time from the static type the typer already knows (`NativeValueOfArgs`) —
 * the same treatment the `println` path gives a container.
 *
 * <p>RED-first: every native face below printed garbage pre-fix.
 */
class NativeStringValueOfContainerE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String SRC = """
            main() {
                println(String.valueOf(listOf(1, 2, 3)))
                println(String.valueOf(mapOf("k", 1)))
                println(String.valueOf(setOf("a", "b")))
                println(String.valueOf(listOf("x", "y")))
                println(String.valueOf(listOf(listOf(1), listOf(2))))
                println("end")
            }
            """;

    private static final String GOLDEN = String.join("\n",
            "[1, 2, 3]", "{k=1}", "[a, b]", "[x, y]", "[[1], [2]]", "end");

    @Test
    void jvmIsTheContractForStringValueOf() throws Exception {
        assertEquals(GOLDEN, runJvm("oracle"));
    }

    @Test
    void nativeX86MatchesTheJvm() throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        assertEquals(GOLDEN, runTarget("x86", Target.NATIVE, null));
    }

    @Test
    void nativeRiscv64MatchesTheJvm() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        assertEquals(GOLDEN, runTarget("riscv64", Target.NATIVE_RISCV64, "riscv64"));
    }

    @Test
    void nativeAarch64MatchesTheJvm() throws Exception {
        Assumptions.assumeTrue(has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross aarch64 + qemu absent — skipping (NATIVE002)");
        assertEquals(GOLDEN, runTarget("aarch64", Target.NATIVE_AARCH64, "aarch64"));
    }

    private String runJvm(String name) throws Exception {
        Path dir = tmp.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.JVM);
        assertTrue(r.success(), () -> name + " compile: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder("java", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8",
                "-cp", out.toString(), "Default.Main").redirectErrorStream(true).start();
        String o = NativeRiscv64E2ETest.runBounded(p, name + " jvm");
        assertEquals(0, p.exitValue(), () -> name + " (oráculo JVM, byte a byte): " + o);
        return o;
    }

    private String runTarget(String name, Target target, String arch) throws Exception {
        Path dir = tmp.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
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
