[English](log.md) | [Português](log.pt_BR.md)

# kof.log — four levels, zero ceremony

> **Status: stable — all targets.** `debug`, `info`, `warn`, `error` — each
> prints a timestamped line; no logger wiring, no SLF4J.

| Function | Form |
|----------|------|
| `debug` | `debug(String msg) -> void` |
| `info` | `info(String msg) -> void` |
| `warn` | `warn(String msg) -> void` |
| `error` | `error(String msg) -> void` |

```kf
log.info("service started on port " + port)
log.warn("retry " + attempt + " of " + max)
log.error("query failed: " + e)
```

- Interpolation is plain `+` — there is no `{}` placeholder grammar to learn.
- For distributed context (request/trace ids) see
  [kof.observability](observability.md).

**See also:** [kof.observability](observability.md) — metrics and traces.
