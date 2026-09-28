package dev.kof.compiler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Shared readiness probe for the JVM web E2E fixtures: the server is started as a
 * child process, so the test must wait until it binds the port. This is the single
 * home of that bounded poll — test classes call {@link #awaitListening(Process, int)}
 * instead of duplicating the loop (and its {@code Thread.sleep}) each time.
 *
 * <p>Pure test infrastructure: the compiler is never touched.
 */
final class TestServerFixture {
    private TestServerFixture() {
    }

    static void awaitListening(Process serverProcess, int port) throws IOException {
        for (int attempt = 0; attempt < 40; attempt++) {
            if (!serverProcess.isAlive()) {
                String out = new String(serverProcess.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                        .replace("\r\n", "\n").trim();
                throw new IOException("server exited early: " + out);
            }
            try (Socket probe = new Socket()) {
                probe.connect(new InetSocketAddress("127.0.0.1", port), 200);
                return;
            } catch (IOException e) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        serverProcess.destroyForcibly();
        throw new IOException("server did not start listening on port " + port);
    }
}
