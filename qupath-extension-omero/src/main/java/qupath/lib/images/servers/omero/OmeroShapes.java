/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2021 QuPath developers, The University of Edinburgh
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
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
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
			if (type.endsWith("#rectangle"))
				return context.deserialize(json, Rectangle.class);
			
			if (type.endsWith("#ellipse"))
				return context.deserialize(json, Ellipse.class);
			
			if (type.endsWith("#line"))
				return context.deserialize(json, Line.class);
			
			if (type.endsWith("#polygon"))
				return context.deserialize(json, Polygon.class);
			
			if (type.endsWith("#polyline"))
				return context.deserialize(json, Polyline.class);
			
			if (type.endsWith("#point"))
				return context.deserialize(json, Point.class);
			
			if (type.endsWith("#label"))
				return context.deserialize(json, Label.class);
			
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
			Type type = null;
			OmeroShape shape;
			if (roi instanceof RectangleROI) {
				type = Rectangle.class;
				shape = new Rectangle(roi.getBoundsX(), roi.getBoundsY(), roi.getBoundsWidth(), roi.getBoundsHeight());
				shape.setType("Rectangle");

			} else if (roi instanceof EllipseROI) {
				type = Ellipse.class;
				shape = new Ellipse(roi.getCentroidX(), roi.getCentroidY(), roi.getBoundsWidth()/2, roi.getBoundsHeight()/2);
				shape.setType("Ellipse");
				
			} else if (roi instanceof LineROI) {
				type = Line.class;
				LineROI lineRoi = (LineROI)roi;
				shape = new Line(lineRoi.getX1(), lineRoi.getY1(), lineRoi.getX2(), lineRoi.getY2());
				shape.setType("Line");

			} else if (roi instanceof PolylineROI) {
				type = Polyline.class;
				shape = new Polyline(pointsToString(roi.getAllPoints()));
				shape.setType("Polyline");

			} else if (roi instanceof PolygonROI) {
				type = Polygon.class;
				shape = new Polygon(pointsToString(roi.getAllPoints()));
				shape.setType("Polygon");
				
			} else if (roi instanceof PointsROI) {
				JsonElement[] points = new JsonElement[roi.getNumPoints()];
				List<Point2> roiPoints = roi.getAllPoints();
				PathClass pathClass = src.getPathClass();
				
				for (int i = 0; i < roiPoints.size(); i++) {
					shape = new Point(roiPoints.get(i).getX(), roiPoints.get(i).getY());
					shape.setType("Point");
					shape.setText(src.getName() != null ? src.getName() : "");
					shape.setFillColor(pathClass != null ? ARGBToRGBA(src.getPathClass().getColor()) : -256);
					points[i] = context.serialize(shape, Point.class);;
				}
				return context.serialize(points);
				
			} else if (roi instanceof GeometryROI) {
				// MultiPolygon
				logger.info("OMERO shapes do not support holes.");
				logger.warn("MultiPolygon will be split for OMERO compatibility.");
				roi = RoiTools.fillHoles(roi);
				PathClass pathClass = src.getPathClass();
				
				List<ROI> rois = RoiTools.splitROI(roi);
				JsonElement[] polygons = new JsonElement[rois.size()];
				
				for (int i = 0; i < polygons.length; i++) {
					shape = new Polygon(pointsToString(rois.get(i).getAllPoints()));
					shape.setType("Polygon");
					shape.setText(src.getName() != null ? src.getName() : "");
					shape.setFillColor(pathClass != null ? ARGBToRGBA(pathClass.getColor()) : -256);
					polygons[i] = context.serialize(shape, Polygon.class);
				}
				return context.serialize(polygons);
				
			} else {
				logger.warn("Unsupported type {}", roi.getRoiName());
				return null;				
			}
			
			// Set the appropriate colors
			if (src.getPathClass() != null) {
				int classColor = ARGBToRGBA(src.getPathClass().getColor());
				shape.setFillColor(classColor);
				shape.setStrokeColor(classColor);
			} else {
				shape.setFillColor(-256);	// Transparent
				shape.setStrokeColor(ARGBToRGBA(PathPrefs.colorDefaultObjectsProperty().get())); // Default Qupath object color
			}
			
			shape.setText(src.getName() != null ? src.getName() : "");
			return context.serialize(shape, type);
		}
	}
	
	/**
	 * Return the packed RGBA representation of the specified ARGB (packed) value.
	 * <p>
	 * This doesn't use the convenient method {@code makeRGBA()} as 
	 * the order in the method is confusing.
	 * @param argb
	 * @return rgba
	 */
	private static int ARGBToRGBA(int argb) {
		int a =  (argb >> 24) & 0xff;
		int r =  (argb >> 16) & 0xff;
		int g =  (argb >> 8) & 0xff;
		int b =  argb & 0xff;
		return (r<<24) + (g<<16) + (b<<8) + a;
	}
	
//	/**
//	 * Return the packed ARGB representation of the specified RGBA (packed) value.
//	 * <p>
//	 * This method is similar to {@code makeRGBA()} but with packed RGBA input.
//	 * @param rgba
//	 * @return argb
//	 */
//	private static int RGBAToARGB(int rgba) {
//		int r =  (rgba >> 24) & 0xff;
//		int g =  (rgba >> 16) & 0xff;
//		int b =  (rgba >> 8) & 0xff;
//		int a =  rgba & 0xff;
//		return (a<<24) + (r<<16) + (g<<8) + b;
//	}
	
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
		
		@SerializedName(value = "FillColor", alternate = "fillColor")
		private Integer fillColor;
		
		@SerializedName(value = "StrokeColor", alternate = "strokeColor")
		private Integer strokeColor;
		
		@SerializedName(value = "oldId")
		private String oldId = "-1:-1";
		
		
		private PathObject createObject(Function<ROI, PathObject> fun) {
			var pathObject = fun.apply(createROI());
			initializeObject(pathObject);
			return pathObject;
		}
		
		abstract ROI createROI();

		protected PathObject createAnnotation() {
			return createObject(r -> PathObjects.createAnnotationObject(r));
		}
		
		protected PathObject createDetection() {
			return createObject(r -> PathObjects.createDetectionObject(r));
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
		
		protected void setType(String type) {
			this.type = "http://www.openmicroscopy.org/Schemas/OME/2016-06#" + type;
		}
		
		protected void setText(String text) {
			this.text = text;			
		}
		
		protected void setStrokeColor(Integer color) {
			this.strokeColor = color;
		}
		
		protected void setFillColor(Integer color) {
			this.fillColor = color;
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
		ROI createROI() {
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
		ROI createROI() {
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
		ROI createROI() {
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
		ROI createROI() {
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
		ROI createROI() {
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
		ROI createROI() {
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
		ROI createROI() {
			logger.warn("Creating point (requested label shape is unsupported)");
			return ROIs.createPointsROI(x, y, getPlane());
		}
	}
	
	static class Mask extends OmeroShapes.OmeroShape {
		
		@Override
		ROI createROI() {
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