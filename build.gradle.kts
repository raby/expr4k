plugins {
    alias(libs.plugins.kotlin.multiplatform)
    `maven-publish`
}

group = "com.digitalbluebird"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    explicitApi()
    jvmToolchain(21)

    jvm()

    js {
        moduleName = "expr4k"
        browser()
        binaries.executable() // webpack a browser bundle exposing the @JsExport playground API
    }

    sourceSets {
        // The library (commonMain) is pure Kotlin stdlib, so it compiles to every target.
        jvmTest.dependencies {
            implementation(kotlin("test-junit5"))
            implementation(libs.assertk)
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

// The CLI and benchmark are JVM-only; run them off the JVM compilations directly.
val jvmMainOutput = kotlin.jvm().compilations.getByName("main")
val jvmTestOutput = kotlin.jvm().compilations.getByName("test")

tasks.register<JavaExec>("runCli") {
    group = "application"
    description = "Run the expr4k REPL/CLI (add --args=\"<expression>\" for one-shot mode)."
    classpath = files(jvmMainOutput.output.allOutputs, jvmMainOutput.runtimeDependencyFiles)
    mainClass.set("com.digitalbluebird.expr4k.cli.MainKt")
    standardInput = System.`in`
}

tasks.register<JavaExec>("benchmark") {
    group = "verification"
    description = "Run the indicative expr4k microbenchmark."
    classpath = files(
        jvmTestOutput.output.allOutputs,
        jvmMainOutput.output.allOutputs,
        jvmTestOutput.runtimeDependencyFiles,
    )
    mainClass.set("com.digitalbluebird.expr4k.benchmark.BenchmarkKt")
}
