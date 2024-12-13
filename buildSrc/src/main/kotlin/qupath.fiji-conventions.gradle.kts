/**
 * This performs some extra configuration when building QuPath
 * with Fiji's dependencies.
 */

plugins {
    application
}


/**
 * Check if we should build with Fiji dependencies
 * Can use -Pfiji=true or even just -Pfiji
 */
var buildWithFiji: Boolean by project.extra {
    providers.gradleProperty("fiji").getOrElse("false").trim().lowercase() != "false"
}

if (buildWithFiji) {

    val appName = "QuPath-Fiji"
    gradle.extra["qupath.app.name"] = "QuPath-Fiji"

    application {

        println("Building QuPath with Fiji dependencies: $appName")
        applicationName = appName

        for (toOpen in setOf(
            // Fiji
            "java.base/java.lang=ALL-UNNAMED",
            "java.base/java.nio=ALL-UNNAMED",
            "java.base/java.util=ALL-UNNAMED",
            "java.desktop/sun.awt=ALL-UNNAMED",
            "java.desktop/javax.swing=ALL-UNNAMED",
            "java.desktop/java.awt=ALL-UNNAMED",
            "java.desktop/sun.awt.X11=ALL-UNNAMED",
            "java.desktop/com.apple.eawt=ALL-UNNAMED",
            // Scenery
            "java.base/java.lang=ALL-UNNAMED",
            "java.base/java.lang.invoke=ALL-UNNAMED",
            "java.base/java.net=ALL-UNNAMED",
            "java.base/java.nio=ALL-UNNAMED",
            "java.base/java.time=ALL-UNNAMED",
            "java.base/java.util.concurrent.atomic=ALL-UNNAMED",
            "java.base/sun.nio.ch=ALL-UNNAMED",
            "java.base/sun.util.calendar=ALL-UNNAMED"
        )) {
            applicationDefaultJvmArgs += "--add-opens"
            applicationDefaultJvmArgs += toOpen
        }

        // Can't use system menubar at all - need to be make sure it is unavailable
        applicationDefaultJvmArgs += "-Dqupath.enableSystemMenuBar=false"

    }

}

