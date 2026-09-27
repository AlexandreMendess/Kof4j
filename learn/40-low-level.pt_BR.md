[English](40-low-level.md) | [Português](40-low-level.pt_BR.md)

# 40 — Baixo Nível: FFI, Perfis Native e Bare Metal

> **Status: implementado (JVM / Native x86-64 / riscv64 / aarch64 / JS) — 0.5.0-beta**
>
> Kof compila para código de máquina de verdade (assembler e linker próprios,
> ABI System V AMD64) e vincula bibliotecas C reais. Este capítulo é a
> superfície: `extern` para FFI C, `--profile` para executáveis freestanding
> e o caminho bare-metal.

## `extern` — vincule uma função C

```kf
extern "/lib/x86_64-linux-gnu/libm.so.6" cos(Double x): Double
extern "/lib/x86_64-linux-gnu/libc.so.6" atoi(String s): Int

main() {
    println(cos(0.0))   // 1.0
    println(atoi("42")) // 42
}
```

- O NOME da função Kof é o símbolo C (não existe sintaxe de alias).
- Assinaturas ESCALARES completas vinculam: `Int`, `Long`, `Float`, `Double`,
  `Boolean`, `String` (char\* ↔ `String` UTF-8) e `void`, em qualquer posição,
  qualquer aridade.
- Argumentos numéricos seguem a regra de conversão ordinária do Kof: `f(4)`
  num slot `Float` converte (Int→Float), `sqrt(9)` (slot Int→Double) — o
  mesmo resultado no JVM, Native e JS. O que o Kof NÃO converte (`String` em
  slot numérico, estreitamento `Double`→`Int`) é **SEM014** no ponto de
  chamada.
- Um `record` de campos escalares cruza POR VALOR (argumento e retorno); um
  `T[]` escalar cruza como ponteiro com copy-in; um `Buffer(U8)` cruza INOUT.
- Diagnósticos honestos: forma não-escalar/não suportada ou biblioteca
  ausente é **FFI001** na linha da declaração; formas genuinamente não
  suportadas (String[], List, Handle) são **FFI002**. Nunca um stub silencioso.

## Callbacks — entregue uma função Kof ao C

```kf
extern "libcallback.so" kof_cb_add(Int a, Int b, (Int, Int) -> Int cb): Int

main() {
    println(kof_cb_add(20, 22, (x: Int, y: Int) -> x + y))  // 42
}
```

O lambda vira um ponteiro de função C real (síncrono, não escapa; ABI de
primitivos + String). No host runner JS os callbacks vinculam com paridade
byte a byte com o JVM; o browser degrada com erro de runtime honesto.

## Suporte por alvo

| Face | JVM | Native x86-64 | riscv64/aarch64 | JS |
|------|-----|----------------|------------------|-----|
| `extern` escalar | ✅ downcall FFM | ✅ direto (link-by-use) | ✅ medido sob qemu | ✅ bridge do host |
| Callbacks | ✅ upcall stub | FFI001 (honesto) | FFI001 | ✅ host runner |
| Struct / array / out-buffer | ✅ | FFI001 | FFI001 | ✅ (JVM==JS) |

Retorno String no Native é cópia de fronteira (o buffer C nunca é liberado);
char\* `NULL` vira `null` Kof.

## Perfis native — `--profile`

```bash
kof build app/ --target native --profile freestanding
```

- `host` (padrão): o ELF padrão com o runtime Kof completo.
- `freestanding`: sem libc — o runtime fornece ele mesmo as costuras da HAL
  `kof_plat_*` (print/exit/random/alloc); o mesmo fonte Kof compila.

O compilador também carrega os perfis bare-metal (`bios`/`mbr`, `uefi`,
`uefi-ring`) usados pela frente da série B — o payload boota pelo SeaBIOS
sob qemu. Ainda não são expostos como flag de CLI embarcada; quando forem,
este capítulo é o contrato que os descreve.

## O que o backend native realmente emite

```kf
main() = print("Hello")
```

compila (sem toolchain externa — o assembler e o linker são do próprio Kof)
para um ELF cujo `_start` emite syscalls crus. Detalhes, opções de backend e
a história multi-arquitetura vivem em [Native — Multiplataforma](native/README.md).

## Próximo passo

[Glossário →](glossary.md)
