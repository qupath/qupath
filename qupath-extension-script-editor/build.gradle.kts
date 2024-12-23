plugins {
  id("qupath.extension-conventions")
  id("qupath.javafx-conventions")
  id("qupath.publishing-conventions")
  `java-library`
}

extra["moduleName"] = "qupath.extension.scripteditor"

base {
  archivesName = "qupath-extension-script-editor"
  description = "QuPath extension to provide an alternative script editor using RichTextFX."
}

dependencies {
  implementation(libs.qupath.fxtras)
  implementation(libs.bundles.markdown)
  implementation(libs.richtextfx)
  implementation(libs.snakeyaml)
  implementation(libs.guava)
}