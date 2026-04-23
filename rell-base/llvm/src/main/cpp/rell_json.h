// Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
//
// rell_json.h — namespace rell::json: a self-contained JSON parser + canonical serializer that is
// BYTE-IDENTICAL to Rell's `json` type, which is backed by Jackson 2.21.3
// (com.fasterxml.jackson.databind.ObjectMapper / JsonNode). This is consensus-critical: `json`
// values are stored/compared/hashed by their canonical text, so the C++ port must reproduce
// Jackson's exact output for the LLVM backend to avoid JNI round-trips.
//
// SOURCE OF TRUTH (Rell side, all in rell-base/runtime-core/src/main/kotlin):
//   - runtime/rt_value_json.kt : Rt_JsonValue
//       * parse(s): require(!s.isBlank()); mapper.readTree(s); JsonProcessingException ->
//         IllegalArgumentException; requireNotNull(json).
//       * str (the canonical text used everywhere, incl. equals/hashCode/to_text/to_gtv) =
//         node.toString()  -- Jackson JsonNode.toString(), the COMPACT serialization.
//   - lib/type/lib_type_json.kt : Lib_Type_Json
//       * json(text) constructor wraps parse(); IllegalArgumentException -> Rt_Exception
//         "fn_json_badstr".
//       * to_text / str -> self.str (this header's to_text).
//       * accessors (get/as_*/is_*/size/keys) operate on the parsed node; provided here as pure
//         logic over the parsed model (see JsonUtils mirror at the bottom of the .cpp).
//
// EXACT FORMATTING (verified against Jackson 2.21.3 on this machine; see the self-test golden
// vectors in rell_json.cpp, generated from a JVM probe):
//   - Compact: no whitespace. `{"a":1,"b":[2,3]}` — no spaces after ':' or ','.
//   - Object key order = PARSE/INSERTION order (Jackson does NOT sort object keys; this is the
//     opposite of GTV dicts).
//   - null/true/false literally.
//   - Strings: escape only chars < 0x20 plus '"' and '\\'. Named escapes \b \t \n \f \r; other
//     control chars as \u00XX with UPPERCASE hex. '/' is NOT escaped. 0x7F (DEL), U+2028/U+2029
//     and all non-ASCII pass through verbatim (UTF-8). Matches Jackson's default
//     JsonStringEncoder / CharTypes ESCAPE_STANDARD output.
//   - Numbers: Jackson stores an integral number with no '.'/'e'/'E' as a BigInteger node and
//     re-emits its EXACT decimal digits (leading '-0' normalised to '0'); a number containing
//     '.', 'e' or 'E' is parsed as a Java `double` and re-emitted via Java's Double.toString()
//     (shortest round-trippable decimal; decimal form for 1e-3 <= |v| < 1e7, else `d.dddEexp`
//     with uppercase 'E'). Both are reproduced exactly here.
//
//   NOTE on parse leniency (Jackson default, mirrored here): readTree() reads exactly ONE JSON
//   value and IGNORES any trailing whitespace + further tokens after it ("1 2" -> 1, "{}extra" ->
//   {}). But garbage glued to the first token ("123abc", "1,2", "trailing") is rejected. A
//   blank/empty string is rejected by Rell's `require(!s.isBlank())` before Jackson even runs.
//   Standard-JSON-only: single quotes, comments, NaN/Infinity, leading-zero ints, unquoted keys
//   are all rejected (Jackson's STRICT defaults — Rell does not enable any leniency features).
//
// SELF-CONTAINED: no external JSON library; plain std::string in/out so it unit-tests without the
// rest of the backend. The number model distinguishes integral(BigInteger)/double the same way
// Jackson's numeric JsonNode subtypes do, which the is_*/as_* accessors rely on.
//
// COMPILE/TEST STANDALONE:
//   clang++ -std=c++17 -DRELL_JSON_SELFTEST rell_json.cpp -o rell_json_test && ./rell_json_test
//   (syntax-only check: clang++ -std=c++17 -fsyntax-only rell_json.cpp)

#ifndef RELL_JSON_H
#define RELL_JSON_H

#include <cstdint>
#include <memory>
#include <stdexcept>
#include <string>
#include <vector>

namespace rell::json {

// Thrown by parse() when the input is not valid JSON (mirrors the JVM
// IllegalArgumentException -> Rt_Exception "fn_json_badstr" path). The message is the offending
// input, matching Rell's "Bad JSON: <text>".
class JsonParseError : public std::invalid_argument {
public:
    explicit JsonParseError(const std::string &msg) : std::invalid_argument(msg) {}
};

// Node kinds, mirroring Jackson's JsonNodeType set that Rell observes. Numbers carry the
// distinction Jackson preserves: an integral number kept as exact digits vs a double.
enum class NodeType {
    Null,       // JSON null
    Boolean,    // true / false
    NumberInt,  // integral number stored as exact decimal digits (Jackson BigInteger/long node)
    NumberDbl,  // number with '.'/'e'/'E' stored as a Java double (Jackson double node)
    String,     // JSON string
    Array,      // JSON array
    Object,     // JSON object (members keep parse/insertion order)
};

// A parsed JSON value. Immutable after parse(). Mirrors the relevant shape of a Jackson JsonNode.
//
// Number representation:
//   - NumberInt: `int_digits` holds the canonical decimal text (sign + digits, no leading zeros,
//     "0" for zero, "-0" normalised to "0"); `dbl` is unused.
//   - NumberDbl: `dbl` holds the IEEE-754 double; the canonical text is produced on demand by
//     Java-Double.toString-equivalent formatting.
struct Json {
    NodeType type = NodeType::Null;

    bool boolean = false;             // Boolean
    std::string str_val;              // String (decoded UTF-8 content)
    std::string int_digits;           // NumberInt canonical decimal text
    double dbl = 0.0;                 // NumberDbl

    std::vector<std::shared_ptr<Json>> array;                              // Array
    std::vector<std::pair<std::string, std::shared_ptr<Json>>> members;    // Object (ordered)

    NodeType node_type() const { return type; }
    bool is_object() const { return type == NodeType::Object; }
    bool is_array() const { return type == NodeType::Array; }
    bool is_string() const { return type == NodeType::String; }
    bool is_null() const { return type == NodeType::Null; }
    bool is_boolean() const { return type == NodeType::Boolean; }
    // Any number node (Jackson isNumber()).
    bool is_number() const { return type == NodeType::NumberInt || type == NodeType::NumberDbl; }
    // Integral number node (Jackson isIntegralNumber()): only NumberInt qualifies here.
    bool is_integral_number() const { return type == NodeType::NumberInt; }

    // size() for array/object (Jackson container size). Caller must guard non-containers.
    size_t size() const { return type == NodeType::Array ? array.size() : members.size(); }
};

using JsonPtr = std::shared_ptr<Json>;

// Parse one JSON value from `s`, byte-for-byte compatible with Rt_JsonValue.parse:
//   * blank/empty -> JsonParseError (Rell's require(!s.isBlank())).
//   * invalid JSON -> JsonParseError (Jackson JsonProcessingException).
//   * trailing tokens after a complete value are ignored (Jackson readTree leniency).
JsonPtr parse(const std::string &s);

// Canonical compact text == Jackson JsonNode.toString() == Rt_JsonValue.str. Byte-exact.
std::string to_text(const Json &node);
inline std::string to_text(const JsonPtr &node) { return to_text(*node); }

// Java Double.toString(v), exposed for testing the number-formatting core directly.
std::string java_double_to_string(double v);

// Run inline golden-vector self-tests; returns true on success. (Built into a main() under
// -DRELL_JSON_SELFTEST.)
bool self_test();

}  // namespace rell::json

#endif  // RELL_JSON_H
