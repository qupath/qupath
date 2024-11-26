plugins {
  id("qupath.extension-conventions")
  id("qupath.publishing-conventions")
  id("qupath.javafx-conventions")
  `java-library`

}

extra["moduleName"] = "qupath.extension.bioformats"
base {
  archivesName = "qupath-extension-bioformats"
  description = "QuPath extension to support image reading and writing using Bio-Formats."
}

var bioformatsVersion = libs.versions.bioformats.get()
val versionOverride = project.properties["bioformats-version"]
if (versionOverride is String) {
	println("Using specified Bio-Formats version ${versionOverride}")
	bioformatsVersion = versionOverride
}

val nativesClassifier = properties["platform.classifier"]
if (nativesClassifier == "darwin-aarch64") {
	println("WARNING! Bio-Formats does not fully support Apple Silicon (many .czi and some .ndpi images are known to fail)")
}


dependencies {
	// This can be used to include bioformats_package.jar - however it causes warnings with SLF4J
//  implementation("ome:bioformats_package:${bioformatsVersion}") {
//  	isTransitive = false
//  }

  implementation(libs.qupath.fxtras)
  implementation(libs.controlsfx)
  implementation(libs.picocli)
  implementation(libs.jna)           // needed for OMEZarrReader (see https://github.com/bcdev/jzarr/issues/31)

  implementation("ome:formats-gpl:${bioformatsVersion}") {
    exclude(group="xalan", module="serializer")
    exclude(group="xalan", module="xalan")
    exclude(group="io.minio", module="minio")
    exclude(group="commons-codec", module="commons-codec")
    exclude(group="commons-logging", module="commons-logging")
//        exclude(group="edu.ucar", module="cdm")
    exclude(group="com.google.code.findbugs", module="jsr305")
    exclude(group="com.google.code.findbugs", module="annotations")
  }
  implementation(libs.omeZarrReader) {
      exclude(group="ome", module="formats-api") // Through bioformats_package
  }
  implementation("io.github.qupath:blosc:${libs.versions.blosc.get()}:${nativesClassifier}")

//  testImplementation("ome:bioformats_package:${bioformatsVersion}")
  testImplementation("ome:bio-formats_plugins:${bioformatsVersion}")
  
  testImplementation(libs.imagej)
  
}
