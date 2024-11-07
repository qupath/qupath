plugins {
  id("qupath.extension-conventions")
  `java-library`
  alias(libs.plugins.javafx)
}

extra["moduleName"] = "qupath.extension.scripteditor"

base {
  archivesName = "qupath-extension-script-editor"
  description = "QuPath extension to provide an alternative script editor using RichTextFX."
}

dependencies {
  implementation(libs.qupath.fxtras)
  implementation(libs.bundles.groovy)
  implementation(libs.bundles.markdown)
  implementation(libs.richtextfx)
  implementation(libs.snakeyaml)
  implementation(libs.guava)
}