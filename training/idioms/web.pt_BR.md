[English](web.md) | [Português](web.pt_BR.md)

# Idioms — Web (kof.web)

**Status:** available (JVM) · **Introduced:** 0.2.6-beta · **Updated:** 0.3.0-beta

## What it is

`web.app()` cria a aplicação; cada `app.get/post/put/patch/delete(path) { … }`
registra uma rota. O **retorno do handler é o contrato da resposta**:

- `return "texto"` → `200 OK` com o corpo (`String`).
- `return null` → `404 Not Found` (ausência documentada, não erro).
- `status(código)` / `headerSet(...)` antes do return → cabeçalhos + código.

## GOOD — handler com presença/ausência

```kof
main() {
    var app = web.app()
    app.get("/tasks/:id") {
        var id = param("id").toInt()
        if (id >= 1) {
            return "task " + id
        }
        return null    // → 404
    }
    app.delete("/tasks/:id") {
        return "deleted:" + param("id")
    }
    app.listen(8080)
}
```

A forma idiomática `if (cond) { return valor } return null` funciona em qualquer
ordem de pernas (bug 53, GitHub #28 — corrigido 07/09: o type do handler agora
é inferido de TODOS os returns do corpo, não só do topo).

## Quando usar

- Rota REST/HTTP com o runtime `kof.web` (JVM).
- Ausência de recurso → `return null` (404), não `throw`.

## Quando NÃO usar

- Erro real do handler → `throw "mensagem"` (o runtime vira 500 com o
  diagnóstico, R6).
- Resposta não-200/404 (ex.: 301, 401) → `status(código)` + return.

## BOM — corpos de rejeição declarativos (`app.security`)

```kof
main() {
    var app = web.app()
    var r = mapOf()
    r.put("unauthorized", "{\"error\":\"faça login antes\"}")
    val rObj: Object = r
    val o: Map<String, Object> = mapOf()
    val h: Object = "authorization"
    o.put("sessionHeader", h)
    o.put("responses", rObj)
    app.security(o)
    app.get("/me") { return "ok" }
    app.listen(8080)
}
```

`responses` (`Map`) substitui o JSON embutido do pipeline para os `401`/`403`/`429`
sintéticos (chaves `unauthorized`/`forbidden`/`tooManyRequests`). Chaves omitidas
mantêm os corpos embutidos — aditivo.

## Notas

- `app.listen` aceita SÓ Int (`app.listen(8080)` — #102.2 13/09: String
  virava VerifyError em runtime; agora é SEM025 no `kof check`).

- `app.delete(path) { … }` é uma rota (verb HTTP), não `File.delete()` —
  o nome colidido era o bug 54 (GitHub #29), corrigido 07/09 (guarda de
  aridade no `KofIo`).
- Middlewares (`app.use { … }`) seguem o MESMO contrato: `return null`
  prossegue para o handler; `return "corpo"` responde e encerra (short-circuit).
