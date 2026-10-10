package com.cetvocab.app.data.local

import androidx.room.TypeConverter
import com.cetvocab.app.core.json.JsonCodec

/**
 * Room converters.
 *
 * Only *structural* conversions live here (lists/maps stored as JSON text). Domain enums are
 * mapped explicitly in the repository layer instead, so the stored strings are stable and
 * readable in the database file even if an enum is renamed later.
 */
class Converters {

    @TypeConverter
    fun fromStringList(value: List<String>?): String = JsonCodec.encodeStrings(value.orEmpty())

    @TypeConverter
    fun toStringList(value: String?): List<String> = JsonCodec.decodeStrings(value)

    @TypeConverter
    fun fromLongList(value: List<Long>?): String = JsonCodec.encodeLongs(value.orEmpty())

    @TypeConverter
    fun toLongList(value: String?): List<Long> = JsonCodec.decodeLongs(value)

    @TypeConverter
    fun fromIntCountMap(value: Map<Long, Int>?): String = JsonCodec.encodeIntCounts(value.orEmpty())

    @TypeConverter
    fun toIntCountMap(value: String?): Map<Long, Int> = JsonCodec.decodeIntCounts(value)
}
