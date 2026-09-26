package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * §516 (26/09): `json.encode` de uma List&lt;Record&gt; no x86 despejava o
 * ponteiro cru do objeto (listTag sem tag de elemento-objeto + walker sem ramo).
 * Oráculo = saída real medida no JVM; prova de paridade JVM≡x86.
 */
class JsonNativeRecordListE2ETest {
    private final CompilerDriver driver = new CompilerDriver();

    private static final String SRC = """
            record Point(Int x, Int y)
            record Flag(Bool ok)
            main() {
                println(json.encode(listOf(Point(1, 2))))
                println(json.encode(listOf(Point(3, 4), Point(5, 6))))
                println(json.encode(listOf(Flag(true), Flag(false))))
                var p = json.decode<Point>("{\\"x\\":7,\\"y\\":8}")
                println(p.x())
                println(p.y())
            }
            """;

    private static final String ORACLE =
            "[{\"x\":1,\"y\":2}]\n[{\"x\":3,\"y\":4},{\"x\":5,\"y\":6}]\n[{\"ok\":true},{\"ok\":false}]\n7\n8\n";

    // §516 complemento (26/09): campo String de record. Dois bugs de raiz
    // achados no round-trip Python: (1) o coletor de schema testava campo
    // aninhado para QUALQUER ClassType — java.lang.String caia aqui, nao e
    // classe de usuario, e a tabela INTEIRA era pulada (encode List<S> dava
    // o "null" do notfound); (2) o fold nativo de decode passava ao
    // decode_string (que pede LITERAL com aspas) o valor ja cru do
    // find_value (sem aspas) -> campo String saia vazio ate para "abc".
    private static final String SRC4 = """
            record S(String t, Int n)
            main() {
                println(json.encode(listOf(S("hi", 1))))
                println(json.encode(listOf(S("a\\"b", 2), S("c\\nd", 3))))
                var m = json.decode<S>("{\\"t\\":\\"p\\\\\\"q\\\\nr\\",\\"n\\":9}")
                println(m.t())
                println(m.n())
            }
            """;
    private String runJvm(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.diagnostics().getDiagnostics().isEmpty(), "JVM compilação: " + diags(r));
        var old = System.out;
        var buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true));
        try {
            var cl = new URLClassLoader(new java.net.URL[]{out.toUri().toURL()},
                    getClass().getClassLoader());
            Class.forName("Default.Main", true, cl)
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw new AssertionError("JVM run", e.getCause());
        } finally {
            System.setOut(old);
        }
        return buf.toString(StandardCharsets.UTF_8);
    }

    private String runNative(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        assertTrue(r.diagnostics().getDiagnostics().isEmpty(), "NATIVE compilação: " + diags(r));
        Path bin = out.resolve("Default/Main");
        assertTrue(Files.exists(bin), "binário nativo deve existir");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String o = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, p.waitFor(), "exit nativo: " + o);
        return o;
    }

    @Test
    void stringFieldRecordsMatchJvmOracleOnX86(@TempDir Path tmp) throws Exception {
        assumeTrue(Files.exists(Path.of("/usr/bin/as")) || Files.exists(Path.of("/bin/as")),
                "as ausente — gate de ambiente, não regressão");
        Path src = tmp.resolve("RecStr.kf");
        Files.writeString(src, SRC4);
        String jvm = runJvm(src, tmp.resolve("out-jvm"));
        String nat = runNative(src, tmp.resolve("out-nat"));
        assertEquals(jvm, nat, "§516: JVM≡x86 — String field encode/decode (escapes incluídos)");
        assertTrue(jvm.contains("[{\"t\":\"hi\",\"n\":1}]"),
                "golden JVM (medido 26/09): " + jvm);
    }

    private static String diags(CompilationResult r) {
        StringBuilder sb = new StringBuilder();
        for (Diagnostic d : r.diagnostics().getDiagnostics()) sb.append(d).append('\n');
        return sb.toString();
    }

    private static final String SRC2 = """
            record Point(Int x, Int y)
            record Pair(Point a, Point b)
            main() {
                println(json.encode(listOf(Pair(Point(1, 2), Point(3, 4)))))
                var m = mapOf("p", Point(5, 6))
                println(json.encode(m))
            }
            """;

    private static final String SRC3 = """
            record T(Float v)
            main() {
                println(json.encode(listOf(T(1.5))))
            }
            """;

    @Test
    void recordListEncodeMatchesJvmOracleOnX86(@TempDir Path tmp) throws Exception {
        assumeTrue(Files.exists(Path.of("/usr/bin/as")) || Files.exists(Path.of("/bin/as")),
                "as ausente — gate de ambiente, não regressão");
        Path src = tmp.resolve("RecList.kf");
        Files.writeString(src, SRC);

        String jvm = runJvm(src, tmp.resolve("out-jvm"));
        // oráculo = medição real do JVM (não memória); a constante abaixo trava-a
        assertEquals(ORACLE, jvm, "golden JVM (medido 26/09)");

        String nat = runNative(src, tmp.resolve("out-nat"));
        assertEquals(ORACLE, nat, "§516: JVM≡x86 — List<Record> encode");
    }

    @Test
    void nestedRecordListAndMapMatchJvmOracleOnX86(@TempDir Path tmp) throws Exception {
        assumeTrue(Files.exists(Path.of("/usr/bin/as")) || Files.exists(Path.of("/bin/as")),
                "as ausente — gate de ambiente, não regressão");
        Path src = tmp.resolve("NestedRec.kf");
        Files.writeString(src, SRC2);

        String jvm = runJvm(src, tmp.resolve("out-jvm"));
        // oráculo = medição real do JVM (código-fonte do teste trava-a abaixo)
        assertEquals(
                "[{\"a\":{\"x\":1,\"y\":2},\"b\":{\"x\":3,\"y\":4}}]\n{\"p\":{\"x\":5,\"y\":6}}\n",
                jvm, "golden JVM nested/map (medido 26/09)");

        String nat = runNative(src, tmp.resolve("out-nat"));
        assertEquals(jvm, nat, "§516: JVM≡x86 — record aninhado + Map<String,Record>");
    }

    @Test
    void floatFieldRecordListIsDiagnosedOnNative(@TempDir Path tmp) throws Exception {
        assumeTrue(Files.exists(Path.of("/usr/bin/as")) || Files.exists(Path.of("/bin/as")),
                "as ausente — gate de ambiente, não regressão");
        Path src = tmp.resolve("FloatRec.kf");
        Files.writeString(src, SRC3);

        CompilationResult jr = driver.compile(src, tmp.resolve("out-jvm"), Target.JVM);
        assertTrue(jr.diagnostics().getDiagnostics().isEmpty(), "JVM deve compilar: " + diags(jr));

        CompilationResult nr = driver.compile(src, tmp.resolve("out-nat"), Target.NATIVE);
        String d = diags(nr);
        assertTrue(d.contains("JSN002"),
                "§516/R6: nativo deve diagnosticar JSN002 (sem tabela de schema), obteve: " + d);
    }
}
