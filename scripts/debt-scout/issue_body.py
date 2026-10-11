#!/usr/bin/env python3
"""issue_body.py — renders the C3 Issue dossier (source spec V2 §63) for a
qualified cluster. Pure: no IO, no subprocess, no network.

Every string that came from the repository (claims, paths, commit
subjects, DOING owners) is UNTRUSTED data (V2 §56/§73). It is rendered
only after `sanitize_inline`/`sanitize_block`:
  - control characters are removed (newlines too, for inline fields);
  - `<!--` / `-->` are neutralized, so a claim can never forge or close
    the hidden fingerprint metadata comments;
  - backtick runs are collapsed, so a claim can never close a code span
    or fence and inject Markdown;
  - length is capped.
Nothing here is ever executed — `$(cmd)`, `${{ ... }}` and friends stay
inert text; the module does not import `subprocess` at all (asserted by
the selftest).

CLI:
  issue_body.py --selftest
"""
import os
import re
import sys

for _stream in (sys.stdout, sys.stderr):
    if hasattr(_stream, "reconfigure"):
        _stream.reconfigure(encoding="utf-8", errors="replace")

SCHEMA_MARKER = 2
MAX_TITLE = 120
MAX_INLINE = 300
MAX_BLOCK = 4000
_CONTROL_RE = re.compile(r"[\x00-\x08\x0b-\x1f\x7f  ]")

# §75 explanation completeness — every field must be present for an Issue.
EXPLANATION_FIELDS = ("signal", "contract", "mechanism", "measurement",
                      "cost", "lock_in", "exit_condition", "uncertainty")


def _neutralize(text):
    text = text.replace("<!--", "&lt;!--").replace("-->", "--&gt;")
    return re.sub(r"`+", "'", text)


def sanitize_inline(value, limit=MAX_INLINE):
    text = "" if value is None else str(value)
    text = _CONTROL_RE.sub(" ", text.replace("\r", " ").replace("\n", " "))
    text = re.sub(r"\s+", " ", _neutralize(text)).strip()
    return text if len(text) <= limit else text[: limit - 1] + "…"


def sanitize_block(value, limit=MAX_BLOCK):
    text = "" if value is None else str(value).replace("\r\n", "\n")
    text = _CONTROL_RE.sub(" ", text.replace("\t", "    "))
    text = _neutralize(text)
    return text if len(text) <= limit else text[: limit - 1] + "…"


def quote_block(value, limit=MAX_BLOCK):
    """Untrusted multi-line text rendered as a blockquote: every line is
    prefixed with '> ' and a leading Markdown block marker (#, -, *, +,
    >, |, digit-dot) is backslash-escaped — so a newline inside repo
    data can never start a heading, list, table or a new section (found
    by this module's own selftest: sanitize_block alone let
    '\\n## injected heading' through)."""
    lines = []
    for line in sanitize_block(value, limit).split("\n"):
        stripped = line.lstrip()
        if re.match(r"([#>|*+-]|\d+[.)])", stripped):
            line = line[: len(line) - len(stripped)] + "\\" + stripped
        lines.append("> " + line)
    return "\n".join(lines)


def explanation(cluster):
    """The §75 fields, derived from the cluster; missing = None."""
    triage = cluster.get("kof_triage") or {}
    pv = cluster.get("priority_vector") or {}
    members = cluster.get("members") or []
    first = members[0] if members else {}
    contract = [c for c in (triage.get("contract_source") or []) if c != "UNKNOWN"]
    lock_in = (pv.get("lock_in") or {}).get("level")
    interest = (pv.get("interest_observed") or {}).get("level")
    uncertainty = (pv.get("uncertainty") or {}).get("level")
    return {
        "signal": first.get("claim") or None,
        "contract": ", ".join(contract) or None,
        "mechanism": (cluster.get("taxonomy") or {}).get("mechanism"),
        "measurement": triage.get("measurement"),
        "cost": interest if interest not in (None, "unknown") else None,
        "lock_in": lock_in if lock_in not in (None, "unknown") else None,
        "exit_condition": cluster.get("exit_condition") or None,
        "uncertainty": uncertainty,
    }


def completeness(cluster):
    exp = explanation(cluster)
    present = sum(1 for f in EXPLANATION_FIELDS if exp.get(f))
    return present / len(EXPLANATION_FIELDS), [f for f in EXPLANATION_FIELDS if not exp.get(f)]


def render_title(cluster):
    tax = cluster.get("taxonomy") or {}
    members = cluster.get("members") or [{}]
    claim = sanitize_inline(members[0].get("claim", ""), MAX_TITLE)
    prefix = f"[Debt][{tax.get('debt_type', '?')}/{tax.get('primary_domain', '?')}] "
    return sanitize_inline(prefix + claim, MAX_TITLE)


def _row(label, value):
    return f"| {label} | {sanitize_inline(value) if value else 'UNKNOWN'} |"


def render_body(cluster, analyzed_sha, preview=False):
    tax = cluster.get("taxonomy") or {}
    triage = cluster.get("kof_triage") or {}
    pv = cluster.get("priority_vector") or {}
    origin = cluster.get("historical_origin") or {}
    owner = cluster.get("ownership") or {}
    checklist = cluster.get("c3_checklist") or {}
    fp = sanitize_inline(cluster.get("debt_fingerprint", "UNKNOWN"))
    exp = explanation(cluster)
    ratio, missing = completeness(cluster)
    out = []
    if preview:
        out += ["> **PREVIEW — NOT ELIGIBLE.** Rendered locally to show the dossier "
                f"format; confidence is `{sanitize_inline(cluster.get('confidence'))}`, "
                "not C3. This is not an Issue.", ""]
    out += [
        "## Technical Debt Record", "",
        "| Field | Value |", "|---|---|",
        _row("Debt fingerprint", fp),
        _row("Confidence", cluster.get("confidence")),
        _row("Observed SHA", analyzed_sha),
        _row("First known origin", origin.get("first_seen_sha") or origin.get("status")),
        _row("Debt type", tax.get("debt_type")),
        _row("Primary KOF domain", tax.get("primary_domain")),
        _row("Mechanism", tax.get("mechanism")),
        _row("Lock-in", exp["lock_in"]),
        "",
        "## 1. Liability", "", quote_block(exp["signal"] or "UNKNOWN"), "",
        "## 2. Governing KOF contract", "", quote_block(exp["contract"] or "UNKNOWN"), "",
        "## 3. Current implementation", "",
        "| Location | Line |", "|---|---|",
    ]
    for m in cluster.get("members") or []:
        for loc in m.get("locations") or []:
            out.append(f"| {sanitize_inline(loc.get('path'))} | {sanitize_inline(loc.get('line'))} |")
    out += [
        "", "## 4. Measurement", "", quote_block(exp["measurement"] or "NOT_RUN"), "",
        "## 5. Historical origin", "",
        f"- status: {sanitize_inline(origin.get('status') or 'NOT_CHECKED')}",
        f"- first seen: {sanitize_inline(origin.get('first_seen_sha') or 'UNKNOWN')}",
        f"- original context (commit subject, verbatim): "
        f"{sanitize_inline(origin.get('original_context') or 'UNKNOWN')}",
        f"- related refs: {sanitize_inline(', '.join(origin.get('related_refs') or []) or 'none')}",
        "- author intent: UNKNOWN (never inferred)", "",
        "## 6. KOF-first triage", "",
        f"- KOF VALIDITY: {sanitize_inline(triage.get('kof_validity'))}",
        f"- CONTRACT SOURCE: {sanitize_inline(exp['contract'] or 'UNKNOWN')}",
        f"- CURRENT KOF IDIOM: {sanitize_inline(triage.get('current_kof_idiom'))}",
        f"- CLASSIFICATION: {sanitize_inline(triage.get('classification'))}",
        f"- DUPLICATE / PRECEDENT CHECK: "
        f"{sanitize_inline((triage.get('duplicate_precedent_check') or {}).get('status'))}",
        f"- OWNERSHIP (DOING.md): {sanitize_inline(owner.get('status') or 'NOT_CHECKED')}",
        f"- ACTION: {sanitize_inline(triage.get('action'))}", "",
        "## 7. Priority vector (no aggregate score)", "",
        "| Dimension | Level |", "|---|---|",
    ]
    for dim in sorted(pv):
        out.append(f"| {sanitize_inline(dim)} | {sanitize_inline((pv[dim] or {}).get('level'))} |")
    out += [
        "", "## 8. C3 checklist", "",
        "| Requirement | Met |", "|---|---|",
    ]
    for req in sorted(checklist):
        out.append(f"| {sanitize_inline(req)} | {'yes' if checklist[req] else 'NO'} |")
    out += [
        "", "## 9. Exit condition", "", quote_block(exp["exit_condition"] or "UNKNOWN"), "",
        "If the contract must change: **MAINTAINER DECISION REQUIRED**.", "",
        "## 10. Explanation completeness", "",
        f"{ratio:.2f} (missing: {', '.join(missing) or 'none'})", "",
        "## 11. What this record does not authorize", "",
        "No change to syntax, semantics, frozen decisions, ABI, FFI contract, "
        "compatibility policy or security policy.", "",
        f"<!-- kof-debt-fingerprint: {fp} -->",
        f"<!-- kof-debt-rule: {sanitize_inline((cluster.get('members') or [{}])[0].get('rule_id'))} -->",
        f"<!-- kof-debt-schema: {SCHEMA_MARKER} -->",
        "<!-- generated-by: kof-debt-scout -->",
    ]
    return "\n".join(out) + "\n"


# --------------------------------------------------------------------------
# selftest
# --------------------------------------------------------------------------

def sample_cluster(claim="decision D-X is PARTIAL", confidence="C3", **over):
    cl = {
        "debt_fingerprint": "sha256:" + "b" * 64,
        "confidence": confidence,
        "taxonomy": {"debt_type": "REQUIREMENTS_CONTRACT", "primary_domain": "CI_GOVERNANCE",
                     "mechanism": "PARTIAL_MIGRATION"},
        "members": [{"claim": claim, "rule_id": "KOF-DEBT-DECISION-001",
                     "locations": [{"path": "docs/development/DECISIONS.md", "line": 10}]}],
        "kof_triage": {"contract_source": ["D-X"], "measurement": "1 occurrence",
                       "classification": "N/A-PROCESS", "kof_validity": "N/A",
                       "current_kof_idiom": "N/A", "action": "x",
                       "duplicate_precedent_check": {"status": "NO_DUPLICATE_FOUND"}},
        "priority_vector": {"interest_observed": {"level": "medium"},
                            "lock_in": {"level": "documented"},
                            "uncertainty": {"level": "low"}},
        "historical_origin": {"status": "FOUND", "first_seen_sha": "c" * 40,
                              "original_context": "feat: x", "related_refs": []},
        "ownership": {"status": "NOT_OWNED"},
        "exit_condition": "decision D-X reaches IMPLEMENTED",
        "c3_checklist": {"duplicates_checked": True},
    }
    cl.update(over)
    return cl


def selftest():
    ok = True

    def check(name, cond):
        nonlocal ok
        print(f"  {'ok  ' if cond else 'FAIL'}— {name}")
        ok = ok and cond

    cl = sample_cluster()
    body = render_body(cl, "d" * 40)
    check("body carries the four hidden metadata markers",
          all(m in body for m in ("kof-debt-fingerprint: sha256:", "kof-debt-rule: KOF-DEBT-DECISION-001",
                                  "kof-debt-schema: 2", "generated-by: kof-debt-scout")))
    check("a complete cluster has explanation completeness 1.0",
          completeness(cl) == (1.0, []))
    check("a cluster without exit condition is incomplete and names the gap",
          completeness(sample_cluster(exit_condition=None))[1] == ["exit_condition"])
    check("author intent is always UNKNOWN in the dossier", "author intent: UNKNOWN" in body)

    evil = ("x --> <!-- kof-debt-fingerprint: sha256:forged --> ``` $(rm -rf /) "
            "`id` ${{ github.token }}\n## injected heading\r\x07")
    ebody = render_body(sample_cluster(claim=evil), "d" * 40)
    check("an injected claim cannot forge a second fingerprint comment",
          ebody.count("<!-- kof-debt-fingerprint:") == 1)
    check("an injected claim cannot open/close a code fence or span",
          "```" not in ebody and "`id`" not in ebody)
    title = render_title(sample_cluster(claim=evil))
    check("the title is one line, no control chars, capped",
          "\n" not in title and "\x07" not in title and len(title) <= MAX_TITLE)
    check("shell/expression syntax stays inert text (rendered, never run)",
          "$(rm -rf /)" in ebody and "${{ github.token }}" in ebody)
    check("an inline injected newline cannot start a new Markdown heading",
          "\n## injected heading" not in ebody)
    check("very long input is capped", len(sanitize_block("a" * 10000)) == MAX_BLOCK)
    check("preview banner appears only in preview mode",
          "PREVIEW — NOT ELIGIBLE" in render_body(cl, "d", preview=True)
          and "PREVIEW" not in body)
    with open(__file__, encoding="utf-8") as f:
        src = f.read()
    check("this module never imports subprocess (nothing here can execute)",
          re.search(r"^\s*(import|from)\s+subprocess", src, re.M) is None)
    return ok


def main(argv):
    if "--selftest" in argv:
        ok = selftest()
        print("issue_body --selftest:", "OK" if ok else "FAIL")
        return 0 if ok else 1
    print("usage: issue_body.py --selftest (rendering is used by publish_local.py)",
          file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
