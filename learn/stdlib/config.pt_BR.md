[English](config.md) | [Português](config.pt_BR.md)

# kof.config — configuração tipada com precedência sensata

> **Status: estável — todos os alvos.** Precedência: env `KOF_<KEY>` >
> arquivo de config (`kof.config`) > default do chamador.

| Função | Forma |
|--------|-------|
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
    var port = config.int("port", 8080)        // KOF_PORT vence o arquivo
    var token = config.required("api_token")   // lança quando ausente — sem "" silencioso
    if (config.bool("verbose", false)) { log.info("verbose on") }
}
```

- Os getters tipados caem no DEFAULT que você passa; `required` é o caminho
  honesto para o que não pode ter default.
- Sem getters/setters, sem classe Config — a plataforma dona da ordem de carga.

**Veja também:** [24 — Build Tools](../24-build-tools.pt_BR.md) — onde o
`kof.config` vive num projeto.
