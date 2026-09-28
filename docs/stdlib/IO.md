[English](IO.md) | [Português](IO.pt_BR.md)

# kof.io — Filesystem API

`kof.io` is the official Kof filesystem API: files, directories and
paths with a single semantics on the JVM and Native targets.

## Types

`File`, `Path` and `Directory` represent a path (the path string).
All `kof.io` operations work on the three types — the type only
guides the intent.

## Path

| Operation | Example | Result (Linux/macOS) |
|----------|---------|--------------------------|
| `resolve` | `Path("data").resolve("users.txt")` | `data/users.txt` |
| `parent` | `Path("data/users.txt").parent()` | `data` |
| `fileName` | `Path("data/users.txt").fileName()` | `users.txt` |
| `extension` | `Path("data/users.txt").extension()` | `txt` |
| `normalize` | `Path("a/./b/../c").normalize()` | `a/c` |
| `isAbsolute` | `Path("/x").isAbsolute()` | `true` |
| `toAbsolute` | `Path("x").toAbsolute()` | absolute path |

On Windows the separator is `\`; Kof code never concatenates separators.

## File

| Operation | Description |
|----------|-----------|
| `exists()` | Bool |
| `isFile()` / `isDirectory()` | Bool |
| `readText()` | `String?` — `null` on failure (JVM and Native) |
| `writeText(s)` / `appendText(s)` | Bool, UTF-8 |
| `readBytes()` | `Int[]` (0-255), `null` on failure |
| `writeBytes(b)` / `appendBytes(b)` | Bool |
| `size()` | Long; throws an exception if the file does not exist (02/09 — no `-1` sentinel) |
| `delete()` | Bool (file or empty directory) |
| `name()` / `path()` | String |
| `copyTo(destination)` | Bool — JVM + Native (x86-64/riscv64/aarch64, parity row 13). Copies bytes + basic attributes. No-overwrite by default (returns `false`, does not touch either file, if `destination` already exists); does not create the parent directory of `destination` implicitly — the caller must ensure it exists |
| `moveTo(destination)` | Bool — JVM + Native. Filesystem-primitive rename/move, no-overwrite by default (same contract as `copyTo`). Not a safe transaction: callers that need a hash-verified move should keep doing copy → verify → delete, same as before this method existed |
| `modifiedTime()` | Long — JVM + Native. Last-modified time in epoch milliseconds; throws an exception if the file does not exist (same contract as `size()`, no sentinel) |
| `isSymlink()` | Bool — JVM + Native. `true` when the path itself is a symbolic link (the link is never followed implicitly by this check) |

There is **no static form** for any `kof.io` face: `File.exists(p)`,
`File.readText(p)`, `File.readRange(p, o, n)` are rejected by the typer with
`SEM011 Undefined variable or type: 'File'` on **every** target (measured
28/09) — use the instance form `File(p).exists()`, `File(p).readRange(0, 4)`.
(The compiler's `KofIo.staticMethod` table is unreachable because the typer
never resolves the type as a static receiver; `copyTo`/`moveTo`/`modifiedTime`/
`isSymlink` are instance-only.)

## Directory

| Operation | Description |
|----------|-----------|
| `exists()` | Bool |
| `create()` | creates; fails if it already exists |
| `createDirectories()` | creates recursively |
| `list()` | `List<String>` of names, sorted |
| `delete()` | removes an empty directory |

```kof
var dir = Directory("data")
dir.createDirectories()
for (var entry in dir.list()) {
    println(entry.name)
}
```

`entry.name` and `entry.path` return the entry itself.

## Complete example

```kof
var path = Path("data/users.txt")
path.parent().createDirectories()
path.writeText("Mel\nKof\n")
var text = path.readText()
println(text)
println(path.size())
```

## Errors and encoding

- Text: UTF-8 always.
- Absence as a value (02/09): `readText()`/`readFile()` return `String?`
  (`null` for a nonexistent file) on JVM and Native; `size()` throws a
  recoverable exception (`catch (String e)`) — the `-1` sentinel was removed.
- Booleans: `true`/`false`. `size()` throws an exception when the file does not exist (without `-1`).

## Streaming (`libs/file`)

`kof.io` also exposes `readRange(offset, len)` (incremental read). The
official pure-Kof library `libs/file` builds streaming on top of it —
`D-KOF-FILE-GO`; no new syntax, no compiler change. Slice 2 measured the
library on every target (`FileLibraryE2ETest` 7/7).

```kof
import file.FileStream

main() {
    var stream = FileStream("large.log", 4096)   // chunk size
    var chunk = stream.readChunk()
    while (chunk != null) {
        // process a fixed-size byte chunk; memory stays bounded
        chunk = stream.readChunk()
    }
    println(stream.position())
}
```

| Operation | Description |
|----------|-------------|
| `FileStream(path[, chunkSize])` | chunked byte reader (default 8192) |
| `readChunk()` | `Int[]?` — next chunk, `null` at end of file |
| `done()` | `Bool` |
| `position()` | `Long` bytes consumed |
| `copyStream(source, destination, chunkSize)` | `Long` bytes copied, constant memory |

Targets: JVM, Native (x86-64/riscv64/aarch64) and Script run the real
`readRange` (all measured against the same golden). JS has no host binding
for `readRange` (nor `copyTo`/`moveTo`/`modifiedTime`/`isSymlink`): calling
it refuses at compile time with `IOJS001` (`D-KOF-FILE-GO`), never a silent
whole-file fallback nor a runtime `SyntaxError`.

## Reference

- [learn/34-file-system.md](../../learn/34-file-system.md)
- Tests: `kof-compiler/src/test/java/dev/kof/compiler/IoE2ETest.java`
- Streaming: `libs/file/FileStream.kf`, `FileLibraryE2ETest.java`
