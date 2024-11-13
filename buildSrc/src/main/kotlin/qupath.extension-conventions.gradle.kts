/**
 * Conventions for QuPath extensions.
 */

plugins {
    id("qupath.common-conventions")
}

// All extensions have access to the core modules
dependencies {
    implementation(project(":qupath-core"))
    implementation(project(":qupath-core-processing"))
    implementation(project(":qupath-gui-fx"))
}