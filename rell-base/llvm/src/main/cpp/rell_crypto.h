// Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
//
// rell_crypto.h — rell::crypto: native C++17 implementations of the pure-but-
// currently-JNI Rell stdlib `crypto` family, for the Rell LLVM backend. Bit-exact
// with the JVM/Postchain implementations (consensus code), with ZERO JNI callbacks.
//
// Source of truth (Rell side):
//   rell-base/runtime-core/src/main/kotlin/lib/lib_crypto.kt  (Lib_Crypto)
// which delegates the actual crypto to:
//   - org.bouncycastle.jcajce.provider.digest.Keccak.Digest256  (keccak256)
//   - java.security.MessageDigest("SHA-256")                    (sha256)
//   - net.postchain.crypto.Secp256K1CryptoSystem (BouncyCastle ECDSA, RFC6979,
//       low-S canonical)                                        (get_signature/verify)
//   - net.postchain.rell.base.utils.etherjar.Signer             (eth_sign/eth_ecrecover)
//   - org.bouncycastle.math.ec.ECPoint / CURVE_PARAMS (secp256k1) (key conversions)
//
// This file maps:
//   - SHA-256        -> self-contained FIPS 180-4 impl (asserted against NIST vectors)
//   - Keccak-256     -> self-contained Keccak[r=1088,c=512] impl, padding suffix 0x01
//                       (Ethereum keccak256, NOT SHA3-256's 0x06; see brief §2)
//   - RIPEMD-160     -> self-contained impl (asserted against "abc" vector). NOTE: no
//                       Rell stdlib function currently exposes ripemd160 (brief §2 TODO);
//                       provided here for completeness / internal hash() paths.
//   - secp256k1 ECDSA-> libsecp256k1 (Homebrew `secp256k1`, /opt/homebrew/opt/secp256k1).
//                       We DO NOT reimplement EC math; we wrap libsecp256k1 and only
//                       enforce Rell's exact byte formats / recId / low-S semantics.
//
// IO CONVENTION: plain bytes in, plain bytes out (std::vector<uint8_t>) so the library
// unit-tests standalone without RellValue / the rest of the backend. Rell-level errors
// (wrong sizes, bad keys, out-of-range recId) are surfaced as rell::crypto::crypto_error
// carrying the same error code the JVM produces (e.g. "fn:get_signature:datahash_size:31"),
// so the backend integration layer can translate them to Rt_Exception faithfully.
//
// Determinism (consensus-critical): every output must be byte-identical to the JVM.
// crypto_self_test() asserts the published FIPS/Keccak/RIPEMD/secp256k1 vectors plus the
// end-to-end eth_ecrecover example from lib_crypto.kt's doc comment.
//
// Pure dependencies: C++ stdlib + <secp256k1.h> + <secp256k1_recovery.h>. No JNI, no LLVM,
// no FlatBuffers, no RellValue. Compile/test standalone (see bottom of rell_crypto.cpp).

#ifndef RELL_CRYPTO_H
#define RELL_CRYPTO_H

#include <array>
#include <cstdint>
#include <stdexcept>
#include <string>
#include <vector>

namespace rell::crypto {

using bytes = std::vector<uint8_t>;

// Rell-level crypto error. `code` mirrors the JVM error code (the first element of the
// `code to message` pair in Rt_Utils.check / Rt_Exception.common), so the integration
// layer can build the same Rt_Exception. For checks raised purely inside libsecp256k1
// (e.g. invalid signature parse) we use stable "crypto:..."-style codes.
class crypto_error : public std::runtime_error {
public:
    std::string code;
    crypto_error(std::string code_, const std::string& message)
        : std::runtime_error(message), code(std::move(code_)) {}
};

// =====================================================================================
// HASHES  (self-contained; no external deps)
// =====================================================================================

// SHA-256, FIPS 180-4. 32-byte output. Mirrors MessageDigest("SHA-256").digest(input).
std::array<uint8_t, 32> sha256(const uint8_t* data, size_t len);
inline std::array<uint8_t, 32> sha256(const bytes& data) { return sha256(data.data(), data.size()); }

// Keccak-256 (original Keccak, padding suffix 0x01 — Ethereum keccak256, the digest BC's
// Keccak.Digest256 produces). 32-byte output. NOT SHA3-256 (which pads with 0x06).
std::array<uint8_t, 32> keccak256(const uint8_t* data, size_t len);
inline std::array<uint8_t, 32> keccak256(const bytes& data) { return keccak256(data.data(), data.size()); }

// RIPEMD-160. 20-byte output. (No direct Rell stdlib function — see header note.)
std::array<uint8_t, 20> ripemd160(const uint8_t* data, size_t len);
inline std::array<uint8_t, 20> ripemd160(const bytes& data) { return ripemd160(data.data(), data.size()); }

// =====================================================================================
// secp256k1 ECDSA  (wraps libsecp256k1)
// =====================================================================================
//
// All functions below mirror the Rell stdlib `crypto.*` functions in lib_crypto.kt,
// byte-for-byte. Sizes / formats are validated exactly as Rell does.

// crypto.get_signature(data_hash[32], privkey[32]) -> 64-byte signature (r[32] || s[32]).
// ECDSA over secp256k1, RFC 6979 deterministic-k (HMAC-SHA256), low-S canonical, big-endian
// 32-byte r and s. Signs the digest directly (no rehash). Derives pubkey internally only to
// build the BC SigMaker; libsecp256k1 needs only the privkey.
//   throws crypto_error if data_hash != 32 bytes ("fn:get_signature:datahash_size:N")
//   throws crypto_error if privkey   != 32 bytes ("fn:get_signature:privkey_size:N")
//   throws crypto_error if privkey is not a valid scalar in [1, n-1]
bytes get_signature(const bytes& data_hash, const bytes& privkey);

// crypto.verify_signature(data_hash[32], pubkey[33|65], signature[64]) -> bool.
// Standard secp256k1 ECDSA verify. Accepts ONLY 33- or 65-byte pubkeys (NOT 64).
// Returns false (not throw) on well-formed-but-invalid input. To match BouncyCastle (which
// accepts non-canonical high-S signatures), S is normalized to low-S before verification.
//   throws crypto_error if data_hash != 32 bytes ("fn:verify_signature:datahash_size:N")
//   throws crypto_error (code "verify_signature") if pubkey size invalid or signature !=64
bool verify_signature(const bytes& data_hash, const bytes& pubkey, const bytes& signature);

// Result of eth_sign / the raw recoverable sign.
struct eth_signature {
    std::array<uint8_t, 32> r;  // big-endian
    std::array<uint8_t, 32> s;  // big-endian, low-S canonical
    int rec_id;                 // 0..3 (== Ethereum v - 27)
};

// crypto.eth_sign(data_hash[32], privkey[32]) -> (r[32], s[32], rec_id).
// Ethereum signature: same RFC6979/low-S ECDSA core, plus the recovery id. rec_id = v - 27.
//   throws crypto_error on bad data_hash / privkey sizes (same codes as get_signature but
//   fn = "eth_sign"), or invalid privkey.
eth_signature eth_sign(const bytes& data_hash, const bytes& privkey);

// crypto.eth_ecrecover(r, s, rec_id, data_hash[32]) -> 64-byte pubkey (uncompressed X||Y,
// WITHOUT the 0x04 prefix). r/s are interpreted as unsigned big-endian (any length, but
// canonically 32 bytes each; left-padded internally). v = rec_id + 27 in Rell, but
// libsecp256k1 takes recid in {0,1,2,3} == rec_id directly.
//   throws crypto_error if rec_id not in 0..100000 ("eth_ecrecover" / message "recId out of range")
//   throws crypto_error if data_hash != 32 bytes
//   throws crypto_error if recovery fails
bytes eth_ecrecover(const bytes& r, const bytes& s, int64_t rec_id, const bytes& data_hash);

// crypto.privkey_to_pubkey(privkey[32], compressed=false) -> 65 or 33 byte pubkey.
bytes privkey_to_pubkey(const bytes& privkey, bool compressed = false);

// crypto.pubkey_encode(pubkey[33|64|65], compressed=false) -> 65 or 33 byte pubkey.
// 64-byte input is accepted (Rell prepends 0x04 internally). Cannot output 64-byte.
bytes pubkey_encode(const bytes& pubkey, bool compressed = false);

// crypto.eth_privkey_to_address(privkey[32]) -> 20-byte Ethereum address.
// keccak256( uncompressed_pubkey_without_0x04_prefix )[12..32).
bytes eth_privkey_to_address(const bytes& privkey);

// crypto.eth_pubkey_to_address(pubkey[33|64|65]) -> 20-byte Ethereum address.
bytes eth_pubkey_to_address(const bytes& pubkey);

// crypto.pubkey_to_xy(pubkey[33|64|65]) -> (x, y) as 32-byte big-endian unsigned magnitudes.
// (Rell returns big_integer; here we return the canonical 32-byte unsigned encoding, which is
// exactly the magnitude the JVM's BigInteger holds. The backend wraps these into big_integer.)
struct point_xy {
    std::array<uint8_t, 32> x;
    std::array<uint8_t, 32> y;
};
point_xy pubkey_to_xy(const bytes& pubkey);

// crypto.xy_to_pubkey(x, y, compressed=false) -> 65 or 33 byte pubkey.
// x and y are unsigned big-endian magnitudes (any length <= 32 meaningful bytes; left-padded).
// Validates the point is on secp256k1.
//   throws crypto_error (code "crypto:bad_point") if (x,y) is not on the curve.
bytes xy_to_pubkey(const bytes& x, const bytes& y, bool compressed = false);

// =====================================================================================
// SELF-TEST
// =====================================================================================
// Asserts all published vectors (SHA-256 NIST, Keccak-256 empty/abc, RIPEMD-160 "abc",
// secp256k1 sign/verify round-trip + low-S, and the lib_crypto.kt eth_ecrecover doc example
// that recovers address 0x5b0c087542d5c1e66df0041e179c4201675b1614). Returns true on success;
// aborts via assert / throws on mismatch. Safe to call at startup.
bool crypto_self_test();

}  // namespace rell::crypto

#endif  // RELL_CRYPTO_H
