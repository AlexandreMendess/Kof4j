package dev.kof.browser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §6.5 — o parser PURO da gramática de locator + asserção (sem browser no
 * caminho): cada esquema da matriz de prioridade do plano, a opção
 * {@code [name="..."]} do role, os valores com {@code =} dentro (css/xpath)
 * e as recusas nomeadas de esquema/asserção desconhecidos (Q7).
 *
 * <p>RED-first: pre-fatia não havia gramática nenhuma — nem classe.
 */
class KofLocatorTest {

    @Test
    void parsesEverySemanticSchemeOfThePlan() {
        assertEquals(new KofLocator.Parsed("text", "Hello", null),
                KofLocator.parseLocator("text=Hello"));
        assertEquals(new KofLocator.Parsed("label", "User", null),
                KofLocator.parseLocator("label=User"));
        assertEquals(new KofLocator.Parsed("placeholder", "Search", null),
                KofLocator.parseLocator("placeholder=Search"));
        assertEquals(new KofLocator.Parsed("test-id", "save", null),
                KofLocator.parseLocator("test-id=save"));
        // css/xpath carregam `=` no seletor — o corte é no PRIMEIRO `=`.
        assertEquals(new KofLocator.Parsed("css", "#id > [x=1]", null),
                KofLocator.parseLocator("css=#id > [x=1]"));
        assertEquals(new KofLocator.Parsed("xpath", "//h1[@id=\"t\"]", null),
                KofLocator.parseLocator("xpath=//h1[@id=\"t\"]"));
    }

    @Test
    void parsesRoleWithAndWithoutTheAccessibleName() {
        assertEquals(new KofLocator.Parsed("role", "button", "Submit"),
                KofLocator.parseLocator("role=button[name=\"Submit\"]"));
        assertEquals(new KofLocator.Parsed("role", "heading", null),
                KofLocator.parseLocator("role=heading"));
    }

    @Test
    void refusesUnknownOrMalformedLocatorsWithNamedDiagnostics() {
        IllegalArgumentException e1 = assertThrows(IllegalArgumentException.class,
                () -> KofLocator.parseLocator("jquery=.x"));
        assertTrue(e1.getMessage().contains("jquery")
                        && e1.getMessage().contains("css"),
                "a recusa nomeia o esquema errado E os suportados: "
                        + e1.getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> KofLocator.parseLocator("no-separator"));
        assertThrows(IllegalArgumentException.class,
                () -> KofLocator.parseLocator("text="));
        assertThrows(IllegalArgumentException.class,
                () -> KofLocator.parseLocator("  "));
        assertThrows(IllegalArgumentException.class,
                () -> KofLocator.parseLocator(null));
    }

    @Test
    void parsesEveryAssertionKindIncludingAttr() {
        assertEquals(new KofLocator.Assert("visible", null),
                KofLocator.parseAssertion("visible"));
        assertEquals(new KofLocator.Assert("enabled", null),
                KofLocator.parseAssertion("enabled"));
        assertEquals(new KofLocator.Assert("text", null),
                KofLocator.parseAssertion("text"));
        assertEquals(new KofLocator.Assert("value", null),
                KofLocator.parseAssertion("value"));
        assertEquals(new KofLocator.Assert("attr", "href"),
                KofLocator.parseAssertion("attr=href"));
    }

    @Test
    void refusesUnknownOrMalformedAssertionsWithNamedDiagnostics() {
        IllegalArgumentException e1 = assertThrows(IllegalArgumentException.class,
                () -> KofLocator.parseAssertion("count"));
        assertTrue(e1.getMessage().contains("count")
                        && e1.getMessage().contains("visible"),
                "a recusa nomeia a asserção errada E as suportadas: "
                        + e1.getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> KofLocator.parseAssertion("attr="));
        assertThrows(IllegalArgumentException.class,
                () -> KofLocator.parseAssertion(""));
        assertThrows(IllegalArgumentException.class,
                () -> KofLocator.parseAssertion(null));
    }

    @Test
    void roleParseKeepsTheNameVerbatimIncludingEmptyBrackets() {
        // sem colchetes não há name; colchetes sem aspas não são a opção
        assertNull(KofLocator.parseLocator("role=button").name());
        assertEquals("a b c",
                KofLocator.parseLocator("role=button[name=\"a b c\"]").name());
    }
}
