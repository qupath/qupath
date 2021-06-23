/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
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

package qupath.opencv.classify;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import qupath.lib.analysis.stats.RunningStatistics;
import qupath.lib.classifiers.Normalization;
import qupath.lib.classifiers.PathObjectClassifier;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.plugins.parameters.Parameterizable;

import static org.bytedeco.opencv.global.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_ml.*;

import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.opencv_ml.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for OpenCV classifiers.
 * <p>
 * Note: We cannot directly serialize an OpenCV classifier, so instead the training data is serialized and the classifier
 * rebuilt as required.  This means that potentially if a classifier is reloaded with a different version of the OpenCV library,
 * if the training algorithm has changed then there may be a different result.
 * 
 * @author Pete Bankhead
 * @param <T> 
 *
 */
public abstract class OpenCvClassifier<T extends StatModel> implements PathObjectClassifier, Externalizable {
	
	private static final long serialVersionUID = -7974734731360344083L;

	final private static Logger logger = LoggerFactory.getLogger(OpenCvClassifier.class);
	
	private long timestamp = System.currentTimeMillis();
	private Normalization normalization = Normalization.NONE;
	List<PathClass> pathClasses;
	private double[] normScale;
	private double[] normOffset;
	transient T classifier;
	
	List<String> measurements = new ArrayList<>();
	// We can't serialize directly, so instead save all training data so classifier can be rebuilt as required
	float[] arrayTraining = null; // Array of training data
	int[] arrayResponses = null; // Array of 'responses', i.e. indices to pathClasses list
	
	
	protected OpenCvClassifier() {}
	

	/**
	 * Protected method used to indicate whether any options for the classifier have been changed.
	 * If this false, then updateClassifier may choose not to retrain a classifier fully if it already has a classifier
	 * trained on identical data.
	 * 
	 * By default this always returns false (assuming that no externally-accessible parameters are involved).
	 * 
	 * A conservative subclass that enables options to be set may always return 'true' to force retraining in all instances.
	 * 
	 * A less conservative subclass that enables options to be set should check all options to see if they have changed since
	 * the last time the classifier was trained, and return true or false accordingly.
	 * 
	 * @return
	 */
	protected boolean classifierOptionsChanged() {
		return false;
	}
	
	@Override
	public boolean updateClassifier(final Map<PathClass, List<PathObject>> map, final List<String> measurements, Normalization normalization) {
		
		// There is a chance we don't need to retrain... to find out, cache the most important current variables
		boolean maybeSameClassifier = isValid() && 
				this.normalization == normalization && 
				!classifierOptionsChanged() && 
				this.measurements.equals(measurements) 
				&& pathClasses.size() == map.size() && 
				map.keySet().containsAll(pathClasses);
		
		float[] arrayTrainingPrevious = arrayTraining;
		int[] arrayResponsesPrevious = arrayResponses;
		
		pathClasses = new ArrayList<>(map.keySet());
		Collections.sort(pathClasses);

		int n = 0;
		for (Map.Entry<PathClass, List<PathObject>> entry : map.entrySet()) {
			n += entry.getValue().size();
		}

		// Compute running statistics for normalization
		HashMap<String, RunningStatistics> statsMap = new LinkedHashMap<>();
		for (String m : measurements)
			statsMap.put(m, new RunningStatistics());


		this.measurements.clear();
		this.measurements.addAll(measurements);
		int nMeasurements = measurements.size();
		arrayTraining = new float[n * nMeasurements];
		arrayResponses = new int[n];

		int row = 0;
		int nnan = 0;
		for (PathClass pathClass : pathClasses) {
			List<PathObject> list = map.get(pathClass);
			int classIndex = pathClasses.indexOf(pathClass);
			for (int i = 0; i < list.size(); i++) {
				MeasurementList measurementList = list.get(i).getMeasurementList();
				int col = 0;
				for (String m : measurements) {
					double value = measurementList.getMeasurementValue(m);
					if (Double.isNaN(value))
						nnan++;
					else
						statsMap.get(m).addValue(value);
					arrayTraining[row * nMeasurements + col] = (float)value;
					col++;
				}
				arrayResponses[row] = classIndex;
				row++;
			}
		}
		
		
		// Normalise, if required
		if (normalization != null && normalization != Normalization.NONE) {
			logger.debug("Training classifier with normalization: {}", normalization);
			int numMeasurements = measurements.size();
			normOffset = new double[numMeasurements];
			normScale = new double[numMeasurements];
			for (int i = 0; i < numMeasurements; i++) {
				RunningStatistics stats = statsMap.get(measurements.get(i));
				if (normalization == Normalization.MEAN_VARIANCE) {
					normOffset[i] = -stats.getMean();
					if (stats.getStdDev() > 0)
						normScale[i] = 1.0 / stats.getStdDev();
				} else if (normalization == Normalization.MIN_MAX){
					normOffset[i] = -stats.getMin();
					if (stats.getRange() > 0)
						normScale[i] = 1.0 / (stats.getMax() - stats.getMin());					
					else
						normScale[i] = 1.0;
				}
			}
			
			// Apply normalisation
			for (int i = 0; i < arrayTraining.length; i++) {
				int k = i % numMeasurements;
				arrayTraining[i] = (float)((arrayTraining[i] + normOffset[k]) * normScale[k]);
			}
			this.normalization = normalization;
			
		} else {
			logger.debug("Training classifier without normalization");
			normScale = null;
			normOffset = null;
			this.normalization = Normalization.NONE;
		}
		
		
		
		// Record that we have NaNs
		if (nnan > 0)
			logger.debug("Number of NaNs in training set: " + nnan);


		
		// Having got this far, check to see whether we really do need to retrain
		if (maybeSameClassifier) {
			if (Arrays.equals(arrayTrainingPrevious, arrayTraining) &&
					Arrays.equals(arrayResponsesPrevious, arrayResponses)) {
				logger.info("Classifier already trained with the same samples - existing classifier will be used");
				return false;
			}
		}
		
		
		createAndTrainClassifier();

		timestamp = System.currentTimeMillis();
		this.measurements = new ArrayList<>(measurements);
		
		
		return true;
	}
	
	
	
	protected void createAndTrainClassifier() {
		
		// Create the required Mats
		int nMeasurements = measurements.size();
		
		
		Mat matTraining = new Mat(arrayTraining.length / nMeasurements, nMeasurements, CV_32FC1);
		((FloatBuffer)matTraining.createBuffer()).put(arrayTraining);
		Mat matResponses = new Mat(arrayResponses.length, 1, CV_32SC1);
		((IntBuffer)matResponses.createBuffer()).put(arrayResponses);
		
//		// Clear any existing classifier
//		if (classifier != null)
//			classifier.clear();
		
		logger.info("Training size: " + matTraining.size());
		logger.info("Responses size: " + matResponses.size());
		
		// Create & train the classifier
		try {
			// Some classifiers (e.g. RTrees) require the global RNG to be set before training.
			// We can't trust OpenCV to be consistent in this between versions, so set it here -
			// see https://github.com/qupath/qupath/issues/567
			// Note: we set it before training so that createClassifier() could potentially override our setting.
			opencv_core.setRNGSeed(-1);
			classifier = createClassifier();
			classifier.train(matTraining, ROW_SAMPLE, matResponses);
		} catch (Exception e) {
			// For reasons I haven't yet discerned, sometimes OpenCV throws an exception with the following message:
			// OpenCV Error: Assertion failed ((int)_sleft.size() < n && (int)_sright.size() < n) in calcDir, file /tmp/opencv320150620-1681-1u5iwhh/opencv-3.0.0/modules/ml/src/tree.cpp, line 1190
			// With one sample fewer, it can often recover... so attempt that, rather than failing miserably...
//			logger.error("Classifier training error", e);
			logger.info("Will attempt retraining classifier with one sample fewer...");
			matTraining = matTraining.rowRange(0, matTraining.rows()-1);
			matResponses = matResponses.rowRange(0, matResponses.rows()-1);
			classifier = createClassifier();
			classifier.train(matTraining, ROW_SAMPLE, matResponses);			
		}
		
		matTraining.close();
		matResponses.close();
		
		logger.info("Classifier trained with " + arrayResponses.length + " samples");
	}
	

	@Override
	public List<String> getRequiredMeasurements() {
		return new ArrayList<>(measurements);
	}

	@Override
	public Collection<PathClass> getPathClasses() {
		return new ArrayList<>(pathClasses);
	}

	@Override
	public boolean isValid() {
		return classifier != null && classifier.isTrained();
	}
	
	
	@Override
	public int classifyPathObjects(Collection<PathObject> pathObjects) {
		
		
		int counter = 0;
		float[] array = new float[measurements.size()];
		Mat samples = new Mat(1, array.length, CV_32FC1);
		FloatBuffer bufferSamples = samples.createBuffer();

		Mat results = new Mat();

		for (PathObject pathObject : pathObjects) {
			MeasurementList measurementList = pathObject.getMeasurementList();
			int idx = 0;
			for (String m : measurements) {
				double value = measurementList.getMeasurementValue(m);
				
				if (normScale != null && normOffset != null)
					value = (value + normOffset[idx]) * normScale[idx];
				
				array[idx] = (float)value;
				idx++;
			}
			
//			FloatIndexer indexerSamples = samples.createIndexer();
//			indexerSamples.put(0L, 0L, array);
			bufferSamples.clear();
			bufferSamples.put(array);
			
			try {
				setPredictedClass(classifier, pathClasses, samples, results, pathObject);
//				float prediction = classifier.predict(samples);
//				
////				float prediction2 = classifier.predict(samples, results, StatModel.RAW_OUTPUT);
//				float prediction2 = classifier.predict(samples, results, StatModel.RAW_OUTPUT);
//				
//				pathObject.setPathClass(pathClasses.get((int)prediction), prediction2);
				} catch (Exception e) {
					pathObject.setPathClass(null);
					logger.trace("Error with samples: {}", samples);
//					e.printStackTrace();
				}
			// TODO: See if this can be created outside the loop & reused... appears to work, but docs say release should be called
//			indexerSamples.release();
//			}
			counter++;
		}
		
		samples.close();
		results.close();
				
		return counter;
	}
	
	
	/**
	 * Default prediction method.  Makes no attempt to populate results matrix or to provide probabilities.
	 * (Results matrix only given as a parameter in case it is needed)
	 * 
	 * Subclasses may choose to override this method if they can do a better prediction, e.g. providing probabilities as well.
	 * 
	 * Upon returning, it is assumed that the PathClass of the PathObject will be correct, but it is not assumed that the results matrix will
	 * have been updated.
	 * 
	 * @param classifier
	 * @param pathClasses
	 * @param samples
	 * @param results
	 * @param pathObject
	 */
	protected void setPredictedClass(final T classifier, final List<PathClass> pathClasses, final Mat samples, final Mat results, final PathObject pathObject) {
		float prediction = classifier.predict(samples);
		PathClass pathClass = pathClasses.get((int)prediction);
		pathObject.setPathClass(pathClass);
	}
	
	
	/**
	 * Create a new classifier, of whichever type the subclass desires.
	 * 
	 * It can be assumed that this is the classifier that will be used - without modifications - until createClassifier is called again.
	 * 
	 * In other words, it is permissible to cache values within createClassifier() (e.g. TermCriteria) that might
	 * be import during prediction.
	 * 
	 * @return
	 */
	protected abstract T createClassifier();
	
	
	

//	@Override
//	public int classifyPathObjects(Collection<PathObject> pathObjects) {
//		
//		
//		int counter = 0;
//		Mat samples = new Mat(1, measurements.size(), CvType.CV_32FC1);
//		
//		for (PathObject pathObject : pathObjects) {
//			MeasurementList measurementList = pathObject.getMeasurementList();
//			int idx = 0;
//			for (String m : measurements) {
//				double value = measurementList.getMeasurementValue(m);
//				samples.put(0, idx, value);
//				idx++;
//			}
//			
//			float prediction = trees.predict(samples);
//			
////			if (computeProbabilities) {
////				double prediction = svm.svm_predict_probability(model, nodes, probabilities);
////				int index = (int)prediction;
////				pathObject.setPathClass(pathClasses.get(index), probabilities[index]);
////			} else {
////				double prediction = svm.svm_predict(model, nodes);
//				pathObject.setPathClass(pathClasses.get((int)prediction));
////			}
//			counter++;
//		}
//				
//		return counter;
//	}

	
	@Override
	public String getDescription() {
		
		if (classifier == null)
			return "No classifier set!";
		
		StringBuilder sb = new StringBuilder();
		String mainString = getName() + (!isValid() ? " (not trained)" : "");;
		sb.append("Classifier:\t").append(mainString).append("\n\n");
		sb.append("Classes:\t[");
		Iterator<PathClass> iterClasses = getPathClasses().iterator();
		while (iterClasses.hasNext()) {
			sb.append(iterClasses.next());			
			if (iterClasses.hasNext())
				sb.append(", ");
			else
				sb.append("]\n\n");
		}
		sb.append("Normalization:\t").append(normalization).append("\n\n");
		
		if (this instanceof Parameterizable) {
			ParameterList params = ((Parameterizable)this).getParameterList();
			String paramString = ParameterList.getParameterListJSON(params, "\n  ");
			sb.append("Main parameters:\n  ").append(paramString);
			sb.append("\n\n");
		}
		
		
		List<String> measurements = getRequiredMeasurements();
		sb.append("Required measurements (").append(measurements.size()).append("):\n");
		Iterator<String> iter = getRequiredMeasurements().iterator();
		while (iter.hasNext()) {
			sb.append("    ");
			sb.append(iter.next());			
			sb.append("\n");
		}
		
//		sb.append("\n");
//		sb.append(classifier.toString());
		
		return sb.toString();
//		return getName() + (!isValid() ? " (not trained)" : "");
	}
	

	@Override
	public long getLastModifiedTimestamp() {
		return timestamp;
	}

	
	
	
	
	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeLong(2); // Version
		out.writeLong(timestamp);
		out.writeObject(pathClasses);
		out.writeObject(normScale);
		out.writeObject(normOffset);
		out.writeObject(measurements);
		out.writeObject(arrayTraining);
		out.writeObject(arrayResponses);
		out.writeObject(normalization.toString());
	}


	@SuppressWarnings("unchecked")
	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		
		long version = in.readLong();
		if (version < 1 || version > 2)
			throw new IOException("Unsupported version!");
		
		timestamp = in.readLong();
		pathClasses = (List<PathClass>)in.readObject();
		// Ensure we have correct, single entries
		if (pathClasses != null) {
			for (int i = 0; i < pathClasses.size(); i++) {
				pathClasses.set(i, PathClassFactory.getSingletonPathClass(pathClasses.get(i)));
			}
		}
		
		normScale = (double[])in.readObject();
		normOffset = (double[])in.readObject();
		measurements = (List<String>)in.readObject();
		arrayTraining = (float[])in.readObject();
		arrayResponses = (int[])in.readObject();
		if (version == 2) {
			String method = (String)in.readObject();
			for (Normalization n : Normalization.values()) {
				if (n.toString().equals(method)) {
					normalization = n;
					break;
				}
			}
//			normalization = Normalization.valueOf((String)in.readObject());
		}
		
		if (arrayTraining != null && arrayResponses != null) {
			createAndTrainClassifier();
		}
		
	}

	
	
	

}
