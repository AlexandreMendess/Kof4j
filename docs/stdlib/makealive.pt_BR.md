[English](makealive.md) | [Português](makealive.pt_BR.md)

# Infraestrutura como código tipado — `kof.makealive`

**Data:** 20 de setembro de 2026
**Status:** 3.1 POUSOU (plano universal, Estágio 3, fatia MK-1 — núcleo + estado + provedores fs/CLI/REST). A enquete do mantenedor em `DECISIONS.md` §D-MAKEALIVE respondeu Q1–Q4: namespace `kof.makealive` (o literal `kof.infra` é vetado por R1), provedores = corpos genéricos escritos pelo usuário, estado em `kof.db` desde o primeiro dia, host plano injetado com faces em inglês.

> **O que é isto:** uma descrição tipada de um mundo desejado (uma
> `Infrastructure` de recursos com props e dependências), um `plan` puro de
> diff contra o `State` atual, e `apply`/`destroy` executados através de um
> `Provider` de três function-values. O host é só composição — toda fronteira
> de runtime (arquivos, processos, HTTP, um banco) vive no corpo do provedor,
> escrito por você em Kof puro. O desenho tem um **açúcar** declarativo (linha 3.2
> `infra "prod" { ... }`, **DECIDIDA 21/09 `D-MAKEALIVE-SYNTAX`**, pousada — ver "O desenho declarativo" abaixo);
> o gate de ciclo fica **runtime-only** (linha 3.7 FECHADA em 21/09 — a recusa
> nomeia os membros; sem grafo em compile-time).

## O contrato (faces injetadas por `import kof.makealive`)

```kf
import kof.makealive

var d = Infrastructure("prod")          // um design
d.resource("file", "index")             // (tipo, nome) — nome único por design
d.resource("file", "style")
d.requires("style", "index")            // dependência: style é criado DEPOIS de index
d.prop("index", "html", "<h1>hi</h1>") // atributos desejados

var p = Provider(
    (r: Resource) -> Map<String, String>,          // lê o mundo para r
    (r: Resource, Map<String, String>) -> Bool,    // converge r às props
    (r: Resource) -> Bool                           // remove r do mundo
)

var rep = apply(d, State(), p)     // Plan = diff(desejado, estado); apply
var rep2 = apply(d, rep.state, p)  // → zero chamadas quando o mundo já converge
var rep3 = destroy(d, rep2.state, p) // ordem topológica REVERSA
```

Regras duras (plano §3, todas pinadas por `MakealiveE2ETest`):

- **Idempotência por construção**: `plan` é um diff puro; um segundo apply de
  um design convergido faz ZERO chamadas ao provedor. A fonte da verdade de
  "convergido" é o READ do seu provedor, nunca memória do host.
- **State só no sucesso**: `apply` monta o novo `State` a partir dos sucessos
  por operação; um set/delete recusado NÃO entra no registro e o `Report`
  mantém o restante intocado do estado anterior — sem mundo parcial
  silencioso (R6). A operação recusada GRITA nomeando o recurso
  (`makealive: provider recusou set em 'index'`).
- **Guardas lançam por nome**: nome de recurso duplicado (builder), ciclo de
  dependência (nomeia todos os membros), nomes vazios. `destroy` executa a
  ordem topológica REVERSA e, depois, a reversão de inserção para órfãos.
- **State é dado**: `State.entries` é `List<StateEntry>` e cada entry guarda
  `propFlat` (chave, valor alternados) — iterado com `while` + índice.
  `Map.keys()` funciona em JVM/JS mas é target-dependente (Native); o READ do
  `plan` acontece pelo SEU provedor, então é o seu corpo que decide a
  codificação.

## O desenho declarativo: `infra "prod" { ... }`

A linha 3.2 (`DECISIONS.md` §D-MAKEALIVE-SYNTAX, 21/09) adiciona UMA forma
declarativa; é **açúcar puro** sobre as faces acima — sem keyword, sem token,
sem tipo, sem runtime. O bloco desugara no `design(): Infrastructure` que o CLI
já espera:

```kf
import kof.makealive

infra "prod" {
    resource("file", "index")           // mesmas faces: (tipo, nome)
    prop("index", "html", "<h1>hi</h1>")
    resource("file", "style")
    requires("style", "index")          // style criado DEPOIS de index
}
```

Desugaring (exatamente o gêmeo escrito à mão):

```kf
Infrastructure design() {
    var __infra = Infrastructure("prod")
    __infra.resource("file", "index")
    __infra.prop("index", "html", "<h1>hi</h1>")
    __infra.resource("file", "style")
    __infra.requires("style", "index")
    return __infra
}
```

Regras:

- O corpo aceita **apenas chamadas de builder** (`resource`/`prop`/`requires`);
  qualquer outro statement é erro de parse nomeado — nunca ignorado em silêncio (R6).
- `infra` é **identificador**, despachado como `test`/`application` — o núcleo da
  linguagem não cresce (`LanguageCoreSurfaceTest` fica verde por construção).
- As dependências recusam ciclo **em runtime**, nomeando os membros; não há gate
  em compile-time (linha 3.7 fechada como runtime-only, 21/09).

Prova: `InfraSyntaxE2ETest` — o bloco e seu gêmeo `design()` escrito à mão
produzem `plan` byte-idêntico (JVM==JS) e o bloco compila para Native.

## A porta de estado: persistindo `State` em `kof.db`

`mkSaveState(dbConn, design, gen, state)` / `mkLoadState(dbConn, design)`
(faces de uma fatia db aditiva — o host-core nunca referencia `orm`, então o
pacote inteiro ainda compila para Native):

- uma linha por (recurso, posição-em-propFlat) da entidade `KofMkState`, mais
  uma linha MARCA em `idx = -1` para que um recurso com zero props sobreviva;
- **updates são gerações novas** (`gen` é coluna): a entidade é imutável
  (SEM038), re-salvar uma chave é violação de unique — `mkLoadState` devolve
  as linhas da `gen` MAIOR do design;
- JVM e JS são byte-idênticos (a face JS delega ao mesmo
  `kof_platform.db*` in-JVM); **Native recusa em runtime nomeando `ORM001`**
  (o stub alto, mesma divisão de `workflow-ckpt-host`). O flip é mecânico
  (trocar o stub pela fatia) quando D-DB-GAPS entregar `orm.create` para
  Native (F1d); `delete_all` já pousou como F1a (20/09).

Provas: `MakealiveDbHostE2ETest` (roundtrip, gen máxima, design vazio, guardas).

## Provedores: corpos genéricos (fs / CLI / REST), não APIs novas

A superfície v1 é as três interfaces sistêmicas genéricas — cada uma é só o
corpo do SEU provedor em Kof puro, e cada uma tem um golden executável:

| provedor | fronteira usada | golden |
|---|---|---|
| **fs** | `kof.io` `File` (`readText`/`writeText`/`delete`/`exists`) | `MakealiveFsProviderE2ETest` |
| **CLI** | `kof.shell` `run`/`pipeline` (`exitCode`/`stdout`) | `MakealiveCliProviderE2ETest` |
| **REST** | `kof.http` `get`/`put`/`delete` + `http.status(url)` | `MakealiveRestProviderE2ETest` |

Formas medidas (20/09, estes são os contratos — `docs/architecture/makealive-plan.md` §4):

- **fs**: `writeText` devolve Bool no JVM mas o **JS reporta o código bruto
  da ponte `0/-1`** (§382 — `0` é falsy); o padrão honesto é
  efeito-e-verificação: `f.writeText(x); return f.exists()`. `File` NÃO tem
  mkdirs (regra 6 — o pai deve existir); `readText` de arquivo inexistente
  devolve `null` (teste na forma positiva: `if (t == null) return m`).
- **CLI**: `shell.run("prog", listOf("arg"))` → `Result{exitCode, stdout}`;
  programa inexistente é `exitCode -1` (nunca throw); `pipeline(listOf(
  listOf(...), listOf(...)))` compõe. O teste de idempotência = `test -f` /
  `test ! -e` pela mesma interface.
- **REST**: `http.get(url)` devolve o **body** como String (404 = body vazio,
  SEM throw; falha de conexão = throw nos DOIS motores — paridade honesta);
  o status vivo é uma sonda separada `http.status(url)` → `200/404/...`;
  `http.put(url, body)`/`http.delete(url)` devolvem o body. Um provedor REST
  portanto pergunta a existência via `status` e as props via `get`.

Os goldens CLI/REST rodam o JVM primeiro e o JS SEGUNDO **contra o mesmo
mundo compartilhado** (o diretório em disco / o servidor HTTP), provando que
a idempotência por READ cruza motores, e comparam byte a byte os programas
idênticos.

## O laço de reconciliação: `reconcile`

`reconcile(design, provider, intervalMs)` (linha 3.3) transforma o `apply` em
um laço contínuo: cada tick roda `apply(design, last, provider)` dentro de um
`spawn` (a mesma forma CONC003-JS-01 do `schedule` do workflow) e devolve o
jobId do `scheduler.every` — pare com `scheduler.cancel(id)`. Ticks podem se
sobrepor e isso é seguro por construção: a idempotência é o READ do provedor,
o `last` é só uma dica. **Não há stub no Native** aqui — `every` é real em
todo alvo (o CRON001 gateia `at`/cron, nunca o `every`). O golden
(`MakealiveReconcileE2ETest`) prova que o laço converge o mundo sozinho e que
após o `cancel` o mundo fica morto por 4 intervalos. Esperar um tick em teste
segue a regra §132: UM `time.sleep` longo, nunca polling curto dentro de
`while (a && b)` (ver `training/idioms/concurrency.md`).

```kf
var id = reconcile(design, provider, 5000)   // converge a cada 5s
// ...
scheduler.cancel(id)                          // o laco morre
```

## Nuvens ficam FORA do compilador

Provedores AWS/Azure/GCP concretos são **pacotes oficiais** (`infra-<cloud>`,
R1): são só mais corpos de provedor sobre `kof.http`/`kof.shell` com uma API
de assinatura. Nada em `kof.makealive` sabe que uma nuvem existe.

## Itens abertos / gaps honestos

- **§380** (corrigido 20/09, codegen): o roubo do false-label do `if`
  ENVOLVENTE por `if` NESTADO com `throw` no JS está fechado — as duas formas
  funcionam; a forma de guarda de nível único (`if (bad && refuse) return
  false; if (bad) throw …`) continua legal e é o que os goldens usam.
- **§382** (aberto, ponte): códigos numéricos do kof.io JS (acima).
- Linhas 3.3 (reconcile via `scheduler`), 3.6 (secrets `kof.security` — lane
  security) e 3.8 agora é o CLI `kof makealive` (D-MAKEALIVE-CLI, 20/09); o resto é do
  Estágio 3; **3.2 pousada 21/09** (açúcar declarativo `infra`, `D-MAKEALIVE-SYNTAX`), 3.7 segue ela.

## CLI (3.8 — landed 20/09, D-MAKEALIVE-CLI)

`kof makealive <plan|apply|destroy> file.kf [--state PATH] [--json] [--target jvm|js]` —
o arquivo segue Kof puro (`import kof.makealive`, `design(): Infrastructure`,
`provider(): Provider`, sem `main()`); o tool síntetiza o main() do runner sobre as MESMAS
faces do host e lê/escreve o estado h2 via `mkLoadState`/`mkSaveState`/`mkMaxGen`
(gen = max+1 sempre; destroy salva uma MARCA de estado vazio para que a geracao vazia fique
visível). `plan` nunca grava linhas; a linha marcada decide o exit code; JVM e JS são
idênticos byte a byte (`CmdMakealiveTest` 7/7). script/native seguem recusa honesta (R7), e
no Native as faces de estado mantêm o stub `ORM001` no call-site.


