[English](release-beta-0.5.0-prep.md) | [Português](release-beta-0.5.0-prep.pt_BR.md)

# Release 0.5.0 — preparo (branch `beta-0.5.0`)

Decisão: `DECISIONS.md` §D-BRANCH-0.5.0 (20/09). Branch ativa `beta-0.5.0`; `beta-0.4.0` só recebe pousos em voo + preparo de release. Fica em `docs/development/` até o corte (regra dos três estados).

## Checklist (ordenado — número da versão e tag são decisão da mantenedora, regra 6)

1. [x] Pousos em voo: §374/#553, §371/#550, §378/#554 — **TODOS OS TRÊS POUSARAM 20/09 ✅ FIXED** (`BareCollectionFieldE2ETest` 8/8; `ShippedCliCrossSmokeTest` 2/2 + `RuntimeSourceLoaderTest` 6/6; §378 cruzamento EN×PT do conjunto aberto). A lane docs faz ff da `beta-0.5.0` após cada pouso na 0.4.0.
2. [ ] Dívida CodeQL (#555): **TRIAGEM FECHADA 20/09 (§385)** — 40 na janela: 13 fixados com teste alvo, 26 descartados com motivo (25 harness `src/test` + FP JEP 443 #876), #938 pela tooling aguardando re-scan do `main`. Portão por BASELINE (`scripts/codeql-baseline.txt`: só alerta NOVO bloqueia; `CODEQL_GATE_SKIP` exige motivo, imprime banner, loga em `.git/codeql-gate-skips.log`, ignorado em CI); `scripts/codeql-gate.sh --fast` VERDE rc=0. Para marcar [x]: ff da `q555` + primeiro re-scan podando os 14 ids tolerados; #563 segue na fila própria.
3. [ ] Versão: **DECIDIDO 20/09 — sai como `0.5.0-beta`** (adendo `D-RELEASE-0.5.0-GATE`); `VERSION`/`pom.xml` já em `0.5.0-beta`; restam só CHANGELOG/tag. Auditado 21/09: limpo (hits `0.4.0` restantes são comentários de procedência / `beta-0.4.0` como nome de branch, nenhum codifica a versão do artefato).
4. [~] Corte do CHANGELOG (EN+PT): seção `0.5.0` + `Version:` do `AGENTS.md`(+PT) no mesmo commit. **DRAFT LANDADO 22/09:** cabeçalho `## [0.5.0-beta] - unreleased (branch beta-0.5.0)`. O CORTE em si (datar + tag) segue esperando as sete condições.
5. [ ] Tally: `'Current build: **N**'` em `docs/backend-parity.md`(+PT) a partir da primeira CI Build+Tests hospedada VERDE no tip da release (contagem do log do job, nunca memória).
6. [ ] Prova de estabilidade: suíte completa 0F/0E + matriz de conformidade 5/5 MEDIDOS no candidato à tag (AGENTS §Estabilidade — tag só com verde).
7. [ ] Tag + release notes (EN+PT); declarar `beta-0.4.0` fechada exceto a lista de fixes residuais. **O `main` fica congelado até este release** (20/09): os 12 alertas CodeQL pré-fix do `main` são portados no dia do release, não antes; o gate mede a `beta-0.5.0`.

## Issues abertas que viajam para `beta-0.5.0`

#555 (guarda-chuva CodeQL — o único ainda aberto; #550/#553/#554 pousaram 20/09). Avisadas em cada issue e pelo banner no `DOING.md`(+PT).

## Gate de release (`D-RELEASE-0.5.0-GATE`, 20/09, diretiva da mantenedora)

Corte só quando **todas as sete condições** valerem, cada uma **medida** (nunca a olho). O checklist é a fila tática; estas sete são a aceitação.

| # | Condição | Como é medida | Estado (medido — nunca a olho) |
|---|---|---|---|
| 1 | Paridade 100% entre os alvos | matriz por alvo + paridade byte dos goldens onde o contrato exige; divergência = bug ou gap `XXX00x`. Medida automaticamente pelo `check_release_050_gate.sh` (`target-matrix.sh` → `PARITY: 100%`) | GREEN (21/09 `29198ea8`: `PARITY: 100%` jvm/x86-64/riscv64/aarch64/JS/Script vs o oráculo JVM; jar reconstruído+estampado por `build-kof-jar.sh`; toolchain cross rootless via `setup-cross-toolchain.sh`; kofc=EG-9, android=EG-10 delegados) |
| 2 | Nenhuma decisão pendente | `DECISIONS.md` sem pergunta aberta que mude a superfície | NEEDS-REVIEW (**não RED**) — **2** frentes aprovadas com `State: OPEN` (`D-TYPE-VARIANCE`/X5, `D-INTEROP-REFLECT`/X6); `D-SECRETS` agora `DECIDED` (pousou 21/09 `04473bbe`); frente aprovada ABERTA não bloqueia o corte |
| 3 | Todos os `docs/development/*.md` soltos concluídos e movidos | regra dos três estados | **GREEN (21/09, `D-RELEASE-0.5.0-SCOPE`)** — planos em voo com dono entram no allowlist e não barram o corte (`ffi-abi-structs` [jonas], `db-parity-plan` [gaps-db]); `makealive-plan`/`secrets-plan`/`IMPLEMENTATION-UNIVERSAL-PLATFORM` movidos 21/09; `type-system-extensions-plan` movido 22/09 |
| 4 | Estabilidade total | suíte completa 0F/0E + matriz 5/5 na candidata; auto-medida pelo `stability-report.sh` a partir de log real **estampado** (`SUITE-SHA`==tip, `SUITE-DIRTY=0`) | GREEN (21/09 `29198ea8`: `TOTAL: tests=3479 failures=0 errors=0 skipped=13`, `SUITE-SHA==tip`, `stability-report.sh` GREEN). Reds stale anteriores (§422 `96af9b63`, ratchets JS-FFI em `§431`) resolvidos; a candidata é re-medida no tip final limpo na hora do corte |
| 5 | 0 issues abertas que sejam bug | issues OPEN do GitHub com label `bug` = 0; lê o **rc da consulta** (API fora = `UNKNOWN`) | GREEN (0 issues de bug abertas; `scripts/fetch-open-issues.sh` fornece `R050_OPEN_ISSUES_TSV` sem `gh`; a #580 é documentation/enhancement) |
| 6 | Todas as arestas fechadas | EG-1..EG-7 fechadas + `1.0-blocks` abertos = 0; **EG-8 desacoplado** (`D-RELEASE-0.5.0-SCOPE`) | **GREEN (21/09, `R050_OPEN_BLOCKS=0` via `gh` autenticado: só 2 PRs do dependabot, 0 issues)**; tabela EG ilegível = `UNKNOWN`, nunca GREEN |
| 7 | Nada pendente em bugs-and-gaps | conjunto live do `check_known_bugs_status.sh` vazio + `specification-gaps.md` 0 abertos | RED — 4 live no tip (autoridade = `scripts/check_known_bugs_status.sh`; entradas em `docs/bugs-and-gaps/known-bugs.md`) |
| 8 | **Paridade total da plataforma (IMPEDITIVA, `D-FULL-PARITY-050` 24/09)** | `docs/development/parity/PARITY-GAPS.pt_BR.md`(+EN) **0 linhas abertas**; verificação por máquina `check_release_050_gate.sh` → `full_parity` (ausente/ilegível = UNKNOWN) | RED (24/09) — 16 linhas abertas: `PROC001`; ssh; `MEDIA001/003`; `MQ001`; `GPU001`; `OBS003`; `TIME002/004`; `CONF001`; `MATH001`; `NAT-STR01`/`STR003`; `WEB00x`; `NAT006/007`; `SECN001/003/004/005`; `ORM001`; `DB001`. Golden não medido = ABERTO (Q5) |

Mecanizado por `scripts/check_release_050_gate.sh` (GREEN/RED/NEEDS-MEASURE/UNKNOWN por condição; RED-first `scripts/tests/check-release-050-gate-test.sh`). Toda condição data-driven **recusa GREEN quando a fonte não é legível** — jar velho, log de outro commit/árvore suja, consulta ao GitHub que falha, tabela EG impossível de parsear, ledger ilegível: inconclusiva, nunca verde falso. RED é esperado até a fila fechar.

### Recuperação — limpar as condições auto-medidas

```bash
eval "$(scripts/setup-cross-toolchain.sh --export)"     # cond. 1: binutils/qemu/libc cross (host sem root; uma vez)
scripts/build-kof-jar.sh                                # cond. 1: rebuilda + estampa o jar da árvore (após o último commit do compiler)
scripts/target-matrix.sh                                #          -> PARITY: 100% (6 alvos core)
scripts/fetch-open-issues.sh > /tmp/open-issues.tsv     # cond. 5: quando o `gh` não existe (API pública)
SAFE_SUITE_LOG="$PWD/.suite.log" scripts/safe-suite.sh  # cond. 4: rodar em árvore LIMPA
R050_OPEN_ISSUES_TSV=/tmp/open-issues.tsv \
KOF_SUITE_LOG="$PWD/.suite.log" scripts/check_release_050_gate.sh
```
