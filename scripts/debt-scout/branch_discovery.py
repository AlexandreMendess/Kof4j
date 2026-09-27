#!/usr/bin/env python3
"""branch_discovery.py — resolves default/active branch at RUNTIME; never
hardcodes a version string like `beta-0.5.0` (`docs/development/
technical-debt/DEBT_SCOUT_CONTRACT.md` §11/§`Branch/ref discovery`).

Two independent facts, both discovered, never assumed:
  - the repository's default branch (`git symbolic-ref
    refs/remotes/origin/HEAD`, with a network fallback to
    `git ls-remote --symref`);
  - the branch `AGENTS.md` DECLARES as active (parsed out of its prose —
    `AGENTS.md` version header carries "active branch = `<name>`", with
    or without bold markdown around the backticks — confirmed both forms
    are real: beta-0.5.0's header wraps the name in ** (bold), main's
    frozen snapshot leaves it plain; the first cut of this regex only
    matched the bold form and silently returned `None` on `main` —
    found by actually running this tool against `main`, not by
    inspection).

If the declared branch does not exist as a ref, this is NOT a crash and
NOT silently ignored: it becomes one `C1` contract-drift candidate
(§18/§27 of the source V2 spec — "docs says branch X is active but X
does not exist" is the spec's own motivating example). Confirming the
declared branch DOES exist (the common case) produces zero candidates —
a clean run is not itself a finding.

CLI:
  branch_discovery.py [--cwd DIR] [--agents-md PATH]  -> JSON to stdout
  branch_discovery.py --selftest
"""
import json
import os
import re
import subprocess
import sys

sys.path.insert(0, os.path.dirname(__file__))
import fingerprint  # noqa: E402

for _stream in (sys.stdout, sys.stderr):
    if hasattr(_stream, "reconfigure"):
        _stream.reconfigure(encoding="utf-8", errors="replace")

_DECLARED_RE = re.compile(r"active branch\s*=\s*\*{0,2}`([^`]+)`\*{0,2}")
_RULE_ID = "KOF-DEBT-DOC-BRANCH-001"
_RULE_VERSION = "1.0.0"


def parse_declared_active_branch(agents_md_text):
    """Pure function: AGENTS.md prose -> declared branch name or None."""
    m = _DECLARED_RE.search(agents_md_text)
    return m.group(1) if m else None


def _run_git(cwd, *args, timeout=10):
    try:
        return subprocess.run(
            ["git", "-C", cwd, *args],
            capture_output=True, text=True, timeout=timeout,
        )
    except (OSError, subprocess.SubprocessError):
        return None


def default_branch(cwd="."):
    """origin's default branch, or None if it cannot be determined
    (never raises — an unresolved default branch is data, not a crash)."""
    out = _run_git(cwd, "symbolic-ref", "--short", "refs/remotes/origin/HEAD")
    if out is not None and out.returncode == 0:
        name = out.stdout.strip()
        if name.startswith("origin/"):
            return name[len("origin/"):]
    # Fallback: some checkouts (notably shallow CI checkouts) never set
    # the local origin/HEAD symref. This needs network.
    out = _run_git(cwd, "ls-remote", "--symref", "origin", "HEAD")
    if out is not None and out.returncode == 0:
        for line in out.stdout.splitlines():
            if line.startswith("ref:"):
                ref = line.split()[1]
                if ref.startswith("refs/heads/"):
                    return ref[len("refs/heads/"):]
    return None


def branch_exists(name, cwd="."):
    for ref in (f"refs/remotes/origin/{name}", f"refs/heads/{name}"):
        out = _run_git(cwd, "show-ref", "--verify", "--quiet", ref)
        if out is not None and out.returncode == 0:
            return True
    return False


def _drift_candidate(declared, analyzed_sha="UNKNOWN"):
    """Schema-v2-compliant (contract §59/`schema.py`) — this path was NOT
    exercised by any selftest until a real drift was found by running
    against `main` (beta-0.5.0 always has declared_active_branch_exists
    == True, so `candidates` was always `[]` there and this function had
    never actually been schema-validated end to end). Keep it that way:
    every field `schema.validate_candidate` requires is filled for real,
    not stubbed."""
    claim = (
        f"AGENTS.md declares the active branch as '{declared}', but no "
        f"ref refs/remotes/origin/{declared} or refs/heads/{declared} "
        "exists in this checkout."
    )
    symbol = f"AGENTS.md#active-branch:{declared}"
    finding_fp = fingerprint.finding_fingerprint(_RULE_ID, symbol, claim)
    return {
        "schema": 2,
        "candidate_id": finding_fp.split(":", 1)[1][:16],
        "rule_id": _RULE_ID,
        "rule_version": _RULE_VERSION,
        "finding_fingerprint": finding_fp,
        "confidence": "C1",
        "taxonomy": {
            "debt_type": "DOCUMENTATION",
            "primary_domain": "CI_GOVERNANCE",
            "domains": ["CI_GOVERNANCE", "DOCS_TRAINING"],
            "mechanism": "DOC_CODE_DRIFT",
        },
        "claim": claim,
        "locations": [{"path": "AGENTS.md", "line": None}],
        "publication": {"public_safe": True, "eligible": False,
                         "reason": "C1 — candidate store only, never an Issue"},
        "lineage": {
            "analyzed_sha": analyzed_sha,
            "model_used": False,
            "rule_id": _RULE_ID,
            "rule_version": _RULE_VERSION,
        },
        "evidence": [{"type": "DOC", "ref": "AGENTS.md"}],
    }


def _analyzed_sha(cwd):
    out = _run_git(cwd, "rev-parse", "HEAD")
    if out is not None and out.returncode == 0:
        return out.stdout.strip()
    return "UNKNOWN"


def discover(cwd=".", agents_md_path=None):
    if agents_md_path is None:
        agents_md_path = os.path.join(cwd, "AGENTS.md")
    result = {
        "default_branch": default_branch(cwd),
        "declared_active_branch": None,
        "declared_active_branch_exists": None,
        "candidates": [],
    }
    if not os.path.exists(agents_md_path):
        return result
    with open(agents_md_path, "r", encoding="utf-8") as f:
        text = f.read()
    declared = parse_declared_active_branch(text)
    result["declared_active_branch"] = declared
    if declared is None:
        return result
    exists = branch_exists(declared, cwd)
    result["declared_active_branch_exists"] = exists
    if not exists:
        result["candidates"].append(_drift_candidate(declared, _analyzed_sha(cwd)))
    return result


# --------------------------------------------------------------------------
# selftest — pure-logic assertions (no network) + a live check against the
# real repo this script ships in (offline: origin/HEAD is a local ref).
# --------------------------------------------------------------------------

def selftest():
    ok = True

    def check(name, cond):
        nonlocal ok
        status = "ok  " if cond else "FAIL"
        print(f"  {status}— {name}")
        ok = ok and cond

    check("parses the bold form (beta-0.5.0's real wording)",
          parse_declared_active_branch(
              "active branch = **`beta-0.5.0`** (`D-BRANCH-0.5.0`, 09/20)"
          ) == "beta-0.5.0")
    check("parses the plain, unbolded form (main's real frozen wording — "
          "this form was a false negative in the first cut of the regex, "
          "found by running against main, not by inspection)",
          parse_declared_active_branch(
              "...D-BRANCH-0.5.0` (20/09); active branch = `beta-0.4.0`)"
          ) == "beta-0.4.0")
    check("returns None when the wording is absent",
          parse_declared_active_branch("nothing about branches here") is None)
    check("does not false-positive on a similar but different phrase",
          parse_declared_active_branch("the active branch is `main`") is None)

    import schema  # local import: avoids a hard dependency for callers
    # that only need parsing/discovery, not validation

    cwd = os.path.join(os.path.dirname(__file__), "..", "..")
    real_agents_md = os.path.join(cwd, "AGENTS.md")
    if os.path.exists(real_agents_md):
        result = discover(cwd=cwd, agents_md_path=real_agents_md)
        check("live: default branch resolves to a non-empty name",
              bool(result["default_branch"]))
        check("live: AGENTS.md's declared active branch is parsed",
              result["declared_active_branch"] is not None)
        # INVARIANT, not a specific outcome — this repo's checked-out
        # branch decides whether there IS drift right now (there
        # genuinely is on `main`, genuinely isn't on `beta-0.5.0`; the
        # selftest must hold on either, not assume one).
        exists = result["declared_active_branch_exists"]
        candidates = result["candidates"]
        check("live: exists=True <=> zero candidates, exists=False <=> "
              f"exactly one schema-valid C1 candidate (measured: "
              f"declared={result['declared_active_branch']!r} exists={exists})",
              (exists is True and candidates == [])
              or (exists is False and len(candidates) == 1
                  and candidates[0]["confidence"] == "C1"
                  and schema.validate_candidate(candidates[0]) == []))
    else:
        print("  FAIL — real repo AGENTS.md not found at", real_agents_md)
        ok = False

    # drift path, exercised without touching the real repo: a branch name
    # that certainly does not exist as a ref.
    bogus = "definitely-not-a-real-branch-xyz-000"
    check("branch_exists is False for a made-up branch name",
          branch_exists(bogus, cwd=cwd) is False)
    candidate = _drift_candidate(bogus, "deadbeef" * 5)
    check("a drift candidate is C1 and never Issue-eligible",
          candidate["confidence"] == "C1"
          and candidate["publication"]["eligible"] is False)
    errors = schema.validate_candidate(candidate)
    check("a drift candidate is schema-valid (this path was NOT exercised "
          "before the first real drift was found on main)", errors == [])
    if errors:
        print("    ", errors)

    return ok


def main(argv):
    if "--selftest" in argv:
        ok = selftest()
        print("branch_discovery --selftest:", "OK" if ok else "FAIL")
        return 0 if ok else 1
    cwd = "."
    agents_md_path = None
    if "--cwd" in argv:
        cwd = argv[argv.index("--cwd") + 1]
    if "--agents-md" in argv:
        agents_md_path = argv[argv.index("--agents-md") + 1]
    print(json.dumps(discover(cwd, agents_md_path), indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
