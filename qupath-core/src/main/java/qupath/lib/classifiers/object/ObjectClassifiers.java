package qupath.lib.classifiers.object;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.RuntimeTypeAdapterFactory;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;

import qupath.lib.images.servers.ImageChannel;
import qupath.lib.io.GsonTools;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectFilter;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;

/**
 * Helper class for creating {@linkplain ObjectClassifier ObjectClassifiers}.
 * 
 * @author Pete Bankhead
 */
public class ObjectClassifiers {
	
	public static class ObjectClassifierTypeAdapterFactory implements TypeAdapterFactory {

		public ObjectClassifierTypeAdapterFactory() {}
		
		private static String typeName = "object_classifier_type";
		
		private final static RuntimeTypeAdapterFactory<ObjectClassifier> objectClassifierTypeAdapter = 
				RuntimeTypeAdapterFactory.of(ObjectClassifier.class, typeName)
//					.registerSubtype(OpenCVMLClassifier.class)
					.registerSubtype(CompositeClassifier.class)
					.registerSubtype(SimpleClassifier.class);
//					.registerSubtype(ResetClassifier.class);
		
		private final static RuntimeTypeAdapterFactory<Function> classifierFunTypeAdapter = 
				RuntimeTypeAdapterFactory.of(Function.class, "classifier_fun")
					.registerSubtype(ClassifyByMeasurementFunction.class);
		
		public static void registerSubtype(Class<? extends ObjectClassifier> cls) {
			objectClassifierTypeAdapter.registerSubtype(cls);
		}
		
		@Override
		public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
			var adaptor = classifierFunTypeAdapter.create(gson, type);
			if (adaptor != null)
				return adaptor;
			return objectClassifierTypeAdapter.create(gson, type);
		}
		
	}
	
	private final static TypeAdapterFactory factory = new ObjectClassifierTypeAdapterFactory();
	
	public static TypeAdapterFactory getTypeAdapterFactory() {
		return factory;
	}
	
	/**
	 * Create a composite {@link ObjectClassifier}, which sequentially applies multiple (usually single-class) classifiers.
	 * This can be used for multiplexed images, where a single classifier may be trained for each marker.
	 * @param <T>
	 * @param classifiers an array of classifiers to combine
	 * @return the composite object classifier
	 */
	public static <T> ObjectClassifier<T> createCompositeClassifier(ObjectClassifier<T>... classifiers) {
		return new CompositeClassifier<>(Arrays.asList(classifiers));
	}
	
	/**
	 * Create a composite {@link ObjectClassifier}, which sequentially applies multiple (usually single-class) classifiers.
	 * This can be used for multiplexed images, where a single classifier may be trained for each marker.
	 * @param <T>
	 * @param classifiers a collection of classifiers to combine
	 * @return the composite object classifier
	 */
	public static <T> ObjectClassifier<T> createCompositeClassifier(Collection<ObjectClassifier<T>> classifiers) {
		return new CompositeClassifier<>(classifiers);
	}
	
//	/**
//	 * Create a classifier that only resets the classification.
//	 * This can be useful as the first input to a {@link CompositeClassifier}.
//	 * 
//	 * @param filter filter to select objects that should be classified
//	 * @return the 'classifier' that sets the classification to null
//	 */
//	public static <T> ObjectClassifier<T> createResetClassifier(PathObjectFilter filter) {
//		return new ResetClassifier<T>(filter);
//	}
	
	/**
	 * Create a classifier that thresholds a single measurement, assigning a class based on an {@link ImageChannel} 
	 * if the value is &ge; threshold.
	 * 
	 * @param filter filter to apply, typically {@code PathObjectFilter.CELLS}
	 * @param channel {@link ImageChannel} from which the {@link PathClass} should be derived.
	 * @param measurement name of the measurement (within the object's {@link MeasurementList})
	 * @param threshold threshold to apply to the measurement
	 * @return the object classifier
	 * 
	 * @see PathObjectFilter
	 */
	public static <T> ObjectClassifier<T> createChannelClassifier(PathObjectFilter filter, ImageChannel channel, String measurement, double threshold) {
		var pathClass = PathClassFactory.getPathClass(channel.getName(), channel.getColor());
		return new ClassifyByMeasurementBuilder<T>(measurement)
			.threshold(threshold)
			.filter(filter)
			.aboveEquals(pathClass)
			.build();
	}
	
	
	/**
	 * Read the classifier from a file.
	 * @param <T>
	 * @param path
	 * @throws IOException
	 */
	public static <T> ObjectClassifier<T> readClassifier(Path path) throws IOException {
		try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			return GsonTools.getInstance().fromJson(reader, ObjectClassifier.class);
		}
	}
	
	/**
	 * Write the classifier to a file.
	 * @param <T>
	 * @param classifier
	 * @param path
	 * @throws IOException
	 */
	public static <T> void writeClassifier(ObjectClassifier<T> classifier, Path path) throws IOException {
		try (var writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
			GsonTools.getInstance(true).toJson(classifier, writer);
		}
	}
	
	
	/**
	 * Builder to create a simple {@link ObjectClassifier} that assigns a classification based upon whether the 
	 * measurement of an object is above, equal to or below a specified threshold.
	 * 
	 * @param <T>
	 */
	public static class ClassifyByMeasurementBuilder<T> {
		
		private PathObjectFilter filter = PathObjectFilter.DETECTIONS_ALL;
		private String measurementName;
		private Double threshold;
		private PathClass pathClassBelow, pathClassEquals, pathClassAbove;
		
		/**
		 * Constructor.
		 * @param measurementName name of the measurement used for clasification (should be present within the object's {@link MeasurementList}).
		 */
		public ClassifyByMeasurementBuilder(String measurementName) {
			this.measurementName = measurementName;
		}

		/**
		 * Define the filter used to identify objects compatible with this classifier.
		 * @param filter
		 * @return this builder
		 */
		public ClassifyByMeasurementBuilder<T> filter(PathObjectFilter filter) {
			this.filter = filter;
			return this;
		}
		
		/**
		 * Set the filter to accept cell objects only.
		 * @return this builder
		 */
		public ClassifyByMeasurementBuilder<T> cells() {
			return filter(PathObjectFilter.CELLS);
		}
		
		/**
		 * Set the filter to accept tile objects only.
		 * @return this builder
		 */
		public ClassifyByMeasurementBuilder<T> tiles() {
			return filter(PathObjectFilter.TILES);
		}
		
		/**
		 * Set the filter to accept all detection objects only.
		 * @return this builder
		 */
		public ClassifyByMeasurementBuilder<T> detections() {
			return filter(PathObjectFilter.DETECTIONS_ALL);
		}

		/**
		 * Set the threshold value used for the classification.
		 * @return this builder
		 */
		public ClassifyByMeasurementBuilder<T> threshold(double threshold) {
			this.threshold = threshold;
			return this;
		}
		
		/**
		 * Set the classification (by name) for objects for which the specified measurement has a value above the threshold.
		 * @return this builder
		 * @see #threshold(double)
		 */
		public ClassifyByMeasurementBuilder<T> above(String pathClassName) {
			var pathClass = PathClassFactory.getPathClass(pathClassName);
			this.pathClassAbove = pathClass;
			return this;
		}
		
		/**
		 * Set the classification (by name) for objects for which the specified measurement has a value above or equal to the threshold.
		 * @return this builder
		 * @see #threshold(double)
		 */
		public ClassifyByMeasurementBuilder<T> aboveEquals(String pathClassName) {
			var pathClass = PathClassFactory.getPathClass(pathClassName);
			this.pathClassAbove = pathClass;
			this.pathClassEquals = pathClass;
			return this;
		}
		
		/**
		 * Set the classification (by name) for objects for which the specified measurement has a value below or equal to the threshold.
		 * @return this builder
		 * @see #threshold(double)
		 */
		public ClassifyByMeasurementBuilder<T> belowEquals(String pathClassName) {
			var pathClass = PathClassFactory.getPathClass(pathClassName);
			this.pathClassBelow = pathClass;
			this.pathClassEquals = pathClass;
			return this;
		}
		
		/**
		 * Set the classification (by name) for objects for which the specified measurement has a value below the threshold.
		 * @return this builder
		 * @see #threshold(double)
		 */
		public ClassifyByMeasurementBuilder<T> below(String pathClassName) {
			var pathClass = PathClassFactory.getPathClass(pathClassName);
			this.pathClassBelow = pathClass;
			return this;
		}
		
		/**
		 * Set the classification (by name) for objects for which the specified measurement has a value exactly equal to the threshold.
		 * @return this builder
		 * @see #threshold(double)
		 */
		public ClassifyByMeasurementBuilder<T> equalTo(String pathClassName) {
			var pathClass = PathClassFactory.getPathClass(pathClassName);
			this.pathClassEquals = pathClass;
			return this;
		}
		
		
		/**
		 * Set the classification for objects for which the specified measurement has a value above the threshold.
		 * @return this builder
		 * @see #threshold(double)
		 */
		public ClassifyByMeasurementBuilder<T> above(PathClass pathClass) {
			this.pathClassAbove = pathClass;
			return this;
		}
		
		/**
		 * Set the classification for objects for which the specified measurement has a value above or equal to the threshold.
		 * @return this builder
		 * @see #threshold(double)
		 */
		public ClassifyByMeasurementBuilder<T> aboveEquals(PathClass pathClass) {
			this.pathClassAbove = pathClass;
			this.pathClassEquals = pathClass;
			return this;
		}
		
		/**
		 * Set the classification for objects for which the specified measurement has a value below or equal to the threshold.
		 * @return this builder
		 * @see #threshold(double)
		 */
		public ClassifyByMeasurementBuilder<T> belowEquals(PathClass pathClass) {
			this.pathClassBelow = pathClass;
			this.pathClassEquals = pathClass;
			return this;
		}
		
		/**
		 * Set the classification for objects for which the specified measurement has a value below the threshold.
		 * @return this builder
		 * @see #threshold(double)
		 */
		public ClassifyByMeasurementBuilder<T> below(PathClass pathClass) {
			this.pathClassBelow = pathClass;
			return this;
		}
		
		/**
		 * Set the classification for objects for which the specified measurement has a value exactly equal to the threshold.
		 * @return this builder
		 * @see #threshold(double)
		 */
		public ClassifyByMeasurementBuilder<T> equalTo(PathClass pathClass) {
			this.pathClassEquals = pathClass;
			return this;
		}
		
		
		/**
		 * Build the classifier defined by the parameters of this builder.
		 * @return the object classifier
		 */
		public ObjectClassifier<T> build() {
			if (threshold == null)
				throw new IllegalArgumentException("No threshold is specified!");
			var fun = new ClassifyByMeasurementFunction(
					measurementName, threshold,
					pathClassBelow, pathClassEquals, pathClassAbove);
			return new SimpleClassifier<>(
					filter,
					fun,
					Arrays.asList(fun.pathClassBelow, fun.pathClassEquals, fun.pathClassAbove)
						.stream().filter(p -> p != null)
						.distinct()
						.collect(Collectors.toList()));
		}
		
		
	}
	
	

	static class ClassifyByMeasurementFunction implements Function<PathObject, PathClass> {

		private String measurement;
		private PathClass pathClassBelow, pathClassEquals, pathClassAbove;
		private double threshold;

		ClassifyByMeasurementFunction(String measurement, double threshold, PathClass pathClassBelow, PathClass pathClassEquals, PathClass pathClassAbove) {
			this.measurement = measurement;
			this.threshold = threshold;
			this.pathClassBelow = pathClassBelow == PathClassFactory.getPathClassUnclassified() ? null : pathClassBelow;
			this.pathClassEquals = pathClassEquals == PathClassFactory.getPathClassUnclassified() ? null : pathClassEquals;
			this.pathClassAbove = pathClassAbove == PathClassFactory.getPathClassUnclassified() ? null : pathClassAbove;
		}

		@Override
		public PathClass apply(PathObject pathObject) {
			double val = pathObject.getMeasurementList().getMeasurementValue(measurement);
			if (Double.isNaN(val))
				return null;
			if (val > threshold)
				return pathClassAbove;
			if (val < threshold)
				return pathClassBelow;
			if (val == threshold)
				return pathClassEquals;
			return null;
		}
	}


//	static class ResetClassifier<T> extends AbstractObjectClassifier<T> {
//
//		protected ResetClassifier(PathObjectFilter filter) {
//			super(filter);
//		}
//
//		@Override
//		public Collection<PathClass> getPathClasses() {
//			return Collections.emptyList();
//		}
//
//		@Override
//		public int classifyObjects(ImageData<T> imageData, Collection<? extends PathObject> pathObjects) {
//			int n = 0;
//			for (var pathObject : pathObjects) {
//				if (pathObject.getPathClass() != null) {
//					pathObject.setPathClass(null);
//					n++;
//				}
//			}
//			return n++;
//		}
//		
//	}

}
