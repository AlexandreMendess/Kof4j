#!/usr/bin/env bash
# sync-push.sh — a regra de sync-on-push mecanizada (AGENTS.md §Multi-agent,
# diretriz da mantenedora 18/09): NENHUM commit sobe de base dessincronizada e
# NENHUM commit esperado vira "tarefa fantasma" (outro agente reatribui a lane:
# "owner sumiu = tarefa morta").
#
# Faz, na ordem: fetch → pull --rebase --autostash → push → verificação 0/0.
# Conflito de rebase = PARA e manda resolver com edição cirúrgica dos dois
# lados (lição d7dba433: NUNCA `checkout --ours/theirs` às cegas em arquivo de
# registro — DOING.md/known-bugs).
#
# Uso:  scripts/sync-push.sh                 # push normal (hooks/gates rodam)
#       scripts/sync-push.sh --no-verify     # só com a CAUSA registrada no
#                                            # commit e no DOING.md (gate RED
#                                            # por dívida pré-existente alheia
#                                            # — nunca p/ esconder falha própria)
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"

branch=$(git rev-parse --abbrev-ref HEAD)
[ "$branch" != "HEAD" ] || { echo "fora de branch (detached/rebase em curso): primeiro conclua/abra o rebase" >&2; exit 1; }

# §384: `pull --rebase` ACHATA e DESCARTA merge commits locais em silencio
# (20/09: dois ff-merges beta-0.4.0->beta-0.5.0 sumiram do tip sem erro).
# Se a janela origin..HEAD tem merge, a sincronizacao usa merge (preserva os
# dois lados); senao, segue o rebase mecanico de sempre.
git fetch -q origin
if [ -n "$(git rev-list --merges "origin/$branch..HEAD")" ]; then
    if ! git merge --no-edit "origin/$branch"; then
        echo "== CONFLITO no merge de $branch (HEAD tem merge commit — rebase destruidor foi pulado)." >&2
        echo "   Resolva os blocos <<<<<<< preservando os DOIS lados, git add, git commit --no-edit, e rode $0 de novo" >&2
        echo "   (registro truncado = incidente d7dba433; na dúvida: git merge --abort)" >&2
        exit 1
    fi
elif ! git pull --rebase --autostash origin "$branch"; then
    echo "== CONFLITO no rebase de $branch. Resolva os blocos <<<<<<< preservando os DOIS lados," >&2
    echo "   depois: git add <arquivos> && git rebase --continue && $0" >&2
    echo "   (registro truncado = incidente d7dba433; na dúvida: git rebase --abort)" >&2
    exit 1
fi

# Árvore compartilhada: o remoto anda durante o push (outras lanes). Retry
# mecânico: falhou → re-fetch + rebase + tenta de novo (até 3x). Conflito no
# retry = PARA (preservar os dois lados, mesma política do rebase inicial).

# Credencial de headless (medido 19/09 ~21:0x): uma sessão de agente iniciada
# FORA do terminal integrado do VS Code não herda o canal GIT_ASKPASS da janela
# e o push HTTPS morre em "could not read Username" — o segredo nunca se toca
# aqui: só reaproveitamos o socket ipc que a própria janela do VS Code expõe em
# /run/user/$UID/vscode-git-*.sock (dois deles estavam mortos; um respondeu).
# Só tenta quando NÃO estamos dentro de um terminal do VS Code.
if [ -z "${GIT_ASKPASS:-}" ]; then
  codebin=$(readlink -f "/proc/$(pgrep -x code 2>/dev/null | head -1)/exe" 2>/dev/null)
  [ -x "$codebin" ] || codebin=/usr/share/code/code
  coderoot=$(dirname "$codebin")
  for sock in /run/user/"$(id -u)"/vscode-git-*.sock; do
    [ -S "$sock" ] || continue
    # socket morto = arquivo órfão sem listener; o probe ls-remote NÃO serve de
    # teste (repo publico responde anonimo). ss -xl lista os que ESTAO aceitos.
    ss -xl 2>/dev/null | grep -qF "$sock" || continue
    if GIT_ASKPASS="$coderoot/resources/app/extensions/git/dist/askpass.sh" \
       VSCODE_GIT_ASKPASS_NODE="$codebin" \
       VSCODE_GIT_ASKPASS_MAIN="$coderoot/resources/app/extensions/git/dist/askpass-main.js" \
       VSCODE_GIT_ASKPASS_EXTRA_ARGS="" \
       VSCODE_GIT_IPC_HANDLE="$sock" \
       timeout 30 git ls-remote -q origin HEAD >/dev/null 2>&1; then
      export GIT_ASKPASS="$coderoot/resources/app/extensions/git/dist/askpass.sh" \
             VSCODE_GIT_ASKPASS_NODE="$codebin" \
             VSCODE_GIT_ASKPASS_MAIN="$coderoot/resources/app/extensions/git/dist/askpass-main.js" \
             VSCODE_GIT_ASKPASS_EXTRA_ARGS="" \
             VSCODE_GIT_IPC_HANDLE="$sock"
      echo "== credencial via ipc da janela VS Code ($sock)"
      break
    fi
  done
fi

ok=""
for try in 1 2 3; do
    if git push "$@" origin "$branch"; then ok=1; break; fi
    echo "== push falhou (tentativa $try/3) — re-fetch + sincroniza (merge-preservante se HEAD tem merge) e tenta de novo" >&2
    if [ -n "$(git rev-list --merges "origin/$branch..HEAD")" ]; then
        git merge -q --no-edit "origin/$branch" \
            || { echo "== CONFLITO no merge do retry — resolva os DOIS lados, git add, git commit e rode $0 de novo" >&2; exit 1; }
    else
        git pull -q --rebase --autostash origin "$branch" \
            || { echo "== CONFLITO no rebase do retry — resolva os DOIS lados, git add, rebase --continue e rode $0 de novo" >&2; exit 1; }
    fi
done
[ -n "$ok" ] || { echo "== push falhou 3x — rode scripts/codeql-gate.sh --fast p/ ver a causa do hook" >&2; exit 1; }

git fetch -q origin
behind=$(git rev-list --count "HEAD..origin/$branch")
ahead=$(git rev-list --count "origin/$branch..HEAD")
echo "== SYNCED $branch: ahead=$ahead behind=$behind"
[ "$ahead" = "0" ] && [ "$behind" = "0" ]
