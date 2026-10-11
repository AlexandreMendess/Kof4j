package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #807 — comportamento de runtime da acessibilidade {@code D-UI-A11Y} (#789).
 *
 * Os dois testes do {@code UiAccessibilityE2ETest} cobrem um render ESTÁTICO
 * único; estes cobrem os caminhos que a mantenedora exigiu na aprovação do
 * #789: semântica segue o render CORRENTE (root reuse + rebuild+prune),
 * defaults nunca convertem decorativo em controle, alvo de clique é um botão
 * visível à tecnologia assistiva, e a ativação por teclado vale só para teclas
 * no PRÓPRIO widget. Probes leem {@code window.__kofNodes} no engine JS.
 */
class UiA11yRuntimeE2ETest extends UiSupport {

    // 1. Um Icon CLICÁVEL não pode ficar aria-hidden (axe: aria-hidden-focus).
    private static final String P1 = """
            main() {
                var del = Icon("trash")
                del.on("click", () -> println("removed"))
                var w = Window("App")
                w.bind(del)
                w.show()
            }
            """;
    private static final String PR1 = """
            const el = Object.values(window.__kofNodes).find(function (n) { return n.tagName === "SVG"; });
            console.log("role=" + el.getAttribute("role") + " tabindex=" + el.getAttribute("tabindex")
                + " hidden=" + el.getAttribute("aria-hidden"));
            """;

    // 2. A ordem das chamadas não pode decidir o papel: nome antes do on("click").
    private static final String P2 = """
            main() {
                var a = Icon("trash")
                a.setAccessibleName("Delete")
                a.on("click", () -> println("a"))
                var w = Window("App")
                w.bind(a)
                w.show()
            }
            """;
    private static final String PR2 = """
            const el = Object.values(window.__kofNodes).find(function (n) { return n.tagName === "SVG"; });
            console.log("role=" + el.getAttribute("role") + " label=" + el.getAttribute("aria-label"));
            """;

    // 3. setAccessibleName("") devolve o Icon a decorativo.
    private static final String P3 = """
            main() {
                var gone = Icon("trash")
                gone.setAccessibleName("Delete")
                gone.setAccessibleName("")
                var w = Window("App")
                w.bind(gone)
                w.show()
            }
            """;
    private static final String PR3 = """
            const el = Object.values(window.__kofNodes).find(function (n) { return n.tagName === "SVG"; });
            console.log("role=" + el.getAttribute("role") + " label=" + el.getAttribute("aria-label")
                + " hidden=" + el.getAttribute("aria-hidden"));
            """;

    // 4a. Root reuse: o nome acessível obsoleto tem de sumir quando o render para de setá-lo.
    private static final String P4A = """
            main() {
                var app = Component(0)
                var win = Window("App")
                app.view((s: Int) -> {
                    var l = Label("" + s)
                    if (s < 5) { l.setAccessibleName("Unread " + s) }
                    return l
                })
                win.bind(app)
                win.show()
            }
            """;
    private static final String PR4A = """
            import { kofUiComponentStateSet } from './kof-runtime.mjs';
            kofUiComponentStateSet(1, 9);
            const el = Object.values(window.__kofNodes).find(function (n) { return n.tagName === "SPAN"; });
            console.log("label=" + el.getAttribute("aria-label"));
            """;

    // 4b. Root reuse: aria-live obsoleto tem de sumir.
    private static final String P4B = """
            main() {
                var app = Component(0)
                var win = Window("App")
                app.view((s: Int) -> {
                    var l = Label("x")
                    if (s == 0) { l.setAnnounce(true) }
                    return l
                })
                win.bind(app)
                win.show()
            }
            """;
    private static final String PR4B = """
            import { kofUiComponentStateSet } from './kof-runtime.mjs';
            kofUiComponentStateSet(1, 1);
            const el = Object.values(window.__kofNodes).find(function (n) { return n.tagName === "SPAN"; });
            console.log("live=" + el.getAttribute("aria-live"));
            """;

    // 4c. Root reuse: um Icon que deixa de ser clicável não pode seguir role=button.
    private static final String P4C = """
            main() {
                var app = Component(0)
                var win = Window("App")
                app.view((s: Int) -> {
                    var ic = Icon("trash")
                    if (s == 0) { ic.on("click", () -> println("c")) }
                    return ic
                })
                win.bind(app)
                win.show()
            }
            """;
    private static final String PR4C = """
            import { kofUiComponentStateSet } from './kof-runtime.mjs';
            kofUiComponentStateSet(1, 1);
            const el = Object.values(window.__kofNodes).find(function (n) { return n.tagName === "SVG"; });
            console.log("role=" + el.getAttribute("role") + " tabindex=" + el.getAttribute("tabindex")
                + " hidden=" + el.getAttribute("aria-hidden"));
            """;

    // 5. Enter dentro de um descendente NÃO pode acionar o container clicável.
    private static final String P5 = """
            main() {
                var field = Input("")
                var row = Row(listOf(field))
                row.on("click", () -> println("row"))
                var w = Window("App")
                w.bind(row)
                w.show()
            }
            """;
    private static final String PR5 = """
            const row = Object.values(window.__kofNodes).find(function (n) { return n.tagName === "DIV"; });
            const inp = Object.values(window.__kofNodes).find(function (n) { return n.tagName === "INPUT"; });
            const kf = (row._handlers && row._handlers.keydown && row._handlers.keydown[0]);
            if (kf) kf({ key: "Enter", target: inp, preventDefault: function () {} });
            console.log("probe-ok");
            """;

    // 6. Component.on("click") também vira operável por teclado.
    private static final String P6 = """
            main() {
                var app = Component(0)
                app.view((s: Int) -> { return Label("card") })
                app.on("click", () -> println("card"))
                var w = Window("App")
                w.bind(app)
                w.show()
            }
            """;
    private static final String PR6 = """
            const stack = [document.body];
            let host = null;
            while (stack.length > 0) {
                const n = stack.shift();
                if (n.className === "kof-component") { host = n; break; }
                for (const k of Array.from(n.children || [])) stack.push(k);
            }
            console.log("role=" + (host ? host.getAttribute("role") : "null")
                + " tabindex=" + (host ? host.getAttribute("tabindex") : "null"));
            """;

    // 7. Rebuild+prune (kind diferente): a semântica segue o render CORRENTE —
    // o nó novo traz o nome e o papel do render atual, sem herdar nada.
    private static final String P7 = """
            main() {
                var app = Component(0)
                var win = Window("App")
                app.view((s: Int) -> {
                    if (s == 0) { return Label("plain") }
                    var ic = Icon("trash")
                    ic.setAccessibleName("Delete")
                    return ic
                })
                win.bind(app)
                win.show()
            }
            """;
    private static final String PR7 = """
            import { kofUiComponentStateSet } from './kof-runtime.mjs';
            kofUiComponentStateSet(1, 1);
            const el = Object.values(window.__kofNodes).find(function (n) { return n.tagName === "SVG"; });
            console.log("role=" + el.getAttribute("role") + " label=" + el.getAttribute("aria-label")
                + " hidden=" + el.getAttribute("aria-hidden"));
            """;

    // 8. Sem duplicatas: um documento tem no máximo UM marco "main".
    private static final String P8 = """
            main() {
                var a = Window("Um")
                a.bind(Label("a"))
                a.show()
                var b = Window("Dois")
                b.bind(Label("b"))
                b.show()
            }
            """;
    private static final String PR8 = """
            let mains = 0;
            const stack = [document.body];
            while (stack.length > 0) {
                const n = stack.shift();
                if (n.getAttribute && n.getAttribute("role") === "main") mains = mains + 1;
                for (const k of Array.from(n.children || [])) stack.push(k);
            }
            console.log("mains=" + mains);
            """;

    @Test
    void clickableIconIsNotHiddenFromAssistiveTech(@TempDir Path tempDir) throws IOException {
        assertEquals("role=button tabindex=0 hidden=null",
                runJsProbe(tempDir, "p1", P1, PR1));
    }

    @Test
    void namedClickableIconIsAButtonRegardlessOfCallOrder(@TempDir Path tempDir) throws IOException {
        assertEquals("role=button label=Delete",
                runJsProbe(tempDir, "p2", P2, PR2));
    }

    @Test
    void emptyAccessibleNameRestoresDecorativeIcon(@TempDir Path tempDir) throws IOException {
        assertEquals("role=null label=null hidden=true",
                runJsProbe(tempDir, "p3", P3, PR3));
    }

    @Test
    void staleAccessibleNameIsDroppedOnRootReuse(@TempDir Path tempDir) throws IOException {
        assertEquals("label=null", runJsProbe(tempDir, "p4a", P4A, PR4A));
    }

    @Test
    void staleAnnounceIsDroppedOnRootReuse(@TempDir Path tempDir) throws IOException {
        assertEquals("live=null", runJsProbe(tempDir, "p4b", P4B, PR4B));
    }

    @Test
    void noLongerClickableIconDropsButtonSemanticsOnRootReuse(@TempDir Path tempDir) throws IOException {
        assertEquals("role=null tabindex=null hidden=true",
                runJsProbe(tempDir, "p4c", P4C, PR4C));
    }

    @Test
    void enterOnDescendantDoesNotActivateClickableContainer(@TempDir Path tempDir) throws IOException {
        assertEquals("probe-ok", runJsProbe(tempDir, "p5", P5, PR5));
    }

    @Test
    void clickableComponentIsKeyboardOperable(@TempDir Path tempDir) throws IOException {
        assertEquals("role=button tabindex=0",
                runJsProbe(tempDir, "p6", P6, PR6));
    }

    @Test
    void semanticsFollowCurrentRenderOnRebuildAndPrune(@TempDir Path tempDir) throws IOException {
        assertEquals("role=img label=Delete hidden=false",
                runJsProbe(tempDir, "p7", P7, PR7));
    }

    @Test
    void onlyOneMainLandmarkPerDocument(@TempDir Path tempDir) throws IOException {
        assertEquals("mains=1", runJsProbe(tempDir, "p8", P8, PR8));
    }
}
