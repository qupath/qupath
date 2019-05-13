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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
	
	
	private final static String LATEST_VERSION = getLatestVerion();
	
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
	private static String getLatestVerion() {
		String version = GeneralTools.class.getPackage().getImplementationVersion();
		if (version == null) {
			var path = Paths.get("VERSION");
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
	 * The absolute difference is divided by the first of the numbers before the tolerance is checked.
	 * 
	 * @param n1
	 * @param n2
	 * @param tolerance
	 * @return
	 */
	public static boolean almostTheSame(double n1, double n2, double tolerance) {
		return Math.abs(n1 - n2)/n1 < tolerance;
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
//		switch (nDecimalPlaces) {
//		case 0: return new DecimalFormat("#."); // TODO: Check if this is correct!
//		case 1: return new DecimalFormat("#.#");
//		case 2: return new DecimalFormat("#.##");
//		case 3: return new DecimalFormat("#.###");
//		case 4: return new DecimalFormat("#.####");
//		case 5: return new DecimalFormat("#.#####");
//		case 6: return new DecimalFormat("#.######");
//		case 7: return new DecimalFormat("#.#######");
//		case 8: return new DecimalFormat("#.########");
//		case 9: return new DecimalFormat("#.#########");
//		default:
//			StringBuilder sb = new StringBuilder();
//			sb.append("#.");
//			for (int i = 0; i < nDecimalPlaces; i++)
//				sb.append("#");
//			return new DecimalFormat(sb.toString());
//		}
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
		return '\u00B5' + "m";
	}
	
	
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

}
