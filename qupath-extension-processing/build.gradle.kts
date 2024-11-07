plugins {
  id("qupath.extension-conventions")
  id("qupath.publishing-conventions")
  `java-library`
  alias(libs.plugins.javafx)
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
