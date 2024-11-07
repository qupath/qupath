plugins {
  // Don't need extension-conventions because we don't require access to the UI
  id("qupath.common-conventions")
  `java-library`
  alias(libs.plugins.javafx)
}

extra["moduleName"] = "qupath.extension.openslide"
base {
    archivesName = "qupath-extension-openslide"
    description = "QuPath extension to support image reading using OpenSlide."
}

val nativesClassifier = properties["platform.classifier"].toString().replace("natives-", "")
dependencies {
    implementation(project(":qupath-core"))
    implementation(project(":qupath-gui-fx"))
    implementation("io.github.qupath:openslide:${libs.versions.openslide.get()}:${nativesClassifier}")
    implementation(libs.jna)
    implementation(libs.qupath.fxtras)
}
