package net.postchain.rell.toolbox.core.indexer

import net.postchain.rell.toolbox.core.parser.RellLexer
import net.postchain.rell.toolbox.core.parser.RellParser
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.io.File
import java.net.URI

//data class WorkspaceIndex(val uri: URI, val resource: Resource)
data class Resource(val parseTree: RellParser.RuleX_RootParserContext)
class WorkspaceIndexer() {
    var indexMap: HashMap<URI, Resource> = HashMap()

    fun fullIndexing(rootURI: URI) {
        //1. Use root path to of workspace read uri to all rell files
        val uris = scan(rootURI)


        //2. Create resource from each .rell files
        //3. Add to indexMap
    }

    fun buildFileResources(uris: MutableList<URI>) {
        uris.forEach { uri ->
            val input: CharStream = CharStreams.fromString(source)
            val lexer = RellLexer(input)
            val tokens = CommonTokenStream(lexer)
            val parser = RellParser(tokens)
        }
    }

    private fun readFile(uri: URI): String {

    }

    fun scan(uri: URI): List<URI> {
        val uris: MutableList<URI> = ArrayList()
        val file = File(uri)
        scanRec(file, uris)
        return uris.toList()
    }

    fun scanRec(file: File, uris: MutableList<URI>) {
        //TODO: Verify path
        // we need to convert the given file to a decoded emf file uri
        // e.g. file:///Users/x/y/z
        // or file:///C:/x/y/z

        if (file.isDirectory()) {
            val files = file.listFiles()
            if (files != null) {
                for (f in files) {
                    scanRec(f, uris)
                }
            }
        } else {
            //TODO: Filter out for rell files
            if (file.extension == "rell") {
                uris.add(file.toURI())
            }
        }
    }


}