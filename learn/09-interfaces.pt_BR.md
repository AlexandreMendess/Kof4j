[English](09-interfaces.md) | [Português](09-interfaces.pt_BR.md)

# 09 — Interfaces

## O que é uma interface

Uma interface define um contrato: métodos que uma classe deve implementar.

```kf
interface Comparavel {
    Bool maiorQue(Comparavel outro)
}
```

## Implementando uma interface

```kf
class Produto(String nome, Double preco) implements Comparavel {
    Bool maiorQue(Comparavel outro) {
        if (outro instanceof Produto) {
            var p = outro as Produto
            return this.preco > p.preco
        }
        return false
    }
}
```

## Interface com herança

```kf
interface Listavel<T> {
    T get(Int indice)
    Int tamanho()
}

interface Editavel<T> extends Listavel<T> {
    void set(Int indice, T valor)
    void adicionar(T valor)
    void remover(Int indice)
}
```

## Métodos default

Um método de interface COM corpo é um método default — os implementadores
herdam (#213; §248: JVM, Native x86_64, riscv64/aarch64 e JS):

```kf
interface Greeter {
    String greet(String name)
    String greetLoud(String name) { return greet(name).toUpperCase() }
}

class Pt implements Greeter {
    String greet(String name) { return "Ola " + name }
}

println(Pt("Mel").greetLoud("Mel"))   // OLA Mel (o default chama greet)
```

- A classe também pode sobrecarregar um default herdado por ARIDADE —
  `greetLoud(String, Int)` com assinatura diferente coexiste e o typer
  seleciona por aridade (#610/#611).
- Herdar a MESMA assinatura default de duas interfaces NÃO relacionadas é o
  diamante do Java → erro de compilação **SEM101**: "inherits conflicting
  default method ... (add an explicit override in ...)". Sobrescrever na
  classe resolve; o default de uma subinterface comum vence legitimamente.

## Status atual

✅ Parser e análise semântica reconhecem interfaces
✅ Implementação (`implements`) funciona e é validada em compile-time
✅ JVM: gera interface padrão com métodos `ACC_ABSTRACT` (chamadas via interface com retorno primitivo corrigidas)
✅ Native: gera vtable (dispatch virtual) para as implementações
✅ KofJS: interfaces são de nível de tipo — chamadas são reduzidas a dispatch estrutural por nome de método

Funciona nos três targets (JVM, Native, KofJS).

## Multiplatform

Interfaces funcionam tanto no backend JVM quanto no nativo:

**JVM:** gera uma interface JVM padrão
**Nativo:** gera um vtable (tabela de dispatch virtual)

```kf
interface Comparavel {
    Bool maiorQue(Comparavel outro)
}
```

No JVM:
```java
// Gerado: interface Comparavel { boolean maiorQue(Comparavel); }
```

No nativo:
```c
// Gerado: struct Comparavel_vtable { bool (*maiarQue)(void*, void*); }
```

## Como adicionar uma interface

1. Declare a interface no arquivo `.kf`
2. Implemente em uma classe usando `implements`
3. Compile com `kof build`
4. O bytecode gerado terá a interface correta

## Próximo passo

[Herança →](10-inheritance.md)