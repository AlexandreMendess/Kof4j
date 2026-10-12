package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class KofEnumSwitchTest {
    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void switchExhaustiveJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
                enum Color { Red, Green, Blue }

                String cor(Color c) {
                    var r = ""
                    switch (c) {
                        case Color.Red: { r = "vermelho" }
                        case Color.Green: { r = "verde" }
                        case Blue: { r = "azul" }
                    }
                    return r
                }

                main() {
                    println(cor(Color.Red))
                    println(cor(Color.Green))
                    println(cor(Color.Blue))
                }
                """, "vermelho\nverde\nazul");
    }

    @Test
    void switchWithDefaultOk(@TempDir Path tmp) throws Exception {
        Path f = tmp.resolve("M.kf");
        Files.writeString(f, """
                enum Color { Red, Green, Blue }

                String cor(Color c) {
                    var r = ""
                    switch (c) {
                        case Red: { r = "r" }
                        default: { r = "outra" }
                    }
                    return r
                }

                main() {
                    println(cor(Color.Green))
                }
                """);
        CompilationResult result = driver.compile(f, tmp.resolve("out"), Target.JVM);
        assertTrue(result.success(), "default cobre os ausentes: " + result.diagnostics().getDiagnostics());
    }

    @Test
    void switchMissingCasesRejected(@TempDir Path tmp) throws Exception {
        Path f = tmp.resolve("M.kf");
        Files.writeString(f, """
                enum Color { Red, Green, Blue }

                String cor(Color c) {
                    var r = ""
                    switch (c) {
                        case Red: { r = "r" }
                    }
                    return r
                }

                main() {
                    println(cor(Color.Red))
                }
                """);
        CompilationResult result = driver.compile(f, tmp.resolve("out"), Target.JVM);
        assertFalse(result.success(), "Faltando casos sem default deve falhar");
        assertTrue(result.diagnostics().getDiagnostics().stream().anyMatch(d -> "SEM031".equals(d.code())),
                "Esperado SEM031: " + result.diagnostics().getDiagnostics());
        assertTrue(result.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> d.message().contains("Green") && d.message().contains("Blue")),
                "Deve listar os faltantes");
    }

    @Test
    void switchNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, """
                enum Color { Red, Green }

                String cor(Color c) {
                    var r = ""
                    switch (c) {
                        case Red: { r = "R" }
                        case Green: { r = "G" }
                    }
                    return r
                }

                main() {
                    println(cor(Color.Green))
                }
                """, "G");
    }

    // §674: Enum.valueOf(String) — no native, o handler de String.valueOf
    // interceptava o STATIC valueOf do enum (o dono é a CLASSE do enum, não
    // String) e devolvia a própria String de entrada; o consumidor lia a
    // String como instância de enum → SIGSEGV (139). RED-first: o nativo
    // quebrava antes; agora devolve a constante (ou null no miss) como JVM.
    @Test
    void valueOfReturnsTheConstantOnJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, ENUM_VALUEOF, "GREEN\n1\nBLUE\ntrue\ntrue");
    }

    @Test
    void valueOfReturnsTheConstantOnNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, ENUM_VALUEOF, "GREEN\n1\nBLUE\ntrue\ntrue");
    }

    private static final String ENUM_VALUEOF = """
            enum Color { RED, GREEN, BLUE }
            main() {
                var g = Color.valueOf("GREEN")
                println(g.name())
                println(g.ordinal())
                println(Color.valueOf("BLUE").name())
                println(Color.valueOf("NOPE") == null)
                println(Color.valueOf("RED") == Color.RED)
            }
            """;

    private String runJvm(Path tempDir, String source, String expected) throws java.io.IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compile failed: " + result.diagnostics().getDiagnostics());
        try {
            Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                    "-cp", outDir.toString(), "Default.Main").redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "JVM exit code, output: " + output);
            assertEquals(expected, output, "JVM output");
            return output;
        } catch (InterruptedException e) {
            throw new java.io.IOException("interrupted", e);
        }
    }

    private String runNative(Path tempDir, String source, String expected) throws java.io.IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.NATIVE);
        assertTrue(result.success(), "Native compile failed: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        try {
            Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Native exit code, output: " + output);
            assertEquals(expected, output, "Native output");
            return output;
        } catch (InterruptedException e) {
            throw new java.io.IOException("interrupted", e);
        }
    }
}
