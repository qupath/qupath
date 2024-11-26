/**
 * Conventions for modules that require JavaFX.
 * This includes a list of the JavaFX modules to make available.
 */

import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("org.openjfx.javafxplugin")
}

val libs = the<LibrariesForLibs>()

// Required since moving to JavaFX Gradle Plugin v0.1.0
javafx {
    version = libs.versions.javafx.get()
    modules = listOf(
        "javafx.base",
        "javafx.controls",
        "javafx.graphics",
        "javafx.media",
        "javafx.fxml",
        "javafx.web",
        "javafx.swing"
    )
}

// javafxplugin 0.1.0 is not compatible with configuration cache
// See https://github.com/openjfx/javafx-gradle-plugin/issues/136
plugins.withId("application") {
    tasks.named(ApplicationPlugin.TASK_RUN_NAME, JavaExec::class.java).configure {
        notCompatibleWithConfigurationCache("JavaFX plugin does not support configuration cache")
    }
}
