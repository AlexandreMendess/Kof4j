package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #588 — sondas ADVERSARIAIS e cross-target sobre o fix do `default:` vazio
 * no pattern-switch (o fix da lane 9092, §475): ataques que tentam trazer o
 * `goto <self>` de volta — default vazio sob ITERAÇÃO, aninhado, com GUARD e
 * misturado a cases de valor — em 4 alvos (JVM, x86 direto, riscv64/aarch64
 * sob qemu-user). Toda sonda deve TERMINAR e imprimir o total esperado.
 */
class Adv588E2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String VERBATIM = """
            record Point(Int x, Int y)

            main() {
                var p = Point(1, 2)
                var total = 0
                switch (p) {
                    case Point(var x, var y):
                        total = total + x + y
                    default:
                }
                println(total)
            }
            """;

    private Path compileTo(Path tempDir, String name, String source, Target target) throws IOException {
        Path src = tempDir.resolve(name + ".kf");
        Files.writeString(src, source);
        Path out = tempDir.resolve(name + "-out");
        CompilationResult r = driver.compile(src, out, target);
        assertTrue(r.success(), name + " deve compilar p/ " + target + ": "
                + r.diagnostics().getDiagnostics());
        return out;
    }

    private void assertJvmOutput(Path tempDir, String name, String source, String expected)
            throws IOException {
        Path out = compileTo(tempDir, name, source, Target.JVM);
        Process p = new ProcessBuilder("java", "-cp", out.toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String output;
        try {
            output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            assertEquals(0, p.waitFor(), name + " exit (hang = o self-jump voltou), output: " + output);
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
        assertEquals(expected, output, name + " deve imprimir o total e TERMINAR");
    }

    @Test
    void adversarialNestedLoopAndEmptyDefaultsTerminateJvm(@TempDir Path tempDir) throws IOException {
        String p1 = """
                record Point(Int x, Int y)
                record Pair(Int a, Int b)
                main() {
                    var i = 0
                    var acc = 0
                    while (i < 3) {
                        switch (Point(i, i)) {
                            case Point(var x, var y):
                                acc = acc + x
                            default:
                        }
                        switch (Pair(1, 2)) {
                            case Pair(var a, var b):
                                acc = acc + 1
                            default:
                        }
                        i = i + 1
                    }
                    println(acc)
                }
                """;
        assertJvmOutput(tempDir, "adv-p1", p1, "6");
    }

    @Test
    void adversarialGuardWithEmptyDefaultFallsThroughJvm(@TempDir Path tempDir) throws IOException {
        String p2 = """
                Int pick(Object o) {
                    var out = 0
                    switch (o) {
                        case String s if s.length() > 2:
                            out = 1
                        default:
                    }
                    return out
                }
                main() {
                    println(pick("ab"))
                }
                """;
        assertJvmOutput(tempDir, "adv-p2", p2, "0");
    }

    @Test
    void verbatimReproRunsOnNativeX86(@TempDir Path tempDir) throws IOException {
        Path out = compileTo(tempDir, "x588-x86", VERBATIM, Target.NATIVE);
        Process p = new ProcessBuilder(out.resolve("Default").resolve("Main").toString())
                .redirectErrorStream(true).start();
        String output;
        try {
            output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            assertEquals(0, p.waitFor(), "x86 exit, output: " + output);
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
        assertEquals("3", output, "verbatim #588 no x86 nativo");
    }

    @Test
    void verbatimReproRunsOnRiscv64UnderQemu(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(NativeRiscv64E2ETest.hasToolchain("riscv64"),
                "cross toolchain riscv64 + qemu ausente");
        Path out = compileTo(tempDir, "x588-rv", VERBATIM, Target.NATIVE_RISCV64);
        String output = NativeRiscv64E2ETest.runQemu("riscv64", out.resolve("Default").resolve("Main"));
        assertEquals("3", output.trim(), "verbatim #588 no riscv64 (qemu)");
    }

    @Test
    void verbatimReproRunsOnAarch64UnderQemu(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(NativeRiscv64E2ETest.hasToolchain("aarch64"),
                "cross toolchain aarch64 + qemu ausente");
        Path out = compileTo(tempDir, "x588-aa", VERBATIM, Target.NATIVE_AARCH64);
        String output = NativeRiscv64E2ETest.runQemu("aarch64", out.resolve("Default").resolve("Main"));
        assertEquals("3", output.trim(), "verbatim #588 no aarch64 (qemu)");
    }
}
