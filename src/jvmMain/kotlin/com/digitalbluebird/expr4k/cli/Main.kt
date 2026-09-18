package com.digitalbluebird.expr4k.cli

import com.digitalbluebird.expr4k.Expr
import com.digitalbluebird.expr4k.Expr4kException
import com.digitalbluebird.expr4k.Type
import kotlin.system.exitProcess

/**
 * A REPL session: feed it one input line at a time with [handle] and it returns the text to print.
 *
 * Variables accumulate across lines via `:let`, and each bare expression is type-checked against the
 * current bindings before it runs, so type errors are reported with the same messages the library
 * gives. Kept free of I/O so it can be driven directly by tests.
 */
internal class Session {
    private val context = mutableMapOf<String, Any?>()

    /** Handle one input line and return the output (empty string means "print nothing"). */
    fun handle(line: String): String {
        val trimmed = line.trim()
        return when {
            trimmed.isEmpty() -> ""
            trimmed.startsWith(":") -> command(trimmed)
            else -> evaluate(trimmed)
        }
    }

    private fun evaluate(source: String): String = tryExpr {
        val expr = Expr.compile(source, schema())
        "${render(expr.eval(context))} : ${expr.type.name.lowercase()}"
    }

    private fun command(line: String): String {
        val parts = line.split(Regex("\\s+"), limit = 3)
        return when (parts[0]) {
            ":let" ->
                if (parts.size < 3) "usage: :let <name> <expression>"
                else letBinding(parts[1], parts[2])
            ":type" -> {
                val source = line.removePrefix(":type").trim()
                if (source.isEmpty()) "usage: :type <expression>"
                else tryExpr { Expr.compile(source, schema()).type.name.lowercase() }
            }
            ":vars" ->
                if (context.isEmpty()) "(no variables bound)"
                else context.entries.joinToString("\n") { (name, value) ->
                    "$name = ${render(value)} : ${valueType(value).name.lowercase()}"
                }
            ":help" -> HELP
            else -> "unknown command '${parts[0]}' (try :help)"
        }
    }

    private fun letBinding(name: String, source: String): String = tryExpr {
        val value = Expr.compile(source, schema()).eval(context)
        context[name] = value
        "$name = ${render(value)} : ${valueType(value).name.lowercase()}"
    }

    /** A schema for the current bindings, so bare expressions get static type checking against them. */
    private fun schema(): Map<String, Type> = context.mapValues { (_, value) -> valueType(value) }

    private companion object {
        val HELP = """
            Enter an expression to evaluate it, e.g.  age >= 18 && country in ['UK', 'IE']
            Commands:
              :let <name> <expr>   bind a variable to the value of an expression
              :type <expr>         show the inferred type without evaluating
              :vars                list the bound variables
              :help                show this help
              :quit                leave the REPL
        """.trimIndent()

        /** Run [body], turning any expr4k error into a one-line message. */
        inline fun tryExpr(body: () -> String): String = try {
            body()
        } catch (e: Expr4kException) {
            "error: ${e.message}"
        }
    }
}

/** The static [Type] of a runtime value, for deriving a schema and labelling REPL output. */
private fun valueType(value: Any?): Type = when (value) {
    is Double -> Type.NUMBER
    is String -> Type.STRING
    is Boolean -> Type.BOOLEAN
    is List<*> -> Type.LIST
    is Map<*, *> -> Type.OBJECT
    else -> Type.ANY
}

/** Render a value for display: strings quoted, lists and objects bracketed, everything else as-is. */
private fun render(value: Any?): String = when (value) {
    null -> "null"
    is String -> "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    is List<*> -> value.joinToString(", ", "[", "]") { render(it) }
    is Map<*, *> -> value.entries.joinToString(", ", "{", "}") { (k, v) -> "$k: ${render(v)}" }
    else -> value.toString()
}

/** Entry point: with arguments, evaluate them as one expression; otherwise start the REPL. */
public fun main(args: Array<String>) {
    if (args.isNotEmpty()) {
        runOnce(args.joinToString(" "))
        return
    }
    repl()
}

/** Evaluate a single expression against an empty context, printing the value or exiting non-zero. */
private fun runOnce(source: String) {
    try {
        println(render(Expr.compile(source).eval()))
    } catch (e: Expr4kException) {
        System.err.println("error: ${e.message}")
        exitProcess(1)
    }
}

private fun repl() {
    val session = Session()
    println("expr4k REPL — enter an expression, :help for commands, :quit to exit")
    while (true) {
        print("> ")
        val line = readlnOrNull() ?: break
        if (line.trim() in setOf(":quit", ":q", ":exit")) break
        val output = session.handle(line)
        if (output.isNotEmpty()) println(output)
    }
}
