[English](syntax.md) | [Português](syntax.pt_BR.md)

# Concrete Syntax (forms and examples)

**Status:** Stable · This document shows the **concrete form** of each
construct with minimal verifiable examples. The **formal rules** are in
[grammar.md](grammar.md); the **tokens** in [lexical-structure.md](lexical-structure.md);
the **semantics** in the domain documents. It does not repeat — it references.

> Every example here **compiles** in `kof-compiler` 0.5.0-beta (verified by
> probe/suite). Examples that *look* valid but do not compile are listed
> in [lexical-structure.md](lexical-structure.md) §5.3 and
> [specification-gaps.md](../bugs-and-gaps/specification-gaps.md).

---

## Minimal program

`kof
main() {
    println("Olá, mundo")
}
`

## Variables

`kof
var x = 10              // inferred int, mutable
val y = 20              // immutable (reassignment → SEM037)
String nome = "Mel"     // type-first
var idade: Int = 30     // annotated
String? opcional = find(key) // nullable — null via API (= null literal is SEM048)
var arr: Int[] = new Int[3]
`

## Functions

`kof
Int dobro(Int x) { return x * 2 }
dobro2(Int x): Int { return x * 2 }   // suffixed return
Bool positivo(Int x) = x > 0          // expression body
void faz() { println("x") }
main(args: List<String>) { println(args.size) }
`

## Control flow

`kof
var s = if (cond) "a" else "b"        // if-EXPRESSION (else required)
if (cond) { … } else { … }            // if-STATEMENT (else optional)
while (cond) { … }
do { … } while (cond)
for (var i = 0; i < n; i++) { … }
for (var item in lista) { … }          // for-in (var required)
switch (x) {
    case 1: println("um"); break
    default: println("outro")
}
var d = switch (x) {                   // switch-EXPRESSION
    case 1 -> "um"
    default -> "outro"
}
`

## Classes and data

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
var u = User("Mel", 26)               // no new
u.age = 27                             // mutable, direct field

record Point(Int x, Int y)             // immutable, accessors
var p = Point(10, 20)
println(p.x())                         // 10

enum Color { Red, Blue }               // constants; each is a singleton instance
println(Color.Red)                     // "Red"
// Color.Red == "Red"                  // SEM062: an enum value is not a String

interface Shape { Double area() }
class Circle(Double r) implements Shape {   // ⚠ class X(...) = record!
    Double area() { return 3.14 * r * r }
}
`

## Collections

`kof
var l = listOf(1, 2, 3)
l.add(4)
println(l.get(0))
println(l.size)                        // property (not method)
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
println(s.length)                      // property
println(s.substring(1))
println(s.contains("ell"))
var a = "ab"; var b = "a" + "b"
println(a == b)                        // content → true (NEVER .equals)
`

## Lambdas and closures

`kof
var f = (x: Int) -> x * 2
println(f(5))
var n = 0
var inc = () -> { n = n + 1 }          // mutable capture (Box)
inc(); inc()
println(n)                             // 2
list.forEach { x: Int -> println(x) }  // trailing lambda
`

## Errors

`kof
try {
    throw "not found: " + key          // exceptions are String
} catch (String e) {
    println("falhou: " + e)
} finally {
    println("cleanup")
}
`

## Concurrency

`kof
spawn trabalho()                       // fire-and-forget
val r = spawn compute()                // Handle<T>
var v = await r                        // blocks
var w = awaitTimeout(r, 1000)          // with deadline (function, not syntax)
`

## Null safety

`kof
var nome: String? = find(key)
if (nome != null) {
    println(nome.length)               // narrowing only in the then-branch
}
`

## Packages and imports

`kof
package com.dev.app
import com.dev.NodeUI
import kof.json

main() {
    var l: List<NodeUI> = listOf()     // type-arg resolved by the import
}
`

## Annotations (interop)

`kof
@JsonFormat(using = MyMapper.class)
record Dato(Int x)
`

## FFI to C — `extern` (JVM, any scalar signature)

`kof
extern "/lib/x86_64-linux-gnu/libm.so.6" cos(Double x): Double
main() { println(cos(0.0)) }            // JVM: 1.0
`

The JVM binds **any scalar signature** (R3 generalized 18/09) — arbitrary arity,
scalars in any position, `void` and `String` returns. Measured on tip:
`fmod(Double,Double):Double`→`1.5`; `ldexp(Double,Int):Double`→`12.0`;
`strncmp(String,String,Int):Int`→`-1`; `puts(String):void`; `getenv(String):String`→`mel`.
The Kof function name IS the C symbol (no alias). Non-scalar types are **partially** bound: on the **JVM** a `record` by value (argument and return), a scalar `T[]`→`ptr` (call-scoped copy-in) and an out-buffer `Buffer(U8)` (INOUT: copy-in / call / copy-back, created with `buffer.alloc`) bind since 3.8b (20–21/09, `FfiStructE2ETest` 10/10, `FfiArrayE2ETest` 5/5, `BufferFfiE2ETest` 4/4), and a function-typed parameter binds as a C callback on JVM/JS (3.4); the remaining faces (struct/array/out-buffer on Native, JS struct bridge, Native callbacks) → `FFI001` at compile time; the JS **host runner** (GraalJS/node) binds the
same scalar ABI through `KofJsFfiBridge` — JVM↔JS parity proven 18/09 (`FfiE2ETest` 16/16,
slice 3.6.F2/F3 ✅) — with non-scalars → `FFI002` there and the browser an honest **runtime**
error (R7, no host, same degrade as `kof.io`); **Native binds the same scalar ABI DIRECT on
x86-64, riscv64 and aarch64** (#431 slices 1–2, 20/09, §369): the `library()` is a link-by-use
linker input and the call is `call sym@PLT` — no `dlopen` (§61 closed). Non-scalar or a missing
`library()` → `FFI001` at the declaration line on Native (R6). Numeric arguments follow the ordinary
Kof conversion rule on every target (§370/#549 fixed 20/09): `Int`/`Double` into a `Float` slot,
`Int`/`Long`/`Float` into a `Double` slot etc. are converted; String/Bool/`Double→Int` are `SEM014`.
The lib path is resolved at **runtime** on JVM/JS (missing symbol = `kof_ffi_*` exception).
History: widening was the R3 slice (#431); the JVM face landed 18/09 (`.18`), JS host the same day,
Native x86-64 + riscv64/aarch64 on 20/09, and the D6 struct/array/out-buffer shapes on the JVM
(record by value in/out, `T[]`→`ptr` copy-in, `Buffer(U8)` INOUT) on 20–21/09.

## Tests and lifecycle

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

## Anti-forms (does NOT compile — see gaps)

`kof
fun f() {}          // ❌ `fun`/`fn`/`func` are reserved words (SG-001) — they do not exist; use `f() { }`
let x = 1           // ❌ does not exist — not even in .ks (KofScript is pure Kof)
var x = [1,2]       // ❌ array literal does not exist — use listOf(1,2)
var s = {"a","b"}   // ❌ set literal does not exist — use setOf("a","b")
if (x in lista) {}  // ❌ no in operator — use lista.contains(x)
var t = c ? a : b   // ❌ no ternary — use if (c) a else b
var n = x ?? d      // ❌ no null-coalesce
var r = 1..10       // ❌ no range
class X(val a) {}   // ❌ = record (immutable), not a mutable class
`
