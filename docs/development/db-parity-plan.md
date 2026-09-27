[English](db-parity-plan.md) | [Português](db-parity-plan.pt_BR.md)

# DB Parity Plan — every target accepts every scheme (mariadb, mysql, sqlite, mongodb, …)

> **IN DEVELOPMENT** — opened 21/09/2026 from the maintainer's directive
> (`D-DB-GAPS` addendum, `DECISIONS.md`). The plan is the queue; each slice
> lands with proof (Q0–Q7) and this doc is **moved to `docs/stdlib/`** only
> when parity is complete.

**Owner:** `gaps-db` lane (handed over 21/09 by order of the maintainer, under `D-DB-PARITY-OWNER`; S0/S1 authorized) · **Records/plan:** docs/plataforma lane
**Branch:** `beta-0.5.0` · **Status:** S0 ✅ DONE (21/09, session 9092) — honest native refusal of an unsupported scheme with the named `DB001` code, plus real link-by-use (no `libmariadb` link for non-mysql literals); **S1 ✅ DONE on Native x86-64 (23/09, gaps-db lane)** — `mariadb://` is a `mysql://` wire alias; cross stays honest `DB001` until the mysql wire is ported (R7) — **superseded by S5.4 slice 1 (24/09, piece `B73`): `mysql://`/`mariadb://` on the cross now connect for real**; **S2 ✅ DONE on JVM/JS/Android (23/09, gaps-db lane)** — a missing JDBC driver is now a named `DB001` diagnostic (real connection failures untouched); **S3/S4 ✅ DONE 23/09 (gaps-db lane)** — `mongodb://` real on JVM/Android and declared `DB001` on JS/Native; `oracle` declared (no driver/server on the host); **S5.0 (cross socket layer) ✅ SATISFIED 23/09 (measured — already proven by the cross HTTP core)**; **S5.1 (handshake+auth) ✅ SATISFIED 23/09 (pieces `B62`–`B66`): SHA1/bswap + scramble/lenenc + greeting parse + auth-response build + the socket round-trip, proven by `NativeRiscvDbWireTest` on riscv64+aarch64 (qemu, against the JVM oracle and against the REAL MariaDB — the server returns the OK packet; an unknown DB yields Err). The Kof surface (`db.connect` cross) now connects for real (S5.4 slice 1, piece `B73`) and only refuses non-ported schemes with the honest `DB001`**; **S5.2 (`COM_QUERY`) 🟡 PARTIAL 23/09 (pieces `B67`–`B69`) — request framing + first-response classification (`SELECT 1`→1, `SET`→0, bad SQL→255) AND the packet reader + resultset header (ncols + first-row lenenc payload: `SELECT 1`→1col/[01 31], `SELECT 1,'ab'`→2col/[01 31 02 61 62]), proven on riscv64+aarch64 (qemu) against the real MariaDB; materialising all rows as Kof values is the next slice**; **S5.2 ✅ 24/09 (pieces `B70` resultset-as-Kof-rows, `B72` OK/affected `COM_QUERY`); S5.3 ✅ 24/09 (piece `B71` client-side bind substitution); S5.4 ✅ 24/09 (piece `B73` real cross `connect`, piece `B47b` the `execute`/`query` mysql dispatch + `transaction { }`; the scheme-parity front is done); S5.5 (ORM over mysql on the cross) 🟡 IN PROGRESS 24/09 — `orm.count` (fatia 1, `RtB74`/`RtB50`), `orm.count_where` (fatia 2, `RtB53`) and `orm.delete`/`deleteAll` (fatia 3, `RtB75`/`RtB50`/`RtB54`) and `orm.save` (fatias 4a+4b, `RtB76`/`RtB77` — generated-PK INSERT + `LAST_INSERT_ID` patch, UPDATE hit, UPDATE miss → upsert) are REAL on the cross (x86-64 + riscv64 + aarch64, qemu); `orm.find`/`orm.all`/`orm.where`/`orm.page` (fatias 5a–5d, `RtB78`–`RtB81`) plus the writes are now REAL on the cross too (typed-column ABI inside the wire walk) — **S5.5 complete; `PARITY-GAPS` rows 15/16 closed****

---

## Why

The maintainer was asked which gap-code to use for the **silent acceptance** of
unsupported schemes on Native (§421: `kof_db_connect` returns a type-0 handle and
the failure surfaces late at `.Lorm_conn`). The answer was **full parity**: every
target must **accept** `mariadb`, `mysql`, `sqlite`, `mongodb`, … — no gap-code
endpoint. This generalizes `D-DB-GAPS` DB-3 (extend MySQL to riscv64/aarch64) into
a **cross-target scheme-parity** goal.

This is **not** a frozen-surface change: the `kof.db`/`kof.orm` API is unchanged —
the plan only **widens the set of accepted URLs**, each scheme **real** (R6). An
unsupported scheme is an **honest interim gap** while its slice lands (R6: a
diagnostic, never silence) — never a permanent refusal, never a silent accept.

## Measured state (corrected 23/09/2026 — measurement, not memory)

> The earlier table used bare scheme names as shorthand for the **JDBC URL** on
> JVM/Android/JS. That was misleading: `sqlite:`/`mysql://` are accepted **as
> written** only on **Native**. On JVM/Android/JS the same scheme must be a
> `jdbc:` URL (`jdbc:sqlite:`, `jdbc:mysql://`, …) — a bare non-JDBC scheme is a
> named `DB001`. `mongodb://` is the exception (JVM/Android special-case it).

| URL as written | JVM | Android | JS | Native (x86/riscv/aarch) |
|---|---|---|---|---|
| `jdbc:sqlite:` / `jdbc:h2:` | ✅ JDBC (driver) | ✅ JVM | ✅ host JDBC | — |
| `sqlite:` (bare) | ❌ `DB001` (use `jdbc:` on JVM/JS) | ❌ `DB001` | ❌ `DB001` | ✅ `sqlite3` link-by-use |
| `jdbc:mysql:` / `jdbc:mariadb:` / `jdbc:postgresql:` | ✅ JDBC (driver on cp) | ✅ JVM | ✅ host JDBC | — |
| `mysql://` / `mariadb://` (bare) | ❌ `DB001` (not JDBC) | ❌ `DB001` | ❌ `DB001` | ⚠️ x86: `mysql://` wire + `mariadb://` alias (23/09); riscv/aarch `DB001` (wire not ported → R7) |
| `mongodb://` | ✅ real (driver via reflection) | ✅ JVM | ❌ `DB001` — declared gap (S3) | ❌ `DB001` |
| `jdbc:oracle:` | ✅ JDBC when the driver is on cp, else `DB001` | ✅ JVM | ✅ host JDBC | — |
| any other / driver absent | ❌ named `DB001` (S2) | ❌ named `DB001` | ❌ named `DB001` | ❌ named `DB001` (S0) |

- **JVM/Android/JS** accept any **`jdbc:`** URL whose driver is on the classpath;
  the JS delegate **is** the host's JDBC. A missing driver is now a **named**
  `DB001` (S2), never a raw `SQLException`.
- **`mongodb://`** is real on JVM/Android (driver via reflection) and a declared
  `DB001` on JS/Native (S3). Native will not grow a home-grown Mongo server (R9).
- **Native** parses only `sqlite:` and `mysql://`/`mariadb://`; any other scheme
  refuses with the named `DB001` at connect time (S0). `kof_db_type` reserves
  **1=sqlite 2=mysql 3=oracle 4=mongo**.
- **Open design question (rule 6, NOT an agent edit):** should JVM/Android/JS
  **normalize** a bare scheme (`mysql://`, `sqlite:`) into its `jdbc:` equivalent
  so the *same URL* works on every target? Today the caller must write `jdbc:` on
  JVM/JS and the bare form on Native. Normalization is fragile for credentials
  (`mysql://user:pass@host` vs `?user=&password=`) — the maintainer decides.
- Reference: `docs/stdlib/DATABASE_VISION.md` (Levels 0–4, JVM Mongo face, native
  SQLite real, native MySQL/MariaDB in progress).

## Acceptance (parity definition)

A scheme is **at parity** when the **same Kof program** (connect → execute/query →
typed roundtrip) produces the **same observable result** on all four targets, or a
**declared `XXX00x`/`DB00x` diagnostic** where a target genuinely cannot run it
(never a silent divergence). Cross-target E2E per scheme is the proof.

## Slices

- **S0 — interim honest diagnostic (clears §421). ✅ DONE 21/09 (session 9092).**
  On Native, `kof_db_connect`/`kof_db_connect2` now **reject** a scheme outside
  `sqlite:`/`mysql://` with the named code (`DB001: unsupported db scheme …`)
  thrown at connect time, instead of a silent null handle that only died later at
  `.Lorm_conn`. Kept real connection failures (auth / slot limit / `sqlite3_open`)
  in `.Ldb_connect_bad`. x86-64 + riscv/aarch (`NativeRiscvAsmRtB47`, aarch via
  translator). Also made `NativeBackend.connectsToMysql` real link-by-use (only a
  literal `mysql://`/`mariadb://`/`jdbc:mysql://` links `libmariadb`), so the
  probe can link+run on a host without the lib. Transient: removed per scheme as
  S1–S4 land.
  *Proof (measured, skipped=0):* `MakealiveDbStateE2ETest.stateSurfaceNativeNeverSilent`
  green + `KofDbE2ETest.nativeUnsupportedSchemeNamesGapNotSilent` pin (rc≠0,
  `DB001`, no `unknown db connection`) + `NativeDbSchemeRefusalAsmTest`
  (deterministic codegen) + `LinkByUseTest` 3/3.
- **S1 — `mariadb://` = mysql-wire alias (Native, 3 arches). ✅ DONE on x86-64
  23/09 (gaps-db lane).** `kof_db_connect_inner` (`RuntimeDb2`) matches
  `mariadb://` and reuses the `mysql://` path (`r12 = schemeStart+2`, so the
  shared `leaq 8(%r12)` host offset lands after the 10-char scheme);
  `kof_db_type` reports the mysql family (2), so `execute`/`query`/ORM take the
  wire. The `DB001` message now lists `mariadb://`. On riscv64/aarch64 both
  `mysql://` and `mariadb://` still refuse with `DB001` (the cross mysql wire is
  not ported — honest R7). *Proof:* `KofDbE2ETest#nativeMariadbAliasWireProtocol`
  — real MariaDB (KOF_MYSQL_PORT), both the `user:pass@host` and the host-only
  form, byte-identical `{"id":7,"name":"Alias"}`; `KofDbE2ETest` 28/0F +
  `NativeDbSchemeRefusalAsmTest` 2/2. **Cross diagnostic corrected 23/09:**
  the riscv64/aarch64 `DB001` message advertised `mysql://` as supported while
  the cross code refuses it — now it states the truth. **Superseded by S5.4
  (24/09):** after the cross wire was ported, the message truthfully lists
  `sqlite:, mysql://, mariadb://` (a non-ported scheme such as `postgres://`
  still throws `DB001` at connect), pinned by
  `NativeRiscvRuntimeSliceRegistryTest#crossDb001MessageAdvertisesPortedSchemesOnly`
  + `KofDbE2ETest#crossNativeUnsupportedSchemeNamesTruthfulDb001`.
- **S2 — JDBC scheme parity JVM/JS/Android. ✅ DONE 23/09 (gaps-db lane).**
  Per-driver measurement is already proven by the E2E corpus — `h2`
  (`KofDbE2ETest` execute/query/typed), `sqlite` (`KofOrmE2ETest` `jdbc:sqlite:`,
  JVM find/save/page), `mariadb` (`KofOrmE2ETest#mariadbCrud` + the F2d JVM
  oracle, real server), `postgres` (`KofOrmE2ETest#postgresCrud`, skipped without
  a server) — all through the same `DriverManager` path that JS/Android delegate
  to. What was missing was the **diagnostic**: a JDBC URL whose driver is absent
  leaked a raw `SQLException: No suitable driver`. Now
  `JvmConfigRuntime.kof_db_connect/connect2` (JVM, and Android through the same
  runtime) and `KofJsDbBridge.connect/connect2` (JS delegate) map it to a
  **named** `DB001: no JDBC driver for this URL (add the driver to the
  classpath): <url>`, while a **real** connection failure (server down / bad
  credentials) passes through **untouched** — never masked as `DB001`. *Proof:*
  `KofDbE2ETest#jvmMissingJdbcDriverNamesGapNotSilent` +
  `#jvmRealConnectionFailureIsNotRelabeledDb001` +
  `#jsMissingJdbcDriverNamesGapNotSilent` +
  `#jsRealConnectionFailureIsNotRelabeledDb001` (the JVM one RED on the old code
  — `No suitable driver`); `KofDbE2ETest` 32/0F.
- **S3 — `mongodb://` interop-first (R9). ✅ DONE 23/09 (gaps-db lane).**
  JVM/Android run it for real: `KofOrmE2ETest#mongoCrud` (full ORM roundtrip —
  save/find/where/count/saveAll/page/deleteAll — over the real
  `mongodb-driver-sync`). JS/Native are a **declared** `DB001`, never silent:
  `KofJsDbBridge.connect/connect2` now rejects `mongodb://` with a scheme-aware
  `DB001: mongodb:// is not supported on the JS target yet (host driver bridge
  pending): <url>` (instead of the misleading "no JDBC driver" message), and
  Native refuses via S0. On JVM a missing mongo driver is also named (`DB001:
  mongodb:// needs the mongodb-driver-sync on the classpath`). No home-grown
  server (R9). *Proof:* `KofDbE2ETest#jsMongodbSchemeNamesGapNotSilent` +
  `#jvmMongodbMissingDriverNamesGap`; `KofOrmE2ETest#mongoCrud` (JVM real).
- **S4 — `jdbc:oracle:` (same route as S3). ✅ DONE 23/09 (gaps-db lane,
  declared).** No Oracle driver/server exists on this host, and Oracle — like
  Mongo — will never be a home-grown server (R9). On JVM/Android/JS,
  `jdbc:oracle:` connects when the driver is on the classpath, otherwise the S2
  named `DB001` fires; bare `oracle://` is a `DB001` (not a JDBC URL). Native
  refuses via S0. *Proof:* the S2 diagnostic tests cover the driver-absent path
  generically; no server-specific E2E is possible here (declared, not silent).
- **S5 — cross `mysql://`/`mariadb://` wire (riscv64/aarch64). PLANNED —
  dimensioned 23/09 (gaps-db lane).** This is the multi-session front behind the
  honest cross `DB001`; Native closes last (R7), so it runs after the others.

  **Measured x86 surface to reproduce** (the wire lives in `runtime/RuntimeDb*.java`
  + `RuntimeNet`; the cross runtime today has only the SQLite FFI):
  | x86 piece | Responsibility |
  |---|---|
  | `RuntimeNet` (`kof_net_write`/`kof_net_read`) | socket + TCP read/write framing |
  | `RuntimeDb1` (`kof_sec_sha1_*`, `kof_db_mysql_scramble`, `kof_db_mysql_lenenc`, `kof_db_mysql_render`) | SHA1 + auth scramble + length-encoded ints |
  | `RuntimeDb2` (`kof_db_connect_inner`, `kof_db_mysql_next`, `.Ldb_scheme_*`, `.Ldb_up_*`, `.Ldb_res_parse`) | scheme/URL parse, handshake read, resultset parse |
  | `RuntimeDb3` (`.Ldb_auth_*`, `.Ldb_connect_register`) | auth switch + registration |
  | `RuntimeDb4` (`kof_db_bind/close/execute/transaction`) | dispatch to the sqlite/mysql branches |
  | `RuntimeDb6` (`kof_db_mysql_*`) | value/column handling |

  **Slices (one session each, each with its own proof):**
  - **S5.0 — cross socket layer. ✅ SATISFIED (measured 23/09).** The cross HAL
    (`NativeRiscvAsmRt0`) already exposes `kof_plat_net_socket` (198) /
    `kof_plat_net_connect` (203) / `kof_plat_read` (63) / `kof_plat_write` (64) /
    `kof_plat_close` (57), and the cross HTTP core
    (`NativeRiscvHttpCore`) already drives real TCP with them under qemu —
    `KofHttpNativeResilienceCrossTest` opens/reads/writes/retries real sockets on
    riscv64/aarch64. **No new wrapper is needed:** the DB wire builds directly on
    the same primitives (write on an fd works on sockets), exactly as the HTTP
    core does. (The x86 `kof_net_*` wrappers are an x86 convenience, not a
    requirement.)
  - **S5.1 — handshake + auth.** Port SHA1/scramble/lenenc + read the greeting +
    send the handshake/auth-switch response. *Proof:* a cross program connects to
    the real MariaDB under qemu and the server returns the OK packet.
    - **23/09 — first slice DONE (piece `B62`, gaps-db lane):** SHA1 (short input,
      `len < 56` — the auth path never hashes more) ported from the x86
      `RuntimeDb1` (`kof_sec_sha1_block` + `kof_sec_sha1_internal`) plus
      `kof_bswap32`/`kof_bswap64`. Proof: `NativeRiscvDbWireTest` runs SHA1 on
      riscv64 + aarch64 (qemu) against the JVM `MessageDigest` oracle over 3
      vectors (empty, `abc`, the 43-byte pangram), plus a sabotage test proving
      the piece is the one exercised (without `B62`, `ld` fails undefined).
      Cross lesson (load-bearing): RV64 `lw` SIGN-EXTENDS (x86 `movl` zeroes) —
      every 32-bit load feeding `srli`/`slli` needs an explicit zero-extend, and
      the 32-bit working words must be zext'd each round       (RV64 has no 32-bit GPR).
    - **23/09 — second slice DONE (piece `B63`, gaps-db lane):** the auth helper
      itself — `kof_db_mysql_scramble` (`mysql_native_password`:
      `SHA1(pass) XOR SHA1(seed || SHA1(SHA1(pass)))`, over the B62 SHA1) and
      `kof_db_mysql_lenenc` (length-encoded integer: `<0xFC` / `0xFC`+2 LE /
      `0xFD`+3 LE) ported from `RuntimeDb1`. Proof: same `NativeRiscvDbWireTest`
      harness on riscv64 + aarch64 against a JVM oracle (`MessageDigest` +
      the standard scramble formula) for a fixed seed/password, plus the three
      lenenc cases and a B63 sabotage test.
    - **23/09 — third slice DONE (piece `B64`, gaps-db lane):** the server
      greeting parser — `kof_db_mysql_parse_greeting` walks the handshake packet
      (protocol 0x0A, NUL-terminated version, conn-id, the two auth-plugin-data
      halves) and extracts the 20-byte seed, exactly like `RuntimeDb3` does
      before `kof_db_mysql_scramble`. Proof: `NativeRiscvDbWireTest` parses a
      synthetic MariaDB greeting + a bad-protocol packet on both arches against a
      fixed oracle, plus a B64 sabotage test.
    - **23/09 — fourth slice DONE (piece `B65`, gaps-db lane):** the handshake
      response builder — `kof_db_mysql_build_auth_response` writes the packet
      frame (3-byte length LE + seq 1) and payload (capabilities
      `0x0008820B`, max-packet, charset, 23-byte reserved, user, auth response
      `<20>+scramble` or empty, database, plugin `mysql_native_password`),
      mirroring `RuntimeDb3`. Proof: `NativeRiscvDbWireTest` builds it for
      `passLen=20` and empty on both arches against a fixed oracle, plus a B65
      sabotage test.
    - **23/09 — fifth/final slice DONE (piece `B66`, gaps-db lane):** the socket
      round-trip — `kof_db_mysql_handshake(fd, user, pass, db)` reads the
      greeting, parses the seed, computes the scramble, builds and sends the
      handshake response and reads OK/ERR, over the `kof_plat_net_*` HAL.
      **S5.1 ✅ SATISFIED:** `NativeRiscvDbWireTest` drives it on riscv64 +
      aarch64 (qemu) against the **real MariaDB** — correct credentials return
      `0` (OK packet received) and an unknown database returns `-1` (Err 1049).
      (The host runs MariaDB with `--skip-grant-tables`, so the pass/fail axis is
      the unknown-DB error, not the password; the scramble itself is proven
      against the JVM oracle in B63.) The `AuthSwitchRequest` branch is not hit
      by this server (native password is the default) and stays a declared,
      diagnosed path for a later slice if a server negotiates it.
  - **S5.2 — `COM_QUERY` + text resultset.** Port packet framing + result parse.
    **Partial ✅ 23/09 (piece `B67`):** the request framing + the first response
    packet are ported — `kof_db_mysql_command(fd, sql, buf, buflen)` sends
    `[0x03][sql]` (3-byte little-endian length, seq 0) and returns the first
    payload so the caller classifies it (`>=1` column count, `0x00` OK, `0xFF`
    ERR). Proven on riscv64 + aarch64 (qemu) against the **real MariaDB**:
    `SELECT 1` → `1`, `SET @x=1` → `0`, bad SQL → `255`.
    *Proof:* `NativeRiscvDbWireTest#commandClassifiesResponseAgainstRealMariaDb*`.
    **Partial ✅ 23/09 (pieces `B68`–`B69`):** the packet reader
    (`kof_db_mysql_reset`/`next`, port of `RuntimeDb2`) and the resultset header
    parse (`kof_db_mysql_query_text(fd, sql)` → `ncols` + the raw payload of the
    first row via lenenc cells) are ported. Proven on riscv64 + aarch64 (qemu)
    against the **real MariaDB**: `SELECT 1` → 1 column / row `[0x01,'1']`;
    `SELECT 1,'ab'` → 2 columns / row `[0x01,'1',0x02,'a','b']`.
    *Proof:* `NativeRiscvDbWireTest#resultsetHeaderAgainstRealMariaDb*`.
    **Done ✅ 24/09 (piece `B70`, gaps-db lane):** full text query —
    `kof_db_mysql_query(fd, sql)` sends `COM_QUERY`, reads the column
    definitions (names), iterates ALL rows and materialises one JSON object
    `{"col":value,…}` per row into a `List<KofString>` (port of the x86
    `kof_db_query` in `RuntimeDb5/Db6`). Value rules follow the JVM contract
    (`kof_db_row_to_json` in `JvmConfigRuntime`, the Kof oracle): NULL →
    the bare `null` literal; digits-only → raw number; everything else
    (including the empty string) → `json_encode_string`. Proven on
    riscv64+aarch64 (qemu) against the **real MariaDB**: `SELECT 1` →
    `{"1":1}`; `SELECT 1,'ab'` → `{"1":1,"ab":"ab"}`; `UNION ALL` → 2 rows;
    `SELECT NULL AS n,'a"b' AS s` → `{"n":null,"s":"a\"b"}`.
    *Proof:* `NativeRiscvDbWireTest#queryAllRowsAgainstRealMariaDb*` +
    the B70 sabotage test. **Honest divergence found on the way (§488, OPEN):**
    the x86 MySQL path (`RuntimeDb5 .Ldb_mysql_null`) emits NULL as a raw
    EMPTY string (invalid JSON `{"n":,`); B70 does NOT copy the bug — same
    posture as the B47 cross-sqlite NULL face.
    Left for the next slices: prepared/tx/ORM (S5.3) + link/parity (S5.4).
    *Completion criterion:* `db.query` roundtrip under qemu, byte-identical to x86/JVM.
  - **S5.3 — bind/prepared + tx + ORM.** Port the prepared/execute/transaction
    dispatch. **Slice 1 ✅ 24/09 (piece `B71`, gaps-db lane):** the client-side
    bind helpers — `kof_db_mysql_render(val)` (Int → decimals, KofString →
    `'escaped'`) + `kof_db_mysql_replace_q(sql, literal)` (first `?` only) —
    port of the x86 fallback in `RuntimeDb1`/`RuntimeDb2`/`RuntimeDb4`.
    *Proof:* `NativeRiscvDbWireTest#bindRenderReplaceMatchesOracle*` + B71
    sabotage (riscv64 + aarch64, qemu). **Slice 2 ✅ 24/09 (piece `B72`,
    gaps-db lane):** `kof_db_mysql_execute(fd, sql)` — COM_QUERY via B67 +
    affected-rows from the OK packet (1-byte / FC+2LE / FD+3LE), port of the
    x86 execute tail (`RuntimeDb4` subst + `RuntimeDb5` done/afc/afd/bad);
    error/ERR/resultset → 0. Query with binds needs no new piece (B71
    substitute + B70 query). *Proof:*
    `NativeRiscvDbWireTest#execWithBindsAgainstRealMariaDb*` + B72 sabotage
    (riscv64 + aarch64, qemu, real MariaDB: CREATE 0, INSERT×2 with Int/String
    binds incl. quote-escape 1, UPDATE 1, DELETE no-match 0 / match 1, bad SQL
    0, SELECT-via-execute 0, bound query coherence). Left: the executeN/queryN
    mysql dispatch in the B47 bodies (lands with the S5.4 link, when a mysql
    fd can reach them) + tx + ORM. *Proof:* `orm.*` E2E under qemu.
  - **S5.4 — link + parity test.** `-lmariadb` link-by-use on cross + the riscv/
    aarch mirror of `KofDbE2ETest#nativeMariadbAliasWireProtocol`. After this the
    S1 cross `DB001` becomes real. **Slice 1 ✅ 24/09 (piece `B73`, gaps-db
    lane):** the real cross `connect` — `mysql://`/`mariadb://` URL parse
    (`[user[:pass]@]host[:port][/db]`, dotted IPv4 + the x86 127.0.0.1
    fallback), socket/connect on the HAL, the B66 handshake and registration of
    the fd as type 2 in the B47 tables; the honest `DB001` now lists only the
    ported schemes. *Proof:* `NativeRiscvDbWireTest#connectMysqlAgainstRealMariaDb*`
    + `withoutConnectPieceLinkFailsSabotage` (riscv64 + aarch64, qemu, real
    MariaDB — both the userinfo and the host-only `kof_db_connect2` form
    authenticate) + `KofDbE2ETest#crossNativeUnsupportedSchemeNamesTruthfulDb001`.
    **Slice 2 ✅ 24/09 (piece `B47b`, gaps-db lane):** the `executeN`/`queryN`
    mysql dispatch — a resolved type-2 handle now reaches the wire through
    `db.execute`/`db.query` (the bodies dispatch on `kof_db_type`: 2 → B71
    substitute + B72/B70, else the sqlite branch; the dispatch outgrew the B47
    frame, so it lives in `B47b` and B47 keeps resolve/type/connect/close/bind/
    transaction). `kof_db_connect` zeroes user2/pass2 (host-only signature) so
    the URL form without userinfo authenticates; `kof_db_close` closes type 2 via
    `kof_plat_close`. **`transaction { }` rode along for free** — the B47
    BEGIN/COMMIT/ROLLBACK call `kof_db_execute`, which now dispatches. `-lmariadb`
    is **moot**: the cross wire is self-contained (raw sockets + own SHA1), no
    external driver is linked. *Proof:* `KofDbE2ETest#crossNativeMariadbAliasWireProtocol`
    (the riscv/aarch mirror, userinfo + host-only `mariadb://`, real row output) +
    `#crossNativeMariadbTransactionCommits` / `#...RollsBackOnFailure` (real
    BEGIN/COMMIT/ROLLBACK over COM_QUERY) + `NativeRiscvDbWireTest#dispatchExecuteQueryAgainstRealMariaDb*`
    + `withoutDispatchPieceLinkFailsSabotage` (riscv64 + aarch64, qemu, real
    MariaDB). **S5.4 complete; the scheme-parity front is done.**
  - **S5.5 — `kof.orm` over the mysql wire on the cross (NEW, 24/09).** The
    cross row-object faces (`RtB50`/`RtB53`/`RtB55`/`RtB55Helpers`/`RtB57`/
    `RtB58`…) call `sqlite3_*` directly on the handle from `kof_orm_conn`, which
    refuses anything but type 1 (`kof_orm_conn` throws `unknown db connection:
    db1` for a type-2 handle — measured 24/09). Compile is clean (no `ORM001`);
    the refusal is honest but **misnamed** and the mysql ORM is absent. The x86
    reference is the `RuntimeOrmMysql*` family (`RuntimeOrmMysqlDdl`,
    `RuntimeOrmMysqlCountWhere`, `RuntimeOrmMysqlSave`,
    `RuntimeOrmMysqlFieldLit`) — **the dialect matters**: MariaDB rejects the
    sqlite `"table"` quoting (measured: `SELECT COUNT(*) FROM "q"` → `ERROR
    1064`); the mysql path must quote with backticks (`` `table` ``).
    **Slices (each with qemu proof on riscv64 + aarch64):**
    1. **scalar + `orm.count`** — new cross primitive
       `kof_db_mysql_scalar_int(fd, sql) -> Long` (COM_QUERY → first row's first
       column) + `kof_orm_count` branches type 2 → backtick `SELECT COUNT(*)
       FROM `table``. *Proof:* `orm.count<User>` over `mysql://` cross.
       **DONE 24/09** — piece `RtB74` (`kof_db_mysql_scalar_int`, reuses
       `kof_db_mysql_query_text`/B69) + `RtB50` type-2 branch with backtick
       dialect; E2E `KofOrmE2ETest#crossNativeMariadbCountMatchesX86Oracle`
       green on x86-64 oracle + riscv64 + aarch64 (`3` → `2` after a bound
       `DELETE`).
    2. **`orm.count_where`** — bind substitution (B71) + the mysql quoting, with
       the null bind handled (B71 renders a 0 pointer as Int 0 today).
       **DONE 24/09** — `RtB53` type-2 branch builds `SELECT COUNT(*) FROM
       `t` WHERE `f` = <literal>` with backtick names, dispatching the literal
       to the shared renderer `kof_orm_mysql_lit` (box §284 int/long/bool/
       double/float → `kof_*_to_string`, KofString via `kof_db_mysql_render`,
       null → `NULL`, else ORM001) — promoted to a global in `RtB75` (fatia 3)
       so a single copy serves `count_where` and `delete`. The same unit fixed
       a latent **x86** bug: `RuntimeOrmMysqlCountWhere` checked the box tag as
       1 instead of 3 for Bool → ORM001 on any boolean bind (catalogued §492).
       *Proof:* `KofOrmE2ETest#crossNativeMariadbCountWhereMatchesOracles` —
       JVM + x86-64 + riscv64 + aarch64 byte-identical on string/miss/injection/
       negative/positive/bool (`1\n0\n0\n1\n0\n1\n1\n1`).
    3. **`orm.delete`/`deleteAll` over mysql** (part of fatia 3).
       **DONE 24/09** — new piece `RtB75`: `kof_orm_delete_mysql(id,key,table,
       schema)` and `kof_orm_delete_all_mysql(id,table,schema)` build ``DELETE
       FROM `t`[ WHERE `pk` = <lit>]`` (the `<lit>` from the shared renderer)
       and dispatch via `kof_db_resolve` + `kof_db_mysql_execute` (B72). `RtB50`
       (`delete_all`) and `RtB54` (`delete`) branch on `kof_db_type == 2`. The
       **semantics mirror the x86, which is the contract reference (D-DB-GAPS)**:
       the x86 `.Lorm_da_my`/`.Lorm_del_my` run the **generic** `kof_db_execute`
       (`.Ldb_exec_bad` → `0`, no throw) and the caller returns `affected >= 0`
       → `true` on success **and on ERR**. So the cross reuses B72 (no throw) —
       it does **not** need a throwing exec here. `RtB54` also fixed an
       off-by-one in its own new mysql branch: the prologue spills callee-saved
       regs **before** assigning the args, so `key`/`table`/`schema` must be
       read from the live `s2`/`s3`/`s4`, not from the stack slots (the first
       attempt read the caller's stale regs → ORM001/segfault; isolated with a
       scratch run under qemu `-strace`/`-d in_asm`). The JVM↔Native divergence
       on the **error** path (JVM throws, Native returns `true`) is
       **pre-existing** and catalogued **§493** — RESOLVED 26/09 by
       `D-DECISION-BATCH-2609` item 1 (the JVM is the law): delete/deleteAll now
       THROWS on every Native target (x86 routes `.Lorm_del_my`/`.Lorm_da_my`
       through `.Lorm_sa_exec`; cross `RtB75` calls `kof_orm_mysql_exec`/B76) —
       §493 FIXED.
       *Proof:* `KofOrmE2ETest#crossNativeMariadbDeleteAndDeleteAllMatchesOracles`
       — JVM + x86-64 + riscv64 + aarch64 byte-identical on hit/miss/negative/
       idempotent (`3\ntrue\n2\ntrue\n2\ntrue\n2\ntrue\ntrue\n0`) — and
       `#crossNativeMariadbDeleteErrorsThrowLikeX86Oracle` (26/09, §493: Native
       x86-64 == riscv64 == aarch64 on the missing-table ERR — all three throw
       `mysql: Table 'test.user' doesn't exist`; RED pre-fix: all `true`).
    4. **`orm.save`** — INSERT path; the generated key needs
       `SELECT LAST_INSERT_ID()` (the `RtB74` scalar) and the field literals.
       This is the **only** face whose x86 reference **throws** on ERR
       (`RuntimeOrmMysqlExec`/`.Lorm_sa_exec`), so it is the one that needs a
       throwing cross exec primitive (`kof_orm_mysql_exec`, port of that
       reference) — see the design note below.
       **Slice 4a DONE 24/09** — the throwing exec primitive is landed as part
       of `RtB76`: `kof_orm_mysql_exec(fd, sql) -> affected | throw` (OK →
       lenenc affected; ERR → `mysql: <msg>` capped at 400; lost/odd response →
       `mysql: connection lost`). *Proof:* `NativeRiscvDbWireTest` — harness on
       riscv64 + aarch64 under qemu against the real MariaDB
       (`0\n1\n1\n1\n0\n1`) plus the ERR throw (`mysql: …`, exit 1) and the
        link-without-B76 sabotage. **Slice 4b DONE 24/09** — the `kof_orm_save`
        body was landed as `RtB77` (`kof_orm_save_mysql`), branching on
        `kof_db_type == 2` at the top of `kof_orm_save` (before `kof_orm_conn`,
        which refuses type≠1). It mirrors the three measured outcomes: (1) PK
        null/0 → INSERT **without** the PK column + `SELECT LAST_INSERT_ID()`
        (`kof_db_mysql_scalar_int`/`RtB74`) and a **new instance** with the PK
        patched (`kof_alloc` + `kof_init_object` + `kof_memcpy`); (2) PK != 0 →
        ``UPDATE `t` SET `f` = <lit>,… WHERE `pk` = <lit>`` → same pointer when
        rows are found; (3) UPDATE 0 rows → full-column INSERT (upsert) → same
        pointer. PK decision mirrors x86 (int/long == 0, double/float truncated
        == 0 with the INT64_MIN/MAX sentinels, String null → INSERT, bool never).
        Field literals are schema-`typeCode`-driven (`kof_orm_mysql_field_lit`)
        with backtick quoting (mysql dialect); exec is the throwing
        `kof_orm_mysql_exec` (`RtB76`). *Proof:*
        `KofOrmE2ETest#crossNativeMariadbSaveMatchesOracles` — JVM + x86-64 +
        riscv64 + aarch64 byte-identical (`1\n1\n1\n1\n{"name":"Mel2"}\n7\n2\n8\n3`)
        — and `#crossNativeMariadbSaveErrorMatchesX86Oracle` (missing table
        throws on x86 == riscv64 == aarch64). **Slice 4c DONE 24/09** —
        `orm.saveAll` over mysql came for free: `kof_orm_save_all` (`B56`) only
        loops and delegates to `kof_orm_save`, which now dispatches type 2 to
        `B77`. *Proof:* `KofOrmE2ETest#crossNativeMariadbSaveAllMatchesOracles`
        — JVM + x86-64 + riscv64 + aarch64 byte-identical
        (`true\n2\n{"name":"Mel"}\n{"name":"Ana"}\ntrue\n2\n{"name":"Mel2"}\ntrue\n2`).
    5. **`orm.find`/`all`/`where`/`where_op`/`page`** — row materialisation.
       **Slice 5a (`find`) DONE 24/09** as `B78` (`kof_orm_find_mysql`): the
       typed column ABI did **not** need a new wire primitive — the cross walks
       the resultset directly (reusing the `B68` reader + `B63` lenenc), matches
       columns by **name** against the schema and converts each cell by its
       **typeCode** (int/long `kof_orm_mysql_atoi`, bool `kof_orm_mysql_bool`
       §397, string `kof_io_make_string`, double/float `kof_string_to_double`/
       `kof_string_to_float`; NULL → 0/null/false). The key becomes a SQL literal
       by `kof_orm_mysql_lit` (`B75`) + `kof_db_mysql_replace_q` (`B71`); miss →
       `null`, ERR → `mysql: <msg>`, a schema field with no column →
       `mysql: no column <name>` (R6). *Proof:*
       `KofOrmE2ETest#crossNativeMariadbFindMatchesOracles` — JVM + x86-64 +
       riscv64 + aarch64 byte-identical
       (`1\nMel\nm@kof.dev\n30\nnull\nAna/25\nAna/25\nMel`).        **Slice 5b (`all`) DONE 24/09** as `B79` (`kof_orm_all_mysql`, dispatch in
       `B58`): same packet walk, `SELECT * FROM \`t\`` (no bind), one record per
       row via `kof_list_new`/`kof_list_add`, **empty list** (never null) when
       there are no rows; dead/ERR/`no column` → throw (R6). *Proof:*
       `KofOrmE2ETest#crossNativeMariadbAllMatchesOracles` — JVM + x86-64 +
       riscv64 + aarch64 byte-identical (`3\nMel/30\nAna/25\nLeo/40\n2\nMel\nLeo\n0`).
       **Slice 5c (`where`/`where_op`) DONE 24/09** as `B80` +
       `B80Helpers` (`kof_orm_where_mysql` + `kof_orm_mysql_op`, dispatch in
       `B59`): ``SELECT * FROM `t` WHERE `f` <op> ?``, value literal via
       `kof_orm_mysql_lit`, `?` swapped by `kof_db_mysql_replace_q`; the op
       whitelist is the mysql port of `RuntimeOrmMysqlOp` (`==`→`=`, `>`/`<`/
       `>=`/`<=`/`!=`/`LIKE`, else throw `ORM operator not allowed: <op>` — the
       `where` face defaults to `=`). *Proof:*
       `KofOrmE2ETest#crossNativeMariadbWhereMatchesOracles` — JVM + x86-64 +
       riscv64 + aarch64 byte-identical (incl. the exact throw and the empty
       list).        **Slice 5d (`page`) DONE 24/09** as `B81` + `B81Helpers`
       (`kof_orm_page_mysql` + `kof_orm_mysql_pv`, dispatch in `B60`):
       ``SELECT * FROM `t` LIMIT <lim> OFFSET <off>`` (lim/off boxed →
       `((Number)x).intValue()` via `kof_orm_mysql_pv` → `kof_long_to_string`),
       same packet walk, empty page = empty list. *Proof:*
       `KofOrmE2ETest#crossNativeMariadbPageMatchesOracles` — JVM + x86-64 +
       riscv64 + aarch64 byte-identical. **S5.5 COMPLETE**: all row faces
       (`find`/`all`/`where`/`where_op`/`page`) + the writes
       (`save`/`saveAll`/`delete`/`deleteAll`/`count`/`count_where`) now run on
       the cross over the MySQL wire, byte-identical to the host — rows 15/16
       of `PARITY-GAPS` closed.
    Until each lands this stays a **declared interim gap** (never a silent
    accept), and `kof_orm_conn`'s message should name the real cause (mysql ORM
    not yet ported) instead of `unknown db connection`.

    **Design note (corrected 24/09; SUPERSEDED 26/09 by `D-DECISION-BATCH-2609`
    item 1 — the JVM is the law, §493 FIXED):** the throwing cross exec
    (`kof_orm_mysql_exec`) was thought to belong to **`orm.save` only** — the x86
    `delete`/`deleteAll` do **not** throw (they use the generic `kof_db_execute`),
    so porting a throw there would *diverge* from the x86 contract. The JVM's
    `orm.delete`/`deleteAll` do throw (JDBC), which was the **JVM↔Native
    divergence catalogued §493**. **SUPERSEDED 26/09** by `D-DECISION-BATCH-2609`
    item 1 (rule 6 decided — the JVM is the law): x86/cross `delete`/`deleteAll`
    now go through the SAME throwing exec (`.Lorm_sa_exec` / `kof_orm_mysql_exec`)
    as `save`, and §493 is FIXED — both faces match the JVM on success AND on
    error.

## Non-goals / invariants

- No new syntax, no API change; no frozen-semantics edit (behavior freeze).
- Interop-first (R9): drivers/protocols come from audited external libs, never
  reimplemented. Native closes last (R7), each slice honest meanwhile (R6).
- Connection pooling stays the separate PLANNED item of `DATABASE_VISION.md`
  (not part of scheme parity).
