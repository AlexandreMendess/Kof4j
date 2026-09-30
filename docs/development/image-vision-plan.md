[English](image-vision-plan.md) | [Português](image-vision-plan.pt_BR.md)

# Strategic plan — Kof Image & Vision

> **State (29/09): UNDER DEVELOPMENT — promoted `future/` → `docs/development/` by `D-FUTURE-PROMOTION` + `D-IMAGE-VISION-GO` (maintainer 29/09), library-first (`D-KOF-FIRST-IMPL`).**
> **Slice 1 LANDED 29/09:** pure-Kof `libs/image/` — `Image(path).format()/.width()/.height()` read the **format + pixel dimensions** from the leading bytes (`PNG`/`GIF`/`BMP` info+core/`JPEG` SOF/`WEBP` VP8·VP8L·VP8X + `TIFF` + `ICO`/`CUR` + `PNM` P1–P6 + `QOI` + `PSD`/`DDS`/`farbfeld`/`AVIF`-`HEIF`) and the `Bool isImage(path)` helper, over a bounded 4 KiB prefix of `kof.io.readRange`; no codec, no pixels, no new syntax. Golden measured on JVM + Native x86-64 + riscv64 (qemu) + Script, JS gap `IOJS001` — `ImageMetadataE2ETest` 7/7.
> Heavy per R1/R9: `kof.image`/`kof.vision` are **official packages** (born `experimental`); codecs and algorithms come from mature libraries isolated behind the Kof API (imageio/turbojpeg/OpenCV/ONNX, evaluated per license/target). Pixels/filters and `kof.vision` remain future slices; a measured native-lane finding is catalogued as `known-bugs` **§540** (cross natives fail a single ≥64 Ki Int allocation).
> **How to finish:** pixel decode + `Image` data → `resize`/`crop`/`rotate` (interop slice), then Phase 2 processing, Phase 3 `kof.vision`; each slice additive, with docs + all-target golden. Rule 6: any new operator/semantics is a maintainer decision; real syntax is `var`/`val` (never `let`/`const`).
> **Decision RESOLVED 29/09 (`D-IMAGE-SURFACE`, maintainer):** the pixel surface **reuses `Raster`** (no new `Image`/`Pixel`/`Color`); codecs are pure Kof wherever feasible and ride JVM **imageio** (through the `kof.image` builtin `image.decode`, an explicit JVM-only commitment) only where a pure-Kof decoder is infeasible, with an honest `IMG001` gap elsewhere — see §34. JPEG interop landed 29/09 (`RasterDecodeE2ETest#jpegDecodesOnJvmViaInterop`). (The original decision request follows for history.)
> **Slice 2a LANDED 29/09 (Kof-first half of the pixel slice):** `decodeRaster(path)` returns a provisional `Raster(format, width, height, channels, samples)` for **uncompressed** formats — PNM `P5`/`P6` and farbfeld — bounded to ≤16384 samples (one read, under the then-open §540 cross-native cap; later raised to 262144, §5); compressed formats stay interop-first behind the decision. Proof: `RasterDecodeE2ETest` 7/7 (PNM/farbfeld golden + unsupported/oversized; JVM + Native x86-64/riscv64 + Script; JS `IOJS001`).
> **Slice 2b LANDED 29/09:** pure-Kof raster operations over the provisional `Raster` — `cropRaster(r,x,y,w,h)` and `resizeNearest(r,w,h)` (nearest-neighbour), output bounded by the same cap; smooth filtering waits for the interop slice. Proof: `RasterDecodeE2ETest` 7/7.
> **Slice 3g LANDED 29/09 (pure Kof, all targets):** `libs/image/Vp8lTransforms.kf` (new) + `libs/image/Vp8l.kf` — VP8L **predictor** (14 modes, §3.5.1) and **color** (§3.5.2, `ColorTransformDelta = (s8(t)*s8(c))>>5`) inverse transforms, applied in reverse order; the transform loop now reads `size_bits`/subresolution grids for both and the entropy decode was factored into `vp8lDecodeImage(r,w,h,metaAllowed)` so transform sub-images never read the ARGB-only meta-prefix bit (§3.8.3). Color-indexing transform and meta-Huffman groups still refused with explicit `IMAGE:` diagnostics. Proof: `RasterDecodeE2ETest` 19/19 (`webpVp8lDecodesOn*` incl. a new 8x8 libwebp-generated predictor+color stream **byte-validated against libwebp**, JVM + Native x86-64 + riscv64(qemu) + Script); RED measured on the pre-slice decoder (`IMAGE: WebP predictor transform is not supported yet`).
> **Slice 3h LANDED 30/09 (pure Kof, all targets):** `libs/image/Vp8lTransforms.kf` + `libs/image/Vp8l.kf` — VP8L **COLOR_INDEXING transform** (§3.5.4): the palette sub-image (`num_colors = ReadBits(8)+1`, colors delta-coded left-to-right and expanded to `1 << (8 >> bits)` entries) is applied to the entropy image, whose green channel packs `1 << bits` indices of `8 >> bits` bits, least significant first, at the reduced width `ceil(w / 2^bits)`. Fixed a latent **`max_symbol`** bug in `vp8lReadNormal` (`ReadHuffmanCodeLengths` caps the number of decoded code-length *symbols*, not the resulting array length — RFC 9649 §3.6.2.1); the bug only surfaced when the "use length" flag reduced `max_symbol` below the alphabet and desynced the bitstream. Meta-Huffman groups still refused with an explicit `IMAGE:` diagnostic. Proof: `RasterDecodeE2ETest` 19/19 (new 8x8 libwebp-generated 8-color indexing stream, byte-validated against libwebp; JVM + Native x86-64 + riscv64(qemu) + Script); RED measured on the pre-slice decoder (`IMAGE: WebP color-indexing transform is not supported yet`).
> **Slice 3i LANDED 30/09 (pure Kof, all targets):** `libs/image/Vp8l.kf` — VP8L **meta-Huffman groups** (RFC 9649 §3.7.2.2): `prefix_bits = ReadBits(3)+2`; the entropy image `ceil(w/2^bits) × ceil(h/2^bits)` is entropy-decoded, each pixel's red/green bytes give its group index, one prefix-code group is read per distinct value, and each pixel selects its group by `entropy[(y>>bits)*xw + (x>>bits)]` (the LZ77 copy is not clamped to group blocks, matching libwebp). This was the last VP8L refusal — the whole VP8L lossless path now decodes. Proof: `RasterDecodeE2ETest` 19/19 (new 8x8 two-group libwebp-generated stream, byte-validated against libwebp; JVM + Native x86-64 + riscv64(qemu) + Script); RED measured on the pre-slice decoder (`IMAGE: WebP meta-Huffman groups are not supported yet`).
> **Slice 3f LANDED 29/09 (pure Kof, all targets):** `libs/image/Vp8l.kf` — VP8L **color cache** (RFC 9649 §3.6.2.3: `color_cache_code_bits` 1..11, slot `(0x1e35a7bd * argb) >> (32 - bits)`, every literal/copied pixel inserted in stream order, `S >= 256+24` reads the cache). The green prefix code alphabet is now `256+24+cache_size`. Decodes subtract-green/color-cache/single-group streams. Predictor/color/indexing transforms and meta-Huffman still refused with explicit `IMAGE:` diagnostics. Proof: `RasterDecodeE2ETest` 19/19 (`webpVp8lDecodesOn*` incl. a new 8x8 color-cache stream **generated by libwebp and byte-validated against it**, JVM + Native x86-64 + riscv64(qemu) + Script); RED measured on the pre-slice decoder (`IMAGE: WebP color cache is not supported yet`).
> **Slice 3e LANDED 29/09 (pure Kof, all targets):** `libs/image/Vp8l.kf` — VP8L **normal (code-length) Huffman codes** + **LZ77 backward references** (length/distance prefix extra bits + the §3.6.2.2.1 distance map). Decodes the subtract-green/no-cache/single-group subset that real libwebp emits. Predictor/color/indexing transforms, color cache and meta-Huffman still refused with explicit `IMAGE:` diagnostics. Proof: `RasterDecodeE2ETest` 19/19 (`webpVp8lDecodesOn*` incl. a hand-built normal-Huffman+LZ77 stream, libwebp-validated, JVM + Native x86-64 + riscv64(qemu) + Script). Re-test of the VP8L native face after the §541/§543 fixes.
> **Slice 3d LANDED 29/09 (pure Kof, all targets):** `libs/image/Vp8l.kf` — VP8L transform loop + **SUBTRACT_GREEN** inverse; predictor/color/indexing, cache, meta and LZ77 still refused with explicit `IMAGE:` diagnostics.
> **Slice 3c LANDED 29/09 (pure Kof, all targets):** `libs/image/Vp8l.kf` — VP8L core (bit reader + simple Huffman + literals); transforms/cache/meta/LZ77 refused with explicit `IMAGE:` diagnostics. Proof: `RasterDecodeE2ETest` 19 run/0F (`webpVp8lDecodesOn*` on JVM + Native x86-64/riscv64 + Script).
> **Slice 3b LANDED 29/09 (pure Kof, all targets):** `libs/image/Gif.kf` decodes the first GIF frame (Kof LZW, global/local palette, interlaced) to RGB. Proof: `RasterDecodeE2ETest` 15 run/0F (`gifDecodesOn*` on JVM + Native x86-64/riscv64 + Script).
> **Slice 2f LANDED 29/09 (pure Kof, parity — no gap):** `decodeRaster` decodes **QOI** (all chunks: RGB/RGBA/diff/luma/run/index) in Kof, so a compressed-format decode ships on every target. Decision `D-IMAGE-SURFACE` (reuse `Raster`; pure Kof when feasible, JVM imageio only where infeasible) + §34 TODO recorded. Proof: `RasterDecodeE2ETest` 7/7 (QOI golden incl. a RUN chunk; JVM + Native x86-64 + riscv64 + Script).
> **Slice 2c LANDED 29/09:** `flipHorizontal`, `flipVertical` and `rotate90` (clockwise, dimensions swap) over the provisional `Raster`. Proof: `RasterDecodeE2ETest` 7/7.
> **Slice 2d LANDED 29/09:** `decodeRaster` also decodes uncompressed **BMP** 24/32-bit (BGR rows padded to 4 bytes, bottom-up or top-down, alpha dropped). Proof: `RasterDecodeE2ETest` 7/7.
> **Slice 2e LANDED 29/09 (processing overlap):** `grayscale` (BT.601), `threshold(level)` and `boxBlur` (3x3, clamped borders) over the `Raster`. Proof: `RasterDecodeE2ETest` 7/7.

## Objective

Create native Kof support for **image manipulation and computer
vision**, through own idiomatic APIs, integrated with the language and
stdlib architecture.

The project splits conceptually into:

```text
kof.image  → image manipulation and processing
kof.vision → computer vision and visual analysis
```

`kof.file` remains responsible for files and storage formats.

The responsibility of `kof.image` and `kof.vision` starts from the
already-loaded image data.

---

# FUNDAMENTAL RULE — KOF IS KOF

Before implementing anything:

1. Read the current Kof grammar.
2. Read real examples in the project.
3. Consult existing stdlib APIs.
4. Consult the type system.
5. Consult the current arrays/buffers model.
6. Consult the memory model.
7. Consult the existing targets.
8. Consult the module system.
9. Run the current tests.

Do not invent syntax.

Kof uses `var`.

Do not use: `let`, `const`, JavaScript variations, Python syntax, Kotlin
syntax.

Do not turn the API into a DSL inspired by another language.

All examples in this document are conceptual and must be adapted to the
real Kof syntax before being implemented.

---

# 1. Architecture

The desired architecture is:

```text
kof.file
    │  bytes / stream / file
    ▼
kof.image
    ├── Image  ├── Pixel  ├── Color  ├── ImageBuffer
    ├── ImageIO  ├── Transform  └── Processing
    ▼
kof.vision
    ├── Detection  ├── Features  ├── Segmentation
    ├── Tracking   ├── Geometry  ├── OCR  └── ML integration
```

The final structure must follow Kof's existing architecture.

Do not create modules just to reproduce this tree literally.

---

# 2. `kof.image`

`kof.image` must provide its own abstraction for images.

Conceptually:

```text
Image
├── width
├── height
├── format
├── channels
├── pixels
└── metadata
```

The internal representation must be efficient and adequate to the
targets.

---

# 3. Image formats

Progressively support common formats: PNG, JPEG, WebP, GIF, BMP, TIFF.

The first implementation does not need to support all of them.

Prioritize the most-used formats and those with mature libraries
available.

---

# 4. Reading and writing

Integrate with `kof.file`.

Conceptually:

```text
arquivo → kof.file → bytes/stream → kof.image → Image
Image → kof.image → encoder → kof.file → arquivo
```

The image API must not need to know filesystem details.

---

# 5. Pixels

Provide pixel access when needed.

Support representations like: RGB, RGBA, Grayscale.

Evaluate later: BGR, BGRA, YUV, HSV, Lab.

Do not create dozens of pixel formats in the first version.

---

# 6. Basic operations

Implement progressively:

* resize; crop; rotate; flip; transpose; scale; padding;
* composition; format conversion; channel conversion; grayscale;
* brightness; contrast; saturation; alpha; normalization.

The API should favor composable operations.

---

# 7. Image processing

Add classic processing operations:

```text
Blur / Gaussian Blur / Median Blur / Sharpen
Threshold / Adaptive Threshold / Edge Detection
Morphology / Convolution / Histogram / Equalization
```

Prioritize classic, well-defined algorithms.

Do not add algorithms just to increase the feature count.

---

# 8. Geometry

Create own types when needed:

```text
Point  Size  Rect  Circle  Line  Polygon  Contour
```

These structures must be reusable by `kof.image` and `kof.vision`.

---

# 9. Masks

Support image masks.

Conceptual example:

```text
Image + Mask → Operation → Image
```

Enable: selection; composition; cropping; mathematical operations;
localized processing.

---

# 10. Histograms

Provide histogram infrastructure.

Allow: per-channel histograms; grayscale; distribution; equalization;
statistical analysis.

This is useful for both processing and computer vision.

---

# 11. `kof.vision`

`kof.vision` must be responsible for computer-vision algorithms.

The API must work over `Image` and the geometric structures of
`kof.image`.

---

# 12. Detection

Support progressively: edges; lines; circles; contours; regions;
objects; features.

The first implementation must prioritize classic algorithms.

---

# 13. Feature detection

Evaluate support for:

```text
Corners  Keypoints  Descriptors  Feature Matching
```

Possible algorithms:

```text
Harris  FAST  ORB  SIFT
```

The choice must consider: license; performance; maturity; real need;
availability per target.

Do not implement everything simultaneously.

---

# 14. Segmentation

Add progressively:

* thresholding; binary segmentation; connected components; region
  growing; contour extraction; watershed when appropriate.

The API must produce structures reusable by other operations.

---

# 15. Tracking

Evaluate support for tracking objects/regions in image sequences.

Possible components:

```text
Tracker  Frame  Region  Object  Trajectory
```

Do not implement tracking before there is adequate infrastructure for
frames and incremental processing.

---

# 16. Camera

Create an abstraction for frame capture when the target allows it.

Conceptually:

```text
Camera → Frame stream → Image → Vision pipeline
```

It must support: open; close; resolution; FPS; capture; streaming;
resource control.

Do not block the main thread unnecessarily.

Do not create naive infinite loops.

A target without adequate support: document the limitation (gap
`XXX00x`, R6) instead of a fake implementation.

---

# 17. Pipelines

One of the important features of `kof.vision` must be operation
composition.

Conceptually:

```text
Camera → Frame → Resize → Grayscale → Blur → Edge Detection → Contour Detection → Result
```

The model must allow efficient pipelines without creating unnecessary
image copies.

Evaluate:

* reusable buffers;
* in-place operations when safe;
* lazy processing;
* operation fusion;
* streaming.

Do not implement complex optimizations before having benchmarks.

---

# 18. OCR

Evaluate integration with OCR.

The first version does not need to implement its own OCR.

It may use a mature external engine, isolated behind a Kof API.

Conceptually:

```text
Image → OCR → Text
```

Future possibilities:

* bounding boxes; confidence; lines; words; characters; language.

---

# 19. QR Code

`kofqrcode` must remain a specific module.

But there must be natural integration with `kof.image` and, in the
future, `kof.vision`.

Architecture:

```text
kof.image → Image → kofqrcode → QR Result
```

Do not duplicate image decoder/encoder inside `kofqrcode`.

---

# 20. Machine Learning

`kof.vision` must leave room for future integration with ML models.

Do not create an entire ML framework inside this module.

The initial responsibility may be:

```text
Image → Tensor/Buffer → Model → Inference → Detection/Classification/Segmentation
```

Evaluate later integration with runtimes such as:

* ONNX Runtime;
* TensorFlow Lite;
* other adequate runtimes.

The public API must remain independent of the runtime used.

---

# 21. Object detection

In the future:

```text
Image → Object Detector → Detection[]
```

Each detection may conceptually have:

```text
class  confidence  boundingBox
```

The data model must be simple and reusable.

---

# 22. Classification

Support in the future:

```text
Image → Classifier → Classification[]
```

With: class; confidence; optional metadata.

---

# 23. Semantic segmentation

Plan future support for:

```text
Image → Segmentation Model → Mask
```

Reusing the mask abstractions that already exist.

---

# 24. Performance

Computer vision can be extremely intensive.

Design considering:

* SIMD; reusable buffers; contiguous memory; in-place operations;
  zero-copy when possible; parallel processing; GPU when available;
  specific accelerators; WASM SIMD; Native SIMD.

Do not sacrifice the clean API in the name of micro-optimizations.

---

# 25. Targets

Evaluate progressively:

```text
JVM  Native  JS  WASM
```

### JVM

May use mature libraries when needed.

### Native

Prioritize performance and efficient memory access.

### JS

Support operations compatible with the browser.

### WASM

Explore:

* WASM SIMD;
* local processing;
* image pipelines;
* inference when there is an adequate runtime.

Do not promise artificial parity between targets.

Document clearly the support of each API.

---

# 26. Security

Consider:

* malformed images; giant files; decompression bombs; dimension
  overflow; invalid buffers; corrupted formats; excessive memory
  consumption; untrusted models; camera input; processing of external
  data.

Do not trust images received from external sources.

---

# 27. Dependencies

Do not implement complex codecs or algorithms from scratch when there
are mature, adequate libraries.

But:

**the dependency must not leak into Kof's public API.**

For example, the user must not need to know a specific class of an
external library to work with `Image`.

The external library is an implementation detail.

Evaluate:

* license; maturity; security; maintenance; performance; size;
  compatibility with targets.

---

# 28. Tests

Create tests for:

## Image

* open; save; resize; crop; rotate; grayscale; conversion; channels; pixels; metadata.

## Processing

* blur; threshold; edge detection; morphology; histogram.

## Vision

* contours; lines; circles; features; segmentation.

## Camera

* open; capture; lifecycle; close.

## OCR

* recognition; bounding boxes; errors.

## QR Code

* integration with `kof.image`; read; generate.

---

# 29. Integration tests

Create real pipelines.

Conceptual examples:

```text
Image file → kof.file → kof.image → grayscale → threshold → kof.vision → contours → result
Camera → Image → Vision → Detection
Image → QR Code Reader → Text
```

---

# 30. Benchmarks

Add benchmarks for critical operations:

* decode; encode; resize; grayscale; blur; edge detection;
  convolution; segmentation; feature detection.

Compare:

* image size; time; memory; throughput.

Do not make performance claims without a benchmark.

---

# 31. Incremental implementation

Do not try to create the whole computer-vision stack at once.

### Phase 1

```text
kof.image
├── Image
├── Pixel
├── Color
├── ImageIO
└── resize/crop/rotate
```

### Phase 2

```text
processing
├── grayscale
├── blur
├── threshold
├── histogram
└── edges
```

### Phase 3

```text
kof.vision
├── contours
├── lines
├── circles
├── geometry
└── segmentation
```

### Phase 4

```text
camera  tracking  features  OCR
```

### Phase 5

```text
ML  object detection  classification  semantic segmentation  GPU acceleration
```

The order may change according to the architecture and the existing
targets.

---

# 32. Architecture criteria

Do not turn `kof.image` into:

* an OpenCV clone;
* an ML framework;
* a graphics library;
* an image editor;
* a giant wrapper of external libraries.

`kof.image` must handle **images**.

`kof.vision` must handle **computer vision**.

External runtimes must remain implementation details.

---

---

# 34. TODO — what is missing (implementation plan, 29/09)

Decision **`D-IMAGE-SURFACE`** (maintainer 29/09): the value surface **reuses
`Raster`** (no new `Image`/`Pixel`/`Color` types); the codecs are **pure Kof
whenever feasible** (full cross-target parity, zero gaps) and fall back to the
JVM **imageio** interop only where a pure-Kof decoder is technically
infeasible. No gap is added "just to add one" — it exists only where the
ability genuinely does not exist on a target.

**Done (pure Kof, all targets):**
- metadata for 17 formats (`Image.kf`);
- raw decode: PNM `P5`/`P6`, farbfeld, BMP 24/32-bit, **QOI** (all chunks);
- ops: `cropRaster`, `resizeNearest`, `flipHorizontal`/`flipVertical`,
  `rotate90`, `grayscale`, `threshold`, `boxBlur`.

**Missing — ordered by cost:**

1. **PNG decode (pure Kof) — LANDED 29/09 on JVM/riscv64/Script/x86-64 (`known-bugs` §541, fixed 29/09: x86 heap returned dirty reused memory, now zeroed).**
   - Files: `libs/image/Png.kf` (new), `libs/image/Raster.kf` (`decodeRaster`
     dispatch `fmt == "PNG"`).
   - Work: `IHDR` parse (color type 0/2/3/4/6, bit depth 8), `IDAT`
     concatenation, **zlib inflate** (DEFLATE: stored/fixed/dynamic Huffman)
     in pure Kof, per-scanline filters 0–4 (None/Sub/Up/Average/Paeth),
     de-palette (`PLTE`) and expand to the `Raster` channels.
   - Proof: `PngDecodeE2ETest` (known-good PNG bytes → golden samples) on JVM +
     Native x86-64 + riscv64 + Script; JS `IOJS001` (the library still uses
     `readRange`). No new gap: the decoder is target-independent.
   - Risk: inflate correctness; mitigate with a fixed/dynamic-block golden and
     the zlib Adler-32 check (ignore trailing, must not crash).
2. **JPEG decode (infeasible pure Kof → JVM imageio interop) — LANDED 29/09.**
   - `kof.image` platform builtin `image.decode(path): Int[]` (layout
     `[w,h,samples…]`) + JVM runtime `JvmImageRuntime` via
     `javax.imageio.ImageIO`; `libs/image/Jpeg.kf` wraps it as
     `decodeJpegRaster(path): Raster` (JPEG, RGB/RGBA); the wrapper binds the
     result to an explicit `Int[]` local (`var` on the call inferred an
     `Unknown[]` element in the emit — measured, harmless once typed). Other targets:
     honest **`IMG001`** compile-time gap in the namespace lowering
     (`ExpressionMethodCallLowerer`), never a silent fallback. Because the
     builtin exists only on the JVM, importing `image.Jpeg` is the explicit
     JVM-only commitment; the gap-free pure-Kof formats in `Raster.kf` stay
     untouched. Registered in the stdlib ledger (`platform`,
     `experimental`) and pinned in the parity matrix.
   - Proof: `RasterDecodeE2ETest#jpegDecodesOnJvmViaInterop` (JVM golden ==
     an ImageIO-decoded fixture) + `#jpegOnNonJvmIsImg001` (JS refuses with
     `IMG001`) + `DomainGapCodesTest#imageDecodeOnJsIsImg001`.
3. **GIF — LANDED 29/09 (pure Kof, all targets).** `libs/image/Gif.kf` decodes the first frame with a Kof LZW (variable width 2–12, KwKwK), global/local palette and interlaced rows, RGB output.
   **WebP VP8L — slices A–I LANDED 30/09 (pure Kof, all targets):** `libs/image/Vp8l.kf` + `libs/image/Vp8lTransforms.kf` decode the full VP8L lossless path: **simple and normal (code-length) Huffman**, **LZ77 backward references**, the **color cache**, the **predictor + color inverse transforms** (14 predictor modes and the §3.5.2 color delta, applied in reverse order), the **COLOR_INDEXING transform** (§3.5.4) and **meta-Huffman groups** (§3.7.2.2; RFC 9649 §3.5/§3.6.2.1/§3.6.2.2/§3.7). Fixtures are libwebp-validated (the hand-built normal-Huffman+LZ77 stream, a libwebp-generated 8x8 color-cache stream, a libwebp-generated 8x8 predictor+color stream, a libwebp-generated 8x8 8-color indexing stream and a libwebp-generated 8x8 two-group meta-Huffman stream, all byte-matched by PIL); the VP8L native face was re-tested green after the §541/§543 native fixes. No VP8L machinery remains refused. Next: WebP lossy (`VP8 `) and AVIF still pending (interop/gap).
4. **Encode/write** (`encodeRaster` for PNM/BMP/farbfeld/QOI) — pure Kof,
   parity; deferred until a write surface is requested.
5. **Larger rasters — LANDED 30/09 (post-`§540`/`§542`).** The cap that was
   deliberately held at 16384 samples until the native allocation fixes landed
   is raised to **262144** (a 1 MiB `Int` array, fitting the cross-native
   16 MiB arena of `known-bugs` §540 and the x86-64 contiguous arena of §542).
   Proof: `RasterDecodeE2ETest#largeRasterAboveOldCapDecodes` (JVM) and
   `#largeRasterAboveOldCapDecodesOnNativeRiscv64` (riscv64/qemu) decode a
   200×200 P6 (120 000 samples, far past both the old 16384 cap and the old
   256 KiB cross arena) plus the bumped `#oversizedRasterThrows` (400×400).
   The VP8L path now shares the same guard (`libs/image/Vp8l.kf` calls
   `guardRaster(pixels * 4)` instead of its own 16384-pixel cap), verified on
   JVM and Native x86-64 with a libwebp-generated 160×120 lossless WebP
   (`#largeWebpAboveOldPixelCapDecodesOnJvm`/`...OnNativeX86`); Native riscv64
   aborts on the same fixture and is quarantined by new `known-bugs` **§544**
   (owner = native/GC lane, issue #700).

## PT
[Português](image-vision-plan.pt_BR.md)

# 33. Final rule

The goal is for Kof to evolve from:

```text
arquivo → imagem → processamento → visão computacional → resultado
```

with own, consistent, cross-platform APIs.

The Kof developer must not need to abandon the language to do:

* image processing;
* camera reading;
* detection;
* OCR;
* QR Code;
* visual analysis;
* model inference.

Everything must be built incrementally, preserving the existing base
and following the philosophy of Kof:

**less accidental complexity, small APIs, clear intention and control
over the implementation.**
