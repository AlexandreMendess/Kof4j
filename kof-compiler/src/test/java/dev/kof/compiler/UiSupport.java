package dev.kof.compiler;

import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Suporte do E2E de {@code kof.ui} ({@code UiE2ETest}): os runners JVM/Native e
 * o helper {@code both}. Os programas Kof hoisted vivem em {@code UiPrograms}.
 * Vive fora da classe de teste para mantê-la abaixo do limite de 500 linhas de
 * teste (Fase 3 da arquitetura de testes, {@code D-TEST-ARCHITECTURE-GO}); os
 * testes e o nome da classe seguem no {@code UiE2ETest} — zero drift de citação.
 */
abstract class UiSupport extends UiPrograms {

    protected final CompilerDriver driver = new CompilerDriver();

    protected static boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase().contains("linux");
    }

    protected String runJvm(Path source, Path outDir, String expected) throws IOException {
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
            assertEquals(expected, output, "Unexpected output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running JVM class", e);
        }
    }

    protected String runNative(Path source, Path outDir, String expected) throws IOException {
        assumeTrue(isLinux(), "Native target runs on Linux");
        CompilationResult result = driver.compile(source, outDir, Target.NATIVE);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        Path binFile = outDir.resolve("Default/Main");
        assertTrue(Files.exists(binFile), "Binary should exist");
        try {
            ProcessBuilder pb = new ProcessBuilder(binFile.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
            assertEquals(expected, output, "Unexpected output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running native binary", e);
        }
    }

    protected void both(Path tempDir, String name, String program, String expected) throws IOException {
        Path source = tempDir.resolve(name + ".kf");
        Files.writeString(source, program);
        runJvm(source, tempDir.resolve("jvm"), expected);
        runNative(source, tempDir.resolve("native"), expected);
    }

    /** Compila para JS, roda no engine embutido e devolve o stdout. */
    protected String runJs(Path tempDir, String name, String program) throws IOException {
        Path source = tempDir.resolve(name + "-js.kf");
        Files.writeString(source, program);
        CompilationResult js = driver.compile(source, tempDir.resolve("js-" + name), Target.JS);
        assertTrue(js.success(), "JS compilation should succeed: " + js.diagnostics().getDiagnostics());
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int code = dev.kof.runtime.KofJsRunner.run(
                tempDir.resolve("js-" + name).resolve("Default.mjs"), out,
                new java.io.ByteArrayInputStream(new byte[0]), out);
        assertEquals(0, code, "JS run should succeed: " + out);
        return out.toString().trim();
    }

    /**
     * Compila para JS e ANEXA um probe ao módulo gerado (o probe re-roda `main`
     * e lê o estado de runtime — `window.__kofNodes` etc.), devolvendo o stdout.
     * Mesmo harness de {@code ComponentCoreSupport.runJsProbe}.
     */
    protected String runJsProbe(Path tempDir, String name, String program,
                                String probeJs) throws IOException {
        Path source = tempDir.resolve(name + "-js.kf");
        Files.writeString(source, program);
        CompilationResult js = driver.compile(source, tempDir.resolve("js-" + name), Target.JS);
        assertTrue(js.success(), "JS compilation should succeed: " + js.diagnostics().getDiagnostics());
        Path module = tempDir.resolve("js-" + name).resolve("Default.mjs");
        Files.writeString(module, Files.readString(module) + "\n" + probeJs + "\n");
        dev.kof.compiler.js.JsRuntimeTestSupport.includeImportsOf(module.getParent(), probeJs);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int code = dev.kof.runtime.KofJsRunner.run(module, out,
                new java.io.ByteArrayInputStream(new byte[0]), out);
        assertEquals(0, code, "JS probe run should succeed: " + out);
        return out.toString().trim();
    }
}
