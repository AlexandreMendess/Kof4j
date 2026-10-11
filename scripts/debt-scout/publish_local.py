#!/usr/bin/env python3
"""publish_local.py — LOCAL-ONLY rehearsal of the Wave-3 C3 publisher
(source spec V2 §41/§45/§64/§75). It applies every publication gate the
real publisher would, but its only sink is the local filesystem:

  .debt-scout/local-issues/NNNN-<fp12>.md   one file per "opened" Issue
  .debt-scout/local-issues/ledger.jsonl     dedup + budget ledger
  .debt-scout/local-issues/previews/        --preview renders (not Issues)

There is NO GitHub write path in this module — not disabled, absent: it
does not import subprocess, urllib, http or socket (asserted by the
selftest). Real publication stays gated on the maintainer's phase-S1
authorization (`D-DEBT-SCOUT`/`D-DEBT-SCOUT-W2`); this rehearsal needs
none, because nothing leaves the machine and `.debt-scout/` is ignored.

Input is a `scan.py --out` report, treated as DATA (V2 §58): size cap,
JSON, expected shape; nothing in it is ever executed.

Gates, in order (first failure is the recorded reason):
  confidence == C3 · rule in allowlist · public-safe (no SECURITY
  domain) · not RESOLUTION_IN_PROGRESS · not a known duplicate ·
  actionability (contract + exit condition) · explanation completeness
  1.0 · not already in the local ledger · budget (run/day/week)

CLI:
  publish_local.py --report FILE [--out-dir DIR] [--rules R1,R2]
                   [--per-run N] [--per-day N] [--per-week N] [--preview N]
  publish_local.py --selftest
"""
import json
import os
import re
import sys
import tempfile
from datetime import datetime, timedelta, timezone

sys.path.insert(0, os.path.dirname(__file__))
import issue_body  # noqa: E402

for _stream in (sys.stdout, sys.stderr):
    if hasattr(_stream, "reconfigure"):
        _stream.reconfigure(encoding="utf-8", errors="replace")

DEFAULT_OUT = os.path.join(".debt-scout", "local-issues")
DEFAULT_RULES = ("KOF-DEBT-DECISION-001",)
MAX_REPORT_BYTES = 20_000_000
_FP_RE = re.compile(r"^sha256:[0-9a-f]{64}$")


class ReportError(ValueError):
    pass


def load_report(path):
    if os.path.getsize(path) > MAX_REPORT_BYTES:
        raise ReportError(f"report larger than {MAX_REPORT_BYTES} bytes")
    with open(path, encoding="utf-8") as f:
        try:
            data = json.load(f)
        except json.JSONDecodeError as e:
            raise ReportError(f"report is not valid JSON: {e}") from e
    if not isinstance(data, dict) or not isinstance(data.get("clusters"), list) \
            or not isinstance(data.get("run"), dict):
        raise ReportError("report lacks run/clusters (not a scan.py --out report)")
    for cl in data["clusters"]:
        if not isinstance(cl, dict) or not _FP_RE.match(str(cl.get("debt_fingerprint", ""))):
            raise ReportError("a cluster has no valid debt_fingerprint")
    return data


def gate(cluster, rules, ledger_fps):
    """Pure: (eligible, reason). Order matters — the first failing gate
    is what gets recorded."""
    members = cluster.get("members") or []
    rule = members[0].get("rule_id") if members else None
    domains = {d for m in members for d in (m.get("taxonomy") or {}).get("domains", [])}
    domains.add((cluster.get("taxonomy") or {}).get("primary_domain"))
    dup = ((cluster.get("kof_triage") or {}).get("duplicate_precedent_check") or {})
    if cluster.get("confidence") != "C3":
        return False, f"confidence {cluster.get('confidence')} (only C3 publishes)"
    if rule not in rules:
        return False, f"rule {rule} not in the publish allowlist"
    if "SECURITY" in domains:
        return False, "SECURITY_REVIEW — never a public Issue (V2 §31)"
    if cluster.get("lifecycle_state") == "RESOLUTION_IN_PROGRESS":
        return False, "RESOLUTION_IN_PROGRESS — an active DOING.md claim owns it"
    if dup.get("status") == "DUPLICATE":
        return False, f"duplicate of {dup.get('matched')}"
    exp = issue_body.explanation(cluster)
    if not exp["contract"] or not exp["exit_condition"]:
        return False, "actionability: governing contract and exit condition required (V2 §45)"
    ratio, missing = issue_body.completeness(cluster)
    if ratio < 1.0:
        return False, f"explanation completeness {ratio:.2f} (missing {', '.join(missing)})"
    if cluster["debt_fingerprint"] in ledger_fps:
        return False, "already recorded (same debt fingerprint) — idempotent"
    return True, "eligible"


def read_ledger(out_dir):
    path = os.path.join(out_dir, "ledger.jsonl")
    entries = []
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line:
                    entries.append(json.loads(line))
    return entries


def _count_since(entries, now, delta):
    return sum(1 for e in entries
               if datetime.fromisoformat(e["opened_at"]) >= now - delta)


def publish(report, out_dir=DEFAULT_OUT, rules=DEFAULT_RULES, per_run=1,
            per_day=1, per_week=8, preview=0, now=None):
    now = now or datetime.now(timezone.utc)
    os.makedirs(out_dir, exist_ok=True)
    ledger = read_ledger(out_dir)
    ledger_fps = {e["debt_fingerprint"] for e in ledger}
    sha = str(report["run"].get("analyzed_sha", "UNKNOWN"))
    result = {"opened": [], "queued_over_budget": [], "skipped": {}, "previews": [],
              "issues_opened_on_github": 0}
    opened_this_run = 0
    skipped = []
    for cl in report["clusters"]:
        ok, reason = gate(cl, rules, ledger_fps)
        if not ok:
            skipped.append((cl, reason))
            result["skipped"][reason] = result["skipped"].get(reason, 0) + 1
            continue
        if (opened_this_run >= per_run
                or _count_since(ledger, now, timedelta(days=1)) >= per_day
                or _count_since(ledger, now, timedelta(days=7)) >= per_week):
            result["queued_over_budget"].append(cl["debt_fingerprint"])  # never dropped
            continue
        number = len(ledger) + 1
        name = f"{number:04d}-{cl['debt_fingerprint'].split(':')[1][:12]}.md"
        title = issue_body.render_title(cl)
        with open(os.path.join(out_dir, name), "w", encoding="utf-8", newline="\n") as f:
            f.write(f"# {title}\n\n" + issue_body.render_body(cl, sha))
        entry = {"number": number, "file": name, "title": title,
                 "debt_fingerprint": cl["debt_fingerprint"], "analyzed_sha": sha,
                 "opened_at": now.isoformat()}
        with open(os.path.join(out_dir, "ledger.jsonl"), "a", encoding="utf-8") as f:
            f.write(json.dumps(entry, sort_keys=True) + "\n")
        ledger.append(entry)
        ledger_fps.add(cl["debt_fingerprint"])
        opened_this_run += 1
        result["opened"].append(entry)

    if preview:
        pdir = os.path.join(out_dir, "previews")
        os.makedirs(pdir, exist_ok=True)
        order = {"C2": 0, "C1": 1, "C0": 2}
        ranked = sorted(skipped, key=lambda t: order.get(t[0].get("confidence"), 3))
        for cl, reason in ranked[:preview]:
            name = f"preview-{cl['debt_fingerprint'].split(':')[1][:12]}.md"
            with open(os.path.join(pdir, name), "w", encoding="utf-8", newline="\n") as f:
                f.write(f"# {issue_body.render_title(cl)}\n\n"
                        f"> Not eligible: {issue_body.sanitize_inline(reason)}\n\n"
                        + issue_body.render_body(cl, sha, preview=True))
            result["previews"].append(name)
    return result


# --------------------------------------------------------------------------
# selftest
# --------------------------------------------------------------------------

def _report(*clusters):
    return {"run": {"analyzed_sha": "e" * 40}, "clusters": list(clusters)}


def selftest():
    ok = True

    def check(name, cond):
        nonlocal ok
        print(f"  {'ok  ' if cond else 'FAIL'}— {name}")
        ok = ok and cond

    fp = lambda c: "sha256:" + c * 64  # noqa: E731
    c3 = issue_body.sample_cluster(debt_fingerprint=fp("1"))
    t0 = datetime(2026, 9, 23, 12, tzinfo=timezone.utc)
    with tempfile.TemporaryDirectory() as td:
        r1 = publish(_report(c3), td, now=t0)
        files = [f for f in os.listdir(td) if f.endswith(".md")]
        check("a complete C3 cluster opens exactly one local Issue file",
              len(r1["opened"]) == 1 and len(files) == 1)
        check("the file carries the fingerprint marker exactly once",
              open(os.path.join(td, files[0]), encoding="utf-8").read()
              .count("kof-debt-fingerprint: " + fp("1")) == 1)
        r2 = publish(_report(c3), td, now=t0 + timedelta(days=2))
        check("rerun with the same debt opens nothing (idempotent)",
              r2["opened"] == [] and "already recorded" in " ".join(r2["skipped"]))
        moved = issue_body.sample_cluster(debt_fingerprint=fp("1"))
        moved["members"][0]["locations"][0]["line"] = 999
        check("same debt after line movement is still a duplicate",
              publish(_report(moved), td, now=t0 + timedelta(days=3))["opened"] == [])

    with tempfile.TemporaryDirectory() as td:
        a = issue_body.sample_cluster(debt_fingerprint=fp("2"))
        b = issue_body.sample_cluster(debt_fingerprint=fp("3"))
        r = publish(_report(a, b), td, now=t0)
        check("budget 1/run: second C3 is queued, never dropped",
              len(r["opened"]) == 1 and r["queued_over_budget"] == [fp("3")])
        r_same_day = publish(_report(b), td, now=t0 + timedelta(hours=3))
        check("budget 1/day holds across runs on the same day",
              r_same_day["opened"] == [] and r_same_day["queued_over_budget"] == [fp("3")])
        check("next day the queued one opens",
              len(publish(_report(b), td, now=t0 + timedelta(days=1, hours=1))["opened"]) == 1)

    def reason(cl):
        return gate(cl, DEFAULT_RULES, set())[1]
    check("C2 never publishes",
          reason(issue_body.sample_cluster(confidence="C2")).startswith("confidence C2"))
    check("a rule outside the allowlist never publishes", "allowlist" in reason(
        issue_body.sample_cluster(members=[{"claim": "x", "rule_id": "KOF-DEBT-SATD-001"}])))
    sec = issue_body.sample_cluster()
    sec["taxonomy"] = dict(sec["taxonomy"], primary_domain="SECURITY")
    check("a SECURITY finding goes to security review, never an Issue",
          reason(sec).startswith("SECURITY_REVIEW"))
    check("an owned (RESOLUTION_IN_PROGRESS) cluster never publishes",
          reason(issue_body.sample_cluster(lifecycle_state="RESOLUTION_IN_PROGRESS"))
          .startswith("RESOLUTION_IN_PROGRESS"))
    dup = issue_body.sample_cluster()
    dup["kof_triage"] = dict(dup["kof_triage"],
                             duplicate_precedent_check={"status": "DUPLICATE", "matched": "#9"})
    check("a known duplicate never publishes", reason(dup) == "duplicate of #9")
    check("no exit condition -> actionability gate",
          reason(issue_body.sample_cluster(exit_condition=None)).startswith("actionability"))

    with tempfile.TemporaryDirectory() as td:
        bad = os.path.join(td, "bad.json")
        for payload in ("not json", json.dumps([1, 2]),
                        json.dumps({"run": {}, "clusters": [{"debt_fingerprint": "x; rm -rf /"}]})):
            with open(bad, "w", encoding="utf-8") as f:
                f.write(payload)
            try:
                load_report(bad)
                check(f"malformed report rejected: {payload[:20]!r}", False)
            except ReportError:
                check(f"malformed report rejected: {payload[:20]!r}", True)

    with open(__file__, encoding="utf-8") as f:
        src = f.read()
    check("no network/process module is imported (no GitHub write path exists)",
          re.search(r"^\s*(import|from)\s+(subprocess|urllib|http|socket|requests)\b",
                    src, re.M) is None)
    return ok


def main(argv):
    if "--selftest" in argv:
        ok = selftest()
        print("publish_local --selftest:", "OK" if ok else "FAIL")
        return 0 if ok else 1

    def arg(name, default):
        return argv[argv.index(name) + 1] if name in argv else default

    if "--report" not in argv:
        print(__doc__.split("CLI:")[1], file=sys.stderr)
        return 2
    try:
        report = load_report(arg("--report", None))
    except (ReportError, OSError) as e:
        print(f"publish_local: {e}", file=sys.stderr)
        return 1
    result = publish(report, arg("--out-dir", DEFAULT_OUT),
                     tuple(r for r in arg("--rules", ",".join(DEFAULT_RULES)).split(",") if r),
                     int(arg("--per-run", 1)), int(arg("--per-day", 1)),
                     int(arg("--per-week", 8)), int(arg("--preview", 0)))
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
