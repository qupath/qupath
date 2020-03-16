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
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;
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
	 * Read a list of point annotations from a stream.
	 * @param stream
	 * @return list of PathObjects
	 * @throws IOException
	 */
	public static List<PathObject> readPointsObjectFromStream(InputStream stream) throws IOException {
		List<PathObject> pathObjects = new ArrayList<>();
		Map<String[], List<Point2>> pointsMap = new HashMap<>();
		Scanner scanner = null;
		String[] cols = null;
		try {
			scanner = new Scanner(stream);
			
			// Header
			cols = scanner.nextLine().split("\t");
					
			while(scanner.hasNextLine())  {
				putPointObjectFromString(scanner.nextLine(), cols, pointsMap);
			}
		} finally {
			if (scanner != null)
				scanner.close();
		}
		
		ImagePlane defaultPlane = ImagePlane.getDefaultPlane();
		for (var entry: pointsMap.entrySet()) {
			var temp = Arrays.asList(cols);
			String pathClass = temp.indexOf("class") > -1 ? entry.getKey()[temp.indexOf("class")-2] : "";
			String name = temp.indexOf("name") > -1 ? entry.getKey()[temp.indexOf("name")-2] : "";
			Integer color = null;
			if (temp.indexOf("color") > -1) {
				var colorTemp = entry.getKey()[temp.indexOf("color")-2];
				if (!colorTemp.isEmpty())
					color = Integer.parseInt(colorTemp);
			}
			int c = temp.indexOf("c") > defaultPlane.getC() ? Integer.parseInt(entry.getKey()[temp.indexOf("c")-2]) : defaultPlane.getC();
			int z = temp.indexOf("z") > defaultPlane.getZ() ? Integer.parseInt(entry.getKey()[temp.indexOf("z")-2]) : defaultPlane.getZ();
			int t = temp.indexOf("t") > defaultPlane.getT() ? Integer.parseInt(entry.getKey()[temp.indexOf("t")-2]) : defaultPlane.getT();
			

			ROI points = ROIs.createPointsROI(entry.getValue(), ImagePlane.getPlaneWithChannel(c, z, t));
			PathObject pathObject = PathObjects.createAnnotationObject(points);
			
			if (name != null && name.length() > 0 && !"null".equals(name))
				pathObject.setName(name);
			if (pathClass != null && pathClass.length() > 0 && !"null".equals(pathClass))
				pathObject.setPathClass(PathClassFactory.getPathClass(pathClass, color));
			pathObject.setColorRGB(color);
			
			if (pathObject != null)
				pathObjects.add(pathObject);
		}
		return pathObjects;
	}
	
	/**
	 * Read a list of point annotations from a file.
	 * @param file
	 * @return list of PathObjects
	 * @throws IOException
	 */
	public static List<PathObject> readPointsObjectFromFile(File file) throws IOException {
		try (FileInputStream fis = new FileInputStream(file)){
			return readPointsObjectFromStream(fis);
		} catch (Exception e) {
			logger.error(e.getLocalizedMessage(), e);
		}
		return null;
	}
	

	
	/**
	 * Write a list of point annotations to a stream.
	 * @param stream
	 * @param pathObjects
	 * @throws IOException
	 */
	public static void writePointsObjectsList(OutputStream stream, List<? extends PathObject> pathObjects) throws IOException {
		// Check that all PathObjects contain only point annotations
		int unfilteredSize = pathObjects.size();
		pathObjects = pathObjects.stream()
								.filter(p -> p.getROI() instanceof PointsROI)
								.collect(Collectors.toList());
		int filteredSize = pathObjects.size();
		if (unfilteredSize != filteredSize)
			logger.warn(unfilteredSize-filteredSize + " of the " + filteredSize 
					+ " elements in list is/are not point annotations. These will be skipped.");
		
		
		try (OutputStreamWriter writer = new OutputStreamWriter(stream, StandardCharsets.UTF_8)) {
			
			List<String> cols = new ArrayList<>();
			cols.addAll(Arrays.asList("x", "y"));
			String sep = "\t";
			
			ImagePlane defaultPlane = ImagePlane.getDefaultPlane();
			boolean hasClass = pathObjects.stream().anyMatch(p -> p.getPathClass() != null);
			boolean hasName = pathObjects.stream().anyMatch(p -> p.getName() != null);
			boolean hasColor = pathObjects.stream().anyMatch(p -> p.getColorRGB() != null);
			boolean hasC = pathObjects.stream().anyMatch(p -> p.getROI().getC() > defaultPlane.getC());
			boolean hasZ = pathObjects.stream().anyMatch(p -> p.getROI().getZ() > defaultPlane.getZ());
			boolean hasT = pathObjects.stream().anyMatch(p -> p.getROI().getT() > defaultPlane.getT());
			
			if (hasC)
				cols.add("c");
			if (hasZ)
				cols.add("z");
			if (hasT)
				cols.add("t");
			if (hasClass)
				cols.add("class");
			if (hasName)
				cols.add("name");
			if (hasColor)
				cols.add("color");
			
			
			for (String col: cols)
				writer.write(col + sep);
			writer.write("\n");
			
			for (PathObject pathObject : pathObjects) {
				if (!PathObjectTools.hasPointROI(pathObject))
					continue;
				
				PointsROI points = (PointsROI)pathObject.getROI();
				
				for (Point2 point: points.getAllPoints()) {
					String[] row = new String[cols.size()];
					
					row[cols.indexOf("x")] = point.getX() + sep;
					row[cols.indexOf("y")] = point.getY() + sep;
					if (hasC)
						row[cols.indexOf("c")] = points.getC() + sep;
					if (hasZ)
						row[cols.indexOf("z")] = points.getZ() + sep;
					if (hasT)
						row[cols.indexOf("t")] = points.getT() + sep;
					if (hasClass)
						row[cols.indexOf("class")] = pathObject.getPathClass() != null ? pathObject.getPathClass() + sep : sep;
					if (hasName)
						row[cols.indexOf("name")] = pathObject.getName() != null ? pathObject.getName() + sep : sep;
					if (hasColor)
						row[cols.indexOf("color")] = pathObject.getColorRGB() != null ? pathObject.getColorRGB() + sep : sep;
					
					for (String val: row)
						writer.write(val);
					writer.write("\n");
				}
			}
		}
	}
	
	
	/**
	 * Write a list of point annotations to a file.
	 * @param file
	 * @param pathObjects
	 * @throws IOException
	 */
	public static void writePointsObjectsList(File file, List<? extends PathObject> pathObjects) throws IOException {
		try(FileOutputStream fos = new FileOutputStream(file)) {
			writePointsObjectsList(fos, pathObjects);
		} catch (Exception e) {
			logger.error(e.getLocalizedMessage(), e);
		}
	}
	
	
	/**
	 * Helper method that takes a String representing any row taken from a 'Point2 annotations 
	 * TSV file' and creates a Point2 object to put into the provided map.
	 * @param s
	 * @param pointsMap
	 */
	private static void putPointObjectFromString(String s, String[] cols, Map<String[], List<Point2>> pointsMap) {
		String[] info = new String[cols.length-2];
		String[] values = s.split("(?<=\t)");

		// Core columns
		double x = Double.parseDouble(values[0].trim());
		double y = Double.parseDouble(values[1].trim());
		
		// Optional columns
		for (int i = 0; i < cols.length-2; i++) {
				info[i] = values[i+2].trim();
		}
		
		boolean found = false;
		for (var key: pointsMap.keySet()) {
			if (Arrays.equals(key,  info)) {
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