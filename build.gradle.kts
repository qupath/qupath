import io.github.qupath.gradle.PlatformPlugin

plugins {
    id("qupath.java-conventions")
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

// See https://discuss.gradle.org/t/best-approach-gradle-multi-module-project-generate-just-one-global-javadoc/18657
tasks.register<Javadoc>("mergedJavadocs") {
    source = sourceSets.main.get().allJava
    description = "Generate merged javadocs for all projects"
    group = "Documentation"

    val dest = layout.buildDirectory.dir("docs-merged/javadoc").get().asFile
    setDestinationDir(dest)
    title = "QuPath ${gradle.extra["qupathVersion"]}"

    // Don't fail on error, because this happened too often due to a javadoc link being temporarily down
    isFailOnError = false
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
            version = gradle.extra["qupathVersion"].toString()
            from(components["versionCatalog"])
        }
    }

}
