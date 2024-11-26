/**
 * Build QuPath application.
 * This involves creating a jpackage task.
 */

import com.github.jk1.license.render.*

buildscript {
    repositories {
        maven {
            url = uri("https://plugins.gradle.org/m2/")
        }
    }
}

plugins {
    id("qupath.common-conventions")
    id("qupath.djl-conventions")
    id("qupath.jpackage-conventions")
    id("qupath.javafx-conventions")
    alias(libs.plugins.license.report)
}


extra["moduleName"] = "qupath.app"
base {
    archivesName = "qupath-app"
    description = "Main QuPath application."
}

/**
 * Determine which projects to include/exclude as dependencies
 */
val excludedProjects = listOf(project)
val includedProjects = rootProject.subprojects.filter { !excludedProjects.contains(it) }

dependencies {
    includedProjects.forEach {
        implementation(it)
    }
    implementation(libs.picocli)

    implementation(extraLibs.bundles.extensions) {
        // We don't want to bring in snapshot versions
        exclude(group="io.github.qupath")
    }
}


// Put the output in the main directory so it is easier to find
//project.buildDir = rootProject.file("build")

application {
    mainClass = "qupath.QuPath"

    val qupathAppName = gradle.extra["qupath.app.name"] as String

    applicationName = qupathAppName
    applicationDefaultJvmArgs = listOf(gradle.extra["qupath.jvm.args"] as String)

    // Necessary when using ./gradlew run to support style manager to change themes
    applicationDefaultJvmArgs += "--add-opens"
    applicationDefaultJvmArgs += "javafx.graphics/com.sun.javafx.css=ALL-UNNAMED"

    // Necessary when using ./gradlew run to support project metadata autocomplete
    // See https://github.com/controlsfx/controlsfx/issues/1505
    applicationDefaultJvmArgs += "--add-opens"
    applicationDefaultJvmArgs += "javafx.base/com.sun.javafx.event=ALL-UNNAMED"
}

/**
 * Add classpath and main class to make it easier to launch from jar
 * TODO: Check this: previously used afterEvaluate { jar { ... }  }
 */
java {
    manifest {
        val manifestAttributes = mapOf(
            // Calling this too early is a problem - we need the JavaCPP plugin to have already sorted out
            // the classpath, otherwise we risk bringing in dependencies for all platforms
//            "Class-Path" to configurations.runtimeClasspath.get().joinToString(separator = " ") { it.name },
            "Main-Class" to "qupath.QuPath"
        )
        attributes(manifestAttributes)
    }
}


/**
 * Copies the Javadoc jars to a directory for access within QuPath
 */
tasks.register<Copy>("assembleJavadocs") {
    group = "documentation"
    description = "Copies the Javadoc jars to a directory for access within QuPath"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    // Always include jars developed by all subprojects
    rootProject.subprojects.forEach {
        from(it.tasks.javadocJar)
    }

    // Also include selected dependency jars
    val dependenciesJavadoc = dependencies.createArtifactResolutionQuery()
            .forComponents(configurations
                    .runtimeClasspath
                    .get()
                    .incoming
                    .resolutionResult
                    .allDependencies
                    .filterIsInstance<ResolvedDependencyResult>()
                    .map { it.selected.id }
            )
            .withArtifacts(JvmLibrary::class, SourcesArtifact::class, JavadocArtifact::class)
            .execute()
            .resolvedComponents
            .map {
                c -> c.getArtifacts(JavadocArtifact::class)
                    .filterIsInstance<ResolvedArtifactResult>()
                    .map {it.file}
            }
            .flatten()
            .filter {
                val docs = findProperty("docs") ?: "default"
                when (docs) {
                    "all" -> return@filter true
                    "none" -> return@filter false
                    "qupath" -> return@filter it.name.startsWith("qupath")
                    else -> return@filter it.name.startsWith("qupath") ||
                            it.name.startsWith("jts")  || it.name.startsWith("ij")
                }
            }

    dependenciesJavadoc.forEach {
        from(it)
    }

    into(layout.buildDirectory.dir("javadocs"))
}
tasks.distZip {
    enabled = false
}
tasks.distTar {
    enabled = false
}
tasks.installDist {
    dependsOn("assembleJavadocs")
}

/**
 * Create license report
 */
licenseReport {
    val fileUnknown = rootProject.file("unknown-license-details.txt")
    renderers = arrayOf<ReportRenderer>(TextReportRenderer("THIRD-PARTY.txt"),
        InventoryHtmlReportRenderer("index.html", "Third party licenses", fileUnknown))

    outputDir = rootProject.layout.buildDirectory.dir("reports/dependency-license").get().asFile.absolutePath

    // TODO: Try to remove this. It's needed (I think) due to the license plugin not supporting
    //       Gradle variants, as required by the JavaFX Gradle Plugin v0.1.0. Possibly-relevant links:
    //       - https://github.com/openjfx/javafx-gradle-plugin#variants
    //       - https://github.com/jk1/Gradle-License-Report/issues/199
    //       The JavaFX license is still included in QuPath, but unfortunately not in this report.
    excludeGroups = arrayOf("org.openjfx")
}
tasks.startScripts {
    dependsOn("generateLicenseReport")
}


/**
 * Copy key files into the distribution
 */
distributions {
    main {
        contents {
            // We need a DuplicatesStrategy if settings.gradle.kts uses includeFlat for extra extensions
            // (which require QuPath as a dependency)
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            into("lib") {
                from(project.rootDir)
                include("CHANGELOG.md")
                include("STARTUP.md")
                include("LICENSE")
            }
            // Get the core licenses associated with the app
            into("lib") {
                from(".")
                include("licenses/**")
            }
            // Check if we have licenses stored with other extensions,
            // either directly in the project directory or under "resources"
            into("lib") {
                includedProjects.forEach {
                    from(it.projectDir)
                    from(File(it.projectDir, "src/main/resources"))
                    include("licenses/**")
                    includeEmptyDirs = false
                }
            }
            // Copy license report
            into("lib/licenses") {
                from(rootProject.layout.buildDirectory.dir("reports/dependency-license"))
                include("THIRD-PARTY.txt")
            }
            // Copy javadocs
            into("lib/docs") {
                from(project.layout.buildDirectory.dir("javadocs").get())
                include("*.jar")
            }
        }
    }
}
