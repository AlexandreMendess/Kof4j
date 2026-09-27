[English](ARCHITECTURE.md) | [Português](ARCHITECTURE.pt_BR.md)

# Arquitetura de Distribuição do Kof

**Versão:** 0.5.0-beta (16/09/2026)

O Kof não é apenas um compilador — é uma plataforma distribuível. A partir
do 0.2.x-beta, o projeto trata a instalação como parte oficial do produto:

> **Kof deve parecer uma linguagem que você instala, não um projeto Java que você precisa montar.**

---

## 1. Modelo de Distribuição

O pacote oficial é autocontido. A instalação fornece:

```text
kof/
├── bin/
│   ├── kof          # launcher (Unix)
│   └── kof.bat      # launcher (Windows)
├── lib/
│   └── kof.jar      # CLI + compiler + tooling (shaded, self-contained)
├── jdk/             # OpenJDK embutido (apenas no pacote oficial com --jdk)
│   └── bin/java
├── tooling/         # definições e ferramentas consumidas por editores
├── editor/          # grammar TextMate oficial (source.kof)
├── docs/            # documentação compacta que viaja com a distribuição
└── VERSION          # versão da instalação
```

O usuário **não** precisa instalar Java, configurar `JAVA_HOME`, usar SDKMAN
ou ajustar o `PATH` manualmente. O launcher `bin/kof` resolve o JDK embutido
da própria instalação e, apenas em builds de desenvolvimento sem JDK
embutido, cai para um `java` do sistema.

### Estrutura de arquivos por artefato

```text
kof-<versão>-linux-x86_64.tar.gz      # Linux (Intel/AMD)
kof-<versão>-macos-arm64.tar.gz       # macOS (Apple Silicon)
kof-<versão>-windows-x86_64.zip       # Windows (Intel/AMD)
```

Cada artefato acompanha um `SHA256SUMS` para verificação de integridade.
Matriz completa em [PACKAGING.md](PACKAGING.md).

---

## 2. JDK Embutido (OpenJDK)

O backend JVM do Kof precisa de uma JVM para executar programas compilados.
Em vez de depender do ambiente do usuário, o pacote oficial **embarca um
OpenJDK compatível** (Eclipse Temurin 25; o tooling API level continua 21 — D-BASELINE).

Decisões:

- **Não** implementamos uma JVM própria — usamos OpenJDK.
- O pacote oficial traz o JDK em `jdk/` e o launcher o utiliza
  automaticamente.
- `kof run`, `kof build --target=jvm`, `kof serve` e `kof test` funcionam
  sem nenhuma instalação externa.
- Em pacotes construídos localmente sem `--jdk`, o launcher usa `java` do
  `PATH` (equivalente a um build de desenvolvimento).

O download do JDK é feito pela pipeline de release via
`scripts/package.sh --jdk` (API de binários da Adoptium). A verificação de
que o JDK embutido está sendo usado aparece em `kof info` (campo JVM marcado
como *embedded*).

---

## 3. Tooling API Level: 21

O tooling distribuído pelo Kof assume como baseline a **API Java 21**.

- APIs usadas pelo tooling são compatíveis com Java 21.
- O Kof não exige Java anterior a 21 para seu tooling.
- O pacote oficial carrega sua própria JVM (Temurin 25).
- O toolchain do repo exige JDK 25 (D-BASELINE, 14/09): compilar o repo e rodar
  a CLI são JDK 25. O **API level** do tooling continua 21
  (`KofVersion.TOOLING_API`) e programas Kof ainda rodam em JVM 21+.

Esta decisão está documentada em [docs/tooling/README.md](../tooling/README.md)
e é reportada por `kof info` (`Tooling API: 21`).

---

## 4. Isolamento e Portabilidade

O layout de distribuição garante:

| Propriedade | Como |
|-------------|------|
| Isolamento | Nada é instalado fora do diretório do Kof |
| Portabilidade | Caminhos relativos entre `bin/`, `lib/` e `jdk/` |
| Atualização simples | Substituir o diretório de instalação (ou extrair por cima) |
| Versionamento | `VERSION` + arquivos de versão empacotados |
| Reprodução | Build determinístico via Maven + scripts de empacotamento |

---

## 5. Multi-target preservado

A distribuição não muda a arquitetura de compilação:

```text
Kof Source
    │
    ▼
Frontend
    │
    ▼
Kof IR
    ├──────────► JVM
    ├──────────► Native
    ├──────────► Script
    └──────────► KofJS
```

O código Kof não é reescrito quando o target muda — **a linguagem é a mesma,
o backend muda**. Especialmente para o target Native, a complexidade de
memória (`malloc`, `free`, ponteiros, gerenciamento manual) é absorvida pelo
compilador/runtime, nunca exposta ao programador.

---

## 6. Instalação

### Pacote oficial (recomendado)

Baixe o pacote do seu sistema em
[GitHub Releases](https://github.com/KofLang/Kof4j/releases/latest)
(Linux `linux-x86_64` / macOS `macos-arm64` / Windows `windows-x86_64`).
O nome muda a cada release — o globo `*` evita depender da versão:

```bash
# Linux
tar -xzf kof-*-linux-x86_64.tar.gz
export PATH="$PWD/$(ls -d kof-*-linux-x86_64 | head -1)/bin:$PATH"

# macOS (Apple Silicon)
tar -xzf kof-*-macos-arm64.tar.gz
export PATH="$PWD/$(ls -d kof-*-macos-arm64 | head -1)/bin:$PATH"

# Windows (PowerShell)
Expand-Archive .\kof-*-windows-x86_64.zip
# adicione <pasta>\bin ao PATH
```

Guia completo por sistema: [INSTALL.md](INSTALL.md).

### Build de desenvolvimento

```bash
mvn clean package -DskipTests
bin/kof info
```

Verificar a integridade de um download:

```bash
sha256sum -c SHA256SUMS
```

---

## 7. Verificação

Depois de instalar:

```bash
kof version      # kof 0.5.0-beta (a versão da sua release)
kof info         # ambiente completo (JVM embutida aparece com "(embedded)")
kof run hello.kf
```