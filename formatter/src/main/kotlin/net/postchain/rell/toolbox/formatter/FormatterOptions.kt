package net.postchain.rell.toolbox.formatter


class FormatterOptions {
    var maxLineWidth: Int = 120
    var insertSpaces = false
    var tabSize = 4
    var newLineString = NewLineStyle.LF.newLineString
}

enum class NewLineStyle(val newLineString: String) {
    LF("\n"),
    CRLF("\r\n"),
}

