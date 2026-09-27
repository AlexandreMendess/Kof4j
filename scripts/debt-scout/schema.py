#!/usr/bin/env python3
"""schema.py — Candidate schema validation for Wave 1
(`docs/development/technical-debt/DEBT_SCOUT_CONTRACT.md`).

The full V2 source schema (§59) has ~30 fields, several of which (the
priority vector, `principal`/`interest`/`lock_in`, `related_issues`,
`related_prs`) only make sense once a candidate reaches `C2`/`C3` and a
publisher exists to read them. Requiring a Wave-1 detector to fill those
in would mean stuffing placeholder data into fields nothing consumes yet
— exactly the stub the quality gate forbids (`AGENTS.md` Q7). This
module therefore validates the REQUIRED subset Wave 1 actually produces
and consumes, and accepts (but does not require) the rest of the V2
fields as pass-through for forward compatibility.

CLI:
  schema.py <file.json>   -> validates one candidate JSON file
  schema.py --selftest
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import taxonomy  # noqa: E402

for _stream in (sys.stdout, sys.stderr):
    if hasattr(_stream, "reconfigure"):
        _stream.reconfigure(encoding="utf-8", errors="replace")

SUPPORTED_SCHEMA = 2

_REQUIRED_STR_FIELDS = ["candidate_id", "rule_id", "rule_version", "claim"]


def validate_candidate(c):
    """Returns a list of error strings; empty list == valid."""
    errors = []
    if not isinstance(c, dict):
        return ["candidate is not a JSON object"]

    schema = c.get("schema")
    if schema != SUPPORTED_SCHEMA:
        errors.append(f"schema must be {SUPPORTED_SCHEMA}, got {schema!r}")

    for field in _REQUIRED_STR_FIELDS:
        v = c.get(field)
        if not isinstance(v, str) or not v.strip():
            errors.append(f"{field}: required non-empty string, got {v!r}")

    confidence = c.get("confidence")
    if confidence not in taxonomy.CONFIDENCE_LEVELS:
        errors.append(f"confidence: {confidence!r} not in {sorted(taxonomy.CONFIDENCE_LEVELS)}")

    tax = c.get("taxonomy")
    if not isinstance(tax, dict):
        errors.append("taxonomy: required object")
    else:
        dt = tax.get("debt_type")
        if dt not in taxonomy.DEBT_TYPES:
            errors.append(f"taxonomy.debt_type: {dt!r} not in DEBT_TYPES")
        pd = tax.get("primary_domain")
        if pd not in taxonomy.DOMAINS:
            errors.append(f"taxonomy.primary_domain: {pd!r} not in DOMAINS")
        domains = tax.get("domains")
        if not isinstance(domains, list) or not domains:
            errors.append("taxonomy.domains: required non-empty list")
        else:
            bad = [d for d in domains if d not in taxonomy.DOMAINS]
            if bad:
                errors.append(f"taxonomy.domains: unknown domain(s) {bad}")
            if pd in taxonomy.DOMAINS and pd not in domains:
                errors.append("taxonomy.primary_domain must also appear in taxonomy.domains")
        mech = tax.get("mechanism")
        if mech not in taxonomy.MECHANISMS:
            errors.append(f"taxonomy.mechanism: {mech!r} not in MECHANISMS")

    pub = c.get("publication")
    if not isinstance(pub, dict):
        errors.append("publication: required object")
    else:
        if not isinstance(pub.get("public_safe"), bool):
            errors.append("publication.public_safe: required bool")
        if not isinstance(pub.get("eligible"), bool):
            errors.append("publication.eligible: required bool")
        if not isinstance(pub.get("reason"), str) or not pub.get("reason", "").strip():
            errors.append("publication.reason: required non-empty string")
        # Wave 1 hard invariant: nothing produced by this landing is ever
        # eligible for auto-publish — there is no publisher to read it yet.
        if pub.get("eligible") is True:
            errors.append(
                "publication.eligible=true is rejected in this Wave 1 "
                "landing — no publisher exists (contract §7/§12)"
            )

    lineage = c.get("lineage")
    if not isinstance(lineage, dict):
        errors.append("lineage: required object")
    else:
        if not isinstance(lineage.get("analyzed_sha"), str) or not lineage.get("analyzed_sha", "").strip():
            errors.append("lineage.analyzed_sha: required non-empty string")
        if lineage.get("model_used") is not False:
            errors.append(
                "lineage.model_used must be false — Wave 1 has zero LLM in "
                "the loop (contract §9)"
            )

    evidence = c.get("evidence")
    if not isinstance(evidence, list):
        errors.append("evidence: required list (may be empty for C0)")

    return errors


def is_valid(c):
    return validate_candidate(c) == []


# --------------------------------------------------------------------------
# selftest
# --------------------------------------------------------------------------

def _valid_c0():
    return {
        "schema": 2,
        "candidate_id": "abc123",
        "rule_id": "KOF-DEBT-TEST-001",
        "rule_version": "1.0.0",
        "claim": "a TODO with no surrounding liability evidence",
        "confidence": "C0",
        "taxonomy": {
            "debt_type": "CODE",
            "primary_domain": "CLI_TOOLING",
            "domains": ["CLI_TOOLING"],
            "mechanism": "WORKAROUND",
        },
        "publication": {"public_safe": True, "eligible": False,
                         "reason": "C0 never publishes"},
        "lineage": {"analyzed_sha": "deadbeef", "model_used": False,
                     "scanner_version": "0.1.0"},
        "evidence": [],
    }


def selftest():
    ok = True

    def check(name, cond):
        nonlocal ok
        status = "ok  " if cond else "FAIL"
        print(f"  {status}— {name}")
        ok = ok and cond

    check("a well-formed C0 candidate validates", is_valid(_valid_c0()))

    bad_schema = _valid_c0()
    bad_schema["schema"] = 1
    check("wrong schema version is rejected", not is_valid(bad_schema))

    missing_claim = _valid_c0()
    del missing_claim["claim"]
    check("missing claim is rejected", not is_valid(missing_claim))

    empty_claim = _valid_c0()
    empty_claim["claim"] = "   "
    check("blank claim is rejected", not is_valid(empty_claim))

    bad_confidence = _valid_c0()
    bad_confidence["confidence"] = "C9"
    check("unknown confidence level is rejected", not is_valid(bad_confidence))

    bad_debt_type = _valid_c0()
    bad_debt_type["taxonomy"]["debt_type"] = "NOT_A_TYPE"
    check("unknown debt_type is rejected", not is_valid(bad_debt_type))

    bad_domain = _valid_c0()
    bad_domain["taxonomy"]["primary_domain"] = "MARS"
    check("unknown primary_domain is rejected", not is_valid(bad_domain))

    inconsistent = _valid_c0()
    inconsistent["taxonomy"]["domains"] = ["JVM"]  # primary_domain is CLI_TOOLING
    check("primary_domain absent from domains list is rejected",
          not is_valid(inconsistent))

    eligible_true = _valid_c0()
    eligible_true["publication"]["eligible"] = True
    check("publication.eligible=true is rejected in Wave 1",
          not is_valid(eligible_true))

    model_used = _valid_c0()
    model_used["lineage"]["model_used"] = True
    check("lineage.model_used=true is rejected in Wave 1",
          not is_valid(model_used))

    not_a_dict = "not a candidate"
    check("a non-object candidate is rejected", not is_valid(not_a_dict))

    check("forward-compatible extra fields do not break validation",
          is_valid({**_valid_c0(), "priority_vector": {}, "related_issues": []}))

    return ok


def main(argv):
    if "--selftest" in argv:
        ok = selftest()
        print("schema --selftest:", "OK" if ok else "FAIL")
        return 0 if ok else 1
    if len(argv) == 1:
        with open(argv[0], "r", encoding="utf-8") as f:
            candidate = json.load(f)
        errors = validate_candidate(candidate)
        if errors:
            for e in errors:
                print(f"schema: {e}", file=sys.stderr)
            return 1
        print("schema: OK")
        return 0
    print(__doc__, file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
