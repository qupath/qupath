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
import java.util.stream.Collectors;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import qupath.lib.common.ColorTools;
import qupath.lib.common.LogTools;
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
import qupath.lib.objects.hierarchy.DefaultTMAGrid;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.TMAGrid;
import qupath.lib.roi.interfaces.ROI;

class QuPathTypeAdapters {
	
	static Gson gson = new GsonBuilder()
			.setLenient()
			.serializeSpecialFloatingPointValues()
			.create();
		
		
	
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
	
	
	static class HierarchyTypeAdapter extends TypeAdapter<PathObjectHierarchy> {
		
		private static final Logger logger = LoggerFactory.getLogger(HierarchyTypeAdapter.class);
		
		static HierarchyTypeAdapter INSTANCE = new HierarchyTypeAdapter();

		@Override
		public void write(JsonWriter out, PathObjectHierarchy value) throws IOException {
			
			if (value == null && out.getSerializeNulls()) {
				out.nullValue();
				return;
			}
			
			out.beginObject();
			
			out.name("root");
			PathObjectTypeAdapter.INSTANCE_HIERARCHY.write(out, value.getRootObject());
			
			// Store TMA core IDs only, to avoid duplicating the objects themselves
			var tmaGrid = value.getTMAGrid();
			if (tmaGrid != null) {
				out.name("tmaGrid");
				var proxy = new TMAGridProxy(tmaGrid);
				gson.toJson(proxy, proxy.getClass(), out);
			}
			
			out.endObject();
		}

		@Override
		public PathObjectHierarchy read(JsonReader in) throws IOException {
			
			var token = in.peek();
			if (token == JsonToken.NULL)
				return null;
			if (token != JsonToken.BEGIN_OBJECT)
				throw new IOException("Expected BEGIN_OBJECT but found " + token);
			
			var hierarchy = new PathObjectHierarchy();
			
			JsonObject obj = gson.fromJson(in, JsonObject.class);
			if (obj.has("root")) {
				PathObject rootObject = PathObjectTypeAdapter.INSTANCE_HIERARCHY.fromJsonTree(obj.get("root"));
				hierarchy.getRootObject().addChildObjects(new ArrayList<>(rootObject.getChildObjects()));
			}
			if (obj.has("tmaGrid")) {
				TMAGridProxy proxy = gson.fromJson(obj.get("tmaGrid").getAsJsonObject(), TMAGridProxy.class);
				
				var allCores = hierarchy.getAllObjects(false).stream().collect(Collectors.toMap(c -> c.getID(), c -> c));
				var sortedCores = proxy.cores
						.stream()
						.map(i -> (TMACoreObject)allCores.getOrDefault(i, null))
						.filter(c -> c != null)
						.collect(Collectors.toList());
				
				if (sortedCores.size() == proxy.cores.size()) {
					var tmaGrid = DefaultTMAGrid.create(sortedCores, proxy.width);
					hierarchy.setTMAGrid(tmaGrid);
				} else {
					logger.warn("Cannot deserialize TMA grid - matched {}/{} cores", sortedCores.size(), allCores.size());
				}
			}
			return hierarchy;
		}

		
	}
	
	
	@SuppressWarnings("unused")
	static class TMAGridProxy {
		
		private int width;
		private List<UUID> cores;
		
		TMAGridProxy(TMAGrid grid) {
			this.width = grid.getGridWidth();
			cores = grid.getTMACoreList().stream().map(c -> c.getID()).collect(Collectors.toList());
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

			var adapter = value.getIncludeChildren() ? PathObjectTypeAdapter.INSTANCE_HIERARCHY : PathObjectTypeAdapter.INSTANCE;

			out.name("features");
			out.beginArray();
			for (PathObject pathObject : value.getPathObjects()) {
				adapter.write(out, pathObject);
			}
			out.endArray();

			out.endObject();
		}

		@Override
		public FeatureCollection read(JsonReader in) throws IOException {
			List<PathObject> list = new ArrayList<>();
			
			JsonArray array = null;
			var token = in.peek();
			if (token == JsonToken.BEGIN_ARRAY) {
				array = gson.fromJson(in, JsonArray.class);
			} else {
				JsonObject obj = gson.fromJson(in, JsonObject.class);
				if (obj.has("features") && obj.get("features").isJsonArray()) {
					array = obj.get("features").getAsJsonArray();
				}
			}
			
			if (array != null) {
				for (JsonElement element : array) {
					list.add(PathObjectTypeAdapter.INSTANCE.fromJsonTree(element));
				}
			}
			return FeatureCollection.wrap(list);
			
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
		
		private static final Logger logger = LoggerFactory.getLogger(PathObjectTypeAdapter.class);
		
		/**
		 * Get an instance that serializes a 'flat' object, ignoring child objects
		 */
		static PathObjectTypeAdapter INSTANCE = new PathObjectTypeAdapter(false);
		
		/**
		 * Get an instance that serializes the object hierarchy - which means including child objects
		 */
		static PathObjectTypeAdapter INSTANCE_HIERARCHY = new PathObjectTypeAdapter(true);
		
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
				TMACoreObject.class, "tmaCore",
				PathRootObject.class, "root"
				);
		
		
		private boolean flattenProperties = false;
		
		private boolean doHierarchy = false;
		
		private PathObjectTypeAdapter(boolean doHierarchy) {
			this.doHierarchy = doHierarchy;
		}
		

		@Override
		public void write(JsonWriter out, PathObject value) throws IOException {
			
			out.beginObject();
			
			out.name("type");
			out.value("Feature");
			
			// Used in v0.2 (unwisely)
//			out.name("id");
//			out.value(value.getClass().getSimpleName());		
	
			// Since v0.4.0
			var id = value.getID();
			if (id != null) {
				out.name("id");
				out.value(id.toString());		
			}

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
//				out.name("object_type"); // Switch to camelCase consistently
				out.name("objectType");
				out.value(objectType);
			} else {
				logger.warn("Unknown object type {}", value.getClass().getSimpleName());
			}
			
			String name = value.getName();
			if (name != null) {
				out.name("name");
				out.value(name);
			}
			
			Integer color = value.getColor();
			if (color != null) {
				out.name("color");
				out.jsonValue(String.format("[%d, %d, %d]", ColorTools.red(color), ColorTools.green(color), ColorTools.blue(color)));
//				out.beginArray();
//				out.value(ColorTools.red(color));
//				out.value(ColorTools.green(color));
//				out.value(ColorTools.blue(color));
//				out.endArray();
			}
			
			// Write classification
			PathClass pathClass = value.getPathClass();
			if (pathClass != null) {
				out.name("classification");
				PathClassTypeAdapter.INSTANCE.write(out, pathClass);
			}
			
			// Write locked status only if locked
			if (value.isLocked()) {
				out.name("isLocked");
				out.value(true);
			}
			
			if (value instanceof TMACoreObject) {
				out.name("isMissing");
				out.value(((TMACoreObject)value).isMissing());
			}
			
			MeasurementList measurements = value.getMeasurementList();
			if (flattenProperties) {
				// Flattening properties probably not a good idea!
				
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
			
			if (doHierarchy && value.hasChildObjects()) {
				out.name("childObjects");
				out.beginArray();
				for (var child : value.getChildObjectsAsArray()) {
					write(out, child);
//					gson.toJson(child, PathObject.class, out);
				}
				out.endArray();
			}
			
			out.endObject();
	
			out.endObject();
		}
	
		@Override
		public PathObject read(JsonReader in) throws IOException {
			
			JsonObject obj = gson.fromJson(in, JsonObject.class);
			
			return parseObject(obj);
		}
			
			
		private PathObject parseObject(JsonObject obj) {
						
			// Object type (annotation, detection etc.)
			String type = "unknown";
			
			// Get an ID
			String id = null;
			UUID uuid = null;
			if (obj.has("id")) {
				id = obj.get("id").getAsString();
				// In v0.2, we (unwisely...) stored the type in an ID
				if (LEGACY_TYPE_IDS.contains(id))
					type = id;
				else {
					// From v0.4, we use UUID
					try {
						uuid = UUID.fromString(id);
						id = null;
					} catch (Exception e) {
						logger.debug("Invalid ID found in GeoJSON");
					}
				}
			}
			
			ROI roi = ROITypeAdapters.ROI_ADAPTER_INSTANCE.fromJsonTree(obj.get("geometry"));
			
			PathClass pathClass = null;
			
			boolean isMissing = false;
			boolean isLocked = false;
			String name = null;
			Integer color = null;
			List<PathObject> childObjects = null;
			
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
				if (properties.has("measurements") && (properties.get("measurements").isJsonArray() || properties.get("measurements").isJsonObject())) {
					measurementList = MeasurementListTypeAdapter.INSTANCE.fromJsonTree(properties.get("measurements"));
				}
				if (properties.has("metadata") && properties.get("metadata").isJsonObject()) {
					metadata = properties.get("metadata").getAsJsonObject();
				}
				if (properties.has("objectType")) {
					type = properties.get("objectType").getAsString();					
				} else if (properties.has("object_type")) {
					LogTools.warnOnce(logger, "PathObject using 'object_type' property - this should be updated to 'objectType'");
					type = properties.get("object_type").getAsString();
				} else if (properties.has("type")) {
					// Allow 'type' to be used as an alias
					type = properties.get("type").getAsString();
				}
				var childObjectsName = properties.has("childObjects") ? "childObjects" : "children";
				if (properties.has(childObjectsName) && properties.get(childObjectsName).isJsonArray()) {
					var children = properties.get(childObjectsName).getAsJsonArray();
					childObjects = new ArrayList<>();
					for (var child : children) {
						var childObject = parseObject(child.getAsJsonObject());
						if (childObject != null)
							childObjects.add(childObject);
					}
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
				pathObject = PathObjects.createTileObject(roi);
				break;
			case ("PathCellObject"):
			case ("cell"):
				pathObject = PathObjects.createCellObject(roi, roiNucleus, null, null);
				break;
			case ("TMACoreObject"):
			case ("tmaCore"):
			case ("tma_core"):
				pathObject = PathObjects.createTMACoreObject(roi.getBoundsX(), roi.getBoundsY(), roi.getBoundsWidth(), roi.getBoundsHeight(), isMissing);
				break;
			case ("PathDetectionObject"):
			case ("detection"):
				pathObject = PathObjects.createDetectionObject(roi);
				break;
			case ("PathRootObject"):
			case ("root"):
				pathObject = new PathRootObject();
				break;
			case ("PathAnnotationObject"):
			case ("annotation"):
			case ("unknown"):
				// Default is to create an annotation
				pathObject = PathObjects.createAnnotationObject(roi);
				break;
			default:
				// Should be called if the type has been specified as *something*, but not something we recognize
				logger.warn("Unknown object type {}, I will create an annotation", type);
				pathObject = PathObjects.createAnnotationObject(roi);
			}
			if (uuid != null)
				pathObject.setID(uuid);
			
			if (measurementList != null && !measurementList.isEmpty()) {
				try (var ml = pathObject.getMeasurementList()) {
					ml.putAll(measurementList);
				}
			}
			if (pathClass != null)
				pathObject.setPathClass(pathClass);
			
			if (name != null)
				pathObject.setName(name);
			
			if (color != null)
				pathObject.setColor(color);
			
			if (isLocked && !pathObject.isRootObject())
				pathObject.setLocked(isLocked);
			
			if (metadata != null && pathObject instanceof MetadataStore) {
				for (Entry<String, JsonElement> entry : metadata.entrySet())
					if (entry.getValue().isJsonPrimitive())
						((MetadataStore)pathObject).putMetadataValue(entry.getKey(), entry.getValue().getAsString());
			}
			
			if (childObjects != null)
				pathObject.addChildObjects(childObjects);
			
			return pathObject;
		}
		
	}
	
	
	static class MeasurementListTypeAdapter extends TypeAdapter<MeasurementList> {
		
		static MeasurementListTypeAdapter INSTANCE = new MeasurementListTypeAdapter();

		@Override
		public void write(JsonWriter out, MeasurementList value) throws IOException {
			out.beginObject();
			if (value != null) {
				for (var entry : value.asMap().entrySet()) {
					out.name(entry.getKey());
					out.value(entry.getValue());
				}				
			}
			out.endObject();
			
			// Approach used before v0.4.0
//			out.beginArray();
//			for (int i = 0; i < value.size(); i++) {
//				out.beginObject();
//				out.name("name");
//				out.value(value.getMeasurementName(i));
//				
//				out.name("value");
//				out.value(value.getMeasurementValue(i));
//				
//				out.endObject();
//			}
//			out.endArray();
		}

		@Override
		public MeasurementList read(JsonReader in) throws IOException {
			var token = in.peek();
			if (token == JsonToken.BEGIN_ARRAY) {
				JsonArray array = gson.fromJson(in, JsonArray.class);
				MeasurementList list = MeasurementListFactory.createMeasurementList(array.size(), MeasurementListType.DOUBLE);
				for (int i = 0; i < array.size(); i++) {
					JsonObject obj = array.get(i).getAsJsonObject();
					list.put(obj.get("name").getAsString(), obj.get("value").getAsDouble());
				}
				list.close();
				return list;
			} else if (token == JsonToken.BEGIN_OBJECT) {
				Map<String, Double> map = gson.fromJson(in, Map.class);
				MeasurementList list = MeasurementListFactory.createMeasurementList(map.size(), MeasurementListType.DOUBLE);
				list.putAll(map);
				list.close();
				return list;
			} else {
				return null;
			}
		}
		
	}
	
	
}