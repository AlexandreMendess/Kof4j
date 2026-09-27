[English](README.md) | [Português](README.pt_BR.md)

# KOF Technical Debt Scout

**Status:** EM DESENVOLVIMENTO — Wave 1 (determinístico, somente shadow).
Ver `DOING.md` para o claim/dono vivo e o próximo passo.

Esta é uma ferramenta que encontra e documenta dívida técnica
*histórica* antes que uma escolha temporária de implementação, uma
divergência de target ou uma suposição obsoleta virem, silenciosamente,
compatibilidade que não pode mais ser mudada. Ela não corrige nada e não
decide contrato de linguagem — ela produz evidência para um humano (hoje:
para `docs/development/tech-debt.md`, o ledger mantido pela mantenedora,
aberto em 23/09/2026).

## Ordem de leitura

1. `DEBT_SCOUT_CONTRACT.md` (EN) — o contrato operacional: definições,
   hard stops, o gate KOF-first, escala de confiança, fingerprints,
   roteamento de publicação/trust rollout, regras de privilégio. É isso
   que o código em `scripts/debt-scout/` implementa.
2. `TAXONOMY.md` (EN) — a classificação de três eixos que todo candidato usa.
3. `docs/development/DECISIONS.md` §`D-DEBT-SCOUT` — o registro de decisão
   que autorizou esta frente e o limite de escopo atual (ainda não existe
   capacidade de publicar Issue).

## De onde veio o desenho

Dois documentos de pesquisa foram fornecidos pelo usuário na sessão que
abriu esta frente (22–23/09/2026): uma proposta inicial e uma V2
revisada por pesquisa que a substitui estruturalmente (candidato ≠
dívida confirmada; `C2` nunca abre Issue automática; prioridade é vetor,
nunca um score único; reusar `scripts/agent-*.sh` em vez de um sistema de
governança paralelo). Esses documentos-fonte têm 100+ seções cada e
**não** são copiados para o repositório — `DEBT_SCOUT_CONTRACT.md` é a
destilação condensada e sincronizada com o código que os scripts de fato
seguem. Quando os dois divergem, o contrato desta pasta vence.

## O que existe hoje (Wave 1)

```text
scripts/debt-scout/
├── config.py            — carrega/valida .debt-scout.yml (só stdlib)
├── schema.py             — schema v2 do Candidate (validação)
├── fingerprint.py        — fingerprints de finding/dívida (estável, sha256)
├── branch_discovery.py   — resolve branch default/ativa; sinaliza drift
│                            de contrato em vez de hardcodar uma ref
├── detectors/
│   └── satd.py            — detector de marcadores SATD (TODO/FIXME/HACK/…)
└── scan.py               — CLI orquestrador (--phase state|deterministic)
```

Todo módulo tem `--selftest` e/ou um teste
`scripts/tests/debt-scout-*.sh`. **Nenhum script chama a API de escrita
de Issues do GitHub.** Nenhum workflow em `.github/workflows/` concede
`issues: write` a este sistema.

## O que ainda NÃO existe (não assumir que roda)

Upload de SARIF, o Debt Inbox, o publisher de C3, qualquer etapa de
qualificação com LLM, o experimento de corpus estilo Crater e as
superfícies de CLI `kof debt`/`kof fix`. Cada um é uma unidade futura
separada e com escopo explícito — ver `DEBT_SCOUT_CONTRACT.md` §11 e
`DOING.md`.
