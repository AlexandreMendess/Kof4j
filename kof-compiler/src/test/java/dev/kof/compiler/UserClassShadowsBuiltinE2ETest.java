package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §243 — issue #261: uma classe DECLARADA pelo usuário com o nome de um builtin
 * Kof (`List`, `Set`, `Map`, `String`) era IGNORADA em todo ponto de uso — o
 * alias builtin vencia e o bytecode referenciaba `java.util.ArrayList`/
 * `java.lang.String` (`IllegalAccessError`/`NoSuchFieldError` em runtime),
 * embora o `List.class`/`String.class` do usuário fosse emitido.
 *
 * <p>Contrato ratificado em {@code docs/development/DECISIONS.md} §4/§179: o
 * shadowing do usuário é preservado — o builtin só vale quando o módulo/
 * SymbolTable não declara o nome. Estes testes travam as DUAS direções:
 * <ul>
 *   <li>(A) sombra do usuário: `class List/Set/Map/String` do usuário vence,
 *       campo write/read roda com o valor do usuário;</li>
 *   <li>(B) sem sombra: `new List/Set/Map` sem classe homônima continua sendo
 *       a coleção builtin (#139/#150/#214 não regridem).</li>
 * </ul>
 */
class UserClassShadowsBuiltinE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path tempDir, String source, String expected) throws IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compile failed: " + result.diagnostics().getDiagnostics());
        try {
            Path runnerDir = tempDir.resolve("run-" + System.nanoTime());
            Files.createDirectories(runnerDir);
            Path runnerSrc = runnerDir.resolve("Run.java");
            Files.writeString(runnerSrc, """
                public class Run {
                    public static void main(String[] args) throws Exception {
                        Class.forName(args[0]).getMethod("main", String[].class)
                            .invoke(null, (Object) new String[0]);
                    }
                }
                """);
            Process pCompile = new ProcessBuilder("javac", "-d", runnerDir.toString(), runnerSrc.toString()).start();
            assertEquals(0, pCompile.waitFor());
            Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                    "-cp", outDir.toString() + java.io.File.pathSeparator + runnerDir.toString(), "Run", "Default.Main")
                    .redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "JVM exit code, output: " + output);
            assertEquals(expected, output, "JVM output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("interrupted", e);
        }
    }

    private Path write(Path tmp, String src) throws IOException {
        Path f = tmp.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(f, src);
        return f;
    }

    // ---- (A) sombra do usuário vence o alias builtin ----

    @Test
    void userListShadowsBuiltinList(@TempDir Path tmp) throws IOException {
        runJvm(tmp, """
                class List { Int size }
                main() {
                    var lst = new List()
                    lst.size = 5
                    println(lst.size)
                }
                """, "5");
    }

    @Test
    void userStringShadowsBuiltinString(@TempDir Path tmp) throws IOException {
        runJvm(tmp, """
                class String { Int length }
                main() {
                    var s = new String()
                    s.length = 5
                    println(s.length)
                }
                """, "5");
    }

    @Test
    void userSetShadowsBuiltinSet(@TempDir Path tmp) throws IOException {
        runJvm(tmp, """
                class Set { Int n }
                main() {
                    var s = new Set()
                    s.n = 7
                    println(s.n)
                }
                """, "7");
    }

    @Test
    void userMapShadowsBuiltinMap(@TempDir Path tmp) throws IOException {
        runJvm(tmp, """
                class Map { Int n }
                main() {
                    var m = new Map()
                    m.n = 9
                    println(m.n)
                }
                """, "9");
    }

    @Test
    void userRecordShadowsBuiltinName(@TempDir Path tmp) throws IOException {
        // record declarado pelo usuário também vence (mesmo guard unitDeclaresType)
        runJvm(tmp, """
                record Map(Int n)
                main() {
                    var m = Map(11)
                    println(m.n())
                }
                """, "11");
    }

    // ---- (B) sem sombra: a coleção builtin continua valendo ----

    @Test
    void builtinListStillWorksWithoutShadow(@TempDir Path tmp) throws IOException {
        runJvm(tmp, """
                main() {
                    var l = new List<Int>()
                    l.add(3)
                    println(l.get(0))
                }
                """, "3");
    }

    @Test
    void builtinSetAndMapStillWorkWithoutShadow(@TempDir Path tmp) throws IOException {
        runJvm(tmp, """
                main() {
                    var s = new Set<Int>()
                    s.add(4)
                    println(s.size)
                    var m = new Map<String, Int>()
                    m.put("a", 1)
                    println(m.get("a"))
                }
                """, "1\n1");
    }
}
