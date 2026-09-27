[English](http.md) | [Português](http.pt_BR.md)

# kof.http — cliente HTTP como uma chamada

> **Status: estável — JVM + JS (mesmo fonte, paridade byte a byte); Native tem
> núcleo próprio (HTTP002 como código de gap nas partes ainda ausentes).**

| Função | Forma |
|--------|-------|
| `get` | `get(String url) -> String` · `get(url, headers...) -> String` |
| `post` | `post(String url, String body) -> String` · `post(url, body, headers...) -> String` |
| `put` | `put(String url, String body) -> String` · variantes com headers |
| `delete` | `delete(String url) -> String` · variantes com headers |
| `patch` | `patch(String url, String body) -> String` · variantes com headers |
| `options` | `options(String url) -> String` · variantes com headers |
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

- Os verbos recebem headers como varargs (strings `"Nome: valor"`).
- `timeout`/`retry`/`circuit` são políticas DO PROCESSO — define uma vez,
  toda chamada obedece (intenção, não cerimônia por chamada).
- Payloads JSON compõem com [kof.json](json.pt_BR.md); o passo a passo real
  vive em [26 — Aplicação Real](../26-real-world-application.pt_BR.md).
