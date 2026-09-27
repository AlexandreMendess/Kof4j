[English](05-control-flow.md) | [Português](05-control-flow.pt_BR.md)

# 05 — Control Flow

> **Status: implemented (JVM / Native / JS) — 0.5.0-beta**
>
> `if/else`, `while`, `for`, `for-in`, `switch`, `break/continue` work on the three targets. Pattern matching (`case String s`, `Point(x,y)`) see chapter 15.

## Conditional

### if / else

```kf
if (idade >= 18) {
    print("maior de idade");
} else {
    print("menor de idade");
}
```

### if as an expression

```kf
String mensagem = if (ativo) "sim" else "não";
```

## Loops

### while

```kf
var i = 0;
while (i < 10) {
    print(i);
    i++;
}
```

### for

```kf
for (var i = 0; i < 10; i++) {
    print(i);
}
```

### for-in

```kf
for (var nome in nomes) {
    print(nome)
}
```

Works over `List<T>` and arrays (syntax `for (var x in colecao)`).

## switch

```kf
switch (dia) {
    case 1:
        println("segunda")
    case 5:
        println("sexta")
    default:
        println("meio")
}
```

> Note: each `case` ends on its own (there is no *fallthrough* between cases —
> the compiler jumps to the end of the `switch` when the body finishes), so `break`
> **is not needed** inside `switch`. Use `break`/`continue` only in
> loops. If-expr is the preferred form for conditional values:
> `var x = if (c) a else b`. When the `switch` produces a value, use the
> **expression** form (SYN001, 0.4.0-beta): `var r = switch (dia) { case 1 -> "seg";
> default -> "outro" }` — no `break`, no block scope, `default`
> required. Switch with patterns (type pattern / destructuring) see chapter
> 15.

## break and continue

```kf
for (var i = 0; i < 100; i++) {
    if (i == 50) break;
    if (i % 2 == 0) continue;
    print(i);
}
```

## Next step

[Functions →](06-functions.md)
