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

package qupath.lib.io;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import qupath.lib.common.ColorTools;
import qupath.lib.io.GsonTools.PathClassTypeAdapter;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.measurements.MeasurementList.MeasurementListType;
import qupath.lib.measurements.MeasurementListFactory;
import qupath.lib.objects.MetadataStore;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.PathRootObject;
import qupath.lib.objects.PathTileObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.roi.interfaces.ROI;

class PathObjectTypeAdapters {
	
	static Gson gson = new GsonBuilder()
			.setLenient()
			.create();
		
	static class PathObjectTypeAdapterFactory implements TypeAdapterFactory {

		@Override
		public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
			if (Collection.class.isAssignableFrom(type.getRawType())) {
				System.err.println(type.getType().getTypeName());
			}
			return null;
		}
		
	}
	
	static class FeatureCollection {
		
		private Collection<? extends PathObject> pathObjects;
		
		FeatureCollection(Collection<? extends PathObject> pathObjects) {
			this.pathObjects = pathObjects;
		}
		
		public Collection<? extends PathObject> getPathObjects() {
			return pathObjects;
		}
		
	}
	
	static class PathObjectCollection extends AbstractCollection<PathObject> {
		
		private List<PathObject> pathObjects = new ArrayList<>();
		
		PathObjectCollection(Collection<? extends PathObject> pathObjects) {
			this.pathObjects.addAll(pathObjects);
		}

		@Override
		public Iterator<PathObject> iterator() {
			return pathObjects.iterator();
		}

		@Override
		public int size() {
			return pathObjects.size();
		}
		
	}
	
	static class ROICollection extends AbstractCollection<ROI> {
		
		private List<ROI> rois = new ArrayList<>();
		
		ROICollection(Collection<? extends ROI> rois) {
			this.rois.addAll(rois);
		}

		@Override
		public Iterator<ROI> iterator() {
			return rois.iterator();
		}

		@Override
		public int size() {
			return rois.size();
		}
		
	}
	
	
	static class PathObjectCollectionTypeAdapter extends TypeAdapter<FeatureCollection> {
		
		static PathObjectCollectionTypeAdapter INSTANCE = new PathObjectCollectionTypeAdapter();
		
		static Type TYPE = new TypeToken<Collection<PathObject>>() {}.getType();	

		@Override
		public void write(JsonWriter out, FeatureCollection value) throws IOException {
			out.beginObject();
			
			out.name("type");
			out.value("FeatureCollection");

			out.name("features");
			out.beginArray();
			for (PathObject pathObject : value.getPathObjects())
				PathObjectTypeAdapter.INSTANCE.write(out, pathObject);
			out.endArray();

			out.endObject();
		}

		@Override
		public FeatureCollection read(JsonReader in) throws IOException {
			List<PathObject> list = new ArrayList<>();
			
			JsonObject obj = gson.fromJson(in, JsonObject.class);
			if (obj.has("features") && obj.get("features").isJsonArray()) {
				JsonArray array = obj.get("features").getAsJsonArray();
				for (JsonElement element : array) {
					list.add(PathObjectTypeAdapter.INSTANCE.fromJsonTree(element));
				}
			}
			return new FeatureCollection(list);
			
//			in.beginObject();
//			
//			String name = in.nextName(); // type
//			String value = in.nextString();
//			
//			in.nextName();
//			in.beginArray();
//			List<PathObject> list = new ArrayList<>();
//			while (in.hasNext()) {
//				PathObject pathObject = PathObjectTypeAdapter.INSTANCE.read(in);
//				list.add(pathObject);
//			}
//			in.endArray();
//
//			in.endObject();

//			return list;
		}
		
	}
	
	
	static class PathObjectTypeAdapter extends TypeAdapter<PathObject> {
		
		private final static Logger logger = LoggerFactory.getLogger(PathObjectTypeAdapter.class);
		
		static PathObjectTypeAdapter INSTANCE = new PathObjectTypeAdapter();
		
		/**
		 * In v0.2 we unwisely stored object type in an "id" property.
		 */
		private static Collection<String> LEGACY_TYPE_IDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
				"PathTileObject",
				"PathCellObject",
				"TMACoreObject",
				"PathDetectionObject",
				"PathRootObject",
				"PathAnnotationObject"
				)));
		
		private static Map<Class<?>, String> MAP_TYPES = Map.of(
				PathTileObject.class, "tile",
				PathCellObject.class, "cell",
				PathDetectionObject.class, "detection",
				PathAnnotationObject.class, "annotation",
				TMACoreObject.class, "tma_core",
				PathRootObject.class, "root"
				);
		
		
		private boolean flattenProperties = false;

		@Override
		public void write(JsonWriter out, PathObject value) throws IOException {
			
			out.beginObject();
			
			out.name("type");
			out.value("Feature");
			
			// Used in v0.2 (unwisely)
//			out.name("id");
//			out.value(value.getClass().getSimpleName());		
	
			// TODO: Write cell objects as a Geometry collection to include the nucleus as well
			out.name("geometry");
			ROITypeAdapters.ROI_ADAPTER_INSTANCE.write(out, value.getROI());
	
			if (value instanceof PathCellObject) {
				ROI roiNucleus = ((PathCellObject)value).getNucleusROI();
				if (roiNucleus != null) {
					out.name("nucleusGeometry");
					ROITypeAdapters.ROI_ADAPTER_INSTANCE.write(out, roiNucleus);				
				}
			}
			
			out.name("properties");
			out.beginObject();
			
			String objectType = MAP_TYPES.getOrDefault(value.getClass(), null);
			if (objectType != null) {
				out.name("object_type");
				out.value(objectType);
			} else {
				logger.warn("Unknown object type {}", value.getClass().getSimpleName());
			}
			
			String name = value.getName();
			if (name != null) {
				out.name("name");
				out.value(name);
			}
			
			Integer color = value.getColorRGB();
			if (color != null) {
				out.name("color");
				out.beginArray();
				out.value(ColorTools.red(color));
				out.value(ColorTools.green(color));
				out.value(ColorTools.blue(color));
				out.endArray();
			}
			
			PathClass pathClass = value.getPathClass();
			if (pathClass != null) {
				out.name("classification");
				PathClassTypeAdapter.INSTANCE.write(out, pathClass);
			}
			
			out.name("isLocked");
			out.value(value.isLocked());
			
			if (value instanceof TMACoreObject) {
				out.name("isMissing");
				out.value(((TMACoreObject)value).isMissing());
			}
			
			MeasurementList measurements = value.getMeasurementList();
			if (flattenProperties) {
				// Add measurements
				if (!measurements.isEmpty()) {
					out.name("Measurement count");
					out.value(measurements.size());
					for (int i = 0; i < measurements.size(); i++) {
						out.name(measurements.getMeasurementName(i));
						out.value(measurements.getMeasurementValue(i));
					}
				}
				if (value instanceof MetadataStore) {
					MetadataStore store = (MetadataStore)value;
					Set<String> keys = store.getMetadataKeys();
					if (!keys.isEmpty()) {
						out.name("Metadata count");
						out.value(keys.size());
						for (String key : keys) {
							out.name(key);
							out.value(store.getMetadataString(key));
						}
					}
				}
			} else {
				if (!measurements.isEmpty()) {
					out.name("measurements");
					MeasurementListTypeAdapter.INSTANCE.write(out, measurements);
				}
				
				if (value instanceof MetadataStore) {
					MetadataStore store = (MetadataStore)value;
					Map<String, String> map = store.getMetadataMap();
					if (!map.isEmpty()) {
						out.name("metadata");
						gson.toJson(map, Map.class, out);
					}
				}
			}
			
			out.endObject();
	
			out.endObject();
		}
	
		@Override
		public PathObject read(JsonReader in) throws IOException {
			
			JsonObject obj = gson.fromJson(in, JsonObject.class);
						
			// Object type (annotation, detection etc.)
			String type = "unknown";
			
			// Get an ID
			String id = null;
			if (obj.has("id")) {
				id = obj.get("id").getAsString();
				// In v0.2, we (unwisely...) stored the type in an ID
				if (LEGACY_TYPE_IDS.contains(id))
					type = id;
			}
			
			ROI roi = ROITypeAdapters.ROI_ADAPTER_INSTANCE.fromJsonTree(obj.get("geometry"));
			
			PathClass pathClass = null;
			
			boolean isMissing = false;
			boolean isLocked = false;
			String name = null;
			Integer color = null;
			
			MeasurementList measurementList = null;
			JsonObject metadata = null;
			
			if (obj.has("properties")) {
				JsonObject properties = obj.get("properties").getAsJsonObject();
				if (properties.has("name")) {
					name = properties.get("name").getAsString();
				}
				if (properties.has("color")) {
					var colorObj = properties.get("color");
					if (colorObj.isJsonPrimitive())
						color = colorObj.getAsInt();
					else if (colorObj.isJsonArray()) {
						var colorArray = colorObj.getAsJsonArray();
						if (colorArray.size() == 3)
							color = ColorTools.packRGB(
									colorArray.get(0).getAsInt(),
									colorArray.get(1).getAsInt(),
									colorArray.get(2).getAsInt()
									);
					}
				}
				if (properties.has("classification")) {
					pathClass = PathClassTypeAdapter.INSTANCE.fromJsonTree(properties.get("classification"));
				}
				if (properties.has("isMissing") && properties.get("isMissing").isJsonPrimitive()) {
					isMissing = properties.get("isMissing").getAsBoolean();
				}
				if (properties.has("isLocked") && properties.get("isLocked").isJsonPrimitive()) {
					isLocked = properties.get("isLocked").getAsBoolean();
				}
				if (properties.has("measurements") && properties.get("measurements").isJsonArray()) {
					measurementList = MeasurementListTypeAdapter.INSTANCE.fromJsonTree(properties.get("measurements"));
				}
				if (properties.has("metadata") && properties.get("metadata").isJsonObject()) {
					metadata = properties.get("metadata").getAsJsonObject();
				}
				if (properties.has("object_type")) {
					type = properties.get("object_type").getAsString();
				} else if (properties.has("type")) {
					// Allow 'type' to be used as an alias
					type = properties.get("type").getAsString();
				}
			}
			ROI roiNucleus = null;
			if (obj.has("nucleusGeometry")) {
				roiNucleus = ROITypeAdapters.ROI_ADAPTER_INSTANCE.fromJsonTree(obj.get("nucleusGeometry"));
			}
			
			PathObject pathObject = null;
			switch (type) {
			case ("PathTileObject"):
			case ("tile"):
				pathObject = PathObjects.createTileObject(roi, pathClass, measurementList);
				break;
			case ("PathCellObject"):
			case ("cell"):
				pathObject = PathObjects.createCellObject(roi, roiNucleus, pathClass, measurementList);
				break;
			case ("TMACoreObject"):
			case ("tma_core"):
				pathObject = PathObjects.createTMACoreObject(roi.getBoundsX(), roi.getBoundsY(), roi.getBoundsWidth(), roi.getBoundsHeight(), isMissing);
				break;
			case ("PathDetectionObject"):
			case ("detection"):
				pathObject = PathObjects.createDetectionObject(roi, pathClass, measurementList);
				break;
			case ("PathRootObject"):
			case ("root"):
				pathObject = new PathRootObject();
				break;
			case ("PathAnnotationObject"):
			case ("annotation"):
			case ("unknown"):
				// Default is to create an annotation
				pathObject = PathObjects.createAnnotationObject(roi, pathClass);
				break;
			default:
				// Should be called if the type has been specified as *something*, but not something we recognize
				logger.warn("Unknown object type {}, I will create an annotation", type);
				pathObject = PathObjects.createAnnotationObject(roi, pathClass);
			}
			if (name != null)
				pathObject.setName(name);
			
			if (color != null)
				pathObject.setColorRGB(color);
			
			if (isLocked)
				pathObject.setLocked(isLocked);
			
			if (metadata != null && pathObject instanceof MetadataStore) {
				for (Entry<String, JsonElement> entry : metadata.entrySet())
					if (entry.getValue().isJsonPrimitive())
						((MetadataStore)pathObject).putMetadataValue(entry.getKey(), entry.getValue().getAsString());
			}
			
			return pathObject;
		}
		
	}
	
	
	static class MeasurementListTypeAdapter extends TypeAdapter<MeasurementList> {
		
		static MeasurementListTypeAdapter INSTANCE = new MeasurementListTypeAdapter();

		@Override
		public void write(JsonWriter out, MeasurementList value) throws IOException {
			out.beginArray();
			for (int i = 0; i < value.size(); i++) {
				out.beginObject();
				out.name("name");
				out.value(value.getMeasurementName(i));
				
				out.name("value");
				out.value(value.getMeasurementValue(i));
				
				out.endObject();
			}
			out.endArray();
		}

		@Override
		public MeasurementList read(JsonReader in) throws IOException {
			JsonArray array = gson.fromJson(in, JsonArray.class);
			MeasurementList list = MeasurementListFactory.createMeasurementList(array.size(), MeasurementListType.DOUBLE);
			for (int i = 0; i < array.size(); i++) {
				JsonObject obj = array.get(i).getAsJsonObject();
				list.addMeasurement(obj.get("name").getAsString(), obj.get("value").getAsDouble());
			}
			list.close();
			return list;
		}
		
	}
	
	
}