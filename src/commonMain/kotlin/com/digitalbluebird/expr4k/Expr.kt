package com.digitalbluebird.expr4k

/**
 * A compiled expression: lexed, parsed and type-checked once, then evaluated against a context any
 * number of times.
 *
 * ```
 * val expr = Expr.compile("age >= 18 && country in ['UK', 'IE']")
 * val ok = expr.evalBoolean(mapOf("age" to 21, "country" to "UK"))
 * ```
 *
 * [compile] runs the whole front end; any failure is an [Expr4kException] pointing at the source.
 * Evaluation coerces context numbers to the single number type and enforces types, so a well-typed
 * expression against a matching context cannot surprise you.
 *
 * A compiled [Expr] is immutable and holds no evaluation state, so it is safe to cache and to
 * evaluate from multiple threads at once.
 */
public class Expr private constructor(
    private val root: Node,
    private val source: String,
    /** The statically inferred [Type] of this expression; [Type.ANY] if it could not be pinned down. */
    public val type: Type,
) {
    /** Evaluate against [context], returning the raw value: `Double`, `String`, `Boolean`, `null`, list or object. */
    public fun eval(context: Map<String, Any?> = emptyMap()): Any? = Evaluator(context).eval(root)

    /** Evaluate and require a boolean result, else [EvalException]. The common case for a predicate. */
    public fun evalBoolean(context: Map<String, Any?> = emptyMap()): Boolean {
        val value = eval(context)
        return value as? Boolean ?: throw wrongResult("boolean", value)
    }

    /** Evaluate and require a number result, else [EvalException]. */
    public fun evalNumber(context: Map<String, Any?> = emptyMap()): Double {
        val value = eval(context)
        return value as? Double ?: throw wrongResult("number", value)
    }

    /** Evaluate and require a string result, else [EvalException]. */
    public fun evalString(context: Map<String, Any?> = emptyMap()): String {
        val value = eval(context)
        return value as? String ?: throw wrongResult("string", value)
    }

    private fun wrongResult(expected: String, got: Any?): EvalException =
        EvalException("Expected a $expected result but got ${typeName(got)}", root.pos)

    override fun toString(): String = "Expr($source)"

    public companion object {
        /**
         * Compile [source] into an [Expr], type-checking against an optional [schema] (variable name
         * to [Type]); names absent from the schema are [Type.ANY]. Throws [Expr4kException] on any
         * lexing, parsing or type error.
         */
        public fun compile(source: String, schema: Map<String, Type> = emptyMap()): Expr {
            val root = Parser(Lexer(source).tokenize()).parse()
            val type = TypeChecker(schema).check(root)
            return Expr(root, source, type)
        }
    }
}
