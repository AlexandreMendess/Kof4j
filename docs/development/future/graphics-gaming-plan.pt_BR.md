[English](graphics-gaming-plan.md) | [Português](graphics-gaming-plan.pt_BR.md)

# Graphics, Games e Media — Superfície de Intenção do Kof

last: none
doing: none-planned
next: spike-3.0
location: docs/development/future
state: planned

**Status:** plano futuro — desenho apenas, **zero código**
**Fonte normativa:** `DECISIONS.md` §D-GRAPHICS-GAMING + adendos da mantenedora
**Deps:** R3/FFI-ABI, runtime, matriz de capabilities, fronteira da stdlib, suíte de conformância

> **Regra fundamental:** direção futura apenas. Toda sintaxe aqui é **forma de intenção**; a forma definitiva da linguagem é decisão da mantenedora. Sem keywords, namespaces ou trilha de implementação a partir deste doc.

# 0. Objetivo

Superfície de intenção (o backend decide como) para: gráficos 2D/3D, janelas, game loops, input, sprites, tilemaps, áudio, vídeo, media, integração KofUI, jogos. Não é uma linguagem gráfica dentro do Kof.

# 1. Princípios

- **Intenção antes de mecanismo:** `sprite("player.png").at(100, 80).draw()` (intenção) vs `createTexture/bindTexture/beginBatch/drawQuad/swapBuffers` (mecanismo, escondido).
- **Loop é da plataforma:** usuário escreve `frame { dt -> update(dt); draw() }`; clock/vsync/scheduling/polling/submit/present são backend.
- **Nenhuma API estrangeira cruza:** nunca `SDL_*`, `gl*`, `canvas.*`, `MediaPlayer`, `javafx.*` — tecnologia de backend não é linguagem.

# 2. Estado medido (beta-0.5.0)

- **JavaFX:** zero uso (medição 09/21; ocorrências são comentários de sintoma do launcher). Não é implementação anterior; decisão: nunca introduzir. Sem migração.
- **`kof.ui`:** JVM/Native = handle sem render (`kof_ui_window_new`, setters/show vazios); KofJS = superfície DOM/webview funcional. Código de gap até o real: `GFX00x`. No-op nunca lê como compatibilidade.
- **`kof.media`:** tem abrir/salvar bitmaps, metadados de vídeo, sampling WAV, enum/mic. Falta: playback, streaming, mixer, pipeline de vídeo/playback, superfícies Native + JS (`MEDIA001`/`MEDIA003`). Evoluir aditivamente; programas existentes continuam funcionando.

# 3. Dependência R3

Gráficos cruzam Kof IR → backend → ABI → runtime → biblioteca → SO/dispositivo e precisam de handles/pointers/buffers/structs/callbacks/arrays/strings/lifecycle/ownership/error-codes/native-resources. **Sem FFI paralelo** — mecanismo faltante vira extensão R3 primeiro.

# 4. Arquitetura (5 níveis)

```text
Kof App (intenção) → Kof Graphics API → Kof Runtime ABI (handles/buffers/events)
→ backends JVM/Native/JS → plataforma/browser
```

A mesma intenção alcança todo alvo.

# 5. Recursos

Objetos de plataforma (`Window Sprite Texture Tilemap Mesh Material Camera Sound
Music Video InputDevice`) escondendo impl (GL/Vulkan/WebGL/imagem/nativo/GPU).
Lifecycle por recurso deve responder: criado/lazy/carregado/disponível/dono/
release/caching/perda de janela. Sem gerenciamento GPU manual quando o backend resolve.

# 6. Janela / loop / clock

- Janela (do backend, forma indecisa): `Window("Pong") { frame { dt -> ... } }`
  vs `Scene("Pong") { dt -> ... }`; cobre título/tamanho/fullscreen/resize/foco/
  close/DPI/orientação/visibilidade/input; sem APIs de SO.
- Loop: backend detém clock/vsync/scheduling/poll/submit/present; programa recebe
  `dt` (unidade/precisão/primeiro-frame/frame-longo/limite/pause/minimizado/foco TBD).
- Clock virtual obrigatório (`dt` determinístico) para física/animações/input/
  áudio/playback/goldens.

# 7. Input

Snapshot por frame: `keys.down("left")`, `keys.pressed("space")`;
estados `down/pressed/released`; `mouse.pos/down/pressed`, `pad.stick/down/pressed`;
backend traduz scancodes/X11/Wayland/KeyboardEvent/WinVK (semântica exata TBD).

# 8. 2D

Primeiro nível. `sprite("player.png").at(120, 80).draw()`; transforms
`at/scale/turn/origin/flip` (API TBD); animação `player.frames("walk")` +
`player.animate()` (plataforma: atlas/batching/upload/seleção).
Tilemaps = intenção de mapa (`tilemap("level.png", 16)`). Render: app declara *o
quê*, backend decide *como* (batching/atlas/command-buffer/ordem/cache/upload ocultos).

# 9. 3D (depois)

Mínimo: mesh/camera/material/light/transform. `mesh("hero.glb")`,
`camera3d().at().lookAt()`, `draw(scene3d { ... })` — apenas ilustrativo.
Sem parsers próprios (glTF/OBJ via libs maduras; critérios: licença/segurança/
cobertura/manutenção/testabilidade/cross-platform). Shaders escondidos no início
(Kof/SPIR-V/WGSL/GLSL/HLSL/cross-compile adiados, fora da primeira fatia).

# 10. Áudio/vídeo

- Intenções: `sound("boom.ogg").play()`, `music("theme.ogg").loop().play()`;
  backend detém decoder/buffer/mixer/output/device/latência/vozes (app nunca
  cria canais/buffers/callbacks/threads). Faces do mixer (`volume/pause/resume/
  stop/loop/fade/pan`) exigem contrato cross-target cada. Latência medida
  request→submission→audível por alvo (valor após spike 3.0/3.3). Devices:
  capability comum + gaps honestos por alvo (restrições de browser).
- Vídeo: `Window("Trailer") { video("intro.mp4").autoplay() }`; sem demuxer/
  decoder/codec/fila/hardware-decoder no app. Sem codecs próprios (FFmpeg/Libav/
  nativos; critérios: licença/alvo/segurança/manutenção/formatos/headless).
- `kof.media` hoje (bitmap/WAV/metadados/mic) → playback/streaming/mixing/
  video-playback, aditivamente.
- KofUI ≠ linguagem concorrente (apps UI vs jogos); compartilha janela/input/
  vídeo/imagens/eventos onde equivalente.

# 11. Alvos

- **JS:** capabilities de browser como backend; `video("intro.mp4")` pode baixar
  para elemento HTML (detalhe de lowering, API segue Kof).
- **WASM:** mesma intenção quando o alvo existir; documentado no plano WASM/WASI,
  nunca duplicado aqui.
- **Native:** stack portátil, sem bindings manuais; x86-64/aarch64/riscv64 entram
  na matriz individualmente.
- **JVM:** nunca JavaFX/Swing/AWT/javax.sound; rota Kof→JVM→R3→stack portátil.
- **Script:** mesma semântica de intenção, delega ao ambiente; capability
  ausente → `GFX001`/`SND001`/`VID001` (nunca unknown-method).

# 12. Matriz de capabilities / gaps / paridade

- Matriz por operação (`window/sprite/audio/video/3d` × JVM/Native/JS/Script);
  `✓` só após implementação+testes+golden+paridade+docs.
- Famílias de gap `GFX00x/INP00x/SND00x/VID00x` (ex. `GFX001` indisponível,
  `SND001` sem playback, `VID001` sem playback); códigos finais entram no
  catálogo normativo antes de implementar; nunca esconder.
- Promoção = mesmo contrato observável em JVM+Script+Native+JS-Web
  (compile+execute+esperado+conformância — só-compilar não basta).

# 13. Observabilidade / conformância

- Pixels testáveis: render→readback→buffer→hash (nunca driver/GPU/framebuffer
  screenshots; formato/tolerância definidos na implementação).
- Áudio offline: programa→mixer→PCM→hash/referência (volume/mix/ordem/loop/
  duração/canais, sem caixas de som).
- Caminhos de conformância (`graphics/sprite/basic.kf`, `audio/mix/basic.kf`, …)
  rodam JVM/Script/Native/JS comparando observáveis.
- Goldens: sequências de frame/input/sprite/transform/audio-mix/media-meta/
  video-decode (clock virtual, input determinístico, mixer offline, frame readback).
- Fuzz em transforms/coords/sizes/textures/input/lifecycle/assets/mídia
  malformada/audio/video/release (arquivos não-confiáveis primeiro).
- Segurança: malformados/overflow/corrupção/bombs/decoder/recursos/sandbox —
  nunca codecs próprios por isso.

# 14. Stack / licenças / escala

- Candidatos (spike 3.0 decide; nunca por familiaridade): gráficos SDL3/SDL2/
  raylib/GLFW+API; áudio miniaudio/OpenAL-Soft/SDL-audio; vídeo FFmpeg/Libav/
  nativos. Critérios: licença/cobertura de alvos/manutenção/segurança/headless/
  cross-compile/estabilidade de API/binário/startup/performance.
- Licenças analisadas antes de integrar (GPL/LGPL/zlib/MIT/BSD/Apache ×
  distribuição/runtime/executável/estático/dinâmico/Native/JVM/JS); "open source"
  sozinho não aprova nada.
- Camada fina apenas (`Kof API → abstração fina → backend`); plataforma detém
  complexidade. Benchmarks (startup/janela/scheduling/throughput/upload/draw/
  latência/mix/decode/memória) comparam mesma-semântica apenas; sem afirmações
  sem medição.
- Orçamentos de memória (runtime/texturas/áudio/vídeo/temps; Native/mobile/WASM/
  embedded); sem buffers manuais. Política de cache transparente (+ release sob
  demanda se contratado).
- Assets (`sprites/sounds/music/video/models`): copy/embed/compress/hash/cache/
  paths/packaging definidos depois, fora da primeira fatia.
- Caminho headless obrigatório (CI/testes/fuzzing/servidores/conformância;
  render buffer, sem janela). `kof test` nunca precisa de monitor/GPU/caixas/
  mic/câmera.
- Android: mesma intenção, sem split `KofAndroidGraphics`; iOS precisa de decisão.
- Debugging relaciona fonte Kof→operação→runtime (sem internals de GPU no início).
- Diagnósticos nomeiam código Kof (`Kof graphics error GFX002`, recurso, razão,
  `game.kof:42` — nunca `SIGSEGV in libSDL` cru). Taxonomia de erros de asset:
  missing/formato-nao-suportado/falha-decode/sem-permissão/exausto (nunca um
  "not found" genérico).
- Modelo de threading explícito (main/render/audio/loading) sobre spawn/async/
  channels/scheduler do Kof. Determinismo: runtime pode variar, teste não
  (sem não-determinismo no harness de conformância).

# 15. Fases / promoção / aberto

- **3.0** spike+infra (stack/R3/FFI/licenças/headless/cross/guard-JavaFX;
  relatório, sem API) → **3.1** janela/frame/input (JVM/Script/Native/JS +
  conformância) → **3.2** 2D (sprite/texture/transform/tilemap/draw; golden/
  headless/cross/assets/lifecycle) → **3.3** áudio (sound/music/play/pause/
  stop/loop/volume + decoder/mixer/device; golden PCM offline) → **3.4** vídeo
  (`video/play/pause/seek/volume` com contrato definido; frame readback) →
  **3.5** 3D só se stack/alvos/R3/runtime/conformância permitirem (senão `GFX00x`
  segue válido) → **3.6** corpus (training/learn/docs/conformância/paridade).
- Checklist de promoção: implementação/runtime/alvos/conformância/golden/
  headless/docs/gaps-catalogados/perf/segurança/licenças/corpus (sem "funciona
  na minha máquina").
- Aberto (mantenedora decide): R1 (namespace `kof.game`?), forma scene (call
  vence sintaxe nova), 3D-após-paridade, contratos de hash golden, pick de
  stack, input snapshot-vs-eventos, entrada auto do WASM, media atual
  manter-vs-rebasar.
- Não-objetivos: renderer/mixer/codec/demuxer próprios; expor SDL/GL/WebGL/DOM/
  HTML/CSS/JavaFX/Swing/AWT; APIs por alvo; linguagem de shader precoce;
  fachadas-que-embrulham; paridade parcial como feature; gaps escondidos;
  implementação sem promoção.
- Regra de ouro: o desenvolvedor pensa em **intenção**, nunca na plataforma
  (`sound("shot.ogg").play()` não pode exigir saber áudio de Linux/browser/
  Android). Forma resultante: Kof App sobre UI/Graphics/Media → Contrato de
  Runtime Kof → backend por alvo → plataforma.
