import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    kotlin("jvm")
    id("org.jetbrains.intellij.platform")
    id("org.jlleitschuh.gradle.ktlint")
    id("dev.detekt")
    id("org.jetbrains.kotlinx.kover")
}

group = "dev.happs"
version = "0.1.0"

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2025.2.6.2")
        bundledPlugin("Git4Idea")
        testFramework(TestFrameworkType.Platform)
    }

    testImplementation(kotlin("test"))
    testRuntimeOnly("junit:junit:4.13.2")
}

kotlin {
    jvmToolchain(21)
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
    jvmTarget.set("21")
}

tasks.withType<dev.detekt.gradle.DetektCreateBaselineTask>().configureEach {
    jvmTarget.set("21")
}

kover {
    reports {
        filters {
            excludes {
                classes("dev.happs.aigitassistant.action.*")
            }
        }
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

intellijPlatform {
    pluginConfiguration {
//        name = "AI Git Workflow Assistant"
        version = project.version.toString()
        description =
            """
            AI Git Workflow Assistant is a small IntelliJ Platform plugin that will help developers
            prepare Git changes with AI-assisted commit messages, branch names, and change summaries.
            """.trimIndent()

        ideaVersion {
            sinceBuild = "252"
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
