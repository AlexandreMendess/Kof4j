#!/usr/bin/env bash
#
# auto-loop-test.sh — o heartbeat só pode chamar o OpenCode quando o ESTADO
# mudou desde o último despacho (Onda 1). fingerprint pré-run: um run produtivo
# muda HEAD/DOING → o tick seguinte continua; um run que não muda nada → o tick
# seguinte é grátis. flock, --attach e o health-check permanecem.
#
# Cenários (gh/opencode/curl FAKES; repo git temporário):
#   A1  1º tick                              → despacha
#   A2  run PRODUTIVO (muda HEAD/DOING)      → o tick seguinte despacha (autonomia)
#   A3  run que NÃO muda o estado            → o tick seguinte é SKIP (0 chamadas)
#   A4  --dry-run                            → mostra a decisão e NÃO persiste
#   A5  servidor TUI fora do ar              → não despacha e não consome o estado
#   A6  lock ocupada por run ativo           → tick pulado (flock preservado)
#   A7  falha do agente                      → 1ª: retenta no tick seguinte; 2ª: cooldown
#   A8  estado legado (sem gate_mode)        → shadow: comportamento antigo + registro
#   A9  árvore suja muda o fingerprint       → despacha
#   A10 --attach continua no comando         → nunca spawna sessão concorrente
#   A11 AUTOLOOP_NAME: dois heartbeats coexistem (estado + cron isolados)
#   A12 telemetria encadeada: previous_fingerprint = estado PRÉ-run (≠ pós)
#
# Uso: scripts/tests/auto-loop-test.sh   (exit 0 = todos passam)
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"
. scripts/tests/lib-agent-test.sh

LOOP="$REPO_ROOT/scripts/auto-loop.sh"

mk_loop_state() { # gate_mode
    local st="$XDG_STATE_HOME/kof-auto-loop"
    mkdir -p "$st"
    {
        echo "session=ses_test"
        echo "interval=5"
        echo "repo=$REPO"
        echo "prompt='continue'"
        echo "server=http://127.0.0.1:9"
        echo "started=2026-09-20T00:00:00-03:00"
        [ -n "${1:-}" ] && echo "gate_mode=$1"
    } > "$st/state"
}
tick() { bash "$LOOP" tick >> "$TMP/tick.out" 2>&1; }
# hook do fake opencode: run PRODUTIVO = commita uma mudança no DOING.md
productive_hook() {
    cat > "$FAKE_GH_DIR/opencode.hook" <<EOF
#!/usr/bin/env bash
cd "$REPO" && echo "doing \$(date +%s%N)" > DOING.md && git add -A && git commit -q -m tick
EOF
    chmod +x "$FAKE_GH_DIR/opencode.hook"
}
setup() { mk_env; mk_repo; mk_loop_state "${1-active}"; }

echo "A1 — 1º tick despacha"
setup
tick
assert_eq 1 "$(opencode_calls)" "primeiro tick: 1 chamada"

echo "A2 — run produtivo → o tick seguinte continua"
setup
productive_hook
tick
assert_eq 1 "$(opencode_calls)" "tick 1 despacha (o hook commita e muda HEAD/DOING)"
tick
assert_eq 2 "$(opencode_calls)" "estado mudou desde o fingerprint PRÉ-run: tick 2 despacha"

echo "A3 — run sem mudança → tick seguinte é grátis"
setup
tick                                  # despacha (hook ausente: nada muda)
N="$(opencode_calls)"
tick; tick
assert_eq "$N" "$(opencode_calls)" "estado idêntico ao pré-run: 0 chamadas nos ticks seguintes"

echo "A4 — --dry-run mostra a decisão e não persiste"
setup
OUT="$(bash "$LOOP" tick --dry-run 2>&1)"
assert_contains "$OUT" "decision=DISPATCH" "dry-run mostra decision=DISPATCH"
assert_eq 0 "$(opencode_calls)" "dry-run não chama o OpenCode"
OUT="$(bash "$LOOP" tick --dry-run 2>&1)"
assert_contains "$OUT" "decision=DISPATCH" "dry-run não consumiu o estado (continua DISPATCH)"

echo "A5 — servidor fora do ar não consome o estado"
setup
export FAKE_SERVER_DOWN=1
tick
assert_eq 0 "$(opencode_calls)" "servidor fora do ar: 0 chamadas"
unset FAKE_SERVER_DOWN
tick
assert_eq 1 "$(opencode_calls)" "servidor de volta: o 1º despacho ainda acontece"

echo "A6 — flock: run anterior ativo pula o tick"
setup
mkdir -p "$XDG_STATE_HOME/kof-auto-loop"
( exec 9>"$XDG_STATE_HOME/kof-auto-loop/lock"; flock -n 9; sleep 4 ) &
HOLDER=$!
sleep 1
tick
assert_eq 0 "$(opencode_calls)" "lock ocupada: tick pulado, 0 chamadas"
wait "$HOLDER" 2>/dev/null || true

echo "A7 — falha do agente: retenta 1x, depois cooldown"
setup
export FAKE_OPENCODE_RC=1
tick
assert_eq 1 "$(opencode_calls)" "1º despacho (rc=1)"
tick
assert_eq 2 "$(opencode_calls)" "1ª falha: retenta no tick seguinte"
tick
assert_eq 2 "$(opencode_calls)" "2ª falha consecutiva: cooldown ativo, sem 3ª chamada"
assert_contains "$(cat "$(telemetry_file)" 2>/dev/null)" '"reason":"cooldown"' "telemetria registra o cooldown"
unset FAKE_OPENCODE_RC

echo "A8 — estado legado (sem gate_mode) = shadow"
setup ""
tick; tick
assert_eq 2 "$(opencode_calls)" "shadow preserva o heartbeat legado (chama todo tick)"
assert_contains "$(cat "$(telemetry_file)" 2>/dev/null)" '"legacy_would_call":true' "registra legacy_would_call"
assert_contains "$(cat "$(telemetry_file)" 2>/dev/null)" '"new_gate":"skip"' "registra que o gate novo teria pulado o 2º tick"

echo "A9 — árvore suja muda o fingerprint"
setup
tick
N="$(opencode_calls)"
echo "wip" > "$REPO/wip.txt"
tick
assert_eq "$((N + 1))" "$(opencode_calls)" "arquivo novo na árvore (git status) despacha"

echo "A10 — --attach obrigatório permanece"
setup
tick
assert_contains "$(last_opencode_call)" "--attach" "comando usa --attach (sem sessão concorrente)"

echo "A11 — AUTOLOOP_NAME: dois heartbeats coexistem (estado + cron isolados)"
setup
# fake crontab: arquivo em disco, para provar install/remove sem tocar o cron real
export FAKE_CRONTAB_FILE="$TMP/crontab.txt"
cat > "$TMP/bin/crontab" <<'EOF'
#!/usr/bin/env bash
f="${FAKE_CRONTAB_FILE:?}"
if [ "${1:-}" = "-l" ]; then
    cat "$f" 2>/dev/null || true
else
    tmp="$(mktemp)"; cat > "$tmp"; mv "$tmp" "$f"   # como o crontab real: lê o stdin todo, depois troca
fi
EOF
chmod +x "$TMP/bin/crontab"
# default (A) e loop nomeado (B) ativos ao mesmo tempo
bash "$LOOP" start ses_default 2 9094 >/dev/null 2>&1
AUTOLOOP_NAME=kof-auto-loop-b bash "$LOOP" start ses_b 2 9095 >/dev/null 2>&1
assert_contains "$(cat "$FAKE_CRONTAB_FILE")" "env AUTOLOOP_NAME=kof-auto-loop-b" "cron do loop B carrega o env do nome"
assert_eq 1 "$(grep -cE '# kof-auto-loop$' "$FAKE_CRONTAB_FILE")" "cron default presente"
assert_eq 1 "$(grep -cE '# kof-auto-loop-b$' "$FAKE_CRONTAB_FILE")" "cron do loop B presente"
# tick com o nome lê o state do loop B (não o default)
AUTOLOOP_NAME=kof-auto-loop-b bash "$LOOP" tick >> "$TMP/tick.out" 2>&1
assert_contains "$(last_opencode_call)" "--session ses_b" "tick nomeado usa a sessão do loop B"
# parar B não pode apagar o cron default (marcador ancorado, não substring)
AUTOLOOP_NAME=kof-auto-loop-b bash "$LOOP" stop >/dev/null 2>&1
assert_eq 1 "$(grep -cE '# kof-auto-loop$' "$FAKE_CRONTAB_FILE")" "parar B preserva o cron default"
assert_eq 0 "$(grep -cE '# kof-auto-loop-b$' "$FAKE_CRONTAB_FILE")" "parar B remove só a linha de B"
unset FAKE_CRONTAB_FILE AUTOLOOP_NAME

echo "A12 — telemetria encadeada: previous_fingerprint = estado PRÉ-run"
setup
productive_hook
tick                     # dispatch 1 (first_dispatch, persiste o estado A)
tick                     # o hook mudou o estado -> dispatch 2 (estado B)
recs="$(grep '"decision":"dispatch"' "$(telemetry_file)")"
fp1="$(sed -n '1p' <<<"$recs" | grep -oE '"fingerprint":"[^"]*"' | head -1 | cut -d'"' -f4)"
fp2="$(sed -n '2p' <<<"$recs" | grep -oE '"fingerprint":"[^"]*"' | head -1 | cut -d'"' -f4)"
pfp2="$(sed -n '2p' <<<"$recs" | grep -oE '"previous_fingerprint":"[^"]*"' | head -1 | cut -d'"' -f4)"
[ -n "$fp1" ] && [ -n "$fp2" ] && pass "dois dispatches registrados (fp1/fp2 presentes)" || fail "faltam dispatches na telemetria"
assert_eq "$fp1" "$pfp2" "previous_fingerprint do 2º = fingerprint do 1º (estado PRÉ-run)"
[ "$fp2" != "$pfp2" ] && pass "2º dispatch: fingerprint ≠ previous_fingerprint (a telemetria não colapsa)" || fail "previous_fingerprint colapsou no fingerprint atual"

finish
