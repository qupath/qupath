/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
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

package qupath.lib.common;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.Locale.Category;

import org.apache.commons.math3.util.Precision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.CharStreams;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * Collection of generally useful static methods.
 * 
 * @author Pete Bankhead
 *
 */
public final class GeneralTools {
	
	final private static Logger logger = LoggerFactory.getLogger(GeneralTools.class);
	
	
	private final static String LATEST_VERSION = getCurrentVersion();
	
	/**
	 * Request the version of QuPath.
	 * 
	 * @return
	 */
	public static String getVersion() {
		return LATEST_VERSION;
	}
	
	/**
	 * Get the current QuPath version.
	 * @return
	 */
	private static String getCurrentVersion() {
		var version = getPackageVersion(GeneralTools.class);
		// v0.2, less reliable way
		if (version == null) {
			var path = Paths.get("VERSION");
			if (!Files.exists(path))
				path = Paths.get("app/VERSION");
			if (Files.exists(path)) {
				try {
					version = Files.readString(path);
				} catch (IOException e) {
					logger.error("Unable to read version from {}", path);
				}
			}
		}
		if (version == null) {
			logger.warn("QuPath version is unknown! Proceed with caution: this may cause problems with reading/writing projects.");
			return null;
		}
		return version.strip();
	}
	
	/**
	 * Try to determine the version of a jar containing a specified class.
	 * This first checks the implementation version in the package, then looks for a VERSION 
	 * file stored as a resource.
	 * 
	 * @return the version, if available, or null if no version is known.
	 */
	public static String getPackageVersion(Class<?> cls) {
		// Version should be preserved in the manifest
		String version = cls.getPackage().getImplementationVersion();
		if (version == null) {
			// From v0.3 onwards, the version should also be stored as a resource
			try {
				var stream = GeneralTools.class.getResourceAsStream("/VERSION");
				if (stream != null) {
					version = readInputStreamAsString(stream);
				}
			} catch (Exception e) {
				logger.error("Error reading version: " + e.getLocalizedMessage(), e);
			}
		}
		if (version == null || version.isBlank())
			return null;
		return version.strip();
	}
	
	// Suppressed default constructor for non-instantiability
	private GeneralTools() {
		throw new AssertionError();
	}
	
	
	private static List<String> DEFAULT_EXTENSIONS = Arrays.asList(
			".ome.tif", ".ome.tiff", ".tar.gz"
			);
	
	/**
	 * Get filename extension. Some implementation notes:
	 * <ul>
	 * <li>Note that this is <i>generally</i> 'the final dot and beyond', however this method 
	 * also handles several important special cases: ".ome.tif", ".ome.tiff" and ".tar.gz".</li>
	 * <li>The dot is included as the first character.</li>
	 * <li>No check is performed to see if the  file is actually a directory, but if a dot is the final character then no 
	 * extension is returned.</li>
	 * <li>The extension is returned as-is, without adjusting to be upper or lower case.</li>
	 * </ul>
	 * @param file
	 * @return
	 * see #getNameWithoutExtension(File)
	 */
	public static Optional<String> getExtension(File file) {
		Objects.nonNull(file);
		return getExtension(file.getName());
	}
	
	/**
	 * Get extension from a filename. Some implementation notes:
	 * <ul>
	 * <li>Note that this is <i>generally</i> 'the final dot and beyond', however this method 
	 * also handles several important special cases: ".ome.tif", ".ome.tiff" and ".tar.gz".</li>
	 * <li>The dot is included as the first character.</li>
	 * <li>No check is performed to see if the  file is actually a directory, but if a dot is the final character then no 
	 * extension is returned.</li>
	 * <li>The extension is returned as-is, without adjusting to be upper or lower case.</li>
	 * </ul>
	 * @param name
	 * @return
	 * @see #getExtension(File)
	 * @see #getNameWithoutExtension(File)
	 */
	public static Optional<String> getExtension(String name) {
		Objects.nonNull(name);
		var lower = name.toLowerCase();
		String ext = null;
		for (var temp : DEFAULT_EXTENSIONS) {
			if (lower.endsWith(temp)) {
				ext = temp;
				break;
			}
		}
		if (ext == null) {
			int ind = name.lastIndexOf(".");
			if (ind >= 0) {
				ext = name.substring(ind);
				// Check we only have letter
				if (!ext.matches(".\\w*"))
					ext = null;
			}
		}
		return ext == null || ext.equals(".") ? Optional.empty() : Optional.of(lower.substring(lower.length()-ext.length()));
	}
	
	/**
	 * Strip characters that would make a String invalid as a filename.
	 * This test is very simple, and may not catch all problems; the behavior of the method may 
	 * improve in future versions.
	 * <p>
	 * Note that the test is not platform-dependent, and may be stricter than absolutely necessary - 
	 * for example, by removing newline characters.
	 * This can result in some filenames that <i>would</i> be valid on the current platform 
	 * being modified. This can however be necessary to help retain cross-platform portability.
	 * @param name
	 * @return the (possibly-shortened) filename without invalid characters
	 */
	public static String stripInvalidFilenameChars(String name) {
		return name.replaceAll("[\\\\/:\"*?<>|\\n\\r]+", "");
	}
	
	/**
	 * Returns true if the output of {@link #stripInvalidFilenameChars(String)} matches the provided name, 
	 * and the name is not null or blank.
	 * @param name
	 * @return true if the name is expected to be valid, false otherwise
	 * @see #stripInvalidFilenameChars(String)
	 */
	public static boolean isValidFilename(String name) {
		return name != null && !name.isBlank() && name.equals(stripInvalidFilenameChars(name));
	}
	
	/**
	 * Get the file name with extension removed.
	 * @param file
	 * @return
	 * {@link #getExtension(File)}
	 */
	public static String getNameWithoutExtension(File file) {
		var ext = getExtension(file).orElse(null);
		String name = file.getName();
		return ext ==  null ? name : name.substring(0, name.length() - ext.length());
	}
	
	/**
	 * Get the file name with extension removed.
	 * @param name
	 * @return
	 * {@link #getExtension(File)}
	 */
	public static String getNameWithoutExtension(String name) {
		var ext = getExtension(name).orElse(null);
		return ext ==  null ? name : name.substring(0, name.length() - ext.length());
	}
	
	/**
	 * Returns true for file extensions containing multiple parts (or 'dots').
	 * Examples include ome.tif and tar.gz, which can be problematic with some file choosers.
	 * @param ext
	 * @return
	 */
	public static boolean isMultipartExtension(String ext) {
		if (ext.length() <= 1)
			return false;
		if (ext.startsWith("."))
			return isMultipartExtension(ext.substring(1));
		return ext.length() - ext.replace(".", "").length() > 0;
	}
	
	
	/**
	 * Check if a string is blank, i.e. it is null or its length is 0.
	 * @param s
	 * @param trim If true, any string will be trimmed before its length checked.
	 * @return True if the string is null or empty.
	 */
	public static boolean blankString(final String s, final boolean trim) {
		return s == null || (trim ? s.trim().length() == 0 : s.length() == 0);
	}
	
	/**
	 * Escape backslashes in an absolute file path - useful when scripting.
	 * 
	 * @param path
	 * @return
	 */
	public static String escapeFilePath(final String path) {
		return path.replace("\\", "\\\\");
	}
	
	/**
	 * Clip a value to be within a specific range.
	 * 
	 * @param value
	 * @param min
	 * @param max
	 * @return
	 */
	public static int clipValue(final int value, final int min, final int max) {
		return value < min ? min : (value > max ? max : value);
	}
	
	/**
	 * Clip a value to be within a specific range.
	 * 
	 * @param value
	 * @param min
	 * @param max
	 * @return
	 */
	public static double clipValue(final double value, final double min, final double max) {
		return value < min ? min : (value > max ? max : value);
	}

	/**
	 * Test if two doubles are approximately equal, within a specified tolerance.
	 * <p>
	 * Relies on apache.common's method as the history of this method proved this task not as straightforward as initially thought.
	 * <p>
	 * Note that this calculation changed in v0.2.0-m4 and in v0.3.0. The behavior prior to v0.2.0-m4 divided the absolute difference 
	 * by the first value only, which is not robust to differences in the input order or if the first value is negative. The behavior 
	 * before v0.3.0 returned whether the absolute difference divided by the average is less than the specified tolerance.
	 * 
	 * @param n1
	 * @param n2
	 * @param tolerance
	 * @return
	 */
	public static boolean almostTheSame(double n1, double n2, double tolerance) {
//		// Behavior prior to v0.3.0
//		if (n1 == n2)
//			return true;
//		double difference = n1 - n2;
//		double average = (n1/2 + n2/2);
//		return Math.abs(difference / average) < tolerance;
		return Precision.equalsWithRelativeTolerance(n1, n2, tolerance);
	}
	
	
	/**
	 * Try to convert a path to a URI.
	 * <p>
	 * This currently does a very simple check for a known scheme at the beginning 
	 * ("http:", "https:" or ""file:") to see if it can construct the URI directly; 
	 * if not, it assumes the path refers to a local file (as it generally did in 
	 * QuPath 0.1.2 and earlier). This method does not encode special characters.
	 * 
	 * @param path
	 * @return
	 * @throws URISyntaxException
	 * @see #toEncodedURI(String path)
	 */
	public static URI toURI(String path) throws URISyntaxException {
		if (path == null || path.isEmpty())
			return new URI("");
		if (path.startsWith("http:") || path.startsWith("https:") || path.startsWith("file:"))
			return new URI(path);
		return new File(path).toURI();
	}
	
	/**
	 * Try to convert a path to an encoded URI.
	 * <p>
	 * URIs do not accept some characters (e.g. "|"). This method will perform a simple check for
	 * {@code http:} and {@code https:} schemes at the beginning of the URI. It will then modify 
	 * the Query (@see <a href=https://docs.oracle.com/javase/tutorial/networking/urls/urlInfo.html>Query</a>) 
	 * to a valid form. Finally, a reconstructed valid URI is returned. Note: this method will 
	 * only encode the Query part of the URI (i.e. Fragments, if present, will be ignored ).
	 * <p>
	 * E.g. "{@code https://host?query=first|second}" will return "{@code https://host?query%3Dfirst%7Csecond}".
	 * 
	 * @param path
	 * @return encodedURI
	 * @throws URISyntaxException
	 * @throws UnsupportedEncodingException
	 * @throws MalformedURLException 
	 */
	public static URI toEncodedURI(String path) throws URISyntaxException, UnsupportedEncodingException, MalformedURLException {
		if (path == null || path.isEmpty())
			return new URI("");
		if (path.startsWith("http:") || path.startsWith("https:")) {
			String urlQuery = new URL(path).getQuery();
			if (urlQuery != null && !urlQuery.isEmpty()) {
				String encodedQueryString = URLEncoder.encode(urlQuery, StandardCharsets.UTF_8);
				String encodedURL = path.substring(0, path.lastIndexOf(urlQuery)) + urlQuery.replace(urlQuery, encodedQueryString);
				return new URI(encodedURL);
			}
			return new URI(path);
		}
		return new URI(path);
	}
	
	
	/**
	 * Try to identify a Path from a URI, dropping any query or fragment elements.
	 * <p>
	 * This returns the Path if successful and null otherwise (e.g. if the URI does not correspond to a file). 
	 * There is no check whether the Path exists, and support for an authority is platform-dependent.
	 * 
	 * @param uri
	 * @return
	 */
	public static Path toPath(URI uri) {
		String scheme = uri.getScheme();
		if (scheme != null && !"file".equals(scheme))
			return null;
		try {
			if (uri.getFragment() != null || uri.getQuery() != null)
				uri = new URI(uri.getScheme(), uri.getHost(), uri.getPath(), null);
			return Paths.get(uri);
		} catch (URISyntaxException | IllegalArgumentException | FileSystemNotFoundException e) {
			logger.warn("Problem parsing file from URI " + uri + " (" + e.getLocalizedMessage() + ")", e);
		}
		return null;
	}
	

	/**
	 * Convert a double array to string, with a specified number of decimal places.
	 * Trailing zeros are not included.
	 * 
	 * @param locale
	 * @param array
	 * @param delimiter
	 * @param nDecimalPlaces
	 * @return
	 */
	public static String arrayToString(final Locale locale, final double[] array, final String delimiter, final int nDecimalPlaces) {
		StringBuilder sb = new StringBuilder();
		if (array.length == 0)
			return "";
		for (int i = 0; i < array.length; i++) {
			sb.append(formatNumber(locale, array[i], nDecimalPlaces));
			if (i < array.length-1)
				sb.append(delimiter);
		}
		return sb.toString();
	}
	
	/**
	 * Convert a double array to a String using a space as a delimiter.
	 * 
	 * @param locale
	 * @param array
	 * @param nDecimalPlaces
	 * @return
	 */
	public static String arrayToString(final Locale locale, final double[] array, final int nDecimalPlaces) {
		return arrayToString(locale, array, " ", nDecimalPlaces);
	}
	
	/**
	 * Convert a String array to a single string, with a specified delimiter string.
	 * @param array
	 * @param delimiter
	 * @return
	 */
	public static String arrayToString(final Object[] array, final String delimiter) {
		StringBuilder sb = new StringBuilder();
		if (array.length == 0)
			return "";
		for (int i = 0; i < array.length; i++) {
			sb.append(array[i]);
			if (i < array.length-1)
				sb.append(delimiter);
		}
		return sb.toString();
	}
	
	
	/**
	 * Split new lines (in a cross-platform way... i.e. not with s.split("\n"), which is asking for trouble).
	 * 
	 * @param s
	 * @return
	 */
	public static String[] splitLines(final String s) {
		List<String> lines = new ArrayList<>();
		try (Scanner scanner = new Scanner(s)) {
			while (scanner.hasNextLine())
				lines.add(scanner.nextLine());
		}
		return lines.toArray(new String[lines.size()]);
	}
	
	
	/**
	 * Create a new DecimalFormat that may be used to convert a number to have a maximum of nDecimalPlaces
	 * (trailing zeros are not shown).
	 * 
	 * Important note: this always formats as 1.234 rather than 1,234 - regardless of Locale.
	 * Consequently its results are more predictable... but may not be consistent with other number formatting on 
	 * the specified platform.
	 * 
	 * @param nDecimalPlaces
	 * @return
	 */
	public static NumberFormat createFormatter(final int nDecimalPlaces) {
		NumberFormat nf = NumberFormat.getInstance();
		nf.setMaximumFractionDigits(nDecimalPlaces);
		return nf;
	}
	
	/**
	 * Cache of NumberFormat objects
	 */
	private static Map<Locale, NumberFormat> formatters = new HashMap<>();
	
	/**
	 * Format a value with a maximum number of decimal places, using the default Locale.
	 * 
	 * @param value
	 * @param maxDecimalPlaces
	 * @return
	 */
	public synchronized static String formatNumber(final double value, final int maxDecimalPlaces) {
		return formatNumber(Locale.getDefault(Category.FORMAT), value, maxDecimalPlaces);
	}
	
	/**
	 * Format a value with a maximum number of decimal places, using a specified Locale.
	 * 
	 * @param locale
	 * @param value
	 * @param maxDecimalPlaces
	 * @return
	 */
	public synchronized static String formatNumber(final Locale locale, final double value, final int maxDecimalPlaces) {
		NumberFormat nf = formatters.get(locale);
		if (nf == null) {
			nf = NumberFormat.getInstance(locale);
			nf.setGroupingUsed(false);
			formatters.put(locale, nf);
		}
		nf.setMaximumFractionDigits(maxDecimalPlaces);
		return nf.format(value);
	}
	
	

	/**
	 * Parse the contents of a JSON String.
	 * <p>
	 * Note that this is pretty unsophisticated... also, no localization is performed (using Java's Locales, for example) -
	 * so that decimal values should be provided in the form 1.234 (and not e.g. 1,234).
	 * 
	 * @param s
	 * @return
	 */
	// TODO: MOVE SOMEWHERE SENSIBLE
	public static Map<String, String> parseArgStringValues(String s) {
		if (s == null)
			return Collections.emptyMap();
		Type type = new TypeToken<Map<String, String>>() {}.getType();
		return new Gson().fromJson(s, type);
	}

	
	/**
	 * Get a string to represent um (but with the proper 'mu' symbol)
	 * 
	 * @return
	 */
	public final static String micrometerSymbol() {
		return SYMBOL_MICROMETER;
	}
	
	/**
	 * Small Green mu (useful for micrometers)
	 */
	public final static char SYMBOL_MU = '\u00B5';

	/**
	 * Small Greek sigma (useful for Gaussian filter sizes, standard deviations)
	 */
	public final static char SYMBOL_SIGMA = '\u03C3';

	/**
	 * String to represent um (but with the proper 'mu' symbol)
	 */
	public final static String SYMBOL_MICROMETER = '\u00B5' + "m";
	
	
	/**
	 * Read the entire contents of a file into a single String.
	 * <p>
	 * Note that from QuPath v0.3 this assumes UTF8 encoding. 
	 * Previously, platform-specific encoding was assumed.
	 * 
	 * @param path
	 * @return
	 * @throws IOException
	 */
	public static String readFileAsString(final String path) throws IOException {
		return Files.readString(Paths.get(path), StandardCharsets.UTF_8);
	}
	
	
	/**
	 * Read the entire contents of an InputStream into a single String.
	 * <p>
	 * Note that from QuPath v0.3 this assumes UTF8 encoding. 
	 * Previously, platform-specific encoding was assumed.
	 * 
	 * @param stream
	 * @return
	 * @throws IOException
	 */
	public static String readInputStreamAsString(final InputStream stream) throws IOException {
		return CharStreams.toString(new InputStreamReader(stream, StandardCharsets.UTF_8));
//		Scanner scanner = new Scanner(stream);
//		String contents = scanner.useDelimiter("\\Z").next();
//		scanner.close();
//		return contents;
	}
	

	/**
	 * Check whether a path ends with one of a number of specified extensions (case insensitive).
	 * 
	 * @param path
	 * @param extensions
	 * @return
	 */
	public static boolean checkExtensions(final String path, String... extensions) {
		String pathLower = path.toLowerCase();
		for (String ext : extensions) {
			if (!ext.startsWith("."))
				ext = "." + ext;
			if (pathLower.endsWith(ext.toLowerCase()))
				return true;
		}
		return false;
	}

	/**
	 * Return true if running on macOS.
	 * @return
	 */
	public static boolean isMac() {
		String os = System.getProperty("os.name").toLowerCase();
		return os.indexOf("mac") >= 0 || os.indexOf("darwin") >= 0;
	}

	/**
	 * Return true if running on Linux.
	 * @return
	 */
	public static boolean isLinux() {
		String os = System.getProperty("os.name").toLowerCase();
		return os.indexOf("nux") >= 0;
	}

	/**
	 * Return true if running on Windows.
	 * @return
	 */
	public static boolean isWindows() {
		String os = System.getProperty("os.name").toLowerCase();
		return os.indexOf("win") >= 0;
	}


	/**
	 * Delete a file, optionally requesting that it be moved to the trash rather than permanently deleted.
	 * <p>
	 * Note that the behavior of this method is system-dependent, and there is no guarantee the file will 
	 * indeed be moved to the trash.
	 * 
	 * @param fileToDelete
	 * @param preferTrash
	 */
	public static void deleteFile(File fileToDelete, boolean preferTrash) {
		if (preferTrash && Desktop.isDesktopSupported()) {
			var desktop = Desktop.getDesktop();
			if (desktop.isSupported(Desktop.Action.MOVE_TO_TRASH) && desktop.moveToTrash(fileToDelete))
				return;
		}
		fileToDelete.delete();
	}


	/**
	 * Read URL as String, with specified timeout in milliseconds.
	 * <p>
	 * The content type is checked, and an IOException is thrown if this doesn't start with text/plain.
	 * 
	 * @param url
	 * @param timeoutMillis
	 * @return
	 * @throws IOException 
	 */
	public static String readURLAsString(final URL url, final int timeoutMillis) throws IOException {
		StringBuilder response = new StringBuilder();
		String line = null;
		URLConnection connection = url.openConnection();
		connection.setConnectTimeout(timeoutMillis);
		String contentType = connection.getContentType();
		if (contentType.startsWith("text/plain")) {
			try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
				while ((line = in.readLine()) != null) 
					response.append(line + "\n");
			}
			return response.toString();
		} else throw new IOException("Expected content type text/plain, but got " + contentType);
	}


	/**
	 * Count the number of NaN values in an array.
	 * @param vals
	 * @return
	 */
	public static int numNaNs(double[] vals) {
		int count = 0;
		for (double v : vals) {
			if (Double.isNaN(v))
				count++;
		}
		return count;
	}


	/**
	 * Compute the sum of elements in a long array (possibly representing a histogram).
	 * @param values
	 * @return
	 */
	public static long sum(long[] values) {
		long total = 0L;
		for (long v : values)
			total += v;
		return total;
	}
	
	
	/**
	 * Generate a name that is distinct from the names in an existing collection, while being based on a provided name.
	 * <p>
	 * This is useful, for example, when duplicating named items and requiring that the duplicates can be distinguished.
	 * The precise way in which the name is derived is implementation-dependent, with the only requirement that it be 
	 * recognizably derived from the base name.
	 * <p>
	 * Currently, names are generated in the form {@code "base (i)"} where {@code i} is an integer.
	 * <p>
	 * Note that if the base already has the same form, any existing integer will be stripped away; 
	 * for example providing {@code "name (1)"} as the base will yield the output {@code "name (2)"}, 
	 * (assuming this name does not already exist), rather than {@code "name (1) (1)"}.
	 * 
	 * @param base the (non-empty) base from which the name should be derived
	 * @param existingNames a collection of names that are already in use, and therefore must be avoided
	 * @return the distinct name
	 */
	public static String generateDistinctName(String base, Collection<String> existingNames) {
		if (!existingNames.contains(base))
			return base;
		
		// Check if we already end with a number, and if so strip that
		if (Pattern.matches(".* (\\([\\d]+\\))$", base))
			base = base.substring(0, base.lastIndexOf(" ("));
		
		// Check for the highest number we currently have
		int lastInd = 0;
		var pattern = base.isEmpty() ? Pattern.compile("\\(([\\d]+)\\)") : Pattern.compile(base + " \\(([\\d]+)\\)");
		for (var existing : existingNames) {
			var matcher = pattern.matcher(existing);
			if (base.isEmpty()) {
				if (existing.stripLeading().length() == 3 && matcher.find())
					lastInd = Math.max(lastInd, Integer.parseInt(matcher.group(1)));					
			} else {
				if (matcher.find())
					lastInd = Math.max(lastInd, Integer.parseInt(matcher.group(1)));				
			}
				
		}
        if (!base.isEmpty())
            base = base + " ";
		return base + "(" + (lastInd + 1) + ")";
	}
	
	
	/**
	 * Estimate the current available memory in bytes, based upon the JVM max and the memory currently used.
	 * <p>
	 * This may be used to help determine whether a memory-hungry operation should be attempted.
	 * 
	 * @return the estimated unused memory in bytes
	 */
	public static long estimateAvailableMemory() {
		System.gc();
		return Runtime.getRuntime().maxMemory() - estimateUsedMemory();
	}
	
	/**
	 * Estimate the current used memory.
	 * 
	 * @return the estimated allocated memory in bytes
	 */
	public static long estimateUsedMemory() {
		var runtime = Runtime.getRuntime();
		return runtime.totalMemory() - runtime.freeMemory();
	}
	
	/**
	 * Smart-sort a collection using the {@link Object#toString()} method applied to each element.
	 * See {@link #smartStringSort(Collection, Function)} for more details.
	 * @param <T>
	 * @param collection collection to be sorted (results are retained in-place)
	 * @see #smartStringSort(Collection, Function)
	 */
	public static <T> void smartStringSort(Collection<T> collection) {
		smartStringSort(collection, T::toString);
	}
	
	/**
	 * Smart-sort a collection after extracting a String representation of each element.
	 * This differs from a 'normal' sort by splitting the String into lists of numeric and non-numeric parts,
	 * and comparing corresponding elements separately.
	 * This can sometimes give more intuitive results than a simple String sort, which would treat "10" as 
	 * 'less than' "2".
	 * <p>
	 * For example, applying a simple sort to the list {@code ["a1", "a2", "a10"]} will result in 
	 * {@code ["a1", "a10", "a2]}. Smart-sorting would leave the list unchanged.
	 * <p>
	 * Note: Currently this method considers only positive integer values, treating characters such as 
	 * '+', '-', ',', '.' and 'e' as distinct elements of text.
	 * @param <T>
	 * @param collection collection to be sorted (results are retained in-place)
	 * @param extractor function used to convert each element of the collection to a String representation
	 */
	public static <T> void smartStringSort(Collection<T> collection, Function<T, String> extractor) {
		for (var temp : collection)
			System.err.println(new StringPartsSorter<T>(temp, temp.toString()));
		var list = collection.stream().map(c -> new StringPartsSorter<>(c, extractor.apply(c))).sorted().map(s -> s.obj).collect(Collectors.toList());
		collection.clear();
		collection.addAll(list);
	}
	
	/**
	 * Comparator for smart String sorting.
	 * Note: This comparator is very inefficient. Where possible {@link #smartStringSort(Collection, Function)} should 
	 * be used instead.
	 * @return a String comparator that parses integers from within the String so they may be compared by value
	 */
	public static Comparator<String> smartStringComparator() {
		return (String s1, String s2) -> new StringPartsSorter<>(s1, s1).compareTo(new StringPartsSorter<>(s2, s2));
	}
	
	/**
	 * Helper class for smart-sorting.
	 * @param <T>
	 */
	private static class StringPartsSorter<T> implements Comparable<StringPartsSorter<T>> {
		
		private final static Pattern PATTERN = Pattern.compile("(\\d+)");
		
		private T obj;
		private List<Object> parts;
		
		StringPartsSorter(T obj, String s) {
			this.obj = obj;
			if (s == null)
				s = Objects.toString(obj);
			// Break the string into numeric & non-numeric parts
			var matcher = PATTERN.matcher(s);
			parts = new ArrayList<>();
			int next = 0;
			while (matcher.find()) {
				int s1 = matcher.start();
				if (s1 > next) {
					parts.add(s.substring(next, s1));
				}
				parts.add(new BigDecimal(matcher.group()));
				next = matcher.end();
			}
			if (next < s.length())
				parts.add(s.substring(next));
		}

		@Override
		public int compareTo(StringPartsSorter<T> s2) {
			int n = Math.min(parts.size(), s2.parts.size());
			for (int i = 0; i < n; i++) {
				var p1 = parts.get(i);
				var p2 = s2.parts.get(i);
				int comp = 0;
				if (p1 instanceof BigDecimal && p2 instanceof BigDecimal) {
					comp = ((BigDecimal)p1).compareTo((BigDecimal)p2);
				} else {
					comp = p1.toString().compareTo(p2.toString());					
				}
				if (comp != 0)
					return comp;
			}
			return Integer.compare(parts.size(), s2.parts.size());
		}
		
		@Override
		public String toString() {
			return "[" + parts.stream().map(p -> p.toString()).collect(Collectors.joining(", ")) + "]";
		}
		
	}
}