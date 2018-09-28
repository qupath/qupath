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
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.geom.Point2;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.PointsROI;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.PathPoints;

/**
 * Helper class for reading/writing point objects in terms of their x,y coordinates.
 * 
 * @author Pete Bankhead
 *
 */
public class PointIO {
	
	final private static Logger logger = LoggerFactory.getLogger(PointIO.class);
	
	public static List<PathObject> readPointsObjectList(File file) {
		List<PathObject> pathObjects = new ArrayList<>();
		ZipFile zipFile;
		Scanner s = null;
		try {
			zipFile = new ZipFile(file);
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
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} finally {
			if (s != null)
				s.close();
		}
		return pathObjects;
	}
	
	
	static Integer getDisplayedColor(final PathObject pathObject, final Integer defaultColor) {
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
	

	public static void writePointsObjectsList(File file, List<? extends PathObject> pathObjects, final Integer defaultColor) {
		ZipOutputStream out;
		try {
			out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
			Charset charset = Charset.forName("UTF-8");
			
			int ind = 0;
			for (PathObject pathObject : pathObjects) {
				if (!pathObject.isPoint())
					continue;
				PointsROI points = (PointsROI)pathObject.getROI();
				ZipEntry e = new ZipEntry(String.format("Points %d.txt", ++ind));
				out.putNextEntry(e);
				
				StringBuilder sb = new StringBuilder();
				sb.append("Name\t").append(pathObject.getDisplayedName()).append("\n");
				sb.append("Color\t").append(getDisplayedColor(pathObject, defaultColor)).append("\n");
				sb.append("Coordinates\t").append(points.getNPoints()).append("\n");
				out.write(sb.toString().getBytes(charset));
				
				
				out.write(getPointsAsString(points).getBytes(charset));
				out.closeEntry();
			}
			out.close();
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}
	
	
	
	
	
	
	
	
	public static List<? extends PathPoints> readPointsList(File file) {
		List<PathPoints> pointsList = new ArrayList<>();
		ZipFile zipFile;
		Scanner s = null;
		try {
			zipFile = new ZipFile(file);
			Enumeration<? extends ZipEntry> entries = zipFile.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				s = new Scanner(new BufferedInputStream(zipFile.getInputStream(entry)));
				s.useDelimiter("\\A");
				PathPoints points = readPointsFromString(s.next());
				if (points != null)
					pointsList.add(points);
				s.close();
			}
			zipFile.close();
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} finally {
			if (s != null)
				s.close();
		}
		return pointsList;
	}

	public static void writePointsList(File file, List<? extends PointsROI> pointsList) {
		ZipOutputStream out;
		try {
			out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
			Charset charset = Charset.forName("UTF-8");
			
			int ind = 0;
			for (PointsROI points : pointsList) {
				ZipEntry e = new ZipEntry(String.format("Points %d.txt", ++ind));
				out.putNextEntry(e);
				out.write(getPointsAsString(points).getBytes(charset));
				out.closeEntry();
			}
			out.close();
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}
	
	
	
	public static PathObject readPointsObjectFromString(String s) {
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
		
		PathPoints points = ROIs.createPointsROI(pointsList, ImagePlane.getDefaultPlane());
		PathObject pathObject = PathObjects.createAnnotationObject(points);
		if (name != null && name.length() > 0 && !"null".equals(name))
			pathObject.setName(name);
		pathObject.setColorRGB(color);
		return pathObject;
	}
	

	
	public static PathPoints readPointsFromString(String s) {
		List<Point2> pointsList = new ArrayList<>();
		Scanner scanner = new Scanner(s);
//		String name = scanner.nextLine();
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
		return ROIs.createPointsROI(pointsList, ImagePlane.getDefaultPlane());
	}
	
	public static String getPointsAsString(PointsROI points) {
		StringBuilder sb = new StringBuilder();
//		String name = points.getName();
//		if (name != null)
//			sb.append(name);
//		sb.append("\n");
		for (Point2 p : points.getPointList())
			sb.append(String.format("%.4f\t%.4f\n", p.getX(), p.getY()));
		return sb.toString();
	}
	

	
	public static void writePoints(File file, PointsROI points) {
		try {
			PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(file)));
//			String name = points.getName();
//			if (name != null)
//				out.println(name);
//			else
//				out.println();
			for (Point2 p : points.getPointList())
				out.println(String.format(".%3f\t.%3f", p.getX(), p.getY()));
			out.close();
		} catch (IOException e) {
			logger.error("Error writing {} to {}", points.toString(), file.getAbsolutePath());
			e.printStackTrace();
		}
	}


	public static PathPoints readPoints(File file) {
		BufferedReader reader = null;
		PathPoints points = null;
		List<Point2> pointsList = new ArrayList<>();
		try {
			reader = new BufferedReader(new FileReader(file));
			String name = reader.readLine();

			String line;
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (line.length() == 0)
					break;
				String[] splits = line.split("\t");
				double x = Double.parseDouble(splits[0]);
				double y = Double.parseDouble(splits[1]);
				pointsList.add(new Point2(x, y));
			}
			points = ROIs.createPointsROI(pointsList, ImagePlane.getDefaultPlane());
//			if (name != null && name.length() > 0)
//				points.setName(name);
		} catch (IOException e) {
			logger.error("Error reading points from {}", file.getAbsolutePath());
			e.printStackTrace();
		} finally {
			try {
				if (reader != null)
					reader.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return points;
	}

}
