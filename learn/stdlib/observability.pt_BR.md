[English](observability.md) | [Português](observability.pt_BR.md)

# kof.observability — health, métricas, traces

> **Status: JVM ✅ · Native x86-64 ✅ · golden cross ⏳ (`OBS003` travado) —
> linha 7 do ledger de paridade.**

| Função | Forma |
|--------|-------|
| `health` | `health() -> String` |
| `readiness` / `liveness` | `-> Bool` |
| `counter` | `counter(String name) -> Int` |
| `increment` | `increment(String name, Int by) -> Int` |
| `gauge` | `gauge(String name, Int value) -> void` |
| `histogram` | `histogram(String name, Int value) -> void` |
| `metrics` | `metrics() -> String` |
| `requestId` / `correlationId` / `traceId` / `spanId` | `-> String` |
| `spanStart` | `spanStart(String name) -> String` |
| `spanEnd` | `spanEnd(String id) -> String` |
| `exportSpans` | `exportSpans() -> String` |

```kf
app.get("/healthz", () -> observability.health())
app.get("/ready", () -> observability.readiness() ? "ok" : "draining")

observability.increment("pedidos.total", 1)
var span = observability.spanStart("checkout")
pagar()
observability.spanEnd(span)
```

- `metrics()` exporta counters/gauges/histograms em formato de exposição.
- Os ids de contexto (`requestId`/`traceId`...) vêm do contexto da requisição
  atual — sem plumbing manual.

**Veja também:** [kof.log](log.pt_BR.md) — as linhas que acompanham os números.
