package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §188 (voto (A) da mantenedora, CLOSEALL): `"2026" as Int` compilava e
 * morria no load (checkcast Integer sobre String). `as` = cast SEM parse
 * implícito — rejeita no cheque apontando os parsers do stdlib.
 */
class StringAsParseRejectTest {

    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compile(Path dir, String source) throws Exception {
        Path file = dir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        return driver.compile(file, dir.resolve("out-" + System.nanoTime()), Target.JVM);
    }

    private boolean sem100(CompilationResult r, String needle) {
        return r.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> "SEM100".equals(d.code())
                        && (needle == null || d.message().contains(needle)));
    }

    /** repro mínimo: literal String como Int */
    @Test
    void stringAsIntIsRejected(@TempDir Path tmp) throws Exception {
        var r = compile(tmp, """
            main() {
                var y = "2026" as Int
                println(y)
            }
            """);
        assertTrue(sem100(r, "math.parseInt"), "esperava SEM100 (§188): " + r.diagnostics());
        assertFalse(r.success());
    }

    /** repro do ledger (runtime, get() -> String) */
    @Test
    void ledgerReproRejected(@TempDir Path tmp) throws Exception {
        var r = compile(tmp, """
            main() {
                var today = "2026-09-21"
                var parts = today.split("-")
                var a = parts[0] as Int
            }
            """);
        assertTrue(sem100(r, null), "esperava SEM100 (§188); diagnostics: " + r.diagnostics());
    }

    /** todos os destinos primitivos: float/double/char/bool/long */
    @Test
    void allPrimitiveDestinationsRejected(@TempDir Path tmp) throws Exception {
        var r = compile(tmp, """
            main() {
                var a = "1.5" as Float
                var b = "2.5" as Double
                var c = "x" as Char
                var d = "true" as Bool
                var e = "9" as Long
            }
            """);
        assertTrue(sem100(r, null), "esperava SEM100: " + r.diagnostics());
    }

    /** cast numérico legítimo continua passando (100 as Long) */
    @Test
    void numericWidenCastStillOk(@TempDir Path tmp) throws Exception {
        var r = compile(tmp, """
            main() {
                var a = 100
                var b = a as Long
                println(b)
            }
            """);
        assertFalse(sem100(r, null), "SEM100 falso em cast numérico: " + r.diagnostics());
        assertTrue(r.success(), "cast numérico tem que compilar: " + r.diagnostics());
    }
}
