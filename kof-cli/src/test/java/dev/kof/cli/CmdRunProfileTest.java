package dev.kof.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * B-1: {@code kof run --profile host|freestanding} — a mesma superfície do
 * {@code build}. O {@code CmdRun} sempre termina em {@code System.exit}, então o
 * teste roda o CLI num subprocesso (precedente de {@code CmdBuildFatTest}).
 * Prova que o perfil freestanding é aceito e executa o binário estático, e que
 * um alvo não-nativo é recusado nomeadamente (R6).
 */
class CmdRunProfileTest {

    private static String javaBin() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    private static Process startCli(Path workDir, String... cliArgs) throws IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add(javaBin());
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add("dev.kof.cli.Main");
        cmd.addAll(List.of(cliArgs));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(true);
        return pb.start();
    }

    private static boolean hasTool(String tool) {
        try {
            Process p = new ProcessBuilder(tool, "--version").start();
            return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    void runFreestandingExecutes(@TempDir Path dir) throws Exception {
        assumeTrue(hasTool("as") && hasTool("ld"), "x86 toolchain ausente");
        Path src = dir.resolve("Main.kf");
        Files.writeString(src, "main() { println(42) }\n");
        Process p = startCli(dir, "run", src.toString(),
                "--target", "native", "--profile", "freestanding");
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(180, TimeUnit.SECONDS), "run timeout\n" + out);
        assertEquals(0, p.exitValue(), "run freestanding rc\n" + out);
        assertTrue(out.contains("42"), "run freestanding deve imprimir 42: " + out);
    }

    @Test
    void runRejectsProfileOnNonNativeTarget(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("Main.kf");
        Files.writeString(src, "main() { println(1) }\n");
        Process p = startCli(dir, "run", src.toString(),
                "--target", "jvm", "--profile", "freestanding");
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(180, TimeUnit.SECONDS), "run timeout\n" + out);
        assertNotEquals(0, p.exitValue(), "run com perfil em JVM deve falhar: " + out);
        assertTrue(out.contains("--target native"), "mensagem R6 esperada: " + out);
    }
}
