package qupath.lib.classifiers.opencv;

import static org.bytedeco.javacpp.opencv_core.CV_32FC1;
import static org.bytedeco.javacpp.opencv_core.CV_32SC1;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.javacpp.indexer.IntIndexer;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

import qupath.lib.analysis.stats.RunningStatistics;
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
import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.plugins.parameters.Parameterizable;

public class OpenCVMLClassifier implements PathObjectClassifier, Parameterizable {

	final private static Logger logger = LoggerFactory.getLogger(OpenCVMLClassifier.class);

	private OpenCVStatModel classifier;

	private Normalization normalization = Normalization.NONE;
	private List<PathClass> pathClasses;

	private Normalizer normalizer;
	private PCAProjector pca;

	private List<String> measurements = new ArrayList<>();

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
		return Collections.unmodifiableList(measurements);
	}

	@Override
	public Collection<PathClass> getPathClasses() {
		return pathClasses == null ? Collections.emptyList() : Collections.unmodifiableList(pathClasses);
	}

	@Override
	public boolean isValid() {
		return classifier.isTrained();
	}

	@Override
	public boolean updateClassifier(Map<PathClass, List<PathObject>> map, List<String> measurements,
			Normalization normalization) {

		// There is a chance we don't need to retrain... to find out, cache the most important current variables
		boolean maybeSameClassifier = isValid() && 
				this.normalization == normalization && 
				//					!classifierOptionsChanged() && 
				this.measurements.equals(measurements) 
				&& pathClasses.size() == map.size() && 
				map.keySet().containsAll(pathClasses);

		float[] arrayTrainingPrevious = null;//arrayTraining;
		int[] arrayResponsesPrevious = null;//arrayResponses;

		pathClasses = new ArrayList<>(map.keySet());
		Collections.sort(pathClasses);

		int n = 0;
		for (Map.Entry<PathClass, List<PathObject>> entry : map.entrySet()) {
			n += entry.getValue().size();
		}

		// Compute running statistics for normalization
		Map<String, RunningStatistics> statsMap = new LinkedHashMap<>();
		for (String m : measurements)
			statsMap.put(m, new RunningStatistics());


		this.measurements.clear();
		this.measurements.addAll(measurements);
		int nMeasurements = measurements.size();
		float[] arrayTraining = new float[n * nMeasurements];
		int[] arrayResponses = new int[n];

		int row = 0;
		int numMissing = 0;
		for (PathClass pathClass : pathClasses) {
			List<PathObject> list = map.get(pathClass);
			int classIndex = pathClasses.indexOf(pathClass);
			for (int i = 0; i < list.size(); i++) {
				MeasurementList measurementList = list.get(i).getMeasurementList();
				int col = 0;
				for (String m : measurements) {
					double value = measurementList.getMeasurementValue(m);
					if (!Double.isFinite(value))
						numMissing++;
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
		float fillMissingValues = Float.NaN;
		if (normalization != null && normalization != Normalization.NONE) {
			logger.debug("Training classifier with normalization: {}", normalization);
			int numMeasurements = measurements.size();
			double[] normOffset = new double[numMeasurements];
			double[] normScale = new double[numMeasurements];
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
			boolean doFillMissing = !classifier.supportsMissingValues();
			if (doFillMissing)
				fillMissingValues = 0f;
			for (int i = 0; i < arrayTraining.length; i++) {
				int k = i % numMeasurements;
				float val = arrayTraining[i];
				// Fill missing values with zero, if needed
				if (doFillMissing && !Float.isFinite(val))
					arrayTraining[i] = fillMissingValues;
				else
					arrayTraining[i] = (float)((val + normOffset[k]) * normScale[k]);
			}
			this.normalization = normalization;

			normalizer = new Normalizer(normOffset, normScale);

		} else {
			logger.debug("Training classifier without normalization");
			normalizer = null;
			this.normalization = Normalization.NONE;
		}


		// Record that we have NaNs, and whether or not we filled them in
		if (numMissing > 0) {
			if (!Float.isNaN(fillMissingValues)) {
				logger.warn("Training set contains {} NaN values, replaced by {} (after normalization)", numMissing, fillMissingValues);
			} else
				logger.warn("Training set contains {} NaN values (not replaced)", numMissing);
		}

		// Having got this far, check to see whether we really do need to retrain
		if (maybeSameClassifier) {
			if (Arrays.equals(arrayTrainingPrevious, arrayTraining) &&
					Arrays.equals(arrayResponsesPrevious, arrayResponses)) {
				logger.info("Classifier already trained with the same samples - existing classifier will be used");
				return false;
			}
		}

		createAndTrainClassifier(arrayTraining, arrayResponses);

		timestamp = System.currentTimeMillis();
		this.measurements = new ArrayList<>(measurements);

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


	protected void createAndTrainClassifier(float[] arrayTraining, int[] arrayResponses) {

		// Create the required Mats
		int nMeasurements = measurements.size();

		Mat matTraining = new Mat(arrayTraining.length / nMeasurements, nMeasurements, CV_32FC1);
		((FloatBuffer)matTraining.createBuffer()).put(arrayTraining);
		Mat matResponses = new Mat(arrayResponses.length, 1, CV_32SC1);
		((IntBuffer)matResponses.createBuffer()).put(arrayResponses);



		pca = new PCAProjector(matTraining, 0.99, true);
		pca.project(matTraining, matTraining);

		//			pca = null;

		logger.info("Training size: " + matTraining.size());
		logger.info("Responses size: " + matResponses.size());

		// Create & train the classifier
		classifier.train(matTraining, matResponses);

		matTraining.release();
		matResponses.release();

		logger.info("Classifier trained with " + arrayResponses.length + " samples");
	}



	@Override
	public int classifyPathObjects(Collection<PathObject> pathObjects) {

		boolean fillMissing = !classifier.supportsMissingValues();

		double missingValue = fillMissing ? 0.0 : Double.NaN;
		var extractor = new FeatureExtractor(measurements, normalizer, missingValue);

		int counter = 0;

		List<Reclassifier> reclassifiers = new ArrayList<>();

		// Try not to have more than ~1 million entries per list
		int subListSize = (int)(1024 * 1024 / extractor.nFeatures());

		Mat samples = new Mat(subListSize, measurements.size(), CV_32FC1);
		FloatBuffer bufferSamples = samples.createBuffer();

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


		for (var tempObjectList : Lists.partition(new ArrayList<>(pathObjects), subListSize)) {
			bufferSamples.clear();
			extractor.extractFeatures(tempObjectList, bufferSamples);

			if (pca != null)
				pca.project(samples, matPCA);

			try {
				classifier.predict(matSamplesInput, results, probabilities);

				//					var results2 = new Mat();
				//					classifier.predict(matSamplesInput, results2, null);
				//					opencv_core.compare(results, results2, results2, opencv_core.CMP_NE);
				//					int nnz = opencv_core.countNonZero(results2);
				//					System.err.println(nnz);
				//					assert nnz == 0;
				//					results2.release();

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
		sb.append("\nNum features: \t").append(measurements.size());
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