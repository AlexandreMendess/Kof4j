[English](myself.md) | [Português](myself.pt_BR.md)

# Sessão lane pipeline/image-vision — contexto completo para migração (11/10/2026)

last: AVIF slice 3af POUSADA 11/10 — o walk de blocos de mode-info + tx-size intra (`libs/image/Av1DecodeTile.kf`, novo) + o bug real de compilador catalogado (`known-bugs` §671); `Av1DecodeTileE2ETest` 7 rodados / 0F / 2 pulados (JVM/Script/x86-64/JS exatos; riscv64/aarch64 PULAM por §602 com razão nomeada); não-regressão 16 suítes verdes (76 rodados/0F/6 pulados na varredura larga)
doing: nenhum — unidade completa e pousada; a frente segue o próximo slice do `image-vision-plan`
next: o DRIVER DE TILE que alimenta os blocos de mode-info pelo caminho de coeficientes (o laço `read_coeffs_tx_intra_block` -> `predict_and_reconstruct_intra_block` das fatias 3ad/3ae) até um plano reconstruído, depois croma e `decodeRaster` AVIF; §602 (a marca perdida num array vivo do chamador) segue como bloqueador cross ABERTO. `decodeRaster` AVIF segue recusado até a cadeia fechar.
location: repository
state: STABLE (para a lane)

## O que EU sou
- lane pipeline/image-vision — identidade honesta `owner = 192.168.15.21:0` (flatt CLI, NÃO opencode; não há listener opencode neste host — porta anotada como `0`/n/a conforme a regra de nunca-inventar).
- frente ativa: `docs/development/image-vision-plan.md` — a cadeia de decode AVIF/AV1 em Kof puro (`libs/image/`).

## Trabalho pousado nesta sessão

### AVIF fatia 3af — walk de blocos de mode-info + tx-size (`libs/image/Av1DecodeTile.kf`, novo)
- `av1DecodeTileBlocks(data, miCols, miRows, blocks, cfg, lossless, hasChroma, enableFilterIntra, palettePossible, bitDepth, txMode, baseQIndex, segEnabled, segIdPreSkip, lastActiveSegId, segSkipActive)` -> 17 ints por bloco (`skip, segId, cdef, qIndex, txSz, yMode, angleY, uvMode, angleUV, cflU, cflV, useFilterIntra, filterIntraMode, deltaLf0..3`).
- Compõe as fatias 3p/3q/3r/3s na ordem EXATA que libaom roda em `decode_mbmi_block` + `read_tx_size`: prefixo (segment id + skip) -> cauda (CDEF + delta-Q + delta-LF) -> corpo (Y/UV, angles, CFL, palette, filter-intra) -> tx size. Os blocos compartilham UM `Av1Symbol`, então um bloco depende das CDFs adaptadas e dos mapas de vizinhança que os anteriores deixaram.
- **Oráculo = o caminho REAL do libaom:** um encoder C escreve os símbolos na ordem exata que `read_intra_frame_mode_info` lê, com as PRÓPRIAS CDFs default (`av1_init_mode_probs`) e o PRÓPRIO encoder de range (`aom_dsp/entenc.c`); o oráculo decodifica com o REAL `read_intra_frame_mode_info` + `read_tx_size` do libaom (extraídos intra-only num TU com o código inter descartado via `-ffunction-sections`/`--gc-sections`), 8 casos, zero divergências, golden `SHA256 abb5d8e9e78b75f39329d44e75d83a9b017f2e66b1272def7cfcb17e838264ff`.
- **Defeito real catalogado (`known-bugs` §671, RED-proven):** dois construtores de MESMA ARIDADE (diferindo só no tipo do 1º parâmetro) colidem no backend NATIVO — o mangler os chaveia por aridade (`Classe_init_<aridade>`), então `as` falha com `symbol ... is already defined`. JVM/Script aceitam. Fix honesto nesta fatia: `Av1ModeInfo`/`Av1TxSize` passaram a ter UM ÚNICO construtor recebendo o `Av1Symbol` do chamador; as sondas 3p/3q/3r/3s embrulham seus bytes (`Av1ModeInfo(Av1Symbol(data), w, h)`). O fix do compilador (mangler por descritor completo) fica para a lane do compilador.

## Lições medidas (para a próxima lane)
- **Bug §661 (armadilha de design de biblioteca Kof):** NUNCA usar dois construtores da mesma aridade numa classe que precise rodar no nativo. Prefira um único construtor com um parâmetro que carregue a escolha (aqui: receber o `Av1Symbol` direto em vez de sobrecarregar `Int[]` vs `Av1Symbol`).
- **Oráculo com o código REAL do libaom:** para `read_intra_frame_mode_info` foi preciso extrair um TU intra-only (linhas 1-35, 36-168, 258-335, 446-625, 672-845 de `decodemv.c`) + o `read_tx_size` de `decodeframe.c`, e linkar com `-ffunction-sections -fdata-sections -Wl,--gc-sections` para descartar o código inter morto (que arrastava `av1_find_mv_refs` etc.). Também exigiu um stub de `entropymode.c`/`seg_common.c`/`entropymv.c`.
- **libaom `delta_q_res`/`delta_lf_res` são o MULTIPLICADOR `1 << n`** (`decodeframe.c`: `= 1 << aom_rb_read_literal(rb, 2)`), enquanto o cfg do Kof guarda o shift cru `n`. O oráculo precisa passar `1 << n`.
- **libaom aliases todo mi cell de um bloco para o MESMO `MB_MODE_INFO`**; `readSkip`/`readSegmentId` do Kof escrevem só a célula de origem. O walk precisa difundir skip/seg pelo footprint do bloco (`setSkipSeg`), senão o contexto de skip do próximo bloco lê 0.
- **libaom chama `set_txfm_ctxs` depois do `read_tx_size`** (em `parse_decode_block`), atualizando o contexto de transformada por-4x4 que o próximo bloco lê. O driver precisa chamar `txSize.setTxfmCtxs(r, c, bs, txSz, 0)`.
- **`known-bugs` §602 (bloqueia as faces cross).** Contorno honesto: as faces riscv64/aarch64 PULAM com razão nomeada (`BLOCKED_BY_602`), nunca verde falso.
- **Rebase do ledger sempre conflita em CHANGELOG*/DOING*/known-bugs*:** preservar OS DOIS lados. As âncoras `<!-- en-switch -->`/`<!-- pt-switch -->` do known-bugs usam o `gh_slug` EXATO do gate (minúsculas, diacríticos dobrados, `_` preservado, pontuação removida, espaço->hífen) — computar com a função do próprio gate, não à mão.

## Estado do repositório no fecho
- branch `lab`; arquivos desta unidade: `libs/image/Av1DecodeTile.kf` (novo), `libs/image/Av1ModeInfo.kf` (ctor único + `setSkipSeg`), `libs/image/Av1TxSize.kf` (ctor único), `kof-compiler/src/test/java/dev/kof/compiler/Av1DecodeTileSupport.java` + `…/Av1DecodeTileE2ETest.java` (novos), as sondas `Av1ModeInfoSupport`/`Av1ModeBodySupport`/`Av1ModeTailSupport`/`Av1PaletteSupport`/`Av1TxSizeSupport` (embrulho `Av1Symbol(data)`), `kof-compiler/src/test/resources/av1_decode_tile_golden.txt.b64` (novo), docs EN+PT (plan/CHANGELOG/status/DOING/known-bugs §661/development README contagem viva 4->5).
- push só via `scripts/sync-push.sh` (usar `--no-verify` apenas se o Gate 1 CodeQL estiver VERMELHO por alertas ALIEN — registrar a causa na NOTA DE PUSH no DOING EN+PT; NÃO adicionar ao `codeql-baseline.txt`).
- `check_release_blockers` rc=0 neste ciclo.
