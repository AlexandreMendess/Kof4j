#!/usr/bin/env bash
# safe-suite.sh — runner de suíte com proteção de host (regra 6: as duas mortes
# de 19/09 foram MEDIDAS, cada guarda abaixo mata uma causa-raiz real):
#
#   Morte 1 (17:40, global OOM): DOIS agentes rodando `mvn` ao mesmo tempo na
#     árvore compartilhada (heartbeat corrompido anexado no servidor errado) →
#     forks do surefire empilhados. Guarda: flock de instância ÚNICA por host.
#   Morte 2 (18:42, global OOM): `kill -STOP` no JDT LS (mitigação da corrida
#     de stub ECJ) → o health-check do redhat.java (`jcmd <pid> VM.uptime`) não
#     tem timeout nem cleanup: cada ping congela no attach para sempre. 395
#     jcmd pendurados (visto no despejo do kernel), cada um mapeando o espaço
#     do alvo via ptrace → RAM esgotada; as VÍTIMAS do OOM foram `code` e
#     `opencode`, nunca o próprio invasor. Guardas: watchdog de jcmd + recusa
#     rodar com qualquer java em estado T (SIGSTOP).
#
# A alternativa correta ao "pare o JDT" é no HOST:
#   "java.autobuild.enabled": false  (settings.json do VS Code)
# — sem autobuild o JDT não escreve em target/classes e não precisa ser pausado.
#
# Uso:
#   scripts/safe-suite.sh                       # suíte completa (reatores default)
#   scripts/safe-suite.sh -pl kof-compiler -am -Dtest=WorkflowE2ETest
#   SAFE_SUITE_LOG=/tmp/x.log scripts/safe-suite.sh
# O mvn roda em PRIMEIRO PLANO neste script (chame com setsid/nohup você mesmo);
# o log é tee'd para SAFE_SUITE_LOG e o resumo sai no final, sempre.
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"

LOG="${SAFE_SUITE_LOG:-/tmp/opencode/safe-suite-$(date +%Y%m%d-%H%M%S).log}"
LOCK="${SAFE_SUITE_LOCK:-/tmp/opencode/kof-suite.lock}"
mkdir -p "$(dirname "$LOG")" "$(dirname "$LOCK")"

kill_stale_jcmd() {
  # ping legítimo do redhat.java responde em ms; >120s = congelado no attach
  local victims
  victims=$(ps -o pid=,etimes=,comm= -C jcmd 2>/dev/null | awk '$2 > 120 { print $1 }')
  [ -n "$victims" ] || return 0
  echo "safe-suite: matando jcmd congelado(s): $(echo $victims | tr '\n' ' ')" >&2
  # shellcheck disable=SC2086
  kill -9 $victims 2>/dev/null || true
}

# guarda 3: nenhum java SIGSTOPado nesta máquina (o estado que gerou a morte 2)
STOPPED_JAVA=$(ps -eo stat=,comm= | awk '$2 == "java" && $1 ~ /^T/ { c++ } END { print c + 0 }')
if [ "$STOPPED_JAVA" -gt 0 ]; then
  echo "safe-suite: RECUSADO — $STOPPED_JAVA processo(s) java em estado T (SIGSTOP)." >&2
  echo "  Não pause o JDT LS; use no host \"java.autobuild.enabled\": false e" >&2
  echo "  libere os pausados: kill -CONT <pid>. (causa da morte 19/09 18:42)" >&2
  ps -eo pid=,stat=,comm= | awk '$2 ~ /^T/ && $3 == "java" { print "  pid " $1 " parado" }' >&2
  exit 1
fi

# guarda 1: flock de instância única (herdado pelo mvn; solta quando a suíte acaba)
exec 9>"$LOCK"
if ! flock -n 9; then
  echo "safe-suite: RECUSADO — outra suíte segura a lock $LOCK." >&2
  echo "  (causa da morte 19/09 17:40: dois mvn na mesma árvore)" >&2
  exit 1
fi

# guarda 2: watchdog pontual — jcmd congelado de antes não entra carona na suíte
kill_stale_jcmd

# guarda 4: teto de heap do JVM mestre (forks já levam -Xmx1g do argLine do pom raiz)
export MAVEN_OPTS="${MAVEN_OPTS:--Xmx1g}"

export JAVA_HOME="${JAVA_HOME:-$HOME/tools/jdk-25}"
export PATH="$HOME/tools/apache-maven-3.9.9/bin:$JAVA_HOME/bin:$PATH"

if [ "$#" -eq 0 ]; then
  set -- -o test -Dmaven.test.failure.ignore=true
fi
echo "safe-suite: mvn $*  ->  $LOG"
# Limpeza SEM o maven-clean-plugin: em 19/09 19:3x o `mvn -o clean` FALHOU
# silenciosamente (maven-shared-utils ausente no .m2 + offline) e a suíte rodou
# sobre os STUBS ECJ que o JDT LS escreveu às 17:45 (JRE 21 da extensão não tem
# Arena.allocateFrom -> classe-fantoche "Unresolved compilation problems"); o
# build incremental do javac pulou os .class "mais novos que o fonte" e o
# surefire executou o stub. rm -rf no filesystem não depende de plugin nenhum.
STUBS=$(grep -rl "Unresolved compilation problem" */target/classes 2>/dev/null | wc -l)
[ "$STUBS" -gt 0 ] && echo "safe-suite: $STUBS .class contaminado(s) por stub ECJ — removendo targets" >&2
rm -rf */target/classes */target/test-classes
# estampa de proveniencia (R6): o log grava o SHA e a sujeira da arvore ANTES do
# mvn, para scripts/stability-report.sh so certificar o commit que foi testado.
SUITE_SHA="$(git rev-parse HEAD 2>/dev/null || echo unknown)"
SUITE_DIRTY="$(git status --porcelain --untracked-files=no 2>/dev/null | wc -l | tr -d ' ')"
{ printf 'SUITE-SHA: %s\n' "$SUITE_SHA"
  printf 'SUITE-DIRTY: %s\n' "$SUITE_DIRTY"; } > "$LOG"
mvn "$@" >>"$LOG" 2>&1
RC=$?
RC=$?

# watchdog pós-execução: se a extensão congelou pings durante a corrida, limpa
kill_stale_jcmd

echo "----- resumo (rc=$RC) -----"
grep -hE "Tests run:.*Time elapsed" "$LOG" 2>/dev/null \
  | sed -E 's/.*Tests run: ([0-9]+), Failures: ([0-9]+), Errors: ([0-9]+), Skipped: ([0-9]+).*/\1 \2 \3 \4/' \
  | awk '{t+=$1; f+=$2; e+=$3; s+=$4} END {printf "TOTAL: tests=%d failures=%d errors=%d skipped=%d\n", t, f, e, s}' \
  | tee -a "$LOG"
echo "log completo: $LOG"
exit $RC
