[English](20-annotations.md) | [Português](20-annotations.pt_BR.md)

# 20 — Annotations

> **Status: implemented (JVM/KofJS) — 0.5.0-beta**
>
> The parser accepts `@Name` and `@Name(valor | key = valor, ...)` on classes,
> records, interfaces, entities, fields, methods, constructors, functions,
> record components and parameters. The JVM backend emits the annotations
> into the bytecode; Native ignores metadata.

## What annotations are

Annotations are metadata that can be added to classes, methods, fields and parameters. In Kof they exist for **interoperation** — when an external target (Android, JVM framework) requires metadata in the bytecode. Idiomatic Kof code still prefers explicit intention (`app.get(...)`, `entity { ... }`) over annotations+container.

## Annotations in Kof

```kf
@Entity
class User {
    @Id
    UUID id

    @Column("user_name")
    String name
}
```

## Annotations with parameters

```kf
@GetMapping("/users/{id}")
User findUser(@PathVariable UUID id) {
    // ...
}
```

Accepted forms:

| Form | Example |
|-------|---------|
| simple | `@Override` |
| single value (goes to `value`) | `@Column("user_name")` |
| `key = value` pairs | `@JsonFormat(pattern = "yyyy")` |
| array of literals | `@Roles({"admin", "dev"})` |
| package-qualified | `@androidx.annotation.NonNull` |

Values must be **compile-time constants**: literals `String`, `Int`, `Long`, `Float`, `Double`, `Bool`, `Char`, `null`, or arrays `{...}` of those literals. Non-constant identifiers become the diagnostic `ANNOT001` — never a silently wrong value.

## What the compiler generates in the bytecode

- `RuntimeVisibleAnnotations` / `RuntimeInvisibleAnnotations`
- Annotations on parameters (`RuntimeVisible/InvisibleParameterAnnotations`)
- Annotations on fields

Retention is decided by table: `@Override` and `@SuppressWarnings` and the metadata
packages (`androidx.annotation.*`, `javax.annotation.*`,
`org.jetbrains.annotations.*`, `edu.umd.cs.findbugs.annotations.*`) are
emitted as **invisible** (`RuntimeInvisible`); everything else — including
`@Deprecated`, `@FunctionalInterface` and `@SafeVarargs` — goes as **visible**
(`RuntimeVisible`). It is a conservative choice for frameworks that read the
annotations at runtime (JUnit, Android).

## Name resolution

Qualified names (`androidx.annotation.NonNull`) go straight to the bytecode. Simple names use the file's imports (`import androidx.annotation.NonNull` makes `@NonNull` resolvable) and the built-ins from `java.lang`.

## Interoperability with Java frameworks

Annotations work normally with:
- Spring (`@Service`, `@Autowired`, `@RestController`)
- JPA (`@Entity`, `@Table`, `@Column`)
- Jackson (`@JsonProperty`, `@JsonIgnore`)
- JUnit (`@Test`, `@BeforeEach`)
- Android (`@Override`, `@NonNull`, lifecycle via superclasses)

Spring sees `@Service` normally because the annotation is in the bytecode.

## Known limitations

- Enum or `Class<?>` values are not supported yet (`ANNOT001`).
- Native ignores annotations (metadata has no executable semantics there).

## Next step

[Java Interoperability →](21-java-interoperability.md)
