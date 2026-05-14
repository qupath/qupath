pluginManagement {
    plugins {
        kotlin("jvm") version "2.3.21"
    }
}

rootProject.name = "qupath-conventions"

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
