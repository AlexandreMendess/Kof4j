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
 * §6.5/§6.2 — a face de interação (`interact`: `click`, `fill=<texto>`,
 * `press=<tecla>`) medida no Chromium real: o LAÇO mínimo de E2E fica
 * completo — navegar → preencher → clicar/Enter → afirmar a mutação do DOM.
 * A §6.4 manda descobrir capacidades durante a implementação, nunca assumir:
 * antes desta fatia o provider NÃO tinha interação alguma (o javadoc §6.2
 * prometia "click/fill" — a matriz agora é verdadeira, não aspiracional).
 */
class PlaywrightInteractE2ETest {

    /** Fixture: os handlers mutam o DOM — a asserção mede o efeito REAL. */
    private static Path fixture(Path dir) throws Exception {
        Path page = dir.resolve("interact.html");
        Files.writeString(page, """
                <html><body>
                  <input id="name" placeholder="Type your name">
                  <button id="go" type="button">Greet</button>
                  <p id="out">idle</p>
                  <input id="echo">
                  <p id="echoed">-</p>
                  <script>
                    function greet() {
                      var v = document.getElementById('name').value;
                      document.getElementById('out').textContent = 'hello ' + v;
                    }
                    document.getElementById('go').addEventListener('click', greet);
                    document.getElementById('echo').addEventListener('keydown', function (e) {
                      if (e.key === 'Enter') {
                        document.getElementById('echoed').textContent =
                          'entered:' + document.getElementById('echo').value;
                      }
                    });
                  </script>
                </body></html>
                """);
        return page;
    }

    @Test
    void flowFillsClicksAndAssertsTheDomMutation(@TempDir Path dir)
            throws Exception {
        assumeTrue(PlaywrightLibProvider.findPlaywrightChromium() != null,
                "sem o Chromium do Playwright — rode npx playwright install chromium");
        PlaywrightLibProvider p = new PlaywrightLibProvider();
        String url = fixture(dir).toUri().toString();
        // o laço mínimo de E2E numa ÚNICA vida de página: preencher → clicar
        // → afirmar a mutação REAL do mesmo DOM (MEDIDO: cada face avulsa
        // abre e fecha o próprio browser — estado não atravessa chamadas)
        String out = p.flow("chromium", url, java.util.List.of(
                "css=#name :: fill=Kof",
                "css=#go :: click",
                "css=#out :: text"));
        assertEquals("hello Kof", out);
        String value = p.flow("chromium", url, java.util.List.of(
                "css=#name :: fill=Kof",
                "css=#name :: value"));
        assertEquals("Kof", value);
    }

    @Test
    void flowPressesEnterAndAssertsTheHandlerEffect(@TempDir Path dir)
            throws Exception {
        assumeTrue(PlaywrightLibProvider.findPlaywrightChromium() != null,
                "sem o Chromium do Playwright — rode npx playwright install chromium");
        PlaywrightLibProvider p = new PlaywrightLibProvider();
        String url = fixture(dir).toUri().toString();
        String out = p.flow("chromium", url, java.util.List.of(
                "css=#echo :: fill=abc",
                "css=#echo :: press=Enter",
                "css=#echoed :: text"));
        assertEquals("entered:abc", out);
    }

    @Test
    void flowRefusesActionEndingAndMalformedStepsBeforeAnyLaunch() {
        PlaywrightLibProvider p = new PlaywrightLibProvider();
        // fluxo que termina em AÇÃO é recusado: desfecho não medido não é teste
        IllegalArgumentException e1 = assertThrows(
                IllegalArgumentException.class,
                () -> p.flow("chromium", "http://localhost/x",
                        java.util.List.of("css=#a :: click")));
        assertTrue(e1.getMessage().contains("ASSERTION"),
                "a recusa exige desfecho medido: " + e1.getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> p.flow("chromium", "http://localhost/x", java.util.List.of()));
        // passo com lado direito desconhecido — parse ANTES do launch
        IllegalArgumentException e2 = assertThrows(
                IllegalArgumentException.class,
                () -> p.flow("chromium", "http://localhost/x",
                        java.util.List.of("css=#a :: hover")));
        assertTrue(e2.getMessage().contains("hover")
                        && e2.getMessage().contains("click"),
                "a recusa nomeia o passo errado E os suportados: "
                        + e2.getMessage());
    }

    @Test
    void malformedActionsRefuseBeforeAnyBrowserLaunch() {
        PlaywrightLibProvider p = new PlaywrightLibProvider();
        // o parse da ação é ANTES do launch — nada abre navegador aqui
        IllegalArgumentException e1 = assertThrows(
                IllegalArgumentException.class,
                () -> p.interact("http://localhost/x", "css=#a", "hover"));
        assertTrue(e1.getMessage().contains("hover")
                        && e1.getMessage().contains("click"),
                "a recusa nomeia a ação errada E as suportadas: "
                        + e1.getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> p.interact("http://localhost/x", "css=#a", "fill="));
        assertThrows(IllegalArgumentException.class,
                () -> p.interact("http://localhost/x", "css=#a", "press="));
        // asserção não é ação — a medição pertence ao assertOn
        IllegalArgumentException e2 = assertThrows(
                IllegalArgumentException.class,
                () -> p.interact("http://localhost/x", "css=#a", "visible"));
        assertTrue(e2.getMessage().contains("assertOn"),
                "a recusa aponta a face certa: " + e2.getMessage());
    }

    @Test
    void missingElementOnInteractionIsANamedError(@TempDir Path dir)
            throws Exception {
        assumeTrue(PlaywrightLibProvider.findPlaywrightChromium() != null,
                "sem o Chromium do Playwright — rode npx playwright install chromium");
        PlaywrightLibProvider p = new PlaywrightLibProvider();
        String url = fixture(dir).toUri().toString();
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> p.interact(url, "css=#no-such", "click"));
        assertTrue(e.getMessage().contains("#no-such")
                        && e.getMessage().contains("click"),
                "o erro nomeia locator+ação: " + e.getMessage());
    }

    @Test
    void otherBackendsRefuseTheInteractionHonestly() {
        String refused = "capability unsupported: interact on ";
        UnsupportedOperationException e1 = assertThrows(
                UnsupportedOperationException.class,
                () -> new ChromeHeadlessProvider("/usr/bin/chromium").interact(
                        "http://localhost/x", "css=#a", "click"));
        assertEquals(refused + "chrome-headless", e1.getMessage());
        UnsupportedOperationException e2 = assertThrows(
                UnsupportedOperationException.class,
                () -> new CypressProvider().interact(
                        "http://localhost/x", "css=#a", "click"));
        assertEquals(refused + "cypress", e2.getMessage());
    }
}
