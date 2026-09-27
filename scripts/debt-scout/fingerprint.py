#!/usr/bin/env python3
"""fingerprint.py — the two stable fingerprints the KOF Technical Debt
Scout uses for deduplication (`docs/development/technical-debt/
DEBT_SCOUT_CONTRACT.md` §6).

  finding_fingerprint(rule_id, symbol, claim)
      identity of ONE signal at ONE location.
  debt_fingerprint(debt_type, primary_domain, mechanism,
                    governing_contract_id, root_boundary, liability)
      identity of the underlying debt CONCEPT.

Both are plain functions over caller-supplied strings — by construction
there is no line-number, commit-SHA, or timestamp parameter to pass, so a
detector cannot accidentally destabilize a fingerprint by including one
(contract §6: "do not include: line number, commit SHA, timestamp,
runner path"). Whitespace is normalized (trim + collapse) so cosmetic
reformatting of a symbol/claim string does not change the identity; case
is preserved because KOF symbols are case-sensitive.

Fields are joined with U+001F (ASCII unit separator) before hashing so
that ("ab", "c") and ("a", "bc") never collide — a plain string
concatenation would not have that property.

CLI:
  fingerprint.py finding <rule_id> <symbol> <claim>
  fingerprint.py debt <debt_type> <primary_domain> <mechanism> \
                       <governing_contract_id> <root_boundary> <liability>
  fingerprint.py --selftest
"""
import hashlib
import re
import sys

for _stream in (sys.stdout, sys.stderr):
    if hasattr(_stream, "reconfigure"):
        _stream.reconfigure(encoding="utf-8", errors="replace")

_FIELD_SEP = "\x1f"
_WS = re.compile(r"\s+")


def _normalize(s):
    return _WS.sub(" ", s.strip())


def _sha256_hex(*fields):
    normalized = [_normalize(f) for f in fields]
    joined = _FIELD_SEP.join(normalized)
    return hashlib.sha256(joined.encode("utf-8")).hexdigest()


def finding_fingerprint(rule_id, symbol, claim):
    return "sha256:" + _sha256_hex(rule_id, symbol, claim)


def debt_fingerprint(debt_type, primary_domain, mechanism,
                      governing_contract_id, root_boundary, liability):
    return "sha256:" + _sha256_hex(
        debt_type, primary_domain, mechanism, governing_contract_id,
        root_boundary, liability,
    )


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

    fp1 = finding_fingerprint("KOF-DEBT-TEST-001", "KofFormatter#escapeLiteral",
                               "skip has no exit condition")
    fp2 = finding_fingerprint("KOF-DEBT-TEST-001", "KofFormatter#escapeLiteral",
                               "skip has no exit condition")
    check("finding fingerprint is idempotent for identical inputs", fp1 == fp2)
    check("finding fingerprint has the sha256: prefix", fp1.startswith("sha256:"))
    check("finding fingerprint hex part is 64 chars", len(fp1) == len("sha256:") + 64)

    fp3 = finding_fingerprint("KOF-DEBT-TEST-001", "KofFormatter#escapeLiteral",
                               "  skip   has no exit   condition  ")
    check("whitespace-only differences in claim do not change the fingerprint",
          fp1 == fp3)

    fp4 = finding_fingerprint("KOF-DEBT-TEST-002", "KofFormatter#escapeLiteral",
                               "skip has no exit condition")
    check("changing rule_id changes the fingerprint", fp1 != fp4)

    fp5 = finding_fingerprint("KOF-DEBT-TEST-001", "KofFormatter#other",
                               "skip has no exit condition")
    check("changing symbol changes the fingerprint", fp1 != fp5)

    fp6 = finding_fingerprint("KOF-DEBT-TEST-001", "KofFormatter#escapeLiteral",
                               "skip HAS a documented exit condition")
    check("changing claim changes the fingerprint", fp1 != fp6)

    # field-boundary disambiguation: ("ab","c") must differ from ("a","bc")
    left = finding_fingerprint("ab", "c", "x")
    right = finding_fingerprint("a", "bc", "x")
    check("field-boundary disambiguation (no plain concatenation collision)",
          left != right)

    db1 = debt_fingerprint("TEST", "CI_GOVERNANCE", "DISABLED_GATE",
                            "D-ASM-GATE", "scripts/setup-cross-toolchain.sh",
                            "skip never re-evaluated after toolchain landed")
    db2 = debt_fingerprint("TEST", "CI_GOVERNANCE", "DISABLED_GATE",
                            "D-ASM-GATE", "scripts/setup-cross-toolchain.sh",
                            "skip never re-evaluated after toolchain landed")
    check("debt fingerprint is idempotent for identical inputs", db1 == db2)
    check("debt and finding fingerprints do not collide by construction",
          db1 != fp1)

    # simulate "line moved" by keeping every semantic field identical —
    # the functions accept no line-number parameter, so this is structural,
    # not just a lucky test outcome.
    db3 = debt_fingerprint("TEST", "CI_GOVERNANCE", "DISABLED_GATE",
                            "D-ASM-GATE", "scripts/setup-cross-toolchain.sh",
                            "skip never re-evaluated after toolchain landed")
    check("debt fingerprint stable when the caller passes no line reference "
          "at all (no such parameter exists)", db1 == db3)

    db4 = debt_fingerprint("TEST", "CI_GOVERNANCE", "OBSOLETE_ASSUMPTION",
                            "D-ASM-GATE", "scripts/setup-cross-toolchain.sh",
                            "skip never re-evaluated after toolchain landed")
    check("changing mechanism changes the debt fingerprint", db1 != db4)

    return ok


def main(argv):
    if "--selftest" in argv:
        ok = selftest()
        print("fingerprint --selftest:", "OK" if ok else "FAIL")
        return 0 if ok else 1
    if len(argv) == 4 and argv[0] == "finding":
        print(finding_fingerprint(argv[1], argv[2], argv[3]))
        return 0
    if len(argv) == 7 and argv[0] == "debt":
        print(debt_fingerprint(*argv[1:]))
        return 0
    print(__doc__, file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
