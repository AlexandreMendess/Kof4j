[English](observability.md) | [Português](observability.pt_BR.md)

# kof.observability — health, metrics, traces

> **Status: JVM ✅ · Native x86-64 ✅ · cross golden ⏳ (`OBS003` pinned) —
> parity ledger row 7.**

| Function | Form |
|----------|------|
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

observability.increment("orders.total", 1)
var span = observability.spanStart("checkout")
pay()
observability.spanEnd(span)
```

- `metrics()` exports the counters/gauges/histograms in an exposition format.
- Context ids (`requestId`/`traceId`...) come from the current request
  context — no manual plumbing.

**See also:** [kof.log](log.md) — the lines that accompany the numbers.
