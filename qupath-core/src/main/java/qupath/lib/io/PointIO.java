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

package qupath.lib.io;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.geom.Point2;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.PointsROI;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

/**
 * Helper class for reading/writing point objects in terms of their x,y coordinates.
 * 
 * @author Pete Bankhead
 *
 */
public class PointIO {
	
	final private static Logger logger = LoggerFactory.getLogger(PointIO.class);
	
	/**
	 * Read a list of point annotations from a file.
	 * @param file
	 * @return
	 * @throws IOException
	 */
	public static List<PathObject> readPointsObjectFromFile(File file) throws IOException {
		List<PathObject> pathObjects = new ArrayList<>();
		Map<String[], List<Point2>> pointsMap = new HashMap<>();
		Scanner scanner = null;
		try (FileInputStream fis = new FileInputStream(file)) {
			scanner = new Scanner(fis);
			
			// Header
			scanner.nextLine();
					
			while(scanner.hasNextLine())  {
				putPointObjectFromString(scanner.nextLine(), pointsMap);
			}
		} finally {
			if (scanner != null)
				scanner.close();
		}

		for (var entry: pointsMap.entrySet()) {
			String color = entry.getKey()[0];
			String name = entry.getKey()[1];
			String pathClass = entry.getKey()[2];
			int c = entry.getKey()[3].isEmpty() ? -1 : Integer.parseInt(entry.getKey()[3]);
			int z = entry.getKey()[4].isEmpty() ? 0 : Integer.parseInt(entry.getKey()[4]);
			int t = entry.getKey()[5].isEmpty() ? 0 : Integer.parseInt(entry.getKey()[5]);
			

			// This will create a different pathObject for each Point2 (no grouping)
			for (var value: entry.getValue()) {
				ROI points = ROIs.createPointsROI(Arrays.asList(value), ImagePlane.getPlaneWithChannel(c, z, t));
				PathObject pathObject = PathObjects.createAnnotationObject(points);
				
				if (name != null && name.length() > 0 && !"null".equals(name))
					pathObject.setName(name);
				if (pathClass != null && pathClass.length() > 0 && !"null".equals(pathClass))
					pathObject.setPathClass(PathClassFactory.getPathClass(pathClass, Integer.parseInt(color)));
				pathObject.setColorRGB(Integer.parseInt(color));
				
				if (pathObject != null)
					pathObjects.add(pathObject);
			}	
		}
		return pathObjects;
	}
	

	
	/**
	 * Write a list of point annotations to a TSV file.
	 * @param file
	 * @param pathObjects
	 * @param defaultColor
	 * @throws IOException
	 */
	public static void writePointsObjectsList(File file, List<? extends PathObject> pathObjects, final Integer defaultColor) throws IOException {
		try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
			
			String sep = "\t";
			List<String> cols = new ArrayList<>();
			cols.addAll(Arrays.asList("color", "x", "y"));
			
			boolean hasClass = pathObjects.stream().anyMatch(p -> p.getPathClass() != null);
			boolean hasName = pathObjects.stream().anyMatch(p -> p.getName() != null);
			boolean hasZ = pathObjects.stream().anyMatch(p -> ((PointsROI)p.getROI()).getZ() > 0);
			boolean hasC = pathObjects.stream().anyMatch(p -> ((PointsROI)p.getROI()).getC() > -1);
			boolean hasT = pathObjects.stream().anyMatch(p -> ((PointsROI)p.getROI()).getT() > 0);
			
			if (hasName)
				cols.add("name");
			if (hasClass)
				cols.add("class");
			if (hasC)
				cols.add("c");
			if (hasZ)
				cols.add("z");
			if (hasT)
				cols.add("t");
			
			for (String col: cols)
				writer.write(col + sep);
			writer.write("\n");
			
			for (PathObject pathObject : pathObjects) {
				if (!PathObjectTools.hasPointROI(pathObject))
					continue;
				
				PointsROI points = (PointsROI)pathObject.getROI();
				
				for (Point2 point: points.getAllPoints()) {
					String color = (getDisplayedColor(pathObject, defaultColor)) + sep;
					String x = point.getX() + sep;
					String y = point.getY() + sep;
					
					String name = pathObject.getName() != null ? pathObject.getName() + sep : sep;
					String pathClass = pathObject.getPathClass() != null ? pathObject.getPathClass() + sep : sep;
					String c = points.getC() > -1 ? points.getC() + sep : sep;
					String z = points.getZ() > 0 ? points.getZ() + sep : sep;
					String t = points.getT() > 0 ? points.getT() + sep : sep;
					
					writer.write(color + x + y + name + pathClass + c + z + t + "\n");
				}
			}
		}
	}
	
	
	/**
	 * Helper method that takes a String representing any row taken from a 'Point2 annotations 
	 * TSV file' and creates a Point2 object to put into the provided map.
	 * @param s
	 * @param pointsMap
	 */
	private static void putPointObjectFromString(String s, Map<String[], List<Point2>> pointsMap) {
		List<Point2> pointsList = new ArrayList<>();
		String[] info = new String[6];
		String[] optionalCols = new String[] {"color", "name", "class", "z", "c", "t"};
		String[] values = s.split("(?<=\t)");

		// Core columns
		info[0] = values[0].trim();	// color
		double x = Double.parseDouble(values[1].trim());
		double y = Double.parseDouble(values[2].trim());
		
		// Optional columns
		for (int i = 1; i < optionalCols.length; i++) {
				info[i] = values[i+2].trim();
		}
		
		pointsList.add(new Point2(x, y));
		boolean found = false;
		for (var key: pointsMap.keySet()) {
			if (Arrays.equals(key, info)) {
				pointsMap.get(key).add(new Point2(x, y));
				found = true;
				break;
			}	
		}
		if (!found) {
			List<Point2> newArray = new ArrayList<>();
			newArray.add(new Point2(x, y));
			pointsMap.put(info, newArray);
		}

	}
	
	
	
	private static Integer getDisplayedColor(final PathObject pathObject, final Integer defaultColor) {
		// Check if any color has been set - if so, return it
		Integer color = pathObject.getColorRGB();
		if (color != null)
			return color;
		// Check if any class has been set, if so then use its color
		PathClass pathClass = pathObject.getPathClass();
		if (pathClass != null)
			color = pathClass.getColor();
		if (color != null)
			return color;
		return defaultColor;
	}
	
	
	/**
	 * Read a list of point annotations from a file. Not recommended for use (will be removed in future releases).
	 * @param file
	 * @return
	 * @throws ZipException
	 * @throws IOException
	 */
	@Deprecated
	public static List<PathObject> readPointsObjectList(File file) throws ZipException, IOException {
		List<PathObject> pathObjects = new ArrayList<>();
		Scanner s = null;
		try (ZipFile zipFile = new ZipFile(file)) {
			Enumeration<? extends ZipEntry> entries = zipFile.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				s = new Scanner(new BufferedInputStream(zipFile.getInputStream(entry)));
				s.useDelimiter("\\A");
				PathObject pathObject = readPointsObjectFromString(s.next());
				if (pathObject != null)
					pathObjects.add(pathObject);
				s.close();
			}
			zipFile.close();
		} finally {
			if (s != null)
				s.close();
		}
		return pathObjects;
	}
	
	/**
	 * Helper method for readPointsObjectList(), will be removed in future releases.
	 * @param s
	 * @return
	 */
	@Deprecated
	private static PathObject readPointsObjectFromString(String s) {
		List<Point2> pointsList = new ArrayList<>();
		Scanner scanner = new Scanner(s);
		String name = scanner.nextLine().split("\t")[1].trim();
		Integer color = Integer.parseInt(scanner.nextLine().split("\t")[1]);
		// Skip the coordinate count line...
		int count = Integer.parseInt(scanner.nextLine().split("\t")[1]);
		while (scanner.hasNextLine()) {
			String line = scanner.nextLine().trim();
			if (line.length() == 0)
				break;
			String[] splits = line.split("\t");
			double x = Double.parseDouble(splits[0]);
			double y = Double.parseDouble(splits[1]);
			pointsList.add(new Point2(x, y));
		}
		scanner.close();
//		if (name != null && name.length() > 0)
//			points.setName(name);
		if (count != pointsList.size())
			logger.warn("Warning: {} points expected, {} points found", count, pointsList.size());
		
		ROI points = ROIs.createPointsROI(pointsList, ImagePlane.getDefaultPlane());
		PathObject pathObject = PathObjects.createAnnotationObject(points);
		if (name != null && name.length() > 0 && !"null".equals(name))
			pathObject.setName(name);
		pathObject.setColorRGB(color);
		return pathObject;
	}
	
	
	private static String getPointsAsString(PointsROI points) {
		StringBuilder sb = new StringBuilder();
//		String name = points.getName();
//		if (name != null)
//			sb.append(name);
//		sb.append("\n");
		for (Point2 p : points.getAllPoints())
			sb.append(String.format("%.4f\t%.4f\n", p.getX(), p.getY()));
		return sb.toString();
	}
	
}