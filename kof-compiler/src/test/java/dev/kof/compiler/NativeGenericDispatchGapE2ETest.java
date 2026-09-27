package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §358 — chamada de MÉTODO de instância num receiver que é type-parameter
 * SEM bound (apaga para {@code Object}) nos alvos NATIVOS: o runtime cross não
 * tem dispatch genérico (vtable é por classe, sem um {@code Object} comum; os
 * primitivos são crus). Antes do fix o emit saía com {@code call toString} nu
 * → {@code ld: undefined reference to 'toString'} no link (repro #368,
 * re-medido no tip 21/09). Fix = recusa honesta no compile com {@code NAT004}
 * (padrão §352/NAT001-NAT002-NAT003), nunca link-fail críptico.
 *
 * <p>Q3: o caminho JVM (que ERA verde) continua byte-a-byte {@code "42\nhello"};
 * o bound ({@code T : Animal}) continua linkando (não é falso positivo).
 */
class NativeGenericDispatchGapE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String UNBOUNDED = """
        process<T>(item: T): String {
            return item.toString()
        }
        main() {
            println(process(42))
            println(process("hello"))
        }
        """;

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
            return new Run(true, buf.toString().replace("\r\n", "\n"));
        } catch (ReflectiveOperationException e) {
            Throwable c = e.getCause() != null ? e.getCause() : e;
            return new Run(false, "THROW: " + c);
        } finally {
            System.setOut(oldOut);
        }
    }

    @Test
    void unboundedTypeParamMethodCallRefusedOnNative(@TempDir Path tmp) throws Exception {
        // Q3 happy path JVM: a generic toString() legítima NÃO regride.
        Path jvm = tmp.resolve("GJvm.kf");
        Files.writeString(jvm, UNBOUNDED);
        Run rj = runJvm(jvm, tmp.resolve("oj"));
        assertTrue(rj.ok(), "JVM must keep compiling+running the generic toString: " + rj.output());
        assertEquals("42\nhello\n", rj.output(), "JVM output (Q3 non-regression)");

        // Native: recusa honesta NAT004 (lowering, antes de assembler/link).
        for (Target t : new Target[]{Target.NATIVE, Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            Path f = tmp.resolve("GN-" + t + "-" + System.nanoTime() + ".kf");
            Files.writeString(f, UNBOUNDED);
            CompilationResult rn = driver.compile(f, tmp.resolve("on-" + t + "-" + System.nanoTime()), t);
            assertFalse(rn.success(), t + " must refuse the unbounded generic dispatch");
            assertTrue(rn.diagnostics().getDiagnostics().stream()
                            .anyMatch(d -> "NAT004".equals(d.code())),
                    t + " NAT004 expected: " + rn.diagnostics().getDiagnostics());
        }
    }

    @Test
    void boundedTypeParamStaysGreenOnNative(@TempDir Path tmp) throws Exception {
        // Q3 controle: `T : Animal` apaga para o BOUND (não Object) — sem NAT004.
        String bounded = """
            class Animal {
                String name
                constructor(n: String) { name = n }
            }
            tag<T : Animal>(item: T): String {
                return item.name
            }
            main() {
                println(tag(Animal("rex")))
            }
            """;
        for (Target t : new Target[]{Target.NATIVE, Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            Path f = tmp.resolve("GB-" + t + "-" + System.nanoTime() + ".kf");
            Files.writeString(f, bounded);
            CompilationResult rb = driver.compile(f, tmp.resolve("ob-" + t + "-" + System.nanoTime()), t);
            assertTrue(rb.diagnostics().getDiagnostics().stream()
                            .noneMatch(d -> "NAT004".equals(d.code())),
                    t + " bounded type-param must NOT be refused: " + rb.diagnostics().getDiagnostics());
        }
    }

    private static String diags(CompilationResult r) {
        StringBuilder sb = new StringBuilder();
        r.diagnostics().getDiagnostics().forEach(d -> sb.append(d.message()).append("\n"));
        return sb.toString();
    }
}
