/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.lib.common;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;
import java.util.Locale.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * Collection of generally useful static methods.
 * 
 * @author Pete Bankhead
 *
 */
public class GeneralTools {
	
	final private static Logger logger = LoggerFactory.getLogger(GeneralTools.class);
	
	
	private final static String LATEST_VERSION = getLatestVersion();
	
	/**
	 * Request the version of QuPath.
	 * 
	 * @return
	 */
	public static String getVersion() {
		return LATEST_VERSION;
	}
	
	
	/**
	 * Try to determine latest QuPath version, first from the manifest and then from the source 
	 * (useful if running from an IDE, for example).
	 * 
	 * @return
	 */
	private static String getLatestVersion() {
		String version = GeneralTools.class.getPackage().getImplementationVersion();
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
		return version;
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
		if (ext.length() > 1 && ext.startsWith("."))
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
		return s == null || s.trim().length() == 0;
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
	 * Returns true if the numbers are equal, or the absolute difference divided by the average is less than the specified tolerance.
	 * <p>
	 * Note that this calculation changed in v0.2.0-m4. The previous behavior divided the absolute difference by the first value only, 
	 * which is not robust to differences in the input order or if the first value is negative.
	 * 
	 * @param n1
	 * @param n2
	 * @param tolerance
	 * @return
	 */
	public static boolean almostTheSame(double n1, double n2, double tolerance) {
		if (n1 == n2)
			return true;
		double difference = n1 - n2;
		double average = (n1/2 + n2/2);
		return Math.abs(difference / average) < tolerance;
	}
	
	
	/**
	 * Try to convert a path to a URI.
	 * <p>
	 * This currently does a very simple check for http:/https:/file: at the beginning to see if it 
	 * can construct the URI directly; if not, it assumes the path refers to a local file (as it 
	 * generally did in QuPath 0.1.2 and earlier).
	 * 
	 * @param path
	 * @return
	 * @throws URISyntaxException 
	 */
	public static URI toURI(String path) throws URISyntaxException {
		if (path.startsWith("http:") || path.startsWith("https:") || path.startsWith("file:"))
			return new URI(path);
		return new File(path).toURI();
	}
	
	
	/**
	 * Try to identify a Path from a URI, dropping any query or fragment elements.
	 * <p>
	 * This returns the Path if successful and null otherwise. There is no check whether the Path exists.
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
		} catch (URISyntaxException e) {
			logger.warn("Problem parsing file from URI", e);
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
			if (i < array.length)
				sb.append(" ");
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
	 * Convert a String array to a single string, with a specified separator string.
	 * @param array
	 * @param separator
	 * @return
	 */
	public static String arrayToString(final Object[] array, final String separator) {
		StringBuilder sb = new StringBuilder();
		if (array.length == 0)
			return "";
		for (int i = 0; i < array.length; i++) {
			sb.append(array[i]);
			if (i < array.length - 1)
				sb.append(separator);
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
	 * 
	 * @param path
	 * @return
	 * @throws IOException
	 */
	public static String readFileAsString(final String path) throws IOException {
		Scanner scanner = new Scanner(new File(path));
		String contents = scanner.useDelimiter("\\Z").next();
		scanner.close();
		return contents;
	}
	
	
	/**
	 * Read the entire contents of an InputStream into a single String.
	 * 
	 * @param stream
	 * @return
	 * @throws IOException
	 */
	public static String readInputStreamAsString(final InputStream stream) throws IOException {
		Scanner scanner = new Scanner(stream);
		String contents = scanner.useDelimiter("\\Z").next();
		scanner.close();
		return contents;
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
			if (pathLower.endsWith(ext))
				return true;
		}
		return false;
	}

	/**
	 * Returns true if running on macOS.
	 * @return
	 */
	public static boolean isMac() {
		String os = System.getProperty("os.name").toLowerCase();
		return os.indexOf("mac") >= 0 || os.indexOf("darwin") >= 0;
	}

	/**
	 * Returns true if running on Linux.
	 * @return
	 */
	public static boolean isLinux() {
		String os = System.getProperty("os.name").toLowerCase();
		return os.indexOf("nux") >= 0;
	}

	/**
	 * Returnst true if running on Windows.
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

}
