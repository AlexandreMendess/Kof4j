[English](quality-pipeline.md) | [Português](quality-pipeline.pt_BR.md)

# Esteira de qualidade — política de branches executável

last: automation-enforcement — o auto-cascade por commit POUSADO (auto-promote.yml + auto_cascade.sh, 11/10)
doing: 14.4-rulesets+dispatch-on-main (mantenedora)
next: observação viva da primeira semana do auto-cascade; o caminho canônico com janela segue no promote.yml (mantenedora)
location: docs/development
state: active

intent: executable-branch-quality-policy

constraint:

* lab é o único ponto de entrada de desenvolvimento
* toda promoção é explícita, unidirecional e auditável
* nenhum push direto para um estágio posterior
* qualquer falha retorna para lab
* falha fechado quando o estado não pode ser determinado
* automação determinística e idempotente

## Esteira

`lab → testing → prerelease → stable → release/x.y.z → tag`

| Estágio | Papel | Gate para sair | Protegida |
|---|---|---|---|
| `lab` | desenvolvimento + correções | todos os checks obrigatórios PASS | não |
| `testing` | integração/QA | todos os checks PASS, 0 issue bloqueante | sim |
| `prerelease` | candidato público | janela de 7 dias, 0 issue relacionada, checks PASS | sim |
| `stable` | contrato fechado | todos os checks obrigatórios PASS | sim |
| `release/x.y.z` | apenas empacotamento | todos os checks PASS, versão ainda não tagueada | sim |
| tag `kof-*` | contrato público | — | imutável |

O denominador `≥80%` foi **dropado** (`D-QUALITY-PIPELINE-2609`, mantenedora
28/09/2026): toda promoção é 100%.

## Estados

`LAB → TESTING → PRERELEASE → PRERELEASE_OBSERVATION → STABLE → RELEASE → TAGGED`

Falha nunca abre fluxo paralelo: `TESTING|PRERELEASE|STABLE|RELEASE → BLOCKED → LAB`.

A máquina é código, não convenção: `scripts/pipeline/pipeline_state.py`. Transição
fora de `ALLOWED`, branch desconhecida ou no-op é **recusada** (fail closed).
`lab→prerelease`, `lab→stable`, `testing→stable`, `prerelease→release`, `lab→release`
e `stable→prerelease` são bloqueadas por teste.

## Promotion Gate

`scripts/pipeline/promotion_gate.py` avalia um passo: reusa a máquina de estados e
exige 100% dos checks obrigatórios fixos (Build + Tests, Native cross, kof.io
multiplatform, Structural quality gates, CodeQL Gate, bots), a janela de observação
completa com 0 issues relacionadas (`prerelease→stable`) e criação de tag idempotente.
É função pura dos inputs — determinística, sem writes, sem rede — portanto segura
para reexecutar.

Campos do relatório: estágio/estado de/para, status, resultado por check, commit,
versão, timestamp, issues bloqueantes/relacionadas, motivos e a ação
(`return_to: lab`) em caso de falha.

## Enforcement

* Rulesets (servidor): `pipeline-stages` bloqueia push direto, force-push e remoção
  em `main`/`testing`/`prerelease`/`stable`/`release/*`, e exige PR + status checks;
  `release-tags` torna as tags `kof-*` imutáveis. `lab` é aberta.
* Workflow `.github/workflows/promote.yml` (manual, idempotente): valida a transição,
  roda a SUITE COMPLETA no tip exato despachado como primeiro gate formal (o `lab`
  não tem CI por push por design — a suite é a medição, nunca um check-run velho),
  mescla esse veredito com os check-runs do tip e as contagens MEDIDAS de issues
  (`promotion_evidence.py`: bloqueantes = issues `bug` abertas; relacionadas =
  issues atualizadas desde `promoted-at`), roda o gate e abre um PR de promoção
  auditável apenas quando PASSA. Nunca faz push em estágio, e as contagens nunca
  têm default — omiti-las é recusado (opinião é proibida).
* Concorrência agrupada por estágio de destino; execuções duplicadas são no-op.

## O auto-cascade por commit (automation-enforcement, 11/10)

Ordem viva da mantenedora: *"era pra cada commit na lab avaliar se iria ter
promoção automatica e merge com as proximas"*. Workflow
`.github/workflows/auto-promote.yml` (em todo push na `lab`;
`scripts/pipeline/auto_cascade.sh` é o motor):

1. **avaliar** — gates baratos e fail-closed no tip exato: os cinco passos
   estruturais do CI verbatim (version-sources + `bump-version.sh`,
   `check_500`, fronteira stdlib, catálogo de assinaturas, testes de agentes),
   o rc-gate de release, 0 issue `bug` aberta e divergência (igualdade de
   árvore por salto). Qualquer VERMELHO = skip honesto; o corte escorrega
   (`D-LAB-STABILITY`).
2. **suíte** — `safe-suite.sh` no tip exato, certificado pelo
   `stability-report.sh` (`STABILITY: GREEN`, com SHA). A `lab` não tem CI
   pesado por push por design, então esta corrida É a medição; sem maquinaria
   de retry — suíte vermelha nunca cascateia.
3. **cascata** — o fluxo que a mantenedora demonstrou à mão duas vezes em
   11/10 (PRs #799–#801 e #802–#804): `lab→testing→stable→prerelease`, um PR
   de promoção idempotente por salto (guarda de igualdade de árvore,
   fail-closed), cada merge só depois do veredito medido. Esta ordem
   comprimida é decisão registrada da mantenedora em 11/10 — o caminho
   canônico com janela da máquina de estados
   (`lab→testing→prerelease→stable`) segue disponível pelo `promote.yml`
   para um corte conduzido pela mantenedora com a janela de 7 dias.
4. **publicar** — `release.yml` despachado na `prerelease` SOMENTE quando
   `kof-${VERSION}*` não tem tags ainda; versão já publicada espera o próximo
   bump (skip honesto, registrado no log da corrida).

A `main` NUNCA é tocada pela automação: o ruleset `main-gate` (11/10) carrega
as proteções da main inalteradas — PR + os dois checks obrigatórios + 1 review
— sem afrouxamento nenhum, então o release final sem data segue sendo o merge
+ dispatch da mantenedora. O `pipeline-stages` agora cobre
`testing/prerelease/stable/release/*` com os dois status checks ainda
OBRIGATÓRIOS: a automação os publica com o veredito medido e a proveniência
(direto no tip medido; merge commits tree-idênticos carregam a nota), e a
exigência de review humano nunca usada foi derrubada lá (todo merge de estágio
até hoje foi bypass `--admin` humano; a exigência de review nunca foi
satisfeita normalmente). Os escopos de escrita do bot são por job e
justificados em `scripts/workflow-permissions.txt`.

## Issue linkage

O bloqueio é determinístico: a promoção é recusada quando a contagem de issues
bloqueantes (`testing→prerelease`) ou de issues relacionadas na janela
(`prerelease→stable`) é não-zero. A associação é por versão/commit/tag, nunca por
heurística de texto livre.

## Cadência de release e estabilidade do lab

O plano de releases pré-1.0 é uma **cadência semanal de minors até `0.9.0`**
(`D-RELEASE-CADENCE`, mantenedora 02/10): um minor por fim de semana, cada um
cortado por esta esteira a partir de um `lab` ESTÁVEL. Se a `0.9.0` for atingida
sem o exit gate completo da 1.0 verde, a linha continua como `1.0.0-RC-1`,
`1.0.0-RC-2`, … — cada RC tratado como um minor (seu próprio corte de fim de
semana por esta esteira) até estabilizar, então a `1.0.0` de verdade. O número da
versão no corte é chamada da mantenedora.

**`D-LAB-STABILITY` (mantenedora 02/10):** um minor só entra em `lab → testing` a
partir de um `lab` ESTÁVEL — suíte completa verde, gates estruturais/de qualidade
rc=0, 0 bloqueantes em `scripts/release-blockers.tsv`, nenhum `1.0-blocks` aberto.
Senão o corte **escorrega** para o próximo fim de semana; nunca é forçado com
waiver e nenhum estágio é pulado. O Promotion Gate abaixo é a forma executável
desta regra.

## Política de correção

> Encontrou problema? Volta para `lab`.

Correção nunca é feita em `testing`, `prerelease`, `stable` ou `release/x.y.z` para
depois seguir de lado. Nasce em `lab` e percorre toda a esteira; uma nova pre-release
inicia sua própria janela de observação.

## Contrato

`D-QUALITY-PIPELINE-2609` / `D-BRANCH-PIPELINE`, `D-RELEASE-CADENCE`,
`D-LAB-STABILITY` (`docs/development/DECISIONS.md`).
Anúncio do cutover e instruções de migração: issue #647.
