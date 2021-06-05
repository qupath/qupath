package io.github.qupath.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * Useful info about the current platform to help with building custom packages.
 */
public class PlatformPlugin implements Plugin<Project> {
    
    @Override
    public void apply(Project project) {
        var platform = current();
        var extensions = project.getExtensions();
        extensions.add("platform.name", platform.getPlatformName());
        extensions.add("platform.shortName", platform.getShortName());
        extensions.add("platform.classifier", platform.getClassifier());
        extensions.add("platform.iconExt", platform.getIconExtension());
        extensions.add("platform.installerExt", platform.getInstallerExtension());
    }
    
    /**
     * Enum representing the current platform / operating system.
     * This also provides some useful operating system-dependent values, 
     * such as the preferred icon and installer extensions.
     * <p>
     * Note that, at this time, we assume the preferred installer for Linux is deb.
     * rpm can be requested from the command line if supported by the platform.
     */
    public enum Platform {
        WINDOWS("windows", "win", "natives-windows", "ico", "msi"),
        MAC("macosx", "mac", "natives-osx", "icns", "pkg"),
        LINUX("linux", "linux", "natives-linux", "png", "deb"),
        UNKNOWN(null, null, null, null, null);
        
        private String platformName;
        private String shortName;
        private String iconExt;
        private String classifier;
        private String installerExtension;
        
        private Platform(String platformName, String shortName, String classifier, String iconExt, String installerExtension) {
            this.platformName = platformName;
            this.shortName = shortName;
            this.classifier = classifier;
            this.iconExt = iconExt;
            this.installerExtension = installerExtension;
        }
    
        /**
         * Short name representing the platform ("win", "mac", "linux").
         * @return
         */
        public String getShortName() {
            return shortName;
        }
    
        /**
         * File extension to use with JPackage icon.
         * @return
         */
        public String getIconExtension() {
            return iconExt;
        }
    
        /**
         * Classifier to use with native libraries.
         * @return
         */
        public String getClassifier() {
            return classifier;
        }
    
        /**
         * Long name of the platform, all lowercase ("windows", "macosx", "linux")
         * @return
         */
        public String getPlatformName() {
            return platformName;
        }
    
        /**
         * Preferred extension to use for an installer.
         * Note that for Linux this currently uses 'deb', since QuPath is more frequently build on Ubuntu.
         * However, it may be necessary to override this to request 'rpm'.
         * @return
         */
        public String getInstallerExtension() {
            return installerExtension;
        }
        
        @Override
        public String toString() {
            return getPlatformName();
        }
        
    }
    
    /**
     * Get the current platform.
     * @return
     */
    public static Platform current() {
        var os = System.getProperty("os.name").toLowerCase();
        if (os.indexOf("win") >= 0)
            return Platform.WINDOWS;
        else if (os.indexOf("nix") >= 0 || os.indexOf("nux") >= 0)
            return Platform.LINUX;
        else if (os.indexOf("mac") >= 0)
            return Platform.MAC;
        else
            return Platform.UNKNOWN;
    }
    
}
