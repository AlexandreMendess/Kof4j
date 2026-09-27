[English](log.md) | [Português](log.pt_BR.md)

# kof.log — quatro níveis, zero cerimônia

> **Status: estável — todos os alvos.** `debug`, `info`, `warn`, `error` —
> cada um imprime uma linha com timestamp; sem wiring de logger, sem SLF4J.

| Função | Forma |
|--------|-------|
| `debug` | `debug(String msg) -> void` |
| `info` | `info(String msg) -> void` |
| `warn` | `warn(String msg) -> void` |
| `error` | `error(String msg) -> void` |

```kf
log.info("serviço no ar na porta " + port)
log.warn("tentativa " + attempt + " de " + max)
log.error("query falhou: " + e)
```

- Interpolação é `+` puro — não existe gramática de placeholder `{}` para
  aprender.
- Para contexto distribuído (request/trace ids) veja
  [kof.observability](observability.pt_BR.md).

**Veja também:** [kof.observability](observability.pt_BR.md) — métricas e
traces.
