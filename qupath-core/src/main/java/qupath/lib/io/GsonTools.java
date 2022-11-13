/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2022 QuPath developers, The University of Edinburgh
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

package qupath.lib.io;

import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.locationtech.jts.geom.Geometry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import qupath.lib.common.ColorTools;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathRootObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassTools;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.interfaces.ROI;

/**
 * Helper class providing Gson instances with type adapters registered to serialize 
 * several key classes.
 * <p>
 * These include:
 * <ul>
 * <li>{@link PathObject}</li>
 * <li>{@link PathClass}</li>
 * <li>{@link ROI}</li>
 * <li>{@link ImagePlane}</li>
 * <li>Java Topology Suite Geometry objects</li>
 * </ul>
 * 
 * @author Pete Bankhead
 *
 */
public class GsonTools {
	
	private static final Logger logger = LoggerFactory.getLogger(GsonTools.class);
	
	private static GsonBuilder builder = new GsonBuilder()
			.serializeSpecialFloatingPointValues()
			.setLenient()
			.registerTypeAdapterFactory(new QuPathTypeAdapterFactory())
			.registerTypeAdapter(AffineTransform.class, AffineTransformTypeAdapter.INSTANCE);
			//.create();
	
	/**
	 * Access the builder used with {@link #getInstance()}.
	 * This makes it possible to register new type adapters if required, which will be used by future Gson instances 
	 * returned by this class.
	 * <p>
	 * <b>Use this with caution!</b> Changes made here impact JSON serialization/deserialization throughout 
	 * the software. Access by be removed or restricted in the future for this reason.
	 * <p>
	 * To create a derived builder that inherits from the default but does not change it, use {@code GsonBuilder.getInstance().newBuilder()}.
	 * 
	 * @return
	 */
	public static GsonBuilder getDefaultBuilder() {
		logger.trace("Requesting GsonBuilder from {}", Thread.currentThread().getStackTrace()[0]);
		return builder;
	}
	
	
	static class QuPathTypeAdapterFactory implements TypeAdapterFactory {

		@Override
		public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
			return getTypeAdaptor((Class<T>)type.getRawType());
		}
		
		@SuppressWarnings("unchecked")
		static <T> TypeAdapter<T> getTypeAdaptor(Class<T> cls) {
			// No point serializing the root object alone - serialize the whole hierarchy instead
			if (PathRootObject.class.isAssignableFrom(cls))
				return (TypeAdapter<T>)QuPathTypeAdapters.PathObjectTypeAdapter.INSTANCE_HIERARCHY;
			
			if (PathObject.class.isAssignableFrom(cls))
				return (TypeAdapter<T>)QuPathTypeAdapters.PathObjectTypeAdapter.INSTANCE;

			if (MeasurementList.class.isAssignableFrom(cls))
				return (TypeAdapter<T>)QuPathTypeAdapters.MeasurementListTypeAdapter.INSTANCE;

//			if (HierarchyFeatureCollection.class.isAssignableFrom(cls))
//				return (TypeAdapter<T>)PathObjectTypeAdapters.PathObjectCollectionTypeAdapter.INSTANCE_HIERARCHY;

			if (FeatureCollection.class.isAssignableFrom(cls))
				return (TypeAdapter<T>)QuPathTypeAdapters.PathObjectCollectionTypeAdapter.INSTANCE;

			if (PathObjectHierarchy.class.isAssignableFrom(cls))
				return (TypeAdapter<T>)QuPathTypeAdapters.HierarchyTypeAdapter.INSTANCE;

			if (ROI.class.isAssignableFrom(cls))
				return (TypeAdapter<T>)ROITypeAdapters.ROI_ADAPTER_INSTANCE;
						
			if (Geometry.class.isAssignableFrom(cls))
				return (TypeAdapter<T>)ROITypeAdapters.GEOMETRY_ADAPTER_INSTANCE;
			
			if (PathClass.class.isAssignableFrom(cls))
				return (TypeAdapter<T>)PathClassTypeAdapter.INSTANCE;

			if (ImagePlane.class.isAssignableFrom(cls))
				return (TypeAdapter<T>)ImagePlaneTypeAdapter.INSTANCE;

			return null;
		}
		
	}
	
	
	/**
	 * Create a {@link TypeAdapterFactory} that is suitable for handling class hierarchies.
	 * This can be used to construct the appropriate subtype when parsing the JSON by using a specific field in the JSON representation.
	 * 
	 * @param <T>
	 * @param baseType the base type, i.e. the class or interface that all types descend from
	 * @param typeFieldName a field name to include within the serialized JSON object to identify the specific type
	 * @return
	 */
	public static <T> SubTypeAdapterFactory<T> createSubTypeAdapterFactory(Class<T> baseType, String typeFieldName) {
		return new SubTypeAdapterFactory<>(baseType, typeFieldName);
	}

	
	/**
	 * A {@link TypeAdapterFactory} that is suitable for handling class hierarchies.
	 * This can be used to construct the appropriate subtype when parsing the JSON.
	 * <p>
	 * This is inspired and influenced by the {@code RuntimeTypeAdapterFactory} class available as part of Gson extras, 
	 * but not the main Gson library 
	 * (https://github.com/google/gson/blob/gson-parent-2.8.6/extras/src/main/java/com/google/gson/typeadapters/RuntimeTypeAdapterFactory.java),
	 * which is Copyright (C) 2011 Google Inc. licensed under Apache License, Version 2.0.
	 * <p>
	 * This behavior of this class differs in several ways:
	 * <ul>
	 * 	<li>it supports alias labels for deserialization, which can be used to help achieve backwards compatibility</li>
	 *  <li>it avoids use of the internal {@code Streams} class for Gson, which complicates modularity</li>
	 *  <li>it does not support a {@code maintainLabel} option (the label field is always removed)</li>
	 * </ul>
	 *
	 * @param <T>
	 */
	public static class SubTypeAdapterFactory<T> implements TypeAdapterFactory {
		
		private static final Logger logger = LoggerFactory.getLogger(SubTypeAdapterFactory.class);
		
		private final Class<?> baseType;
		private final String typeFieldName;
		private final Map<String, Class<?>> labelToSubtype = new LinkedHashMap<>();
		private final Map<Class<?>, String> subtypeToLabel = new LinkedHashMap<>();
		private final Map<String, Class<?>> aliasToSubtype = new LinkedHashMap<>();
		
		private SubTypeAdapterFactory(Class<T> baseType, String typeFieldName) {
			Objects.requireNonNull(baseType, "baseType must not be null!");
			Objects.requireNonNull(typeFieldName, "typeFieldName must not be null!");
			this.typeFieldName = typeFieldName;
			this.baseType = baseType;
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public synchronized <R> TypeAdapter<R> create(Gson gson, TypeToken<R> type) {
			if (!Objects.equals(type.getRawType(), baseType)) {
				return null;
			}
			return (TypeAdapter<R>)new SubTypeAdapter(gson).nullSafe();
		}
		
		private class SubTypeAdapter extends TypeAdapter<T> {
			
			private final Gson gson;
			private final Map<Class<?>, TypeAdapter<?>> subtypeToDelegate = new LinkedHashMap<>();
			
			private SubTypeAdapter(final Gson gson) {
				this.gson = gson;
				for (Map.Entry<String, Class<?>> entry : labelToSubtype.entrySet()) {
					TypeAdapter<?> delegate = gson.getDelegateAdapter(SubTypeAdapterFactory.this, TypeToken.get(entry.getValue()));
					subtypeToDelegate.put(entry.getValue(), delegate);
				}
			}

			@Override
			public void write(JsonWriter out, T value) throws IOException {
				Class<?> srcType = value.getClass();
				String label = subtypeToLabel.get(srcType);
				TypeAdapter<T> delegate = (TypeAdapter<T>)subtypeToDelegate.get(srcType);
				if (delegate == null)
					throw new JsonParseException("Cannot serialize " + baseType + " subtype named " + srcType.getName() +
							"; did you forget to register a subtype?");
				JsonObject jsonObject = delegate.toJsonTree(value).getAsJsonObject();
				if (jsonObject.has(typeFieldName))
					throw new JsonParseException("Cannot serialize " + srcType.getName() + 
							" because it already defines a field named " + typeFieldName);
				JsonObject clone = new JsonObject();
				clone.add(typeFieldName, new JsonPrimitive(label));
				for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
					clone.add(entry.getKey(), entry.getValue());
				}
				logger.trace("Writing {} for {} ", label, value);
				gson.toJson(clone, out);
			}

			@Override
			public T read(JsonReader in) throws IOException {
				JsonElement jsonElement = gson.fromJson(in, JsonElement.class);
				JsonElement labelElement = jsonElement.getAsJsonObject().remove(typeFieldName);
				if (labelElement == null)
					throw new JsonParseException("Cannot deserialize " + baseType + " because there is no field named " + typeFieldName);
				String label = labelElement.getAsString();
				Class<?> subtype = labelToSubtype.get(label);
				if (subtype == null)
					subtype = aliasToSubtype.get(label);
				TypeAdapter<T> delegate = (TypeAdapter<T>)subtypeToDelegate.get(subtype);
				if (delegate == null)
					throw new JsonParseException("Cannot deserialize " + baseType + " subtype named " + label);
				logger.trace("Reading {} for {} ", label, baseType);
				return delegate.fromJsonTree(jsonElement);
			}
			
		}
		
		/**
		 * Register a subtype using a custom label.
		 * This allows objects to serialized to JSON and deserialized while retaining the same class.
		 * 
		 * @param subtype the subtype to register
		 * @param label the label used to identify objects of this subtype; this must be unique
		 * @return this {@link SubTypeAdapterFactory}
		 * @see #registerSubtype(Class, String)
		 */
		public synchronized SubTypeAdapterFactory<T> registerSubtype(Class<? extends T> subtype, String label) {
			Objects.requireNonNull(subtype, "subtype must not be null!");
			Objects.requireNonNull(label, "label must not be null!");
			if (labelToSubtype.containsKey(label))
				throw new IllegalArgumentException("Label " + label + " is already assigned! Did you want to register an alias instead?");
			labelToSubtype.put(label, subtype);
			subtypeToLabel.put(subtype, label);
			return this;
		}
		
		/**
		 * Register an alias label for a specified subtype.
		 * This can be used during deserialization for backwards compatibility, but will not be used 
		 * for serializing new objects.
		 * 
		 * @param subtype the subtype to register
		 * @param alias the alias used as an alternative label to identify objects of this subtype
		 * @return this {@link SubTypeAdapterFactory}
		 * @see #registerSubtype(Class, String)
		 */
		public synchronized SubTypeAdapterFactory<T> registerAlias(Class<? extends T> subtype, String alias) {
			Objects.requireNonNull(subtype, "subtype must not be null!");
			Objects.requireNonNull(alias, "label must not be null!");
			if (aliasToSubtype.containsKey(alias)) {
				if (Objects.equals(aliasToSubtype.get(alias), subtype))
					return this;
				logger.warn("Alias {} is already assigned to subtype {}, request will be ignored", alias, subtype);
			}
			aliasToSubtype.put(alias, subtype);
			return this;
		}
		
		/**
		 * Register a subtype using the default label (the simple name of the class).
		 * This allows objects to serialized to JSON and deserialized while retaining the same class.
		 * 
		 * @param subtype the subtype to register
		 * @return this {@link SubTypeAdapterFactory}
		 * @see #registerSubtype(Class, String)
		 */
		public synchronized SubTypeAdapterFactory<T> registerSubtype(Class<? extends T> subtype) {
			return registerSubtype(subtype, subtype.getSimpleName());
		}
		
	}
	
	
	/**
	 * Get default Gson, capable of serializing/deserializing some key QuPath classes.
	 * @return
	 * 
	 * @see #getInstance(boolean)
	 */
	public static Gson getInstance() {
		return builder.create();
	}
	
	/**
	 * Get default Gson, optionally with pretty printing enabled.
	 * 
	 * @param pretty if true, write using pretty-printing (i.e. more whitespace for formatting)
	 * @return
	 * 
	 * @see #getInstance()
	 */
	public static Gson getInstance(boolean pretty) {
		if (pretty)
			return getInstance().newBuilder().setPrettyPrinting().create();
		return getInstance();
	}
	
	/**
	 * TypeAdapter for PathClass objects, ensuring each is a singleton.
	 */
	static class PathClassTypeAdapter extends TypeAdapter<PathClass> {
		
		static PathClassTypeAdapter INSTANCE = new PathClassTypeAdapter();
		
		private static Gson gson = new Gson();

		// TODO: Consider writing just the toString() representation & ensure recreate-able from that (but lacking color?)
		@Override
		public void write(JsonWriter out, PathClass value) throws IOException {
			// Use a proxy object for easier use elsewhere
			if (value == null || value == PathClass.NULL_CLASS) {
				// Write in the default way
				gson.toJson(value, PathClass.class, out);				
			} else {
				out.beginObject();
				var names = PathClassTools.splitNames(value);
				if (names.size() == 1) {
					out.name("name");
					out.value(names.get(0));
				} else {
					out.name("names");
					out.beginArray();
					for (var name : names)
						out.value(name);					
					out.endArray();
				}
				var color = value.getColor();
				if (color != null) {
					out.name("color");
					var alpha = ColorTools.alpha(color);
					try {
						if (alpha != 0 && alpha != 255)
							out.jsonValue(String.format("[%d, %d, %d, %d]", ColorTools.red(color), ColorTools.green(color), ColorTools.blue(color), ColorTools.alpha(color)));
						else
							out.jsonValue(String.format("[%d, %d, %d]", ColorTools.red(color), ColorTools.green(color), ColorTools.blue(color)));
					} catch (UnsupportedOperationException e) {
						// TODO: Consider not trying to write json value, since it isn't always supported
						out.beginArray();
						out.value(ColorTools.red(color));
						out.value(ColorTools.green(color));
						out.value(ColorTools.blue(color));
						if (alpha != 0 && alpha != 255)
							ColorTools.alpha(color);
						out.endArray();
					}
				}
				out.endObject();
				
//				// Write in a simplified way, with toString() and an array of RGB values
//				var proxy = new PathClassProxy();
//				if (names.size() == 1)
//					proxy.name = names.get(0);
//				else
//					proxy.names = names;
//				proxy.color = rgbToArray(value.getColor());
//				gson.toJson(proxy, PathClassProxy.class, out);	
			}
		}

		@Override
		public PathClass read(JsonReader in) throws IOException {
			// Check what kind of representation we have
			var token = in.peek();
			
			// Handle no (null) classification
			if (token == JsonToken.NULL)
				return null;
			
			// Accept a classification just from a string (i.e. no color specified)
			if (token == JsonToken.STRING)
				return PathClass.fromString(in.nextString());
			
			// Handle objects
			if (token == JsonToken.BEGIN_OBJECT) {
				JsonObject pathClassObject = gson.fromJson(in, JsonObject.class);	
				// Check if we have just serialized in the default way (with the usual private field name for color)
				// This also should be used with PathClass.NULL_CLASS (which has an empty object)
				// (It was the method used before v0.4.0)
				if (pathClassObject.size() == 0 || pathClassObject.has("colorRGB") || pathClassObject.has("parentClass")) {
					// Read in the default way, then replace with a singleton instance
					PathClass pathClass = gson.fromJson(pathClassObject, PathClass.class);
					return PathClass.getSingleton(pathClass);					
				}
				// Check if we have a proxy object
				if ((pathClassObject.has("name") || pathClassObject.has("names")) && pathClassObject.has("color")) {
					PathClassProxy proxy = gson.fromJson(pathClassObject, PathClassProxy.class);
					return proxy.getPathClass();
				}
			}
			throw new JsonParseException("Unable to parse PathClass from " + in);
		}
		
		
		private static class PathClassProxy {
			
			private List<String> names;
			private String name;
			private int[] color;
			
			private PathClass getPathClass() {
				Integer rgb = color == null ? null : arrayToRgb(color);
				if (name == null) {
					if (names == null || names.isEmpty())
						return null;
					return PathClass.fromCollection(names, rgb);
				}
				return PathClass.fromString(name, rgb);
			}
			
		}
		

	}
	
	
//	/**
//	 * Convert packed RGB value to an array
//	 * @param rgb
//	 * @return
//	 */
//	private static int[] rgbToArray(Integer rgb) {
//		if (rgb == null)
//			return new int[0];
//		else
//			return new int[] {
//				ColorTools.red(rgb.intValue()),
//				ColorTools.green(rgb.intValue()),
//				ColorTools.blue(rgb.intValue())
//			};
//	}
	
	/**
	 * Convert array to a packed RGB value
	 * @param rgb
	 * @return
	 */
	private static Integer arrayToRgb(int[] rgb) {
		if (rgb == null || rgb.length == 0)
			return null;
		if (rgb.length == 1)
			return rgb[0];
		if (rgb.length == 3)
			return ColorTools.packRGB(rgb[0], rgb[1], rgb[2]);
		if (rgb.length == 4)
			// Alpha last in the array!
			return ColorTools.packARGB(rgb[3], rgb[0], rgb[1], rgb[2]);
		throw new IllegalArgumentException("RGB array should have length 0, 1, 3 or 4 - not " + rgb.length);
	}
	
	
	
	static class AffineTransformTypeAdapter extends TypeAdapter<AffineTransform> {
		
		static AffineTransformTypeAdapter INSTANCE = new AffineTransformTypeAdapter();
		
		private static Gson gson = new Gson();

		@Override
		public void write(JsonWriter out, AffineTransform value) throws IOException {
			gson.toJson(new AffineTransformProxy(value), AffineTransformProxy.class, out);
		}

		@Override
		public AffineTransform read(JsonReader in) throws IOException {
			AffineTransformProxy proxy = gson.fromJson(in, AffineTransformProxy.class);
			var transform = new AffineTransform();
			proxy.fill(transform);
			return transform;
		}
		
		static class AffineTransformProxy {
			
			public final double m00, m10, m01, m11, m02, m12;
			
			AffineTransformProxy(AffineTransform transform) {
				double[] flatmatrix = new double[6];
				transform.getMatrix(flatmatrix);
		        m00 = flatmatrix[0];
		        m10 = flatmatrix[1];
		        m01 = flatmatrix[2];
		        m11 = flatmatrix[3];
	            m02 = flatmatrix[4];
	            m12 = flatmatrix[5];
			}
			
			void fill(AffineTransform transform) {
				transform.setTransform(m00, m10, m01, m11, m02, m12);
			}
			
		}

	}
	
	
	/**
	 * TypeAdapter for ImagePlane objects, ensuring each is a singleton.
	 */
	static class ImagePlaneTypeAdapter extends TypeAdapter<ImagePlane> {
		
		static ImagePlaneTypeAdapter INSTANCE = new ImagePlaneTypeAdapter();

		@Override
		public void write(JsonWriter out, ImagePlane plane) throws IOException {
			out.beginObject();
			out.name("c");
			out.value(plane.getC());
			out.name("z");
			out.value(plane.getZ());
			out.name("t");
			out.value(plane.getT());
			out.endObject();
		}

		@Override
		public ImagePlane read(JsonReader in) throws IOException {
			in.beginObject();
			
			ImagePlane plane = ImagePlane.getDefaultPlane();
			int c = plane.getC();
			int z = plane.getZ();
			int t = plane.getT();
			
			while (in.hasNext()) {
				switch (in.nextName()) {
				case "c":
					c = in.nextInt();
					break;
				case "z":
					z = in.nextInt();
					break;
				case "t":
					t = in.nextInt();
					break;
				}
			}
			in.endObject();
			return ImagePlane.getPlaneWithChannel(c, z, t);
		}
		
	}

	
}