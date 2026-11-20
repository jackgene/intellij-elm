import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

fun properties(key: String) = project.findProperty(key).toString()

plugins {
    // Java support
    id("java")
    // Kotlin support
    id("org.jetbrains.kotlin.jvm") version "2.4.21"
    // IntelliJ Platform Gradle Plugin
    id("org.jetbrains.intellij.platform") version "2.20.1"
    // GrammarKit Plugin
    id("org.jetbrains.grammarkit") version "2023.3.0.4"
    // Gradle Changelog Plugin
    id("org.jetbrains.changelog") version "2.5.0"
}

group = properties("pluginGroup")
version = properties("pluginVersion")

// Configure project's dependencies
repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("com.github.ajalt.colormath:colormath:2.1.0")

    testImplementation("org.jetbrains.kotlin:kotlin-test:2.4.21")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")

    intellijPlatform {
        intellijIdea(properties("platformVersion"))
        // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file.
        bundledPlugins(properties("platformPlugins").split(',').map(String::trim).filter(String::isNotEmpty))
        bundledModules(
            "intellij.spellchecker",
            "intellij.libraries.jackson",
            "intellij.libraries.jackson.databind",
        )
        testFramework(TestFrameworkType.Platform)
        // The spell checking inspection lives in Grazie
        testBundledPlugin("tanvd.grazi")
    }
}

// Configure IntelliJ Platform Gradle Plugin - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
intellijPlatform {
    pluginConfiguration {
        name = properties("pluginName")
        version = properties("pluginVersion")
        // Change notes are maintained by hand in `plugin.xml`.
        changeNotes = provider { null }

        ideaVersion {
            sinceBuild = properties("pluginSinceBuild")
            untilBuild = provider { null }
        }

        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
        description = projectDir.resolve("README.md").readText().lines().run {
            val start = "<!-- Plugin description -->"
            val end = "<!-- Plugin description end -->"

            if (!containsAll(listOf(start, end))) {
                throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
            }
            subList(indexOf(start) + 1, indexOf(end))
        }.joinToString("\n").run { markdownToHTML(this) }
    }

    pluginVerification {
        ides {
            current()
            create(IntelliJPlatformType.IntellijIdea, "2026.3")
        }
    }
}

// Configure Gradle Changelog Plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
changelog {
    version.set(properties("pluginVersion"))
    groups.set(emptyList())
}

val generateGrammars = tasks.register("generateGrammars") {
    dependsOn("generateParser", "generateLexer")
}

tasks.withType<KotlinCompile> {
    dependsOn(generateGrammars)
}

// This was needed on the `clojj-master` branch for the tests to pass
tasks.withType<Test> {
    jvmArgs(
        "--add-exports=java.base/jdk.internal.vm=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
        "--add-opens=java.base/java.lang.ref=ALL-UNNAMED",
        "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
        // "--add-opens=java.base/java.security=ALL-UNNAMED", // saw this once, did not see it again
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
        "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED",
        "--add-opens=java.base/java.util.concurrent.locks=ALL-UNNAMED",
        "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
        "--add-opens=java.desktop/java.awt.event=ALL-UNNAMED",
        "--add-opens=java.desktop/java.beans=ALL-UNNAMED",
        "--add-opens=java.desktop/javax.swing=ALL-UNNAMED",
        "--add-opens=java.desktop/javax.swing.plaf.basic=ALL-UNNAMED",
        "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
        "--add-opens=java.desktop/sun.font=ALL-UNNAMED"
    )
}

tasks {
    sourceSets {
        java.sourceSets["main"].java {
            srcDir("src/main/gen")
        }
    }

    // Set the JVM compatibility versions
    properties("javaVersion").let {
        withType<JavaCompile> {
            sourceCompatibility = it
            targetCompatibility = it
        }
        withType<KotlinCompile> {
            compilerOptions.jvmTarget.set(JvmTarget.fromTarget(it))
        }
    }

    wrapper {
        gradleVersion = properties("gradleVersion")
    }

    generateLexer {
        // ("generateElmLexer") {
        sourceFile.set(file("$projectDir/src/main/grammars/ElmLexer.flex"))
        skeleton.set(file("$projectDir/src/main/grammars/lexer.skeleton"))
        targetOutputDir.set(file("$projectDir/src/main/gen/org/elm/lang/core/lexer/"))
        purgeOldFiles.set(true)
    }

    generateParser {
        //("generateElmParser") {
        sourceFile.set(file("$projectDir/src/main/grammars/ElmParser.bnf"))
        targetRootOutputDir.set(file("$projectDir/src/main/gen"))
        pathToParser.set("/org/elm/lang/core/parser/ElmParser.java")
        pathToPsiRoot.set("/org/elm/lang/core/psi")
        purgeOldFiles.set(true)
    }

}
