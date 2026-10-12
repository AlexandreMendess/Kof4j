package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** The §657 family, part 2: the `mq` topic params bound by ARITY alone —
 *  `mq.push(42, "x")` bound a non-String topic silently. The isStr guards on
 *  the topic params make the mismatch fall to the existing namespace
 *  resolution refusal (SEM025). Valid faces untouched. */
class MqTopicTypeGuardE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private String diagOf(String tag, String src) throws Exception {
        Path file = tmp.resolve(tag + "/Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, src);
        return String.join("\n", driver.compile(file, tmp.resolve("out-" + tag), Target.JVM)
                .diagnostics().getDiagnostics().stream().map(Object::toString).toList());
    }

    @Test
    void pushWithIntTopicIsRefused() throws Exception {
        String d = diagOf("pushInt", "main() {\n"
                + "    mq.push(42, \"x\")\n"
                + "}");
        assertTrue(d.contains("SEM025"), "esperava SEM025, veio: " + d);
    }

    @Test
    void popWithIntTopicIsRefused() throws Exception {
        String d = diagOf("popInt", "main() {\n"
                + "    var v = mq.pop(42)\n"
                + "    println(v)\n"
                + "}");
        assertTrue(d.contains("SEM025"), "esperava SEM025, veio: " + d);
    }

    @Test
    void mqValidFacesStayClean() throws Exception {
        String d = diagOf("ok", "main() {\n"
                + "    mq.publish(\"order.created\", \"12345\")\n"
                + "    var v = mq.pop(\"order.created\")\n"
                + "    println(v)\n"
                + "}");
        assertTrue(d.isEmpty(), "as faces válidas mantêm o comportamento, veio: " + d);
    }
}
