plugins {
  id("qupath.extension-conventions")
  `java-library`
  alias(libs.plugins.javafx)
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
