#!/usr/bin/env python3
"""scan.py — the Wave 1 orchestrator CLI (`docs/development/
technical-debt/DEBT_SCOUT_CONTRACT.md` §11). Combines `config`,
`branch_discovery`, and every detector under `detectors/` into one
report. This is the last Wave-1 unit before a discovery-only GitHub
Actions workflow calls it — this script itself still calls no GitHub
API and writes nothing but a local JSON file/stdout.

Hard invariant, checked at the orchestration layer too (defense in
depth, not trusting each detector alone): the run REFUSES to proceed if
`.debt-scout.yml` fails validation, and the emitted report asserts
`issues_opened: 0` and that no candidate is `publication.eligible=true`
— violating either is a bug in this script, not a possible outcome.

CLI:
  scan.py --phase state        -> JSON: branch_discovery.discover() only
  scan.py --phase deterministic [--out FILE]
                                -> JSON: full Wave-1 report (default phase)
  scan.py --selftest
"""
import json
import os
import subprocess
import sys
import tempfile

HERE = os.path.dirname(__file__)
sys.path.insert(0, HERE)
import branch_discovery  # noqa: E402
import config as config_mod  # noqa: E402
import schema  # noqa: E402
from detectors import satd  # noqa: E402

for _stream in (sys.stdout, sys.stderr):
    if hasattr(_stream, "reconfigure"):
        _stream.reconfigure(encoding="utf-8", errors="replace")

SCANNER_VERSION = "0.1.0"


class ScanError(RuntimeError):
    pass


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


def run_state_phase(root="."):
    return branch_discovery.discover(root)


def run_deterministic_phase(root="."):
    """Loads+validates config, runs every Wave-1 detector, returns the
    full report dict. Raises ScanError if the config is invalid — a scan
    never runs on an unvalidated/rejected config."""
    config_path = os.path.join(root, ".debt-scout.yml")
    try:
        cfg = config_mod.load_config(config_path)
    except (config_mod.ConfigError, OSError) as e:
        raise ScanError(f"refusing to scan: invalid config: {e}") from e

    repo_state = branch_discovery.discover(root)
    candidates = list(repo_state.get("candidates", []))
    if cfg["rules"].get("satd"):
        candidates.extend(satd.scan(root))

    for c in candidates:
        errors = schema.validate_candidate(c)
        if errors:
            raise ScanError(f"a detector emitted a schema-invalid candidate: {errors}")
        if c["publication"]["eligible"] is True:
            raise ScanError(
                "a detector emitted publication.eligible=true — impossible "
                "in Wave 1 (no publisher exists); this is a bug in the "
                "detector, not a valid outcome"
            )

    by_confidence = {"C0": 0, "C1": 0, "C2": 0, "C3": 0}
    for c in candidates:
        by_confidence[c["confidence"]] = by_confidence.get(c["confidence"], 0) + 1

    return {
        "run": {
            "analyzed_sha": _analyzed_sha(root),
            "scanner_version": SCANNER_VERSION,
            "mode": cfg["mode"],
            "model_used": False,
        },
        "repository_state": {
            "default_branch": repo_state.get("default_branch"),
            "declared_active_branch": repo_state.get("declared_active_branch"),
            "declared_active_branch_exists": repo_state.get("declared_active_branch_exists"),
        },
        "candidates": candidates,
        "summary": {
            "total_candidates": len(candidates),
            "by_confidence": by_confidence,
            "issues_opened": 0,
        },
    }


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

    # A non-git, non-KOF temp directory: branch_discovery must degrade
    # gracefully (no exception), and with rules.satd off there should be
    # zero candidates and zero detectors run.
    with tempfile.TemporaryDirectory() as td:
        with open(os.path.join(td, ".debt-scout.yml"), "w", encoding="utf-8") as f:
            f.write(
                "schema: 2\nmode: shadow\n"
                "budget:\n  issues_per_run: 1\n  issues_per_day: 1\n  issues_per_week: 1\n"
                "confidence:\n  auto_issue_min: C3\n  inbox_min: C2\n"
                "trust:\n  min_reviewed_findings: 0\n  min_effective_precision: 1.0\n"
                "sarif:\n  enabled: false\n"
                "model:\n  enabled: false\n"
                "rules:\n  satd: false\n"
            )
        report = run_deterministic_phase(td)
        check("a non-git temp directory does not crash the deterministic phase",
              report["repository_state"]["default_branch"] is None)
        check("rules.satd=false runs zero candidates",
              report["summary"]["total_candidates"] == 0)
        check("issues_opened is always 0 in Wave 1",
              report["summary"]["issues_opened"] == 0)

        # invalid config must refuse to scan, not silently proceed
        with open(os.path.join(td, ".debt-scout.yml"), "w", encoding="utf-8") as f:
            f.write("schema: 2\nmode: canary\n")
        try:
            run_deterministic_phase(td)
            check("an invalid config (mode=canary) is refused", False)
        except ScanError:
            check("an invalid config (mode=canary) is refused", True)

        # a small fixture with a real TODO, satd enabled
        with open(os.path.join(td, ".debt-scout.yml"), "w", encoding="utf-8") as f:
            f.write(
                "schema: 2\nmode: shadow\n"
                "budget:\n  issues_per_run: 1\n  issues_per_day: 1\n  issues_per_week: 1\n"
                "confidence:\n  auto_issue_min: C3\n  inbox_min: C2\n"
                "trust:\n  min_reviewed_findings: 0\n  min_effective_precision: 1.0\n"
                "sarif:\n  enabled: false\n"
                "model:\n  enabled: false\n"
                "rules:\n  satd: true\n"
            )
        os.makedirs(os.path.join(td, "scripts"), exist_ok=True)
        with open(os.path.join(td, "scripts", "x.sh"), "w", encoding="utf-8") as f:
            f.write("#!/bin/sh\n# TODO: fixture marker\n")
        report2 = run_deterministic_phase(td)
        check("rules.satd=true finds the planted TODO fixture",
              report2["summary"]["total_candidates"] == 1
              and report2["summary"]["by_confidence"]["C0"] == 1)
        check("every candidate in the report is schema-valid",
              all(schema.validate_candidate(c) == [] for c in report2["candidates"]))

    # live run against the real repo this script ships in
    root = os.path.join(HERE, "..", "..")
    if os.path.exists(os.path.join(root, "AGENTS.md")):
        state = run_state_phase(root)
        # declared_active_branch_exists can legitimately be False here —
        # e.g. on a frozen `main` whose AGENTS.md still names a deleted
        # branch; that IS the tool working, not a test precondition.
        check("live --phase state resolves the real repo's branches",
              state["default_branch"] is not None
              and state["declared_active_branch_exists"] is not None)
        live_report = run_deterministic_phase(root)
        check("live --phase deterministic produces schema-valid candidates only",
              all(schema.validate_candidate(c) == [] for c in live_report["candidates"]))
        check("live run: issues_opened is 0",
              live_report["summary"]["issues_opened"] == 0)
        check("live run: no candidate is ever publish-eligible",
              all(c["publication"]["eligible"] is False for c in live_report["candidates"]))
    else:
        print("  FAIL — real repo root not found for the live check")
        ok = False

    return ok


def main(argv):
    if "--selftest" in argv:
        ok = selftest()
        print("scan --selftest:", "OK" if ok else "FAIL")
        return 0 if ok else 1

    root = "."
    if "--root" in argv:
        root = argv[argv.index("--root") + 1]
    phase = "deterministic"
    if "--phase" in argv:
        phase = argv[argv.index("--phase") + 1]

    try:
        if phase == "state":
            result = run_state_phase(root)
        elif phase == "deterministic":
            result = run_deterministic_phase(root)
        else:
            print(f"scan: unknown --phase {phase!r} (state|deterministic)", file=sys.stderr)
            return 2
    except ScanError as e:
        print(f"scan: {e}", file=sys.stderr)
        return 1

    text = json.dumps(result, indent=2, sort_keys=True)
    if "--out" in argv:
        out_path = argv[argv.index("--out") + 1]
        os.makedirs(os.path.dirname(out_path) or ".", exist_ok=True)
        with open(out_path, "w", encoding="utf-8") as f:
            f.write(text + "\n")
        print(f"scan: wrote {out_path}")
    else:
        print(text)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
