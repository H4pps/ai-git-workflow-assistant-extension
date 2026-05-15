plugins {
    kotlin("jvm") version "2.3.20"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("dev.detekt") version "2.0.0-alpha.3"
    id("org.jetbrains.kotlinx.kover") version "0.9.8"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(25)
}

ktlint {
    filter {
        exclude("**/generated/**")
    }
}

detekt {
    toolVersion = "2.0.0-alpha.3"
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    basePath.set(rootDir)
}

tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
    jvmTarget.set("25")
}

tasks.withType<dev.detekt.gradle.DetektCreateBaselineTask>().configureEach {
    jvmTarget.set("25")
}

kover {
    reports {
        total {
            html {
                onCheck = true
            }
            xml {
                onCheck = true
            }
        }
        verify {
            rule("Minimum line coverage") {
                minBound(80)
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.named("check") {
    dependsOn("ktlintCheck")
}

tasks.register("qualityCheck") {
    group = "verification"
    description = "Runs tests, coverage verification, Kotlin style checks, and static analysis."
    dependsOn("test", "koverVerify", "ktlintCheck", "detekt")
}

tasks.register("qualityFormat") {
    group = "formatting"
    description = "Formats Kotlin source and Gradle Kotlin scripts with ktlint."
    dependsOn("ktlintFormat")
}

tasks.register("autoFormat") {
    group = "formatting"
    description = "Auto-formats all Kotlin source and Gradle Kotlin scripts."
    dependsOn("qualityFormat")
}

tasks.register("format") {
    group = "formatting"
    description = "Alias for autoFormat."
    dependsOn("autoFormat")
}

tasks.register("coverageCheck") {
    group = "verification"
    description = "Runs tests and verifies minimum coverage."
    dependsOn("koverVerify")
}

tasks.register("coverageReport") {
    group = "verification"
    description = "Generates HTML and XML coverage reports."
    dependsOn("koverHtmlReport", "koverXmlReport")
}
