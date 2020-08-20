/*-
 * #%L
 * This file is part of QuPath.
 * %%
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

package qupath.lib.images.servers.omero;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.annotations.SerializedName;
import qupath.lib.geom.Point2;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.EllipseROI;
import qupath.lib.roi.GeometryROI;
import qupath.lib.roi.LineROI;
import qupath.lib.roi.PointsROI;
import qupath.lib.roi.PolygonROI;
import qupath.lib.roi.PolylineROI;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.RectangleROI;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.interfaces.ROI;

class OmeroShapes {
	
	private final static Logger logger = LoggerFactory.getLogger(OmeroShapes.class);

	
	static class GsonShapeDeserializer implements JsonDeserializer<OmeroShape> {

		@Override
		public OmeroShape deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
				throws JsonParseException {
			
			var type = ((JsonObject)json).get("@type").getAsString().toLowerCase();
			if (type.endsWith("#rectangle")) {
				return context.deserialize(json, Rectangle.class);
			}
			if (type.endsWith("#ellipse")) {
				return context.deserialize(json, Ellipse.class);
			}
			if (type.endsWith("#line")) {
				return context.deserialize(json, Line.class);
			}
			if (type.endsWith("#polygon")) {
				return context.deserialize(json, Polygon.class);
			}
			if (type.endsWith("#polyline")) {
				return context.deserialize(json, Polyline.class);
			}
			if (type.endsWith("#point")) {
				return context.deserialize(json, Point.class);
			}
			if (type.endsWith("#label")) {
				return context.deserialize(json, Label.class);
			}
//			if (type.endsWith("#mask")) {
//				return context.deserialize(json, Mask.class);
//			}
			logger.warn("Unsupported type {}", type);
			return null;
		}
		
	}
	
	static class GsonShapeSerializer implements JsonSerializer<PathObject> {

		@Override
		public JsonElement serialize(PathObject src, Type typeOfSrc, JsonSerializationContext context) {
			ROI roi = src.getROI();
			if (roi instanceof RectangleROI) {
				var shape = new Rectangle(roi.getBoundsX(), roi.getBoundsY(), roi.getBoundsWidth(), roi.getBoundsHeight());
				shape.setType("Rectangle");
				return context.serialize(shape, Rectangle.class);
			}
			if (roi instanceof EllipseROI) {
				var shape = new Ellipse(roi.getCentroidX(), roi.getCentroidY(), roi.getBoundsWidth()/2, roi.getBoundsHeight()/2);
				shape.setType("Ellipse");
				return context.serialize(shape, Ellipse.class);
			}
			if (roi instanceof LineROI) {
				LineROI lineRoi = (LineROI)roi;
				var shape = new Line(lineRoi.getX1(), lineRoi.getY1(), lineRoi.getX2(), lineRoi.getY2());
				shape.setType("Line");
				return context.serialize(shape, Line.class);
			}
			if (roi instanceof PointsROI) {
				JsonElement[] points = new JsonElement[roi.getNumPoints()];
				List<Point2> roiPoints = roi.getAllPoints();
				
				for (int i = 0; i < roiPoints.size(); i++) {
					var shape = new Point(roiPoints.get(i).getX(), roiPoints.get(i).getY());
					shape.setType("Point");
					points[i] = context.serialize(shape, Point.class);;
				}
				return context.serialize(points);
			}
			if (roi instanceof PolylineROI) {
				var shape = new Polyline(pointsToString(roi.getAllPoints()));
				shape.setType("Polyline");
				return context.serialize(shape, Polyline.class);
			}
			if (roi instanceof PolygonROI) {
				var shape = new Polygon(pointsToString(roi.getAllPoints()));
				shape.setType("Polygon");
				return context.serialize(shape, Polygon.class);
			}
			if (roi instanceof GeometryROI) {
				// MultiPolygon
				logger.info("OMERO shapes do not support holes.");
				logger.warn("MultiPolygon will be split for OMERO compatibility.");
				roi = RoiTools.fillHoles(roi);
				
				List<ROI> rois = RoiTools.splitROI(roi);
				JsonElement[] polygons = new JsonElement[rois.size()];
				
				for (int i = 0; i < polygons.length; i++) {
					var shape = new Polygon(pointsToString(rois.get(i).getAllPoints()));
					shape.setType("Polygon");
					polygons[i] = context.serialize(shape, Polygon.class);
				}
				return context.serialize(polygons);
				
			}
			logger.warn("Unsupported type {}", roi.getRoiName());
			return null;
		}
	}
	
	
	public static abstract class OmeroShape {
		
		@SerializedName(value = "TheC")
		private int c = -1;
		@SerializedName(value = "TheZ")
		private int z;
		@SerializedName(value = "TheT")
		private int t;
		
		@SerializedName(value = "@type")
		private String type;
		
		@SerializedName(value = "Text", alternate = "text")
		private String text;
		
		@SerializedName(value = "Locked", alternate = "locked")
		private Boolean locked;
		
		@SerializedName(value = "StrokeColor", alternate = "strokeColor")
		private Integer strokeColor;
		
		@SerializedName(value = "oldId")
		private String oldId = "-1:-1";
		
		
		public PathObject createAnnotation() {
			return createObject(r -> PathObjects.createAnnotationObject(r));
		}
		
		public PathObject createDetection() {
			return createObject(r -> PathObjects.createDetectionObject(r));
		}
		
		public PathObject createObject(Function<ROI, PathObject> fun) {
			var pathObject = fun.apply(createROI());
			initializeObject(pathObject);
			return pathObject;
		}
		
		protected void initializeObject(PathObject pathObject) {
			if (text != null && !text.isBlank())
				pathObject.setName(text);
			if (strokeColor != null)
				pathObject.setColorRGB(strokeColor >> 8);
			if (locked != null)
				pathObject.setLocked(locked);
		}
		
		
		protected ImagePlane getPlane() {
			if (c >= 0)
				return ImagePlane.getPlaneWithChannel(c, z, t);
			else
				return ImagePlane.getPlane(z, t);
		}
		
		public abstract ROI createROI();
		
		protected void setType(String type) {
			this.type = "http://www.openmicroscopy.org/Schemas/OME/2016-06#" + type;
		}
		
	}
	
	static class Rectangle extends OmeroShapes.OmeroShape {
		
		@SerializedName(value = "X", alternate = "x")
		private double x;
		@SerializedName(value = "Y", alternate = "y")
		private double y;
		@SerializedName(value = "Width", alternate = "width")
		private double width;
		@SerializedName(value = "Height", alternate = "height")
		private double height;
		
		private Rectangle(double x, double y, double width, double height) {
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
		}
		
		@Override
		public ROI createROI() {
			logger.debug("Creating rectangle");
			return ROIs.createRectangleROI(x, y, width, height, getPlane());
		}
	}
	
	static class Ellipse extends OmeroShapes.OmeroShape {
		
		@SerializedName(value = "X", alternate = "x")
		private double x;
		@SerializedName(value = "Y", alternate = "y")
		private double y;
		@SerializedName(value = "RadiusX", alternate = "radiusX")
		private double radiusX;
		@SerializedName(value = "RadiusY", alternate = "radiusY")
		private double radiusY;
		
		private Ellipse(double x, double y, double radiusX, double radiusY) {
			this.x = x;
			this.y = y;
			this.radiusX = radiusX;
			this.radiusY = radiusY;
		}
		
		@Override
		public ROI createROI() {
			logger.debug("Creating ellipse");
			return ROIs.createEllipseROI(x-radiusX, y-radiusY, radiusX*2, radiusY*2, getPlane());
		}
	}
	
	static class Line extends OmeroShapes.OmeroShape {
		
		@SerializedName(value = "X1", alternate = "x1")
		private double x1;
		@SerializedName(value = "Y1", alternate = "y1")
		private double y1;
		@SerializedName(value = "X2", alternate = "x2")
		private double x2;
		@SerializedName(value = "Y2", alternate = "y2")
		private double y2;
		
		private Line(double x1, double y1, double x2, double y2) {
			this.x1 = x1;
			this.y1 = y1;
			this.x2 = x2;
			this.y2 = y2;
		}
		
		@Override
		public ROI createROI() {
			logger.debug("Creating line");
			return ROIs.createLineROI(x1, y1, x2, y2, getPlane());
		}
	}
	
	static class Point extends OmeroShapes.OmeroShape {
		
		@SerializedName(value = "X", alternate = "x")
		private double x;
		@SerializedName(value = "Y", alternate = "y")
		private double y;
		
		private Point(double x, double y) {
			this.x = x;
			this.y = y;
		}
		
		@Override
		public ROI createROI() {
			logger.debug("Creating point");
			return ROIs.createPointsROI(x, y, getPlane());
		}
	}
	
	static class Polyline extends OmeroShapes.OmeroShape {
		
		@SerializedName(value = "Points", alternate = "points")
		private String pointString;
		
		private Polyline(String pointString) {
			this.pointString = pointString;
		}
		
		@Override
		public ROI createROI() {
			logger.debug("Creating polyline");
			return ROIs.createPolylineROI(parseStringPoints(pointString), getPlane());
		}
	}
	
	static class Polygon extends OmeroShapes.OmeroShape {
		
		@SerializedName(value = "Points", alternate = "points")
		private String pointString;
		
		private Polygon(String pointString) {
			this.pointString = pointString;
		}
		
		@Override
		public ROI createROI() {
			logger.debug("Creating polygon");
			return ROIs.createPolygonROI(parseStringPoints(pointString), getPlane());
		}
	}
	
	static class Label extends OmeroShapes.OmeroShape {
		
		@SerializedName(value = "X", alternate = "x")
		private double x;
		@SerializedName(value = "Y", alternate = "y")
		private double y;
		
		private Label(double x, double y) {
			this.x = x;
			this.y = y;
		}
		
		@Override
		public ROI createROI() {
			logger.warn("Creating point (requested label shape is unsupported)");
			return ROIs.createPointsROI(x, y, getPlane());
		}
	}
	
	static class Mask extends OmeroShapes.OmeroShape {
		
		@Override
		public ROI createROI() {
			throw new UnsupportedOperationException("Mask rois not yet supported!");
		}
	}
	
	/**
	 * Parse the OMERO string representing points
	 * @param pointsString
	 * @return list of Point2
	 */
	private static List<Point2> parseStringPoints(String pointsString) {
		List<Point2> points = new ArrayList<>();
		for (String p : pointsString.split(" ")) {
			String[] p2 = p.split(",");
			points.add(new Point2(Double.parseDouble(p2[0]), Double.parseDouble(p2[1])));
		}
		return points;
	}
	
	/**
	 * Converts the specified list of {@code Point2}s into an OMERO-friendly string
	 * @param points
	 * @return string of points
	 */
	private static String pointsToString(List<Point2> points) {
		return points.stream().map(e -> e.getX() + "," + e.getY()).collect(Collectors.joining (" "));
		
		
	}
	
}