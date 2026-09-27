[English](41-android.md) | [Português](41-android.pt_BR.md)

# 41 — Android

> **Status: implementado — `--target android` emite bytecode JVM compilado
> para o ART (sem FFM em runtime) com uma host Activity em Kof
> (`android-host.kf`), empacotado pela pipeline própria do CLI
> (aapt2/d8/zipalign/apksigner — build-tools ≥ 35 travado). Sem SDK, a falha é
> um `DEP001` honesto (R6).**

## Build

```bash
kof build app/ --target android                 # projeto Maven + APK (com SDK)
kof build app/ --target android --apk           # APK standalone direto do CLI
kof build app/ --target android --aab           # Android App Bundle
```

- Mesmo fonte Kof, mesma semântica do alvo JVM — `kof.db`, `kof.orm`,
  `kof.security` e `kof.gpu` compilam no Android como no JVM (§278 fechado;
  `kof.gpu` roda no runtime stub sem FFM no ART).
- `detectAppLabel` deriva o label do app do programa; permissões vêm de
  `@Permissions` no seu código — sem manifest escrito à mão.

## Assine a release

```bash
kof build app/ --target android --apk \
  --keystore release.jks --storepass *** --keypass *** --alias kof
```

- Assinatura de release faz parte do `kof build` (não é passo pós) —
  `keytool`/`apksigner` invocados pelo CLI, CWD travado (§299).

## Deploy de release

```bash
kof deploy app/ --target android --name meu-app --version 1.0
```

- Produz o APK assinado + `RELEASE.md` + `SHA256SUMS` + `.tar.gz`
  (veja [32 — CLI e Tooling](32-cli-tooling.pt_BR.md)); SDK ausente ou alvo
  não suportado recusa com `DEP001` — nunca um artefato falso.

## Próximo passo

[Glossário →](glossary.md)
