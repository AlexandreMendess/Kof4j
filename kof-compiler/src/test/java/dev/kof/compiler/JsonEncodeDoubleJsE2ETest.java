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
 * não-inteiro e as faces Int/Long/Bool seguem byte-idênticos. Prova
 * JVM≡JS; oráculo = JVM medido. (A face de CAMPO Double de record permanece
 * aberta — precisa do registro por campo da classe.)
 */
class JsonEncodeDoubleJsE2ETest extends ShellSupport {

    private static final String SRC = """
            main() {
                println(json.encode(1.0))
                println(json.encode(0.0))
                println(json.encode(-7.0))
                println(json.encode(2.5))
                println(json.encode(42))
                println(json.encode(9000000000L))
                println(json.encode(true))
                println(json.encode(mapOf("x", 1.5)))
            }
            """;

    @Test
    void wholeDoubleKeepsDotZeroOnJsAndMatchesJvm() throws Exception {
        Files.writeString(tmp.resolve("D.kf"), SRC);
        Run jvm = runJvm(tmp.resolve("D.kf"), tmp.resolve("o-jvm"));
        assertTrue(jvm.ok(), () -> "JVM falhou: " + jvm.output());
        String oracle = jvm.output().trim().replace("\r\n", "\n");
        assertEquals("1.0\n0.0\n-7.0\n2.5\n42\n9000000000\ntrue\n{\"x\":1.5}",
                oracle, "oráculo JVM (§659d escalar)");

        Run js = runJs(tmp.resolve("D.kf"), tmp.resolve("o-js"));
        assertTrue(js.ok(), () -> "JS falhou: " + js.output());
        assertEquals(oracle, js.output().trim().replace("\r\n", "\n"),
                "JS≡JVM no encode de Double inteiro (§659d escalar)");
    }
}
