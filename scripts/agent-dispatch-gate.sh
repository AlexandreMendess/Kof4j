#!/usr/bin/env bash
#
# agent-dispatch-gate.sh — decide, SEM modelo, se uma chamada cara ao agente
# (OpenCode) deve acontecer. Polling é barato e pode ser frequente; LLM só
# quando o gate acha mudança/trabalho real.
#
# Uso:
#   agent-dispatch-gate.sh decide <auto-loop|issue-watcher> --session S [--repo D] [--dry-run] [--shadow]
#   agent-dispatch-gate.sh record <mode> --session S --rc N [--duration SEC] [--shadow]
#   agent-dispatch-gate.sh seed   <mode> --session S [--repo D]
#   agent-dispatch-gate.sh note   <mode> --session S --decision skip|dispatch --reason R
#   agent-dispatch-gate.sh events <mode> --session S      # eventos do último `decide` (watcher)
#   agent-dispatch-gate.sh stats [--since 24h|7d]   # ticks x chamadas evitadas + CUSTO REAL do `opencode stats` (medido, nunca estimado)
#
# Exit do `decide` (0 nunca significa skip):
#   0  DISPATCH                     10 SKIP_NO_CHANGE
#   11 SKIP_NO_ACTIONABLE_WORK      12 SKIP_AGENT_BUSY (cooldown após falhas)
#   20 RETRY_TRANSIENT_SOURCE_FAILURE (git/GitHub falhou; NÃO é "estável")
#
# Semântica por modo:
#  · auto-loop: persiste o fingerprint PRÉ-run ao despachar. Run produtivo muda
#    HEAD/DOING → o tick seguinte difere e continua; run que não muda nada → o
#    tick seguinte é idêntico e passa a ser grátis.
#  · issue-watcher: compara snapshots por issue (título, corpo, último comentário
#    EXTERNO). O snapshot só avança após `record --rc 0` (falha de entrega = o
#    mesmo evento é retentado). Issue nova sem comentário é evento próprio.
#
# Falhas do agente: 1ª → retenta no tick seguinte; 2ª consecutiva → cooldown de
# 15 min; 3ª+ → 30 min (AGENT_COOLDOWN_2_S / AGENT_COOLDOWN_3_S sobrescrevem).
set -uo pipefail
HERE="$(cd "$(dirname "$(readlink -f "$0")")" && pwd)"
# shellcheck source=agent-common.sh
. "$HERE/agent-common.sh"
FP="$HERE/agent-state-fingerprint.sh"

sub="${1:-}"; shift || true
mode=""; session="default"; repo=""; dry=0; shadow=0; rc_arg=""; dur=""; decision_arg=""; reason_arg=""; since="24h"
# fingerprint do estado ANTERIOR, capturado ANTES de qualquer persist_last
# sobrescrever $D/last (senão a telemetria loga previous == current no dispatch).
PREV_FP=""
if [ "$sub" != "stats" ] && [ "$sub" != "" ]; then mode="${1:-}"; shift || true; fi
while [ $# -gt 0 ]; do
    case "$1" in
        --session)  session="${2:-default}"; shift 2;;
        --repo)     repo="${2:-}"; shift 2;;
        --dry-run)  dry=1; shift;;
        --shadow)   shadow=1; shift;;
        --rc)       rc_arg="${2:-}"; shift 2;;
        --duration) dur="${2:-}"; shift 2;;
        --decision) decision_arg="${2:-}"; shift 2;;
        --reason)   reason_arg="${2:-}"; shift 2;;
        --since)    since="${2:-24h}"; shift 2;;
        *) echo "argumento desconhecido: $1" >&2; exit 2;;
    esac
done
[ "${AGENT_GATE_MODE:-}" = "shadow" ] && shadow=1

case "$mode" in auto-loop|issue-watcher|"") ;; *) echo "modo inválido: $mode" >&2; exit 2;; esac
SAFE_SESSION="$(printf '%s' "$session" | tr -c 'A-Za-z0-9_.-' '_')"
D="$AGENT_STATE_ROOT/gate/${mode:-none}/$SAFE_SESSION"

# --- computa o estado atual ---------------------------------------------------
CUR=""; CUR_FP=""
compute() {
    local out rc
    if [ "$mode" = "auto-loop" ]; then out="$("$FP" auto-loop --repo "$repo")"; rc=$?
    else out="$("$FP" issue-watcher)"; rc=$?; fi
    [ "$rc" -eq 0 ] || return "$rc"
    CUR_FP="$(printf '%s\n' "$out" | sed -n 's/^fingerprint=//p')"
    CUR="$(printf '%s\n' "$out" | grep -v '^fingerprint=' || true)"
    return 0
}

prev_fp() { [ -f "$D/last" ] && printf '%s\n' "$(cat "$D/last")" | sha; }
field() { printf '%s' "$1" | sed -n "s/.* $2=\([^ ]*\).*/\1/p"; }
val() { grep -m1 "^$1=" "$2" 2>/dev/null | cut -d= -f2-; }

# --- decisão + telemetria -------------------------------------------------------
# say <exit> <NOME> <dispatch|skip|retry> <razão> — imprime, registra e sai
say() {
    local code="$1" name="$2" kind="$3" reason="$4" pfp="$PREV_FP"
    echo "decision=$name reason=$reason fingerprint=${CUR_FP:-none}"
    if [ "$dry" -eq 0 ]; then
        if [ "$shadow" -eq 1 ]; then
            tel type decision source "$mode" session "$session" decision "$kind" reason "$reason" \
                fingerprint "${CUR_FP:-}" previous_fingerprint "$pfp" \
                legacy_would_call @true new_gate "$kind" opencode_invoked @true
        else
            tel type decision source "$mode" session "$session" decision "$kind" reason "$reason" \
                fingerprint "${CUR_FP:-}" previous_fingerprint "$pfp" \
                opencode_invoked "@$([ "$kind" = dispatch ] && echo true || echo false)"
        fi
    fi
    exit "$code"
}

persist_last() { [ "$dry" -eq 0 ] && { mkdir -p "$D"; { printf '%s\n' "$CUR"; } > "$D/last"; }; return 0; }

diff_reasons() { # componentes que mudaram (auto-loop)
    local k out=""
    for k in head doing known_bugs docs_dev worktree ci; do
        [ "$(val "$k" "$D/last")" != "$(printf '%s\n' "$CUR" | sed -n "s/^$k=//p")" ] && out="${out:+$out+}${k}_changed"
    done
    echo "${out:-state_changed}"
}

compute_events() { # eventos do watcher: prev($D/last) x CUR
    local line n p ct cb cc pt pb pc
    while IFS= read -r line; do
        case "$line" in issue:*) ;; *) continue;; esac
        n="${line#issue:}"; n="${n%% *}"
        p="$(grep -m1 "^issue:$n " "$D/last" 2>/dev/null || true)"
        if [ -z "$p" ]; then echo "new_issue #$n"; continue; fi
        ct="$(field "$line" t)"; cb="$(field "$line" b)"; cc="$(field "$line" c)"
        pt="$(field "$p" t)";    pb="$(field "$p" b)";    pc="$(field "$p" c)"
        { [ "$ct" != "$pt" ] || [ "$cb" != "$pb" ]; } && echo "edited_issue #$n"
        [ "${cc:-0}" -gt "${pc:-0}" ] 2>/dev/null && echo "external_comment #$n id=$cc"
    done <<< "$CUR"
}

cmd_decide() {
    [ -n "$mode" ] || { echo "decide exige o modo" >&2; exit 2; }
    [ "$mode" = "auto-loop" ] && [ -z "$repo" ] && { echo "auto-loop exige --repo" >&2; exit 2; }
    local rc cu now
    # captura o fingerprint PRE-run antes que persist_last sobrescreva $D/last
    PREV_FP=""; [ -f "$D/last" ] && PREV_FP="$(prev_fp)"
    compute; rc=$?
    if [ "$rc" -ne 0 ]; then
        [ "$rc" -eq 20 ] && say 20 RETRY_TRANSIENT_SOURCE_FAILURE retry source_failure
        exit "$rc"
    fi
    now="$(date +%s)"; cu="$(cat "$D/cooldown_until" 2>/dev/null || echo 0)"
    case "$cu" in ''|*[!0-9]*) cu=0;; esac
    [ "$cu" -gt "$now" ] && say 12 SKIP_AGENT_BUSY skip cooldown

    if [ ! -f "$D/last" ]; then
        if [ "$mode" = "auto-loop" ]; then
            persist_last; say 0 DISPATCH dispatch first_dispatch
        fi
        persist_last; say 10 SKIP_NO_CHANGE skip baseline_seeded    # watcher: semeia
    fi

    if [ "$CUR_FP" = "$(prev_fp)" ]; then
        if [ "$mode" = "auto-loop" ] && [ -f "$D/failed" ]; then
            persist_last; say 0 DISPATCH dispatch retry_after_failure
        fi
        say 10 SKIP_NO_CHANGE skip "$([ "$mode" = auto-loop ] && echo no_change || echo no_external_change)"
    fi

    if [ "$mode" = "auto-loop" ]; then
        local why; why="$(diff_reasons)"
        persist_last; say 0 DISPATCH dispatch "$why"        # fingerprint PRÉ-run
    fi

    local events; events="$(compute_events)"
    if [ -z "$events" ]; then
        persist_last; say 11 SKIP_NO_ACTIONABLE_WORK skip no_actionable_work   # só remoções
    fi
    if [ "$dry" -eq 0 ]; then
        mkdir -p "$D"
        printf '%s\n' "$events" > "$D/events"
        printf '%s\n' "$CUR" > "$D/pending"                 # só vira 'last' após record --rc 0
    fi
    printf '%s\n' "$events" | sed 's/^/event: /'
    say 0 DISPATCH dispatch "$(printf '%s\n' "$events" | awk '{print $1}' | sort -u | paste -sd+ -)"
}

cmd_record() {
    [ -n "$mode" ] && [ -n "$rc_arg" ] || { echo "record exige o modo e --rc" >&2; exit 2; }
    mkdir -p "$D"
    tel type run source "$mode" session "$session" rc "@$rc_arg" duration_s "@${dur:-0}" opencode_invoked @true
    if [ "$rc_arg" -eq 0 ]; then
        [ "$mode" = "issue-watcher" ] && [ -f "$D/pending" ] && mv "$D/pending" "$D/last"
        rm -f "$D/failed" "$D/failures" "$D/cooldown_until"
        return 0
    fi
    local n; n="$(cat "$D/failures" 2>/dev/null || echo 0)"; n=$((n + 1))
    echo "$n" > "$D/failures"; : > "$D/failed"
    if [ "$n" -eq 2 ]; then echo $(( $(date +%s) + ${AGENT_COOLDOWN_2_S:-900} )) > "$D/cooldown_until"
    elif [ "$n" -ge 3 ]; then echo $(( $(date +%s) + ${AGENT_COOLDOWN_3_S:-1800} )) > "$D/cooldown_until"; fi
}

cmd_seed() {
    [ -n "$mode" ] || { echo "seed exige o modo" >&2; exit 2; }
    compute || exit $?
    persist_last
    tel type note source "$mode" session "$session" decision skip reason baseline_seeded \
        fingerprint "$CUR_FP" opencode_invoked @false
    echo "baseline semeado: $CUR_FP"
}

cmd_note() {
    [ -n "$mode" ] && [ -n "$decision_arg" ] && [ -n "$reason_arg" ] || { echo "note exige modo, --decision e --reason" >&2; exit 2; }
    # `--decision dispatch` = o chamador VAI invocar o modelo (ex.: watcher de 1 issue)
    tel type decision source "$mode" session "$session" decision "$decision_arg" reason "$reason_arg" \
        opencode_invoked "@$([ "$decision_arg" = dispatch ] && echo true || echo false)"
}

cmd_events() { [ -f "$D/events" ] && cat "$D/events"; return 0; }

cmd_stats() {
    python3 - "$AGENT_STATE_ROOT/dispatch.jsonl" "$since" <<'PY'
import json, sys, datetime, statistics
path, since = sys.argv[1], sys.argv[2]
unit = since[-1:]; num = int(since[:-1] or 0)
delta = datetime.timedelta(hours=num) if unit == "h" else datetime.timedelta(days=num)
cut = datetime.datetime.now(datetime.timezone.utc) - delta
recs = []
try:
    for line in open(path, encoding="utf-8"):
        line = line.strip()
        if not line:
            continue
        try:
            r = json.loads(line)
            ts = datetime.datetime.fromisoformat(r["ts"])
            if ts.tzinfo is None:
                ts = ts.replace(tzinfo=datetime.timezone.utc)
            if ts >= cut:
                recs.append(r)
        except Exception:
            continue
except FileNotFoundError:
    pass
dec = [r for r in recs if r.get("type") in ("decision", "note")]
runs = [r for r in recs if r.get("type") == "run"]
ticks = len(dec)
called = sum(1 for r in dec if r.get("opencode_invoked") is True)
skipped = ticks - called
legacy = [r for r in dec if r.get("legacy_would_call") is True]
would_skip = sum(1 for r in legacy if r.get("new_gate") != "dispatch")
fails = sum(1 for r in runs if r.get("rc", 0) != 0)
durs = [r["duration_s"] for r in runs if isinstance(r.get("duration_s"), (int, float))]
avoid = (100.0 * skipped / ticks) if ticks else 0.0
print(f"Ticks:                 {ticks}")
print(f"OpenCode dispatches:   {called}")
print(f"Skipped:               {skipped}")
print(f"Dispatch avoidance:    {avoid:.1f}%")
print(f"Failures:              {fails}")
print(f"Median run duration:   {statistics.median(durs) if durs else 0}s")
if legacy:
    print(f"Shadow (legacy calls): {len(legacy)}, gate would skip {would_skip}")
PY
    opencode_cost_section
}

# Custo REAL medido pelo próprio opencode (`opencode stats --days N`): tokens e
# dólares do provedor. NUNCA estimado: sem opencode, com falha ou com saída não
# reconhecida, diz "indisponível" — não inventa número. É de TODAS as sessões da
# máquina (não só as despachadas pelo gate) e a janela é em dias inteiros
# (--since 24h -> 1 dia; 36h -> 2; 7d -> 7). AGENT_OPENCODE_PROJECT filtra por projeto.
opencode_cost_section() {
    local oc="${OPENCODE_BIN:-}" num unit days out rc clean k v total=""
    [ -n "$oc" ] || oc="$(command -v opencode 2>/dev/null || true)"
    [ -n "$oc" ] || oc="$HOME/.opencode/bin/opencode"
    num="${since%[hd]}"; unit="${since: -1}"
    case "$num" in ''|*[!0-9]*) num=1;; esac
    if [ "$unit" = "d" ]; then days="$num"; else days=$(( (num + 23) / 24 )); fi
    [ "$days" -ge 1 ] || days=1
    echo
    echo "Custo real (opencode stats --days $days; medido pelo provedor, sem estimativa;"
    echo "            todas as sessões desta máquina, janela em dias inteiros):"
    if [ ! -x "$oc" ]; then echo "  indisponível: opencode não encontrado ($oc)"; return 0; fi
    local pflag=(); [ -n "${AGENT_OPENCODE_PROJECT+x}" ] && pflag=(--project "$AGENT_OPENCODE_PROJECT")
    out="$(timeout 30 "$oc" stats --days "$days" ${pflag[@]+"${pflag[@]}"} 2>/dev/null)"; rc=$?
    if [ "$rc" -ne 0 ]; then echo "  indisponível: opencode stats falhou (rc=$rc)"; return 0; fi
    clean="$(printf '%s\n' "$out" | sed 's/\x1b\[[0-9;]*m//g')"
    _oc_val() { printf '%s\n' "$clean" | awk -F'│' -v k="$1" '{ s=$2; if (index(s,k)==1) { sub("^" k "[ \t]+","",s); sub("[ \t]+$","",s); print s; exit } }'; }
    total="$(_oc_val 'Total Cost')"
    if [ -z "$total" ]; then echo "  indisponível: saída do opencode stats não reconhecida (sem 'Total Cost')"; return 0; fi
    for k in 'Sessions' 'Messages' 'Total Cost' 'Input' 'Output' 'Cache Read' 'Cache Write'; do
        v="$(_oc_val "$k")"
        [ -n "$v" ] && printf '  %-14s %s\n' "$k:" "$v"
    done
}

case "$sub" in
    decide) cmd_decide;;
    record) cmd_record;;
    seed)   cmd_seed;;
    note)   cmd_note;;
    events) cmd_events;;
    stats)  cmd_stats;;
    *) echo "uso: $0 {decide|record|seed|note|events|stats} ..." >&2; exit 2;;
esac
