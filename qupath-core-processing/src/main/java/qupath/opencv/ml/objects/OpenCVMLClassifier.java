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

package qupath.opencv.ml.objects;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.javacpp.indexer.IntIndexer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

import qupath.lib.classifiers.object.AbstractObjectClassifier;
import qupath.lib.classifiers.object.ObjectClassifier;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectFilter;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.classes.PathClassTools;
import qupath.lib.objects.classes.Reclassifier;
import qupath.opencv.ml.objects.features.FeatureExtractor;
import qupath.opencv.ml.OpenCVClassifiers.OpenCVStatModel;

/**
 * An {@link ObjectClassifier} that uses an {@link OpenCVStatModel} for classification.
 * 
 * @author Pete Bankhead
 *
 * @param <T>
 */
public class OpenCVMLClassifier<T> extends AbstractObjectClassifier<T> {
	
	private final static Logger logger = LoggerFactory.getLogger(OpenCVMLClassifier.class);
	
	/**
	 * Extract features from objects
	 */
	private FeatureExtractor<T> featureExtractor;

	/**
	 * Object classifier
	 */
	private OpenCVStatModel classifier;

	/**
	 * Supported classifications - this is an ordered list, required to interpret labels
	 */
	private List<PathClass> pathClasses;
	
	/**
	 * Request whether probabilities should be estimated.
	 * This isn't supported for all stat models, and can make things substantially slower.
	 */
	private boolean requestProbabilityEstimate = false;

	
//	public static List<OpenCVStatModel> createDefaultStatModels() {
//		return Arrays.asList(
//				OpenCVClassifiers.createStatModel(RTrees.class),
//				OpenCVClassifiers.createStatModel(DTrees.class),
//				OpenCVClassifiers.createStatModel(Boost.class),
//				OpenCVClassifiers.createStatModel(ANN_MLP.class),
//				OpenCVClassifiers.createStatModel(EM.class),
//				OpenCVClassifiers.createStatModel(NormalBayesClassifier.class),
//				OpenCVClassifiers.createStatModel(KNearest.class),
//				OpenCVClassifiers.createStatModel(LogisticRegression.class),
//				OpenCVClassifiers.createStatModel(SVM.class)
//				);
//	}
	

	OpenCVMLClassifier(OpenCVStatModel classifier, PathObjectFilter filter,
			FeatureExtractor<T> extractor, List<PathClass> pathClasses) {
		super(filter);
		this.classifier = classifier;
		this.featureExtractor = extractor;
		this.pathClasses = new ArrayList<>(pathClasses);
	}
	
	/**
	 * Create a new {@link ObjectClassifier} that uses an {@link OpenCVStatModel} for classification.
	 * @param <T> generic type, which matches that of an {@link ImageData}
	 * @param model the {@link OpenCVStatModel} used to apply the prediction
	 * @param filter a filter used to select objects from a hierarchy
	 * @param extractor a feature extractor to determine features for each object
	 * @param pathClasses available classifications; the order is important, and relates to the classification output
	 * @return
	 */
	public static <T> ObjectClassifier<T> create(OpenCVStatModel model, PathObjectFilter filter,
			FeatureExtractor<T> extractor, List<PathClass> pathClasses) {
		return new OpenCVMLClassifier<>(model, filter, extractor, pathClasses);
	}

	@Override
	public Collection<PathClass> getPathClasses() {
		return pathClasses == null ? Collections.emptyList() : Collections.unmodifiableList(pathClasses);
	}
	
	@Override
	public int classifyObjects(ImageData<T> imageData, Collection<? extends PathObject> pathObjects, boolean resetExistingClass) {
		return classifyObjects(featureExtractor, classifier, pathClasses, imageData, pathObjects, resetExistingClass, requestProbabilityEstimate);
	}

	
	static <T> int classifyObjects(
			FeatureExtractor<T> featureExtractor,
			OpenCVStatModel classifier,
			List<PathClass> pathClasses,
			ImageData<T> imageData,
			Collection<? extends PathObject> pathObjects,
			boolean resetExistingClass,
			boolean requestProbabilityEstimate) {

		if (featureExtractor == null) {
			logger.warn("No feature extractor! Cannot classify {} objects", pathObjects.size());
			return 0;
		}
		
		int counter = 0;
		
		List<Reclassifier> reclassifiers = new ArrayList<>();

		// Try not to have more than ~10 million entries per list
		int subListSize = (int)Math.max(1, Math.min(pathObjects.size(), (1024 * 1024 * 10 / featureExtractor.nFeatures())));
		
		Mat samples = new Mat();
		
		Mat results = new Mat();
		Mat probabilities = requestProbabilityEstimate ? new Mat() : null;

		// Work through the objects in chunks
		long startTime = System.currentTimeMillis();
		long lastTime = startTime;
		int nComplete = 0;
		for (var tempObjectList : Lists.partition(new ArrayList<>(pathObjects), subListSize)) {

			if (Thread.interrupted()) {
				logger.warn("Classification interrupted - will not be applied");
				return 0;
			}
			
			samples.create(tempObjectList.size(), featureExtractor.nFeatures(), opencv_core.CV_32FC1);
			FloatBuffer buffer = samples.createBuffer();
			featureExtractor.extractFeatures(imageData, tempObjectList, buffer);
			
			// Possibly log time taken
			nComplete += tempObjectList.size();
			long intermediateTime = System.currentTimeMillis();
			if (intermediateTime - lastTime > 1000L) {
				logger.debug("Calculated features for {}/{} objects in {} ms ({} ms per object, {}% complete)", nComplete, pathObjects.size(), 
						(intermediateTime - startTime),
						GeneralTools.formatNumber((intermediateTime - startTime)/(double)nComplete, 2),
						GeneralTools.formatNumber(nComplete * 100.0 / pathObjects.size(), 1));
				lastTime = startTime;
			}
			
			boolean doMulticlass = classifier.supportsMulticlass();
			double threshold = 0.5;

			try {
				classifier.predict(samples, results, probabilities);

				IntIndexer idxResults = results.createIndexer();
				FloatIndexer idxProbabilities = null;
				if (probabilities != null && !probabilities.empty())
					idxProbabilities = probabilities.createIndexer();

				if (doMulticlass && idxProbabilities != null) {
					// Use probabilities if we require multiclass outputs
					long row = 0;
					int nCols = (int)idxProbabilities.size(2); // Previously .cols()
					List<String> classifications = new ArrayList<>();
					for (var pathObject : tempObjectList) {
						classifications.clear();
						for (int col = 0; col < nCols; col++) {
							double prob = idxProbabilities.get(row, col);
							if (prob >= threshold) {
								var pathClass = col >= pathClasses.size() ? null : pathClasses.get(col);
								if (pathClass != null)
									classifications.add(pathClass.getName());
							}
						}
						var pathClass = PathClassFactory.getPathClass(classifications);
						if (PathClassTools.isIgnoredClass(pathClass)) {
							pathClass = null;
						}
						if (!resetExistingClass) {
							pathClass = PathClassTools.mergeClasses(pathObject.getPathClass(), pathClass);
						}
						reclassifiers.add(new Reclassifier(pathObject, pathClass, false));
						row++;
					}
				} else {
					// Use results (indexed values) if we do not require multiclass outputs
					long row = 0;
					for (var pathObject : tempObjectList) {
						int prediction = idxResults.get(row);
						var pathClass = pathClasses.get(prediction);
						double probability = idxProbabilities == null ? Double.NaN : idxProbabilities.get(row, prediction);
						if (PathClassTools.isIgnoredClass(pathClass)) {
							pathClass = null;
							probability = Double.NaN;
						} 
						if (!resetExistingClass) {
							pathClass = PathClassTools.mergeClasses(pathObject.getPathClass(), pathClass);
							probability = Double.NaN;
						}
						reclassifiers.add(new Reclassifier(pathObject, pathClass, true, probability));							
						row++;
					}
				}
				idxResults.release();
				if (idxProbabilities != null)
					idxProbabilities.release();
			} catch (Exception e) {
				logger.warn("Error with samples: {}", samples);
				logger.error(e.getLocalizedMessage(), e);
			}
			counter += tempObjectList.size();
		}
		long predictTime = System.currentTimeMillis() - startTime;
		logger.info("Prediction time: {} ms for {} objects ({} ns per object)",
				predictTime, pathObjects.size(),
				GeneralTools.formatNumber((double)predictTime/pathObjects.size() * 1000.0, 2));

		samples.release();
		results.release();
		if (probabilities != null)
			probabilities.release();

		// Apply classifications now
		reclassifiers.stream().forEach(p -> p.apply());

		return counter;
	}
	
	@Override
	public String toString() {
		return String.format("OpenCV object classifier (%s, %d classes)", classifier.getName(), getPathClasses().size());
	}
	
	@Override
	public Map<String, Integer> getMissingFeatures(ImageData<T> imageData, Collection<? extends PathObject> pathObjects) {
		if (pathObjects == null)
			pathObjects = getCompatibleObjects(imageData);
		var missing = new LinkedHashMap<String, Integer>();
		var zero = Integer.valueOf(0);
		for (var pathObject : pathObjects) {
			for (var name : featureExtractor.getMissingFeatures(imageData, pathObject))
				missing.put(name, missing.getOrDefault(name, zero) + 1);
		}
		return missing;
	}


}