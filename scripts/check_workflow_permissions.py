#!/usr/bin/env python3
"""check_workflow_permissions.py — gate de least privilege dos workflows
(D-ARTIFACT-TRUST §5, hardening P2: "job-level least privilege, matar
`contents: write` no nivel do workflow").

Regras (por arquivo em .github/workflows/, fora as isencoes):
  R1  o workflow declara `permissions:` no topo — nunca herda o default do repo
      (setting administrativo que pode virar `write` sem nenhum diff aqui).
  R2  o topo NAO concede `write` (nem `write-all`): escrita e escopo de JOB.
  R3  todo `write` de job precisa de uma linha `allow` (com o motivo) no ledger;
      `write-all` em job nunca passa. Um `write` novo sem justificativa falha.
  R4  ledger sem drift: `allow` para arquivo/job/escopo que nao e mais `write`
      (ou nao existe) e `exempt` para arquivo que ja cumpre tudo FALHAM — a
      justificativa nao sobrevive ao codigo que ela justificava.

Ledger scripts/workflow-permissions.txt (uma linha por decisao):
  exempt <arquivo.yml> <motivo>
  allow  <arquivo.yml> <job> <escopo> <motivo>

Parser: so stdlib, por indentacao (topo = indent 0, job = indent 2, permissions
do job = indent 4). Nao interpreta YAML alem disso — blocos `run: |` ficam mais
fundo que qualquer chave lida.  Exit 0 limpo · 1 violacao/drift.
--selftest prova que o gate morde.
"""
import os
import re
import sys
import tempfile

for _stream in (sys.stdout, sys.stderr):
    if hasattr(_stream, "reconfigure"):
        _stream.reconfigure(encoding="utf-8", errors="replace")

WF_DIR = os.environ.get("PERMS_WORKFLOWS_DIR", ".github/workflows")
LEDGER = os.environ.get("PERMS_LEDGER", "scripts/workflow-permissions.txt")

KV = re.compile(r"^([A-Za-z-]+):\s*(read|write|none)\s*$")


def strip_comment(text):
    # so nas linhas que lemos (chaves simples de permissions): ' #' inicia comentario
    i = text.find(" #")
    return (text[:i] if i >= 0 else text).rstrip()


def parse_value(value):
    """Valor inline de `permissions:` -> dict|str|None (None = bloco a seguir)."""
    v = value.strip()
    if v == "":
        return None
    if v == "{}":
        return {}
    return v  # read-all / write-all (ou lixo — cai como nao-dict)


def parse(text):
    """-> (wf_perms, jobs). wf_perms: None (ausente) | dict | str.
    jobs: {nome: None (sem bloco) | dict | str}."""
    wf, jobs = None, {}
    section, job = None, None
    owner = None  # ('wf',) | ('job', nome) — dono do bloco de escopos em leitura
    owner_indent = -1
    for raw in text.splitlines():
        if not raw.strip() or raw.lstrip().startswith("#"):
            continue
        indent = len(raw) - len(raw.lstrip())
        line = raw.strip()
        if owner is not None and indent <= owner_indent:
            owner = None
        if owner is not None:
            m = KV.match(strip_comment(line))
            if m:
                target = wf if owner[0] == "wf" else jobs[owner[1]]
                if isinstance(target, dict):
                    target[m.group(1)] = m.group(2)
            continue
        if indent == 0:
            key = line.split(":", 1)[0]
            section, job = key, None
            if key == "permissions":
                val = parse_value(strip_comment(line.split(":", 1)[1]))
                if val is None:
                    wf, owner, owner_indent = {}, ("wf",), 0
                else:
                    wf = val
        elif section == "jobs" and indent == 2 and line.endswith(":"):
            job = line[:-1].strip("'\"")
            jobs.setdefault(job, None)
        elif section == "jobs" and job is not None and indent == 4 and line.startswith("permissions:"):
            val = parse_value(strip_comment(line.split(":", 1)[1]))
            if val is None:
                jobs[job], owner, owner_indent = {}, ("job", job), 4
            else:
                jobs[job] = val
    return wf, jobs


def load_ledger(path):
    exempt, allow = {}, {}
    if not os.path.isfile(path):
        return exempt, allow
    with open(path, encoding="utf-8") as fh:
        for n, raw in enumerate(fh, 1):
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            parts = line.split(None, 4)
            if parts[0] == "exempt" and len(parts) >= 3:
                exempt[parts[1]] = " ".join(parts[2:])
            elif parts[0] == "allow" and len(parts) >= 5:
                allow[(parts[1], parts[2], parts[3])] = parts[4]
            else:
                exempt["<ledger-invalido>"] = f"{path}:{n}: linha malformada (allow exige arquivo job escopo motivo)"
    return exempt, allow


def check(wf_dir, ledger):
    errors, notes = [], []
    exempt, allow = load_ledger(ledger)
    if "<ledger-invalido>" in exempt:
        errors.append(exempt.pop("<ledger-invalido>"))
    files = sorted(f for f in os.listdir(wf_dir) if f.endswith((".yml", ".yaml"))) if os.path.isdir(wf_dir) else []
    used_allow = set()
    for name in files:
        with open(os.path.join(wf_dir, name), encoding="utf-8") as fh:
            wf, jobs = parse(fh.read())
        problems = []
        if wf is None:
            problems.append(f"{name}: sem `permissions:` no topo (herda o default do repo) — declare `permissions: contents: read`")
        elif wf == "write-all":
            problems.append(f"{name}: `permissions: write-all` no topo")
        elif isinstance(wf, dict):
            for scope, lvl in sorted(wf.items()):
                if lvl == "write":
                    problems.append(f"{name}: `{scope}: write` no nivel do WORKFLOW — escrita e escopo de job")
        for job, perms in sorted(jobs.items()):
            if perms == "write-all":
                problems.append(f"{name}: job `{job}` com `write-all`")
            elif isinstance(perms, dict):
                for scope, lvl in sorted(perms.items()):
                    if lvl == "write":
                        key = (name, job, scope)
                        if key in allow:
                            used_allow.add(key)
                        else:
                            problems.append(f"{name}: job `{job}` concede `{scope}: write` sem justificativa em {ledger}")
        if name in exempt:
            if problems:
                notes.append(f"isento: {name} ({len(problems)} achado(s) — {exempt[name]})")
            else:
                errors.append(f"DRIFT: {name} esta isento em {ledger} mas cumpre tudo — remova a isencao")
        else:
            errors.extend(problems)
    for name in exempt:
        if name not in files:
            errors.append(f"DRIFT: {ledger} isenta {name}, que nao existe em {wf_dir}")
    for key in sorted(allow):
        # allow de arquivo isento nao e conferido (o arquivo esta fora do gate)
        if key[0] in exempt:
            continue
        if key not in used_allow:
            errors.append(f"DRIFT: allow {' '.join(key)} nao corresponde a nenhum `write` de job — remova a linha")
    return errors, notes


def selftest():
    def run(files, ledger_text=""):
        with tempfile.TemporaryDirectory() as tmp:
            wf = os.path.join(tmp, "wf")
            os.makedirs(wf)
            for name, body in files.items():
                with open(os.path.join(wf, name), "w", encoding="utf-8") as fh:
                    fh.write(body)
            led = os.path.join(tmp, "ledger.txt")
            with open(led, "w", encoding="utf-8") as fh:
                fh.write(ledger_text)
            return check(wf, led)[0]

    GOOD = "name: x\npermissions:\n  contents: read\njobs:\n  a:\n    runs-on: ubuntu-latest\n    steps:\n      - run: echo\n"

    def expect(desc, files, ledger_text, want_errors):
        errs = run(files, ledger_text)
        if bool(errs) != want_errors:
            print(f"SELFTEST FALHOU: {desc} (esperava {'erro' if want_errors else 'limpo'}, veio {errs})", file=sys.stderr)
            sys.exit(1)
        print(f"  ok — {desc}")

    expect("topo read + job sem bloco passa", {"a.yml": GOOD}, "", False)
    expect("sem permissions no topo e capturado", {"a.yml": "name: x\njobs:\n  a:\n    runs-on: x\n"}, "", True)
    expect("`permissions: {}` no topo passa", {"a.yml": "permissions: {}\njobs:\n  a:\n    runs-on: x\n"}, "", False)
    expect("`permissions: read-all` no topo passa", {"a.yml": "permissions: read-all\njobs:\n  a:\n    runs-on: x\n"}, "", False)
    expect("write-all no topo e capturado", {"a.yml": "permissions: write-all\njobs:\n  a:\n    runs-on: x\n"}, "", True)
    expect("contents: write no topo e capturado",
           {"a.yml": "permissions:\n  contents: write\njobs:\n  a:\n    runs-on: x\n"}, "", True)
    expect("write no topo com comentario ao lado e capturado",
           {"a.yml": "permissions:\n  issues: write # so o job precisa\njobs:\n  a:\n    runs-on: x\n"}, "", True)
    JOBW = "permissions:\n  contents: read\njobs:\n  a:\n    runs-on: x\n    permissions:\n      contents: read\n      issues: write\n"
    expect("write de job sem justificativa e capturado", {"a.yml": JOBW}, "", True)
    expect("write de job justificado passa", {"a.yml": JOBW}, "allow a.yml a issues comenta na issue\n", False)
    expect("justificativa de OUTRO escopo nao vale", {"a.yml": JOBW}, "allow a.yml a pull-requests x\n", True)
    expect("justificativa de OUTRO job nao vale", {"a.yml": JOBW}, "allow a.yml b issues x\n", True)
    expect("allow obsoleto (job sem write) e drift", {"a.yml": GOOD}, "allow a.yml a issues x\n", True)
    expect("write-all em job e capturado, mesmo com allow",
           {"a.yml": "permissions:\n  contents: read\njobs:\n  a:\n    permissions: write-all\n"}, "allow a.yml a write-all x\n", True)
    expect("job `permissions: {}` passa",
           {"a.yml": "permissions:\n  contents: read\njobs:\n  a:\n    permissions: {}\n"}, "", False)
    two = "permissions:\n  contents: read\njobs:\n  a:\n    permissions:\n      contents: write\n  b:\n    permissions:\n      contents: read\n"
    expect("write no job a nao contamina o job b (so a exige allow)", {"a.yml": two}, "allow a.yml a contents x\n", False)
    inrun = ("permissions:\n  contents: read\njobs:\n  a:\n    steps:\n      - run: |\n"
             "          permissions:\n            contents: write\n")
    expect("`permissions:` dentro de bloco run: nao conta", {"a.yml": inrun}, "", False)
    expect("comentario de linha inteira nao conta",
           {"a.yml": "# permissions:\n#   contents: write\n" + GOOD}, "", False)
    expect("isencao declarada passa", {"a.yml": "name: x\njobs:\n  a:\n    runs-on: x\n"}, "exempt a.yml lane CI\n", False)
    expect("isencao de arquivo que ja cumpre e drift", {"a.yml": GOOD}, "exempt a.yml lane CI\n", True)
    expect("isencao de arquivo inexistente e drift", {"a.yml": GOOD}, "exempt sumiu.yml x\n", True)
    expect("linha de ledger malformada falha", {"a.yml": GOOD}, "allow a.yml so-dois\n", True)
    print("check_workflow_permissions --selftest: OK")


def main():
    if len(sys.argv) > 1 and sys.argv[1] == "--selftest":
        selftest()
        return 0
    errors, notes = check(WF_DIR, LEDGER)
    for n in notes:
        print(n)
    if errors:
        for e in errors:
            print(e, file=sys.stderr)
        print("check_workflow_permissions: FALHOU — least privilege por job; justifique cada write em " + LEDGER, file=sys.stderr)
        return 1
    print(f"check_workflow_permissions: OK ({len(notes)} arquivo(s) isento(s))")
    return 0


if __name__ == "__main__":
    sys.exit(main())
