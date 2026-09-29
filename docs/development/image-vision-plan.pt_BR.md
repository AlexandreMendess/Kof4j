[English](image-vision-plan.md) | [Português](image-vision-plan.pt_BR.md)

# Plano Estratégico — Kof Image & Vision

> **Estado (29/09): EM DESENVOLVIMENTO — promovido `future/` → `docs/development/` por `D-FUTURE-PROMOTION` + `D-IMAGE-VISION-GO` (mantenedora 29/09), library-first (`D-KOF-FIRST-IMPL`).**
> **Fatia 1 LANDED 29/09:** `libs/image/` pure-Kof — `Image(path).format()/.width()/.height()` leem o **formato + dimensões em pixels** dos primeiros bytes (`PNG`/`GIF`/`BMP` info+core/`JPEG` SOF/`WEBP` VP8·VP8L·VP8X) sobre um prefixo limitado de 4 KiB do `kof.io.readRange`; sem codec, sem pixels, sem sintaxe nova. Golden medido na JVM + Native x86-64 + riscv64 (qemu) + Script, lacuna JS `IOJS001` — `ImageMetadataE2ETest` 7/7.
> Pesado por R1/R9: `kof.image`/`kof.vision` são **pacotes oficiais** (nascentes `experimental`); codecs e algoritmos vêm de bibliotecas maduras isoladas atrás da API Kof (imageio/turbojpeg/OpenCV/ONNX, avaliação por licença/target). Pixels/filtros e `kof.vision` seguem fatias futuras; achado medido da lane native catalogado como `known-bugs` **§540** (nativos cross falham uma única alocação ≥64 Ki Int).
> **Como terminar:** decode de pixels + dados `Image` → `resize`/`crop`/`rotate` (fatia interop), depois Fase 2 de processamento, Fase 3 `kof.vision`; cada fatia aditiva, com docs + golden em todos os alvos. Regra 6: qualquer operador/semântica nova é decisão da mantenedora; sintaxe real `var`/`val`.

## Objetivo

Criar suporte nativo do Kof para **manipulação de imagens e visão computacional**, através de APIs próprias e idiomáticas, integradas à arquitetura da linguagem e da stdlib.

O projeto deve ser dividido conceitualmente em:

```text
kof.image
    ↓
manipulação e processamento de imagens

kof.vision
    ↓
visão computacional e análise visual
```

`kof.file` continua responsável por arquivos e formatos de armazenamento.

A responsabilidade de `kof.image` e `kof.vision` começa a partir dos dados de imagem já carregados.

---

# REGRA FUNDAMENTAL — KOF É KOF

Antes de implementar qualquer coisa:

1. Ler a gramática atual do Kof.
2. Ler exemplos reais do projeto.
3. Consultar APIs existentes da stdlib.
4. Consultar o sistema de tipos.
5. Consultar o modelo atual de arrays/buffers.
6. Consultar o modelo de memória.
7. Consultar os targets existentes.
8. Consultar o sistema de módulos.
9. Executar os testes atuais.

Não inventar sintaxe.

Kof utiliza `var`.

Não utilizar:

```text
let
const
variações de JavaScript
sintaxe de Python
sintaxe de Kotlin
```

Não transformar a API em uma DSL inspirada em outra linguagem.

Todos os exemplos deste documento são conceituais e devem ser adaptados à sintaxe real do Kof antes de serem implementados.

---

# 1. Arquitetura

A arquitetura desejada é:

```text
kof.file
    │
    │ bytes / stream / arquivo
    ▼
kof.image
    │
    ├── Image
    ├── Pixel
    ├── Color
    ├── ImageBuffer
    ├── ImageIO
    ├── Transform
    └── Processing
    │
    ▼
kof.vision
    │
    ├── Detection
    ├── Features
    ├── Segmentation
    ├── Tracking
    ├── Geometry
    ├── OCR
    └── ML integration
```

A estrutura final deve seguir a arquitetura existente do Kof.

Não criar módulos apenas para reproduzir essa árvore literalmente.

---

# 2. `kof.image`

`kof.image` deve fornecer uma abstração própria para imagens.

Conceitualmente:

```text
Image
├── width
├── height
├── format
├── channels
├── pixels
└── metadata
```

A representação interna deve ser eficiente e adequada aos targets.

---

# 3. Formatos de imagem

Suportar progressivamente formatos comuns:

```text
PNG
JPEG
WebP
GIF
BMP
TIFF
```

A primeira implementação não precisa suportar todos.

Priorizar os formatos mais utilizados e aqueles com bibliotecas maduras disponíveis.

---

# 4. Leitura e escrita

Integrar com `kof.file`.

Conceitualmente:

```text
arquivo → kof.file → bytes/stream → kof.image → Image
Image → kof.image → encoder → kof.file → arquivo
```

A API de imagem não deve precisar conhecer detalhes de filesystem.

---

# 5. Pixels

Fornecer acesso aos pixels quando necessário.

Suportar representações como:

```text
RGB
RGBA
Grayscale
```

Avaliar posteriormente:

```text
BGR
BGRA
YUV
HSV
Lab
```

Não criar dezenas de formatos de pixel na primeira versão.

---

# 6. Operações básicas

Implementar progressivamente:

* resize; crop; rotate; flip; transpose; scale; padding;
* composição; conversão de formato; conversão de canais; grayscale;
* ajuste de brilho; contraste; saturação; alpha; normalização.

A API deve favorecer operações composáveis.

---

# 7. Processamento de imagem

Adicionar operações clássicas de processamento:

```text
Blur
Gaussian Blur
Median Blur
Sharpen
Threshold
Adaptive Threshold
Edge Detection
Morphology
Convolution
Histogram
Equalization
```

Priorizar algoritmos clássicos e bem definidos.

Não adicionar algoritmos apenas para aumentar a quantidade de funcionalidades.

---

# 8. Geometria

Criar tipos próprios quando necessário:

```text
Point
Size
Rect
Circle
Line
Polygon
Contour
```

Essas estruturas devem ser reutilizáveis por `kof.image` e `kof.vision`.

---

# 9. Máscaras

Suportar máscaras de imagem.

Exemplo conceitual:

```text
Image + Mask → Operation → Image
```

Possibilitar:

* seleção;
* composição;
* recorte;
* operações matemáticas;
* processamento localizado.

---

# 10. Histogramas

Fornecer infraestrutura para histogramas.

Permitir:

* histogramas por canal;
* grayscale;
* distribuição;
* equalização;
* análise estatística.

Isso será útil tanto para processamento quanto para visão computacional.

---

# 11. `kof.vision`

`kof.vision` deve ser responsável por algoritmos de visão computacional.

A API deve trabalhar sobre `Image` e estruturas geométricas de `kof.image`.

---

# 12. Detecção

Suportar progressivamente:

* detecção de bordas;
* linhas;
* círculos;
* contornos;
* regiões;
* objetos;
* features.

A primeira implementação deve priorizar algoritmos clássicos.

---

# 13. Feature detection

Avaliar suporte para:

```text
Corners
Keypoints
Descriptors
Feature Matching
```

Algoritmos possíveis:

```text
Harris
FAST
ORB
SIFT
```

A escolha deve considerar:

* licença;
* performance;
* maturidade;
* necessidade real;
* disponibilidade por target.

Não implementar tudo simultaneamente.

---

# 14. Segmentação

Adicionar progressivamente:

* thresholding;
* binary segmentation;
* connected components;
* region growing;
* contour extraction;
* watershed quando apropriado.

A API deve produzir estruturas que possam ser reutilizadas por outras operações.

---

# 15. Tracking

Avaliar suporte para rastreamento de objetos/regiões em sequências de imagens.

Possíveis componentes:

```text
Tracker
Frame
Region
Object
Trajectory
```

Não implementar tracking antes de existir infraestrutura adequada para frames e processamento incremental.

---

# 16. Câmera

Criar uma abstração para captura de frames quando o target permitir.

Conceitualmente:

```text
Camera → Frame stream → Image → Vision pipeline
```

Deve suportar:

* abertura;
* fechamento;
* resolução;
* FPS;
* captura;
* streaming;
* controle de recursos.

Não bloquear desnecessariamente a thread principal.

Não criar loops infinitos ingênuos.

Target sem suporte adequado: documentar a limitação (gap `XXX00x`, R6) em vez de implementação fake.

---

# 17. Pipelines

Uma das funcionalidades importantes de `kof.vision` deve ser a composição de operações.

Conceitualmente:

```text
Camera → Frame → Resize → Grayscale → Blur → Edge Detection → Contour Detection → Result
```

O modelo deve permitir pipelines eficientes sem criar cópias desnecessárias de imagens.

Avaliar:

* buffers reutilizáveis;
* operações in-place quando seguras;
* lazy processing;
* fusão de operações;
* streaming.

Não implementar otimizações complexas antes de possuir benchmarks.

---

# 18. OCR

Avaliar integração com OCR.

A primeira versão não precisa implementar um OCR próprio.

Pode utilizar engine externa madura, isolada atrás de uma API Kof.

Conceitualmente:

```text
Image → OCR → Text
```

Possibilidades futuras:

* bounding boxes;
* confidence;
* linhas;
* palavras;
* caracteres;
* idioma.

---

# 19. QR Code

`kofqrcode` deve permanecer um módulo específico.

Porém, deve existir integração natural com:

```text
kof.image
```

e futuramente:

```text
kof.vision
```

Arquitetura:

```text
kof.image → Image → kofqrcode → QR Result
```

Não duplicar decoder/encoder de imagem dentro do `kofqrcode`.

---

# 20. Machine Learning

`kof.vision` deve possuir espaço para integração futura com modelos de ML.

Não criar um framework de ML inteiro dentro desse módulo.

A responsabilidade inicial pode ser:

```text
Image → Tensor/Buffer → Model → Inference → Detection/Classification/Segmentation
```

Avaliar posteriormente integração com runtimes como:

* ONNX Runtime;
* TensorFlow Lite;
* outros runtimes adequados.

A API pública deve permanecer independente do runtime utilizado.

---

# 21. Detecção de objetos

Futuramente:

```text
Image → Object Detector → Detection[]
```

Cada detecção pode possuir conceitualmente:

```text
class
confidence
boundingBox
```

O modelo de dados deve ser simples e reutilizável.

---

# 22. Classificação

Suportar futuramente:

```text
Image → Classifier → Classification[]
```

Com:

* classe;
* confiança;
* metadata opcional.

---

# 23. Segmentação semântica

Planejar suporte futuro para:

```text
Image → Segmentation Model → Mask
```

Reutilizando as abstrações de máscara já existentes.

---

# 24. Performance

Visão computacional pode ser extremamente intensiva.

Projetar considerando:

* SIMD;
* buffers reutilizáveis;
* memória contígua;
* operações in-place;
* zero-copy quando possível;
* processamento paralelo;
* GPU quando disponível;
* aceleradores específicos;
* WASM SIMD;
* Native SIMD.

Não sacrificar a API limpa em nome de micro-otimizações.

---

# 25. Targets

Avaliar progressivamente:

```text
JVM
Native
JS
WASM
```

### JVM

Pode utilizar bibliotecas maduras quando necessário.

### Native

Priorizar performance e acesso eficiente à memória.

### JS

Suportar operações compatíveis com browser.

### WASM

Explorar:

* WASM SIMD;
* processamento local;
* pipelines de imagem;
* inferência quando houver runtime adequado.

Não prometer paridade artificial entre targets.

Documentar claramente o suporte de cada API.

---

# 26. Segurança

Considerar:

* imagens malformadas;
* arquivos gigantes;
* decompression bombs;
* overflow de dimensões;
* buffers inválidos;
* formatos corrompidos;
* consumo excessivo de memória;
* modelos não confiáveis;
* entrada de câmera;
* processamento de dados externos.

Não confiar em imagens recebidas de fontes externas.

---

# 27. Dependências

Não implementar codecs ou algoritmos complexos do zero quando houver bibliotecas maduras e adequadas.

Porém:

**a dependência não deve vazar para a API pública do Kof.**

Por exemplo, o usuário não deve precisar conhecer uma classe específica de uma biblioteca externa para trabalhar com `Image`.

A biblioteca externa é detalhe de implementação.

Avaliar:

* licença;
* maturidade;
* segurança;
* manutenção;
* performance;
* tamanho;
* compatibilidade com targets.

---

# 28. Testes

Criar testes para:

## Image

* abrir; salvar; resize; crop; rotate; grayscale; conversão; canais; pixels; metadata.

## Processing

* blur; threshold; edge detection; morphology; histogram.

## Vision

* contours; lines; circles; features; segmentation.

## Camera

* abertura; captura; lifecycle; encerramento.

## OCR

* reconhecimento; bounding boxes; erros.

## QR Code

* integração com `kof.image`; leitura; geração.

---

# 29. Testes de integração

Criar pipelines reais.

Exemplos conceituais:

```text
Image file → kof.file → kof.image → grayscale → threshold → kof.vision → contours → result
Camera → Image → Vision → Detection
Image → QR Code Reader → Text
```

---

# 30. Benchmarks

Adicionar benchmarks para operações críticas:

* decode; encode; resize; grayscale; blur; edge detection; convolution; segmentation; feature detection.

Comparar:

* tamanho da imagem;
* tempo;
* memória;
* throughput.

Não fazer afirmações de performance sem benchmark.

---

# 31. Implementação incremental

Não tentar criar toda a stack de visão computacional de uma vez.

### Fase 1

```text
kof.image
├── Image
├── Pixel
├── Color
├── ImageIO
└── resize/crop/rotate
```

### Fase 2

```text
processing
├── grayscale
├── blur
├── threshold
├── histogram
└── edges
```

### Fase 3

```text
kof.vision
├── contours
├── lines
├── circles
├── geometry
└── segmentation
```

### Fase 4

```text
camera
tracking
features
OCR
```

### Fase 5

```text
ML
object detection
classification
semantic segmentation
GPU acceleration
```

A ordem pode mudar conforme a arquitetura e os targets existentes.

---

# 32. Critérios de arquitetura

Não transformar `kof.image` em:

* um clone de OpenCV;
* um framework de ML;
* uma biblioteca gráfica;
* um editor de imagens;
* um wrapper gigante de bibliotecas externas.

`kof.image` deve cuidar de **imagens**.

`kof.vision` deve cuidar de **visão computacional**.

Runtimes externos devem permanecer detalhes de implementação.

---

# 33. Regra final

O objetivo é que Kof possa evoluir de:

```text
arquivo → imagem → processamento → visão computacional → resultado
```

com APIs próprias, consistentes e multiplataforma.

O desenvolvedor Kof não deve precisar abandonar a linguagem para fazer:

* processamento de imagem;
* leitura de câmera;
* detecção;
* OCR;
* QR Code;
* análise visual;
* inferência de modelos.

Tudo deve ser construído incrementalmente, preservando a base existente e seguindo a filosofia do Kof:

**menos complexidade acidental, APIs pequenas, intenção clara e controle sobre a implementação.**
