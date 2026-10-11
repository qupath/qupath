/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2026 QuPath developers, The University of Edinburgh
 * %%
 * QuPath is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * QuPath is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with QuPath.  If not, see <https://www.gnu.org/licenses/>.
 * #L%
 */

package qupath.lib.common;

import java.util.OptionalLong;

/**
 * Helper methods for querying memory use.
 * <p>
 * This is useful as a convenience, because the definitions of total, free and max memory in {@link Runtime}
 * are a little tricky to remember, and some commonly required values need to be calculated.
 * @since v0.8.0
 */
public class MemoryTools {

    /**
     * Get the total amount of memory in the Java virtual machine, which may vary over time.
     * <p>
     * Be careful: this is often confused with {@link #getMaxMemory()} (which doesn't vary).
     * @return the total memory, in bytes
     * @see Runtime#totalMemory()
     */
    public static long getCurrentTotalMemory() {
        return Runtime.getRuntime().totalMemory();
    }

    /**
     * Get the amount of free memory currently available in the Java virtual machine, which may vary over time.
     * @return the free memory, in bytes
     * @see Runtime#freeMemory() ()
     */
    public static long getCurrentFreeMemory() {
        return Runtime.getRuntime().freeMemory();
    }

    /**
     * Get the total free memory in the Java virtual machine that could potentially be allocated.
     * This is calculated as {@code getMaxMemory() - getUsedMemory()} if the max memory is available.
     * Otherwise, it returns the same as {@code getAvailableFreeMemory()}.
     * @return the total free memory, in bytes
     * @see #getCurrentFreeMemory()
     * @see #getMaxMemory()
     * @see #getCurrentUsedMemory()
     */
    public static long getTotalFreeMemory() {
        var maxMemory = getMaxMemory();
        if (isValidMaxMemory(maxMemory)) {
            return maxMemory - getCurrentUsedMemory();
        } else {
            return getCurrentFreeMemory();
        }
    }

    /**
     * Get the amount of memory currently used, which may vary over time.
     * This is the difference between the current total memory and the current free memory.
     * @return the used memory, in bytes
     * @see #getCurrentTotalMemory()
     * @see #getCurrentFreeMemory()
     */
    public static long getCurrentUsedMemory() {
        return getCurrentTotalMemory() - getCurrentFreeMemory();
    }

    /**
     * Get the maximum memory that the Java virtual machine will attempt to use.
     * <p>
     * This is set when QuPath is started, and is a useful value when determining cache sizes.
     * Conceivably, it may not be known if no limit has been set.
     * @return the maximum memory, in bytes, or {@link Long#MAX_VALUE} if this is not specified.
     * @see Runtime#maxMemory() ()
     * @see #maxMemoryKnown()
     */
    public static long getMaxMemory() {
        return Runtime.getRuntime().maxMemory();
    }

    /**
     * Query if the maximum memory is known.
     * @return true if {@link #getMaxMemory()} returns a valid value, false otherwise (i.e. there is no specified max).
     * @see #maxMemoryKnown()
     */
    public static boolean maxMemoryKnown() {
        return isValidMaxMemory(getMaxMemory());
    }

    /**
     * Get a percentage of the maximum memory, if possible.
     * This returns an {@link OptionalLong} so that a default value can easily be used if the maximum memory is unknown.
     * @param percent the percentage
     * @return an optional containing the requested memory value in bytes, or an empty optional if
     *         {@link #maxMemoryKnown()} is {@code false}.
     * @throws IllegalArgumentException if percent is less than 0 or greater than 100.
     */
    public static OptionalLong getMaxMemoryPercent(double percent) throws IllegalArgumentException {
        var maxMemory = getMaxMemory();
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("Percent must be between 0 and 100, not " + percent);
        }
        if (isValidMaxMemory(maxMemory)) {
            return OptionalLong.of(Math.round(maxMemory * (percent / 100.0)));
        } else {
            return OptionalLong.empty();
        }
    }

    private static boolean isValidMaxMemory(long maxMemory) {
        return maxMemory > 0L && maxMemory < Long.MAX_VALUE;
    }

    /**
     * Convert a number of kilobytes to a number of bytes.
     * This method exists to make code more readable, not because the maths is so involved.
     * @param kilobytes the value of kilobytes
     * @return kilobytes multiplied by 1_000
     */
    public static long kilobytesToBytes(long kilobytes) {
        return kilobytes * 1000L;
    }

    /**
     * Convert a number of megabytes to a number of bytes.
     * This method exists to make code more readable, not because the maths is so involved.
     * @param megabytes the value of megabytes
     * @return megabytes multiplied by 1_000_000
     */
    public static long megabytesToBytes(long megabytes) {
        return kilobytesToBytes(megabytes * 1000L);
    }

    /**
     * Convert a number of gigabytes to a number of bytes.
     * This method exists to make code more readable, not because the maths is so involved.
     * @param gigabytes the value of gigabytes
     * @return gigabytes multiplied by 1_000_000_000
     */
    public static long gigabytesToBytes(long gigabytes) {
        return megabytesToBytes(gigabytes * 1000L);
    }

    /**
     * Convert a number of kibibytes to a number of bytes.
     * This method exists to make code more readable, not because the maths is so involved.
     * @param kibibytes the value of kibibytes
     * @return kibibytes multiplied by 1024
     */
    public static long kibibytesToBytes(long kibibytes) {
        return kibibytes * 1024L;
    }

    /**
     * Convert a number of mebibytes to a number of bytes.
     * This method exists to make code more readable, not because the maths is so involved.
     * @param mebibytes the value of mebibytes
     * @return mebibytes multiplied by 1024 * 1024
     */
    public static long mebibytesToBytes(long mebibytes) {
        return kibibytesToBytes(mebibytes * 1024L);
    }

    /**
     * Convert a number of gibibytes to a number of bytes.
     * This method exists to make code more readable, not because the maths is so involved.
     * @param gibibytes the value of gibibytes
     * @return gibibytes multiplied by 1024 * 1024 * 1024
     */
    public static long gibibytesToBytes(long gibibytes) {
        return mebibytesToBytes(gibibytes * 1024L);
    }

}
