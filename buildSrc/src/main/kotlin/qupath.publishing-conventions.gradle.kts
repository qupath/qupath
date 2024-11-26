/**
 * Conventions for publishing modules to SciJava Maven.
 */

plugins {
    java
    `maven-publish`
}

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
        create<MavenPublication>("mavenJava") {
            groupId = "io.github.qupath"
            from(components["java"])

            pom {
                licenses {
                    license {
                        name = "GNU General Public License, Version 3"
                        url = "https://www.gnu.org/licenses/gpl-3.0.txt"
                    }
                }
            }
        }
    }

}
