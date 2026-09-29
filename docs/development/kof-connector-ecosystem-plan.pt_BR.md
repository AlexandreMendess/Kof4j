[English](kof-connector-ecosystem-plan.md) | [Português](kof-connector-ecosystem-plan.pt_BR.md)

# Interoperabilidade Kof — Ecossistema de Connectors

**Status:** EM DESENVOLVIMENTO — promovido `future/` → `docs/development/` por `D-CONNECTORS-GO` (mantenedora 29/09/2026)
**Local:** `docs/development/kof-connector-ecosystem-plan.md`
**Natureza:** arquitetura, contratos, dependências, estratégia de implementação, critérios de promoção
**Fonte normativa:** `DECISIONS.md` §`D-CONNECTORS-GO` (DECIDIDO — promoção autorizada)
**Dependências principais:** R3 / FFI-ABI (`docs/ffi-abi-structs.md`), o caminho de interop JVM
(`ExternalClasspath`/`JdkReflectionResolver`), `kof.process`/`kof.shell`/`kof.ssh`,
KofJS, os backends Native, `kof.toml`/`kofdeps`
**Estado de implementação:** fatia 1 = leitor de manifest de connector (pure-Kof `libs/interop/`) — ver §9

> **Regra fundamental.** Este documento descreve uma direção arquitetural futura. Ele **não**
> altera a linguagem, não adiciona palavras-chave, não cria namespaces e não abre trilha de
> implementação. Toda sintaxe mostrada é uma **forma de intenção**; a forma definitiva pertence
> à mantenedora.
>
> **KOF-first (regra 10).** Nenhuma linguagem, runtime ou ABI externa é oráculo da Kof. O
> ecossistema de connectors é construído a partir do modelo de tipos/ABI da própria Kof;
> pesquisa externa (ELF/Mach-O/PE, CPython C-API, JNI/FFM, `repr(C)`, …) contribui com
> invariantes e trade-offs, nunca com sintaxe ou semântica copiada.
>
> **Kof não é Java/Frankenstein (regras 8/45).** A interoperabilidade acontece **através do
> modelo de tipos e APIs da Kof**. A Kof nunca embute sintaxe estrangeira; a linguagem continua
> sendo Kof.

---

# 0. Objetivo e não-objetivos

## 0.1 Objetivo

A Kof já funciona dentro de sistemas Java por meio de integrações improvisadas. A necessidade
é real:

> **A Kof precisa conseguir entrar em sistemas existentes sem exigir que o sistema inteiro
> seja reescrito em Kof.**

Este plano transforma essas integrações improvisadas em uma **arquitetura oficial,
consistente, documentada e extensível**: um Interop Core mais connectors. Suporta as duas
direções:

```text
Sistema existente (Java, Python, Rust, C, C++, C#, Go, Kotlin, Swift, JavaScript/TypeScript, Ruby, PHP, Lua, Dart, Scala, R, Julia, COBOL, Pascal, Fortran, …)
        │
        ▼                    Kof            (a Kof chama o ecossistema externo)
   Kof Connector   ───────────────▶  função/biblioteca externa
        │
        ▼                    Kof            (o ecossistema externo chama a Kof)
       Kof
```

A interoperabilidade é **bidirecional sempre que tecnicamente possível**.

## 0.2 Não-objetivos

* Não é uma camada de "executa processo e lê stdout" como mecanismo principal (isso já existe
  como `kof.process`; é um connector, não o modelo).
* Não são 30 connectors independentes com regras próprias de marshalling/lifecycle/erro.
* Não é embutir sintaxe estrangeira ("sintaxe Java dentro da Kof", "sintaxe Python dentro da Kof").
* Não é mapear automaticamente toda a dinâmica de uma linguagem externa para o type system da Kof.
* Não é prometer estabilidade de ABI antes de existirem testes de compatibilidade.

---

# 1. Princípio arquitetural — um Interop Core, não connectors isolados

Não criar dezenas de connectors completamente independentes. Criar primeiro a
**infraestrutura comum**, depois cada connector é uma implementação fina dela.

```text
                     Kof Interop Core
                            │
           ┌────────────────┼────────────────┐
          ABI              API              FFI
           └────────────────┼────────────────┘
                            │
                     Connector SPI
                            │
   ┌──────────┬─────────┬───┴────┬──────────┬─────────┐
 Java      Python     Rust      C        C++      … outros
```

O Core é dono, uma única vez, do que nenhum connector pode re-inventar:

* marshal de tipos;
* lifecycle;
* ownership;
* gerenciamento de memória;
* propagação de erros;
* callbacks;
* resolução de símbolos;
* metadados de ABI;
* versionamento;
* diagnósticos.

Um connector implementa apenas o **adapter específico da linguagem** sobre esse substrato.

---

# 2. O que já existe (reusar — nunca duplicar)

A regra 54 exige inventário antes de qualquer código. A tabela abaixo é o ponto de partida
honesto; o plano **evolui esses mecanismos** em vez de construir uma segunda implementação.

| Mecanismo existente | Âncora real | Papel no ecossistema |
|---|---|---|
| FFI `extern` + códigos de assinatura | `docs/ffi-abi-structs.md`; `compiler/FfiSignature.java`; `compiler/CompilerFfiBinding.java` (`FFI001`/`FFI002`) | O substrato de ABI sobre o qual os connectors são construídos |
| Motor de layout ABI | `compiler/AbiLayout.java` (SysV-x86_64 / AAPCS64 / RISCV64 size-align-argclass); `compiler/FfiStructLayout.java` | Convenção de chamada + layout de struct/array — **não** inventar uma segunda ABI |
| Runtime FFI da JVM (FFM) | `compiler/jvm/JvmFfiRuntime.java` (`SymbolLookup.libraryLookup`, downcall/upcall, struct read/write, buffer in/out) | Chamadas externas no lado JVM |
| Runtime FFI Native | `compiler/nat/NativeFfiCall.java`, `NativeFfiCallRiscv.java`; política de link `NativeAssembler.java` / `NativeCrossLink.java` | Chamadas externas no Native (link-by-use, `call sym@PLT`) |
| Ponte FFI do JS | `kof-runtime/.../KofJsFfiBridge.java`, `KofJsFfiMarshal.java` | Chamadas externas no host JS |
| Resolução de classes Java | `compiler/ExternalClasspath.java`, `compiler/JdkReflectionResolver.java`, `JavaLangProbe.java`, `ExternalCtorTyper.java` | O resolvedor em compile-time do connector Java |
| Interop por processo/shell/ssh | `KofProcess.java`, `KofShell.java`, `KofSsh.java`; `runtime/RuntimeProcess.java`, `RuntimeShell.java`; JS `KofJsProcessBridge`; gap honesto `PROC001` | O connector de "processo" (protocolo stdout), não o modelo inteiro |
| Contrato de ABI do runtime | `docs/runtime/RUNTIME_ABI.md`, `ARRAY_MODEL.md`, `STRING_MODEL.md` (payload @24) | Layout de objeto/array/string para handles e buffers |
| GC + alocador | `runtime/RuntimeGc.java` (mark/sweep), `runtime/RuntimeMemory.java` (`kof_alloc`/`kof_free`) | Suporte a ownership/lifetime no Native |
| Sistema de pacotes | `KofProjectConfig.java` (`kof.toml`), `ProjectLocator.java`; `Deps.java`/`DepsRegistry.java` (`kofdeps`) | Manifest do connector + distribuição |
| Seleção de alvo | `Target.java`, `TargetMatrix.java`, `CompilerPipeline.java:193` | Qual connector/runtime é válido por alvo |
| Dispatch da CLI | `kof-cli/.../Main.java:17` (`switch` do comando); precedentes `CmdNew.java`, subcomandos `publish`/`Deps` | Onde `kof connector init` entra |
| Reflexão de interop (X6) | `compiler/CompilerInterop.java`, `docs/type-system-extensions-plan.md`; `training/idioms/interop.md`, `learn/21-java-interoperability.md` | Ponto de entrada existente de reflexão de superfície |
| Motores de script como connector de PROCESSO (X2) | resources `dev/kof/interop-py-host.kf` / `interop-r-host.kf` (`KofPy`/`KofR`: faces tipadas + `callJson`, protocolo de 2 linhas KOFOK/KOFERR, `INTEROP004`/`005`/`006`) | Os conectores scripting de §5.5/§5.6 na forma de processo — LANDED 26/09 (X2); a rota embedding/CPython-C-API/R-C-API segue nao-implementada |

> **Conclusão:** o substrato (ABI, helpers de marshal, runtime por alvo, resolução de classes)
> em grande parte já existe. A peça que falta é a **camada unificadora**: o Core, a Connector
> SPI, o manifest, o versionamento e os contratos de ownership/erro de nível de linguagem.

---

# 3. O Interop Core

## 3.1 Modelo de tipos de interop (ABI oficial)

Definir uma única representação oficial para tipos interoperáveis, compatível com os alvos
que já existem (JVM, C ABI/Native, JS; WASM quando existir). Tipos a cobrir:

| Conceito Kof | Tipo de interop | Observações |
|---|---|---|
| `Int`/`Long`/`Byte`/`Short` | integer | largura/sinal explícitos; SysV/AAPCS/RISCV64 via `AbiLayout` |
| `Float`/`Double` | ponto flutuante | IEEE-754; NaN/Inf preservados (regra 5 cross) |
| `Bool` | boolean | mapeamento para `_Bool`/int por connector |
| `Char` | code point / largura | declarado por connector |
| `String` | view de string | ver §3.3 (UTF-8 / UTF-16 / NUL-terminated / por length) |
| `Buffer` | buffer de bytes | `KofBuffer.java` já modela `Buffer(U8)` |
| `List<T>` | array | ponteiro+tamanho (emprestado ou dono, §3.2) |
| `record`/classe | struct/handle | ver §3.4 |
| enum | inteiro/enum | |
| função | function pointer | ver §3.5 |
| lambda/callback | callback | ver §3.6 |
| recurso opaco | handle opaco | precedente `Handle` boxed `Long` (`KofProcess.java`) |
| nullable | nullable/referência | explícito; nunca silencioso |
| erro | result/error | ver §3.7 |
| objeto da heap externa | object handle | ciente de GC/lifetime |

O modelo **não pode** inventar uma ABI incompatível com os alvos existentes. `FfiSignature`
(códigos de caractere), `FfiStructLayout` (`kof.ffi/struct`, `kof.ffi/array`) e `AbiLayout`
são a representação inicial; novos tipos (handle, result, view de string) a estendem, e toda
extensão é decisão de regra 6.

## 3.2 Ownership e memória (o contrato central)

Para cada travessia, afirmar explicitamente:

```text
quem aloca?   quem libera?   quem possui?   quando pode liberar?
quem pode modificar?   quem mantém referência?
```

Conceitos comuns, independentes de connector:

```text
owned      o receptor deve liberar
borrowed   válido apenas durante a chamada
shared     contado por referência / gerenciado por GC de um lado
opaque     nunca inspecionado; devolvido ao dono
immutable  não pode ser mutado pelo lado externo
mutable    pode ser mutado; contrato declarado
```

Regras:

* Nenhum connector inventa sua própria regra de ownership — o Core a define e o connector
  declara quais conceitos suporta.
* Quando uma linguagem não expressa um conceito (borrows do Rust, ARC do Swift, refcount do
  Python), usar **handles/wrappers** com lifetime explícito e documentado, nunca fallback
  silencioso.
* JVM: alocações FFI confinadas em `Arena` (D6-5) seguem sendo a regra da JVM; Native: o
  GC/alocador da Kof (`RuntimeGc`/`RuntimeMemory`) governa a memória de dono Kof.
* Decisões de ownership/lifetime que mudam a semântica da Kof são **regra 6** — plano, não
  edição silenciosa.

## 3.3 Strings

Interop explícita para strings, com a view mais barata e segura:

```text
Kof String
   ├── view UTF-8          (C, Rust &str, Go string, …)
   ├── view UTF-16         (JVM, JS, C#/CLR, Objective-C/NSString)
   └── buffer nativo       (mutável, NUL-terminated ou por length)
```

* Suportar UTF-8, UTF-16, NUL-terminated, por length, strings imutáveis e buffers mutáveis.
* Evitar cópias desnecessárias quando for seguro; quando uma cópia for necessária, **a camada
  de interop sabe disso** e documenta (§3.10).
* O layout de string da própria Kof é normativo (`docs/runtime/STRING_MODEL.md`); as views são
  mapeadas sobre ele.

## 3.4 Structs e tipos compostos

Permitir representação interoperável sem conversão manual campo a campo:

```text
Kof record User(Int id, String name)
        ↕
C struct  ·  Rust #[repr(C)] struct  ·  Java record/class
C# record  ·  Go struct  ·  Swift struct
```

* A forma real na Kof usa a gramática atual: `record` (imutável) ou `class` mutável com
  `constructor(...)`. **Nada de sintaxe importada.**
* O layout segue `AbiLayout`/`FfiStructLayout`; padding/alinhamento fazem parte do contrato.
* Formas não suportadas (enums Rust com dados, classes C++, interfaces Go) falham com
  diagnóstico claro — nunca layout adivinhado.

## 3.5 Funções, chamadas e o foreign module

Mecanismo oficial para declarar/importar/exportar funções:

```text
função estrangeira → símbolo Kof → chamada Kof
função Kof         → símbolo externo → Java/Python/Rust/C/…
```

Suportar: argumentos, retorno, callbacks, variádicas quando suportado, async quando
suportado, erros, lifecycle. (Nota: FFI variádica hoje não é suportada — `DECISIONS.md`
§`D-R3-3.5`; um connector pode declará-la como gap diagnosticado.)

Introduzir um conceito oficial de **foreign module**, independente de connector específico:

```text
foreign module = biblioteca + símbolos + tipos + funções + ownership + ABI
```

Essa é a abstração que o Core consome e os connectors populam.

## 3.6 Callbacks

Suportar, quando tecnicamente possível, as duas direções:

```text
biblioteca externa → callback → função Kof
Kof                → callback → runtime externo
```

Os mecanismos diferem e **não** são fingidos iguais — uma abstração comum mais adapters
específicos: function pointers C, interfaces JVM (upcalls via FFM), callables Python, funções
`extern "C"` do Rust, delegates C#, funções exportadas Go, closures Swift, funções JS.
Afinidade de thread, reentrância e lifetime fazem parte do contrato de callback.

## 3.7 Erros e exceções

Uma única camada comum capaz de representar:

```text
success · failure · código de erro · mensagem de erro · payload de erro
exceção estrangeira · stack/contexto quando disponível
```

Mapeamentos por connector, sem perder o erro estrangeiro:

```text
exceção Java      → erro de interop Kof
Rust Result::Err  → erro de interop Kof
código de erro C  → erro de interop Kof
```

Um erro estrangeiro nunca deve ser engolido em silêncio (R6).

## 3.8 Carregamento de bibliotecas

Uma abstração reutilizável de carregamento:

```text
.so  ·  .dylib  ·  .dll   (+ bibliotecas estáticas quando aplicável)
```

* JVM/JS: `SymbolLookup.libraryLookup` (já em `JvmFfiRuntime`/`KofJsFfiBridge`).
* Native: link-by-use (`NativeAssembler`/`NativeCrossLink`); `dlopen`/`dlsym` reservados (hoje
  usados só pelo caminho Vulkan `RuntimeVk.java`/`VkChain64Loader.java`).
* O loader é infraestrutura do Core: um caminho, reusado por todos os connectors.

## 3.9 Versionamento e estabilidade de ABI

A interop não pode depender só da versão da linguagem. Rastrear e validar:

```text
versão Kof · versão do Connector · versão da linguagem externa · versão do runtime externo
versão de ABI · ABI da plataforma · versão do compilador
```

* Classificar cada interface como `stable`, `experimental` ou `internal`.
* Não prometer estabilidade de ABI antes de existirem testes de compatibilidade.
* Testes de compatibilidade devem validar nomes de símbolos, convenção de chamada, layout de
  tipos, alinhamento, layout de struct, compatibilidade binária e ownership.
* Combinação não suportada deve produzir **diagnóstico claro** (R6).

## 3.10 Nada de custos escondidos

A API de interop deve deixar visível quando existe: cópia · conversão · alocação · travessia
de runtime · troca de thread · serialização · boxing · interação com GC. Interop conveniente
não pode significar comportamento invisível e imprevisível. Todo custo é documentado e, quando
possível, observável (diagnóstico ou métrica).

---

# 4. Connector SPI e manifest

## 4.1 Connector SPI

Cada connector implementa apenas os adapters que o Core não fornece, atrás de uma interface
de serviço estável (hooks de marshal, lifecycle, mapeamento de erro, ponte de callback,
resolução de símbolo, declaração de ABI). Adicionar um connector **não** pode exigir mudar o
core do compilador; o compilador o descobre pela SPI.

## 4.2 Manifest do connector

Um manifest declarativo, consistente com as convenções existentes da Kof (`kof.toml`,
`kofdeps`):

```text
name
language
version
abi
platforms
runtime
dependencies
capabilities
```

Declara linguagens, versões, alvos, ABI, requisitos de runtime, bibliotecas, tipos
suportados, suporte a callback e modelo de ownership. O formato real deve seguir o formato
existente de projeto/pacote — não um novo inventado aqui.

---

# 5. Catálogo de connectors

Agrupado por **mecanismo**, não por marca. Cada entrada declara sua rota, seus limites
honestos e se é planejado, avaliado ou fora de escopo. Os valores da matriz do §6 são
**descobertos na implementação, nunca supostos**.

## 5.1 Família JVM (um substrato, adapters onde a semântica diverge)

```text
Kof JVM Interop
   ├── Java
   ├── Kotlin
   └── Scala   (+ Groovy, Clojure avaliados)
```

* **Java (`kof-java-connector`) — primeiro connector.** Reusa `ExternalClasspath`,
  `JdkReflectionResolver`, `JvmFfiRuntime`. Suporta classes, métodos, construtores, métodos
  estáticos, campos quando apropriado, interfaces, callbacks, exceções, arrays, primitivos,
  objetos, generics quando há representação segura, bibliotecas JVM e módulos Kof consumidos
  por Java. Avaliar geração de bindings.
* **Kotlin (`kof-kotlin-connector`).** Adapter fino sobre o substrato JVM; manter explícitas
  nullability/extension semantics onde divergirem do Java.
* **Scala (`kof-scala-connector`).** Não reduzir Scala a Java se isso perde semântica
  (objects, traits, collections, tipos específicos de Scala). Adapter, não alias.

## 5.2 Família C ABI (a rota nativa fundamental)

* **C (`kof-c-connector`) — segundo connector, fundamental.** Headers, C ABI, structs,
  pointers, arrays, strings, function pointers, callbacks, bibliotecas compartilhadas/estáticas
  (`.so`/`.a`/`.dll`/`.lib`/`.dylib` por alvo). Avaliar geração header→binding (`kof-c-compiler`
  já emite um subconjunto C e é alvo cross para fixtures).
* **C++ (`kof-cpp-connector`).** Não é "C com classes": name mangling, ABI, namespaces, classes,
  ctors/dtors, templates, exceções, STL, smart pointers. Preferir uma **camada C ABI estável**;
  documentar honestamente os limites de ABI entre toolchains.
* **Rust (`kof-rust-connector`).** `extern`, `repr(C)`, ownership/borrowing, handles opacos,
  `Result`, fronteiras de panic, callbacks. Nunca expor tipos Rust arbitrários como se fossem C ABI.
* **Zig (`kof-zig-connector`).** Próximo de interop C/sistemas; C ABI, funções exportadas,
  structs, pointers, interação com allocator.
* **Go (`kof-go-connector`).** cgo, C ABI, funções exportadas, bibliotecas compartilhadas,
  callbacks, fronteiras goroutine/thread, ownership de memória. Respeitar as regras do runtime
  Go — nunca uma abstração que as viole.
* **Nim / D (`kof-nim-connector`, avaliado).** Mecanismos nativos de interop onde sustentável.

## 5.3 Gerenciado / .NET

* **C# (`kof-csharp-connector`).** .NET/CLR, P/Invoke, interop nativa, objetos gerenciados,
  delegates, exceções, assemblies. Avaliar Kof→.NET e .NET→Kof.
* **F# / VB.NET** avaliados via o mesmo substrato.

## 5.4 Apple

* **Swift (`kof-swift-connector`).** Interop Swift/C, ABI Swift, interop Objective-C, structs,
  classes, closures, ARC, ownership; camada C ABI documentada quando o direto for inseguro.
* **Objective-C (`kof-objectivec-connector`).** Runtime, messaging, `NSObject`, blocks, ARC,
  C ABI, headers — para o ecossistema Apple existente.

## 5.5 Scripting / dinâmicas

* **Python (`kof-python-connector`).** CPython, Python C API, embedding, extension modules,
  bibliotecas nativas, objetos, callables, exceções, buffers. Tratar o lifecycle do runtime
  Python e a GIL explicitamente — Python **não** é uma biblioteca nativa comum. A forma
  **processo** landed 26/09 como o motor X2 `KofPy` (linha do inventário em §2); embedding/C-API
  segue nao-implementada — essa é a forma-objetivo deste item, nao a landed.
* **JavaScript / TypeScript (`kof-javascript-connector` / `kof-typescript-connector`).** O KofJS
  já existe: torná-lo integração oficial (`Kof → KofJS → runtime JS`), não duplicar. Futuro:
  `Kof → KofWasm → host JS`; o mesmo módulo Kof deve rodar em ambos quando semanticamente compatível.
* **Ruby (`kof-ruby-connector`).** Ruby C API, extensões nativas, embedding, objetos, exceções.
* **PHP (`kof-php-connector`).** Extensões PHP, Zend API, FFI, embedding, lifecycle — preferir
  mecanismos estáveis/oficialmente suportados.
* **Lua (`kof-lua-connector`).** Lua C API, userdata, tables, funções, callbacks, embedding —
  especialmente para uso embutido/scripting.
* **Dart (`kof-dart-connector`).** Dart FFI, extensões nativas, isolates, callbacks, memória.

## 5.6 Científico

* **Fortran (`kof-fortran-connector`).** `ISO_C_BINDING`, C ABI, arrays, tipos numéricos,
  convenções de chamada, bibliotecas legadas (HPC/científico).
* **Julia (`kof-julia-connector`).** Julia C API, embedding, bibliotecas nativas, arrays,
  callbacks, objetos. Não mapear automaticamente toda a semântica dinâmica de Julia.
* **R (`kof-r-connector`).** Embedding, extensões nativas, interface C, vectors, data frames,
  callbacks (dados científicos). A forma **processo** landed 26/09 como o motor X2 `KofR`.
* **MATLAB/Octave (`kof-matlab-connector`, `kof-octave-connector`, avaliados).** Interfaces
  nativas, bibliotecas compartilhadas, C ABI, extension APIs onde oficialmente sustentável.

## 5.7 Funcional / específica de runtime

* **Haskell (`kof-haskell-connector`, quando sustentável).** GHC FFI, C ABI, funções exportadas,
  inicialização de runtime, callbacks. Não mapear lazy evaluation para o modelo de execução da Kof.
* **Erlang/Elixir (`kof-erlang-connector`, `kof-elixir-connector`, avaliados).** BEAM NIFs,
  ports, interfaces nativas, message passing, fronteiras de processo. Respeitar a concorrência
  do BEAM — não transformar chamadas Kof em síncronas se isso quebrar as propriedades da plataforma.
* **OCaml** avaliado via sua C ABI.

## 5.8 Legado

* **COBOL (`kof-cobol-connector`).** Um **caso de integração de legado, tratado a sério**:
  C ABI, runtime nativo, convenções de chamada, bindings gerados, bibliotecas compartilhadas,
  o runtime específico do compilador COBOL. Objetivo: um sistema COBOL existente consumir
  funcionalidade Kof — não reescrever COBOL. Records/códigos de erro passam pelo Core.
* **Pascal (`kof-pascal-connector`).** Free Pascal primeiro (baseline open-source), depois
  Object Pascal/Delphi onde tecnicamente possível: C ABI, bibliotecas compartilhadas,
  convenções de chamada, records, pointers, strings.

## 5.9 Extensibilidade

A lista é uma **direção**, não uma exigência de 30 runtimes (regra 55). Primeiro provar a
arquitetura com poucos connectors; cada novo connector é consequência da arquitetura, não uma
nova gambiarra. O catálogo é agrupado para que novas linguagens caiam numa família de mecanismo
existente. Uma matriz de linguagens/mecanismos (JVM · Native/Systems · .NET · Apple · Scripting ·
Científico · Funcional · Legado) rastreia candidatos conforme forem avaliados.

---

# 6. Matriz de capacidades dos connectors

Criar documentação com uma matriz; **descobrir os valores na implementação, nunca preenchê-los
por suposição**:

```text
Linguagem | Kof → Lang | Lang → Kof | ABI   | Callbacks | Structs | Errors | Ownership
Java      | ...        | ...        | JVM   | ...       | ...     | ...    | managed
C         | ...        | ...        | C     | ...       | ...     | ...    | manual
Rust      | ...        | ...        | C     | ...       | limited | Result | explicit
Python    | ...        | ...        | C/API | ...       | objects | exc.   | runtime
COBOL     | ...        | ...        | ABI   | limited   | records | codes  | manual
```

Cada connector implementa apenas o subconjunto que a linguagem permite; capacidades que não
existem são declaradas como gaps honestos (`XXX00x`), nunca fingidas.

---

# 7. Geração de headers / bindings

Avaliar geração automática de bindings:

```text
header C         → gerador de binding Kof → API Kof
metadados Rust/Java/.NET/Python (depois)
```

Não implementar todos os geradores de uma vez. Começar por uma linguagem com interface
**formal e estável** — provavelmente C ABI. Bindings gerados devem ser determinísticos e
cobertos por testes golden; binding gerado que não se pode confiar não é entregue.

---

# 8. CLI — gerador de connector

Avaliar `kof connector init` (ou equivalente na forma existente da CLI):

```text
connector/
    manifest
    bindings
    runtime
    types
    tests
    docs
```

Gera a estrutura inicial de um connector para que a comunidade crie connectors sem alterar o
core do compilador. O lugar é o dispatch existente de `kof-cli` (`Main.java:17`), seguindo o
precedente dos subcomandos `kof new` / `kof deps`.

---

# 9. Fases (incremental)

| Fase | Escopo | Prova de saída |
|---|---|---|
| 1 · Interop Core | modelo ABI, tipos externos, ownership, modelo de erro, handles, chamadas, metadados, testes | Core documentado + testes unitários verdes; nenhum connector necessário |
| 2 · C ABI | connector C, bibliotecas dinâmicas, structs, pointers, callbacks | ida-e-volta Kof↔C nas duas direções |
| 3 · JVM | Java, depois adapters Kotlin/Scala | app Java chama módulo Kof; Kof chama biblioteca Java |
| 4 · Systems | Rust, C++, Zig, Go | cada connector prova uma integração real |
| 5 · Gerenciado/scripting | Python, C#, JS/TS, Ruby, PHP, Lua | ida-e-volta Python↔Kof e C#↔Kof |
| 6 · Científico | Fortran, Julia, R, MATLAB/Octave | ida-e-volta de array/record numérico |
| 7 · Legado | COBOL, Pascal/Delphi | um sistema legado real consome um módulo Kof |
| 8 · Funcional/runtime | Haskell, Erlang/Elixir, OCaml, outros | integração respeitando o runtime externo |

A ordem pode mudar após análise técnica; a fase 1 é pré-requisito de todas as outras.

---


## 9.1 Fatia promovida 1 (29/09/2026) — leitor de manifest de connector

**Estado:** definida; implementação = biblioteca pure-Kof `libs/interop/` sobre o formato
`kof.toml` existente (nunca um formato novo, §4.2), consumida por `ConnectorManifest(path)`:

- `name()` / `language()` / `version()` / `abi()` / `runtime()` → `String` (campo obrigatório
  ausente lança um `CONNECTOR: missing <field>` explícito — R6, nunca silencioso);
- `platforms()` / `dependencies()` / `capabilities()` → `List<String>` (vazio quando ausente);
- `hasCapability(String)` → `Bool`; `describe()` → resumo de uma linha.

**Como terminar:** pousar a biblioteca + um E2E cross-target (`ConnectorManifestE2ETest`) provando a
leitura + o diagnóstico de campo ausente na JVM/Native x86-64/riscv64(qemu)/Script (JS herda a
lacuna `TOML`/`IOJS001`). **Sem mudança no compilador** (library-first, `D-KOF-FIRST-IMPL`). É a
costura declarativa que todo connector compartilha; o Interop Core (Fase 1) e o connector C-ABI
(Fase 2) se apoiam nela.

---
# 10. Testes

Cada connector deve possuir testes em múltiplos níveis:

* **Unit** — mapeamento de tipos, metadados, geração de bindings, representação de ABI.
* **Integração** — `Kof → externo` e `externo → Kof`.
* **Runtime** — memória, callbacks, exceções/erros, concorrência, lifecycle.
* **Compatibilidade** — versões e combinações de ABI suportadas.
* **Negativos** — ABI incompatível, tipo incompatível, ownership inválido, símbolo inexistente,
  runtime ausente (devem produzir diagnóstico claro, R6).

**Suíte cross-language.** Um conjunto mínimo de operações que todo connector testa quando
suportado:

```text
valores primitivos · strings · arrays · structs · enums · pointers/handles
callbacks · erros · ownership de memória · threads · async · objetos opacos
```

Cada connector implementa o subconjunto que a linguagem permite. A suíte é o corpus golden do
ecossistema; testes de compatibilidade e negativos são portões, não extras.

---

# 11. Critérios de conclusão

A iniciativa é funcional quando:

* existe um Interop Core;
* o modelo de ABI/tipos está documentado;
* ownership está definido;
* erros estão definidos;
* callbacks são suportados quando possível;
* connectors são independentes do core do compilador;
* C ABI funciona;
* JVM interop funciona;
* pelo menos um runtime scripting funciona;
* existem testes bidirecionais;
* a documentação permite criar integrações reais;
* connectors podem ser adicionados sem modificar arbitrariamente o compilador;
* incompatibilidades produzem diagnósticos claros.

E principalmente:

> **um sistema existente consegue incorporar módulos Kof sem virar um projeto Kof inteiro.**

---

# 12. Regras de implementação

Antes de alterar qualquer código (regra 54):

1. analisar a arquitetura atual;
2. localizar os mecanismos existentes de FFI/ABI;
3. localizar o suporte JVM;
4. localizar o KofJS;
5. localizar o Native;
6. localizar o loading de bibliotecas;
7. localizar a representação de tipos;
8. localizar o gerenciamento de memória;
9. localizar o sistema de módulos/pacotes;
10. localizar os mecanismos existentes de linking.

**Não duplicar** mecanismos que já existem. Se uma capacidade já existir parcialmente,
evoluí-la para o Interop Core — nunca criar uma segunda implementação.

**Controle de escopo (regra 55).** A lista de linguagens é direção arquitetural, não exigência
de implementar todos os runtimes agora. Provar a arquitetura com poucos connectors, depois
adicionar linguagens incrementalmente. **Um connector nunca é criado para marcar checkbox** —
só está pronto quando uma integração real pode ser demonstrada (nas duas direções quando a
linguagem permitir).

**Lei da Simplicidade (regra 11).** Tudo que chega à superfície da linguagem deve ser
extremamente simples, curto, idiomático e representante de intenção. Conveniência de interop
não pode importar cerimônia estrangeira para dentro da Kof.

---

# 13. Decisões abertas (regra 6 — a mantenedora decide)

Os itens abaixo **não** são decisão de agente; precisam ser travados no `DECISIONS.md` antes da
frente abrir:

* **D-CONNECTORS** — abrir a frente e seu escopo ordenado.
* O **vocabulário de ownership** e se algo dele chega à superfície da linguagem.
* Se o modelo de erro de interop é um tipo na linguagem ou interno ao Core.
* Se/quando uma declaração `foreign module` entra na gramática.
* Tiers de estabilidade de ABI e a primeira versão estável de ABI.
* Qual connector é o segundo caso oficial depois do Java.
* Roteiro de promoção: `future/` → `docs/development/` **FEITO 29/09/2026** (`D-CONNECTORS-GO`, mantenedora) — a fatia 1 (leitor de manifest) é a primeira fatia (§9.1); (linha original: quando a primeira fatia de connector
  landar (regra dos três estados + R12, salvo sobreposição da mantenedora).

---

# 14. Relação com outros planos

* `docs/ffi-abi-structs.md` — o substrato de ABI; este plano o consome, nunca o redefinir.
* `docs/development/future/graphics-gaming-plan.md` — a exceção nomeada ao R9 (engine própria);
  este plano fornece a camada FFI só para a superfície não-engine.
* `docs/development/future/PLAN-BOOTSTRAP.md` — E4 exige FFI structs ratificados; um Connector
  Core maduro fortalece o caminho do bootstrap.
* `docs/development/future/LEGACY_MIGRATION.md` / `TRANSLATOR.md` / `DECOMPILER.md` — a frente
  de legado compartilha a motivação de integração legada (COBOL/Pascal/Fortran); despriorizada
  separadamente.
* `docs/bugs-and-gaps/ecosystem-coverage.md` §3.15 — linhas de cobertura de interoperabilidade
  que este plano pode eventualmente alimentar.

---

# 15. Fora de escopo / não-promessas

* Sem datas e sem estimativas de esforço — planejar não é fila.
* Nenhuma mudança de linguagem é decidida aqui; toda mudança necessária é registrada como gap
  normal e independente de connector, e decidida pela mantenedora (regra 6).
* Nenhuma promessa de estabilidade de ABI antes de existirem testes de compatibilidade.
* Nenhuma sintaxe estrangeira embutida na Kof; nenhuma "linguagem Frankenstein".
* Nenhuma exposição automática de tipos externos complexos como se fossem ABI estável.
* Nenhum fallback silencioso, stub ou custo escondido (R6/Q7): caminhos não suportados falham
  com diagnóstico claro.
