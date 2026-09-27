package dev.kof.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * X7-5 (doc §2 "attach — future", fechada 20/09): `kof debug --attach <porta|pid>`.
 * JVM = ANEXA a um Kof JVM vivo (debuggee com server=y,suspend=n) por JDWP cru —
 * prova REAL (sem stub: o socket e o HotSpot do proprio teste); o processo do
 * usuario NUNCA e morto no disconnect (semantica DAP de attach). Native = gdb -p
 * via ponte MI (stub-MI no host, como em X7-3/X7-4; gdb real na CI).
 */
class KofDebugAttachTest {

    private record Cli(Process p, OutputStream to, InputStream from, InputStream err) {
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
        return new Cli(proc, proc.getOutputStream(), proc.getInputStream(), proc.getErrorStream());
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

    @Test
    void jvmAttachBreaksIntoALivingProgramAndNeverKillsIt(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("Loop.kf");
        Files.writeString(src, "main() {\n    var beat = 0\n    while (true) {\n        beat = beat + 1\n        time.sleep(20)\n    }\n}\n");
        Path out = dir.resolve("out");
        dev.kof.compiler.CompilerDriver drv = new dev.kof.compiler.CompilerDriver();
        assertTrue(drv.compile(src, out, dev.kof.compiler.Target.JVM).success());

        int port;
        try (ServerSocket probe = new ServerSocket(0)) {
            port = probe.getLocalPort();
        }
        Process debuggee = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=" + port,
                "-cp", out.toString(), "Default.Main")
                .redirectErrorStream(true).start();
        debuggee.getOutputStream().close();
        Thread drain = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(debuggee.getInputStream(), StandardCharsets.UTF_8))) {
                while (r.readLine() != null) {
                    // consome para o pipe nunca encher
                }
            } catch (Exception ignored) {
            }
        });
        drain.setDaemon(true);
        drain.start();

        Cli c = cli(dir, "debug", "--attach", String.valueOf(port), "Loop.kf");
        try {
            send(c, 1, "initialize", "");
            assertTrue(await(c, "\"command\":\"initialize\"", "initialize").contains("success"));
            send(c, 2, "setBreakpoints", "\"source\":{\"path\":\"Loop.kf\"},\"breakpoints\":[{\"line\":3}]");
            assertTrue(await(c, "\"command\":\"setBreakpoints\"", "setBreakpoints")
                            .contains("\"verified\":true"),
                    "classe ja carregada no alvo vivo: breakpoint resolve via VM.Classes (1,3), nao so no ClassPrepare");
            assertTrue(await(c, "\"event\":\"stopped\"", "stopped").contains("breakpoint"),
                    "o loop bate na linha 3 (while) — o evento vem");
            send(c, 3, "stackTrace", "\"startFrame\":0");  // sem threadId: usa o da parada real
            assertTrue(await(c, "\"command\":\"stackTrace\"", "stackTrace").contains("Loop.kf"));
            send(c, 4, "disconnect", "");
            await(c, "\"command\":\"disconnect\"", "disconnect");
        } finally {
            c.p().waitFor(10, TimeUnit.SECONDS);
            assertTrue(debuggee.isAlive(),
                    "SEMANTICA DE ATTACH: disconnect fecha o ADAPTADOR, nunca mata o processo do usuario");
            CliProcessTree.terminate(debuggee);
        }
    }

    @Test
    void nativeDapAttachDrivesGdbDashPidOnTheLiveTarget(@TempDir Path dir) throws Exception {
        Path stub = dir.resolve("stub-gdb-mi.sh");
        Files.writeString(stub, "#!/bin/sh\n"
                + "echo \"$*\" > \"$KOF_GDB_ARGS_FILE\"\n"
                + "while IFS= read -r line; do\n"
                + "  tok=\"${line%%[!0-9]*}\"\n"
                + "  rest=\"${line#\"$tok\"}\"\n"
                + "  case \"$rest\" in\n"
                + "    -break-insert*) echo \"${tok}^done,bkpt={number=\\\"1\\\",line=\\\"7\\\"}\" ;;\n"
                + "    -stack-list-frames*) echo \"${tok}^done,stack=[frame={level=\\\"0\\\",func=\\\"main_kf\\\",file=\\\"Main.kf\\\",line=\\\"7\\\"}]\" ;;\n"
                + "    *) [ -n \"$tok\" ] && echo \"${tok}^done\" ;;\n"
                + "  esac\n"
                + "done\n");
        stub.toFile().setExecutable(true);
        Path argsFile = dir.resolve("argv.txt");
        Files.writeString(dir.resolve("Main.kf"), "main() { println(1) }\n");
        List<String> cmd = new ArrayList<>();
        cmd.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add("dev.kof.cli.Main");
        cmd.addAll(List.of("debug", "--dap", "--attach", "4242", "--target", "native", "Main.kf"));
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(dir.toFile());
        pb.redirectErrorStream(false);
        pb.environment().put("KOF_GDB", stub.toString());
        pb.environment().put("KOF_GDB_ARGS_FILE", argsFile.toString());
        Process p = pb.start();
        Cli c = new Cli(p, p.getOutputStream(), p.getInputStream(), p.getErrorStream());
        try {
            send(c, 1, "initialize", "");
            await(c, "\"command\":\"initialize\"", "initialize");
            send(c, 2, "setBreakpoints", "\"breakpoints\":[{\"line\":7}]");
            String bp = await(c, "\"command\":\"setBreakpoints\"", "breakpoints no alvo vivo");
            assertTrue(bp.contains("\"verified\":true"), bp);
            send(c, 3, "stackTrace", "\"startFrame\":0");
            assertTrue(await(c, "\"command\":\"stackTrace\"", "stack").contains("Main.kf"));
        } finally {
            CliProcessTree.terminate(p);
        }
        String argv = Files.readString(argsFile);
        assertTrue(argv.contains("-p 4242"), "attach NATIVE = gdb -p PID, sem build, sem launch: " + argv);
    }

    @Test
    void attachArgIsStrictAndJsStaysHonest(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Main.kf"), "main() { println(1) }\n");
        Cli bad = cli(dir, "debug", "--attach", "nao-e-porta", "Main.kf");
        Cli js = cli(dir, "debug", "--attach", "9", "--target", "js", "Main.kf");
        try {
            assertTrue(bad.p().waitFor(60, TimeUnit.SECONDS));
            assertNotEquals(0, bad.p().exitValue());
            String out = new String(bad.err().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(out.contains("number"), "erro nomeia o problema (R6): " + out);
            assertTrue(js.p().waitFor(60, TimeUnit.SECONDS));
            assertEquals(1, js.p().exitValue(), "attach JS = mesma recusa honesta do launch");
        } finally {
            CliProcessTree.terminate(bad.p());
            CliProcessTree.terminate(js.p());
        }
    }
}
