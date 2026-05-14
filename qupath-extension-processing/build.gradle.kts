plugins {
  id("qupath.extension-conventions")
  id("qupath.publishing-conventions")
  id("qupath.javafx-conventions")
  `java-library`
}

extra["moduleName"] = "qupath.extension.processing"
base {
  archivesName = "qupath-extension-processing"
  description = "QuPath extension to support processing (including many common commands)."
}

dependencies {
  implementation(libs.qupath.fxtras)
  implementation(libs.commons.math)
}
