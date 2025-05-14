/*
 * Copyright (C) 2024 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.utils.ide

import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.core.util.Separators
import com.fasterxml.jackson.databind.ObjectMapper
import net.postchain.rell.base.compiler.base.core.C_CompilerModuleSelection
import net.postchain.rell.base.compiler.base.core.C_CompilerOptions
import net.postchain.rell.base.compiler.base.utils.C_MessageType
import net.postchain.rell.base.model.R_ModuleName
import net.postchain.rell.base.utils.*

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

        val mapper = ObjectMapper()
        val res = mapper.writeValueAsString(obj)
        deserializeOne(res) // Verification
        return res
    }

    override fun equals(other: Any?) = this === other || (other is IdeCodeSnippet && other.serialized == serialized)

    override fun hashCode() = serialized.hashCode()

    @Suppress("UNCHECKED_CAST")
    companion object {
        @JvmStatic fun serialize(snippets: Collection<IdeCodeSnippet>): String {
            val json = snippets.joinToString(separator = ",", prefix = "[", postfix = "]") { it.serialized }
            return prettyFormatJson(json)
        }

        @JvmStatic fun deserialize(s: String): List<IdeCodeSnippet> {
            val mapper = ObjectMapper()
            val any = mapper.readValue(s, Any::class.java)
            val list = any as List<Any>
            return list.map { deserialize0(it) }
        }

        private fun deserializeOne(s: String): IdeCodeSnippet {
            val mapper = ObjectMapper()
            val any = mapper.readValue(s, Any::class.java)
            return deserialize0(any)
        }

        private fun deserialize0(any: Any): IdeCodeSnippet {
            val obj = any as Map<String, Any>

            val filesRaw = obj.getValue("files") as Map<Any, Any>
            val files = filesRaw.map { (k, v) -> k as String to v as String }.toImmMap()

            val modulesRaw = obj.getValue("modules") as Map<Any, Any>
            val modulesMap = modulesRaw.map { (k, v) -> k as String to v }.toImmMap()
            val modules = C_CompilerModuleSelection(
                appModules = (modulesMap.getValue("modules") as List<Any>?)?.mapToImmList { R_ModuleName.of(it as String) },
                testModules = (modulesMap.getValue("test_root_modules") as List<Any>).mapToImmList { R_ModuleName.of(it as String) },
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
            }?.toImmMap() ?: immMapOf()

            val commentsRaw = obj["comments"] as Map<Any, Any>?
            val comments = commentsRaw?.map { (k, v) -> k as String to v as String }?.toImmMap() ?: immMapOf()

            return IdeCodeSnippet(files, modules, options, messages, parsing, comments)
        }

        private fun prettyFormatJson(json: String): String {
            val mapper = ObjectMapper()
            val separators = Separators()
                .withArrayEmptySeparator("")
                .withObjectEmptySeparator("")
                .withObjectFieldValueSpacing(Separators.Spacing.AFTER)
            val prettyPrinter = DefaultPrettyPrinter()
                .withArrayIndenter(DefaultIndenter("    ", "\n"))
                .withObjectIndenter(DefaultIndenter("    ", "\n"))
                .withSeparators(separators)
            val jsonObject = mapper.readValue(json, Any::class.java)
            return mapper.writer().with(prettyPrinter).writeValueAsString(jsonObject)
        }
    }
}
