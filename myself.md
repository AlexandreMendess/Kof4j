[English](myself.md) | [Português](myself.pt_BR.md)

# Sessão lane pipeline/image-vision — contexto completo para migração (11/10/2026)

last: AVIF slice 3ag POUSADA 11/10 — o DRIVER DE TILE intra (`libs/image/Av1DecodeTileRecon.kf`, novo): mode-info + tx-size + coeficientes INTERCALADOS por bloco; `Av1DecodeTileReconE2ETest` **7 rodados / 0F / 0 pulados** (JVM/Script/x86-64/JS + riscv64(qemu)/aarch64(qemu) — o §602 foi CORRIGIDO upstream, então `BLOCKED_BY_602` virou false); não-regressão 12 suítes verdes (80 rodados/0F/4 pulados)
doing: nenhum — unidade completa e pousada; a frente segue o próximo slice do `image-vision-plan`
next: os PLANOS DE CROMA (U/V pelo mesmo laço intercalado com os tamanhos de transformada de croma e a derivação `compute_tx_type` de croma), depois a montagem tile-group/frame que roteia o payload de tile real por este driver, e por fim o `decodeRaster` AVIF. `decodeRaster` AVIF segue recusado até a cadeia fechar.
location: repository
state: STABLE (para a lane)

## O que EU sou
- lane pipeline/image-vision — identidade honesta `owner = 192.168.15.21:0` (flatt CLI, NÃO opencode; não há listener opencode neste host — porta anotada como `0`/n/a conforme a regra de nunca-inventar).
- frente ativa: `docs/development/image-vision-plan.md` — a cadeia de decode AVIF/AV1 em Kof puro (`libs/image/`).

## Trabalho pousado nesta sessão

### AVIF fatia 3ag — o driver de tile intra (`libs/image/Av1DecodeTileRecon.kf`, novo)
- `av1DecodeTileRecon(frame, stride, frameW, frameH, blocks, cfg, lossless, hasChroma, enableFilterIntra, palettePossible, bitDepth, txMode, baseQIndex, segEnabled, segIdPreSkip, lastActiveSegId, segSkipActive, dcQuant, acQuant, disableEdgeFilter, intraEdgeFilterType, reader, store, above, left, levelsOut, modeOut)` -> plano de luma reconstruído.
- O estágio que o libaom roda como `parse_decode_block`: por bloco (1) mode info (prefixo 3p, cauda 3r, corpo 3q), (2) tx size (3s) + `setTxfmCtxs`, (3) os blocos de transformada (3y), cada um: coeficientes (3e/3x) -> disponibilidade (3ab) -> predição (3u) -> desquant+inv-tx+recon (3z) com os níveis DECODIFICADOS. Tudo sobre UM `Av1Symbol` compartilhado.
- `levelsOut` = coeficientes decodificados por bloco de transformada; `modeOut` = registro de mode-info por bloco (o layout de 17 ints da 3af).
- **Oráculo = caminho REAL do libaom:** um encoder C escreve mode info + tx size + coeficientes na ordem EXATA em que o libaom os lê (com as PRÓPRIAS CDFs default e o encoder de range do libaom); o oráculo decodifica com `read_intra_frame_mode_info` + `read_tx_size` + `av1_read_coeffs_txb` + os builders de `av1_predict_intra_block` + `av1_highbd_inv_txfm_add_c`, 3 casos (2 blocos, 4 blocos com modos mistos, skip), zero divergências, golden `SHA256 202acbb32920089506ba7ef69bb4f4810605160662ea90e5848cbd4e62feedb1`.

## Lições medidas (para a próxima lane)
- **O encoder do oráculo precisa de `av1_default_coef_probs` no SEU `AV1_COMMON`.** As CDFs de coeficiente vivem em `AV1_COMMON.fc`; sem chamar `av1_default_coef_probs(&enc_cm)` no lado do encoder, as CDFs ficam zeradas e o desync só aparece no PRIMEIRO bloco com resíduo (parece bug do driver, é do harness).
- **`get_tx_size_context` lê `above_txfm_context[0]`/`left_txfm_context[0]`** (a célula na borda esquerda/superior do bloco), não a célula no offset do bloco. O encoder precisa espelhar isso e chamar o equivalente de `set_txfm_ctxs` depois de cada tx size.
- **Extrair TUs intra-only do libaom:** `read_intra_frame_mode_info` + `read_tx_size` saem de `decodemv.c`/`decodeframe.c` em fatias de linha (com o código inter descartado); `av1_get_eob_pos_token` + `av1_txb_init_levels_c` + `av1_get_nz_map_contexts_c` + `av1_get_txb_entropy_context` + `write_golomb` saem de `encoder/encodetxb.c`. Linkar com `-ffunction-sections -fdata-sections -Wl,--gc-sections` para descartar o código morto.
- **`#include "av1/encoder/encodetxb.h"` arrasta o encoder inteiro** (CNN, partition weights) e não compila; declarar só as 3-4 funções usadas resolve.
- **NÃO sobrecarregar construtor por aridade** (bug §671): use um único construtor com um parâmetro que carregue a escolha.
- **§602 está CORRIGIDO** (11/10, lane nativa/GC) — as faces cross dos testes irmãos podem virar `BLOCKED_BY_602 = false` e rodar de verdade; neste slice as duas faces cross passam.

## Estado do repositório no fecho
- branch `lab`; arquivos desta unidade: `libs/image/Av1DecodeTileRecon.kf` (novo), `libs/image/Av1DecodeTile.kf` (correção de um fragmento de debug que ficou num comentário na 3af), `kof-compiler/src/test/java/dev/kof/compiler/Av1DecodeTileReconSupport.java` + `…/Av1DecodeTileReconE2ETest.java` (novos), `kof-compiler/src/test/resources/av1_decode_tile_recon_golden.txt.b64` (novo), docs EN+PT (plan/CHANGELOG/status/DOING).
- push só via `scripts/sync-push.sh` (usar `--no-verify` apenas se o Gate 1 CodeQL estiver VERMELHO por alertas ALIEN — registrar a causa na NOTA DE PUSH no DOING EN+PT; NÃO adicionar ao `codeql-baseline.txt`).
