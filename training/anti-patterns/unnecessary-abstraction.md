[English](unnecessary-abstraction.md) | [Português](unnecessary-abstraction.pt_BR.md)

# Anti-pattern — Unnecessary Abstraction

**Updated:**  0.5.0-beta (Sep 2026) (02 Sep 2026)

## Name

Abstractions created without a real problem.

## Problem

"Manager", "Helper", "Context", "Handler", "Wrapper", "Factory" classes that
only forward calls. Each layer adds indirection without semantics.

## Bad example

```kof
class UserManager {
    UserRepository repo
    constructor() {
        repo = new UserRepository()
    }
    find(Int id): User {
        return repo.find(id)
    }
}
class UserRepository {
    find(Int id): User {
        // real logic
    }
}
```

## Why it is bad

The consumer must know two classes to do what one function does.
The indirection solves no problem (transaction? cache? swappability?).

## Preferred approach

```kof
User findUser(Int id) {
    // real logic
}
```

## Rule

Add a layer only when it solves a concrete problem:
- tested swappability (interface + multiple implementations);
- cross-cutting transaction/cleanup;
- real shared state.

If the layer only forwards, remove it.

## Exceptions

- Interop with legacy code that requires the structure.
