[English](11-generics.md) | [Português](11-generics.pt_BR.md)

# 11 — Generics

> **Status: implementado (JVM / Native / JS) — 0.5.0-beta — erasure + `Box<T>` com `T` primitivo**
>
> Generics por erasure funcionam nos três targets; `Box<Int>` com `substituteTypeVariable` + `kof_int_to_string` nativo já está em 0.2.0.

## O problema

Sem generics, você precisa de casts:

```java
List lista = new ArrayList();
lista.add("texto");
String texto = (String) lista.get(0);  // cast manual
```

Com generics, o compilador sabe o tipo:

```java
List<String> lista = new ArrayList<String>();
lista.add("texto");
String texto = lista.get(0);  // sem cast
```

## Generics em Kof

### Classes genéricas

```kf
class Box<T> {
    T value

    set(T v) {
        value = v
    }

    get(): T {
        return value
    }
}
```

Uso:

```kf
var caixaTexto = new Box<String>()
caixaTexto.set("olá")
var caixaNumero = new Box<Int>()
caixaNumero.set(42)
println(caixaNumero.get())   // 42 — Box<T> com T primitivo
```

`Box<T>` com `T` primitivo (`Box<Int>`) funciona nos três targets — no Native
o `get()` que devolve `T` tem o tipo substituído em compile-time
(`substituteTypeVariable`), então `println(b.get())` imprime o valor e não
vira segfault.

### Métodos genéricos

Os parâmetros de tipo vêm **depois** do nome da função:

```kf
identity<T>(T x): T {
    return x
}

main() {
    println(identity(42))     // 42
    println(identity("hi"))   // hi
}
```

### Interfaces genéricas

```kf
interface Mapper<T> {
    map(input: T): String
}

class Upper implements Mapper<String> {
    map(input: String): String { return input.upper() }
}
```

- `interface I<T>` parseia, e `implements I<Concreto>` apaga os argumentos de
  tipo em todo ponto de emissão — a variável de tipo leva o BOUND para a
  chamada (famílias §355–§357, #160).
- A subclasse herda a entrada genérica do ancestral: `class Sub extends Base`
  onde `Base implements Runner<Int>` continua despachando como `Runner<Int>`
  (resolução BFS).
- Bridges de retorno de método são sintetizadas quando duas interfaces colidem
  no mesmo membro com retornos diferentes (boxing Int/Boolean, retornos
  covariantes — §486): record/classe implementando interfaces genéricas
  funciona em TODOS os alvos.
- Recusas honestas conhecidas: `SEM098` rejeita array primitivo passado a um
  slot apagado (decisão só-JVM); `NAT004` recusa `toString` sobre `T` não
  delimitado no link cross Native (§358).

### Bounds (planejado)

`extends` em parâmetros de tipo ainda não é resolvido em compile-time.

## Variância (planejado)

```kf
void copiar(List<? extends Animal> origem, List<? super Animal> destino) {
    for (var animal : origem) {
        destino.add(animal);
    }
}
```

## Interoperabilidade com generics Java

```kf
// Kof usando generics Java
var lista = new java.util.ArrayList<String>();
lista.add("hello");
String item = lista.get(0);
```

## Próximo passo

[Collections →](12-collections.md)
