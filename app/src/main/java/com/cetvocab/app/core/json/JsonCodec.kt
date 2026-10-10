package com.cetvocab.app.core.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Small, dependency-light JSON helper used for two very different jobs:
 *  * serialising list/map columns inside Room (meanings, collocations, queues);
 *  * parsing user supplied word-list / backup files.
 *
 * It is deliberately lenient about unknown keys (a newer export can still be imported) and
 * never pretty-prints by default, which keeps database rows compact.
 */
object JsonCodec {

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
        prettyPrint = false
        allowStructuredMapKeys = false
    }

    val prettyJson: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
        prettyPrint = true
    }

    fun encodeStrings(values: List<String>): String =
        json.encodeToString(ListSerializer(String.serializer()), values)

    /** Never throws: a malformed column degrades to an empty list instead of crashing a screen. */
    fun decodeStrings(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(ListSerializer(String.serializer()), raw) }
            .getOrDefault(emptyList())
    }

    fun encodeLongs(values: List<Long>): String =
        json.encodeToString(ListSerializer(Long.serializer()), values)

    fun decodeLongs(raw: String?): List<Long> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(ListSerializer(Long.serializer()), raw) }
            .getOrDefault(emptyList())
    }

    fun encodeIntCounts(values: Map<Long, Int>): String =
        json.encodeToString(
            MapSerializer(Long.serializer(), Int.serializer()),
            values,
        )

    fun decodeIntCounts(raw: String?): Map<Long, Int> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            json.decodeFromString(MapSerializer(Long.serializer(), Int.serializer()), raw)
        }.getOrDefault(emptyMap())
    }
}
