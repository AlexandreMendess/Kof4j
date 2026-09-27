[English](db-parity-plan.md) | [Português](db-parity-plan.pt_BR.md)

# Plano de Paridade de DB — todo alvo aceita todo scheme (mariadb, mysql, sqlite, mongodb, …)

last: S5.4-cross-connect
doing: db-parity
next: remaining-schemes
location: db-parity-plan
state: active

> **EM DESENVOLVIMENTO** — aberto 21/09/2026 a partir da diretiva da mantenedora
> (adendo `D-DB-GAPS`, `DECISIONS.md`). O plano é a fila; cada fatia pousa com
> prova (Q0–Q7) e este doc só **move para `docs/stdlib/`** quando a paridade
> estiver completa.

**Dono:** lane `gaps-db` (repassada 21/09 por ordem da mantenedora, sob `D-DB-PARITY-OWNER`; S0/S1 autorizadas) · **Registros/plano:** lane docs/plataforma
**Branch:** `beta-0.5.0` · **Estado:** S0 ✅ FEITO (21/09, sessão 9092) — recusa nativa honesta de scheme não suportado com o código nomeado `DB001`, mais link-by-use real (sem link de `libmariadb` para literais não-mysql); **S1 ✅ FEITO no Native x86-64 (23/09, lane gaps-db)** — `mariadb://` é alias do wire `mysql://`; no cross segue `DB001` honesto até o wire mysql ser portado (R7) — **superado pelo S5.4 fatia 1 (24/09, peça `B73`): `mysql://`/`mariadb://` no cross agora conectam de verdade**; **S2 ✅ FEITO no JVM/JS/Android (23/09, lane gaps-db)** — driver JDBC ausente agora é diagnóstico `DB001` nomeado (falhas reais de conexão intactas); **S3/S4 ✅ FEITOS 23/09 (lane gaps-db)** — `mongodb://` real no JVM/Android e `DB001` declarado no JS/Native; `oracle` declarado (sem driver/servidor no host); **S5.0 (camada de socket cross) ✅ SATISFEITA 23/09 (medido — já provada pelo core HTTP cross)**; **S5.1 (handshake+auth) ✅ SATISFEITA 23/09 (peças `B62`–`B66`): SHA1/bswap + scramble/lenenc + parse do greeting + montagem da resposta de auth + o round-trip de socket, provados por `NativeRiscvDbWireTest` em riscv64+aarch64 (qemu, contra o oráculo JVM e contra o MariaDB REAL — o servidor devolve o pacote OK; um DB inexistente devolve Err). A superfície Kof (`db.connect` cross) agora conecta de verdade (S5.4 fatia 1, peça `B73`) e só recusa esquemas não portados com o `DB001` honesto**; **S5.2 (`COM_QUERY`) 🟡 PARCIAL 23/09 (peças `B67`–`B69`) — framing do request + classificação da 1ª resposta (`SELECT 1`→1, `SET`→0, SQL ruim→255) E o reader de pacotes + cabeçalho do resultset (ncols + payload lenenc da 1ª linha: `SELECT 1`→1col/[01 31], `SELECT 1,'ab'`→2col/[01 31 02 61 62]), provados em riscv64+aarch64 (qemu) contra o MariaDB real; materializar todas as linhas como valores Kof é a próxima fatia**; **S5.2 ✅ 24/09 (peças `B70` resultset-como-linhas-Kof, `B72` OK/affected do `COM_QUERY`); S5.3 ✅ 24/09 (peça `B71` substituição client-side dos binds); S5.4 ✅ 24/09 (peça `B73` `connect` cross real, peça `B47b` o dispatch mysql do `execute`/`query` + `transaction { }`; a frente de paridade de esquema está feita); S5.5 (ORM sobre mysql no cross) 🟡 EM ANDAMENTO 24/09 — `orm.count` (fatia 1, `RtB74`/`RtB50`), `orm.count_where` (fatia 2, `RtB53`) e `orm.delete`/`deleteAll` (fatia 3, `RtB75`/`RtB50`/`RtB54`) são REAIS no cross (x86-64 + riscv64 + aarch64, qemu); `orm.save` (fatia 4a ✅: exec que lança `kof_orm_mysql_exec`/`RtB76`, provada em riscv64+aarch64 qemu — o corpo do `save` e o patch de `LAST_INSERT_ID` são a 4b) e as faces row-object (`find`/`all`/`where`/`page`, ABI de coluna tipada) seguem sqlite-only no cross**

---

## Por quê

Perguntada qual gap-code usar para a **aceitação silenciosa** de schemes não
suportados no Native (§421: `kof_db_connect` devolve um handle tipo-0 e a falha
aparece tarde no `.Lorm_conn`), a mantenedora respondeu **paridade total**: todo
alvo deve **aceitar** `mariadb`, `mysql`, `sqlite`, `mongodb`, … — sem endpoint de
gap-code. Isso generaliza o `D-DB-GAPS` DB-3 (estender MySQL a riscv64/aarch64)
numa meta de **paridade de schemes entre alvos**.

Isto **não** é mudança de superfície congelada: a API `kof.db`/`kof.orm` não muda —
o plano só **alarga o conjunto de URLs aceitas**, cada scheme **real** (R6). Um
scheme não suportado é **gap interino honesto** enquanto sua fatia pousa (R6: um
diagnóstico, nunca silêncio) — nunca recusa permanente, nunca aceite silencioso.

## Estado medido (corrigido 23/09/2026 — medição, não memória)

> A tabela anterior usava nomes de scheme crus como atalho para a **URL JDBC** no
> JVM/Android/JS. Isso era enganoso: `sqlite:`/`mysql://` são aceitos **como
> escritos** só no **Native**. No JVM/Android/JS o mesmo scheme precisa ser URL
> `jdbc:` (`jdbc:sqlite:`, `jdbc:mysql://`, …) — um scheme nu não-JDBC é `DB001`
> nomeado. `mongodb://` é a exceção (JVM/Android o tratam à parte).

| URL como escrita | JVM | Android | JS | Native (x86/riscv/aarch) |
|---|---|---|---|---|
| `jdbc:sqlite:` / `jdbc:h2:` | ✅ JDBC (driver) | ✅ JVM | ✅ JDBC host | — |
| `sqlite:` (nu) | ❌ `DB001` (use `jdbc:` no JVM/JS) | ❌ `DB001` | ❌ `DB001` | ✅ `sqlite3` link-by-use |
| `jdbc:mysql:` / `jdbc:mariadb:` / `jdbc:postgresql:` | ✅ JDBC (driver no cp) | ✅ JVM | ✅ JDBC host | — |
| `mysql://` / `mariadb://` (nu) | ❌ `DB001` (não é JDBC) | ❌ `DB001` | ❌ `DB001` | ⚠️ x86: wire `mysql://` + alias `mariadb://` (23/09); riscv/aarch `DB001` (wire não portado → R7) |
| `mongodb://` | ✅ real (driver via reflexão) | ✅ JVM | ❌ `DB001` — gap declarado (S3) | ❌ `DB001` |
| `jdbc:oracle:` | ✅ JDBC com driver no cp, senão `DB001` | ✅ JVM | ✅ JDBC host | — |
| qualquer outra / driver ausente | ❌ `DB001` nomeado (S2) | ❌ `DB001` nomeado | ❌ `DB001` nomeado | ❌ `DB001` nomeado (S0) |

- **JVM/Android/JS** aceitam qualquer URL **`jdbc:`** com driver no classpath; o
  delegate JS **é** o JDBC do host. Driver ausente agora é `DB001` **nomeado**
  (S2), nunca `SQLException` cru.
- **`mongodb://`** é real no JVM/Android (driver via reflexão) e `DB001`
  declarado no JS/Native (S3). O Native não vai criar servidor Mongo caseiro (R9).
- **Native** parseia só `sqlite:` e `mysql://`/`mariadb://`; qualquer outro scheme
  recusa com `DB001` nomeado no connect (S0). `kof_db_type` reserva
  **1=sqlite 2=mysql 3=oracle 4=mongo**.
- **Questão de design aberta (rule 6, NÃO é edição de agente):** JVM/Android/JS
  devem **normalizar** um scheme nu (`mysql://`, `sqlite:`) para seu equivalente
  `jdbc:` para que a *mesma URL* funcione em todo alvo? Hoje o chamador precisa
  escrever `jdbc:` no JVM/JS e a forma nua no Native. A normalização é frágil para
  credenciais (`mysql://user:pass@host` vs `?user=&password=`) — a mantenedora decide.
- Referência: `docs/stdlib/DATABASE_VISION.md` (Níveis 0–4, face Mongo no JVM,
  SQLite nativo real, MySQL/MariaDB nativo em andamento).

## Aceitação (definição de paridade)

Um scheme está **em paridade** quando o **mesmo programa Kof** (connect →
execute/query → roundtrip tipado) produz o **mesmo resultado observável** nos
quatro alvos, ou um **diagnóstico declarado `XXX00x`/`DB00x`** onde um alvo
genuinamente não consegue rodar (nunca divergência silenciosa). E2E cross-target
por scheme é a prova.

## Fatias

- **S0 — diagnóstico interino honesto (limpa o §421). ✅ FEITO 21/09 (sessão 9092).**
  No Native, `kof_db_connect`/`kof_db_connect2` agora **recusam** scheme fora de
  `sqlite:`/`mysql://` com o código nomeado (`DB001: unsupported db scheme …`)
  lançado no connect, em vez de handle nulo silencioso que só morria depois no
  `.Lorm_conn`. Falhas reais de conexão (auth / limite de slot / `sqlite3_open`)
  ficaram em `.Ldb_connect_bad`. x86-64 + riscv/aarch (`NativeRiscvAsmRtB47`,
  aarch pelo translator). Também tornei `NativeBackend.connectsToMysql` link-by-use
  real (só literal `mysql://`/`mariadb://`/`jdbc:mysql://` liga `libmariadb`), para
  a sonda linkar+rodar em host sem a lib. Transitório: removido por scheme
  conforme S1–S4 pousam. *Prova (medida, skipped=0):*
  `MakealiveDbStateE2ETest.stateSurfaceNativeNeverSilent` verde +
  `KofDbE2ETest.nativeUnsupportedSchemeNamesGapNotSilent` (rc≠0, `DB001`, sem
  `unknown db connection`) + `NativeDbSchemeRefusalAsmTest` (codegen
  determinístico) + `LinkByUseTest` 3/3.
- **S1 — `mariadb://` = alias mysql-wire (Native, 3 arcos). ✅ FEITO em x86-64
  23/09 (lane gaps-db).** O `kof_db_connect_inner` (`RuntimeDb2`) casa
  `mariadb://` e reusa o caminho `mysql://` (`r12 = schemeStart+2`, para o
  `leaq 8(%r12)` compartilhado cair após o scheme de 10 chars); `kof_db_type`
  reporta a família mysql (2), então `execute`/`query`/ORM pegam o wire. A
  mensagem `DB001` agora lista `mariadb://`. No riscv64/aarch64 tanto `mysql://`
  quanto `mariadb://` seguem recusando com `DB001` (wire mysql cross não
  portado — R7 honesto). *Prova:* `KofDbE2ETest#nativeMariadbAliasWireProtocol`
  — MariaDB real (KOF_MYSQL_PORT), nas duas formas `user:pass@host` e só-host,
  byte-idêntico `{"id":7,"name":"Alias"}`; `KofDbE2ETest` 28/0F +
  `NativeDbSchemeRefusalAsmTest` 2/2. **Diagnóstico cross corrigido 23/09:** a
  mensagem `DB001` do riscv64/aarch64 ANUNCIAVA `mysql://` como suportado
  enquanto o código cross o recusa — agora diz a verdade (`sqlite: only here;
  mysql:// / mariadb:// wire is x86-64 only`), travada por
  `NativeRiscvRuntimeSliceRegistryTest#crossDb001MessageDoesNotAdvertiseUnportedMysql`.
- **S2 — paridade de schemes JDBC JVM/JS/Android. ✅ FEITO 23/09 (lane gaps-db).**
  A medição por-driver já é provada pelo corpus E2E — `h2` (`KofDbE2ETest`
  execute/query/tipado), `sqlite` (`KofOrmE2ETest` `jdbc:sqlite:`, find/save/page
  no JVM), `mariadb` (`KofOrmE2ETest#mariadbCrud` + o oráculo Oracle JVM da F2d,
  servidor real), `postgres` (`KofOrmE2ETest#postgresCrud`, skip sem servidor) —
  tudo pelo mesmo caminho `DriverManager` para o qual JS/Android delegam. O que
  faltava era o **diagnóstico**: URL JDBC cujo driver está ausente vazava um
  `SQLException: No suitable driver` cru. Agora
  `JvmConfigRuntime.kof_db_connect/connect2` (JVM, e Android pelo mesmo runtime) e
  `KofJsDbBridge.connect/connect2` (delegate JS) mapeiam isso para o **nomeado**
  `DB001: no JDBC driver for this URL (add the driver to the classpath): <url>`,
  enquanto uma falha **real** de conexão (servidor fora / credencial ruim) passa
  **intacta** — nunca mascarada como `DB001`. *Prova:*
  `KofDbE2ETest#jvmMissingJdbcDriverNamesGapNotSilent` +
  `#jvmRealConnectionFailureIsNotRelabeledDb001` +
  `#jsMissingJdbcDriverNamesGapNotSilent` +
  `#jsRealConnectionFailureIsNotRelabeledDb001` (a do JVM VERMELHA no código
  antigo — `No suitable driver`); `KofDbE2ETest` 32/0F.
- **S3 — `mongodb://` interop-first (R9). ✅ FEITO 23/09 (lane gaps-db).**
  JVM/Android rodam de verdade: `KofOrmE2ETest#mongoCrud` (roundtrip ORM completo —
  save/find/where/count/saveAll/page/deleteAll — sobre o `mongodb-driver-sync`
  real). JS/Native são um `DB001` **declarado**, nunca silencioso:
  `KofJsDbBridge.connect/connect2` agora recusa `mongodb://` com
  `DB001: mongodb:// is not supported on the JS target yet (host driver bridge
  pending): <url>` (em vez da mensagem enganosa de "sem driver JDBC"), e o Native
  recusa via S0. No JVM, driver mongo ausente também é nomeado (`DB001: mongodb://
  needs the mongodb-driver-sync on the classpath`). Sem servidor caseiro (R9).
  *Prova:* `KofDbE2ETest#jsMongodbSchemeNamesGapNotSilent` +
  `#jvmMongodbMissingDriverNamesGap`; `KofOrmE2ETest#mongoCrud` (JVM real).
- **S4 — `jdbc:oracle:` (mesma rota do S3). ✅ FEITO 23/09 (lane gaps-db,
  declarado).** Não há driver/servidor Oracle neste host, e Oracle — como Mongo —
  nunca será servidor caseiro (R9). No JVM/Android/JS, `jdbc:oracle:` conecta com o
  driver no classpath, senão o `DB001` nomeado do S2 dispara; `oracle://` nu é
  `DB001` (não é URL JDBC). O Native recusa via S0. *Prova:* os testes de
  diagnóstico do S2 cobrem o caminho driver-ausente genericamente; nenhum E2E
  específico de servidor é possível aqui (declarado, não silencioso).
- **S5 — wire `mysql://`/`mariadb://` no cross (riscv64/aarch64). PLANEJADO —
  dimensionado 23/09 (lane gaps-db).** É a frente multi-sessão por trás do `DB001`
  honesto do cross; o Native fecha por último (R7), então roda depois dos demais.

  **Superfície x86 medida a reproduzir** (o wire vive em `runtime/RuntimeDb*.java`
  + `RuntimeNet`; o runtime cross hoje só tem a FFI SQLite):
  | peça x86 | Responsabilidade |
  |---|---|
  | `RuntimeNet` (`kof_net_write`/`kof_net_read`) | socket + framing de leitura/escrita TCP |
  | `RuntimeDb1` (`kof_sec_sha1_*`, `kof_db_mysql_scramble`, `kof_db_mysql_lenenc`, `kof_db_mysql_render`) | SHA1 + scramble de auth + inteiros length-encoded |
  | `RuntimeDb2` (`kof_db_connect_inner`, `kof_db_mysql_next`, `.Ldb_scheme_*`, `.Ldb_up_*`, `.Ldb_res_parse`) | parse de scheme/URL, leitura do handshake, parse do resultset |
  | `RuntimeDb3` (`.Ldb_auth_*`, `.Ldb_connect_register`) | auth switch + registro |
  | `RuntimeDb4` (`kof_db_bind/close/execute/transaction`) | dispatch para os ramos sqlite/mysql |
  | `RuntimeDb6` (`kof_db_mysql_*`) | tratamento de valor/coluna |

  **Fatias (uma sessão cada, cada uma com a própria prova):**
  - **S5.0 — camada de socket cross. ✅ SATISFEITA (medido 23/09).** A HAL cross
    (`NativeRiscvAsmRt0`) já expõe `kof_plat_net_socket` (198) /
    `kof_plat_net_connect` (203) / `kof_plat_read` (63) / `kof_plat_write` (64) /
    `kof_plat_close` (57), e o core HTTP cross (`NativeRiscvHttpCore`) já conduz TCP
    real com eles sob qemu — `KofHttpNativeResilienceCrossTest` abre/lê/escreve/
    re-tenta sockets de verdade no riscv64/aarch64. **Nenhum wrapper novo é
    necessário:** o wire DB constrói direto sobre os mesmos primitivos (write num fd
    funciona em socket), exatamente como o core HTTP faz. (Os wrappers `kof_net_*`
    do x86 são conveniência do x86, não requisito.)
  - **S5.1 — handshake + auth.** Portar SHA1/scramble/lenenc + ler o greeting +
    enviar a resposta de handshake/auth-switch. *Prova:* programa cross conecta no
    MariaDB real sob qemu e o servidor devolve o pacote OK.
    - **23/09 — primeira fatia FEITA (peça `B62`, lane gaps-db):** SHA1 (entrada
      curta, `len < 56` — o caminho de auth nunca hasheia mais) portado do
      `RuntimeDb1` x86 (`kof_sec_sha1_block` + `kof_sec_sha1_internal`) mais
      `kof_bswap32`/`kof_bswap64`. Prova: `NativeRiscvDbWireTest` roda SHA1 em
      riscv64 + aarch64 (qemu) contra o oráculo `MessageDigest` do JVM em 3
      vetores (vazio, `abc`, o pangrama de 43 bytes), mais um teste de sabotagem
      provando que a peça é a exercitada (sem `B62`, o `ld` falha undefined).
      Lição cross (load-bearing): `lw` no RV64 SIGN-ESTENDE (o `movl` x86 zera) —
      todo load de 32 bits que alimenta `srli`/`slli` precisa de zero-extend
      explícito, e as palavras de trabalho de 32 bits devem ser zext a cada round
      (RV64 não tem GPR de 32 bits).
    - **23/09 — segunda fatia FEITA (peça `B63`, lane gaps-db):** o helper de
      auth em si — `kof_db_mysql_scramble` (`mysql_native_password`:
      `SHA1(pass) XOR SHA1(seed || SHA1(SHA1(pass)))`, sobre o SHA1 da B62) e
      `kof_db_mysql_lenenc` (inteiro length-encoded: `<0xFC` / `0xFC`+2 LE /
      `0xFD`+3 LE) portados do `RuntimeDb1`. Prova: o mesmo harness
      `NativeRiscvDbWireTest` em riscv64 + aarch64 contra um oráculo JVM
      (`MessageDigest` + a fórmula padrão do scramble) para seed/password fixos,
      mais os 3 casos de lenenc e um teste de sabotagem da B63.
    - **23/09 — terceira fatia FEITA (peça `B64`, lane gaps-db):** o parser do
      greeting do servidor — `kof_db_mysql_parse_greeting` percorre o pacote de
      handshake (protocolo 0x0A, versão NUL-terminated, conn-id, as duas metades
      do auth-plugin-data) e extrai os 20 bytes do seed, exatamente como o
      `RuntimeDb3` faz antes do `kof_db_mysql_scramble`. Prova:
      `NativeRiscvDbWireTest` parseia um greeting sintético do MariaDB + um
      pacote com protocolo ruim nas 2 archs contra um oráculo fixo, mais um
      teste de sabotagem da B64.
    - **23/09 — quarta fatia FEITA (peça `B65`, lane gaps-db):** o montador do
      handshake response — `kof_db_mysql_build_auth_response` escreve o frame
      (len 3 bytes LE + seq 1) e o payload (capabilities `0x0008820B`,
      max-packet, charset, 23 bytes reservados, user, auth response
      `<20>+scramble` ou vazio, database, plugin `mysql_native_password`),
      espelhando o `RuntimeDb3`. Prova: `NativeRiscvDbWireTest` monta para
      `passLen=20` e vazio nas 2 archs contra um oráculo fixo, mais um teste de
      sabotagem da B65.
    - **23/09 — quinta/última fatia FEITA (peça `B66`, lane gaps-db):** o
      round-trip de socket — `kof_db_mysql_handshake(fd, user, pass, db)` lê o
      greeting, parseia o seed, calcula o scramble, monta e envia a resposta e
      lê OK/ERR, sobre a HAL `kof_plat_net_*`. **S5.1 ✅ SATISFEITA:**
      `NativeRiscvDbWireTest` dirige em riscv64 + aarch64 (qemu) contra o
      **MariaDB real** — credenciais corretas devolvem `0` (pacote OK recebido) e
      um banco inexistente devolve `-1` (Err 1049). (O host roda MariaDB com
      `--skip-grant-tables`, então o eixo passa/falha é o erro de DB inexistente,
      não a senha; o scramble em si é provado contra o oráculo JVM na B63.) O
      ramo `AuthSwitchRequest` não é atingido por este servidor (native password
      é o default) e fica como caminho declarado/diagnosticado para uma fatia
      futura caso um servidor o negocie.
  - **S5.2 — `COM_QUERY` + resultset texto.** Portar framing + parse do resultado.
    **Parcial ✅ 23/09 (peça `B67`):** o framing do request + o 1º pacote de
    resposta estão portados — `kof_db_mysql_command(fd, sql, buf, buflen)` envia
    `[0x03][sql]` (comprimento 3 bytes little-endian, seq 0) e devolve o 1º
    payload para o chamador classificar (`>=1` column count, `0x00` OK, `0xFF`
    ERR). Provado em riscv64 + aarch64 (qemu) contra o **MariaDB real**:
    `SELECT 1` → `1`, `SET @x=1` → `0`, SQL ruim → `255`.
    *Prova:* `NativeRiscvDbWireTest#commandClassifiesResponseAgainstRealMariaDb*`.
    **Parcial ✅ 23/09 (peças `B68`–`B69`):** o reader de pacotes
    (`kof_db_mysql_reset`/`next`, port do `RuntimeDb2`) e o parse do cabeçalho do
    resultset (`kof_db_mysql_query_text(fd, sql)` → `ncols` + o payload cru da
    primeira linha via células lenenc) estão portados. Provado em riscv64 +
    aarch64 (qemu) contra o **MariaDB real**: `SELECT 1` → 1 coluna / linha
    `[0x01,'1']`; `SELECT 1,'ab'` → 2 colunas / linha `[0x01,'1',0x02,'a','b']`.
    *Prova:* `NativeRiscvDbWireTest#resultsetHeaderAgainstRealMariaDb*`.
    **Feita ✅ 24/09 (peça `B70`, lane gaps-db):** query texto completa —
    `kof_db_mysql_query(fd, sql)` envia o `COM_QUERY`, lê as definições de
    coluna (nomes), itera TODAS as linhas e materializa um JSON object
    `{"col":valor,…}` por linha numa `List<KofString>` (port do `kof_db_query`
    x86 em `RuntimeDb5/Db6`). Regras de valor seguem o contrato JVM
    (`kof_db_row_to_json` em `JvmConfigRuntime`, o oráculo Kof): NULL →
    literal `null` sem aspas; só-dígitos → número cru; resto (incl. string
    vazia) → `json_encode_string`. Provado em riscv64+aarch64 (qemu) contra o
    **MariaDB real**: `SELECT 1` → `{"1":1}`; `SELECT 1,'ab'` →
    `{"1":1,"ab":"ab"}`; `UNION ALL` → 2 linhas;
    `SELECT NULL AS n,'a"b' AS s` → `{"n":null,"s":"a\"b"}`.
    *Prova:* `NativeRiscvDbWireTest#queryAllRowsAgainstRealMariaDb*` +
    o teste de sabotagem da B70. **Divergência honesta achada no caminho
    (§488, ABERTA):** o caminho mysql do x86 (`RuntimeDb5 .Ldb_mysql_null`)
    emite NULL como string VAZIA crua (JSON inválido `{"n":,`); a B70 NÃO
    copia o bug — mesma postura da face NULL do cross-sqlite B47.
    Faltam nas próximas fatias: prepared/tx/ORM (S5.3) + link/paridade (S5.4).
    *Critério de conclusão:* roundtrip `db.query` sob qemu, byte-idêntico ao x86/JVM.
  - **S5.3 — bind/prepared + tx + ORM.** Portar o dispatch de prepared/execute/
    transaction. **Fatia 1 ✅ 24/09 (peça `B71`, lane gaps-db):** os helpers de
    bind client-side — `kof_db_mysql_render(val)` (Int → decimais, KofString →
    `'escaped'`) + `kof_db_mysql_replace_q(sql, literal)` (só o 1º `?`) — port
    do fallback do x86 em `RuntimeDb1`/`RuntimeDb2`/`RuntimeDb4`.
    *Prova:* `NativeRiscvDbWireTest#bindRenderReplaceMatchesOracle*` + sabotagem
    da B71 (riscv64 + aarch64, qemu). **Fatia 2 ✅ 24/09 (peça `B72`,
    lane gaps-db):** `kof_db_mysql_execute(fd, sql)` — COM_QUERY via B67 +
    affected-rows do OK-packet (1 byte / FC+2LE / FD+3LE), port da cauda de
    execute do x86 (subst de `RuntimeDb4` + done/afc/afd/bad de `RuntimeDb5`);
    erro/ERR/resultset → 0. Query com binds não precisa de peça nova (B71
    substitui + B70 consulta). *Prova:*
    `NativeRiscvDbWireTest#execWithBindsAgainstRealMariaDb*` + sabotagem da B72
    (riscv64 + aarch64, qemu, MariaDB real: CREATE 0, INSERT×2 com binds
    Int/String incl. escape de quote 1, UPDATE 1, DELETE sem-match 0 / com-match
    1, SQL ruim 0, SELECT-via-execute 0, coerência da query com bind). Falta: o
    dispatch mysql de executeN/queryN nos corpos da B47 (pousa com o link da
    S5.4, quando um fd mysql puder alcançá-los) + tx + ORM. *Prova:* E2E
    `orm.*` sob qemu.
  - **S5.4 — link + teste de paridade.** `-lmariadb` link-by-use no cross + o
    espelho riscv/aarch de `KofDbE2ETest#nativeMariadbAliasWireProtocol`. Depois
    disso o `DB001` cross do S1 vira real. **Fatia 1 ✅ 24/09 (peça `B73`, lane
    gaps-db):** o `connect` cross real — parse de `mysql://`/`mariadb://`
    (`[user[:pass]@]host[:port][/db]`, IPv4 dotted + fallback 127.0.0.1 do
    x86), socket/connect pela HAL, handshake da B66 e registro do fd como type
    2 nas tabelas da B47; o `DB001` honesto agora lista só os esquemas
    portados. *Prova:* `NativeRiscvDbWireTest#connectMysqlAgainstRealMariaDb*`
    + `withoutConnectPieceLinkFailsSabotage` (riscv64 + aarch64, qemu, MariaDB
    real — formas userinfo e host-only `kof_db_connect2` autenticam) +
    `KofDbE2ETest#crossNativeUnsupportedSchemeNamesTruthfulDb001`.
    **Fatia 2 ✅ 24/09 (peça `B47b`, lane gaps-db):** o dispatch mysql do
    `executeN`/`queryN` — um handle type-2 resolvido agora chega ao wire via
    `db.execute`/`db.query` (os corpos despacham por `kof_db_type`: 2 → B71
    substitui + B72/B70, senão o ramo sqlite; o dispatch não cabia mais no
    frame da B47, então vive na `B47b` e a B47 fica com resolve/type/connect/
    close/bind/transaction). O `kof_db_connect` zera user2/pass2 (assinatura
    host-only) para a URL sem userinfo autenticar; o `kof_db_close` fecha type 2
    via `kof_plat_close`. **O `transaction { }` veio de graça** — o BEGIN/COMMIT/
    ROLLBACK da B47 chamam `kof_db_execute`, que agora despacha. O `-lmariadb` é
    **moot**: o wire cross é auto-contido (sockets crus + SHA1 próprio), nenhum
    driver externo é linkado. *Prova:*
    `KofDbE2ETest#crossNativeMariadbAliasWireProtocol` (o espelho riscv/aarch,
    `mariadb://` com userinfo e host-only, saída de linha real) +
    `#crossNativeMariadbTransactionCommits` / `#...RollsBackOnFailure`
    (BEGIN/COMMIT/ROLLBACK reais sobre COM_QUERY) +
    `NativeRiscvDbWireTest#dispatchExecuteQueryAgainstRealMariaDb*` +
    `withoutDispatchPieceLinkFailsSabotage` (riscv64 + aarch64, qemu, MariaDB
    real). **S5.4 completo; a frente de paridade de esquema está feita.**
  - **S5.5 — `kof.orm` sobre o wire mysql no cross (NOVO, 24/09).** As faces
    row-object do cross (`RtB50`/`RtB53`/`RtB55`/`RtB55Helpers`/`RtB57`/`RtB58`…)
    chamam `sqlite3_*` direto no handle do `kof_orm_conn`, que recusa tudo que
    não é type 1 (o `kof_orm_conn` lança `unknown db connection: db1` para um
    handle type-2 — medido 24/09). O compile é limpo (sem `ORM001`); a recusa é
    honesta mas **mal nomeada** e o ORM mysql está ausente. A referência x86 é a
    família `RuntimeOrmMysql*` (`RuntimeOrmMysqlDdl`, `RuntimeOrmMysqlCountWhere`,
    `RuntimeOrmMysqlSave`, `RuntimeOrmMysqlFieldLit`) — **o dialeto importa**: o
    MariaDB rejeita a citação sqlite `"table"` (medido: `SELECT COUNT(*) FROM
    "q"` → `ERROR 1064`); o caminho mysql deve citar com backtick
    (`` `table` ``).     **Fatias (cada uma com prova qemu em riscv64 + aarch64):**
    1. **scalar + `orm.count`** — primitiva nova cross
       `kof_db_mysql_scalar_int(fd, sql) -> Long` (COM_QUERY → 1ª linha, 1ª
       coluna) + `kof_orm_count` ramifica type 2 → `SELECT COUNT(*) FROM
       `table`` com backtick. *Prova:* `orm.count<User>` sobre `mysql://` cross.
       **FEITO 24/09** — peça `RtB74` (`kof_db_mysql_scalar_int`, reusa
       `kof_db_mysql_query_text`/B69) + ramo type 2 da `RtB50` com dialeto
       backtick; E2E `KofOrmE2ETest#crossNativeMariadbCountMatchesX86Oracle`
       verde no oráculo x86-64 + riscv64 + aarch64 (`3` → `2` após `DELETE`
       bindado).
    2. **`orm.count_where`** — substituição de bind (B71) + a citação mysql, com
       o bind nulo tratado (a B71 rende ponteiro 0 como Int 0 hoje).
       **FEITO 24/09** — o ramo type-2 da `RtB53` monta `SELECT COUNT(*) FROM
       `t` WHERE `f` = <literal>` com nomes em backtick, despachando o literal
       ao renderizador compartilhado `kof_orm_mysql_lit` (box §284 int/long/bool/
       double/float → `kof_*_to_string`, KofString via `kof_db_mysql_render`,
       null → `NULL`, senão ORM001) — promovido a global na `RtB75` (fatia 3)
       para uma cópia só servir `count_where` e `delete`. A
       mesma unidade corrigiu um bug **x86** latente: o
       `RuntimeOrmMysqlCountWhere` checava o tag do box como 1 em vez de 3 para
       Bool → ORM001 em qualquer bind booleano (catalogado §492).
       *Prova:* `KofOrmE2ETest#crossNativeMariadbCountWhereMatchesOracles` —
       JVM + x86-64 + riscv64 + aarch64 byte-idênticos em string/ausente/
       injeção/negativo/positivo/bool (`1\n0\n0\n1\n0\n1\n1\n1`).
    3. **`orm.delete`/`deleteAll` sobre mysql** (parte da fatia 3).
       **FEITO 24/09** — peça nova `RtB75`: `kof_orm_delete_mysql(id,key,table,
       schema)` e `kof_orm_delete_all_mysql(id,table,schema)` montam ``DELETE
       FROM `t`[ WHERE `pk` = <lit>]`` (o `<lit>` pelo renderizador
       compartilhado) e despacham via `kof_db_resolve` + `kof_db_mysql_execute`
       (B72). A `RtB50` (`delete_all`) e a `RtB54` (`delete`) ramificam em
       `kof_db_type == 2`. A **semântica espelha o x86, que é a referência do
       contrato (D-DB-GAPS)**: o x86 `.Lorm_da_my`/`.Lorm_del_my` roda o
       `kof_db_execute` **genérico** (`.Ldb_exec_bad` → `0`, sem throw) e o
       chamador devolve `affected >= 0` → `true` no sucesso **e no ERR**. Então o
       cross reusa a B72 (sem throw) — **não** precisa de exec que lança aqui. A
       `RtB54` também corrigiu um off-by-one no próprio ramo mysql novo: o
       prólogo derrama os regs callee-saved **antes** de atribuir os args, então
       `key`/`table`/`schema` devem ser lidos dos `s2`/`s3`/`s4` **vivos**, não
       dos slots da pilha (a 1ª tentativa leu os regs velhos do chamador →
       ORM001/segfault; isolado com scratch sob qemu `-strace`/`-d in_asm`). A
       divergência JVM↔Native no caminho de **erro** (JVM lança, Native devolve
       `true`) é **pré-existente** e catalogada **§493** — RESOLVIDA 26/09 por
       `D-DECISION-BATCH-2609` item 1 (a JVM é a lei): delete/deleteAll agora
       LANÇA em todo target Native (x86 roteia `.Lorm_del_my`/`.Lorm_da_my` por
       `.Lorm_sa_exec`; o cross `RtB75` chama `kof_orm_mysql_exec`/B76) —
       §493 CORRIGIDA.
       *Prova:* `KofOrmE2ETest#crossNativeMariadbDeleteAndDeleteAllMatchesOracles`
       — JVM + x86-64 + riscv64 + aarch64 byte-idênticos em hit/miss/negativo/
       idempotente (`3\ntrue\n2\ntrue\n2\ntrue\n2\ntrue\ntrue\n0`) — e
       `#crossNativeMariadbDeleteErrorsThrowLikeX86Oracle` (26/09, §493: Native
       x86-64 == riscv64 == aarch64 no ERR de tabela inexistente — os três lançam
       `mysql: Table 'test.user' doesn't exist`; RED pré-fix: todos `true`).
    4. **`orm.save`** — caminho INSERT; a chave gerada precisa de
       `SELECT LAST_INSERT_ID()` (o scalar da `RtB74`) e dos literais de campo.
       Esta é a **única** face cuja referência x86 **lança** no ERR
       (`RuntimeOrmMysqlExec`/`.Lorm_sa_exec`), então é a que precisa de uma
       primitiva exec cross que lança (`kof_orm_mysql_exec`, port daquela
       referência) — ver a nota de design abaixo.
       **Fatia 4a FEITA 24/09** — a primitiva exec que lança está landada na
       `RtB76`: `kof_orm_mysql_exec(fd, sql) -> affected | throw` (OK → lenenc
       affected; ERR → `mysql: <msg>` com teto 400; perda/resposta estranha →
       `mysql: connection lost`). *Prova:* `NativeRiscvDbWireTest` — harness em
       riscv64 + aarch64 sob qemu contra o MariaDB real
       (`0\n1\n1\n1\n0\n1`) mais o throw do ERR (`mysql: …`, exit 1) e a
       sabotagem do link sem a B76. **Fatia 4b FEITA 24/09** — o corpo do `kof_orm_save` pousou na
       `RtB77` (`kof_orm_save_mysql`), ramificando por `kof_db_type == 2` no
       topo do `kof_orm_save` (antes do `kof_orm_conn`, que recusa type≠1).
       Espelha as 3 saídas medidas: (1) PK nula/0 → INSERT **sem** a coluna PK
       + `SELECT LAST_INSERT_ID()` (`kof_db_mysql_scalar_int`/`RtB74`) e **nova
       instância** com a PK patchada (`kof_alloc` + `kof_init_object` +
       `kof_memcpy`); (2) PK != 0 → ``UPDATE `t` SET `f` = <lit>,… WHERE `pk` =
       <lit>`` → mesmo ponteiro quando acha linhas; (3) UPDATE 0 linhas →
       INSERT de todas as colunas (upsert) → mesmo ponteiro. Critério de PK =
       x86 (int/long == 0, double/float truncado == 0 com os sentinelas
       `INT64_MIN`/`MAX`, String null → INSERT, bool nunca). Identificadores
       com backtick; literais de campo pelo `typeCode` do schema; exec que
       LANÇA `kof_orm_mysql_exec` (`RtB76`). *Prova:*
       `KofOrmE2ETest#crossNativeMariadbSaveMatchesOracles` — JVM + x86-64 +
       riscv64 + aarch64 byte-idênticos — e
       `#crossNativeMariadbSaveErrorMatchesX86Oracle` (tabela inexistente
       lança em x86 == riscv64 == aarch64). **Fatia 4c FEITA 24/09** —
       `orm.saveAll` sobre mysql veio de graça: `kof_orm_save_all` (B56) só
       faz loop e delega ao `kof_orm_save`, que agora desvia type 2 para a
       B77. *Prova:*
       `KofOrmE2ETest#crossNativeMariadbSaveAllMatchesOracles` — JVM + x86-64
       + riscv64 + aarch64 byte-idênticos.
    5. **`orm.find`/`all`/`where`/`where_op`/`page`** — materialização de linhas.
       **Fatia 5a (`find`) FEITA 24/09** como B78 (`kof_orm_find_mysql`): o
       walk do resultset direto (reusando o reader B68 + lenenc B63), casa as
       colunas por **NOME** contra o schema e converte cada célula pelo
       **typeCode**; miss → `null`, ERR → `mysql: <msg>`, campo do schema sem
       coluna → `mysql: no column <nome>` (R6). *Prova:*
       `KofOrmE2ETest#crossNativeMariadbFindMatchesOracles` — JVM + x86-64 +
       riscv64 + aarch64 byte-idênticos. **Fatia 5b (`all`) FEITA 24/09** como
       B79 (`kof_orm_all_mysql`, dispatch na B58): mesmo walk,
       `SELECT * FROM `t`` (sem bind), um record por linha,
       **lista vazia** (nunca null) sem linhas; dead/ERR/`no column` → throw.
       *Prova:* `KofOrmE2ETest#crossNativeMariadbAllMatchesOracles` —
       byte-idênticos. **Fatia 5c (`where`/`where_op`) FEITA 24/09** como B80 +
       `B80Helpers` (`kof_orm_where_mysql` + `kof_orm_mysql_op`, dispatch na
       B59): ``SELECT * FROM `t` WHERE `f` <op> ?`` com whitelist do op idêntica
       ao host (`==`→`=`, `>`/`<`/`>=`/`<=`/`!=`/`LIKE`, senão throw
       `ORM operator not allowed: <op>`). *Prova:*
       `KofOrmE2ETest#crossNativeMariadbWhereMatchesOracles` — byte-idênticos.
       **Fatia 5d (`page`) FEITA 24/09** como B81 + `B81Helpers`
       (`kof_orm_page_mysql`, dispatch na B60):
       ``SELECT * FROM `t` LIMIT <lim> OFFSET <off>`` (lim/off boxeados →
       `((Number)x).intValue()`), mesmo walk, página vazia = lista vazia.
       *Prova:* `KofOrmE2ETest#crossNativeMariadbPageMatchesOracles` —
       byte-idênticos. **S5.5 COMPLETA**: todas as faces de linha
       (`find`/`all`/`where`/`where_op`/`page`) + as escritas
       (`save`/`saveAll`/`delete`/`deleteAll`/`count`/`count_where`) agora rodam
       no cross sobre o wire MySQL, byte-idênticas ao host — linhas 15/16 do
       `PARITY-GAPS` fechadas.
     **§523 CORRIGIDA 27/09 (lane issues, por ordem da mantenedora; causa raiz
     da lane db reaproveitada):** connect com auth rejeitada não lançava nada
     (handle morto + SIGSEGV no query seguinte) — o ERR do handshake agora
     lança `mysql: <msg>` no x86 (`RuntimeDb3 .Ldb_auth_done`/primeiro-pacote)
     e no cross (B66); falhas de socket/connect/leitura lançam
     `mysql: connection lost` (escopo votado). Prova RED-first hermética +
     perna lei-JVM em `KofDbE2ETest`; pin-flip no `NativeRiscvDbWireTest`.
     Fixtures user-land `~/.local/share/kof-mariadb` (13306 skip-grant, 13307
     auth). Até cada uma pousar, fica **gap interino declarado** (nunca aceite
     silencioso), e a mensagem do `kof_orm_conn` deve nomear a causa real (ORM
     mysql ainda não portado) em vez de `unknown db connection`.

    **Nota de design (corrigida 24/09; SUPERSEDEDA 26/09 por `D-DECISION-BATCH-2609`
    item 1 — a JVM é a lei, §493 CORRIGIDA):** o exec cross que lança
    (`kof_orm_mysql_exec`) pertencia **só ao `orm.save`** — o
    `delete`/`deleteAll` x86 **não** lançava (usava o `kof_db_execute` genérico).
    O `orm.delete`/`deleteAll` do JVM lança (JDBC), que era justamente a
    **divergência JVM↔Native catalogada §493**. **SUPERSEDEDA 26/09** por
    `D-DECISION-BATCH-2609` item 1 (regra 6 decidida — a JVM é a lei): o
    x86/cross `delete`/`deleteAll` agora passa pelo MESMO exec que lança
    (`.Lorm_sa_exec` / `kof_orm_mysql_exec`) do `save`, e a §493 está CORRIGIDA —
    as duas faces batem com o JVM no sucesso E no erro.
    *(Nota do cabeçalho corrigida 26/09: o bloco acima descrevia o estado
    pré-decisão.)*

## Não-objetivos / invariantes

- Sem sintaxe nova, sem mudança de API; sem edição de semântica congelada
  (behavior freeze).
- Interop-first (R9): drivers/protocolos vêm de libs externas auditadas, nunca
  reimplementados. Native fecha por último (R7), cada fatia honesta enquanto isso
  (R6).
- Pool de conexões segue o item PLANNED separado do `DATABASE_VISION.md` (não faz
  parte da paridade de schemes).
