package com.digitalbluebird.expr4k.benchmark

import com.digitalbluebird.expr4k.Expr
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import java.util.concurrent.TimeUnit

/**
 * JMH microbenchmark for the compile-once value proposition: evaluating a pre-compiled expression
 * against a context (the intended usage), versus lexing + parsing + type-checking + evaluating from
 * source on every call. The ratio between the two is the reason to hold onto a compiled [Expr]. JMH
 * returns each value to its Blackhole, so nothing is optimised away. Run with `./gradlew :benchmark:jmh`.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
open class ExprBenchmark {

    private val source = "age >= 18 && country in ['UK', 'IE'] && score >= 0.5"
    private val context = mapOf("age" to 21, "country" to "UK", "score" to 0.9)
    private lateinit var compiled: Expr

    @Setup
    fun setUp() {
        compiled = Expr.compile(source)
    }

    /** Evaluate a compiled expression — the hot path when one predicate is reused across many contexts. */
    @Benchmark
    fun evalCompiled(): Boolean = compiled.evalBoolean(context)

    /** Compile from source and evaluate every time — the cost avoided by reusing a compiled [Expr]. */
    @Benchmark
    fun compileAndEval(): Boolean = Expr.compile(source).evalBoolean(context)
}
