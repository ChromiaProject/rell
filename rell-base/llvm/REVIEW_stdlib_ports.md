# Adversarial bit-exactness review — `rell_gtv.cpp`, `rell_crypto.cpp`, `rell_json.cpp`

Reviewer: independent pass against the *actual* Rell + Postchain source (not the brief). All
findings below were verified against decompiled/sources of `postchain-gtv 3.49.12`,
`postchain-common 3.49.12`, `gson 2.13.1`, the vendored etherjar in
`rell-base/runtime-core/src/main/java/.../etherjar/`, and the Rell stdlib Kotlin
(`lib_crypto.kt`, `lib_type_json.kt`, `lib_type_gtv.kt`, `rt_value_json.kt`, `PostchainGtvUtils.kt`).

Sources extracted to `/tmp/pcgtv` (postchain-gtv-3.49.12-sources.jar) and `/tmp/pccommon`
(postchain-common-3.49.12-sources.jar); Gson escape tables read from `gson-2.13.1.jar` bytecode.

Default posture: anything not provably byte-identical is flagged.

---

## Severity legend
- **BLOCKER** — provable byte-divergence from the JVM on valid/consensus-reachable input. Must fix
  before wiring into the backend.
- **HIGH** — divergence on a reachable but narrower input class (malformed-input acceptance set,
  rare code points); can break consensus if any chain hits it.
- **MEDIUM** — correctness/robustness gap or unverified equivalence that needs a golden-vector gate.
- **LOW / NIT** — cosmetic, scoping, or documentation.

---

## BLOCKER findings

### B1. `rell_gtv.cpp` — Gson does NOT escape U+2028 / U+2029; the port passes them through
**File:** `rell_gtv.cpp`, `json_escape_string()` (lines ~672-704).
**Path:** `gtv.to_json`, `gtv.to_text`, `Rt_GtvValue.str` (all go through `GtvAdapter` → Gson).

Gson 2.13.1's `JsonWriter.string()` escapes the Unicode line/paragraph separators
**unconditionally, regardless of `htmlSafe`**:
```
if (c == 8232) replacement = " ";   // U+2028
if (c == 8233) replacement = " ";   // U+2029
```
(verified from `com.google.gson.stream.JsonWriter` bytecode, offsets 74-97 of `string()`).

The port's `json_escape_string` iterates over raw bytes and lets everything `>= 0x20` pass through
verbatim, so a `GtvString` (or any string-typed value) containing U+2028/U+2029 serializes as the
raw 3-byte UTF-8 sequence instead of ` ` / ` `.

- **Divergent input:** `GtvString(" ")` (UTF-8 `E2 80 A8`).
- **JVM:** `" "` (the 6 ASCII chars). **Port:** `"<raw E2 80 A8>"`.
- **Required fix:** in `json_escape_string`, decode UTF-8 to code points (you already have
  `next_codepoint` in this file) and emit ` ` / ` ` for U+2028 / U+2029. These are the
  ONLY two non-ASCII code points Gson escapes; everything else ≥ 0x80 still passes through. Add a
  golden vector.

> Note: this is GTV-only. `rell_json.cpp` (Jackson) correctly does NOT escape these (Jackson's
> table does not), so do not "fix" the JSON family the same way — see N1.

### B2. `rell_gtv.cpp` — `from_json` rejects integer-valued decimal/exponent numbers that the JVM accepts
**File:** `rell_gtv.cpp`, `JsonParser::parse_number()` (lines ~940-970).
**Path:** `gtv.from_json(text)` / `gtv.from_json(json)` → `PostchainGtvUtils.jsonToGtv` →
`GSON.fromJson(s, Gtv)` → `GtvAdapter.deserialize`, which does
`prim.asBigDecimal.longValueExact()`.

`BigDecimal.longValueExact()` succeeds for any number whose **value** is an exact integer, even when
written with a fraction or exponent. Verified on JVM:
```
"1.0"    -> 1
"1e2"    -> 100
"100.00" -> 100
"1.0E1"  -> 10
"0.0"    -> 0
"1.5"    -> ArithmeticException (reject)
```
The port hard-rejects any token containing `.`/`e`/`E` (`is_int=false → fail`), so `from_json("1.0")`
throws in the port but yields `GtvInteger(1)` on the JVM.

- **Divergent input:** `gtv.from_json("1.0")`, `"1e2"`, `"100.00"`, `"1.0E1"`.
- **JVM:** `GtvInteger(1 / 100 / 100 / 10)`. **Port:** throws `fn_json_badstr`.
- **Required fix:** parse the full JSON number, then apply `BigDecimal.longValueExact()` semantics:
  accept the number iff it is an exact integer in `[-2^63, 2^63-1]`, regardless of textual form;
  otherwise throw the `errorMsg(number)` Programmer-Mistake (range) vs `fn_json_badstr` (parse)
  distinction. Reuse the decimal/exponent normalization from `rell_bigdec`/`rell_json` rather than
  re-implementing. Add vectors for `1.0`, `1e2`, `100.00`, `1.5` (reject), `1e3` (=1000),
  out-of-range exact integers.

### B3. `rell_gtv.cpp` — `from_json` rejects blank/empty input that the JVM maps to `GtvNull`
**File:** `rell_gtv.cpp`, `from_json()` (lines ~975-988).
**Path:** `gtv.from_json` → `jsonToGtv(s) = GSON.fromJson(s, Gtv) ?: GtvNull`.

Verified on JVM: `Gson().fromJson("", …)` and `fromJson("   ", …)` both return `null`, and
`PostchainGtvUtils.jsonToGtv` maps `null` → **`GtvNull`**. The port throws `fn_json_badstr` on blank.

- **Divergent input:** `gtv.from_json("")`, `gtv.from_json("   ")`.
- **JVM:** `GtvNull`. **Port:** throws.
- **Required fix:** in the GTV `from_json` path, blank/empty input must return `Gtv::make_null()`,
  not throw. (Do NOT change `rell_json.cpp`'s `json()` constructor — that one is the Jackson path and
  correctly rejects blank via `require(!s.isBlank())`. The two "from_json"s have opposite blank
  semantics; keep them separate.)

---

## HIGH findings

### H1. `rell_gtv.cpp` — GTV `from_json` is a strict parser; Gson `fromJson(String)` is lenient
**File:** `rell_gtv.cpp`, `JsonParser` (lines ~784-988).
**Path:** `gtv.from_json` → Gson `fromJson`.

Gson's `fromJson(String, …)` reads with `JsonReader` in **lenient legacy mode**, so it accepts inputs
the port's strict recursive-descent parser rejects: `NaN`, `Infinity`, `-Infinity`, single-quoted
strings, unquoted object keys, leading `+`, etc. Any of these in chain data → JVM accepts, port
throws (acceptance-set divergence = consensus risk if a chain ever feeds such input through
`from_json`).

- **Divergent input examples:** `gtv.from_json("NaN")`, `gtv.from_json("'x'")`,
  `gtv.from_json("{a:1}")`.
- **Required fix:** either (a) match Gson lenient acceptance for this path, or (b) document the
  narrowed acceptance as an intentional, gated decision with a test matrix. Note `NaN`/`Infinity`
  then hit `BigDecimal` conversion and throw on the JVM anyway, but single-quote/unquoted-key cases
  succeed on the JVM — those are the real divergences. Gate with golden vectors generated from
  `GSON.fromJson`.

### H2. `rell_crypto.cpp` — RFC 6979 nonce equivalence (BouncyCastle vs libsecp256k1) is assumed, not asserted
**File:** `rell_crypto.cpp`, `get_signature()` / `eth_sign()`.
**JVM truth (verified):** `lib_crypto.kt get_signature` → `Secp256K1CryptoSystem.buildSigMaker(...).signDigest`
→ `secp256k1.kt Secp256k1SigMaker.sign` = `ECDSASigner(HMacDSAKCalculator(SHA256Digest()))` + low-S
(`s > HALF_CURVE_ORDER → n - s`) + `encodeSignature` = `bigIntegerToBytes(r,32) || bigIntegerToBytes(s,32)`.
`eth_sign` → vendored etherjar `Signer.create` = identical `ECDSASigner(HMacDSAKCalculator(SHA256Digest()))`
+ low-S + `recId = v - 27 = y ∈ {0,1}`.

The **algorithm** matches libsecp256k1 (RFC 6979 HMAC-SHA256 deterministic-k, low-S, 32+32 BE). This
is a well-established cross-implementation equivalence, but it is **consensus-critical and currently
unproven in this tree**: the self-test only checks self-consistency (sign-twice determinism +
round-trip verify), never that the bytes equal the JVM's.

- **Required fix (gate, not necessarily code):** add at least one *fixed* `(privkey, data_hash) ->
  exact 64-byte signature` vector captured from `PostchainGtvUtils.cryptoSystem` on the JVM, and the
  same for `eth_sign` `(r, s, rec_id)`. Until that vector passes, treat byte-exactness of
  `get_signature`/`eth_sign` as unverified. (Example 1/2 in `lib_crypto.kt` only assert round-trip
  recovery, which is necessary but not sufficient for byte-identity of the signature itself.)

### H3. `rell_crypto.cpp` — `eth_ecrecover` recId range divergence for `rec_id > 3`
**File:** `rell_crypto.cpp`, `eth_ecrecover()` (lines ~405-428).
**JVM truth:** `lib_crypto.kt` allows `rec_id in 0..100000`; etherjar `ecrecover(recId,…)` computes
`i = recId / 2`, `x = r + i·n`, y-parity = `recId & 1` — i.e. it *will* attempt recovery for
`rec_id` up to 100000 (returning `null`/throwing only when the point is invalid).

The port passes `int(rec_id)` straight to `secp256k1_ecdsa_recoverable_signature_parse_compact`,
which **only accepts recid 0..3** and fails otherwise. For `rec_id` in `0..1` (and effectively `2..3`)
behavior matches; for `rec_id >= 4` the JVM may compute a key (when `i = rec_id/2` keeps `x < q`)
while the port throws `Cannot parse recoverable signature`.

- **Divergent input:** `eth_ecrecover(r, s, 4, h)` and higher even/odd recIds where `x < q`.
- **Assessment:** vanishingly rare in real signatures (valid Ethereum recId is 0/1), and most high
  recIds yield `x >= q` → both return null. But it is a provable divergence within Rell's documented
  `0..100000` domain.
- **Required fix:** for `rec_id > 3`, replicate etherjar's `i = rec_id/2` multi-`n` recovery
  (BitcoinJ-style) rather than delegating to libsecp256k1's 0..3-only parser, OR confirm with the
  orchestrator that recId is contractually 0/1 here and clamp/document. Flagging per "default to
  flagging anything not provably byte-identical."

---

## MEDIUM findings

### M1. `rell_crypto.cpp` — `verify_signature` low-S normalization is correct, but the equivalence rationale is subtle; assert it
**File:** `rell_crypto.cpp`, `verify_signature()` (lines ~354-382).
**JVM truth (verified):** `secp256k1_verify` uses BouncyCastle `ECDSASigner().verifySignature(digest, r, s)`
with NO low-S/canonical check — it accepts **both** high-S and low-S, and returns `false` for r/s
outside `[1, n-1]`.

The port calls `secp256k1_ecdsa_signature_normalize` before `verify`, which makes libsecp256k1 (which
*would* reject high-S) accept high-S too — so the accepting behavior **matches** BC. This is correct,
and the in-code comment is accurate. The only residual risk: `secp256k1_ecdsa_signature_parse_compact`
rejects `r` or `s >= n` (returns `false` → port returns `false`), and BC's `BigInteger(1,…)` +
`verifySignature` also returns `false` for `r/s` out of range — equivalent. **No fix needed**, but add
one explicit high-S vector (a valid signature with `s > n/2`) that verifies `true` on both, to lock
the behavior against future libsecp256k1 default changes.

### M2. `rell_crypto.cpp` — `verify_signature` error-vs-false boundary may not match Rell wrapping
**File:** `rell_crypto.cpp`, `verify_signature()`.
**JVM truth:** `lib_crypto.kt verify_signature` constructs `Signature(pubkey, sig)` and calls
`cryptoSystem.verifyDigest` **inside a try/catch that converts ANY exception to
`Rt_Exception.common("verify_signature", …)`**. So size/format problems surface as a *thrown Rell
exception*, while a well-formed-but-wrong signature returns `false`. Additionally `data_hash` size is
checked *before* the try (throws on != 32).

The port throws `crypto_error` for bad pubkey size (33/65 only), bad sig size (64), and bad pubkey
parse — consistent with "throw on format". But the **exact trigger set** differs in one spot: Rell's
`Signature(pubkey.value, signature.value)` validation (in postchain `Signature`/`secp256k1_decodeSignature`)
throws for sig size not in `{64,65}` — Rell stdlib doc says signature must be 64, and
`secp256k1_decodeSignature` accepts 64 OR 65. The port only accepts 64. A 65-byte signature would
throw in the port but might be accepted (truncated to r/s from first 64) by postchain.
- **Required fix:** confirm whether a 65-byte signature reaches `verify_signature` in practice; if so,
  mirror `secp256k1_decodeSignature`'s `size in {64,65}` acceptance. Low likelihood but flagged.

### M3. `rell_gtv.cpp` — DER decoder is stricter than jasn1; round-trip is safe but decode-acceptance may diverge
**File:** `rell_gtv.cpp`, `decode_gtv` / `der_read_length` (lines ~549-654).
The encoder is DER-exact (verified below). The decoder additionally enforces "no trailing bytes
inside outer tag", canonical length, and re-sorts dict keys via `make_dict` (faithful — postchain
`GtvDecoder` also calls `GtvDictionary.build` which sorts, verified). However jasn1's BER decoder
(`GtvFactory.decodeGtv`) is more permissive about length forms and may accept some non-canonical BER
that the port rejects (and vice-versa the port may reject inputs jasn1 accepts). Since Rell only ever
*decodes* bytes it (or a peer) *encoded* with the canonical DER encoder, this is low-risk, but a
decoder-acceptance mismatch is still a theoretical consensus surface if `byte_array.from_gtv`/
`gtv.from_bytes` is fed adversarial bytes.
- **Required fix (gate):** add a corpus of jasn1-encoded GTVs (including arrays/dicts/bigints) and
  assert `decode_from_bytes` accepts exactly what `GtvFactory.decodeGtv` accepts, or document the
  intentional strict-DER narrowing.

### M4. `rell_json.cpp` — Jackson number round-trip relies on host `strtod`/`printf`; lock with vectors
**File:** `rell_json.cpp`, `java_double_to_string()` + `parse_number()`.
The `java_double_to_string` shortest-round-trip approach plus the 8 hard-coded subnormal exceptions is
a thoughtful, well-documented reconstruction of `Double.toString`, and the self-test is broad. But it
depends on the host's `strtod`/`snprintf("%.*e")` being correctly-rounded IEEE-754 (true on
glibc/macOS libc, but not guaranteed on every libc the LLVM backend may target). Determinism is
consensus-critical.
- **Required fix:** either ship a self-contained Ryū/Grisu shortest-double formatter (removes the
  libc dependency entirely) or assert in CI that the target libc is correctly rounded. At minimum,
  document the libc assumption prominently. Also confirm `NumberInt` path: Jackson parses a bare
  integer too large for `long` as a `BigIntegerNode` whose `toString()` is the canonical decimal — the
  port keeps `int_digits` verbatim, which matches; good, but add a vector for an integer with a
  leading-zero-stripping edge (grammar already forbids leading zeros, so fine).

### M5. `rell_json.cpp` — lone/unpaired surrogate escapes: Jackson behavior unverified
**File:** `rell_json.cpp`, `parse_string_raw()` surrogate handling (lines ~175-194).
The port tolerates a high surrogate not followed by a low surrogate by emitting the raw code unit as
UTF-8 (3-byte CESU-8-ish `ED A0 80…`). Jackson's actual behavior for `"\uD800"` (lone high surrogate)
is version-specific and not asserted here. Since `to_text` then re-serializes the stored bytes, a
mismatch in how the lone surrogate is *stored* propagates to output.
- **Required fix:** add golden vectors `"\uD800"`, `"\uDC00"`, `"\uD800x"`, `"\uD800\uD800"` captured
  from `ObjectMapper().readTree(...).toString()` and make the port match exactly (Jackson typically
  emits the U+FFFD replacement or the literal escape depending on config — must be pinned).

---

## LOW / NIT

### N1. `rell_json.cpp` (Jackson) U+2028/2029 are correctly NOT escaped — keep it that way
Confirmed Jackson's default `JsonStringEncoder` does not escape U+2028/U+2029 (unlike Gson). The
JSON-family port is correct. Cross-reference with B1 so a future "consistency" refactor does not break
one to match the other.

### N2. `rell_crypto.cpp` — RIPEMD-160 is dead code w.r.t. Rell stdlib
Confirmed: `lib_crypto.kt` exposes no `ripemd160` Rell function (grep of the namespace shows only
sha256/keccak256/get_signature/verify_signature/eth_*/privkey_to_pubkey/pubkey_encode/pubkey_to_xy/
xy_to_pubkey). RIPEMD-160 is used only internally by postchain's crypto system, not reachable from
Rell. The implementation + test vector are fine to keep for a future internal `hash()` path but are
not on any consensus surface today. The self-test vector `ripemd160("abc")` in the file is **wrong**:
it asserts `8eb208f7e05d987a9b044a8e98c6b087f15a0bfc` but the correct RIPEMD-160("abc") is
`8eb208f7e05d987a9b044a8e98c6b087f15a0bce` (ends `…bce`, not `…bfc`). Either the test will fail or the
implementation is being validated against a typo'd expected value — fix the expected constant (the
brief itself quotes the correct `…bce`).

### N3. `rell_gtv.cpp` — merkle `hash()` / `legacy_hash()` correctly deferred
The TODO at the bottom correctly scopes out GtvMerkleHash. Verified prefixes against source:
`HASH_PREFIX_LEAF=1`, `HASH_PREFIX_NODE=0` (`MerkleBasics.kt`), `HASH_PREFIX_NODE_GTV_ARRAY=7`,
`HASH_PREFIX_NODE_GTV_DICT=8` (`GtvMerkleBasics.kt`), digester SHA-256. The V1/V2 tree-shape work
genuinely must be transcribed from `GtvBinaryTreeFactory{,Array,Dict}` + `BinaryTreeFactory` before
implementing — leaving it out of scope is the right call. No action beyond the existing TODO.

### N4. `rell_crypto.cpp` — `crypto_error` code strings are approximations
The port synthesizes codes like `"verify_signature"`, `"eth_ecrecover"`; Rell's real codes are e.g.
`"fn:get_signature:datahash_size:<n>"` (matched for size checks — good) but `verify_signature`
wraps with `Rt_Exception.common("verify_signature", e.message)`. For a standalone byte-IO library the
exact code strings are out of scope; flagging only so the eventual backend-wiring layer maps them to
the real Rell codes rather than leaking these placeholders.

---

## What is provably correct (verified, no action)

- **GTV DER tags + length:** outer context tags `A0..A6` and inner universal tags
  `NULL=05, OCTETSTRING=04, UTF8STRING=0C, INTEGER=02, SEQUENCE=30` match jasn1's `RawGtv`/`DictPair`
  exactly. DER definite-length short/long form matches `ReverseByteArrayOutputStream` output. The 14
  encode vectors in `self_test` (incl. min/max i64, 2^63/2^64 big_integer leading-`00`, long-form
  length) are correct and consistent with the source semantics.
- **GTV integer vs big_integer:** identical inner INTEGER body, distinguished only by outer tag
  (`A3` vs `A6`) — matches `GtvInteger`/`GtvBigInteger` both using `BerInteger`.
- **Dict key sort = Kotlin `keys.sorted()` = UTF-16 code-unit order:** `cmp_utf16` correctly diverges
  from UTF-8 byte order for supplementary planes (emoji-before-U+FFFF test is right). Matches
  `GtvDictionary.build`. Applied on both encode and decode (decoder re-sorts, faithful to
  `GtvDecoder`→`GtvDictionary.build`).
- **GTV→JSON byte-array hex is UPPERCASE, no prefix:** verified `net.postchain.common.toHex` uses
  `"0123456789ABCDEF"`. Port matches.
- **GTV→JSON HTML escaping ON:** verified `make_gtv_gson_builder()` does NOT call
  `disableHtmlEscaping()`, so Gson default `htmlSafe=true` applies; `< > & = '` →
  `< > & = '` (lowercase). Port matches. Control chars `<0x20` →
  `\u00xx` lowercase, with `\b \t \n \f \r` overrides — port matches Gson's `REPLACEMENT_CHARS`.
  (Only gap is U+2028/2029 — see B1.)
- **GTV→JSON big_integer:** `to_json` uses `LENIENT_GSON` (`strict=false`) → bare number via
  `BigInteger.toString()`; `Rt_GtvValue.str` uses strict GSON (`supportBigInteger=false`) → throws.
  Port's `to_json(v, true/false)` split matches both.
- **Keccak-256 padding 0x01 (NOT SHA3's 0x06):** verified `lib_crypto.kt` uses BouncyCastle
  `Keccak.Digest256()`; port's sponge uses suffix `0x01`, and the empty-input vector
  `c5d2460186f7233c…85a470` is the correct Ethereum keccak256 (would be different for SHA3). SHA-256
  NIST vectors present and correct.
- **secp256k1 signing algorithm** (RFC6979/HMAC-SHA256 + low-S + 32‖32 BE) and **eth recId = v−27 =
  y∈{0,1}** match the JVM source (BouncyCastle + vendored etherjar). Byte-identity still needs a
  fixed JVM-captured vector — see H2.
- **JSON (Jackson) canonical `to_text`:** key order preserved (NOT sorted) via `members` vector;
  compact (no spaces); `/` not escaped; non-ASCII passes through; control chars `\u00XX` uppercase;
  duplicate-key "last value, first position" — all match Jackson `JsonNode.toString()`. (Pending the
  number/surrogate gates M4/M5.)
- **JSON int-range predicates** `json_can_be_rell_integer` (±2^63 boundary incl. min/max) verified by
  the self-test boundary cases.

---

## Prioritized fix order
1. **B1** (U+2028/2029 in GTV→JSON) — small, surgical, real chain-data risk.
2. **B2** (GTV `from_json` integer-valued decimals) — common input (`1.0`, `1e2`).
3. **B3** (GTV `from_json` blank → `GtvNull`) — trivial, but flips error↔value.
4. **H2** (lock `get_signature`/`eth_sign` with a JVM-captured byte vector) — highest-stakes
   consensus surface; algorithm is right, byte-identity unproven.
5. **H1 / H3** (lenient acceptance; recId>3) — narrower, gate + decide.
6. **M1–M5** (assert/lock with golden vectors; consider Ryū for M4).
7. **N2** (fix the RIPEMD-160 expected-vector typo `…bfc` → `…bce`).
