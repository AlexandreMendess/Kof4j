[English](registry-live-roundtrip-2026-09-20.md) | [Português](registry-live-roundtrip-2026-09-20.pt_BR.md)

# Registry 1.5.3 — smoke real de round-trip no GitHub (20/09/2026) — **VERMELHO → VERDE após a #564**

> **ATUALIZAÇÃO — VERDE após o fix da #564** (ver "Re-run após o fix da #564" abaixo): publish, pull por
> versão, pin do `latest`, 2º resolve idempotente e erro honesto passam contra o GitHub real.
> O passo que parecia em aberto também **não** é defeito do Registry: código KOF **consegue** consumir o
> pacote baixado no caminho documentado (`kof deps` + `--deps` + `new Classe()`); ver "Consumo canônico"
> no fim (uma afirmação anterior aqui estava errada). O texto abaixo é o registro RED original.
>
> **Resultado da 1ª rodada: VERMELHO.** O publish funciona contra o GitHub real. O pull **não**: dois bugs
> reais achados, ambos abertos (#564, #565). A nota do tracker `round-trip live no GitHub =
> smoke manual pendente` (`IMPLEMENTATION-UNIVERSAL-PLATFORM`, item 1.5.3) **continua pendente**.
> Este smoke não alterou código de produção. Abaixo só dados sanitizados: nenhum token foi
> impresso, logado ou commitado (registra-se a presença, nunca o valor).

## Preparo (medido)

| Item | Valor |
|---|---|
| Tip sob teste | `5d8a2b98` (`beta-0.5.0`), jar construído da árvore (`kof-cli-0.4.7-beta.jar`) |
| CLI oficial para contraprova | `kof 0.4.9-beta` (jar released) |
| Host | WSL Ubuntu-24.04, JDK 25 |
| Repo do smoke | público `jonasrochasilva-prog/kof-registry-smoke` (só README; **não** o `KofLang/Kof4j`) |
| Release / tag publicada | `kof-registry-smoke-0.1.0-smoke.5d8a2b98` |
| Asset | `kof-registry-smoke-0.1.0-smoke.5d8a2b98.tar.gz`, 1920 bytes (jar + `RELEASE.md` + `SHA256SUMS`, tar uid/gid 0) |
| Isolamento do cache | `-Duser.home=<dir novo>` por fase; diretório do consumidor novo; `GH_TOKEN`/`GITHUB_TOKEN` **removidos** em toda fase de pull |
| Trato do token | só no produtor, por variável de ambiente (nunca em linha de comando); saída redigida; **varredura pós-publish do asset enviado** sem token, caminho de usuário ou nome de host |

A release e a tag ficam como evidência (sem limpeza automática).

## Fases

| Fase | O quê | Resultado |
|---|---|---|
| A | empacotar de árvore limpa (`kof deploy src --target jvm ...`), varredura pré-publish do artefato | OK — nenhum dado sensível |
| B | `kof deploy --publish` no repo do smoke → Release real + asset `.tar.gz` | **OK** — release e asset visíveis, asset baixável **sem token** (HTTP 200), `SHA256SUMS` interno confere |
| C | consumidor, HOME/cache novos, `kof deps add owner/repo@<ver>` + `kof deps resolve` | **VERMELHO** — `REG002: release <tag> has no .tar.gz asset` (exit 1); sem lock, sem cache gravado |
| D | idem com `latest` (sem `@version`) | **VERMELHO** — mesmo `REG002` (exit 1) |
| E | idempotência (2º `resolve`) | **BLOQUEADA** pela C (nada instalado para comparar) |
| F | referência inexistente `@9.9.9-inexistente` | **OK** — `REG001: release not found on registry ...`, exit 1, 0 arquivos no cache, release válida intacta |

C/D foram reproduzidas também com o jar **oficial** `kof 0.4.9-beta`, então não é artefato do build local.
A fase F prova que o caminho de falha é honesto (R6): só o parsing dos assets de uma release *existente* está quebrado.

## Achados

| # | Issue | Classe | Resumo |
|---|---|---|---|
| A | [#564](https://github.com/KofLang/Kof4j/issues/564) | BUG REAL | `DepsRegistry.pickTarball` lê `"download_url"` (a chave real é `"browser_download_url"`) e corta cada objeto de asset na primeira `}` (o asset real tem `"uploader": {…}` aninhado antes da URL). O pull nunca funciona no GitHub real; o `DepsRegistryTest` só passava contra um servidor falso mínimo. |
| B | [#565](https://github.com/KofLang/Kof4j/issues/565) | BUG REAL (baixo) | `CmdBuild.buildFatJar` grava `classesDir/kof-app.jar` dentro do diretório que percorre, então todo fat jar embute uma entrada `kof-app.jar` truncada e inválida (561 bytes). Não é falha de execução. |

> **Atualização 20/09 — #565 CORRIGIDA** (entregue em `d1a12dd9`): self-inclusion removida de `kof build --fat` e `kof deploy --target jvm` (jar em staging fora do `classesDir` + exclusão exata do path final + substituição só após fechar; um rebuild que falha mantém o jar anterior e não deixa `.kof-app-*`). Travada por `CmdBuildFatTest` (build 1, rebuild no mesmo `classesDir`, rebuild que falha) e pela inspeção estrutural do jar distribuído no `CmdDeployTest`. (Naquele momento o smoke seguia VERMELHO por causa da #564 — superado pelo re-run abaixo.)

Ambos triados KOF-first (D-KOF-FIRST): bugs de tooling, sem sintaxe KOF; contrato = `DECISIONS.md` D2-A;
busca de duplicatas (abertas+fechadas) não achou nenhuma.

## Observações (sem issue)

- O jar construído da árvore mostra `compiler: unknown` no `RELEASE.md` porque o build local não tem `dev/kof/version.properties`; o jar oficial imprime `kof 0.4.9-beta`. Artefato de ambiente, não bug.
- O `kof deploy` empacota só classes alcançáveis a partir do `main`. Um pacote só-biblioteca não leva nada se o entry point não o usa — questão de contrato para publicar *bibliotecas* (regra 6: da mantenedora), não um defeito alegado aqui.
- Meu próprio script chamou `kof run .` (COMP001: exige um arquivo `.kf`); foi mau uso do harness, não bug, e por isso o passo `run` de C/D/E não é evidência.

## Re-run após o fix da #564 — **VERDE** (20/09/2026)

Mesma release pública, mesmas condições limpas: jar construído da árvore (`kof-cli-0.5.0-beta.jar`, verificado como contendo o fix), `-Duser.home` novo por fase, diretório de consumidor novo, `GH_TOKEN`/`GITHUB_TOKEN` **removidos**, `api.github.com` real. Nada sensível é registrado (caminhos como `$HOME`).

| Fase | Resultado (medido) |
|---|---|
| C — por versão explícita | **VERDE** — `resolve` exit 0; jar instalado em `$HOME/.kof/deps/kof/<owner>/<repo>/<ver>/<repo>-<ver>.jar`; sha256 do jar instalado == sha256 do jar publicado (`6099e28f…`); o `SHA256SUMS` do próprio pacote publicado confere (`OK`) |
| E — idempotência | **VERDE** — 2º `resolve` exit 0, **sem download**; mtime/tamanho do jar, `kofdeps` e estado do lock byte-idênticos |
| D — `latest` (sem `@version`), HOME e workspace separados | **VERDE** — `kofdeps` `owner/repo` vira `owner/repo@0.1.0-smoke.5d8a2b98`, jar instalado |
| F — referência inexistente | **VERDE** — `REG001` (exit 1), 0 arquivos no cache |
| rodar o pacote instalado | `java -cp <jar> Default.Main` → `hello, producer` |

Prova no código: o `DepsRegistryTest` agora serve o **shape real do GitHub** (`uploader{…}` aninhado com `url` próprio antes de `browser_download_url`, sem `download_url`, `author{…}` antes de `tag_name`, delimitadores e aspas escapadas dentro de string, ordem de campos invertida) e afirma o contrato HTTP (asset baixado pelo `url` da API do asset com `Accept: application/octet-stream`; `User-Agent: kof-cli`; `X-GitHub-Api-Version: 2022-11-28`; um 302 para outro host é seguido e o token `Authorization` **não** é enviado a ele). Antes do fix 11 dos 13 testes falhavam com `REG002: … has no .tar.gz asset`; depois: 13/13 (+ `DepsTest` 4, `DepsTransitiveTest` 10).

Decisão do fix (KOF-first): o JSON da release é lido com o `Json.parse` estrutural do próprio CLI (sem dependência nova, diferente do `jackson-core` proposto no plano); a exigência de `SHA256SUMS` e a seleção exato → `-jvm` → primeiro `.tar.gz` não mudaram.

## Observações do re-run

- O `kofdeps.lock` não é gravado para deps do registry: ele é o fecho transitivo Maven (roadmap 1.5.2). Deps do registry são pinadas **no próprio `kofdeps`** (`latest` → versão concreta) — comportamento existente, afirmado por `latestResolvesAndPinsConcreteVersion`.
- Consumo do pacote baixado por código KOF: ver a seção de correção abaixo (a leitura anterior de `PKG006` veio de medir sem `--deps`).

## Consumo canônico do pacote baixado — medido em 20/09/2026 (correção de uma afirmação anterior)

Uma versão anterior desta auditoria (e a linha do tracker) dizia que um programa KOF **não consegue** `import` o pacote baixado (`PKG006`, mesmo com `--classpath`). A premissa estava errada: foi medida **sem `--deps`** (a forma documentada de pôr as dependências resolvidas no classpath) e com `run --classpath`, que não é flag do `kof run` (`kof run <file.kf> … [--deps] [args...]`: o que vier depois do arquivo é argumento do programa). Re-medido na superfície documentada, mesma release pública, sem token, HOME limpo, jar construído do tip:

| Passo | Resultado (medido) |
|---|---|
| `kof deps init/add/resolve` | jar instalado; classes `Default/Main`, `regsmoke/Greeter` |
| `kof run Main.kf --deps` com `import regsmoke.Greeter` + `new Greeter()` | **VERDE** — `hello, consumer`, rc 0 |
| idem, **sem** `--deps` (o que eu tinha medido antes) | `PKG006`, rc 1 — esperado: a dependência não está no classpath |
| `kof build src --target jvm --deps`, depois `java -cp dist:<jar> Default.Main` | **VERDE** — `hello, consumer`, rc 0 |
| `Greeter()` **sem** `new`, `run`/`build` com `--deps` (ou `--classpath`) | `SEM015` ×3, rc 1, **0 classes emitidas** — ver abaixo |
| `kof run Main.kf --deps --flag-inexistente` | executa, rc 0: argumentos extras após o arquivo são do programa (`[args...]`), por design |

Consequências, todas medidas (nada implementado):

- O consumo JVM básico de um pacote publicado **existe** no caminho documentado (`kof deps` + `--deps` + `new Classe()`). O que segue em aberto é contrato, não transporte: se um pacote publicado é uma *biblioteca* com superfície pública (hoje o `kof deploy` empacota só o que o `main` alcança), se o consumo KOF→KOF é só JVM ou multi-alvo, e se o `new` opcional vale também para classe vinda de classpath externo (`Greeter()` dá `SEM015`; `new Greeter()` funciona). São decisões da mantenedora (regra 6).
- "Build reporta erro mas emite artifact válido" **não reproduz**: com qualquer erro (`SEM015` por `--deps` ou `--classpath`, `SEM011` genuíno) o build sai com rc 1 e emite **0 classes**. O artifact visto no relato é **resíduo de um build anterior bem-sucedido no mesmo diretório de saída** (build 1 válido em `cout`; build 2 falha no mesmo `cout` sem limpar → `rc=1`, `classes=1`, `java -cp cout:<jar> Default.Main` imprime a saída do build *anterior*; com diretório limpo: `rc=1`, `classes=0`).
- `kof run --classpath` não é flag documentada do `run`; adicioná-la seria uma superfície nova de CLI (decisão de design), não bugfix.

## Consumo como módulo-fonte (#566, decisão (b) da mantenedora) — implementado e medido no GitHub real (20/09/2026)

A mantenedora decidiu (adendo do `D-RELEASE-0.5.0-GATE`) que um pacote publicado por `kof deploy --publish` é consumido como **módulo-fonte**, e não pelo jar compilado. Isto substitui a leitura de "consumo canônico" acima para pacotes *novos* (a rota jar-no-classpath fica só para pacotes publicados antes desta mudança).

**Implementação** (sem mudança de sintaxe/semântica): o compilador resolve `import` também nas raízes de fonte das dependências instaladas (depois do módulo local e das bibliotecas oficiais — uma dependência nunca sombreia a biblioteca padrão), em todo alvo; o `kof deps resolve` instala as fontes do pacote, cada uma verificada contra o `SHA256SUMS`; `kof run|build --deps` entregam essas raízes ao compilador; o `kof deploy` leva `src/…` e aceita uma **biblioteca** (só árvore de pacotes, nenhuma fonte no topo), que é compilada para validar e publicada só como fontes.

**Round-trip real** (jar do tip; o produtor publica uma biblioteca no repo público de smoke; o consumidor com HOME limpo e **sem token** contra a API real). Varredura pré-publish do tarball e pós-publish do **asset real baixado**: 0 achados (caminhos de usuário, host, token, segredos); asset = 3 entradas (`src/regsmoke/Greeter.kf`, `RELEASE.md`, `SHA256SUMS`), `SHA256SUMS` confere.

| Passo | Resultado (medido) |
|---|---|
| `kof deps add/resolve` por versão (biblioteca, só fontes) | **VERDE** — fontes instaladas em `$HOME/.kof/deps/kof/<owner>/<repo>/<ver>/src/`; sha256 da fonte instalada == publicada |
| `kof run Main.kf --deps` com `import regsmoke.Greeter` + `Greeter()` (**sem `new`**) | **VERDE** — `hello, consumer` |
| idem, sem `--deps` | `PKG006`, rc 1 (honesto) |
| `kof build --target jvm --deps` e `java -cp dist` (sem jar de dependência) | **VERDE** — `hello, consumer` |
| `kof build --target js --deps` (as mesmas fontes, outro alvo) | **VERDE** — rc 0 |
| 2º `resolve` | **VERDE** — sem download, cache byte-idêntico |
| `latest` (HOME/workspace novos) | **VERDE** — o `kofdeps` pina a versão concreta |
| **pacote legado só-jar** (`0.1.0-smoke…`) com `run --deps` + `new Greeter()` | **VERDE** — regressão sobre um pacote real anterior à mudança |
| tag inexistente | `REG001`, rc 1 |

**Testes** (WSL Ubuntu-24.04, JDK 25; cada fatia com RED antes do fix): `DependencySourceRootE2ETest` 7/7 (RED 4/7), `DepsSourceModuleTest` 8/8 (RED 8/8), `CmdDeploySourcesTest` 5/5 (RED 3/5, inclui o ciclo completo deploy → registry → `kof run --deps`). Suíte local completa dos 4 módulos: kof-compiler 2792 testes — 7 falhas, **todas o §337 conhecido** (SIGSEGV do `qemu-aarch64` no WSL2: Dtoa + 5 classes GC; idêntico na base; o CI hospedado dá 0F), 0 erros; kof-script 50/0; kof-c 7/0; kof-cli 432/0F/0E/3 skips. O contrato do tar do deploy mudou de propósito (eram 3 entradas; as fontes agora viajam entre o artefato e o `RELEASE.md`).

As perguntas residuais listadas antes (superfície pública de biblioteca, consumo multi-alvo) são respondidas por este modelo; `Classe()` sem `new` funciona porque a dependência é uma classe-fonte KOF (o `SEM015` da #568 só dizia respeito a classes vindas de jar externo).
