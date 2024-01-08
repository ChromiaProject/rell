package net.postchain.rell.toolbox.core.tokens

enum class RellCustomTokenChannels(
    val channel: Int
) {
    //Sets comment to hardcorded channel 2, since HIDDEN default gets value 1.
    //We also set this value in Rell.g4 file
    //Do introduce channel names properly we would need to separate parser and lexer into
    //two different g4 files:
    //https://github.com/antlr/antlr4/issues/1555
    //https://stackoverflow.com/questions/28197609/extra-channels-in-antlr-4-5
    COMMENTS(2)
}