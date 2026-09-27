#!/usr/bin/env python3
"""config.py — loads/validates `.debt-scout.yml` for the KOF Technical Debt
Scout (Wave 1, `docs/development/technical-debt/DEBT_SCOUT_CONTRACT.md`).

Stdlib-only indentation parser, same precedent as
`scripts/check_workflow_permissions.py`: this repo's CI never installs
PyYAML, so depending on it silently would be exactly the kind of
undeclared-toolchain debt this very tool exists to catch. The subset
supported is deliberately small — nested mappings via 2-space indent,
scalar values (bool/int/float/string), `#` comments, no lists, no
anchors/aliases/flow style.

Hard invariants (§7 of the contract — not policy lookup, a code gate):
  - `mode` MUST be `shadow`. No canary/trusted implementation exists yet
    in this Wave 1 landing, regardless of what the file says.
  - `model.enabled` MUST be `false`. Wave 1 has zero LLM in the loop.
A file that sets either differently is REJECTED — advancing past Wave 1
needs its own DECISIONS.md entry (contract §7/§12) and a code change
here, never a config flip.

CLI:
  config.py [--path FILE]   -> prints the resolved, validated config as
                                JSON to stdout. Exit 0 valid, 1 invalid.
  config.py --selftest      -> proves the gate bites. Exit 0/1.
"""
import json
import os
import sys

for _stream in (sys.stdout, sys.stderr):
    if hasattr(_stream, "reconfigure"):
        _stream.reconfigure(encoding="utf-8", errors="replace")

DEFAULT_PATH = os.environ.get("DEBT_SCOUT_CONFIG", ".debt-scout.yml")

SUPPORTED_SCHEMA = 2
BOOL_RULE_KEYS = {
    "satd", "partial_decisions", "contract_drift", "cross_target",
    "accidental_contract", "skipped_tests",
}
ENUM_RULE_KEYS = {
    "architecture": {"shadow", "on", "off"},
    "performance": {"measured-only", "off"},
    "security": {"private-route"},
}


class ConfigError(ValueError):
    """A `.debt-scout.yml` that failed parsing or a contract invariant."""


def _parse_scalar(text):
    t = text.strip()
    if t in ("true", "True"):
        return True
    if t in ("false", "False"):
        return False
    try:
        return int(t)
    except ValueError:
        pass
    try:
        return float(t)
    except ValueError:
        pass
    if len(t) >= 2 and t[0] == t[-1] and t[0] in "\"'":
        return t[1:-1]
    return t


def _strip_comment(line):
    # No quoted strings carry '#' in this file's actual values (versions,
    # enum words) — a plain search is enough for this small subset.
    i = line.find("#")
    return line[:i] if i >= 0 else line


def parse_yaml_subset(text):
    """Minimal nested-mapping YAML subset -> nested dict. Raises
    ConfigError on anything outside the supported subset (lists, flow
    style, tabs, inconsistent indentation)."""
    root = {}
    # stack of (indent, dict)
    stack = [(-1, root)]
    for lineno, raw in enumerate(text.splitlines(), start=1):
        if "\t" in raw:
            raise ConfigError(f"line {lineno}: tabs are not supported")
        line = _strip_comment(raw).rstrip()
        if not line.strip():
            continue
        indent = len(line) - len(line.lstrip(" "))
        content = line.strip()
        if ":" not in content:
            raise ConfigError(f"line {lineno}: expected 'key: value' or 'key:'")
        key, _, value = content.partition(":")
        key = key.strip()
        value = value.strip()
        while stack and indent <= stack[-1][0]:
            stack.pop()
        if not stack:
            raise ConfigError(f"line {lineno}: bad indentation")
        parent = stack[-1][1]
        if value == "":
            child = {}
            parent[key] = child
            stack.append((indent, child))
        else:
            parent[key] = _parse_scalar(value)
    return root


def load_raw(path=DEFAULT_PATH):
    with open(path, "r", encoding="utf-8") as f:
        return parse_yaml_subset(f.read())


def _require(cfg, dotted_path, expected_type=None):
    node = cfg
    parts = dotted_path.split(".")
    for p in parts:
        if not isinstance(node, dict) or p not in node:
            raise ConfigError(f"missing required key: {dotted_path}")
        node = node[p]
    if expected_type is not None and not isinstance(node, expected_type):
        raise ConfigError(
            f"{dotted_path}: expected {expected_type.__name__}, got {type(node).__name__}"
        )
    return node


def validate(cfg):
    """Raises ConfigError on the first violation. Returns cfg unchanged
    (caller keeps the parsed dict) so validate() composes with load()."""
    schema = _require(cfg, "schema", int)
    if schema != SUPPORTED_SCHEMA:
        raise ConfigError(f"schema {schema} unsupported (only {SUPPORTED_SCHEMA})")

    mode = _require(cfg, "mode", str)
    if mode != "shadow":
        raise ConfigError(
            f"mode={mode!r} rejected — Wave 1 implements shadow only "
            "(contract §7); advancing needs a DECISIONS.md phase entry "
            "AND a code change, never a config flip"
        )

    model_enabled = _require(cfg, "model.enabled", bool)
    if model_enabled is not False:
        raise ConfigError(
            "model.enabled=true rejected — Wave 1 has zero LLM in the "
            "loop (contract §9)"
        )

    budget = _require(cfg, "budget", dict)
    per_run = _require(cfg, "budget.issues_per_run", int)
    per_day = _require(cfg, "budget.issues_per_day", int)
    per_week = _require(cfg, "budget.issues_per_week", int)
    for name, v in (("issues_per_run", per_run), ("issues_per_day", per_day),
                     ("issues_per_week", per_week)):
        if v < 0:
            raise ConfigError(f"budget.{name} must be >= 0, got {v}")
    if per_run > per_day:
        raise ConfigError("budget.issues_per_run must be <= issues_per_day")
    if per_day > per_week:
        raise ConfigError("budget.issues_per_day must be <= issues_per_week")

    auto_min = _require(cfg, "confidence.auto_issue_min", str)
    if auto_min != "C3":
        raise ConfigError(
            f"confidence.auto_issue_min={auto_min!r} unsupported (only C3 — "
            "contract §5/§7)"
        )
    inbox_min = _require(cfg, "confidence.inbox_min", str)
    if inbox_min not in ("C1", "C2"):
        raise ConfigError(
            f"confidence.inbox_min={inbox_min!r} unsupported (only C1 or C2)"
        )

    precision = _require(cfg, "trust.min_effective_precision", (int, float))
    if not (0.0 <= float(precision) <= 1.0):
        raise ConfigError("trust.min_effective_precision must be in [0, 1]")
    reviewed = _require(cfg, "trust.min_reviewed_findings", int)
    if reviewed < 0:
        raise ConfigError("trust.min_reviewed_findings must be >= 0")

    _require(cfg, "sarif.enabled", bool)

    rules = _require(cfg, "rules", dict)
    for key, value in rules.items():
        if key in BOOL_RULE_KEYS:
            if not isinstance(value, bool):
                raise ConfigError(f"rules.{key} must be a bool, got {value!r}")
        elif key in ENUM_RULE_KEYS:
            allowed = ENUM_RULE_KEYS[key]
            if value not in allowed:
                raise ConfigError(
                    f"rules.{key}={value!r} not in {sorted(allowed)}"
                )
        else:
            raise ConfigError(f"rules.{key}: unknown rule key")

    return cfg


def load_config(path=DEFAULT_PATH):
    """load_raw + validate. This is the only entry point other
    scripts/debt-scout modules should call."""
    return validate(load_raw(path))


# --------------------------------------------------------------------------
# selftest
# --------------------------------------------------------------------------

_VALID_MIN = """
schema: 2
mode: shadow
budget:
  issues_per_run: 2
  issues_per_day: 2
  issues_per_week: 8
confidence:
  auto_issue_min: C3
  inbox_min: C2
trust:
  min_reviewed_findings: 30
  min_effective_precision: 0.90
sarif:
  enabled: false
model:
  enabled: false
rules:
  satd: true
  architecture: shadow
"""


def _fixture(overrides_text):
    return _VALID_MIN + overrides_text


def _expect_ok(name, text):
    try:
        validate(parse_yaml_subset(text))
        print(f"  ok  — {name}")
        return True
    except ConfigError as e:
        print(f"  FAIL — {name}: expected ok, got ConfigError: {e}")
        return False


def _expect_fail(name, text, needle=None):
    try:
        validate(parse_yaml_subset(text))
        print(f"  FAIL — {name}: expected ConfigError, got ok")
        return False
    except ConfigError as e:
        if needle and needle not in str(e):
            print(f"  FAIL — {name}: error {e!r} does not mention {needle!r}")
            return False
        print(f"  ok  — {name}")
        return True


def selftest():
    ok = True
    ok &= _expect_ok("minimal valid config parses and validates", _VALID_MIN)
    ok &= _expect_fail("mode=canary is rejected", _fixture("").replace(
        "mode: shadow", "mode: canary"), "mode")
    ok &= _expect_fail("mode=trusted is rejected", _fixture("").replace(
        "mode: shadow", "mode: trusted"), "mode")
    ok &= _expect_fail("model.enabled=true is rejected", _fixture("").replace(
        "model:\n  enabled: false\n", "model:\n  enabled: true\n"), "model.enabled")
    ok &= _expect_fail("schema=1 is rejected", _fixture("").replace(
        "schema: 2", "schema: 1"), "schema")
    ok &= _expect_fail("negative budget is rejected", _fixture("").replace(
        "issues_per_run: 2", "issues_per_run: -1"), "budget")
    ok &= _expect_fail("budget.issues_per_run > issues_per_day is rejected",
                        _fixture("").replace("issues_per_run: 2", "issues_per_run: 99"),
                        "budget")
    ok &= _expect_fail("auto_issue_min != C3 is rejected", _fixture("").replace(
        "auto_issue_min: C3", "auto_issue_min: C2"), "auto_issue_min")
    ok &= _expect_fail("inbox_min=C0 is rejected", _fixture("").replace(
        "inbox_min: C2", "inbox_min: C0"), "inbox_min")
    ok &= _expect_fail("min_effective_precision out of range is rejected",
                        _fixture("").replace("min_effective_precision: 0.90",
                                              "min_effective_precision: 1.5"),
                        "min_effective_precision")
    ok &= _expect_fail("unknown rule key is rejected",
                        _fixture("  unknown_rule: true\n"), "unknown")
    ok &= _expect_fail("bool rule key given an enum value is rejected",
                        _fixture("").replace("satd: true", "satd: shadow"),
                        "rules.satd")
    ok &= _expect_fail("enum rule key given a bad value is rejected",
                        _fixture("").replace("architecture: shadow",
                                              "architecture: aggressive"),
                        "rules.architecture")
    ok &= _expect_fail("missing required key is rejected",
                        _VALID_MIN.replace("schema: 2\n", ""), "schema")
    ok &= _expect_fail("tabs are rejected", "schema: 2\n\tmode: shadow\n", "tab")
    real_path = os.path.join(os.path.dirname(__file__), "..", "..", ".debt-scout.yml")
    if os.path.exists(real_path):
        try:
            load_config(real_path)
            print("  ok  — real repo .debt-scout.yml loads and validates")
        except ConfigError as e:
            print(f"  FAIL — real repo .debt-scout.yml: {e}")
            ok = False
    else:
        print("  FAIL — real repo .debt-scout.yml not found at", real_path)
        ok = False
    return ok


def main(argv):
    if "--selftest" in argv:
        ok = selftest()
        print("config --selftest:", "OK" if ok else "FAIL")
        return 0 if ok else 1
    path = DEFAULT_PATH
    if "--path" in argv:
        path = argv[argv.index("--path") + 1]
    try:
        cfg = load_config(path)
    except (ConfigError, OSError) as e:
        print(f"config: {e}", file=sys.stderr)
        return 1
    print(json.dumps(cfg, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
