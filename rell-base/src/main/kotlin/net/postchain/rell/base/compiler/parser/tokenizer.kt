/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.parser

import com.github.h0tk3y.betterParse.lexer.Token
import com.github.h0tk3y.betterParse.lexer.TokenMatch
import com.google.common.collect.ImmutableSortedMap
import net.postchain.rell.base.compiler.ast.S_BasicPos
import net.postchain.rell.base.compiler.ast.S_Comment
import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.utils.C_Error
import net.postchain.rell.base.compiler.base.utils.C_ParserFilePath
import net.postchain.rell.base.lib.type.Lib_DecimalMath
import net.postchain.rell.base.lib.type.Rt_BigIntegerValue
import net.postchain.rell.base.lib.type.Rt_DecimalValue
import net.postchain.rell.base.model.R_LangVersion
import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.runtime.Rt_Value
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
            } else if (p.isNotEmpty() && p.firstOrNull { !isDelim(it) } == null) {
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

    private fun scanToken(seq: CharSeq): TokenRec? {
        seq.setComment(null)
        scanBlank(seq)

        val k = seq.cur()
        if (k == null) {
            return null
        }

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
            R_Name.isNameStart(k) -> {
                scanWhileTrue(seq) { R_Name.isNamePart(it) }
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
        if (r != null && R_Name.isNamePart(r)) {
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

        for (i in 0..3) {
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
                for (c in tk.pattern) {
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

        fun decodeName(pos: S_Pos, s: String): R_Name {
            val rName = R_Name.ofOpt(s)
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
                java.lang.Long.parseLong(p, radix)
            } catch (e: NumberFormatException) {
                val big = try {
                    BigInteger(p, radix)
                } catch (e2: NumberFormatException) {
                    null
                }

                if (big != null && (big < BIG_MIN_INTEGER || big > BIG_MAX_INTEGER)) {
                    throw RellTokenizerDecodingException(pos, "lex:int:range:$s", "Integer literal out of range: $s")
                } else {
                    throw RellTokenizerDecodingException(pos, "lex:int:invalid:$s", "Invalid integer literal: '$s'")
                }
            }
        }

        fun decodeBigInteger(pos: S_Pos, s: String): Rt_Value {
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
            } catch (e: NumberFormatException) {
                throw RellTokenizerDecodingException(pos, "lex:bigint:invalid:$s", "Invalid big integer literal: '$s'")
            }

            val v = Rt_BigIntegerValue.getTry(bi)
            v ?: throw RellTokenizerDecodingException(pos, "lex:bigint:range:$s", "Big integer literal value out of range")

            return v
        }

        fun decodeDecimal(pos: S_Pos, s: String): Rt_Value {
            val len = s.length
            if (len > MAX_DECIMAL_LITERAL_LENGTH) {
                throw RellTokenizerDecodingException(pos, "lex:decimal:length:$len",
                        "Decimal literal is too long: $len (max: $MAX_DECIMAL_LITERAL_LENGTH)")
            }

            val bd = try {
                Lib_DecimalMath.parse(s)
            } catch (e: NumberFormatException) {
                throw RellTokenizerDecodingException(pos, "lex:decimal:invalid:$s", "Invalid decimal literal: '$s'")
            }

            val v = Rt_DecimalValue.getTry(bd)
            if (v == null) {
                throw RellTokenizerDecodingException(pos, "lex:decimal:range:$s", "Decimal literal value out of range")
            }

            return v
        }

        fun decodeString(@Suppress("UNUSED_PARAMETER") pos: S_Pos, s: String): String {
            return s
        }

        fun decodeByteArray(pos: S_Pos, s: String): ByteArray {
            try {
                return CommonUtils.hexToBytes(s)
            } catch (e: IllegalArgumentException) {
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

            return ImmutableSortedMap.copyOf(res)
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
