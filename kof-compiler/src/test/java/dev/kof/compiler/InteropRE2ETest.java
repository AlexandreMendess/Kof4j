package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.PrintStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * X2 fatia 2 — motor R (`KofR`): SIMETRIA total com o {@link InteropPyE2ETest}
 * (mesmo protocolo de 2 linhas, mesmas faces, mesmos goldens). O motor so
 * roda onde `Rscript` + `jsonlite` existem: neste dev-host o probe falha e o
 * teste SALTA honesto (assumeTrue, nunca falso-verde); a prova real sai no
 * CI ubuntu (step de install do workflow, medido). Recusa INTEROP005 nos
 * alvos sem runtime de processo e nomeados 004/006 identicos ao py.
 */
class InteropRE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir
    Path tmp;

    private record Run(boolean ok, String output) {}

    private static boolean rEngineReady() {
        try {
            Process p = new ProcessBuilder("Rscript", "--vanilla", "-e",
                    "library(jsonlite)")
                    .redirectErrorStream(true).start();
            return p.waitFor(120, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static void requireR() {
        assumeTrue(rEngineReady(),
                "Rscript/jsonlite ausentes — gate de ambiente, nao regressao");
    }

    private Run runJvm(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.JVM);
        if (!r.success()) return new Run(false, diags(r));
        var oldOut = System.out;
        var buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true));
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

    private Run runNative(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        if (!r.success()) return new Run(false, diags(r));
        Path bin = out.resolve("Default/Main");
        assertTrue(Files.exists(bin), "binario nativo deve existir");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String o = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new Run(p.waitFor() == 0, o);
    }

    private Run runJs(Path src, Path out) throws Exception {
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
        for (Diagnostic d : r.diagnostics().getDiagnostics()) {
            sb.append(d.code()).append(": ").append(d.message()).append('\n');
        }
        return sb.toString();
    }

    private static final String HAPPY_R = """
            import kof.interop
            main() {
                var r = KofR("sq <- function(n) n*n\\nadd <- function(x) x+1\\nyes <- function() TRUE\\nhi <- function(n) paste0(\\"oi \\", n)")
                println(r.callInt("sq", listOf(5)))
                println(r.callDouble("add", listOf(1.5)))
                println(r.callBool("yes", listOf()))
                println(r.callString("hi", listOf("mel")))
            }
            """;

    @Test
    void rHostParsesAndTypesOnJvmWithoutR() throws Exception {
        // NAO pula: prova que o HOST interop-r-host.kf e Kof valido (parse +
        // tipo) mesmo em host sem R — um erro de sintaxe no resource cai aqui
        // em todo lugar, nao so no CI com R (Q3: o edge "ambiente ausente").
        Path src = tmp.resolve("parse.kf");
        Files.writeString(src, HAPPY_R);
        CompilationResult r = driver.compile(src, tmp.resolve("out-parse"), Target.JVM);
        assertTrue(r.success(), "host KofR deve compilar sem R instalado: " + diags(r));
    }

    @Test
    void typedCallsRoundTripOnJvm() throws Exception {
        requireR();
        Path src = tmp.resolve("happy.kf");
        Files.writeString(src, HAPPY_R);
        Run jvm = runJvm(src, tmp.resolve("out-jvm"));
        assertTrue(jvm.ok(), "JVM deve compilar e rodar: " + jvm.output());
        // MESMO golden do motor py (InteropPyE2ETest) — simetria de engines
        assertEquals("25\n2.5\ntrue\noi mel\n", jvm.output(),
                "round-trip tipado Int/Double/Bool/String no motor R");
    }

    @Test
    void x86AndJsMatchJvmOutput() throws Exception {
        requireR();
        Path src = tmp.resolve("happy2.kf");
        Files.writeString(src, HAPPY_R);
        Run jvm = runJvm(src, tmp.resolve("out-jvm"));
        assertTrue(jvm.ok(), "JVM base: " + jvm.output());
        Run nat = runNative(src, tmp.resolve("out-nat"));
        assertEquals(jvm.output(), nat.output(), "motor R JVM≡x86 broken");
        Run js = runJs(src, tmp.resolve("out-js"));
        assertEquals(jvm.output(), js.output(), "motor R JVM≡JS broken");
    }

    @Test
    void remoteErrorIsNamedInterop006() throws Exception {
        requireR();
        Path src = tmp.resolve("err.kf");
        Files.writeString(src, """
                import kof.interop
                main() {
                    var r = KofR("boom <- function() stop(\\"kaboom\\")")
                    try {
                        println(r.callInt("boom", listOf()))
                    } catch (String e) {
                        println(e)
                    }
                }
                """);
        Run jvm = runJvm(src, tmp.resolve("out-err"));
        assertTrue(jvm.ok(), "JVM: " + jvm.output());
        assertTrue(jvm.output().contains("INTEROP006"),
                "falha remota nomeada: " + jvm.output());
        assertTrue(jvm.output().contains("kaboom"),
                "mensagem remota na face: " + jvm.output());
    }

    @Test
    void interpreterDeathWithoutResponseIsNamedInterop004() throws Exception {
        requireR();
        Path src = tmp.resolve("dead.kf");
        Files.writeString(src, """
                import kof.interop
                main() {
                    var r = KofR("quit(save=\\"no\\", status=9)")
                    try {
                        println(r.callInt("sq", listOf(2)))
                    } catch (String e) {
                        println(e)
                    }
                }
                """);
        Run jvm = runJvm(src, tmp.resolve("out-dead"));
        assertTrue(jvm.ok(), "JVM: " + jvm.output());
        assertTrue(jvm.output().contains("INTEROP004"),
                "morte sem resposta = INTEROP004, nunca vazio: " + jvm.output());
    }

    @Test
    void recordRoundTripMatchesPyGolden() throws Exception {
        requireR();
        Path src = tmp.resolve("rec.kf");
        Files.writeString(src, """
                import kof.interop
                record Point(Int x, Int y)
                record Msg(String text)
                main() {
                    var r = KofR("norm <- function(p) list(x=p$x*2, y=p$y*2)\\nshout <- function(m) list(text=paste0(m$text, intToUtf8(10), \\"q\\", intToUtf8(34), \\"10\\", intToUtf8(34), \\"!\\"))\\nzero <- function() 0")
                    println(json.encode(listOf(Point(3, 4), Point(5, 6))))
                    var q = json.decode<Point>(r.callJson("norm", json.encode(listOf(Point(3, 4)))))
                    println(q.x())
                    println(q.y())
                    var m = json.decode<Msg>(r.callJson("shout", json.encode(listOf(Msg("he-\\"llo\\"")))))
                    println(m.text)
                    println(json.decode<Int>(r.callJson("zero", "[]")))
                    println(json.encode(Msg("rt-OK")))
                }
                """);
        Run jvm = runJvm(src, tmp.resolve("out-rec"));
        assertTrue(jvm.ok(), "JVM: " + jvm.output());
        // golden IDENTICO ao do motor py (InteropPyRecordE2ETest.ORACLE):
        // engine e detalhe; o wire e o contrato — simetria provada.
        assertEquals("[{\"x\":3,\"y\":4},{\"x\":5,\"y\":6}]\n6\n8\nhe-\"llo\"\nq\"10\"!\n0\n{\"text\":\"rt-OK\"}\n",
                jvm.output(), "motor R round-trip == golden py");
        Run nat = runNative(src, tmp.resolve("out-recnat"));
        assertEquals(jvm.output(), nat.output(), "motor R record JVM≡x86");
    }

    @Test
    void refusalTargetStillSeesTheKofRShape() throws Exception {
        Path src = tmp.resolve("refusal.kf");
        Files.writeString(src, """
                import kof.interop
                main() {
                    var r = KofR("sq <- function(n) n*n")
                    println(r.callInt("sq", listOf(2)))
                }
                """);
        CompilationResult r = driver.compile(src, tmp.resolve("out-ref"), Target.ANDROID);
        String msgs = diags(r);
        // o unico erro permitido e o gate de emissao/alvo — nunca metodo/
        // simbolo desconhecido: o shape KofR existe no alvo de recusa.
        assertTrue(msgs.isEmpty() || msgs.contains("COMP003") || msgs.contains("AND004"),
                "shape KofR deve sobreviver a parse/tipo no alvo de recusa: " + msgs);
    }
}
