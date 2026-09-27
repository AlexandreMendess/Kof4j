[English](19-packages-and-modules.md) | [Português](19-packages-and-modules.pt_BR.md)

# 19 — Packages and Modules

> **Status: implemented — imports `a.b.C` fixed for large projects (0.5.0-beta)**
>
> `package` + `import` work end-to-end. On 27/08 the `CompilerDriver` started treating `import a.b.C` as a file import **plus** a directory import `a.b`, fixing the loss of imports in projects with `a/b/C.kf`.

## Package

Each Kof file can declare a package:

```kf
package com.exemplo.users

record User(String name, String email)
```

This generates the class in the package `com.exemplo.users` (JVM: internal name `com/exemplo/users/User`).

## Imports

```kf
import a.b.C          // 0.2.0: resolves both the C.kf file and the a/b/ directory
import a.b.*          // entire directory
import kof.http       // stdlib
```

> **`java.util.*` is interop, not the idiomatic way.** For Kof collections, `List<T>`/
> `Map<K,V>`/`Set<T>` + `listOf`/`mapOf`/`setOf` come from the stdlib **without an import**.
> `import java.util.ArrayList` etc. is only necessary when calling Java APIs
> directly (see ch. 21).

`kof build` now compiles `largeproj` correctly:

```text
src/
├── Main.kf          // import a.b.C
└── a/b/C.kf         // package a.b; class C { ... }
→ Main.class + a/b/C.class  (decls=2, both emitted)
```

Before 27/08, `import a.b.C` in large projects could lose the second declaration — the driver only registered the file, not the directory. Now (`CompilerDriver.java:243`) it registers `file import` + `dir import`, and the chain `intention->Kof->frontend->IR->backend->runtime` preserves all `CompilationUnit`s up to the backend.

### Runnable example (multi-file)

```kf
// a/b/C.kf
package a.b
class C {
    String msg() { return "de C" }
}
```

```kf
// Main.kf
import a.b.C

main() {
    var c = C()
    println(c.msg())  // de C
}
```

```bash
kof build src --target=jvm     # generates Main.class + a/b/C.class
kof build src --target=js      # Default.mjs with import C
```

> ⚠️ **Native (x86_64) breaks with a class imported from another package** — the
> constructor mangling uses the simple name (`C_init_0`) instead of the internal
> name (`a_b_C_init_0`) → `undefined reference`. Bug 22 in
> `docs/bugs-and-gaps/known-bugs.md`. Use `--target=jvm`/`js` meanwhile, or fix the
> `NativeBackend.java:369`.

> ⚠️ **Equal names in different packages are rejected** (PKG005) — `pkgA.Data`
> + `pkgB.Data` do not compile together. Bug 21 in `docs/bugs-and-gaps/known-bugs.md`.

### Static import (planned)

```kf
import static java.lang.Math.PI
import static java.lang.Math.sqrt
```

### Module import (planned)

```kf
import module java.base
```

## Visibility

| Modifier | Same package | Subclasses | Anywhere |
|-------------|:---:|:---:|:---:|
| `public` | ✅ | ✅ | ✅ |
| `protected` | ✅ | ✅ | ❌ |
| (default) | ✅ | ❌ | ❌ |
| `private` | ❌ | ❌ | ❌ |

## JPMS modules (planned)

```kf
module com.exemplo.app {
    requires java.sql
    requires spring.boot
    exports com.exemplo.api
}
```

## Next step

[Annotations →](20-annotations.md)
