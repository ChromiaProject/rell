package net.postchain.rell.toolbox.formatter

class RellFormatterException : RuntimeException {
    constructor(message: String) : super(message)
    constructor(message: String, e: Exception?) : super(message, e)
}