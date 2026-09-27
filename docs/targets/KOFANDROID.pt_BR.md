[English](KOFANDROID.md) | [Português](KOFANDROID.pt_BR.md)

# KofAndroid — o target Android da Kof

> **Status: Fases 1-4 implementadas (Fase 3 = responsividade, Fase 4 = `--min-sdk`/`--target-sdk`, 17/09).**
> `kof build --target android` gera o
> projeto Maven + APK pipeline com o host Activity escrito EM KOF
> (`dev/kof/android-host.kf`) — zero Java, zero Kotlin, zero Gradle no
> projeto gerado; dependências resolvidas pelo Kof (ExternalClasspath).
> Fase 2: label/permissões derivados do programa, `--apk` standalone
> (aapt2/d8/apksigner direto do CLI) e release signing `--keystore`.
> Fase 3: o WebView renderiza na largura do aparelho (`<meta viewport>` +
> `setUseWideViewPort`) com um bloco CSS para tela estreita — ver abaixo.
> A base de compilador que isso exige está funcional: herança de classes
> externas, `super(...)`/`super.metodo()` com INVOKESPECIAL correto,
> chamadas encadeadas em receivers externos, construtores e campos
> externos, annotations emitidas no bytecode.

## O que é

`kof-android` é o target que transforma um programa Kof em um **aplicativo
Android instalável** — APK/AAB — mantendo a promessa central da linguagem:

> **A linguagem não muda. O target muda.**

O mesmo `.kf` que abre uma `Window` no desktop abre um app no celular:

```kof
main() {
    var w = Window("Contador")
    var label = Label("contagem: 0")
    w.bind(label)
    w.bind(Button("+1", () -> {
        label.text = "contagem: " + App.count
    }))
    w.show()
}
```

Nada de `Activity`, `Intent`, `LayoutInflater`, XML de layout ou
`findViewById` no código do usuário. Se é essencial para qualquer programa,
pertence à plataforma (linguagem + compilador + runtime) — nunca ao
mecanismo vazando na intenção.

## Por que NÃO é um novo compilador

Android não tem linguagem própria: o ART executa **bytecode convertido para
dex**. O pipeline reaproveita tudo o que existe:

```text
                    Kof Source
                         │
                         ▼
                 ┌──────────────┐
                 │ Kof Frontend │   (um só: lexer/parser/tipos/IR)
                 └──────┬───────┘
                        ▼
                    Kof IR
                        │
                ┌───────┴────────┐
                ▼                ▼
          JvmBackend        validações AND*
          (.class bytecode)
                │
        ┌───────┼──────────────────┐
        ▼       ▼                  ▼
   d8/dex    AndroidManifest   projeto/pacote
   (dx)      sintético         (Gradle fase 1;
   │                            aapt2+d8+apksigner fase 2)
        ▼
     APK/AAB → instalação → ART
```

**Não é transpilar para Java.** É o mesmo bytecode do backend JVM, com
restrições e pós-processamento próprios do alvo — a mesma relação que
KofJS tem com o frontend único.

## Modelo de execução do kof.ui

Hoje o `kof.ui` renderiza widgets como **DOM via KofJS** no webview nativo
do desktop (WebKitGTK embutido). O Android já traz um WebView maduro
(`android.webkit.WebView`). A realização por target, sem mudar o código:

```text
Window/Label/Button/Input/Column/Row/View/Style
        │  (mesma IR, mesmos handles Int)
        ▼
MainActivity (sintetizada pelo target)
  └── WebView (fullscreen, JS habilitado)
        └── engine embarcada carrega o .mjs do programa
              └── widgets → DOM (mesma camada de render do desktop)
```

A `MainActivity` é escrita **em Kof** (`dev/kof/android-host.kf`) e
compilada junto com o programa pelo mesmo frontend — o usuário nunca
escreve Activity em Java. Quem precisa de UI **nativa de verdade** usa
interop direta: com `ExternalClasspath` no classpath, tudo isto compila
hoje (ver [learn/10-inheritance.md](../../learn/10-inheritance.md)):

```kof
import android.widget.Button
import android.view.View

class MeuListener implements OnClickListener {
    Void onClick(View v) {
        println("clicado")
    }
}

// SAM conversion: lambda vira o listener direto
var b = new Button(this)
b.setOnClickListener((v) -> println("clicou"))
b.setOnLongClickListener((v, n) -> println("long " + n))
var i = Button.inflate(this)      // método estático externo
if (i instanceof View) { ... }
var c = i as Button               // cast externo qualificado
b.clicks = 5                      // campo externo (leitura/escrita)
```

## Ciclo de vida e convenções

| Intenção | Como o usuário escreve | O que o target faz |
|----------|------------------------|--------------------|
| app de UI | `main()` com `Window(...)` | sintetiza host Activity + WebView |
| componente Android | `class MinhaTela extends android.app.Activity` | respeita a hierarquia; exige assinaturas reais via classpath |
| metadado de framework | `@Override`, `@NonNull`, ... | emite RuntimeVisible/Invisible no bytecode |
| ponto de entrada lógico | `main()` | continua existindo — teste a lógica com `kof test` em `jvm`/`js`; android é empacotamento, não alvo de teste (`kof test --target android` recusa com exit 1, R6) |

Regras de convenção (nenhuma configuração obrigatória):

1. **Pacote/aplicação**: derivado do `package` do arquivo; default
   `dev.kof.app`.
2. **Label/ícone**: label vem do título da primeira `Window`; ícone default
   do Kof (override futuro por metadado declarativo, não annotation).
3. **minSdk/targetSdk**: defaults conservadores fixados pelo target
   (ex.: minSdk 24); override por flag explícita do CLI, não arquivo mágico.

## Fases

### Fase 1 — implementada: pipeline Maven, código 100% Kof

`kof build app.kf --target android` produz:

```text
<output>/
├── pom.xml                          ← cola do pipeline SDK; NENHUMA <dependencies>
├── src/main/AndroidManifest.xml     ← dados da plataforma (label, launcher)
├── src/main/assets/kof/
│   ├── index.html, Default.mjs      ← saída KofJS do MESMO programa
│   └── kof-runtime*.mjs
├── libs/kof-app.jar                 ← bytecode: programa + host Activity EM KOF
└── README.txt
```

Pontos centrais:

- **Zero Java. Zero Kotlin. Zero Gradle.** A host `MainActivity` é escrita
  EM KOF (`kof-compiler/src/main/resources/dev/kof/android-host.kf`),
  compilada junto pelo mesmo frontend e vai no jar. O usuário que quiser
  um host próprio declara `class MainActivity extends Activity` em Kof —
  a versão embutida cede o lugar.
- **Dependências geridas pelo Kof**: assinaturas de `android.*` vêm do
  ExternalClasspath (o android.jar que o fluxo do projeto fornecer). O
  `pom.xml` não declara dependência nenhuma — ele só orquestra os
  binários oficiais do SDK nas fases do Maven (antrun puro):
  `d8 → aapt2 link -A assets → zipalign → apksigner`.
- Uso:

```bash
# só o projeto Maven:
kof build app.kf --target android --output app-android \
    --classpath $ANDROID_HOME/platforms/android-34/android.jar

# ou direto pro APK (standalone, sem Maven; precisa de build-tools >= 35 p/ classes Java 21):
kof build app.kf --target android --output app-android --apk \
    --classpath $ANDROID_HOME/platforms/android-34/android.jar
```

Permissões ficam NO CÓDIGO Kof — metadado consumido pelo target:

```kof
@Permissions(["android.permission.INTERNET", "android.permission.CAMERA"])
class MainActivity extends Activity { ... }
```

O label do app é a primeira `Window("...")` do programa. O ícone é
vetorial (`res/drawable/ic_launcher_kof.xml`) — nenhum binário gerado.

### Fase 2 — implementada (31/08): refinamentos

- ✅ **label derivado do programa**: título da primeira `Window("...")` vira
  `android:label` do manifesto (`AndroidProjectWriter.detectAppLabel`);
- ✅ **permissões declarativas**: `@Permissions([...])` numa classe Kof vira
  `<uses-permission>` no manifesto (`detectPermissions`);
- ✅ **modo standalone sem Maven**: `kof build --target android --apk` chama
  `aapt2 → d8 → zip → zipalign → apksigner` direto do CLI (build-tools >= 35 +
  `ANDROID_HOME`). Sem o SDK (`ANDROID_HOME` ausente, sem `aapt2`), a flag falha
  com **exit 1** e mensagem honesta — nunca exit 0 sem APK (R6); o projeto ainda
  é gerado, então `mvn verify` segue como alternativa;
- ✅ **release signing parametrizável**: `--keystore <ks> [--storepass <p>]
  [--keypass <p>] [--alias <a>]` — sem `--keystore`, mantém o debug keystore
  local gerado na primeira vez. As flags de assinatura/artefato (`--apk`,
  `--keystore`, `--storepass`, `--keypass`, `--alias`) são **só android**: em
  qualquer outro alvo, ou assinando sem `--apk`, o CLI recusa com exit 1 (R6)
  em vez de ignorá-las em silêncio;
- ícone: default vetorial do Kof (`res/drawable/ic_launcher_kof.xml`);
  override declarativo por metadado segue planejado (nenhum binário gerado).

### Fase 3 — implementada (17/09): responsividade

O host WebView agora renderiza a UI na **largura do aparelho** em vez do
viewport de layout de 980px do desktop:

- ✅ **`<meta viewport>` no `index.html` gerado** — `width=device-width,
  initial-scale=1, viewport-fit=cover` (`JsArtifactWriter.writeHtmlEntry`);
  `AndroidProjectWriter.patchIndexForPlatform` injeta defensivamente também
  num `index.html` customizado;
- ✅ **host liga o viewport largo** — `setUseWideViewPort(true)` +
  `setLoadWithOverviewMode(true)` no `dev/kof/android-host.kf`; sem os dois o
  WebView **ignora** a meta tag e a UI aparece encolhida;
- ✅ **CSS para tela estreita** — um bloco `@media (max-width: 600px)` quebra
  `.kof-row` e diminui o padding do titlebar/root, então a mesma intenção
  `Window`/`Column`/`Row` se adapta sem mudar o código `.kf`;
- `kofUiSerializeHtml` (caminho de export HTML) emite a mesma meta viewport.

Prova: `AndroidInteropE2ETest.androidResponsiveViewportAndWebViewWideViewport`.

### Fase 4 — implementada (17/09): versionamento do SDK por flag

- ✅ **`--min-sdk <n>` / `--target-sdk <n>`** no `kof build --target android`
  (flag explícita, nunca arquivo mágico). Os valores chegam ao
  `AndroidManifest.xml` gerado (`<uses-sdk>`), ao `pom.xml` (platform jar
  `android-<targetSdk>` + `d8 --min-api <minSdk>`) e ao pipeline standalone
  `--apk`. Defaults seguem 24/34; `min > target` e alvos não-android são
  recusados com diagnóstico honesto (R6).
  Prova: `AndroidInteropE2ETest.androidSdkOverrideThreadsToManifestPomAndReadme`
  + `CmdBuildAndroidSdkTest`.

### CI

`.github/workflows/android.yml` (manual `workflow_dispatch`) tem dois jobs:
`interop` (roda `AndroidInteropE2ETest` contra o SDK) e `emulator-smoke` (builda
o CLI, gera o projeto, `mvn verify` monta o APK e então instala/abre com
`android-emulator-runner`). Não está ligado a rodar a cada push.

### Pendente (Fases 5+, sem dono ainda)

- saída `--aab` (App Bundle p/ Play) — precisa de `bundletool` (não está no
  build-tools). A flag é reconhecida e recusada com diagnóstico honesto (R6) em
  vez de ser ignorada em silêncio; o projeto ainda é gerado.
- override declarativo do ícone por metadado — **decisão pendente** (o mecanismo
  `kof.toml [app] icon` vs flag `--icon` não está fechado).

## Restrições e gaps (diagnosticados em compile-time)

O contrato é o mesmo dos outros targets: **a intenção compila em todos os
alvos; o alvo que não consegue realizá-la diz isso na hora, com código.**

| Código | Situação | Motivo |
|--------|----------|--------|
| ~~`AND001`~~ | ~~`spawn { ... }`~~ | ✅ **fechado 31/08**: ART não tem virtual threads (Java 21), mas o runtime cai em **platform threads** quando `Thread.startVirtualThread` não existe — `spawn`/`await`/`cancel`/`cancelled`/`selectAny`/`awaitTimeout`/`channel`/`scheduler` compilam e rodam (bytecode: `CompletableFuture` + `new Thread` + `LinkedBlockingQueue`; KofJS do WebView: sequencial). `KofConcurrency2Test`/`AndroidInteropE2ETest` |
| `AND002` | `web.app()` / `kof.web` (servidor embutido) | ✅ **imposto em compile-time (17/09)** tanto no `kof build` quanto no `kof check --target android` (18/09): app mobile não escuta porta — o alvo recusa com `AND002` e aponta o interop, nunca emite código de servidor que não roda (R6) |
| `AND003` | reflexão sobre classes Kof via interop | *caveat, não gate de compile-time*: desugaring/R8 pode remover símbolos; a linguagem não tem superfície de reflexão própria, então não há o que o compilador detectar |
| `AND004` | android.jar ausente no ExternalClasspath | host Activity não incluída (warning) |
| `SECN00x` / `GPU001` | `kof.security` / `kof.gpu` | `kof.security` **corrigido 23/09** (`D-TECHDEBT-23/09` "portar as pilhas"): os shims do JVM são só JCA/`java.util`, então `KofSecurity.supportedOn` agora trata `ANDROID` como `JVM` — `crypto.sha256/sha512`, `crypto.hmacSha256`, `passwords.*`, `secrets.*`, cookies e JWT compilam limpos, `Main.class` byte-idêntico ao JVM (`KofSecurityTest.androidSecurityCompilesByteIdenticalToJvm`). `kof.gpu` **corrigido 24/09** (§278): Android compila como o JVM (`Main.class` byte-idêntico) e o runtime é o `JvmVkStubRuntime` sem FFM (`available()=false` + fallback CPU), front inalterado. `kof.db`/`kof.orm` eram over-gating e foram **corrigidos 20/09** (D-DB-GAPS DB-2) — `known-bugs.md` §278 FIXED |
| `SAM001` | aridade da lambda ≠ método SAM | interface externa exige N args |
| `SUP001` | `super.metodo()` no Native | já coberto; ANDROID reusa o caminho JVM |

Suportado no compilador: sobrecarga de **construtores** (despacho por
aridade), `super.metodo()` **dentro de lambdas** via captura `$outer` +
método-ponte `kof_super$*` na classe dona, valores `Classe.class` e
enum (`@Anno(Pkg.Enum.CONST)`) resolvidos pelo classpath. Ainda faltando:
sobrecarga de métodos comuns, checagem de generics externos.

Bytecode: o JvmBackend emite nível moderno; o `d8` faz desugaring para
dispositivos antigos. Se a Fase 2 precisar de nível menor, a flag vira
parâmetro do backend — não um segundo backend.

## Integração com o trabalho em andamento

- **ExternalClasspath** (Gradle → `.jar`/`.aar`): fonte das assinaturas
  para INVOKESPECIAL exato em `super.metodo()` contra `android.*`;
  warnings `CP002` para entradas ilegíveis.
- **Annotations**: `@Override`/`@NonNull`/androidx já emitidos com
  retenção correta; frameworks Android que leem metadata em runtime
  continuam funcionando.
- **kof.ui**: nenhum widget novo; o WebView host substitui o WebKitGTK
  desktop. `Palette`/`Theme` seguem idênticos.

## Roadmap resumido

1. `Target.ANDROID` no enum + dispatch no CLI (`--target android`) com as
   validações `AND*` antes da emissão (reuso do JvmBackend).
2. Gerador do projeto Maven (pom.xml, zero Gradle) + manifesto + assets
   (`AndroidProjectWriter`).
3. Host Activity + bridge WebView ↔ handles do `kof.ui` (reusar runtime.mjs).
4. E2E: build → assembleDebug em CI com emulator smoke test.
5. Fase 2: standalone aapt2/d8/apksigner.

## Próximo passo

Comparativo de filosofia entre backends:
[KOFJS.md](KOFJS.md) · interop Java/Android:
[../../learn/21-java-interoperability.md](../../learn/21-java-interoperability.md)
