package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-MEMORY-CLEAR (O-03/`MEM003`) — a face OBSERVAVEL do contrato nos alvos de
 * programa: {@code clear()} esvazia o container e ele continua reutilizavel,
 * sem "resurreição" de elemento antigo. A prova do mecanismo (slots ANULADOS
 * na memoria) mora nos harnesses de asm nativos:
 * {@code NativeX86MemClearTest} + {@code NativeRiscvMemClearTest} (riscv64 +
 * aarch64) — aqui se trava o comportamento uniforme JVM≡Native≡JS≡Script.
 */
class MemoryClearE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir
    Path tmp;

    private static final String SRC = """
            main() {
                var xs = listOf(1, 2, 3)
                println(xs.size)
                xs.clear()
                println(xs.size)
                xs.add(9)
                println(xs.size)
                println(xs.get(0))

                var m = mapOf("a", 1, "b", 2)
                println(m.size)
                m.clear()
                println(m.size)

                var s = setOf("a", "b", "c")
                println(s.size)
                s.clear()
                println(s.size)
            }
            """;

    private static final String EXPECTED = "3\n0\n1\n9\n2\n0\n3\n0\n";

    private static String diags(CompilationResult r) {
        StringBuilder sb = new StringBuilder();
        for (Diagnostic d : r.diagnostics().getDiagnostics()) {
            sb.append(d.code()).append(": ").append(d.message()).append('\n');
        }
        return sb.toString();
    }

    private String runJvm(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compila: " + diags(r));
        var oldOut = System.out;
        var buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true));
        try {
            var cl = new URLClassLoader(new java.net.URL[]{out.toUri().toURL()},
                    getClass().getClassLoader());
            Class.forName("Default.Main", true, cl)
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            return buf.toString(StandardCharsets.UTF_8);
        } finally {
            System.setOut(oldOut);
        }
    }

    private String runNative(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        assertTrue(r.success(), "NATIVE compila: " + diags(r));
        Process p = new ProcessBuilder(out.resolve("Default/Main").toString())
                .redirectErrorStream(true).start();
        String o = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, p.waitFor(), "nativo saiu com erro:\n" + o);
        return o;
    }

    private String runJs(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.JS);
        assertTrue(r.success(), "JS compila: " + diags(r));
        var buf = new ByteArrayOutputStream();
        int rc = dev.kof.runtime.KofJsRunner.run(
                out.resolve("Default.mjs"), buf,
                new ByteArrayInputStream(new byte[0]), buf);
        assertEquals(0, rc, "JS saiu com erro:\n" + buf);
        return buf.toString(StandardCharsets.UTF_8);
    }

    private String runScript(Path src) throws Exception {
        KofInterpreter.Result ir = driver.interpret(List.of(src), tmp.resolve("o-script"), new String[0]);
        assertEquals(0, ir.exitCode(), "SCRIPT saiu com erro:\n" + ir.stderr());
        return ir.stdout();
    }

    @Test
    void clearEmptiesAndContainerStaysReusableOnJvmNativeJsScript() throws Exception {
        Path src = tmp.resolve("clear.kf");
        Files.writeString(src, SRC);

        assertEquals(EXPECTED, runJvm(src, tmp.resolve("o-jvm")), "JVM oracle");
        assertEquals(EXPECTED, runNative(src, tmp.resolve("o-nat")), "Native x86 == JVM");
        assertEquals(EXPECTED, runJs(src, tmp.resolve("o-js")), "JS == JVM");
        assertEquals(EXPECTED, runScript(src), "Script == JVM");
    }

    @Test
    void clearMatchesJvmOnCrossTargets() throws Exception {
        Path src = tmp.resolve("clear-cross.kf");
        Files.writeString(src, SRC);
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            String arch = t == Target.NATIVE_RISCV64 ? "riscv64" : "aarch64";
            org.junit.jupiter.api.Assumptions.assumeTrue(
                    NativeRiscv64E2ETest.hasToolchain(arch),
                    "cross toolchain " + arch + " + qemu ausente — pulando (NATIVE002)");
            Path f = tmp.resolve("clear-" + arch + ".kf");
            Files.writeString(f, SRC);
            Path out = tmp.resolve("o-" + arch);
            CompilationResult r = driver.compile(f, out, t);
            assertTrue(r.success(), arch + " compila clear: " + diags(r));
            assertEquals(EXPECTED.trim(), NativeRiscv64E2ETest.runQemu(arch, out.resolve("Default/Main")),
                    arch + " == JVM em clear()");
        }
    }
}
