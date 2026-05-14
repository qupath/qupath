plugins {
  id("qupath.extension-conventions")
  id("qupath.javafx-conventions")
  id("qupath.publishing-conventions")
  `java-library`
}

extra["moduleName"] = "qupath.extension.svg"
base {
  archivesName = "qupath-extension-svg"
  description = "QuPath extension to write SVG images using JFreeSVG."
}

dependencies {
  implementation(libs.qupath.fxtras)
  implementation(libs.jfreesvg)
}
