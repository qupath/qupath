/**
 * Build QuPath application.
 * This involves creating a jpackage task.
 *
 * Important properties:
 *  -Pld-path=true - set LD_LIBRARY_PATH on Linux (for both "run" and distribution tasks).
 *                   This is needed to use QuPath"s own OpenSlide rather than system shared libraries.
 *  -Ppackage="installer" - request jpackage to create an installer rather than an image.
 *                           Other options include "all", "deb", "rpm", "exe", "msi", "pkg" and "dmg"
 *                           although not all are available on all platforms.
 */

import com.github.jk1.license.render.*
import org.gradle.crypto.checksum.Checksum

buildscript {
    repositories {
        maven {
            url = uri("https://plugins.gradle.org/m2/")
        }
    }
}

plugins {
    id("org.gradle.crypto.checksum") version "1.4.0"
    id("qupath.common-conventions")
    id("qupath.djl-conventions")
    application
    alias(libs.plugins.license.report)
    alias(libs.plugins.jpackage)
    alias(libs.plugins.javafx)
}


extra["moduleName"] = "qupath.app"
base {
    archivesName = "qupath-app"
    description = "Main QuPath application."
}

/*
 * There are several problems with jpackage on macOS:
 * - The major version must be > 0, or a "wrong" version needs to be applied
 * - The wrong version is appended to the end of any installer filenames
 * - The .cfg file generated can be invalid (e.g. QuPath-0.3.cfg for QuPath-v0.3.0),
 *   which results in the app being unable to launch.
 *
 * These variables are used to help overcome this by specifying the defaults to use
 * up-front, so that a later action can rename any generated packages.
 */
var macOSDefaultVersion = "1"
var qupathVersion = gradle.extra["qupathVersion"] as String
val qupathAppName = "QuPath-${qupathVersion}"
extra["qupathAppName"] = qupathAppName

// Required since moving to JavaFX Gradle Plugin v0.1.0
javafx {
    version = libs.versions.javafx.get()
    modules = listOf("javafx.base",
        "javafx.controls",
        "javafx.graphics",
        "javafx.media",
        "javafx.fxml",
        "javafx.web",
        "javafx.swing")
}


/**
 * Determine which projects to include/exclude as dependencies
 */
val excludedProjects = listOf(project)
val includedProjects = rootProject.subprojects.filter { !excludedProjects.contains(it) }

dependencies {
    includedProjects.forEach {
        implementation(it)
    }
    implementation(libs.picocli)

    implementation(extraLibs.bundles.extensions) {
        // We don't want to bring in snapshot versions
        exclude(group="io.github.qupath")
    }
}


// Put the output in the main directory so it is easier to find
//project.buildDir = rootProject.file("build")

application {
    mainClass = "qupath.QuPath"
    applicationName = qupathAppName
    applicationDefaultJvmArgs = buildDefaultJvmArgs()

    // Necessary when using ./gradlew run to support style manager to change themes
    applicationDefaultJvmArgs += "--add-opens"
    applicationDefaultJvmArgs += "javafx.graphics/com.sun.javafx.css=ALL-UNNAMED"

    // Necessary when using ./gradlew run to support project metadata autocomplete
    // See https://github.com/controlsfx/controlsfx/issues/1505
    applicationDefaultJvmArgs += "--add-opens"
    applicationDefaultJvmArgs += "javafx.base/com.sun.javafx.event=ALL-UNNAMED"
}

/**
 * Add classpath and main class to make it easier to launch from jar
 * TODO: Check this: previously used afterEvaluate { jar { ... }  }
 */
java {
    manifest {
        val manifestAttributes = mapOf(
            // Calling this too early is a problem - we need the JavaCPP plugin to have already sorted out
            // the classpath, otherwise we risk bringing in dependencies for all platforms
//            "Class-Path" to configurations.runtimeClasspath.get().joinToString(separator = " ") { it.name },
            "Main-Class" to "qupath.QuPath"
        )
        attributes(manifestAttributes)
    }
}


/**
 * Copies the Javadoc jars to a directory for access within QuPath
 */
tasks.register<Copy>("assembleJavadocs") {
    group = "documentation"
    description = "Copies the Javadoc jars to a directory for access within QuPath"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    // Always include jars developed by all subprojects
    rootProject.subprojects.forEach {
        from(it.tasks.javadocJar)
    }

    // Also include selected dependency jars
    val dependenciesJavadoc = dependencies.createArtifactResolutionQuery()
            .forComponents(configurations
                    .runtimeClasspath
                    .get()
                    .incoming
                    .resolutionResult
                    .allDependencies
                    .filterIsInstance<ResolvedDependencyResult>()
                    .map { it.selected.id }
            )
            .withArtifacts(JvmLibrary::class, SourcesArtifact::class, JavadocArtifact::class)
            .execute()
            .resolvedComponents
            .map {
                c -> c.getArtifacts(JavadocArtifact::class)
                    .filterIsInstance<ResolvedArtifactResult>()
                    .map {it.file}
            }
            .flatten()
            .filter {
                val docs = findProperty("docs") ?: "default"
                when (docs) {
                    "all" -> return@filter true
                    "none" -> return@filter false
                    "qupath" -> return@filter it.name.startsWith("qupath")
                    else -> return@filter it.name.startsWith("qupath") ||
                            it.name.startsWith("jts")  || it.name.startsWith("ij")
                }
            }

    dependenciesJavadoc.forEach {
        from(it)
    }

    into(layout.buildDirectory.dir("javadocs"))
}
tasks.distTar {
    dependsOn("assembleJavadocs")
}
tasks.installDist {
    dependsOn("assembleJavadocs")
}

/**
 * Create license report
 */
licenseReport {
    val fileUnknown = rootProject.file("unknown-license-details.txt")
    renderers = arrayOf<ReportRenderer>(TextReportRenderer("THIRD-PARTY.txt"),
        InventoryHtmlReportRenderer("index.html", "Third party licenses", fileUnknown))

    outputDir = rootProject.layout.buildDirectory.dir("reports/dependency-license").get().asFile.absolutePath

    // TODO: Try to remove this. It's needed (I think) due to the license plugin not supporting
    //       Gradle variants, as required by the JavaFX Gradle Plugin v0.1.0. Possibly-relevant links:
    //       - https://github.com/openjfx/javafx-gradle-plugin#variants
    //       - https://github.com/jk1/Gradle-License-Report/issues/199
    //       The JavaFX license is still included in QuPath, but unfortunately not in this report.
    excludeGroups = arrayOf("org.openjfx")
}
tasks.startScripts {
    dependsOn("generateLicenseReport")
}


/**
 * Copy key files into the distribution
 */
distributions {
    main {
        contents {
            // We need a DuplicatesStrategy if settings.gradle.kts uses includeFlat for extra extensions
            // (which require QuPath as a dependency)
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            into("lib") {
                from(project.rootDir)
                include("CHANGELOG.md")
                include("STARTUP.md")
                include("LICENSE")
            }
            // Get the core licenses associated with the app
            into("lib") {
                from(".")
                include("licenses/**")
            }
            // Check if we have licenses stored with other extensions,
            // either directly in the project directory or under "resources"
            into("lib") {
                includedProjects.forEach {
                    from(it.projectDir)
                    from(File(it.projectDir, "src/main/resources"))
                    include("licenses/**")
                    includeEmptyDirs = false
                }
            }
            // Copy license report
            into("lib/licenses") {
                from(rootProject.layout.buildDirectory.dir("reports/dependency-license"))
                include("THIRD-PARTY.txt")
            }
            // Copy javadocs
            into("lib/docs") {
                from(project.layout.buildDirectory.dir("javadocs").get())
                include("*.jar")
            }
        }
    }
}

/**
 * Create Java Runtime & call jpackage
 */
runtime {
    options.addAll(listOf(
        "--strip-debug",
        "--no-header-files",
        "--no-man-pages",
        "--strip-native-commands",
        "--compress", "zip-6", // jlink option; can be zip-0 (no compression) to zip-9; default is zip-6
        "--bind-services"
    ))
    modules.addAll(listOf(
        "java.desktop",
        "java.xml",
        "java.scripting",
        "java.sql",
        "java.naming",
        "jdk.unsupported",

        "jdk.zipfs",           // Needed for zip filesystem support

        "java.net.http",        // Add HttpClient support (may be used by scripts)
        "java.management",      // Useful to check memory usage
        "jdk.management.agent", // Enables VisualVM to connect and sample CPU use
        "jdk.jsobject",         // Needed to interact with WebView through JSObject
    ))

    val params = buildParameters()

    for (installer in params.installerTypes) {
        if (installer != null)
            println("Calling JPackage for \"${installer}\"")

        jpackage {
            mainJar = params.mainJar
            jvmArgs = params.jvmArgs
            imageName = params.imageName
            appVersion = params.appVersion
            resourceDir = params.resourceDir
            imageOptions = params.imageOptions
            skipInstaller = params.skipInstaller
            installerType = installer
            installerOptions = params.installerOptions
            installerName = params.installerName
            imageOutputDir = params.outputDir
            installerOutputDir = params.outputDir
        }
    }
}


/**
 * Build a .pkg for an existing .app on macOS.
 * This is a separate task because it needs to be run after the Info.plist has been updated.
 * @param appFile
 * @return
 */
fun makeMacOSPkg(appFile: File): Unit {
    exec {
        workingDir = appFile.getParentFile()
        commandLine = listOf("jpackage",
            "-n", "QuPath",
            "--app-image", appFile.getCanonicalPath(),
            "--type", "pkg",
            "--app-version", qupathVersion)
    }
}

/**
 * Postprocessing of jpackage outputs; this is needed to fix the macOS version
 * and assemble the outputs for the checksums.
 */
tasks.register("jpackageFinalize") {
    doLast {
        val outputDir = rootProject.layout.buildDirectory.get().asFile
        // Loop for Mac things to do
        val appFile = File(outputDir, "/dist/${getCorrectAppName(".app")}")
        if (appFile.exists()) {
            // We need to make the macOS pkg here to incorporate the changes
            val requestedPackage = findProperty("package") as String?
            if (requestedPackage?.lowercase() in setOf("installer", "pkg")) {
                println("Creating pkg")
                makeMacOSPkg(appFile)
                // Ensure we haven't accidentally changed the name
                val file = File(appFile.getParentFile(), "QuPath-${qupathVersion}.pkg")
                val correctName = getCorrectAppName(".pkg")
                if (file.exists() && !file.name.equals(correctName)) {
                    file.renameTo(File(file.getParent(), correctName))
                }
                // Remove the .app as it's no longer needed (and just takes up space)
                println("Deleting $appFile")
                delete(appFile)
            }
        }
        // On windows, for the installer we should also zip up the image
        if (project.properties["platform.name"] == "windows") {
            val imageDir = File(outputDir, "/dist/${qupathAppName}")
            val requestedPackage = findProperty("package") as String?
            if (imageDir.isDirectory() && requestedPackage?.lowercase() in setOf("installer")) {
                println("Zipping $imageDir")
                // See https://docs.gradle.org/current/userguide/ant.html
                ant.withGroovyBuilder {
                    "zip"("destfile" to "${imageDir.getCanonicalPath()}.zip") {
                        "fileset"("dir" to imageDir.getCanonicalPath()) {
                        }
                    }
                }
            }
        }
    }
    // Identify outputs, which are used to create checksums
    inputs.files(tasks["jpackage"].outputs.files)
    outputs.files(tasks["jpackage"].outputs.files.asFileTree.matching {
        include {
            it.file.parentFile.name == "dist" &&
                    it.name.startsWith("QuPath") &&
                    !it.name.endsWith(".sha512") &&
                    !it.name.endsWith(".sha256") &&
                    !it.name.endsWith(".sha384")
        }
    })
}

/**
 * Create SHA512 checksums of JPackage outputs
 */
tasks.register<Checksum>("createChecksums") {
    inputFiles.setFrom(tasks["jpackageFinalize"].outputs)
    outputDirectory.set(getDistOutputDir())
    checksumAlgorithm.set(Checksum.Algorithm.SHA512)
    appendFileNameToChecksum.set(true)
}

/**
 * Get the name we want to use for the app.
 * On macOS, we want to append the architecture to make it easier to install both
 * the x64 and ARM versions on the same machine.
 * @param ext
 * @return
 */
fun getCorrectAppName(ext: String): String {
    var baseName = qupathAppName
    if (ext == ".app" || ext == ".pkg") {
        if (!baseName.contains("-arm64") && !baseName.contains("-x64")) {
            if (System.getProperty("os.arch") == "aarch64")
                baseName = "${baseName}-arm64"
            else
                baseName = "${baseName}-x64"
        }
    }
    return "${baseName}${ext}"
}

/**
 * Try to resolve annoying macOS/Windows renaming with an invalid version
 * (I realise this is very awkward...)
 */
tasks.named("jpackage") {
    doLast {
        val extensions = listOf(".app", ".dmg", ".pkg", ".exe", ".msi", ".deb", ".rpm")
        for (dir in outputs.files) {
            val packageFiles = dir.listFiles()
            for (f in packageFiles!!) {
                for (ext in extensions) {
                    if (!f.name.endsWith(ext))
                        continue
                    val correctName = getCorrectAppName(ext)
                    if (!f.name.equals(correctName)) {
                        println("Renaming to: $correctName")
                        f.renameTo(File(f.getParent(), correctName))
                    }
                }
            }
        }
    }

    finalizedBy("jpackageFinalize")
}

/**
 * Encapsulate key parameters to pass to jpackage
 */
class JPackageParams {

    var mainJar: String? = null
    var jvmArgs = mutableListOf<String>()
    var imageName = "QuPath"
    var appVersion: String = getNonSnapshotVersion()
    var imageOptions = mutableListOf<String>()

    var installerTypes = mutableListOf<String?>()
    var skipInstaller: Boolean = false
    var installerName = "QuPath"
    var installerOptions = mutableListOf<String>()

    var resourceDir: File? = null
    var outputDir: File = getDistOutputDir()

    override fun toString(): String {
        return "JPackageParams{" +
                "mainJar=" + mainJar + "\"" +
                ", jvmArgs=\"" + jvmArgs + "\"" +
                ", imageName=\"" + imageName + "\"" +
                ", appVersion=\"" + appVersion + "\"" +
                ", imageOptions=" + imageOptions +
                ", installerTypes=" + installerTypes +
                ", skipInstaller=" + skipInstaller +
                ", installerName=\"" + installerName + "\"" +
                ", installerOptions=" + installerOptions +
                ", resourceDir=" + resourceDir +
                ", outputDir=" + outputDir +
                "}"
    }
}

/**
 * Get the version, with any "SNAPSHOT" element removed
 * @return
 */
fun getNonSnapshotVersion(): String {
    return qupathVersion.replace("-SNAPSHOT", "")
}

/**
 * Get the output directory for any distributions
 */
fun getDistOutputDir(): File {
    return rootProject.layout.buildDirectory.dir("dist").get().asFile
}

/**
 * Build default parameters for jpackage, customizing these according to the current platform
 * @return
 */
fun buildParameters(): JPackageParams {
    val params = JPackageParams()
    params.mainJar = project.tasks.jar.get().archiveFileName.get()
    params.imageName = qupathAppName // Will need to be removed for some platforms
    params.installerName = "QuPath"
    params.jvmArgs += buildDefaultJvmArgs()

    // Configure according to the current platform
    val platform = properties["platform.name"]
    val iconExt = properties["platform.iconExt"]
    if (platform == "macosx")
        configureJPackageMac(params)
    else if (platform == "windows")
        configureJPackageWindows(params)
    else if (platform == "linux")
        configureJPackageLinux(params)
    else
        logger.log(LogLevel.WARN, "Unknown platform $platform - may be unable to generate a package")

    params.resourceDir = project.file("jpackage/${platform}")

    val iconFile = project.file("jpackage/${platform}/QuPath.${iconExt}")
    if (iconFile.exists())
        params.imageOptions += listOf("--icon", iconFile.getAbsolutePath())
    else
        logger.log(LogLevel.WARN, "No icon file found at ${iconFile}")

    return params
}

/**
 * Update package type according to "package" parameter.
 * By default, we just create an image because that"s faster
 * (although the jpackage default is to create all installers).
 * @param params
 * @param defaultInstallers
 */
fun updatePackageType(params: JPackageParams, vararg defaultInstallers: String): Unit {
    // Define platform-specific jpackage configuration options
    val requestedPackage = findProperty("package") as String?
    val packageType = requestedPackage?.lowercase()
    if (packageType == null || setOf("image", "app-image").contains(packageType) || project.properties["platform.name"] == "macosx") {
        // We can't make installers directly on macOS - need to base them on an image
        params.skipInstaller = true
        params.installerTypes += null
        logger.info("No package type specified, using default ${packageType}")
    } else if (packageType == "all") {
        params.skipInstaller = false
        params.installerTypes += null
    } else if (packageType == "installer") {
        params.skipInstaller = false
        params.installerTypes += defaultInstallers
    } else {
        params.installerTypes += packageType
    }
}

/**
 * Custom configurations for Windows
 * @param params
 * @return
 */
fun configureJPackageWindows(params: JPackageParams): Unit {
    updatePackageType(params, properties["platform.installerExt"] as String)

    if (params.installerTypes.contains("msi")) {
        params.installerOptions += "--win-menu"
        params.installerOptions += "--win-dir-chooser"
        params.installerOptions += "--win-shortcut"
        params.installerOptions += "--win-per-user-install"
        params.installerOptions += "--win-menu-group"
        params.installerOptions += "QuPath"
    }

    // Can't have any -SNAPSHOT or similar added
    params.appVersion = stripVersionSuffix(params.appVersion)


    // Create a separate launcher with a console - this can help with debugging
    val fileTemp = File.createTempFile("qupath-building", ".properties")
    val consoleLauncherName = params.imageName + " (console)"
    val javaOptions = params.jvmArgs
    fileTemp.deleteOnExit()
    fileTemp.writeText(
        "win-console=true" + System.lineSeparator() +
                "java-options=-Dqupath.config=console " + javaOptions.joinToString(separator=" ")
                + System.lineSeparator())
    params.imageOptions += "--add-launcher"
    params.imageOptions += "\"${consoleLauncherName}\"=\"${fileTemp.getAbsolutePath()}\""
}

/**
 * Custom configurations for macOS
 * @param params
 * @return
 */
fun configureJPackageMac(params: JPackageParams): Unit {
    updatePackageType(params, properties["platform.installerExt"] as String)

    params.installerOptions += listOf("--mac-package-name", "QuPath")
    // Need to include the version so that we can have multiple versions installed
    params.installerOptions += listOf("--mac-package-identifier", "QuPath-${qupathVersion}")

    // File associations supported on Mac
    setFileAssociations(params)

    // Can't have any -SNAPSHOT or similar added
    params.appVersion = stripVersionSuffix(params.appVersion)

    params.imageName = getCorrectAppName(".app")
    if (params.imageName.endsWith(".app"))
        params.imageName = params.imageName.substring(0, params.imageName.length - 4)
    params.installerName = getCorrectAppName(".pkg")

    // Sadly, on a Mac we can't have an appVersion that starts with 0
    // See https://github.com/openjdk/jdk/blob/jdk-16+36/src/jdk.jpackage/macosx/classes/jdk/jpackage/internal/CFBundleVersion.java
    if (params.appVersion.startsWith("0")) {
        params.appVersion = macOSDefaultVersion
    }
}

/**
 * Custom configurations for Linux
 * @param params
 * @return
 */
fun configureJPackageLinux(params: JPackageParams): Unit {
    updatePackageType(params, properties["platform.installerExt"] as String)
    // This has the same issues as on macOS with invalid .cfg file, requiring another name
    params.imageName = "QuPath"
}

/**
 * Strip suffixes (by default any starting with "-SNAPSHOT", "-rc") from any version string
 * @param version
 * @return
 */
fun stripVersionSuffix(version: String): String {
    var result = version
    for (suffix in setOf("-SNAPSHOT", "-rc")) {
        val lastDash = result.lastIndexOf(suffix)
        if (lastDash > 0)
            result = result.substring(0, lastDash)
    }
    return result
}

/**
 * Set file associations according to contents of a .properties file
 * @param params
 */
fun setFileAssociations(params: JPackageParams): Unit {
    val associations = project.file("jpackage/associations")
        .listFiles()
        ?.filter { it.isFile() && it.name.endsWith(".properties") }
    if (associations != null) {
        for (file in associations) {
            params.installerOptions += listOf("--file-associations", file.absolutePath)
        }
    }
}

/**
 * Get the JavaVersion used with the current toolchain.
 * This is useful for JVM-specific arguments.
 */
fun getToolchainJavaVersion(): JavaVersion {
    try {
        val toolchain = project.extensions.getByType<JavaPluginExtension>().toolchain
        return JavaVersion.toVersion(toolchain.languageVersion.get())
    } catch (e: Exception) {
        println("Unable to determine Java version from toolchain: ${e.message}")
        return JavaVersion.current()
    }
}

/**
 * Get default JVM arguments (e.g. to set memory, library path)
 * Java version not currently used.
 * @return
 */
fun buildDefaultJvmArgs(libraryPath: String? = null): List<String> {
    // Set up the main Java options
    val javaOptions = ArrayList<String>()

    // Set the library path to the app directory, for loading native libraries
    if (libraryPath != null)
        javaOptions += "-Djava.library.path=${libraryPath}"

    // Default to using 50% available memory
    javaOptions += "-XX:MaxRAMPercentage=50"

    return javaOptions
}




/**
 * Export all icons from the icon factory (useful for documentation).
 * This is here (and not in the gui-fx module) because it's needed to load extensions.
 */
tasks.register<JavaExec>("exportDocs") {
    description = "Export icons and command descriptions for documentation"
    group = "QuPath"

    val docsDir = rootProject.layout.buildDirectory.dir("qupath-docs").get().asFile
    doFirst {
        println("Making docs dir in ${docsDir.absolutePath}")
        docsDir.mkdirs()
    }
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "qupath.lib.gui.tools.DocGenerator"
    args = listOf(docsDir.absolutePath, "--all")
}
