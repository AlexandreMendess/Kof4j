[English](net.md) | [Português](net.pt_BR.md)

# kof.net — URL components, encode/decode

> **Status: stable — JVM / Native (incl. riscv64/aarch64) / JS.**

| Function | Form |
|----------|------|
| `scheme` | `scheme(String url) -> String` |
| `host` | `host(String url) -> String` |
| `port` | `port(String url) -> String` |
| `path` | `path(String url) -> String` |
| `query` | `query(String url) -> String` |
| `fragment` | `fragment(String url) -> String` |
| `queryEncode` | `queryEncode(String s) -> String` |
| `queryDecode` | `queryDecode(String s) -> String` |

```kf
var u = "https://kof.dev:8443/docs?page=2#intro"
println(net.scheme(u))    // https
println(net.host(u))      // kof.dev
println(net.port(u))      // 8443
println(net.path(u))      // /docs
println(net.query(u))     // page=2
println(net.queryEncode("a b&c"))   // a%20b%26c
```

- Component accessors return `""` when the part is absent (no `null` dance).
- `queryEncode`/`queryDecode` are the RFC 3986 form for query strings — pair
  them with [kof.http](http.md).

**See also:** [kof.http](http.md) — the requests themselves.
