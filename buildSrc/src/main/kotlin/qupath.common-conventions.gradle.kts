/**
 * Conventions common to most (or all) modules.
 * This includes qupath.java-conventions.
 */

import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.kotlin.dsl.the

plugins {
    id("qupath.java-conventions")
    id("org.bytedeco.gradle-javacpp-platform")
    jacoco
}

// See https://discuss.gradle.org/t/how-to-apply-binary-plugin-from-convention-plugin/48778/2
apply(plugin = "io.github.qupath.platform")

val libs = the<LibrariesForLibs>()

repositories {

    val useLocal = providers.gradleProperty("use-maven-local")
    if (useLocal.orNull == "true") {
        logger.warn("Using Maven local")
        mavenLocal()
    }

    mavenCentral()

    // Required for scijava (including some QuPath jars)
    maven {
    	name = "SciJava"
	    url = uri("https://maven.scijava.org/content/groups/public/")
	}

    // May be required during development
    maven {
        name = "SciJava snapshots"
        url = uri("https://maven.scijava.org/content/repositories/snapshots")
    }

    // Required for Bio-Formats
    maven {
        name = "Unidata"
        url = uri("https://artifacts.unidata.ucar.edu/content/repositories/unidata-releases")
    }
    maven {
        name = "Open Microscopy"
        url = uri("https://artifacts.openmicroscopy.org/artifactory/maven/")
    }

    // May be required for snapshot JavaCPP jars
    maven {
        name = "Sonatype snapshots"
        url = uri("https://oss.sonatype.org/content/repositories/snapshots")
    }

}

/*
 * Some metadata for the manifest
 */
project.version = gradle.extra["qupath.app.version"] as String

dependencies {
    implementation(libs.bundles.logging)

    testImplementation(libs.junit)
    testRuntimeOnly(libs.junit.platform)
}

tasks.test {
    useJUnitPlatform()
}