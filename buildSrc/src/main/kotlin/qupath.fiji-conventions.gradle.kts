plugins {
    application
}


dependencies {

    implementation("org.jogamp.gluegen:gluegen-rt:2.5.0:natives-macosx-universal")
    implementation("org.jogamp.jogl:jogl-all:2.5.0:natives-macosx-universal")
    implementation("sc.fiji:fiji:2.16.0")
    {
        exclude(group = "org.jogamp.gluegen")
        exclude(group = "org.jogamp.jogl")
        exclude(group = "org.bytedeco", module = "ffmpeg")
        exclude(module = "jai-core")
    }

}


application {

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

}