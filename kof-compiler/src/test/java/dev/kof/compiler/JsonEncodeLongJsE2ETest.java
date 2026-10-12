package dev.kof.compiler;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §659b — o encode JSON de um valor com {@code Long} quebrava o processo no JS:
 * um {@code Long} do Kof é um {@code BigInt} do JS e o {@code JSON.stringify} o
 * recusa ({@code TypeError: Do not know how to serialize a BigInt}). O lowering
 * agora roteia para {@code kofJsonEncode}, que serializa o {@code BigInt} como o
 * inteiro decimal (paridade JVM/nativo/Script) e mantém byte-idêntico o caminho
 * sem {@code Long}. Prova JVM≡JS; oráculo = JVM medido.
 */
class JsonEncodeLongJsE2ETest extends ShellSupport {

    private static final String SRC = """
            record L(Long v)
            main() {
                println(json.encode(9000000000L))
                println(json.encode(42L))
                println(json.encode(L(9000000000)))
                println(json.encode(listOf(1L, 2L, 3L)))
                println(json.encode(mapOf("a", 1L, "b", 2L)))
                println(json.encode(2.5))
                println(json.encode(mapOf("x", 1.5)))
            }
            """;

    @Test
    void longValuesEncodeWithoutBigIntCrashAndMatchJvm() throws Exception {
        Files.writeString(tmp.resolve("L.kf"), SRC);
        Run jvm = runJvm(tmp.resolve("L.kf"), tmp.resolve("o-jvm"));
        assertTrue(jvm.ok(), () -> "JVM falhou: " + jvm.output());
        String oracle = jvm.output().trim().replace("\r\n", "\n");
        assertEquals("9000000000\n42\n{\"v\":9000000000}\n[1,2,3]\n{\"a\":1,\"b\":2}\n2.5\n{\"x\":1.5}",
                oracle, "oráculo JVM (§659b)");

        Run js = runJs(tmp.resolve("L.kf"), tmp.resolve("o-js"));
        assertTrue(js.ok(), () -> "JS falhou (BigInt?): " + js.output());
        assertEquals(oracle, js.output().trim().replace("\r\n", "\n"),
                "JS≡JVM no encode de Long (§659b)");
    }
}
