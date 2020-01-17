package qupath.opencv.ml.objects;

import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.javacpp.indexer.IntIndexer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

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

public class OpenCVMLClassifier {

	final private static Logger logger = LoggerFactory.getLogger(OpenCVMLClassifier.class);
	
	/**
	 * Choose which objects are supportsed (often detections)
	 */
	private PathObjectFilter filter;
	
	/**
	 * Extract features from objects
	 */
	private FeatureExtractor featureExtractor;

	/**
	 * Object classifier
	 */
	private OpenCVStatModel classifier;

	/**
	 * Supported classifications - this is an ordered list, required to interpret labels
	 */
	private List<PathClass> pathClasses;

	/**
	 * Timestamp representing when the classifier was created/trained
	 */
	private long timestamp = System.currentTimeMillis();

	
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
	

	OpenCVMLClassifier() {}

	OpenCVMLClassifier(OpenCVStatModel classifier, PathObjectFilter filter,
			FeatureExtractor extractor, List<PathClass> pathClasses) {
		this.classifier = classifier;
		this.filter = filter;
		this.featureExtractor = extractor;
		this.pathClasses = new ArrayList<>(pathClasses);
		this.timestamp = System.currentTimeMillis();
	}
	
	public static OpenCVMLClassifier create(OpenCVStatModel model, PathObjectFilter filter
			, FeatureExtractor extractor, List<PathClass> pathClasses) {
		return new OpenCVMLClassifier(model, filter, extractor, pathClasses);
	}

	public Collection<PathClass> getPathClasses() {
		return pathClasses == null ? Collections.emptyList() : Collections.unmodifiableList(pathClasses);
	}
	
		
	public int classifyObjects(ImageData<BufferedImage> imageData) {
		var pathObjects = imageData.getHierarchy().getFlattenedObjectList(null);
		if (filter != null)
			pathObjects = pathObjects.stream().filter(filter).collect(Collectors.toList());
		return classifyObjects(imageData, pathObjects);
	}
	

	public int classifyObjects(ImageData<BufferedImage> imageData, Collection<PathObject> pathObjects) {

		if (featureExtractor == null) {
			logger.warn("No feature extractor! Cannot classify {} objects", pathObjects.size());
			return 0;
		}
		
		int counter = 0;
		
		List<Reclassifier> reclassifiers = new ArrayList<>();

		// Try not to have more than ~1 million entries per list
		int subListSize = (int)Math.max(1, Math.min(pathObjects.size(), (1024 * 1024 / featureExtractor.nFeatures())));
		
		Mat samples = new Mat();
		
		Mat results = new Mat();
		Mat probabilities = new Mat();

		// Work through the objects in chunks
		long startTime = System.currentTimeMillis();
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
			if (intermediateTime - startTime > 1000L) {
				logger.info("Calculated features for {}/{} objects in {} ms ({} ms per object, {}% complete)", nComplete, pathObjects.size(), 
						(intermediateTime - startTime),
						GeneralTools.formatNumber((intermediateTime - startTime)/(double)nComplete, 2),
						GeneralTools.formatNumber(nComplete * 100.0 / pathObjects.size(), 1));
			}
			
			boolean doMulticlass = classifier.supportsMulticlass();
			double threshold = 0.5;

			try {
				classifier.predict(samples, results, probabilities);

				IntIndexer idxResults = results.createIndexer();
				FloatIndexer idxProbabilities = null;
				if (!probabilities.empty())
					idxProbabilities = probabilities.createIndexer();

				if (doMulticlass && idxProbabilities != null) {
					// Use probabilities if we require multiclass outputs
					long row = 0;
					int nCols = (int)idxProbabilities.cols();
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
						if (PathClassTools.isIgnoredClass(pathClass))
							pathClass = null;
						reclassifiers.add(new Reclassifier(pathObject, pathClass, false));
						row++;
					}
				} else {
					// Use results (indexed values) if we do not require multiclass outputs
					long row = 0;
					for (var pathObject : tempObjectList) {
						int prediction = idxResults.get(row);
						var pathClass = pathClasses.get(prediction);
						if (PathClassTools.isIgnoredClass(pathClass))
							pathClass = null;
						if (idxProbabilities == null)
							reclassifiers.add(new Reclassifier(pathObject, pathClass, true));
						else
							reclassifiers.add(new Reclassifier(pathObject, pathClass, true, idxProbabilities.get(row, prediction)));							
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

		samples.release();
		results.release();
		probabilities.release();

		// Apply classifications now
		reclassifiers.stream().forEach(p -> p.apply());

		return counter;
	}


}