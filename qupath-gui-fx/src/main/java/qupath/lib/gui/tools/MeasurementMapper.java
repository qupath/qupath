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

package qupath.lib.gui.tools;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.ColorTools;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathTileObject;

/**
 * Helpers class that can be used to map an object's measurement to a color (packed RGB int).
 * <p>
 * By passing a collection of objects, the minimum and maximum of all the measurements are found
 * and these used to determine the lookup table scaling; alternative minimum and maximum values can also
 * be set to override these extrema.
 * 
 * @author Pete Bankhead
 *
 */
public class MeasurementMapper {
	
	final private static Logger logger = LoggerFactory.getLogger(MeasurementMapper.class);
	
	private ColorMapper colorMapper;

	// Data min & max values
	private double minValueData = 0;
	private double maxValueData = 1;

	// Display min & max values
	private double minValue = 0;
	private double maxValue = 1;

	private String measurement;
	private boolean isClassProbability = false;
	private boolean valid = false;
	private boolean excludeOutsideRange;

	/**
	 * Constructor.
	 * @param mapper color mapper (lookup table)
	 * @param measurement the measurement to colorize
	 * @param pathObjects an initial collection of objects used to determine display ranges (i.e. find the min/max values of the specified measurement)
	 */
	public MeasurementMapper(ColorMapper mapper, String measurement, Collection<? extends PathObject> pathObjects) {
		this.colorMapper = mapper;
		this.measurement = measurement;
		isClassProbability = measurement.toLowerCase().trim().equals("class probability");
		
		// Initialize max & min values
		minValueData = Double.POSITIVE_INFINITY;
		maxValueData = Double.NEGATIVE_INFINITY;
		for (PathObject pathObject : pathObjects) {
			double value = getUsefulValue(pathObject, Double.NaN);
			if (Double.isNaN(value) || Double.isInfinite(value))
				continue;
			if (value > maxValueData)
				maxValueData = value;
			if (value < minValueData)
				minValueData = value;
			valid = true;
		}
		// Set display range to match the data
		minValue = minValueData;
		maxValue = maxValueData;
		logger.debug("Measurement mapper limits for " + measurement + ": " + minValueData + ", " + maxValueData);
	}
	
	private static List<ColorMapper> DEFAULT_COLOR_MAPS;
	private static ColorMapper LEGACY_COLOR_MAP = new PseudoColorMapper();
	
	private synchronized static List<ColorMapper> loadDefaultColorMaps() throws URISyntaxException, IOException {
		if (DEFAULT_COLOR_MAPS == null) {
			Path pathColorMaps;
			URI uri = MeasurementMapper.class.getResource("/colormaps").toURI();
		    if (uri.getScheme().equals("jar")) {
		        FileSystem fileSystem = FileSystems.newFileSystem(uri, Map.of());
		        pathColorMaps = fileSystem.getPath("/colormaps");
		    } else {
		    	pathColorMaps = Paths.get(uri);
		    }
		    DEFAULT_COLOR_MAPS = loadColorMapsFromDirectory(pathColorMaps);
		}
		return DEFAULT_COLOR_MAPS == null ? Collections.emptyList() : DEFAULT_COLOR_MAPS;
	}
	
	/**
	 * Load the available ColorMappers.
	 * @return
	 */
	public static List<ColorMapper> loadColorMappers() {
		List<ColorMapper> colorMappers = new ArrayList<>();
		// Load the default color maps
		try {
			colorMappers.addAll(loadDefaultColorMaps());
		} catch (Exception e) {
			logger.error("Error loading default color maps", e);
		}
		
		// Try adding user color maps, if we have any
		try {
	        // See if we have some custom colormaps installed by the user
	        String userPath = PathPrefs.getUserPath();
	        if (userPath != null) {
	        	Path dirUser = Paths.get(userPath, "colormaps");
		        if (Files.isDirectory(dirUser)) {
		        	colorMappers.addAll(loadColorMapsFromDirectory(dirUser));
		        }
	        }
		} catch (Exception e) {
			logger.error("Error loading custom color maps", e);
		}
		
		// Make sure we have at least the legacy map
		if (!colorMappers.contains(LEGACY_COLOR_MAP))
			colorMappers.add(LEGACY_COLOR_MAP);
		return colorMappers;
	}
	
	private static List<ColorMapper> loadColorMapsFromDirectory(Path path) throws IOException {
		List<ColorMapper> list = new ArrayList<>();
		try (var stream = Files.list(path)) {
			Iterator<Path> iter = stream.filter(p -> p.getFileName().toString().endsWith(".tsv")).iterator();
		    while (iter.hasNext()) {
	    		var temp = iter.next();
		    	try {
		    		list.add(loadColorMap(temp));
		    	} catch (Exception e) {
		    		logger.error("Error loading color map from {}", temp);
		    	}
		    }
		    return list;
		}
	}
	
	private static ColorMapper loadColorMap(Path path) throws IOException {
		// Parse a name
		String name = path.getFileName().toString();
    	if (name.endsWith(".tsv"))
    		name = name.substring(0, name.length()-4);
    	
		// Read non-blank lines
		List<String> lines = Files.readAllLines(path).stream().filter(s -> !s.isBlank()).collect(Collectors.toList());
		
        // Parse values
		int n = lines.size();
    	double[] r = new double[n];
    	double[] g = new double[n];
    	double[] b = new double[n];
    	int i = 0;
    	for (String line : lines) {
    		String[] split = line.split("\\s+");
    		if (split.length == 0)
    			continue;
    		if (split.length < 3) {
    			logger.warn("Invalid line (must contain 3 doubles): {}", line);
    			continue;
    		}
    		r[i] = Double.parseDouble(split[0]);
    		g[i] = Double.parseDouble(split[1]);
    		b[i] = Double.parseDouble(split[2]);
    		i++;
    	}
    	if (i < n) {
    		r = Arrays.copyOf(r, i);
    		g = Arrays.copyOf(g, i);
    		b = Arrays.copyOf(b, i);
    	}
    	return MeasurementMapper.createColorMapper(name, r, g, b);
	}
	
	
	/**
	 * Set a new color mapper.
	 * @param mapper
	 */
	public void setColorMapper(ColorMapper mapper) {
		this.colorMapper = mapper;
	}

	/**
	 * Returns true if objects with values outside the specified min/max range have the min/max colors returned, false if null should be returned instead.
	 * @return
	 */
	public boolean getExcludeOutsideRange() {
		return excludeOutsideRange;
	}

	/**
	 * Specify whether out-of-range values should be excluded.
	 * @param excludeOutsideRange 
	 */
	public void setExcludeOutsideRange(boolean excludeOutsideRange) {
		this.excludeOutsideRange = excludeOutsideRange;
	}

	/**
	 * Query if the mapper is valid. This returns true if the mapper has been initialized with objects to 
	 * determine an appropriate display range.
	 * @return
	 */
	public boolean isValid() {
		return valid;
	}

	/**
	 * Get the display color for a specified object, according to the settings of this mapper.
	 * @param pathObject
	 * @return
	 */
	public Integer getColorForObject(PathObject pathObject) {

//		if (!(colorMapper instanceof RedAlphaColorMapper6))
//			colorMapper = new RedAlphaColorMapper6();

		//		if (!pathObject.isDetection())
		if (!(pathObject instanceof PathDetectionObject || pathObject instanceof PathTileObject))
			return ColorToolsFX.getDisplayedColorARGB(pathObject);

		// Replace NaNs with the minimum value
		double value = getUsefulValue(pathObject, Double.NaN);

		if (excludeOutsideRange && (value < minValue || value > maxValue))
			return null;

		if (Double.isNaN(value))
			return null;
		//		if (Double.isNaN(value))
		//			value = minValue;

		// Map value to color
		return colorMapper.getColor(value, minValue, maxValue);
	}


	protected double getUsefulValue(PathObject pathObject, double nanValue) {
		double value;
		if (isClassProbability)
			value = pathObject.getClassProbability();
		else
			value = pathObject.getMeasurementList().getMeasurementValue(measurement);
		// Convert NaN to zero
		if (Double.isNaN(value))
			value = nanValue;
		// Convert infinities to the data min/max
		else if (Double.isInfinite(value)) {
			if (value > 0)
				return maxValueData;
			else
				return minValueData;
		}
		return value;
	}

	/**
	 * Get the color mapper, which is effectively a lookup table.
	 * @return
	 */
	public ColorMapper getColorMapper() {
		return colorMapper;
	}

	/**
	 * Get the minimum measurement value from the objects passed to the constructor of this mapper.
	 * @return
	 */
	public double getDataMinValue() {
		return minValueData;
	}

	/**
	 * Get the maximum measurement value from the objects passed to the constructor of this mapper.
	 * @return
	 */
	public double getDataMaxValue() {
		return maxValueData;
	}

	/**
	 * Set the measurement value that maps to the first color in the color mapper.
	 * @param minValue
	 */
	public void setDisplayMinValue(double minValue) {
		this.minValue = minValue;
	}

	/**
	 * Set the measurement value that maps to the last color in the color mapper.
	 * @param maxValue
	 */
	public void setDisplayMaxValue(double maxValue) {
		this.maxValue = maxValue;
	}

	/**
	 * Get the measurement value that maps to the first color in the color mapper.
	 * @return
	 */
	public double getDisplayMinValue() {
		return minValue;
	}

	/**
	 * Get the measurement value that maps to the last color in the color mapper.
	 * @return
	 */
	public double getDisplayMaxValue() {
		return maxValue;
	}


	/**
	 * Color mapper, which acts as the lookup table for a {@link MeasurementMapper}.
	 */
	public static interface ColorMapper {
		
		/**
		 * Get the name of the color mapper.
		 * @return
		 */
		public String getName();

		/**
		 * Returns true if the mapper uses alpha values within its colors.
		 * @return
		 */
		public boolean hasAlpha();

		/**
		 * Get a packed ARGB representation of the (interpolated) color at the specified value,.
		 * @param value value that should be colorized
		 * @param minValue minimum display value, corresponding to the first color in the lookup table of this mapper
		 * @param maxValue maximum display value, corresponding to the first last in the lookup table of this mapper
		 * @return
		 */
		public Integer getColor(double value, double minValue, double maxValue);

	}
	
	static ColorMapper createColorMapper(String name, double[] r, double[] g, double[] b) {
		int[] ri = convertToInt(r);
		int[] gi = convertToInt(g);
		int[] bi = convertToInt(b);
		return createColorMapper(name, ri, gi, bi);
	}
	
	static int[] convertToInt(double[] arr) {
		int[] arr2 = new int[arr.length];
		for (int i = 0; i < arr.length; i++) {
			arr2[i] = (int)Math.round(arr[i] * 255.0);
		}
		return arr2;
	}

	static ColorMapper createColorMapper(String name, int[] r, int[] g, int[] b) {
		return new DefaultColorMapper(name, r, g, b);
	}

	static class DefaultColorMapper implements ColorMapper {
		
		private String name;
		
		private final int[] r;
		private final int[] g;
		private final int[] b;
		private int nColors = 256;
		private Integer[] colors = new Integer[nColors];
		
		DefaultColorMapper(String name, int[] r, int[] g, int[] b) {
			this.name = name;
			this.r = r.clone();
			this.g = g.clone();
			this.b = b.clone();
			double scale = (double)(r.length - 1) / nColors;
			for (int i = 0; i < nColors; i++) {
				int ind = (int)(i * scale);
				double residual = (i * scale) - ind;
				colors[i] = ColorTools.packRGB(
						r[ind] + (int)((r[ind+1] - r[ind]) * residual),
						g[ind] + (int)((g[ind+1] - g[ind]) * residual),
						b[ind] + (int)((b[ind+1] - b[ind]) * residual));
			}
			colors[nColors-1] = ColorTools.packRGB(r[r.length-1], g[g.length-1], b[b.length-1]);
		}
		
		@Override
		public String getName() {
			return name;
		}
		
		@Override
		public String toString() {
			return getName();
		}

		public Integer getColor(int ind) {
			Integer color = colors[ind];
			if (color == null) {
				color = ColorTools.packRGB(r[ind], g[ind], b[ind]);
				colors[ind] = color;
			}
			return color;
		}

		@Override
		public Integer getColor(double value, double minValue, double maxValue) {
			//			System.out.println("Measurement mapper: " + minValue + ", " + maxValue);
			int ind = 0;
			if (maxValue > minValue) {
				ind = (int)Math.round((value - minValue) / (maxValue - minValue) * nColors);
				ind = ind >= nColors ? nColors - 1 : ind;
				ind = ind < 0 ? 0 : ind;
			} else if (minValue > maxValue) {
				ind = (int)Math.round((value - maxValue) / (minValue - maxValue) * nColors);
				ind = ind >= nColors ? nColors - 1 : ind;
				ind = ind < 0 ? 0 : ind;
				ind = nColors - 1 - ind;
			}
			return getColor(ind);
		}


		@Override
		public boolean hasAlpha() {
			return false;
		}

	}

	/**
	 * The previous default color mapper (v0.1.2 and earlier).
	 */
	static class PseudoColorMapper implements ColorMapper {

		private static final int[] r = {0, 0,   0,   0,   255, 255};
		private static final int[] g = {0, 0,   255, 255, 255, 0};
		private static final int[] b = {0, 255, 255, 0,   0,   0};
		private static int nColors = 256;
		private static Integer[] colors = new Integer[nColors];

		static {
			double scale = (double)(r.length - 1) / nColors;
			for (int i = 0; i < nColors; i++) {
				int ind = (int)(i * scale);
				double residual = (i * scale) - ind;
				colors[i] = ColorTools.packRGB(
						r[ind] + (int)((r[ind+1] - r[ind]) * residual),
						g[ind] + (int)((g[ind+1] - g[ind]) * residual),
						b[ind] + (int)((b[ind+1] - b[ind]) * residual));
			}
			colors[nColors-1] = ColorTools.packRGB(r[r.length-1], g[g.length-1], b[b.length-1]);
		}
		
		@Override
		public String toString() {
			return getName() + " (legacy)";
		}

		@Override
		public String getName() {
			return "Jet";
		}

		public Integer getColor(int ind) {
			Integer color = colors[ind];
			if (color == null) {
				color = ColorTools.packRGB(r[ind], g[ind], b[ind]);
				colors[ind] = color;
			}
			return color;
		}

		@Override
		public Integer getColor(double value, double minValue, double maxValue) {
			//			System.out.println("Measurement mapper: " + minValue + ", " + maxValue);
			int ind = 0;
			if (maxValue > minValue) {
				ind = (int)((value - minValue) / (maxValue - minValue) * nColors + .5);
				ind = ind >= nColors ? nColors - 1 : ind;
				ind = ind < 0 ? 0 : ind;
			}
			return getColor(ind);
		}


		@Override
		public boolean hasAlpha() {
			return false;
		}

	}


}
