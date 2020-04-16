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
	
	private final static Comparator<Version> COMPARATOR_MAIN = Comparator.comparingInt(Version::getMajor)
			.thenComparingInt(Version::getMinor)
			.thenComparingInt(Version::getPatch);
	
	private final static Comparator<Version> COMPARATOR_FULL = COMPARATOR_MAIN
			.thenComparing(Version::getSuffix, (s1, s2) -> compareSuffixes(s1, s2));
	
	private final static Comparator<String> COMPARATOR_SUFFIX = GeneralTools.smartStringComparator();
	
	private int major;
	private int minor;
	private int patch;
	private String suffix;
	
	
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
		
		return COMPARATOR_SUFFIX.compare(s1, s2);
	}
	
	/**
	 * Parse a {@link Version} object representing a semantic version number from a String.
	 * @param versionString
	 * @return
	 */
	public static Version parse(String versionString) {
		var matcher = PATTERN.matcher(versionString);
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

}
