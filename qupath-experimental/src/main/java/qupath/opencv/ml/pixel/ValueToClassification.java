package qupath.opencv.ml.pixel;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import qupath.lib.io.GsonTools;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;

/**
 * Helper class for defining simple classifiers that can convert a single input value into a classification.
 * This can be used, for example, for pixel classifiers that may apply one or more cutoff thresholds.
 * 
 * @author Pete Bankhead
 */
public class ValueToClassification {
	
	
	private static class ThresholdClassifierTypeAdapter extends TypeAdapter<ThresholdClassifier> {
		
		private static Gson gson = new GsonBuilder().setLenient().create();

		@Override
		public void write(JsonWriter out, ThresholdClassifier value) throws IOException {
			gson.toJson(gson.toJsonTree(value), out);
		}

		@Override
		public ThresholdClassifier read(JsonReader in) throws IOException {
			JsonObject obj = JsonParser.parseReader(in).getAsJsonObject();
			
			if (hasMembers(obj, "thresholdValue", "lessThanThreshold", "equalsThreshold", "greaterThanThreshold")) {
				return gson.fromJson(obj, SimpleClassificationThresholder.class);
			}
			if (obj.has("pathClass"))
				return gson.fromJson(obj, SimpleClassificationWrapper.class);
			throw new IOException("Cannot parse " + obj);
		}
		
		static boolean hasMembers(JsonObject obj, String...members) {
			for (String m : members)
				if (!obj.has(m))
					return false;
			return true;
		}
		
	}
	

	/**
	 * Interface implemented by classes capable of converting a single threshold value into a classification.
	 * <p>
	 * Beware implementations of this may should be registered with {@link GsonTools} to be serializable;
	 * it if for this reason not really intended to support alternative implementations at this time.
	 */
	@JsonAdapter(ThresholdClassifierTypeAdapter.class)
	public static interface ThresholdClassifier {

		/**
		 * Get the appropriate classification given a specific threshold value.
		 * @return
		 */
		PathClass getClassification(double value);

		/**
		 * Get a collection of all available classifications.
		 * This depends upon how many cutoff threshold values are applied internally.
		 * @return
		 */
		Collection<PathClass> getPathClasses();

	}
	
	
	/**
	 * Create a {@link ThresholdClassifier} using a single threshold.
	 * @param threshold the threshold value to apply
	 * @param lessThanThreshold the classification for values lower than threshold
	 * @param equalsThreshold the classification for values equal to threshold
	 * @param greaterThanThreshold the classification for values greater than threshold
	 * @return
	 */
	public static ThresholdClassifier createThresholdClassifier(double threshold, PathClass lessThanThreshold, PathClass equalsThreshold, PathClass greaterThanThreshold) {
		return new SimpleClassificationThresholder.Builder(threshold)
			.lessThanThreshold(lessThanThreshold)
			.equalsThreshold(equalsThreshold)
			.greaterThanThreshold(greaterThanThreshold)
			.build();
	}
	
	

	static class SimpleClassificationWrapper implements ThresholdClassifier {

		private PathClass pathClass;

		SimpleClassificationWrapper(PathClass pathClass) {
			if (pathClass == null)
				this.pathClass = PathClassFactory.getPathClassUnclassified();
			else
				this.pathClass = pathClass;
		}

		@Override
		public PathClass getClassification(double value) {
			return pathClass;
		}

		@Override
		public Collection<PathClass> getPathClasses() {
			return Collections.singletonList(pathClass);
		}

		@Override
		public String toString() {
			return pathClass.toString();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((pathClass == null) ? 0 : pathClass.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			SimpleClassificationWrapper other = (SimpleClassificationWrapper) obj;
			if (pathClass == null) {
				if (other.pathClass != null)
					return false;
			} else if (!pathClass.equals(other.pathClass))
				return false;
			return true;
		}

	}

	static class SimpleClassificationThresholder implements ThresholdClassifier {

		private static final SimpleClassificationWrapper DEFAULT_WRAPPER = new SimpleClassificationWrapper(null);

		private double thresholdValue;
		private ThresholdClassifier lessThanThreshold = DEFAULT_WRAPPER;
		private ThresholdClassifier equalsThreshold = DEFAULT_WRAPPER;
		private ThresholdClassifier greaterThanThreshold = DEFAULT_WRAPPER;

		private SimpleClassificationThresholder(double thresholdValue) {
			this.thresholdValue = thresholdValue;
		}

		@Override
		public PathClass getClassification(double value) {
			if (value > thresholdValue)
				return greaterThanThreshold.getClassification(value);
			if (value < thresholdValue)
				return lessThanThreshold.getClassification(value);
			return equalsThreshold.getClassification(value);
		}

		@Override
		public Collection<PathClass> getPathClasses() {
			Set<PathClass> set = new LinkedHashSet<>();
			set.addAll(lessThanThreshold.getPathClasses());
			set.addAll(equalsThreshold.getPathClasses());
			set.addAll(greaterThanThreshold.getPathClasses());
			return set;
		}

		@Override
		public String toString() {
			if (lessThanThreshold.equals(equalsThreshold)) {
				if (equalsThreshold.equals(greaterThanThreshold)) {
					return lessThanThreshold.toString();
				} else {
					return lessThanThreshold.toString() + " if <=" + thresholdValue + ", " + greaterThanThreshold.toString();
				}
			} else if (equalsThreshold.equals(greaterThanThreshold)) {
				return lessThanThreshold.toString() + "<" + thresholdValue + "<=" + greaterThanThreshold.toString();
			} else {
				return lessThanThreshold.toString() + "<" + thresholdValue + "<=" + greaterThanThreshold.toString();				
			}
		}


		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((equalsThreshold == null) ? 0 : equalsThreshold.hashCode());
			result = prime * result + ((greaterThanThreshold == null) ? 0 : greaterThanThreshold.hashCode());
			result = prime * result + ((lessThanThreshold == null) ? 0 : lessThanThreshold.hashCode());
			long temp;
			temp = Double.doubleToLongBits(thresholdValue);
			result = prime * result + (int) (temp ^ (temp >>> 32));
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			SimpleClassificationThresholder other = (SimpleClassificationThresholder) obj;
			if (equalsThreshold == null) {
				if (other.equalsThreshold != null)
					return false;
			} else if (!equalsThreshold.equals(other.equalsThreshold))
				return false;
			if (greaterThanThreshold == null) {
				if (other.greaterThanThreshold != null)
					return false;
			} else if (!greaterThanThreshold.equals(other.greaterThanThreshold))
				return false;
			if (lessThanThreshold == null) {
				if (other.lessThanThreshold != null)
					return false;
			} else if (!lessThanThreshold.equals(other.lessThanThreshold))
				return false;
			if (Double.doubleToLongBits(thresholdValue) != Double.doubleToLongBits(other.thresholdValue))
				return false;
			return true;
		}


		static class Builder {

			SimpleClassificationThresholder thresholder;

			Builder(double threshold) {
				thresholder = new SimpleClassificationThresholder(threshold);
			}

			Builder greaterThanThreshold(PathClass pathClass) {
				thresholder.greaterThanThreshold = new SimpleClassificationWrapper(pathClass);
				return this;
			}

			Builder greaterThanThreshold(SimpleClassificationThresholder thresholder) {
				thresholder.greaterThanThreshold = thresholder;
				return this;
			}

			Builder lessThanThreshold(PathClass pathClass) {
				thresholder.lessThanThreshold = new SimpleClassificationWrapper(pathClass);
				return this;
			}

			Builder lessThanThreshold(SimpleClassificationThresholder thresholder) {
				thresholder.lessThanThreshold = thresholder;
				return this;
			}

			Builder equalsThreshold(PathClass pathClass) {
				thresholder.equalsThreshold = new SimpleClassificationWrapper(pathClass);
				return this;
			}

			Builder equalsThreshold(SimpleClassificationThresholder thresholder) {
				thresholder.equalsThreshold = thresholder;
				return this;
			}

			SimpleClassificationThresholder build() {
				var newThresholder = new SimpleClassificationThresholder(thresholder.thresholdValue);
				newThresholder.greaterThanThreshold = thresholder.greaterThanThreshold;
				newThresholder.lessThanThreshold = thresholder.lessThanThreshold;
				newThresholder.equalsThreshold = thresholder.equalsThreshold;
				return newThresholder;
			}

		}


	}

}
