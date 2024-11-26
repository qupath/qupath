/**
 * Conventions for modules that require JavaFX.
 * This includes a list of the JavaFX modules to make available.
 */

import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.kotlin.dsl.the

plugins {
    id("org.openjfx.javafxplugin")
}

val libs = the<LibrariesForLibs>()

// Required since moving to JavaFX Gradle Plugin v0.1.0
javafx {
    version = libs.versions.javafx.get()
    modules(
        "javafx.base",
        "javafx.controls",
        "javafx.graphics",
        "javafx.media",
        "javafx.fxml",
        "javafx.web",
        "javafx.swing"
    )
}