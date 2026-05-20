/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.parser

import com.github.h0tk3y.betterParse.lexer.Token
import com.github.h0tk3y.betterParse.lexer.TokenMatch
import net.postchain.rell.base.compiler.ast.S_BasicPos
import net.postchain.rell.base.compiler.ast.S_Comment
import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.utils.C_Error
import net.postchain.rell.base.compiler.base.utils.C_ParserFilePath
import net.postchain.rell.base.compiler.base.utils.C_SourcePath
import net.postchain.rell.base.compiler.base.utils.IdeSourcePathFilePath
import net.postchain.rell.base.model.Name
import net.postchain.rell.base.model.R_LangVersion
import net.postchain.rell.base.model.rr.RR_ConstantValue
import net.postchain.rell.base.utils.*
import java.math.BigInteger
import java.util.*
import java.util.concurrent.ConcurrentHashMap

sealed class RellTokenizerException(
    @JvmField val pos: S_Pos,
    @JvmField val code: String,
    @JvmField val msg: String,
    @JvmField val eof: Boolean,
): RuntimeException(msg) {
    fun toCError(): C_Error = C_Error.other(pos, code, msg)
}

class RellTokenizerScanException(pos: S_Pos, code: String, msg: String, eof: Boolean = false): RellTokenizerException(pos, code, msg, eof)

// Referenced in Eclipse.
class RellTokenizerDecodingException(pos: S_Pos, code: String, msg: String): RellTokenizerException(pos, code, msg, false)

class RellTokens private constructor(version: R_LangVersion) {
    val all: ImmList<RellToken> = S_Grammar.rellTokens
    val keywords: ImmList<RellToken>
    val delims: ImmList<RellToken>

    val identifier: RellToken
    val integer: RellToken
    val bigInteger: RellToken
    val decimal: RellToken
    val string: RellToken
    val byteArray: RellToken

    init {
        val enabled = all.filterToImmList { it.isEnabled(version) }
        require(enabled.isNotEmpty()) { "The tokens list should not be empty" }

        val generals = mutableMapOf<String, RellToken>()
        val keywords = mutableMapOf<String, RellToken>()
        val delims = mutableMapOf<String, RellToken>()

        for (token in enabled) {
            val p = token.pattern
            if (isGeneralToken(p)) {
                require(p !in generals) { "Duplicate token: '$p'" }
                generals[p] = token
            } else if (p.matches(Regex("[A-Za-z_][A-Za-z_0-9]*"))) {
                require(p !in keywords) { "Duplicate keyword: '$p'" }
                keywords[p] = token
            } else if (p.isNotEmpty() && p.find { !isDelim(it) } == null) {
                require(p !in delims) { "Duplicate token: '$p'" }
                delims[p] = token
            } else {
                throw IllegalArgumentException("Invalid token: '$p'")
            }
        }

        identifier = generals.getValue(IDENTIFIER)
        integer = generals.getValue(INTEGER)
        bigInteger = generals.getValue(BIG_INTEGER)
        decimal = generals.getValue(DECIMAL)
        string = generals.getValue(STRING)
        byteArray = generals.getValue(BYTEARRAY)

        this.keywords = keywords.values.toImmList()
        this.delims = delims.values.sortedBy { -it.pattern.length }.toImmList()
    }

    companion object {
        const val IDENTIFIER = "<IDENTIFIER>"
        const val INTEGER = "<INTEGER>"
        const val BIG_INTEGER = "<BIGINTEGER>"
        const val DECIMAL = "<DECIMAL>"
        const val STRING = "<STRING>"
        const val BYTEARRAY = "<BYTEARRAY>"

        val DEFAULT: RellTokens = RellTokens(RellVersions.VERSION)

        fun get(version: R_LangVersion): RellTokens {
            return if (version == RellVersions.VERSION) DEFAULT else RellTokens(version)
        }

        private fun isGeneralToken(s: String) = s.matches(Regex("<[A-Z0-9_]+>"))
    }
}

private class RellTokenizerSetup private constructor(version: R_LangVersion) {
    val tokens = RellTokens.get(version)
    val tokenSet = tokens.all.map { it.token }.toImmSet()

    val keywords = tokens.keywords.associateByToImmMap { it.pattern }

    val maxDelimLen: Int = tokens.delims.maxOf { it.pattern.length }

    companion object {
        private val versionMap = ConcurrentHashMap<R_LangVersion, RellTokenizerSetup>()

        fun get(version: R_LangVersion): RellTokenizerSetup {
            var res = versionMap[version]
            if (res == null) {
                // On race condition (unlikely), will build a redundant instance and discard it - that's fine.
                val tokens = RellTokenizerSetup(version)
                res = versionMap.putIfAbsent(version, tokens) ?: tokens
            }
            return res
        }
    }
}

class RellTokenizer(version: R_LangVersion = RellVersions.VERSION) {
    private val setup = RellTokenizerSetup.get(version)
    private val tokens = setup.tokens

    fun tokenProducer(filePath: C_ParserFilePath, input: String): RellTokenProducer {
        return RellTokenProducerImpl(filePath, input)
    }

    private fun scanToken(seq: CharSeq, skipLeadingBlank: Boolean = true): TokenRec? {
        seq.setComment(null)
        if (skipLeadingBlank) {
            scanBlank(seq)
        }

        val k = seq.cur() ?: return null

        seq.keepPos()

        return when {
            k == '0' && seq.afterCur() == 'x' -> {
                scanHexLiteral(seq)
            }
            isDigit(k) || k == '.' && isDigit(seq.afterCur()) -> {
                scanNumericLiteral(seq)
            }
            k == 'x' && (seq.afterCur() == '\'' || seq.afterCur() == '"') -> {
                val s = scanByteArrayLiteral(seq)
                val pos = seq.startPos()
                val tk = seq.tokenRec(tokens.byteArray, s)
                decodeByteArray(pos, tk.text) // Fail early - will throw an exception if the token is invalid.
                tk
            }
            Name.isNameStart(k) -> {
                scanWhileTrue(seq) { Name.isNamePart(it) }
                val s = seq.text(0, 0)
                val tk = setup.keywords.getOrDefault(s, tokens.identifier)
                seq.tokenRec(tk, s)
            }
            isDelim(k) -> {
                val s = seq.lookahead(setup.maxDelimLen)
                scanDelimiter(seq, s)
            }
            k == '\'' || k == '"' -> {
                val s = scanStringLiteral(seq)
                seq.tokenRec(tokens.string, s)
            }
            else -> throw seq.err("lex:token", "Syntax error")
        }
    }

    private fun scanBlank(seq: CharSeq) {
        while (true) {
            val k = seq.cur()
            if (k == null) {
                break
            } else if (Character.isWhitespace(k)) {
                seq.next()
            } else if (k == '/') {
                val k2 = seq.afterCur()
                if (k2 == '/') {
                    seq.next()
                    seq.next()
                    scanSingleLineComment(seq)
                    seq.setComment(null)
                } else if (k2 == '*') {
                    seq.keepPos()
                    seq.next()
                    seq.next()
                    scanMultiLineComment(seq)
                } else {
                    break
                }
            } else {
                break
            }
        }
    }

    private fun scanSingleLineComment(seq: CharSeq) {
        scanWhileTrue(seq) { it != '\n' }
        seq.next()
    }

    private fun scanMultiLineComment(seq: CharSeq) {
        val isDoc = seq.cur() == '*' && seq.afterCur() != '/'
        if (isDoc) {
            seq.next()
        }

        while (true) {
            val k = seq.cur()
            seq.next()
            if (k == null) {
                throw seq.err("lex:comment_eof", "Unclosed multiline comment", true)
            } else if (k == '*' && seq.cur() == '/') {
                seq.next()
                break
            }
        }

        val comment = if (!isDoc) null else {
            val pos = seq.startPos()
            val text = seq.text(0, 0)
            S_Comment(pos, text)
        }

        seq.setComment(comment)
    }

    private fun scanHexLiteral(seq: CharSeq): TokenRec {
        seq.next()
        seq.next()
        scanWhileTrue(seq) { isHexDigit(it) }

        val bigInteger = seq.cur() == 'L'
        if (bigInteger) seq.next()

        scanNumberEnd(seq)

        return when {
            bigInteger -> makeBigInteger(seq)
            else -> makeInteger(seq)
        }
    }

    private fun scanNumericLiteral(seq: CharSeq): TokenRec {
        scanWhileTrue(seq) { isDigit(it) }

        var decimal = false
        var bigInteger = false

        var k = seq.cur()

        if (k == '.') {
            seq.next()
            if (!isDigit(seq.cur())) {
                throw seq.err("lex:number:no_digit_after_point", "Invalid number: no digit after decimal point")
            }
            scanWhileTrue(seq) { isDigit(it) }
            decimal = true
            k = seq.cur()
        }

        if (k == 'E' || k == 'e') {
            seq.next()
            k = seq.cur()
            if (k == '+' || k == '-') {
                seq.next()
                k = seq.cur()
            }
            if (!isDigit(k)) {
                throw seq.err("lex:number:no_digit_after_exp", "Invalid number: no digit after E")
            }
            scanWhileTrue(seq) { isDigit(it) }
            decimal = true
        }

        if (k == 'L') {
            seq.next()
            if (decimal) {
                throw seq.err("lex:number:decimal_bigint", "Invalid numeric literal")
            }
            bigInteger = true
        }

        scanNumberEnd(seq)

        return when {
            decimal -> makeDecimal(seq)
            bigInteger -> makeBigInteger(seq)
            else -> makeInteger(seq)
        }
    }

    private fun scanNumberEnd(seq: CharSeq) {
        val r = seq.cur()
        if (r != null && Name.isNamePart(r)) {
            throw seq.err("lex:number_end", "Invalid numeric literal")
        }
    }

    private fun makeInteger(seq: CharSeq): TokenRec {
        val pos = seq.startPos()
        val s = seq.text(0, 0)
        val tk = seq.tokenRec(tokens.integer, s)
        decodeInteger(pos, tk.text) // Fail early - will throw an exception if the number is invalid.
        return tk
    }

    private fun makeBigInteger(seq: CharSeq): TokenRec {
        val pos = seq.startPos()
        val s = seq.text(0, 0)
        val tk = seq.tokenRec(tokens.bigInteger, s)
        decodeBigInteger(pos, tk.text) // Fail early - will throw an exception if the number is invalid.
        return tk
    }

    private fun makeDecimal(seq: CharSeq): TokenRec {
        val pos = seq.startPos()
        val s = seq.text(0, 0)
        val tk = seq.tokenRec(tokens.decimal, s)
        decodeDecimal(pos, tk.text) // Fail early - will throw an exception if the number is invalid.
        return tk
    }

    private fun scanStringLiteral(seq: CharSeq): String {
        val q = seq.cur()
        seq.next()

        val buf = seq.buffer
        buf.setLength(0)

        while (true) {
            val k = seq.cur()
            if (k == q) {
                seq.next()
                break
            } else if (k == null || k == '\n') {
                throw seq.err("lex:string_unclosed", "Unclosed string literal", false) // Cannot recover with more input
            } else if (k == '\\') {
                val c = scanEscapeSeq(seq)
                buf.append(c)
            } else {
                buf.append(k)
                seq.next()
            }
        }

        return buf.toString()
    }

    private fun scanEscapeSeq(seq: CharSeq): Char {
        val x = seq.afterCur()
        if (x == 'u') {
            return scanUnicodeEscapeSeq(seq)
        } else {
            val c = when (x) {
                'b' -> '\b'
                't' -> '\t'
                'r' -> '\r'
                'n' -> '\n'
                '"' -> '"'
                '\'' -> '\''
                '\\' -> '\\'
                else -> throw seq.err("lex:string_esc", "Invalid escape sequence")
            }
            seq.next()
            seq.next()
            return c
        }
    }

    private fun scanUnicodeEscapeSeq(seq: CharSeq): Char {
        val pos = seq.textPos()
        seq.next()
        seq.next()

        var code = 0

        repeat(4) { _ ->
            val c = seq.cur()
            val k = if (c == null) -1 else Character.digit(c, 16)
            if (k < 0) {
                throw seq.err(pos, "lex:string_esc_unicode", "Invalid UNICODE escape sequence")
            }
            code = code * 16 + k
            seq.next()
        }

        return code.toChar()
    }

    private fun scanByteArrayLiteral(seq: CharSeq): String {
        seq.next()
        val q = seq.cur()
        seq.next()

        val buf = seq.buffer
        buf.setLength(0)

        while (true) {
            val c = seq.cur()
            if (c == q) {
                seq.next()
                break
            } else if (c == null || c == '\n') {
                throw seq.err("lex:bytearray_unclosed", "Unclosed byte array literal", false) // Cannot recover with more input
            }
            seq.next()
            buf.append(c)
        }

        return buf.toString()
    }

    private fun scanDelimiter(seq: CharSeq, s: String): TokenRec {
        for (tk in tokens.delims) {
            if (s.startsWith(tk.pattern)) {
                tk.pattern.forEach { _ ->
                    seq.next()
                }
                return seq.tokenRec(tk, tk.pattern)
            }
        }
        throw seq.err("lex:delim:$s", "Syntax error")
    }

    private fun scanWhileTrue(seq: CharSeq, predicate: (Char) -> Boolean) {
        while (true) {
            val k = seq.cur()
            if (k == null || !predicate(k)) break
            seq.next()
        }
    }

    private inner class RellTokenProducerImpl(
        filePath: C_ParserFilePath,
        input: String,
    ): RellTokenProducer {
        private val charSeq = CharSeq(filePath, input)
        private var lastPos: S_Pos = S_BasicPos(filePath, 0, 1, 1)
        private var tokenIndex = 0
        private var end = false

        override fun getEndPos() = lastPos

        override fun nextToken(): TokenMatch? {
            if (end) {
                return null
            }

            val rec = scanToken(charSeq)
            lastPos = rec?.pos ?: charSeq.textPos()
            if (rec == null) {
                end = true
                return null
            }

            val match = rec.tokenMatch(tokenIndex, setup.tokenSet)
            ++tokenIndex
            return match
        }
    }

    companion object {
        private const val MAX_BIG_INTEGER_LITERAL_LENGTH = 1000
        private const val MAX_DECIMAL_LITERAL_LENGTH = 1000

        private val BIG_MIN_INTEGER = BigInteger.valueOf(Long.MIN_VALUE)
        private val BIG_MAX_INTEGER = BigInteger.valueOf(Long.MAX_VALUE)

        private val DUMMY_FILE_PATH = C_ParserFilePath(C_SourcePath.EMPTY, IdeSourcePathFilePath(C_SourcePath.EMPTY))
        private val SIGNS = immListOf("+", "-")

        fun decodeName(pos: S_Pos, s: String): Name {
            val rName = Name.ofOpt(s)
            return rName ?: throw RellTokenizerDecodingException(pos, "lex:name:invalid:$s", "Invalid name: '$s'")
        }

        fun decodeInteger(pos: S_Pos, s: String): Long {
            var radix = 10
            var p = s

            if (s.startsWith("0x")) {
                radix = 16
                p = s.substring(2)
            }

            return try {
                p.toLong(radix)
            } catch (_: NumberFormatException) {
                val big = try {
                    BigInteger(p, radix)
                } catch (_: NumberFormatException) {
                    null
                }

                if (big != null && big !in BIG_MIN_INTEGER..BIG_MAX_INTEGER) {
                    throw RellTokenizerDecodingException(pos, "lex:int:range:$s", "Integer literal out of range: $s")
                } else {
                    throw RellTokenizerDecodingException(pos, "lex:int:invalid:$s", "Invalid integer literal: '$s'")
                }
            }
        }

        fun decodeBigInteger(pos: S_Pos, s: String): RR_ConstantValue {
            if (!s.endsWith("L")) {
                throw RellTokenizerDecodingException(pos, "lex:bigint:nosuffix", "Invalid big integer literal: no suffix")
            }

            val len = s.length
            if (len > MAX_BIG_INTEGER_LITERAL_LENGTH) {
                throw RellTokenizerDecodingException(pos, "lex:bigint:length:$len",
                    "Big integer literal is too long: $len (max: $MAX_BIG_INTEGER_LITERAL_LENGTH)")
            }

            val radix: Int
            val p: String

            if (s.startsWith("0x")) {
                radix = 16
                p = s.substring(2, s.length - 1)
            } else {
                radix = 10
                p = s.substring(0, s.length - 1)
            }

            val bi = try {
                BigInteger(p, radix)
            } catch (_: NumberFormatException) {
                throw RellTokenizerDecodingException(pos, "lex:bigint:invalid:$s", "Invalid big integer literal: '$s'")
            }

            if (bi !in BIG_INTEGER_MIN..BIG_INTEGER_MAX) {
                throw RellTokenizerDecodingException(pos, "lex:bigint:range:$s", "Big integer literal value out of range")
            }

            return RR_ConstantValue.BigInteger(bi.toString())
        }

        fun decodeDecimal(pos: S_Pos, s: String): RR_ConstantValue {
            val len = s.length
            if (len > MAX_DECIMAL_LITERAL_LENGTH) {
                throw RellTokenizerDecodingException(pos, "lex:decimal:length:$len",
                        "Decimal literal is too long: $len (max: $MAX_DECIMAL_LITERAL_LENGTH)")
            }

            val bd = try {
                parseDecimalLiteral(s)
            } catch (_: NumberFormatException) {
                throw RellTokenizerDecodingException(pos, "lex:decimal:invalid:$s", "Invalid decimal literal: '$s'")
            }

            val scaled = scaleDecimal(bd) ?: throw RellTokenizerDecodingException(
                pos,
                "lex:decimal:range:$s",
                "Decimal literal value out of range"
            )

            return RR_ConstantValue.Decimal(scaled.toString())
        }

        fun decodeString(@Suppress("UNUSED_PARAMETER") pos: S_Pos, s: String): String {
            return s
        }

        fun decodeByteArray(pos: S_Pos, s: String): ByteArray {
            try {
                return CommonUtils.hexToBytes(s)
            } catch (_: IllegalArgumentException) {
                val maxlen = 64
                val p = if (s.length <= maxlen) s else (s.substring(0, maxlen) + "...")
                throw RellTokenizerDecodingException(pos, "lex:bad_hex:$p", "Invalid byte array literal: '$p'")
            }
        }

        fun linePosMap(text: String, startPos: S_Pos): NavigableMap<Int, S_Pos> {
            val rowColTracker = RowColTracker(startPos.line(), startPos.column())

            val res = mutableMapOf<Int, S_Pos>()
            res[0] = startPos

            for (i in text.indices) {
                rowColTracker.update(text[i])
                if (rowColTracker.col == 1) {
                    val index = i + 1
                    val offset = startPos.offset() + index
                    val pos = S_BasicPos(startPos.path(), startPos.idePath(), offset, rowColTracker.row, rowColTracker.col)
                    res[index] = pos
                }
            }

            return TreeMap(res)
        }

        fun matchToken(
            s: String,
            expectedPattern: String,
            allowSign: Boolean,
        ): Boolean {
            val tokenizer = RellTokenizer()
            val charSeq = CharSeq(DUMMY_FILE_PATH, s)
            return try {
                var tokenRec = tokenizer.scanToken(charSeq, false)
                tokenRec ?: return false
                if (allowSign && tokenRec.token.pattern in SIGNS) {
                    tokenRec = tokenizer.scanToken(charSeq, false)
                }
                val nextTokenRec = tokenizer.scanToken(charSeq, false)
                tokenRec != null &&                                 // got a valid token
                    tokenRec.token.pattern == expectedPattern &&    // token is the expected type
                    nextTokenRec == null                            // all input was consumed
            } catch (_: RellTokenizerException) {
                false
            }
        }
    }
}

private class RowColTracker(row: Int = 1, col: Int = 1) {
    init {
        require(row >= 1)
        require(col >= 1)
    }

    private val tabSize = 4

    private var row0 = row
    private var col0 = col

    val row: Int get() = row0
    val col: Int get() = col0

    fun update(k: Char) {
        when (k) {
            '\n' -> {
                ++row0
                col0 = 1
            }
            '\t' -> {
                val step = tabSize - (col0 - 1) % tabSize
                col0 += step
            }
            else -> {
                ++col0
            }
        }
    }
}

private class CharSeq(
    private val filePath: C_ParserFilePath,
    private val str: String,
) {
    private var len = str.length
    private var cur: Char? = null
    private var pos = 0
    private val rowColTracker = RowColTracker()

    private var startPos = 0
    private var startRow = 1
    private var startCol = 1

    private var comment: S_Comment? = null

    val buffer = StringBuilder()

    init {
        update()
    }

    private fun update() {
        cur = if (pos >= len) null else str[pos]
    }

    fun cur() = cur
    fun afterCur() = if (pos >= len - 1) null else str[pos + 1]
    fun startPos() = S_BasicPos(filePath, startPos, startRow, startCol)

    fun keepPos() {
        startPos = pos
        startRow = rowColTracker.row
        startCol = rowColTracker.col
    }

    fun textPos() = S_BasicPos(filePath, pos, rowColTracker.row, rowColTracker.col)
    fun text(startSkip: Int, endSkip: Int) = str.substring(startPos + startSkip, pos - endSkip)

    fun setComment(comment: S_Comment?) {
        this.comment = comment
    }

    fun tokenRec(token: RellToken, text: String): TokenRec {
        val sPos = S_BasicPos(filePath, startPos, startRow, startCol)
        return TokenRec(sPos, token, str, text, startPos, pos - startPos, startRow, startCol, comment)
    }

    fun err(code: String, msg: String, eof: Boolean = false) = err(textPos(), code, msg, eof)
    fun err(pos: S_Pos, code: String, msg: String, eof: Boolean = false) = RellTokenizerScanException(pos, code, msg, eof)

    fun next() {
        if (pos < len) {
            val k = str[pos]
            rowColTracker.update(k)
            pos += 1
            update()
        }
    }

    fun lookahead(n: Int): String {
        val end = (pos + n).coerceAtMost(len)
        return str.substring(pos, end)
    }
}

private class TokenRec(
    val pos: S_Pos,
    val token: RellToken,
    val input: String,
    val text: String,
    val offset: Int,
    val length: Int,
    val row: Int,
    val col: Int,
    val comment: S_Comment?,
) {
    fun tokenMatch(index: Int, validTokens: ImmSet<Token>): TokenMatch {
        val rellMatch = RellTokenMatch(pos, text, comment)
        val rellInput = RellTokenInput(input, token, rellMatch, validTokens)
        return TokenMatch(token.token, index, rellInput, offset, length, row, col)
    }
}

private fun isDelim(c: Char) = "~!@#$%^&*()-=+[]{}|;:,.<>/?".contains(c)

private fun isDigit(c: Char?) = c != null && c >= '0' && c <= '9'

// Kotlin complains to replace char comparisons with range checks. Tested, with range checks it's a bit slower
// (up to 10-15%). Negligible, but keeping the old code, as it has more obvious performance. Range checks rely
// on some implicit optimizations (Kotlin compiler or JVM), apparently.
@Suppress("ConvertTwoComparisonsToRangeCheck")
private fun isHexDigit(c: Char) = isDigit(c) || c >= 'A' && c <= 'F' || c >= 'a' && c <= 'f'

// Big integer range (matches Lib_BigIntegerMath: precision = 131072, max = 10^131072 - 1).
private val BIG_INTEGER_MAX: BigInteger = BigInteger.TEN.pow(131072).subtract(BigInteger.ONE)
private val BIG_INTEGER_MIN: BigInteger = -BIG_INTEGER_MAX

private const val DECIMAL_INT_DIGITS = 131072
private const val DECIMAL_FRAC_DIGITS = 20

// Decimal literal parsing (matches Lib_DecimalMath.parse: normalize leading dot, strip trailing zeros from string).
private fun parseDecimalLiteral(s: String): java.math.BigDecimal {
    val t = when {
        s.startsWith(".") -> "0$s"
        s.startsWith("+.") -> "0${s.substring(1)}"
        s.startsWith("-.") -> "-0${s.substring(1)}"
        else -> s
    }
    return java.math.BigDecimal(removeTrailingZerosStr(t))
}

private fun removeTrailingZerosStr(s: String): String {
    val n = s.length
    var i = 0
    if (i < n && (s[i] == '-' || s[i] == '+')) ++i
    var fracStart = n
    var fracEnd = n
    while (i < n) {
        val c = s[i]
        if (c == '.') {
            fracStart = i
            ++i
            while (i < n && s[i] in '0'..'9') ++i
            fracEnd = i
            break
        }
        ++i
    }
    if (fracStart == n) return s
    var j = fracEnd
    while (j > fracStart && s[j - 1] == '0') --j
    if (j > fracStart && s[j - 1] == '.') --j
    return if (j == fracEnd) s
    else if (fracEnd == s.length) s.substring(0, j)
    else s.substring(0, j) + s.substring(fracEnd)
}

// Decimal range validation (matches Lib_DecimalMath.scale).
private fun scaleDecimal(v: java.math.BigDecimal): java.math.BigDecimal? {
    if (v.signum() == 0) return java.math.BigDecimal.ZERO
    val scale = v.scale()
    val intDigits = v.precision() - scale
    if (intDigits > DECIMAL_INT_DIGITS) return null
    if (intDigits < -DECIMAL_FRAC_DIGITS) return java.math.BigDecimal.ZERO
    return if (scale <= DECIMAL_FRAC_DIGITS) v
    else {
        val v2 = v.setScale(DECIMAL_FRAC_DIGITS, java.math.RoundingMode.HALF_UP)
        val intDigits2 = v2.precision() - v2.scale()
        if (intDigits2 > DECIMAL_INT_DIGITS) null else v2
    }
}
