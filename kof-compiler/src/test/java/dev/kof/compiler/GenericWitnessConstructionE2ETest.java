package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #585 — o witness de construção implícita (`Box<Point>(...)`, forma idiomática
 * de training/idioms/records.md) era descartado em {@code BuiltinCallTyper}:
 * a inferência devolvia a classe CRUA (typeArguments vazio) e a substituição
 * de {@code T} no receptor virava no-op — o retorno {@code get(): T} saía
 * {@code Object} e a chamada seguinte emitia {@code Methodref java/lang/Object}
 * → {@code NoSuchMethodError} no JVM. A face primitiva ({@code Box<Int>}) já
 * funcionava (§288), por isso o furo só aparecia com argumento reference-type.
 *
 * <p>Prova em 4 alvos: JVM (o caso da issue), Native x86_64, JS e o controle
 * primitivo (não pode regredir). A forma record-sugar {@code class Box<T>(T
 * value)} parseia como RecordDeclarationNode — a substituição por record já
 * existe (§355); o que faltava era o witness chegar tipado ao receptor.
 */
class GenericWitnessConstructionE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String RECORD_SUGAR_BOX = """
            record Point(Int x, Int y)

            class Box<T>(T value) {
                get(): T { return value }
            }

            main() {
                var b = Box<Point>(Point(5, 6))
                var p = b.get()
                println(p.x() + "," + p.y())
            }
            """;

    private String runJvm(Path outDir) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            assertEquals(0, p.waitFor(), "JVM exit code, output: " + output);
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
    }

    private String runNative(Path outDir) throws IOException {
        Path bin = outDir.resolve("Default").resolve("Main");
        try {
            ProcessBuilder pb = new ProcessBuilder(bin.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            assertEquals(0, p.waitFor(), "Native exit code, output: " + output);
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
    }

    private String runJs(Path outDir) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int exitCode = dev.kof.runtime.KofJsRunner.run(outDir.resolve("Default.mjs"), out,
                new ByteArrayInputStream(new byte[0]), out);
        assertEquals(0, exitCode, "JS exit code, output: " + out);
        return out.toString().trim();
    }

    private Path compileTo(Path tempDir, String name, String source, Target target) throws IOException {
        Path src = tempDir.resolve(name + ".kf");
        Files.writeString(src, source);
        Path out = tempDir.resolve(name + "-out");
        CompilationResult r = driver.compile(src, out, target);
        assertTrue(r.success(), name + " deve compilar p/ " + target + ": "
                + r.diagnostics().getDiagnostics());
        return out;
    }

    @Test
    void witnessWithRecordSugarMatchesJvmOnJvm(@TempDir Path tempDir) throws IOException {
        Path out = compileTo(tempDir, "w585j", RECORD_SUGAR_BOX, Target.JVM);
        assertEquals("5,6", runJvm(out), "a forma verbatim da #585 deve imprimir 5,6 no JVM");
    }

    @Test
    void witnessWithRecordSugarMatchesJvmOnNativeX86(@TempDir Path tempDir) throws IOException {
        Path out = compileTo(tempDir, "w585n", RECORD_SUGAR_BOX, Target.NATIVE);
        assertEquals("5,6", runNative(out), "a forma verbatim da #585 deve imprimir 5,6 no Native x86_64");
    }

    @Test
    void witnessWithRecordSugarMatchesJvmOnJs(@TempDir Path tempDir) throws IOException {
        Path out = compileTo(tempDir, "w585js", RECORD_SUGAR_BOX, Target.JS);
        assertEquals("5,6", runJs(out), "a forma verbatim da #585 deve imprimir 5,6 no JS");
    }

    @Test
    void directChainAndAnnotatedFormsMatchJvm(@TempDir Path tempDir) throws IOException {
        String chain = """
                record Point(Int x, Int y)

                class Box<T>(T value) {
                    get(): T { return value }
                }

                main() {
                    var b = Box<Point>(Point(5, 6))
                    println(b.get().x() + "," + b.get().y())
                }
                """;
        Path out = compileTo(tempDir, "w585c", chain, Target.JVM);
        assertEquals("5,6", runJvm(out), "a forma cadeia direta (sem var intermediária)");

        String annotated = """
                record Point(Int x, Int y)

                class Box<T>(T value) {
                    get(): T { return value }
                }

                main() {
                    var b = Box<Point>(Point(5, 6))
                    var p: Point = b.get()
                    println(p.x() + "," + p.y())
                }
                """;
        Path out2 = compileTo(tempDir, "w585a", annotated, Target.JVM);
        assertEquals("5,6", runJvm(out2), "a forma com anotação explícita de tipo");
    }

    @Test
    void explicitNewReferenceWitnessMatchesJvm(@TempDir Path tempDir) throws IOException {
        String ref = """
                record Point(Int x, Int y)

                class Box<T>(T value) {
                    get(): T { return value }
                }

                main() {
                    var b = new Box<Point>(Point(7, 8))
                    println(b.get().x() + "," + b.get().y())
                }
                """;
        Path out = compileTo(tempDir, "w585nr", ref, Target.JVM);
        assertEquals("7,8", runJvm(out), "`new Box<Point>(...)` (witness reference-type via NewExpr)");

        String str = """
                class Box<T>(T value) {
                    get(): T { return value }
                }

                main() {
                    var a = Box<String>("hello")
                    var b = new Box<String>("kof")
                    println(a.get().length())
                    println(b.get().toUpperCase())
                }
                """;
        Path out2 = compileTo(tempDir, "w585ns", str, Target.JVM);
        assertEquals("5\nKOF", runJvm(out2), "witness String nas formas implícita e explícita");
    }

    @Test
    void primitiveWitnessControlStaysGreen(@TempDir Path tempDir) throws IOException {
        String primitive = """
                record Point(Int x, Int y)

                class Box<T>(T value) {
                    get(): T { return value }
                }

                main() {
                    var b = Box<Int>(42)
                    println(b.get())
                }
                """;
        Path out = compileTo(tempDir, "w585p", primitive, Target.JVM);
        assertEquals("42", runJvm(out), "controle primitivo (§288) não pode regredir");
    }

    private static final String BARE_FN = """
            record Point(Int x, Int y)

            T idf<T>(T x) { return x }

            main() {
                var p = idf<Point>(Point(5, 6))
                println(p.x() + "," + p.y())
            }
            """;

    /** #592 — o reproducer verbatim da issue (função de topo `T idf<T>(T)`). */
    @Test
    void bareTopLevelGenericReturnMatchesJvmOnJvm(@TempDir Path tempDir) throws IOException {
        Path out = compileTo(tempDir, "w592j", BARE_FN, Target.JVM);
        assertEquals("5,6", runJvm(out), "#592: idf<Point> deve imprimir 5,6 no JVM");
    }

    @Test
    void bareTopLevelGenericReturnMatchesJvmOnNativeX86(@TempDir Path tempDir) throws IOException {
        Path out = compileTo(tempDir, "w592n", BARE_FN, Target.NATIVE);
        assertEquals("5,6", runNative(out), "#592: idf<Point> deve imprimir 5,6 no Native x86_64");
    }

    @Test
    void bareTopLevelGenericReturnMatchesJvmOnJs(@TempDir Path tempDir) throws IOException {
        Path out = compileTo(tempDir, "w592js", BARE_FN, Target.JS);
        assertEquals("5,6", runJs(out), "#592: idf<Point> deve imprimir 5,6 no JS");
    }

    /** #592 — faces reference (record/String, com chamada de membro) e o
     *  controle primitivo num único programa (Q3). */
    @Test
    void bareTopLevelGenericReturnReferenceAndPrimitiveFaces(@TempDir Path tempDir) throws IOException {
        String src = """
                record Point(Int x, Int y)

                T idf<T>(T x) { return x }

                main() {
                    var p = idf<Point>(Point(9, 10))
                    println(p.x() + "," + p.y())
                    println(idf<String>("kof").length())
                    println(idf<Int>(7))
                }
                """;
        Path out = compileTo(tempDir, "w592f", src, Target.JVM);
        assertEquals("9,10\n3\n7", runJvm(out),
                "reference (record/String com membro) + primitivo não podem regredir");
    }
}
