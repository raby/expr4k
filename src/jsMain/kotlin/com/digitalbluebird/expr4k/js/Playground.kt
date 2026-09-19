@file:OptIn(kotlin.js.ExperimentalJsExport::class)

package com.digitalbluebird.expr4k.js

import com.digitalbluebird.expr4k.Expr
import com.digitalbluebird.expr4k.Expr4kException
import com.digitalbluebird.expr4k.Type

/**
 * The browser entry point for the playground.
 *
 * Evaluate an expr4k [source] expression against a [contextJson] object (a JSON string such as
 * `{"age": 21, "country": "UK"}`, or empty for no variables) and return a JSON string:
 *
 *  - success: `{"ok": true, "output": "<rendered value>", "type": "<inferred type>"}`
 *  - failure: `{"ok": false, "output": "<message>", "line": <n>, "col": <n>}`
 *
 * JSON in, JSON out keeps the boundary with JavaScript small and stable — no marshalling of arbitrary
 * Kotlin values across the language edge.
 */
@JsExport
public fun evaluate(source: String, contextJson: String): String {
    val result: dynamic = js("({})")
    try {
        val context = parseContext(contextJson)
        // Type-check against the context's shape, so type errors surface at compile time — the point
        // of the language — rather than only when a bad branch is evaluated.
        val expr = Expr.compile(source, schemaOf(context))
        result.ok = true
        result.output = render(expr.eval(context))
        result.type = expr.type.name.lowercase()
    } catch (e: Expr4kException) {
        result.ok = false
        result.output = e.message
        result.line = e.pos.line
        result.col = e.pos.col
    } catch (e: Throwable) {
        result.ok = false
        result.output = "context must be a JSON object, e.g. {\"age\": 21}"
    }
    return JSON.stringify(result)
}

/** Derive a type schema from the context's values, so the checker can validate against them. */
private fun schemaOf(context: Map<String, Any?>): Map<String, Type> =
    context.mapValues { (_, value) ->
        when (value) {
            is Double -> Type.NUMBER
            is String -> Type.STRING
            is Boolean -> Type.BOOLEAN
            is List<*> -> Type.LIST
            is Map<*, *> -> Type.OBJECT
            else -> Type.ANY
        }
    }

/** Parse a JSON object string into a Kotlin context map; an empty string means no variables. */
private fun parseContext(contextJson: String): Map<String, Any?> {
    val trimmed = contextJson.trim()
    if (trimmed.isEmpty()) return emptyMap()
    val converted = jsToKotlin(JSON.parse(trimmed))
    @Suppress("UNCHECKED_CAST")
    return converted as? Map<String, Any?> ?: throw IllegalArgumentException("context must be a JSON object")
}

/** Convert a parsed JSON value (a JS value) into the plain Kotlin values the evaluator understands. */
private fun jsToKotlin(value: dynamic): Any? = when {
    value == null -> null
    jsTypeOf(value) == "string" -> value as String
    jsTypeOf(value) == "boolean" -> value as Boolean
    jsTypeOf(value) == "number" -> value as Double
    jsIsArray(value) -> (value as Array<dynamic>).map { jsToKotlin(it) }
    jsTypeOf(value) == "object" -> {
        val map = LinkedHashMap<String, Any?>()
        for (key in jsKeys(value)) map[key] = jsToKotlin(value[key])
        map
    }
    else -> null
}

private fun jsIsArray(value: dynamic): Boolean = js("Array.isArray(value)") as Boolean

private fun jsKeys(value: dynamic): Array<String> = js("Object.keys(value)") as Array<String>

/** Render a result value for display: strings quoted, lists and objects bracketed. */
private fun render(value: Any?): String = when (value) {
    null -> "null"
    is String -> "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    is Double -> value.toString()
    is Boolean -> value.toString()
    is List<*> -> value.joinToString(", ", "[", "]") { render(it) }
    is Map<*, *> -> value.entries.joinToString(", ", "{", "}") { (k, v) -> "$k: ${render(v)}" }
    else -> value.toString()
}
