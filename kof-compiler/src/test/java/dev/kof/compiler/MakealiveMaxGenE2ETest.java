package dev.kof.compiler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 3.8 (fatia preparatória): face `mkMaxGen` do host makealive — o CLI
 * `kof makealive apply/destroy` precisa gravar SEMPRE geração maior que a
 * atual (update = nova geração; entidade imutável, SEM038), e o máximo só
 * vivia dentro de `mkLoadState`. JVM==JS byte-parity (mesmo h2 em memória da
 * MESMA JVM — precedente MakealiveDbHostE2ETest) + Native compila com o stub
 * ORM001.
 */
class MakealiveMaxGenE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private record Run(boolean ok, String output) {}

    private static final String PROGRAM = """
        import kof.makealive

        String stateDump(State s) {
            var d = ""
            var i = 0
            while (i < s.entries.size()) {
                var e = s.entries.get(i)
                d = d + e.name + ":"
                var j = 0
                while (j < e.propFlat.size()) {
                    d = d + e.propFlat.get(j) + "/"
                    j = j + 1
                }
                d = d + ";"
                i = i + 1
            }
            return d
        }

        main() {
            var conn = "jdbc:h2:mem:__T__;DB_CLOSE_DELAY=-1"
            println("vazio:" + mkMaxGen(conn, "prod"))
            var st = State()
            st.entries.add(StateEntry("a1", listOf()))
            println("s1:" + mkSaveState(conn, "prod", mkMaxGen(conn, "prod") + 1, st))
            println("g1:" + mkMaxGen(conn, "prod"))
            st.entries.add(StateEntry("a2", listOf("size", "3")))
            println("s2:" + mkSaveState(conn, "prod", mkMaxGen(conn, "prod") + 1, st))
            println("g2:" + mkMaxGen(conn, "prod"))
            println("load:" + stateDump(mkLoadState(conn, "prod")))
            println("outro:" + mkMaxGen(conn, "nope"))
        }
        """;

    private Run runJvm(String token) throws Exception {
        return runJvm(token, PROGRAM);
    }

    private Run runJvm(String token, String program) throws Exception {
        Path src = tmp.resolve("Jvm.kf");
        Path out = tmp.resolve("out-jvm");
        Files.writeString(src, program.replace("__T__", token));
        CompilationResult r = driver.compile(src, out, Target.JVM);
        if (!r.success()) return new Run(false, r.diagnostics().getDiagnostics().toString());
        var oldOut = System.out;
        var buf = new ByteArrayOutputStream();
        System.setOut(new java.io.PrintStream(buf, true));
        try {
            var cl = new URLClassLoader(new java.net.URL[]{out.toUri().toURL()},
                    getClass().getClassLoader());
            Class.forName("Default.Main", true, cl)
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            return new Run(true, buf.toString());
        } catch (java.lang.reflect.InvocationTargetException e) {
            return new Run(false, "THROW: " + e.getCause());
        } finally {
            System.setOut(oldOut);
        }
    }

    private Run runJs(String token) throws Exception {
        return runJs(token, PROGRAM);
    }

    private Run runJs(String token, String program) throws Exception {
        Path src = tmp.resolve("Js.kf");
        Path out = tmp.resolve("out-js");
        Files.writeString(src, program.replace("__T__", token));
        CompilationResult r = driver.compile(src, out, Target.JS);
        if (!r.success()) return new Run(false, r.diagnostics().getDiagnostics().toString());
        var buf = new ByteArrayOutputStream();
        try {
            int rc = dev.kof.runtime.KofJsRunner.run(
                    out.resolve("Default.mjs"), buf,
                    new ByteArrayInputStream(new byte[0]), buf);
            return new Run(rc == 0, buf.toString());
        } catch (Exception e) {
            return new Run(false, "THROW: " + e.getMessage() + "\n" + buf);
        }
    }

    @Test
    void maxGenTracksSavedGenerationsAndIsByteEqualJvmJs() throws Exception {
        // tokens DISTINTOS: DB_CLOSE_DELAY=-1 mantem o h2 mem na JVM do teste —
        // com o mesmo nome o segundo motor herdaria as geraes do primeiro.
        String token = "MkMaxGen" + System.nanoTime();
        Run jvm = runJvm(token);
        assertTrue(jvm.ok(), jvm.output());
        Run js = runJs(token + "Js");
        assertTrue(js.ok(), js.output());
        assertEquals(jvm.output(), js.output(), "paridade JVM==JS");
        String expected = """
                vazio:-1
                s1:true
                g1:0
                s2:true
                g2:1
                load:a1:;a2:size/3/;
                outro:-1
                """;
        assertEquals(expected, jvm.output());
    }

    @Test
    void maxGenNamesItsMissingArgument() throws Exception {
        Path src = tmp.resolve("Guard.kf");
        Path out = tmp.resolve("out-guard");
        Files.writeString(src, """
            import kof.makealive
            main() {
                println(mkMaxGen("jdbc:h2:mem:MkMaxGenGuard", ""))
            }
            """);
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), r.diagnostics().getDiagnostics().toString());
        var oldOut = System.out;
        var errBuf = new ByteArrayOutputStream();
        var oldErr = System.err;
        System.setOut(new java.io.PrintStream(errBuf, true));
        System.setErr(new java.io.PrintStream(errBuf, true));
        String thrown;
        try {
            var cl = new URLClassLoader(new java.net.URL[]{out.toUri().toURL()},
                    getClass().getClassLoader());
            Class.forName("Default.Main", true, cl)
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            thrown = "";
        } catch (java.lang.reflect.InvocationTargetException e) {
            thrown = String.valueOf(e.getCause());
        } finally {
            System.setOut(oldOut);
            System.setErr(oldErr);
        }
        assertTrue(thrown.contains("mkMaxGen sem nome de design"), "guarda deve nomear o argumento: " + thrown);
    }

    @Test
    void nativeStubsCompileWithOrm001Gate() throws Exception {
        Path src = tmp.resolve("Nat.kf");
        Path out = tmp.resolve("out-native");
        Files.writeString(src, PROGRAM.replace("__T__", "Nat" + System.nanoTime()));
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        assertTrue(r.success(), "Native deve compilar com o stub ORM001: "
                + r.diagnostics().getDiagnostics());
    }

    @Test
    void emptyGenerationIsVisibleAndLoadsEmpty() throws Exception {
        String token = "MkMaxGenEmpty" + System.nanoTime();
        String program = """
            import kof.makealive

            main() {
                var conn = "jdbc:h2:mem:__T__;DB_CLOSE_DELAY=-1"
                var st = State()
                st.entries.add(StateEntry("a1", listOf()))
                println("g0:" + mkMaxGen(conn, "d1"))
                println("s:" + mkSaveState(conn, "d1", 0, st))
                var e = State()
                println("se:" + mkSaveState(conn, "d1", 1, e))
                println("g1:" + mkMaxGen(conn, "d1"))
                println("l:" + mkLoadState(conn, "d1").entries.size())
                println("g0b:" + mkMaxGen(conn, "d0"))
            }
            """;
        Run jvm = runJvm(token + "Jv", program);
        assertTrue(jvm.ok(), jvm.output());
        Run js = runJs(token + "Js", program);
        assertTrue(js.ok(), js.output());
        assertEquals(jvm.output(), js.output(), "paridade JVM==JS");
        assertEquals("""
            g0:-1
            s:true
            se:true
            g1:1
            l:0
            g0b:-1
            """, jvm.output());
    }
}
