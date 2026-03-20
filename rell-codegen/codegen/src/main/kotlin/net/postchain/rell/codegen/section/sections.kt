package net.postchain.rell.codegen.section

import net.postchain.rell.base.utils.doc.DocSymbol

interface Entity : DocumentSection

interface Struct : DocumentSection

interface Enumeration : DocumentSection

interface Query : DocumentSection

interface Operation : DocumentSection

interface Builtin : DocumentSection {
    override val docSymbol: DocSymbol
        get() = DocSymbol.NONE
}
