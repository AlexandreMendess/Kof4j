[English](ssh.md) | [Português](ssh.pt_BR.md)

# kof.ssh — comandos remotos

> **Status: JVM (interop-first sobre a stack SSH do JVM) · outros alvos =
> código de gap honesto.**

| Função | Forma |
|--------|-------|
| `cmd` | `cmd(String host, String command) -> List<String>` |
| `run` | `run(String host, String command) -> Result` |
| `ok` | `ok(result) -> Bool` |

```kf
val r = ssh.run("deploy@host", "systemctl restart kof-app")
if (!ssh.ok(r)) { throw "restart falhou: " + r.stderr }
println(ssh.cmd("deploy@host", "uptime"))
```

- Mesma forma de `Result` de process/shell (`stdout`/`stderr`/`exitCode`).
- O comando executa do lado REMOTO — nunca interpole input não confiável
  nele.

**Veja também:** [kof.process](process.pt_BR.md) — one-shot local;
[kof.shell](shell.pt_BR.md) — pipelines.
