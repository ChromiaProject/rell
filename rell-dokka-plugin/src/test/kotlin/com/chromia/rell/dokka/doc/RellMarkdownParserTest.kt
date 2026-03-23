package com.chromia.rell.dokka.doc

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.jetbrains.dokka.InternalDokkaApi
import org.jetbrains.dokka.analysis.kotlin.markdown.MARKDOWN_ELEMENT_FILE_NAME
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.model.doc.CustomDocTag
import org.jetbrains.dokka.model.doc.Description
import org.jetbrains.dokka.model.doc.DocumentationLink
import org.jetbrains.dokka.model.doc.DocumentationNode
import org.jetbrains.dokka.model.doc.P
import org.jetbrains.dokka.model.doc.Text
import org.junit.jupiter.api.Test


class RellMarkdownParserTest {

    private fun parseMarkdownToDocNode(text: String) =
            RellMarkdownParser().parseStringToDocNode(text)

    @OptIn(InternalDokkaApi::class)
    @Test
    fun `Can parse with references`() {
        val rellDoc = """
            | My doc
            | 
            | My doc references [a.b.my_fun]
        """.trimMargin()
        val expected = DocumentationNode(listOf(
                Description(CustomDocTag(
                        listOf(
                            P(listOf(Text(" My doc"))),
                            P(listOf(
                                    Text(" My doc references "),
                                    DocumentationLink(
                                            DRI("a.b", "my_fun"),
                                            children = listOf(Text("a.b.my_fun")),
                                            params = mapOf("href" to "[a.b.my_fun]")
                                    )
                            ))
                        ), name = MARKDOWN_ELEMENT_FILE_NAME
                ))
        ))
        val res = RellMarkdownParser().parse(rellDoc)
        assertThat(res).isEqualTo(expected)
    }
}