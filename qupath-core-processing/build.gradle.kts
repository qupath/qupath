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

configurations {
  api {
    extendsFrom(opencv.get())
  }
  implementation {
    extendsFrom(guava.get())
  }
}

dependencies {
  api(project(":qupath-core"))
  
  api(libs.imagej)
  api(libs.bioimageio.spec)
  
  implementation(libs.commons.math)
  testImplementation(project.project(":qupath-core").sourceSets["test"].output)
}