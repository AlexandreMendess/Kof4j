package dev.kof.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** #820 — `kof profile` CPU ratio sempre 0%: o GNU time imprime "28%" e,
 * em LC_NUMERIC=pt_BR, "0,24" — Double.parseDouble rejeita ambos e o catch
 * devolvia 0 silenciosamente (R6: o derived metric mentia). */
class ProfileNumberParseTest {

    @Test
    void percentSuffixParses() {
        assertEquals(28.0, Profile.parseLenientNumber("28%"), 1e-9);
        assertEquals(28.0, Profile.parseLenientNumber(" 28% "), 1e-9);
    }

    @Test
    void commaDecimalParses() {
        assertEquals(0.24, Profile.parseLenientNumber("0,24"), 1e-9);
        assertEquals(1.5, Profile.parseLenientNumber("1,5"), 1e-9);
    }

    @Test
    void dotDecimalStillParses() {
        assertEquals(0.24, Profile.parseLenientNumber("0.24"), 1e-9);
    }

    @Test
    void garbageStillReturnsZero() {
        assertEquals(0.0, Profile.parseLenientNumber("abc"), 1e-9);
    }
}
