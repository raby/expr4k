import com.vanniktech.maven.publish.SonatypeHost

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.maven.publish)
}

group = "com.digitalbluebird"
version = "0.1.0"

repositories {
    mavenCentral()
}

// Maven Central (Central Portal) publishing. The config is ready; the publish itself is user-driven:
// it needs a Sonatype Central account with the com.digitalbluebird namespace verified, a GPG signing
// key, and credentials (mavenCentralUsername / mavenCentralPassword and signing.* in
// ~/.gradle/gradle.properties or env vars). Central Portal takes releases only, so bump off -SNAPSHOT
// first. See PUBLISHING.md. Signing runs only on publish tasks, so `./gradlew build` needs no keys.
mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()
    coordinates(group.toString(), "expr4k", version.toString())
    pom {
        name.set("expr4k")
        description.set("A small, safe, typed expression language for the JVM and the browser.")
        url.set("https://github.com/raby/expr4k")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("raby")
                name.set("Raby Whyte")
                url.set("https://digitalbluebird.com")
            }
        }
        scm {
            url.set("https://github.com/raby/expr4k")
            connection.set("scm:git:https://github.com/raby/expr4k.git")
            developerConnection.set("scm:git:ssh://git@github.com/raby/expr4k.git")
        }
    }
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

// The CLI is JVM-only; run it off the JVM main compilation directly. (JMH benchmarks live in :benchmark.)
val jvmMainOutput = kotlin.jvm().compilations.getByName("main")

tasks.register<JavaExec>("runCli") {
    group = "application"
    description = "Run the expr4k REPL/CLI (add --args=\"<expression>\" for one-shot mode)."
    classpath = files(jvmMainOutput.output.allOutputs, jvmMainOutput.runtimeDependencyFiles)
    mainClass.set("com.digitalbluebird.expr4k.cli.MainKt")
    standardInput = System.`in`
}
