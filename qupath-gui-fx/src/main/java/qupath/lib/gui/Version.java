/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2020 QuPath developers, The University of Edinburgh
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

package qupath.lib.gui;

import java.util.Comparator;
import java.util.Objects;
import java.util.regex.Pattern;

import qupath.lib.common.GeneralTools;

/**
 * Helper class for working with semantic versioning.
 * <p>
 * This should be in the form {@code major.minor.patch}.
 * An optional suffix is also permitted to indicate a pre-release, alpha, beta or milestone version,
 * in the form {@code major.minor.patch-suffix}, where the suffix may contain any alphanumeric characters or periods.
 * <p>
 * Implementation note: No checking is currently performed to ensure any suffix conforms to semantic 
 * versioning standards - however this behavior may change.
 * 
 * @author Pete Bankhead
 *
 */
public class Version implements Comparable<Version> {
	
	private static Pattern PATTERN = Pattern.compile(
			"(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-([\\w.]+))?");
	
	/**
	 * Compare major versions only.
	 */
	public final static Comparator<Version> COMPARATOR_MAJOR = Comparator.comparingInt(Version::getMajor);

	/**
	 * Compare major then minor versions.
	 */
	public final static Comparator<Version> COMPARATOR_MAJOR_MINOR = COMPARATOR_MAJOR
																			.thenComparing(Version::getMinor);

	/**
	 * Compare major then minor then patch versions (ignoring suffixes).
	 */
	public final static Comparator<Version> COMPARATOR_MAJOR_MINOR_PATCH = COMPARATOR_MAJOR_MINOR
																			.thenComparing(Version::getPatch);

	/**
	 * Compare full version, including any suffixes.
	 */
	public final static Comparator<Version> COMPARATOR_FULL = COMPARATOR_MAJOR_MINOR_PATCH
																		.thenComparing(Version::getSuffix, (s1, s2) -> compareSuffixes(s1, s2));

	private final static Comparator<String> COMPARATOR_SUFFIX = GeneralTools.smartStringComparator();
	
	private int major;
	private int minor;
	private int patch;
	private String suffix;
	
	/**
	 * Constant representing any unknown version.
	 */
	public static final Version UNKNOWN = new Version(-1, -1, -1, "UNKNOWN.VERSION");
	
	
	private Version(int major, int minor, int patch, String suffix) {
		this.major = major;
		this.minor = minor;
		this.patch = patch;
		this.suffix = suffix == null ? "" : suffix;
	}
	
	/**
	 * Get the major version number (X.0.0).
	 * @return
	 */
	public int getMajor() {
		return major;
	}
	
	/**
	 * Get the minor version number (0.X.0).
	 * @return
	 */
	public int getMinor() {
		return minor;
	}
	
	/**
	 * Get the patch version number (0.0.X).
	 * @return
	 */
	public int getPatch() {
		return patch;
	}
	
	/**
	 * Get the (optional) suffix, which may be appended at the end of the version 
	 * number to indicate a pre-release (0.0.0-X).
	 * @return
	 */
	public String getSuffix() {
		return suffix;
	}
	
	/**
	 * Returns true if the version has a suffix, which typically indicates that it 
	 * does not refer to a final stable version.
	 * @return
	 */
	public boolean hasSuffix() {
		return suffix != null && !suffix.isBlank();
	}
	
	@Override
	public String toString() {
		String v = major + "." + minor + "." + patch;
		if (suffix != null && !suffix.isEmpty())
			v += "-" + suffix;
		return v;
	}
	
	private static int compareSuffixes(String s1, String s2) {
		if (s1 == null)
			s1 = "";
		else
			s1 = s1.trim();
		if (s2 == null)
			s2 = "";
		else
			s2 = s2.trim();
		
		if (Objects.equals(s1, s2))
			return 0;
		
		if (s1.isEmpty())
			return 1;
		if (s2.isEmpty())
			return -1;
		
		// If one string is longer, and contains the former, it is 'earlier'
		// For example m1-SNAPSHOT is earlier than m1
		if (s1.length() > s2.length() && s1.startsWith(s2))
			return -1;
		else if (s2.length() > s1.length() && s2.startsWith(s1))
			return 1;
		
		return COMPARATOR_SUFFIX.compare(s1, s2);
	}
	
	/**
	 * Parse a {@link Version} object representing a semantic version number from a String.
	 * <p>
	 * The version should be in the form {@code x.y.z} or {@code x.y.z-additional}, where {@code additional} 
	 * should be a dot-separated patch version identifier. In the case that it is hyphen-separated, the hyphens will 
	 * be replaced by dots.
	 * 
	 * @param versionString the version String to be parsed
	 * @return a Version parsed from this string
	 * @throws IllegalArgumentException if no version could be parsed from the String
	 */
	public static Version parse(String versionString) throws IllegalArgumentException {
		if (versionString.toLowerCase().startsWith("v"))
			versionString = versionString.substring(1);
		int ind = versionString.indexOf("-");
		if (ind >= 0 && ind < versionString.length()-1) {
			versionString = versionString.substring(0, ind+1) + versionString.substring(ind+1).replaceAll("-", ".");
		}
		var matcher = PATTERN.matcher(versionString.strip());
		if (matcher.matches()) {
			int major = Integer.parseInt(matcher.group(1));
			int minor = Integer.parseInt(matcher.group(2));
			int patch = Integer.parseInt(matcher.group(3));
			String suffix = null;
			if (matcher.groupCount() > 3)
				suffix = matcher.group(4);
			return new Version(major, minor, patch, suffix);
		} else
			throw new IllegalArgumentException("No version could be parsed from " + versionString);
	}

	@Override
	public int compareTo(Version o) {
		return COMPARATOR_FULL.compare(this, o);
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + major;
		result = prime * result + minor;
		result = prime * result + patch;
		result = prime * result + ((suffix == null) ? 0 : suffix.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Version other = (Version) obj;
		if (major != other.major)
			return false;
		if (minor != other.minor)
			return false;
		if (patch != other.patch)
			return false;
		if (suffix == null) {
			if (other.suffix != null)
				return false;
		} else if (!suffix.equals(other.suffix))
			return false;
		return true;
	}

}