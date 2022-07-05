package net.postchain.rell.codegen

interface Entity : DocumentSection {
    val name: String
}

interface Struct : DocumentSection

interface Enumeration : DocumentSection

interface Transaction : DocumentSection

interface Query : DocumentSection
