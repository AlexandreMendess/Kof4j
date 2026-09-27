[English](README.md) | [Português](README.pt_BR.md)

# Paridade Total — a regra ABSOLUTA da release 0.5.0

> **Mantenedora, 24/09: A REGRA ABSOLUTA PARA QUALQUER PLANO É A PARIDADE
> TOTAL.** Toda superfície do Kof funciona em TODO alvo — JVM/Script, Native
> x86-64, Native riscv64/aarch64, JS — com paridade byte/golden contra o
> oráculo JVM. Um código de gap honesto é o rastreador, nunca o estado final.
> A release 0.5.0 não corta enquanto o ledger tiver linhas abertas.

## Arquivos

- [`PARITY-GAPS.md`](PARITY-GAPS.pt_BR.md) — **o ledger impeditivo**: cada
  linha de paridade parcial medida (superfície × alvo × código de gap × lane
  dona). Gate de máquina: `check_release_050_gate.sh` → `full_parity`
  (qualquer linha aberta = o gate de release fica RED).

## Como uma linha fecha

Veja o "Definition of done" no ledger: compilar + prova golden/E2E byte a
byte + tabelas de docs atualizadas no mesmo commit + linha removida no mesmo
commit.
