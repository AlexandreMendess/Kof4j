package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * known-bugs §681 — {@code random.hex(n)} with {@code n <= 0} on the native
 * x86-64 target did NOT return the contract's {@code null}: {@code
 * kof_random_hex} was a bare {@code jmp kof_sec_random_hex}, whose only callers
 * in the tree pass a literal 16/32, so the non-positive length was never
 * guarded. For {@code n = 0} it allocated the header and the hex loop (index
 * {@code n-1 = -1}, signed compare) scanned DOWN through the whole address
 * space writing hex bytes — ~2 GiB of garbage before the fault. The JVM and the
 * riscv64 twin already return {@code null} for {@code n <= 0}; the fix adds the
 * guard at the {@code random.hex} face wrapper, so the {@code security} face
 * keeps its own contract ({@code security.randomHex(0)} = {@code ""}). The
 * oracle is the JVM, measured; every target must agree byte-for-byte.
 */
class RandomHexNonPositiveNativeTest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private static final String PROGRAM = """
            main() {
                println(random.hex(0) == null)
                println(random.hex(-1) == null)
                println(random.hex(-100) == null)
                println(random.hex(4).length)
                println(random.hex(1).length)
                println(random.hex(16).length)
                println(random.hex(4) != null)
            }
            """;

    private static final String GOLDEN = String.join("\n",
            "true", "true", "true", "8", "2", "32", "true");

    /** The {@code security} face keeps its OWN contract (empty, not null) —
     *  pinned separately because it is refused on the cross arches (SECN000). */
    private static final String SECURITY_PROGRAM = """
            main() {
                println(security.randomHex(0).length)
                println(security.randomHex(4).length)
            }
            """;

    private static final String SECURITY_GOLDEN = String.join("\n", "0", "8");

    @Test
    void hexNonPositiveOnJvmIsTheContract() throws Exception {
        assertEquals(GOLDEN, runJvm());
    }

    /** The `security` face is a DIFFERENT contract (`""` for n<=0, and it
     *  throws outside 0..4096 on the JVM) — the `random.hex` guard must not
     *  leak into it. JVM+JS only: it is refused on the cross arches (SECN000). */
    @Test
    void securityFaceKeepsItsOwnContract() throws Exception {
        Path root = tmp.resolve("sec");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), SECURITY_PROGRAM);
        Path out = root.resolve("out");
        CompilationResult result = driver.compile(root.resolve("Main.kf"), out, Target.JVM);
        assertTrue(result.success(), () -> "compile: " + result.diagnostics().getDiagnostics());
        var stdout = new ByteArrayOutputStream();
        var previousOut = System.out;
        try {
            System.setOut(new java.io.PrintStream(stdout, true, StandardCharsets.UTF_8));
            try (var loader = new URLClassLoader(
                    new java.net.URL[]{out.toUri().toURL()}, getClass().getClassLoader())) {
                Class.forName("Default.Main", true, loader)
                        .getMethod("main", String[].class)
                        .invoke(null, (Object) new String[0]);
            }
        } finally {
            System.setOut(previousOut);
        }
        assertEquals(SECURITY_GOLDEN, stdout.toString(StandardCharsets.UTF_8).strip(),
                "security.randomHex conserva o próprio contrato (§681)");
    }

    @Test
    void hexNonPositiveOnScriptMatchesTheJvm() throws Exception {
        Path root = tmp.resolve("script");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROGRAM);
        KofInterpreter.Result result = driver.interpret(
                java.util.List.of(root.resolve("Main.kf")), root, new String[0]);
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(GOLDEN, result.stdout().strip());
    }

    @Test
    void hexNonPositiveOnJsMatchesTheJvm() throws Exception {
        assertEquals(GOLDEN, runJs());
    }

    @Test
    void hexNonPositiveOnNativeX86ReturnsNullInsteadOfGarbage() throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        assertEquals(GOLDEN, runNativeX86());
    }

    @Test
    void hexNonPositiveOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        assertEquals(GOLDEN, runCross("riscv64", Target.NATIVE_RISCV64));
    }

    @Test
    void hexNonPositiveOnNativeAarch64() throws Exception {
        Assumptions.assumeTrue(has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross aarch64 + qemu absent — skipping (NATIVE002)");
        assertEquals(GOLDEN, runCross("aarch64", Target.NATIVE_AARCH64));
    }

    private String runJvm() throws Exception {
        Path root = tmp.resolve("jvm");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROGRAM);
        Path out = root.resolve("out");
        CompilationResult result = driver.compile(root.resolve("Main.kf"), out, Target.JVM);
        assertTrue(result.success(), () -> "compile: " + result.diagnostics().getDiagnostics());
        var stdout = new ByteArrayOutputStream();
        var previousOut = System.out;
        try {
            System.setOut(new java.io.PrintStream(stdout, true, StandardCharsets.UTF_8));
            try (var loader = new URLClassLoader(
                    new java.net.URL[]{out.toUri().toURL()}, getClass().getClassLoader())) {
                Class.forName("Default.Main", true, loader)
                        .getMethod("main", String[].class)
                        .invoke(null, (Object) new String[0]);
            }
        } finally {
            System.setOut(previousOut);
        }
        return stdout.toString(StandardCharsets.UTF_8).strip();
    }

    private String runJs() throws Exception {
        Path root = tmp.resolve("js");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROGRAM);
        Path out = root.resolve("out");
        CompilationResult result = driver.compile(root.resolve("Main.kf"), out, Target.JS);
        assertTrue(result.success(), () -> "js compile: " + result.diagnostics().getDiagnostics());
        Path jsFile = out.resolve("Default.mjs");
        assertTrue(Files.isRegularFile(jsFile), "generated JS module must exist");
        var stdout = new ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(jsFile, stdout,
                new java.io.ByteArrayInputStream(new byte[0]), stdout);
        assertEquals(0, exit, "js output: " + stdout);
        return stdout.toString(StandardCharsets.UTF_8).strip();
    }

    private String runNativeX86() throws Exception {
        Path root = tmp.resolve("native-x86");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROGRAM);
        Path out = root.resolve("out");
        CompilationResult result = driver.compile(root.resolve("Main.kf"), out, Target.NATIVE);
        assertTrue(result.success(), () -> "native compile: " + result.diagnostics().getDiagnostics());
        Path binary = out.resolve("Default/Main");
        assertTrue(Files.isRegularFile(binary), "native binary must exist");
        Process process = new ProcessBuilder(binary.toString())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").strip();
        assertEquals(0, process.waitFor(), "native output: " + output);
        return output;
    }

    private String runCross(String arch, Target target) throws Exception {
        Path root = tmp.resolve("cross-" + arch);
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROGRAM);
        Path out = root.resolve("out");
        CompilationResult result = driver.compile(root.resolve("Main.kf"), out, target);
        assertTrue(result.success(), arch + " compile: " + result.diagnostics().getDiagnostics());
        Path binary = out.resolve("Default/Main");
        assertTrue(Files.isRegularFile(binary), arch + " binary must exist");
        ProcessBuilder pb = NativeRiscv64E2ETest.qemu(arch, binary);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").strip();
        assertEquals(0, process.waitFor(), arch + " output: " + output);
        return output;
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
