/*
 * Copyright (C) 2026 ChromaWay AB. See LICENSE for license information.
 */

package net.postchain.rell.base.model

import net.postchain.rell.base.utils.immSetOf

object R_SqlConstants {
    const val ROWID_COLUMN = "rowid"
    const val ROWID_GEN = "rowid_gen"
    const val MAKE_ROWID = "make_rowid"
    const val MAKE_ROWIDS = "make_rowids"

    const val FN_INTEGER_POWER = "rell_integer_power"
    const val FN_BIGINTEGER_FROM_TEXT = "rell_biginteger_from_text"
    const val FN_BIGINTEGER_POWER = "rell_biginteger_power"
    const val FN_BYTEA_SUBSTR1 = "rell_bytea_substr1"
    const val FN_BYTEA_SUBSTR2 = "rell_bytea_substr2"
    const val FN_DECIMAL_FROM_TEXT = "rell_decimal_from_text"
    const val FN_DECIMAL_TO_TEXT = "rell_decimal_to_text"
    const val FN_TEXT_REPEAT = "rell_text_repeat"
    const val FN_TEXT_SUBSTR1 = "rell_text_substr1"
    const val FN_TEXT_SUBSTR2 = "rell_text_substr2"
    const val FN_TEXT_GETCHAR = "rell_text_getchar"
    const val FN_JSON_ARRAY_GET = "rell_json_array_get"
    const val FN_JSON_OBJECT_GET = "rell_json_object_get"
    const val FN_JSON_ARRAY_GET_OR_NULL = "rell_json_array_get_or_null"
    const val FN_JSON_AS_BOOLEAN_OR_NULL = "rell_json_as_boolean_or_null"
    const val FN_JSON_AS_INTEGER = "rell_json_as_integer"
    const val FN_JSON_AS_INTEGER_OR_NULL = "rell_json_as_integer_or_null"
    const val FN_JSON_AS_BIG_INTEGER = "rell_json_as_big_integer"
    const val FN_JSON_AS_BIG_INTEGER_OR_NULL = "rell_json_as_big_integer_or_null"
    const val FN_JSON_AS_TEXT = "rell_json_as_text"
    const val FN_JSON_AS_TEXT_OR_NULL = "rell_json_as_text_or_null"
    const val FN_JSON_SIZE = "rell_json_size"

    const val BLOCKCHAINS_TABLE = "blockchains"
    const val BLOCKS_TABLE = "blocks"
    const val TRANSACTIONS_TABLE = "transactions"

    val SYSTEM_CHAIN_TABLES = immSetOf(
        "events",
        "states",
        "event_pages",
        "snapshot_pages",
        "configurations",
        "gtx_module_version"
    )

    private val SYSTEM_OBJECTS_0 = immSetOf(
        ROWID_GEN,
        MAKE_ROWID,
        BLOCKCHAINS_TABLE,
        BLOCKS_TABLE,
        TRANSACTIONS_TABLE,
        "meta",
        "peerinfos"
    )

    val SYSTEM_OBJECTS = SYSTEM_OBJECTS_0 + SYSTEM_CHAIN_TABLES
}
