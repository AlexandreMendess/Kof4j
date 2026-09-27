[English](cache.md) | [Português](cache.pt_BR.md)

# kof.cache — cache local do processo com TTL

> **Status: estável — JVM / Native / JS / Script** · em memória, local do
> processo (não é cache distribuído — para isso, interop-first, R9).

| Função | Forma |
|--------|-------|
| `get` | `get(String key) -> String` |
| `set` | `set(String key, String value) -> void` · `set(key, value, Int ttlSeconds) -> void` |
| `ttl` | `ttl(String key) -> Int` |
| `delete` | `delete(String key) -> void` |
| `clear` | `clear() -> void` |

```kf
var page = cache.get("dashboard")
if (page == "") {
    page = consultaCara()
    cache.set("dashboard", page, 60)   // expira em 60s
}
println(page)
```

- `ttl(key)` devolve os segundos restantes (`-1` = sem expiração, `0` =
  ausente).
- Valores são strings — componha com `json.encode` para cachear records.

**Veja também:** [kof.json](json.pt_BR.md) — serialize qualquer coisa numa
string cacheável.
