package io.github.qupath.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

import java.util.Locale;

/**
 * Useful info about the current platform to help with building custom packages.
 */
public class PlatformPlugin implements Plugin<Project> {

    private static final Platform CURRENT = findPlatform();
    
    @Override
    public void apply(Project project) {
        var platform = current();
        var extensions = project.getExtensions();
        extensions.add("platform.name", platform.getPlatformName());
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
        WINDOWS("windows", "win32-x86_64", "ico", "msi"),
        MAC_INTEL("macosx", "darwin-x86_64", "icns", "pkg"),
        MAC_AARCH64("macosx", "darwin-aarch64", "icns", "pkg"),
        LINUX("linux", "linux-x86_64", "png", "deb"),
        LINUX_AARCH64("linux", "linux-aarch64", "png", "deb"),
        UNKNOWN();
        
        private final String platformName;
        private final String iconExt;
        private final String classifier;
        private final String installerExtension;


        Platform() {
            this(null, null, null, null);
        }

        Platform(String platformName, String classifier, String iconExt, String installerExtension) {
            this.platformName = platformName;
            this.classifier = classifier;
            this.iconExt = iconExt;
            this.installerExtension = installerExtension;
        }

        /**
         * Query if the current platform is Windows.
         * @return
         */
        public boolean isWindows() {
            return this == WINDOWS;
        }

        /**
         * Query if the current platform is Mac (may be Intel or Apple Silicon)
         * @return
         */
        public boolean isMac() {
            return this == MAC_INTEL || this == MAC_AARCH64;
        }

        /**
         * Query if the current platform is Linux.
         * @return
         */
        public boolean isLinux() {
            return this == LINUX || this == LINUX_AARCH64;
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
            return getPlatformName() + " (" + getClassifier() + ")";
        }
        
    }
    
    /**
     * Get the current platform.
     * @return
     */
    public static Platform current() {
        return CURRENT;
    }

    private static Platform findPlatform() {
        var os = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
        if (os.contains("win"))
            return Platform.WINDOWS;
        else if (os.contains("nix") || os.contains("nux")) {
            if ("aarch64".equalsIgnoreCase(System.getProperty("os.arch")))
                return Platform.LINUX_AARCH64;
            return Platform.LINUX;
        } else if (os.contains("mac")) {
            if ("aarch64".equalsIgnoreCase(System.getProperty("os.arch")))
                return Platform.MAC_AARCH64;
            return Platform.MAC_INTEL;
        } else
            return Platform.UNKNOWN;
    }
    
}
