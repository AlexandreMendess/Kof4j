[English](supply-chain-trust-boundary-2026-09-20.md) | [Português](supply-chain-trust-boundary-2026-09-20.pt_BR.md)

# Fronteira de confiança da cadeia de suprimentos do KOF v1 — baseline (20/09/2026)

> **Medição somente leitura.** Nenhum workflow, script ou arquivo de produção foi alterado para
> produzir este documento, e **nenhuma decisão é tomada aqui**: ele mapeia o que é verdade hoje
> para que a mantenedora responda às perguntas de contrato da §5. Fontes: arquivos do repositório
> no tip, a API do GitHub (somente leitura, como `jonasrochasilva-prog`) e o contrato ratificado
> (`D-RELEASE-1.0`, `D-1.0-EDGES`, `PROPOSAL-1.0-EXIT-GATE` §32.6/§32.7). Os fatos são
> re-verificáveis com os comandos indicados em cada linha.

## 1. O que o contrato já diz (ratificado) — e o que deixa indefinido

| Já ratificado | Onde |
|---|---|
| o pacote testado deve ter o **mesmo digest** do artefato que será publicado | `PROPOSAL-1.0-EXIT-GATE` §32.6 `[RATIFICADO]` |
| **identidade de artefato (SHA256/proveniência)** é gate obrigatório; **manifesto de evidência por alvo** | `D-1.0-EDGES` Q7 (gates do §35) |
| Registry = GitHub Releases + `SHA256SUMS` verificado antes de instalar | `D2-A` |

**Indefinido:** a raiz de confiança, a identidade do builder, o formato mínimo da proveniência, quem verifica, onde a política é aplicada e o que acontece com prova ausente/inválida. Classificação da frente: **AMBIGUIDADE DE CONTRATO** (o requisito existe; o conteúdo dele, não).

## 2. Baseline medido

| # | Medição | Resultado | Como re-verificar |
|---|---|---|---|
| M1 | **SHA testado × SHA publicado** (release real `0.4.9-beta`) | testado = `e790137ee1` (merge do #558); publicado = `22a186b9bf` (commit de bump do `kof-release-bot`, **sem assinatura**). As árvores diferem exatamente em `CHANGELOG.md`, `VERSION`, `version.properties`, `pom.xml`. | `git log --grep 'bump version to 0.4.9-beta'`; `git diff <pai> <bump> --name-only` |
| M2 | Forma do workflow de release | `release.yml`: testes completos (`mvn clean package` + golden + integração) rodam no job 1 no **SHA de disparo**; o job 1 então commita o bump de versão e dá push; o job 2 faz checkout do **SHA do bump** e recompila com `-DskipTests` numa matriz de 3 SOs (linux/windows/macos) e publica. Dentro do workflow de release os arquivos windows/macos são construídos uma vez e só passam por sanidade (`kof version`/`kof info`); a suíte completa roda ali só no Linux (o workflow `CI` separado tem um job `kof.io` multi-SO, que é outra execução). | `.github/workflows/release.yml` |
| M3 | Escopo do token do workflow de release | `permissions: contents: write` no **nível do workflow** (os dois jobs), e o job 1 faz `git push` direto na `main`. | `release.yml` linhas 14–15, 94 |
| M4 | Referências a actions de terceiros | **56** referências `uses:`, **0** fixadas por SHA completo de commit (todas por tag/branch, ex.: `actions/checkout@v7`, `softprops/action-gh-release@v3`, `docker://…gitleaks:v8.28.0`). Configuração do repo `sha_pinning_required=false`, `allowed_actions=all`. | `grep -h 'uses:' .github/workflows/*.yml`; `gh api repos/KofLang/Kof4j/actions/permissions` |
| M5 | Token padrão / aprovações | permissões padrão de workflow = `read`; `can_approve_pull_request_reviews=false`. 3 workflows não declaram `permissions:` no topo (herdam o padrão `read`). | `gh api repos/KofLang/Kof4j/actions/permissions/workflow` |
| M6 | Governança da fonte | `main` e `beta-0.5.0`: `protected=false`, **0 rulesets**, nenhum required status check visível. (É o que a API mostra das configurações visíveis do repositório; permissões de colaboradores e política de organização são outras dimensões e não foram medidas.) | `gh api repos/KofLang/Kof4j/branches/<b>/protection`, `.../rulesets` |
| M7 | Proveniência / attestations | nenhuma encontrada: a API de attestations devolve 404 para o digest de um asset real de release. As releases são publicadas por `github-actions[bot]`; assets = arquivo + `kof-cli-*.jar` + `SHA256SUMS` (o arquivo de checksum é produzido e publicado pelo mesmo job do artefato). O próprio GitHub registra um `digest` por asset no servidor. | `gh api repos/KofLang/Kof4j/attestations/sha256:<digest>` |
| M8 | Verificação do consumidor (Registry) | `kof deps resolve` verifica o `SHA256SUMS` **antes de instalar**, e agora cada fonte (`REG002/REG004`); o arquivo de checksum viaja **dentro do mesmo tarball**. | `DepsRegistry`, `DepsSources` |
| M9 | Já existe (não redesenhar) | CodeQL, Gitleaks, Dependabot (`maven` + `github-actions`, semanal), `SECURITY.md` (relato privado de vulnerabilidade), guarda de path traversal na extração, `REG00x` honestos, #564/#565/#563 fechadas com evidência de CI hospedado. | arquivos do repo |
| M10 | Assets publicados × `SHA256SUMS` | o `SHA256SUMS` (1 linha) cobre **só o arquivo**; o `kof-cli-0.4.9-beta.jar` avulso publicado ao lado não é coberto por nenhum checksum, e o jar de mesmo nome tem **bytes diferentes por SO** (42.090.580 B linux × 42.090.623 B windows — os builds não são reprodutíveis). | `gh api repos/KofLang/Kof4j/releases/tags/<tag>`; baixar o asset `SHA256SUMS` |
| M11 | Veredito sobre o commit exato publicado | commit testado `e790137ee1`: runs de CodeQL, Benchmark, Release e Code Quality com sucesso. Commit publicado `22a186b9bf` (`[skip ci]`): só `Code Quality: Push on main` (mais runs de bots disparados por issue) — **nenhum run de CodeQL, Release ou Benchmark**; o workflow `CI` exclui a `main` do `push` (`'!main'`). | `gh api 'repos/KofLang/Kof4j/actions/runs?head_sha=<sha>'`; `ci.yml` linhas 3–8 |

## 3. Classificação dos achados

| Achado | Classe | Nota |
|---|---|---|
| M1/M2 descontinuidade do mesmo candidato | **lacuna de release-readiness já coberta pelo §32.6 ratificado** | pertence à lane de release/EXIT-GATE; não é afirmação de que uma release passada foi adulterada — só que, sem mudança, esse desenho não satisfaria o contrato de evidência do 1.0 |
| M3, M4 | **achado de hardening de segurança / lacuna de confiança do release** | não é `BUG REAL`; precisa da decisão do dono do workflow (Q7) |
| M6 | **ambiguidade de governança da fonte** | proveniência de build prova "commit X, workflow Y", não "o commit X foi autorizado" (Q8) |
| M7/M8 | **ambiguidade de contrato** | SHA256 dá integridade, não autenticidade: se artefato **e** checksum forem trocados pelo mesmo ator, o hash continua batendo (T3) |
| M10, M11 | **hardening / lacuna do mesmo candidato** | checksum que cobre só parte dos assets publicados, jars por SO não reprodutíveis e nenhum veredito de CodeQL/Release no commit exato publicado — evidência da lacuna do §32.6, não um bug contra um contrato do KOF |

## 4. Modelo de ameaças (o que cada camada pode e não pode afirmar)

| ID | Ameaça | SHA256SUMS hoje | Proveniência de build | Hardening de workflow/fonte |
|---|---|---|---|---|
| T1/T2 | corrupção / pacote alterado sem seu hash | detecta | detecta | — |
| T3 | pacote **e** checksum trocados juntos | **insuficiente sozinho** | ajuda se a prova vier de raiz independente | ajuda |
| T4/T5 | artefato de outro commit / recompilado após os testes | não | **forte** se o digest do subject atestado for o testado | a forma do pipeline (M2) é essencial |
| T6 | workflow/action comprometido | não | pode atestar um build comprometido | **essencial** (M3/M4) |
| T7 | credencial de publicação vazada | não | ajuda a detectar origem divergente | privilégio mínimo / credenciais efêmeras |
| T8 | commit não autorizado | não | proveniência ainda válida | **governança da fonte** (M6) |
| T9/T10 | rollback/replay; comprometimento de chave de assinatura | não | não sozinha | desenhos da classe TUF tratam disso (pós-1.0 salvo decisão contrária) |
| T11/T12 | dependência legítima maliciosa; falso-green do CI | não | prova origem, não segurança | gates confiáveis (`EG-2`) |

Não existe ferramenta única que cubra tudo; a cadeia é **confiança na fonte → política de build confiável → testar o artefato exato → digest → proveniência → publicar os mesmos bytes → verificar por política**.

## 5. Perguntas que só a mantenedora pode decidir (nenhuma é decidida aqui)

1. **Propriedade obrigatória no 1.0:** integridade, autenticidade, proveniência de build, proveniência de fonte/controle de mudança, ou combinação?
2. **Identidade confiável** que pode atestar uma release oficial (o workflow oficial, um workflow reutilizável confiável, uma identidade de mantenedor, uma identidade Sigstore, outra).
3. **Política da fonte:** basta proveniência de *qualquer* commit, ou a revisão também precisa ter passado por uma política (revisão/required checks)?
4. **Invariante do artefato exato** (construir uma vez → testar → atestar → publicar os mesmos bytes): regra formal? (O §32.6 já ratifica a igualdade de digest.)
5. **Onde a verificação é obrigatória:** só no gate de release, mais a doc de download, também em `kof deps resolve`, só para pacotes oficiais?
6. **Política de falha:** evidência ausente/inválida bloqueia a release, bloqueia o consumo, avisa, ou difere entre oficial e comunitário?
7. **Confiança no workflow:** actions fixadas por SHA, privilégio mínimo por job, workflow de build reutilizável/confiável, tudo isso, ou hardening fora do contrato?
8. **Proteção da fonte:** branch protection/rulesets, required checks, revisão, commits assinados, nenhum?
9. **SBOM:** gate do 1.0, evidência complementar ou pós-1.0?
10. **Rollback/frescor:** GitHub Releases + proveniência bastam para o 1.0, ou é exigida proteção explícita contra rollback/replay?
11. **Neutralidade de fornecedor:** nomear "GitHub Artifact Attestation" no contrato, ou enunciar propriedades neutras e permitir implementações equivalentes? (A pesquisa favorece a segunda.)
12. **Objeto do consumidor (decorre da #566(b)):** como pacotes agora são consumidos como módulos-fonte, o artefato que recebe digest/attestation para uma *biblioteca* é o tarball de fontes — confirmar.

## 6. Resultados do laboratório — GitHub Artifact Attestations (Onda 2, 20/09/2026)

Executado num **repositório de smoke público pessoal** (nada do Kof4j foi tocado). Dois workflows publicam o mesmo tipo de artefato: **`lab-broad`** espelha o `release.yml` (`contents: write` no workflow, actions por tag) e **`lab-pinned`** segue o fluxo ideal (actions fixadas por SHA completo resolvido pela API, permissões por job, **build uma vez → atesta → sobe → publica os mesmos bytes**). A verificação é feita por um consumidor com `gh` 2.98.

| Experimento | Resultado (medido) |
|---|---|
| os dois pipelines | sucesso em 18 s / 22 s; o digest que o GitHub registra no servidor para o asset é igual ao sha256 local |
| `gh attestation verify` online (ambos) | **VERDE**; verificação levou ≈6,2 s (inclui obter a raiz de confiança) |
| N1 artefato com 1 byte alterado | **RED** — não existe attestation para o novo digest |
| N2 bundle de attestation do artefato A usado para o artefato B | **RED** |
| N3 repositório errado / N4 owner errado | **RED** |
| N5 workflow assinante fixado no *outro* workflow | **RED** |
| N6 `--source-ref` errado / N7 tipo de predicate errado | **RED** |
| P1/P2 política: assinante = `lab-pinned.yml`, ref = `refs/heads/main` | **VERDE** |
| **offline** (bundle + raiz de confiança local, rede forçada a falhar por proxy morto) | **VERDE**; o mesmo comando sem o bundle dá **RED** (precisa de rede); arquivo adulterado e bundle de outro digest dão **RED** também offline |
| o que o attestation afirma | predicate `slsa.dev/provenance/v1`; builder = `<repo>/.github/workflows/<arquivo>@<ref>`; issuer = GitHub OIDC; repo/ref/commit de origem; runner `github-hosted`; 1 timestamp verificado (log de transparência); bundle ≈11,7 KB, raiz de confiança ≈34,6 KB |
| sonda de menor privilégio (job de build com `contents: read` tenta `gh release create`) | **`HTTP 403 Resource not accessible by integration`**, nenhuma release criada — o escopo por job realmente impede a ação |
| configuração do repositório `sha_pinning_required=true` | o workflow por tag **falha em "Set up job"** ("all actions must be pinned to a full-length commit SHA"); o fixado por SHA passa. A configuração foi restaurada para `false`. |

**O que o laboratório ensina (medido, não decidido):**

- A verificação é tão forte quanto a **política** dada a ela: `--repo`/`--owner` sozinhos aceitam qualquer workflow daquele repositório; fixar `--signer-workflow` (e `--source-ref`) é o que torna a identidade do builder uma propriedade real (N5/P1).
- **Um defeito na minha primeira rodada**, mantido aqui de propósito: os dois workflows produziram tarballs **byte-idênticos** (mesmo commit, mesmo número de run, `tar` determinístico), então um digest carregou duas attestations e os negativos "digest errado" e "workflow errado" deram VERDE legitimamente. Foi corrigido fazendo o conteúdo diferir por workflow. Lição: testes negativos precisam de sujeitos genuinamente distintos.
- A verificação offline funciona, mas só com uma **raiz de confiança guardada localmente**; alguém precisa distribuí-la/atualizá-la.
- A configuração `sha_pinning_required` é um gate **mecânico** (não exige mudar workflow para impô-lo) — mas para o Kof4j bloquearia as 56 referências por tag de uma vez.
- Custo/lock-in: gratuito em repositório público; a infraestrutura de certificado/log é GitHub-OIDC + Sigstore public good, então o *formato da evidência* (statement in-toto de proveniência SLSA) é portável enquanto o *emissor* é específico do GitHub — a base da pergunta de neutralidade de fornecedor (Q11).

## 7. Não feito aqui / próximos passos

- **Laboratório:** feito — ver §6 (resultados, inclusive o defeito da minha primeira rodada).
- **Issue de design aberta: #571** (`[Design/Contract][Security]`, as perguntas da §5 + a evidência do laboratório); a checagem de duplicatas não achou nenhuma. Aguarda a mantenedora; ainda não tem categoria `1.0-*` (quem classifica é o gate/lane, não o autor).
- **Parada dura:** nenhuma mudança de produção (workflow, gate, `kof deps`) até a mantenedora registrar uma decisão; e nenhum arquivo `EM CURSO` de outra lane é tocado.
- **Efeito colateral desta pesquisa:** ao listar os workflow runs do commit publicado, foi achado um defeito real num workflow de bot não relacionado, registrado como **#570**; a lane da mantenedora o corrigiu primeiro (`known-bugs.md` §394) e este trabalho acrescentou o teste de regressão e uma prova em GitHub real. Não faz parte do contrato de confiança.

## 8. Feito sem esperar a decisão da mantenedora na #571

Tudo abaixo fica dentro do que **já está ratificado** (`PROPOSAL-1.0-EXIT-GATE` §32.6 `[RATIFICADO]` "digest testado == digest publicado", §32.7/§35 "manifesto de evidência por alvo", `D-1.0-EDGES` Q7) ou é medição. Nada disso escolhe raiz de confiança, formato de proveniência ou política de verificação, e nada toca `release.yml`, os gates ou o `kof deps`.

**Ferramentas (somente leitura / offline, cada uma com teste RED-first registrado na suíte de agentes que o CI já roda):**

- `scripts/verify-release-identity.sh` — confere uma release publicada (`--release <tag>`) ou um diretório local (`--dir`): o `SHA256SUMS` existe; toda linha aponta para um asset real e confere com o digest que o GitHub registra no servidor (ou o recalculado); o arquivo (`*.tar.gz`/`*.zip`) está coberto (FAIL se não; outros assets sem checksum = WARN); e, com `--tested <sha256>`, **testado == publicado**. Sem `--tested` a igualdade é reportada como **`NOT_RUN`, nunca PASS**; `--require-tested` transforma isso em falha. Teste: 12 cenários offline (adulteração, arquivo descoberto, listado-e-ausente, sem `SHA256SUMS`, marcador `*`, testado igual/diferente/ausente, digest do servidor divergente).
  **Rodado nas releases reais (`0.4.9-beta` linux e windows):** arquivo coberto — `PASS`; o `kof-cli-0.4.9-beta.jar` avulso — `WARN` (sem checksum, achado M10); testado == publicado — **`NOT_RUN`**, e `--tested` com digest errado — `FAIL`. Ou seja, hoje a igualdade ratificada é literalmente **não verificável**, e a ferramenta torna isso visível em vez de silencioso.
- `scripts/release-evidence.sh` — o manifesto de evidência por alvo (um TSV: timestamp, alvo, SHA candidata, resultado, **digest do pacote testado**, prova, verificador). O `check` só aprova se os 8 alvos Stable (`jvm x86-64 riscv64 aarch64 js script kofc android`) têm evidência **GREEN** na **mesma SHA candidata**: RED/SKIP/NOT_RUN nunca viram verde, evidência de SHA antiga é recusada, o digest do pacote testado é obrigatório (exceto Script), um RED seguido de GREEN na mesma SHA passa **com WARN** (rerun nunca é silencioso), entradas inválidas são rejeitadas e nada é gravado. `digest <manifesto> <alvo>` imprime o digest para encadear em `verify-release-identity.sh --tested`. Teste: 9 grupos offline.
- Não ligados a nenhum gate: encaixá-los no EG-8 / gate de release é da lane EG/release.

**Medições adicionais (alimentam a decisão, não decidem nada):**

| Pergunta | Medição | Leitura |
|---|---|---|
| Uma attestation válida protege contra **rollback/replay** (T9, Q10)? | a attestation de um artefato **antigo** do lab (run 1) ainda verifica VERDE com versões mais novas publicadas (runs 3–4); ela não tem noção de "última" | proveniência sozinha não dá frescor; se a Q10 exigir, é preciso um mecanismo à parte |
| O build do KOF é **reprodutível em bytes**? (mesmo commit, mesmo SO, filesystem nativo, 3 builds limpos) | os 3 deram certo; mesmo tamanho (42.156.948 B) e **0 arquivos com conteúdo diferente**, mas **3 sha256 diferentes** (mesmo caminho repetido e outro caminho) | reprodutível em *conteúdo*, não em *bytes* (metadados/datas do jar): "recompilar e obter o mesmo digest" é impossível hoje, por isso a regra do artefato exato tem de publicar os *bytes testados* em vez de recompilar — a próxima linha diz se uma propriedade padrão do Maven resolve |
| `project.build.outputTimestamp` tornaria o jar reprodutível em bytes? | **sim** — 3 builds limpos (mesmo caminho repetido, e outro caminho) deram o **mesmo sha256** (`30e2bca1…`) | uma propriedade padrão do Maven torna o jar do `kof-cli` byte-reprodutível; aplicada no `pom.xml` raiz e codificada em `scripts/check-reproducible-build.sh` (RED antes: digests diferentes; GREEN depois). Os arquivos de distribuição são medidos na próxima linha (`tar.gz` Linux sem JDK: **não** reprodutível); os arquivos com o JDK embutido e o `windows-x86_64.zip` ainda **não** foram medidos |
| O `tar.gz` Linux do `scripts/package.sh --skip-build` é **reprodutível em bytes**? (21/09; mesmo commit `e7a2e876`, mesmo jar, mesmo usuário, WSL em ext4, sem `--jdk`, dois diretórios de saída diferentes, runs com 2 s de intervalo) | **não (RED)** — `d069d29e…` ≠ `313a255d…` (38.657.033 B × 38.657.025 B). Dos 134 bytes que diferem no tar descomprimido, 58 estão no campo `mtime` do cabeçalho e 76 no `chksum` (derivado do `mtime`): **o `mtime` difere em 58 de 58 entradas** (Δ = 2 s = quando o `cp` copiou cada arquivo). Nomes, ordem, modo, uid/gid, tamanho, tipo, o conteúdo dos 58 arquivos e o cabeçalho do gzip (o mtime dele é 0) são **idênticos** | a única causa medida são os timestamps das entradas (`cp` sem `-p`, `tar -czf` sem `--mtime`). **Não medido:** ordem das entradas em outro filesystem/host (o `package.sh` nunca ordena), dono/grupo entre contas diferentes (uid/uname/gname ficam gravados no tar), diferenças entre implementações do gzip, os arquivos com `--jdk`, o `windows-x86_64.zip`. Um GREEN válido exige os dois runs em segundos diferentes (no mesmo segundo poderiam coincidir por acaso). Nenhuma correção nesta medição — o `package.sh` não foi tocado |
| (minha 1ª tentativa de medir o build foi **inválida**: o `mvn clean` falhou no mount do Windows e os dois "builds" eram o mesmo jar velho; foi pega ao ler o `rc=1` impresso, o script ganhou uma guarda de `rc` e foi refeita em filesystem nativo) | | |

**O que ainda precisa da decisão dela (nada foi feito nisto):** fixar actions por SHA e tokens de menor privilégio (Q7); proteção de branch/rulesets e required checks (Q8); gerar/atestar proveniência no `release.yml` e a reescrita do pipeline para o mesmo candidato (Q1–Q4); verificar no `kof deps resolve` e a política de falha (Q5/Q6); SBOM (Q9); frescor (Q10); a redação do contrato (Q11); e o objeto do consumidor para bibliotecas (Q12).
