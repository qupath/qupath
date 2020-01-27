package qupath.opencv.ml.objects;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.RuntimeTypeAdapterFactory;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;

import qupath.lib.classifiers.object.ObjectClassifier;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectFilter;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;


public class ObjectClassifiers {
	
	static class ObjectClassifierTypeAdapterFactory implements TypeAdapterFactory {

		public ObjectClassifierTypeAdapterFactory() {}
		
		private static String typeName = "object_classifier_type";
		
		private final static RuntimeTypeAdapterFactory<ObjectClassifier> featureCalculatorTypeAdapter = 
				RuntimeTypeAdapterFactory.of(ObjectClassifier.class, typeName)
					.registerSubtype(OpenCVMLClassifier.class)
					.registerSubtype(CompositeClassifier.class)
					.registerSubtype(SimpleClassifier.class)
					.registerSubtype(ResetClassifier.class);
		
		private static void registerSubtype(Class<? extends ObjectClassifier> cls) {
			featureCalculatorTypeAdapter.registerSubtype(cls);
		}
		
		@Override
		public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
			return featureCalculatorTypeAdapter.create(gson, type);
		}
		
	}
	
	private final static TypeAdapterFactory factory = new ObjectClassifierTypeAdapterFactory();
	
	public static TypeAdapterFactory getTypeAdapterFactory() {
		return factory;
	}
	
	
	public static <T> ObjectClassifier<T> createCompositeClassifier(ObjectClassifier<T>... classifiers) {
		return new CompositeClassifier(Arrays.asList(classifiers));
	}
	
	public static <T> ObjectClassifier<T> createCompositeClassifier(Collection<ObjectClassifier<T>> classifiers) {
		return new CompositeClassifier<>(classifiers);
	}
	
	/**
	 * Create a classifier that only resets the classification.
	 * This can be useful as the first input to a {@link CompositeClassifier}.
	 * 
	 * @param filter filter to select objects that should be classified
	 * @return the 'classifier' that sets the classification to null
	 */
	public static ObjectClassifier<BufferedImage> createResetClassifier(PathObjectFilter filter) {
		return new ResetClassifier(filter);
	}
	
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
	public static ObjectClassifier createChannelClassifier(PathObjectFilter filter, ImageChannel channel, String measurement, double threshold) {
		var pathClass = PathClassFactory.getPathClass(channel.getName(), channel.getColor());
		return new ClassifyByMeasurementBuilder(measurement)
			.threshold(threshold)
			.filter(filter)
			.aboveEquals(pathClass)
			.build();
	}
	
	public static class ClassifyByMeasurementBuilder {
		
		private PathObjectFilter filter = PathObjectFilter.DETECTIONS_ALL;
		private String measurementName;
		private Double threshold;
		private PathClass pathClassBelow, pathClassEquals, pathClassAbove;
		
		public ClassifyByMeasurementBuilder(String measurementName) {
			this.measurementName = measurementName;
		}

		public ClassifyByMeasurementBuilder filter(PathObjectFilter filter) {
			this.filter = filter;
			return this;
		}
		
		public ClassifyByMeasurementBuilder cells() {
			return filter(PathObjectFilter.CELLS);
		}
		
		public ClassifyByMeasurementBuilder tiles() {
			return filter(PathObjectFilter.TILES);
		}
		
		public ClassifyByMeasurementBuilder detections() {
			return filter(PathObjectFilter.DETECTIONS_ALL);
		}

		public ClassifyByMeasurementBuilder threshold(double threshold) {
			this.threshold = threshold;
			return this;
		}
		
		public ClassifyByMeasurementBuilder above(String pathClassName) {
			var pathClass = PathClassFactory.getPathClass(pathClassName);
			this.pathClassAbove = pathClass;
			return this;
		}
		
		public ClassifyByMeasurementBuilder aboveEquals(String pathClassName) {
			var pathClass = PathClassFactory.getPathClass(pathClassName);
			this.pathClassAbove = pathClass;
			this.pathClassEquals = pathClass;
			return this;
		}
		
		public ClassifyByMeasurementBuilder belowEquals(String pathClassName) {
			var pathClass = PathClassFactory.getPathClass(pathClassName);
			this.pathClassBelow = pathClass;
			this.pathClassEquals = pathClass;
			return this;
		}
		
		public ClassifyByMeasurementBuilder below(String pathClassName) {
			var pathClass = PathClassFactory.getPathClass(pathClassName);
			this.pathClassBelow = pathClass;
			return this;
		}
		
		public ClassifyByMeasurementBuilder equalTo(String pathClassName) {
			var pathClass = PathClassFactory.getPathClass(pathClassName);
			this.pathClassEquals = pathClass;
			return this;
		}
		
		
		public ClassifyByMeasurementBuilder above(PathClass pathClass) {
			this.pathClassAbove = pathClass;
			return this;
		}
		
		public ClassifyByMeasurementBuilder aboveEquals(PathClass pathClass) {
			this.pathClassAbove = pathClass;
			this.pathClassEquals = pathClass;
			return this;
		}
		
		public ClassifyByMeasurementBuilder belowEquals(PathClass pathClass) {
			this.pathClassBelow = pathClass;
			this.pathClassEquals = pathClass;
			return this;
		}
		
		public ClassifyByMeasurementBuilder below(PathClass pathClass) {
			this.pathClassBelow = pathClass;
			return this;
		}
		
		public ClassifyByMeasurementBuilder equalTo(PathClass pathClass) {
			this.pathClassEquals = pathClass;
			return this;
		}
		
		
		public ObjectClassifier<BufferedImage> build() {
			if (threshold == null)
				throw new IllegalArgumentException("No threshold is specified!");
			var fun = new ClassifyByMeasurementFunction(
					measurementName, threshold,
					pathClassBelow, pathClassEquals, pathClassAbove);
			return new SimpleClassifier(
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
		    this.pathClassBelow = pathClassBelow;
		    this.pathClassEquals = pathClassEquals;
		    this.pathClassAbove = pathClassAbove;
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
	
	
	static class ResetClassifier extends AbstractObjectClassifier {

		protected ResetClassifier(PathObjectFilter filter) {
			super(filter);
		}

		@Override
		public Collection<PathClass> getPathClasses() {
			return Collections.emptyList();
		}

		@Override
		public int classifyObjects(ImageData<BufferedImage> imageData, Collection<? extends PathObject> pathObjects) {
			int n = 0;
			for (var pathObject : pathObjects) {
				if (pathObject.getPathClass() != null) {
					pathObject.setPathClass(null);
					n++;
				}
			}
			return n++;
		}
		
	}

}
