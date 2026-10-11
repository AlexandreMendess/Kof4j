package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** §665 Boundary item 2: `KofTime.staticCall` bound `sleep`/`interval`/
 *  `cancel`/`daysInMonth`/`dayOfWeek`/`daysBetween`/`age` by ARITY ALONE —
 *  `time.sleep("abc")` compiled silently and died as a JVM VerifyError
 *  (the MATH-02/HTTP-01 shape), while `isLeapYear`/`isWeekend`/`addDays`
 *  in the SAME table already carried `== INT`/`== STR` guards. The guards
 *  are completed; a known member with no matching overload falls to the
 *  EXISTING namespace diagnostic (SEM025), and the bare `sleep(...)` alias
 *  (#108) diagnoses instead of inventing a phantom top-level call. */
class TimeNamespaceOverloadDiagnosticTest {

    @TempDir
    Path tmp;

    private String diagOf(String tag, String src) throws Exception {
        Path file = tmp.resolve(tag + "/Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, src);
        return String.join("\n", new CompilerDriver()
                .compile(file, tmp.resolve("out-" + tag), Target.JVM)
                .diagnostics().getDiagnostics().stream().map(Object::toString).toList());
    }

    @Test
    void sleepWithStringArgIsDiagnosed() throws Exception {
        String d = diagOf("sleepStr", "main() { time.sleep(\"abc\") }");
        assertTrue(d.contains("SEM025"), "esperava SEM025, veio: " + d);
        assertTrue(d.contains("'sleep' on namespace 'time'"),
                "a mensagem deve nomear o membro e o namespace: " + d);
    }

    @Test
    void cancelWithIntArgIsDiagnosed() throws Exception {
        String d = diagOf("cancelInt", "main() { time.cancel(401) }");
        assertTrue(d.contains("SEM025"), "esperava SEM025, veio: " + d);
    }

    @Test
    void daysInMonthWithStringArgIsDiagnosed() throws Exception {
        String d = diagOf("dimStr", "main() { var x = time.daysInMonth(\"a\", 1)  println(x) }");
        assertTrue(d.contains("SEM025"), "esperava SEM025, veio: " + d);
    }

    @Test
    void bareSleepWithStringArgFailsCheck() throws Exception {
        // the #108 bare alias only exists for the VALID Int form: with the
        // guards, sleep("abc") falls to the normal flow and the EXISTING
        // honest diagnostic fires (SEM015 Undefined function — there IS no
        // top-level sleep; the alias was never meant for wrong args).
        String d = diagOf("bareSleep", "main() { sleep(\"abc\") }");
        assertTrue(d.contains("SEM015"), "esperava SEM015, veio: " + d);
    }

    @Test
    void timeValidFacesStayClean() throws Exception {
        assertTrue(diagOf("sleepOk", "main() { time.sleep(10) }").isEmpty(),
                "time.sleep(Int) keeps compiling");
        assertTrue(diagOf("nowOk", "main() { var t = time.now()  println(t > 0) }").isEmpty(),
                "time.now() keeps compiling");
        assertTrue(diagOf("leapOk", "main() { var x = time.isLeapYear(2024)  println(x) }").isEmpty(),
                "time.isLeapYear(Int) keeps compiling");
    }
}
