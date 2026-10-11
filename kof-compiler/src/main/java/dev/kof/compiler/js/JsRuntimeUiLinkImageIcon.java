package dev.kof.compiler.js;

/** kof-runtime.mjs — Link, Image and Icon widgets (split from Component Core, ≤500 rule). */
public final class JsRuntimeUiLinkImageIcon {
    private JsRuntimeUiLinkImageIcon() {
    }

    static  String UI_LINK_IMAGE_ICON_RUNTIME = """
            // ── Link ────────────────────────────────────────────
            export function kofUiLinkNew(text, url) {
                if (typeof document === "undefined") return -1;
                const a = document.createElement("a");
                a.textContent = text;
                a.href = url;
                a.target = "_blank";
                a.rel = "noopener";
                a.className = "kof-link";
                if (typeof window.__kofNodes === "undefined") window.__kofNodes = {};
                const id = ++kofNodeSeq;
                window.__kofNodes[id] = a;
                return id;
            }
            export function kofUiLinkSetText(link, text) {
                const n = window.__kofNodes && window.__kofNodes[link];
                if (n) n.textContent = text;
            }
            export function kofUiLinkText(link) {
                const n = window.__kofNodes && window.__kofNodes[link];
                return n ? n.textContent : "";
            }
            export function kofUiLinkSetUrl(link, url) {
                const n = window.__kofNodes && window.__kofNodes[link];
                if (n) {
                    n.href = url;
                    if (!n.target) { n.target = "_blank"; n.rel = "noopener"; }
                }
            }
            export function kofUiLinkUrl(link) {
                const n = window.__kofNodes && window.__kofNodes[link];
                return n ? n.href : "";
            }
            export function kofUiLinkRemove(link) {
                const n = window.__kofNodes && window.__kofNodes[link];
                if (n && n.parentNode) n.parentNode.removeChild(n);
            }

            // ── Image (preview) ────────────────────────────────
            export function kofUiImageNew(src) {
                if (typeof document === "undefined") return -1;
                const img = document.createElement("img");
                img.src = src;
                img.className = "kof-image";
                img.alt = "";
                if (typeof window.__kofNodes === "undefined") window.__kofNodes = {};
                const id = ++kofNodeSeq;
                window.__kofNodes[id] = img;
                return id;
            }
            export function kofUiImageSetSrc(image, src) {
                const n = window.__kofNodes && window.__kofNodes[image];
                if (n) n.src = src;
            }
            export function kofUiImageSetAlt(image, alt) {
                const n = window.__kofNodes && window.__kofNodes[image];
                if (n) n.alt = alt;
            }
            export function kofUiImageSetWidth(image, width) {
                const n = window.__kofNodes && window.__kofNodes[image];
                if (n) n.setAttribute("width", width);
            }
            export function kofUiImageSetHeight(image, height) {
                const n = window.__kofNodes && window.__kofNodes[image];
                if (n) n.setAttribute("height", height);
            }
            export function kofUiImageSrc(image) {
                const n = window.__kofNodes && window.__kofNodes[image];
                return n ? n.src : "";
            }
            export function kofUiImageRemove(image) {
                const n = window.__kofNodes && window.__kofNodes[image];
                if (n && n.parentNode) n.parentNode.removeChild(n);
            }

            // ── Icon (SVG paths embutidos) ─────────────────────
            const KOF_ICONS = {
              home: "M12 3l9 8h-3v9h-5v-6h-2v6H6v-9H3z",
              star: "M12 2l2.9 6.3 6.9.7-5.1 4.6 1.4 6.8L12 17l-6.1 3.4 1.4-6.8L2.2 9l6.9-.7z",
              heart: "M12 21s-8-5.3-8-11a4.6 4.6 0 018-3 4.6 4.6 0 018 3c0 5.7-8 11-8 11z",
              search: "M10 2a8 8 0 105.3 14l5.4 5.4 1.4-1.4-5.4-5.4A8 8 0 0010 2zm0 2a6 6 0 110 12 6 6 0 010-12z",
              settings: "M12 8a4 4 0 100 8 4 4 0 000-8zm9 4l-2.1-.6a7 7 0 00-.6-1.5l1.1-1.9-1.5-1.5-1.9 1.1a7 7 0 00-1.5-.6L14 3h-4l-.6 2.1a7 7 0 00-1.5.6L6 4.6 4.5 6.1l1.1 1.9a7 7 0 00-.6 1.5L3 10v4l2.1.6c.1.5.3 1 .6 1.5l-1.1 1.9 1.5 1.5 1.9-1.1c.5.3 1 .5 1.5.6L10 23h4l.6-2.1c.5-.1 1-.3 1.5-.6l1.9 1.1 1.5-1.5-1.1-1.9c.3-.5.5-1 .6-1.5L21 14z",
              user: "M12 12a5 5 0 100-10 5 5 0 000 10zm0 2c-4.4 0-8 2.2-8 5v3h16v-3c0-2.8-3.6-5-8-5z",
              menu: "M3 5h18v2H3zM3 11h18v2H3zM3 17h18v2H3z",
              close: "M6 5l13 13-1.4 1.4L4.6 6.4zM19 5L6 18l1.4 1.4L20.4 6.4z",
              check: "M9 16.2l-4.2-4.2L3.4 13.4 9 19 21 7l-1.4-1.4z",
              plus: "M11 5h2v6h6v2h-6v6h-2v-6H5v-2h6z",
              minus: "M5 11h14v2H5z",
              trash: "M6 7h12l-1 14H7zM9 4h6l1 2h4v2H4V6h4z",
              edit: "M4 20h4L20 8l-4-4L4 16zm2.8-3.4L16.6 6.8 17.2 7.4 7.4 17.2z",
              share: "M18 8a3 3 0 10-2.8-4H15L6 9.3a3 3 0 100 5.4l9 5.3h.2A3 3 0 1018 16a3 3 0 00-2 .8L7.3 11.6a3 3 0 000-1.2L15.9 5.3A3 3 0 0118 8z",
              download: "M12 16l-5-5h3V4h4v7h3zM5 18h14v2H5z",
              upload: "M12 3l5 5h-3v8h-4V8H7zM5 18h14v2H5z",
              mail: "M2 5h20v14H2zm2 2v.4l8 5 8-5V7l-8 5z",
              phone: "M6 2h4l2 5-2.5 1.5a12 12 0 006 6L17 12l5 2v4a2 2 0 01-2 2A17 17 0 014 4a2 2 0 012-2z",
              calendar: "M7 2h2v2h6V2h2v2h4v18H3V4h4zm12 8H5v10h14zM7 6H5v2h14V6h-2z",
              clock: "M12 2a10 10 0 100 20 10 10 0 000-20zm1 5h-2v6l5 3 1-1.7-4-2.3z",
              eye: "M12 5C6 5 2 12 2 12s4 7 10 7 10-7 10-7-4-7-10-7zm0 11a4 4 0 110-8 4 4 0 010 8z",
              lock: "M6 10V7a6 6 0 1112 0v3h2v12H4V10zm2 0h8V7a4 4 0 10-8 0z"
            };
            export function kofUiIconNew(name) { return kofUiIconNewSize(name, 24); }
            export function kofUiIconNewSize(name, size) {
                if (typeof document === "undefined") return -1;
                const d = KOF_ICONS[name] || KOF_ICONS["close"];
                const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
                svg.setAttribute("viewBox", "0 0 24 24");
                svg.setAttribute("width", size);
                svg.setAttribute("height", size);
                // D-UI-A11Y (#789): um Icon é decorativo por PADRÃO (aria-hidden);
                // setAccessibleName() o promove a imagem nomeada. Assim um ícone
                // puramente visual não é anunciado como gráfico sem nome.
                svg.setAttribute("aria-hidden", "true");
                const p = document.createElementNS("http://www.w3.org/2000/svg", "path");
                p.setAttribute("d", d);
                p.setAttribute("fill", "currentColor");
                svg.appendChild(p);
                svg.dataset.kofIcon = name;
                if (typeof window.__kofNodes === "undefined") window.__kofNodes = {};
                const id = ++kofNodeSeq;
                window.__kofNodes[id] = svg;
                return id;
            }
            export function kofUiIconSetName(icon, name) {
                const n = window.__kofNodes && window.__kofNodes[icon];
                if (!n) return;
                const d = KOF_ICONS[name];
                if (d) n.querySelector("path").setAttribute("d", d);
                n.dataset.kofIcon = name;
            }
            export function kofUiIconName(icon) {
                const n = window.__kofNodes && window.__kofNodes[icon];
                return n ? (n.dataset.kofIcon || "") : "";
            }
            export function kofUiIconSetSize(icon, size) {
                const n = window.__kofNodes && window.__kofNodes[icon];
                if (n) { n.setAttribute("width", size); n.setAttribute("height", size); }
            }
            export function kofUiIconSize(icon) {
                const n = window.__kofNodes && window.__kofNodes[icon];
                return n ? parseInt(n.getAttribute("width"), 10) || 24 : 24;
            }
            export function kofUiIconRemove(icon) {
                const n = window.__kofNodes && window.__kofNodes[icon];
                if (n && n.parentNode) n.parentNode.removeChild(n);
            }

            """;

}
