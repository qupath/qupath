package qupath.lib.io;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import qupath.lib.common.GeneralTools;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.interfaces.ROI;
import qupath.lib.roi.jts.ConverterJTS;

/**
 * Gson-compatible TypeAdapter that converts ROIs to and from a Geo-JSON representation.
 * 
 * @author Pete Bankhead
 */
class ROITypeAdapter extends TypeAdapter<ROI> {

	@Override
	public void write(JsonWriter out, ROI roi) throws IOException {
		/*
		 * A Collection<PathObject> is a "FeatureCollection"
		 * A PathObject is a "Feature"
		 *   The type of object is the "id" (detection, annotation, cell, tile...)
		 *   Additional info should be in the "Properties"
		 * A ROI is a "Geometry"
		 */
		
		Geometry geometry = roi.getGeometry();
		
		out.beginObject();
		writeGeometry(geometry, out, 2);
		
		// Write the plane info if it isn't the default
		ImagePlane plane = roi.getImagePlane();
		if (!ImagePlane.getDefaultPlane().equals(plane)) {
			out.name("plane");
			out.beginObject();
			out.name("c");
			out.value(plane.getC());
			out.name("z");
			out.value(plane.getZ());
			out.name("t");
			out.value(plane.getT());
			out.endObject();
		}
		
		out.endObject();
	}

	@Override
	public ROI read(JsonReader in) throws IOException {
		
		Gson gson = new GsonBuilder()
			.setLenient()
			.create();
		
		JsonObject obj = gson.fromJson(in, JsonObject.class);
		Geometry geometry = parseGeometry(obj, new GeometryFactory());
		
		ImagePlane plane;
		if (obj.has("plane")) {
			JsonObject planeObj = obj.get("plane").getAsJsonObject();
			int c = planeObj.get("c").getAsInt();
			int z = planeObj.get("z").getAsInt();
			int t = planeObj.get("t").getAsInt();
			plane = ImagePlane.getPlaneWithChannel(c, z, t);
		} else
			plane = ImagePlane.getDefaultPlane();
		
		return ConverterJTS.convertGeometryToROI(geometry, plane);
	}
	
	
	
	static Geometry parseGeometry(JsonObject obj, GeometryFactory factory) {
		String type = obj.get("type").getAsString();
		JsonArray coordinates = null;
		if (obj.has("coordinates"))
			coordinates = obj.getAsJsonArray("coordinates").getAsJsonArray();
			
		switch (type) {
		case "Point":
			return parsePoint(coordinates, factory);
		case "MultiPoint":
			return parseMultiPoint(coordinates, factory);
		case "LineString":
			return parseLineString(coordinates, factory);
		case "MultiLineString":
			return parseMultiLineString(coordinates, factory);
		case "Polygon":
			return parsePolygon(coordinates, factory);
		case "MultiPolygon":
			return parseMultiPolygon(coordinates, factory);
		case "GeometryCollection":
			return parseGeometryCollection(obj, factory);
		}
		throw new IllegalArgumentException("No Geometry type found for object " + obj);
	}

	/**
	 * Parse a coordinate from a JsonArray. No error checking is performed. Supports either two elements (x,y) or three (x,y,z).
	 * @param array
	 * @return
	 */
	static Coordinate parseCoordinate(JsonArray array) {
		double x = array.get(0).getAsDouble();
		double y = array.get(1).getAsDouble();
		if (array.size() == 2)
			return new Coordinate(x, y);
		else {
			double z = array.get(2).getAsDouble();
			return new Coordinate(x, y, z);
		}
	}

	static Coordinate[] parseCoordinateArray(JsonArray array) {
		Coordinate[] coordinates = new Coordinate[array.size()];
		for (int i = 0; i < array.size(); i++) {
			coordinates[i]  = parseCoordinate(array.get(i).getAsJsonArray());
		}
		return coordinates;
	}

	static Point parsePoint(JsonArray coord, GeometryFactory factory) {
		return factory.createPoint(parseCoordinate(coord));
	}

	static MultiPoint parseMultiPoint(JsonArray coords, GeometryFactory factory) {
		return factory.createMultiPointFromCoords(parseCoordinateArray(coords));
	}

	static LineString parseLineString(JsonArray coords, GeometryFactory factory) {
		return factory.createLineString(parseCoordinateArray(coords));
	}

	static MultiLineString parseMultiLineString(JsonArray coords, GeometryFactory factory) {
		LineString[] lineStrings = new LineString[coords.size()];
		for (int i = 0; i < coords.size(); i++) {
			JsonArray array = coords.get(i).getAsJsonArray();
			lineStrings[i] = factory.createLineString(parseCoordinateArray(array));
		}
		return factory.createMultiLineString(lineStrings);
	}

	static Polygon parsePolygon(JsonArray coords, GeometryFactory factory) {
		int n = coords.size();
		if (n == 0)
			return factory.createPolygon();
		JsonArray array = coords.get(0).getAsJsonArray();
		LinearRing shell = factory.createLinearRing(parseCoordinateArray(array));
		if (n == 1)
			return factory.createPolygon(shell);
		LinearRing[] holes = new LinearRing[n-1];
		for (int i = 1; i < n; i++) {
			array = coords.get(i).getAsJsonArray();
			holes[i-1] = factory.createLinearRing(parseCoordinateArray(array));
		}
		return factory.createPolygon(shell, holes);
	}

	static MultiPolygon parseMultiPolygon(JsonArray coords, GeometryFactory factory) {
		int n = coords.size();
		Polygon[]  polygons = new Polygon[n];
		for (int i = 0; i < n; i++) {
			polygons[i] = parsePolygon(coords.get(i).getAsJsonArray(), factory);
		}
		return factory.createMultiPolygon(polygons);
	}

	static GeometryCollection parseGeometryCollection(JsonObject obj, GeometryFactory factory) {
		JsonArray array = obj.get("geometries").getAsJsonArray();
		List<Geometry> geometries = new ArrayList<>();
		for (int i = 0; i < array.size(); i++)
			geometries.add(parseGeometry(array.get(i).getAsJsonObject(), factory));
		return new GeometryCollection(geometries.toArray(Geometry[]::new), factory);
	}

	/**
	 * Write a Geometry as GeoJSON. Note that this does <i>not</i> call beginObject() and endObject() 
	 * so as to provide an opportunity to add additional fields. Rather it only writes the key 
	 * type, coordinates and (for GeometryCollections) geometries fields.
	 * @param geometry
	 * @param out
	 * @param nDecimals
	 * @throws IOException
	 */
	static void writeGeometry(Geometry geometry, JsonWriter out, int nDecimals) throws IOException {
		String type = geometry.getGeometryType();
		out.name("type");
		out.value(type);

		if ("GeometryCollection".equals(geometry.getGeometryType())) {
			out.beginArray();
			for (int i = 0; i < geometry.getNumGeometries(); i++) {
				writeGeometry(geometry.getGeometryN(i), out, nDecimals);
			}
			out.endArray();
		} else {
			out.name("coordinates");
			writeCoordinates(geometry, out, nDecimals);			
		}
	}


	static void writeCoordinates(Geometry geometry, JsonWriter out, int nDecimals) throws IOException {
		if (geometry instanceof Point)
			writeCoordinates((Point)geometry, out, nDecimals);
		else if (geometry instanceof MultiPoint)
			writeCoordinates((MultiPoint)geometry, out, nDecimals);
		else if (geometry instanceof LineString)
			writeCoordinates((LineString)geometry, out, nDecimals);
		else if (geometry instanceof MultiLineString)
			writeCoordinates((MultiLineString)geometry, out, nDecimals);
		else if (geometry instanceof Polygon)
			writeCoordinates((Polygon)geometry, out, nDecimals);
		else if (geometry instanceof MultiPolygon)
			writeCoordinates((MultiPolygon)geometry, out, nDecimals);
		else
			throw new IllegalArgumentException("Unable to write coordinates for geometry type " + geometry.getGeometryType());
	}

	static void writeCoordinates(Point point, JsonWriter out, int nDecimals) throws IOException {
		out.jsonValue(coordinateToString(point.getCoordinate(), nDecimals));
	}

	static void writeCoordinates(MultiPoint multiPoint, JsonWriter out, int nDecimals) throws IOException {
		Coordinate[] coords = multiPoint.getCoordinates();
		out.beginArray();
		for (Coordinate c : coords)
			out.jsonValue(coordinateToString(c, nDecimals));
		out.endArray();
	}

	static void writeCoordinates(LineString lineString, JsonWriter out, int nDecimals) throws IOException {
		Coordinate[] coords = lineString.getCoordinates();
		out.beginArray();
		for (Coordinate c : coords)
			out.jsonValue(coordinateToString(c, nDecimals));
		out.endArray();
	}

	static void writeCoordinates(Polygon polygon, JsonWriter out, int nDecimals) throws IOException {
		out.beginArray();
		writeCoordinates(polygon.getExteriorRing(), out, nDecimals);
		for (int i = 0; i < polygon.getNumInteriorRing(); i++)
			writeCoordinates(polygon.getInteriorRingN(i), out, nDecimals);
		out.endArray();
	}

	static void writeCoordinates(MultiPolygon multiPolygon, JsonWriter out, int nDecimals) throws IOException {
		out.beginArray();
		for (int i = 0; i < multiPolygon.getNumGeometries(); i++)
			writeCoordinates(multiPolygon.getGeometryN(i), out, nDecimals);
		out.endArray();
	}


	static void writeMultiPoint(MultiPoint multiPoint, JsonWriter out, int nDecimals) throws IOException {
		out.name("type");
		out.value("MultiPoint");
		
		out.name("coordinates");
		out.beginArray();
		Coordinate[] coords = multiPoint.getCoordinates();
		for (Coordinate c : coords)
			out.jsonValue(coordinateToString(c, nDecimals));
		out.endArray();
	}

	static void writeLineString(LineString lineString, JsonWriter out, int nDecimals) throws IOException {
		out.name("type");
		out.value("LineString");
		
		out.name("coordinates");
		out.beginArray();
		CoordinateSequence coords = lineString.getCoordinateSequence();
		for (int i = 0; i < coords.size(); i++)
			out.jsonValue(coordinateToString(coords.getX(i), coords.getY(i), nDecimals));
		out.endArray();
	}

	static String coordinateToString(Coordinate coord, int nDecimals) {
		return coordinateToString(coord.x, coord.y, nDecimals);
	}

	static String coordinateToString(double x, double y, int nDecimals) {
		return "[" + GeneralTools.formatNumber(Locale.US, x, nDecimals) + ", "
				+ GeneralTools.formatNumber(Locale.US, y, nDecimals) + "]";		
	}
	
}