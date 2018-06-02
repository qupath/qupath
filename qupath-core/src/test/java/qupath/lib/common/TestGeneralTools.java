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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Pattern;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.color.ColorDeconvolutionStains.DEFAULT_CD_STAINS;

public class TestGeneralTools {
	
	private static Logger logger = LoggerFactory.getLogger(TestGeneralTools.class);
	
	@Test
	public void test_parseArgStringValues() {
		// Generate some Strings to parse
		ColorDeconvolutionStains stains = ColorDeconvolutionStains.makeDefaultColorDeconvolutionStains(DEFAULT_CD_STAINS.H_E);
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
		
		// Check that we can handle newlines (before <= 0.1.2 we couldn't...)
		String argsNewlines = argsDetection.replace(",", "," + System.lineSeparator());
		Map<String, String> mapCurrentNewlines = GeneralTools.parseArgStringValues(argsNewlines);
		assertEquals(mapCurrentNewlines, mapCurrent);
		
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
