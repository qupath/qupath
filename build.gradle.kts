plugins {
    id("qupath.java-conventions")
    `version-catalog`
    `maven-publish`
}

// See https://discuss.gradle.org/t/best-approach-gradle-multi-module-project-generate-just-one-global-javadoc/18657
tasks.register<Javadoc>("mergedJavadocs") {
    source = sourceSets.main.get().allJava
    description = "Generate merged javadocs for all projects"
    group = "Documentation"

    val dest = layout.buildDirectory.dir("docs-merged/javadoc").get().getAsFile()
    setDestinationDir(dest)
    title = "QuPath $gradle.ext.qupathVersion"

    // See https://docs.gradle.org/current/javadoc/org/gradle/external/javadoc/StandardJavadocDocletOptions.html
    (options as StandardJavadocDocletOptions).author(true)
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")

    (options as StandardJavadocDocletOptions).encoding = "UTF-8"

    (options as StandardJavadocDocletOptions).links = listOf(
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

    // Don"t fail on error, because this happened too often due to a javadoc link being temporarily down
    setFailOnError(false)
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
