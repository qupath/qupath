import io.github.qupath.gradle.PlatformPlugin
import io.github.qupath.gradle.Utils

plugins {
  // Don't need extension-conventions because we don't require access to the UI
  id("qupath.common-conventions")
  id("qupath.javafx-conventions")
  `java-library`
}

extra["moduleName"] = "qupath.extension.openslide"
base {
    archivesName = "qupath-extension-openslide"
    description = "QuPath extension to support image reading using OpenSlide."
}

val platform = Utils.currentPlatform()
dependencies {
    implementation(project(":qupath-core"))
    implementation(project(":qupath-gui-fx"))
    if (platform == PlatformPlugin.Platform.LINUX_AARCH64) {
        println("OpenSlide native libraries not yet available for linux-arm64")
    } else {
        implementation("io.github.qupath:openslide:${libs.versions.openslide.get()}:${platform.classifier}")
    }
    implementation(libs.jna)
    implementation(libs.qupath.fxtras)
}
