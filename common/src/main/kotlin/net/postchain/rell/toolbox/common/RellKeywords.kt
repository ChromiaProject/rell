package net.postchain.rell.toolbox.common

enum class RellKeywords(val value: String) {
    ABSTRACT("abstract"),
    AND("and"),
    BREAK("break"),
    CLASS("class"),
    CONTINUE("continue"),
    CREATE("create"),
    DELETE("delete"),
    ELSE("else"),
    ENTITY("entity"),
    ENUM("enum"),
    FALSE("false"),
    FOR("for"),
    FUNCTION("function"),
    GUARD("guard"),
    IF("if"),
    IMPORT("import"),
    IN("in"),
    INCLUDE("include"),
    INDEX("index"),
    KEY("key"),
    LIMIT("limit"),
    LIST("list"),
    MAP("map"),
    MODULE("module"),
    MUTABLE("mutable"),
    NAMESPACE("namespace"),
    NOT("not"),
    NULL("null"),
    OBJECT("object"),
    OFFSET("offset"),
    OPERATION("operation"),
    OR("or"),
    OVERRIDE("override"),
    QUERY("query"),
    RECORD("record"),
    RETURN("return"),
    SET("set"),
    STRUCT("struct"),
    TRUE("true"),
    UPDATE("update"),
    VAL("val"),
    VAR("var"),
    VIRTUAL("virtual"),
    WHEN("when"),
    WHILE("while");

    companion object {
        fun asList(): List<String> {
            return RellKeywords.entries.map { it.value }
        }
    }
}
