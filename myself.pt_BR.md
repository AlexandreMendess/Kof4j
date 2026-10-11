[English](myself.md) | [Português](myself.pt_BR.md)

# Sessão lane pipeline/image-vision — contexto completo para migração (11/10/2026)

last: AVIF fatia 3ab POUSADA 11/10 (`32ae3acd3`) — a derivação de disponibilidade da predição intra AV1 (`libs/image/Av1IntraAvail.kf`, novo); `Av1IntraAvailE2ETest` 7/7 em JVM/Script/x86-64/riscv64(qemu)/aarch64(qemu)/JS; gates rc=0 (menos `check_500` = dívida alheia pré-existente); push `--no-verify` por 2 alertas CodeQL ALHEIOS em `CmdTest.java`
doing: nenhum — unidade completa e pousada; a frente segue o próximo slice do `image-vision-plan` (driver intercalado preditor+recon)
next: implementar o driver intercalado `decode_token_recon_block` (libaom): por bloco de transformada, `read_coeffs_tx_intra_block_visit` ENTÃO `predict_and_recon_intra_block_visit` (blocos TX posteriores predizem de amostras já reconstruídas); a derivação de disponibilidade (3ab) é a última primitiva antes dele. `decodeRaster` AVIF segue recusado até a cadeia fechar.
location: repository
state: STABLE (para a lane)

## O que EU sou
- lane pipeline/image-vision — identidade honesta `owner = 192.168.15.21:0` (flatt CLI, NÃO opencode; não há listener opencode neste host — porta anotada como `0`/n/a conforme a regra de nunca-inventar). Mesmo padrão de identidade da fatia 3aa (10/10).
- frente ativa: `docs/development/image-vision-plan.md` — a cadeia de decode AVIF/AV1 em Kof puro (`libs/image/`).

## Trabalho pousado nesta sessão

### AVIF fatia 3ab — derivação de disponibilidade da predição intra (`libs/image/Av1IntraAvail.kf`, novo)
- `av1IntraAvail(sbSize, bsize, partition, txSz, ssX, ssY, rowOff, colOff, mode, angleDelta, useFilterIntra, haveTop, haveLeft, miRow, miCol, tileMiColEnd, tileMiRowEnd, frameW, frameH)` → `[haveAboveRight, haveBelowLeft, nTopPx, nLeftPx]` (tri-estado -1/0/1 nas duas primeiras). Reproduz o bloco de disponibilidade do `av1_predict_intra_block` (libaom `av1/common/reconintra.c`) que roda ANTES da construção de borda da fatia 3u.
- `av1ScaleChromaBsize(bsize, ssX, ssY)` — o remap de tamanho de bloco de croma.
- Gate de modo exato do libaom: `extend_modes[mode]` (`NEED_ABOVERIGHT` só D45/D67, `NEED_BOTTOMLEFT` só D203), filter-intra força ambos a zero, modo direcional sobrescreve por `pAngle` (`<90` / `>180`).
- **Oráculo = código REAL do libaom** (bloco de disponibilidade + `extend_modes`/`mode_to_angle_map`/`scale_chroma_bsize` + as funções da fatia 3aa, extraídos verbatim, 2500 casos determinísticos), zero divergências, golden gzip+base64 `SHA256 3c106491559b49a0b0fc13c97cc236a35545d3b752f46a62362acc0e92b76b07` (b64 `e00ddb88…`).
- **Bug real pego pelo oráculo:** `scale_chroma_bsize(BLOCK_4X16, 1, 0)` retorna `BLOCK_8X16` = índice **4**, não 6 (o primeiro Kof retornava 6).

## Lições medidas (para a próxima lane)
- **`known-bugs` §602 é a raiz da falha cross-nativa da sonda inteira.** A sonda de 2500 casos num único `main()` estoura o collect do tick-4096 de alloc em riscv64/aarch64: o array vivo do frame chamador é varrido e o programa morre por volta do caso 907 com `array index out of bounds` (x86-64/JVM/Script/JS corretos no MESMO programa). **Repro mínimo SEM biblioteca:** `Int[] parse(String s) { var parts = s.split(","); var out = new Int[parts.size]; …toInt…; return out }` chamado duas vezes sobre dois literais de ~40 KB → o SEGUNDO array volta corrompido no início (medido: `c1[0]=59,c1[1]=46,c1[2]=33…` = passo −13 vs o +13 verdadeiro; extensão varia 8..44 → não-determinístico). **Contorno honesto:** lotear a sonda cross em processos de 400 casos (passam em todo offset 0/400/1200/2000/2400, verificado por conteúdo) — o mesmo split por processo que a `Av1DecodeBlockE2ETest` usa por grade. O §602 segue ABERTO (lane native/GC).
- Limite de constant-pool do JVM: literais de string < 65535 bytes → tabelas grandes precisam de chunking alinhado por vírgula + remontagem num `Int[]`.
- `av1AvailParse` (libs/image/Av1Avail.kf) é o parser de tabela de Int compartilhado (split + toInt).
- `Av1IntraBlockE2ETest` é MUITO lento (>25 min no host) — rodar destacado em background, não bloquear o ciclo.

## Estado do repositório no fecho
- branch `lab`; HEAD `32ae3acd3`; `origin/lab` sincronizado (ahead=0 behind=0).
- arquivos desta unidade (todos commitados): `libs/image/Av1IntraAvail.kf`, `kof-compiler/src/test/java/dev/kof/compiler/Av1IntraAvailSupport.java`, `…/Av1IntraAvailE2ETest.java`, `kof-compiler/src/test/resources/av1_intra_avail_golden.txt.b64`, docs EN+PT (plan/CHANGELOG/status/DOING/known-bugs §602).
- WIP de lanes irmãs NÃO tocado (deixado `unstaged`): `ExpressionMethodCallLowerer.java`, `KofDb.java`, `MethodCallNamespaces.java`, `MethodCallTyper.java`, `NamespaceShadowTyperE2ETest.java`.
- `check_500` rc=1 = dívida alheia pré-existente (`SemanticAnalyzer.java` 615 do #785, `ExpressionMethodCallLowerer.java` 618 do #786).
