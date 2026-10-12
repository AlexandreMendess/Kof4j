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

## Auditoria 12/10 — `known-bugs` §674 (o driver nunca lê `tx_type` para tx <= 16x16)
- **ACHADO (real, provado com o libaom pinado `d53e6d0b14568ddbd36ad7354bfb9a59a127346e`):** `av1_read_coeffs_txb` (`av1/decoder/decodetxb.c:158`) chama `av1_read_tx_type` sempre que o bloco não é `all_zero`; `av1_read_tx_type` (`av1/decoder/decodemv.c:626`) só lê o símbolo quando `get_ext_tx_types(tx, is_inter=0, reduced) > 1`, isto é `av1_num_ext_tx_set[av1_get_ext_tx_set_type(...)]`: `TX_32X32` -> `EXT_TX_SET_DCTONLY` -> **1** (SEM símbolo); `TX_16X16` -> `EXT_TX_SET_DTT4_IDTX` -> **5** (COM símbolo).
- O golden da fatia 3ag só exercita `BLOCK_32X32` -> `TX_32X32` -> sem símbolo, então o modelo do driver (que passa `txType = 0` fixo em `Av1DecodeTileRecon.kf` -> `av1DecodeCoeffsBlockEdge`) concordava com o oráculo por ACIDENTE. Num bitstream real, todo bloco intra <= 16x16 carrega o símbolo e o driver dessincroniza.
- `libs/image/Av1TxType.kf` já tem `readTxType(txSz, reducedTxSet, qindex, intraMode, useFilterIntra, filterIntraMode, txTypes, stride, x4, y4)` (fatia 3t) — só nunca é chamado pelo driver.
- **Prova medida:** o oráculo reprovisionado (`/tmp/s3ag/oracle2.c`) imprime, para um bloco 16x16, `DTTX tx=2 mode=0 reduced=0 ext=5` e depois `MS n=5 s=0` (leitura de 5 símbolos em `intra_ext_tx_cdf`), enquanto o fluxo do encoder não tem escrita de `tx_type`. Um caso de um único bloco 16x16 roda redondo quando o encoder escreve o símbolo (`av1_ext_tx_inv[EXT_TX_SET_DTT4_IDTX][DCT_DCT] = 9`).
- **SEGURADO, não pousado:** os casos multi-bloco 16x16 do oráculo ainda dessincronizam (descasamento de contexto `txb_skip` do lado do encoder), então não há golden verificado do caminho <= 16x16; pousar a mudança seria caminho não verificado (Q1/Q5). Registrado como `known-bugs` §674 🟡 OPEN.

## Lições do harness libaom (12/10, para quem retomar o §674)
- `/tmp` é limpo periodicamente: reprovisione com `git clone --filter=blob:none --no-checkout https://aomedia.googlesource.com/aom /tmp/opencode/aom-src` + `git checkout d53e6d0b1`; os stubs de config vão em `/tmp/av1slice3ac/config/{aom_config.h,av1_rtcd.h,aom_dsp_rtcd.h,aom_scale_rtcd.h}` (o include ROOT é `/tmp/av1slice3ac`, e `av1_common_int.h` faz `#include "config/aom_config.h"`).
- O TU de mode info pode ser só `#include "av1/decoder/decodemv.c"` inteiro (não precisa mais fatiar) — o código inter fica não-referenciado e o linker `--gc-sections` o descarta.
- Armadilhas do encoder do oráculo que custaram horas: (1) `filter_intra_cdfs` é indexado por BLOCK SIZE e só é escrito quando `mode == DC_PRED && block <= 32x32` (`av1_filter_intra_allowed`); (2) o contexto de skip é `get_skip_txfm_context` (flags skip dos vizinhos), não `skip ? 1 : 0`; (3) o contexto de entropia de coeficiente é `min(7, soma dos níveis QUANTIZADOS)` com o sinal DC dobrado em bits 3-4 (`set_dc_sign`) — um resíduo DC-only vale **1**, não 7; (4) `eob_multi_size = txsize_log2_minus4[tx]` usado DIRETO no switch (TX_32X32 -> 6 -> `eob_flag_cdf1024`, 11 símbolos); (5) o encoder precisa de `enc_cm.fc = &enc_fc` + `av1_init_mode_probs` + `av1_default_coef_probs`; (6) `ENTROPY_CONTEXT` é `int16_t`, não `uint8_t`; (7) `above_ec`/`left_ec` são compartilhados com o passo do decoder e precisam de reset por caso; (8) o mi grid precisa ser preenchido ANTES de `read_intra_frame_mode_info` (`read_cdef` desreferencia `mi_grid_base[grid_idx]`).
- Dica de instrumentação: instrumente os DOIS lados com o MESMO rótulo e o valor da CDF ANTES da leitura/escrita (`pre=`); rótulos diferentes (MS vs TS) em dois TUs do decoder confundem o diff.
