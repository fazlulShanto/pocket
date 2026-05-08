package dev.spikeysanju.expensetracker.voice.domain

import org.json.JSONArray
import org.json.JSONObject

internal fun JSONObject.optStringOrNull(name: String): String? {
    if (!has(name) || isNull(name)) {
        return null
    }
    return optString(name).trim().ifBlank { null }
}

internal fun JSONObject.optDoubleOrNull(name: String): Double? {
    if (!has(name) || isNull(name)) {
        return null
    }
    return when (val value = opt(name)) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    }
}

internal fun JSONObject.optIntOrNull(name: String): Int? {
    if (!has(name) || isNull(name)) {
        return null
    }
    return when (val value = opt(name)) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull()
        else -> null
    }
}

internal fun JSONArray.toStringList(): List<String> {
    val values = mutableListOf<String>()
    for (index in 0 until length()) {
        val item = opt(index)
        when (item) {
            is String -> item.trim().takeIf { it.isNotBlank() }?.let(values::add)
            is JSONObject -> item.optStringOrNull("text")?.let(values::add)
        }
    }
    return values
}
