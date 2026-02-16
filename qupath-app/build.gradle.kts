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
    // Main QuPath interface (brings in most other dependencies)
    implementation(project(":qupath-gui-fx"))

    // For command line
    implementation(libs.picocli)

    // For extensions
    implementation(libs.extensionmanager)

    // Bundled extensions
    implementation(libs.qupath.training)
    implementation(libs.qupath.djl)

    // For imglib2 support (optional - included here since no other dependency needs it)
    implementation(libs.qupath.imglib2)
    implementation(sciJava.imglib2.algorithm)
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
