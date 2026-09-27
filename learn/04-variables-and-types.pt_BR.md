[English](04-variables-and-types.md) | [Português](04-variables-and-types.pt_BR.md)

# 04 — Variáveis e Tipos

## O que você vai aprender

Neste capítulo você vai entender como declara variáveis, como o sistema de tipos funciona, e como a inferência de tipos opera.

## Declaração de variáveis (0.5.0-beta)

Em Kof existem duas palavras-chave para variáveis (`var`/`val`); NÃO há
`let`/`const` (sugar JS removido do KofScript em 06/09 — ver abaixo):

### `var` — variável mutável

```kf
var nome = "Mel"
nome = "Outro"  // funciona
```

### `val` — Valor constante

```kf
val PI = 3.14
// PI = 2.0  // ERRO: não pode reatribuir
```

### `let` / `const` — NÃO existem (sugar removido 06/09)

KofScript é **Kof puro executado diretamente** — NÃO é JavaScript. Não há
**alias `let`/`const`**: `let x = 5` dá `SEM011`/`PARSE011` (*medido no jar do
tip, 16/09*). Use `var`/`val`; o único serviço do wrapper é o modelo de
script — `var`/`val` no topo viram campos estáticos de `KofScriptGlobals` e
statements soltos viram `main()`:

```kf
var nome = "Mel"   // .ks no topo → KofScriptGlobals.nome (campo estático)
val pi = 3.14      // o mesmo; não existe let/const
println(nome)      // um statement solto é embrulhado em main()
```

## Tipagem explícita

Você pode especificar o tipo explicitamente:

```kf
Int idade = 26
String nome = "Mel"
Bool ativo = true
```

## Inferência de tipos

Quando você usa `var` ou `val`, o compilador infere o tipo:

```kf
var idade = 26        // compilador sabe que é Int
var nome = "Mel"      // compilador sabe que é String
var pi = 3.14         // compilador sabe que é Double
var ativo = true      // compilador sabe que é Bool
```

Isso **não** é tipagem dinâmica. O compilador conhece o tipo em compile-time. É apenas uma forma mais concisa de escrever.

```kf
// Essas duas linhas são equivalentes:
var nome = "Mel"
String nome = "Mel"
```

## Tipos de referência

### Records

```kf
record Point(Int x, Int y)
```

### Troolean (trivalente)

`Bool` tem exatamente dois valores; `Troolean` carrega `true`/`false`/`unknown`
(D-TROOL, 0.5.0-beta). `Bool?` escrito pelo usuário é SEM095 — o erro aponta
`Troolean`. `if (t)` é açúcar para `if (t == true)`; `&&`/`||`/`!` seguem as
tabelas de Kleene (o unknown persiste). Detalhes: `training/language/types.md`
§Troolean.

### Classes

```kf
class User(String name)
```

### Arrays

Não existe literal de array (`{1, 2, 3}` / `[1, 2, 3]` não compilam). Use
`new Tipo[n]` e preencha por índice:

```kf
var numeros = new Int[3]
numeros[0] = 1
numeros[1] = 2
numeros[2] = 3
println(numeros.length)    // 3
```

Para uma sequência dinâmica, use `listOf(1, 2, 3)` (ver cap. 12).

### Enums

```kf
enum Color { Red, Green, Blue }
```

Um enum declara um conjunto fechado de constantes. O valor em runtime é o
próprio nome — comparação é por conteúdo (`==` funciona como esperado) e
`println(Color.Red)` imprime `Red`.

API embutida:

| Chamada | Retorna | Descrição |
|---------|---------|-----------|
| `Color.values()` | `List<String>` | todas as constantes, na ordem declarada |
| `Color.valueOf("Red")` | `Color?` | constante pelo nome; `null` se inválida |
| `c.name()` | `String` | o nome da constante |

Constante inexistente é erro de compilação:

```kf
Color.Nope   // SEM030: enum 'Color' não tem constante 'Nope'
```

**Switch exaustivo**: um switch sobre enum precisa cobrir **todas** as
constantes ou ter `default` — senão vira erro `SEM031` listando os casos
faltantes:

```kf
String nome(Color c) {
    var r = ""
    switch (c) {
        case Color.Red:   { r = "vermelho" }
        case Green:       { r = "verde" }      // não-qualificado também vale
        case Color.Blue:  { r = "azul" }
    }
    return r
}   // sem os três casos e sem default → SEM031
```

## Conversões

### Widening (automática)

O compilador converte automaticamente tipos menores para maiores:

```kf
Int i = 42
Long l = i     // Int → Long (automático)
Double d = i   // Int → Double (automático)
```

### Narrowing (casting)

A conversão de maior para menor precisa de cast explícito:

```kf
Double d = 3.14
Int i = d as Int   // Double → Int (precisa de 'as')
```

## Compatibilidade com tipos Java

Kof usa os mesmos tipos da JVM:

| Kof | Java | JVM |
|-----|------|-----|
| `Bool` | `boolean` | `Z` |
| `Byte` | `byte` | `B` |
| `Short` | `short` | `S` |
| `Int` | `int` | `I` |
| `Long` | `long` | `J` |
| `Float` | `float` | `F` |
| `Double` | `double` | `D` |
| `Char` | `char` | `C` |
| `String` | `String` | `Ljava/lang/String;` |

## KofScript: `var`/`val` no topo + String? (0.2.0; sugar let/const REMOVIDO 06/09)

```kf
var x = 5            // .ks no topo → campo estático de KofScriptGlobals
String? s = mapOf("k", "abc").get("k")   // nullable básico — null via API (= null é SEM048)
if (s != null) { println(s.length()) }
```

## Status atual (0.5.0-beta)

✅ `var` e `val` funcionam
✅ `var`/`val` no topo do KofScript → `KofScriptGlobals` (NÃO existe `let`/`const` — sugar JS removido 06/09)
✅ `String?` nullable básico
✅ Inferência de tipos funciona
✅ Records funcionam
✅ Classes com campos funcionam
✅ Type checking (análise semântica, erros `SEM` em compile-time)
✅ Conversões automáticas (widening: `Int→Long`, `Int→Double`)

## Exercício 1

Declare variáveis de todos os tipos primitivos (Int, Double, Bool, Char...) e imprima seus valores com println.

## Exercício 2

Crie um record `Produto` com campos `nome String`, `preco Double` e `quantidade Int`. Crie uma instância e acesse seus valores.

## Próximo passo

[Controle de Fluxo →](05-control-flow.md)