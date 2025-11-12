package com.ailive.memory.database.converters

import androidx.room.TypeConverter
import com.ailive.memory.database.entities.FactCategory
import org.json.JSONArray
import org.json.JSONObject

/**
 * Type converters for Room database
 *
 * Handles conversion of complex types to/from database-compatible formats.
 */
class Converters {

    // ===== List<String> =====
    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        if (value == null) return null
        return JSONArray(value).toString()
    }

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        if (value == null) return null
        val jsonArray = JSONArray(value)
        return List(jsonArray.length()) { jsonArray.getString(it) }
    }

    // ===== List<Float> (for embeddings) =====
    @TypeConverter
    fun fromFloatList(value: List<Float>?): String? {
        if (value == null) return null
        return JSONArray(value).toString()
    }

    @TypeConverter
    fun toFloatList(value: String?): List<Float>? {
        if (value == null) return null
        val jsonArray = JSONArray(value)
        return List(jsonArray.length()) { jsonArray.getDouble(it).toFloat() }
    }

    // ===== Map<String, String> =====
    @TypeConverter
    fun fromStringMap(value: Map<String, String>?): String? {
        if (value == null) return null
        return JSONObject(value).toString()
    }

    @TypeConverter
    fun toStringMap(value: String?): Map<String, String>? {
        if (value == null) return null
        val jsonObject = JSONObject(value)
        val map = mutableMapOf<String, String>()
        jsonObject.keys().forEach { key ->
            map[key] = jsonObject.getString(key)
        }
        return map
    }

    // ===== FactCategory Enum =====
    @TypeConverter
    fun fromFactCategory(value: FactCategory?): String? {
        return value?.name
    }

    @TypeConverter
    fun toFactCategory(value: String?): FactCategory? {
        return value?.let { FactCategory.valueOf(it) }
    }
}
