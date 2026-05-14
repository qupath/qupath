plugins {
  id("qupath.common-conventions")
  id("qupath.publishing-conventions")
  `java-library`
}

extra["moduleName"] = "qupath.core.processing"
base {
  archivesName = "qupath-core-processing"
  description = "Core QuPath module containing the main processing operations."
}

dependencies {
  api(project(":qupath-core"))

  api(libs.opencv)
  api(libs.imagej)
  api(libs.bioimageio.spec)

  implementation(libs.guava)
  implementation(libs.commons.math)
  testImplementation(project.project(":qupath-core").sourceSets["test"].output)
}