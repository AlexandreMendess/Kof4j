#!/usr/bin/env python3
"""satd.py — the SATD (self-admitted technical debt) marker detector,
Wave 1's first real detector (`docs/development/technical-debt/
DEBT_SCOUT_CONTRACT.md` §2/§16).

**Division of labor, not duplication:** `scripts/audit-stubs.sh` already
sweeps `*/src/main` and `*/src/test` Java sources for
`TODO|FIXME|XXX|HACK` (maintainer-ordered, session 9094) and prints a
human-read report. This detector deliberately EXCLUDES those same Java
source trees and covers the rest of the repo (scripts, workflows, docs,
config) instead, emitting schema-validated `C0` candidates rather than a
report. The two are complementary, not overlapping.

**Marker set is deliberately narrow.** The V2 source spec's §16 list
includes `legacy`, `workaround`, `temporary`, `deprecated`, ... — this
repo's OWN `audit-stubs.sh` documents (its own header) that such English
words collide with normal Portuguese prose in this bilingual codebase
("todo" = "every" in Portuguese; "stub honesto" is a deliberately
documented R6 refusal, not debt). Wave 1 reuses only the four
ALL-CAPS, word-bounded, case-sensitive markers that tool already proved
low-noise: `TODO FIXME HACK XXX`. Broader markers are deferred to a
later rule version — a declared smaller scope, not a stub (Q7).

**Every match is `C0` — never higher.** A marker alone is a signal, not
liability evidence (contract §2/§3). `publication.eligible` is always
`false`; nothing here is Issue-eligible or even SARIF-eligible yet
(Wave 1 has no SARIF writer).

CLI:
  satd.py [--root DIR]   -> JSON list of C0 candidates to stdout
  satd.py --selftest
"""
import json
import os
import re
import subprocess
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
import fingerprint  # noqa: E402
import schema  # noqa: E402

for _stream in (sys.stdout, sys.stderr):
    if hasattr(_stream, "reconfigure"):
        _stream.reconfigure(encoding="utf-8", errors="replace")

RULE_ID = "KOF-DEBT-SATD-001"
RULE_VERSION = "1.0.0"

MARKER_RE = re.compile(r"\b(TODO|FIXME|HACK|XXX)\b")

EXCLUDED_DIR_NAMES = {
    ".git", "target", "node_modules", "lib", "dist", "build", "out",
    ".idea", ".vscode", "__pycache__", ".debt-scout",
}
# audit-stubs.sh already owns these (Java src trees) — no overlap.
EXCLUDED_PATH_PREFIXES_ANYWHERE = ("src/main", "src/test")
# self-exclusion: this tool's own code (would find its own docstring
# literally naming the markers) and already-catalogued debt records
# (a marker mention there is documentation of a CLOSED/tracked item, not
# a fresh signal).
SELF_EXCLUDE_PREFIXES = (
    "scripts/debt-scout/",
    "scripts/audit-stubs.sh",
    "scripts/tests/audit-stubs-test.sh",
    "docs/bugs-and-gaps/",
    "training/anti-patterns/fake-idioms.md",
)
TEXT_EXTENSIONS = {
    ".py", ".sh", ".md", ".yml", ".yaml", ".json", ".kf", ".txt",
    ".properties", ".xml", ".js", ".ts",
}
MAX_FILE_BYTES = 2_000_000


def _domain_for(relpath):
    """Coarse Wave-1 domain heuristic — precise per-module mapping for
    compiler/backend source is deferred (those trees are excluded here
    anyway; see module docstring)."""
    if relpath.startswith(".github/workflows/"):
        return "CI_GOVERNANCE"
    if relpath.startswith(("docs/", "training/", "learn/")):
        return "DOCS_TRAINING"
    return "CLI_TOOLING"


def _is_excluded_dir(name):
    return name in EXCLUDED_DIR_NAMES or name.startswith(".")


def iter_source_files(root):
    """Yields repo-relative, POSIX-style paths of files this detector
    should scan."""
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if not _is_excluded_dir(d)]
        for name in filenames:
            _, ext = os.path.splitext(name)
            if ext not in TEXT_EXTENSIONS:
                continue
            abspath = os.path.join(dirpath, name)
            relpath = os.path.relpath(abspath, root).replace(os.sep, "/")
            if any(relpath.startswith(p) for p in SELF_EXCLUDE_PREFIXES):
                continue
            if any(part in EXCLUDED_PATH_PREFIXES_ANYWHERE
                   for part in (relpath,)):
                continue
            if "/src/main/" in relpath or "/src/test/" in relpath \
                    or relpath.startswith("src/main/") \
                    or relpath.startswith("src/test/"):
                continue
            try:
                if os.path.getsize(abspath) > MAX_FILE_BYTES:
                    continue
            except OSError:
                continue
            yield relpath


def find_markers_in_text(text):
    """Pure function: file text -> list of (1-based lineno, marker,
    stripped line text). No IO."""
    hits = []
    for lineno, line in enumerate(text.splitlines(), start=1):
        m = MARKER_RE.search(line)
        if m:
            hits.append((lineno, m.group(1), line.strip()))
    return hits


def _analyzed_sha(root):
    try:
        out = subprocess.run(
            ["git", "-C", root, "rev-parse", "HEAD"],
            capture_output=True, text=True, timeout=10,
        )
        if out.returncode == 0:
            return out.stdout.strip()
    except (OSError, subprocess.SubprocessError):
        pass
    return "UNKNOWN"


def _candidate(relpath, lineno, marker, line_text, analyzed_sha):
    symbol = f"{relpath}#{marker}"
    claim = f"{marker} marker: {line_text}" if line_text else f"{marker} marker (blank surrounding line)"
    finding_fp = fingerprint.finding_fingerprint(RULE_ID, symbol, claim)
    candidate_id = finding_fp.split(":", 1)[1][:16]
    domain = _domain_for(relpath)
    return {
        "schema": 2,
        "candidate_id": candidate_id,
        "rule_id": RULE_ID,
        "rule_version": RULE_VERSION,
        "finding_fingerprint": finding_fp,
        "claim": claim,
        "confidence": "C0",
        "taxonomy": {
            "debt_type": "CODE",
            "primary_domain": domain,
            "domains": [domain],
            "mechanism": "WORKAROUND",
        },
        "locations": [{"path": relpath, "line": lineno}],
        "publication": {
            "public_safe": True,
            "eligible": False,
            "reason": "C0 — a bare marker is a signal, never confirmed debt (contract §2/§16)",
        },
        "lineage": {
            "analyzed_sha": analyzed_sha,
            "model_used": False,
            "rule_id": RULE_ID,
            "rule_version": RULE_VERSION,
        },
        "evidence": [{"type": "CODE", "ref": f"{relpath}:{lineno}"}],
    }


def scan(root="."):
    analyzed_sha = _analyzed_sha(root)
    candidates = []
    for relpath in sorted(iter_source_files(root)):
        abspath = os.path.join(root, relpath)
        try:
            with open(abspath, "r", encoding="utf-8", errors="replace") as f:
                text = f.read()
        except OSError:
            continue
        for lineno, marker, line_text in find_markers_in_text(text):
            candidates.append(_candidate(relpath, lineno, marker, line_text, analyzed_sha))
    return candidates


# --------------------------------------------------------------------------
# selftest
# --------------------------------------------------------------------------

def selftest():
    ok = True

    def check(name, cond):
        nonlocal ok
        status = "ok  " if cond else "FAIL"
        print(f"  {status}— {name}")
        ok = ok and cond

    hits = find_markers_in_text("normal line\n// TODO: fix this later\nanother line\n")
    check("finds a TODO on its own line, reports the right line number",
          hits == [(2, "TODO", "// TODO: fix this later")])

    hits2 = find_markers_in_text("todo esta funcao (Portuguese 'every function')\n")
    check("lowercase Portuguese 'todo' is NOT matched (case-sensitive, "
          "word-bounded — this repo's own audit-stubs.sh precedent)",
          hits2 == [])

    hits3 = find_markers_in_text("// FIXME(auto-generated) and HACK: two on one line\n")
    check("only the first marker per line is reported (simple, bounded output)",
          len(hits3) == 1 and hits3[0][1] == "FIXME")

    c = _candidate("scripts/example.sh", 5, "TODO", "# TODO: something",
                    "deadbeef" * 5)
    errors = schema.validate_candidate(c)
    check("an emitted candidate is schema-valid", errors == [], )
    if errors:
        print("    ", errors)
    check("an emitted candidate is always C0", c["confidence"] == "C0")
    check("an emitted candidate is never publish-eligible",
          c["publication"]["eligible"] is False)

    c2 = _candidate("scripts/example.sh", 99, "TODO", "# TODO: something",
                     "deadbeef" * 5)
    check("finding fingerprint is stable when only the line number moves "
          "(same file/marker/claim text)",
          c["finding_fingerprint"] == c2["finding_fingerprint"])

    check(".java is not in the scanned extension set at all (owned by "
          "audit-stubs.sh, contract-drift-free division of labor)",
          ".java" not in TEXT_EXTENSIONS)

    check("domain heuristic: workflow files -> CI_GOVERNANCE",
          _domain_for(".github/workflows/kof-bots.yml") == "CI_GOVERNANCE")
    check("domain heuristic: docs -> DOCS_TRAINING",
          _domain_for("docs/development/roadmap.md") == "DOCS_TRAINING")
    check("domain heuristic: scripts -> CLI_TOOLING",
          _domain_for("scripts/foo.sh") == "CLI_TOOLING")

    root = os.path.join(os.path.dirname(__file__), "..", "..", "..")
    if os.path.isdir(root):
        found_self = [p for p in iter_source_files(root) if p.startswith("scripts/debt-scout/")]
        check("the live walk never yields this tool's own files "
              "(no self-detection loop)", found_self == [])
        candidates = scan(root)
        check("every candidate produced by a live scan of this repo is "
              "schema-valid", all(schema.validate_candidate(c) == [] for c in candidates))
        check("every candidate produced by a live scan is C0",
              all(c["confidence"] == "C0" for c in candidates))
    else:
        print("  FAIL — repo root not found for live scan")
        ok = False

    return ok


def main(argv):
    if "--selftest" in argv:
        ok = selftest()
        print("satd --selftest:", "OK" if ok else "FAIL")
        return 0 if ok else 1
    root = "."
    if "--root" in argv:
        root = argv[argv.index("--root") + 1]
    print(json.dumps(scan(root), indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
