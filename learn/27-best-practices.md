[English](27-best-practices.md) | [Português](27-best-practices.pt_BR.md)

# 27 — Best Practices

> **Kof 0.5.0-beta — `String?`, `Point(x,y)`, `map/filter/reduce`, `intention->Kof->frontend->IR->backend->runtime`**

## 0.2.0 news that affect style

- Use `String?` for nullable instead of comments about null.
- Prefer `case Point(x, y):` over a cascading `if` when destructuring records.
- Use `list.map/filter/reduce` instead of a manual `for` when the intent is to transform.
- Top-level `var`/`val` in `.ks` (KofScript → `KofScriptGlobals`); there is NO `let`/`const` (JS sugar removed 06/09) — use `var`/`val` everywhere.
- Web: one `web.app()` app per process; middleware in `app.use { }` before the routes;
  rich responses with `status(code, body)` + `headerSet(...)` instead of raw strings.
- HTTP client: `http.get/post/put/delete/patch/options` + `timeout`/`retry`/`circuit`
  (JVM+JS) — never raw sockets for HTTP.
- `spawn` for parallelism; `await` for the result. No exposed thread API.
- Format with `kof fmt -w` (31/08) — the formatter is idempotent.

## Naming

- **Classes**: PascalCase (`UserService`, `TaskRepository`)
- **Records**: PascalCase (`User`, `Point`)
- **Methods**: camelCase (`findUser`, `isActive`)
- **Fields**: camelCase (`userName`, `createdAt`)
- **Local variables**: camelCase (`indice`, `tamanho`)
- **Constants**: SCREAMING_SNAKE_CASE (`MAX_SIZE`, `DEFAULT_TIMEOUT`)
- **Packages**: lowercase (`com.exemplo.users`)

## Organization

A `.kf` file should contain one main type declaration.

```
src/main/kof/
├── com/exemplo/
│   ├── model/
│   │   ├── User.kf
│   │   └── Task.kf
│   ├── service/
│   │   ├── UserService.kf
│   │   └── TaskService.kf
│   ├── repository/
│   │   ├── UserRepository.kf
│   │   └── TaskRepository.kf
│   └── controller/
│       ├── UserController.kf
│       └── TaskController.kf
```

## Composition vs Inheritance

Prefer composition:

```kf
// GOOD
class Motorista(Carro carro) {
    void dirigir() {
        carro.mover();
    }
}

// AVOID (when it does not make sense)
class Motorista extends Carro {
    // ...
}
```

Use inheritance only when the relationship is "is a type of":
- `Cachorro` is an `Animal`
- `Exception` is an `Exception`
- `AdminController` is a `Controller`

## Error Handling

```kf
// GOOD: explicit handling
User findUser(UUID id) {
    return repository.findById(id)
        .orElseThrow(() -> new UserNotFound(id.toString()));
}

// AVOID: swallowed exceptions
try {
    riskyOperation();
} catch (Exception e) {
    // silently ignored
}
```

## Immutability

Prefer `val` over `var`:

```kf
val nome = "Mel";        // good
var nome = "Mel";        // ok if you need to reassign
```

Prefer records over mutable classes for data:

```kf
record User(String name, String email)  // immutable
```

## Next step

[Language Design →](28-language-design.md)
