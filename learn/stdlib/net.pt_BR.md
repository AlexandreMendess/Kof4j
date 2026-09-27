[English](net.md) | [Português](net.pt_BR.md)

# kof.net — componentes de URL, encode/decode

> **Status: estável — JVM / Native (incl. riscv64/aarch64) / JS.**

| Função | Forma |
|--------|-------|
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

- Acessores de componente devolvem `""` quando a parte está ausente (sem
  dança do `null`).
- `queryEncode`/`queryDecode` são a forma RFC 3986 para query strings — use
  com [kof.http](http.pt_BR.md).

**Veja também:** [kof.http](http.pt_BR.md) — as requisições em si.
