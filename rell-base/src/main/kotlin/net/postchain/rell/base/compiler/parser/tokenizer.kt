/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.compiler.parser

import com.github.h0tk3y.betterParse.lexer.TokenMatch
import com.github.h0tk3y.betterParse.lexer.Tokenizer
import com.github.h0tk3y.betterParse.utils.cached
import net.postchain.rell.base.compiler.ast.S_BasicPos
import net.postchain.rell.base.compiler.ast.S_Pos
import net.postchain.rell.base.compiler.base.utils.C_Error
import net.postchain.rell.base.compiler.base.utils.C_Parser
import net.postchain.rell.base.lib.type.Lib_DecimalMath
import net.postchain.rell.base.lib.type.Rt_BigIntegerValue
import net.postchain.rell.base.lib.type.Rt_DecimalValue
import net.postchain.rell.base.model.R_Name
import net.postchain.rell.base.runtime.Rt_Value
import net.postchain.rell.base.utils.CommonUtils
import java.io.InputStream
import java.math.BigInteger
import java.util.*

class RellTokenizerState {
    var lastOffset = 0
    var lastRow = 1
    var lastCol = 1
    var lastEof = false

    fun update(offset: Int, row: Int, col: Int, eof: Boolean) {
        lastOffset = offset
        lastRow = row
        lastCol = col
        lastEof = eof
    }
}

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

class RellTokenizer(tokensEx: List<RellToken>) : Tokenizer {
    val tkIdentifier: RellToken
    val tkInteger: RellToken
    val tkBigInteger: RellToken
    val tkDecimal: RellToken
    val tkString: RellToken
    val tkByteArray: RellToken

    val tkKeywords: Map<String, RellToken>
    val tkDelims: List<RellToken>

    override val tokens = tokensEx.map { it.token }

    init {
        require(tokens.isNotEmpty()) { "The tokens list should not be empty" }

        val generals = mutableMapOf<String, RellToken>()
        val keywords = mutableMapOf<String, RellToken>()
        val delims = mutableMapOf<String, RellToken>()

        for (token in tokensEx) {
            val p = token.token.pattern
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

        tkIdentifier = generals.getValue(IDENTIFIER)
        tkInteger = generals.getValue(INTEGER)
        tkBigInteger = generals.getValue(BIG_INTEGER)
        tkDecimal = generals.getValue(DECIMAL)
        tkString = generals.getValue(STRING)
        tkByteArray = generals.getValue(BYTEARRAY)

        tkKeywords = keywords.toMap()
        tkDelims = delims.values.toList().sortedBy { -it.token.pattern.length }
    }

    override fun tokenize(input: InputStream) = throw UnsupportedOperationException()
    override fun tokenize(input: Readable) = throw UnsupportedOperationException()
    override fun tokenize(input: Scanner) = throw UnsupportedOperationException()
    override fun tokenize(input: String) = tokenize(input, RellTokenizerState())

    fun tokenize(input: String, outState: RellTokenizerState) =
            Sequence { TokenizerIterator(input, outState) }.constrainOnce().cached()

    private fun scanToken(seq: CharSeq): TokenMatch? {
        scanBlank(seq)

        val k = seq.cur()
        if (k == null) {
            return null
        }

        seq.keepPos()

        if (k == '0' && seq.afterCur() == 'x') {
            val tk = scanHexLiteral(seq)
            return tk
        } else if (isDigit(k) || k == '.' && isDigit(seq.afterCur())) {
            val tk = scanNumericLiteral(seq)
            return tk
        } else if (k == 'x' && (seq.afterCur() == '\'' || seq.afterCur() == '"')) {
            val s = scanByteArrayLiteral(seq)
            val pos = seq.startPos()
            val tk = seq.tokenMatch(tkByteArray, s)
            decodeByteArray(pos, tk.text) // Fail early - will throw an exception if the token is invalid.
            return tk
        } else if (R_Name.isNameStart(k)) {
            scanWhileTrue(seq) { R_Name.isNamePart(it) }
            val s = seq.text(0, 0)
            val tk = tkKeywords.getOrDefault(s, tkIdentifier)
            return seq.tokenMatch(tk, s)
        } else if (isDelim(k)) {
            scanWhileTrue(seq, Companion::isDelim)
            val s = seq.text(0, 0)
            return scanDelimiter(seq, s)
        } else if (k == '\'' || k == '"') {
            val s = scanStringLiteral(seq)
            return seq.tokenMatch(tkString, s)
        } else {
            throw seq.err("lex:token", "Syntax error")
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
                } else if (k2 == '*') {
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
    }

    private fun scanHexLiteral(seq: CharSeq): TokenMatch {
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

    private fun scanNumericLiteral(seq: CharSeq): TokenMatch {
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

    private fun makeInteger(seq: CharSeq): TokenMatch {
        val pos = seq.startPos()
        val s = seq.text(0, 0)
        val tk = seq.tokenMatch(tkInteger, s)
        decodeInteger(pos, tk.text) // Fail early - will throw an exception if the number is invalid.
        return tk
    }

    private fun makeBigInteger(seq: CharSeq): TokenMatch {
        val pos = seq.startPos()
        val s = seq.text(0, 0)
        val tk = seq.tokenMatch(tkBigInteger, s)
        decodeBigInteger(pos, tk.text) // Fail early - will throw an exception if the number is invalid.
        return tk
    }

    private fun makeDecimal(seq: CharSeq): TokenMatch {
        val pos = seq.startPos()
        val s = seq.text(0, 0)
        val tk = seq.tokenMatch(tkDecimal, s)
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

    private fun scanDelimiter(seq: CharSeq, s: String): TokenMatch {
        for (tkEx in tkDelims) {
            val tk = tkEx.token
            if (s.startsWith(tk.pattern)) {
                seq.back(s.length - tk.pattern.length)
                return seq.tokenMatch(tkEx, tk.pattern)
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

    private inner class TokenizerIterator(input: String, private val outState: RellTokenizerState): Iterator<TokenMatch> {
        private val charSeq = CharSeq(input)
        private var token: TokenMatch? = null
        private var eof = false

        override fun hasNext(): Boolean {
            val t = fetchToken()
            return t != null
        }

        override fun next(): TokenMatch {
            val t = fetchToken()
            t ?: throw NoSuchElementException()
            token = null
            return t
        }

        private fun fetchToken(): TokenMatch? {
            var t = token
            if (t != null || eof) return t

            t = scanToken(charSeq)
            if (t == null) {
                eof = true
                outState.update(charSeq.pos(), charSeq.row(), charSeq.col(), true)
                return null
            }

            token = t
            outState.update(t.position, t.row, t.column, false)

            return t
        }
    }

    companion object {
        const val IDENTIFIER = "<IDENTIFIER>"
        const val INTEGER = "<INTEGER>"
        const val BIG_INTEGER = "<BIGINTEGER>"
        const val DECIMAL = "<DECIMAL>"
        const val STRING = "<STRING>"
        const val BYTEARRAY = "<BYTEARRAY>"

        private const val MAX_BIG_INTEGER_LITERAL_LENGTH = 1000
        private const val MAX_DECIMAL_LITERAL_LENGTH = 1000

        private val BIG_MIN_INTEGER = BigInteger.valueOf(Long.MIN_VALUE)
        private val BIG_MAX_INTEGER = BigInteger.valueOf(Long.MAX_VALUE)

        private fun isGeneralToken(s: String) = s.matches(Regex("<[A-Z0-9_]+>"))

        private fun isDigit(c: Char?) = c != null && c >= '0' && c <= '9'
        private fun isHexDigit(c: Char) = isDigit(c) || c >= 'A' && c <= 'F' || c >= 'a' && c <= 'f'
        private fun isDelim(c: Char) = "~!@#$%^&*()-=+[]{}|;:,.<>/?".contains(c)

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

        fun decodeString(pos: S_Pos, s: String): String {
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
    }
}

private class CharSeq(private val str: String) {
    private val tabSize = 4

    private var len = str.length
    private var pos = 0
    private var row = 1
    private var col = 1
    private var cur: Char? = null

    private var startPos = 0
    private var startRow = 1
    private var startCol = 1

    val buffer = StringBuilder()

    init {
        update()
    }

    private fun update() {
        cur = if (pos >= len) null else str[pos]
    }

    fun cur() = cur
    fun afterCur() = if (pos >= len - 1) null else str[pos + 1]
    fun startPos() = S_BasicPos(C_Parser.currentFile(), startPos, startRow, startCol)
    fun pos() = pos
    fun row() = row
    fun col() = col

    fun keepPos() {
        startPos = pos
        startRow = row
        startCol = col
    }

    fun textPos() = S_BasicPos(C_Parser.currentFile(), pos, row, col)
    fun text(startSkip: Int, endSkip: Int) = str.substring(startPos + startSkip, pos - endSkip)

    fun tokenMatch(token: RellToken, text: String) = TokenMatch(token.token, text, startPos, startRow, startCol)

    fun err(code: String, msg: String, eof: Boolean = false) = err(textPos(), code, msg, eof)
    fun err(pos: S_Pos, code: String, msg: String, eof: Boolean = false) = RellTokenizerScanException(pos, code, msg, eof)

    fun next() {
        if (pos < len) {
            val k = str[pos]
            if (k == '\n') {
                ++row
                col = 1
            } else if (k == '\t') {
                val step = tabSize - (col - 1) % tabSize
                col += step
            } else {
                ++col
            }
            pos++
            update()
        }
    }

    fun back(n: Int) {
        // Assuming we never go back to a previous line, only within the same line.
        require(n <= pos)
        require(n < col)
        pos -= n
        col -= n
        update()
    }
}
