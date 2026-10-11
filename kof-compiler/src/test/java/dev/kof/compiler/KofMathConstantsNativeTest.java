package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

/**
 * The three {@code kof.math} constants — {@code math.pi()}, {@code math.e()},
 * {@code math.tau()} — are compile-time doubles. They type-checked on every
 * target, but the ONLY runtime that defined {@code kof_math_pi/e/tau} was the
 * JVM, so a Native build (x86-64 and the cross) type-checked fine and then died
 * at {@code ld} with {@code undefined reference to 'kof_math_pi'} reported as
 * the mislabeled {@code COMP001} — the §621 false-support class, whose fix
 * covered the nine trig functions but left these three constants behind. The
 * lowering site now folds them to a {@code KofLoadLiteral.ofDouble}, so they
 * work on every target, byte-identical to the JVM (the exact IEEE-754 value).
 */
class KofMathConstantsNativeTest extends KofMathSupport {

    private static final String CONST_SRC = """
            main() {
                println(math.pi())
                println(math.e())
                println(math.tau())
            }
            """;

    /** Measured on the Script target first, cross-checked on jvm/native/js. */
    private static final String CONST_OUT =
            "3.141592653589793\n2.718281828459045\n6.283185307179586";

    @Test
    void constantsWorkOnJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, CONST_SRC, CONST_OUT);
    }

    @Test
    void constantsWorkOnNative(@TempDir Path tmp) throws Exception {
        // RED pre-fold: the compile failed at ld (COMP001 undefined kof_math_pi).
        runNative(tmp, CONST_SRC, CONST_OUT);
    }

    @Test
    void constantsWorkOnJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, CONST_SRC, CONST_OUT);
    }

    @Test
    void constantsMatchOnCrossArch(@TempDir Path tmp) throws Exception {
        forCrossArch(tmp, CONST_SRC, CONST_OUT);
    }

    /** The constants still compose with the rest of the namespace (arithmetic
     *  and comparison), so the fold is not a special-case dead end. */
    @Test
    void constantsComposeOnNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, """
                main() {
                    println(math.tau() == 2.0 * math.pi())
                    println(math.pi() > 3.14 && math.pi() < 3.15)
                    println(math.e() > 2.71 && math.e() < 2.72)
                }
                """, "true\ntrue\ntrue");
    }
}
