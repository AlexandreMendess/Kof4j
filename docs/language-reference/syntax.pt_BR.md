[English](syntax.md) | [Português](syntax.pt_BR.md)

# Sintaxe Concreta (formas e exemplos)

**Status:** Stable · Este documento mostra a **forma concreta** de cada
construção com exemplos mínimos verificáveis. As **regras formais** estão em
[grammar.md](grammar.md); os **tokens** em [lexical-structure.md](lexical-structure.md);
a **semântica** nos documentos de domínio. Não repete — referencia.

> Todo exemplo aqui **compila** no `kof-compiler` 0.5.0-beta (verificado por
> probe/suíte). Exemplos que *parecem* válidos mas não compila estão listados
> em [lexical-structure.md](lexical-structure.md) §5.3 e
> [specification-gaps.md](../bugs-and-gaps/specification-gaps.md).

---

## Programa mínimo

`kof
main() {
    println("Olá, mundo")
}
`

## Variáveis

`kof
var x = 10              // inferido int, mutável
val y = 20              // imutável (reatribuição → SEM037)
String nome = "Mel"     // type-first
var idade: Int = 30     // anotado
String? opcional = find(key) // nullable — null via API (literal `= null` é SEM048)
var arr: Int[] = new Int[3]
`

## Funções

`kof
Int dobro(Int x) { return x * 2 }
dobro2(Int x): Int { return x * 2 }   // retorno sufixado
Bool positivo(Int x) = x > 0          // expression body
void faz() { println("x") }
main(args: List<String>) { println(args.size) }
`

## Controle de fluxo

`kof
var s = if (cond) "a" else "b"        // if-EXPRESSION (else obrigatório)
if (cond) { … } else { … }            // if-STATEMENT (else opcional)
while (cond) { … }
do { … } while (cond)
for (var i = 0; i < n; i++) { … }
for (var item in lista) { … }          // for-in (var obrigatório)
switch (x) {
    case 1: println("um"); break
    default: println("outro")
}
var d = switch (x) {                   // switch-EXPRESSION
    case 1 -> "um"
    default -> "outro"
}
`

## Classes e dados

`kof
class User {
    String name
    Int age
    constructor(String name, Int age) {
        this.name = name
        this.age = age
    }
    String greeting() { return "Hello " + name }
}
var u = User("Mel", 26)               // sem new
u.age = 27                             // mutável, campo direto

record Point(Int x, Int y)             // imutável, accessors
var p = Point(10, 20)
println(p.x())                         // 10

enum Color { Red, Blue }               // constantes; cada uma é instância singleton
println(Color.Red)                     // "Red"
// Color.Red == "Red"                  // SEM062: um valor de enum não é String

interface Shape { Double area() }
class Circle(Double r) implements Shape {   // ⚠ class X(...) = record!
    Double area() { return 3.14 * r * r }
}
`

## Coleções

`kof
var l = listOf(1, 2, 3)
l.add(4)
println(l.get(0))
println(l.size)                        // propriedade (não método)
var m = mapOf("a", 1, "b", 2)
println(m.get("a"))
var s = setOf("x", "y")
println(s.contains("x"))
var nomes = users.map((u: User) -> u.name)
var adultos = users.filter((u: User) -> u.age >= 18)
var total = nums.reduce((a: Int, b: Int) -> a + b, 0)
`

## Strings

`kof
var s = "Hello"
println(s.length)                      // propriedade
println(s.substring(1))
println(s.contains("ell"))
var a = "ab"; var b = "a" + "b"
println(a == b)                        // conteúdo → true (NUNCA .equals)
`

## Lambdas e closures

`kof
var f = (x: Int) -> x * 2
println(f(5))
var n = 0
var inc = () -> { n = n + 1 }          // captura mutável (Box)
inc(); inc()
println(n)                             // 2
list.forEach { x: Int -> println(x) }  // trailing lambda
`

## Erros

`kof
try {
    throw "not found: " + key          // exceções são String
} catch (String e) {
    println("falhou: " + e)
} finally {
    println("cleanup")
}
`

## Concorrência

`kof
spawn trabalho()                       // fire-and-forget
val r = spawn compute()                // Handle<T>
var v = await r                        // bloqueia
var w = awaitTimeout(r, 1000)          // com prazo (função, não sintaxe)
`

## Null safety

`kof
var nome: String? = find(key)
if (nome != null) {
    println(nome.length)               // narrowing só no then-branch
}
`

## Pacotes e imports

`kof
package com.dev.app
import com.dev.NodeUI
import kof.json

main() {
    var l: List<NodeUI> = listOf()     // type-arg resolvido pelo import
}
`

## Annotations (interop)

`kof
@JsonFormat(using = MyMapper.class)
record Dato(Int x)
`

## FFI com C — `extern` (JVM, qualquer assinatura escalar)

`kof
extern "/lib/x86_64-linux-gnu/libm.so.6" cos(Double x): Double
main() { println(cos(0.0)) }            // JVM: 1.0
`

A JVM liga **qualquer assinatura escalar** (R3 generalizado 18/09) — aridade
livre, escalares em qualquer posição, retorno `void` e `String`. Medidos no
tip: `fmod(Double,Double):Double`→`1.5`; `ldexp(Double,Int):Double`→`12.0`;
`strncmp(String,String,Int):Int`→`-1`; `puts(String):void`;
`getenv(String):String`→`mel`. O nome da função Kof É o símbolo C (sem alias).
Não-escalares são **parcialmente** ligados: na **JVM** um `record` por valor (argumento e retorno), um `T[]` escalar→`ptr` (copy-in por chamada) e um out-buffer `Buffer(U8)` (INOUT: copy-in / chamada / copy-back, criado com `buffer.alloc`) ligam desde a 3.8b (20–21/09, `FfiStructE2ETest` 10/10, `FfiArrayE2ETest` 5/5, `BufferFfiE2ETest` 4/4), e um parâmetro de tipo função liga como callback C em JVM/JS (3.4); as faces restantes (struct/array/out-buffer no Native, bridge de struct no JS, callbacks no Native) → `FFI001` em tempo de
compilação; o **runner host** JS (GraalJS/node) liga a MESMA ABI escalar via
`KofJsFfiBridge` — paridade JVM↔JS provada 18/09 (`FfiE2ETest` 16/16, fatia
3.6.F2/F3 ✅) — com não-escalares → `FFI002` ali e o browser um erro honesto
de **runtime** (R7, sem host, o mesmo degrade de `kof.io`); o **Native liga a MESMA
ABI escalar DIRETO em x86-64, riscv64 e aarch64** (#431 fatias 1–2, 20/09, §369): a
`library()` é entrada de link-by-use no linker e a chamada é `call sym@PLT` — sem
`dlopen` (§61 fechado). Não-escalar ou `library()` ausente → `FFI001` na linha da
declaração no Native (R6). Argumentos numéricos seguem a regra comum de conversão do Kof em todos
os alvos (§370/#549 corrigido 20/09): `Int`/`Double` num slot `Float`, `Int`/`Long`/`Float` num
slot `Double` etc. são convertidos; String/Bool/`Double→Int` são `SEM014`.
O caminho da lib é resolvido em **runtime** na JVM/JS (símbolo ausente =
exceção `kof_ffi_*`). O alargamento era a fatia R3
(`docs/architecture/IMPLEMENTATION-UNIVERSAL-PLATFORM.pt_BR.md`, #431); a face JVM landou 18/09 (`.18`), o host JS no mesmo dia, o Native x86-64 + riscv64/aarch64 em 20/09, e as formas D6 struct/array/out-buffer na JVM (record por valor in/out, `T[]`→`ptr` copy-in, `Buffer(U8)` INOUT) em 20–21/09.

## Testes e lifecycle

`kof
test "soma funciona" {
    assert(1 + 1 == 2)
}
application {
    onStart { println("subiu") }
    onShutdown { println("desceu") }
}
main() { println("rodando") }
`

---

## Anti-formas (NÃO compila — ver gaps)

`kof
fun f() {}          // ❌ `fun`/`fn`/`func` são palavras reservadas (SG-001) — não existem; use `f() { }`
let x = 1           // ❌ não existe — nem em .ks (KofScript é Kof puro)
var x = [1,2]       // ❌ literal de array não existe — use listOf(1,2)
var s = {"a","b"}   // ❌ literal de set não existe — use setOf("a","b")
if (x in lista) {}  // ❌ sem operador in — use lista.contains(x)
var t = c ? a : b   // ❌ sem ternário — use if (c) a else b
var n = x ?? d      // ❌ sem null-coalesce
var r = 1..10       // ❌ sem range
class X(val a) {}   // ❌ = record (imutável), não classe mutável
`
