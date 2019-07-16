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

package qupath.lib.gui.helpers;

import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.ColorTools;
import qupath.lib.gui.objects.helpers.PathObjectColorToolsAwt;
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
	
	private static Map<String, ColorMapper> defaultMappers = new TreeMap<>();
	
	static {
		
		try {
			Path pathColorMaps;
			URI uri = MeasurementMapper.class.getResource("/colormaps").toURI();
	        if (uri.getScheme().equals("jar")) {
	            FileSystem fileSystem = FileSystems.newFileSystem(uri, Collections.emptyMap());
	            pathColorMaps = fileSystem.getPath("/resources");
	        } else {
	        	pathColorMaps = Paths.get(uri);
	        }
	        List<Path> maps = Files.list(pathColorMaps).filter(p -> p.getFileName().toString().endsWith(".tsv")).collect(Collectors.toList());
	        for (Path map : maps) {
	        	String name = map.getFileName().toString();
	        	if (name.endsWith(".tsv"))
	        		name = name.substring(0, name.length()-4);
	        	List<String> lines = Files.readAllLines(map).stream().filter(s -> !s.isBlank()).collect(Collectors.toList());
	        	int n = lines.size();
	        	double[] r = new double[n];
	        	double[] g = new double[n];
	        	double[] b = new double[n];
	        	int i = 0;
	        	for (String line : lines) {
	        		String[] split = line.split("\t");
	        		r[i] = Double.parseDouble(split[0]);
	        		g[i] = Double.parseDouble(split[1]);
	        		b[i] = Double.parseDouble(split[2]);
	        		i++;
	        	}
	        	defaultMappers.put(name, createColorMapper(r, g, b));
	        }
		} catch (Exception e) {
			logger.error("Unable to load color maps", e);
		}
		
	}

	private ColorMapper colorMapper = defaultMappers.getOrDefault("viridis", new PseudoColorMapper());

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

	public MeasurementMapper(String measurement, Collection<PathObject> pathObjects) {
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
		logger.info("Measurement mapper limits for " + measurement + ": " + minValueData + ", " + maxValueData);
	}
	
	/**
	 * Get the available color mappers (lookup tables).
	 * @return
	 */
	public static Map<String, ColorMapper> getAvailableColorMappers() {
		return Collections.unmodifiableMap(defaultMappers);
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
	 * Returns true if objects with values outside the specified min/max range have the min/max colors returned, false if null should be returned instead.
	 */
	public void setExcludeOutsideRange(boolean excludeOutsideRange) {
		this.excludeOutsideRange = excludeOutsideRange;
	}

	public boolean isValid() {
		return valid;
	}

	public Integer getColorForObject(PathObject pathObject) {

//		if (!(colorMapper instanceof RedAlphaColorMapper6))
//			colorMapper = new RedAlphaColorMapper6();

		//		if (!pathObject.isDetection())
		if (!(pathObject instanceof PathDetectionObject || pathObject instanceof PathTileObject))
			return PathObjectColorToolsAwt.getDisplayedColor(pathObject);

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

	public ColorMapper getColorMapper() {
		return colorMapper;
	}

	public double getDataMinValue() {
		return minValueData;
	}

	public double getDataMaxValue() {
		return maxValueData;
	}

	public void setDisplayMinValue(double minValue) {
		this.minValue = minValue;
	}

	public void setDisplayMaxValue(double maxValue) {
		this.maxValue = maxValue;
	}

	public double getDisplayMinValue() {
		return minValue;
	}

	public double getDisplayMaxValue() {
		return maxValue;
	}







	public static interface ColorMapper {

		public boolean hasAlpha();

		public Integer getColor(int ind);

		public Integer getColor(double value, double minValue, double maxValue);

	}
	
	static ColorMapper createColorMapper(double[] r, double[] g, double[] b) {
		int[] ri = convertToInt(r);
		int[] gi = convertToInt(g);
		int[] bi = convertToInt(b);
		return createColorMapper(ri, gi, bi);
	}
	
	static int[] convertToInt(double[] arr) {
		int[] arr2 = new int[arr.length];
		for (int i = 0; i < arr.length; i++) {
			arr2[i] = (int)Math.round(arr[i] * 255.0);
		}
		return arr2;
	}

	static ColorMapper createColorMapper(int[] r, int[] g, int[] b) {
		return new DefaultColorMapper(r, g, b);
	}

	static class DefaultColorMapper implements ColorMapper {

		private final int[] r;
		private final int[] g;
		private final int[] b;
		private int nColors = 256;
		private Integer[] colors = new Integer[nColors];
		
		DefaultColorMapper(int[] r, int[] g, int[] b) {
			this.r = r.clone();
			this.g = g.clone();
			this.b = b.clone();
			double scale = (double)(r.length - 1) / nColors;
			for (int i = 0; i < nColors; i++) {
				int ind = (int)(i * scale);
				double residual = (i * scale) - ind;
				colors[i] = ColorTools.makeRGB(
						r[ind] + (int)((r[ind+1] - r[ind]) * residual),
						g[ind] + (int)((g[ind+1] - g[ind]) * residual),
						b[ind] + (int)((b[ind+1] - b[ind]) * residual));
			}
			colors[nColors-1] = ColorTools.makeRGB(r[r.length-1], g[g.length-1], b[b.length-1]);
		}

		@Override
		public Integer getColor(int ind) {
			Integer color = colors[ind];
			if (color == null) {
				color = ColorTools.makeRGB(r[ind], g[ind], b[ind]);
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
				colors[i] = ColorTools.makeRGB(
						r[ind] + (int)((r[ind+1] - r[ind]) * residual),
						g[ind] + (int)((g[ind+1] - g[ind]) * residual),
						b[ind] + (int)((b[ind+1] - b[ind]) * residual));
			}
			colors[nColors-1] = ColorTools.makeRGB(r[r.length-1], g[g.length-1], b[b.length-1]);
		}


		@Override
		public Integer getColor(int ind) {
			Integer color = colors[ind];
			if (color == null) {
				color = ColorTools.makeRGB(r[ind], g[ind], b[ind]);
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
