// Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
//
// rell_gtv.cpp — implementation of rell::gtv (see rell_gtv.h). Faithful to
// net.postchain.gtv.GtvEncoder / GtvFactory / GtvAdapter (postchain-gtv 3.49.12).
//
// Compile/test standalone:
//   clang++ -std=c++17 -fsyntax-only rell_gtv.cpp
//   clang++ -std=c++17 -DRELL_GTV_MAIN rell_gtv.cpp -o /tmp/rell_gtv_test && /tmp/rell_gtv_test

#include "rell_gtv.h"

#include <algorithm>
#include <cstdio>
#include <cstring>
#include <sstream>

namespace rell {
namespace gtv {

// =====================================================================================
// Outer context tags (0x80 CONTEXT | 0x20 CONSTRUCTED | number) and inner universal tags
// =====================================================================================
static constexpr uint8_t TAG_NULL = 0xA0;
static constexpr uint8_t TAG_BYTEARRAY = 0xA1;
static constexpr uint8_t TAG_STRING = 0xA2;
static constexpr uint8_t TAG_INTEGER = 0xA3;
static constexpr uint8_t TAG_DICT = 0xA4;
static constexpr uint8_t TAG_ARRAY = 0xA5;
static constexpr uint8_t TAG_BIGINTEGER = 0xA6;

static constexpr uint8_t U_NULL = 0x05;
static constexpr uint8_t U_OCTETSTRING = 0x04;
static constexpr uint8_t U_UTF8STRING = 0x0C;
static constexpr uint8_t U_INTEGER = 0x02;
static constexpr uint8_t U_SEQUENCE = 0x30;

// =====================================================================================
// UTF-16-code-unit comparison (Kotlin String.compareTo) over UTF-8 inputs.
// =====================================================================================
// We compare without materializing full UTF-16 strings: decode each input into a stream
// of UTF-16 code units lazily and compare unit-by-unit. A code unit is an unsigned 16-bit
// value; BMP chars map to one unit, supplementary chars to a high+low surrogate pair.
namespace {

// Decode one UTF-8 scalar starting at i; advance i. Returns the Unicode code point.
// Assumes well-formed UTF-8 (Rell/JVM strings are always valid UTF-16/UTF-8).
uint32_t next_codepoint(const std::string &s, size_t &i) {
    uint8_t c = static_cast<uint8_t>(s[i]);
    if (c < 0x80) { i += 1; return c; }
    if ((c >> 5) == 0x6) {
        uint32_t cp = ((c & 0x1F) << 6) | (static_cast<uint8_t>(s[i + 1]) & 0x3F);
        i += 2; return cp;
    }
    if ((c >> 4) == 0xE) {
        uint32_t cp = ((c & 0x0F) << 12) | ((static_cast<uint8_t>(s[i + 1]) & 0x3F) << 6) |
                      (static_cast<uint8_t>(s[i + 2]) & 0x3F);
        i += 3; return cp;
    }
    uint32_t cp = ((c & 0x07) << 18) | ((static_cast<uint8_t>(s[i + 1]) & 0x3F) << 12) |
                  ((static_cast<uint8_t>(s[i + 2]) & 0x3F) << 6) |
                  (static_cast<uint8_t>(s[i + 3]) & 0x3F);
    i += 4; return cp;
}

// Pull the next UTF-16 code unit; uses `pending` to hold a low surrogate between calls.
// Returns false when the string is exhausted (and no pending unit remains).
bool next_unit(const std::string &s, size_t &i, uint16_t &pending, bool &has_pending,
               uint16_t &out) {
    if (has_pending) {
        out = pending;
        has_pending = false;
        return true;
    }
    if (i >= s.size()) return false;
    uint32_t cp = next_codepoint(s, i);
    if (cp <= 0xFFFF) {
        out = static_cast<uint16_t>(cp);
        return true;
    }
    cp -= 0x10000;
    out = static_cast<uint16_t>(0xD800 + (cp >> 10));
    pending = static_cast<uint16_t>(0xDC00 + (cp & 0x3FF));
    has_pending = true;
    return true;
}

}  // namespace

int cmp_utf16(const std::string &a, const std::string &b) {
    size_t ia = 0, ib = 0;
    uint16_t pa = 0, pb = 0;
    bool hpa = false, hpb = false;
    for (;;) {
        uint16_t ua, ub;
        bool oka = next_unit(a, ia, pa, hpa, ua);
        bool okb = next_unit(b, ib, pb, hpb, ub);
        if (!oka && !okb) return 0;
        if (!oka) return -1;
        if (!okb) return 1;
        if (ua != ub) return ua < ub ? -1 : 1;
    }
}

// =====================================================================================
// DER INTEGER body: minimal two's-complement big-endian, == BigInteger.toByteArray().
// =====================================================================================
namespace {

// Encode a signed 64-bit value as a minimal two's-complement big-endian byte string.
bytes der_int_body_from_i64(int64_t v) {
    // Emit 8 big-endian bytes of the two's-complement representation, then strip
    // redundant leading bytes per DER minimality.
    uint64_t u = static_cast<uint64_t>(v);
    bytes raw(8);
    for (int k = 0; k < 8; ++k) raw[k] = static_cast<uint8_t>((u >> (8 * (7 - k))) & 0xFF);

    size_t start = 0;
    if (v >= 0) {
        // strip leading 0x00 while the next byte's high bit stays clear
        while (start + 1 < raw.size() && raw[start] == 0x00 && (raw[start + 1] & 0x80) == 0) start++;
    } else {
        // strip leading 0xFF while the next byte's high bit stays set
        while (start + 1 < raw.size() && raw[start] == 0xFF && (raw[start + 1] & 0x80) != 0) start++;
    }
    return bytes(raw.begin() + start, raw.end());
}

// Encode (sign, unsigned big-endian magnitude) as a minimal two's-complement DER INTEGER
// body, matching java.math.BigInteger(sign, mag).toByteArray().
bytes der_int_body_from_sign_mag(bool negative, const bytes &mag_in) {
    // Normalize magnitude: strip leading zero bytes.
    size_t s = 0;
    while (s < mag_in.size() && mag_in[s] == 0x00) s++;
    bytes mag(mag_in.begin() + s, mag_in.end());

    if (mag.empty()) return bytes{0x00};  // value 0 -> single 0x00 byte

    if (!negative) {
        // Positive: prepend 0x00 if the top bit is set, else as-is.
        if (mag[0] & 0x80) {
            bytes out;
            out.reserve(mag.size() + 1);
            out.push_back(0x00);
            out.insert(out.end(), mag.begin(), mag.end());
            return out;
        }
        return mag;
    }

    // Negative: two's complement of the magnitude over the minimal byte width.
    // Compute (2^(8n) - mag); if the top bit ends up clear (i.e. result looks positive),
    // we'd need an extra 0xFF byte. Match BigInteger.toByteArray() exactly:
    //   - take n = mag length, build two's complement into n bytes
    //   - if the high bit of byte[0] is NOT set, prepend 0xFF.
    size_t n = mag.size();
    bytes tc(n);
    // tc = ~mag + 1
    int carry = 1;
    for (size_t k = 0; k < n; ++k) {
        size_t idx = n - 1 - k;
        int val = static_cast<uint8_t>(~mag[idx]) + carry;
        tc[idx] = static_cast<uint8_t>(val & 0xFF);
        carry = val >> 8;
    }
    if ((tc[0] & 0x80) == 0) {
        bytes out;
        out.reserve(n + 1);
        out.push_back(0xFF);
        out.insert(out.end(), tc.begin(), tc.end());
        return out;
    }
    return tc;
}

// Decode a DER INTEGER body into a signed int64; throws on overflow.
int64_t der_int_body_to_i64(const bytes &body) {
    if (body.empty()) throw GtvError("GTV decode: empty INTEGER body");
    if (body.size() > 8) {
        // Allow a 9-byte form only if it is a valid sign-extension; otherwise overflow.
        // For a strict i64, more than 8 significant bytes is out of range.
        // (A canonical DER INTEGER of an i64 is at most 8 bytes; a leading 0x00/0xFF
        //  for sign would already be stripped by minimality.)
        throw GtvError("GTV decode: INTEGER out of int64 range");
    }
    bool neg = (body[0] & 0x80) != 0;
    uint64_t u = neg ? ~0ULL : 0ULL;  // sign-extend
    for (uint8_t b : body) u = (u << 8) | b;
    return static_cast<int64_t>(u);
}

// Decode a DER INTEGER body into (sign, unsigned big-endian magnitude).
void der_int_body_to_sign_mag(const bytes &body, bool &negative, bytes &mag) {
    if (body.empty()) throw GtvError("GTV decode: empty INTEGER body");
    negative = (body[0] & 0x80) != 0;
    if (!negative) {
        size_t s = 0;
        while (s + 1 < body.size() && body[s] == 0x00) s++;
        mag.assign(body.begin() + s, body.end());
        // strip a possible single leading zero kept for sign
        size_t t = 0;
        while (t < mag.size() && mag[t] == 0x00) t++;
        mag.erase(mag.begin(), mag.begin() + t);
    } else {
        // value = body (two's complement); magnitude = -value = 2^(8n) - body
        size_t n = body.size();
        bytes tc(n);
        int carry = 1;
        for (size_t k = 0; k < n; ++k) {
            size_t idx = n - 1 - k;
            int val = static_cast<uint8_t>(~body[idx]) + carry;
            tc[idx] = static_cast<uint8_t>(val & 0xFF);
            carry = val >> 8;
        }
        size_t s = 0;
        while (s < tc.size() && tc[s] == 0x00) s++;
        mag.assign(tc.begin() + s, tc.end());
    }
}

// Base-10 string -> (sign, unsigned big-endian magnitude). Decimal only, optional '-'.
void dec_to_sign_mag(const std::string &dec, bool &negative, bytes &mag) {
    size_t i = 0;
    negative = false;
    if (i < dec.size() && (dec[i] == '+' || dec[i] == '-')) {
        negative = (dec[i] == '-');
        i++;
    }
    if (i >= dec.size()) throw GtvError("GTV: invalid decimal big_integer");
    // Repeated multiply-by-10/add over a base-256 big-endian accumulator.
    bytes acc;  // big-endian, no leading zeros
    for (; i < dec.size(); ++i) {
        char ch = dec[i];
        if (ch < '0' || ch > '9') throw GtvError("GTV: invalid decimal big_integer");
        int digit = ch - '0';
        // acc = acc * 10 + digit
        int carry = digit;
        for (size_t k = acc.size(); k-- > 0;) {
            int val = acc[k] * 10 + carry;
            acc[k] = static_cast<uint8_t>(val & 0xFF);
            carry = val >> 8;
        }
        while (carry) {
            acc.insert(acc.begin(), static_cast<uint8_t>(carry & 0xFF));
            carry >>= 8;
        }
    }
    size_t s = 0;
    while (s < acc.size() && acc[s] == 0x00) s++;
    mag.assign(acc.begin() + s, acc.end());
    if (mag.empty()) negative = false;  // zero is non-negative
}

// (sign, unsigned big-endian magnitude) -> base-10 string.
std::string sign_mag_to_dec(bool negative, const bytes &mag_in) {
    size_t s = 0;
    while (s < mag_in.size() && mag_in[s] == 0x00) s++;
    bytes mag(mag_in.begin() + s, mag_in.end());
    if (mag.empty()) return "0";
    // Repeated divide-by-10 over a base-256 big-endian magnitude.
    std::string digits;
    bytes cur = mag;
    while (!cur.empty()) {
        int rem = 0;
        bytes next;
        next.reserve(cur.size());
        for (uint8_t b : cur) {
            int acc = (rem << 8) | b;
            int q = acc / 10;
            rem = acc % 10;
            if (!next.empty() || q != 0) next.push_back(static_cast<uint8_t>(q));
        }
        digits.push_back(static_cast<char>('0' + rem));
        cur.swap(next);
    }
    std::reverse(digits.begin(), digits.end());
    if (negative) digits.insert(digits.begin(), '-');
    return digits;
}

}  // namespace

// =====================================================================================
// Factories
// =====================================================================================
GtvPtr Gtv::make_null() {
    return GtvPtr(new Gtv(GtvType::NULLV));
}

GtvPtr Gtv::make_byte_array(bytes value) {
    auto *g = new Gtv(GtvType::BYTEARRAY);
    g->ba_ = std::move(value);
    return GtvPtr(g);
}

GtvPtr Gtv::make_string(std::string value) {
    auto *g = new Gtv(GtvType::STRING);
    g->str_ = std::move(value);
    return GtvPtr(g);
}

GtvPtr Gtv::make_integer(int64_t value) {
    auto *g = new Gtv(GtvType::INTEGER);
    g->int_ = value;
    return GtvPtr(g);
}

GtvPtr Gtv::make_array(std::vector<GtvPtr> elements) {
    auto *g = new Gtv(GtvType::ARRAY);
    g->arr_ = std::move(elements);
    return GtvPtr(g);
}

GtvPtr Gtv::make_dict(std::vector<std::pair<std::string, GtvPtr>> entries) {
    // Sort by UTF-16-code-unit order (Kotlin String.compareTo). Stable to keep
    // deterministic behavior on duplicate keys (the JVM map would have deduplicated;
    // callers should pass unique keys — we keep the last on duplicate to match map sem).
    std::stable_sort(entries.begin(), entries.end(),
                     [](const std::pair<std::string, GtvPtr> &a,
                        const std::pair<std::string, GtvPtr> &b) {
                         return cmp_utf16(a.first, b.first) < 0;
                     });
    auto *g = new Gtv(GtvType::DICT);
    g->dict_ = std::move(entries);
    return GtvPtr(g);
}

GtvPtr Gtv::make_dict(std::map<std::string, GtvPtr> entries) {
    std::vector<std::pair<std::string, GtvPtr>> v(entries.begin(), entries.end());
    return make_dict(std::move(v));
}

GtvPtr Gtv::make_big_integer(bool negative, bytes magnitude_be) {
    size_t s = 0;
    while (s < magnitude_be.size() && magnitude_be[s] == 0x00) s++;
    bytes mag(magnitude_be.begin() + s, magnitude_be.end());
    auto *g = new Gtv(GtvType::BIGINTEGER);
    g->big_neg_ = mag.empty() ? false : negative;
    g->big_mag_ = std::move(mag);
    return GtvPtr(g);
}

GtvPtr Gtv::make_big_integer_dec(const std::string &decimal) {
    bool neg;
    bytes mag;
    dec_to_sign_mag(decimal, neg, mag);
    return make_big_integer(neg, std::move(mag));
}

// =====================================================================================
// Accessors
// =====================================================================================
const bytes &Gtv::as_byte_array() const {
    if (type_ != GtvType::BYTEARRAY) throw GtvError("Gtv: not a byte_array");
    return ba_;
}
const std::string &Gtv::as_string() const {
    if (type_ != GtvType::STRING) throw GtvError("Gtv: not a string");
    return str_;
}
int64_t Gtv::as_integer() const {
    if (type_ != GtvType::INTEGER) throw GtvError("Gtv: not an integer");
    return int_;
}
const std::vector<GtvPtr> &Gtv::as_array() const {
    if (type_ != GtvType::ARRAY) throw GtvError("Gtv: not an array");
    return arr_;
}
const std::vector<std::pair<std::string, GtvPtr>> &Gtv::as_dict() const {
    if (type_ != GtvType::DICT) throw GtvError("Gtv: not a dict");
    return dict_;
}
bool Gtv::big_integer_negative() const {
    if (type_ != GtvType::BIGINTEGER) throw GtvError("Gtv: not a big_integer");
    return big_neg_;
}
const bytes &Gtv::big_integer_magnitude_be() const {
    if (type_ != GtvType::BIGINTEGER) throw GtvError("Gtv: not a big_integer");
    return big_mag_;
}
std::string Gtv::big_integer_to_dec() const {
    if (type_ != GtvType::BIGINTEGER) throw GtvError("Gtv: not a big_integer");
    return sign_mag_to_dec(big_neg_, big_mag_);
}

bool Gtv::equals(const Gtv &o) const {
    if (type_ != o.type_) return false;
    switch (type_) {
        case GtvType::NULLV: return true;
        case GtvType::BYTEARRAY: return ba_ == o.ba_;
        case GtvType::STRING: return str_ == o.str_;
        case GtvType::INTEGER: return int_ == o.int_;
        case GtvType::BIGINTEGER: return big_neg_ == o.big_neg_ && big_mag_ == o.big_mag_;
        case GtvType::ARRAY: {
            if (arr_.size() != o.arr_.size()) return false;
            for (size_t i = 0; i < arr_.size(); ++i) {
                if (!arr_[i]->equals(*o.arr_[i])) return false;
            }
            return true;
        }
        case GtvType::DICT: {
            if (dict_.size() != o.dict_.size()) return false;
            for (size_t i = 0; i < dict_.size(); ++i) {
                if (dict_[i].first != o.dict_[i].first) return false;
                if (!dict_[i].second->equals(*o.dict_[i].second)) return false;
            }
            return true;
        }
    }
    return false;
}

// =====================================================================================
// ENCODE
// =====================================================================================
namespace {

void der_put_length(bytes &out, size_t len) {
    if (len <= 0x7F) {
        out.push_back(static_cast<uint8_t>(len));
        return;
    }
    // long form: 0x80 | numBytes, then big-endian length bytes (minimal).
    bytes lb;
    size_t v = len;
    while (v > 0) {
        lb.insert(lb.begin(), static_cast<uint8_t>(v & 0xFF));
        v >>= 8;
    }
    out.push_back(static_cast<uint8_t>(0x80 | lb.size()));
    out.insert(out.end(), lb.begin(), lb.end());
}

// Append a complete TLV: <tag> <der-length> <content>.
void der_put_tlv(bytes &out, uint8_t tag, const bytes &content) {
    out.push_back(tag);
    der_put_length(out, content.size());
    out.insert(out.end(), content.begin(), content.end());
}

// Build the INNER universal TLV (without the outer context tag) for a Gtv.
bytes encode_inner(const Gtv &v);

// Build the full Gtv encoding: outer context tag wrapping the inner universal TLV.
bytes encode_gtv(const Gtv &v) {
    bytes inner = encode_inner(v);
    uint8_t outer;
    switch (v.type()) {
        case GtvType::NULLV: outer = TAG_NULL; break;
        case GtvType::BYTEARRAY: outer = TAG_BYTEARRAY; break;
        case GtvType::STRING: outer = TAG_STRING; break;
        case GtvType::INTEGER: outer = TAG_INTEGER; break;
        case GtvType::DICT: outer = TAG_DICT; break;
        case GtvType::ARRAY: outer = TAG_ARRAY; break;
        case GtvType::BIGINTEGER: outer = TAG_BIGINTEGER; break;
        default: throw GtvError("GTV encode: unknown type");
    }
    bytes out;
    der_put_tlv(out, outer, inner);
    return out;
}

bytes encode_inner(const Gtv &v) {
    switch (v.type()) {
        case GtvType::NULLV: {
            bytes out;
            der_put_tlv(out, U_NULL, bytes{});
            return out;
        }
        case GtvType::BYTEARRAY: {
            bytes out;
            der_put_tlv(out, U_OCTETSTRING, v.as_byte_array());
            return out;
        }
        case GtvType::STRING: {
            const std::string &s = v.as_string();
            bytes content(s.begin(), s.end());  // UTF-8 bytes verbatim
            bytes out;
            der_put_tlv(out, U_UTF8STRING, content);
            return out;
        }
        case GtvType::INTEGER: {
            bytes body = der_int_body_from_i64(v.as_integer());
            bytes out;
            der_put_tlv(out, U_INTEGER, body);
            return out;
        }
        case GtvType::BIGINTEGER: {
            bytes body = der_int_body_from_sign_mag(v.big_integer_negative(),
                                                    v.big_integer_magnitude_be());
            bytes out;
            der_put_tlv(out, U_INTEGER, body);
            return out;
        }
        case GtvType::ARRAY: {
            bytes seq;
            for (const auto &el : v.as_array()) {
                bytes e = encode_gtv(*el);  // each element is a full Gtv (outer tag included)
                seq.insert(seq.end(), e.begin(), e.end());
            }
            bytes out;
            der_put_tlv(out, U_SEQUENCE, seq);
            return out;
        }
        case GtvType::DICT: {
            bytes seq;
            for (const auto &kv : v.as_dict()) {
                // DictPair ::= SEQUENCE { name UTF8String, value Gtv }
                bytes pair;
                bytes name(kv.first.begin(), kv.first.end());
                der_put_tlv(pair, U_UTF8STRING, name);
                bytes val = encode_gtv(*kv.second);
                pair.insert(pair.end(), val.begin(), val.end());
                der_put_tlv(seq, U_SEQUENCE, pair);
            }
            bytes out;
            der_put_tlv(out, U_SEQUENCE, seq);
            return out;
        }
        default: throw GtvError("GTV encode: unknown type");
    }
}

}  // namespace

bytes encode_to_bytes(const Gtv &v) {
    return encode_gtv(v);
}

// =====================================================================================
// DECODE (strict DER)
// =====================================================================================
namespace {

struct Reader {
    const bytes &buf;
    size_t pos = 0;
    explicit Reader(const bytes &b) : buf(b) {}

    uint8_t u8() {
        if (pos >= buf.size()) throw GtvError("GTV decode: unexpected end of input");
        return buf[pos++];
    }
    void need(size_t n) {
        if (pos + n > buf.size()) throw GtvError("GTV decode: truncated content");
    }
    bool eof() const { return pos >= buf.size(); }
};

size_t der_read_length(Reader &r) {
    uint8_t first = r.u8();
    if ((first & 0x80) == 0) return first;  // short form
    size_t num = first & 0x7F;
    if (num == 0) throw GtvError("GTV decode: indefinite length not allowed (DER)");
    if (num > sizeof(size_t)) throw GtvError("GTV decode: length too large");
    size_t len = 0;
    for (size_t i = 0; i < num; ++i) len = (len << 8) | r.u8();
    return len;
}

GtvPtr decode_gtv(Reader &r);

// Read one TLV header, returning tag and a sub-reader scoped to its content.
struct Tlv {
    uint8_t tag;
    size_t start;  // content start in buf
    size_t len;
};

Tlv read_tlv(Reader &r) {
    Tlv t;
    t.tag = r.u8();
    t.len = der_read_length(r);
    r.need(t.len);
    t.start = r.pos;
    r.pos += t.len;
    return t;
}

GtvPtr decode_gtv(Reader &r) {
    Tlv outer = read_tlv(r);
    // The content is a single inner universal TLV.
    Reader inner(r.buf);
    inner.pos = outer.start;
    size_t inner_end = outer.start + outer.len;

    Tlv it = read_tlv(inner);
    if (inner.pos != inner_end) throw GtvError("GTV decode: trailing bytes inside outer tag");

    auto inner_bytes = [&]() {
        return bytes(r.buf.begin() + it.start, r.buf.begin() + it.start + it.len);
    };

    switch (outer.tag) {
        case TAG_NULL:
            if (it.tag != U_NULL || it.len != 0) throw GtvError("GTV decode: bad NULL");
            return Gtv::make_null();
        case TAG_BYTEARRAY:
            if (it.tag != U_OCTETSTRING) throw GtvError("GTV decode: bad byte_array");
            return Gtv::make_byte_array(inner_bytes());
        case TAG_STRING: {
            if (it.tag != U_UTF8STRING) throw GtvError("GTV decode: bad string");
            bytes b = inner_bytes();
            return Gtv::make_string(std::string(b.begin(), b.end()));
        }
        case TAG_INTEGER: {
            if (it.tag != U_INTEGER) throw GtvError("GTV decode: bad integer");
            return Gtv::make_integer(der_int_body_to_i64(inner_bytes()));
        }
        case TAG_BIGINTEGER: {
            if (it.tag != U_INTEGER) throw GtvError("GTV decode: bad big_integer");
            bool neg;
            bytes mag;
            der_int_body_to_sign_mag(inner_bytes(), neg, mag);
            return Gtv::make_big_integer(neg, std::move(mag));
        }
        case TAG_ARRAY: {
            if (it.tag != U_SEQUENCE) throw GtvError("GTV decode: bad array");
            Reader elems(r.buf);
            elems.pos = it.start;
            size_t end = it.start + it.len;
            std::vector<GtvPtr> out;
            while (elems.pos < end) out.push_back(decode_gtv(elems));
            if (elems.pos != end) throw GtvError("GTV decode: array overrun");
            return Gtv::make_array(std::move(out));
        }
        case TAG_DICT: {
            if (it.tag != U_SEQUENCE) throw GtvError("GTV decode: bad dict");
            Reader pairs(r.buf);
            pairs.pos = it.start;
            size_t end = it.start + it.len;
            std::vector<std::pair<std::string, GtvPtr>> out;
            while (pairs.pos < end) {
                Tlv pair = read_tlv(pairs);
                if (pair.tag != U_SEQUENCE) throw GtvError("GTV decode: bad DictPair");
                Reader pr(r.buf);
                pr.pos = pair.start;
                size_t pend = pair.start + pair.len;
                Tlv name = read_tlv(pr);
                if (name.tag != U_UTF8STRING) throw GtvError("GTV decode: bad DictPair key");
                std::string key(r.buf.begin() + name.start,
                                r.buf.begin() + name.start + name.len);
                GtvPtr val = decode_gtv(pr);
                if (pr.pos != pend) throw GtvError("GTV decode: DictPair overrun");
                out.emplace_back(std::move(key), std::move(val));
            }
            if (pairs.pos != end) throw GtvError("GTV decode: dict overrun");
            // make_dict re-sorts (decoded input is already sorted by a conformant encoder,
            // but we re-sort to be robust and to canonicalize).
            return Gtv::make_dict(std::move(out));
        }
        default:
            throw GtvError("GTV decode: unknown outer tag");
    }
}

}  // namespace

GtvPtr decode_from_bytes(const bytes &data) {
    Reader r(data);
    GtvPtr g = decode_gtv(r);
    if (!r.eof()) throw GtvError("GTV decode: trailing bytes after top-level Gtv");
    return g;
}

// =====================================================================================
// GTV -> JSON (Gson / GtvAdapter.serialize)
// =====================================================================================
namespace {

static const char *HEX_UPPER = "0123456789ABCDEF";  // UtilsKt.HEX_CHAR_ARRAY (uppercase!)

void json_escape_string(std::string &out, const std::string &s) {
    // Gson default string escaping: \", \\, \b, \f, \n, \r, \t, and \u00XX for other
    // control chars (< 0x20). Gson also HTML-escapes < > & = ' by default
    // (escapeHtmlChars=true unless disabled). The postchain Gson builders use the
    // default builder (no disableHtmlEscaping), so HTML escaping IS active for the
    // strict/lenient instances. We mirror that.
    out.push_back('"');
    for (unsigned char c : s) {
        switch (c) {
            case '"': out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\b': out += "\\b"; break;
            case '\f': out += "\\f"; break;
            case '\n': out += "\\n"; break;
            case '\r': out += "\\r"; break;
            case '\t': out += "\\t"; break;
            case '<': out += "\\u003c"; break;
            case '>': out += "\\u003e"; break;
            case '&': out += "\\u0026"; break;
            case '=': out += "\\u003d"; break;
            case '\'': out += "\\u0027"; break;
            default:
                if (c < 0x20) {
                    char buf[7];
                    std::snprintf(buf, sizeof(buf), "\\u%04x", c);
                    out += buf;
                } else {
                    out.push_back(static_cast<char>(c));
                }
        }
    }
    out.push_back('"');
}

void gtv_to_json_rec(std::string &out, const Gtv &v, bool support_big_integer) {
    switch (v.type()) {
        case GtvType::NULLV:
            out += "null";
            break;
        case GtvType::INTEGER:
            out += std::to_string(v.as_integer());
            break;
        case GtvType::BIGINTEGER:
            if (!support_big_integer) {
                throw GtvError("big_integer cannot be serialized as JSON");
            }
            // JSON number via BigInteger.toString() (base-10, leading '-' for negatives).
            out += v.big_integer_to_dec();
            break;
        case GtvType::STRING:
            json_escape_string(out, v.as_string());
            break;
        case GtvType::BYTEARRAY: {
            // UtilsKt.toHex: UPPERCASE hex, no prefix; emitted as a JSON string.
            const bytes &b = v.as_byte_array();
            std::string hex;
            hex.reserve(b.size() * 2);
            for (uint8_t byte : b) {
                hex.push_back(HEX_UPPER[byte >> 4]);
                hex.push_back(HEX_UPPER[byte & 0x0F]);
            }
            json_escape_string(out, hex);
            break;
        }
        case GtvType::ARRAY: {
            out.push_back('[');
            const auto &a = v.as_array();
            for (size_t i = 0; i < a.size(); ++i) {
                if (i) out.push_back(',');
                gtv_to_json_rec(out, *a[i], support_big_integer);
            }
            out.push_back(']');
            break;
        }
        case GtvType::DICT: {
            out.push_back('{');
            const auto &d = v.as_dict();  // already key-sorted
            for (size_t i = 0; i < d.size(); ++i) {
                if (i) out.push_back(',');
                json_escape_string(out, d[i].first);
                out.push_back(':');
                gtv_to_json_rec(out, *d[i].second, support_big_integer);
            }
            out.push_back('}');
            break;
        }
        default:
            throw GtvError("GTV to_json: unknown type");
    }
}

}  // namespace

std::string to_json(const Gtv &v, bool support_big_integer) {
    std::string out;
    gtv_to_json_rec(out, v, support_big_integer);
    return out;
}

// =====================================================================================
// JSON -> GTV (Gson / GtvAdapter.deserialize)
// =====================================================================================
//
// A minimal recursive-descent JSON parser sufficient for GtvAdapter.deserialize:
//   bool   -> GtvInteger(0/1)
//   number -> GtvInteger(longValueExact)  [non-integer / out-of-long-range -> error]
//   string -> GtvString
//   array  -> GtvArray
//   object -> GtvDictionary (keys sorted)
//   null   -> GtvNull
// Note: deliberately not a general-purpose JSON library; it matches Gson's lenient-ish
// acceptance only as far as GtvAdapter needs. Whitespace is skipped per JSON spec.
namespace {

struct JsonParser {
    const std::string &s;
    size_t i = 0;
    explicit JsonParser(const std::string &str) : s(str) {}

    void skip_ws() {
        while (i < s.size()) {
            char c = s[i];
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') i++;
            else break;
        }
    }
    [[noreturn]] void fail(const std::string &m) { throw GtvError("fn_json_badstr: " + m); }
    char peek() {
        if (i >= s.size()) fail("unexpected end");
        return s[i];
    }

    GtvPtr parse_value() {
        skip_ws();
        char c = peek();
        switch (c) {
            case '{': return parse_object();
            case '[': return parse_array();
            case '"': return Gtv::make_string(parse_string());
            case 't': case 'f': return parse_bool();
            case 'n': return parse_null();
            default: return parse_number();
        }
    }

    GtvPtr parse_object() {
        i++;  // '{'
        std::vector<std::pair<std::string, GtvPtr>> entries;
        skip_ws();
        if (peek() == '}') { i++; return Gtv::make_dict(std::move(entries)); }
        for (;;) {
            skip_ws();
            if (peek() != '"') fail("expected object key");
            std::string key = parse_string();
            skip_ws();
            if (peek() != ':') fail("expected ':'");
            i++;
            GtvPtr val = parse_value();
            entries.emplace_back(std::move(key), std::move(val));
            skip_ws();
            char c = peek();
            if (c == ',') { i++; continue; }
            if (c == '}') { i++; break; }
            fail("expected ',' or '}'");
        }
        return Gtv::make_dict(std::move(entries));  // sorts keys
    }

    GtvPtr parse_array() {
        i++;  // '['
        std::vector<GtvPtr> elems;
        skip_ws();
        if (peek() == ']') { i++; return Gtv::make_array(std::move(elems)); }
        for (;;) {
            elems.push_back(parse_value());
            skip_ws();
            char c = peek();
            if (c == ',') { i++; continue; }
            if (c == ']') { i++; break; }
            fail("expected ',' or ']'");
        }
        return Gtv::make_array(std::move(elems));
    }

    std::string parse_string() {
        if (peek() != '"') fail("expected string");
        i++;
        std::string out;
        for (;;) {
            if (i >= s.size()) fail("unterminated string");
            char c = s[i++];
            if (c == '"') break;
            if (c == '\\') {
                if (i >= s.size()) fail("bad escape");
                char e = s[i++];
                switch (e) {
                    case '"': out.push_back('"'); break;
                    case '\\': out.push_back('\\'); break;
                    case '/': out.push_back('/'); break;
                    case 'b': out.push_back('\b'); break;
                    case 'f': out.push_back('\f'); break;
                    case 'n': out.push_back('\n'); break;
                    case 'r': out.push_back('\r'); break;
                    case 't': out.push_back('\t'); break;
                    case 'u': {
                        if (i + 4 > s.size()) fail("bad \\u escape");
                        uint32_t cp = 0;
                        for (int k = 0; k < 4; ++k) {
                            char h = s[i++];
                            cp <<= 4;
                            if (h >= '0' && h <= '9') cp |= (h - '0');
                            else if (h >= 'a' && h <= 'f') cp |= (h - 'a' + 10);
                            else if (h >= 'A' && h <= 'F') cp |= (h - 'A' + 10);
                            else fail("bad hex in \\u");
                        }
                        // Handle a surrogate pair if present.
                        if (cp >= 0xD800 && cp <= 0xDBFF && i + 6 <= s.size() &&
                            s[i] == '\\' && s[i + 1] == 'u') {
                            i += 2;
                            uint32_t lo = 0;
                            for (int k = 0; k < 4; ++k) {
                                char h = s[i++];
                                lo <<= 4;
                                if (h >= '0' && h <= '9') lo |= (h - '0');
                                else if (h >= 'a' && h <= 'f') lo |= (h - 'a' + 10);
                                else if (h >= 'A' && h <= 'F') lo |= (h - 'A' + 10);
                                else fail("bad hex in \\u");
                            }
                            cp = 0x10000 + ((cp - 0xD800) << 10) + (lo - 0xDC00);
                        }
                        // Encode code point as UTF-8.
                        if (cp < 0x80) {
                            out.push_back(static_cast<char>(cp));
                        } else if (cp < 0x800) {
                            out.push_back(static_cast<char>(0xC0 | (cp >> 6)));
                            out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
                        } else if (cp < 0x10000) {
                            out.push_back(static_cast<char>(0xE0 | (cp >> 12)));
                            out.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
                            out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
                        } else {
                            out.push_back(static_cast<char>(0xF0 | (cp >> 18)));
                            out.push_back(static_cast<char>(0x80 | ((cp >> 12) & 0x3F)));
                            out.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
                            out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
                        }
                        break;
                    }
                    default: fail("bad escape");
                }
            } else {
                out.push_back(c);
            }
        }
        return out;
    }

    GtvPtr parse_bool() {
        if (s.compare(i, 4, "true") == 0) { i += 4; return Gtv::make_integer(1); }
        if (s.compare(i, 5, "false") == 0) { i += 5; return Gtv::make_integer(0); }
        fail("bad literal");
    }

    GtvPtr parse_null() {
        if (s.compare(i, 4, "null") == 0) { i += 4; return Gtv::make_null(); }
        fail("bad literal");
    }

    GtvPtr parse_number() {
        size_t start = i;
        if (peek() == '-') i++;
        bool is_int = true;
        while (i < s.size()) {
            char c = s[i];
            if (c >= '0' && c <= '9') { i++; }
            else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') { is_int = false; i++; }
            else break;
        }
        std::string tok = s.substr(start, i - start);
        if (tok.empty() || tok == "-") fail("bad number");
        // GtvAdapter: number -> BigDecimal.longValueExact(). Non-integer or out-of-range
        // -> error. We accept only an exact int64 here (matches GtvInteger semantics).
        if (!is_int) {
            // Could still be an integer value expressed with exponent/decimal point;
            // GtvAdapter would call longValueExact() and throw on any fractional part.
            // We reject non-plain-integer forms to stay strict & deterministic.
            fail("non-integer JSON number cannot become GtvInteger");
        }
        try {
            size_t consumed = 0;
            long long val = std::stoll(tok, &consumed);
            if (consumed != tok.size()) fail("bad number");
            return Gtv::make_integer(static_cast<int64_t>(val));
        } catch (const std::out_of_range &) {
            fail("JSON number out of int64 range");
        } catch (const std::invalid_argument &) {
            fail("bad number");
        }
    }
};

}  // namespace

GtvPtr from_json(const std::string &json) {
    // Empty/blank -> error (mirrors Rt_JsonValue.parse on blank input, and Gson would
    // return null which jsonToGtv maps to GtvNull; but the Rell `from_json` path requires
    // non-blank). Match the stricter Rell stdlib behavior: blank -> badstr.
    size_t k = 0;
    while (k < json.size() && (json[k] == ' ' || json[k] == '\t' || json[k] == '\n' || json[k] == '\r')) k++;
    if (k == json.size()) throw GtvError("fn_json_badstr: blank input");

    JsonParser p(json);
    GtvPtr g = p.parse_value();
    p.skip_ws();
    if (p.i != json.size()) throw GtvError("fn_json_badstr: trailing content");
    return g;
}

// =====================================================================================
// SELF-TEST — asserted encode vectors (Gtv -> known DER hex), round-trips, json bridges.
// Vectors were cross-checked against GtvEncoder.encodeGtv (postchain-gtv 3.49.12).
// =====================================================================================
namespace {

std::string to_hex_upper(const bytes &b) {
    std::string out;
    for (uint8_t x : b) {
        out.push_back(HEX_UPPER[x >> 4]);
        out.push_back(HEX_UPPER[x & 0x0F]);
    }
    return out;
}

bool g_ok = true;

void check_enc(const std::string &name, const GtvPtr &g, const std::string &expect_hex) {
    bytes enc = encode_to_bytes(*g);
    std::string got = to_hex_upper(enc);
    if (got != expect_hex) {
        std::fprintf(stderr, "[rell::gtv] ENC FAIL %s: expected %s, got %s\n",
                     name.c_str(), expect_hex.c_str(), got.c_str());
        g_ok = false;
        return;
    }
    // round-trip
    try {
        GtvPtr back = decode_from_bytes(enc);
        if (!back->equals(*g)) {
            std::fprintf(stderr, "[rell::gtv] ROUNDTRIP FAIL %s: decode != original\n",
                         name.c_str());
            g_ok = false;
        }
    } catch (const std::exception &e) {
        std::fprintf(stderr, "[rell::gtv] ROUNDTRIP THROW %s: %s\n", name.c_str(), e.what());
        g_ok = false;
    }
}

void check_json(const std::string &name, const std::string &got, const std::string &expect) {
    if (got != expect) {
        std::fprintf(stderr, "[rell::gtv] JSON FAIL %s: expected %s, got %s\n",
                     name.c_str(), expect.c_str(), got.c_str());
        g_ok = false;
    }
}

}  // namespace

bool self_test() {
    g_ok = true;

    // ---- NULL: A0 00 -> wraps NULL 05 00 -> A0 02 05 00 ----
    check_enc("null", Gtv::make_null(), "A0020500");

    // ---- INTEGER 0:  A3 03 02 01 00 ----
    check_enc("int 0", Gtv::make_integer(0), "A303020100");
    // INTEGER 1:       A3 03 02 01 01
    check_enc("int 1", Gtv::make_integer(1), "A303020101");
    // INTEGER 127:     A3 03 02 01 7F
    check_enc("int 127", Gtv::make_integer(127), "A30302017F");
    // INTEGER 128 needs a leading 0x00: 02 02 00 80 -> A3 04 02 02 00 80
    check_enc("int 128", Gtv::make_integer(128), "A30402020080");
    // INTEGER 255:     02 02 00 FF -> A3 04 02 02 00 FF
    check_enc("int 255", Gtv::make_integer(255), "A304020200FF");
    // INTEGER 256:     02 02 01 00 -> A3 04 02 02 01 00
    check_enc("int 256", Gtv::make_integer(256), "A30402020100");
    // INTEGER -1:      02 01 FF -> A3 03 02 01 FF
    check_enc("int -1", Gtv::make_integer(-1), "A3030201FF");
    // INTEGER -128:    02 01 80 -> A3 03 02 01 80
    check_enc("int -128", Gtv::make_integer(-128), "A303020180");
    // INTEGER -129:    02 02 FF 7F -> A3 04 02 02 FF 7F
    check_enc("int -129", Gtv::make_integer(-129), "A3040202FF7F");
    // INTEGER 0x7FFFFFFFFFFFFFFF (max i64): 02 08 7F FF FF FF FF FF FF FF
    check_enc("int max", Gtv::make_integer(0x7FFFFFFFFFFFFFFFLL),
              "A30A02087FFFFFFFFFFFFFFF");
    // INTEGER min i64 -9223372036854775808: 02 08 80 00 00 00 00 00 00 00
    check_enc("int min", Gtv::make_integer(static_cast<int64_t>(0x8000000000000000ULL)),
              "A30A02088000000000000000");

    // ---- STRING "" : A2 02 0C 00 ----
    check_enc("str empty", Gtv::make_string(""), "A2020C00");
    // STRING "hello": UTF8 68 65 6C 6C 6F -> 0C 05 68656C6C6F -> A2 07 ...
    check_enc("str hello", Gtv::make_string("hello"), "A2070C0568656C6C6F");

    // ---- BYTEARRAY empty: A1 02 04 00 ----
    check_enc("ba empty", Gtv::make_byte_array(bytes{}), "A1020400");
    // BYTEARRAY [0x01,0x02,0x03]: 04 03 010203 -> A1 05 04 03 010203
    check_enc("ba 010203", Gtv::make_byte_array(bytes{0x01, 0x02, 0x03}),
              "A1050403010203");

    // ---- BIGINTEGER 0: same inner as int 0, outer A6: A6 03 02 01 00 ----
    check_enc("bigint 0", Gtv::make_big_integer_dec("0"), "A603020100");
    // BIGINTEGER 128: A6 04 02 02 00 80
    check_enc("bigint 128", Gtv::make_big_integer_dec("128"), "A60402020080");
    // BIGINTEGER -1: A6 03 02 01 FF
    check_enc("bigint -1", Gtv::make_big_integer_dec("-1"), "A6030201FF");
    // BIGINTEGER 18446744073709551616 (2^64): mag = 01 00..00 (9 bytes), top bit clear
    //   so no leading 0x00 added: 02 09 01 00 00 00 00 00 00 00 00 -> A6 0B 02 09 ...
    check_enc("bigint 2^64", Gtv::make_big_integer_dec("18446744073709551616"),
              "A60B0209010000000000000000");
    // BIGINTEGER 2^63 (9223372036854775808): mag = 80 00..00 (8 bytes), top bit SET, so a
    //   positive value needs a leading 0x00: 02 09 00 80 00..00 -> A6 0B 02 09 00 80 ...
    check_enc("bigint 2^63", Gtv::make_big_integer_dec("9223372036854775808"),
              "A60B0209008000000000000000");

    // ---- ARRAY [] : inner SEQUENCE 30 00 -> A5 02 30 00 ----
    check_enc("arr empty", Gtv::make_array({}), "A5023000");
    // ARRAY [1, 2]:
    //   elem1 = A3 03 02 01 01 ; elem2 = A3 03 02 01 02
    //   seq = 30 0A <elem1><elem2> -> A5 0C 30 0A ...
    {
        std::vector<GtvPtr> e{Gtv::make_integer(1), Gtv::make_integer(2)};
        check_enc("arr [1,2]", Gtv::make_array(std::move(e)),
                  "A50C300AA303020101A303020102");
    }

    // ---- DICT {} : 30 00 -> A4 02 30 00 ----
    check_enc("dict empty", Gtv::make_dict(std::vector<std::pair<std::string, GtvPtr>>{}),
              "A4023000");
    // DICT {"a":1} :
    //   key "a" = 0C 01 61 ; val = A3 03 02 01 01
    //   pair = 30 08 <0C0161><A3030201 01> -> 30 08 0C0161A303020101
    //   seq = 30 0A 30 08 ... -> A4 0C 30 0A 30 08 0C0161A303020101
    {
        std::vector<std::pair<std::string, GtvPtr>> d;
        d.emplace_back("a", Gtv::make_integer(1));
        check_enc("dict {a:1}", Gtv::make_dict(std::move(d)),
                  "A40C300A30080C0161A303020101");
    }
    // DICT key sort: {"b":2,"a":1} must serialize with "a" first.
    {
        std::vector<std::pair<std::string, GtvPtr>> d;
        d.emplace_back("b", Gtv::make_integer(2));
        d.emplace_back("a", Gtv::make_integer(1));
        // pair_a = 30 08 0C0161 A303020101 ; pair_b = 30 08 0C0162 A303020102
        // seq = 30 14 <pair_a><pair_b> -> A4 16 30 14 ...
        check_enc("dict sort {b,a}", Gtv::make_dict(std::move(d)),
                  "A416301430080C0161A30302010130080C0162A303020102");
    }

    // ---- Long-form length: byte_array of 200 bytes ----
    // 04 81 C8 <200 bytes> ; content len = 2(tag+lenbyte? no) ...
    // inner TLV = 04 81 C8 <200x00>; its length = 3 + 200 = 203 = 0xCB -> outer A1 81 CB ...
    {
        bytes big(200, 0x00);
        GtvPtr g = Gtv::make_byte_array(big);
        bytes enc = encode_to_bytes(*g);
        // Check the header bytes explicitly.
        bool hdr_ok = enc.size() == 206 && enc[0] == 0xA1 && enc[1] == 0x81 &&
                      enc[2] == 0xCB && enc[3] == 0x04 && enc[4] == 0x81 && enc[5] == 0xC8;
        if (!hdr_ok) {
            std::fprintf(stderr, "[rell::gtv] LONGLEN FAIL: header wrong (size=%zu)\n",
                         enc.size());
            g_ok = false;
        } else {
            GtvPtr back = decode_from_bytes(enc);
            if (!back->equals(*g)) {
                std::fprintf(stderr, "[rell::gtv] LONGLEN roundtrip FAIL\n");
                g_ok = false;
            }
        }
    }

    // ---- JSON serialize ----
    check_json("json int", to_json(*Gtv::make_integer(123)), "123");
    check_json("json neg int", to_json(*Gtv::make_integer(-5)), "-5");
    check_json("json string", to_json(*Gtv::make_string("hi")), "\"hi\"");
    check_json("json null", to_json(*Gtv::make_null()), "null");
    // byte_array -> UPPERCASE hex string
    check_json("json ba", to_json(*Gtv::make_byte_array(bytes{0xAB, 0x12})), "\"AB12\"");
    {
        std::vector<GtvPtr> e{Gtv::make_integer(2), Gtv::make_integer(3)};
        check_json("json arr", to_json(*Gtv::make_array(std::move(e))), "[2,3]");
    }
    {
        std::vector<std::pair<std::string, GtvPtr>> d;
        d.emplace_back("b", Gtv::make_integer(2));
        d.emplace_back("a", Gtv::make_integer(1));
        // sorted: a then b
        check_json("json dict sorted", to_json(*Gtv::make_dict(std::move(d))),
                   "{\"a\":1,\"b\":2}");
    }
    // big_integer strict -> throws; lenient -> number
    {
        bool threw = false;
        try {
            to_json(*Gtv::make_big_integer_dec("42"), false);
        } catch (const GtvError &) {
            threw = true;
        }
        if (!threw) {
            std::fprintf(stderr, "[rell::gtv] JSON bigint-strict should throw\n");
            g_ok = false;
        }
        check_json("json bigint lenient",
                   to_json(*Gtv::make_big_integer_dec("18446744073709551616"), true),
                   "18446744073709551616");
    }
    // HTML escaping (Gson default)
    check_json("json html escape", to_json(*Gtv::make_string("a<b>&c")),
               "\"a\\u003cb\\u003e\\u0026c\"");

    // ---- JSON deserialize ----
    {
        GtvPtr g = from_json("123");
        if (g->type() != GtvType::INTEGER || g->as_integer() != 123) {
            std::fprintf(stderr, "[rell::gtv] from_json int FAIL\n");
            g_ok = false;
        }
        g = from_json("true");
        if (g->type() != GtvType::INTEGER || g->as_integer() != 1) {
            std::fprintf(stderr, "[rell::gtv] from_json bool true FAIL\n");
            g_ok = false;
        }
        g = from_json("false");
        if (g->type() != GtvType::INTEGER || g->as_integer() != 0) {
            std::fprintf(stderr, "[rell::gtv] from_json bool false FAIL\n");
            g_ok = false;
        }
        g = from_json("null");
        if (g->type() != GtvType::NULLV) {
            std::fprintf(stderr, "[rell::gtv] from_json null FAIL\n");
            g_ok = false;
        }
        g = from_json("\"hello\"");
        if (g->type() != GtvType::STRING || g->as_string() != "hello") {
            std::fprintf(stderr, "[rell::gtv] from_json string FAIL\n");
            g_ok = false;
        }
        g = from_json("{\"b\":2,\"a\":1}");
        if (g->type() != GtvType::DICT || g->as_dict().size() != 2 ||
            g->as_dict()[0].first != "a") {
            std::fprintf(stderr, "[rell::gtv] from_json dict sort FAIL\n");
            g_ok = false;
        }
        g = from_json("[1,\"x\",null]");
        if (g->type() != GtvType::ARRAY || g->as_array().size() != 3) {
            std::fprintf(stderr, "[rell::gtv] from_json array FAIL\n");
            g_ok = false;
        }
        // non-integer number must fail
        bool threw = false;
        try { from_json("1.5"); } catch (const GtvError &) { threw = true; }
        if (!threw) {
            std::fprintf(stderr, "[rell::gtv] from_json 1.5 should fail\n");
            g_ok = false;
        }
        // blank must fail
        threw = false;
        try { from_json("   "); } catch (const GtvError &) { threw = true; }
        if (!threw) {
            std::fprintf(stderr, "[rell::gtv] from_json blank should fail\n");
            g_ok = false;
        }
    }

    // ---- UTF-16 dict ordering: supplementary char vs BMP char ----
    // U+1F600 (surrogate D83D DE00) vs U+FFFF: by UTF-16 units, D83D < FFFF, so the
    // emoji sorts BEFORE U+FFFF (this differs from raw UTF-8 byte order, where the
    // 4-byte F0 9F.. > 3-byte EF BF..). Confirms cmp_utf16 matches Kotlin.
    {
        std::string emoji = "\xF0\x9F\x98\x80";  // U+1F600
        std::string ufff = "\xEF\xBF\xBF";        // U+FFFF
        if (!(cmp_utf16(emoji, ufff) < 0)) {
            std::fprintf(stderr, "[rell::gtv] UTF16 order FAIL (emoji should sort first)\n");
            g_ok = false;
        }
    }

    if (g_ok) std::fprintf(stderr, "[rell::gtv] self_test: ALL PASS\n");
    return g_ok;
}

}  // namespace gtv
}  // namespace rell

// TODO(merkle): GtvMerkleHash (gtv.hash() / gtv.legacy_hash()) is intentionally NOT
// implemented here. It needs (a) SHA-256 (use rell::crypto from rell_crypto.* once it
// lands) and (b) the postchain GtvBinaryTreeFactory V1/V2 tree-balancing rules
// (left/right padding + odd-node promotion), which must be transcribed from
// net.postchain.gtv.merkle before implementation to stay byte-exact. Prefixes are known:
// LEAF=0x01, NODE=0x00, NODE_ARRAY=0x07, NODE_DICT=0x08; leaf = SHA256(0x01 || encodeGtv);
// node = SHA256(prefix || left32 || right32). Ship as rell_gtv_merkle.* when ready.

#ifdef RELL_GTV_MAIN
int main() {
    return rell::gtv::self_test() ? 0 : 1;
}
#endif
