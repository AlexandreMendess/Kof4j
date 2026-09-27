#!/usr/bin/env bash
# audit-stubs-test.sh — teste offline (fixture hermética) do inventário de stubs.
#
# RED-first: um TODO/catch-vazio/@Disabled PLANTADO precisa ser detectado; uma
# fixture LIMPA precisa dar 0 (anti-falso-positivo — o ruído "todo"=todos em PT
# é a razão de existir do cuidado); o script é read-only (a árvore não muda) e
# raiz sem */src/main precisa recusar com rc!=0 (nunca um verde mudo).
set -u
cd "$(git rev-parse --show-toplevel)"
S="scripts/audit-stubs.sh"
[ -x "$S" ] || { echo "FALHOU: $S ausente/nao-executavel"; exit 1; }
rc=0
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT

# ── fixture suja: 1 TODO (case-sensitive) + 1 catch vazio + 1 @Disabled ──────
R="$T/repo"; mkdir -p "$R/alpha/src/main/java" "$R/alpha/src/test/java" "$R/beta/src/main/java"
cat > "$R/alpha/src/main/java/A.java" <<'J'
package a;
class A {
    void f() {
        // TODO: plantado (deve ser candidato)
        try { g(); } catch (Exception e) {}
    }
    void h() { try { g(); } catch (Exception e) { /* absorvido */ } }
    void k() { throw new UnsupportedOperationException("nao implementado"); }
    void g() { throw new UnsupportedOperationException("R6 honesto"); }
}
J
cat > "$R/beta/src/main/java/B.java" <<'J'
package b;
class B { int x() { return 0; } }
J
cat > "$R/alpha/src/test/java/ATest.java" <<'J'
package a;
class ATest {
    @Disabled void t() {}
    void w() { assertTrue(true); }
    void m() { assumeTrue(r.success()); }
}
J
cat > "$R/alpha/src/main/java/LspServer.java" <<'J'
package a;
class LspServer {
    void h() {
        switch (x) {
            case 1 -> a();
            default -> { }
        }
        switch (y) {
            case 1 -> a();
            default -> respond(seq, command, Map.of());
        }
    }
    void a() {}
}
J
cat > "$R/alpha/src/test/java/FooDebugTest.java" <<'J'
package a;
class FooDebugTest { @Test void d() { System.out.println("x"); } }
J

# (a) raiz sem */src/main => recusa (rc!=0), nunca verde mudo
mkdir -p "$T/empty"
if bash "$S" "$T/empty" >/dev/null 2>&1; then
    echo "FALHOU: raiz sem src/main devia recusar"; rc=1
else
    echo "ok  — raiz sem src/main recusa (rc!=0)"
fi

# (b) read-only: hash da árvore antes == depois
before="$(find "$R" -type f -exec sha256sum {} + | sort | sha256sum)"
OUT="$(bash "$S" "$R" 2>/dev/null)"; orc=$?
after="$(find "$R" -type f -exec sha256sum {} + | sort | sha256sum)"
[ "$orc" -eq 0 ] && echo "ok  — roda rc=0 na fixture" || { echo "FALHOU: rc=$orc"; rc=1; }
[ "$before" = "$after" ] && echo "ok  — read-only (arvore intacta)" \
    || { echo "FALHOU: script escreveu na arvore"; rc=1; }

# (c) TODO plantado detectado
todo="$(printf '%s\n' "$OUT" | sed -n 's/^candidatos TODO\/FIXME:[[:space:]]*//p' | head -1)"
[ "${todo:-0}" -ge 1 ] && echo "ok  — TODO plantado detectado ($todo)" \
    || { echo "FALHOU: TODO nao detectado (=$todo)"; rc=1; }

# (d) catch vazio detectado
ec="$(printf '%s\n' "$OUT" | sed -n 's/^catch vazio:[[:space:]]*//p' | head -1)"
[ "${ec:-0}" -ge 1 ] && echo "ok  — catch vazio detectado ($ec)" \
    || { echo "FALHOU: catch vazio nao detectado (=$ec)"; rc=1; }

# (e) @Disabled em src/test listado
printf '%s\n' "$OUT" | grep -q "ATest.java" && echo "ok  — @Disabled listado" \
    || { echo "FALHOU: @Disabled nao listado"; rc=1; }

# (e2) detectores ESTRUTURAIS v2 (sem depender de marcador)
fac="$(printf '%s\n' "$OUT" | sed -n 's/^facades triviais:[[:space:]]*//p' | head -1)"
[ "${fac:-0}" -ge 1 ] && echo "ok  — facade trivial detectada ($fac)" \
    || { echo "FALHOU: facade (return 0) nao detectada (=$fac)"; rc=1; }
sw="$(printf '%s\n' "$OUT" | sed -n 's/^catch so-comentario:[[:space:]]*//p' | head -1)"
[ "${sw:-0}" -ge 1 ] && echo "ok  — catch so-comentario detectado ($sw)" \
    || { echo "FALHOU: catch so-comentario nao detectado (=$sw)"; rc=1; }
hf="$(printf '%s\n' "$OUT" | sed -n 's/^hard-fail sem codigo:[[:space:]]*//p' | head -1)"
[ "${hf:-0}" -ge 1 ] && echo "ok  — hard-fail sem codigo detectado ($hf)" \
    || { echo "FALHOU: hard-fail sem codigo nao detectado (=$hf)"; rc=1; }
# (e3) --section 7 isola a fatia (nao imprime os cortes 1-6)
SEC="$(bash "$S" "$R" --section 7 2>/dev/null)"
printf '%s\n' "$SEC" | grep -q "## 7\." && ! printf '%s\n' "$SEC" | grep -q "## 1\." \
    && echo "ok  — --section 7 isola a fatia" || { echo "FALHOU: --section 7 nao isolou"; rc=1; }

# (e4) v2.1: fachada de resposta vazia, testes false-green, *DebugTest*
facv="$(printf '%s\n' "$OUT" | sed -n 's/^fachada resposta vazia:[[:space:]]*//p' | head -1)"
[ "${facv:-0}" -ge 1 ] && echo "ok  — fachada resposta-vazia detectada ($facv)" \
    || { echo "FALHOU: fachada resposta-vazia nao detectada (=$facv)"; rc=1; }
wgr="$(printf '%s\n' "$OUT" | sed -n 's/^testes false-green:[[:space:]]*//p' | head -1)"
[ "${wgr:-0}" -ge 2 ] && echo "ok  — false-green detectado ($wgr)" \
    || { echo "FALHOU: false-green nao detectado (=$wgr)"; rc=1; }
dbg="$(printf '%s\n' "$OUT" | sed -n 's/^debug tests so-print:[[:space:]]*//p' | head -1)"
[ "${dbg:-0}" -ge 1 ] && echo "ok  — *DebugTest* detectado ($dbg)" \
    || { echo "FALHOU: *DebugTest* nao detectado (=$dbg)"; rc=1; }

# (f) fixture LIMPA => 0 candidatos (anti-falso-positivo)
C="$T/clean"; mkdir -p "$C/x/src/main/java"
cat > "$C/x/src/main/java/C.java" <<'J'
package x;
class C { int y() { return 1; } }
J
OUT2="$(bash "$S" "$C" 2>/dev/null)"
todo2="$(printf '%s\n' "$OUT2" | sed -n 's/^candidatos TODO\/FIXME:[[:space:]]*//p' | head -1)"
ec2="$(printf '%s\n' "$OUT2" | sed -n 's/^catch vazio:[[:space:]]*//p' | head -1)"
fac2="$(printf '%s\n' "$OUT2" | sed -n 's/^facades triviais:[[:space:]]*//p' | head -1)"
facv2="$(printf '%s\n' "$OUT2" | sed -n 's/^fachada resposta vazia:[[:space:]]*//p' | head -1)"
wg2="$(printf '%s\n' "$OUT2" | sed -n 's/^testes false-green:[[:space:]]*//p' | head -1)"
dbg2="$(printf '%s\n' "$OUT2" | sed -n 's/^debug tests so-print:[[:space:]]*//p' | head -1)"
if [ "${todo2:-x}" = "0" ] && [ "${ec2:-x}" = "0" ] && [ "${fac2:-x}" = "0" ] \
    && [ "${facv2:-x}" = "0" ] && [ "${wg2:-x}" = "0" ] && [ "${dbg2:-x}" = "0" ]; then
    echo "ok  — fixture limpa da 0/0/0 (sem falso-positivo)"
else
    echo "FALHOU: fixture limpa deu todo=$todo2 catch=$ec2 facade=$fac2 respvazia=$facv2 falsegreen=$wg2 debug=$dbg2"; rc=1
fi

exit "$rc"
