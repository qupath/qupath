import io.github.qupath.gradle.PlatformPlugin

plugins {
    id("qupath.common-conventions")
    id("qupath.javafx-conventions")
    id("qupath.git-commit-id")
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
 * Get version catalog
 */
catalog {
    versionCatalog {
        from(files("./gradle/libs.versions.toml"))
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
            groupId = "io.github.qupath"
            artifactId = "qupath-catalog"
            version = gradle.extra["qupath.app.version"] as String
            from(components["versionCatalog"])
        }
    }

}

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
 * Export all icons from the icon factory (useful for documentation).
 * Note that this requires JavaFX to be available.
 */
tasks.register<JavaExec>("exportDocs") {
    description = "Export icons and command descriptions for documentation"
    group = "QuPath"

    val docsDir = rootProject.layout.buildDirectory.dir("qupath-docs").get().asFile

    // Note we need all subprojects to ensure icons & commands are also loaded from extensions
    dependencies {
        subprojects.forEach(::implementation)
    }

    doFirst {
        println("Making docs dir in ${docsDir.absolutePath}")
        docsDir.mkdirs()
    }
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "qupath.lib.gui.tools.DocGenerator"
    args = listOf(docsDir.absolutePath, "--all")
}