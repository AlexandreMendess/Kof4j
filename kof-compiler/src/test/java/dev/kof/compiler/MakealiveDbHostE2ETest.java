package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fatia MK-1 do tracker 3.1 (D-MAKEALIVE Q3 "estado sobre kof.db desde o dia
 * 1"): as faces {@code mkSaveState}/{@code mkLoadState} do host makealive,
 * escritas EM KOF na fatia injetada por alvo (makealive-db-host.kf em
 * não-Native; stub ORM001 no Native — espelho exato do checkpoint do workflow
 * 2.1.3). O programa grava o State de um apply real, lê de volta por
 * reconexão, prova max-gen (update = NOVA geração; entidade imutável, SEM038),
 * res sem props (linha MARCA idx -1), design inexistente = State vazio, e a
 * guarda que nomeia o argumento ausente (R6). JVM==JS byte-parity (o delegate
 * JS roda na MESMA JVM — sondado em MakealiveDbStateE2ETest); Native compila
 * com o stub e o programa inteiro passa no gate ORM001 estático.
 */
class MakealiveDbHostE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private record Run(boolean ok, String output) {}

    private Run runJvm(Path src, Path out, String token, String program) throws Exception {
        Files.writeString(src, program.replace("__T__", token));
        CompilationResult r = driver.compile(src, out, Target.JVM);
        if (!r.success()) return new Run(false, diags(r));
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

    private Run runJs(Path src, Path out, String token, String program) throws Exception {
        Files.writeString(src, program.replace("__T__", token));
        CompilationResult r = driver.compile(src, out, Target.JS);
        if (!r.success()) return new Run(false, diags(r));
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

    private static String diags(CompilationResult r) {
        StringBuilder sb = new StringBuilder();
        r.diagnostics().getDiagnostics().forEach(d -> sb.append(d.message()).append("\n"));
        return sb.toString();
    }

    private static final String PROGRAM = """
        import kof.makealive

        class World {
            List<String> rn = listOf()
            List<String> rk = listOf()
            List<String> rv = listOf()
        }

        Map<String, String> mkWorldRead(World w, Resource r) {
            var m = mapOf()
            var i = 0
            while (i < w.rn.size) {
                if (w.rn.get(i) == r.name()) { m.put(w.rk.get(i), w.rv.get(i)) }
                i = i + 1
            }
            return m
        }

        Bool mkWorldSet(World w, Resource r, Map<String, String> props) {
            if (r.name() == "a1") {
                w.rn.add("a1")
                w.rk.add("size")
                w.rv.add(props.get("size"))
            }
            return true
        }

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
            var w = World()
            var p = Provider(
                (r: Resource) -> mkWorldRead(w, r),
                (r: Resource, props: Map<String, String>) -> mkWorldSet(w, r, props),
                (r: Resource) -> true
            )
            var d = Infrastructure("prod")
            d.resource("app", "a1")
            d.resource("app", "a2")
            d.prop("a1", "size", "3")
            var rep = apply(d, State(), p)
            println("saved-world:" + w.rn.size())
            var conn = "jdbc:h2:mem:__T__;DB_CLOSE_DELAY=-1"
            println("save1:" + mkSaveState(conn, "prod", 1, rep.state))
            println("load1:" + stateDump(mkLoadState(conn, "prod")))
            println("save2:" + mkSaveState(conn, "prod", 7, rep.state))
            println("load2:" + stateDump(mkLoadState(conn, "prod")))
            println("vazio:[" + stateDump(mkLoadState(conn, "nope")) + "]")
        }
        """;

    @Test
    void dbStateRoundtripMaxGenAndMarkerRowParityJvmJs() throws Exception {
        Path src = tmp.resolve("MkDbHost.kf");
        Files.writeString(src, PROGRAM);
        Run jvm = runJvm(src, tmp.resolve("o-jvm"), "RT-JVM", PROGRAM);
        assertTrue(jvm.ok(), () -> "JVM failed: " + jvm.output());
        Run js = runJs(src, tmp.resolve("o-js"), "RT-JS", PROGRAM);
        assertTrue(js.ok(), () -> "JS failed: " + js.output());
        for (String e : new String[] {
                "saved-world:1",
                "save1:true",
                "load1:a1:size/3/;a2:;",
                "save2:true",
                "load2:a1:size/3/;a2:;",
                "vazio:[]",
        }) {
            assertTrue(jvm.output().contains(e), () -> "JVM expected '" + e + "' in: " + jvm.output());
        }
        assertEquals(jvm.output(), js.output(), "3.1 db-face golden: JVM/JS diverge");
        Files.writeString(src, PROGRAM);
        var native_ = driver.compile(src, tmp.resolve("o-native"), Target.NATIVE);
        assertTrue(native_.success(), () -> "Native must compile com o stub ORM001: " + diags(native_));
    }

    private static final String GUARD_PROGRAM = """
        import kof.makealive

        main() {
            var conn = "jdbc:h2:mem:__T__;DB_CLOSE_DELAY=-1"
            println(mkSaveState(conn, "", 1, State()))
        }
        """;

    @Test
    void saveStateGuardNamesMissingDesignOnBothEngines() throws Exception {
        Path src = tmp.resolve("MkDbGuard.kf");
        Files.writeString(src, GUARD_PROGRAM);
        Run jvm = runJvm(src, tmp.resolve("o-jvm"), "GR-JVM", GUARD_PROGRAM);
        assertTrue(!jvm.ok(), () -> "JVM deve falhar ALTO na guarda (R6): " + jvm.output());
        assertTrue(jvm.output().contains("sem nome de design"),
                () -> "guarda deve nomear o argumento: " + jvm.output());
        Run js = runJs(src, tmp.resolve("o-js"), "GR-JS", GUARD_PROGRAM);
        assertTrue(!js.ok(), () -> "JS deve falhar ALTO na guarda (R6): " + js.output());
        assertTrue(js.output().contains("sem nome de design"),
                () -> "JS deve mesma mensagem da guarda: " + js.output());
    }

    @Test
    void nativeTargetInjectsStubNotOrmSlice() throws Exception {
        Path src = tmp.resolve("MkDbNative.kf");
        Files.writeString(src, GUARD_PROGRAM);
        var r = driver.compile(src, tmp.resolve("o-native"), Target.NATIVE);
        assertTrue(r.success(), () -> "Native compila com stub (sem orm no host injetado): " + diags(r));
        // o throw ORM001 so existe em RUNTIME (gate de compilacao e das faces
        // puras do stub); rodar cross-toolchain aqui e do MakealiveE2ETest.
    }
}
