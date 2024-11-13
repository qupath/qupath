import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import java.lang.ProcessBuilder.Redirect
import java.util.stream.Collectors

/**
 * Define jar manifests and the toolchain JDK.
 * This separates the JDK used to run Gradle from that used to build QuPath.
 */

plugins {
    `java-library`
}

val libs = the<LibrariesForLibs>()

java {
    var version = project.findProperty("toolchain") as String?
    if (version == null) {
        version = libs.versions.jdk.get()
    } else if (version.trim() == "skip")
        version = null
    if (version != null) {
        logger.info("Setting toolchain to {}", version)
        toolchain.languageVersion = JavaLanguageVersion.of(version)
    }
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile> {
    // Include parameter names, so they are available in the script editor via reflection
    options.compilerArgs = listOf("-parameters")
    // Specify source should be UTF8
    options.encoding = "UTF-8"
}

// Avoid "Entry .gitkeep is a duplicate but no duplicate handling strategy has been set."
// when using withSourcesJar()
tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

// Including the latest commit when building can help traceability - but requires git being available
// TODO: Don't compute this separately for separate libraries
var latestGitCommit: String? = null

afterEvaluate {

    val requestLatestCommit = project.findProperty("git-commit") == "true"
    if (requestLatestCommit) {
        try {
            latestGitCommit = ProcessBuilder().command(
                "git", "log", "--pretty=format:\"%h\"", "-n 1"
            ).start().inputReader().readText().trim()
            logger.info("Latest commit: {}", latestGitCommit)
        } catch (e: Exception) {
            logger.warn("Unable to get latest commit: {}", e.message)
            latestGitCommit = "Unknown (is Git installed?)"
        }
    } else {
        logger.info("I won't try to get the last commit - consider running with \"-Pgit-commit=true\" if you want this next time (assuming Git is installed)")
    }

    tasks.withType<Jar> {
        // Important to set version so this can be queried within QuPath
		manifest {
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
			val manifestAttributes = mutableMapOf(
					"Implementation-Vendor" to "QuPath developers",
					"Implementation-Version" to project.version,
					"QuPath-build-time" to formatter.format(LocalDateTime.now())
            )
			// Set the module name where we can
			if (project.hasProperty("moduleName")) {
				manifestAttributes["Automatic-Module-Name"] = "io.github." + project.extra["moduleName"]
			}

			if (latestGitCommit != null)
				manifestAttributes["QuPath-latest-commit"] = latestGitCommit

			attributes(manifestAttributes)
		}
    }
}


/*
 * Set options for creating javadocs for all modules/packages
 * Use -PstrictJavadoc=true to fail on error with doclint (which is rather strict).
 */
tasks.withType<Javadoc> {
    options.encoding = "UTF-8"
    options {
        this as StandardJavadocDocletOptions

        val strictJavadoc = findProperty("strictJavadoc")
        if (strictJavadoc == null || strictJavadoc != "true") {
            // This should be made more strict in the future
            addStringOption("Xdoclint:none", "-quiet")
        }

        tags(
            "apiNote:a:API Note",
            "implNote:a:Implementation Note",
            "implSpec:a:Implementation Requirements"
        )

        // Need to use the major version only with javafx
        links(
            "https://docs.oracle.com/en/java/javase/${libs.versions.jdk.get()}/docs/api/",
            "https://openjfx.io/javadoc/${libs.versions.javafx.get().split("\\.")[0]}/",
            "https://javadoc.io/doc/org.bytedeco/javacpp/${libs.versions.javacpp.get()}/",
            "https://javadoc.io/doc/org.bytedeco/opencv/${libs.versions.opencv.get()}/",
            "https://javadoc.io/doc/com.google.code.gson/gson/${libs.versions.gson.get()}/",
            "https://javadoc.io/doc/org.locationtech.jts/jts-core/${libs.versions.jts.get()}/",
            "https://javadoc.io/doc/net.imagej/ij/${libs.versions.imagej.get()}/",
            "https://javadoc.scijava.org/Bio-Formats/",
            "https://javadoc.io/doc/ai.djl/api/${libs.versions.deepJavaLibrary.get()}/"
        )
    }
    val currentSource = source
    val currentClasspath = classpath
    val currentExcludes = excludes
    val currentIncludes = includes
    rootProject.tasks.withType<Javadoc> {
        source += currentSource
        classpath += currentClasspath
        excludes += currentExcludes
        includes += currentIncludes
    }
}

/*
 * On Apple Silicon (at least), there are problems running tests involving JavaCPP 1.5.5 with
 * java.lang.OutOfMemoryError: Physical memory usage is too high: physicalBytes (1028M) > maxPhysicalBytes (1024M)
 * https://github.com/bytedeco/javacpp/issues/468
 */
tasks.withType<Test> {
    if ("32" == System.getProperty("sun.arch.data.model"))
        maxHeapSize = "1G"
    else
        maxHeapSize = "2G"
}
