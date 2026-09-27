[English](http.md) | [Português](http.pt_BR.md)

# kof.http — HTTP client as a call

> **Status: stable — JVM + JS (same source, byte-parity); Native has its own
> core (HTTP002 gap code on the pieces still absent).**

| Function | Form |
|----------|------|
| `get` | `get(String url) -> String` · `get(url, headers...) -> String` |
| `post` | `post(String url, String body) -> String` · `post(url, body, headers...) -> String` |
| `put` | `put(String url, String body) -> String` · variants with headers |
| `delete` | `delete(String url) -> String` · variants with headers |
| `patch` | `patch(String url, String body) -> String` · variants with headers |
| `options` | `options(String url) -> String` · variants with headers |
| `status` | `status(String url) -> Int` |
| `timeout` | `timeout(Int ms) -> void` |
| `retry` | `retry(Int count) -> void` |
| `circuit` | `circuit(Int threshold) -> void` |

```kf
main() {
    http.timeout(5000)
    var body = http.get("https://api.example.com/tasks")
    var t = json.decode<Task>(body)
    http.post("https://api.example.com/tasks", json.encode(t), "Content-Type: application/json")
}
```

- Verbs take headers as varargs (`"Name: value"` strings).
- `timeout`/`retry`/`circuit` are PROCESS-WIDE policies — set once, every call
  obeys (intention, not per-call ceremony).
- JSON payloads compose with [kof.json](json.md); the real-world walkthrough
  lives in [26 — Real World Application](../26-real-world-application.md).
