[English](myself.md) | [Português](myself.pt_BR.md)

# Sessão lane pipeline/image-vision — contexto completo para migração (11/10/2026)

last: AVIF slice 3ac POUSADA 11/10 (`<sha-deste-commit>`) — o driver intercalado de predição+reconstrução intra AV1 por bloco de transformada (`libs/image/Av1DecodeReconBlock.kf`, novo); `Av1DecodeReconBlockE2ETest` 7 rodados / 0F / 2 pulados (JVM/Script/x86-64/JS exatos; riscv64/aarch64 PULAM por §602 com razão nomeada); não-regressão 7 suítes verdes; gates rc=0 (menos `check_changelog_ledger` §651 ALHEIO e `check_release_blockers` #807 classificação ALHEIA)
doing: nenhum — unidade completa e pousada; a frente segue o próximo slice do `image-vision-plan` (driver de tile/plano que alimenta o `av1DecodeReconBlockPlane`)
next: implementar o driver de tile/plano que alimenta o `av1DecodeReconBlockPlane` com um plano decodificado real (mode-info → coeffs → este laço intercalado) rumo ao decode de frame AVIF; §602 (a marca perdida num array vivo do chamador) segue como bloqueador cross ABERTO — corrigi-lo desbloqueia as faces cross e permite virar `BLOCKED_BY_602` para `false`. `decodeRaster` AVIF segue recusado até a cadeia fechar.
location: repository
state: STABLE (para a lane)

## O que EU sou
- lane pipeline/image-vision — identidade honesta `owner = 192.168.15.21:0` (flatt CLI, NÃO opencode; não há listener opencode neste host — porta anotada como `0`/n/a conforme a regra de nunca-inventar).
- frente ativa: `docs/development/image-vision-plan.md` — a cadeia de decode AVIF/AV1 em Kof puro (`libs/image/`).

## Trabalho pousado nesta sessão

### AVIF slice 3ac — driver intercalado preditor+recon por bloco de transformada (`libs/image/Av1DecodeReconBlock.kf`, novo)
- `av1DecodeReconBlockPlane(frame, stride, frameW, frameH, bsize, txSz, txType, mode, angleDelta, useFilterIntra, filterIntraMode, bitDepth, disableEdgeFilter, intraEdgeFilterType, maxBlocksWide, maxBlocksHigh, dcQuant, acQuant)` → plano reconstruído (flat `frameW * frameH`). Reproduz o laço intra de `decode_token_recon_block` (libaom `av1/decoder/decodeframe.c:908`): por bloco, disponibilidade (fatia 3ab) → predição (fatia 3u) → desquant+inv-tx+recon (fatia 3z), de modo que blocos posteriores predizem das amostras já reconstruídas.
- `av1ReconBlockLevels(txSz, cul, dcCat)` / `av1ReconScanPos1(txSz)` — o padrão determinístico de níveis das sondas 3x/3y.
- **Oráculo = caminho REAL do libaom:** `highbd_build_non_directional_intra_predictors` / `highbd_build_directional_and_filter_intra_predictors` (`av1/common/reconintra.c`) + `av1_highbd_inv_txfm_add_c` (`av1/common/idct.c`), 12 casos, zero divergências, golden gzip+base64 `SHA256 8be02528d74c96c2d369e7da43329616e5dc37c7251c4df78e85443d30f08853`.

## Lições medidas (para a próxima lane)
- **`known-bugs` §602 (SEXTA FACE, registrada EN+PT) bloqueia as faces cross DESTA fatia e o split por processo NÃO contorna.** A unidade de trabalho é UM plano; um plano de 16 blocos de transformada (caso g1 16x16 `TX_4X4`) JÁ ultrapassa o tick de alocação 4096 do `kof_gc_collect` — provado com trap de hardware no 2º `kof_gc_collect` do ELF riscv64. O collect do meio do laço recupera o buffer `frame` vivo → plano corrompido em 9 dos 12 casos (g0/g8/g10 abaixo do tick). Processos por caso foram medidos e falham identicamente (a corrupção é INTRA-plano). Contorno honesto escolhido: as faces riscv64/aarch64 PULAM com razão nomeada (`Assumptions.assumeFalse` atrelado à constante `BLOCKED_BY_602` em `Av1DecodeReconBlockE2ETest`), nunca verde falso.
- **Isolamento de injeção-zero para provar o driver (receita reusável):** copiar o `.s` gerado (`KOF_KEEP_ASM=1` no build), editar o corpo de `kof_gc_collect`/`kof_gc_collect_now` e remontar/religar com `riscv64-linux-gnu-as`+`riscv64-linux-gnu-ld`. Três variantes: (1) `kof_gc_collect` pulando direto ao skip-label → exato; (2) `kof_gc_collect_now` = `ret` → exato; (3) coletar SÓ no tick 0 → exato, coletar SÓ no tick ≥4096 → corrompe (mesmas linhas). Isso fixa o defeito no collect do tick 4096, não no driver.
- Não vale a pena tentar contornar §602 reescrevendo o driver (helper por bloco, alocação antecipada, enumeração inline sem `List`): todos foram medidos e continuam corrompendo — a causa é o coletor, não a forma do Kof.

## Estado do repositório no fecho
- branch `lab`; unidade pousada `<sha-deste-commit>`; `origin/lab` sincronizado (ahead=0 behind=0).
- arquivos desta unidade: `libs/image/Av1DecodeReconBlock.kf`, `kof-compiler/src/test/java/dev/kof/compiler/Av1DecodeReconBlockSupport.java`, `…/Av1DecodeReconBlockE2ETest.java`, `kof-compiler/src/test/resources/av1_decode_recon_block_golden.txt.b64`, docs EN+PT (plan/CHANGELOG/status/DOING/known-bugs §602 sexta face).
- WIP de lanes irmãs NÃO tocado (deixado `unstaged`): `ExpressionMethodCallLowerer.java`, `KofDb.java`, `MethodCallNamespaces.java`, `MethodCallTyper.java`, `NamespaceShadowTyperE2ETest.java`.
- `check_changelog_ledger` rc=1 = dívida ALHEIA §651 (irmã `17bc225de`); `check_release_blockers` rc=1 = classificação ALHEIA (#807 sem label). Ambos vermelhos no HEAD limpo também (não são meus).
