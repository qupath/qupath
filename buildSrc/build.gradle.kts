plugins {
    `groovy-gradle-plugin`
    `kotlin-dsl`
    alias(libs.plugins.javacpp)
}

repositories {
    mavenCentral()
    gradlePluginPortal()
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
    implementation(kotlin("stdlib-jdk8"))

    // See https://github.com/gradle/gradle/issues/15383#issuecomment-779893192 for rationale
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))

    // Make Gradle plugin available to limit platform jars
    // Couldn't find a cleaner way to get the version from the catalog
    implementation("org.bytedeco:gradle-javacpp:${libs.plugins.javacpp.get().version}")
    // Other required Gradle plugins
    implementation("org.beryx:badass-runtime-plugin:${libs.plugins.jpackage.get().version}")
    implementation("gradle.plugin.org.gradle.crypto:checksum:${libs.plugins.checksum.get().version}")
    implementation("org.openjfx:javafx-plugin:${libs.plugins.javafx.get().version}")
    implementation("com.github.jk1:gradle-license-report:${libs.plugins.license.report.get().version}")
}