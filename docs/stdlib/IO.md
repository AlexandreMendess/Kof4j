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
| `copyTo(destination)` | Bool — **JVM only** (18/09). Copies bytes + basic attributes. No-overwrite by default (returns `false`, does not touch either file, if `destination` already exists); does not create the parent directory of `destination` implicitly — the caller must ensure it exists |
| `moveTo(destination)` | Bool — **JVM only** (18/09). Filesystem-primitive rename/move, no-overwrite by default (same contract as `copyTo`). Not a safe transaction: callers that need a hash-verified move should keep doing copy → verify → delete, same as before this method existed |
| `modifiedTime()` | Long — **JVM only** (18/09). Last-modified time in epoch milliseconds; throws an exception if the file does not exist (same contract as `size()`, no sentinel) |
| `isSymlink()` | Bool — **JVM only** (18/09). `true` when the path itself is a symbolic link (the link is never followed implicitly by this check) |

Static forms: `File.exists(p)`, `File.readText(p)`,
`File.writeText(p, s)`, `File.appendText(p, s)`, `File.delete(p)`,
`File.size(p)`, `File.name(p)`.

`copyTo`/`moveTo`/`modifiedTime`/`isSymlink` have no static form yet and no
Native backend (`RuntimeIo2` has no case for them yet) — using them when
targeting Native is a known gap, not a silent no-op; it has not been
exercised as part of this change (JVM-only) and its exact failure mode on
Native has not been characterized yet.

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
`D-KOF-FILE-GO` slice 1, JVM-proven; no new syntax, no compiler change.

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

Targets: JVM and Native (`readRange` is cross-proven on x86-64/riscv64/aarch64).
JS and Script lack `readRange` — an honest gap (`D-KOF-FILE-GO`), never a
silent whole-file fallback.

## Reference

- [learn/34-file-system.md](../../learn/34-file-system.md)
- Tests: `kof-compiler/src/test/java/dev/kof/compiler/IoE2ETest.java`
- Streaming: `libs/file/FileStream.kf`, `FileLibraryE2ETest.java`
