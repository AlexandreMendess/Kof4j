package dev.kof.compiler;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §659d (face escalar) — {@code json.encode(1.0)} no JS imprimia {@code 1}
 * enquanto JVM/Script/nativo imprimem {@code 1.0}: o backend JS delegava o
 * número ao {@code JSON.stringify}, que solta o {@code .0} de um Double
 * inteiro. Quando o tipo estático do argumento é Double/Float, o lowering
 * roteia para {@code kofJsonEncodeDouble}, que repõe o {@code .0} do inteiro;
 * não-inteiro e as faces Int/Long/Bool seguem byte-idênticos. A face RECORD:
 * o {@code toJSON} do record emite a sentinela {@code KOFDBL_SENTINEL} para o
 * campo Double/Float inteiro e o {@code kofJsonEncode} a devolve com o {@code .0}
 * (a mecânica da sentinela BigInt do §659b). Prova JVM≡JS; oráculo = JVM medido.
 */
class JsonEncodeDoubleJsE2ETest extends ShellSupport {

    private static final String SRC = """
            record Real(Double d, Int n, Long v)
            record Whole(Double w)
            main() {
                println(json.encode(1.0))
                println(json.encode(0.0))
                println(json.encode(-7.0))
                println(json.encode(2.5))
                println(json.encode(42))
                println(json.encode(9000000000L))
                println(json.encode(true))
                println(json.encode(mapOf("x", 1.5)))
                println(json.encode(Real(1.0, 42, 9000000000)))
                println(json.encode(Real(2.5, 7, 3)))
                println(json.encode(Whole(1.0)))
                println(json.encode(listOf(Real(1.0, 1, 1), Real(0.0, 2, 2))))
            }
            """;

    @Test
    void wholeDoubleKeepsDotZeroOnJsAndMatchesJvm() throws Exception {
        Files.writeString(tmp.resolve("D.kf"), SRC);
        Run jvm = runJvm(tmp.resolve("D.kf"), tmp.resolve("o-jvm"));
        assertTrue(jvm.ok(), () -> "JVM falhou: " + jvm.output());
        String oracle = jvm.output().trim().replace("\r\n", "\n");
        assertEquals("1.0\n0.0\n-7.0\n2.5\n42\n9000000000\ntrue\n{\"x\":1.5}"
                        + "\n{\"d\":1.0,\"n\":42,\"v\":9000000000}"
                        + "\n{\"d\":2.5,\"n\":7,\"v\":3}"
                        + "\n{\"w\":1.0}"
                        + "\n[{\"d\":1.0,\"n\":1,\"v\":1},{\"d\":0.0,\"n\":2,\"v\":2}]",
                oracle, "oráculo JVM (§659d escalar + record)");

        Run js = runJs(tmp.resolve("D.kf"), tmp.resolve("o-js"));
        assertTrue(js.ok(), () -> "JS falhou: " + js.output());
        assertEquals(oracle, js.output().trim().replace("\r\n", "\n"),
                "JS≡JVM no encode de Double inteiro (§659d escalar + record)");
    }
}
