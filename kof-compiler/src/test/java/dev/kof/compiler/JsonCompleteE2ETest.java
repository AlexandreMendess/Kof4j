package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;


/**
 * Fase 2 — JSON nativo completo: Float/Double (JSN001) e arrays (JSN003)
 * no target JVM (JS já cobria via JSON.parse).
 */
class JsonCompleteE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path source, Path outDir, String expected) throws IOException {
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

    @Test
    void jvmEncodeDecodeFloatDouble(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                println(json.encode(3.14))
                println(json.encode(2.5))
                println(json.decode<Float>("1.5"))
                println(json.decode<Double>("2.75"))
                println(json.decode<Double>("-0.5"))
            }
            """);
        runJvm(source, tempDir.resolve("out"), "3.14\n2.5\n1.5\n2.75\n-0.5");
    }

    @Test
    void jvmDecodeIntFieldIntoBoolRecordBindsTrue(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            record Flag(Bool ok)

            main() {
                var a = json.decode<Flag>("{\\"ok\\": 1}")
                var b = json.decode<Flag>("{\\"ok\\": 0}")
                println(a.ok)
                println(b.ok)
            }
            """);
        runJvm(source, tempDir.resolve("out"), "true\nfalse");
    }

    @Test
    void jvmEncodeNaNAsNull(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var bad = 0.0 / 0.0
                println(json.encode(bad))
            }
            """);
        runJvm(source, tempDir.resolve("out"), "null");
    }

    @Test
    void jvmEncodeDoubleArray(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var arr = new Double[3]
                arr[0] = 1.5
                arr[1] = -2.25
                arr[2] = 0.0
                println(json.encode(arr))
            }
            """);
        runJvm(source, tempDir.resolve("out"), "[1.5,-2.25,0.0]");
    }

    @Test
    void jvmDecodeIntArray(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var arr = json.decode<Int[]>("[1, 2, 3]")
                println(arr.length)
                println(arr[0] + arr[2])
            }
            """);
        runJvm(source, tempDir.resolve("out"), "3\n4");
    }

    @Test
    void jvmDecodeStringArray(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var arr = json.decode<String[]>("[\\"a\\", \\"b\\"]")
                println(arr.length)
                println(arr[1])
            }
            """);
        runJvm(source, tempDir.resolve("out"), "2\nb");
    }

    @Test
    void jvmDecodeLongAndBoolArrays(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var longs = json.decode<Long[]>("[10, 20]")
                println(longs[0] + longs[1])
                var bools = json.decode<Bool[]>("[true, false, true]")
                println(bools[0] == true && bools[1] == false)
            }
            """);
        runJvm(source, tempDir.resolve("out"), "30\ntrue");
    }

    @Test
    void nativeEncodeDecodeFloatDoubleJsn001(@TempDir Path tempDir) throws IOException, InterruptedException {
        // JSN001 fechado: encode/decode de Float/Double no Native (FP XMM)
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                println(json.encode(3.14))
                println(json.decode<Double>("2.75"))
                println(json.decode<Float>("1.5"))
                var arr = json.decode<Double[]>("[1.5, -2.25]")
                println(arr.length)
                println(arr[1])
            }
            """);
        CompilationResult r = driver.compile(source, tempDir.resolve("native-out"), Target.NATIVE);
        assertTrue(r.success(), "Native Float/Double JSON should compile: "
                + r.diagnostics().getDiagnostics());
        Path binFile = tempDir.resolve("native-out").resolve("Default/Main");
        assertTrue(Files.exists(binFile), "Binary should exist");
        ProcessBuilder pb = new ProcessBuilder(binFile.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
            .replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
        assertTrue(output.contains("3.14"), "encode double: " + output);
        assertTrue(output.contains("2.75"), "decode double: " + output);
        assertTrue(output.contains("1.5"), "decode float: " + output);
        assertTrue(output.contains("2"), "array length: " + output);
        assertTrue(output.contains("-2.25"), "array elem: " + output);

        // Int[] continua decodificando (JSN003)
        Files.writeString(source, """
            main() {
                var arr = json.decode<Int[]>("[7]")
                println(arr.length)
            }
            """);
        r = driver.compile(source, tempDir.resolve("native-out3"), Target.NATIVE);
        assertTrue(r.success(), r.diagnostics().getDiagnostics().toString());
    }

    // §106 (decisão 2b, 13/09): json.encode(Map) -> objeto JSON com chaves
    // SORTED. Antes: JVM crashava (reflexão em HashMap →
    // InaccessibleObjectException); nativo dava link-error (kof_json_encode
    // genérico inexistente); interpretador ok. Golden = determinismo sorted.
    @Test
    void jvmEncodeMapSortedKeys(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            import kof.json.*
            main() {
                var m = mapOf("b", 2)
                m.put("a", 1)
                println(json.encode(m))
                var rec = mapOf("y", "z")
                rec.put("x", "w")
                println(json.encode(rec))
            }
            """);
        runJvm(source, tempDir.resolve("map-out"),
                "{\"a\":1,\"b\":2}\n{\"x\":\"w\",\"y\":\"z\"}");
    }

    @Test
    void nativeEncodeMapSortedKeys(@TempDir Path tempDir) throws IOException, InterruptedException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            import kof.json.*
            main() {
                var m = mapOf("b", 2)
                m.put("a", 1)
                println(json.encode(m))
            }
            """);
        CompilationResult r = driver.compile(source, tempDir.resolve("map-native"), Target.NATIVE);
        assertTrue(r.success(), "Native encode Map should compile: "
                + r.diagnostics().getDiagnostics());
        Path binFile = tempDir.resolve("map-native").resolve("Default/Main");
        assertTrue(Files.exists(binFile), "Binary should exist");
        ProcessBuilder pb = new ProcessBuilder(binFile.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
            .replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
        assertEquals("{\"a\":1,\"b\":2}", output, "sorted keys: " + output);
    }
}