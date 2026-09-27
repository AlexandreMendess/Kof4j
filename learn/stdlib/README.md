[English](README.md) | [Português](README.pt_BR.md)

# stdlib/ — one chapter per namespace

Every Kof stdlib namespace has its own chapter here. The function tables are
**measured from the real dispatchers** (`StdCatalog` / `Kof*.java`) — the
chapter set is the 1:1 contract with the implementation. Parity faces are
tracked in [`docs/development/parity/PARITY-GAPS.md`](../../docs/development/parity/PARITY-GAPS.md)
(full parity is the 0.5.0 blocker).

App services: [json](json.md) · [db](db.md) · [http](http.md) ·
[cache](cache.md) · [config](config.md) · [log](log.md) ·
[observability](observability.md)

Exec/platform: [process](process.md) · [shell](shell.md) · [ssh](ssh.md) ·
[net](net.md) · [mq](mq.md) · [gpu](gpu.md) · [tetris](tetris.md)

Data: [orm](orm.md) · [buffer](buffer.md) · [media](media.md)

Security: [passwords](passwords.md) · [crypto](crypto.md) · [jwt](jwt.md) ·
[secrets](secrets.md) · [security](security.md) · [auth](auth.md)

Core: [math](math.md) · [strings](strings.md) · [encoding](encoding.md) ·
[uuid](uuid.md) · [time](time.md) · [random](random.md) · [rng](rng.md) ·
[validation](validation.md)
