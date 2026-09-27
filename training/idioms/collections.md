[English](collections.md) | [Português](collections.pt_BR.md)

# Idioms — Collections

**Status:** available · **Introduced:** 0.0.4-alpha · **Updated:**  0.5.0-beta (Sep 2026) (02 Sep 2026)

## What it is

`List<T>` is the language's ordered collection. Creation: `listOf(...)` or `new List<T>()`.
Available on JVM (ArrayList), Native (its own implementation with free-list GC) and JS (Array) with the same API.
`Map<K,V>` and `Set<T>` have existed since 0.1.0 on the 3 targets (JVM HashMap/HashSet, Native own asm, JS Map/Set).

## Real API (verified in the compiler — 0.5.0-beta)

```kof
var l = listOf(1, 2, 3, 4)
l.add(5)
var x = l.get(0)        // native bounds check via kof_list_get — no manual workaround
l.set(0, 9)
l.size                  // property, not a method
l.contains(3)
l.isEmpty()
var r = l.remove(1)       // remove by INDEX (Int), returns the element
// NEVER l.remove("x") (Java's by-value): SEM055 (bug 122) — to find by
// value use contains(x); to find the POSITION, indexOf(x) (0.4.0, #382).
l.clear()
var vazio = listOf<Int>()

// Higher-order (3 targets)
var dobrados = l.map((x: Int) -> x * 2)
var pares = l.filter((x: Int) -> x % 2 == 0)
var soma = l.reduce((a: Int, b: Int) -> a + b, 0)   // order: (lambda, init)
// `reduce(0, (a, b) -> ...)` (init, lambda) is also accepted

// Map / Set
var m = mapOf("a", 1)
m.put("b", 2)
var v = m.get("a")
var n = m.getOrDefault("b", 0)   // default when key absent (0.4.0, 4 targets)
var s = setOf(1, 2, 3)
s.add(4)
s.contains(2)
// Kof collections are HOMOGENEOUS: after the type PINS, add/put/set with a type ≠
// is rejected at compile-time (SEM056, bug 126 — it was not only Native that broke:
// on the JVM the heterogeneous add already gave a VerifyError). Numeric widening
// (Int in List<Long>) and the FIRST add (which pins a listOf()) pass. Querying by a
// type ≠ (m.get(5) on a Map<String,Int>, s.contains("x") on a Set<Int>) is a SAFE
// MISS (null/false), never an error — only WRITING is checked.

// As a class field, constructor param and method return (3 targets — 01/09)
class Bag(Set<Int> tags) {
    Set<Int> all() {
        return tags
    }
}
var b = Bag(setOf(1, 2, 3))
println(b.all().size())
```

Fix 01/09: `Set<T>`/`Map<K,V>` as a class field/return on the JVM — the mapper mapped only `List`→`ArrayList` (so `Set`/`Map` became `Lkof/Set;` → `NoClassDefFoundError`); now `HashSet`/`HashMap`. Parser: a class method with a generic return (`Set<Int> all(`) now parses (before it fell into the field branch). `KofMapSetTest.setMapAsFieldAndReturn`.

## Search, cut and order (0.4.0 — #382/#386, 4 targets)

```kof
val l: List<Int> = listOf(3, 1, 2, 1)
var i = l.indexOf(1)          // first occurrence, -1 when absent
var j = l.lastIndexOf(1)      // last occurrence, -1 when absent
val mid = l.subList(1, 3)     // [begin, end) — copy; begin==end yields empty
val all = l.subList(0, l.size)
var grew = mid.addAll(l)      // true when the list changed (false: empty source)
val ordered: List<Int> = listOf(5, 4, 3)
ordered.sort()                // NATURAL order, in-place (no Comparator in Kof)

val m: Map<String, Int> = mapOf("a", 1)
var has = m.containsValue(1)             // scan by VALUE (containsKey is by key)
var prev = m.putIfAbsent("a", 9)         // V? — returns the previous and does NOT
prev = m.putIfAbsent("z", 9)             // overwrite; null when the key is new
```

- `sort()` accepts naturally ordered elements (Int/Long/Double/String…);
  a record or other without order → `SEM097` (shared compile-time gate).
  `Float` on Native → `NAT001` with an honest diagnostic (§349) — never a
  silent wrong order.
- `indexOf`/`lastIndexOf` search by value with the pinned type; searching a
  type ≠ is a safe MISS (`-1`), same rule as `contains` (SEM056 only checks
  the WRITE).
- `subList` out-of-range dies with the bounds check (measured:
  `IndexOutOfBoundsException: toIndex = N` on the JVM; `kof_bounds_error`
  on Native) — same family as `get(i)`.
- `putIfAbsent` returns `V?` (D-NULL-INTENT): narrow with `if (prev != null)`.
- The Java idiom `Collections.sort(l, comparator)` does not exist in Kof:
  `sort()` is natural order, period. To search a position, `indexOf(x)`
  (not a manual `get(i)` loop with `||`).

## `listOf` with related subtypes infers the common ancestor (0.5.0-beta, §285)

```kof
interface Animal { String sound() }
class Dog implements Animal { String sound() { return "woof" } }
class Cat implements Animal { String sound() { return "meow" } }
var animals = listOf(new Dog(), new Cat())   // inferred List<Animal>, not List<Dog>
animals.get(1).sound()                       // "meow" — no ClassCastException
```

Elements that SHARE a supertype (class or interface) are homogeneous at the
ancestor level: the inference widens to the common supertype. Unrelated
elements (`listOf(new Dog(), 42)`) keep the SEM056 homogeneity rejection.
Before 0.4.0 the element type came from the FIRST argument only — the fix
walks superclasses AND interfaces (family of §156). Measured 18/09 on the
tip: `woof`/`meow`.

## `Map.get` returns `V?` for reference values (02/09)

`m.get(chave)` returns `V?` when the value is a reference type
(`Map<String, String>`, `Map<String, User>`): absence = `null`, use
`if (v != null)` to narrow. For **primitive** values (`Map<String, Int>`)
the type is now `V?` as well (since the D-NULL-INTENT N1 merge, #438 `250f6207`,
18/09: `Int z = m.get("a")` fails with a type-mismatch on `NullableType[int]` —
absence is representable). **Caution while §294 is open:** on the JVM a
present-key `if (v != null)` check over a primitive-valued map still dies at
runtime (`NoSuchMethodError Object.valueOf(boxed)`); until §294 closes, prefer
`m.getOrDefault(key, fallback)` (landed `62bd455e`, measured working) or
`contains`/`containsKey` checks for primitive-valued maps. Reference values are
unaffected.

Fix 27/08: `listOf(...).get(n)` and `size` in large projects with `import a.b.C` now resolve correctly (CompilerDriver file-specific imports). A manual index workaround is not necessary.

## Bare `List`/`Set`/`Map` in a declared position (0.4.0 — §373/#443)

A collection name WITHOUT type arguments in a field, parameter or return type is the
**builtin collection** — exactly what the local form has always meant (#139/#150/#214):

```kof
class Box {
    List items                      // bare = builtin List (before §373: crashed at class load)
    public constructor() { items = listOf(1, 2) }
}
count(xs: List): Int { return xs.size }     // bare in a parameter
```

Prefer the explicit element type (`List<Int> items`) when it is known — the compiler
checks more with it. A user class with the same name (`class List { ... }` in your own
package) keeps winning over the builtin (the §243 shadow guard, proven by controls in
`BareCollectionFieldE2ETest` 8/8, the same print on the 4 targets).

## When to use

Any problem that requires a sequence of elements:
collections, records, simple queues, groupings, accumulators.
`Map`/`Set` for associations and sets. `map`/`filter`/`reduce` for transformation without a manual loop.

## When not to use

- Do not reimplement `map`/`filter`/`reduce` with a loop when the higher-order expresses the intent.
- Do not use `List<record>` with a linear search when `Map<K,V>` solves it (when there is a key).

## BAD — manual structure

```kof
class Node {
    Node next
    Int value
}
class Registry {
    Node root
    Int count
}
```

## GOOD — the language's collection

```kof
class Registry {
    List<LanguageEntry> entries

    constructor() {
        entries = listOf(
            LanguageEntry("Kof", "kf", "kof"),
            LanguageEntry("JSON", "json", "json")
        )
    }
}
```

## GOOD — declarative transformation (0.5.0-beta)

```kof
var nomes = users.map((u: User) -> u.name)
var adultos = users.filter((u: User) -> u.age >= 18)
var total = nums.reduce((a: Int, b: Int) -> a + b, 0)
```

## GOOD — Box<T> with primitives (0.1.0 fix)

```kof
class Box<T>(T value) {
    get(): T { return value }
}
var b = Box<Int>(42)
println(b.get())   // 42 — substituteTypeVariable fixes T → Int on Native
```

## WHY

`Node`/`next`/`count` is accidental implementation. The domain is "a sequence of entries".
Kof has the abstraction. Represent the domain, not the implementation.
Higher-orders and `Box<T>` eliminate manual loops and wrappers.

## Iteration

```kof
var items = listOf("a", "b", "c")
for (var item in items) {
    println(item)
}
```

`for-in` works over `List<T>` and arrays (`new Int[5]`).

## Element types

```kof
var ids = listOf<Int>()          // empty list of Int
var nomes = listOf("Ana", "Mel")
var users = listOf<User>()       // list of objects (erasure)
var boxed: Box<Int> = Box(5)
```

## Related anti-patterns

- Manual linked list → `training/anti-patterns/manual-data-structures.md`
- Array as a substitute for a dynamic collection → use `List<T>`
- Manual loop for map/filter → use higher-order
