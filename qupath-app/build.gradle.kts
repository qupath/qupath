/**
 * Build launcher for the main QuPath application.
 */

plugins {
    id("qupath.common-conventions")
    id("qupath.javafx-conventions")
    id("qupath.publishing-conventions")
}


extra["moduleName"] = "qupath.app"

base {
    archivesName = "qupath-app"
    description = "Main QuPath application."
}

dependencies {
    implementation(project(":qupath-gui-fx"))
    implementation(libs.picocli)

    implementation(libs.extensionmanager)
}

/**
 * Add main class to the jar.
 */
java {
    manifest {
        val manifestAttributes = mapOf(
            // Calling this too early is a problem - we need the JavaCPP plugin to have already sorted out
            // the classpath, otherwise we risk bringing in dependencies for all platforms
            // TODO: Consider whether to specify the classpath here (we did before v0.6.0)
//            "Class-Path" to configurations.runtimeClasspath.get().joinToString(separator = " ") { it.name },
            "Main-Class" to "qupath.QuPath"
        )
        attributes(manifestAttributes)
    }
}
