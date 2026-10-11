package dev.kof.browser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * §6.5 Locators and auto-wait — a face find + assert MEDIDA no Chromium real
 * do Playwright (o binário pinado da m2, fixture local `file://`, zero rede).
 * Cada esquema semântico do plano é resolvido pelo engine nativo do backend
 * e a asserção devolve o valor medido; as recusas dos backends sem a
 * capacidade são o §6.3 (nunca equivalência fingida).
 *
 * <p>RED-first: pre-fatia a SPI não tinha `assertOn` — a superfície
 * find+assert do §6.5 não existia.
 */
class PlaywrightLocatorE2ETest {

    private static void assumeManagedChromium() {
        assumeTrue(PlaywrightLibProvider.findPlaywrightChromium() != null,
                "sem o Chromium do Playwright — rode npx playwright install chromium");
    }

    /** Fixture §6.5: um de cada face — role/label/placeholder/test-id,
     *  desabilitado, escondido, link com atributo, título pro xpath. */
    private static Path fixture(Path dir) throws Exception {
        Path page = dir.resolve("locators.html");
        Files.writeString(page, """
                <html><body>
                <h1 id="title">Kof locator fixture</h1>
                <form>
                  <label for="user">Username</label>
                  <input id="user" placeholder="Type your name" value="pre-set">
                  <button type="button" data-testid="save-btn">Save</button>
                  <button type="button" disabled>Archive</button>
                  <a id="docs" href="/docs">Docs</a>
                  <p id="hidden-one" style="display:none">ghost</p>
                </form>
                </body></html>
                """);
        return page;
    }

    @Test
    void semanticLocatorsMeasureThePlannedAssertions(@TempDir Path dir)
            throws Exception {
        assumeManagedChromium();
        PlaywrightLibProvider p = new PlaywrightLibProvider();
        String url = fixture(dir).toUri().toString();
        assertEquals("true", p.assertOn(url, "role=button[name=\"Save\"]",
                "visible"));
        assertEquals("Save", p.assertOn(url, "role=button[name=\"Save\"]",
                "text"));
        assertEquals("false", p.assertOn(url, "role=button[name=\"Archive\"]",
                "enabled"));
        assertEquals("true", p.assertOn(url, "test-id=save-btn", "enabled"));
        assertEquals("pre-set", p.assertOn(url, "label=Username", "value"));
        assertEquals("pre-set", p.assertOn(url,
                "placeholder=Type your name", "value"));
        assertEquals("false", p.assertOn(url, "css=#hidden-one", "visible"));
        assertEquals("Kof locator fixture", p.assertOn(url, "xpath=//h1",
                "text"));
        assertEquals("/docs", p.assertOn(url, "css=#docs", "attr=href"));
        assertEquals("null", p.assertOn(url, "css=#docs", "attr=title"));
    }

    @Test
    void missingElementOnAutoWaitingAssertionIsANamedError(@TempDir Path dir)
            throws Exception {
        assumeManagedChromium();
        PlaywrightLibProvider p = new PlaywrightLibProvider();
        String url = fixture(dir).toUri().toString();
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> p.assertOn(url, "text=no-such-text-anywhere", "text"));
        assertTrue(e.getMessage().contains("no-such-text-anywhere"),
                "o erro nomeia o locator que não achou nada: "
                        + e.getMessage());
        // `visible` é a leitura imediata: elemento ausente = "false", sem erro
        assertEquals("false", p.assertOn(url, "text=no-such-text-anywhere",
                "visible"));
    }

    @Test
    void backendsWithOutTheCapabilityRefuseHonestly() {
        String refused = "capability unsupported: locator/assert on ";
        UnsupportedOperationException e1 = assertThrows(
                UnsupportedOperationException.class,
                () -> new ChromeHeadlessProvider("/usr/bin/chromium").assertOn(
                        "http://localhost/x", "css=#a", "visible"));
        assertEquals(refused + "chrome-headless", e1.getMessage());
        UnsupportedOperationException e2 = assertThrows(
                UnsupportedOperationException.class,
                () -> new CypressProvider().assertOn(
                        "http://localhost/x", "css=#a", "visible"));
        assertEquals(refused + "cypress", e2.getMessage());
    }
}
