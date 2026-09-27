package dev.kof.compiler.jvm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.kof.compiler.CompilationResult;
import dev.kof.compiler.CompilerDriver;
import dev.kof.compiler.Target;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * §385 (20/09) — o backend JVM emitia TODO local no LocalVariableTable com
 * Start=0/Length=método inteiro: um local declarado na linha do breakpoint é
 * "visível" mas ainda sem store, e o JDWP StackFrame.GetValues (batch) morria
 * em INVALID_SLOT (35) — locals vazios na linha de declaração. Raiz corrigida:
 * cada entrada do table agora começa no pc do PRIMEIRO store do seu slot
 * (long/double = 2 slots; slot sem store no método — raro, edge abaixo —
 * mantém o escopo do método inteiro). Prova = `javap -v` real no class
 * emitido, nunca texto de fonte.
 */
class LocalVariableTableScopesTest {

    private static final String REPRO = """
            Int add(Int a, Int b) { return a + b }
            main() {
                var x = 1
                var y = 2
                var z = add(x, y)
                println(z)
            }
            """;

    private static final String WIDE = """
            main() {
                var a = 1
                var big = 5000000000
                var c = 3
                println(a + c + big)
            }
            """;

    private boolean hasTool(String tool) {
        try {
            Process p = new ProcessBuilder(tool, "-version").start();
            return p.waitFor(15, TimeUnit.SECONDS);
        } catch (Exception e) {
            return false;
        }
    }

    /** Compila e devolve as linhas (name -> start) do LocalVariableTable de main. */
    private Set<String> tableEntries(Path dir, String program) throws Exception {
        CompilerDriver driver = new CompilerDriver();
        Files.createDirectories(dir);
        Path src = dir.resolve("Main.kf");
        Files.writeString(src, program);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "compile: " + r.diagnostics().getDiagnostics());
        assumeTrue(hasTool("javap"), "javap ausente");
        Process p = new ProcessBuilder("javap", "-v", "-c", "-p", "-cp", out.toString(),
                "Default.Main")
                .redirectErrorStream(true).start();
        String v = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        p.waitFor(30, TimeUnit.SECONDS);
        // LocalVariableTable section of main: lines "<start> <length> <slot> <name> <desc>"
        int mIdx = v.indexOf("public static void main");
        assertTrue(mIdx >= 0, "main nao encontrado no javap:\n" + v.substring(0, Math.min(400, v.length())));
        int lvt = v.indexOf("LocalVariableTable:", mIdx);
        assertTrue(lvt >= 0, "sem LocalVariableTable:\n" + v.substring(mIdx, Math.min(mIdx + 600, v.length())));
        int end = v.indexOf("LineNumberTable", lvt);
        String sec = end > lvt ? v.substring(lvt, end) : v.substring(lvt, Math.min(lvt + 800, v.length()));
        Set<String> starts = new HashSet<>();
        Matcher mm = Pattern.compile("\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\w+)\\s").matcher(sec);
        int entries = 0;
        while (mm.find()) {
            starts.add(mm.group(1) + ":" + mm.group(3)); // start -> slot
            entries++;
        }
        assertTrue(entries >= 3, "esperava >=3 entradas:\n" + sec);
        return starts;
    }

    @Test
    void eachLocalStartsAtItsOwnStore(@TempDir Path dir) throws Exception {
        Set<String> entries = tableEntries(dir, REPRO);
        // RED de §385: antes, TODOS comecavam em Start=0. Agora: no maximo um
        // local pode começar em 0 (nenhum store dele e o primeiro do metodo);
        // x/y/z comecam em pcs DISTINTOS (stores em linhas diferentes).
        long zeroStarts = entries.stream().filter(e -> e.startsWith("0:")).count();
        assertTrue(zeroStarts <= 1,
                "§385: um table todo-Start=0 e o bug original; entradas=" + entries);
        assertEquals(3, entries.size(), "x,y,z com starts distintos:\n" + entries);
    }

    /** ConfigGenTest do §385 foi o canario: ultimo store (2 palavras) colado
     *  ao RETURN implicito NAO pode ganhar label intermedio (Frame.merge do
     *  ASM com COMPUTE_FRAMES explode) — o fallback debugStart mantem a
     *  entrada no table e a classe valida. */
    @Test
    void lastStoreBeforeImplicitReturnStaysCompilable(@TempDir Path dir) throws Exception {
        CompilerDriver driver = new CompilerDriver();
        Files.createDirectories(dir);
        Path src = dir.resolve("Main.kf");
        Files.writeString(src, "main() {\n    var big = 5000000000\n    val s = \"x\"\n}\n");
        CompilationResult r = driver.compile(src, dir.resolve("out"), Target.JVM);
        assertTrue(r.success(), "store final adjacente ao RETURN: "
                + r.diagnostics().getDiagnostics());
        Process p2 = new ProcessBuilder("java", "-cp", dir.resolve("out").toString(),
                "Default.Main").redirectErrorStream(true).start();
        p2.waitFor(60, TimeUnit.SECONDS);
        assertEquals(0, p2.exitValue(), "programa deve rodar");
    }

    @Test
    void wideLocalsAndPlainLocalsStayCorrect(@TempDir Path dir) throws Exception {
        Set<String> entries = tableEntries(dir, WIDE);
        assertEquals(3, entries.size(), "a/big/c — big (long, 2 slots) comeca apos seu store:\n"
                + entries);
        assertTrue(entries.stream().filter(e -> e.startsWith("0:")).count() <= 1,
                "no maximo um local com Start=0 legitimo (primeiro store do metodo):\n" + entries);
    }
}
