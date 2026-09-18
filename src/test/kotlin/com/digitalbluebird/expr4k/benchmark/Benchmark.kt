package com.digitalbluebird.expr4k.benchmark

import com.digitalbluebird.expr4k.Expr

/**
 * An indicative microbenchmark for the compile-once value proposition — not JMH, but careful about
 * the usual traps: it warms the JIT first, keeps every result live through a volatile [sink] so the
 * work cannot be optimised away, and reports the best of several rounds. Run with `./gradlew benchmark`.
 *
 * It contrasts two ways to evaluate one predicate against many contexts:
 *  - compile the expression once, then evaluate it repeatedly;
 *  - re-compile (lex + parse + type-check) on every evaluation.
 */
private const val WARMUP = 100_000
private const val ITERATIONS = 500_000
private const val ROUNDS = 5

@Volatile
private var sink: Any? = null

private fun blackhole(value: Any?) {
    sink = value
}

private inline fun best(block: () -> Unit): Long {
    var min = Long.MAX_VALUE
    repeat(ROUNDS) {
        val start = System.nanoTime()
        block()
        min = minOf(min, System.nanoTime() - start)
    }
    return min
}

private fun report(label: String, totalNs: Long) {
    val perOp = totalNs.toDouble() / ITERATIONS
    val opsPerSec = 1_000_000_000.0 / perOp
    println("%-26s %8.1f ns/op   %,13.0f ops/sec".format(label, perOp, opsPerSec))
}

fun main() {
    val source = "age >= 18 && country in ['UK', 'IE'] && score >= 0.5"
    val context = mapOf("age" to 21, "country" to "UK", "score" to 0.9)

    // warm up both paths so the JIT has compiled them before we measure
    repeat(WARMUP) { blackhole(Expr.compile(source).evalBoolean(context)) }
    val compiled = Expr.compile(source)
    repeat(WARMUP) { blackhole(compiled.evalBoolean(context)) }

    val evalNs = best {
        var acc = false
        repeat(ITERATIONS) { acc = acc xor compiled.evalBoolean(context) }
        blackhole(acc)
    }
    val compileEvalNs = best {
        var acc = false
        repeat(ITERATIONS) { acc = acc xor Expr.compile(source).evalBoolean(context) }
        blackhole(acc)
    }

    println("expr4k benchmark  (predicate: $source)")
    println("iterations per round: %,d   rounds: %d   (best shown)".format(ITERATIONS, ROUNDS))
    println()
    report("compile once, eval many", evalNs)
    report("compile on every eval", compileEvalNs)
    println()
    println("compiling once is %.1fx faster per evaluation".format(compileEvalNs.toDouble() / evalNs))
}
