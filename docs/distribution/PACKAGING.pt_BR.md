[English](PACKAGING.md) | [Português](PACKAGING.pt_BR.md)

# Empacotamento (Packaging)

Como os artefatos oficiais do Kof são produzidos, nomeados e verificados.

---

## 1. Script

```bash
scripts/package.sh [--jdk] [--output <dir>] [--skip-build]
```

| Opção | Efeito |
|-------|--------|
| `--jdk` | Baixa e embute o OpenJDK (Temurin 25) no pacote |
| `--output <dir>` | Diretório de saída (padrão: `dist/`) |
| `--skip-build` | Usa o jar já compilado sem rebuild |

O script:

1. lê a versão de `VERSION`;
2. compila `kof-cli-<versão>.jar` se necessário (`mvn package -DskipTests`);
3. monta o layout de distribuição;
4. opcionalmente embute o JDK;
5. gera o arquivo (`tar.gz` ou `zip`) e o `SHA256SUMS`.

## 2. Nomenclatura

```text
kof-<version>-<os>-<arch>.tar.gz   # Linux / macOS
kof-<version>-<os>-<arch>.zip      # Windows
```

O `<os>-<arch>` vem da matriz do workflow de release (um pacote por
plataforma). Exemplos reais:

```text
kof-0.5.0-beta-linux-x86_64.tar.gz
kof-0.5.0-beta-macos-arm64.tar.gz
kof-0.5.0-beta-windows-x86_64.zip
```

> O nome carrega a **versão da release** (ex.: `0.5.0-beta`). O usuário não
> precisa decorar a versão: o guia de instalação usa o globo
> `kof-*-<os>-<arch>.tar.gz`.

## 3. Matriz de plataformas (workflow `release.yml`)

| Runner | Target | Artefato |
|--------|--------|----------|
| `ubuntu-latest` | `linux-x86_64` | `kof-<v>-linux-x86_64.tar.gz` |
| `windows-latest` | `windows-x86_64` | `kof-<v>-windows-x86_64.zip` |
| `macos-latest` | `macos-arm64` | `kof-<v>-macos-arm64.tar.gz` |

> **macOS é publicado para Apple Silicon (`arm64`).** Não há pacote
> `macos-x86_64`. O mapeamento `os`/`arch` do script (`linux`/`macos`/
> `windows` × `x86_64`/`arm64`) suporta qualquer combinação futura — para
> publicar uma nova plataforma basta adicionar uma linha na matriz do
> workflow.

## 4. Layout do pacote

```text
kof-<version>-<os>-<arch>/
├── bin/
│   ├── kof            # launcher Unix
│   ├── kof.bat        # launcher Windows
│   └── kof-webview    # shell do kof.ui (quando disponível)
├── lib/
│   └── kof.jar        # CLI + compiler + tooling (autocontido)
├── jdk/               # OpenJDK embutido (apenas com --jdk)
├── tooling/
├── editor/
│   └── kof.tmLanguage.json
├── docs/              # README, LICENSE, arquitetura, tooling, distribuição
└── VERSION
```

O `lib/kof.jar` é o jar *shaded* da CLI — contém compiler e tooling. Quando
o JDK embutido está presente, o launcher o usa automaticamente; sem JDK
embutido, o launcher usa `java` do PATH (somente builds de desenvolvimento).

## 5. Checksums

Cada build de pacote gera:

```text
SHA256SUMS
```

Verificação pelo usuário:

```bash
sha256sum -c SHA256SUMS
```

## 6. JDK embutido

`--jdk` baixa o OpenJDK Eclipse Temurin 25 (tooling API level 21) da API de
binários da Adoptium e o coloca em `jdk/`. Não é feito download em builds
locais sem `--jdk` para manter o ciclo rápido; a pipeline de release sempre
empacota com `--jdk`.

## 7. Verificação do artefato (CI)

Antes do release, o CI:

1. extrai o pacote em um diretório limpo;
2. executa `bin/kof version` e `bin/kof info`;
3. verifica a presença do JDK embutido (`jdk/bin/java`).

Nenhum artefato é publicado sem passar nessa validação.

## 8. Referências

- [ARCHITECTURE.md](ARCHITECTURE.md) — estrutura conceitual da distribuição
- [INSTALL.md](INSTALL.md) — instalação pelo usuário
- [RELEASES.md](RELEASES.md) — pipeline de release