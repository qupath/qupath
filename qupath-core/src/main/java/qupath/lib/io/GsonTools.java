package qupath.lib.io;

import java.io.IOException;
import java.util.Collection;

import org.locationtech.jts.geom.Geometry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import qupath.lib.images.servers.ImageServers;
import qupath.lib.io.PathObjectTypeAdapters.FeatureCollection;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
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
	
	private static Gson gson = new GsonBuilder()
			.serializeSpecialFloatingPointValues()
			.setLenient()
			.registerTypeAdapterFactory(new QuPathTypeAdapterFactory())
			.registerTypeAdapterFactory(OpenCVTypeAdapters.getOpenCVTypeAdaptorFactory())
			.registerTypeAdapterFactory(ImageServers.getImageServerTypeAdapterFactory(true))
			.registerTypeAdapterFactory(ImageServers.getServerBuilderFactory())
//			.registerTypeHierarchyAdapter(PathObject.class, PathObjectTypeAdapters.PathObjectTypeAdapter.INSTANCE)
//			.registerTypeHierarchyAdapter(MeasurementList.class, PathObjectTypeAdapters.MeasurementListTypeAdapter.INSTANCE)
//			.registerTypeHierarchyAdapter(FeatureCollection.class, PathObjectTypeAdapters.PathObjectCollectionTypeAdapter.INSTANCE)
//			.registerTypeHierarchyAdapter(ROI.class, ROITypeAdapters.ROI_ADAPTER_INSTANCE)
//			.registerTypeHierarchyAdapter(Geometry.class, ROITypeAdapters.GEOMETRY_ADAPTER_INSTANCE)
//			.registerTypeAdapter(PathClass.class, PathClassTypeAdapter.INSTANCE)
//			.registerTypeAdapter(ImagePlane.class, ImagePlaneTypeAdapter.INSTANCE)
//			.registerTypeAdapterFactory(OpenCVTypeAdapters.getOpenCVTypeAdaptorFactory())
			.create();
	
	
	static class QuPathTypeAdapterFactory implements TypeAdapterFactory {

		@Override
		public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
			return getTypeAdaptor((Class<T>)type.getRawType());
		}
		
		@SuppressWarnings("unchecked")
		static <T> TypeAdapter<T> getTypeAdaptor(Class<T> cls) {
			if (PathObject.class.isAssignableFrom(cls))
				return (TypeAdapter<T>)PathObjectTypeAdapters.PathObjectTypeAdapter.INSTANCE;

			if (MeasurementList.class.isAssignableFrom(cls))
				return (TypeAdapter<T>)PathObjectTypeAdapters.MeasurementListTypeAdapter.INSTANCE;

			if (FeatureCollection.class.isAssignableFrom(cls))
				return (TypeAdapter<T>)PathObjectTypeAdapters.PathObjectCollectionTypeAdapter.INSTANCE;
			
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
	 * Wrap a collection of PathObjects as a FeatureCollection. The purpose of this is to enable 
	 * exporting a GeoJSON FeatureCollection that may be reused in other software.
	 * @param pathObjects
	 * @return
	 */
	public static FeatureCollection wrapFeatureCollection(Collection<? extends PathObject> pathObjects) {
		return new FeatureCollection(pathObjects);
	}
	
	/**
	 * Get default Gson, capable of serializing/deserializing some key QuPath classes.
	 * @return
	 * 
	 * @see #getInstance(boolean)
	 */
	public static Gson getInstance() {
		return gson;
	}
	
	/**
	 * Get default Gson, optionally with pretty printing enabled.
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
			// Write in the default way
			gson.toJson(value, PathClass.class, out);
//			Streams.write(gson.toJsonTree(value), out);
		}

		@Override
		public PathClass read(JsonReader in) throws IOException {
			// Read in the default way, then replace with a singleton instance
			PathClass pathClass = gson.fromJson(in, PathClass.class);
			return PathClassFactory.getSingletonPathClass(pathClass);
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
