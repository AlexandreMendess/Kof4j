package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Audit 0.6.0 MATH-02: {@code math.min}/{@code max}/{@code clamp} and the
 *  {@code isEven}/{@code isOdd}/{@code isPositive}/{@code isNegative}/{@code isZero}
 *  family bound ANY argument type (arity-only check), so {@code math.min(1, 2.5)}
 *  passed {@code check} and died as a JVM {@code VerifyError} / a silent {@code 0}
 *  on native. The guards mirror {@code abs}/{@code sign}: a non-Int argument is
 *  refused at the binding layer with SEM025 (R6 — no silent widening). */
class KofMathTypeGuardTest {

    @TempDir
    Path tmp;

    private String diagOf(String tag, String src) throws Exception {
        Path file = tmp.resolve(tag + "/Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, src);
        CompilationResult r = new CompilerDriver().compile(file, tmp.resolve("out-" + tag), Target.JVM);
        return String.join("\n", r.diagnostics().getDiagnostics().stream()
                .map(Object::toString).toList());
    }

    @Test
    void nonIntArgsAreRefusedWithSem025() throws Exception {
        // the audit's exact repro faces
        assertTrue(diagOf("minMix", "main() {\n    println(\"\" + math.min(1, 2.5))\n}").contains("SEM025"),
                "math.min(Int, Double) must be refused at the binding layer");
        assertTrue(diagOf("minDD", "main() {\n    println(\"\" + math.min(3.5, 2.5))\n}").contains("SEM025"),
                "math.min(Double, Double) must be refused");
        assertTrue(diagOf("maxDD", "main() {\n    println(\"\" + math.max(1.0, 2.0))\n}").contains("SEM025"),
                "math.max(Double, Double) must be refused");
        assertTrue(diagOf("clampD", "main() {\n    println(\"\" + math.clamp(1.5, 0.0, 2.0))\n}").contains("SEM025"),
                "math.clamp(Double, ...) must be refused");
        assertTrue(diagOf("isEvenD", "main() {\n    println(\"\" + math.isEven(2.5))\n}").contains("SEM025"),
                "math.isEven(Double) must be refused");
        assertTrue(diagOf("minStr", "main() {\n    println(\"\" + math.min(\"a\", \"b\"))\n}").contains("SEM025"),
                "math.min(String, String) must be refused (the audit's garbage-pointer face)");
    }

    @Test
    void intFacesStillBindCleanly() throws Exception {
        assertTrue(diagOf("minII", "main() {\n    println(\"\" + math.min(1, 2))\n}").isEmpty(),
                "math.min(Int, Int) keeps binding");
        assertTrue(diagOf("maxII", "main() {\n    println(\"\" + math.max(1, 2))\n}").isEmpty(),
                "math.max(Int, Int) keeps binding");
        assertTrue(diagOf("clampIII", "main() {\n    println(\"\" + math.clamp(5, 0, 10))\n}").isEmpty(),
                "math.clamp(Int, Int, Int) keeps binding");
        assertTrue(diagOf("isEvenI", "main() {\n    println(\"\" + math.isEven(2))\n}").isEmpty(),
                "math.isEven(Int) keeps binding");
        assertTrue(diagOf("absD", "main() {\n    println(\"\" + math.abs(1.5))\n}").contains("SEM025"),
                "the abs/sign precedent (MATH-01) is unchanged");
    }
}
