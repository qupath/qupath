import io.github.qupath.gradle.PlatformPlugin
import org.apache.tools.ant.taskdefs.condition.Os

plugins {
    id("qupath.common-conventions")
    id("qupath.javafx-conventions")
    id("qupath.git-commit-id")

    id("qupath.djl-conventions")
    id("qupath.application-conventions")

    id("qupath.fiji-conventions")

    id("qupath.jpackage-conventions")
    id("qupath.license-conventions")

    `version-catalog`
    `maven-publish`
}

/*
 * Fail early if the operating system or JDK isn't compatible
 */
if (io.github.qupath.gradle.Utils.currentPlatform() == PlatformPlugin.Platform.UNKNOWN) {
    throw GradleException("Unknown operating system - can't build QuPath, sorry!")
}
if ("32" == System.getProperty("sun.arch.data.model")) {
    throw GradleException("Can't build QuPath using a 32-bit JDK - please use a 64-bit JDK instead")
}

/*
 * Set by Fiji conventions plugin
 */
val buildWithFiji: Boolean by project.extra

/*
 * Get the current QuPath version
 */
val qupathVersion = rootProject.version.toString()

/**
 * Set the group
 */
allprojects {
    group = "io.github.qupath"
}

/*
 * Specify the version catalog for publishing - including the current projects.
 */
catalog {
    versionCatalog {
        from(files("./gradle/libs.versions.toml"))

        val qupathGroup = group.toString()

        // Include version for the current jars in the version catalog, as they will be useful in extensions
        version("qupath", qupathVersion)
        library("qupath.gui.fx", qupathGroup, project(":qupath-gui-fx").name).versionRef("qupath")
        library("qupath.core", qupathGroup, project(":qupath-core").name).versionRef("qupath")
        library("qupath.core.processing", qupathGroup, project(":qupath-core-processing").name).versionRef("qupath")

        // Launcher
        library("qupath.app", qupathGroup, project(":qupath-app").name).versionRef("qupath")

        // Bundled extensions
        library("qupath.ext.bioformats", qupathGroup, project(":qupath-extension-bioformats").name).versionRef("qupath")
        library("qupath.ext.openslide", qupathGroup, project(":qupath-extension-openslide").name).versionRef("qupath")
        library("qupath.ext.script.editor", qupathGroup, project(":qupath-extension-script-editor").name).versionRef("qupath")
        library("qupath.ext.svg", qupathGroup, project(":qupath-extension-svg").name).versionRef("qupath")

        // All core dependencies
        bundle("qupath", listOf("qupath.gui.fx", "qupath.core", "qupath.core.processing"))

        // Everything
        bundle("qupath.all", listOf("qupath.gui.fx", "qupath.core", "qupath.core.processing",
            "qupath.app", "qupath.ext.bioformats", "qupath.ext.openslide", "qupath.ext.script.editor", "qupath.ext.svg"))
    }
}

/*
 * Publish catalog to help with dependency management across extensions
 */
publishing {
    repositories {
        maven {
            name = "SciJava"
            val releasesRepoUrl = uri("https://maven.scijava.org/content/repositories/releases")
            val snapshotsRepoUrl = uri("https://maven.scijava.org/content/repositories/snapshots")
            // Use gradle -Prelease publish
            url = if (project.hasProperty("release")) releasesRepoUrl else snapshotsRepoUrl
            credentials {
                username = System.getenv("MAVEN_USER")
                password = System.getenv("MAVEN_PASS")
            }
        }
    }

    publications {
        create<MavenPublication>("maven") {
            artifactId = "qupath-catalog"
            version = qupathVersion
            from(components["versionCatalog"])
        }
    }

}

/**
 * Generate a single Javadoc for all projects.
 */
// See https://discuss.gradle.org/t/best-approach-gradle-multi-module-project-generate-just-one-global-javadoc/18657
tasks.register<Javadoc>("mergedJavadocs") {
    source = sourceSets.main.get().allJava
    description = "Generate merged javadocs for all projects"
    group = "Documentation"

    val dest = layout.buildDirectory.dir("docs-merged/javadoc").get().asFile
    setDestinationDir(dest)
    title = "QuPath ${gradle.extra["qupath.app.version"]}"

    // Don't fail on error - this happened too often due to a javadoc link being temporarily down
    isFailOnError = false
}


/**
 * Export icons and a command list based on menu items (useful for documentation).
 * Note that this requires JavaFX to be available.
 */
tasks.register<JavaExec>("exportDocs") {
    description = "Export icons and command descriptions for documentation"
    group = "QuPath"

    val docsDir = layout.buildDirectory.dir("qupath-docs").get().asFile

    // Note we need all subprojects to ensure icons & commands are also loaded from extensions
    dependencies {
        subprojects.forEach(::implementation)
    }

    doFirst {
        println("Making docs dir in ${docsDir.absolutePath}")
        docsDir.mkdirs()
    }
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "qupath.lib.gui.tools.DocGenerator"
    args = listOf(docsDir.absolutePath, "--all")
}




/**
 * Determine which projects to include/exclude as dependencies
 */
val excludedProjects = listOf(project)
val includedProjects = rootProject.subprojects.filter { !excludedProjects.contains(it) }

dependencies {

    // Include Groovy scripting
    runtimeOnly(libs.bundles.groovy)

    includedProjects.forEach {
        implementation(it)
    }

    with (gradle.extra["qupath.included.dependencies"] as List<*>) {
        forEach {
            if (it != null)
                implementation(it)
        }
    }

    implementation(libs.picocli)

    implementation(extraLibs.bundles.extensions) {
        // We don't want to bring in snapshot versions
        exclude(group=group)
    }
}

/**
 * Avoid bundling the DejaVu web fonts, which add >4 MB to each jar -
 * and so 40-50 MB to a QuPath download.
 * See https://bugs.openjdk.org/browse/JDK-8326683
 */
tasks.register("shrinkJavadocs") {
    rootProject.subprojects.forEach {
        it.tasks.javadoc {
            options {
                this as StandardJavadocDocletOptions
                addBooleanOption("-no-fonts", true)
            }
        }
    }
}

/**
 * Copies the Javadoc jars to a directory for access within QuPath
 */
tasks.register<Copy>("assembleJavadocs") {
    group = "documentation"
    description = "Copies the Javadoc jars to a directory for access within QuPath"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    dependsOn("shrinkJavadocs")

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
tasks.distZip {
    enabled = false
}
tasks.distTar {
    enabled = false
}
tasks.installDist {
    dependsOn("assembleJavadocs")
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
                from(rootDir)
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
                from(layout.buildDirectory.dir("reports/dependency-license"))
                include("THIRD-PARTY.txt")
            }
            // Copy javadocs
            into("lib/docs") {
                from(layout.buildDirectory.dir("javadocs").get())
                include("*.jar")
            }
        }
    }
}



/*
 * Custom behavior when we want to build with all Fiji dependencies
 */
if (buildWithFiji) {

    apply(plugin="qupath.fiji-conventions")

    // Set dependencies here (rather than in plugin) so we can use the SciJava version catalog
    dependencies {
        implementation(sciJava.bundles.fiji)
        {
            // Excluded because gradle doesn't support maven profiles fully - so need to get them some other way
            exclude(group = "org.jogamp.gluegen")
            exclude(group = "org.jogamp.jogl")
            exclude(group = "org.jogamp.joal")
            exclude(group = "org.bytedeco", module = "ffmpeg")
        }
        implementation(sciJava.org.jogamp.gluegen.gluegenRt)
        implementation(sciJava.org.jogamp.jogl.joglAll)
        implementation(sciJava.org.bytedeco.ffmpeg)

        // Requires to ensure we use Groovy 4.x
        implementation(sciJava.scijava.scriptingGroovy)

        implementation("org.bytedeco:ffmpeg-platform:${sciJava.org.bytedeco.ffmpeg.get().version}")

        val classifier = getJogampClassifier()
        if (classifier != null) {
            implementation("org.jogamp.gluegen:gluegen-rt:${sciJava.org.jogamp.gluegen.gluegenRt.get().version}:$classifier")
            implementation("org.jogamp.jogl:jogl-all:${sciJava.org.jogamp.jogl.joglAll.get().version}:$classifier")
            implementation("org.jogamp.joal:joal:${sciJava.org.jogamp.joal.joal.get().version}:$classifier")
        } else {
            logger.warn("Native libraries not found for jogamp")
        }
    }

    // Required for SciJava Context
    runtime {
        modules.add("java.instrument")
    }

}

fun getJogampClassifier(): String? {
    if (Os.isFamily(Os.FAMILY_MAC)) {
        return "natives-macosx-universal"
    }
    if (Os.isFamily(Os.FAMILY_WINDOWS)) {
        return "natives-windows-amd64"
    }
    if (Os.isFamily(Os.FAMILY_UNIX)) {
        return if (Os.isArch("arm64")) {
            "natives-linux-aarch64"
        } else {
            "natives-linux-amd64"
        }
    }
    return null
}
