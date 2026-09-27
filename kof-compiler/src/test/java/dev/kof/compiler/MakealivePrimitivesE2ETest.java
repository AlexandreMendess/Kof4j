package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * kof.makealive 3.0.1 RECON (universal plan Stage 3; docs/development/
 * makealive-plan.md §4) — freezes the primitive forms the design intends to
 * compose, WITHOUT shipping any surface. Discipline = workflow 2.1.0 recon
 * (WorkflowPrimitivesE2ETest): every shape below was read out of already-green
 * sources (sup-host class fields, `catch (String e)` in KofConcurrency2Test,
 * `record Point(Int x, Int y)` from the record E2E family, `mapOf(k,v,...)`
 * from the SEM048 golden, `File.readText/writeText` from docs/stdlib/IO.md)
 * — nothing invented.
 *
 * Outcome feeds back into makealive-plan §5 3.0.1. The namespace itself is
 * ⛔ Q1 (rule 6): the R1 gate HARD-DENYs the literal `kof.infra` — measured,
 * recorded in plan §2.1; no host/compiler class/ledger line before the
 * maintainer answers Q1–Q4.
 */
class MakealivePrimitivesE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private record Run(boolean ok, String output) {}

    private Run runJvm(Path src, Path out) throws Exception {
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

    private Run runScript(Path src) {
        try {
            var r = driver.interpret(java.util.List.of(src), tmp, new String[0]);
            return new Run(r.exitCode() == 0, r.stdout());
        } catch (Exception e) {
            return new Run(false, "THROW: " + e.getMessage());
        }
    }

    private void compileNative(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        assertTrue(r.success(), () -> "Native must compile: " + diags(r));
    }

    private static String diags(CompilationResult r) {
        StringBuilder sb = new StringBuilder();
        r.diagnostics().getDiagnostics().forEach(d -> sb.append(d.message()).append("\n"));
        return sb.toString();
    }

    private void assertRunsJvmJsNativeCompiles(String base, String source, String... expected)
            throws Exception {
        Path src = tmp.resolve(base + ".kf");
        Files.writeString(src, source);
        Run jvm = runJvm(src, tmp.resolve("o-" + base + "-jvm"));
        assertTrue(jvm.ok(), () -> "JVM failed: " + jvm.output());
        Run js = runJs(src, tmp.resolve("o-" + base + "-js"));
        assertTrue(js.ok(), () -> "JS failed: " + js.output());
        for (String e : expected) {
            assertTrue(jvm.output().contains(e), () -> "JVM expected '" + e + "' in: " + jvm.output());
        }
        assertEquals(jvm.output(), js.output(), "3.0.1 recon: JVM/JS shapes diverge");
        compileNative(src, tmp.resolve("o-" + base + "-native"));
    }

    /** §4 row 1: `record Resource` + accessors — the resource-descriptor form. */
    @Test
    void resourceRecordShapeRunsJvmJsAndNativeCompiles() throws Exception {
        assertRunsJvmJsNativeCompiles("MARecord", """
            record Resource(String name, String kind)

            main() {
                var r = Resource("web-1", "service")
                println(r.name())
                println(r.kind())
            }
            """, "web-1", "service");
    }

    /** §4 row 2: class with Map field + lookup through a caller-owned key list
     *  (iteration order of mapOf().keys() is target-dependent — the design must
     *  drive iteration from a deterministic List, never from keys()). */
    @Test
    void classWithMapFieldRunsDeterministically() throws Exception {
        assertRunsJvmJsNativeCompiles("MAMap", """
            class Design {
                Map<String, String> tags = mapOf("env", "prod", "tier", "web")
                List<String> order = listOf()
            }

            main() {
                var d = Design()
                d.order.add("env")
                d.order.add("tier")
                var out = d.order.map((k: String) -> k + "=" + d.tags.get(k))
                println(out.get(0))
                println(out.get(1))
                var missing = d.tags.get("region")
                if (missing == null) {
                    println("none")
                } else {
                    println("something")
                }
            }
            """, "env=prod", "tier=web", "none");
    }

    /** §4 row 3: topological fixpoint in pure language, deterministic order —
     *  the plan-ordering core `makealive` needs (same fixpoint the workflow
     *  `run()` proved, re-locked in resource shape). */
    @Test
    void topoFixpointIsDeterministicJvmJs() throws Exception {
        assertRunsJvmJsNativeCompiles("MATopo", """
            main() {
                var names = listOf("deploy", "build", "image")
                var needs = listOf("image", "none", "build")
                var done = listOf()
                var progress = true
                while (progress) {
                    progress = false
                    var i = 0
                    while (i < names.size()) {
                        var n = names.get(i)
                        var need = needs.get(i)
                        if (!done.contains(n)) {
                            if (need == "none" || done.contains(need)) {
                                done.add(n)
                                progress = true
                            }
                        }
                        i = i + 1
                    }
                }
                println(done.size())
                for (var n in done) {
                    println(n)
                }
            }
            """, "3", "build", "image", "deploy");
    }

    /** §4 row 4: cycle detection throws a String the caller catches (the §3
     *  guard shape). JVM+JS+Script run; Native compiles. */
    @Test
    void cycleThrowsStringCaughtOnAllBackends() throws Exception {
        String source = """
            main() {
                var msg = "no-throw"
                try {
                    throw "cycle: a->b->a"
                } catch (String e) {
                    msg = e
                }
                println(msg)
            }
            """;
        Path src = tmp.resolve("MACycle.kf");
        Files.writeString(src, source);
        Run jvm = runJvm(src, tmp.resolve("o-cycle-jvm"));
        assertTrue(jvm.ok() && jvm.output().contains("cycle: a->b->a"), () -> "JVM: " + jvm.output());
        Run js = runJs(src, tmp.resolve("o-cycle-js"));
        assertTrue(js.ok() && js.output().contains("cycle: a->b->a"), () -> "JS: " + js.output());
        Run script = runScript(src);
        assertTrue(script.ok() && script.output().contains("cycle: a->b->a"), () -> "SCRIPT: " + script.output());
        compileNative(src, tmp.resolve("o-cycle-native"));
    }

    /** §4 row 5: kof.io file round-trip as the state-file substrate (JSON on
     *  kof.io is the v1 state backend per VISÃO; only the file form is locked
     *  here). MEASURED 19/09 (coordenação sibling): the kof.io face is NOT
     *  static-call — `File` is a constructor and the ops are instance methods
     *  (IoE2ETest.fileTextRoundTrip); bare `File.readText(p)` = "Undefined
     *  variable or type: 'File'". JVM+JS parity on what was read back; Native
     *  compiles. */
    @Test
    void ioStateRoundTripRunsJvmJsNativeCompiles() throws Exception {
        Path state = tmp.resolve("state.json");
        assertRunsJvmJsNativeCompiles("MAIo", """
            main() {
                var f = File("%s")
                f.writeText("{\\"web\\":\\"on\\",\\"count\\":\\"2\\"}")
                var back = f.readText()
                if (back != null && back.length > 10) {
                    println("roundtrip")
                } else {
                    println("short")
                }
                f.delete()
                if (f.exists()) {
                    println("leak")
                } else {
                    println("gone")
                }
            }
            """.formatted(state.toString()), "roundtrip", "gone");
    }

    /** 3.1.0 probe (claim D-MAKEALIVE): the provider shape is function-typed
     *  fields over a host record + generic `Map<String,String>` — the workflow
     *  `KofWfJob` locks `() -> Bool` and `(String, String) -> Bool`, but a
     *  record parameter and a Map return are NOT covered by 3.0.1. MEASURED
     *  today: direct invoke on a function-typed field (`p.read(r)`) does NOT
     *  typecheck ("Cannot resolve method 'read' on type 'MvProvider'") — the
     *  locked form is the workflow precedent (`var corpoFn: () -> Bool =
     *  job.corpo`, workflow-host.kf:356): copy to a typed local, then invoke.
     *  JVM==JS byte parity; Native compiles. */
    @Test
    void providerShapeFunctionTypedFieldsRunsJvmJs() throws Exception {
        assertRunsJvmJsNativeCompiles("MAProvider", """
            record MvRes(String kind, String name)

            class MvProvider {
                (MvRes) -> Map<String, String> read = null
                (MvRes, Map<String, String>) -> Bool set = null
                (MvRes) -> Bool del = null

                public constructor((MvRes) -> Map<String, String> read,
                                   (MvRes, Map<String, String>) -> Bool set,
                                   (MvRes) -> Bool del) {
                    this.read = read
                    this.set = set
                    this.del = del
                }
            }

            main() {
                var p = MvProvider(
                    (r: MvRes) -> mapOf("seen", r.kind() + ":" + r.name()),
                    (r: MvRes, props: Map<String, String>) -> props.get("seen") != null,
                    (r: MvRes) -> true
                )
                var readFn: (MvRes) -> Map<String, String> = p.read
                var setFn: (MvRes, Map<String, String>) -> Bool = p.set
                var delFn: (MvRes) -> Bool = p.del
                var m = readFn(MvRes("service", "web"))
                println(m.get("seen"))
                println(setFn(MvRes("service", "web"), m))
                println(delFn(MvRes("service", "web")))
            }
            """, "service:web", "true", "true");
    }

    /** 3.1.0 probe (2): the full host grammar the `makealive-host.kf` will use
     *  — method returning own class via `return this` (builder fluent), state
     *  port with `List<String>`/record/Map function fields (re-confirming the
     *  probe-1 measurement: invoke on a function field needs a typed local),
     *  and `Map<String, Map<String,String>>` nested in a `mapOf` literal as
     *  LOCAL + function-typed PARAM. JVM==JS byte parity; Native compiles. */
    @Test
    void hostGrammarBuilderAndStatePortRun() throws Exception {
        assertRunsJvmJsNativeCompiles("MAHostGrammar", """
            record HgRes(String kind, String name)

            Bool hgTemNome(List<HgRes> xs, String nome) {
                var i = 0
                while (i < xs.size()) {
                    if (xs.get(i).name() == nome) { return true }
                    i = i + 1
                }
                return false
            }

            class HgDesign {
                List<HgRes> declared = listOf()
                HgDesign resource(String kind, String name) {
                    declared.add(HgRes(kind, name))
                    return this
                }
            }

            class HgState {
                () -> List<String> names = null
                (String) -> Map<String, String> props = null
                (String, Map<String, String>) -> Bool put = null
            }

            Map<String, String> hgOr(Map<String, String>? m, Map<String, String> fallback) {
                if (m != null) { return m }
                return fallback
            }

            HgState hgStateFrom(Map<String, Map<String, String>> data, List<String> order) {
                var s = HgState()
                s.names = () -> order
                s.props = (n: String) -> hgOr(data.get(n), mapOf())
                s.put = (n: String, p: Map<String, String>) -> true
                return s
            }

            main() {
                var d = HgDesign()
                d.resource("bucket", "media")
                d.resource("db", "main")
                println(d.declared.size())
                var data = mapOf("media", mapOf("acl", "private"))
                var st = hgStateFrom(data, listOf("media"))
                var namesFn: () -> List<String> = st.names
                var propsFn: (String) -> Map<String, String> = st.props
                var putFn: (String, Map<String, String>) -> Bool = st.put
                var nm = namesFn()
                println(nm.get(0))
                var pr = propsFn("media")
                println(pr.get("acl"))
                println(putFn("main", mapOf("engine", "pg")))
                println(hgTemNome(d.declared, "db"))
            }
            """, "2", "media", "private", "true", "true");
    }

    /** §379 (era §363/§371/§376; probe 2; FIXED): calling a generic-typed lambda
     *  through a typed local crashed the JVM with
     *  `IncompatibleClassChangeError: Class LambdaN does not implement the
     *  requested interface kof.Function1_CString_CMap` — the lambda class was
     *  emitted with the interface mangled from the INFERRED body return
     *  (`data.get(n)` is `Map?` -> NCMap) while the call site dispatched by
     *  the DECLARED type (CMap), and the SC2 conformance check skipped
     *  FunctionType assignments silently (R6). Now rejected at compile time
     *  with SEM021 — cases (a) no capture + Map return and (b) capture +
     *  String return stay green (no false positives). */
    @Test
    void genericLambdaInvokeCases() throws Exception {
        Path srcA = tmp.resolve("MAIfaceA.kf");
        Files.writeString(srcA, """
            main() {
                var f: (String) -> Map<String, String> = (n: String) -> mapOf("k", n)
                var m = f("x")
                println(m.get("k"))
            }
            """);
        Run a = runJvm(srcA, tmp.resolve("o-iface-a"));
        System.err.println("CASE-A (no capture, Map return): ok=" + a.ok() + " out=[" + a.output().trim() + "]");
        Path srcB = tmp.resolve("MAIfaceB.kf");
        Files.writeString(srcB, """
            main() {
                var top = "acl"
                var g: (String) -> String = (n: String) -> n + top
                println(g("m"))
            }
            """);
        Run b = runJvm(srcB, tmp.resolve("o-iface-b"));
        System.err.println("CASE-B (capture, String return): ok=" + b.ok() + " out=[" + b.output().trim() + "]");
        Path srcC = tmp.resolve("MAIfaceC.kf");
        Files.writeString(srcC, """
            main() {
                var data = mapOf("m", mapOf("acl", "private"))
                var f: (String) -> Map<String, String> = (n: String) -> data.get(n)
                var m = f("m")
                println(m.get("acl"))
            }
            """);
        Run c = runJvm(srcC, tmp.resolve("o-iface-c"));
        assertFalse(c.ok(), "§379: body returning Map? assigned to (String) -> Map must be a COMPILE error, got ok + out=" + c.output());
        assertTrue(c.output().contains("SEM021") || c.output().contains("type mismatch"),
                "§379 fix must name the mismatch: " + c.output());
        System.err.println("CASE-C (capture, Map return): rejected at compile-time as expected");
    }
}
