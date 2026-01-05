import org.apache.tools.ant.taskdefs.condition.Os

pluginManagement {
    plugins {
        kotlin("jvm") version "2.3.0"
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" // to download JDK if needed
}

// Define project name
rootProject.name = "qupath"

// Define the current QuPath version
val qupathVersion = file("./VERSION").readText().trim()

// Define the group to use for artifacts
val qupathGroup = "io.github.qupath"

// Store version & base app name in extra properties for build scripts to use
gradle.extra["qupath.app.version"] = qupathVersion
gradle.extra["qupath.app.name"] = "QuPath"

// Default is to use 50% of available RAM
gradle.extra["qupath.jvm.args"] = listOf(
    providers.gradleProperty("qupath.jvm.args")
        .getOrElse("-XX:MaxRAMPercentage=50"))

// By default, create an image with jpackage (not an installer, which is slower)
gradle.extra["qupath.package"] = providers.gradleProperty("package").getOrElse("image")

// By default, don't bind services; this can only be used if jmods is available in the JDK (it isn't or Temurin)
// Activate this with -Pbind-services or -Pbind-services=true
gradle.extra["qupath.bind-services"] = !"false".equals(providers.gradleProperty("bind-services").getOrElse("false"), true)

// By default, create a per-user installer on Windows
gradle.extra["qupath.package.per-user"] = "true".equals(providers.gradleProperty("per-user-install").getOrElse("true"), true)

// Optionally include extra libraries/extensions
// Use -Pinclude-extras=true
val includeExtras = "true".equals(providers.gradleProperty("include-extras").getOrElse("false"), true)

// Optionally request that the git commit ID be included in the build
gradle.extra["qupath.package.git-commit"] = providers.gradleProperty("git-commit").getOrElse("false")

// Main application
include("qupath-app")

// Core modules
include("qupath-core")
include("qupath-core-processing")
include("qupath-gui-fx")

// Core extension
include("qupath-extension-processing")

// Extra extensions
include("qupath-extension-svg")
include("qupath-extension-script-editor")
include("qupath-extension-openslide")
include("qupath-extension-bioformats")

// Support JavaFX dependency override
// This can be used to create a build for older versions of macOS
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            val javafxOverride = System.getProperties().getOrDefault("javafx.version", null)
            if (javafxOverride is String) {
                println("Overriding JavaFX version to request $javafxOverride")
                version("javafx", javafxOverride)
            }

            val djlOverride = System.getProperties().getOrDefault("djl.version", null)
            if (djlOverride is String) {
                println("Overriding DeepJavaLibrary version to request $djlOverride")
                version("deepJavaLibrary", djlOverride)
            }

            // Add QuPath jars to the version catalog
            // This is important when developing extensions & using includeFlat, because then the catalog is used
            // directly, rather than accessed from Maven.
            version("qupath", qupathVersion)
            library("qupath.gui.fx", qupathGroup, project(":qupath-gui-fx").name).versionRef("qupath")
            library("qupath.core", qupathGroup, project(":qupath-core").name).versionRef("qupath")
            library("qupath.core.processing", qupathGroup, project(":qupath-core-processing").name).versionRef("qupath")
            bundle("qupath", listOf("qupath.gui.fx", "qupath.core", "qupath.core.processing"))

        }

        // Extra version catalog for bundled extensions
        // This can be useful to make custom QuPath builds with specific versions of extensions
        // (It was used for v0.6.0 release candidates to bundle some extensions, but is no longer needed)
        create("extraLibs") {
            // Define the libraries needed here
//            library("djl", qupathGroup, "qupath-extension-djl").version("0.4.0-20240911.172830-2")
            // Include or exclude bundled extensions
            if (includeExtras) {
                println("Extra libs requested, but none have been defined")
//                bundle("extensions", listOf("djl", "instanseg", "training", "py4j"))
            } else {
                bundle("extensions", listOf())
            }
        }

        create("sciJava") {
            from("org.scijava:pom-scijava:43.0.0")
        }

    }

    repositories {
        maven("https://maven.scijava.org/content/groups/public/")
        mavenCentral()
    }

}

// These lines make it possible to define directories within gradle.properties
// to include in the build using either includeFlat or includeBuild.
// This is useful when developing extensions, especially because gradle.properties
// is not under version control.

// Make subprojects of the main build available for substitution
includeBuild(".")

// Include flat directories for extensions
findIncludes("qupath.include.flat").forEach(::includeFlat)

// Include build directories
findIncludes("qupath.include.build").forEach(::includeBuild)

// Check for include-extras file
gradle.extra["qupath.included.dependencies"] = emptyList<String>()
val includeExtrasFile = findIncludeExtras()
if (includeExtrasFile != null) {
    with (includeExtrasFile) {
        if (exists())
            handleExtensionConfig(this)
    }
}

/**
 * Find a file called 'include-extra' (extension doesn't matter, shortest file name is preferred)
 */
fun findIncludeExtras(): File? {
   val possibleFiles = rootDir.listFiles()
       ?.filter { f -> f.name == "include-extra" || f.name.startsWith("include-extra.")}
       ?.sortedBy { f -> f.name.length }
    if (possibleFiles != null) {
        return if (possibleFiles.isEmpty()) null else possibleFiles.get(0)
    } else {
        return null
    }
}

/**
 * Parse a delimited property string for a list of directories or projects.
 */
fun findIncludes(propName: String): List<String> {
    return providers.gradleProperty(propName)
        .getOrElse("")
        .split(",", "\\\n", ";")
        .map(String::trim)
        .filter(String::isNotBlank)
}

/**
 * Support for specifying additional builds and dependencies to include in a text file.
 *
 * This is useful when developing extensions.
 * The file should be named 'include-extra.properties' and have the format
 *
 * [includeBuild]
 * /path/to/build
 *
 * [dependencies]
 * # Optional comments
 * group:name:version
 * group2:name2:version2
 *
 * where version can be omitted if the project is part of an included build.
 */
fun handleExtensionConfig(file: File) {
    if (!file.isFile)
        return
    val lines = file.readLines()
        .map { it.substringBefore("#") }
        .map(String::trim)
        .filter(String::isNotBlank)
        .toList()

    val searchNothing = 0
    val searchIncludeBuild = 1
    val searchIncludeFlat = 2
    val searchDependencies = 3
    var search = searchNothing
    val dependenciesToAdd = ArrayList<String>()
    for (line in lines) {
        when (line) {
            "[includeBuild]" -> search = searchIncludeBuild
            "[includeFlat]" -> search = searchIncludeFlat
            "[dependencies]" -> search = searchDependencies
            else -> {
                when (search) {
                    searchIncludeBuild -> includeBuild(line)
                    searchIncludeFlat -> includeFlat(line)
                    searchDependencies -> dependenciesToAdd.add(line)
                }
            }
        }
    }
    // Store for use in the build script
    gradle.extra["qupath.included.dependencies"] = dependenciesToAdd
}
