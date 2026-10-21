package com.js8call.example.api

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.TreeMap

/**
 * One JS8Call API message (JS8Call 3.0.3 JS8_Main/Message.cpp):
 * {"params":{...},"type":"RX.DIRECTED","value":"..."}, sent as compact JSON with the keys in
 * order, like QJsonObject.
 *
 * _ID: a message made with type and value only always gets a new one; one made with params keeps
 * a non-zero _ID and gets a new one otherwise; a message read from JSON keeps what it had. New
 * ones are text (milliseconds since 6 July 2017), like the PC.
 */
class ApiMessage private constructor(
    val type: String,
    val value: String,
    val params: LinkedHashMap<String, Any?>
) {
    /** Message(type, value): always a new _ID. */
    constructor(type: String, value: String = "") : this(type, value, LinkedHashMap()) {
        insertId()
    }

    /** Message(type, value, params): keeps a non-zero _ID. */
    constructor(type: String, value: String, params: Map<String, Any?>) : this(type, value, LinkedHashMap(params)) {
        ensureId()
    }

    /** Message::id: the _ID as a number, 0 without one. */
    val id: Long get() = toLong(params["_ID"])

    private fun insertId(): Long {
        val id = System.currentTimeMillis() - EPOCH
        params["_ID"] = id.toString()
        return id
    }

    /** Message::ensureId. */
    fun ensureId(): Long {
        val current = id
        return if (current != 0L) current else insertId()
    }

    fun toJson(): String = Json.write(mapOf("params" to params, "type" to type, "value" to value))

    /** Message::toVariantMap (inbox lists). */
    fun toVariantMap(): Map<String, Any?> = mapOf("type" to type, "value" to value, "params" to params)

    companion object {
        /** July 6, 2017 (Message.cpp EPOCH). */
        const val EPOCH = 1499299200000L

        /** Message::fromJson: type and value only when they are text, params only when an object. */
        fun fromJson(text: String): ApiMessage {
            val parsed = JSONTokener(text).nextValue()
            if (parsed !is JSONObject) throw IllegalArgumentException("json not an object")
            val type = parsed.opt("type") as? String ?: ""
            val value = parsed.opt("value") as? String ?: ""
            @Suppress("UNCHECKED_CAST")
            val params = (parsed.opt("params") as? JSONObject)?.let { Json.toKotlin(it) as Map<String, Any?> } ?: emptyMap()
            return ApiMessage(type, value, LinkedHashMap(params))
        }

        /** QVariant::toLongLong: numbers, text holding a number, true = 1. */
        fun toLong(v: Any?): Long = when (v) {
            null -> 0L
            is Number -> v.toLong()
            is Boolean -> if (v) 1L else 0L
            is String -> v.trim().toLongOrNull() ?: v.trim().toDoubleOrNull()?.toLong() ?: 0L
            else -> 0L
        }

        /** QVariant::toInt with the ok flag: null when it is not a number. */
        fun toIntOrNull(v: Any?): Int? = when (v) {
            is Number -> v.toInt()
            is Boolean -> if (v) 1 else 0
            is String -> v.trim().toIntOrNull()
            else -> null
        }

        /** QVariant(QString).toBool(): false for "", "0" and "false", true otherwise. */
        fun toBool(v: Any?): Boolean = when (v) {
            null -> false
            is Boolean -> v
            is Number -> v.toDouble() != 0.0
            is String -> v.isNotEmpty() && v != "0" && !v.equals("false", ignoreCase = true)
            else -> false
        }
    }
}

/** Compact JSON with sorted keys (QJsonDocument::Compact), and JSON back to Kotlin maps and lists. */
object Json {
    fun write(v: Any?): String = StringBuilder().also { append(it, v) }.toString()

    private fun append(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is String -> string(sb, v)
            is Boolean -> sb.append(if (v) "true" else "false")
            is Int, is Long, is Short, is Byte -> sb.append(v.toString())
            is Float -> number(sb, v.toDouble())
            is Double -> number(sb, v)
            is Number -> number(sb, v.toDouble())
            is Map<*, *> -> {
                sb.append('{')
                val sorted = TreeMap<String, Any?>()
                v.forEach { (k, value) -> sorted[k.toString()] = value }
                var first = true
                sorted.forEach { (k, value) ->
                    if (!first) sb.append(',')
                    first = false
                    string(sb, k)
                    sb.append(':')
                    append(sb, value)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                v.forEachIndexed { i, item -> if (i > 0) sb.append(','); append(sb, item) }
                sb.append(']')
            }
            is IntArray -> append(sb, v.toList())
            is Array<*> -> append(sb, v.toList())
            else -> string(sb, v.toString())
        }
    }

    /** Whole numbers without ".0", like Qt's shortest form. */
    private fun number(sb: StringBuilder, d: Double) {
        if (d.isNaN() || d.isInfinite()) { sb.append("null"); return }
        if (d == Math.floor(d) && Math.abs(d) < 1e15) sb.append(d.toLong().toString()) else sb.append(d.toString())
    }

    private fun string(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
            }
        }
        sb.append('"')
    }

    fun toKotlin(v: Any?): Any? = when (v) {
        null, JSONObject.NULL -> null
        is JSONObject -> {
            val m = LinkedHashMap<String, Any?>()
            v.keys().forEach { k -> m[k] = toKotlin(v.opt(k)) }
            m
        }
        is JSONArray -> (0 until v.length()).map { toKotlin(v.opt(it)) }
        else -> v
    }
}
