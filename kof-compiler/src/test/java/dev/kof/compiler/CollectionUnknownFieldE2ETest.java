package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * known-bugs §692: an UNKNOWN FIELD on a collection or on {@code String}
 * compiled CLEAN on all four targets and the Script printed a DEBUG STRING
 * ({@code ClassType[packageName=kof, name=Map, ...].keys}) instead of failing.
 *
 * <p>The field-access family already gates the sibling builtin surfaces —
 * {@code kof.io}/{@code Buffer}/{@code Secret}/{@code KeyHandle}/{@code
 * InteropError}/{@code Channel}/{@code Handle} (§491/§503/§505) and the
 * namespaces (§496) — but the COLLECTIONS and {@code String} were left out.
 * The §505 entry recorded them as a residual that "fails LOUD at runtime
 * ({@code NoSuchFieldError} on a class that DOES exist)"; the measurement
 * FALSIFIES that: on the Script it is a SILENT WRONG VALUE, and on the
 * compiling backends the emitted {@code getfield} dies later, at class load —
 * the exact R6 posture the family exists to remove.
 *
 * <p>RED-first: every {@code rejects*} face below compiled successfully
 * pre-fix. The valid spellings ({@code m.keys()}, {@code m.size},
 * {@code s.length}) are pinned too, so the gate cannot over-reach.
 */
class CollectionUnknownFieldE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    /** Um campo desconhecido em cada uma das quatro superfícies. */
    private static final String[][] BAD = {
            {"List", "main() {\n var l = listOf(1, 2)\n println(l.bogus)\n println(\"end\")\n}"},
            {"Map", "main() {\n var m = mapOf(\"a\", 1)\n println(m.bogus)\n println(\"end\")\n}"},
            {"Set", "main() {\n var s = setOf(1, 2)\n println(s.bogus)\n println(\"end\")\n}"},
            {"String", "main() {\n var s = \"abc\"\n println(s.bogus)\n println(\"end\")\n}"},
    };

    /** `m.keys` sem parênteses — o MÉTODO escrito como propriedade. */
    private static final String MAP_KEYS_AS_FIELD =
            "main() {\n var m = mapOf(\"a\", 1)\n println(m.keys)\n println(\"end\")\n}";

    /** As grafias VÁLIDAS que o gate não pode quebrar. */
    private static final String VALID =
            "main() {\n"
            + "    var m = mapOf(\"a\", 1, \"b\", 2)\n"
            + "    println(m.size)\n"
            + "    println(m.keys().size)\n"
            + "    println(m.values().size)\n"
            + "    println(m.keys().contains(\"a\"))\n"
            + "    var l = listOf(1, 2, 3)\n"
            + "    println(l.size)\n"
            + "    println(l.length)\n"
            + "    println(l.get(0))\n"
            + "    var s = setOf(1, 2)\n"
            + "    println(s.size)\n"
            + "    println(s.contains(1))\n"
            + "    var t = \"abc\"\n"
            + "    println(t.length)\n"
            + "    println(t.substring(1))\n"
            + "    println(\"end\")\n"
            + "}";

    private static final String VALID_GOLDEN = String.join("\n",
            "2", "2", "2", "true", "3", "3", "1", "2", "true", "3", "bc", "end");

    // ── as quatro superfícies recusam, em todos os alvos ────────────────────

    @Test
    void unknownFieldOnACollectionIsRejectedOnEveryTarget() throws Exception {
        // O Script nao emite artefatos (COMP003): ele se verifica pelo
        // interpretador, como fazem os testes de paridade do proprio Script.
        for (String[] bad : BAD) {
            Path dir = tmp.resolve("bad-" + bad[0]);
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("Main.kf"), bad[1]);

            for (Target target : new Target[]{Target.JVM, Target.JS, Target.NATIVE}) {
                String label = bad[0] + " @ " + target;
                CompilationResult r = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-" + target), target);
                assertFalse(r.success(), () -> "§692: campo desconhecido em " + label
                        + " tinha de ser RECUSADO (compilava limpo e o Script imprimia a string de debug). "
                        + "diagnosticos: " + r.diagnostics().getDiagnostics());
                String diags = r.diagnostics().getDiagnostics().toString();
                assertTrue(diags.contains("SEM102"),
                        () -> "§692: " + label + " tem de sair com SEM102 (a familia do §491/§505); veio: " + diags);
            }

            KofInterpretException scriptErr = assertThrows(KofInterpretException.class,
                    () -> driver.interpret(java.util.List.of(dir.resolve("Main.kf")), dir, new String[0]),
                    () -> "§692: " + bad[0] + " @ script tinha de ser RECUSADO");
            assertTrue(String.valueOf(scriptErr.getMessage()).contains("SEM102"),
                    () -> "§692: " + bad[0] + " @ script tem de sair com SEM102; veio: " + scriptErr.getMessage());
        }
    }

    @Test
    void methodSpelledAsAPropertyPointsAtTheMethod() throws Exception {
        Path dir = tmp.resolve("keys");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), MAP_KEYS_AS_FIELD);
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), dir.resolve("out"), Target.JVM);
        assertFalse(r.success(), () -> "§692: `m.keys` sem parênteses tinha de ser RECUSADO; veio: "
                + r.diagnostics().getDiagnostics());
        String diags = r.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("SEM102") && diags.contains("keys()"),
                () -> "§692: a mensagem tem de apontar o METODO (`keys()`), que e' o idioma do corpus; veio: " + diags);
    }

    @Test
    void theScriptNoLongerPrintsTheDebugString() throws Exception {
        Path dir = tmp.resolve("scriptvalue");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), BAD[1][1]);
        KofInterpretException scriptErr = assertThrows(KofInterpretException.class,
                () -> driver.interpret(java.util.List.of(dir.resolve("Main.kf")), dir, new String[0]),
                () -> "§692: o Script nao pode mais imprimir o descritor do tipo como valor");
        assertTrue(String.valueOf(scriptErr.getMessage()).contains("SEM102"),
                () -> "§692: veio: " + scriptErr.getMessage());
    }

    // ── as grafias validas seguem intactas, batendo com o oráculo JVM ───────

    @Test
    void validSpellingsStillCompileAndMatchTheJvmOracle() throws Exception {
        String oracle = runJvm("valid");
        assertEquals(VALID_GOLDEN, oracle, "o oraculo JVM mudou — revise o caso");
        assertEquals(oracle, runTarget("valid-script", Target.SCRIPT, null),
                "§692: o gate nao pode quebrar as grafias validas no Script");
        assertEquals(oracle, runTarget("valid-js", Target.JS, null),
                "§692: o gate nao pode quebrar as grafias validas no JS");
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        assertEquals(oracle, runTarget("valid-x86", Target.NATIVE, null),
                "§692: o gate nao pode quebrar as grafias validas no nativo");
    }

    // ── harness ─────────────────────────────────────────────────────────────

    private String runJvm(String name) throws Exception {
        Path dir = tmp.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), VALID);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.JVM);
        assertTrue(r.success(), () -> name + " compile: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder("java", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8",
                "-cp", out.toString(), "Default.Main").redirectErrorStream(true).start();
        String o = NativeRiscv64E2ETest.runBounded(p, name + " jvm");
        assertEquals(0, p.exitValue(), () -> name + " (oraculo JVM): " + o);
        return o;
    }

    private String runTarget(String name, Target target, String arch) throws Exception {
        Path dir = tmp.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), VALID);
        Path out = dir.resolve("out");
        if (target == Target.SCRIPT) {
            KofInterpreter.Result i = driver.interpret(
                    java.util.List.of(dir.resolve("Main.kf")), dir, new String[0]);
            assertEquals(0, i.exitCode(), () -> name + " script stderr: " + i.stderr());
            return i.stdout().strip();
        }
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, target);
        assertTrue(r.success(), () -> name + " compile: " + r.diagnostics().getDiagnostics());
        if (target == Target.JS) {
            var stdout = new java.io.ByteArrayOutputStream();
            int exit = dev.kof.runtime.KofJsRunner.run(out.resolve("Default.mjs"), stdout,
                    new java.io.ByteArrayInputStream(new byte[0]), stdout);
            assertEquals(0, exit, "js output: " + stdout);
            return stdout.toString(java.nio.charset.StandardCharsets.UTF_8).strip();
        }
        Path binary = out.resolve("Default/Main");
        assertTrue(Files.isRegularFile(binary), name + ": binario nativo ausente");
        ProcessBuilder pb = arch == null
                ? new ProcessBuilder(binary.toString())
                : NativeRiscv64E2ETest.qemu(arch, binary);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String o = NativeRiscv64E2ETest.runBounded(p, name + " (" + target + ")");
        assertEquals(0, p.exitValue(), () -> name + " saida: " + o);
        return o;
    }

    private static boolean has(String... cmds) {
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
