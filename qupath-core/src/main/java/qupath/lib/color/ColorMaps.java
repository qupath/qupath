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


package qupath.lib.color;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.ColorTools;

/**
 * Helper class to manage colormaps, which are rather like lookup tables but easily support interpolation.
 * 
 * @author Pete Bankhead
 */
public class ColorMaps {
	
	private final static Logger logger = LoggerFactory.getLogger(ColorMaps.class);
	
	private static ColorMap LEGACY_COLOR_MAP = new PseudoColorMap();
	private static Map<String, ColorMap> maps = new TreeMap<>(loadDefaultColorMaps());
	private static Map<String, ColorMap> mapsUnmodifiable = Collections.unmodifiableMap(maps);
	
	/**
	 * colormap, which acts as an interpolating lookup table with an arbitrary range.
	 */
	public interface ColorMap {
		
		/**
		 * Get the name of the colormap.
		 * @return
		 */
		public String getName();

//		/**
//		 * Returns true if the map uses alpha values within its colors.
//		 * @return
//		 */
//		public boolean hasAlpha();

		/**
		 * Get a packed ARGB representation of the (interpolated) color at the specified value,.
		 * @param value value that should be colorized
		 * @param minValue minimum display value, corresponding to the first color in the lookup table of this map
		 * @param maxValue maximum display value, corresponding to the first last in the lookup table of this map
		 * @return
		 */
		public Integer getColor(double value, double minValue, double maxValue);

	}
	
	/**
	 * Load the default colormaps (from jar)
	 * @return
	 */
	private static Map<String, ColorMap> loadDefaultColorMaps() {
		try {
			Path pathColorMaps;
			URI uri = ColorMaps.class.getResource("/colormaps").toURI();
		    if (uri.getScheme().equals("jar")) {
		        FileSystem fileSystem = FileSystems.newFileSystem(uri, Map.of());
		        pathColorMaps = fileSystem.getPath("/colormaps");
		    } else {
		    	pathColorMaps = Paths.get(uri);
		    }
		    Map<String, ColorMap> maps = new TreeMap<>();
		    maps.put(LEGACY_COLOR_MAP.getName(), LEGACY_COLOR_MAP);
		    for (var cm : loadColorMapsFromDirectory(pathColorMaps))
		    	maps.put(cm.getName(), cm);
		    return maps;
		} catch (Exception e) {
			logger.error("Error loading default colormaps: " + e.getLocalizedMessage(), e);
			return new TreeMap<>();
		}
	}
	
	
	/**
	 * Install colormaps from the specified paths.
	 * 
	 * @param paths optional paths containing either .tsv files containing colormaps, or directories that contain such .tsv files.
	 * @return true if changes were made, false otherwise
	 * 
	 */
	public static boolean installColorMaps(Path... paths) {
		boolean changes = false;
		for (var p : paths) {
			try {
		        // See if we have some custom colormaps installed by the user
		        if (Files.isDirectory(p)) {
		        	for (var cm : loadColorMapsFromDirectory(p)) {
		        		maps.put(cm.getName(), cm);		   
		        		changes = true;
		        	}
		        } else if (Files.isRegularFile(p)) {
		        	var cm = loadColorMap(p);
		        	if (cm != null) {
		        		maps.put(cm.getName(), cm);
		        		changes = true;
		        	}
		        }
			} catch (Exception e) {
				logger.error("Error loading custom colormaps", e);
			}
		}
		return changes;
	}
	
	/**
	 * Install colormaps.
	 * 
	 * @param colorMaps one or more colormaps.
	 * @return true if changes were made, false otherwise
	 * 
	 */
	public static boolean installColorMaps(ColorMap... colorMaps) {
		boolean changes = false;
		for (var cm : colorMaps) {
			maps.put(cm.getName(), cm);
			changes = true;
		}
		return changes;
	}
	
	
	/**
	 * Get an array of packed RGB values for a specific colormap.
	 * @param map the colormap providing colors
	 * @param nValues the number of colors to extract
	 * @param doInvert if true, reverse the order of the colors
	 * @return an int array of length nValues
	 */
	public static int[] getColors(ColorMap map, int nValues, boolean doInvert) {
		int[] vals = new int[nValues];
		double max = nValues - 1;
		for (int i = 0; i < nValues; i++) {
			double value = i/max;
			if (doInvert)
				value = 1 - value;
			vals[i] = map.getColor(value, 0, max);
		}
		return vals;
	}
	
	
	/**
	 * Get an unmodifiable map representing all the currently-available colormaps.
	 * 
	 * @return the available colormaps
	 * 
	 * @implNote 
	 *   The map is unmodifiable to avoid consumers removing colormaps that might be required elsewhere, 
	 *   but the underlying content may still be changed by installing new maps.
	 *   This behavior may change in a later version. Defensive copies should be made if maps need to be persistent.
	 */
	public static Map<String, ColorMap> getColorMaps() {
		return mapsUnmodifiable;
	}
	
	
	private static List<ColorMap> loadColorMapsFromDirectory(Path path) throws IOException {
		List<ColorMap> list = new ArrayList<>();
		Iterator<Path> iter = Files.list(path).filter(p -> p.getFileName().toString().endsWith(".tsv")).iterator();
	    while (iter.hasNext()) {
    		var temp = iter.next();
	    	try {
	    		list.add(loadColorMap(temp));
	    	} catch (Exception e) {
	    		logger.error("Error loading colormap from {}", temp);
	    	}
	    }
	    return list;
	}
	
	private static ColorMap loadColorMap(Path path) throws IOException {
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
    	return ColorMaps.createColorMap(name, r, g, b);
	}
	
	/**
	 * Create a colormap using floating point values for red, green and blue.
	 * These should be in the range 0-1.
	 * @param name
	 * @param r
	 * @param g
	 * @param b
	 * @return
	 */
	public static ColorMap createColorMap(String name, double[] r, double[] g, double[] b) {
		int[] ri = convertToInt(r);
		int[] gi = convertToInt(g);
		int[] bi = convertToInt(b);
		return createColorMap(name, ri, gi, bi);
	}
	
	private static int[] convertToInt(double[] arr) {
		int[] arr2 = new int[arr.length];
		for (int i = 0; i < arr.length; i++) {
			arr2[i] = (int)Math.round(arr[i] * 255.0);
		}
		return arr2;
	}

	/**
	 * Create a colormap using integer values for red, green and blue.
	 * These should be in the range 0-155.
	 * @param name
	 * @param r
	 * @param g
	 * @param b
	 * @return
	 */
	public static ColorMap createColorMap(String name, int[] r, int[] g, int[] b) {
		return new DefaultColorMap(name, r, g, b);
	}

	private static class DefaultColorMap implements ColorMap {
		
		private String name;
		
		private final int[] r;
		private final int[] g;
		private final int[] b;
		private int nColors = 256;
		private Integer[] colors = new Integer[nColors];
		
		DefaultColorMap(String name, int[] r, int[] g, int[] b) {
			this.name = name;
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
				color = ColorTools.makeRGB(r[ind], g[ind], b[ind]);
				colors[ind] = color;
			}
			return color;
		}

		@Override
		public Integer getColor(double value, double minValue, double maxValue) {
			//			System.out.println("Measurement map: " + minValue + ", " + maxValue);
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


//		@Override
//		public boolean hasAlpha() {
//			return false;
//		}

	}

	/**
	 * The previous default colormap (v0.1.2 and earlier).
	 */
	private static class PseudoColorMap implements ColorMap {

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
				color = ColorTools.makeRGB(r[ind], g[ind], b[ind]);
				colors[ind] = color;
			}
			return color;
		}

		@Override
		public Integer getColor(double value, double minValue, double maxValue) {
			//			System.out.println("Measurement map: " + minValue + ", " + maxValue);
			int ind = 0;
			if (maxValue > minValue) {
				ind = (int)((value - minValue) / (maxValue - minValue) * nColors + .5);
				ind = ind >= nColors ? nColors - 1 : ind;
				ind = ind < 0 ? 0 : ind;
			}
			return getColor(ind);
		}


//		@Override
//		public boolean hasAlpha() {
//			return false;
//		}

	}

}
