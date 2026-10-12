package dev.kof.compiler;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * known-bugs §659d — the JS backend printed a whole {@code Double} without its
 * {@code .0} in JSON: a Kof {@code Double}/{@code Float} is a JS {@code number}
 * and the encoder deferred to {@code JSON.stringify}, which prints {@code 1}
 * (and {@code 1e+21}) where the other three targets print the JDK form
 * {@code 1.0} (and {@code 1.0E21}). The divergence was wider than the ledger
 * recorded: the scientific form, {@code -0.0} and {@code List<Double>} all
 * diverged too. The JS encoder now routes the Double/Float faces through the
 * same JDK formatter the display path already uses ({@code kofNumFmt}); a
 * record's Double/Float component is marked in {@code toJSON} and unmarked
 * after stringify. The oracle is the JVM, measured.
 */
class JsonEncodeDoubleJsE2ETest extends ShellSupport {

    private static final String SRC = """
            record R(Double d)
            record M(Double d, Int n, String s)
            main() {
                println(json.encode(1.0))
                println(json.encode(0.0))
                println(json.encode(2.5))
                println(json.encode(-3.0))
                println(json.encode(-0.0))
                println(json.encode(1.0E10))
                println(json.encode(1.0E21))
                println(json.encode(1.0E-7))
                println(json.encode(0.0 / 0.0))
                println(json.encode(1.0 / 0.0))
                println(json.encode(-1.0 / 0.0))
                println(json.encode(R(1.0)))
                println(json.encode(R(2.5)))
                println(json.encode(M(1.0, 7, "x")))
                println(json.encode(listOf(1.0, 2.5)))
                println(json.encode(listOf(1.0, 2.0)))
            }
            """;

    /** The JVM contract — every value measured, frozen. */
    private static final String ORACLE = String.join("\n",
            "1.0", "0.0", "2.5", "-3.0", "-0.0", "1.0E10", "1.0E21", "1.0E-7",
            "null", "null", "null",
            "{\"d\":1.0}", "{\"d\":2.5}", "{\"d\":1.0,\"n\":7,\"s\":\"x\"}",
            "[1.0,2.5]", "[1.0,2.0]");

    @Test
    void jvmIsTheContract() throws Exception {
        Files.writeString(tmp.resolve("D.kf"), SRC);
        Run jvm = runJvm(tmp.resolve("D.kf"), tmp.resolve("o-jvm"));
        assertTrue(jvm.ok(), () -> "JVM falhou: " + jvm.output());
        assertEquals(ORACLE, jvm.output().trim().replace("\r\n", "\n"), "oráculo JVM (§659d)");
    }

    @Test
    void jsMatchesTheJvmOnEveryDoubleFace() throws Exception {
        Files.writeString(tmp.resolve("D.kf"), SRC);
        Run jvm = runJvm(tmp.resolve("D.kf"), tmp.resolve("o-jvm"));
        assertTrue(jvm.ok(), () -> "JVM falhou: " + jvm.output());
        String oracle = jvm.output().trim().replace("\r\n", "\n");
        assertEquals(ORACLE, oracle, "o oráculo medido no JVM mudou");

        Run js = runJs(tmp.resolve("D.kf"), tmp.resolve("o-js"));
        assertTrue(js.ok(), () -> "JS falhou: " + js.output());
        assertEquals(oracle, js.output().trim().replace("\r\n", "\n"),
                "JS≡JVM no encode de Double (§659d)");
    }
}
