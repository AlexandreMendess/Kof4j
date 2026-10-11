package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The per-function gate of the {@code kof.buffer} namespace: {@code peek*}/
 * {@code poke*} touch raw memory and have NO JS runtime symbol (the JS Buffer
 * is emulated — no address space), so they must be REFUSED on JS with the
 * honest {@code FFI001} gap, never emitted as an artifact that dies at load on
 * a missing {@code kof-runtime.mjs} export. {@code buffer.alloc}/{@code bytes}
 * stay supported on JS (the namespace itself is fine there).
 *
 * <p>The gate matched the bare Kof names ({@code startsWith("peek")}) while the
 * dispatcher hands it the emitted SYMBOLS ({@code kof_buffer_peek8},
 * {@code kof_buffer_poke64_buf}) — the check never fired and JS fell through to
 * the namespace-wide {@code supportedOn(target)}, which admits JS.
 */
class BufferRawMemoryJsGateTest {

    private final CompilerDriver driver = new CompilerDriver();

    /** Buffer-form peek/poke — binds on JVM/Native/cross, no JS symbol. */
    private static final String PROGRAM_RAW_MEMORY = """
            main() {
                var b = buffer.alloc(16)
                buffer.poke64(b, 0, 1)
                println(buffer.peek64(b, 0))
            }
            """;

    /** Namespace core (no raw memory) — must stay supported on JS. */
    private static final String PROGRAM_ALLOC = """
            main() {
                var b = buffer.alloc(4)
                println(b.bytes())
            }
            """;

    @Test
    void rawMemoryPeekPokeRefusedOnJs(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("RawMemory.kf");
        Files.writeString(file, PROGRAM_RAW_MEMORY);
        CompilationResult r = driver.compile(file, tmp.resolve("out-js"), Target.JS);
        assertFalse(r.success(), "peek/poke must be refused on JS (no address space)");
        String diag = r.diagnostics().getDiagnostics().toString();
        assertTrue(diag.contains("FFI001"), "diagnostic must be FFI001, was: " + diag);
    }

    @Test
    void rawMemoryPeekPokeStillBindOnJvmAndNative(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("RawMemory2.kf");
        Files.writeString(file, PROGRAM_RAW_MEMORY);
        for (Target t : new Target[]{Target.JVM, Target.NATIVE}) {
            CompilationResult r = driver.compile(file, tmp.resolve("out-" + t), t);
            assertTrue(r.success(), t + ": peek/poke must bind: " + r.diagnostics().getDiagnostics());
        }
    }

    @Test
    void allocStaysSupportedOnJs(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("Alloc.kf");
        Files.writeString(file, PROGRAM_ALLOC);
        CompilationResult r = driver.compile(file, tmp.resolve("out-alloc"), Target.JS);
        assertTrue(r.success(),
                "buffer.alloc must stay supported on JS: " + r.diagnostics().getDiagnostics());
    }
}
