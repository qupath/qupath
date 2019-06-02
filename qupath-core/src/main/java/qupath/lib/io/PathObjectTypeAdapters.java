package qupath.lib.io;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

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

import qupath.lib.io.GsonTools.PathClassTypeAdapter;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.measurements.MeasurementList.MeasurementListType;
import qupath.lib.measurements.MeasurementListFactory;
import qupath.lib.objects.MetadataStore;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.PathRootObject;
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
		
		static PathObjectTypeAdapter INSTANCE = new PathObjectTypeAdapter();
		
		private boolean flattenProperties = false;

		@Override
		public void write(JsonWriter out, PathObject value) throws IOException {
			
			out.beginObject();
			
			out.name("type");
			out.value("Feature");
			
			out.name("id");
			out.value(value.getClass().getSimpleName());		
	
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
			
			if (flattenProperties) {
				// Add measurements
				MeasurementList measurements = value.getMeasurementList();
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
				out.name("measurements");
				MeasurementListTypeAdapter.INSTANCE.write(out, value.getMeasurementList());
				
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
			
			String id = obj.get("id").getAsString();
			
			ROI roi = ROITypeAdapters.ROI_ADAPTER_INSTANCE.fromJsonTree(obj.get("geometry"));
			
			PathClass pathClass = null;
			
			boolean isMissing = false;
			boolean isLocked = false;
			
			MeasurementList measurementList = null;
			JsonObject metadata = null;
			
			if (obj.has("properties")) {
				JsonObject properties = obj.get("properties").getAsJsonObject();
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
			}
			ROI roiNucleus = null;
			if (obj.has("nucleusGeometry")) {
				roiNucleus = ROITypeAdapters.ROI_ADAPTER_INSTANCE.fromJsonTree(obj.get("nucleusGeometry"));
			}
			
			PathObject pathObject = null;
			switch (id) {
			case ("PathTileObject"):
				pathObject = PathObjects.createTileObject(roi, pathClass, measurementList);
				break;
			case ("PathCellObject"):
				pathObject = PathObjects.createCellObject(roi, roiNucleus, pathClass, measurementList);
				break;
			case ("TMACoreObject"):
				pathObject = PathObjects.createTMACoreObject(roi.getBoundsX(), roi.getBoundsY(), roi.getBoundsWidth(), roi.getBoundsHeight(), isMissing);
				break;
			case ("PathDetectionObject"):
				pathObject = PathObjects.createDetectionObject(roi, pathClass, measurementList);
				break;
			case ("PathRootObject"):
				pathObject = new PathRootObject();
				break;
			default:
				// Default is to create an annotation
				pathObject = PathObjects.createAnnotationObject(roi, pathClass);
			}
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
