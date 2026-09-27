package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R4 — internal codegen hook {@link CodegenStepPipeline} (slice D12-A,
 * {@code DECISIONS.md} §D-CODEGEN-STEP, 21/09).
 *
 * <p>Proves the three properties that make the hook safe to land: (1) the empty
 * registry is the IDENTITY (zero behavior change); (2) steps run in registration
 * order and a null return fails loudly (R6/never silent); (3) a registered step
 * actually runs during a real compile, and an identity step leaves the emitted
 * bytecode byte-for-byte unchanged.
 */
class CodegenStepPipelineTest {

    private static final String HELLO = """
            main() { println("ok") }
            """;

    private static IRModule module(String name) {
        return new IRModule(name, List.of(), List.of());
    }

    /** Renames the module, so ordering is observable without touching classes. */
    private static CodegenStep rename(String suffix, List<String> order) {
        return new CodegenStep() {
            @Override
            public String name() {
                return "rename-" + suffix;
            }

            @Override
            public IRModule apply(IRModule m, CompilerDriver driver) {
                order.add(suffix);
                return new IRModule(m.name() + suffix, m.classes(), m.imports(),
                        m.sourceName(), m.sourceContent());
            }
        };
    }

    @Test
    void emptyRegistryIsIdentity() {
        CompilerDriver driver = new CompilerDriver();
        IRModule original = module("m");
        assertSame(original, CodegenStepPipeline.run(List.of(), original, driver),
                "no steps must return the caller's module untouched");
    }

    @Test
    void nullRegistryIsIdentity() {
        CompilerDriver driver = new CompilerDriver();
        IRModule original = module("m");
        assertSame(original, CodegenStepPipeline.run(null, original, driver));
    }

    @Test
    void stepsRunInRegistrationOrder() {
        CompilerDriver driver = new CompilerDriver();
        List<String> order = new ArrayList<>();
        IRModule out = CodegenStepPipeline.run(
                List.of(rename("A", order), rename("B", order)), module("m"), driver);
        assertEquals("mAB", out.name(), "steps must compose in registration order");
        assertEquals(List.of("A", "B"), order, "execution order must be deterministic");
    }

    @Test
    void nullReturnFailsLoudly() {
        CompilerDriver driver = new CompilerDriver();
        CodegenStep bad = new CodegenStep() {
            @Override
            public String name() {
                return "bad";
            }

            @Override
            public IRModule apply(IRModule m, CompilerDriver d) {
                return null;
            }
        };
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> CodegenStepPipeline.run(List.of(bad), module("m"), driver),
                "a step that drops the module must fail loudly, never silently (R6)");
        assertTrue(ex.getMessage().contains("bad"), "the diagnostic must name the offending step");
    }

    @Test
    void registeredStepRunsDuringCompile(@TempDir Path tmp) throws Exception {
        CompilerDriver driver = new CompilerDriver();
        AtomicInteger calls = new AtomicInteger();
        boolean[] sawMain = {false};
        driver.codegenSteps.add(new CodegenStep() {
            @Override
            public String name() {
                return "probe";
            }

            @Override
            public IRModule apply(IRModule m, CompilerDriver d) {
                calls.incrementAndGet();
                assertNotNull(m, "the step must receive a real module");
                assertSame(d, driver, "the step must receive the compiling driver");
                sawMain[0] = m.classes().stream()
                        .anyMatch(c -> c.name().endsWith("Main"));
                return m;
            }
        });

        Path file = tmp.resolve("Main.kf");
        Files.writeString(file, HELLO);
        CompilationResult result = driver.compile(file, tmp.resolve("out"), Target.JVM);

        assertTrue(result.success(), "program must compile: " + result.diagnostics().getDiagnostics());
        assertEquals(1, calls.get(), "the registered step must run exactly once per compile");
        assertTrue(sawMain[0], "the step must see the lowered Main class");
    }

    @Test
    void identityStepDoesNotChangeEmittedBytecode(@TempDir Path tmp) throws Exception {
        Path plain = tmp.resolve("plain");
        byte[] withoutHook = compileMain(tmp.resolve("a"), plain);

        CompilerDriver hooked = new CompilerDriver();
        hooked.codegenSteps.add(new CodegenStep() {
            @Override
            public String name() {
                return "identity";
            }

            @Override
            public IRModule apply(IRModule m, CompilerDriver d) {
                return m;
            }
        });
        byte[] withHook = compileMain(tmp.resolve("b"), tmp.resolve("bout"), hooked);

        assertArrayEquals(withoutHook, withHook,
                "an identity codegen step must not change the emitted bytecode");
    }

    private static byte[] compileMain(Path dir, Path outDir) throws Exception {
        return compileMain(dir, outDir, new CompilerDriver());
    }

    private static byte[] compileMain(Path dir, Path outDir, CompilerDriver driver) throws Exception {
        Path file = dir.resolve("Main.kf");
        Files.createDirectories(dir);
        Files.writeString(file, HELLO);
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "compile failed: " + result.diagnostics().getDiagnostics());
        Path mainClass = outDir.resolve("Default").resolve("Main.class");
        assertTrue(Files.exists(mainClass), "expected emitted Default/Main.class");
        return Files.readAllBytes(mainClass);
    }
}
