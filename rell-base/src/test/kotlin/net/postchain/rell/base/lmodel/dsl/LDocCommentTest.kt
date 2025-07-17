/*
 * Copyright (C) 2025 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.lmodel.dsl

import net.postchain.rell.base.lib.Lib_Rell
import net.postchain.rell.base.lib.type.Rt_UnitValue
import kotlin.test.Test

class LDocCommentTest: BaseLTest() {
    @Test fun testIndent() {
        chkIndent("", "")
        chkIndent("     ", "")
        chkIndent("hello", "hello")

        chkIndent("    hello    ", "hello")
        chkIndent("    hello\n    world", "hello\nworld")
        chkIndent("    hello\n      world", "hello\n  world")
        chkIndent("    line1\n    line2\n    line3", "line1\nline2\nline3")
        chkIndent("    line1\n      line2\n        line3", "line1\n  line2\n    line3")

        chkIndent("\n    hello\n    world\n", "hello\nworld")
        chkIndent("\n    line1\n    line2\n    line3\n", "line1\nline2\nline3")
        chkIndent("\n    line1\n      line2\n        line3\n", "line1\n  line2\n    line3")
        chkIndent("\n    line1\n  line2\nline3\n", "line1\n  line2\nline3") // Questionable (line2), but fine.
        chkIndent("\n    line1\n\n      line2\n\n        line3\n", "line1\n\n  line2\n\n    line3")
    }

    private fun chkIndent(sourceComment: String, exp: String) {
        val mod = makeModule("test") {
            type("foo", comment = sourceComment)
            type("bar") { comment(sourceComment) }
        }
        chkComment(mod, "foo", exp)
        chkComment(mod, "bar", exp)
    }

    @Test fun testTags() {
        chkComment("f", "description|param:x=x-param;y=y-param|return:ret-value|see:other-1;other-2|since:0.10.5") {
            function("f", result = "unit") {
                comment("""
                    description
                    @see other-1
                    @param y y-param
                    @return ret-value
                    @param x x-param
                    @see other-2
                    @since 0.10.5
                """)
                param("x", "integer")
                param("y", "text")
                body { -> Rt_UnitValue }
            }
        }
    }

    @Test fun testTagUnknown() {
        chkErr("DOCE:comment:tag:unknown:foo") {
            makeModule("test") {
                type("data", comment = "hello\n@foo 123")
            }
        }
    }

    @Test fun testTagDuplicate() {
        chkErr("DOCE:comment:tag:duplicate:since") {
            makeModule("test") {
                type("data", comment = "hello\n@since 0.10.5\n@since 0.10.5")
            }
        }
        chkErr("DOCE:comment:tag:duplicate:return") {
            makeModule("test") {
                function("f", result = "any", comment = "hello\n@return 123\n@return 456") {
                    body { -> Rt_UnitValue }
                }
            }
        }
        chkErr("DOCE:comment:tag:duplicate:param[x]") {
            makeModule("test") {
                function("f", result = "any", comment = "hello\n@param x 123\n@param x 123") {
                    body { -> Rt_UnitValue }
                }
            }
        }
        chkComment("data", "hello|see:123;456") {
            type("data", comment = "hello\n@see 123\n@see 456")
        }
    }

    @Test fun testSince() {
        chkComment("foo", "hello|since:0.10.5") {
            type("foo", since = "0.10.5", comment = "hello")
        }
        chkComment("foo", "hello|since:0.10.5") {
            type("foo", comment = "hello\n@since 0.10.5")
        }
        chkErr("DOCE:comment:tag:duplicate:since") {
            makeModule("test") {
                type("foo", since = "0.10.5", comment = "hello\n@since 0.10.5")
            }
        }
        chkErr("DOCE:comment:tag:duplicate:since") {
            makeModule("test") {
                type("foo", since = "0.10.5", comment = "hello\n@since 0.10.6")
            }
        }
        chkErr("DOCE:comment:tag:duplicate:since") {
            makeModule("test") {
                type("foo", comment = "hello\n@since 0.10.5\n@since 0.10.5")
            }
        }
        chkErr("DOCE:comment:tag:duplicate:since") {
            makeModule("test") {
                type("foo", comment = "hello\n@since 0.10.5\n@since 0.10.6")
            }
        }
    }

    @Test fun testParamBasic() {
        chkParam(
            null, null, null,
            null,
            null,
            null,
        )
        chkParam(
            "f-comment\n@param x x-fun\n@param y y-fun", null, null,
            "f-comment|param:x=x-fun;y=y-fun",
            "x-fun",
            "y-fun",
        )
        chkParam(
            null, "x-param", "y-param",
            "|param:x=x-param;y=y-param",
            "x-param",
            "y-param",
        )
        chkParam(
            "f-comment", "x-param", "y-param",
            "f-comment|param:x=x-param;y=y-param",
            "x-param",
            "y-param",
        )
        chkParam(
            "f-comment\n@param x x-fun\n@param y y-fun", "x-param", "y-param",
            "f-comment|param:x=x-fun;y=y-fun",
            "x-fun",
            "y-fun",
        )
        chkParam(
            "f-comment\n@param y y-fun\n@param x x-fun", "x-param", "y-param",
            "f-comment|param:x=x-fun;y=y-fun",
            "x-fun",
            "y-fun",
        )
    }

    @Test fun testParamMixed() {
        chkParam(
            null, "x-param", null,
            "|param:x=x-param",
            "x-param",
            null,
        )
        chkParam(
            null, null, "y-param",
            "|param:y=y-param",
            null,
            "y-param",
        )
        chkParam(
            "f-comment\n@param y y-fun", "x-param", null,
            "f-comment|param:x=x-param;y=y-fun",
            "x-param",
            "y-fun",
        )
        chkParam(
            "f-comment\n@param x x-fun", null, "y-param",
            "f-comment|param:x=x-fun;y=y-param",
            "x-fun",
            "y-param",
        )
    }

    @Test fun testParamAdvanced() {
        chkParam(
            null, "x-param\n@since 0.10.5", "y-param\n@see something else",
            "|param:x=x-param;y=y-param",
            "x-param|since:0.10.5",
            "y-param|see:something else",
        )
        chkParam(
            "f-comment\n@since 0.10.3", "x-param\n@since 0.10.5", "y-param\n@see something else",
            "f-comment|param:x=x-param;y=y-param|since:0.10.3",
            "x-param|since:0.10.5",
            "y-param|see:something else",
        )
        chkParam(
            "f-comment\n@param x x-fun\n@param y y-fun\n@since 0.10.3", "x-param\n@since 0.10.5", "y-param",
            "f-comment|param:x=x-fun;y=y-fun|since:0.10.3",
            "x-fun|since:0.10.5",
            "y-fun",
        )
    }

    @Test fun testParamName() {
        chkComment("f", "desc|param:x=") {
            function("f", result = "unit", comment = "desc\n@param x") {
                param("x", "integer")
                body { -> Rt_UnitValue }
            }
        }
        chkErr("DOCE:tag:no_key:param") {
            makeModule("test") {
                function("f", result = "any", comment = "desc\n@param") {
                    body { -> Rt_UnitValue }
                }
            }
        }
        chkErr("DOCE:comment:param:invalid_name:[test:f]:123") {
            makeModule("test") {
                function("f", result = "any", comment = "desc\n@param 123") {
                    body { -> Rt_UnitValue }
                }
            }
        }
        chkErr("DOCE:comment:param:unknown:[test:f]:x") {
            makeModule("test") {
                function("f", result = "any", comment = "desc\n@param x") {
                    body { -> Rt_UnitValue }
                }
            }
        }
    }

    private fun chkParam(
        funComment: String?,
        paramComment1: String?,
        paramComment2: String?,
        expFunComment: String?,
        expParamComment1: String?,
        expParamComment2: String?,
    ) {
        val mod = makeModule("test") {
            imports(Lib_Rell.MODULE.lModule)
            function("f", result = "unit", comment = funComment) {
                param("x", "integer", comment = paramComment1)
                param("y", "text", comment = paramComment2)
                body { -> Rt_UnitValue }
            }
            type("data") {
                constructor(comment = funComment) {
                    param("x", "integer", comment = paramComment1)
                    param("y", "text", comment = paramComment2)
                    body { -> Rt_UnitValue }
                }
                function("f", result = "unit", comment = funComment) {
                    param("x", "integer", comment = paramComment1)
                    param("y", "text", comment = paramComment2)
                    body { -> Rt_UnitValue }
                }
                staticFunction("g", result = "unit", comment = funComment) {
                    param("x", "integer", comment = paramComment1)
                    param("y", "text", comment = paramComment2)
                    body { -> Rt_UnitValue }
                }
            }
        }

        chkComment(mod, "f", expFunComment)
        chkComment(mod, "f.x", expParamComment1)
        chkComment(mod, "f.y", expParamComment2)
        chkComment(mod, "data.!init", expFunComment)
        chkComment(mod, "data.!init.x", expParamComment1)
        chkComment(mod, "data.!init.y", expParamComment2)
        chkComment(mod, "data.f", expFunComment)
        chkComment(mod, "data.f.x", expParamComment1)
        chkComment(mod, "data.f.y", expParamComment2)
        chkComment(mod, "data.g", expFunComment)
        chkComment(mod, "data.g.x", expParamComment1)
        chkComment(mod, "data.g.y", expParamComment2)
    }

    @Test fun testMultiline() {
        chkComment("f", "Desc 1\n Desc 2\nDesc 3|return:Ret 1\n  Ret 2\nRet 3|see:See 1\nSee 2\n   See 3") {
            function("f", result = "integer") {
                comment("""
                     Desc 1
                      Desc 2
                     Desc 3

                     @return   Ret 1
                       Ret 2
                     Ret 3

                     @see
                     See 1
                     See 2
                        See 3

                """)
                body { -> Rt_UnitValue }
            }
        }
    }
}
