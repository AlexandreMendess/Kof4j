[English](README.md) | [Português](README.pt_BR.md)

# Kof Training — Corpus for LLMs

**Version:** 0.5.0-beta (Sep 2026) — 3225 tests · targets jvm/native/native.risc/native.arm/js/kofc + KofScript

This directory contains structured knowledge about the Kof language, optimized for language models.

## Purpose

The corpus allows LLMs to:
- Understand Kof's syntax and semantics
- Generate valid and idiomatic code
- Fix Kof code
- Explain Kof code
- Migrate Java/Spring to Kof
- Generate APIs, tests and documentation

The goal is not just to teach the grammar — it is to teach **how to think in Kof**:
represent the domain with the language's abstractions, not translate Java.

## Structure

```
training/
├── README.md              # This file
├── language/              # Language concepts (real state)
│   ├── overview.md
│   ├── syntax.md
│   ├── types.md
│   ├── classes.md
│   ├── exceptions.md
│   ├── arrays.md
│   ├── strings.md
│   ├── io.md
│   ├── ui.md
│   └── security.md
├── reference/             # Technical reference
│   ├── compiler.md
│   └── targets.md
├── idioms/                # IDIOMATIC FORM of each problem (BAD/GOOD/WHY)
│   ├── automation.md
│   ├── collections.md
│   ├── classes.md
│   ├── records.md
│   ├── functions.md
│   ├── control-flow.md
│   ├── strings.md
│   ├── errors.md
│   ├── web.md
│   ├── architecture.md
│   ├── composition.md
│   ├── concurrency.md
│   ├── stdlib.md
│   ├── database.md
│   ├── ui.md
│   ├── security.md
│   └── interop.md
├── anti-patterns/         # Catalog of what NOT to do
│   ├── common-mistakes.md
│   ├── java-like-code.md
│   ├── unnecessary-abstraction.md
│   ├── manual-data-structures.md
│   ├── sentinel-values.md
│   ├── duplicate-state.md
│   ├── fake-idioms.md
│   ├── premature-optimization.md
│   ├── runtime-workarounds.md
│   ├── chained-or-membership.md
│   ├── weak-green-proof.md
│   ├── stale-ecj-class-trap.md
│   ├── constant-folded-runtime-asm.md
│   ├── asm-comment-escape.md
│   ├── char-in-string-methods.md
│   └── void-call-merge-crash.md
├── datasets/              # Structured material for automated ingestion
│   └── kof-idioms.json
├── patterns/              # Idiomatic patterns
│   └── common-patterns.md
├── examples/              # Runnable examples
│   ├── hello.kf
│   ├── classes.kf
│   ├── inheritance.kf
│   ├── web.kf
│   └── security.kf
├── distribution/          # Installation and distribution
│   └── install.md
├── tooling/               # CLI, LSP and editor support
│   └── cli.md
├── releases/              # Versioning and release pipeline
│   └── versioning.md
└── migration/             # Migration
    └── java-to-kof.md
```

## Rules

1. All content must reflect the REAL code
2. Do not document non-existent features (see `anti-patterns/fake-idioms.md`)
3. Examples must be verifiable (compile preferably)
4. Workarounds are marked `WORKAROUND` — never taught as an idiom
5. Version-sensitive features record `Introduced`/`Status`
6. Code that compiles ≠ idiomatic Kof code — the corpus teaches the difference
7. If there is a conflict: implementation → tests → documentation → training
