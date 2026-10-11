[English](myself.md) | [Português](myself.pt_BR.md)

# Sessão lane pipeline/image-vision — contexto completo para migração (11/10/2026)

last: AVIF slice 3ae POUSADA 11/10 — o driver de plano intra HETEROGÊNEO (`libs/image/Av1DecodeTilePlane.kf`, novo) + o defeito real do `av1IntraAvail` corrigido (`known-bugs` §660, provado RED); `Av1DecodeTilePlaneE2ETest` 7 rodados / 0F / 2 pulados (JVM/Script/x86-64/JS exatos; riscv64/aarch64 PULAM por §602 com razão nomeada); `Av1IntraAvailE2ETest` 7/7 com golden regenerado; não-regressão 3ab/3ad/3ac/3y/3aa verdes
doing: nenhum — unidade completa e pousada; a frente segue o próximo slice do `image-vision-plan`
next: o walk do leitor de mode-info que FORNECE os `blocks` ao `av1DecodeTilePlaneRecon` a partir do bitstream de tile (walk de partição da fatia 3o + leitores de mode-info 3p/3q/3r + leitura de tx-size 3s) rumo ao decode de frame AVIF; §602 (a marca perdida num array vivo do chamador) segue como bloqueador cross ABERTO — corrigi-lo desbloqueia as faces cross e permite virar `BLOCKED_BY_602` para `false`. `decodeRaster` AVIF segue recusado até a cadeia fechar.
location: repository
state: STABLE (para a lane)

## O que EU sou
- lane pipeline/image-vision — identidade honesta `owner = 192.168.15.21:0` (flatt CLI, NÃO opencode; não há listener opencode neste host — porta anotada como `0`/n/a conforme a regra de nunca-inventar).
- frente ativa: `docs/development/image-vision-plan.md` — a cadeia de decode AVIF/AV1 em Kof puro (`libs/image/`).

## Trabalho pousado nesta sessão

### AVIF slice 3ae — driver de plano intra heterogêneo (`libs/image/Av1DecodeTilePlane.kf`, novo) + fix do `av1IntraAvail` (§660)
- `av1DecodeTilePlaneRecon(frame, stride, frameW, frameH, blocks, bitDepth, disableEdgeFilter, intraEdgeFilterType, dcQuant, acQuant, reader, store, above, left, levelsOut)` → plano reconstruído (flat `frameW * frameH`).
- A composição que o libaom roda para os blocos de um tile real: um plano de VÁRIOS blocos de mode-info, cada um com seu PRÓPRIO tamanho de bloco/tx size/tipo de tx/modo intra, decodificados na ordem do tile sobre UM único fluxo de entropia compartilhado (um reader, um store de CDF, um par above/left). `blocks` = lista flat de `(r, c, bs, tx, tt, mode)` (6 ints por bloco).
- Por bloco: enumera os blocos de transformada do bloco (fatia 3y, deslocados pela origem), decodifica os coeficientes (fatias 3e/3x `av1DecodeCoeffsBlockEdge`) com o tx size/type DESTE bloco, deriva disponibilidade da posição ABSOLUTA (fatia 3ab), prediz com o modo DESTE bloco (fatia 3u) e reconstrói (fatia 3z) — o intercalamento cross-bloco de `decode_partition` -> `decode_block` -> `decode_token_recon_block`.
- **Oráculo = caminho REAL do libaom:** bytes de tile da porta Java fiel de `aom_dsp/entenc.c`; o oráculo C decodifica com o PRÓPRIO `av1_read_coeffs_txb` (`allow_update_cdf = 1`), resolve disponibilidade com `has_top_right`/`has_bottom_left` a partir do `mi_row`/`mi_col` ABSOLUTO, prediz com `av1/common/reconintra.c` e soma o residual com `av1_highbd_inv_txfm_add_c`, 7 casos heterogêneos, zero divergências, golden gzip+base64 `SHA256 5d78218b1fea67f88ad715d767f5b518feb46533b1312c3d8d02487e7920f5e0`.
- **Defeito real corrigido (`known-bugs` §660, RED-proven):** `av1IntraAvail` (fatia 3ab) derivava `xr`/`yd` de `frameW - wpx` SEM subtrair o `miCol`/`miRow` do bloco → sub-contava `nTopPx`/`nLeftPx` para todo bloco fora da origem. Fix: `mbRight = ((frameW >> 2) - av1BlockNum4x4Wide(bsize) - miCol) << 5` (+ análogo inferior). O oráculo 3ab agora dirige a grade off-origin `mi_row`/`mi_col` assimétrica (prólogo de cobertura); 379 das 5000 linhas do golden mudaram; `Av1IntraAvailE2ETest` 6/7 FAIL no código velho, 7/7 com o fix. Golden 3ab regenerado: `SHA256 af05d4e6f4eff76b8bd8399118678a4c6e7b7a8709aceddebe46e59edd67fd36`.

## Lições medidas (para a próxima lane)
- **Layout column-major do `dqcoeff` do libaom (falso alarme de scan-order, resolvido na 3ad).** O buffer `dqcoeff` do libaom é COLUMN-MAJOR (`av1_inv_txfm2d.c` lê `input[c * txfm_size_row + r]`), então suas tabelas de varredura PARECEM transpostas contra um raster row-major. NÃO era defeito do Kof: os arrays `levels` do Kof são row-major e batem elemento-a-elemento uma vez que o oráculo despeja row-major (remap `i → (i%w)*h + (i/w)`). Confirmar o layout de armazenamento do lado C ANTES de acusar o Kof.
- **`-DNDEBUG` é necessário para o oráculo C.** `av1_read_coeffs_txb` tem `assert(dcb->xd.error_info->setjmp)` (`decodetxb.c:344`) — o harness deixa `error_info` NULL, então sem `-DNDEBUG` o oráculo SEGFAULTA. Com `-DNDEBUG` roda limpo.
- **Posição absoluta em `has_top_right`/`has_bottom_left`.** As duas funções recebem `miRow`/`miCol` e usam `miRow & (sbMiSize-1)` / `miCol & (sbMiSize-1)` — para blocos dentro de um superbloco de 64x64 (miRow/miCol < 16) o valor absoluto é correto; passar a origem LOCAL do bloco (0,0) diverge para blocos não no canto. O oráculo 3ae passa a posição ABSOLUTA `ar`/`ac`.
- **`known-bugs` §602 (bloqueia as faces cross).** A unidade de trabalho é UM plano com o buffer `frame` vivo por todo o laço; o reclaim do tick 4096 do `kof_gc_collect` o alcança. Contorno honesto: as faces riscv64/aarch64 PULAM com razão nomeada (`BLOCKED_BY_602`), nunca verde falso.
- **Rebase do ledger sempre conflita em CHANGELOG*/DOING*/known-bugs*:** resolver preservando OS DOIS lados; o link `<!-- en-switch -->`/`<!-- pt-switch -->` deve casar com o cabeçalho VIVO, senão `check_ledger_anchors` fica rc=1. A âncora de link NÃO leva acentos (o gate normaliza: `predição` → `predicao`).

## Estado do repositório no fecho
- branch `lab`; arquivos desta unidade: `libs/image/Av1DecodeTilePlane.kf`, `libs/image/Av1IntraAvail.kf` (fix), `kof-compiler/src/test/java/dev/kof/compiler/Av1DecodeTilePlaneSupport.java`, `…/Av1DecodeTilePlaneE2ETest.java`, `…/Av1IntraAvailSupport.java` (SHA), `kof-compiler/src/test/resources/av1_decode_tile_plane_golden.txt.b64`, `…/av1_intra_avail_golden.txt.b64` (regenerado), docs EN+PT (plan/CHANGELOG/status/DOING/known-bugs §660).
- push só via `scripts/sync-push.sh` (usar `--no-verify` apenas se o Gate 1 CodeQL estiver VERMELHO por alertas ALIEN — registrar a causa na NOTA DE PUSH no DOING EN+PT; NÃO adicionar ao `codeql-baseline.txt`).
- `check_release_blockers` rc=1 = classificação ALHEIA (#807/#809–#814 sem label/ledger). Vermelho no HEAD limpo também (não é meu).
