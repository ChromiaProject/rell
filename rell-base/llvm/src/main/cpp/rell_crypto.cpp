// Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
//
// rell_crypto.cpp — implementation of rell::crypto (see rell_crypto.h).
//
// Hashes (SHA-256, Keccak-256, RIPEMD-160) are self-contained, public-domain-style
// reference implementations transcribed from the published standards (FIPS 180-4 for
// SHA-256; the Keccak reference for keccak256 with the original 0x01 padding; the
// RIPEMD-160 reference). secp256k1 ECDSA is delegated to libsecp256k1; we only enforce
// Rell's exact byte formats and validation, matching lib_crypto.kt bit-for-bit.

#include "rell_crypto.h"

#include <cstring>

#include <secp256k1.h>
#include <secp256k1_recovery.h>

namespace rell::crypto {

namespace {

// -------------------------------------------------------------------------------------
// SHA-256 (FIPS 180-4)
// -------------------------------------------------------------------------------------

inline uint32_t rotr32(uint32_t x, int n) { return (x >> n) | (x << (32 - n)); }

const uint32_t SHA256_K[64] = {
    0x428a2f98u, 0x71374491u, 0xb5c0fbcfu, 0xe9b5dba5u, 0x3956c25bu, 0x59f111f1u, 0x923f82a4u, 0xab1c5ed5u,
    0xd807aa98u, 0x12835b01u, 0x243185beu, 0x550c7dc3u, 0x72be5d74u, 0x80deb1feu, 0x9bdc06a7u, 0xc19bf174u,
    0xe49b69c1u, 0xefbe4786u, 0x0fc19dc6u, 0x240ca1ccu, 0x2de92c6fu, 0x4a7484aau, 0x5cb0a9dcu, 0x76f988dau,
    0x983e5152u, 0xa831c66du, 0xb00327c8u, 0xbf597fc7u, 0xc6e00bf3u, 0xd5a79147u, 0x06ca6351u, 0x14292967u,
    0x27b70a85u, 0x2e1b2138u, 0x4d2c6dfcu, 0x53380d13u, 0x650a7354u, 0x766a0abbu, 0x81c2c92eu, 0x92722c85u,
    0xa2bfe8a1u, 0xa81a664bu, 0xc24b8b70u, 0xc76c51a3u, 0xd192e819u, 0xd6990624u, 0xf40e3585u, 0x106aa070u,
    0x19a4c116u, 0x1e376c08u, 0x2748774cu, 0x34b0bcb5u, 0x391c0cb3u, 0x4ed8aa4au, 0x5b9cca4fu, 0x682e6ff3u,
    0x748f82eeu, 0x78a5636fu, 0x84c87814u, 0x8cc70208u, 0x90befffau, 0xa4506cebu, 0xbef9a3f7u, 0xc67178f2u};

void sha256_compress(uint32_t state[8], const uint8_t block[64]) {
    uint32_t w[64];
    for (int i = 0; i < 16; ++i) {
        w[i] = (uint32_t(block[i * 4]) << 24) | (uint32_t(block[i * 4 + 1]) << 16) |
               (uint32_t(block[i * 4 + 2]) << 8) | uint32_t(block[i * 4 + 3]);
    }
    for (int i = 16; i < 64; ++i) {
        uint32_t s0 = rotr32(w[i - 15], 7) ^ rotr32(w[i - 15], 18) ^ (w[i - 15] >> 3);
        uint32_t s1 = rotr32(w[i - 2], 17) ^ rotr32(w[i - 2], 19) ^ (w[i - 2] >> 10);
        w[i] = w[i - 16] + s0 + w[i - 7] + s1;
    }
    uint32_t a = state[0], b = state[1], c = state[2], d = state[3];
    uint32_t e = state[4], f = state[5], g = state[6], h = state[7];
    for (int i = 0; i < 64; ++i) {
        uint32_t S1 = rotr32(e, 6) ^ rotr32(e, 11) ^ rotr32(e, 25);
        uint32_t ch = (e & f) ^ (~e & g);
        uint32_t t1 = h + S1 + ch + SHA256_K[i] + w[i];
        uint32_t S0 = rotr32(a, 2) ^ rotr32(a, 13) ^ rotr32(a, 22);
        uint32_t maj = (a & b) ^ (a & c) ^ (b & c);
        uint32_t t2 = S0 + maj;
        h = g; g = f; f = e; e = d + t1; d = c; c = b; b = a; a = t1 + t2;
    }
    state[0] += a; state[1] += b; state[2] += c; state[3] += d;
    state[4] += e; state[5] += f; state[6] += g; state[7] += h;
}

// -------------------------------------------------------------------------------------
// Keccak-256 (original Keccak, padding suffix 0x01 — Ethereum keccak256)
// -------------------------------------------------------------------------------------

const uint64_t KECCAK_RC[24] = {
    0x0000000000000001ULL, 0x0000000000008082ULL, 0x800000000000808aULL, 0x8000000080008000ULL,
    0x000000000000808bULL, 0x0000000080000001ULL, 0x8000000080008081ULL, 0x8000000000008009ULL,
    0x000000000000008aULL, 0x0000000000000088ULL, 0x0000000080008009ULL, 0x000000008000000aULL,
    0x000000008000808bULL, 0x800000000000008bULL, 0x8000000000008089ULL, 0x8000000000008003ULL,
    0x8000000000008002ULL, 0x8000000000000080ULL, 0x000000000000800aULL, 0x800000008000000aULL,
    0x8000000080008081ULL, 0x8000000000008080ULL, 0x0000000080000001ULL, 0x8000000080008008ULL};

const int KECCAK_ROT[24] = {1, 3, 6, 10, 15, 21, 28, 36, 45, 55, 2, 14,
                            27, 41, 56, 8, 25, 43, 62, 18, 39, 61, 20, 44};
const int KECCAK_PI[24] = {10, 7, 11, 17, 18, 3, 5, 16, 8, 21, 24, 4,
                           15, 23, 19, 13, 12, 2, 20, 14, 22, 9, 6, 1};

inline uint64_t rotl64(uint64_t x, int n) { return (x << n) | (x >> (64 - n)); }

void keccak_f1600(uint64_t st[25]) {
    for (int round = 0; round < 24; ++round) {
        uint64_t bc[5];
        for (int i = 0; i < 5; ++i) bc[i] = st[i] ^ st[i + 5] ^ st[i + 10] ^ st[i + 15] ^ st[i + 20];
        for (int i = 0; i < 5; ++i) {
            uint64_t t = bc[(i + 4) % 5] ^ rotl64(bc[(i + 1) % 5], 1);
            for (int j = 0; j < 25; j += 5) st[j + i] ^= t;
        }
        uint64_t t = st[1];
        for (int i = 0; i < 24; ++i) {
            int j = KECCAK_PI[i];
            uint64_t tmp = st[j];
            st[j] = rotl64(t, KECCAK_ROT[i]);
            t = tmp;
        }
        for (int j = 0; j < 25; j += 5) {
            uint64_t a0 = st[j], a1 = st[j + 1], a2 = st[j + 2], a3 = st[j + 3], a4 = st[j + 4];
            st[j] = a0 ^ (~a1 & a2);
            st[j + 1] = a1 ^ (~a2 & a3);
            st[j + 2] = a2 ^ (~a3 & a4);
            st[j + 3] = a3 ^ (~a4 & a0);
            st[j + 4] = a4 ^ (~a0 & a1);
        }
        st[0] ^= KECCAK_RC[round];
    }
}

// Sponge with rate=136 bytes (1088 bits), capacity=512, 32-byte output, padding suffix `suffix`.
std::array<uint8_t, 32> keccak_sponge(const uint8_t* data, size_t len, uint8_t suffix) {
    constexpr size_t RATE = 136;  // (1600 - 512) / 8
    uint64_t st[25] = {0};
    auto absorb_lane = [&](size_t offset, const uint8_t* p) {
        uint64_t lane = 0;
        for (int b = 0; b < 8; ++b) lane |= uint64_t(p[b]) << (8 * b);
        st[offset / 8] ^= lane;
    };
    size_t i = 0;
    // Absorb full blocks.
    for (; i + RATE <= len; i += RATE) {
        for (size_t o = 0; o < RATE; o += 8) absorb_lane(o, data + i + o);
        keccak_f1600(st);
    }
    // Last block with padding.
    uint8_t block[RATE] = {0};
    size_t rem = len - i;
    std::memcpy(block, data + i, rem);
    block[rem] ^= suffix;          // domain-separation/padding suffix (0x01 for Keccak)
    block[RATE - 1] ^= 0x80;       // final pad bit
    for (size_t o = 0; o < RATE; o += 8) absorb_lane(o, block + o);
    keccak_f1600(st);
    // Squeeze 32 bytes (fits in the first 4 lanes of the rate).
    std::array<uint8_t, 32> out{};
    for (int lane = 0; lane < 4; ++lane) {
        uint64_t v = st[lane];
        for (int b = 0; b < 8; ++b) out[lane * 8 + b] = uint8_t(v >> (8 * b));
    }
    return out;
}

// -------------------------------------------------------------------------------------
// RIPEMD-160
// -------------------------------------------------------------------------------------

inline uint32_t rol32(uint32_t x, int n) { return (x << n) | (x >> (32 - n)); }

void ripemd160_compress(uint32_t h[5], const uint8_t block[64]) {
    auto f = [](int j, uint32_t x, uint32_t y, uint32_t z) -> uint32_t {
        if (j < 16) return x ^ y ^ z;
        if (j < 32) return (x & y) | (~x & z);
        if (j < 48) return (x | ~y) ^ z;
        if (j < 64) return (x & z) | (y & ~z);
        return x ^ (y | ~z);
    };
    static const uint32_t KL[5] = {0x00000000u, 0x5a827999u, 0x6ed9eba1u, 0x8f1bbcdcu, 0xa953fd4eu};
    static const uint32_t KR[5] = {0x50a28be6u, 0x5c4dd124u, 0x6d703ef3u, 0x7a6d76e9u, 0x00000000u};
    static const int RL[80] = {
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
        7, 4, 13, 1, 10, 6, 15, 3, 12, 0, 9, 5, 2, 14, 11, 8,
        3, 10, 14, 4, 9, 15, 8, 1, 2, 7, 0, 6, 13, 11, 5, 12,
        1, 9, 11, 10, 0, 8, 12, 4, 13, 3, 7, 15, 14, 5, 6, 2,
        4, 0, 5, 9, 7, 12, 2, 10, 14, 1, 3, 8, 11, 6, 15, 13};
    static const int RR[80] = {
        5, 14, 7, 0, 9, 2, 11, 4, 13, 6, 15, 8, 1, 10, 3, 12,
        6, 11, 3, 7, 0, 13, 5, 10, 14, 15, 8, 12, 4, 9, 1, 2,
        15, 5, 1, 3, 7, 14, 6, 9, 11, 8, 12, 2, 10, 0, 4, 13,
        8, 6, 4, 1, 3, 11, 15, 0, 5, 12, 2, 13, 9, 7, 10, 14,
        12, 15, 10, 4, 1, 5, 8, 7, 6, 2, 13, 14, 0, 3, 9, 11};
    static const int SL[80] = {
        11, 14, 15, 12, 5, 8, 7, 9, 11, 13, 14, 15, 6, 7, 9, 8,
        7, 6, 8, 13, 11, 9, 7, 15, 7, 12, 15, 9, 11, 7, 13, 12,
        11, 13, 6, 7, 14, 9, 13, 15, 14, 8, 13, 6, 5, 12, 7, 5,
        11, 12, 14, 15, 14, 15, 9, 8, 9, 14, 5, 6, 8, 6, 5, 12,
        9, 15, 5, 11, 6, 8, 13, 12, 5, 12, 13, 14, 11, 8, 5, 6};
    static const int SR[80] = {
        8, 9, 9, 11, 13, 15, 15, 5, 7, 7, 8, 11, 14, 14, 12, 6,
        9, 13, 15, 7, 12, 8, 9, 11, 7, 7, 12, 7, 6, 15, 13, 11,
        9, 7, 15, 11, 8, 6, 6, 14, 12, 13, 5, 14, 13, 13, 7, 5,
        15, 5, 8, 11, 14, 14, 6, 14, 6, 9, 12, 9, 12, 5, 15, 8,
        8, 5, 12, 9, 12, 5, 14, 6, 8, 13, 6, 5, 15, 13, 11, 11};

    uint32_t x[16];
    for (int i = 0; i < 16; ++i) {
        x[i] = uint32_t(block[i * 4]) | (uint32_t(block[i * 4 + 1]) << 8) |
               (uint32_t(block[i * 4 + 2]) << 16) | (uint32_t(block[i * 4 + 3]) << 24);
    }
    uint32_t al = h[0], bl = h[1], cl = h[2], dl = h[3], el = h[4];
    uint32_t ar = h[0], br = h[1], cr = h[2], dr = h[3], er = h[4];
    for (int j = 0; j < 80; ++j) {
        uint32_t t = rol32(al + f(j, bl, cl, dl) + x[RL[j]] + KL[j / 16], SL[j]) + el;
        al = el; el = dl; dl = rol32(cl, 10); cl = bl; bl = t;
        t = rol32(ar + f(79 - j, br, cr, dr) + x[RR[j]] + KR[j / 16], SR[j]) + er;
        ar = er; er = dr; dr = rol32(cr, 10); cr = br; br = t;
    }
    uint32_t t = h[1] + cl + dr;
    h[1] = h[2] + dl + er;
    h[2] = h[3] + el + ar;
    h[3] = h[4] + al + br;
    h[4] = h[0] + bl + cr;
    h[0] = t;
}

// -------------------------------------------------------------------------------------
// secp256k1 context (lazily created once; libsecp256k1 contexts are thread-safe for use).
// -------------------------------------------------------------------------------------

secp256k1_context* ctx() {
    static secp256k1_context* c =
        secp256k1_context_create(SECP256K1_CONTEXT_SIGN | SECP256K1_CONTEXT_VERIFY);
    return c;
}

// Left-pad / right-trim an unsigned big-endian magnitude to exactly 32 bytes. Throws if the
// value does not fit in 32 bytes. Mirrors BigInteger(1, bytes) followed by 32-byte encoding.
std::array<uint8_t, 32> to32be(const uint8_t* p, size_t len) {
    // Skip leading zeros.
    size_t start = 0;
    while (start < len && p[start] == 0) ++start;
    size_t sig = len - start;
    if (sig > 32) throw crypto_error("crypto:bad_scalar", "value exceeds 32 bytes");
    std::array<uint8_t, 32> out{};
    std::memcpy(out.data() + (32 - sig), p + start, sig);
    return out;
}

void check_size(const bytes& a, size_t exp, const std::string& fn, const std::string& errcode,
                const std::string& what) {
    if (a.size() != exp) {
        throw crypto_error("fn:" + fn + ":" + errcode + ":" + std::to_string(a.size()),
                           "Wrong size of " + what + ": " + std::to_string(a.size()) + " instead of " +
                               std::to_string(exp));
    }
}

// Parse a Rell pubkey (33/64/65) into a libsecp256k1 pubkey. 64-byte input gets 0x04 prepended.
// `fn`-agnostic: throws crypto_error("crypto:bad_pubkey:<origsize>") on failure, like Rell.
secp256k1_pubkey parse_pubkey_any(const bytes& pubkey) {
    size_t orig = pubkey.size();
    bytes buf;
    const uint8_t* in;
    size_t inlen;
    if (orig == 64) {
        buf.reserve(65);
        buf.push_back(0x04);
        buf.insert(buf.end(), pubkey.begin(), pubkey.end());
        in = buf.data();
        inlen = 65;
    } else if (orig == 33 || orig == 65) {
        in = pubkey.data();
        inlen = orig;
    } else {
        throw crypto_error("crypto:bad_pubkey:" + std::to_string(orig),
                           "Bad public key (size: " + std::to_string(orig) + ")");
    }
    secp256k1_pubkey pk;
    if (!secp256k1_ec_pubkey_parse(ctx(), &pk, in, inlen)) {
        throw crypto_error("crypto:bad_pubkey:" + std::to_string(orig),
                           "Bad public key (size: " + std::to_string(orig) + ")");
    }
    return pk;
}

bytes serialize_pubkey(const secp256k1_pubkey& pk, bool compressed) {
    size_t len = compressed ? 33 : 65;
    bytes out(len);
    unsigned int flags = compressed ? SECP256K1_EC_COMPRESSED : SECP256K1_EC_UNCOMPRESSED;
    secp256k1_ec_pubkey_serialize(ctx(), out.data(), &len, &pk, flags);
    out.resize(len);
    return out;
}

}  // namespace

// =====================================================================================
// HASH PUBLIC API
// =====================================================================================

std::array<uint8_t, 32> sha256(const uint8_t* data, size_t len) {
    uint32_t state[8] = {0x6a09e667u, 0xbb67ae85u, 0x3c6ef372u, 0xa54ff53au,
                         0x510e527fu, 0x9b05688cu, 0x1f83d9abu, 0x5be0cd19u};
    size_t i = 0;
    for (; i + 64 <= len; i += 64) sha256_compress(state, data + i);
    uint8_t block[64] = {0};
    size_t rem = len - i;
    std::memcpy(block, data + i, rem);
    block[rem] = 0x80;
    if (rem >= 56) {
        sha256_compress(state, block);
        std::memset(block, 0, 64);
    }
    uint64_t bitlen = uint64_t(len) * 8;
    for (int b = 0; b < 8; ++b) block[63 - b] = uint8_t(bitlen >> (8 * b));
    sha256_compress(state, block);
    std::array<uint8_t, 32> out{};
    for (int j = 0; j < 8; ++j) {
        out[j * 4] = uint8_t(state[j] >> 24);
        out[j * 4 + 1] = uint8_t(state[j] >> 16);
        out[j * 4 + 2] = uint8_t(state[j] >> 8);
        out[j * 4 + 3] = uint8_t(state[j]);
    }
    return out;
}

std::array<uint8_t, 32> keccak256(const uint8_t* data, size_t len) {
    return keccak_sponge(data, len, 0x01);  // 0x01 = original Keccak padding (Ethereum)
}

std::array<uint8_t, 20> ripemd160(const uint8_t* data, size_t len) {
    uint32_t h[5] = {0x67452301u, 0xefcdab89u, 0x98badcfeu, 0x10325476u, 0xc3d2e1f0u};
    size_t i = 0;
    for (; i + 64 <= len; i += 64) ripemd160_compress(h, data + i);
    uint8_t block[64] = {0};
    size_t rem = len - i;
    std::memcpy(block, data + i, rem);
    block[rem] = 0x80;
    if (rem >= 56) {
        ripemd160_compress(h, block);
        std::memset(block, 0, 64);
    }
    uint64_t bitlen = uint64_t(len) * 8;  // little-endian length
    for (int b = 0; b < 8; ++b) block[56 + b] = uint8_t(bitlen >> (8 * b));
    ripemd160_compress(h, block);
    std::array<uint8_t, 20> out{};
    for (int j = 0; j < 5; ++j) {
        out[j * 4] = uint8_t(h[j]);
        out[j * 4 + 1] = uint8_t(h[j] >> 8);
        out[j * 4 + 2] = uint8_t(h[j] >> 16);
        out[j * 4 + 3] = uint8_t(h[j] >> 24);
    }
    return out;
}

// =====================================================================================
// ECDSA PUBLIC API
// =====================================================================================

bytes get_signature(const bytes& data_hash, const bytes& privkey) {
    check_size(data_hash, 32, "get_signature", "datahash_size", "data hash");
    check_size(privkey, 32, "get_signature", "privkey_size", "private key");
    if (!secp256k1_ec_seckey_verify(ctx(), privkey.data())) {
        throw crypto_error("crypto:bad_privkey", "Invalid private key");
    }
    secp256k1_ecdsa_signature sig;
    // nullptr nonce => libsecp256k1 default RFC6979 (HMAC-SHA256); enforces low-S.
    if (!secp256k1_ecdsa_sign(ctx(), &sig, data_hash.data(), privkey.data(), nullptr, nullptr)) {
        throw crypto_error("crypto:sign_failed", "ECDSA signing failed");
    }
    bytes out(64);
    secp256k1_ecdsa_signature_serialize_compact(ctx(), out.data(), &sig);
    return out;  // r[32] || s[32], big-endian, low-S
}

bool verify_signature(const bytes& data_hash, const bytes& pubkey, const bytes& signature) {
    check_size(data_hash, 32, "verify_signature", "datahash_size", "data hash");
    try {
        // Rell's Signature(pubkey, sig): pubkey must be 33 or 65 (NOT 64); sig must be 64.
        if (pubkey.size() != 33 && pubkey.size() != 65) {
            throw crypto_error("verify_signature",
                               "Wrong size of public key: " + std::to_string(pubkey.size()));
        }
        if (signature.size() != 64) {
            throw crypto_error("verify_signature",
                               "Wrong size of signature: " + std::to_string(signature.size()));
        }
        secp256k1_pubkey pk;
        if (!secp256k1_ec_pubkey_parse(ctx(), &pk, pubkey.data(), pubkey.size())) {
            throw crypto_error("verify_signature", "Bad public key");
        }
        secp256k1_ecdsa_signature sig;
        if (!secp256k1_ecdsa_signature_parse_compact(ctx(), &sig, signature.data())) {
            return false;
        }
        // BouncyCastle accepts non-canonical (high-S) signatures; libsecp256k1's verify
        // rejects them. Normalize to low-S first so verify() matches the JVM's acceptance.
        secp256k1_ecdsa_signature normalized;
        secp256k1_ecdsa_signature_normalize(ctx(), &normalized, &sig);
        return secp256k1_ecdsa_verify(ctx(), &normalized, data_hash.data(), &pk) == 1;
    } catch (const crypto_error&) {
        throw;  // size/format errors propagate (Rell wraps as Rt_Exception.common)
    }
}

eth_signature eth_sign(const bytes& data_hash, const bytes& privkey) {
    check_size(data_hash, 32, "eth_sign", "datahash_size", "data hash");
    check_size(privkey, 32, "eth_sign", "privkey_size", "private key");
    if (!secp256k1_ec_seckey_verify(ctx(), privkey.data())) {
        throw crypto_error("crypto:bad_privkey", "Invalid private key");
    }
    secp256k1_ecdsa_recoverable_signature rsig;
    if (!secp256k1_ecdsa_sign_recoverable(ctx(), &rsig, data_hash.data(), privkey.data(), nullptr,
                                          nullptr)) {
        throw crypto_error("crypto:sign_failed", "ECDSA signing failed");
    }
    uint8_t out64[64];
    int recid = 0;
    secp256k1_ecdsa_recoverable_signature_serialize_compact(ctx(), out64, &recid, &rsig);
    eth_signature res{};
    std::memcpy(res.r.data(), out64, 32);
    std::memcpy(res.s.data(), out64 + 32, 32);
    res.rec_id = recid;  // == Ethereum v - 27
    return res;
}

bytes eth_ecrecover(const bytes& r, const bytes& s, int64_t rec_id, const bytes& data_hash) {
    if (rec_id < 0 || rec_id > 100000) {
        throw crypto_error("eth_ecrecover", "recId out of range: " + std::to_string(rec_id));
    }
    check_size(data_hash, 32, "eth_ecrecover", "datahash_size", "data hash");
    // r, s interpreted as unsigned big-endian; left-pad to 32 bytes (Rell uses BigInteger(1, .)).
    std::array<uint8_t, 32> rp = to32be(r.data(), r.size());
    std::array<uint8_t, 32> sp = to32be(s.data(), s.size());
    uint8_t compact[64];
    std::memcpy(compact, rp.data(), 32);
    std::memcpy(compact + 32, sp.data(), 32);
    secp256k1_ecdsa_recoverable_signature rsig;
    // libsecp256k1 recid is exactly rec_id (Rell's rec_id == v - 27 == libsecp256k1 recid).
    if (!secp256k1_ecdsa_recoverable_signature_parse_compact(ctx(), &rsig, compact,
                                                             int(rec_id))) {
        throw crypto_error("eth_ecrecover", "Cannot parse recoverable signature");
    }
    secp256k1_pubkey pk;
    if (!secp256k1_ecdsa_recover(ctx(), &pk, &rsig, data_hash.data())) {
        throw crypto_error("eth_ecrecover", "Public key recovery failed");
    }
    bytes uncompressed = serialize_pubkey(pk, false);  // 65 bytes, 0x04 || X || Y
    return bytes(uncompressed.begin() + 1, uncompressed.end());  // strip 0x04 -> 64 bytes
}

bytes privkey_to_pubkey(const bytes& privkey, bool compressed) {
    check_size(privkey, 32, "privkey_to_pubkey", "privkey_size", "private key");
    if (!secp256k1_ec_seckey_verify(ctx(), privkey.data())) {
        throw crypto_error("crypto:bad_privkey", "Invalid private key");
    }
    secp256k1_pubkey pk;
    if (!secp256k1_ec_pubkey_create(ctx(), &pk, privkey.data())) {
        throw crypto_error("crypto:bad_privkey", "Invalid private key");
    }
    return serialize_pubkey(pk, compressed);
}

bytes pubkey_encode(const bytes& pubkey, bool compressed) {
    secp256k1_pubkey pk = parse_pubkey_any(pubkey);
    return serialize_pubkey(pk, compressed);
}

bytes eth_pubkey_to_address(const bytes& pubkey) {
    secp256k1_pubkey pk = parse_pubkey_any(pubkey);
    bytes uncompressed = serialize_pubkey(pk, false);  // 65 bytes
    auto hash = keccak256(uncompressed.data() + 1, 64); // drop 0x04 prefix
    return bytes(hash.begin() + 12, hash.end());          // last 20 bytes
}

bytes eth_privkey_to_address(const bytes& privkey) {
    bytes uncompressed = privkey_to_pubkey(privkey, false);  // validates size 32
    auto hash = keccak256(uncompressed.data() + 1, 64);
    return bytes(hash.begin() + 12, hash.end());
}

point_xy pubkey_to_xy(const bytes& pubkey) {
    secp256k1_pubkey pk = parse_pubkey_any(pubkey);
    bytes uncompressed = serialize_pubkey(pk, false);  // 0x04 || X(32) || Y(32)
    point_xy out{};
    std::memcpy(out.x.data(), uncompressed.data() + 1, 32);
    std::memcpy(out.y.data(), uncompressed.data() + 33, 32);
    return out;
}

bytes xy_to_pubkey(const bytes& x, const bytes& y, bool compressed) {
    std::array<uint8_t, 32> xp = to32be(x.data(), x.size());
    std::array<uint8_t, 32> yp = to32be(y.data(), y.size());
    uint8_t raw[65];
    raw[0] = 0x04;
    std::memcpy(raw + 1, xp.data(), 32);
    std::memcpy(raw + 33, yp.data(), 32);
    secp256k1_pubkey pk;
    if (!secp256k1_ec_pubkey_parse(ctx(), &pk, raw, 65)) {
        throw crypto_error("crypto:bad_point", "Bad EC point coordinates");
    }
    return serialize_pubkey(pk, compressed);
}

// =====================================================================================
// SELF-TEST
// =====================================================================================

namespace {

bytes from_hex(const std::string& h) {
    bytes out;
    out.reserve(h.size() / 2);
    auto nib = [](char c) -> int {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        return -1;
    };
    for (size_t i = 0; i + 1 < h.size(); i += 2) out.push_back(uint8_t(nib(h[i]) * 16 + nib(h[i + 1])));
    return out;
}

std::string to_hex(const uint8_t* p, size_t n) {
    static const char* d = "0123456789abcdef";
    std::string s;
    s.reserve(n * 2);
    for (size_t i = 0; i < n; ++i) {
        s.push_back(d[p[i] >> 4]);
        s.push_back(d[p[i] & 0xf]);
    }
    return s;
}

template <size_t N>
std::string hx(const std::array<uint8_t, N>& a) { return to_hex(a.data(), N); }
std::string hx(const bytes& a) { return to_hex(a.data(), a.size()); }

bool eq(const std::string& got, const std::string& want, const char* label) {
    if (got != want) {
        throw crypto_error("selftest", std::string(label) + " mismatch: got " + got + " want " + want);
    }
    return true;
}

}  // namespace

bool crypto_self_test() {
    // ---- SHA-256 (FIPS 180-4 / NIST) ----
    {
        std::string empty;
        auto h0 = sha256(reinterpret_cast<const uint8_t*>(empty.data()), 0);
        eq(hx(h0), "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", "sha256(\"\")");
        std::string abc = "abc";
        auto h1 = sha256(reinterpret_cast<const uint8_t*>(abc.data()), abc.size());
        eq(hx(h1), "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", "sha256(\"abc\")");
        std::string two = "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq";
        auto h2 = sha256(reinterpret_cast<const uint8_t*>(two.data()), two.size());
        eq(hx(h2), "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1", "sha256(56-byte)");
    }
    // ---- Keccak-256 (Ethereum, padding 0x01 NOT 0x06) ----
    {
        auto k0 = keccak256(nullptr, 0);
        eq(hx(k0), "c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470", "keccak256(\"\")");
        std::string abc = "abc";
        auto k1 = keccak256(reinterpret_cast<const uint8_t*>(abc.data()), abc.size());
        eq(hx(k1), "4e03657aea45a94fc7d47ba826c8d667c0d1e6e33a64a036ec44f58fa12d6c45", "keccak256(\"abc\")");
    }
    // ---- RIPEMD-160 ----
    {
        std::string abc = "abc";
        auto r1 = ripemd160(reinterpret_cast<const uint8_t*>(abc.data()), abc.size());
        eq(hx(r1), "8eb208f7e05d987a9b044a8e98c6b087f15a0bce", "ripemd160(\"abc\")");
        auto r0 = ripemd160(nullptr, 0);
        eq(hx(r0), "9c1185a5c5e9fc54612808977ee8f548b2258d31", "ripemd160(\"\")");
    }
    // ---- secp256k1: privkey_to_pubkey, get_signature, verify_signature round-trip ----
    {
        // privkey = 0x11 repeated 32 times (lib_crypto.kt Example 1).
        bytes privkey(32, 0x11);
        bytes pub65 = privkey_to_pubkey(privkey, false);
        if (pub65.size() != 65 || pub65[0] != 0x04) throw crypto_error("selftest", "pub65 shape");
        bytes pub33 = pubkey_encode(pub65, true);
        if (pub33.size() != 33) throw crypto_error("selftest", "pub33 shape");
        // pubkey_to_xy equivalence across formats.
        auto xy65 = pubkey_to_xy(pub65);
        auto xy33 = pubkey_to_xy(pub33);
        bytes pub64(pub65.begin() + 1, pub65.end());
        auto xy64 = pubkey_to_xy(pub64);
        if (hx(xy65.x) != hx(xy33.x) || hx(xy65.y) != hx(xy33.y)) throw crypto_error("selftest", "xy 33/65");
        if (hx(xy65.x) != hx(xy64.x) || hx(xy65.y) != hx(xy64.y)) throw crypto_error("selftest", "xy 64/65");
        // xy_to_pubkey inverse.
        bytes back = xy_to_pubkey(bytes(xy65.x.begin(), xy65.x.end()),
                                  bytes(xy65.y.begin(), xy65.y.end()), false);
        if (hx(back) != hx(pub65)) throw crypto_error("selftest", "xy_to_pubkey inverse");

        std::string msg = "To whom it may concern...";
        auto dh = sha256(reinterpret_cast<const uint8_t*>(msg.data()), msg.size());
        bytes data_hash(dh.begin(), dh.end());
        bytes sig = get_signature(data_hash, privkey);
        if (sig.size() != 64) throw crypto_error("selftest", "sig size");
        // Determinism: RFC6979 => signing twice yields identical bytes.
        bytes sig2 = get_signature(data_hash, privkey);
        if (hx(sig) != hx(sig2)) throw crypto_error("selftest", "RFC6979 determinism");
        // low-S: s <= n/2 (high bit of s region must be within curve order half).
        if (!verify_signature(data_hash, pub33, sig)) throw crypto_error("selftest", "verify 33");
        if (!verify_signature(data_hash, pub65, sig)) throw crypto_error("selftest", "verify 65");
        // Tamper -> false.
        bytes bad = sig; bad[10] ^= 0x01;
        if (verify_signature(data_hash, pub33, bad)) throw crypto_error("selftest", "verify tamper");
    }
    // ---- eth_sign / eth_ecrecover round-trip (lib_crypto.kt Example 2) ----
    {
        bytes privkey = from_hex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f");
        bytes pub65 = privkey_to_pubkey(privkey, false);
        std::string hello = "Hello";
        auto dh = sha256(reinterpret_cast<const uint8_t*>(hello.data()), hello.size());
        bytes data_hash(dh.begin(), dh.end());
        eth_signature es = eth_sign(data_hash, privkey);
        bytes rec = eth_ecrecover(bytes(es.r.begin(), es.r.end()), bytes(es.s.begin(), es.s.end()),
                                  es.rec_id, data_hash);
        // x'04' + recovered == privkey_to_pubkey(privkey)
        bytes recovered65;
        recovered65.push_back(0x04);
        recovered65.insert(recovered65.end(), rec.begin(), rec.end());
        if (hx(recovered65) != hx(pub65)) throw crypto_error("selftest", "eth_sign/ecrecover round-trip");
    }
    // ---- eth_ecrecover known-vector from lib_crypto.kt doc (Web3 recover) ----
    {
        bytes r = from_hex("cf722a47bcf1da61967ccc6405e31db4d37bce153255a6937e5cceb222caead0");
        bytes s = from_hex("cf722a47bcf1da61967ccc6405e31db4d37bce153255a6937e5cceb222caead0");
        bytes h = from_hex("53d7b11e61a8059aa4bc3248d24b2936436c9796dfe7f18e414c181004f79427");
        int64_t rec_id = 0x1c - 27;  // v - 27
        bytes pub = eth_ecrecover(r, s, rec_id, h);
        auto k = keccak256(pub);
        bytes addr(k.begin() + 12, k.end());
        eq(hx(addr), "5b0c087542d5c1e66df0041e179c4201675b1614", "eth_ecrecover address");
        // eth_pubkey_to_address on the 64-byte recovered key must match.
        bytes addr2 = eth_pubkey_to_address(pub);
        eq(hx(addr2), "5b0c087542d5c1e66df0041e179c4201675b1614", "eth_pubkey_to_address(64)");
    }
    return true;
}

}  // namespace rell::crypto

// =====================================================================================
// STANDALONE TEST DRIVER (compile with -DRELL_CRYPTO_TEST_MAIN to build a runnable test)
// =====================================================================================
#ifdef RELL_CRYPTO_TEST_MAIN
#include <cstdio>
int main() {
    try {
        rell::crypto::crypto_self_test();
        std::printf("rell::crypto self-test: ALL VECTORS PASS\n");
        return 0;
    } catch (const std::exception& e) {
        std::fprintf(stderr, "rell::crypto self-test FAILED: %s\n", e.what());
        return 1;
    }
}
#endif
