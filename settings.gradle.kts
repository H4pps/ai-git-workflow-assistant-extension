import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

pluginManagement {
    plugins {
        kotlin("jvm") version "2.3.20"
        id("org.jetbrains.intellij.platform") version "2.16.0"
        id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
        id("dev.detekt") version "2.0.0-alpha.3"
        id("org.jetbrains.kotlinx.kover") version "0.9.8"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("org.jetbrains.intellij.platform.settings") version "2.16.0"
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        intellijPlatform {
            defaultRepositories()
        }
    }
}

rootProject.name = "ai-git-workflow-assistant-extension"
