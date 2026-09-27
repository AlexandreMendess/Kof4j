[English](json.md) | [Português](json.pt_BR.md)

# kof.json — encode/decode, tipado

> **Status: estável — JVM / Native / JS / Script** · encode/decode completo de
> objetos, records, arrays (`Int/Long/Bool/String/Double`) e maps.

| Função | Forma |
|--------|-------|
| `encode` | `encode(value) -> String` |
| `decode` | `decode<T>(jsonString) -> T` |

```kf
record Task(String title, Bool done)

main() {
    var t = Task("escrever kof", true)
    var json = json.encode(t)          // {"title":"escrever kof","done":true}
    var back = json.decode<Task>(json) // vinculado de volta ao record
    println(back.title())              // escrever kof
}
```

- `decode<T>` vincula cada campo ao record/classe — sem parser manual, sem
  maps-de-maps a menos que você peça um.
- Funciona com o tipo de elemento preservado através de anotações
  `List<User>` (veja [12 — Coleções](../12-collections.md)).
- O Native compõe encode/decode em compile-time (JSN001/002/003 fechados); o
  mesmo fonte imprime o mesmo JSON em todos os alvos.

**Veja também:** [kof.db](db.pt_BR.md) — linhas voltam em forma JSON;
[26 — Aplicação Real](../26-real-world-application.pt_BR.md) — exemplo completo.
