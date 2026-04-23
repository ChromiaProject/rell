// Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
//
// rell_gtv.h — rell::gtv: a faithful, standalone C++17 reimplementation of the
// Postchain GTV value model + ASN.1 DER codec used by the Rell stdlib (gtv.to_bytes,
// gtv.from_bytes, gtv.to_json, gtv.from_json). Bit-exact with
// net.postchain.gtv.GtvEncoder / GtvFactory (postchain-gtv 3.49.12), which is
// consensus-critical code.
//
// SCOPE
//   IN  : Gtv value model (Null/ByteArray/String/Integer/Dictionary/Array/BigInteger),
//         encode_to_bytes (ASN.1 DER), decode_from_bytes, to_json / from_json bridges.
//   OUT : GtvMerkleHash (gtv.hash() / gtv.legacy_hash()) — requires the postchain
//         GtvBinaryTreeFactory V1/V2 tree-balancing rules to be transcribed first.
//         See the TODO at the bottom of rell_gtv.cpp. Encoding/decoding/json do NOT
//         need the merkle tree, so they ship here independently.
//
// DEPENDENCIES: C++ standard library only (<cstdint>, <string>, <vector>, <map>,
//   <memory>, <stdexcept>). No JNI, no LLVM, no FlatBuffers, no big-integer library:
//   GtvBigInteger carries the value as a two's-complement big-endian DER INTEGER body
//   plus a sign, which is exactly the on-wire form and all the codec needs. (If the
//   live backend later needs arithmetic on GtvBigInteger, layer rell::num::RellBigInt
//   on top — this header deliberately does not depend on it so it tests standalone.)
//
// =====================================================================================
// WIRE FORMAT (verified against jasn1-generated RawGtv in postchain-gtv 3.49.12)
// =====================================================================================
//
// Each Gtv is encoded as  [n] EXPLICIT <universal-DER>  — a context-class CONSTRUCTED
// outer tag wrapping one full universal TLV:
//
//   Gtv ::= An <outer-len> <inner-TLV>
//
//   subtype        outer-tag   inner universal TLV
//   GtvNull        0xA0        05 00                       (NULL)
//   GtvByteArray   0xA1        04 <len> <bytes>            (OCTET STRING)
//   GtvString      0xA2        0C <len> <utf8>             (UTF8String)
//   GtvInteger     0xA3        02 <len> <int>              (INTEGER, two's-complement)
//   GtvDictionary  0xA4        30 <len> <SEQUENCE OF DictPair, key-sorted>
//   GtvArray       0xA5        30 <len> <SEQUENCE OF Gtv>
//   GtvBigInteger  0xA6        02 <len> <int>              (INTEGER, two's-complement)
//
//   DictPair ::= SEQUENCE { name UTF8String, value Gtv }  =  30 <len> 0C.. An..
//
// DER length: short form for len <= 127 (one byte); long form 0x8m + m big-endian
// bytes for len >= 128. INTEGER body is minimal two's-complement big-endian (strip
// redundant 0x00/0xFF, add a leading 0x00 to keep a positive value positive) — i.e.
// exactly java.math.BigInteger.toByteArray() semantics. Integer vs big_integer differ
// ONLY by the outer tag (0xA3 vs 0xA6); the inner INTEGER bytes are identical.
//
// Dictionaries are ALWAYS key-sorted ascending by Kotlin String.compareTo, i.e.
// UTF-16-code-unit ordering, before encoding (consensus-critical). See cmp_utf16().

#ifndef RELL_GTV_H
#define RELL_GTV_H

#include <cstdint>
#include <map>
#include <memory>
#include <stdexcept>
#include <string>
#include <vector>

namespace rell {
namespace gtv {

using bytes = std::vector<uint8_t>;

// Mirrors net.postchain.gtv.GtvType ordinals (also the Rell `gtv_type` enum):
// NULL=0, BYTEARRAY=1, STRING=2, INTEGER=3, DICT=4, ARRAY=5, BIGINTEGER=6.
enum class GtvType : int {
    NULLV = 0,
    BYTEARRAY = 1,
    STRING = 2,
    INTEGER = 3,
    DICT = 4,
    ARRAY = 5,
    BIGINTEGER = 6,
};

// Thrown on malformed input (decode) or unencodable values.
class GtvError : public std::runtime_error {
public:
    explicit GtvError(const std::string &m) : std::runtime_error(m) {}
};

class Gtv;
using GtvPtr = std::shared_ptr<const Gtv>;

// Immutable GTV value. A single class (tagged union) rather than a subtype hierarchy:
// it mirrors net.postchain.gtv.Gtv's seven concrete subtypes via `type()` + accessors.
//
// Construct via the static factories; they enforce invariants (dict key sort,
// big-integer minimal form). Direct field access is intentionally not exposed.
class Gtv {
public:
    // ---- Factories ----
    static GtvPtr make_null();
    static GtvPtr make_byte_array(bytes value);
    static GtvPtr make_string(std::string value);          // value is UTF-8
    static GtvPtr make_integer(int64_t value);
    static GtvPtr make_array(std::vector<GtvPtr> elements);
    // Keys are UTF-8 strings; the map is stored sorted by UTF-16-code-unit order.
    static GtvPtr make_dict(std::map<std::string, GtvPtr> entries);
    static GtvPtr make_dict(std::vector<std::pair<std::string, GtvPtr>> entries);
    // big_integer carried as a signed magnitude. `magnitude_be` is the unsigned
    // big-endian magnitude (no sign byte); `negative` is the sign. Zero => empty.
    static GtvPtr make_big_integer(bool negative, bytes magnitude_be);
    // Convenience: build a big_integer from a base-10 decimal string ("-123", "0", ...).
    static GtvPtr make_big_integer_dec(const std::string &decimal);

    GtvType type() const { return type_; }

    // ---- Accessors (throw GtvError on type mismatch) ----
    const bytes &as_byte_array() const;
    const std::string &as_string() const;
    int64_t as_integer() const;
    const std::vector<GtvPtr> &as_array() const;
    const std::vector<std::pair<std::string, GtvPtr>> &as_dict() const;
    // big_integer sign + unsigned big-endian magnitude
    bool big_integer_negative() const;
    const bytes &big_integer_magnitude_be() const;
    std::string big_integer_to_dec() const;  // base-10, leading '-' if negative

    // Structural equality (matches Gtv.equals — value identity, dicts compared sorted).
    bool equals(const Gtv &o) const;

private:
    explicit Gtv(GtvType t) : type_(t) {}

    GtvType type_;
    bytes ba_;                                                 // BYTEARRAY
    std::string str_;                                          // STRING
    int64_t int_ = 0;                                          // INTEGER
    std::vector<GtvPtr> arr_;                                  // ARRAY
    std::vector<std::pair<std::string, GtvPtr>> dict_;         // DICT (sorted)
    bool big_neg_ = false;                                     // BIGINTEGER sign
    bytes big_mag_;                                            // BIGINTEGER magnitude (BE, no sign byte)

    friend bytes encode_to_bytes(const Gtv &);
    friend GtvPtr decode_from_bytes(const bytes &);
};

// ---- Kotlin String.compareTo: UTF-16-code-unit lexicographic comparison ----
// Returns <0, 0, >0. Inputs are UTF-8; the comparison is performed on the decoded
// UTF-16 code-unit sequences (so non-BMP chars order by their surrogate pair, which
// is what the JVM dict-key sort does — this differs from raw UTF-8 byte ordering).
int cmp_utf16(const std::string &a, const std::string &b);

// =====================================================================================
// CODEC
// =====================================================================================

// ASN.1 DER, byte-exact with GtvEncoder.encodeGtv. Throws GtvError only on genuinely
// unencodable input (none for well-formed Gtv values built via the factories).
bytes encode_to_bytes(const Gtv &v);
inline bytes encode_to_bytes(const GtvPtr &v) { return encode_to_bytes(*v); }

// Inverse of encode_to_bytes, mirroring GtvFactory.decodeGtv. Throws GtvError on any
// malformed/trailing/oversized input (strict DER).
GtvPtr decode_from_bytes(const bytes &data);

// =====================================================================================
// GTV <-> JSON BRIDGE (drives gtv.to_json / gtv.from_json), mirroring
// net.postchain.gtv.GtvAdapter over Gson. See rell_gtv.cpp for the exact rules.
// =====================================================================================

// Gtv -> JSON text (compact, Gson default formatting).
//   support_big_integer=false : strict GSON (Rt_GtvValue.str / to_text). A GtvBigInteger
//                               throws GtvError("big_integer cannot be serialized as JSON").
//   support_big_integer=true  : LENIENT_GSON (gtv.to_json). GtvBigInteger -> JSON number.
// GtvByteArray serializes to an UPPERCASE hex JSON string, no prefix (UtilsKt.toHex).
std::string to_json(const Gtv &v, bool support_big_integer = false);
inline std::string to_json(const GtvPtr &v, bool support_big_integer = false) {
    return to_json(*v, support_big_integer);
}

// JSON text -> Gtv, mirroring GtvAdapter.deserialize:
//   bool -> GtvInteger(0/1); number -> GtvInteger (must be an exact long, else GtvError);
//   string -> GtvString; array -> GtvArray; object -> GtvDictionary (keys sorted);
//   null -> GtvNull. Never produces GtvByteArray or GtvBigInteger.
GtvPtr from_json(const std::string &json);

// Runs the inline encode/decode/json test vectors. Returns true on success; prints the
// first failure to stderr and returns false otherwise. (rell_gtv_test.cpp / the inline
// main() call this.)
bool self_test();

}  // namespace gtv
}  // namespace rell

#endif  // RELL_GTV_H
