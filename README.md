# expr4k

A small, safe, **typed expression language** for the JVM, written in Kotlin.

Evaluate expressions like `age >= 18 && country in ["UK", "IE"]` against a context — with **no code
injection**, type errors caught *before* evaluation, and clear messages that point at the source.

> **Status: early / work in progress.** The lexer is in, with tests. The Pratt parser, type-checker
> and tree-walking evaluator are next. Not yet published.

## Why

Rule engines, feature-flag conditions and search filters keep needing to "evaluate this little
expression, safely". The usual options (SpEL, MVEL, JEXL) are heavy, reflection-based, or unsafe.
`expr4k` aims to be **small, safe by construction** (no arbitrary code execution), **typed**, and
pleasant to embed.

## Example (target API)

```kotlin
val expr = Expr.compile("age >= 18 && country in ['UK','IE']")   // parse + type-check once
val ok: Boolean = expr.evalBoolean(mapOf("age" to 21, "country" to "UK"))
```

## Design

A hand-written pipeline, no parser generator — owning the parser is the point:

```
source ──▶ Lexer ──▶ tokens ──▶ Pratt parser ──▶ typed AST ──▶ type check ──▶ evaluator ──▶ value
```

## Building

```bash
./gradlew build
```

Requires a JDK; the Gradle wrapper fetches the rest.

## Licence

[MIT](LICENSE).
