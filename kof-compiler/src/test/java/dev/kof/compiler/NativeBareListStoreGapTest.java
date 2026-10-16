package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * known-bugs §745 — the NATIVE-only gap for storing a PRIMITIVE into a list
 * whose element type is a BARE (inferred) type parameter. Split out of
 * {@code DomainGapCodesTest} (test-architecture Phase 3, the oversized ratchet):
 * the pins here still feed {@code DomainGapParityMatrixTest}, which now reads
 * BOTH pin files.
 *
 * <p>Measured before the guard: {@code var b = listOf()} +
 * {@code listOf(1,2,3).forEach((x) -> b.add(x * 2))} printed {@code [2, 4, 6]}
 * on JVM/Script/JS but {@code [?, ?, ?]} on native, and {@code b.get(0)} KILLED
 * the program (x86 and riscv64 stopped right after the size; aarch64
 * SEGFAULTed). The same mechanism {@code NAT008} refuses for {@code zip}.
 */
class NativeBareListStoreGapTest {

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void addPrimitiveToBareListOnNativeIsNat009(@TempDir Path tmp) throws Exception {
        for (Target t : new Target[]{Target.NATIVE, Target.NATIVE_RISCV64,
                Target.NATIVE_AARCH64}) {
            assertGap(tmp, t, "NAT009", """
                main() {
                    var b = listOf()
                    listOf(1, 2, 3).forEach((x) -> b.add(x * 2))
                    println(b)
                }
                """);
        }
    }

    @Test
    void bareListBoundaryIsNotOverRefusedOnNative(@TempDir Path tmp) throws Exception {
        // §745 boundary: an EXPLICIT element type and a REFERENCE element stay
        // accepted on the native targets (only the bare-typed PRIMITIVE store is
        // the hazard) — measured correct on all six targets.
        for (Target t : new Target[]{Target.NATIVE, Target.NATIVE_RISCV64,
                Target.NATIVE_AARCH64}) {
            Path file = tmp.resolve("Main-" + System.nanoTime() + ".kf");
            Files.writeString(file, """
                main() {
                    List<Int> a = listOf()
                    listOf(1, 2, 3).forEach((x) -> a.add(x * 2))
                    println(a)
                    var s = listOf()
                    s.add("x")
                    println(s)
                }
                """);
            CompilationResult r = driver.compile(file, tmp.resolve("out-" + System.nanoTime()), t);
            assertTrue(r.success(), () -> t + " must NOT refuse the explicit/reference forms: "
                    + r.diagnostics().getDiagnostics());
        }
    }

    private void assertGap(Path tmp, Target target, String code, String source)
            throws java.io.IOException {
        Path file = tmp.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        CompilationResult result = driver.compile(file, tmp.resolve("out-" + System.nanoTime()), target);
        assertFalse(result.success(), target + " should refuse the call (" + code + ")");
        String diags = result.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains(code),
                "expected " + code + " for " + target + ", got: " + diags);
    }
}
