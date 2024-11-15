/**
 * Utility methods for build scripts.
 */

package io.github.qupath.gradle;

public class Utils {

    /**
     * Get the current platform.
     * @return
     */
    public static PlatformPlugin.Platform currentPlatform() {
        return PlatformPlugin.current();
    }


}
