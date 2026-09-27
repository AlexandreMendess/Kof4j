[English](cache.md) | [Português](cache.pt_BR.md)

# kof.cache — process-local cache with TTL

> **Status: stable — JVM / Native / JS / Script** · in-memory, process-local
> (not a distributed cache — interop-first for that, R9).

| Function | Form |
|----------|------|
| `get` | `get(String key) -> String` |
| `set` | `set(String key, String value) -> void` · `set(key, value, Int ttlSeconds) -> void` |
| `ttl` | `ttl(String key) -> Int` |
| `delete` | `delete(String key) -> void` |
| `clear` | `clear() -> void` |

```kf
var page = cache.get("dashboard")
if (page == "") {
    page = expensiveQuery()
    cache.set("dashboard", page, 60)   // expires in 60s
}
println(page)
```

- `ttl(key)` returns the remaining seconds (`-1` = no expiry, `0` = absent).
- Values are strings — compose with `json.encode` to cache records.

**See also:** [kof.json](json.md) — serialize anything into a cacheable string.
