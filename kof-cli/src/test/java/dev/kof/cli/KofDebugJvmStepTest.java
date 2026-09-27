package dev.kof.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E do STEP do DAP na JVM (`next`/`stepIn`/`stepOut` + `evaluate`), a face
 * que faltava para a paridade com o DAP Native (X7-4). Fala DAP com o CLI real
 * e um debuggee Java de verdade via JDWP: breakpoint -> evaluate local ->
 * step over -> step into `add` -> step out de volta em `main`.
 *
 * O programa tem linhas estaveis (o mapa e a LineNumberTable do JVM):
 *   1  Int add(Int a, Int b) {
 *   2      return a + b
 *   3  }
 *   4  main() {
 *   5      var x = 1
 *   6      var y = 2
 *   7      var z = add(x, y)
 *   8      println(z)
 *   9  }
 */
class KofDebugJvmStepTest {

    private static final String PROGRAM =
            "Int add(Int a, Int b) {\n"
            + "    return a + b\n"
            + "}\n"
            + "main() {\n"
            + "    var x = 1\n"
            + "    var y = 2\n"
            + "    var z = add(x, y)\n"
            + "    println(z)\n"
            + "}\n";

    private record Cli(Process p, OutputStream to, InputStream from) {
    }

    private static Cli cli(Path dir, String... args) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add("dev.kof.cli.Main");
        cmd.addAll(List.of(args));
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(dir.toFile());
        pb.redirectErrorStream(false);
        Process proc = pb.start();
        return new Cli(proc, proc.getOutputStream(), proc.getInputStream());
    }

    private static void send(Cli c, int seq, String command, String argsJson) throws Exception {
        String body = "{\"seq\":" + seq + ",\"type\":\"request\",\"command\":\"" + command
                + "\",\"arguments\":{" + argsJson + "}}";
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        c.to().write(("Content-Length: " + b.length + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        c.to().write(b);
        c.to().flush();
    }

    private static String next(Cli c) throws Exception {
        String line;
        int len = -1;
        while ((line = KofDebug.readLine(c.from())) != null) {
            if (line.isBlank()) {
                break;
            }
            if (line.toLowerCase().startsWith("content-length:")) {
                len = Integer.parseInt(line.substring(15).trim());
            }
        }
        assertNotNull(line, "fluxo DAP fechou");
        if (len < 0) {
            return null;
        }
        return new String(c.from().readNBytes(len), StandardCharsets.UTF_8);
    }

    private static String await(Cli c, String needle, String label) throws Exception {
        long deadline = System.currentTimeMillis() + 60_000;
        List<String> seen = new ArrayList<>();
        while (System.currentTimeMillis() < deadline) {
            String m = next(c);
            if (m == null) {
                continue;
            }
            seen.add(m);
            if (m.contains(needle)) {
                return m;
            }
        }
        fail(label + " nunca chegou; vistos: " + seen);
        return null;
    }

    /** initialize -> launch -> break na linha 7 -> configurationDone -> stopped. */
    private static Cli upToBreakpoint(Path dir) throws Exception {
        Cli c = cli(dir, "debug", "--dap", "Main.kf");
        send(c, 1, "initialize", "");
        assertTrue(await(c, "\"command\":\"initialize\"", "initialize").contains("success"));
        send(c, 2, "launch", "\"program\":\"" + dir.resolve("Main.kf").toString().replace("\\", "/") + "\"");
        assertTrue(await(c, "\"command\":\"launch\"", "launch").contains("\"success\":true"), "launch");
        send(c, 3, "setBreakpoints", "\"source\":{\"path\":\"" + dir.resolve("Main.kf").toString().replace("\\", "/") + "\"},\"breakpoints\":[{\"line\":7}]");
        assertTrue(await(c, "\"command\":\"setBreakpoints\"", "setBreakpoints").contains("\"verified\""));
        send(c, 4, "configurationDone", "");
        String stopped = await(c, "\"event\":\"stopped\"", "stopped no breakpoint (linha 7 = add(x, y))");
        assertTrue(stopped.contains("breakpoint"), stopped);
        return c;
    }

    @Test
    void dapEvaluateLocalAndStepOver(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Main.kf"), PROGRAM);
        Cli c = upToBreakpoint(dir);
        try {
            send(c, 5, "evaluate", "\"expression\":\"x\",\"frameId\":0");
            String ev = await(c, "\"command\":\"evaluate\"", "evaluate de x");
            assertTrue(ev.contains("\"success\":true") && ev.contains("\"result\":\"1\""),
                    "o local x vale 1 no frame parado: " + ev);

            // z e declarado NA MESMA linha do breakpoint: ainda sem valor, entao
            // nao pode aparecer como um valor inventado (INVALID_SLOT no lote ->
            // fallback por-slot omite o ilegivel; R6).
            send(c, 6, "evaluate", "\"expression\":\"z\",\"frameId\":0");
            String z = await(c, "\"command\":\"evaluate\"", "evaluate de z");
            assertTrue(z.contains("\"success\":false"),
                    "z nao tem valor antes da store: recusa honesta: " + z);

            send(c, 7, "next", "");
            assertTrue(await(c, "\"command\":\"next\"", "next").contains("\"success\":true"));
            String stepped = await(c, "\"reason\":\"step\"", "stopped por step (next)");
            assertTrue(stepped.contains("\"reason\":\"step\""), stepped);

            send(c, 8, "stackTrace", "\"startFrame\":0");
            String trace = await(c, "\"command\":\"stackTrace\"", "stackTrace apos next");
            assertTrue(trace.contains("\"line\":8"),
                    "next em `var z = add(x, y)` (linha 7) para em `println(z)` (linha 8): " + trace);
        } finally {
            CliProcessTree.terminate(c.p());
        }
    }

    @Test
    void dapStepInAndStepOut(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Main.kf"), PROGRAM);
        Cli c = upToBreakpoint(dir);
        try {
            send(c, 5, "stepIn", "");
            assertTrue(await(c, "\"command\":\"stepIn\"", "stepIn").contains("\"success\":true"));
            await(c, "\"reason\":\"step\"", "stopped por step (stepIn)");
            send(c, 6, "stackTrace", "\"startFrame\":0");
            String inAdd = await(c, "\"command\":\"stackTrace\"", "stackTrace dentro de add");
            assertTrue(inAdd.contains("\"name\":\"add\""),
                    "stepIn entra na funcao add: " + inAdd);

            send(c, 7, "stepOut", "");
            assertTrue(await(c, "\"command\":\"stepOut\"", "stepOut").contains("\"success\":true"));
            await(c, "\"reason\":\"step\"", "stopped por step (stepOut)");
            send(c, 8, "stackTrace", "\"startFrame\":0");
            String back = await(c, "\"command\":\"stackTrace\"", "stackTrace de volta em main");
            assertFalse(back.contains("\"name\":\"add\""),
                    "stepOut sai de add de volta para main: " + back);
        } finally {
            CliProcessTree.terminate(c.p());
        }
    }

    @Test
    void dapEvaluateRefusesNonLocalHonestly(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Main.kf"), PROGRAM);
        Cli c = upToBreakpoint(dir);
        try {
            send(c, 5, "evaluate", "\"expression\":\"x + y\",\"frameId\":0");
            String ev = await(c, "\"command\":\"evaluate\"", "evaluate de expressao");
            assertTrue(ev.contains("\"success\":false") && ev.contains("local variable name"),
                    "JDWP nao tem avaliador de expressao: recusa honesta, nunca valor inventado: " + ev);
        } finally {
            CliProcessTree.terminate(c.p());
        }
    }

    /**
     * Regressao da corrida do `step` (§397): `stepIn`/`stepOut` seguidos de
     * `stackTrace` sem threadId. O `step()` limpava `stoppedThread` DEPOIS do
     * `resume()`; quando o evento SingleStep chegava primeiro (outra thread, mais
     * provavel sob carga), a limpeza sobrescrevia o id novo com -1 — e o
     * `stackTrace` seguinte virava `FrameCount(-1)` = JDWP error 20, que matava a
     * sessao DAP ("fluxo DAP fechou"). Sessoes frescas em loop forcama a janela:
     * no codigo antigo alguma iteracao morre; no novo o id nunca e perdido.
     */
    @Test
    void stepThenImmediateStackTraceNeverLosesTheStoppedThread(@TempDir Path parent) throws Exception {
        for (int i = 0; i < 12; i++) {
            Path dir = Files.createDirectory(parent.resolve("it" + i));
            Files.writeString(dir.resolve("Main.kf"), PROGRAM);
            Cli c = upToBreakpoint(dir);
            try {
                send(c, 5, "stepIn", "");
                assertTrue(await(c, "\"command\":\"stepIn\"", "stepIn #" + i).contains("\"success\":true"));
                await(c, "\"reason\":\"step\"", "stopped por step (stepIn #" + i + ")");
                send(c, 6, "stackTrace", "\"startFrame\":0");
                String trace = await(c, "\"command\":\"stackTrace\"", "stackTrace #" + i);
                assertTrue(trace.contains("\"success\":true") && trace.contains("\"name\":\"add\""),
                        "o thread do SingleStep foi perdido (iteracao " + i + "): " + trace);

                send(c, 7, "stepOut", "");
                assertTrue(await(c, "\"command\":\"stepOut\"", "stepOut #" + i).contains("\"success\":true"));
                await(c, "\"reason\":\"step\"", "stopped por step (stepOut #" + i + ")");
                send(c, 8, "stackTrace", "\"startFrame\":0");
                String back = await(c, "\"command\":\"stackTrace\"", "stackTrace de volta #" + i);
                assertTrue(back.contains("\"success\":true"),
                        "stackTrace apos stepOut #" + i + ": " + back);
            } finally {
                CliProcessTree.terminate(c.p());
            }
        }
    }
}
