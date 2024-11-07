plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.7.0" // to download if needed
}

gradle.extra["qupathVersion"] = "0.6.0-SNAPSHOT"

rootProject.name = "qupath"

// Main application
include("qupath-app")

// Core modules
include("qupath-core")
include("qupath-core-processing")
include("qupath-gui-fx")

// Core extension
include("qupath-extension-processing")

// Extra extensions
include("qupath-extension-svg")
include("qupath-extension-script-editor")
include("qupath-extension-openslide")
include("qupath-extension-bioformats")

// Support JavaFX dependency override
// This can be used to create a build for older versions of macOS
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            val javafxOverride = System.getProperties().getOrDefault("javafx-version", null)
            if (javafxOverride is String) {
                println("Overriding JavaFX version to request $javafxOverride")
                version("javafx", javafxOverride)
            }
        }

        // Extra version catalog for bundled extensions
        // This can be useful to make custom QuPath builds with specific versions of extensions
        create("extraLibs") {
            library("djl", "io.github.qupath", "qupath-extension-djl").version("0.4.0-20240911.172830-2")
            library("instanseg", "io.github.qupath", "qupath-extension-instanseg").version("0.0.1-20241020.174720-4")
            library("training", "io.github.qupath", "qupath-extension-training").version("0.0.1-20241022.065038-2")
            library("py4j", "io.github.qupath", "qupath-extension-py4j").version("0.1.0-20241021.201937-1")
            // Include or exclude bundled extensions
            bundle("extensions", listOf())
//            bundle("extensions", listOf("djl", "instanseg", "training", "py4j"))
        }
    }
}
