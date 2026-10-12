package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** entity-history Phase 4 slice 4a (D-ENTITY-HISTORY, Q14 decided): the
 *  `observability.correlationId()` was a FRESH random UUID per call — two
 *  calls inside ONE web request returned different ids. The fix: the web
 *  dispatch already sets/clears `KOF_LOG_REQUEST_ID` per request — the
 *  correlation id now returns IT when a request is active, falling back to
 *  the fresh UUID outside requests (jobs/CLI keep today's behavior). */
class ObservabilityCorrelationIdRequestBoundE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    @Test
    void correlationIdIsStableWithinARequestAndDiffersAcrossRequests(@TempDir Path dir)
            throws Exception {
        int port = freePort();
        Path src = dir.resolve("Obs.kf");
        Files.writeString(src, """
                main() {
                    var app = web.app()
                    app.get("/corr") {
                        return observability.correlationId() + "," + observability.correlationId()
                    }
                    spawn {
                        app.listen(PORT)
                    }
                    var first = ""
                    var second = ""
                    var i = 0
                    while (i < 100) {
                        try {
                            first = http.get("http://127.0.0.1:PORT/corr")
                            if (first.contains(",")) { i = 100 }
                        } catch (String e) { }
                        i = i + 1
                    }
                    var j = 0
                    while (j < 100) {
                        try {
                            second = http.get("http://127.0.0.1:PORT/corr")
                            if (second.contains(",")) { j = 100 }
                        } catch (String e) { }
                        j = j + 1
                    }
                    var parts = first.split(",")
                    println(parts[0] == parts[1])
                    var p2 = second.split(",")
                    println(p2[0] == parts[0])
                }
                """.replace("PORT", String.valueOf(port)));
        Path out = dir.resolve("out");
        CompilationResult result = driver.compile(src, out, Target.JVM);
        assertTrue(result.success(), () -> "compile failed: " + result.diagnostics().getDiagnostics());
        String cp = out + File.pathSeparator + findH2();
        Process p = new ProcessBuilder("java", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8",
                "-cp", cp, "Default.Main")
                .redirectErrorStream(true).start();
        String outp = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        int ec = p.waitFor();
        assertEquals("true\nfalse", outp, () -> "ec=" + ec + " out=" + outp
                + " — dentro do request os ids são IGUAIS; entre requests DIFERENTES");
    }

    private String findH2() {
        for (String e : System.getProperty("java.class.path").split(File.pathSeparator)) {
            if (e.contains("h2") && e.endsWith(".jar")) return e;
        }
        throw new IllegalStateException("h2 jar not found on test classpath");
    }
}
