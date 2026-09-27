[English](ssh.md) | [Português](ssh.pt_BR.md)

# kof.ssh — remote commands

> **Status: JVM (interop-first over the JVM SSH stack) · other targets =
> honest gap code.**

| Function | Form |
|----------|------|
| `cmd` | `cmd(String host, String command) -> List<String>` |
| `run` | `run(String host, String command) -> Result` |
| `ok` | `ok(result) -> Bool` |

```kf
val r = ssh.run("deploy@host", "systemctl restart kof-app")
if (!ssh.ok(r)) { throw "restart failed: " + r.stderr }
println(ssh.cmd("deploy@host", "uptime"))
```

- Same `Result` shape as process/shell (`stdout`/`stderr`/`exitCode`).
- The command is executed on the REMOTE side — never interpolate untrusted
  input into it.

**See also:** [kof.process](process.md) — local one-shot;
[kof.shell](shell.md) — pipelines.
