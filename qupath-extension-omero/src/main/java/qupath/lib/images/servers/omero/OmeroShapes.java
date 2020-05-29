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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import qupath.lib.geom.Point2;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;
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
	
	
	public static abstract class OmeroShape {
		
		@SerializedName(value = "TheC")
		private int c = -1;
		@SerializedName(value = "TheZ")
		private int z;
		@SerializedName(value = "TheT")
		private int t;
		
		@SerializedName(value = "Text", alternate = "text")
		private String text;
		
		@SerializedName(value = "Locked", alternate = "locked")
		private Boolean locked;
		
		@SerializedName(value = "StrokeColor", alternate = "strokeColor")
		private Integer strokeColor;
		
		
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
		
		@Override
		public ROI createROI() {
			logger.debug("Creating point");
			return ROIs.createPointsROI(x, y, getPlane());
		}
		
	}
	
	static class Polyline extends OmeroShapes.OmeroShape {
		
		@SerializedName(value = "Points", alternate = "points")
		private String pointString;
		
		@Override
		public ROI createROI() {
			logger.debug("Creating polyline");
			return ROIs.createPolylineROI(parsePoints(pointString), getPlane());
		}
		
	}
	
	static class Polygon extends OmeroShapes.OmeroShape {
		
		@SerializedName(value = "Points", alternate = "points")
		private String pointString;
		
		@Override
		public ROI createROI() {
			logger.debug("Creating polygon");
			return ROIs.createPolygonROI(parsePoints(pointString), getPlane());
		}
		
	}
	
	static class Label extends OmeroShapes.OmeroShape {
		
		@SerializedName(value = "X", alternate = "x")
		private double x;
		@SerializedName(value = "Y", alternate = "y")
		private double y;
		
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
	
	private static List<Point2> parsePoints(String pointsString) {
		List<Point2> points = new ArrayList<>();
		for (String p : pointsString.split(" ")) {
			String[] p2 = p.split(",");
			points.add(new Point2(Double.parseDouble(p2[0]), Double.parseDouble(p2[1])));
		}
		return points;
	}
	
}