# expr4k

A small, safe, **typed expression language** for the JVM and the browser, written in Kotlin
Multiplatform.

Evaluate expressions like `age >= 18 && country in ["UK", "IE"]` against a context — with **no code
injection**, type errors caught *before* evaluation, and clear messages that point at the source.

> **Status: working, pre-release.** The full pipeline is in with a public `Expr` API — lexer,
> parser, static type checker and tree-walking evaluator, all tested, with a REPL/CLI. The same code
> compiles to JVM and JS (Kotlin Multiplatform); a browser playground is next. Not yet published to
> Maven Central.

## Why

Rule engines, feature-flag conditions and search filters keep needing to "evaluate this little
expression, safely". The usual options (SpEL, MVEL, JEXL) are heavy, reflection-based, or unsafe.
`expr4k` aims to be **small, safe by construction** (no arbitrary code execution), **typed**, and
pleasant to embed.

## Example

```kotlin
// compile once (lex + parse + type-check), evaluate many times
val expr = Expr.compile("age >= 18 && country in ['UK', 'IE']")
val ok: Boolean = expr.evalBoolean(mapOf("age" to 21, "country" to "UK"))   // true
```

Pass a **schema** to turn type mismatches into compile-time errors instead of runtime ones:

```kotlin
Expr.compile("age && true", mapOf("age" to Type.NUMBER))
// throws TypeException: operator '&&' expects a boolean but got number at 1:1
```

Every failure — lexing, parsing, type checking or evaluation — is an `Expr4kException` carrying the
source position it refers to. A compiled `Expr` is immutable and safe to cache and share across
threads.

## CLI

A small REPL and one-shot evaluator ship alongside the library:

```bash
./gradlew runCli --args="2 + 3 * 4"    # one-shot → 14.0

./gradlew runCli                       # REPL
> :let age 21
age = 21.0 : number
> age >= 18 && age < 65
true : boolean
> :help                                # :let, :type, :vars, :quit
```

## Design

A hand-written pipeline, no parser generator — owning the parser is the point:

```
source ──▶ Lexer ──▶ tokens ──▶ Pratt parser ──▶ typed AST ──▶ type check ──▶ evaluator ──▶ value
```

## Benchmark

`./gradlew benchmark` runs an indicative harness (JIT warmup, best of five rounds, results kept live
to defeat dead-code elimination). Compiling a predicate once and reusing it is far cheaper than
re-parsing on every evaluation:

| approach | ns / eval | throughput |
| --- | ---: | ---: |
| compile once, eval many | ~90 | ~11M / sec |
| compile on every eval | ~1,520 | ~0.66M / sec |

So reusing a compiled `Expr` is roughly **17x faster per evaluation**. (Figures are indicative and
machine-dependent — run it yourself.)

## Building

```bash
./gradlew build
```

Requires a JDK; the Gradle wrapper fetches the rest.

## Licence

[MIT](LICENSE).
