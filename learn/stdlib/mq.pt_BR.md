[English](mq.md) | [Português](mq.pt_BR.md)

# kof.mq — tópicos e filas, in-process

> **Status: JVM ✅ · faces parciais nos outros alvos (`MQ001`) · golden cross
> ⏳ — veja a linha 5 do ledger de paridade.**

| Função | Forma |
|--------|-------|
| `publish` | `publish(String topic, Object payload) -> void` |
| `subscribe` | `subscribe(String topic, Object handler) -> void` |
| `unsubscribe` | `unsubscribe(String topic, Object handler) -> void` |
| `queue` | `queue() -> String` |
| `push` | `push(String queue, Object value) -> void` |
| `pop` | `pop(String queue) -> Object` |
| `queueSize` | `queueSize(String queue) -> Int` |

```kf
mq.subscribe("pedidos", (o: Order) -> println("novo pedido " + o.id))
mq.publish("pedidos", Order(7, "mel"))
```

- Tópicos espalham para os assinantes; filas são FIFO (`push`/`pop`/
  `queueSize`).
- In-process por design — broker de rede é interop (R9: use o real, nunca
  reimplemente aqui).

**Veja também:** [18 — Concorrência](../18-concurrency.pt_BR.md) — canais
para FIFO entre workers.
