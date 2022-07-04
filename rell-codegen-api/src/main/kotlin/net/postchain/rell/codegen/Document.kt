package net.postchain.rell.codegen

interface Document : Formattable {
    val intro: String
    val packageString: String

    fun addEntity(entity: Entity)

    fun addQuery(query: Query)

    fun addTransaction(tx: Transaction)
}