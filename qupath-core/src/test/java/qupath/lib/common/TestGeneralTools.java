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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.color.ColorDeconvolutionStains.DefaultColorDeconvolutionStains;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.classes.PathClassFactory.StandardPathClasses;

@SuppressWarnings("javadoc")
public class TestGeneralTools {
	
	private static Logger logger = LoggerFactory.getLogger(TestGeneralTools.class);
	private static Locale locale = Locale.US;
	
	@Test
	public void test_fileExtensions() {
		File currentDir = new File(".");
		File parentDir = new File(".");
		File noExt = new File("My file");
		
		assertNull(GeneralTools.getExtension(currentDir).orElse(null));
		assertNull(GeneralTools.getExtension(parentDir).orElse(null));
		assertNull(GeneralTools.getExtension(noExt).orElse(null));
		
		String baseName = "anything a all. here or there";
		for (String ext : Arrays.asList(".ext", ".tif", ".ome.tiff", ".tar.gz", ".ome.tif")) {
			File file = new File(baseName + ext);
			String parsed = GeneralTools.getExtension(file).orElse(null);
			assertEquals(ext, parsed);
			assertEquals(baseName, GeneralTools.getNameWithoutExtension(file));
			assertEquals(baseName, GeneralTools.getNameWithoutExtension(file.getPath()));
			
			File fileUpper = new File(baseName + ext.toUpperCase());
			String parsedUpper = GeneralTools.getExtension(fileUpper).orElse(null);
			assertEquals(ext, parsedUpper);
			assertEquals(baseName, GeneralTools.getNameWithoutExtension(fileUpper));
			assertEquals(baseName, GeneralTools.getNameWithoutExtension(fileUpper.getPath()));
		}
		
		for (String ext : Arrays.asList(".ext (here)", ".tif-not-valid", ".tif?")) {
			File file = new File(baseName + ext);
			String parsed = GeneralTools.getExtension(file).orElse(null);
			assertNull(parsed);
		}
		
		assertEquals(noExt.getPath(), GeneralTools.getNameWithoutExtension(noExt));
		assertEquals(noExt.getPath(), GeneralTools.getNameWithoutExtension(noExt.getPath()));
		
		assertTrue(GeneralTools.isMultipartExtension(".ome.tif"));
		assertTrue(GeneralTools.isMultipartExtension("ome.tif"));
		assertTrue(GeneralTools.isMultipartExtension("..ome.tif"));
		assertFalse(GeneralTools.isMultipartExtension("tif"));
		assertFalse(GeneralTools.isMultipartExtension(".tif"));
		assertFalse(GeneralTools.isMultipartExtension("..tif"));
		assertFalse(GeneralTools.isMultipartExtension("."));
		assertFalse(GeneralTools.isMultipartExtension("t"));
	}
	
	
	@Test
	public void test_filenameValid() {
		assertTrue(GeneralTools.isValidFilename("anything"));
		assertTrue(GeneralTools.isValidFilename("anything.else"));
		assertTrue(GeneralTools.isValidFilename(".anything.else"));
		assertTrue(GeneralTools.isValidFilename(".anytHIng.else"));
		
		assertFalse(GeneralTools.isValidFilename("anything.else?"));
		assertFalse(GeneralTools.isValidFilename("any<thing"));
		assertFalse(GeneralTools.isValidFilename("anyt>hing"));
		assertFalse(GeneralTools.isValidFilename("any:thing"));
		assertFalse(GeneralTools.isValidFilename("any/thing"));
		assertFalse(GeneralTools.isValidFilename("any\\thing"));
		assertFalse(GeneralTools.isValidFilename("any\nthing"));
		assertFalse(GeneralTools.isValidFilename("any\rthing"));
		assertFalse(GeneralTools.isValidFilename(""));
		assertFalse(GeneralTools.isValidFilename("  "));
		assertFalse(GeneralTools.isValidFilename(null));
	}
	
	
	@Test
	public void test_parseArgStringValues() {
		// Generate some Strings to parse
		ColorDeconvolutionStains stains = ColorDeconvolutionStains.makeDefaultColorDeconvolutionStains(DefaultColorDeconvolutionStains.H_E);
		String argsStains = ColorDeconvolutionStains.getColorDeconvolutionStainsAsString(stains, 3);
		String argsStains2 = "{\"Name\" : \"H-DAB default\", \"Stain 1\" : \"Hematoxylin\", \"Values 1\" : \"0.65111 0.70119 0.29049 \", \"Stain 2\" : \"DAB\", \"Values 2\" : \"0.26917 0.56824 0.77759 \", \"Background\" : \" 255 255 255 \"}";
		String argsDetection = "{\"detectionImageBrightfield\": \"Hematoxylin OD\",  \"requestedPixelSizeMicrons\": 0.5,  \"backgroundRadiusMicrons\": 8.0,  \"medianRadiusMicrons\": 0.0,  \"sigmaMicrons\": 1.5,  \"minAreaMicrons\": 10.0,  \"maxAreaMicrons\": 400.0,  \"threshold\": 0.1,  \"maxBackground\": 2.0,  \"watershedPostProcess\": true,  \"excludeDAB\": false,  \"cellExpansionMicrons\": 5.0,  \"includeNuclei\": true,  \"smoothBoundaries\": true,  \"makeMeasurements\": true}";
		
		// Compare the parsed maps
		Map<String, String> mapLegacy = parseArgStringValuesLegacy(argsStains);
		Map<String, String> mapCurrent = GeneralTools.parseArgStringValues(argsStains);
		assertEquals(mapLegacy, mapCurrent);
		assertFalse(mapCurrent.isEmpty());
		
		mapLegacy = parseArgStringValuesLegacy(argsStains2);
		mapCurrent = GeneralTools.parseArgStringValues(argsStains2);
		assertEquals(mapLegacy, mapCurrent);
		assertFalse(mapCurrent.isEmpty());
		
		mapLegacy = parseArgStringValuesLegacy(argsDetection);
		mapCurrent = GeneralTools.parseArgStringValues(argsDetection);
		assertEquals(mapLegacy, mapCurrent);
		assertFalse(mapCurrent.isEmpty());
		
		assertTrue(GeneralTools.parseArgStringValues(null).isEmpty());
		
		// Check that we can handle newlines (before <= 0.1.2 we couldn't...)
		String argsNewlines = argsDetection.replace(",", "," + System.lineSeparator());
		Map<String, String> mapCurrentNewlines = GeneralTools.parseArgStringValues(argsNewlines);
		assertEquals(mapCurrentNewlines, mapCurrent);
		
	}
	
	@Test
	public void test_blankString() {
		assertTrue(GeneralTools.blankString("    ", true));
		assertTrue(GeneralTools.blankString(null, true));
		assertTrue(GeneralTools.blankString(null, false));
		assertFalse(GeneralTools.blankString("    ", false));
		assertFalse(GeneralTools.blankString("  test  ", true));
		assertFalse(GeneralTools.blankString("  test  ", false));
	}
	
	@Test
	public void test_escapeFilePath() {
		assertEquals("path\\\\to\\\\file.ext", GeneralTools.escapeFilePath("path\\to\\file.ext"));
	}
	
	@Test
	public void test_clipValue() {
		assertEquals(100, GeneralTools.clipValue(100, 50, 150));
		assertEquals(100, GeneralTools.clipValue(100, 100, 101));
		assertEquals(100, GeneralTools.clipValue(100, 99, 100));
		assertEquals(200, GeneralTools.clipValue(100, 200, 300));
		assertEquals(200, GeneralTools.clipValue(100, 200, 0));
		assertEquals(0, GeneralTools.clipValue(300, 200, 0));
		assertEquals(50, GeneralTools.clipValue(100, 0, 50));
		
		assertEquals(100.0, GeneralTools.clipValue(100.0, 50.0, 150.0));
		assertEquals(100.0, GeneralTools.clipValue(100.0, 100.0, 101.0));
		assertEquals(100.0, GeneralTools.clipValue(100.0, 99.0, 100.0));
		assertEquals(200.0, GeneralTools.clipValue(100.0, 200.0, 300.0));
		assertEquals(200.0, GeneralTools.clipValue(100.0, 200.0, 0.0));
		assertEquals(0.0, GeneralTools.clipValue(300.0, 200.0, 0.0));
		assertEquals(50.0, GeneralTools.clipValue(100.0, 0.0, 50.0));
	}
	
	@Test
	public void test_almostTheSame() {
		assertTrue(GeneralTools.almostTheSame(0.1, 0.1, 0.1));

		assertTrue(GeneralTools.almostTheSame(0.1, 0.2, 1.0));
		assertTrue(GeneralTools.almostTheSame(0.2, 0.1, 1.0));
		
		assertFalse(GeneralTools.almostTheSame(0.1, 0.2, 0.1));
		assertFalse(GeneralTools.almostTheSame(0.2, 0.1, 0.1));

		assertTrue(GeneralTools.almostTheSame(1.0, 10.0, 2.0));
		assertTrue(GeneralTools.almostTheSame(10000.0, 10010.0, 2.0));
		
		assertTrue(GeneralTools.almostTheSame(1.0, 10.0, 2.0));
		assertTrue(GeneralTools.almostTheSame(-5.0, 4.0, 2.0));

		
		assertTrue(GeneralTools.almostTheSame(-0.1, 0.2, 3.0));
		assertTrue(GeneralTools.almostTheSame(0.2, -0.1, 3.0));

		assertTrue(GeneralTools.almostTheSame(0.1, -0.2, 3.0));
		assertTrue(GeneralTools.almostTheSame(-0.2, 0.1, 3.0));
	}
	
	@Test
	public void test_toURI() {
		try {
			assertEquals(URI.create("http://host.com/?query"), GeneralTools.toURI("http://host.com/?query"));
			assertEquals(URI.create("https://host.com/?query"), GeneralTools.toURI("https://host.com/?query"));
			assertEquals(new File("directory/file.txt").toURI(), GeneralTools.toURI("directory/file.txt"));
			assertEquals(URI.create("file://users/user/path/to/file.ext"), GeneralTools.toURI("file://users/user/path/to/file.ext"));
			assertEquals(URI.create(""), GeneralTools.toURI(""));
			assertEquals(URI.create(""), GeneralTools.toURI(null));
			
		} catch (URISyntaxException e) {
			throw new AssertionError();
		}
		
		Assertions.assertThrows(URISyntaxException.class, () -> GeneralTools.toURI("https://host.com/?query=this|that"));
	}
	
	@Test
	public void test_toEncodedURI() {
		try {
			assertEquals(URI.create("https://host.com"), GeneralTools.toEncodedURI("https://host.com"));
			assertEquals(URI.create("https://host.com/"), GeneralTools.toEncodedURI("https://host.com/"));
			assertEquals(URI.create("https://host.com/?querythisthat"), GeneralTools.toEncodedURI("https://host.com/?querythisthat"));
			assertEquals(URI.create("https://host.com/?query%3Dthis%7Cthat"), GeneralTools.toEncodedURI("https://host.com/?query=this|that"));
			assertEquals(URI.create("https://host.com/?query%3Dthis%7Cthat"), GeneralTools.toEncodedURI("https://host.com/?query=this|that#unexpected|fragment"));
			assertEquals(URI.create("http://host.com/?querythisthat"), GeneralTools.toEncodedURI("http://host.com/?querythisthat"));
			assertEquals(URI.create("http://host.com/?query%3Dthis%7Cthat"), GeneralTools.toEncodedURI("http://host.com/?query=this|that"));
			assertEquals(URI.create("http://host.com/?query%3Dthis%7Cthat"), GeneralTools.toEncodedURI("http://host.com/?query=this|that#unexpected|fragment"));
			assertEquals(URI.create("www.host.com"), GeneralTools.toEncodedURI("www.host.com"));
			assertEquals(URI.create(""), GeneralTools.toEncodedURI(null));
		} catch (Exception e) {
			throw new AssertionError();
		}
	}
	
	@Test
	public void test_toPath() {
		try {
			assertEquals(null, GeneralTools.toPath(new URI("https://host.com")));
			assertEquals(Path.of(new URI("file:/www.host.com/some/path")), GeneralTools.toPath(new URI("file:/www.host.com/some/path")));
			
			// At time or writing https://en.wikipedia.org/wiki/File_URI_scheme#How_many_slashes?
			// specifies that file:/ can have 1 or 3 slashes; 2 is often used but never correct
			assertEquals(Path.of(new URI("file:/users/user/path/to/file.ext")), GeneralTools.toPath(new URI("file:/users/user/path/to/file.ext")));
			assertEquals(Path.of(new URI("file:/users/user/path/to/file.ext")), GeneralTools.toPath(new URI("file:///users/user/path/to/file.ext")));
			assertEquals(Path.of(new URI("file:///users/user/path/to/file.ext")), GeneralTools.toPath(new URI("file:/users/user/path/to/file.ext")));
			assertEquals(Path.of(new URI("file:///users/user/path/to/file.ext")), GeneralTools.toPath(new URI("file:///users/user/path/to/file.ext")));
			
			// Fragment and query elements should be dropped
			assertEquals(Path.of(new URI("file:/users/user/path/to/file.ext")), GeneralTools.toPath(new URI("file:/users/user/path/to/file.ext#fragment")));
			assertEquals(Path.of(new URI("file:/users/user/path/to/file.ext")), GeneralTools.toPath(new URI("file:/users/user/path/to/file.ext/?query=test")));
		} catch (URISyntaxException e) {
			throw new AssertionError();
		}
	}
	
	@Test
	public void test_arrayToString() {
		// Doubles
		var dfs = new DecimalFormatSymbols(Locale.ENGLISH);
		assertEquals("", GeneralTools.arrayToString(locale, new double[0], ", ", 3));
		assertEquals("0, 0, 0, 0, 0", GeneralTools.arrayToString(locale, new double[5], ", ", 3));
		assertEquals("-1, 0.25, 0.5, 0.75, 1, " + dfs.getInfinity(), GeneralTools.arrayToString(locale, new double[] {-1.0, 0.25, 0.50, 0.75, 1.0, Double.POSITIVE_INFINITY}, ", ", 3));
		assertEquals("-1 0.25 0.5001 0.7501 1 " + dfs.getInfinity(), GeneralTools.arrayToString(locale, new double[] {-1.0, 0.25005, 0.5001, 0.75006, 1.0, Double.POSITIVE_INFINITY}, 4));
		
		// Objects (here PathClass)
		assertEquals("", GeneralTools.arrayToString(new PathClass[] {}, ","));
		assertEquals("Ignore*, Positive", GeneralTools.arrayToString(new Object[] {PathClassFactory.getPathClass(StandardPathClasses.IGNORE), PathClassFactory.getPathClass(StandardPathClasses.POSITIVE)}, ", "));
	}
	
	@Test
	public void test_splitLines() {
		String s = "First\nSecond" + System.lineSeparator() + "Third\r\nFourth\r";
		if (System.getProperty("os.name").toLowerCase().indexOf("win") >= 0)
			assertArrayEquals(new String[] {"First", "Second", "Third", "Fourth"}, GeneralTools.splitLines(s));
		// TODO: Add other OSs
	}
	
	@Test
	public void test_createFormatter() {
		// TODO: Either change the code or javadocs -> Locale.fr_BE does print 1,234
		// For now, the following lines are commented out as they will fail if ran with Locale above.
//		var formatter = GeneralTools.createFormatter(5);
//		assertEquals("5.00001", formatter.format(5.000006));
//		assertEquals("-5.00001", formatter.format(-5.00006));
//		assertEquals("5", formatter.format(5.0000005));
//		assertEquals("-5", formatter.format(-5.0000005));
//		assertEquals("1000000", formatter.format(1000000));
//		assertEquals("-1000000", formatter.format(-1000000));
	}
	
	@Test
	public void test_formatNumber() {
		NumberFormat nf = NumberFormat.getInstance();
		nf.setGroupingUsed(false);
		nf.setMaximumFractionDigits(5);
		assertEquals(nf.format(5.00001), GeneralTools.formatNumber(5.000006, 5));
		assertEquals(nf.format(-5.00006), GeneralTools.formatNumber(-5.00006, 5));
		assertEquals(nf.format(5.0000005), GeneralTools.formatNumber(5.0000005, 5));
		assertEquals(nf.format(-5.0000005), GeneralTools.formatNumber(-5.0000005, 5));
		assertEquals(nf.format(1000000), GeneralTools.formatNumber(1000000, 5));
		assertEquals(nf.format(-1000000), GeneralTools.formatNumber(-1000000, 5));
		assertEquals(nf.format(-1000000), GeneralTools.formatNumber(-1000000, -5));
	}
	
	@Test
	public void test_readInputStreamAsString() {
		byte[] randomString = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
		ByteArrayOutputStream baos1 = new ByteArrayOutputStream();
		ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
	    try {
	    	// Write to stream
			baos1.write(randomString);
			
			// Read from stream
			var back = GeneralTools.readInputStreamAsString(new ByteArrayInputStream(baos1.toByteArray()));
			assertArrayEquals(randomString, back.getBytes());

			// Read from empty stream
			assertEquals("", GeneralTools.readInputStreamAsString(new ByteArrayInputStream(baos2.toByteArray())));
		} catch (IOException ex) {
			throw new AssertionError();
		}
	}
	
	@Test
	public void test_checkExtensions() {
		String txtPath = "C:/Users/user/path/to/file.txt";
		assertTrue(GeneralTools.checkExtensions(txtPath, "txt"));
		assertTrue(GeneralTools.checkExtensions(txtPath, ".txt"));
		assertTrue(GeneralTools.checkExtensions(txtPath, "TXT"));
		assertTrue(GeneralTools.checkExtensions(txtPath, ".TXT"));
		assertTrue(GeneralTools.checkExtensions(txtPath, ".png", ".TXT", "PDF"));
		
		assertFalse(GeneralTools.checkExtensions(txtPath, ""));
		assertFalse(GeneralTools.checkExtensions(txtPath, ".png"));
		assertFalse(GeneralTools.checkExtensions(txtPath, "png"));
		assertFalse(GeneralTools.checkExtensions(txtPath, ".PNG"));
		assertFalse(GeneralTools.checkExtensions(txtPath, "PNG"));

		assertFalse(GeneralTools.checkExtensions("C:/Users/user/path/to/file", "PNG"));
		assertFalse(GeneralTools.checkExtensions("C:/Users/user/path/to/file", ""));
	}
	
	@Test
	public void test_numNaNs() {
		double[] array = new double[] {
				0.0, 
				Double.NaN, 
				5.0, 
				Double.NaN, 
				Double.NaN,
				Double.NaN,
				6.0,
				-0.2,
				Double.NaN,
				Double.NaN
				};
		
		assertEquals(6, GeneralTools.numNaNs(array));
		assertEquals(0, GeneralTools.numNaNs(new double[0]));
	}
	
	@Test
	public void test_sum() {
		long[] array = new long[] {
				0L, 
				(long) Double.NaN, 
				5L, 
				(long) Double.NaN, 
				(long) Double.NaN,
				(long) Double.NaN,
				-6L,
				2L,
				(long) Double.NaN,
				(long) Double.NaN
				};
		
		assertEquals(1, GeneralTools.sum(array));
		assertEquals(0, GeneralTools.sum(new long[0]));
	}
	
	@Test
	public void test_generateDistinctName() {
		List<String> existingNames = Arrays.asList("Alpha", "alpha", "Alpha", "Alpha (1)", "Alpha (2)", "Alpha ()", "beta", "", "(1)");
		assertEquals("alpha (1)", GeneralTools.generateDistinctName("alpha", existingNames));
		assertEquals("Alpha (3)", GeneralTools.generateDistinctName("Alpha", existingNames));
		assertEquals("Alpha (3)", GeneralTools.generateDistinctName("Alpha (1)", existingNames));
		assertEquals("Alpha (3)", GeneralTools.generateDistinctName("Alpha (2)", existingNames));
		assertEquals("Alpha () (1)", GeneralTools.generateDistinctName("Alpha ()", existingNames));
		assertEquals("Alpha beta", GeneralTools.generateDistinctName("Alpha beta", existingNames));
		assertEquals("(2)", GeneralTools.generateDistinctName("", existingNames));
		assertEquals(null, GeneralTools.generateDistinctName(null, existingNames));
	}
	
	/**
	 * The legacy method previously used for parsing in QuPath <= 0.1.2
	 * Retained here to check for backwards compatibility.
	 * It wasn't especially robust, and would fail on newlines.
	 * 
	 * @param s
	 * @return
	 */
	private static Map<String, String> parseArgStringValuesLegacy(String s) {
		if (s == null)
			return Collections.emptyMap();

		s = s.trim();
		if (!s.startsWith("{")) { // || !s.startsWith("{")) { // Don't know what I was planning here...?
			throw new IllegalArgumentException(s + " is not a valid parameter string!");
		}

		// Minimum length of 7, for {"a":1}
		if (s.replace(" ",  "").length() < 7 || !s.contains("\"") || !s.contains(":"))
			return Collections.emptyMap();

		//		logger.info(s);
		//		s = s.substring(s.indexOf("\""), s.lastIndexOf("\"") + 1);

		Map<String, String> map = new LinkedHashMap<>();


		String quotedString = "\"([^\"]*)\"[\\s]*";
		// Match quoted strings, followed by : (potential keys)
		Pattern p = Pattern.compile(quotedString + ":");
		// Match everything up to the next potential key, or to the end brace }
		Pattern p2 = Pattern.compile(".+?(?=("+quotedString+"[:])|})");

		Scanner scanner = new Scanner(s);
		scanner.useDelimiter("");
		String key;
		while ((key = scanner.findInLine(p)) != null) {

			// Trim the key
			key = key.substring(key.indexOf("\"")+1, key.lastIndexOf("\""));

			String value = scanner.findInLine(p2);
			if (value == null) {
				logger.error("Unable to associated value with key {}", key);
				continue;
			}

			// Trim any whitespace, commas or quotation marks
			value = value.trim();
			if (value.endsWith(","))
				value = value.substring(0, value.length()-1);
			if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
				value = value.substring(value.indexOf("\"")+1, value.lastIndexOf("\""));
			}

			if (value.length() == 0) {
				logger.warn("Unable to associate value with key {}", key);
				continue;
			}

			// Decide what we have & put into the map
			map.put(key, value);
		}
		scanner.close();

		return map;
	}
}