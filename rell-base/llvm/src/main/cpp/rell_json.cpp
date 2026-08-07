// Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
//
// rell_json.cpp — implementation of namespace rell::json (see rell_json.h). Standalone, no
// external JSON library. Reproduces Jackson 2.21.3's strict-default parse and compact
// JsonNode.toString() output byte-for-byte, including Java's Double.toString() number formatting.

#include "rell_json.h"

#include <array>
#include <cassert>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>

namespace rell::json {

// =====================================================================================
// Parser — mirrors Jackson's strict (RFC 8259) grammar. No comments, single quotes, NaN,
// Infinity, leading-zero ints, unquoted keys, or trailing commas. readTree() reads exactly one
// value and ignores any trailing content after it.
// =====================================================================================
namespace {

class Parser {
public:
    explicit Parser(const std::string &s) : s_(s), n_(s.size()) {}

    JsonPtr parse_document() {
        skip_ws();
        if (pos_ >= n_) fail();  // nothing but whitespace -> JsonEOFException
        JsonPtr v = parse_value();
        // Jackson readTree() reads ONE value and ignores trailing whitespace/tokens after a
        // self-delimiting value (string/array/object): `"a""b"` -> "a", `[1]2` -> [1], `{}{}` -> {}.
        // BUT a ROOT-LEVEL SCALAR (number / true / false / null) must be followed by whitespace or
        // EOF, because the tokenizer reads the scalar greedily and the root EOF check then rejects a
        // glued non-separator: `1,2` / `123abc` / `01` / `truefalse` / `nullx` / `1]` all REJECT,
        // while `1 2` -> 1 and `123 ` -> 123. (Inside containers the same separator requirement is
        // enforced by the array/object delimiter checks, so this only needs handling at the root.)
        if (v->type == NodeType::Null || v->type == NodeType::Boolean ||
            v->type == NodeType::NumberInt || v->type == NodeType::NumberDbl) {
            if (pos_ < n_) {
                char c = s_[pos_];
                if (c != ' ' && c != '\t' && c != '\n' && c != '\r') fail();
            }
        }
        return v;
    }

private:
    const std::string &s_;
    size_t pos_ = 0;
    const size_t n_;

    [[noreturn]] void fail() { throw JsonParseError(s_); }

    char peek() const { return pos_ < n_ ? s_[pos_] : '\0'; }
    char getc() { return s_[pos_++]; }
    bool eof() const { return pos_ >= n_; }

    // JSON insignificant whitespace: space, tab, LF, CR (Jackson's default).
    void skip_ws() {
        while (pos_ < n_) {
            char c = s_[pos_];
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') pos_++;
            else break;
        }
    }

    JsonPtr parse_value() {
        if (eof()) fail();
        char c = peek();
        switch (c) {
            case '{': return parse_object();
            case '[': return parse_array();
            case '"': return parse_string_value();
            case 't': return parse_literal("true", make_bool(true));
            case 'f': return parse_literal("false", make_bool(false));
            case 'n': return parse_literal("null", make_null());
            default:
                if (c == '-' || (c >= '0' && c <= '9')) return parse_number();
                fail();
        }
    }

    static JsonPtr make_null() { auto j = std::make_shared<Json>(); j->type = NodeType::Null; return j; }
    static JsonPtr make_bool(bool b) {
        auto j = std::make_shared<Json>(); j->type = NodeType::Boolean; j->boolean = b; return j;
    }

    JsonPtr parse_literal(const char *lit, JsonPtr value) {
        size_t len = std::strlen(lit);
        if (pos_ + len > n_ || s_.compare(pos_, len, lit) != 0) fail();
        pos_ += len;
        return value;
    }

    JsonPtr parse_object() {
        auto j = std::make_shared<Json>();
        j->type = NodeType::Object;
        getc();  // '{'
        skip_ws();
        if (peek() == '}') { getc(); return j; }
        for (;;) {
            skip_ws();
            if (peek() != '"') fail();         // keys must be quoted strings
            std::string key = parse_string_raw();
            skip_ws();
            if (eof() || getc() != ':') fail();
            skip_ws();
            JsonPtr val = parse_value();
            // Jackson keeps LAST value for duplicate keys but preserves the FIRST key's position.
            bool replaced = false;
            for (auto &m : j->members) {
                if (m.first == key) { m.second = val; replaced = true; break; }
            }
            if (!replaced) j->members.emplace_back(std::move(key), std::move(val));
            skip_ws();
            if (eof()) fail();
            char c = getc();
            if (c == ',') continue;
            if (c == '}') break;
            fail();
        }
        return j;
    }

    JsonPtr parse_array() {
        auto j = std::make_shared<Json>();
        j->type = NodeType::Array;
        getc();  // '['
        skip_ws();
        if (peek() == ']') { getc(); return j; }
        for (;;) {
            skip_ws();
            j->array.push_back(parse_value());
            skip_ws();
            if (eof()) fail();
            char c = getc();
            if (c == ',') continue;
            if (c == ']') break;
            fail();
        }
        return j;
    }

    JsonPtr parse_string_value() {
        auto j = std::make_shared<Json>();
        j->type = NodeType::String;
        j->str_val = parse_string_raw();
        return j;
    }

    // Parse a JSON string token (opening '"' at pos_), returning the decoded UTF-8 content.
    std::string parse_string_raw() {
        if (eof() || getc() != '"') fail();
        std::string out;
        for (;;) {
            if (eof()) fail();  // JsonEOFException (unterminated string)
            unsigned char c = static_cast<unsigned char>(getc());
            if (c == '"') break;
            if (c == '\\') {
                if (eof()) fail();
                char e = getc();
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
                        uint32_t cp = parse_hex4();
                        if (cp >= 0xD800 && cp <= 0xDBFF) {
                            // High surrogate. Jackson combines it with an IMMEDIATELY following
                            // \uXXXX low surrogate into the astral code point; any other case
                            // (no following \u, following \u is not a low surrogate, or this is a
                            // lone low surrogate below) is an unpaired surrogate, which Jackson
                            // substitutes with U+003F '?' (0x3F) on output. Verified against the
                            // pinned Jackson 2.21.3: "\uD800"->?, "\uD800x"->?x, "\uD800A"->?A
                            // (the second escape is reparsed independently), "\uD800\uD800"->??,
                            // "􏿿"->U+10FFFF. See the M5 self-test block.
                            if (pos_ + 1 < n_ && s_[pos_] == '\\' && s_[pos_ + 1] == 'u') {
                                size_t save = pos_;
                                pos_ += 2;
                                uint32_t lo = parse_hex4();
                                if (lo >= 0xDC00 && lo <= 0xDFFF) {
                                    cp = 0x10000 + ((cp - 0xD800) << 10) + (lo - 0xDC00);
                                } else {
                                    // Not a low surrogate: the high surrogate is unpaired ('?'),
                                    // and the following escape must be reparsed from scratch.
                                    pos_ = save;
                                    cp = 0x3F;
                                }
                            } else {
                                cp = 0x3F;  // lone high surrogate at EOF / before non-\u content
                            }
                        } else if (cp >= 0xDC00 && cp <= 0xDFFF) {
                            cp = 0x3F;  // lone low surrogate -> '?'
                        }
                        append_utf8(out, cp);
                        break;
                    }
                    default: fail();  // invalid escape
                }
            } else if (c < 0x20) {
                fail();  // unescaped control char -> Jackson rejects
            } else {
                out.push_back(static_cast<char>(c));  // copy UTF-8 bytes verbatim
            }
        }
        return out;
    }

    uint32_t parse_hex4() {
        if (pos_ + 4 > n_) fail();
        uint32_t v = 0;
        for (int i = 0; i < 4; i++) {
            char c = s_[pos_++];
            v <<= 4;
            if (c >= '0' && c <= '9') v |= (c - '0');
            else if (c >= 'a' && c <= 'f') v |= (c - 'a' + 10);
            else if (c >= 'A' && c <= 'F') v |= (c - 'A' + 10);
            else fail();
        }
        return v;
    }

    static void append_utf8(std::string &out, uint32_t cp) {
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
    }

    // Number grammar (RFC 8259, Jackson strict):
    //   -?  (0 | [1-9][0-9]*)  ( . [0-9]+ )?  ( [eE] [+-]? [0-9]+ )?
    // Leading zeros (e.g. "01") are rejected. A token with '.', 'e' or 'E' becomes a double
    // (NumberDbl); a pure integer becomes NumberInt with exact digits preserved.
    JsonPtr parse_number() {
        size_t start = pos_;
        bool is_double = false;

        if (peek() == '-') getc();

        // integer part
        if (eof()) fail();
        char c = peek();
        if (c == '0') {
            getc();
            // no further digits allowed before '.'/'e' (no leading zeros / "00")
        } else if (c >= '1' && c <= '9') {
            getc();
            while (!eof() && peek() >= '0' && peek() <= '9') getc();
        } else {
            fail();  // e.g. "-" with no digits
        }

        // fraction
        if (!eof() && peek() == '.') {
            is_double = true;
            getc();
            if (eof() || peek() < '0' || peek() > '9') fail();  // need >=1 digit
            while (!eof() && peek() >= '0' && peek() <= '9') getc();
        }

        // exponent
        if (!eof() && (peek() == 'e' || peek() == 'E')) {
            is_double = true;
            getc();
            if (!eof() && (peek() == '+' || peek() == '-')) getc();
            if (eof() || peek() < '0' || peek() > '9') fail();  // need >=1 digit
            while (!eof() && peek() >= '0' && peek() <= '9') getc();
        }

        std::string tok = s_.substr(start, pos_ - start);

        auto j = std::make_shared<Json>();
        if (is_double) {
            j->type = NodeType::NumberDbl;
            j->dbl = std::strtod(tok.c_str(), nullptr);
        } else {
            j->type = NodeType::NumberInt;
            j->int_digits = normalize_int_digits(tok);
        }
        return j;
    }

    // Normalise an integer token to Jackson's BigInteger.toString() form: drop a redundant
    // leading sign on zero ("-0" -> "0"), keep all significant digits. The grammar already
    // forbids leading zeros, so no stripping beyond the sign-on-zero case is needed.
    static std::string normalize_int_digits(const std::string &tok) {
        bool neg = !tok.empty() && tok[0] == '-';
        const std::string digits = neg ? tok.substr(1) : tok;
        // digits has no leading zeros except the single "0".
        bool all_zero = true;
        for (char d : digits) {
            if (d != '0') { all_zero = false; break; }
        }
        if (all_zero) return "0";  // "-0" / "0" -> "0"
        return tok;
    }
};

}  // namespace

JsonPtr parse(const std::string &s) {
    // Rell: require(!s.isBlank()) before Jackson runs. isBlank() == all chars are whitespace
    // (Kotlin uses Character.isWhitespace; for JSON inputs the relevant set is space/\t/\n/\r,
    // matched here).
    bool blank = true;
    for (char c : s) {
        if (c != ' ' && c != '\t' && c != '\n' && c != '\r' && c != '\f' &&
            c != '\v' && static_cast<unsigned char>(c) != 0x1c &&
            static_cast<unsigned char>(c) != 0x1d && static_cast<unsigned char>(c) != 0x1e &&
            static_cast<unsigned char>(c) != 0x1f) {
            blank = false;
            break;
        }
    }
    if (blank) throw JsonParseError(s);
    Parser p(s);
    return p.parse_document();
}

// =====================================================================================
// Serializer — Jackson JsonNode.toString() (compact).
// =====================================================================================
namespace {

// Jackson's default string escaping (CharTypes ESCAPE_STANDARD + JsonStringEncoder):
//   - '"' -> \" ; '\\' -> \\
//   - \b \t \n \f \r for 0x08,0x09,0x0A,0x0C,0x0D
//   - other chars < 0x20 -> \u00XX with UPPERCASE hex
//   - everything >= 0x20 (incl. '/', 0x7F, U+2028/2029, all non-ASCII UTF-8) passes through.
//
// CROSS-REF (do NOT "consistency-align" with GTV): Jackson's JsonStringEncoder does NOT escape
// U+2028 (LINE SEPARATOR) / U+2029 (PARAGRAPH SEPARATOR) — they pass through as raw UTF-8, and the
// " " self-test vector below pins this. This is the OPPOSITE of GTV/Gson, whose
// JsonWriter.string() escapes both unconditionally to   /   (see rell_gtv.cpp
// json_escape_string, review finding B1). The two JSON families intentionally diverge here; a
// future refactor must keep them separate.
void append_escaped_string(std::string &out, const std::string &s) {
    static const char HEX[] = "0123456789ABCDEF";
    out.push_back('"');
    for (unsigned char c : s) {
        switch (c) {
            case '"': out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\b': out += "\\b"; break;
            case '\t': out += "\\t"; break;
            case '\n': out += "\\n"; break;
            case '\f': out += "\\f"; break;
            case '\r': out += "\\r"; break;
            default:
                if (c < 0x20) {
                    out += "\\u00";
                    out.push_back(HEX[(c >> 4) & 0xF]);
                    out.push_back(HEX[c & 0xF]);
                } else {
                    out.push_back(static_cast<char>(c));
                }
        }
    }
    out.push_back('"');
}

void write(std::string &out, const Json &node);

void write(std::string &out, const Json &node) {
    switch (node.type) {
        case NodeType::Null:
            out += "null";
            break;
        case NodeType::Boolean:
            out += node.boolean ? "true" : "false";
            break;
        case NodeType::NumberInt:
            out += node.int_digits;
            break;
        case NodeType::NumberDbl:
            out += java_double_to_string(node.dbl);
            break;
        case NodeType::String:
            append_escaped_string(out, node.str_val);
            break;
        case NodeType::Array: {
            out.push_back('[');
            bool first = true;
            for (const auto &e : node.array) {
                if (!first) out.push_back(',');
                first = false;
                write(out, *e);
            }
            out.push_back(']');
            break;
        }
        case NodeType::Object: {
            out.push_back('{');
            bool first = true;
            for (const auto &m : node.members) {
                if (!first) out.push_back(',');
                first = false;
                append_escaped_string(out, m.first);
                out.push_back(':');
                write(out, *m.second);
            }
            out.push_back('}');
            break;
        }
    }
}

}  // namespace

std::string to_text(const Json &node) {
    std::string out;
    write(out, node);
    return out;
}

// =====================================================================================
// java_double_to_string — reproduce java.lang.Double.toString(double).
//
// Java specifies: the shortest decimal string that round-trips to the same double, formatted as
//   - decimal notation when 1e-3 <= |v| < 1e7;
//   - "computerized scientific notation" d.dddEexp otherwise (uppercase 'E'),
// always with at least one digit after the decimal point, and a leading '-' for negatives.
// Specials: NaN -> "NaN", +Inf -> "Infinity", -Inf -> "-Infinity", +0.0 -> "0.0",
// -0.0 -> "-0.0". (Jackson never emits NaN/Infinity from a parsed JSON number, but the helper
// is faithful for completeness.)
//
// We obtain the unique shortest round-trippable digit string the same way Java's FloatingDecimal
// does in effect: try increasing decimal precision with printf "%.*e" and pick the first that
// strtod-round-trips. Shortest round-trippable is unique, so this yields Java's exact digit set
// (verified against Double.toString on this machine). We then apply Java's layout rules.
//
// !!! CONSENSUS-CRITICAL LIBC ASSUMPTION (review finding M4) !!!
// This routine's byte-exactness depends on the HOST C LIBRARY providing CORRECTLY-ROUNDED
// IEEE-754 round-trip conversions:
//   - snprintf("%.*e", p, x) must emit the correctly-rounded p-significant-digit decimal, and
//   - strtod must parse decimal -> nearest double with round-half-to-even.
// glibc, macOS libc (Apple/FreeBSD libm), and musl all satisfy this. It is NOT guaranteed by the
// C standard for every libc the LLVM backend may someday target (some embedded / minimal libcs
// round only to ~15 digits or use round-half-away). On such a libc the "shortest that round-trips"
// search could pick a DIFFERENT digit string than Java's FloatingDecimal, producing divergent
// canonical JSON text and therefore a CONSENSUS FORK.
//
// Mitigation status: DOCUMENTED-NOT-REPLACED. A self-contained Ryu/Grisu shortest-double formatter
// would remove the libc dependency entirely and is the correct long-term fix, but it is out of
// scope for a surgical change and must not be shipped half-correct. Until then, any port to a new
// target MUST (a) gate CI on this file's self_test (including the boundary vectors below: the
// 1e-3/1e7 layout cutoffs, max-finite, smallest-normal, and the 8 legacy-subnormal pins) passing
// against java.lang.Double.toString, and (b) verify the target libc is correctly-rounded. If
// either fails, replace this routine with Ryu before enabling the JSON family on that target.
// =====================================================================================
std::string java_double_to_string(double v) {
    if (std::isnan(v)) return "NaN";
    if (std::isinf(v)) return v < 0 ? "-Infinity" : "Infinity";

    // Legacy-FloatingDecimal quirk: for the eight smallest subnormals the JDK's pre-19
    // sun.misc.FloatingDecimal emits a non-minimal 2-significant-digit form ("4.9E-324") rather
    // than the shortest round-trippable 1-digit form ("5.0E-324"). The shortest-via-printf finder
    // below would produce the 1-digit form, so we pin these exact bit patterns to match the JVM
    // byte-for-byte. This is the COMPLETE divergence set: verified by comparing
    // java_double_to_string against Double.toString over every subnormal in two dense bands plus
    // 5,000,000 random doubles across the whole range (exactly these 8 differed). Determinism is
    // consensus-critical, so the table closes the gap rather than leaving it open.
    {
        uint64_t bits;
        std::memcpy(&bits, &v, sizeof(bits));
        switch (bits) {
            case 0x1ULL:  return "4.9E-324";
            case 0x2ULL:  return "9.9E-324";
            case 0xaULL:  return "4.9E-323";
            case 0xcULL:  return "5.9E-323";
            case 0xeULL:  return "6.9E-323";
            case 0x10ULL: return "7.9E-323";
            case 0x12ULL: return "8.9E-323";
            case 0x14ULL: return "9.9E-323";
            default: break;
        }
    }

    bool neg = std::signbit(v);
    double mag = neg ? -v : v;

    std::string out;
    if (neg) out.push_back('-');

    if (mag == 0.0) {
        out += "0.0";
        return out;
    }

    // Find shortest round-trippable digits via "%.*e": buffer = "d.ddde±XX".
    char buf[64];
    int chosen_p = -1;
    for (int p = 0; p <= 17; p++) {
        std::snprintf(buf, sizeof(buf), "%.*e", p, mag);
        if (std::strtod(buf, nullptr) == mag) {
            chosen_p = p;
            break;
        }
    }
    if (chosen_p < 0) {
        std::snprintf(buf, sizeof(buf), "%.17e", mag);  // fallback (should not happen)
    }

    // Extract digits (no '.') and base-10 exponent of the first digit.
    // buf is "D.DDDDe±EE" (or "De±EE" when p==0).
    const char *e_ptr = std::strchr(buf, 'e');
    int first_exp = std::atoi(e_ptr + 1);  // exponent of leading digit

    std::string digits;
    for (const char *c = buf; c < e_ptr; c++) {
        if (*c >= '0' && *c <= '9') digits.push_back(*c);
    }
    // Strip trailing zeros (shortest form has none, but %.*e can pad).
    while (digits.size() > 1 && digits.back() == '0') digits.pop_back();
    const int ndigits = static_cast<int>(digits.size());

    // Java layout: decimal form iff -3 <= first_exp < 7.
    if (first_exp >= -3 && first_exp < 7) {
        if (first_exp >= 0) {
            // Integer part spans first_exp+1 leading digits.
            int int_len = first_exp + 1;
            if (ndigits <= int_len) {
                out.append(digits);
                out.append(int_len - ndigits, '0');  // pad integer part
                out += ".0";                          // always a fractional digit
            } else {
                out.append(digits, 0, int_len);
                out.push_back('.');
                out.append(digits, int_len, ndigits - int_len);
            }
        } else {
            // 0.00..digits  : (-first_exp - 1) zeros between the dot and the first digit.
            out += "0.";
            out.append(-first_exp - 1, '0');
            out.append(digits);
        }
    } else {
        // Scientific: d.ddddE{first_exp}, at least one fractional digit.
        out.push_back(digits[0]);
        out.push_back('.');
        if (ndigits == 1) {
            out.push_back('0');
        } else {
            out.append(digits, 1, ndigits - 1);
        }
        out.push_back('E');
        out += std::to_string(first_exp);
    }
    return out;
}

// =====================================================================================
// JsonUtils mirror — pure logic over the parsed model, matching lib_type_json.kt's accessor
// semantics. Provided so the accessor family can be lowered without JNI; not on the consensus
// hot path (only parse/to_text are), but kept faithful. These are header-free helpers usable by
// the backend lowering; see lib_type_json.kt for the exact error-code strings.
//
// Integer-range bounds: Rell `integer` is i64; `big_integer` is bounded by
// Lib_BigIntegerMath.MIN/MAX_VALUE (+/- (10^131072 - 1)). The big_integer bound is enormous, so
// for canBeRellBigInteger we just require an integral number node (NumberInt) — any integral JSON
// literal that fits in memory is within range in practice. (TODO: if exact big_integer overflow
// rejection is ever required, compare digit count against 131072.)
// =====================================================================================

// canBeRellInteger: integral number whose value fits in signed 64-bit.
bool json_can_be_rell_integer(const Json &node) {
    if (node.type != NodeType::NumberInt) return false;
    const std::string &d = node.int_digits;
    bool neg = !d.empty() && d[0] == '-';
    const std::string mag = neg ? d.substr(1) : d;
    // Compare |value| against 2^63 (neg) or 2^63 - 1 (pos) by string length then lexicographically.
    static const char *MAX_POS = "9223372036854775807";  // 2^63 - 1
    static const char *MAX_NEG = "9223372036854775808";  // 2^63
    const char *limit = neg ? MAX_NEG : MAX_POS;
    size_t limit_len = std::strlen(limit);
    if (mag.size() != limit_len) return mag.size() < limit_len;
    return mag.compare(limit) <= 0;
}

// canBeRellBigInteger: any integral number node (see TODO above re: 131072-digit bound).
bool json_can_be_rell_big_integer(const Json &node) {
    return node.type == NodeType::NumberInt;
}

// =====================================================================================
// Self-test — golden vectors generated from a JVM probe against Jackson 2.21.3 +
// java.lang.Double.toString on this machine (2026-06-03). Every IN -> EXPECTED pair below was
// produced by ObjectMapper().readTree(IN).toString() (or REJECT for inputs Jackson refuses).
// =====================================================================================

namespace {

int g_failures = 0;

void check_text(const char *in, const char *expected) {
    try {
        JsonPtr v = parse(in);
        std::string got = to_text(*v);
        if (got != expected) {
            std::fprintf(stderr, "FAIL parse/to_text  in=%s  expected=%s  got=%s\n", in, expected,
                         got.c_str());
            g_failures++;
        }
    } catch (const JsonParseError &e) {
        std::fprintf(stderr, "FAIL parse/to_text  in=%s  expected=%s  but REJECTED\n", in, expected);
        g_failures++;
    }
}

void check_reject(const char *in) {
    try {
        parse(in);
        std::fprintf(stderr, "FAIL expected REJECT but parsed  in=%s\n", in);
        g_failures++;
    } catch (const JsonParseError &) {
        // expected
    }
}

void check_dbl(double v, const char *expected) {
    std::string got = java_double_to_string(v);
    if (got != expected) {
        std::fprintf(stderr, "FAIL double  v=%.17g  expected=%s  got=%s\n", v, expected,
                     got.c_str());
        g_failures++;
    }
}

}  // namespace

bool self_test() {
    g_failures = 0;

    // ---- integers (exact digits preserved; -0 -> 0) ----
    check_text("1", "1");
    check_text("-1", "-1");
    check_text("0", "0");
    check_text("-0", "0");
    check_text("100", "100");
    check_text("123456789012345678901234567890", "123456789012345678901234567890");
    check_text("10000000000000000000", "10000000000000000000");
    check_text("9223372036854775807", "9223372036854775807");
    check_text("9223372036854775808", "9223372036854775808");

    // ---- doubles (Java Double.toString) ----
    check_text("1.0", "1.0");
    check_text("1.5", "1.5");
    check_text("1e3", "1000.0");
    check_text("1E3", "1000.0");
    check_text("1.5e3", "1500.0");
    check_text("1.50", "1.5");
    check_text("0.0", "0.0");
    check_text("-0.0", "-0.0");
    check_text("3.14159265358979323846", "3.141592653589793");
    check_text("1.0e10", "1.0E10");
    check_text("1.23E-4", "1.23E-4");
    check_text("0.1", "0.1");
    check_text("100.00", "100.0");
    check_text("2.0", "2.0");
    check_text("0e0", "0.0");
    check_text("1e+3", "1000.0");
    check_text("-1.5E2", "-150.0");

    // ---- strings & escaping ----
    check_text("\"hello\"", "\"hello\"");
    check_text("\"a\\\"b\"", "\"a\\\"b\"");        // a"b -> a\"b
    check_text("\"a\\\\b\"", "\"a\\\\b\"");        // a\b -> a\\b
    check_text("\"tab\\there\"", "\"tab\\there\"");
    check_text("\"nl\\nhere\"", "\"nl\\nhere\"");
    check_text("\"\\u0000\"", "\"\\u0000\"");
    check_text("\"\\u0001\"", "\"\\u0001\"");
    check_text("\"\\u001f\"", "\"\\u001F\"");      // uppercase hex
    check_text("\"slash/here\"", "\"slash/here\"");  // '/' not escaped
    check_text("\"unicode\xC3\xA9\"", "\"unicode\xC3\xA9\"");  // é passes through
    check_text("\"\\u007f\"", "\"\x7F\"");          // DEL passes through literally
    check_text("\"\\b\\f\\r\"", "\"\\b\\f\\r\"");
    check_text("\"\\u2028\"", "\"\xE2\x80\xA8\"");  // U+2028 passes through (UTF-8); see B1 cross-ref
    check_text("\"\\u2029\"", "\"\xE2\x80\xA9\"");  // U+2029 passes through (UTF-8); NOT escaped
    check_text("\"emoji\xF0\x9F\x98\x80\"", "\"emoji\xF0\x9F\x98\x80\"");  // surrogate pair / 4-byte
    check_text("\"\\uD83D\\uDE00\"", "\"\xF0\x9F\x98\x80\"");  // 😀 -> U+1F600 (4-byte)

    // ---- M5: lone / unpaired surrogate escapes (review finding M5) ----
    // Golden bytes CAPTURED from ObjectMapper().readTree(IN).toString() on the pinned Jackson
    // 2.21.3 (jackson-core/databind 2.21.3): every unpaired surrogate code unit is substituted with
    // U+003F '?' (0x3F). A high surrogate combines ONLY with an immediately following \u low
    // surrogate; otherwise it is '?' and the following escape is reparsed independently.
    check_text("\"\\uD800\"", "\"?\"");          // lone high surrogate
    check_text("\"\\uDC00\"", "\"?\"");          // lone low surrogate
    check_text("\"\\uD800x\"", "\"?x\"");        // high + non-\u char
    check_text("\"\\uD800\\u0041\"", "\"?A\"");  // high + \u BMP (reparsed): '?' then 'A'
    check_text("\"\\uD800\\uD800\"", "\"??\"");  // high + high: both unpaired
    check_text("\"\\uDC00x\"", "\"?x\"");        // lone low + char
    check_text("\"\\uDBFF\\uDFFF\"", "\"\xF4\x8F\xBF\xBF\"");  // valid max pair -> U+10FFFF

    // ---- containers, key order (NOT sorted), whitespace stripping ----
    check_text("{\"b\":1,\"a\":2}", "{\"b\":1,\"a\":2}");  // insertion order preserved
    check_text("[1,2,3]", "[1,2,3]");
    check_text("{}", "{}");
    check_text("[]", "[]");
    check_text("{\"x\":[1,{\"y\":2}]}", "{\"x\":[1,{\"y\":2}]}");
    check_text("  {  \"a\" : 1 , \"b\":2 }  ", "{\"a\":1,\"b\":2}");
    check_text("true", "true");
    check_text("false", "false");
    check_text("null", "null");

    // ---- trailing tokens ignored (Jackson readTree leniency) ----
    check_text("1 2", "1");
    check_text("{}extra", "{}");

    // ---- rejections ----
    check_reject("");
    check_reject("   ");
    check_reject("{");
    check_reject("[1,2");
    check_reject("{\"a\":}");
    check_reject("'single'");
    check_reject("undefined");
    check_reject("NaN");
    check_reject("Infinity");
    check_reject("01");
    check_reject("1,2");
    check_reject("{a:1}");
    check_reject("[1 2]");
    check_reject("trailing");
    check_reject("123abc");
    check_reject("\"unterminated");

    // ---- java_double_to_string direct ----
    check_dbl(1000.0, "1000.0");
    check_dbl(1.5, "1.5");
    check_dbl(0.1, "0.1");
    check_dbl(1.0e10, "1.0E10");
    check_dbl(1.23e-4, "1.23E-4");
    check_dbl(100.0, "100.0");
    check_dbl(1.2345678e7, "1.2345678E7");
    check_dbl(0.3333333333333333, "0.3333333333333333");
    check_dbl(0.30000000000000004, "0.30000000000000004");
    check_dbl(1.1, "1.1");
    check_dbl(9999999.999999998, "9999999.999999998");
    check_dbl(123.456, "123.456");
    check_dbl(9.999999999999998e-4, "9.999999999999998E-4");
    check_dbl(1234567.8, "1234567.8");
    check_dbl(1.23456789e8, "1.23456789E8");
    check_dbl(4.9e-324, "4.9E-324");
    check_dbl(1.7976931348623157e308, "1.7976931348623157E308");
    check_dbl(2.0, "2.0");
    check_dbl(0.0001, "1.0E-4");
    check_dbl(0.001, "0.001");
    check_dbl(9999999.0, "9999999.0");
    check_dbl(10000000.0, "1.0E7");
    check_dbl(-0.0, "-0.0");
    check_dbl(0.0, "0.0");
    check_dbl(123456789.0, "1.23456789E8");
    check_dbl(100000000.0, "1.0E8");
    check_dbl(-1.0e8, "-1.0E8");

    // ---- M4 boundary vectors: layout cutoffs (first_exp -3 and +7) + magnitude extremes ----
    // Decimal-vs-scientific switch is at 1e-3 (<: scientific) and 1e7 (>=: scientific).
    check_dbl(0.001, "0.001");          // first_exp = -3 -> decimal (boundary, included)
    check_dbl(0.0009999999999999999, "9.999999999999998E-4");  // just below 1e-3 -> scientific
    check_dbl(9999999.999999998, "9999999.999999998");          // first_exp = 6 -> decimal
    check_dbl(10000000.0, "1.0E7");     // first_exp = 7 -> scientific (boundary)
    check_dbl(9999999.0, "9999999.0");  // largest 7-digit integer in decimal range
    // Smallest positive normal double (2^-1022).
    check_dbl(2.2250738585072014e-308, "2.2250738585072014E-308");
    // Largest subnormal (just below smallest normal) and smallest subnormal.
    check_dbl(2.225073858507201e-308, "2.225073858507201E-308");
    check_dbl(4.9e-324, "4.9E-324");    // smallest positive subnormal (legacy 2-digit pin)
    // Max finite double.
    check_dbl(1.7976931348623157e308, "1.7976931348623157E308");
    check_dbl(-1.7976931348623157e308, "-1.7976931348623157E308");

    // ---- round-trip stability: to_text(parse(x)) is idempotent on canonical text ----
    {
        const char *canon[] = {"{\"b\":1,\"a\":[2,3,null,true]}", "1.0E10", "-150.0", "\"a\\nb\""};
        for (const char *c : canon) {
            std::string once = to_text(*parse(c));
            std::string twice = to_text(*parse(once));
            if (once != twice) {
                std::fprintf(stderr, "FAIL round-trip not idempotent: %s -> %s -> %s\n", c,
                             once.c_str(), twice.c_str());
                g_failures++;
            }
        }
    }

    // ---- accessor mirrors ----
    {
        auto big = parse("123456789012345678901234567890");
        if (json_can_be_rell_integer(*big)) { std::fprintf(stderr, "FAIL canBeRellInteger huge\n"); g_failures++; }
        if (!json_can_be_rell_big_integer(*big)) { std::fprintf(stderr, "FAIL canBeRellBigInteger huge\n"); g_failures++; }
        auto maxp = parse("9223372036854775807");
        if (!json_can_be_rell_integer(*maxp)) { std::fprintf(stderr, "FAIL canBeRellInteger maxpos\n"); g_failures++; }
        auto over = parse("9223372036854775808");
        if (json_can_be_rell_integer(*over)) { std::fprintf(stderr, "FAIL canBeRellInteger overpos\n"); g_failures++; }
        auto minn = parse("-9223372036854775808");
        if (!json_can_be_rell_integer(*minn)) { std::fprintf(stderr, "FAIL canBeRellInteger minneg\n"); g_failures++; }
        auto undern = parse("-9223372036854775809");
        if (json_can_be_rell_integer(*undern)) { std::fprintf(stderr, "FAIL canBeRellInteger underneg\n"); g_failures++; }
        auto dbl = parse("1.5");
        if (json_can_be_rell_integer(*dbl)) { std::fprintf(stderr, "FAIL canBeRellInteger dbl\n"); g_failures++; }
    }

    if (g_failures == 0) {
        std::fprintf(stderr, "rell::json self_test: ALL PASS\n");
    } else {
        std::fprintf(stderr, "rell::json self_test: %d FAILURE(S)\n", g_failures);
    }
    return g_failures == 0;
}

}  // namespace rell::json

#ifdef RELL_JSON_SELFTEST
int main() { return rell::json::self_test() ? 0 : 1; }
#endif
