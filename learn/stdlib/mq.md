[English](mq.md) | [Português](mq.pt_BR.md)

# kof.mq — topics, queues, in-process

> **Status: JVM ✅ · partial faces on other targets (`MQ001`) · golden cross ⏳
> — see parity ledger row 5.**

| Function | Form |
|----------|------|
| `publish` | `publish(String topic, Object payload) -> void` |
| `subscribe` | `subscribe(String topic, Object handler) -> void` |
| `unsubscribe` | `unsubscribe(String topic, Object handler) -> void` |
| `queue` | `queue() -> String` |
| `push` | `push(String queue, Object value) -> void` |
| `pop` | `pop(String queue) -> Object` |
| `queueSize` | `queueSize(String queue) -> Int` |

```kf
mq.subscribe("orders", (o: Order) -> println("new order " + o.id))
mq.publish("orders", Order(7, "mel"))
```

- Topics fan out to subscribers; queues are FIFO (`push`/`pop`/`queueSize`).
- In-process by design — a network broker is interop (R9: use the real one,
  never reimplement it here).

**See also:** [18 — Concurrency](../18-concurrency.md) — channels for
worker-to-worker FIFO.
