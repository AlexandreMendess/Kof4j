#!/usr/bin/env python3
"""codeql-sarif-filter.py — derruba resultados enraizados em **/src/test/** do
SARIF do CodeQL (#563).

Motivo (medido 20/09, #563): `paths-ignore` no config do CodeQL NAO suprime
queries importadas via `- uses: security-and-quality`/`security-extended` —
alertas #941/#942 nasceram em src/test com o config ativo desde 14/09
(804a03ea). O mecanismo que vale independentemente de versao do
codeql-action/CLI e de importacao de suite e o pos-filtro do SARIF antes do
upload. O codigo de producao (src/main) permanece 100% varrido.

uso: codeql-sarif-filter.py <arquivo.sarif|diretorio> [--selftest]

Reescreve no lugar e imprime `before N after M dropped K (src/test)` por
arquivo. Sem entrada SARIF -> rc=2 (R6: nunca silencia).
"""
import glob
import json
import os
import sys

TEST_MARKER = "/src/test/"


def is_test_location(result):
    for loc in result.get("locations", []):
        art = ((loc.get("physicalLocation") or {}).get("artifactLocation") or {})
        uri = (art.get("uri") or "").replace("\\", "/")
        if TEST_MARKER in uri or uri.startswith("src/test/"):
            return True
    return False


def filter_sarif(path):
    with open(path) as fh:
        sarif = json.load(fh)
    total = dropped = 0
    for run in sarif.get("runs", []):
        results = run.get("results", [])
        kept = [r for r in results if not is_test_location(r)]
        total += len(results)
        dropped += len(results) - len(kept)
        run["results"] = kept
    with open(path, "w") as fh:
        json.dump(sarif, fh)
    print(f"{os.path.basename(path)}: before {total} after {total - dropped} "
          f"dropped {dropped} (src/test)")
    return total, dropped


def selftest():
    fixture = {"version": "2.1.0", "runs": [{"tool": {"driver": {"name": "CodeQL"}},
        "results": [
        {"ruleId": "java/relative-path-command", "message": {"text": "t"},
         "locations": [{"physicalLocation": {"artifactLocation":
             {"uri": "kof-compiler/src/test/java/dev/kof/compiler/X.java"}}}]},
        {"ruleId": "java/better", "message": {"text": "m"},
         "locations": [{"physicalLocation": {"artifactLocation":
             {"uri": "kof-runtime/src/main/java/Y.java"}}}]},
        {"ruleId": "java/concatenated-command-line", "message": {"text": "c"},
         "locations": [{"physicalLocation": {"artifactLocation":
             {"uri": "src/test/Z.java"}}}]},
        {"ruleId": "java/backslash", "message": {"text": "b"},
         "locations": [{"physicalLocation": {"artifactLocation":
             {"uri": "kof-cli\\src\\test\\W.java"}}}]},
        {"ruleId": "java/noloc", "message": {"text": "n"}, "locations": []},
    ]}]}
    import tempfile
    with tempfile.TemporaryDirectory() as d:
        p = os.path.join(d, "r.sarif")
        with open(p, "w") as fh:
            json.dump(fixture, fh)
        total, dropped = filter_sarif(p)
        assert total == 5 and dropped == 3, f"contagem errada: {total}/{dropped}"
        with open(p) as fh:
            ids = [r["ruleId"] for r in json.load(fh)["runs"][0]["results"]]
        assert ids == ["java/better", "java/noloc"], ids
    print("SELFTEST OK: 3 faces src/test (2 URI + 1 backslash) cai, "
          "main e noloc ficam")


def main():
    args = sys.argv[1:]
    if not args or "--selftest" in args:
        selftest()
        return 0
    target = args[0]
    if target.endswith(".sarif"):
        paths = [target]
    else:
        paths = sorted(glob.glob(os.path.join(target, "**", "*.sarif"), recursive=True))
    if not paths:
        print(f"ERROR: nenhum SARIF em {target}", file=sys.stderr)
        return 2
    for p in paths:
        filter_sarif(p)
    return 0


if __name__ == "__main__":
    sys.exit(main())
