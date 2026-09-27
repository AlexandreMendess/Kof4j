[English](03-language-basics.md) | [Português](03-language-basics.pt_BR.md)

# 03 — Language Basics

> **Kof 0.5.0-beta — `intention->Kof->frontend->IR->backend->runtime`**

## What you will learn

In this chapter you will understand how Kof code is structured: statements, expressions, literals, comments and the basic structure of a program.

## Statements

A statement is an instruction. In Kof, the semicolon is **optional** in most contexts:

```kf
record User(String name)
```

```kf
var nome = "Mel"
println(nome)
```

The compiler accepts both with and without a semicolon. Choose a style and be consistent.

## Comments

Line comments:

```kf
// this is a comment
```

Block comments:

```kf
/* this is a
   multi-line
   comment */
```

The compiler ignores comments completely.

## Literals (includes `String?` for nullable — see ch. 13)

### Strings

```kf
"olá mundo"
"com Escape\n"
"com \"aspas\""
```

### Numbers

```kf
42          // Int
42L         // Long
3.14        // Double
3.14f       // Float
```

### Booleans

```kf
true
false
```

### Null

```kf
null
```

## Primitive types

Kof uses the same primitive types as the JVM:

| Type | Description | Size |
|------|-----------|---------|
| `Bool` | boolean | 1 bit |
| `Byte` | integer byte | 8 bits |
| `Short` | short integer | 16 bits |
| `Int` | integer | 32 bits |
| `Long` | long integer | 64 bits |
| `Float` | floating point | 32 bits |
| `Double` | double floating point | 64 bits |
| `Char` | character | 16 bits |
| `Void` | no value | — |

## Strings

Strings are objects. They are immutable (as in Java):

```kf
String nome = "Kof"
```

Concatenation works with `+`:

```kf
String saudacao = "Olá, " + nome
```

## Operators

### Arithmetic

```kf
a + b     // addition
a - b     // subtraction
a * b     // multiplication
a / b     // division
a % b     // modulo
```

### Comparison

```kf
a == b    // equal
a != b    // not equal
a < b     // less than
a <= b    // less than or equal
a > b     // greater than
a >= b    // greater than or equal
```

### Logical

```kf
a && b    // logical and
a || b    // logical or
!a        // negation
```

### Assignment

```kf
x = 5
x += 3    // x = x + 3
x -= 2    // x = x - 2
x *= 4    // x = x * 4
x /= 2    // x = x / 2
x %= 3    // x = x % 3
```

## Structure of a Kof file

A `.kf` file contains:

1. Package declaration (optional)
2. Imports (optional)
3. Type declarations (classes, records, interfaces)
4. Functions

Example:

```kf
package com.example

record Point(Int x, Int y)

main() {
    var p = Point(3, 7)
    print(p)
}
```

## Current status

✅ Lexer recognizes all literals and operators
✅ Optional semicolon
✅ Package and imports work
✅ Records work
✅ Functions with `main()` work

## Next step

[Variables and Types →](04-variables-and-types.md)
