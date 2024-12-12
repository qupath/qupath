/**
 * This configures the application extension, used when calling
 * ./gradlew run as well as when building packages.
 */

import org.gradle.kotlin.dsl.extra

plugins {
    application
}

application {
    mainClass = "qupath.QuPath"

    val qupathAppName = gradle.extra["qupath.app.name"] as String

    applicationName = qupathAppName
    applicationDefaultJvmArgs += listOf(gradle.extra["qupath.jvm.args"] as String)

    // Necessary when using ./gradlew run to support style manager to change themes
    applicationDefaultJvmArgs += "--add-opens"
    applicationDefaultJvmArgs += "javafx.graphics/com.sun.javafx.css=ALL-UNNAMED"

    // Necessary when using ./gradlew run to support project metadata autocomplete
    // See https://github.com/controlsfx/controlsfx/issues/1505
    applicationDefaultJvmArgs += "--add-opens"
    applicationDefaultJvmArgs += "javafx.base/com.sun.javafx.event=ALL-UNNAMED"

}