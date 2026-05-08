// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    val kotlin_version = "1.9.23"

    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        classpath("com.android.tools.build:gradle:8.13.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version")
        classpath("androidx.navigation:navigation-safe-args-gradle-plugin:2.6.0")
        classpath("com.google.dagger:hilt-android-gradle-plugin:2.48")
    }
}

plugins {
    id("com.diffplug.spotless") version ("6.12.0")
    id("org.jetbrains.kotlin.jvm") version "1.9.23" apply false
}

allprojects {
    tasks.withType<JavaCompile>().configureEach {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = "17"
        }
    }

    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }

    apply {
        plugin("com.diffplug.spotless")
    }

    spotless {

        format("misc") {
            target("**/*.gradle', '**/*.md', '**/.gitignore")
            indentWithSpaces()
            trimTrailingWhitespace()
            endWithNewline()
        }

        kotlin {
            target("**/*.kt")
            targetExclude("**/build/**/*.kt")
            targetExclude("bin/**/*.kt")
            trimTrailingWhitespace()
            indentWithSpaces()
            endWithNewline()
            ktlint("0.47.1").editorConfigOverride(mapOf("ktlint_disabled_rules" to "no-wildcard-imports"))
        }
    }
}

tasks.register(name = "type", type = Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
