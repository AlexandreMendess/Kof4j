- **Pendentes (condição 3 do gate de release):** nenhum — o Kofmd concluiu 27/09 (`D-KOFMD`) e o plano saiu de `development/` para `docs/` (regra dos 3 estados). O plano em voo com dono ainda solto (`memory-safety-plan`) está no **allowlist** por `D-RELEASE-0.5.0-SCOPE` (21/09) + `D-BAREMETAL-BOOT` (22/09) + o registro de posse da `D-COMPLETE-FIRST`: mantém dono + fila na §1 e não barra o corte 0.5.0. Autoridade: `scripts/check_release_050_gate.sh` (`loose_docs`).
- **Registros vivos aqui (não são backlog):** `DECISIONS.md`, `roadmap.md`, `quality-pipeline.md`. 24/09: os dois PROPOSALs ratificados saíram de `development/`; o ledger `tech-debt` + a ferramenta `debt-scout` foram MORTOS pela mantenedora (dívida zerada). 28/09: o registro de aceitação `release-beta-0.5.0-prep` foi para `docs/distribution/release-beta-0.5.0.md` quando o corte pousou (tags `kof-0.5.0-beta*`); `http-policies-plan` concluiu (F0–F6) e foi movido para `docs/stdlib/`.
- **§1 é a fila; §4.1/§4.2 são TRILHA DE AUDITORIA** (o que já saiu, com prova) — não leia como trabalho. Como agir: §6.

---

## 1. Ordem de execução dos planos (fila oficial da lane development)

> Critério: (1) frente designada pela mantenedora > (2) saúde do gate > (3) trabalho de código-puro sem decisão > (4) itens bloqueados = NÃO atacar (regra 6). Registros vivos (matrizes/auditorias) não têm "fim" — atualizam-se a cada fechamento, não puxam prioridade.

| # | Plano | Estado | Por que nesta posição | Próximo passo concreto |
|---|---|---|---|---|
| 0 | ~~`http-policies-plan.md`~~ — `D-HTTP-POLICIES` 28/09 | ✅ CONCLUÍDO 28/09 — F0–F6 pousadas (`KofHttpPoliciesE2ETest` 10/10); movido para `docs/stdlib/http-policies-plan.md` (regra dos 3 estados) | frente designada por `D-FUTURE-PROMOTION` (mais barata implementável), fechada | — |
| 0 | `scoped-resources-plan.md` — `D-SCOPED-RESOURCES-GO` 28/09, promovido por `D-FUTURE-PROMOTION` | `EM DESENVOLVIMENTO` — fatia 1 LANDED (`using` + `desugarUsing` + `UsingDesugarE2ETest` 7/7); próximo = idioma de aninhamento + E2E `db` + goldens cross | segunda frente, outra lane (issues); arquivos disjuntos de http-policies (parser/desugar vs runtime web) | doc de aninhamento multi-recurso; E2E com `db` em fixture viva; goldens riscv/aarch |
| 0b | `test-architecture-plan.md` — `D-TEST-ARCHITECTURE-GO` 28/09, promovido por `D-FUTURE-PROMOTION` | `EM DESENVOLVIMENTO` — profiling da Fase 1 (read-only `scripts/test-suite-profile.sh` → `docs/testing/TEST-PERFORMANCE.md`); Fase 2+ não iniciada | infraestrutura pura de testes (não toca o compilador), autorizada por `D-TEST-ARCHITECTURE-GO` + `D-FUTURE-BATCH-2809` | rodar o profiler sobre uma execução da suíte; depois quick wins da Fase 2 |
| 0c | `kof-file-plan.md` — `D-KOF-FILE-GO` 28/09, promovido por `D-FUTURE-PROMOTION` | `EM DESENVOLVIMENTO` — re-escopado (Fase 1 = `kof.io`, já existe); **fatia 1 de streaming LANDED** (pure-Kof `libs/file/` sobre `kof.io.readRange`, `FileLibraryE2ETest` 2/2) | library-first (`D-KOF-FIRST-IMPL`), sem mudança no compilador; arquivos disjuntos (libs/file + teste) das outras frentes | medição Native do `FileStream` + código de gap explícito JS/Script; depois Fase 2 dados estruturados |
| 1 | `stdlib/PLAN-TREE-SHAKING.md` (#97) | ✅ CONCLUÍDO 13/09 — S-1..S-6.1 (`0104f6d6`, PR #106) + S-7 (consolidado em `docs/stdlib/stdlib-loading.md`) | frente designada 11/09, fechada | — (S-5-x86 `root_end` na fila bugfix) |
| 2 | ~~`refactoring/PLAN-SOLID-500.md`~~ → `docs/architecture/PLAN-SOLID-500.md` | ✅ FEITO + MOVIDO 13/09 — F1–F9 (F3: NativeBackend 498 ≤500) | ≤500 virou **ratchet de CI** (§140, `2652aa45`); contagem autoritativa = `wc -l scripts/check_500-baseline.txt` | — |
| 3 | ~~`native-multiarch.md`~~ → `docs/native-multiarch.md` | ✅ CONCLUÍDO + PROMOVIDO 19/09 — faces (1)–(5) fechadas; NATIVE002 FECHADO | movido p/ `docs/` | — (recusas por domínio são códigos de gap honestos no `known-bugs.md` + `backend-parity.md`) |
| 4 | ~~`planning-otp-supervision.md`~~ → `docs/planning-otp-supervision.md` (#83) | ✅ CONCLUÍDO 19/09 — 1ª fatia 11/09; S2-JVM 13/09; S2-Native x86 15/09 (§129); S2-JS 18/09 (§132, `OTP002` elevado); riscv64/aarch64 19/09 (`OTP001` removido) | movido p/ `docs/` | — |
| 5 | ~~`plan-editor-integration.md`~~ → `docs/tooling/PLAN-EDITOR-INTEGRATION.md` | ✅ CONCLUÍDO 14/09 — degraus 0–13 (`EditorIntegrationTest` 23/23) | movido para `docs/tooling/` | — |
| 6 | ~~`plan-stdlib-expansion.md`~~ → `docs/stdlib/PLAN-STDLIB-EXPANSION.md` | ✅ CONCLUÍDO 14/09 — S0–S13 nos 5 alvos | movido para `docs/stdlib/` | — (pendências de decisão na §3) |
| 7 | fila de `DECISIONS.md` (13/09) | ✅ §7 FILA VAZIA — time ISO, `CmdNew`, `chacha20`, cookies, `app.security()`, `--fat`, blog E2E, TLS cert, OAuth resource-server todos FEITOS 14/09 | ratificado 13/09; cada linha = unidade-teste-commit | — |
| 8 | **`IMPLEMENTATION-UNIVERSAL-PLATFORM.md`** (+ visão `docs/architecture/UNIVERSAL-PLATFORM-VISION.md`) | `EM CURSO` — promovido de `future/` 17/09 (`D-UNIVERSAL`, R12 sobreposto) | diretriz da mantenedora 17/09: promover e implementar | R1 ✅ `5f1422c6`; R6 ✅ gate `19a740f2`+`c5897cd5`; R5 ✅ 21/09; X8 ✅ 21/09; 1.5 ✅ OTel `435b7013` (Native `OBS003`); 1.2 GC x86 ✅ 19/09 `a904317e` (§260); 1.4 registry ✅ MVP 19/09; 1.1 MEDIA = `MEDIA001/003` na fila atrás das facades HTTP da `.22`. Reivindicar em `DOING.md` antes do código |
| 9 | ~~`workflow-plan.md`~~ + ~~`shell-plan.md`~~ → `docs/workflow-plan.md` / `docs/shell-plan.md` | ✅ CONCLUÍDOS 19/09 — `WorkflowE2ETest` 20/20; `ShellE2ETest` 15/15 | movidos para `docs/` | — (`pipeline` JS com pipes vivos = linha 2.2 do tracker) |
| 10 | `D-WORKFLOW-RUN` (Stage 2 linhas 2.5/2.6) | ✅ ATERROU 19/09 — `CmdWorkflowTest` 9/9; `examples/ci/ci-pipeline.kf` | `DECISIONS.md` §D-WORKFLOW-RUN | — (faces JS/Native do runner = fatias seguintes honestas, R7) |
| 11 | ~~`makealive-plan.md`~~ → `docs/architecture/makealive-plan.md` (`D-MAKEALIVE`) | ✅ CONCLUÍDO + MOVIDO 21/09 — linhas 3.1–3.8 (3.6 secrets `32285136`) | — | — |
| 12 | ~~`secrets-plan.md`~~ → `docs/architecture/secrets-plan.md` (`D-SECRETS`, Estágio 5/3.6) | ✅ CONCLUÍDO + MOVIDO 21/09 — `04473bbe`; `SecretE2ETest` 7/7 + `KeyHandleE2ETest` 5/5 | — | — |
| — | ~~`ffi-abi-structs.md`~~ → `docs/ffi-abi-structs.md` (D6) | ✅ CONCLUÍDO + MOVIDO 23/09 — bateria FFI 60/60 | movido para `docs/` | — |
| — | `memory-safety-investigation.md` — entregável da Fase 0 | `ENTREGUE 25/09 — aguardando revisão da mantenedora` — varredura de 14 pontos (file:line); família §503/§260/§292/§252; 8 pontos frágeis | lane paridade (gate da Fase 0 = revisão da mantenedora) | Fase 1: `docs/spec/memory-safety.md` |
| — | ~~`interop-engine-plan.md`~~ → `docs/interop-engine-plan.md` — `D-COMPLETE-FIRST` item 2 | ✅ CONCLUÍDO + MOVIDO 27/09 — fatias 1–5 (Py+R+timeout+cross+corpus), D-X2-LANDED; estado de sessão corte declarado | movido p/ `docs/` | — |
| — | `memory-safety-plan.md` — `D-MEMORY-SAFETY` 25/09 | `EM DESENVOLVIMENTO` — Fases 0–1 correntes; edições no core esperam a fila; Kof-first, null safety intocável | **lane paridade** | Fase 1 `docs/spec/memory-safety.md`; Fases 2–6 gateadas |
| — | ~~`kofmd-plan.md`~~ → `docs/kofmd-plan.md` — `D-KOFMD` 27/09 | ✅ CONCLUÍDO + MOVIDO 27/09 — fatias 3.1→3.9 (lib pura-Kof `libs/kofmd/`, CLI `kof md`, hook LSP, corpus golden, convenção de migração §5); spec `docs/spec/kofmd.md` | movido p/ `docs/` | — |
| — | ~~`db-parity-plan.md`~~ → `docs/stdlib/db-parity-plan.md` — adendo `D-DB-GAPS` 21/09 | ✅ CONCLUÍDO + MOVIDO 27/09 — S0–S5 (wire cross + faces ORM, PARITY-GAPS 15/16), §488/§493/§523 CORRIGIDAS, D-DB-NORMALIZE + D-DB-ZERODRIVER trilha (a); follow-ups §534/android-pom/S6 declarados | movido p/ `docs/stdlib/` | — |
| — | ~~`codegen-step-2.2.3-assessment.md`~~ → `docs/architecture/codegen-step-2.2.3-assessment.md` | ✅ CONCLUÍDO + MOVIDO 21/09 — opção B `85779f20`; DDL fica no lowering | — | — |
| — | ~~`type-system-extensions-plan.md`~~ → `docs/type-system-extensions-plan.md` | ✅ CONCLUÍDO + MOVIDO 22/09 — X5.0–X5.5 + X6.0–X6.3 (`InteropSchemaE2ETest` 18/18) | movido p/ `docs/` | — |
| — | ~~`kof-c-cross.md`~~ → `docs/kof-c-cross.md` | ✅ CONCLUÍDO + MOVIDO 23/09 — C1–C4 + C3-residual (14/14 + 5/5 sob qemu) | movido p/ `docs/` | — |
| — | ~~`PLAN-BAREMETAL-BOOT.md`~~ → `docs/PLAN-BAREMETAL-BOOT.md` | ✅ CONCLUÍDO + MOVIDO 25/09 — B-0..B-3+B-6 (`BiosBootE2ETest` 5/0F; prova ring0/ring1 `#GP`+GDT); B-4 MCU riscv32 | movido p/ `docs/` | — (espelho Cortex-M3 + integração no emissor rastreados no `roadmap.md` §23) |
| — | registros vivos: `conformance-matrix.md`, `ecosystem-coverage.md`, `KOFUI-AUDIT.md`, `known-bugs.md` (em `docs/bugs-and-gaps/`); `roadmap.md` (aqui); `roadmap-audit.md`/`complexity-audit.md` (em `docs/audits/`) | `VIVA` | **não são backlog** — atualizam-se a cada fechamento | atualizar célula/seção no MESMO commit que fecha o gap |

**Regra R12 (AGENTS.md):** nada de `future/` (RAII, package-compiler, bare-metal) abre antes de SYSTEMS fechar (paridade + GC + estabilidade). **Exceção (`D-UNIVERSAL`, 17/09):** `IMPLEMENTATION-UNIVERSAL-PLATFORM.md` promovido a trabalho corrente com o portão R12 sobreposto — seu ponto de entrada é o Estágio 1 (consolidação SYSTEMS) + R1–R12.

---

## 2. Bugs abertos (fila em `docs/bugs-and-gaps/known-bugs.md`)

Autoridade = `scripts/check_known_bugs_status.sh` (EN×PT consistentes), nunca um número escrito à mão. **2 itens na fila aberta.** O histórico da contagem vive no ledger + git log, não aqui. Gates que guardam este registro: `check_changelog_ledger.sh` (cada `§NNN ✅ FIXED` vs a fila viva; isenção só por linha nomeada em `scripts/changelog-ledger-waivers.txt`), `check_ledger_anchors.sh` (âncoras pt/en chegam; `--selftest` guarda), `check_live_records.sh` (esta contagem == autoridade; §0 pendentes == loose do gate). **Gate candidato (registrado 28/09, NÃO implementado — #665):** todo `D-*` citado em `CHANGELOG.md`/`.pt_BR.md` deveria existir em `DECISIONS.md`/`.pt_BR.md`. Medido 28/09: 70 tokens `D-*` distintos no `CHANGELOG.md`, **6 sem entrada em `DECISIONS.md`** (`D-001-STRESS`, `D-NULL-QUEUE`, `D-OTP-08`, `D-STDLIB-01`, `D-STDLIB-02`, `D-STDLIB-ULID` — casa normativa em outro lugar/histórico), então o gate exige antes uma lista de isenção com escopo (mantenedora). Motivado pela janela de false-green transitória do #660 (ver errata no DOING 28/09).

---

## 3. Decisões da mantenedora (registro: `DECISIONS.md`)

> Nada aqui está "parado esperando" — as frentes que esperavam decisão foram **ratificadas 13/09** e vivem em `DECISIONS.md` (D-STDLIB/D-SEC/D-APP/D-SPRING/D-PLAT/D-PLATFORM) com a fila de execução aberta. Regra: **frente sem linha em `DECISIONS.md` não é atacada** (regra 6); decisão do chat trava lá no mesmo commit.

| Item | Onde | O que espera |
|---|---|---|
| DD-STDLIB-01 — `randomBytes`/`randomChoice` (S10c) | `docs/stdlib/DD-STDLIB-01-array-returns.md` (FECHADO 13/09) | ✅ IMPLEMENTADO 13/09 (opção 6a) |
| DD-STDLIB-02 — `time.format`/`boundaries` | `DECISIONS.md` §D-STDLIB | ✅ RATIFICADO 13/09 (UTC-only, escalares ISO) — fila liberada |
| DD-01 — `finally` no caminho de `return` | `docs/decisions/DD-01-finally-return.md` (FECHADO 13/09) | ✅ IMPLEMENTADO 13/09 (FinallyFrame IR; bug 45 FECHADO; suíte 1627/0) |
| DD-OTP | `docs/planning-otp-supervision.md` (FECHADO 19/09) | ✅ RATIFICADAS 13/09 — S2 JVM/x86/JS/riscv64/aarch64 todos landados |
| `pow`/`-lm`, `roundTo` | `docs/stdlib/PLAN-STDLIB-EXPANSION.md` | ✅ `pow` FEITO 13/09 (5 alvos); `roundTo` FEITO 14/09 (half-away-from-zero, sem libm) |
| NAT-STR01 (case-map astral) | `known-bugs.md` §161 | ✅ ABERTO POR DECISÃO 13/09 — UTF-8 astral nos nativos |
| §129 (unwind cross-thread via TLS) | `known-bugs.md` | ✅ CORRIGIDO 15/09 (chain TLS por thread; riscv/aarch `OTP001`) |
| json §106 | `known-bugs.md` | ✅ CORRIGIDO 13/09 (chaves sorted; residual JS `ab85cfae`) |

---

## 4. Índice do que está EM DESENVOLVIMENTO aqui

### 4.1 Plataformas & migração (caíram de `future/` 12/09 — código iniciado; os `~~riscados~~` foram ratificados 13/09 e consolidados em `DECISIONS.md` — os 6 arquivos de `decision-pending/` foram apagados)

| Arquivo | Estado real | O que falta p/ fechar |
|---|---|---|
| ~~`PLATFORM-PLAN.md`~~ → `DECISIONS.md` §D-PLATFORM (morto) | F1–3/8/9 com código | F1 pelo manifesto; F4/F5→KOFUI-AUDIT/stdlib-web; F6 wasm/F7 android→D-APP Q7/Q10; F9→conformance-matrix |
| ~~`APPLICATION_MODEL.md`~~ → `DECISIONS.md` §D-APP (Q1–Q10 travados) | `application { onStart/onShutdown }` ✅; I2 ✅ `FullStackE2ETest` | `CmdNew` (I1), I3 (`--fat`), System/distribuído — fila |
| ~~`LEGACY_MIGRATION.md` + `DECOMPILER.md` + `TRANSLATOR.md`~~ → **`future/` (DESPRIORIZADO 15/09)** | código fica: `inspect/decompile/translate/compare/migrate`; **não é trabalho atual — promoção exige decisão explícita**; contagem viva = `roadmap.md` §23 TIER 3–5 | cobertura: switch/athrow opacos, `inspect --java`, IR non-JVM |
| ~~`IMPLEMENTATION_PLAN.md` / `ACTION_PLAN.md`~~ → `roadmap.md` §23 | **FUNDIDOS 13/09** | §23 é o plano único; tiers 6–12 = `IMPLEMENTATION-UNIVERSAL-PLATFORM.md` |
| ~~`PLANNING-FUTURE-AUDIT.md` / `planning-future-reconcile.md`~~ → `docs/audits/` | comparação branch encerrada 13/09 | — (fora de `development/`) |
| ~~`planning-finally-return.md`~~ → `docs/decisions/DD-01-finally-return.md` | FECHADO 13/09 | — |
| ~~`planning-stdlib-time-design.md`~~ → `DECISIONS.md` §D-STDLIB | `addDays`/`diffDays` nos 5 alvos | ✅ RATIFICADO 13/09 — fila liberada |

### 4.2 Plans & auditorias vivas

| Arquivo | Estado real | Nota |
|---|---|---|
| ~~`PLAN-TREE-SHAKING.md`~~ → `docs/stdlib/PLAN-TREE-SHAKING.md` | ✅ CONCLUÍDO 13/09 (consolidado em `docs/stdlib/stdlib-loading.md`) | S-5-x86 = fila bugfix |
| `docs/stdlib/PLAN-STDLIB-EXPANSION.md` | S0–S6, S8–S12 ✅ | só decisões pendentes (§3) |
| ~~`planning-otp-supervision.md`~~ → `docs/planning-otp-supervision.md` | ✅ CONCLUÍDO 19/09 | movido p/ `docs/` |
| `docs/tooling/PLAN-EDITOR-INTEGRATION.md` | CLI/DAP/LSP/stdout-json ✅ | plugin IntelliJ |
| ~~`native-multiarch.md`~~ → `docs/native-multiarch.md` | ✅ PROMOVIDO 19/09 (NATIVE002 FECHADO) | recusas em known-bugs/backend-parity |
| ~~`type-system-extensions-plan.md`~~ → `docs/type-system-extensions-plan.md` | ✅ CONCLUÍDO + MOVIDO 22/09 | movido p/ `docs/` |
| ~~`security-plan.md`~~ → `DECISIONS.md` §D-SEC | A ✅; B/C ✅; C11/C18/D16/D17 ratificados 13/09 | ChaCha20 = fila; OAuth provider = NUNCA |
| ~~`plan-platform-completion.md`~~ → `DECISIONS.md` §D-PLAT (morto) | P0–P3 ✅; P4 ❌; P5 `kof fmt` ✅ | P4/P5 na §23/backend-parity |
| ~~`plan-spring-independence.md`~~ → `DECISIONS.md` §D-SPRING | F1–F12 ✅; `CONCLUÍDA` 19/09 | sem frente aberta |
| ~~`conformance-matrix.md`~~ → `docs/bugs-and-gaps/` | matriz travada por `ConformanceMatrixTest` (11) | viva |
| ~~`ecosystem-coverage.md`~~ → `docs/bugs-and-gaps/` | G1–G12 com `PARTIAL`/`PLANNED` | referência de cobertura |
| `roadmap.md` | §§8–11 ❌ (frontend same-project, monólito→micro) | longo prazo |
| ~~`roadmap-audit.md`~~ → `docs/audits/roadmap-audit.md` | matriz 06/09 + fila P0→P5 | re-audit a cada fechamento |
| ~~`KOFUI-AUDIT.md`~~ → `docs/bugs-and-gaps/` | UI001-Native (no-op silencioso) ABERTO | lane UI |
| ~~`known-bugs.md`~~ → `docs/bugs-and-gaps/` | **2 vivos** (autoridade = `scripts/check_known_bugs_status.sh`) | fila viva |
| ~~`refactoring/PLAN-SOLID-500.md`~~ → `docs/architecture/PLAN-SOLID-500.md` | ✅ FEITO + MOVIDO 13/09 (F1–F9) | ratchet `check_500-baseline.txt` no CI |
| `IMPLEMENTATION-UNIVERSAL-PLATFORM.md` (→ `docs/architecture/`, **CONCLUÍDO** — Estágios 1–3 + R + Estágio 8; fases futuras em `development/future/`) | promovido 17/09 (`D-UNIVERSAL`, R12 sobreposto) | arquitetura dos Tiers 6–12 |
| `docs/PROPOSAL-1.0-EXIT-GATE.md` (+PT; saiu de `development/` 24/09) | **KOF 1.0 EXIT GATE — RATIFICADO 20/09** (`DECISIONS.md` §D-RELEASE-1.0`) | ordem = o §23 do próprio PROPOSAL, rastreado no `roadmap.md` §24 (EG-1..EG-10); todas as arestas `[? MEL]` FECHADAS 20/09 (`D-1.0-EDGES`); resta só EG-8 (abertura do RC) |

### 4.3 `future/` — só plano, zero código (não é trabalho atual)

> O índice completo e autoritativo desta pasta (todo plano + seu gatilho) é `future/README.md`.

| Arquivo | Gatilho p/ cair p/ cá |
|---|---|
| `PLAN-MULTIPARADIGMA.md` (multiparadigma / pipelines funcionais; 16/09, só design) | primeiro incremento funcional (SYSTEMS fechado, R12) |
| `scoped-resources-plan.md` (RAII TIER 2.4) | bump com `using`/`resource_scope` decidido |
| ~~`PLAN-BAREMETAL-BOOT.md`~~ → promovido 22/09, CONCLUÍDO + movido para `docs/PLAN-BAREMETAL-BOOT.md` 25/09 | FECHADO 25/09 (`D-BAREMETAL-BOOT`; `D-BAREMETAL-MCU-GC` fechou B-4 no riscv32) |
| `PLAN-BOOTSTRAP.md` (o Bootstrapper: Kof em Kof — **estrela-guia**, `D-BOOTSTRAP`) | EXIT GATE 1.0 fechado + condições de entrada E1–E6 |
| `DECOMPILER.md`, `TRANSLATOR.md`, `LEGACY_MIGRATION.md` (migração de legado) | de volta p/ cá 15/09 — DESPRIORIZADO; promoção exige decisão explícita |

*(DD-STDLIB-01 `planning-stdlib-array-returns.md` saiu de `future/` 13/09 → `docs/stdlib/DD-STDLIB-01-array-returns.md`. Movimentos históricos de 12/09: 13 docs caíram de `future/` p/ cá; snapshot SG 08/09 → `docs/history/`.)*

---

## 5. O que NÃO está mais aqui (consolidado 12/09, com prova)

| Saiu p/ | Doc | Prova |
|---|---|---|
| `docs/bugs-and-gaps/specification-gaps.md` | SG-001–023 + E1–E3 | fila do maintainer COMPLETA; snapshot antigo → `docs/history/specification-gaps-0.3.0-snapshot.md` |
| `docs/stdlib/DATABASE_VISION.md` | níveis 0–4 | query DSL (`KofOrmE2ETest` 32; paridade JS 18/09), MySQL prepared, pooling ✅; DB001/DB002/ORM001 vivem na matriz de paridade |
| `docs/audits/complexity-audit.md` | snapshot 02/09 | gate vivo = `scripts/check_500.sh` |
| `docs/history/roadmap-gap-2026-09-03.md` | gap report datado | pendências em roadmap-audit/known-bugs |
| `docs/decisions/` | `planning-switch-expr`, `planning-mutability` | SYN001, DD-02/SEM037/SEM038 aplicados |
| `docs/ui/PLAN-CANVAS-WIDGET.md` | CANVAS001 | `UiE2ETest` 29/29 |

---

## 6. Como usar (agente autônomo)

```
1. LEIA docs/status.md + docs/backend-parity.md            → o que funciona (gate)
2. LEIA a fila §1 deste README + DOING.md (donos)          → o que falta, sem colisão
3. BUGS: known-bugs.md §Aberto só com dono; decisão → §3, não editar
4. EXECUTE um escopo → teste (suíte com -Dmaven.test.failure.ignore=true)
   → commit com DOING.md atualizado → mova doc p/ docs/ se FECHOU
5. RE-DISPARO: sem item na fila §1 sem dono E suíte verde → RECUSE (condição de estabilidade AGENTS.md)
```

**Sincronização:** `git fetch && git pull --rebase --autostash` antes de TODO commit; releia este README depois do pull (outro agente pode ter fechado um item da fila). `DOING.md` marca dono/estado; este README é a **fila**.

**Não confundir:** `training/` + `learn/` + `docs/` = corpus estável. `development/` = trabalho que ainda não é comportamento previsto. Mudança de contrato congelado nunca passa por aqui sem bump + decisão (regra 6).
