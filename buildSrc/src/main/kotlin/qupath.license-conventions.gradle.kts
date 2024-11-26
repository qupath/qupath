/**
 * Conventions for the dependency license report plugin,
 * which helps assemble a list of third-party dependencies and their licenses
 * for inclusion in the QuPath distribution.
 */

import com.github.jk1.license.render.*

plugins {
    id("com.github.jk1.dependency-license-report")
}

/**
 * Create license report
 */
licenseReport {
    val fileUnknown = rootProject.file("unknown-license-details.txt")
    renderers = arrayOf<ReportRenderer>(
        TextReportRenderer("THIRD-PARTY.txt"),
        InventoryHtmlReportRenderer("index.html", "Third party licenses", fileUnknown))

    outputDir = rootProject.layout.buildDirectory.dir("reports/dependency-license").get().asFile.absolutePath

    // TODO: Try to remove this. It's needed (I think) due to the license plugin not supporting
    //       Gradle variants, as required by the JavaFX Gradle Plugin v0.1.0. Possibly-relevant links:
    //       - https://github.com/openjfx/javafx-gradle-plugin#variants
    //       - https://github.com/jk1/Gradle-License-Report/issues/199
    //       The JavaFX license is still included in QuPath, but unfortunately not in this report.
    excludeGroups = arrayOf("org.openjfx")
}