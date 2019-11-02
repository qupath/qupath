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
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.geom.Point2;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
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
	 * @throws ZipException
	 * @throws IOException
	 */
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
	 * Write a list of point annotations to a file.
	 * @param file
	 * @param pathObjects
	 * @param defaultColor
	 * @throws IOException
	 */
	public static void writePointsObjectsList(File file, List<? extends PathObject> pathObjects, final Integer defaultColor) throws IOException {
		try (ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
			Charset charset = Charset.forName("UTF-8");
			
			int ind = 0;
			for (PathObject pathObject : pathObjects) {
				if (!PathObjectTools.hasPointROI(pathObject))
					continue;
				PointsROI points = (PointsROI)pathObject.getROI();
				ZipEntry e = new ZipEntry(String.format("Points %d.txt", ++ind));
				out.putNextEntry(e);
				
				StringBuilder sb = new StringBuilder();
				sb.append("Name\t").append(pathObject.getDisplayedName()).append("\n");
				sb.append("Color\t").append(getDisplayedColor(pathObject, defaultColor)).append("\n");
				sb.append("Coordinates\t").append(points.getNumPoints()).append("\n");
				out.write(sb.toString().getBytes(charset));
				
				
				out.write(getPointsAsString(points).getBytes(charset));
				out.closeEntry();
			}
		}
	}
	
	
	
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
