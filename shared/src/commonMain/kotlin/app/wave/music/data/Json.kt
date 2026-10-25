package app.wave.music.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray as KJsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject as KJsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * The few bits of Android's org.json that Wave reads web responses with, on top of
 * kotlinx.serialization so they work on computers too.
 */
class JSONObject internal constructor(private val o: KJsonObject) {
    constructor(text: String) : this(Json.parseToJsonElement(text) as KJsonObject)

    private fun prim(key: String) = (o[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }

    fun has(key: String) = o.containsKey(key)
    fun optString(key: String, fallback: String = ""): String = prim(key)?.contentOrNull ?: fallback
    fun getString(key: String): String = requireNotNull(prim(key)?.contentOrNull) { "No $key" }
    fun optDouble(key: String, fallback: Double = Double.NaN): Double = prim(key)?.doubleOrNull ?: fallback
    fun optBoolean(key: String, fallback: Boolean = false): Boolean = prim(key)?.booleanOrNull ?: fallback
    fun optJSONArray(key: String): JSONArray? = (o[key] as? KJsonArray)?.let(::JSONArray)
    fun optJSONObject(key: String): JSONObject? = (o[key] as? KJsonObject)?.let(::JSONObject)
    fun getJSONObject(key: String): JSONObject = requireNotNull(optJSONObject(key)) { "No $key" }
}

class JSONArray internal constructor(private val a: KJsonArray) {
    constructor(text: String) : this(Json.parseToJsonElement(text) as KJsonArray)

    fun length() = a.size
    fun getJSONObject(index: Int): JSONObject = JSONObject(a[index] as KJsonObject)
    fun element(index: Int): JsonElement = a[index]
}
