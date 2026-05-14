plugins {
  id("qupath.common-conventions")
  id("qupath.publishing-conventions")
  `java-library`
}

extra["moduleName"] = "qupath.core"
base {
  archivesName = "qupath-core"
  description = "Core QuPath module containing the main classes and data structures."
}

dependencies {
  api(libs.gson)
  api(libs.jts)

  implementation(libs.guava)
  implementation(libs.commons.math)
  implementation(libs.picocli)
  implementation(libs.imagej)
}