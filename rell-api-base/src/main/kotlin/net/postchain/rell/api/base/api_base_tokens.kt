package net.postchain.rell.api.base

import net.postchain.rell.base.compiler.parser.RellTokenizer
import net.postchain.rell.base.compiler.parser.RellTokens

public object RellApiTokenizer {

    public fun isIdentifier(s: String): Boolean =
        RellTokenizer.matchToken(s, RellTokens.IDENTIFIER, false)

    public fun isInteger(s: String, allowSign: Boolean = true): Boolean =
        RellTokenizer.matchToken(s, RellTokens.INTEGER, allowSign)

    public fun isBigInteger(s: String, allowSign: Boolean = true): Boolean =
        RellTokenizer.matchToken(s, RellTokens.BIG_INTEGER, allowSign)

    public fun isDecimal(s: String, allowSign: Boolean = true): Boolean =
        RellTokenizer.matchToken(s, RellTokens.DECIMAL, allowSign)

    public fun isText(s: String): Boolean =
        RellTokenizer.matchToken(s, RellTokens.STRING, false)

    public fun isByteArray(s: String): Boolean =
        RellTokenizer.matchToken(s, RellTokens.BYTEARRAY, false)
}
