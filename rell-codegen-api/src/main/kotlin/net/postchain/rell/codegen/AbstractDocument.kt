package net.postchain.rell.codegen

abstract class AbstractDocument(override val intro: String = "",
                                override val packageString: String) : Document {

    val imports = mutableSetOf<String>()
    val entities = mutableSetOf<Entity>()
    val queries = mutableSetOf<Query>()
    val transactions = mutableSetOf<Transaction>()

    override fun format(): String {
        return """
            |$intro
            |$packageString
            |
            |${imports.joinToString("\n")}
            |
            |${entities.joinToString("\n") { it.format() }}
            |${queries.joinToString("\n") { it.format() }}
            |${transactions.joinToString("\n") { it.format() }}
        """.trimMargin()
    }

    override fun addEntity(entity: Entity) {
        imports.addAll(entity.imports)
        entities.add(entity)
    }

    override fun addQuery(query: Query) {
        queries.add(query)
    }

    override fun addTransaction(tx: Transaction) {
        transactions.add(tx)
    }
}