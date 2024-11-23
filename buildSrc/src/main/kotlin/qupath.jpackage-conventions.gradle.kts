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
val macOSDefaultVersion = "1"
val qupathVersion = gradle.extra["qupath.app.version"] as String
val qupathAppName = gradle.extra["qupath.app.name"] as String
val platform: PlatformPlugin.Platform = Utils.currentPlatform()


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

        "java.net.http",        // Add HttpClient support (might be used by scripts)
        "java.management",      // Useful to check memory usage
        "jdk.management.agent", // Enables VisualVM to connect and sample CPU use
        "jdk.jsobject",         // Needed to interact with WebView through JSObject
    ))

    val params = JPackageParams(
        getDistOutputDir(),
        project.file("jpackage/${platform}"))


    for (installer in params.installerTypes) {
        if (installer != null)
            println("Calling JPackage for \"${installer}\"")

        jpackage {
            mainJar = project.tasks.jar.get().archiveFileName.get()
            installerType = installer

            jvmArgs.addAll(params.jvmArgs)
            imageName = params.imageName
            appVersion = params.appVersion
            resourceDir = params.resourceDir
            imageOptions = params.imageOptions
            skipInstaller = params.skipInstaller
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
class JPackageParams(val outputDir: File, val resourceDir: File? = null) {

    var jvmArgs = listOf(gradle.extra["qupath.jvm.args"] as String)
    var imageName = qupathAppName // Will need to be removed for some platforms
    var appVersion: String = qupathVersion.replace("-SNAPSHOT", "")
    var imageOptions = mutableListOf<String>()

    var installerTypes = mutableListOf<String?>()
    var skipInstaller: Boolean = false
    var installerName = "QuPath"
    var installerOptions = mutableListOf<String>()

    init {
        if (resourceDir != null)
            initIcon(resourceDir)
        initInstallTypes()
        if (platform.isWindows) {
            configureWindows()
        } else if (platform.isMac) {
            configureMac()
        } else if (platform.isLinux) {
            configureLinux()
        } else {
            logger.warn("Unknown platform $platform - may be unable to generate a package")
        }
    }

    /**
     * Search for an icon file in the resource directory
     */
    private fun initIcon(resourceDir: File) {
        if (!resourceDir.isDirectory)
            return
        val iconExt = platform.iconExtension
        val iconFile = File(resourceDir, "QuPath.${iconExt}")
        if (iconFile.exists())
            imageOptions += listOf("--icon", iconFile.absolutePath)
        else
            logger.warn("No icon file found at $iconFile")
    }

    /**
     * Update package type according to "package" parameter.
     * By default, we just create an image because that's faster
     * (although the jpackage default is to create all installers).
     */
    private fun initInstallTypes() {
        // Define platform-specific jpackage configuration options
        val requestedPackage = findProperty("package") as String?
        val packageType = requestedPackage?.lowercase()
        if (packageType == null || setOf("image", "app-image").contains(packageType) || platform.isMac) {
            // We can't make installers directly on macOS - need to base them on an image
            this.skipInstaller = true
            this.installerTypes += null
            logger.info("No package type specified, using default $packageType")
        } else if (packageType == "all") {
            this.skipInstaller = false
            this.installerTypes += null
        } else if (packageType == "installer") {
            this.skipInstaller = false
            this.installerTypes += platform.installerExtension
        } else {
            this.installerTypes += packageType
        }
    }


    /**
     * Custom configurations for Windows
     */
    private fun configureWindows() {
        if (this.installerTypes.contains("msi")) {
            this.installerOptions += "--win-menu"
            this.installerOptions += "--win-dir-chooser"
            this.installerOptions += "--win-shortcut"
            this.installerOptions += "--win-per-user-install"
            this.installerOptions += "--win-menu-group"
            this.installerOptions += "QuPath"
        }

        // Can't have any -SNAPSHOT or similar added
        this.appVersion = stripVersionSuffix(this.appVersion)

        // Create a separate launcher with a console - this can help with debugging
        val fileTemp = File.createTempFile("qupath-building", ".properties")
        val consoleLauncherName = this.imageName + " (console)"
        val javaOptions = this.jvmArgs
        fileTemp.deleteOnExit()
        fileTemp.writeText(
            "win-console=true" + System.lineSeparator() +
                    "java-options=-Dqupath.config=console " + javaOptions.joinToString(separator=" ")
                    + System.lineSeparator())
        this.imageOptions += "--add-launcher"
        this.imageOptions += "\"${consoleLauncherName}\"=\"${fileTemp.absolutePath}\""
    }

    /**
     * Custom configurations for macOS
     */
    private fun configureMac() {
        this.installerOptions += listOf("--mac-package-name", "QuPath")
        // Need to include the version so that we can have multiple versions installed
        this.installerOptions += listOf("--mac-package-identifier", "QuPath-${qupathVersion}")

        // File associations supported on Mac
        setFileAssociations()

        // Can't have any -SNAPSHOT or similar added
        this.appVersion = stripVersionSuffix(this.appVersion)

        this.imageName = getCorrectAppName(".app")
        if (this.imageName.endsWith(".app"))
            this.imageName = this.imageName.substring(0, this.imageName.length - 4)
        this.installerName = getCorrectAppName(".pkg")

        // Sadly, on a Mac we can't have an appVersion that starts with 0
        // See https://github.com/openjdk/jdk/blob/jdk-16+36/src/jdk.jpackage/macosx/classes/jdk/jpackage/internal/CFBundleVersion.java
        if (this.appVersion.startsWith("0")) {
            this.appVersion = macOSDefaultVersion
        }
    }

    /**
     * Custom configurations for Linux
     */
    private fun configureLinux() {
        // This has the same issues as on macOS with invalid .cfg file, requiring another name
        this.imageName = "QuPath"
    }


    /**
     * Set file associations according to contents of a .properties file
     */
    private fun setFileAssociations() {
        val associations = project.file("jpackage/associations")
            .listFiles()
            ?.filter { it.isFile() && it.name.endsWith(".properties") }
        if (associations != null) {
            for (file in associations) {
                this.installerOptions += listOf("--file-associations", file.absolutePath)
            }
        }
    }

    /**
     * Strip suffixes (by default any starting with "-SNAPSHOT", "-rc") from any version string
     * @param version the version to strip
     * @return the stripped version (may be the same as the input)
     */
    private fun stripVersionSuffix(version: String): String {
        var result = version
        for (suffix in setOf("-SNAPSHOT", "-rc")) {
            val lastDash = result.lastIndexOf(suffix)
            if (lastDash > 0)
                result = result.substring(0, lastDash)
        }
        return result
    }

}


/**
 * Get the output directory for any distributions
 */
fun getDistOutputDir(): File {
    return rootProject.layout.buildDirectory.dir("dist").get().asFile
}


/**
 * Postprocessing of jpackage outputs; this is needed to fix the macOS version
 * and assemble the outputs for the checksums.
 */
val jpackageFinalize by tasks.registering {
    doLast {
        val outputDir = getDistOutputDir()
        // Loop for Mac things to do
        val appFile = File(outputDir, getCorrectAppName(".app"))
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
        if (Utils.currentPlatform().isWindows) {
            val imageDir = File(outputDir, qupathAppName)
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
    finalizedBy(jpackageFinalize)
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
 * @param appFile the .app file to package
 */
fun makeMacOSPkg(appFile: File) {
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