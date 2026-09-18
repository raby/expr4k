plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
    `maven-publish`
    application
}

group = "com.digitalbluebird"
version = "0.1.0-SNAPSHOT"

// A small REPL / one-shot CLI ships alongside the library. Extract into a separate :cli module before
// publishing if the library artifact should stay free of the entry point.
application {
    mainClass = "com.digitalbluebird.expr4k.cli.MainKt"
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
    explicitApi()
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.assertk)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
