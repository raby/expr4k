plugins {
    // No version: reuse the Kotlin Gradle Plugin the root's multiplatform plugin already put on the
    // shared build classpath (requesting a version here would clash with it).
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.jmh)
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // Consume the library's JVM variant. This module is build-only: it is never published.
    implementation(project(":"))
}

// JMH microbenchmarks live in src/jmh; run them with: ./gradlew :benchmark:jmh
jmh {
    jmhVersion.set("1.37")
    warmupIterations.set(3)
    iterations.set(5)
    fork.set(2)
    warmup.set("1s")
    timeOnIteration.set("1s")
}
