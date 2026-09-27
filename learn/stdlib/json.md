[English](json.md) | [Português](json.pt_BR.md)

# kof.json — encode/decode, typed

> **Status: stable — JVM / Native / JS / Script** · complete encode/decode of
> objects, records, arrays (`Int/Long/Bool/String/Double`) and maps.

| Function | Form |
|----------|------|
| `encode` | `encode(value) -> String` |
| `decode` | `decode<T>(jsonString) -> T` |

```kf
record Task(String title, Bool done)

main() {
    var t = Task("write kof", true)
    var json = json.encode(t)          // {"title":"write kof","done":true}
    var back = json.decode<Task>(json) // bound back to the record
    println(back.title())              // write kof
}
```

- `decode<T>` binds each field to the record/class — no manual parsing, no
  maps-of-maps unless you ask for one.
- Works with the element type preserved through `List<User>` annotations
  (see [12 — Collections](../12-collections.md)).
- Native composes the encode/decode at compile time (JSN001/002/003 closed);
  the same source prints the same JSON on every target.

**See also:** [kof.db](db.md) — rows come back JSON-shaped;
[26 — Real World Application](../26-real-world-application.md) — full example.
