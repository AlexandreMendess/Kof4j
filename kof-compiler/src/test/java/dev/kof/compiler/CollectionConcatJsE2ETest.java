package dev.kof.compiler;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * known-bugs §685 — String CONCATENATION of a collection on the JS target used
 * the host's {@code Array.prototype.toString}/{@code [object Set]}/{@code
 * [object Map]} instead of the Kof container form. The JS lowering boxes the
 * operand to {@code Object} (the JVM {@code valueOf(Object)} signature) and the
 * {@code valueOf} branch fell to a bare {@code String(x)}: for a List that is
 * {@code 1,2,3} (no brackets), for a Set {@code [object Set]}, for a Map
 * {@code [object Map]}, for a nested list {@code 1,2}. JVM/Script/native print
 * the container form {@code [1, 2, 3]}/{@code {k=1}}. The fix routes the
 * erased-{@code Object} case through {@code kofFormat} — the Kof container
 * formatter, identical to {@code String(x)} for scalars/String/records. The
 * oracle is the JVM, measured. (The DIRECT {@code String.valueOf(list)} call on
 * the native target is a SEPARATE, still-open face — see §686 — so it is not
 * pinned here; the concat faces are 4-target clean.)
 */
class CollectionConcatJsE2ETest extends ShellSupport {

    private static final String SRC = """
            main() {
                var l = listOf(1, 2, 3)
                var s = setOf("a", "b")
                var m = mapOf("k", 1)
                println("L=" + l)
                println("S=" + s)
                println("M=" + m)
                println("nl=" + listOf(listOf(1), listOf(2)))
                var e: List<Int> = listOf()
                println("E=" + e)
                println("" + l)
                println(String.valueOf(l))
            }
            """;

    private static final String ORACLE = String.join("\n",
            "L=[1, 2, 3]", "S=[a, b]", "M={k=1}", "nl=[[1], [2]]", "E=[]",
            "[1, 2, 3]", "[1, 2, 3]");

    @Test
    void jvmIsTheContractForCollectionConcat() throws Exception {
        Files.writeString(tmp.resolve("C.kf"), SRC);
        Run jvm = runJvm(tmp.resolve("C.kf"), tmp.resolve("o-jvm"));
        assertTrue(jvm.ok(), () -> "JVM falhou: " + jvm.output());
        assertEquals(ORACLE, jvm.output().trim().replace("\r\n", "\n"), "oráculo JVM (§685)");
    }

    @Test
    void jsMatchesTheJvmOnCollectionConcat() throws Exception {
        Files.writeString(tmp.resolve("C.kf"), SRC);
        Run jvm = runJvm(tmp.resolve("C.kf"), tmp.resolve("o-jvm"));
        assertTrue(jvm.ok(), () -> "JVM falhou: " + jvm.output());
        String oracle = jvm.output().trim().replace("\r\n", "\n");
        assertEquals(ORACLE, oracle, "o oráculo medido no JVM mudou");

        Run js = runJs(tmp.resolve("C.kf"), tmp.resolve("o-js"));
        assertTrue(js.ok(), () -> "JS falhou: " + js.output());
        assertEquals(oracle, js.output().trim().replace("\r\n", "\n"),
                "JS≡JVM no concat de coleção (§685)");
    }
}
