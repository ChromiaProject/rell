package net.postchain.rell.codegen.section

interface Entity : DocumentSection {
    val name: String
}

interface Struct : DocumentSection

interface Enumeration : DocumentSection

interface Query : DocumentSection

interface Operation : DocumentSection

