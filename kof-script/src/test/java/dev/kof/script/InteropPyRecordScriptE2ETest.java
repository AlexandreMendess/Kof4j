package dev.kof.script;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * X2 fatia 2 (26/09) — o round-trip de RECORDS via `KofPy.callJson` roda no
 * SCRIPT por paridade de construção (mesma máquina `kof_json_*`/processo do
 * JVM via reflexão no `KofRuntime` — javadoc do KofInterpreter). Golden =
 * saída JVM REAL medida (InteropPyRecordE2ETest, 26/09); a paridade
 * JVM≡SCRIPT é o ponto desta prova (R5).
 */
class InteropPyRecordScriptE2ETest {

    @Test
    void recordRoundTripScriptMatchesJvmGolden() throws Exception {
        assumeTrue(Files.isExecutable(Path.of("/usr/bin/python3")),
                "python3 ausente — motor não executável neste host");
        Path tmp = Files.createTempDirectory("kofpyrecscript");
        Path f = tmp.resolve("p.kf");
        Files.writeString(f, """
                import kof.interop
                record Point(Int x, Int y)
                record Msg(String text)
                main() {
                    var py = KofPy("def norm(p):\\n    return {'x': p['x']*2, 'y': p['y']*2}\\n" +
                        "def shout(m):\\n    return {'text': m['text'] + chr(10) + 'q' + chr(34) + '10' + chr(34) + '!'}\\n" +
                        "def zero():\\n    return 0")
                    println(json.encode(listOf(Point(3, 4), Point(5, 6))))
                    var q = json.decode<Point>(py.callJson("norm", json.encode(listOf(Point(3, 4)))))
                    println(q.x())
                    println(q.y())
                    var m = json.decode<Msg>(py.callJson("shout", json.encode(listOf(Msg("he-\\"llo\\"")))))
                    println(m.text)
                    println(json.decode<Int>(py.callJson("zero", "[]")))
                    println(json.encode(Msg("rt-OK")))
                }
                """);
        var script = KofScript.runFile(f, dev.kof.compiler.Target.SCRIPT);
        assertTrue(script.success(), "SCRIPT: " + script.stderr());
        assertEquals("[{\"x\":3,\"y\":4},{\"x\":5,\"y\":6}]\n6\n8\nhe-\"llo\"\nq\"10\"!\n0\n{\"text\":\"rt-OK\"}",
                script.stdout().replace("\r\n", "\n").trim(),
                "motor py interpretado == golden JVM (round-trip record)");
    }
}
