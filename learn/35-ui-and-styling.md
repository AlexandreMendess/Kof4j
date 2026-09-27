[English](35-ui-and-styling.md) | [Português](35-ui-and-styling.pt_BR.md)

# 35 — UI and Styling

> The visual is object-oriented: an interface is a tree of objects
> composed in code. Color is a 32-bit citizen with a named palette —
> no converting hex or ANSI by hand.

## The philosophy

In Kof, the visual is not a separate world. There is no template, no XML, no
markup language. There are only **objects composed of objects**, with the
same type system, the same compiler and the same backends as the rest of the
code.

```text
View
├── Style { Color background, Int padding, ... }
├── Column
│   ├── Text "Bem-vinda"  (Style { Color primary, bold })
│   └── Button "Entrar"   (action → login())
└── Style { Color surface, ... }
```

## 32-bit color

A color is a 32-bit `Int` with ARGB semantics (`0xAARRGGBB`):

```kof
var primary = 0xFF6750A4   // hex literals are native
```

The named palette avoids conversions:

```kof
class Colors {
    static Int primary    = 0xFF6750A4
    static Int background = 0xFF121212
    static Int surface    = 0xFF1E1E1E
    static Int text       = 0xFFE0E0E0
    static Int error      = 0xFFCF6679
    static Int success    = 0xFF4CAF50
    static Int warning    = 0xFFFFB74D
}
```

And the type knows how to present itself:

```kof
class Color {
    Int value

    constructor(Int value) {
        this.value = value
    }

    Int red()   { return (this.value >> 16) & 0xFF }
    Int green() { return (this.value >> 8) & 0xFF }
    Int blue()  { return this.value & 0xFF }
    Int alpha() { return (this.value >> 24) & 0xFF }

    String ansi() {
        return "\u001b[38;2;" + this.red() + ";" + this.green() + ";" + this.blue() + "m"
    }
}
```

## Style

```kof
class Style {
    Color background
    Color foreground
    Int padding
    Int radius

    constructor(Color background = Colors.surface,
                Color foreground = Colors.text,
                Int padding = 12,
                Int radius = 8) {
        this.background = background
        this.foreground = foreground
        this.padding = padding
        this.radius = radius
    }
}
```

## Screen composition

```kof
View homeView() {
    return View(
        Style(background: Colors.background, padding: 16),
        Column([
            Text("Bem-vinda", Style(color: Colors.primary, bold: true)),
            Button("Entrar", () -> login())
        ])
    )
}
```

## Why this approach

- **One type system** — a style with a wrong field does not compile.
- **Composition** — the screen is a value: function, list, condition, everything counts.
- **Multi-target** — the same object tree is drawn by each backend.
- **Zero conversion** — the palette is the API; hex and ANSI are internal details.

## Actual state (0.5.0-beta)

This vision is **implemented** as `kof.ui` (KofJS rendering):
`Window`, `Label` (text/fontSize/bold/color), `Button` (action via lambda with
captures), `Input`, `Column`/`Row`, `View`+`Style(background, foreground,
padding, radius)`, `w.theme = Theme.dark()`, and the **Router** (Phase 7, 31/08:
`Router.route/go/replace/back/forward/current/param/depth` + `Component` with
`onMount`/`onDispose` lifecycle). Execution opens the native webview
(WebKitGTK) and closing the window terminates the program. See
[`learn/35-kof-ui.md`](35-kof-ui.md) and [`learn/37-kofjs.md`](37-kofjs.md).

See also: `docs/stdlib/COLOR.md` (palette and semantics),
`training/idioms/composition.md` (the pattern) and
`training/idioms/collections.md`.
