#!/usr/bin/env python3
"""taxonomy.py — the three-axis enums (`docs/development/technical-debt/
TAXONOMY.md`). This module is the SINGLE source of truth the code
enforces; `--selftest` cross-checks the code blocks in `TAXONOMY.md`
against these sets so the doc and the code cannot silently drift apart
(exactly the `DOC_CODE_DRIFT` mechanism this tool exists to catch —
applied here to itself first).
"""
import os
import re
import sys

for _stream in (sys.stdout, sys.stderr):
    if hasattr(_stream, "reconfigure"):
        _stream.reconfigure(encoding="utf-8", errors="replace")

DEBT_TYPES = {
    "REQUIREMENTS_CONTRACT", "ARCHITECTURE", "DESIGN", "CODE", "TEST",
    "BUILD", "DOCUMENTATION", "INFRASTRUCTURE", "VERSIONING_COMPATIBILITY",
}

DOMAINS = {
    "PARSER_GRAMMAR", "SEMANTICS", "TYPE_SYSTEM", "NULLABILITY",
    "GENERICS_ERASURE", "JVM", "JS", "SCRIPT", "NATIVE_X86", "NATIVE_RISCV",
    "NATIVE_AARCH64", "FFI_ABI", "RUNTIME_MEMORY_GC", "CONCURRENCY",
    "STDLIB", "DIAGNOSTICS", "CLI_TOOLING", "BUILD_RELEASE",
    "CI_GOVERNANCE", "SECURITY", "DOCS_TRAINING", "ECOSYSTEM",
}

MECHANISMS = {
    "WORKAROUND", "DUPLICATED_CONTRACT_LOGIC", "TARGET_DIVERGENCE",
    "ACCIDENTAL_CONTRACT", "PARTIAL_MIGRATION", "DISABLED_GATE",
    "OBSOLETE_ASSUMPTION", "RECURRING_REGRESSION", "COMPATIBILITY_LOCK_IN",
    "ARCHITECTURE_EROSION", "DEPENDENCY_LOCK_IN", "MISSING_TEST_ORACLE",
    "DOC_CODE_DRIFT", "MANUAL_PROCESS", "MIGRATION_GAP",
}

LOCK_IN_LEVELS = {
    "NONE", "INTERNAL", "TEST_ENCODED", "DOCUMENTED", "NORMATIVE",
    "ECOSYSTEM_OBSERVED",
}

CONFIDENCE_LEVELS = {"C0", "C1", "C2", "C3"}


def _code_block_words(markdown_text, heading_snippet):
    """Extract the identifier tokens out of the first fenced ```text code
    block that follows a heading containing `heading_snippet`."""
    idx = markdown_text.find(heading_snippet)
    if idx == -1:
        raise ValueError(f"heading {heading_snippet!r} not found in TAXONOMY.md")
    rest = markdown_text[idx:]
    m = re.search(r"```text\n(.*?)```", rest, re.S)
    if not m:
        raise ValueError(f"no ```text block after {heading_snippet!r}")
    return set(re.findall(r"[A-Z][A-Z0-9_]+", m.group(1)))


def _taxonomy_md_path():
    return os.path.join(
        os.path.dirname(__file__), "..", "..",
        "docs", "development", "technical-debt", "TAXONOMY.md",
    )


def selftest():
    ok = True

    def check(name, cond):
        nonlocal ok
        status = "ok  " if cond else "FAIL"
        print(f"  {status}— {name}")
        ok = ok and cond

    path = _taxonomy_md_path()
    if not os.path.exists(path):
        print("  FAIL — TAXONOMY.md not found at", path)
        return False
    with open(path, "r", encoding="utf-8") as f:
        text = f.read()

    doc_debt_types = _code_block_words(text, "Axis A")
    check("Axis A (debt type) code == TAXONOMY.md", doc_debt_types == DEBT_TYPES)

    doc_domains = _code_block_words(text, "Axis B")
    check("Axis B (KOF domain) code == TAXONOMY.md", doc_domains == DOMAINS)

    doc_mechanisms = _code_block_words(text, "Axis C")
    check("Axis C (mechanism) code == TAXONOMY.md", doc_mechanisms == MECHANISMS)

    doc_lockin = _code_block_words(text, "Lock-in scale")
    check("lock-in scale code == TAXONOMY.md", doc_lockin == LOCK_IN_LEVELS)

    check("no overlap between the three axes (a candidate must be able to "
          "tell which axis a token belongs to)",
          not (DEBT_TYPES & DOMAINS) and not (DOMAINS & MECHANISMS)
          and not (DEBT_TYPES & MECHANISMS))

    return ok


def main(argv):
    if "--selftest" in argv:
        ok = selftest()
        print("taxonomy --selftest:", "OK" if ok else "FAIL")
        return 0 if ok else 1
    print(__doc__, file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
