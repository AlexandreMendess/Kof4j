[English](RELEASES.md) | [Português](RELEASES.pt_BR.md)

# Pipeline de Release

Cada commit na `main` representa um estado publicável. A pipeline garante
que a `main` nunca aponte para um estado que não compila.

```text
commit na main
      ↓
CI (ci.yml) — gate: main sempre compila
      ↓
test-and-bump
   ├─ mvn clean package (gate)
   ├─ tests/run-golden.sh (jvm + native)
   ├─ tests/run-integration.sh (CLI + serve + kof test)
   ├─ version bump (scripts/bump-version.sh) — ex.: 0.4.7-beta → 0.5.0-beta
   ├─ seção do changelog → CHANGELOG.md
   └─ commit + push do bump ([skip ci])
      ↓
package-and-release (matriz — um job por plataforma)
   ├─ checkout do COMMIT DE BUMP (não o do trigger)
   ├─ mvn clean package
   ├─ sanity check: VERSION do checkout == versão da release
   ├─ scripts/package.sh --jdk (embute Temurin 25 — toolchain baseline D-BASELINE)
   ├─ valida o artefato (extrai, bin/kof version + info, JDK embutido)
   └─ GitHub Release kof-<versão>-<plataforma> com artefato + SHA256SUMS
```

---

## Workflows

### `.github/workflows/ci.yml` — Pull Requests e branches

Executa:

1. Verificação de que `VERSION` e `pom.xml` concordam
   (`scripts/bump-version.sh` + `git diff --exit-code`);
2. `mvn clean test` (todos os testes E2E JVM + Native);
3. `mvn clean package`.

### `.github/workflows/release.yml` — push na `main`

Dois jobs:

1. **test-and-bump** (Ubuntu):
   - `mvn clean package` — **gate**: nenhuma release é publicada com
     build quebrado;
   - `tests/run-golden.sh` (8 casos × jvm+native) e
     `tests/run-integration.sh` (CLI + serve + kof test);
   - lê `VERSION` (ex.: `0.3.22-beta`), calcula a próxima
     (`0.5.0-beta`), roda `scripts/bump-version.sh`;
   - insere a seção do changelog no `CHANGELOG.md`;
   - commita e faz push do bump (`[skip ci]` para não re-disparar);
   - exporta o **SHA do commit de bump** (`bump_sha`).

2. **package-and-release** (matriz: `ubuntu-latest`/linux-x86_64,
   `windows-latest`/windows-x86_64, `macos-latest`/macos-arm64):
   - **checkout o commit de bump** (via `ref: bump_sha`) — sem isso o
     checkout traria o commit que disparou o workflow (pré-bump) e o
     pacote sairia com a versão anterior;
   - sanity check: `VERSION` do checkout deve ser igual à versão da
     release (falha a job se divergir);
   - `mvn clean package`;
   - `scripts/package.sh --jdk` (embute Temurin 25);
   - valida o artefato: extrai, roda `bin/kof version`, `bin/kof info`
     e verifica o JDK embutido;
   - cria o **GitHub Release por plataforma**
     (`kof-<versão>-<plataforma>`) com o pacote, `SHA256SUMS` e o
     `kof-cli-<versão>.jar`.

---

## Tags e releases

- Uma release **por plataforma**: `kof-0.5.0-beta-linux-x86_64`,
  `kof-0.5.0-beta-macos-arm64`, `kof-0.5.0-beta-windows-x86_64`.
- A mais recente de cada plataforma carrega o selo **Latest**.
- O usuário instala a partir da release do **seu** sistema
  (ver [INSTALL.md](INSTALL.md)).

---

## Regras

- O release **só** acontece se `mvn clean package`, golden e integration
  passarem.
- Nunca publicar uma release quebrada.
- O bump é commitado com `[skip ci]` para evitar loop de releases.
- O pacote é construído **a partir do commit de bump** — a tag e o
  conteúdo do artefato sempre carregam a mesma versão.

---

## Artefatos

```text
kof-<versão>-linux-x86_64.tar.gz     # na release kof-<versão>-linux-x86_64
kof-<versão>-macos-arm64.tar.gz      # na release kof-<versão>-macos-arm64
kof-<versão>-windows-x86_64.zip      # na release kof-<versão>-windows-x86_64
SHA256SUMS                            # em cada release
kof-cli-<versão>.jar                  # jar standalone (cada release)
```

Cada pacote contém: compiler, CLI, runtime, stdlib, tooling, editor
support e JDK embutido.

## Changelog

`CHANGELOG.md` é atualizado pela pipeline via `scripts/changelog.sh`, que
agrupa os commits desde o último tag pela convenção:

```text
feat:  fix:  docs:  refactor:  test:  build:  tooling:
```

## Execução manual

```bash
# Local
scripts/bump-version.sh            # sincroniza VERSION → pom/properties
mvn clean test
scripts/package.sh                 # pacote local sem JDK
scripts/package.sh --jdk           # pacote oficial com JDK embutido
scripts/changelog.sh               # seção do changelog no stdout

# GitHub
# Release manual: GitHub → Actions → Release → Run workflow
```
