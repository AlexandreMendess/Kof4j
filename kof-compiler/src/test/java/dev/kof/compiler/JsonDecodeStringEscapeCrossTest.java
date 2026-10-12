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
 * known-bugs §667 — {@code json.decode<String>} must honour the FULL JSON
 * string escape set on every target ({@code \" \\ \/ \b \f \n \r \t} and the
 * 4-hex-digit unicode form), decoding to the same value byte-for-byte. The
 * JVM/Script runtime only stripped the outer quotes (escapes returned
 * VERBATIM) and the native x86-64/riscv asm handled {@code \n}/{@code \t} but
 * dropped the backslash of every other escape and had no unicode arm; JS was
 * already correct via {@code JSON.parse}. The golden below is the JVM
 * contract; the JVM, Script, JS and both native arches must agree. The
 * non-quoted fallback (an input that is not a JSON string) is a SEPARATE,
 * pre-existing divergence and is deliberately NOT pinned here.
 */
class JsonDecodeStringEscapeCrossTest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    // The Kof source is built by explicit concatenation (NOT a text block):
    // the Kof literals carry backslash escapes AND a 4-hex-digit unicode form,
    // and Java decodes the unicode escape at LEX time even inside a string
    // literal — so the backslash and the `u` must be split across fragments.
    private static final String PROGRAM =
            "main() {\n"
            + "    println(json.decode<String>(\"\\\"x\\\\ty\\\"\"))\n"
            + "    println(json.decode<String>(\"\\\"x\\\\ny\\\"\").length)\n"
            + "    println(json.decode<String>(\"\\\"x\\\\\\\\y\\\"\"))\n"
            + "    println(json.decode<String>(\"\\\"x\\\\\\\"y\\\"\"))\n"
            + "    println(json.decode<String>(\"\\\"a\\\\/b\\\"\"))\n"
            + "    println(json.decode<String>(\"\\\"\\\\b\\\\f\\\"\").length)\n"
            + "    println(json.decode<String>(\"\\\"\\\\" + "u0041\\\"\"))\n"
            + "    println(json.decode<String>(\"\\\"caf\\\\" + "u00e9\\\"\"))\n"
            + "    println(json.decode<String>(\"\\\"\\\\" + "u4e2d\\\"\"))\n"
            + "    println(json.decode<String>(\"\\\"plain\\\"\"))\n"
            + "}\n";

    private static final String GOLDEN = String.join("\n",
            "x\ty", "3", "x\\y", "x\"y", "a/b", "2", "A", "caf\u00e9", "\u4e2d", "plain");

    @Test
    void escapeDecodeOnJvmIsTheContract() throws Exception {
        assertEquals(GOLDEN, decRunJvm());
    }

    @Test
    void escapeDecodeOnScriptMatchesTheJvm() throws Exception {
        Path root = tmp.resolve("script");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROGRAM);
        KofInterpreter.Result result = driver.interpret(
                java.util.List.of(root.resolve("Main.kf")), root, new String[0]);
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(GOLDEN, result.stdout().strip());
    }

    @Test
    void escapeDecodeOnJsStaysCorrect() throws Exception {
        assertEquals(GOLDEN, decRunJs());
    }

    @Test
    void escapeDecodeOnNativeX86HonoursEveryEscape() throws Exception {
        Assumptions.assumeTrue(decHas("as", "ld"), "native toolchain absent");
        assertEquals(GOLDEN, decRunNativeX86());
    }

    @Test
    void escapeDecodeOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(decHas("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        assertEquals(GOLDEN, decRunCross("riscv64", Target.NATIVE_RISCV64));
    }

    @Test
    void escapeDecodeOnNativeAarch64() throws Exception {
        Assumptions.assumeTrue(decHas("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross aarch64 + qemu absent — skipping (NATIVE002)");
        assertEquals(GOLDEN, decRunCross("aarch64", Target.NATIVE_AARCH64));
    }

    private String decRunJvm() throws Exception {
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

    private String decRunJs() throws Exception {
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

    private String decRunNativeX86() throws Exception {
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

    private String decRunCross(String arch, Target target) throws Exception {
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

    private static boolean decHas(String... cmds) {
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
