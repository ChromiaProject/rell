/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils.ide

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.core.util.Separators
import net.postchain.rell.base.compiler.base.core.C_CompilerModuleSelection
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.base.model.ModuleName
import net.postchain.rell.base.utils.*
import net.postchain.rell.base.utils.ide.IdeCodeSnippet.Companion.writeValue
import java.io.StringWriter

class IdeSnippetMessage(
    @JvmField val pos: String,
    @JvmField val type: C_MessageType,
    @JvmField val code: String,
    @JvmField val text: String,
) {
    fun serialize(): Any {
        return mapOf(
                "pos" to pos,
                "type" to type.name,
                "code" to code,
                "text" to text
        )
    }

    companion object {
        fun deserialize(obj: Any): IdeSnippetMessage {
            @Suppress("UNCHECKED_CAST") val raw = obj as Map<Any, Any>
            val map = raw.map { (k, v) -> k as String to v as String }.toMap()
            return IdeSnippetMessage(
                    map.getValue("pos"),
                    C_MessageType.valueOf(map.getValue("type")),
                    map.getValue("code"),
                    map.getValue("text")
            )
        }
    }
}

class IdeCodeSnippet(
    @JvmField val files: ImmMap<String, String>,
    @JvmField val modules: C_CompilerModuleSelection,
    @JvmField val options: C_CompilerOptions,
    @JvmField val messages: ImmList<IdeSnippetMessage>,
    @JvmField val parsing: ImmMap<String, List<IdeSnippetMessage>>,
    @JvmField val comments: ImmMap<String, String>,
) {
    val serialized: String by lazy { serialize() }

    private fun serialize(): String {
        val opts = options.toPojoMap()

        val modulesObj = mapOf(
            "modules" to modules.appModules?.map { it.str() },
            "test_root_modules" to modules.testModules.map { it.str() },
            "test_sub_modules" to modules.testSubModules,
        )

        val messagesObj = messages.map { it.serialize() }

        val obj = mapOf(
            "files" to files,
            "modules" to modulesObj,
            "options" to opts,
            "messages" to messagesObj,
            "parsing" to parsing.mapValues { (_, v) -> v.map { it.serialize() } },
            "comments" to comments,
        )

        val res = writeJson(obj) { }
        deserializeOne(res) // Verification
        return res
    }

    override fun equals(other: Any?) = this === other || (other is IdeCodeSnippet && other.serialized == serialized)

    override fun hashCode() = serialized.hashCode()

    @Suppress("UNCHECKED_CAST")
    companion object {
        private val FACTORY = JsonFactory()

        @JvmStatic fun serialize(snippets: Collection<IdeCodeSnippet>): String {
            val json = snippets.joinToString(separator = ",", prefix = "[", postfix = "]") { it.serialized }
            return prettyFormatJson(json)
        }

        @JvmStatic fun deserialize(s: String): List<IdeCodeSnippet> {
            val list = readJson(s) as List<Any>
            return list.map { deserializeFromRaw(it as Map<String, Any>) }
        }

        private fun deserializeOne(s: String): IdeCodeSnippet {
            val any = readJson(s)
            return deserializeFromRaw(any as Map<String, Any>)
        }

        @JvmStatic
        fun deserializeFromRaw(obj: Map<String, Any>): IdeCodeSnippet {
            val filesRaw = obj.getValue("files") as Map<Any, Any>
            val files = filesRaw.map { (k, v) -> k as String to v as String }.toImmMap()

            val modulesRaw = obj.getValue("modules") as Map<Any, Any>
            val modulesMap = modulesRaw.map { (k, v) -> k as String to v }.toImmMap()
            val modules = C_CompilerModuleSelection(
                appModules = (modulesMap.getValue("modules") as List<Any>?)?.mapToImmList { ModuleName.of(it as String) },
                testModules = (modulesMap.getValue("test_root_modules") as List<Any>).mapToImmList { ModuleName.of(it as String) },
                testSubModules = (modulesMap["test_sub_modules"] as Boolean?) ?: true,
            )

            val optionsRaw = obj.getValue("options") as Map<Any, Any>
            val optionsMap = optionsRaw.map { (k, v) -> k as String to v }.toImmMap()
            val options = C_CompilerOptions.fromPojoMap(optionsMap)

            val messagesRaw = obj.getValue("messages") as List<Any>
            val messages = messagesRaw.mapToImmList { IdeSnippetMessage.deserialize(it) }

            val parsingRaw = obj["parsing"] as Map<Any, Any>?
            val parsing = parsingRaw?.map { (k, v) ->
                k as String to (v as List<Any>).map { IdeSnippetMessage.deserialize(it) }
            }?.toImmMap().orEmpty()

            val commentsRaw = obj["comments"] as Map<Any, Any>?
            val comments = commentsRaw?.map { (k, v) -> k as String to v as String }?.toImmMap().orEmpty()

            return IdeCodeSnippet(files, modules, options, messages, parsing, comments)
        }

        private fun prettyFormatJson(json: String): String {
            val separators = Separators()
                .withArrayEmptySeparator("")
                .withObjectEmptySeparator("")
                .withObjectFieldValueSpacing(Separators.Spacing.AFTER)
            val prettyPrinter = DefaultPrettyPrinter()
                .withArrayIndenter(DefaultIndenter("    ", "\n"))
                .withObjectIndenter(DefaultIndenter("    ", "\n"))
                .withSeparators(separators)
            return writeJson(readJson(json)) { it.prettyPrinter = prettyPrinter }
        }

        private fun writeJson(value: Any?, configure: (JsonGenerator) -> Unit): String {
            val writer = StringWriter()
            FACTORY.createGenerator(writer).use { gen ->
                configure(gen)
                writeValue(gen, value)
            }
            return writer.toString()
        }

        /** Writes a plain Kotlin value tree - maps, lists, strings, numbers, booleans - as JSON. */
        private fun writeValue(gen: JsonGenerator, value: Any?) {
            when (value) {
                null -> gen.writeNull()
                is String -> gen.writeString(value)
                is Boolean -> gen.writeBoolean(value)
                is Int -> gen.writeNumber(value)
                is Long -> gen.writeNumber(value)
                is Map<*, *> -> {
                    gen.writeStartObject()
                    for ((key, v) in value) {
                        gen.writeFieldName(key as String)
                        writeValue(gen, v)
                    }
                    gen.writeEndObject()
                }
                is Collection<*> -> {
                    gen.writeStartArray()
                    value.forEach { writeValue(gen, it) }
                    gen.writeEndArray()
                }
                else -> throw IllegalArgumentException("Cannot serialize value of type ${value.javaClass.name}")
            }
        }

        /** Inverse of [writeValue]. */
        private fun readJson(s: String): Any? = FACTORY.createParser(s).use { parser ->
            parser.nextToken()
            readValue(parser)
        }

        private fun readValue(parser: JsonParser): Any? = when (val token = parser.currentToken()) {
            JsonToken.VALUE_NULL -> null
            JsonToken.VALUE_STRING -> parser.text
            JsonToken.VALUE_TRUE -> true
            JsonToken.VALUE_FALSE -> false
            JsonToken.VALUE_NUMBER_INT -> parser.numberValue
            JsonToken.VALUE_NUMBER_FLOAT -> parser.doubleValue
            JsonToken.START_OBJECT -> {
                val map = mutableMapOf<String, Any?>()
                while (parser.nextToken() != JsonToken.END_OBJECT) {
                    val name = parser.currentName()
                    parser.nextToken()
                    map[name] = readValue(parser)
                }
                map
            }
            JsonToken.START_ARRAY -> {
                val list = mutableListOf<Any?>()
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    list.add(readValue(parser))
                }
                list
            }
            else -> throw IllegalArgumentException("Unexpected JSON token: $token")
        }
    }
}
