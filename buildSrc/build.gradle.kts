/**
 * Gradle script for building QuPath.
 * <p>
 * To create a complete build including associated license files, try the following:
 * <p>
 *     ./gradlew clean jpackage
 * <p>
 * or on Windows
 * <p>
 *     gradlew.bat clean jpackage
 * <p>
 * Gradle's toolchain options are used to overcome this: if you run gradlew with a different JDK,
 * gradle will use a different JDK for building QuPath itself (downloading it if necessary).
 */

plugins {
    `groovy-gradle-plugin`
    alias(libs.plugins.javacpp)
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(libs.versions.jdk.get())
    }
}

gradlePlugin {
    plugins {
        create("simplePlugin") {
            id = "io.github.qupath.platform"
            implementationClass = "io.github.qupath.gradle.PlatformPlugin"
        }
    }
    plugins {
        create("commitIdPlugin") {
            id = "qupath.git-commit-id"
            implementationClass = "io.github.qupath.gradle.GitCommitPlugin"
        }
    }
}

dependencies {
    // Make Gradle plugin available to limit platform jars
    // Couldn't find a cleaner way to get the version from the catalog
    implementation("org.bytedeco:gradle-javacpp:${libs.plugins.javacpp.get().version}")
    implementation(kotlin("stdlib-jdk8"))

    // See https://github.com/gradle/gradle/issues/15383#issuecomment-779893192 for rationale
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}