[English](classes.md) | [Português](classes.pt_BR.md)

# Idioms — Classes

**Status:** available · **Introduced:** 0.0.4-alpha · **Updated:**  0.5.0-beta (Sep 2026)

## What it is

Classe com campos, métodos, construtor, herança e interfaces.
Campos são declarados **sem** `var`/`val` e sem `;` obrigatório.

```kof
class User {
    String name
    Int age
}
```

## Construtores

### `class X(...)` é RECORD — dados imutáveis (verificado 02/09)

```kof
class User(String name, Int age) {
    greeting(): String {
        return "Hello " + name
    }
}
```

> **Atenção (02/09):** `class X(...)` é **alias de `record X(...)`** — o parser
> o trata como record body (imutável, `extends java.lang.Record` no JVM). Os
> "parâmetros" viram componentes com accessors: leitura `user.name` funciona
> (vira `name()`), mas **escrita `user.name = "x"` NÃO** (campo final →
> `IllegalAccessError`). Para dados imutáveis use `record` (a forma canônica);
> para **estado mutável** use campos explícitos + `constructor(...)`.

```kof
var user = User("Mel", 26)   // record — leitura ok, escrita não
```

### Construtor explícito — estado mutável (a forma de classe real)

```kof
class User {
    String name
    Int age

    public constructor(String name, Int age) {
        this.name = name
        this.age = age
    }
}
```

Aqui os campos são **públicos e mutáveis**: `user.name = "Mel"` / `user.age = 30`.

`new User("Mel", 30)` continua válido, mas `User("Mel", 30)` é a forma
recomendada — o compilador trata ambas como construção de instância.

## When to use

- Entidades com comportamento (métodos que operam sobre o estado).
- Estado mutável.
- Herança e polimorfismo.

## When not to use

- Dados imutáveis sem comportamento → **record** (veja `records.md`).
- Apenas agrupamento de valores → record.

## BAD — cerimônia de getter

```kof
class User {
    private String name
    public getName(): String {
        return name
    }
    public setName(String name) {
        this.name = name
    }
}
```

## GOOD — campo direto

```kof
class User {
    String name
}
```

Uso: `u.name = "Mel"` e `println(u.name)`.

## WHY

Getter/setter de Java existe por convenções de encapsulamento (JavaBeans, frameworks).
Kof não possui essas convenções. Campo público é a forma idiomática até que exista
uma razão real para encapsulamento. Não reproduza ceremony sem semântica.

## BAD — factory trivial

```kof
createUser(String name): User {
    return User(name)
}
```

## GOOD

```kof
User(name)
```

## WHY

Uma factory que apenas delega ao construtor não adiciona informação. Chame o construtor.

## Herança

```kof
class Animal {
    String name
    public constructor(String name) {
        this.name = name
    }
    speak(): String = "animal"
}
class Dog extends Animal {
    public constructor(String name) {
        super(name)
    }
    speak(): String = "dog"
}
```

- `super(args)` é a primeira instrução do construtor da subclasse.
- Override é implícito (mesmo nome de método).
- Dispatch é virtual em ambos os targets.
- `super.campo` lê/escreve um campo **herdado** preservando o tipo declarado
  (`super.width = w` não vira `Object`). O mesmo campo escrito via
  `this.width` ou `width` a puro nome tem comportamento idêntico — `super`
  só diz "comece a busca na superclasse".

```kof
class Rect extends Shape {
    resize(Float w) {
        super.width = w          // campo herdado — store tipado, sem temporário
    }
}
```

## Generics Box<T> (0.5.0-beta)

```kof
class Box<T>(T value) {
    get(): T { return value }
}
var b: Box<Int> = Box(42)
println(b.get())   // erasure + substituteTypeVariable — Native OK
```

## Tipos selados e switch exaustivo (0.5.0-beta, §X5.1/§X5.2)

`sealed` fecha o conjunto de subtipos diretos: todo subtipo direto precisa estar
na **mesma unidade de compilação** do tipo selado (`SEM080`). Um `switch` sobre
sujeito sealed precisa cobrir todos os subtipos diretos, ou ter `default`
(`SEM081`).

```kof
sealed class Shape
class Circle extends Shape { ... }
class Square extends Shape { ... }

String describe(Shape sh) {
    return switch (sh) {
        case Circle c -> "circle"
        case Square q -> "square"
    }
}
```

- `sealed` é keyword **contextual** (segue identificador válido).
- É apagado na emissão — sem custo de runtime, idêntico em todos os alvos.

## Variância `out`/`in` e projeção no sítio de uso (0.5.0-beta, §X5.3/§X5.4)

`out T` (covariante) / `in T` (contravariante) num type-param diz que o tipo só
**produz** / só **consome** `T`; sem prefixo, o parâmetro é invariante (o padrão).

```kof
record Source<out T>(T value)      // componente somente-leitura = posição out
class Sink<in T> { String consume(T v) { return "x" } }

Source<Animal> up(Source<Dog> d) { return d }  // OK — covariância
Sink<Dog> down(Sink<Animal> w) { return w }    // OK — contravariância
```

Um `out T` em posição de entrada (parâmetro/campo gravável) ou `in T` em posição
de saída (retorno/campo/componente de record) é insólido → `SEM082`.

A projeção também pode ser escrita no **sítio de uso**, em qualquer tipo — mesmo
um invariante:

```kof
List<out Animal> up(List<Dog> xs) { return xs }   // covariante no uso
List<in Dog> down(List<Animal> xs) { return xs }  // contravariante no uso
List<Animal> same(List<Dog> xs) { return xs }     // SEM021 — invariante
```

- `out`/`in` seguem keywords contextuais dentro de type-arguments.
- Variância e projeção são **só de compile-time** — apagadas na emissão.

## Sobrecarga de Métodos (0.5.0-beta, §131)

```kof
class Calc {
    Int add(Int a, Int b) { return a + b }
    Int add(Int a, Int b, Int c) { return a + b + c }
    String add(String a, String b) { return a + b }
}
var c = Calc()
c.add(1, 2)          // 3
c.add(1, 2, 3)       // 6
c.add("ko", "f")     // "kof"
```

Métodos de mesmo nome com assinaturas diferentes (aridade ou tipos de
parâmetro) coexistem na classe, resolvidos pelo typer nos 4 backends.
Duplicata exata → SEM047; ambiguidade → SEM057; só o tipo de retorno
NÃO distingue — regras completas em `functions.pt_BR.md` (valem
igualmente para métodos).

## Anti-patterns relacionados

- Utility class de métodos estáticos → funções top-level (`functions.md`)
- Service layer sem estado → funções top-level
- Factory trivial → chamar o construtor
- `Box<T>` manual → usar generics nativo