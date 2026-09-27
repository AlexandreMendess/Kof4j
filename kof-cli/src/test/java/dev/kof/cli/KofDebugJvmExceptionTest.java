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
 * E2E do DAP JVM Fase 7: `setExceptionBreakpoints` (JDWP Exception event) e
 * `pause` (JDWP ThreadReference.Suspend). Fala DAP com o CLI real e um
 * debuggee Java de verdade via JDWP.
 */
class KofDebugJvmExceptionTest {

    private record Cli(Process p, OutputStream to, InputStream from, Path err) {
    }

    private static Cli cli(Path dir, String... args) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add("dev.kof.cli.Main");
        cmd.addAll(List.of(args));
        Path err = dir.resolve("cli-err.txt");
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(dir.toFile());
        pb.redirectError(err.toFile());
        Process proc = pb.start();
        return new Cli(proc, proc.getOutputStream(), proc.getInputStream(), err);
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
        if (line == null) {
            String diag = "";
            try {
                diag = " | cli-err: " + Files.readString(c.err());
            } catch (Exception ignored) {
            }
            fail("fluxo DAP fechou" + diag);
        }
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
        String diag = "";
        try {
            diag = " | cli-err: " + Files.readString(c.err());
        } catch (Exception ignored) {
        }
        fail(label + " nunca chegou; vistos: " + seen + diag);
        return null;
    }

    @Test
    void dapExceptionBreakpointStopsAtThrow(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Main.kf"),
                "main() {\n    var x = 1\n    println(x)\n    throw \"boom\"\n}\n");
        Cli c = cli(dir, "debug", "--dap", "Main.kf");
        try {
            send(c, 1, "initialize", "");
            assertTrue(await(c, "\"command\":\"initialize\"", "initialize").contains("success"));
            send(c, 2, "launch", "\"program\":\"" + dir.resolve("Main.kf").toString().replace("\\", "/") + "\"");
            assertTrue(await(c, "\"command\":\"launch\"", "launch").contains("\"success\":true"));
            send(c, 3, "setExceptionBreakpoints", "\"filters\":[\"uncaught\"]");
            String eb = await(c, "\"command\":\"setExceptionBreakpoints\"", "setExceptionBreakpoints");
            assertTrue(eb.contains("\"success\":true"), eb);
            send(c, 4, "configurationDone", "");
            String stopped = await(c, "\"event\":\"stopped\"", "stopped por exception");
            assertTrue(stopped.contains("\"reason\":\"exception\""),
                    "o throw para o debuggee com reason exception: " + stopped);
        } finally {
            CliProcessTree.terminate(c.p());
        }
    }

    @Test
    void dapPauseStopsRunningProgram(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Main.kf"),
                "main() {\n    time.sleep(5000)\n    println(\"done\")\n}\n");
        Cli c = cli(dir, "debug", "--dap", "Main.kf");
        try {
            send(c, 1, "initialize", "");
            assertTrue(await(c, "\"command\":\"initialize\"", "initialize").contains("success"));
            send(c, 2, "launch", "\"program\":\"" + dir.resolve("Main.kf").toString().replace("\\", "/") + "\"");
            assertTrue(await(c, "\"command\":\"launch\"", "launch").contains("\"success\":true"));
            send(c, 3, "configurationDone", "");
            // deixa o laco rodar um pouco, entao pausa
            Thread.sleep(500);
            send(c, 4, "pause", "");
            assertTrue(await(c, "\"command\":\"pause\"", "pause").contains("\"success\":true"));
            String stopped = await(c, "\"event\":\"stopped\"", "stopped por pause");
            assertTrue(stopped.contains("\"reason\":\"pause\""),
                    "pause para o programa com reason pause: " + stopped);
            send(c, 5, "stackTrace", "\"startFrame\":0");
            String trace = await(c, "\"command\":\"stackTrace\"", "stackTrace apos pause");
            assertTrue(trace.contains("Main.kf"), "o frame parado aponta para a fonte Kof: " + trace);
        } finally {
            CliProcessTree.terminate(c.p());
        }
    }
}
