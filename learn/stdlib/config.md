[English](config.md) | [Português](config.pt_BR.md)

# kof.config — typed configuration with sane precedence

> **Status: stable — all targets.** Precedence: env var `KOF_<KEY>` >
> config file (`kof.config`) > caller default.

| Function | Form |
|----------|------|
| `get` | `get(String key) -> String` |
| `env` | `env(String key) -> String` |
| `has` | `has(String key) -> Bool` |
| `str` | `str(String key, String d) -> String` |
| `int` | `int(String key, Int d) -> Int` |
| `long` | `long(String key, Long d) -> Long` |
| `bool` | `bool(String key, Bool d) -> Bool` |
| `required` | `required(String key) -> String` |

```kf
main() {
    var port = config.int("port", 8080)        // KOF_PORT beats the file
    var token = config.required("api_token")   // throws when absent — no silent ""
    if (config.bool("verbose", false)) { log.info("verbose on") }
}
```

- Typed getters fall back to the DEFAULT you pass; `required` is the honest
  path for what cannot have a default.
- No getters/setters, no Config class — the platform owns the loading order.

**See also:** [24 — Build Tools](../24-build-tools.md) — where `kof.config`
lives in a project.
