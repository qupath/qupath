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

package qupath.process.gui.commands;

import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.bytedeco.javacpp.indexer.UByteIndexer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_ml.ANN_MLP;
import org.bytedeco.opencv.opencv_ml.KNearest;
import org.bytedeco.opencv.opencv_ml.RTrees;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import qupath.lib.classifiers.Normalization;
import qupath.lib.classifiers.PathClassifierTools;
import qupath.lib.classifiers.object.ObjectClassifier;
import qupath.lib.classifiers.object.ObjectClassifiers;
import qupath.lib.common.GeneralTools;
import qupath.lib.common.ThreadTools;
import qupath.lib.geom.Point2;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.charts.ChartTools;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.dialogs.ProjectDialogs;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectFilter;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.projects.ProjectImageEntry;
import qupath.opencv.ml.OpenCVClassifiers;
import qupath.opencv.ml.OpenCVClassifiers.OpenCVStatModel;
import qupath.opencv.ml.OpenCVClassifiers.RTreesClassifier;
import qupath.opencv.ml.objects.OpenCVMLClassifier;
import qupath.opencv.ml.objects.features.FeatureExtractor;
import qupath.opencv.ml.objects.features.FeatureExtractors;
import qupath.opencv.ml.objects.features.Preprocessing;
import qupath.opencv.tools.OpenCVTools;
import qupath.process.gui.ml.ProjectClassifierBindings;


/**
 * Command used to create and show a suitable dialog box for interactive display of OpenCV classifiers.
 * <p>
 * This is intended as a replacement for 'Create detection classifier' in QuPath v0.1.2, supporting better 
 * classifier options and serialization.
 * 
 * @author Pete Bankhead
 *
 */
public class ObjectClassifierCommand implements Runnable {

	final private static String name = "Train object classifier";

	private QuPathGUI qupath;

	// TODO: Check use of static dialog
	private Stage dialog;
	//	private ClassifierBuilderPanel<PathObjectClassifier> panel;

	/**
	 * Constructor.
	 * @param qupath
	 */
	public ObjectClassifierCommand(final QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
		if (dialog == null) {
			dialog = new Stage();
			if (qupath != null)
				dialog.initOwner(qupath.getStage());
			dialog.setTitle(name);

			BorderPane pane = new BorderPane();
			var panel = new ObjectClassifierPane(qupath);
			pane.setCenter(panel.getPane());

			ScrollPane scrollPane = new ScrollPane(pane);
			scrollPane.setFitToWidth(true);
			scrollPane.setFitToHeight(true);
			dialog.setScene(new Scene(scrollPane));

			panel.registerListeners(qupath);
			dialog.setOnCloseRequest(e -> {
				dialog = null; // Reset the dialog so a new one will be created next time
				panel.cleanup(qupath);
			});
		} else
			dialog.requestFocus();

		dialog.sizeToScene();
		dialog.show();
	}


	//	/**
	//	 * Handle cleanup whenever a dialog should be closed (and forgotten)
	//	 */
	//	private void resetPanel() {
	//		if (panel == null)
	//			return;
	//		qupath.removeImageDataChangeListener(panel);
	//		panel.setImageData(qupath.getImageData(), null);
	//		if (dialog != null)
	//			dialog.setOnCloseRequest(null);
	//		dialog = null;
	//		panel = null;
	//	}



	static class ObjectClassifierPane implements ChangeListener<ImageData<BufferedImage>>, PathObjectHierarchyListener {

		private final static Logger logger = LoggerFactory.getLogger(ObjectClassifierPane.class);

		private QuPathGUI qupath;

		/**
		 * Use all classifications for training and prediction, or only some.
		 */
		private static enum OutputClasses { ALL, SELECTED;

			@Override
			public String toString() {
				switch(this) {
				case ALL:
					return "All classes";
				case SELECTED:
					return "Selected classes";
				default:
					throw new IllegalArgumentException();
				}
			}

		}

		/**
		 * Use all measurements for training and prediction, or only some.
		 */
		private static enum TrainingFeatures { ALL, SELECTED, FILTERED;

			@Override
			public String toString() {
				switch(this) {
				case ALL:
					return "All measurements";
				case SELECTED:
					return "Selected measurements";
				case FILTERED:
					return "Filtered by output classes";
				default:
					throw new IllegalArgumentException();
				}
			}

		}

		/**
		 * Specify kind of annotations to use for training
		 */
		private static enum TrainingAnnotations { ALL, ALL_UNLOCKED, POINTS, AREAS;

			@Override
			public String toString() {
				switch(this) {
				case ALL:
					return "All annotations";
				case ALL_UNLOCKED:
					return "Unlocked annotations";
				case POINTS:
					return "Points only";
				case AREAS:
					return "Areas only";
				default:
					throw new IllegalArgumentException();
				}
			}

		}


		/**
		 * Main GUI pane
		 */
		private GridPane pane;

		private ReadOnlyObjectProperty<PathObjectFilter> objectFilter;

		private ReadOnlyObjectProperty<OpenCVStatModel> selectedModel;

		private ReadOnlyObjectProperty<OutputClasses> outputClasses;
		private ReadOnlyObjectProperty<TrainingFeatures> trainingFeatures;

		private ReadOnlyObjectProperty<TrainingAnnotations> trainingAnnotations;

		private ObjectProperty<Normalization> normalization = new SimpleObjectProperty<>(Normalization.NONE);

		private DoubleProperty pcaRetainedVariance = new SimpleDoubleProperty(-1.0);

		//		private ObjectClassifier<BufferedImage> classifier;
		private Set<PathClass> selectedClasses = new HashSet<>();

		private Set<String> selectedMeasurements = new LinkedHashSet<>();

		/**
		 * Request that multiclass classification is used where possible
		 */
		private ReadOnlyBooleanProperty doMulticlass = new SimpleBooleanProperty(true);

		/**
		 * Text relevant to the current cursor location when over a viewer
		 */
		private StringProperty cursorLocation = new SimpleStringProperty();

		/**
		 * If true, update classification as automatically
		 */
		private BooleanProperty livePrediction = new SimpleBooleanProperty(false);


		/**
		 * Other images from which training annotations should be used
		 */
		private List<ProjectImageEntry<BufferedImage>> trainingEntries = new ArrayList<>();

		private Map<ProjectImageEntry<BufferedImage>, ImageData<BufferedImage>> trainingMap = new WeakHashMap<>();



		/**
		 * Visualization of the training object proportions
		 */
		private PieChart pieChart;

		private ExecutorService pool = Executors.newSingleThreadExecutor(ThreadTools.createThreadFactory("object-classifier", true));
		private FutureTask<ObjectClassifier<BufferedImage>> classifierTask;

		ObjectClassifierPane(QuPathGUI qupath) {
			this.qupath = qupath;
			selectedClasses.addAll(qupath.getAvailablePathClasses());
			initialize();
		}

		/**
		 * Flag that the classifier settings have changed.
		 * Prompt an update if 'live prediction' is requested, otherwise just stop and reset any current classification task.
		 */
		private void invalidateClassifier() {
			if (!Platform.isFxApplicationThread()) {
				logger.warn("invalidateClassifier() should only be called from the Application thread! I'll try to recover...");
				Platform.runLater(() -> invalidateClassifier());
				return;
			}
			if (classifierTask != null && !classifierTask.isDone())
				classifierTask.cancel(true);
			classifierTask = null;
			if (livePrediction.get()) {
				classifierTask = submitClassifierUpdateTask(true);
			}
		}

		/**
		 * Submit a classification update task, returning the task.
		 * The {@code get()} method can then be used to request the classifier 
		 * (may be null if the task could not create a classifier).
		 * @param doClassification if true, perform a classification after training the classifier.
		 * @return
		 */
		private FutureTask<ObjectClassifier<BufferedImage>> submitClassifierUpdateTask(boolean doClassification) {
			var task = createClassifierUpdateTask(true);
			if (task != null) {
				if (pool == null || pool.isShutdown()) {
					logger.error("No thread pool available to train classifier!");
					return null;
				} else
					pool.submit(task);
			}
			return task;
		}

		public Pane getPane() {
			return pane;
		}

		/**
		 * Extract training objects from a hierarchy, based upon the kind of annotations permitted for training.
		 * @param hierarchy
		 * @param training
		 * @return
		 */
		private static List<PathObject> getTrainingAnnotations(PathObjectHierarchy hierarchy, TrainingAnnotations training) {
			Predicate<PathObject> trainingFilter = (PathObject p) -> p.isAnnotation() && p.getPathClass() != null && p.hasROI();
			switch (training) {
			case AREAS:
				trainingFilter = trainingFilter.and(PathObjectFilter.ROI_AREA);
				break;
			case POINTS:
				trainingFilter = trainingFilter.and(PathObjectFilter.ROI_POINT);
				break;
			case ALL_UNLOCKED:
				trainingFilter = trainingFilter.and(PathObjectFilter.UNLOCKED);
				break;
			default:
				break;
			}
			var annotations = hierarchy.getAnnotationObjects();
			return annotations
					.stream()
					.filter(trainingFilter)
					.collect(Collectors.toList());
		}

		/**
		 * Get all the {@link ImageData} we need for training.
		 * @return
		 */
		private Collection<ImageData<BufferedImage>> getTrainingImageData() {
			// We use the current viewer to determine the image type
			var imageData = qupath.getImageData();
			if (imageData == null) {
				logger.warn("Cannot train classifier - a valid image needs to be open in the current viewer");
				return Collections.emptyList();
			}

			// Read annotations from all compatible images (which here means same channel names)
			List<ImageData<BufferedImage>> list = new ArrayList<>();
			for (var viewer : qupath.getViewers()) {
				var tempData = viewer.getImageData();
				if (tempData != null)
					list.add(tempData);
			}

			// Read any other requested images for the project
			if (!trainingEntries.isEmpty()) {
				var currentEntries = ProjectDialogs.getCurrentImages(qupath);
				for (var entry : trainingEntries) {
					try {
						if (currentEntries.contains(entry)) {
							logger.debug("Will not load data for {} - will use the training annotations from the open viewer");
							var tempData = trainingMap.remove(entry);
							if (tempData != null)
								tempData.getServer().close();
						} else {
							var tempData = trainingMap.get(entry);
							if (tempData == null) {
								tempData = entry.readImageData();
								trainingMap.put(entry, tempData);
							}
							list.add(tempData);
						}
					} catch (Exception e) {
						logger.error(e.getLocalizedMessage(), e);
					}
				}
			}

			return list;
		}

		
		
		private boolean promptToLoadTrainingImages() {
			var project = qupath.getProject();
			if (project == null) {
				Dialogs.showNoProjectError("Object classifier");
				return false;
			}
			
			var listView = ProjectDialogs.createImageChoicePane(qupath, project.getImageList(), trainingEntries,
					"Specified image is open!");
			
			var pane = new BorderPane(listView);
			pane.setTop(new Label("Select images to use for training the object classifier.\n"
					+ "Note that more images will require more memory and more processing time!"));
			
			if (Dialogs.builder()
					.title("Object classifier training images")
					.content(pane)
					.buttons(ButtonType.APPLY, ButtonType.CANCEL)
					.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.CANCEL)
				return false;
			
			trainingEntries.clear();
			trainingEntries.addAll(ProjectDialogs.getTargetItems(listView));
			
			return true;
		}
		

		/**
		 * Create a classifier training task based on the current GUI control values (but don't submit it for processing).
		 * @param doClassification
		 * @return
		 */
		private FutureTask<ObjectClassifier<BufferedImage>> createClassifierUpdateTask(boolean doClassification) {
			var filter = objectFilter.get();
			OpenCVStatModel statModel = selectedModel == null ? null : selectedModel.get();
			if (statModel == null) {
				logger.warn("No classifier - cannot update classifier");
				resetPieChart();
				return null;
			}

			var imageDataCollection = getTrainingImageData();
			if (imageDataCollection.isEmpty()) {
				logger.warn("No image - cannot update classifier");
				resetPieChart();
				return null;
			}

			// Get key parameters
			var annotations = trainingAnnotations.get();
			var output = outputClasses.get();
			var selectedClasses = new HashSet<>(this.selectedClasses);
			var norm = this.normalization.get();

			double pcaRetained = pcaRetainedVariance.get();
			boolean multiclass = doMulticlass.get() && statModel.supportsMulticlass();

			// Determine the measurements to use
			var measurements = getRequestedMeasurements();
			if (measurements.isEmpty()) {
				if (measurements.isEmpty()) {
					logger.warn("No measurements - cannot update classifier");
					return null;
				}
			}

			FeatureExtractor<BufferedImage> extractor = FeatureExtractors
					.createMeasurementListFeatureExtractor(measurements);

			return new FutureTask<>(() -> {
				var training = new ArrayList<TrainingData<BufferedImage>>();
				for (var imageData : imageDataCollection) {
					var temp = createTrainingData(
							filter,
							imageData,
							annotations,
							output == OutputClasses.ALL ? null : selectedClasses);
					training.add(temp);
				}

				if (training.isEmpty() || Thread.interrupted())
					return null;
				
				long nTrainingObjects = training.stream().mapToLong(t -> t.map.size()).sum();
				if (nTrainingObjects <= 1L) {
					Dialogs.showErrorNotification("Object classifier", "You need to annotate objects with at least two classifications to train a classifier!");
					return null;
				}

				var classifier = createClassifier(
						training,
						filter,
						statModel,
						extractor,
						norm,
						pcaRetained,
						multiclass
						);
				
				if (Thread.interrupted())
					return null;
				
				if (classifier == null) {
					Dialogs.showErrorNotification("Object classifier", "Unable to train object classifier with the current settings!");
					return null;
				}

				if (doClassification) {
					for (var viewer : qupath.getViewers()) {
						var imageData = viewer.getImageData();
						if (imageData != null) {
							var pathObjects = classifier.getCompatibleObjects(imageData);
							if (classifier.classifyObjects(imageData, pathObjects, true) > 0) {
								imageData.getHierarchy().fireObjectClassificationsChangedEvent(this, pathObjects);
							}
						}
					}
				}
				updatePieChart(training);
				return classifier;
			});
		}

			
		/**
		 * Extract training annotations from a collection of images.
		 * @param trainingImageData
		 * @return a collection of classified annotations suitable for training
		 */
		private Collection<PathObject> getTrainingAnnotations(Collection<ImageData<BufferedImage>> trainingImageData) {
			if (trainingImageData.isEmpty())
				return Collections.emptyList();
			var annotationType = this.trainingAnnotations.get();
			if (trainingImageData.size() == 1)
				return getTrainingAnnotations(trainingImageData.iterator().next().getHierarchy(), annotationType);
			
			var trainingAnnotations = new ArrayList<PathObject>();
			for (var imageData : trainingImageData) {
				trainingAnnotations.addAll(getTrainingAnnotations(imageData.getHierarchy(), annotationType));
			}
			return trainingAnnotations;
		}
		
		/**
		 * Extract training annotations from a collection of images.
		 * @param trainingImageData
		 * @param includeIntersection if true, retain only measurements found in all images
		 * @return a collection of measurement names
		 */
		private Collection<String> getAllMeasurements(Collection<ImageData<BufferedImage>> trainingImageData, boolean includeIntersection) {
			if (trainingImageData.isEmpty())
				return Collections.emptyList();
			
			var filter = objectFilter.get();
			if (trainingImageData.size() == 1) {
				return getAllMeasurements(trainingImageData.iterator().next(), filter);
			}
			var allMeasurements = new LinkedHashSet<String>();
			var firstMeasurements = new LinkedHashSet<String>();
			boolean firstChanges = false;
			for (var imageData : trainingImageData) {
				var newMeasurements = getAllMeasurements(imageData, filter);
				if (newMeasurements.isEmpty())
					continue;
				if (includeIntersection) {
					if (firstMeasurements.isEmpty()) {
						firstMeasurements.addAll(newMeasurements);
						allMeasurements.addAll(firstMeasurements);
					} else {
						if (allMeasurements.retainAll(newMeasurements) && firstChanges) {
							logger.warn("Different measurements found in different training images! Available measurements will be restricted to the intersection of these.");
						}
					}
				} else
					allMeasurements.addAll(newMeasurements);
			}
			return allMeasurements;
		}
		
		private Collection<String> getAllMeasurements(ImageData<?> imageData, PathObjectFilter filter) {
			var detections = imageData.getHierarchy()
					.getFlattenedObjectList(null)
					.stream()
					.filter(filter)
					.collect(Collectors.toList());
			return PathClassifierTools.getAvailableFeatures(detections);
		}
		

		private List<String> getRequestedMeasurements() {

			var trainingImageData = getTrainingImageData();
			if (trainingImageData.isEmpty())
				return Collections.emptyList();

			if (trainingFeatures.get() == TrainingFeatures.SELECTED)
				return new ArrayList<>(selectedMeasurements);

			// Get all the available measurements
			var allMeasurements = getAllMeasurements(trainingImageData, true);
			
			// Filter out the irrelevant measurements, if needed
			if (trainingFeatures.get() == TrainingFeatures.FILTERED) {
				var trainingAnnotations = getTrainingAnnotations(trainingImageData);
				var measurements = new ArrayList<String>();
				var filterText = trainingAnnotations.stream().distinct().map(a -> a.getPathClass().toString().toLowerCase().trim()).collect(Collectors.toSet());
				for (var m : allMeasurements) {
					for (var f : filterText) {
						if (m.toLowerCase().contains(f)) {
							measurements.add(m);
							break;
						}
					}
				}
				return measurements;
			} else
				return new ArrayList<>(allMeasurements);
		}



		/**
		 * Create a map of training data, with target classes as keys and collections of training objects as values.
		 * @param filter
		 * @param imageData
		 * @param training
		 * @param selectedClasses optional collection containing valid output classes; if null, all classes will be used
		 * @return
		 */
		private static <T> TrainingData<T> createTrainingData(
				PathObjectFilter filter,
				ImageData<T> imageData,
				TrainingAnnotations training,
				Collection<PathClass> selectedClasses) {

			Map<PathClass, Set<PathObject>> map = new TreeMap<>();

			// Get training annotations & associated objects
			var hierarchy = imageData.getHierarchy();
			var trainingAnnotations = getTrainingAnnotations(hierarchy, training);

			if (Thread.interrupted())
				return null;

			// Use a set for detections because we might need to check if we have the same detection for multiple classes
			var filterNegated = filter.negate();
			for (var annotation : trainingAnnotations) {
				var pathClass = annotation.getPathClass();
				if (selectedClasses == null || selectedClasses.contains(pathClass)) {
					var set = map.computeIfAbsent(pathClass, p -> new HashSet<>());
					var roi = annotation.getROI();
					if (roi.isPoint()) {
						for (Point2 p : annotation.getROI().getAllPoints()) {
							var pathObjectsTemp = PathObjectTools.getObjectsForLocation(
									hierarchy, p.getX(), p.getY(), roi.getZ(), roi.getT(), -1);
							pathObjectsTemp.removeIf(filterNegated);
							set.addAll(pathObjectsTemp);
						}
					} else {
						var pathObjectsTemp = hierarchy.getObjectsForROI(PathDetectionObject.class, annotation.getROI());
						pathObjectsTemp.removeIf(filterNegated);
						set.addAll(pathObjectsTemp);
					}
				}
			}

			map.entrySet().removeIf(e -> e.getValue().isEmpty());
			return new TrainingData<>(imageData, map);
		}

		/**
		 * Train an object classifier.
		 * 
		 * @param training training data, see {@link #getTrainingImageData()}
		 * @param filter filter to select compatible objects
		 * @param statModel OpenCV stat model to be trained
		 * @param extractor {@link FeatureExtractor} able to extract features from the training objects
		 * @param normalization type of normalization that should be applied
		 * @param pcaRetainedVariance variance to retain if PCA is applied to reduce features (not currently used or tested!)
		 * @param doMulticlass if true, try to create a multi-class classifier instead of a 'regular' classifier
		 * @return the trained object classifier, or null if insufficient information was provided or the thread was interrupted during training
		 */
		private static ObjectClassifier<BufferedImage> createClassifier(
				Collection<TrainingData<BufferedImage>> training,
				PathObjectFilter filter,
				OpenCVStatModel statModel,
				FeatureExtractor<BufferedImage> extractor,
				Normalization normalization,
				double pcaRetainedVariance,
				boolean doMulticlass) {

			var pathClasses = getPathClasses(training);
			
			if (pathClasses.isEmpty()) {
				logger.warn("No compatible training data found!");
				return null;
			}
			
			if (training.size() > 1)
				logger.info("Creating training data from {} images", training.size());

			extractor = updateFeatureExtractorAndTrainClassifier(
					statModel,
					training,
					extractor,
					normalization,
					pcaRetainedVariance,
					doMulticlass);

			return OpenCVMLClassifier
					.create(statModel, filter, extractor, pathClasses);
		}


		void resetPieChart() {
			updatePieChart(Collections.emptyMap());
		}

		<T> void updatePieChart(Collection<TrainingData<T>> training) {
			if (!Platform.isFxApplicationThread()) {
				Platform.runLater(() -> updatePieChart(training));
				return;
			}
			var counts = new LinkedHashMap<PathClass, Integer>();
			for (var t : training) {
				for (var entry : t.map.entrySet()) {
					var key = entry.getKey();
					Integer total = counts.getOrDefault(key, 0) + entry.getValue().size();
					counts.put(entry.getKey(), total);
				}
			}
			ChartTools.setPieChartData(pieChart, counts, PathClass::toString, p -> ColorToolsFX.getCachedColor(p.getColor()), true, !counts.isEmpty());
		}

		void updatePieChart(Map<PathClass, Set<PathObject>> map) {
			if (!Platform.isFxApplicationThread()) {
				Platform.runLater(() -> updatePieChart(map));
				return;
			}
			var counts = new LinkedHashMap<PathClass, Integer>();
			for (var entry : map.entrySet()) {
				counts.put(entry.getKey(), entry.getValue().size());
			}
			ChartTools.setPieChartData(pieChart, counts, PathClass::toString, p -> ColorToolsFX.getCachedColor(p.getColor()), true, !map.isEmpty());
		}



		static class TrainingData<T>{

			private ImageData<T> imageData;
			private Map<PathClass, Set<PathObject>> map;

			private TrainingData(ImageData<T> imageData, Map<PathClass, Set<PathObject>> map) {
				this.imageData = imageData;
				this.map = map;
			}

			public Collection<PathClass> getPathClasses() {
				return map.keySet();
			}

		}
		
		
		static <T> List<PathClass> getPathClasses(Collection<TrainingData<T>> training) {
			Set<PathClass> classSet = new HashSet<>();
			for (var t : training) {
				classSet.addAll(t.getPathClasses());
			}
			var pathClasses = new ArrayList<>(classSet);
			Collections.sort(pathClasses);
			return pathClasses;
		}



		/**
		 * Train a feature extractor and classifier.
		 * @param classifier
		 * @param trainingCollection
		 * @param extractor
		 * @param normalization
		 * @param pcaRetainedVariance
		 * @param doMulticlass
		 * @return the updated feature extractor, with any normalization/PCA reduction incorporated, 
		 * or null if the training was unsuccessful (e.g. it was interrupted)
		 */
		private static <T> FeatureExtractor<T> updateFeatureExtractorAndTrainClassifier(
				OpenCVStatModel classifier,
				Collection<TrainingData<T>> trainingCollection,
				FeatureExtractor<T> extractor,
				Normalization normalization,
				double pcaRetainedVariance,
				boolean doMulticlass) {

			var pathClasses = getPathClasses(trainingCollection);

			List<Mat> matFeaturesList = new ArrayList<>();
			List<Mat> matTargetsList = new ArrayList<>();

			for (var training : trainingCollection) {
				var imageData = training.imageData;
				var map = training.map;

				int nFeatures = extractor.nFeatures();
				int nSamples = map.values().stream().mapToInt(l -> l.size()).sum();
				int nClasses = pathClasses.size();
				if (nSamples == 0)
					continue;

				Mat matTargets;
				Mat matFeatures;
				if (doMulticlass) {
					// For multiclass, it's quite likely we have samples represented more than once
					var sampleSet = new LinkedHashSet<PathObject>();
					for (var entry : map.entrySet()) {
						sampleSet.addAll(entry.getValue());
					}
					nSamples = sampleSet.size();

					matFeatures = new Mat(nSamples, nFeatures, opencv_core.CV_32FC1);
					FloatBuffer buffer = matFeatures.createBuffer();
					matTargets = new Mat(nSamples, nClasses, opencv_core.CV_8UC1, Scalar.ZERO);
					UByteIndexer idxTargets = matTargets.createIndexer();

					extractor.extractFeatures(imageData, sampleSet, buffer);

					int row = 0;
					for (var sample : sampleSet) {
						for (int col = 0; col < nClasses; col++) {
							var pathClass = pathClasses.get(col);
							if (map.get(pathClass).contains(sample)) {
								idxTargets.put(row, col, 1);
							}
						}
						row++;
					}
					idxTargets.release();				
				} else {
					matFeatures = new Mat(nSamples, nFeatures, opencv_core.CV_32FC1);
					FloatBuffer buffer = matFeatures.createBuffer();

					matTargets = new Mat(nSamples, 1, opencv_core.CV_32SC1, Scalar.ZERO);
					IntBuffer bufTargets = matTargets.createBuffer();

					for (var entry : map.entrySet()) {
						// Extract features
						var pathClass = entry.getKey();
						var pathObjects = entry.getValue();
						extractor.extractFeatures(imageData, pathObjects, buffer);
						// Update targets
						int pathClassIndex = pathClasses.indexOf(pathClass);
						for (int i = 0; i < pathObjects.size(); i++)
							bufTargets.put(pathClassIndex);
					}
				}
				matFeaturesList.add(matFeatures);
				matTargetsList.add(matTargets);
			}
			
			if (matFeaturesList.isEmpty()) {
				logger.warn("No features found!");
				return null;
			}

			var matFeatures = OpenCVTools.vConcat(matFeaturesList, null);
			var matTargets = OpenCVTools.vConcat(matTargetsList, null);


			// Create & apply feature normalizer if we need one
			// We might even if normalization isn't requested so as to fill in missing values
			if (!(classifier.supportsMissingValues() && normalization == Normalization.NONE && pcaRetainedVariance < 0)) {
				double missingValue = classifier.supportsMissingValues() && pcaRetainedVariance < 0 ? Double.NaN : 0.0;
				var normalizer = Preprocessing.createNormalizer(normalization, matFeatures, missingValue);
				Preprocessing.normalize(matFeatures, normalizer);
				extractor = FeatureExtractors.createNormalizingFeatureExtractor(extractor, normalizer);
			}

			// Create a PCA projector, if needed
			if (pcaRetainedVariance > 0) {
				var pca = Preprocessing.createPCAProjector(matFeatures, pcaRetainedVariance, true);
				pca.project(matFeatures, matFeatures);	
				extractor = FeatureExtractors.createPCAProjectFeatureExtractor(extractor, pca);
			}

			// Quit now if we cancelled, before changing fields and doing the slow bits
			if (Thread.currentThread().isInterrupted()) {
				logger.warn("Classifier training interrupted!");
				matFeatures.close();
				matTargets.close();
				return null;
			}

			trainClassifier(classifier, matFeatures, matTargets, doMulticlass);

			if (classifier instanceof RTreesClassifier) {
				tryLoggingVariableImportance((RTreesClassifier)classifier, extractor);
			}

			matFeatures.close();
			matTargets.close();
			return extractor;
		}

		static boolean trainClassifier(OpenCVStatModel classifier, Mat matFeatures, Mat matTargets, boolean doMulticlass) {		
			// Train classifier
			// TODO: Optionally limit the number of training samples we use
			long startTime = System.currentTimeMillis();
			var trainData = classifier.createTrainData(matFeatures, matTargets, null, doMulticlass);
			classifier.train(trainData);
			long endTime = System.currentTimeMillis();
			logger.info("{} classifier trained with {} samples and {} features ({} ms)",
					classifier.getName(), matFeatures.rows(), matFeatures.cols(), endTime - startTime);
			return true;
		}


		static boolean tryLoggingVariableImportance(final RTreesClassifier trees, final FeatureExtractor<?> extractor) {
			var importance = trees.getFeatureImportance();
			if (importance == null)
				return false;
			var sorted = IntStream.range(0, importance.length)
					.boxed()
					.sorted((a, b) -> -Double.compare(importance[a], importance[b]))
					.mapToInt(i -> i).toArray();

			var names = extractor.getFeatureNames();
			var sb = new StringBuilder("Variable importance:");
			for (int ind : sorted) {
				sb.append("\n");
				sb.append(String.format("%.4f \t %s", importance[ind], names.get(ind)));
			}
			logger.info(sb.toString());
			return true;
		}




		private boolean showAdvancedOptions() {
			// TODO: Add PCA options

			//			int row = 0;
			//			var pane = new GridPane();
			//			
			//			var comboNormalization = new ComboBox<Normalization>();
			//			comboNormalization.getItems().setAll(Normalization.values());
			//			comboNormalization.getSelectionModel().select(normalization.get());
			//			var labelNormalization = new Label("Normalization");
			//			labelNormalization.setLabelFor(comboNormalization);
			//			
			//			PaneTools.addGridRow(pane, row++, 0,
			//					"Choose feature normalization",
			//					labelNormalization, comboNormalization);
			//
			//			var comboPCA = new ComboBox<String>();
			//			comboPCA.getItems().setAll("No PCA feature reduction", "PCA ");
			//			comboPCA.getSelectionModel().select(normalization.get());
			//			var labelNormalization = new Label("Normalization");
			//			labelNormalization.setLabelFor(comboNormalization);
			//			
			//			PaneTools.addGridRow(pane, row++, 0,
			//					"Choose feature normalization",
			//					labelNormalization, comboNormalization);

			var norm = Dialogs.showChoiceDialog("Advanced options", "Feature normalization", Normalization.values(), normalization.get());
			if (norm == null || norm == normalization.get())
				return false;
			normalization.set(norm);
			return true;
		}

		private boolean tryToSave(String name) {
			var classifierName = name == null ? "" : GeneralTools.stripInvalidFilenameChars(name);
			if (classifierName.isBlank()) {
				Dialogs.showErrorMessage("Object classifier", "Please enter a valid classifier name!");
				return false;
			}
			if (!classifierName.equals(name))
				logger.warn("Invalid classifier name '{}' replaced with '{}'", name, classifierName);

			// Run the classification, or complete the existing classification
			if (classifierTask == null) {
				classifierTask = submitClassifierUpdateTask(false);
				if (classifierTask == null)
					return false;
			}
			ObjectClassifier<BufferedImage> classifier;
			try {
				classifier = classifierTask.get();
			} catch (InterruptedException e1) {
				Dialogs.showErrorNotification(name, "Classifier training was interrupted!");
				return false;
			} catch (ExecutionException e1) {
				Dialogs.showErrorNotification(name, e1);
				return false;
			}
			if (classifier != null) {
				try {
					var project = qupath.getProject();
					if (project != null) {
						var allClassifiers = project.getObjectClassifiers();
						if (allClassifiers.contains(name) && !
								Dialogs.showConfirmDialog("Object classifiers", "Overwrite existing classifier \"" + classifierName + "\""))
							return false;
						project.getObjectClassifiers().put(classifierName, classifier);
						logger.info("Classifier saved to project as {}", classifierName);
					} else {
						var file = Dialogs.promptToSaveFile("Save object classifier", null, classifierName, "Object classifier", ".obj.json");
						if (file == null)
							return false;
						classifierName = file.getAbsolutePath();
						ObjectClassifiers.writeClassifier(classifier, file.toPath());
					}
					Dialogs.showInfoNotification("Object classifiers", "Saved classifier as \"" + classifierName + "\"");
					// We want to now apply classifier to all images & log to workflow
					// (might be redundant, but we do want to make sure that we are logging the classifier we applied)
					for (var viewer : qupath.getViewers()) {
						var imageData = viewer.getImageData();
						if (imageData != null) {
							classifier.classifyObjects(imageData, true);
							imageData.getHistoryWorkflow().addStep(
									ObjectClassifierLoadCommand.createObjectClassifierStep(classifierName));
						}
					}
					return true;
				} catch (Exception e) {
					logger.error("Error attempting to save classifier " + e.getLocalizedMessage(), e);
				}
			}
			Dialogs.showWarningNotification("Object classifiers", "Unable to save classifier!");
			return false;
		}

		private boolean editClassifierParameters() {
			var model = selectedModel.get();
			if (model == null) {
				Dialogs.showErrorMessage("Edit parameters", "No classifier selected!");
				return false;
			}
			Dialogs.showParameterDialog("Edit parameters", model.getParameterList());
			invalidateClassifier();
			return true;
		}

		private void initialize() {

			pane = new GridPane();
			int row = 0;

			/*
			 * Input object type
			 */
			var labelObjects = new Label("Object filter");
			var comboObjects = new ComboBox<PathObjectFilter>();
			comboObjects.getItems().addAll(
					PathObjectFilter.DETECTIONS_ALL,
					PathObjectFilter.DETECTIONS,
					PathObjectFilter.CELLS,
					PathObjectFilter.TILES
					);
			labelObjects.setLabelFor(comboObjects);
			objectFilter = comboObjects.getSelectionModel().selectedItemProperty();
			comboObjects.getSelectionModel().select(PathObjectFilter.DETECTIONS_ALL);
			objectFilter.addListener((v, o, n) -> invalidateClassifier());

			PaneTools.addGridRow(pane, row++, 0, 
					"Choose object type to classify (default is all detections)",
					labelObjects, comboObjects, comboObjects);

			/*
			 * Classifier type
			 */
			var labelClassifier = new Label("Classifier");
			var comboClassifier = new ComboBox<OpenCVStatModel>();
			comboClassifier.getItems().addAll(
					OpenCVClassifiers.createStatModel(RTrees.class),
					OpenCVClassifiers.createStatModel(ANN_MLP.class),
					//					OpenCVClassifiers.createMulticlassStatModel(ANN_MLP.class),
					OpenCVClassifiers.createStatModel(KNearest.class)
					);
			labelClassifier.setLabelFor(comboClassifier);
			selectedModel = comboClassifier.getSelectionModel().selectedItemProperty();
			comboClassifier.getSelectionModel().selectFirst();
			selectedModel.addListener((v, o, n) -> invalidateClassifier());
			var btnEditClassifier = new Button("Edit");
			btnEditClassifier.setMaxWidth(Double.MAX_VALUE);
			btnEditClassifier.setOnAction(e -> editClassifierParameters());
			btnEditClassifier.disableProperty().bind(selectedModel.isNull());

			PaneTools.addGridRow(pane, row++, 0, 
					"Choose classifier type (RTrees or ANN_MLP are generally good choices)",
					labelClassifier, comboClassifier, btnEditClassifier);

			/*
			 * Feature selection
			 */
			var labelFeatures = new Label("Features");
			var comboFeatures = new ComboBox<TrainingFeatures>();
			labelFeatures.setLabelFor(comboFeatures);
			comboFeatures.getItems().setAll(TrainingFeatures.values());
			comboFeatures.getSelectionModel().select(TrainingFeatures.ALL);
			labelFeatures.setLabelFor(comboFeatures);
			trainingFeatures = comboFeatures.getSelectionModel().selectedItemProperty();
			trainingFeatures.addListener(v -> invalidateClassifier());
			var btnSelectFeatures = new Button("Select");
			btnSelectFeatures.setMaxWidth(Double.MAX_VALUE);
			btnSelectFeatures.disableProperty().bind(
					trainingFeatures.isNotEqualTo(TrainingFeatures.SELECTED)
					);
			btnSelectFeatures.setOnAction(e -> {
				if (promptToSelectFeatures())
					invalidateClassifier();
			});
			PaneTools.addGridRow(pane, row++, 0, 
					null,
					labelFeatures, comboFeatures, btnSelectFeatures);

			var tooltipFeatures = new Tooltip();
			tooltipFeatures.setOnShowing(e -> {
				String text = "Select measurements for the classifier\n";
				if (trainingFeatures.get() == TrainingFeatures.ALL)
					text += "Currently, all available measurements will be used";
				else {
					var measurements = getRequestedMeasurements();
					if (measurements.isEmpty())
						text += "No measurements are currently selected - please choose some!";
					else
						text += "Current measurements: \n - " + String.join("\n - ", measurements);
				}
				tooltipFeatures.setText(text);
			});
			btnSelectFeatures.setTooltip(tooltipFeatures);
			comboFeatures.setTooltip(tooltipFeatures);

			/*
			 * Output classes
			 */
			var labelClasses = new Label("Classes");
			var comboClasses = new ComboBox<OutputClasses>();
			labelClasses.setLabelFor(comboClasses);
			comboClasses.getItems().setAll(OutputClasses.values());
			comboClasses.getSelectionModel().select(OutputClasses.ALL);
			labelClasses.setLabelFor(comboClasses);
			outputClasses = comboClasses.getSelectionModel().selectedItemProperty();
			outputClasses.addListener(v -> invalidateClassifier());
			var btnSelectClasses = new Button("Select");
			btnSelectClasses.setMaxWidth(Double.MAX_VALUE);
			btnSelectClasses.disableProperty().bind(
					outputClasses.isEqualTo(OutputClasses.ALL)
					);
			btnSelectClasses.setOnAction(e -> {
				if (promptToSelectClasses()) {
					invalidateClassifier();
				}
			});
			var tooltipClasses = new Tooltip();
			tooltipClasses.setOnShowing(e -> {
				String text = "Choose which classes to use when training the classifier\n";
				if (outputClasses.get() == OutputClasses.SELECTED) {
					if (selectedClasses.isEmpty())
						text += "No classes are currently selected - please choose some!";
					else
						text += "Current classes (where available): \n - " + selectedClasses.stream().map(c -> c == null ? "Unclassified" : c.toString()).collect(Collectors.joining("\n - "));
				} else {
					text += "Currently, all available classes will be used";
				}
				tooltipClasses.setText(text);
			});
			btnSelectClasses.setTooltip(tooltipClasses);
			comboClasses.setTooltip(tooltipClasses);

			PaneTools.addGridRow(pane, row++, 0, 
					null,
					labelClasses, comboClasses, btnSelectClasses);

			/*
			 * Training annotations
			 */
			var labelTraining = new Label("Training");
			var comboTraining = new ComboBox<TrainingAnnotations>();
			comboTraining.getItems().setAll(TrainingAnnotations.values());
			comboTraining.getSelectionModel().select(TrainingAnnotations.ALL_UNLOCKED);
			trainingAnnotations = comboTraining.getSelectionModel().selectedItemProperty();
			trainingAnnotations.addListener(v -> invalidateClassifier());

			PaneTools.addGridRow(pane, row++, 0, 
					"Choose what kind of annotations to use for training",
					labelTraining, comboTraining, comboTraining);


			/*
			 * Additional options & live predict
			 */
			var btnLoadTraining = new Button("Load training");
			btnLoadTraining.setTooltip(new Tooltip("Train using annotations from more images in the current project"));
			btnLoadTraining.setOnAction(e -> {
				if (promptToLoadTrainingImages()) {
					invalidateClassifier();
					int n = trainingEntries.size();
					if (n > 0)
						btnLoadTraining.setText("Load training (" + n + ")");
					else
						btnLoadTraining.setText("Load training");
				}
			});
			
			var btnAdvancedOptions = new Button("Advanced options");
			btnAdvancedOptions.setTooltip(new Tooltip("Advanced options to customize preprocessing and classifier behavior"));
			btnAdvancedOptions.setOnAction(e -> {
				if (showAdvancedOptions())
					invalidateClassifier();
			});

			var btnLive = new ToggleButton("Live update");
			btnLive.selectedProperty().bindBidirectional(livePrediction);
			btnLive.setTooltip(new Tooltip("Toggle whether to calculate classification 'live' while viewing the image"));
			btnLive.setMaxWidth(Double.MAX_VALUE);
			livePrediction.addListener((v, o, n) -> {
				if (n) {
					invalidateClassifier();				
					return;
				}
			});

			var panePredict = PaneTools.createColumnGridControls(btnLoadTraining, btnAdvancedOptions);
			pane.add(panePredict, 0, row++, pane.getColumnCount(), 1);
			
			pane.add(btnLive, 0, row++, pane.getColumnCount(), 1);

			/*
			 * Training proportions (pie chart)
			 */
			pieChart = new PieChart();

			pieChart.setLabelsVisible(false);
			pieChart.setLegendVisible(true);
			pieChart.setPrefSize(40, 40);
			pieChart.setMaxSize(100, 100);
			pieChart.setLegendSide(Side.RIGHT);
			pieChart.setMaxWidth(Double.MAX_VALUE);
			GridPane.setVgrow(pieChart, Priority.ALWAYS);
			pane.add(pieChart, 0, row++, pane.getColumnCount(), 1);

			// Label showing cursor location
			var labelCursor = new Label();
			labelCursor.textProperty().bindBidirectional(cursorLocation);
			labelCursor.setMaxWidth(Double.MAX_VALUE);
			labelCursor.setAlignment(Pos.CENTER);
			labelCursor.setTooltip(new Tooltip("Prediction for current cursor location"));
			pane.add(labelCursor, 0, row++, pane.getColumnCount(), 1);
			
			/*
			 * Save classifier
			 */
			var btnSave = new Button("Save");
			var labelSave = new Label("Classifier name");
			var tfSaveName = new TextField("");
			tfSaveName.setMaxWidth(Double.MAX_VALUE);
			tfSaveName.setPromptText("Enter object classifier name");
			ProjectClassifierBindings.bindObjectClassifierNameInput(tfSaveName, qupath.projectProperty());
			btnSave.setMaxWidth(Double.MAX_VALUE);
			btnSave.disableProperty().bind(
					tfSaveName.textProperty().isEmpty()
					.or(qupath.projectProperty().isNull())
					.or(qupath.imageDataProperty().isNull()));
			btnSave.setOnAction(e -> {
				tryToSave(tfSaveName.getText());
				tfSaveName.requestFocus();
				btnSave.requestFocus();
			});
			PaneTools.addGridRow(pane, row++, 0, "Specify name of the classifier - this will be used to save to "
					+ "save the classifier in the current project, so it may be used for scripting later", labelSave, tfSaveName, btnSave);
									
//			var btnSave = new Button("Save & Apply");
//			btnSave.setMaxWidth(Double.MAX_VALUE);
//			btnSave.setOnAction(e -> saveAndApply());
//			btnSave.setTooltip(new Tooltip("Save a classifier with the current settings & apply it to the active image"));
//			pane.add(btnSave, 0, row++, pane.getColumnCount(), 1);


			PaneTools.setMaxWidth(Double.MAX_VALUE, comboTraining, comboObjects, comboClassifier, comboFeatures, comboClasses, panePredict);
			PaneTools.setHGrowPriority(Priority.ALWAYS, comboTraining, comboObjects, comboClassifier, comboFeatures, comboClasses, panePredict);
			PaneTools.setFillWidth(Boolean.TRUE, comboTraining, comboObjects, comboClassifier, comboClasses, panePredict);

			pane.setHgap(5);
			pane.setVgap(6);

			qupath.getStage().getScene().addEventFilter(MouseEvent.MOUSE_MOVED, e -> updateLocationText(e));

			pane.setPadding(new Insets(5));
		}


		boolean promptToSelectFeatures() {
			var measurements = getAllMeasurements(getTrainingImageData(), true);
			if (measurements.isEmpty()) {
				Dialogs.showErrorMessage("Select features", "No features available for specified objects!");
				return false;
			}

			var featuresPane = new SelectionPane<>(measurements, true);
			featuresPane.selectItems(selectedMeasurements);
			
			if (Dialogs.builder()
				.title("Select features")
				.buttons(ButtonType.APPLY, ButtonType.CANCEL)
				.content(featuresPane.getPane())
				.showAndWait()
				.orElse(ButtonType.CANCEL) != ButtonType.APPLY)
				return false;
			
			selectedMeasurements.clear();
			selectedMeasurements.addAll(featuresPane.getSelectedItems());
			return true;
		}

		boolean promptToSelectClasses() {
			var annotations = getTrainingAnnotations(getTrainingImageData());
			if (annotations.isEmpty()) {
				Dialogs.showErrorMessage("Object classifier", "No annotations found for training!");
				return false;
			}
			var pathClasses = annotations.stream().map(p -> p.getPathClass()).collect(Collectors.toCollection(TreeSet::new));
			var classesPane = new SelectionPane<>(pathClasses, true);
			classesPane.selectItems(selectedClasses);
			
			if (Dialogs.builder()
					.title("Select classes")
					.buttons(ButtonType.APPLY, ButtonType.CANCEL)
					.content(classesPane.getPane())
					.showAndWait()
					.orElse(ButtonType.CANCEL) != ButtonType.APPLY)
					return false;
			
			selectedClasses.clear();
			selectedClasses.addAll(classesPane.getSelectedItems());
			return true;
		}


		void updateLocationText(MouseEvent e) {
			String text = "";
			for (var viewer : qupath.getViewers()) {
				var hierarchy = viewer.getHierarchy();
				if (hierarchy == null)
					continue;
				var view = viewer.getView();
				var p = view.screenToLocal(e.getScreenX(), e.getScreenY());
				if (view.contains(p)) {
					text = viewer.getObjectClassificationString(p.getX(), p.getY());
				}
			}
			cursorLocation.set(text);
		}


		private void registerListeners(QuPathGUI qupath) {
			qupath.imageDataProperty().addListener(this);
			changed(qupath.imageDataProperty(), null, qupath.getImageData());
		}

		private void deregisterListeners(QuPathGUI qupath) {
			qupath.imageDataProperty().removeListener(this);
			changed(qupath.imageDataProperty(), qupath.getImageData(), null);
		}
		
		private void cleanup(QuPathGUI qupath) {
			deregisterListeners(qupath);
			// Ensure we have closed any cached images
			for (var data : trainingMap.values()) {
				try {
					data.getServer().close();
				} catch (Exception e) {
					logger.warn("Error closing server: " + e.getLocalizedMessage(), e);
				}
			}
			trainingEntries.clear();
			trainingMap.clear();
		}
		

		@Override
		public void changed(ObservableValue<? extends ImageData<BufferedImage>> source, ImageData<BufferedImage> imageDataOld,
				ImageData<BufferedImage> imageDataNew) {
			if (imageDataOld != null)
				imageDataOld.getHierarchy().removePathObjectListener(this);
			if (imageDataNew != null)
				imageDataNew.getHierarchy().addPathObjectListener(this);

			invalidateClassifier();
		}

		@Override
		public void hierarchyChanged(PathObjectHierarchyEvent event) {
			if (!Platform.isFxApplicationThread()) {
				Platform.runLater(() -> hierarchyChanged(event));
				return;
			}
			if (event.isChanging())
				return;
			var filter = objectFilter.get();
			if (event.isObjectClassificationEvent()) {
				if (event.getChangedObjects().stream().allMatch(filter))
					return;
			}
			if (event.isAddedOrRemovedEvent()) {
				// Adding & removing - we don't mind if it's not a relevant object for the classification, or it's an unclassified annotation
				if (event.getChangedObjects().stream().allMatch(p -> !(filter.test(p) || p.isAnnotation()) || (p.isAnnotation() && p.getPathClass() == null)))
					return;				
			}
			invalidateClassifier();
		}

	}


	/**
	 * Helper class to display a table with selectable items.
	 * Includes checkboxes, select all/none options, and (optionally) a filter box.
	 * @param <T>
	 */
	static class SelectionPane<T> {

		private BorderPane pane;

		private TableView<SelectableItem<T>> tableFeatures;
		private FilteredList<SelectableItem<T>> list;

		SelectionPane(Collection<T> items, boolean includeFilter) {
			list = FXCollections.observableArrayList(
					items.stream().map(i -> getSelectableItem(i)).collect(Collectors.toList())
					).filtered(p -> true);
			tableFeatures = new TableView<>(list);
			pane = makePane(includeFilter);
		}

		void updatePredicate(String text) {
			if (text == null || text.isBlank())
				list.setPredicate(p -> true);
			else
				list.setPredicate(p -> p.getItem().toString().toLowerCase().contains(text.toLowerCase()));
		}

		public Pane getPane() {
			return pane;
		}

		public List<T> getSelectedItems() {
			List<T> selectedFeatures = new ArrayList<>();
			for (SelectableItem<T> feature : tableFeatures.getItems()) {
				if (feature.isSelected())
					selectedFeatures.add(feature.getItem());
			}
			return selectedFeatures;
		}


		private BorderPane makePane(boolean includeFilter) {
			TableColumn<SelectableItem<T>, String> columnName = new TableColumn<>("Name");
			columnName.setCellValueFactory(new PropertyValueFactory<>("item"));
			columnName.setEditable(false);

			TableColumn<SelectableItem<T>, Boolean> columnSelected = new TableColumn<>("Selected");
			columnSelected.setCellValueFactory(new PropertyValueFactory<>("selected"));
			columnSelected.setCellFactory(column -> new CheckBoxTableCell<>());
			columnSelected.setEditable(true);
			columnSelected.setResizable(false);

			columnName.prefWidthProperty().bind(tableFeatures.widthProperty().subtract(columnSelected.widthProperty()));

			tableFeatures.getColumns().add(columnName);
			tableFeatures.getColumns().add(columnSelected);
			tableFeatures.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
			tableFeatures.setEditable(true);

			var menu = new ContextMenu();
			var itemSelect = new MenuItem("Select");
			itemSelect.setOnAction(e -> {
				for (SelectableItem<T> feature : tableFeatures.getSelectionModel().getSelectedItems())
					feature.setSelected(true);
			});
			menu.getItems().add(itemSelect);
			var itemDeselect = new MenuItem("Deselect");
			itemDeselect.setOnAction(e -> {
				for (SelectableItem<T> feature : tableFeatures.getSelectionModel().getSelectedItems())
					feature.setSelected(false);
			});
			menu.getItems().add(itemDeselect);

			tableFeatures.setContextMenu(menu);

			// Button to update the features
			var btnSelectAll = new Button("Select all");
			btnSelectAll.setOnAction(e -> {
				for (SelectableItem<T> feature : tableFeatures.getItems())
					feature.setSelected(true);

			});
			var btnSelectNone = new Button("Select none");
			btnSelectNone.setOnAction(e -> {
				for (SelectableItem<T> feature : tableFeatures.getItems())
					feature.setSelected(false);
			});
			var panelSelectButtons = PaneTools.createColumnGridControls(btnSelectAll, btnSelectNone);

			Pane panelButtons;

			if (includeFilter) {
				var tfFilter = new TextField("");
				tfFilter.setTooltip(new Tooltip("Type to filter table entries (case-insensitive)"));
				tfFilter.setPromptText("Type to filter table entries");
				var labelFilter = new Label("Filter");
				labelFilter.setLabelFor(tfFilter);
				labelFilter.setPrefWidth(Label.USE_COMPUTED_SIZE);
				tfFilter.setMaxWidth(Double.MAX_VALUE);
				tfFilter.textProperty().addListener((v, o, n) -> updatePredicate(n));
				var paneFilter = new GridPane();
				paneFilter.add(labelFilter, 0, 0);
				paneFilter.add(tfFilter, 1, 0);
				GridPane.setHgrow(tfFilter, Priority.ALWAYS);
				GridPane.setFillWidth(tfFilter, Boolean.TRUE);
				paneFilter.setHgap(5);
				paneFilter.setPadding(new Insets(5, 0, 5, 0));

				panelButtons = PaneTools.createRowGrid(
						panelSelectButtons,
						paneFilter
						);
			} else
				panelButtons = panelSelectButtons;

			var panelFeatures = new BorderPane();
			panelFeatures.setCenter(tableFeatures);
			panelFeatures.setBottom(panelButtons);


			return panelFeatures;
		}

		void selectItems(Collection<T> toSelect) {
			for (var item : toSelect) {
				var temp = itemPool.get(item);
				if (temp != null)
					temp.setSelected(true);
			}
		}

		private Map<T, SelectableItem<T>> itemPool = new HashMap<>();


		private SelectableItem<T> getSelectableItem(final T item) {
			SelectableItem<T> feature = itemPool.get(item);
			if (feature == null) {
				feature = new SelectableItem<>(item);
				itemPool.put(item, feature);
			}
			return feature;
		}


		public static class SelectableItem<T> {

			private ObjectProperty<T> item = new SimpleObjectProperty<>();
			private BooleanProperty selected = new SimpleBooleanProperty(false);

			public SelectableItem(final T item) {
				this.item.set(item);
			}

			public ReadOnlyObjectProperty<T> itemProperty() {
				return item;
			}

			public BooleanProperty selectedProperty() {
				return selected;
			}

			public boolean isSelected() {
				return selected.get();
			}

			public void setSelected(final boolean selected) {
				this.selected.set(selected);
			}

			public T getItem() {
				return item.get();
			}

		}

	}

}
