package dev.kof.script;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * X2 fatia 3 no SCRIPT — o motor py roda real no interpretador (paridade de
 * construção, precedente da fatia 1) e as faces novas (`timeout`, `cancel`,
 * `INTEROP007/008`) valem no alvo pelo MESMO caminho: o deadline corre no
 * FILHO python (SIGALRM/SIGINT do OS, não do interp) e `cancel` dispara o
 * kill por `process.run` — as duas forças são do SO, o interpretador só
 * espera no readLine do mesmo runtime refletido. Goldens JVM≡SCRIPT.
 */
class InteropTimeoutScriptE2ETest {

    @Test
    void deadlineOnEngineScriptMatchesJvmGolden() throws Exception {
        assumeTrue(Files.isExecutable(Path.of("/usr/bin/python3")),
                "python3 ausente — motor não executável neste host");
        Path tmp = Files.createTempDirectory("koftimeoutscript");
        Path f = tmp.resolve("t.kf");
        Files.writeString(f, """
                import kof.interop
                main() {
                    var py = KofPy("def loop():\\n    while True:\\n        pass\\ndef sq(n):\\n    return n*n")
                    py.timeout(1000)
                    try {
                        println(py.callInt("loop", listOf()))
                    } catch (String e) {
                        println(e)
                    }
                    println(py.callInt("sq", listOf(5)))
                }
                """);
        long start = System.currentTimeMillis();
        var script = KofScript.runFile(f, dev.kof.compiler.Target.SCRIPT);
        long wall = System.currentTimeMillis() - start;
        assertTrue(script.success(), "SCRIPT: " + script.stderr());
        assertEquals("INTEROP007: loop exceeded the 1000ms deadline and was stopped by the engine itself\n25",
                script.stdout().replace("\r\n", "\n").trim(),
                "007 nomeado + motor vivo de novo (replay) = golden JVM");
        assertTrue(wall < 30000, "a espera foi limitada no interpretado tambem: " + wall + "ms");
    }
}
