package qupath.lib.classifiers.opencv;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.javacpp.indexer.IntIndexer;
import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_core.Scalar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

import qupath.lib.classifiers.Normalization;
import qupath.lib.classifiers.PathObjectClassifier;
import qupath.lib.classifiers.opencv.OpenCVClassifiers.ANNClassifierCV;
import qupath.lib.classifiers.opencv.OpenCVClassifiers.BoostClassifier;
import qupath.lib.classifiers.opencv.OpenCVClassifiers.DTreesClassifier;
import qupath.lib.classifiers.opencv.OpenCVClassifiers.EMClusterer;
import qupath.lib.classifiers.opencv.OpenCVClassifiers.KNearestClassifierCV;
import qupath.lib.classifiers.opencv.OpenCVClassifiers.LogisticRegressionClassifier;
import qupath.lib.classifiers.opencv.OpenCVClassifiers.NormalBayesClassifierCV;
import qupath.lib.classifiers.opencv.OpenCVClassifiers.OpenCVStatModel;
import qupath.lib.classifiers.opencv.OpenCVClassifiers.RTreesClassifier;
import qupath.lib.classifiers.opencv.OpenCVClassifiers.SVMClassifierCV;
import qupath.lib.common.GeneralTools;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.plugins.parameters.Parameterizable;

public class OpenCVMLClassifier implements PathObjectClassifier, Parameterizable {

	final private static Logger logger = LoggerFactory.getLogger(OpenCVMLClassifier.class);
	
	public static OpenCVMLClassifier activeClassifier;

	private FeatureExtractor featureExtractor;
	
	private Normalizer normalizer;
	private PCAProjector pca;	
	
	private OpenCVStatModel classifier;

	private List<PathClass> pathClasses;

	private long timestamp = System.currentTimeMillis();

	
	public static List<PathObjectClassifier> createDefaultClassifiers() {
		return Arrays.asList(
				new RTreesClassifier(),
				new DTreesClassifier(),
				new BoostClassifier(),
				new ANNClassifierCV(),
				new EMClusterer(),
				new NormalBayesClassifierCV(),
				new KNearestClassifierCV(),
				new LogisticRegressionClassifier(),
				new SVMClassifierCV()
				).stream().map(c -> new OpenCVMLClassifier(c)).collect(Collectors.toList());
	}
	

	OpenCVMLClassifier() {}

	OpenCVMLClassifier(OpenCVStatModel classifier) {
		this.classifier = classifier;
	}

	@Override
	public List<String> getRequiredMeasurements() {
		return featureExtractor == null ? Collections.emptyList() : featureExtractor.getFeatureNames();
	}

	@Override
	public Collection<PathClass> getPathClasses() {
		return pathClasses == null ? Collections.emptyList() : Collections.unmodifiableList(pathClasses);
	}

	@Override
	public boolean isValid() {
		return classifier.isTrained();
	}
		
	/**
	 * For compatibility with existing interface
	 */
	@Override
	public boolean updateClassifier(Map<PathClass, List<PathObject>> map, List<String> measurements,
			Normalization normalization) {
		return updateClassifier(map, new FeatureExtractor(measurements), normalization, -1);
	}
		
		
	public boolean updateClassifier(final Map<PathClass, List<PathObject>> map, 
			final FeatureExtractor extractor, final Normalization normalization, final double pcaRetainedVariance) {
						
		pathClasses = new ArrayList<>(map.keySet());
		Collections.sort(pathClasses);
		
		this.featureExtractor = extractor;
		
//		int w = 16;
//		double pixelSize = 2.0;
//		featureExtractor = new PixelFeatureExtractor(QuPathGUI.getInstance().getImageData().getServer(), w, w, pixelSize);

		int nFeatures = featureExtractor.nFeatures();
		int nSamples = map.values().stream().mapToInt(l -> l.size()).sum();

		var matFeatures = new Mat(nSamples, nFeatures, opencv_core.CV_32FC1);
		FloatBuffer buffer = matFeatures.createBuffer();
		var matTargets = new Mat(nSamples, 1, opencv_core.CV_32SC1, Scalar.ZERO);
		IntBuffer bufTargets = matTargets.createBuffer();

		for (var entry : map.entrySet()) {
			// Extract (unnormalized) features
			var pathClass = entry.getKey();
			var pathObjects = entry.getValue();
			featureExtractor.extractFeatures(pathObjects, buffer);
			// Update targets
			int pathClassIndex = pathClasses.indexOf(pathClass);
			for (int i = 0; i < pathObjects.size(); i++)
				bufTargets.put(pathClassIndex);
		}
		
		// Create & apply feature normalizer
		if (classifier.supportsMissingValues() && normalization == Normalization.NONE) {
			normalizer = null;
		} else {
			double missingValue = classifier.supportsMissingValues() ? Double.NaN : 0.0;
			normalizer = Normalizer.createNormalizer(normalization, matFeatures, missingValue);
			Normalizer.normalize(matFeatures, normalizer);
		}
		
		// Create a PCA projector, if needed
		if (pcaRetainedVariance > 0) {
			pca = new PCAProjector(matFeatures, pcaRetainedVariance, true);
			pca.project(matFeatures, matFeatures);			
		} else {
			pca = null;
		}
		
		// Train classifier
		classifier.train(matFeatures, matTargets);
		
		logger.info("Classifier trained with " + matFeatures.rows() + " samples and " + matFeatures.cols() + " features");
		
		
		// TODO: Remove this... it is a hack for testing...
		activeClassifier = this;
		
		timestamp = System.currentTimeMillis();
		if (classifier instanceof RTreesClassifier) {
			tryLoggingVariableImportance((RTreesClassifier)classifier);
		}
		return true;
	}


	boolean tryLoggingVariableImportance(final RTreesClassifier trees) {
		var importance = trees.getFeatureImportance();
		if (importance == null)
			return false;
		var sorted = IntStream.range(0, importance.length)
				.boxed()
				.sorted((a, b) -> -Double.compare(importance[a], importance[b]))
				.mapToInt(i -> i).toArray();

		var sb = new StringBuilder("Variable importance:");
		if (pca == null) {
			var measurements = featureExtractor.getFeatureNames();
			for (int ind : sorted) {
				sb.append("\n");
				sb.append(String.format("%.4f \t %s", importance[ind], measurements.get(ind)));
			}
		} else {
			for (int ind : sorted) {
				sb.append("\n");
				sb.append(String.format("%.4f \t PCA %d", importance[ind], ind+1));
			}
		}					
		logger.info(sb.toString());
		return true;
	}

	
	
	void extractAndNormalizeFeatures(final List<PathObject> pathObjects, final Mat features) {
		features.create(pathObjects.size(), featureExtractor.nFeatures(), opencv_core.CV_32FC1);
		FloatBuffer buffer = features.createBuffer();
		featureExtractor.extractFeatures(pathObjects, buffer);
		if (normalizer != null)
			Normalizer.normalize(features, normalizer);
	}
	
	

	@Override
	public int classifyPathObjects(Collection<PathObject> pathObjects) {

		if (featureExtractor == null) {
			logger.warn("No feature extractor!  Cannot classify {} objects", pathObjects.size());
			return 0;
		}
		
		int counter = 0;
		
		List<Reclassifier> reclassifiers = new ArrayList<>();

		// Try not to have more than ~1 million entries per list
		int subListSize = (int)Math.max(1, Math.min(pathObjects.size(), (1024 * 1024 / featureExtractor.nFeatures())));
		
		Mat samples = new Mat();
		
		Mat results = new Mat();
		Mat probabilities = new Mat();

		Mat matPCA = null;
		Mat matSamplesInput;
		if (pca != null) {
			matPCA = new Mat();
			matSamplesInput = matPCA;
		} else {
			matSamplesInput = samples;
		}

		// Work through the objects in chunks
		long startTime = System.currentTimeMillis();
		int nComplete = 0;
		for (var tempObjectList : Lists.partition(new ArrayList<>(pathObjects), subListSize)) {

			if (Thread.interrupted()) {
				logger.warn("Classification interrupted - will not be applied");
				return 0;
			}
			extractAndNormalizeFeatures(tempObjectList, samples);
			
			// Possibly log time taken
			nComplete += tempObjectList.size();
			long intermediateTime = System.currentTimeMillis();
			if (intermediateTime - startTime > 1000L) {
				logger.info("Calculated features for {}/{} objects in {} ms ({} ms per object, {}% complete)", nComplete, pathObjects.size(), 
						(intermediateTime - startTime),
						GeneralTools.formatNumber((intermediateTime - startTime)/(double)nComplete, 2),
						GeneralTools.formatNumber(nComplete * 100.0 / pathObjects.size(), 1));
			}

			if (pca != null)
				pca.project(samples, matPCA);

			try {
				classifier.predict(matSamplesInput, results, probabilities);

				IntIndexer idxResults = results.createIndexer();
				FloatIndexer idxProbabilities = null;
				if (!probabilities.empty())
					idxProbabilities = probabilities.createIndexer();
				long row = 0;
				for (var pathObject : tempObjectList) {
					int prediction = idxResults.get(row);
					var pathClass = pathClasses.get(prediction);
					if (idxProbabilities == null)
						reclassifiers.add(new Reclassifier(pathObject, pathClass));
					else
						reclassifiers.add(new Reclassifier(pathObject, pathClass, idxProbabilities.get(row, prediction)));							
					row++;
				}
				idxResults.release();
				if (idxProbabilities != null)
					idxProbabilities.release();
				//					calculatePredictedClass(classifier.getStatModel(), pathClasses, matSamplesInput, tempObjectList, reclassifiers);
			} catch (Exception e) {
				logger.warn("Error with samples: {}", samples);
			}
			counter += tempObjectList.size();
		}

		if (matPCA != null)
			matPCA.release();
		samples.release();
		results.release();
		probabilities.release();

		// Apply classifications now
		reclassifiers.stream().forEach(p -> p.apply());

		return counter;
	}


	@Override
	public String getDescription() {
		if (!classifier.isTrained())
			return classifier.getName() + " (not trained)";
		var sb = new StringBuilder();
		sb.append("Classifier:     \t").append(classifier.getName());
		sb.append("\nNum features: \t").append(getRequiredMeasurements().size());
		String time = DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(timestamp));
		sb.append("\nTrained at:   \t").append(time);
		return sb.toString();
	}

	@Override
	public String getName() {
		return classifier.getName();
	}

	@Override
	public long getLastModifiedTimestamp() {
		return timestamp;
	}

	@Override
	public boolean supportsAutoUpdate() {
		return classifier.supportsAutoUpdate();
	}

	@Override
	public ParameterList getParameterList() {
		return classifier.getParameterList();
	}


}