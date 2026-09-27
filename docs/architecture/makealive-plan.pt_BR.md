[English](makealive-plan.md) | [Português](makealive-plan.pt_BR.md)

# `Kof Makealive` — infraestrutura como código tipado (plano de design · Estágio 3 · linhas 3.1–3.8)

**Tipo:** plano de design — **COMPLETO** (todas as linhas 3.1–3.8 pousadas; fechado 21/09).
**Q1–Q4 RESPONDIDAS 20/09 (§6)**; o núcleo (3.1), 3.2, 3.3 e 3.8 pousaram
(**3.2 POUSADA 21/09 `966c86a4`** — `D-MAKEALIVE-SYNTAX`, açúcar puro sobre
`design()`), **3.7 FECHADA 21/09 como runtime-only**; o último item de regra 6,
**3.6 (secrets via `kof.security`), POUSOU 21/09 `32285136`** (`D-SECRETS` face 1,
rastreada em `secrets-plan.md`). Movido para `docs/architecture/` pela regra dos
três estados (nada pendente).
**Tracker:** [`IMPLEMENTATION-UNIVERSAL-PLATFORM.md`](../development/IMPLEMENTATION-UNIVERSAL-PLATFORM.pt_BR.md)
Estágio 3 (linhas 3.1–3.8). **Companheiro (visão):**
[`docs/architecture/UNIVERSAL-PLATFORM-VISION.pt_BR.md`](UNIVERSAL-PLATFORM-VISION.pt_BR.md)
§4.2 — o nome do domínio, o veredito imperativo-vs-declarativo e o pipeline
estão DECIDIDOS lá; este arquivo é a decomposição executável.
**Lane dona:** `.18` (development), diretiva da mantenedora 19/09
("assume a frente do kof makealive no plano da plataforma universal").

---

## 1. Objetivo

Infraestrutura como **código Kof tipado** com plan/apply/state/reconciliação —
o substituto conceitual do Terraform (VISÃO §4.2): o código *acorda* o mundo
para o estado declarado. As peças que tornam isso Kof puro **hoje** já estão
embarcadas: records/classes (dados), `Map`/`List` (o grafo), `throw` (o erro
honesto), `spawn`/`await`/`scheduler` (reconciliação), `kof.io`/`kof.db`
(estado), `kof.http`/`kof.process` (providers como interop), `kof.security`
(secrets). Makealive **compõe**; não inventa engine (INTEROP-FIRST, R9) e não
faz o core crescer (golden 8.6).

## 2. Proposta — pacote stdlib em nível Kof, NÃO nova sintaxe

**O modelo canônico é imperativo-transformado-em-dados** (o veredito da
VISÃO §4.2, "A/B — e é onde a linguagem brilha"): recursos tipados + builder
+ funções normais. O bloco declarativo `infra "prod" { ... }` é a linha **3.2**
— **DECIDIDA 21/09 (`D-MAKEALIVE-SYNTAX`) como açúcar puro sobre `design()`** (R4 ✅
pousou 21/09 e o bloqueio do hook de codegen sumiu; `infra` segue identificador) —
**POUSADA 21/09 (`966c86a4`, prova `InfraSyntaxE2ETest`)**; o v1 não esperou por ela.

Esboço de superfície (idioma de host flat, como `kof.workflow`/`kof.supervisor`
— DD-OTP-01 opção A; **formas a serem MEDIDAS pela recon 3.0 antes de virarem
face**, nunca presumidas):

- `Resource(kind, name)` — **record**: a unidade tipada do estado desejado.
- props: `Map<String,String>` (v1; tipos mais ricos = follow-up, sem ABI de
  struct).
- `Infrastructure(nome)` — classe builder: `resource(kind, name, props)` e
  `requires(child, parent)` montam o grafo; duplicatas recusadas.
- `plan(desired, current)` — **diff puro**: `creates` / `updates` / `deletes`
  em ordem determinística (topológica, depois de declaração). Zero efeito
  colateral.
- `apply(design, provider)` — convergir: cria/atualiza o que o plan manda,
  persiste estado só no sucesso; `destroy(design, provider)` — topológica
  invertida.
- **provider = valores de função** (o idioma Kof para interface, precedente
  `KofWfJob.corpo`): `read: (Resource) -> Map` + `set: (Resource, Map) -> Void`
  + `delete: (Resource) -> Void`. Sem primitiva de compilador, sem runtime novo.
- **provider v1: FS local** (recursos materializam como arquivos-marcador sob
  um diretório de estado) — torna plan/apply/state/destroy/idempotência
  **mediáveis ponta a ponta com zero credencial de cloud e zero FFI**; clouds
  concretas vão no §3.5 como interop (REST via `kof.http`, CLI via
  `kof.shell`), nunca no core.

### 2.1 A colisão R1 — MEDIDA 19/09 (é por isso que a Q1 existe)

O tracker nomeia o namespace `kof.infra` (linha 3.1). O gate-máquina R1 **é
HARD-DENY nele**: probe `check_stdlib_boundary.sh` contra uma fonte temporária
com o literal `"kof.infra"` → **rc=1**, `"VIOLATION: 'kof.infra' is a HARD-DENY
heavy domain — forbidden in base stdlib/platform (R1/AGENTS invariant 1;
official package only)"`. A lista de negação foi redigida a partir do
`infra-<cloud>` da invariant 1 (o domínio pesado são os *providers*), mas o
token nu `infra` também bloqueia o **core** do Makealive. A resolução é
decisão da mantenedora → **Q1** (§6). Nada embarca até ela responder; linha no
ledger NÃO sobrepõe hard-deny (medido).

## 3. Contrato

- **Idempotência por construção**: apply de design já convergido é no-op (o
  plan é vazio) — o golden de aceitação, não um slogan.
- **plan não tem efeitos colaterais**; só o apply toca o mundo e o estado.
- **ciclos são recusados na montagem do grafo** com `throw` acionável
  nomeando o ciclo (precedente do run() do workflow, 4/4 targets); detecção de
  ciclo em compile-time é a linha 3.7 — ✅ **FECHADA 21/09 como runtime-only**
  (adendo a `D-MAKEALIVE-SYNTAX`): com a 3.2 como açúcar puro o compilador vê só
  chamadas genéricas, então um grafo estático daria semântica própria ao bloco
  (§7/regra 11); a recusa em runtime da 3.1 nomeia os membros do ciclo.
- **o estado só avança no sucesso**: um apply falho deixa o estado anterior
  intacto e nomeia o recurso que falhou (R6, nunca um parcial silencioso).
- **secrets são só referência**: o v1 guarda o *nome* do secret (resolvido no
  apply pelo chamador); redação/`Secret` é Stage 5 (linha 3.6 🟡), nunca
  material em plaintext no arquivo de estado.
- **sem gate de target na camada de composição** (lição do workflow): o host
  é Kof puro sequencial; os gaps honestos (`CRON001`, `ORM001`, `PROC001`)
  sobem no CALL-SITE quando o corpo do provider cruza fronteira de runtime.

## 4. ABI por target — a ser MEDIDA na recon 3.0, não presumida

| Forma | Esperado | Plano de prova |
|---|---|---|
| record `Resource` + accessors | todos os targets | recon JVM+JS roda, Native compila |
| classe + campo `Map<String,String>` + iteração | JVM/JS/Script rodam; Native compila | recon |
| fixpoint topológico + ordem determinística | padrão provado (workflow) | citado, re-travado na forma de recurso |
| ciclo → `throw` String | 4 targets | recon |
| round-trip de arquivo `kof.io` (estado) | linha `kof.io` da matriz ✅✅✅ (72/74) | recon paridade JVM+JS; Native compila |
| paridade byte JVM==JS da saída do plan | regra de paridade | golden no 3.1 |

**Status do §4 19/09 — MEDIDA por `MakealivePrimitivesE2ETest` (recon 3.0.1), 5/5 VERDE**
(paridade byte JVM==JS nas execuções + pins de compilação Native; a forma throw/catch
roda também no SCRIPT): todas as linhas acima estão travadas hoje. Correções medidas:
(i) `kof.io` **não** é fachada estática — o contrato é construtor `File("path")` +
métodos de instância (`f.writeText/readText/delete/exists`; o golden é
`IoE2ETest.fileTextRoundTrip` — a linha "Static forms" de `docs/stdlib/IO.md` é drift,
sinalizar à lane de docs, não reescrever aqui); (ii) a ordem de iteração de
`mapOf().keys()` depende do target — o design dirige iteração por `List` explícito
(travado); (iii) não há ternário `?:` nem `for (i in 0..n)` de range em `.kf` — usar
if/else e while-com-índice ou `for` de elemento (travado); (iv) `record` com campos
`String` e inicializador de campo de classe `mapOf(k, v, ...)` compilam e rodam
idênticos (travado).

**status §4 20/09 (2) — face de ESTADO MEDIDA por `MakealiveDbStateE2ETest`
(sonda 3.1.1), 2/2 VERDE:** o frontend `.kf` resolve `orm.create/save/find/all/
page/where` sobre `db.connect` — **`delete/count/deleteAll/saveAll` ainda NÃO
resolvem** (o lado runtime existe em `KofOrm.functions()`; a fiação do call-site
são as fatias F1/F2 da lane GAPS-DB, cluster `5cd078c1`). Consequências travadas
para a fatia de estado: (i) entidades são imutáveis (SEM038) — um "update" = uma
NOVA linha de geração, nunca um re-save da mesma chave; (ii) arquitetura do
estado = `key = design/nome#gen` única + `all`/`where` filtrados no host (sem
delete); (iii) paridade byte JVM==JS vale pelo delegate JDBC `kof_platform.db*`
no mesmo host Graal (DB001, 16/09); (iv) o NATIVE recusa por nome (`ORM001`) — a
sonda disjuntiva vira paridade stricta quando o ORM nativo da irmã pousar, e a
fatia db do host deve ser gateada por alvo exatamente como `workflow-ckpt-host.kf`
(+ `.native.kf` stub honesto).

## 5. Fila de passos

- **3.0.0 [plano + claim — 0 superfície]** — este arquivo (EN+PT), flip da
  linha Stage 3 do tracker para 🟡, claim no `DOING.md`. ✅ 19/09.
- **3.0.1 [recon — 0 código]** — `MakealivePrimitivesE2ETest` trava as formas
  do §2/§4 nos 4 targets; o resultado volta para este arquivo (disciplina da
  recon 2.1.0 do workflow). ✅ 19/09 — 5/5 (paridade JVM==JS, compila Native,
  throw/catch no SCRIPT); achados devolvidos na nota "Status do §4" acima — inclusive
  a face medida do kof.io (construtor+instância), que chegou pela linha de coordenação
  do head irmão `c122d266`; minha escrita sobrescreveu o arquivo untracked deles em
  voo antes de eu ler a coordenação — o crédito do achado é da nota DOING deles.
  🔵 próximo: 3.0.2 (⛔ Q1–Q4).
- **3.0.2 [assinatura de design — ⛔ regra 6]** ✅ FEITO 20/09 — enquete da
  mantenedora respondeu Q1–Q4 (§6, `DECISIONS.md` §D-MAKEALIVE:
  `kof.makealive` / providers genéricos completos / kof.db dia-1 / flat+EN).
  Frente aberta.
- **3.1 [core]** ✅ 20/09 dono `.18` — **fatia COMPLETA (MK-1, enquete 20/09 — não
  um fragmento só-núcleo)**: injetor de namespace virtual (`CompilerMakealive`) +
  `makealive-host.kf` + linha no ledger (camada conforme Q1) + os providers
  REST/CLI genéricos (3.5 dobrado aqui) + a face de estado `kof.db` (3.4
  dobrado aqui — Q3: o store é kof.db **desde o primeiro apply**) +
  `MakealiveE2ETest` (golden de idempotência plan/apply/destroy, paridade byte
  JVM==JS, pin de compilação Native, as guardas do §3). Os goldens de kof.db
  rodam onde `kof.db` é real (JVM/JS); o estado no Native espera D-DB-GAPS e
  falha com `DB001`/`ORM001` honestos, nunca silente (R6).
  **POUSOU 20/09 — MK-1 completo:** core `9e8be985`+`f5256f8f` (→ origin
  `3be16f88`), face de estado db `c2850373`, provedor fs `4ee3a5c9`(0.4.0)/`f62206e0`(0.5.0),
  provedores CLI+REST `13b44c6c`, docs stdlib `docs/stdlib/makealive.md` EN+PT; bateria Makealive
  20/20 no tip (paridade byte JVM==JS incl. goldens cross-engine de mundo compartilhado).
  🔵 próximo nesta fila: **3.3 reconcile**.
- **3.3 [reconcile]** ✅ 20/09 — `reconcile(design, provider, intervalMs)` delegando ao
  `scheduler.every` (tick = `apply` dentro de um `spawn`; parar = `scheduler.cancel(jobId)`).
  **Correção medida no pouso:** o stub `CRON001` no Native planejado era DESNECESSÁRIO —
  CRON001 gateia `scheduler.at` (expressão cron); `every` é real em TODOS os alvos desde
  SCHED001 (05/09). A fatia `makealive-recon-host.kf` vai para todo alvo; Native ganha o
  pin de compilação (`MakealiveReconcileE2ETest` 1/1 x3, JVM==JS byte).
- **3.4 [state]** — **dobrado no 3.1 pelo MK-1 (20/09)**; mantido como item
  de verificação: goldens de estado `kof.db` por target (JVM/JS reais; Native
  `DB001`/`ORM001` honestos até D-DB-GAPS fechar).
- **3.5 [providers como interop]** — **dobrado no 3.1 pelo MK-1 (20/09)**:
  provider REST genérico (`kof.http`) + provider CLI (`kof.shell`) embarcam
  na fatia do núcleo; clouds concretas seguem **pacotes oficiais**
  (`infra-<cloud>`, R1 — nunca literal no compilador).
- **3.2 [sintaxe `infra "prod" {}`]** — ✅ **POUSOU 21/09** (`D-MAKEALIVE-SYNTAX`):
  açúcar puro sobre `design()` (`InfraDeclarationNode` + dispatch do parser como
  `test`/`application` + lowering para `design(): Infrastructure`; sem
  keyword/token/tipo/runtime — `LanguageCoreSurfaceTest` 6/6 verde por
  construção). Prova: `InfraSyntaxE2ETest` 2/2 — o bloco e seu gêmeo `design()`
  escrito à mão produzem `plan` byte-idêntico (JVM==JS), Native compila, corpo
  inválido erra nomeado (R6).
- **3.7 [ciclo em compile-time]** — ✅ **DECIDIDO 21/09: runtime-only (FECHADA)** —
  adendo ao `D-MAKEALIVE-SYNTAX`. Com a 3.2 como açúcar puro o compilador só vê
  chamadas genéricas, então um grafo estático daria semântica própria ao bloco
  (§7/regra 11); a recusa em runtime (3.1) nomeia os membros do ciclo — esse é o contrato.
- **3.8 [CLI `kof infra`]** — ✅ **REITERADO 21/09 (`D-MAKEALIVE-SYNTAX`)**: só
  `kof makealive`; `kof infra` não é adicionado. ✅ DECIDIDO + ENTREGUE 20/09 (D-MAKEALIVE-CLI): verbo `makealive` (Q1),
  convenção `design()`+`provider()`, protocolo MARK, estado h2 via `--state`
  (gen=max+1, `mkMaxGen`); recusas honestas script/native (R7).

## 6. Perguntas abertas (decisões da mantenedora — NÃO resolver em código)

**Q1–Q4 RESPONDIDAS 20/09 pela mantenedora (enquete no chat —
`DECISIONS.md` §D-MAKEALIVE; recriar via a mesma decisão multi-escolha se
revisitado — regra 6):**

- **Q1 — RESPONDIDA: `kof.makealive`** (opção A) — o nome É o domínio
  decidido (VISÃO §4.2); passa ileso pelo hard-deny; embarca no padrão
  workflow/shell (namespace virtual + host puro-Kof + linha `platform` no
  ledger). O literal `kof.infra` do tracker segue HARD-DENY (medido, §2.1) —
  a linha do tracker é atualizada, nunca o gate.
- **Q2 — RESPONDIDA: superfície genérica COMPLETA** — local-FS + REST
  (`kof.http`) + CLI (`kof.shell`) embarcam no v1, todos como interop (R9);
  clouds concretas continuam pacotes oficiais (`infra-<cloud>`, R1).
- **Q3 — RESPONDIDA: `kof.db` desde o dia 1** (a opção não recomendada) — o
  estado persiste sobre kof.db; onde o kof.db é gated, o estado é gated junto
  (Native = os gaps honestos `DB001`/`ORM001` até a frente GAPS-DB fechá-los —
  D-KOF-AS-CLOUD torna esse fechamento um item de caminho, não degrade
  permanente).
- **Q4 — RESPONDIDA: host flat injetado + faces em inglês** —
  `resource`/`requires`/`plan`/`apply`/`destroy` confirmados; o golden do 3.1
  congela esses nomes.

## 7. O que NÃO fazer

- **Sem HCL dentro do Kof** — a forma declarativa (3.2), quando vier, desuga
  sobre records; nunca ganha semântica própria.
- **Sem repositório de providers para tudo** — AWS/Azure/GCP são interop
  (REST/CLI) ou pacotes oficiais; o core não conhece cloud nenhuma pelo nome.
- **Sem primitiva nova de compilador/runtime** — cada capacidade rota para um
  namespace existente e herda os gap codes dele.
- **Sem crescimento do core da linguagem** — o golden da linha 8.6
  (`LanguageCoreSurfaceTest`) tem que continuar verde por construção, não
  editando o golden.
- **Sem secret em plaintext no estado** — só referência até o Stage 5 embarcar
  o `Secret`/`KeyHandle` com redação.
