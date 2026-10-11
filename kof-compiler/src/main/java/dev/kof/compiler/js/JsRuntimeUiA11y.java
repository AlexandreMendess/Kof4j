package dev.kof.compiler.js;

/**
 * kof-runtime.mjs — D-UI-A11Y: o mapeamento intent→ARIA do {@code kof.ui}.
 *
 * <p>Bloco separado de {@link JsRuntimeUiComponents} apenas para caber no alvo de linhas por classe
 * (REFACTOR-500); é concatenado ao MESMO bloco {@code ui-components} em {@link JsRuntimeSlices}, de
 * modo que o escopo de módulo — e portanto a resolução de nomes entre as funções — é o mesmo.
 *
 * <p>O contrato (maintainer 11/10, #789): o Kof nunca carrega vocabulário ARIA/HTML; o runtime
 * traduz a INTENÇÃO ({@code setAccessibleName}/{@code setAnnounce}/{@code on("click")}) mais o tipo
 * do widget para os atributos correspondentes. A intenção é gravada no nó ({@code _kofA11y}) e todos
 * os atributos gerenciados são recomputados a partir dela, o que torna o resultado independente da
 * ORDEM das chamadas e descarta estado obsoleto na reutilização de raiz (#807).
 */
public final class JsRuntimeUiA11y {
    private JsRuntimeUiA11y() {
    }

    static String uiA11yRuntime() {
        return """
            // D-UI-A11Y (#789): o papel-base que o TIPO do widget declara,
            // independente de qualquer chamada de acessibilidade — a primeira
            // Window é o marco "main", um Canvas é uma imagem. Preservado por
            // kofUiSyncA11y quando a intenção não o sobrepõe.
            function kofUiA11yBaseRole(node) {
                const tag = node.tagName ? String(node.tagName).toUpperCase() : "";
                if (tag === "CANVAS") return "img";
                const cls = node.className ? String(node.className).split(/\\s+/) : [];
                if (cls.indexOf("kof-window") >= 0) return "main";
                return "";
            }

            // D-UI-A11Y (#789/#807): recomputa TODOS os atributos gerenciados
            // pelo runtime a partir da INTENÇÃO gravada no nó (_kofA11y:
            // name/announce/clickable) e do tipo do widget. Uma única fonte de
            // verdade significa que a ORDEM das chamadas não muda o resultado
            // (nome antes/depois de on("click") dá o mesmo papel) e que a
            // reutilização de raiz descarta o estado obsoleto.
            function kofUiSyncA11y(node) {
                if (!node || typeof node.setAttribute !== "function") return;
                const a = node._kofA11y || {};
                const tag = node.tagName ? String(node.tagName).toUpperCase() : "";
                const nativeControl = (tag === "BUTTON" || tag === "A" || tag === "INPUT"
                        || tag === "TEXTAREA" || tag === "SELECT" || tag === "OPTION");
                const isIcon = !!(node.dataset && node.dataset.kofIcon);
                const named = a.name !== undefined && a.name !== null && a.name !== "";
                const clickable = !!a.clickable && !nativeControl;
                // papel: a intenção "acionável" vence o papel-base do tipo; um
                // Icon nomeado é uma imagem; fora disso vale o papel do tipo.
                let role = "";
                if (clickable) role = "button";
                else if (isIcon && named) role = "img";
                else role = kofUiA11yBaseRole(node);
                if (role) node.setAttribute("role", role);
                else if (node.removeAttribute) node.removeAttribute("role");
                if (clickable) node.setAttribute("tabindex", "0");
                else if (node.removeAttribute) node.removeAttribute("tabindex");
                if (named) node.setAttribute("aria-label", a.name);
                else if (node.removeAttribute) node.removeAttribute("aria-label");
                if (isIcon) {
                    // um Icon acionável por teclado NÃO pode seguir escondido
                    // (aria-hidden-focus); decorativo continua escondido.
                    if (clickable) {
                        if (node.removeAttribute) node.removeAttribute("aria-hidden");
                    } else {
                        node.setAttribute("aria-hidden", named ? "false" : "true");
                    }
                }
                if (a.announce) node.setAttribute("aria-live", "polite");
                else if (node.removeAttribute) node.removeAttribute("aria-live");
            }

            // D-UI-A11Y (#789): um alvo de clique que NÃO é um controle nativo
            // (<button>/<a>/<input>…) precisa ser alcançável e acionável por
            // teclado. A INTENÇÃO é gravada no nó (clickable) e o mapeamento sai
            // de kofUiSyncA11y. NUNCA converte widget decorativo: só roda quando
            // um handler de clique foi registrado (kofUiWidgetOnHandle).
            function kofUiMakeKeyboardOperable(node) {
                const tag = node.tagName ? String(node.tagName).toUpperCase() : "";
                if (tag === "BUTTON" || tag === "A" || tag === "INPUT" || tag === "TEXTAREA"
                        || tag === "SELECT" || tag === "OPTION") {
                    return;
                }
                node._kofA11y = node._kofA11y || {};
                node._kofA11y.clickable = true;
                kofUiSyncA11y(node);
                if (!node._kofKeyboardWired) {
                    node._kofKeyboardWired = true;
                    const keyFn = function (ev) {
                        // #807: só teclas no PRÓPRIO widget o ativam. Um Enter
                        // dentro de um descendente (um Input num Row clicável)
                        // pertence ao descendente — o evento borbulha até aqui e
                        // sem esta guarda o container disparava junto.
                        if (ev && ev.target && ev.target !== node) return;
                        const k = ev && typeof ev.key === "string" ? ev.key : "";
                        if (k === "Enter" || k === " " || k === "Spacebar") {
                            if (ev && typeof ev.preventDefault === "function") ev.preventDefault();
                            if (typeof node.click === "function") node.click();
                        }
                    };
                    // marcado como wrapper de widget: na reutilização de raiz (B)
                    // o listener é RE-CRIADO no nó sobrevivente por
                    // kofUiRehomeWidgetHandlers, não movido (fecharia sobre o nó
                    // descartado).
                    keyFn.__kofWidgetWrapper = true;
                    kofUiOnDom(node, "keydown", keyFn);
                }
            }

            // D-UI-A11Y (#789/#807): o mesmo contrato para o host de um
            // Component — .on("click") no componente também precisa ser
            // acionável por teclado (o host é um <div>, não um controle nativo).
            function kofUiMakeComponentKeyboardOperable(c, el) {
                if (!el || typeof el.setAttribute !== "function") return;
                el._kofA11y = el._kofA11y || {};
                el._kofA11y.clickable = true;
                kofUiSyncA11y(el);
                if (el._kofComponentKeyboardWired) return;
                el._kofComponentKeyboardWired = true;
                if (typeof el.addEventListener !== "function") return;
                el.addEventListener("keydown", function (ev) {
                    if (ev && ev.target && ev.target !== el) return;
                    const k = ev && typeof ev.key === "string" ? ev.key : "";
                    if (k !== "Enter" && k !== " " && k !== "Spacebar") return;
                    if (ev && typeof ev.preventDefault === "function") ev.preventDefault();
                    kofUiDispatchEvent(c, "click", ev);
                });
            }

            """;
    }

}
