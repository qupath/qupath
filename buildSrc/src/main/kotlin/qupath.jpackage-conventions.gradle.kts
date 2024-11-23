/**
 * This configures jpackage using The Badass Runtime Plugin
 * https://badass-runtime-plugin.beryx.org/releases/latest/#jpackageWarning
 *
 * It also adds a task to compute checksums for the jpackage installers.
 */

import io.github.qupath.gradle.PlatformPlugin
import io.github.qupath.gradle.Utils
import org.gradle.crypto.checksum.Checksum

plugins {
    id("org.beryx.runtime")
    id("org.gradle.crypto.checksum")
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

        "jdk.zipfs",            // Needed for zip filesystem support

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
 * Encapsulate key parameters to pass to jpackage
 */
class JPackageParams(appVersion: String, outputDir: File) {

    var mainJar: String? = null
    var jvmArgs = mutableListOf<String>()
    var imageName = "QuPath"
    var appVersion: String = appVersion
    var imageOptions = mutableListOf<String>()

    var installerTypes = mutableListOf<String?>()
    var skipInstaller: Boolean = false
    var installerName = "QuPath"
    var installerOptions = mutableListOf<String>()

    var resourceDir: File? = null
    var outputDir: File = outputDir


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
 * Build default parameters for jpackage, customizing these according to the current platform
 * @return
 */
fun buildParameters(): JPackageParams {
    val params = JPackageParams(getNonSnapshotVersion(), getDistOutputDir())
    params.mainJar = project.tasks.jar.get().archiveFileName.get()
    params.imageName = qupathAppName // Will need to be removed for some platforms
    params.installerName = "QuPath"
    params.jvmArgs += buildDefaultJvmArgs()

    // Configure according to the current platform
    val platform = Utils.currentPlatform()
    if (platform.isMac)
        configureJPackageMac(params)
    else if (platform.isWindows)
        configureJPackageWindows(params)
    else if (platform.isLinux)
        configureJPackageLinux(params)
    else
        logger.log(LogLevel.WARN, "Unknown platform $platform - may be unable to generate a package")

    params.resourceDir = project.file("jpackage/${platform}")

    val iconExt = platform.iconExtension
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
 * Get default JVM arguments (e.g. to set memory, library path)
 * Java version not currently used.
 * @return
 */
fun buildDefaultJvmArgs(): List<String> {
    // Set up the main Java options
    val javaOptions = ArrayList<String>()

    // Default to using 50% available memory
    javaOptions += "-XX:MaxRAMPercentage=50"

    return javaOptions
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
        if (Utils.currentPlatform().isWindows()) {
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
 * Build a .pkg for an existing .app on macOS.
 * This is a separate task because it needs to be run after the Info.plist has been updated.
 * @param appFile
 * @return
 */
fun makeMacOSPkg(appFile: File): Unit {
    ProcessBuilder()
        .directory(appFile.getParentFile())
        .command(
            "jpackage",
            "-n", "QuPath",
            "--app-image", appFile.getCanonicalPath(),
            "--type", "pkg",
            "--app-version", qupathVersion)
        .start()
        .waitFor()
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