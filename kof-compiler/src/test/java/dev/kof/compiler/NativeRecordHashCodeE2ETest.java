package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §114 (face hash, 24/09) — hashCode() de record no Native não pode somar o
 * PONTEIRO de um campo String; o contrato é o JVM (`31*h + o.hashCode()`,
 * `o==null -> 0`). O campo String passa por `String.hashCode` de conteúdo
 * (`kof_string_hash_code` x86 / `String_hashCode` riscv, agora null-safe).
 * O oracle é o JVM, rodado no próprio teste; cobre String interna e
 * não-internada (concat), campo String nullable (null -> 0) e a regressão de
 * primitivos (Int/Long/Bool/Char já casam crus pela fórmula).
 */
class NativeRecordHashCodeE2ETest {

    private static final String PROGRAM = """
            record S(String t)
            record NS(String? t)
            record P(Int i, Long l, Bool b, Char c)
            record RF(Float f)
            record RD(Double d)
            record Inner(Int v)
            record Outer(Inner i, String s)
            record MaybeInner(Inner? i)

            Inner? maybeInner(Int v) {
                if (v > 0) { return Inner(v) }
                return null
            }

            String? maybe(Int v) {
                if (v > 0) { return "ab" }
                return null
            }

            String make(String a, String b) { return a + b }

            main() {
                var a = S("ab")
                var b = S("ab")
                println(a.hashCode() == b.hashCode())
                println(a.hashCode())
                println(b.hashCode())
                println(S("xy").hashCode())
                println(S(make("a", "b")).hashCode())
                println(S(make("a", "b")).hashCode() == a.hashCode())
                println(NS(maybe(-1)).hashCode())
                println(NS(maybe(1)).hashCode())
                println(P(1, 2, true, 'A').hashCode())
                println(P(0, 0, false, 'A').hashCode())
                println(RF(1.5).hashCode())
                println(RF(-0.0).hashCode())
                println(RD(2.5).hashCode())
                println(RD(-0.0).hashCode())
                println(RD(1.0).hashCode())
                println(Inner(7).hashCode())
                println(Outer(Inner(7), "z").hashCode())
                println(Outer(Inner(7), "z").hashCode() == Outer(Inner(7), "z").hashCode())
                println(Outer(Inner(7), "z").hashCode() == Outer(Inner(8), "z").hashCode())
                println(MaybeInner(maybeInner(-1)).hashCode())
                println(MaybeInner(maybeInner(1)).hashCode())
            }
            """;

    private static String run(Path tempDir, Target target, String program) throws IOException {
        CompilerDriver driver = new CompilerDriver();
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, program);
        Path outDir = tempDir.resolve("out-" + target.name());
        CompilationResult result = driver.compile(source, outDir, target);
        assertTrue(result.success(), target + " compile should succeed: "
                + result.diagnostics().getDiagnostics());
        if (target == Target.JVM) {
            return runJvm(tempDir, outDir);
        }
        Path binFile = outDir.resolve("Default/Main");
        assertTrue(Files.exists(binFile), target + " binary should exist");
        ProcessBuilder pb = new ProcessBuilder(binFile.toString());
        if (target == Target.NATIVE_RISCV64) {
            pb = NativeRiscv64E2ETest.qemu("riscv64", binFile);
        } else if (target == Target.NATIVE_AARCH64) {
            pb = NativeRiscv64E2ETest.qemu("aarch64", binFile);
        }
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        int ec;
        try {
            ec = p.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
        assertEquals(0, ec, target + " exit code should be 0, output: '" + output + "'");
        return output;
    }

    private static String runJvm(Path tempDir, Path outDir) throws IOException {
        try {
            Path runnerDir = Files.createDirectory(tempDir.resolve("runner"));
            Path runnerSrc = runnerDir.resolve("Run.java");
            Files.writeString(runnerSrc, """
                    public class Run {
                        public static void main(String[] args) throws Exception {
                            Class.forName(args[0]).getMethod("main", String[].class)
                                .invoke(null, (Object) new String[0]);
                        }
                    }
                    """);
            Process pCompile = new ProcessBuilder(TestJdk.javacBin(), "-d", runnerDir.toString(),
                    runnerSrc.toString()).start();
            assertEquals(0, pCompile.waitFor(), "runner javac");
            Process p = new ProcessBuilder(TestJdk.javaBin(),
                    "-cp", outDir.toString() + java.io.File.pathSeparator + runnerDir.toString(), "Run", "Default.Main")
                    .redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "JVM exit code " + ec + ", output: " + output);
            return output;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }

    @Test
    void recordHashCodeMatchesJvmOnNativeX86(@TempDir Path tempDir) throws IOException {
        Path jvmDir = Files.createDirectory(tempDir.resolve("jvmdir"));
        String oracle = run(jvmDir, Target.JVM, PROGRAM);
        Path nativeDir = Files.createDirectory(tempDir.resolve("natdir"));
        String nativeOut = run(nativeDir, Target.NATIVE, PROGRAM);
        assertEquals(oracle, nativeOut, "Native record hashCode must match the JVM oracle");
    }

    @Test
    void recordHashCodeMatchesJvmOnRiscv64(@TempDir Path tempDir) throws IOException {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                NativeRiscv64E2ETest.hasToolchain("riscv64"),
                "toolchain riscv64/qemu ausente — pulando (NATIVE002)");
        Path jvmDir = Files.createDirectory(tempDir.resolve("jvmdir"));
        String oracle = run(jvmDir, Target.JVM, PROGRAM);
        Path nativeDir = Files.createDirectory(tempDir.resolve("natdir"));
        String nativeOut = run(nativeDir, Target.NATIVE_RISCV64, PROGRAM);
        assertEquals(oracle, nativeOut, "riscv64 record hashCode must match the JVM oracle");
    }

    @Test
    void recordHashCodeMatchesJvmOnAarch64(@TempDir Path tempDir) throws IOException {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                NativeRiscv64E2ETest.hasToolchain("aarch64"),
                "toolchain aarch64/qemu ausente — pulando (NATIVE002)");
        Path jvmDir = Files.createDirectory(tempDir.resolve("jvmdir"));
        String oracle = run(jvmDir, Target.JVM, PROGRAM);
        Path nativeDir = Files.createDirectory(tempDir.resolve("natdir"));
        String nativeOut = run(nativeDir, Target.NATIVE_AARCH64, PROGRAM);
        assertEquals(oracle, nativeOut, "aarch64 record hashCode must match the JVM oracle");
    }
}
