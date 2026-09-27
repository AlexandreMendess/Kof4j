package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §278 face gpu: {@code kof.gpu} no alvo ANDROID.
 *
 * Android reusa o backend JVM e o mesmo front/IR — o {@code Main.class} e
 * byte-a-byte igual ao do JVM (paridade por construcao). Como o ART nao tem
 * FFM ({@code java.lang.foreign}), o runtime injetado e o stub sem FFM
 * ({@code JvmVkStubRuntime}): {@code available()=false} e dispatch devolvendo
 * o fallback (nao-zero) para o caller cair no golden CPU. Um erro claro de
 * paridade nao pode ser escondido atras de um gap: o compilador emite o mesmo
 * programa dos dois lados; a diferenca de runtime e honesta e observavel.
 */
class GpuAndroidE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String GPU_PROGRAM = """
            main() {
                println(gpu.available())
                val rc = gpu.dispatchMatmul(new Int[4], new Int[4], new Int[4], 2, 2, 2)
                println(rc)
            }
            """;

    @Test
    void androidGpuEmitsByteIdenticalMainToJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, GPU_PROGRAM);

        CompilationResult jvm = driver.compile(source, tempDir.resolve("jvm"), Target.JVM);
        assertTrue(jvm.success(), "JVM baseline: " + jvm.diagnostics().getDiagnostics());

        CompilationResult android = driver.compile(source, tempDir.resolve("android"), Target.ANDROID);
        assertTrue(android.success(), "android compila kof.gpu desde §278: "
                + android.diagnostics().getDiagnostics());

        byte[] a = Files.readAllBytes(tempDir.resolve("jvm/Default/Main.class"));
        byte[] b = Files.readAllBytes(tempDir.resolve("android/Default/Main.class"));
        assertArrayEquals(a, b, "Main.class ANDROID deve ser identico ao JVM (paridade por construcao)");
    }

    @Test
    void androidGpuRuntimeIsFfmFreeWhileJvmUsesFfm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, GPU_PROGRAM);

        CompilationResult jvm = driver.compile(source, tempDir.resolve("jvm"), Target.JVM);
        assertTrue(jvm.success(), "JVM baseline: " + jvm.diagnostics().getDiagnostics());
        String jvmRuntime = new String(Files.readAllBytes(
                tempDir.resolve("jvm/dev/kof/runtime/KofRuntime.class")), StandardCharsets.ISO_8859_1);
        assertTrue(jvmRuntime.contains("java/lang/foreign"),
                "o runtime kof.gpu do JVM usa FFM (java.lang.foreign)");

        CompilationResult android = driver.compile(source, tempDir.resolve("android"), Target.ANDROID);
        assertTrue(android.success(), "android compila kof.gpu desde §278: "
                + android.diagnostics().getDiagnostics());
        String androidRuntime = new String(Files.readAllBytes(
                tempDir.resolve("android/dev/kof/runtime/KofRuntime.class")), StandardCharsets.ISO_8859_1);
        assertFalse(androidRuntime.contains("java/lang/foreign"),
                "o runtime kof.gpu do ANDROID nao pode referenciar FFM (ART nao tem java.lang.foreign)");
        assertTrue(androidRuntime.contains("kof_vk_available"),
                "o runtime do ANDROID expoe a entry point kof_vk_available (stub)");
    }

    @Test
    void androidGpuProgramRunsWithCpuFallback(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, GPU_PROGRAM);

        CompilationResult android = driver.compile(source, tempDir.resolve("android"), Target.ANDROID);
        assertTrue(android.success(), "android compila kof.gpu desde §278: "
                + android.diagnostics().getDiagnostics());

        // O bytecode Android roda no host (mesmo backend JVM); o stub prova a
        // degradacao honesta: available=false e dispatch=-1 (fallback CPU).
        String output = runClass(tempDir.resolve("android"));
        assertEquals("false\n-1", output, "stub Android: available=false + dispatch=-1");
    }

    private String runClass(Path outDir) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running class", e);
        }
    }
}
