[English](24-build-tools.md) | [Português](24-build-tools.pt_BR.md)

# 24 — Build Tools

> **Status: partial — Maven/Gradle via `kof build` + `kof test` (0.5.0-beta)**
>
> `kof build`/`kof test` are the native build tools (); Maven/Gradle integration as an external plugin is still a planned vision, but the coexistence of `src/main/java` + `src/main/kof` already works to generate interoperable `.class` files.

## Maven

### Project structure

```
meu-projeto/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/          ← Java code
│   │   └── kof/           ← Kof code
│   └── test/
│       ├── java/
│       └── kof/
```

### pom.xml

```xml
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.exemplo</groupId>
    <artifactId>meu-app</artifactId>
    <version>1.0-SNAPSHOT</version>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
            <version>3.2.0</version>
        </dependency>
    </dependencies>
</project>
```

### Compiling

There is no Kof Maven/Gradle plugin yet (planned vision). The native path is:

```bash
kof build src/main/kof --target jvm --output out/classes
```

The output lands on the classpath alongside the Java `.class` files — Maven/Gradle keep handling Java, and `kof build` handles Kof. `kof test`
runs the `test "nome" { assert(...) }` suite on the jvm/native/js targets.

## Gradle

Same strategy as in Maven: the Java build continues in Gradle; the Kof code
compiles with `kof build` to the same classpath. A Gradle plugin
(`dev.kof.kof`) is a planned vision, it does not exist today.

## Coexistence with Java

Kof and Java can coexist in the same project:

```
src/
├── main/
│   ├── java/
│   │   └── com/exemplo/
│   │       └── legacy/
│   │           └── OldService.java
│   └── kof/
│       └── com/exemplo/
│           └── new/
│               └── NewService.kf
```

The Kof compiler generates `.class` files that Java can call normally.

## Next step

[Spring →](25-spring.md)
