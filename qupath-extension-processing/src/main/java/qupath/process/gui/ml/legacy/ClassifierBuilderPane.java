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

package qupath.process.gui.ml.legacy;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Callback;
import qupath.lib.classifiers.Normalization;
import qupath.lib.classifiers.PathClassifierTools;
import qupath.lib.classifiers.PathObjectClassifier;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.dialogs.ParameterPanelFX;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.classes.PathClassFactory.StandardPathClasses;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent.HierarchyEventType;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.plugins.parameters.Parameterizable;
import qupath.lib.plugins.workflow.RunSavedClassifierWorkflowStep;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;
import qupath.lib.roi.interfaces.ROI;
import qupath.process.gui.ml.legacy.PathClassificationLabellingHelper.SplitType;


/**
 * Main pane for building detection classifiers.
 * 
 * Different classifiers can be used with this by passing them to the constructor.
 * 
 * @author Pete Bankhead
 *
 * @param <T>
 */
public class ClassifierBuilderPane<T extends PathObjectClassifier> implements PathObjectHierarchyListener, ChangeListener<ImageData<BufferedImage>> {

	private final static Logger logger = LoggerFactory.getLogger(ClassifierBuilderPane.class);

	private QuPathGUI qupath;
	
	/**
	 * File extension for (legacy) classifiers
	 */
	static String extPathClassifier = ".qpclassifier";

	private VBox panelClassifier = new VBox();

	private boolean updatingClassification = false;

	private ComboBox<T> comboClassifiers = new ComboBox<>();

	private Label labelSelectedFeatures = new Label("No features selected!");
	private Label labelRetainedObjects = new Label();
	private MenuItem miLoadTrainingObjects = new MenuItem("Load training objects");
	private MenuItem miSaveTrainingObjects = new MenuItem("Save training objects");
	private MenuItem miExportTrainingFeatures = new MenuItem("Export features as text");
	private MenuItem miCrossValidateAcrossImages = new MenuItem("Cross validate across images");
	private MenuItem miResetTrainingObjects = new MenuItem("Reset all");
	private MenuItem miRebuildTrainingFromProject = new MenuItem("Rebuild training from project");
	private MenuItem miClassifyAllImagesInProject = new MenuItem("Classify project images");

	private TextArea textClassifier;
	private ParameterPanelFX panelUpdate;
	
	private ProgressIndicator progressIndicator = new ProgressIndicator();

	private T classifier; // Current classifier
	private T lastClassifierCompleted; // Last classifier that ran to completion
	private PathIntensityClassifierPane panelIntensities;

	/**
	 * If true, PathObjects will only be included if they have base classifications that are either null or represented within the training annotations.
	 * The purpose of this is to allow preclassification by some other means to effectively take some objects out of consideration (e.g. immune cells), 
	 * and not 'pollute' the classification of other objects (e.g. tumor/non-tumor).
	 * 
	 * (Has been used to be a visible parameter);
	 */
	//	private static boolean limitTrainingToRepresentedClasses = true;

	private FeatureSelectionPane featurePanel;

	// Record whether the hierarchy has changed (indicating a need for reclassification)
	private boolean hierarchyChanged = false;

	private Button btnSaveClassifier;
	private Button btnEdit;

	private ToggleButton tbAutoUpdate = new ToggleButton("Auto-update");

	// Map containing PathObjects from previously-opened images
	private RetainedTrainingObjects retainedObjectsMap = new RetainedTrainingObjects();
	//	private Map<String, Map<PathClass, List<PathObject>>> retainedObjectsMap = new HashMap<>();


	private ParameterList paramsUpdate = new ParameterList()
			.addChoiceParameter("normalizationMethod", "Normalization method", Normalization.NONE, Arrays.asList(Normalization.values()), "Method to normalize features - some classifiers (e.g. SVM) require this, while others (e.g. decision trees, random forests) don't")
			.addIntParameter("maxTrainingPercent", "Training set split", 100, "%", 1, 100, "Percentage of the data to use for training - the rest will be used for testing")
			.addChoiceParameter("splitType", "Training set split type", SplitType.EQUIDISTANT, Arrays.asList(SplitType.values()), "Method of splitting the data for training")
			.addIntParameter("randomSeed", "Random seed", 1, null, "Seed used to generate random splits in a reproducible way (ignore if no random splitting is used)")
			.addBooleanParameter("balanceClasses", "Balance classes", false, "Ensure classes contain equal numbers of samples by randomly duplicating samples from classes with less representation")
			//		.addBooleanParameter("showTrainingSamples", "Show training samples", false, "Show the objects that will be used for training instead of actually creating the classifier - useful only for checking what is happening")
			.addBooleanParameter("trainFromPoints", "Train from points only", false, "Use only point annotations to train the classifier - not area regions")
			.addBooleanParameter("limitTrainingToRepresentedClasses", "Limit to represented classes", false, "Limit classification to only objects that are unclassified, or have classifications within the training set." + 
					"\nTurn this setting on if you want to ignore objects that have already been classified as something else, rather than reclassify them.");

	/**
	 * Constructor.
	 * @param qupath QuPath instance
	 * @param classifiers available classifiers
	 * @param classifierDefault default classifier
	 */
	public ClassifierBuilderPane(final QuPathGUI qupath, final List<T> classifiers, final T classifierDefault) {
		this.qupath = qupath;

		comboClassifiers.getItems().addAll(classifiers);

		if (classifierDefault != null)
			comboClassifiers.getSelectionModel().select(classifierDefault);
		else if (!comboClassifiers.getItems().isEmpty())
			comboClassifiers.getSelectionModel().select(0);

		classifier = comboClassifiers.getSelectionModel().getSelectedItem();
		comboClassifiers.setCellFactory(new Callback<ListView<T>, ListCell<T>>() {
			@Override
			public ListCell<T> call(ListView<T> p) {
				return new ListCell<T>() {
					@Override protected void updateItem(T item, boolean empty) {
						super.updateItem(item, empty);
						if (item == null) {
							if (empty)
								setText(null);
							else
								setText("None");
						} else {
							if (item == classifierDefault && !item.getName().toLowerCase().contains("default"))
								setText(item.getName() + " (default)");
							else
								setText(item.getName());
						}
					}
				};
			}
		});
		comboClassifiers.setButtonCell(comboClassifiers.getCellFactory().call(null));

		// Awkward... but need to create before FeatureSelectionPanel
		panelIntensities = new PathIntensityClassifierPane(qupath);

		featurePanel = new FeatureSelectionPane(qupath, panelIntensities);
		featurePanel.getPanel().setMinWidth(400);
		initializeBuildPanel();

		//		viewer.addViewerListener(this);
		setImageData(null, qupath.getImageData());
		qupath.imageDataProperty().addListener(this);

		btnSaveClassifier = new Button("Save classifier");
		btnSaveClassifier.setOnAction(event -> {

			if (!classifier.isValid()) {
				Dialogs.showErrorMessage("Save classifier", "No valid classifier available!");
				logger.error("No valid classifier available!");
				return;
			}

			// Get a classifier file
			File fileClassifier = Dialogs.getChooser(btnSaveClassifier.getScene().getWindow()).promptToSaveFile("Save classifier", null, null, "Classifier", extPathClassifier);
			if (fileClassifier == null)
				return;
			if (fileClassifier.exists()) {
				if (!Dialogs.showYesNoDialog("Overwrite classifier", "Overwrite existing classifier " + fileClassifier.getName() + "?"))
					return;
			}

			try {
				FileOutputStream fileOut = new FileOutputStream(fileClassifier);
				ObjectOutputStream outStream = new ObjectOutputStream(fileOut);
				// Check if we need a composite classifier, or if just one will do
				outStream.writeObject(getFullClassifier());
				outStream.close();
				fileOut.close();
				logger.info(String.format("Writing classifier %s complete!", classifier.toString()));
			} catch (IOException e) {
				e.printStackTrace();
			}

			// Register classification
			ImageData<BufferedImage> imageData = qupath.getImageData();
			if (imageData != null) {
				imageData.getHistoryWorkflow().addStep(new RunSavedClassifierWorkflowStep(fileClassifier.getAbsolutePath()));
			}

		});

		btnSaveClassifier.setTooltip(new Tooltip("Save the current classifier"));
		btnSaveClassifier.setDisable(classifier == null || !classifier.isValid());




		//		panelClassifier.getChildren().add(new TitledPane("Classifier", makeBuildPanel()));
		TitledPane paneDetails = new TitledPane("Details", makeDetailsPanel());
		paneDetails.setMaxHeight(Double.MAX_VALUE);
		panelClassifier.getChildren().add(paneDetails);
		VBox.setVgrow(paneDetails, Priority.ALWAYS);
		panelClassifier.setPadding(new Insets(5, 5, 5, 5));


		//		add(panelSouth, BorderLayout.SOUTH);
		panelClassifier.getChildren().add(btnSaveClassifier);
		btnSaveClassifier.prefWidthProperty().bind(panelClassifier.widthProperty());


		updateSelectedFeaturesLabel();
	}


	/**
	 * Get the full classifier, which may be a composite (also incorporating the intensity classifier).
	 * 
	 * If there is no intensity classifier, this returns the same as getClassifier().
	 * 
	 * @return
	 */
	public PathObjectClassifier getFullClassifier() {
		// Check if we need a composite classifier, or if just one will do
		PathObjectClassifier intensityClassifier = panelIntensities.getIntensityClassifier();
		if (intensityClassifier == null)
			return classifier;
		else
			return PathClassifierTools.createCompositeClassifier(classifier, intensityClassifier);
	}


	private void updateSelectedFeaturesLabel() {
		int n = featurePanel == null ? 0 : featurePanel.getSelectedFeatures().size();
		if (n == 0) {
			labelSelectedFeatures.setText("No features selected!");
			labelSelectedFeatures.setStyle("-fx-text-fill: red;");
		} else {
			labelSelectedFeatures.setText("Number of selected features: " + n);			
			labelSelectedFeatures.setStyle(null);
		}
	}


	private boolean loadRetainedObjects(final File file) {
		try (ObjectInputStream inStream = new ObjectInputStream(new BufferedInputStream(new FileInputStream(file)))) {
			Object input = inStream.readObject();
			if (input instanceof RetainedTrainingObjects) {
				this.retainedObjectsMap = (RetainedTrainingObjects)input;
				updateRetainedObjectsLabel();
				logger.info("Retained training objects loaded from {}", file);
				return true;
			} else
				logger.info("File {} does not contain valid training objects!", file);
		} catch (IOException e) {
			logger.error("Unable to load training objects", e);
		} catch (ClassNotFoundException e) {
			logger.error("Unable to load training objects", e);
		}
		//		logger.info(String.format("Unable to load training objects from {}", file));
		return false;
	}


	private boolean saveRetainedObjects(final File file) {
		// First, update the retained object map
		updateRetainedObjectsMap();
		
		// Write to file
		try (FileOutputStream fileOutMain = new FileOutputStream(file)) {
			BufferedOutputStream outputStream = new BufferedOutputStream(fileOutMain);
			ObjectOutputStream outStream = new ObjectOutputStream(outputStream);
			outStream.writeObject(retainedObjectsMap);
			outStream.flush();
			logger.info("Training objects saved to {}", file);
			return true;
		} catch (FileNotFoundException e) {
			logger.error("Unable to save training objects", e);
		} catch (IOException e) {
			logger.error("Unable to save training objects", e);
		}
		return false;
	}




	private void crossValidateAcrossImages() {

		// Try to put the current image data information into the tempMap, which stores training data separated by image path
		updateRetainedObjectsMap();
		Map<String, Map<PathClass, List<PathObject>>> tempMap = new LinkedHashMap<>(retainedObjectsMap.getMap());
		
		Normalization normalization = (Normalization)paramsUpdate.getChoiceParameterValue("normalizationMethod");
		for (String key : tempMap.keySet()) {
			Map<PathClass, List<PathObject>> validationMap = tempMap.get(key);
			Map<PathClass, List<PathObject>> trainingMap = new LinkedHashMap<>();
			for (Entry<String, Map<PathClass, List<PathObject>>> entry : tempMap.entrySet()) {
				if (entry.getKey().equals(key))
					continue;
				for (Entry<PathClass, List<PathObject>> entry2 : entry.getValue().entrySet()) {
					if (trainingMap.containsKey(entry2.getKey())) {
						trainingMap.get(entry2.getKey()).addAll(entry2.getValue());
					}
					else {
						trainingMap.put(entry2.getKey(), new ArrayList<>(entry2.getValue()));
					}
				}
			}

			// Perform subsampling
			SplitType splitType = (SplitType)paramsUpdate.getChoiceParameterValue("splitType");
			double maxTrainingProportion = paramsUpdate.getIntParameterValue("maxTrainingPercent") / 100.;
			long seed = paramsUpdate.getIntParameterValue("randomSeed");
			trainingMap = PathClassificationLabellingHelper.resampleClassificationMap(trainingMap, splitType, maxTrainingProportion, seed);

			// Get the current classifier - unfortunately, there's no easy way to duplicate/create a new one,
			// so we are left working with the 'live' classifier
			PathObjectClassifier classifier = (T)comboClassifiers.getSelectionModel().getSelectedItem();
			classifier.updateClassifier(trainingMap, featurePanel.getSelectedFeatures(), normalization);
			int nCorrect = 0;
			int nTested = 0;
			for (Entry<PathClass, List<PathObject>> entryValidation : validationMap.entrySet()) {
				classifier.classifyPathObjects(entryValidation.getValue());
				for (PathObject temp : entryValidation.getValue()) {
					if (entryValidation.getKey().equals(temp.getPathClass()))
						nCorrect++;
					nTested++;
				}
			}
			double percent = nCorrect * 100.0 / nTested;
			logger.info(String.format("Percentage correct for %s: %.2f%%", key, percent));
			System.err.println(String.format("Percentage correct for %s: %.2f%% (%d/%d)", key, percent, nCorrect, nTested));
		}
		// Force a normal classifier update, to compensate for the fact we had to modify the 'live' classifier
		updateClassification(false);
	}


	/**
	 * Get a list of the currently-selected features.
	 * 
	 * @return
	 */
	public List<String> getSelectedFeatures() {
		return new ArrayList<>(featurePanel.getSelectedFeatures());
	}


	private boolean exportTrainingFeatures(final File file) {
		// First, add existing objects to map
		ImageData<?> imageData = getImageData();

		// Get the features to export
		List<String> features = featurePanel.getSelectedFeatures();

		// Write the header
		PrintWriter writer;
		try {
			writer = new PrintWriter(file);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return false;
		}
		boolean includePath = !retainedObjectsMap.isEmpty();
		//		String delim = "\t";
		String delim = ",";
		if (includePath) {
			writer.print("Image");
			writer.print(delim);
		}
		writer.print("Class");
		for (String feature : features) {
			writer.print(delim);
			writer.print(feature.replace(delim, "-")); // Make sure we replace the delimiter so we don't have trouble
		}
		writer.println();

		// Write the current objects
		String currentPath = null;
		if (imageData != null) {
			currentPath = imageData.getServerPath();
			Map<PathClass, List<PathObject>> map = getTrainingMap();
			for (Entry<PathClass, List<PathObject>> entry : map.entrySet()) {
				for (PathObject pathObject : entry.getValue()) {
					if (includePath) {
						writer.print(currentPath);
						writer.print(delim);
					}
					writer.print(entry.getKey().getName());
					writer.print(delim);
					for (String name : features) {
						writer.print(Double.toString(pathObject.getMeasurementList().getMeasurementValue(name)));
						writer.print(delim);						
					}
					writer.println();
				}
			}
		}
		writer.close();
		return true;
	}





	private void updateRetainedObjectsLabel() {
		int n = retainedObjectsMap.countRetainedObjects();
		labelRetainedObjects.setText("Total number of training objects: " + n);
		miResetTrainingObjects.setDisable(n <= 0);
	}


	/**
	 * Get the current classifier.
	 * @return
	 */
	public T getClassifier() {
		return classifier;
	}


	private PathObjectHierarchy getHierarchy() {
		ImageData<?> imageData = qupath.getImageData();
		return imageData == null ? null : imageData.getHierarchy();
	}



	private BorderPane makeDetailsPanel() {
		BorderPane panelDetails = new BorderPane();	
		//		panelDetails.setTop(new Label("Details"));
		//		panelDetails.setBorder(BorderFactory.createTitledBorder("Details"));

		// Add text area description
		textClassifier = new TextArea();
		textClassifier.setWrapText(true);
		textClassifier.setPrefRowCount(4);
		textClassifier.setEditable(false);
		textClassifier.setMaxHeight(Double.POSITIVE_INFINITY);

		textClassifier.setTooltip(new Tooltip("Details about the last built classifier"));

		panelDetails.setCenter(textClassifier);

		updateClassifierSummary(null);

		return panelDetails;
	}



	/**
	 * Update the retained image map by loading all image data within the project.
	 */
	private void loadAllTrainingSamplesForProject() {
		Project<BufferedImage> project = qupath.getProject();
		String title = "Load training from project";
		if (project == null || project.isEmpty()) {
			Dialogs.showNoProjectError(title);
			return;
		}

		if (!retainedObjectsMap.isEmpty()) {
			if (!Dialogs.showYesNoDialog(title, "The current retained training objects will be reset - do you want to continue?"))
				return;
		} else {
			if (!Dialogs.showYesNoDialog(title, "Are you sure you want to load training samples from all images in the project?\nThis may take some time."))
				return;
		}


		ProgressBar progressBar = new ProgressBar();
		Stage dialog = new Stage();
		dialog.initOwner(qupath.getStage());
		dialog.initModality(Modality.APPLICATION_MODAL);
		TextArea textArea = new TextArea();
		textArea.setPrefColumnCount(40);
		textArea.setPrefRowCount(10);
		textArea.setEditable(false);
		//		DefaultCaret caret = (DefaultCaret)textArea.getCaret();
		//		caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
		TrainingLoadTask task = new TrainingLoadTask(dialog, textArea, retainedObjectsMap, project, paramsUpdate.getBooleanParameterValue("trainFromPoints"));
		progressBar.progressProperty().bind(task.progressProperty());

		BorderPane paneDialog = new BorderPane();
		paneDialog.setPadding(new Insets(5, 5, 5, 5));
		progressBar.setMaxWidth(Double.MAX_VALUE);
		paneDialog.setTop(progressBar);
		paneDialog.setCenter(textArea);

		dialog.setTitle("Loading project training data");
		dialog.setScene(new Scene(paneDialog));
		qupath.createSingleThreadExecutor(this).submit(task);

		panelClassifier.setCursor(Cursor.WAIT);
		dialog.show();
	}


	/**
	 * Get the pane, which can be added to a scene for display.
	 * @return
	 */
	public Pane getPane() {
		return panelClassifier;
	}



	private void classifyAllImagesInProject() {
		Project<BufferedImage> project = qupath.getProject();
		String title = "Apply classifier to project";
		if (classifier == null || !classifier.isValid()) {
			Dialogs.showErrorMessage(title, "No valid classifier is available! Make sure to build a classifier first.");
			return;
		}
		if (project == null || project.isEmpty()) {
			Dialogs.showNoProjectError(title);
			return;
		}

		// Be cautious...
		if (!Dialogs.showYesNoDialog(title, "Are you sure you want to apply the current classifier to all images in the project?\nThis may take some time - and there is no 'undo'."))
			return;



		ProgressBar progressBar = new ProgressBar();
		Stage dialog = new Stage();
		dialog.setTitle("Classifying project images");
		dialog.initOwner(qupath.getStage());
		dialog.initModality(Modality.APPLICATION_MODAL);
		TextArea textArea = new TextArea();
		textArea.setPrefColumnCount(40);
		textArea.setPrefRowCount(10);
		textArea.setEditable(false);
		Task<Void> task = new ClassificationTask(dialog, textArea, getFullClassifier(), project);

		progressBar.progressProperty().bind(task.progressProperty());

		BorderPane paneDialog = new BorderPane();
		paneDialog.setPadding(new Insets(5, 5, 5, 5));
		progressBar.setMaxWidth(Double.MAX_VALUE);
		paneDialog.setTop(progressBar);
		paneDialog.setCenter(textArea);

		dialog.setScene(new Scene(paneDialog));
		qupath.createSingleThreadExecutor(this).submit(task);
		//		task.execute();
		panelClassifier.setCursor(Cursor.WAIT);
		dialog.show();
		panelClassifier.setCursor(Cursor.DEFAULT);

		logger.info("Classification complete!");
	}



	static class ClassificationTask extends Task<Void> {

		private Stage dialog;
		private TextArea textArea;
		private PathObjectClassifier classifier;
		private Project<BufferedImage> project;

		ClassificationTask(final Stage dialog, final TextArea textArea, final PathObjectClassifier classifier, final Project<BufferedImage> project) {
			this.dialog = dialog;
			this.textArea = textArea;
			this.project = project;
			this.classifier = classifier;
		}

		@Override
		public Void call() {
			List<ProjectImageEntry<BufferedImage>> entries = project.getImageList();

			// Clean the project list to remove files that don't contain any data -
			// this makes the progress bar more accurate
			// TODO: Parallelize classification if the file sizes are small enough?
			Iterator<ProjectImageEntry<BufferedImage>> iter = entries.iterator();
			while (iter.hasNext()) {
				// Get the data file, and check it exists
				if (!iter.next().hasImageData()) {
					iter.remove();
					continue;
				}
			}


			int counter = 0;
			for (ProjectImageEntry<BufferedImage> entry : entries) {
				updateProgress(counter, entries.size());
				counter++;

				if (!entry.hasImageData())
					continue;

				try {
					// TODO: Remove BufferedImage dependency!
					ImageData<BufferedImage> imageDataTemp = entry.readImageData();
					if (imageDataTemp == null || imageDataTemp.getHierarchy().isEmpty())
						continue;

					Collection<PathObject> pathObjects = imageDataTemp.getHierarchy().getDetectionObjects();
					if (pathObjects.isEmpty()) {
						updateLog("No detection objects for " + entry.getImageName() + " - skipping");
						continue;
					}
					int n = classifier.classifyPathObjects(pathObjects);
					if (n > 0) {
						updateLog("Classified " + n + " objects for " + entry.getImageName());
						// Save the image again
						entry.saveImageData(imageDataTemp);
					} else
						updateLog("Unable to classify any objects for " + entry.getImageName() + " - skipping");

				} catch (Exception e) {
					if (entry != null)
						updateError("Error applying classifier to " + entry.getImageName());
				}
			}
			updateProgress(entries.size(), entries.size());
			return null;
		}

		private void updateLog(final String message) {
			logger.info(message);
			if (textArea != null)
				textArea.appendText(message + "\n");
		}

		private void updateError(final String message) {
			logger.error(message);     
			if (textArea != null)
				textArea.appendText("ERROR: " + message + "\n");
		}

		/*
		 * Executed in event dispatching thread
		 */
		@Override
		public void done() {
			Platform.runLater(() -> dialog.close());
		}
	}

	
	
	
	class BackgroundClassificationTask extends Task<Void> {
		
		final private PathObjectHierarchy hierarchy;
		final private List<String> features;
		final private Map<PathClass, List<PathObject>> mapTraining;
		final private Map<PathClass, List<PathObject>> mapTest;
		final private boolean testOnTrainingData;

		BackgroundClassificationTask(
				final PathObjectHierarchy hierarchy,
				final List<String> features,
				final Map<PathClass, List<PathObject>> mapTraining,
				final Map<PathClass, List<PathObject>> mapTest,
				final boolean testOnTrainingData) {
			this.hierarchy = hierarchy;
			this.features = features;
			this.mapTraining = mapTraining;
			this.mapTest = mapTest;
			this.testOnTrainingData = testOnTrainingData;
		}
		
		@Override
		protected Void call() throws Exception {
			doClassification(hierarchy, features, mapTraining, mapTest, testOnTrainingData);
			return null;
		}
		
	}
	
	
	


	class TrainingLoadTask extends Task<Void> {

		private Stage dialog;
		private TextArea textArea;
		private RetainedTrainingObjects retainedObjectsMap;
		private Project<BufferedImage> project;
		private boolean trainFromPoints;

		TrainingLoadTask(final Stage dialog, final TextArea textArea, final RetainedTrainingObjects retainedObjectsMap, final Project<BufferedImage> project, final boolean trainFromPoints) {
			this.dialog = dialog;
			this.textArea = textArea;
			this.project = project;
			this.retainedObjectsMap = retainedObjectsMap;
			this.trainFromPoints = trainFromPoints;
		}

		@Override
		public Void call() {
			List<ProjectImageEntry<BufferedImage>> entries = project.getImageList();

			// Clean the project list to remove files that don't contain any data -
			// this makes the progress bar more accurate
			// TODO: Parallelize loading if the file sizes are small enough?
			// TODO: Reduce duplication with project classification
			Iterator<ProjectImageEntry<BufferedImage>> iter = entries.iterator();
			while (iter.hasNext()) {
				// Get the data file, and check it exists
				if (!iter.next().hasImageData()) {
					iter.remove();
					continue;
				}
			}


			retainedObjectsMap.clear();
			int counter = 0;
			for (ProjectImageEntry<BufferedImage> entry : entries) {
				
				updateProgress(counter, entries.size());
				counter++;

				if (!entry.hasImageData())
					continue;

				try {
					PathObjectHierarchy hierarchy = entry.readHierarchy();
					if (hierarchy == null || hierarchy.isEmpty())
						continue;

					Map<PathClass, List<PathObject>> map = PathClassificationLabellingHelper.getClassificationMap(hierarchy, trainFromPoints);
					// Because we're looking at a detection classifier, remove all other object relationships -
					// we don't need them, and they bring in substantial memory requirements
					// (because objects know their parents, which know their parents... and all know descendants... basically 
					// the whole hierarchy ends up in memory for every image)
					for (List<PathObject> list : map.values()) {
						hierarchy.removeObjects(list, false);
					}

					if (!map.isEmpty() && !retainedObjectsMap.containsValue(map)) {
						retainedObjectsMap.put(getMapKey(project, entry), map);
						updateLog("Training objects read from " + entry.getImageName());
					} else {
						updateLog("No training objects found in " + entry.getImageName());					
					}
				} catch (Exception e) {
					logger.error("Exception reading training objects", e);
					if (entry != null)
						updateError("Error reading training objects from " + entry.getImageName());
				}
			}
			updateProgress(entries.size(), entries.size());
			return null;
		}

		private void updateLog(final String message) {
			logger.info(message);
			if (textArea != null)
				textArea.appendText(message + "\n");
		}

		private void updateError(final String message) {
			logger.error(message);     
			if (textArea != null)
				textArea.appendText("ERROR: " + message + "\n");
		}

		/*
		 * Executed in event dispatching thread
		 */
		@Override
		public void done() {
			if (Platform.isFxApplicationThread()) {
				dialog.close();
				updateRetainedObjectsLabel();
				panelClassifier.setCursor(Cursor.DEFAULT);
				logger.info("Training objects loading complete - " + retainedObjectsMap.countRetainedObjects() + " objects found");
				if (featurePanel != null && !retainedObjectsMap.isEmpty()) {
					featurePanel.updateMeasurements(retainedObjectsMap.getAllObjects());
				}
			}
			else
				Platform.runLater(() -> done());
		}
	}

	
	String getMapKey(Project<BufferedImage> project, ProjectImageEntry<BufferedImage> entry) {
		String key = project.getName() + "::" + entry.getID();
		return key;
	}

	String getMapKey(ImageData<BufferedImage> imageData) {
		return imageData.getServerPath();
//		var project = qupath.getProject();
//		return project == null ? null : getMapKey(project, project.getEntry(imageData));
	}

	private void initializeBuildPanel() {

		Button btnUpdateClassifier = new Button("Build & Apply");
		btnUpdateClassifier.setTooltip(new Tooltip("Build classifier & apply to objects in the current image"));

		tbAutoUpdate.setTooltip(new Tooltip(	"Automatically update the classification when changes are made to the data - only recommended if the classifier is fast & the amount of training data is small"));
		tbAutoUpdate.setOnAction(e -> {
			if (!tbAutoUpdate.isDisabled() && tbAutoUpdate.isSelected())
				updateClassification(true);

		});

		panelUpdate = new ParameterPanelFX(paramsUpdate);
		//		panelUpdate.getPane().setPadding(new Insets(0, 10, 0, 10));

		comboClassifiers.setOnAction(e -> {
			maybeUpdate();
			//				WekaClassifierBuilder builder = (WekaClassifierBuilder)comboClassifiers.getSelectedItem();
			// We can't auto-update if we don't have a valid (non-advanced) classifier builder
			//				cbAutoUpdate.setEnabled(builder != null && builder.getClassifierClass() != null);
			classifier = (T)comboClassifiers.getSelectionModel().getSelectedItem();

			// Enable/disable edit button
			if (btnEdit != null)
				btnEdit.setDisable(!(classifier instanceof Parameterizable));

			tbAutoUpdate.setDisable(classifier == null || !classifier.supportsAutoUpdate());
		});

		// Make panel to create a classifier
		GridPane panelClassifierType = new GridPane();
		panelClassifierType.add(new Label("Classifier type: "), 0, 0);
		panelClassifierType.add(comboClassifiers, 1, 0);
		comboClassifiers.setMaxWidth(Double.MAX_VALUE);
		comboClassifiers.setTooltip(new Tooltip("Choose classifier type"));
		GridPane.setHgrow(comboClassifiers, Priority.ALWAYS);
		panelClassifierType.setHgap(5);
		panelClassifierType.setVgap(5);

		// Add in options button
		btnEdit = new Button("Edit");
		btnEdit.setTooltip(new Tooltip("Edit advanced classifier options"));
		btnEdit.setDisable(!(classifier instanceof Parameterizable));
		btnEdit.setOnAction(e -> {
			if (!(classifier instanceof Parameterizable)) {
				Dialogs.showErrorMessage("Classifier settings", "No options available for selected classifier!");
				return;
			}

			Parameterizable parameterizable = (Parameterizable)classifier;
			ParameterPanelFX panel = new ParameterPanelFX(parameterizable.getParameterList());

			//				JDialog dialog = new JDialog(qupath.getFrame(), "Classifier settings", ModalityType.APPLICATION_MODAL);
			BorderPane pane = new BorderPane();
			pane.setCenter(panel.getPane());

			Button btnRun = new Button("Rebuild classifier");
			btnRun.setOnAction(e2 -> updateClassification(true));
			pane.setBottom(btnRun);

			Dialogs.showMessageDialog("Classifier settings", pane);
		});

		panelClassifierType.add(btnEdit, 2, 0);
		panelClassifierType.add(tbAutoUpdate, 3, 0);
		panelClassifierType.add(btnUpdateClassifier, 4, 0);
		panelClassifierType.setPadding(new Insets(10, 10, 10, 10));





		// Make feature panel
		GridPane panelFeatures = new GridPane();
		Button btnFeatures = new Button("Select...");
		btnFeatures.setTooltip(new Tooltip("Select features to use for classification - this is required before any classifier can be made"));
		btnFeatures.setOnAction(e -> {
			qupath.submitShortTask(() -> featurePanel.ensureMeasurementsUpdated());
			Dialogs.showMessageDialog("Select Features", featurePanel.getPanel());
			updateSelectedFeaturesLabel();
		});

		Button btnUseAllFeatures = new Button("Use all");
		btnUseAllFeatures.setTooltip(new Tooltip("Update feature list to use all available features"));
		btnUseAllFeatures.setOnAction(e -> selectAllFeatures());

		panelFeatures.add(labelSelectedFeatures, 0, 0);
		GridPane.setHgrow(labelSelectedFeatures, Priority.ALWAYS);
		labelSelectedFeatures.setMaxWidth(Double.MAX_VALUE);
		//		labelSelectedFeatures.setTextAlignment(TextAlignment.CENTER);
		//		labelSelectedFeatures.setAlignment(Pos.CENTER);
		panelFeatures.add(btnFeatures, 1, 0);
		panelFeatures.add(btnUseAllFeatures, 2, 0);
		panelFeatures.setHgap(5);



		// Multi-image stuff
		GridPane panelSouth = new GridPane();
		//		Tooltip.install(btnResetTrainingObjects, new Tooltip("Reset all the retained objects, so that the classifier only uses the training objects from the current image"));
		miResetTrainingObjects.setOnAction(e -> {
			if (retainedObjectsMap == null || retainedObjectsMap.isEmpty())
				return;
			if (Dialogs.showYesNoDialog("Remove retained objects", "Remove " + retainedObjectsMap.countRetainedObjects() + " retained object(s) from classifier training?")) {
				retainedObjectsMap.clear();
				updateRetainedObjectsLabel();
			}
		});

		final String trainingExtension = "qptrain";

		miLoadTrainingObjects.setOnAction(e -> {
			File fileTraining = Dialogs.promptForFile("Load objects", null, trainingExtension, new String[]{trainingExtension});
			if (fileTraining == null)
				return;
			if (!loadRetainedObjects(fileTraining)) {
				Dialogs.showErrorMessage("Load training objects", "There was an error loading training objects from \n" + fileTraining);
			}
		});
		//		btnSaveTrainingObjects.setTooltip(new Tooltip("Load training objects saved in a previous session"));

		miSaveTrainingObjects.setOnAction(e -> {
			File fileTraining = Dialogs.promptToSaveFile("Save objects", null, null, trainingExtension, trainingExtension);
			if (fileTraining == null)
				return;
			if (!saveRetainedObjects(fileTraining)) {
				Dialogs.showErrorMessage("Save training objects", "There was an error saving training objects to \n" + fileTraining);
			}
		});
		//		btnSaveTrainingObjects.setTooltip(new Tooltip("Save training objects for reloading in another session"));


		miExportTrainingFeatures.setOnAction(e -> {
			File fileTraining = Dialogs.promptToSaveFile("Export features", null, null, "Text file", "txt");
			if (fileTraining == null)
				return;
			if (!exportTrainingFeatures(fileTraining)) {
				Dialogs.showErrorMessage("Export features", "There was an exporting the training features to \n" + fileTraining);
			}
		});
		//		btnExportTrainingFeatures.setTooltip(new Tooltip("Export training features to a text file (e.g. for analysis elsewhere"));


		//		btnRebuildTrainingFromProject.setTooltip(new Tooltip("Load training objects from all images in the project to use these to create a single classifier"));
		miRebuildTrainingFromProject.setOnAction(e -> {
			loadAllTrainingSamplesForProject();
		});

		miClassifyAllImagesInProject.setOnAction(e -> {
			classifyAllImagesInProject();
		});

		miCrossValidateAcrossImages.setOnAction(e -> {
			crossValidateAcrossImages();
		});


		labelRetainedObjects.setTooltip(new Tooltip("The total number of objects last used for training - including from other images not currently open"));
		//		labelRetainedObjects.setAlignment(Pos.CENTER);
		labelRetainedObjects.setMaxWidth(Double.MAX_VALUE);
		//		labelRetainedObjects.setPadding(new Insets(5, 5, 5, 5));
		panelSouth.add(labelRetainedObjects, 0, 0);
		GridPane.setHgrow(labelRetainedObjects, Priority.ALWAYS);
		//		panelSouth.setStyle("-fx-background-color: red;");

		MenuItem miShowTrainingObjectMatrix = new MenuItem("Show training object counts");
		miShowTrainingObjectMatrix.setOnAction(e -> {
			updateRetainedObjectsMap();
			showRetainedTrainingMap(retainedObjectsMap);
		});
		
		ContextMenu context = new ContextMenu();
		context.getItems().addAll(
				miLoadTrainingObjects,
				miSaveTrainingObjects,
				miRebuildTrainingFromProject,
				new SeparatorMenuItem(),
				miShowTrainingObjectMatrix,
				miResetTrainingObjects,
				new SeparatorMenuItem(),
				miExportTrainingFeatures,
				miCrossValidateAcrossImages,
				miClassifyAllImagesInProject
				);


		context.setOnShowing(e -> {
			boolean hasRetainedObjects = !retainedObjectsMap.isEmpty();
			boolean hasAnyObjects = hasRetainedObjects || (getHierarchy() != null && !getHierarchy().isEmpty());
			miResetTrainingObjects.setDisable(!hasRetainedObjects);
			miCrossValidateAcrossImages.setDisable(!hasRetainedObjects);
			miSaveTrainingObjects.setDisable(!hasAnyObjects);
			miExportTrainingFeatures.setDisable(!hasAnyObjects);
			miRebuildTrainingFromProject.setVisible(qupath.getProject() != null);
			miClassifyAllImagesInProject.setVisible(qupath.getProject() != null);
		});


		Button buttonMore = new Button("More...");
		buttonMore.setOnMouseClicked(e -> {
			context.show(buttonMore, e.getScreenX(), e.getScreenY());
		});
		panelSouth.add(buttonMore, 1, 0);

		//		panelSouth.setBottom(panelRetainingButtons);
		updateRetainedObjectsLabel();
		//		TitledPane multiImage = new TitledPane("Multi-image training", panelSouth);
		//		labelRetainedObjects.prefWidthProperty().bind(multiImage.widthProperty());
		//		panelClassifier.getChildren().add(multiImage);
		//		panelSouth.add(panelRetaining, BorderLayout.NORTH);
		//		panelSouth.add(btnSaveClassifier, BorderLayout.SOUTH);






		//		panelFeatures.setStyle("-fx-background-color: blue;");

		GridPane paneAdvancedMain = new GridPane();
		paneAdvancedMain.add(panelFeatures, 0, 0);
		paneAdvancedMain.add(panelSouth, 0, 1);
		paneAdvancedMain.add(panelUpdate.getPane(), 0, 2);
		paneAdvancedMain.setVgap(5);
		panelUpdate.getPane().setMaxWidth(Double.MAX_VALUE);
		GridPane.setHgrow(panelFeatures, Priority.ALWAYS);
		GridPane.setHgrow(panelSouth, Priority.ALWAYS);
		GridPane.setHgrow(panelUpdate.getPane(), Priority.ALWAYS);
		//		panelUpdate.getPane().setStyle("-fx-background-color: green;");

		TitledPane paneAdvanced = new TitledPane("Advanced options", paneAdvancedMain);
		paneAdvanced.setMaxWidth(Double.MAX_VALUE);
		paneAdvanced.setExpanded(false);
		// Really, I should probably just use a CSS stylesheet somewhere... here is an inelegant way to change things...
		Platform.runLater(() -> {
			try {
				paneAdvanced.lookup(".title").setStyle("-fx-background-color: transparent");
				paneAdvanced.lookup(".title").setEffect(null);
				paneAdvanced.lookup(".content").setStyle("-fx-border-color: null");
			} catch (Exception e) {
				logger.error("Error setting Advanced options pane style", e);
			}
		});

		panelClassifierType.add(paneAdvanced, 0, 1, 6, 1);
		progressIndicator.setVisible(false);
		progressIndicator.setPrefSize(30, 30);
		panelClassifierType.add(progressIndicator, 5, 0, 1, 1);
		//		panelClassifierType.add(panelUpdate.getPane(), 0, 1, 4, 1);
		GridPane.setHgrow(panelUpdate.getPane(), Priority.ALWAYS);
		GridPane.setHgrow(paneAdvanced, Priority.ALWAYS);

		//		btnUpdateClassifier.setMaxWidth(Double.MAX_VALUE);
		//		panelClassifierType.add(btnUpdateClassifier, 0, 2, 3, 1);

		panelClassifier.getChildren().add(new TitledPane("Classifier", panelClassifierType));

		panelIntensities.intensityFeatureProperty().addListener((v, o, n) -> updateIntensityPanelCallback());
		panelIntensities.addThresholdParameterChangeListener((p, k, a) -> updateIntensityPanelCallback());

		panelClassifier.getChildren().add(new TitledPane("Intensity", panelIntensities.getPane()));

		btnUpdateClassifier.setOnAction(e -> updateClassification(true));
	}


	private void updateIntensityPanelCallback() {
		PathObjectHierarchy hierarchy = getHierarchy();
		PathObjectClassifier intensityClassifier = panelIntensities.getIntensityClassifier();
		if (intensityClassifier == null || hierarchy == null || classifier == null || !classifier.isValid() || !tbAutoUpdate.isSelected() || updatingClassification)
			return;
		// We may need to do a bigger reclassification
		if (hierarchyChanged)
			maybeUpdate();
		else {
			Collection<PathObject> pathObjects = hierarchy.getDetectionObjects();
			if (intensityClassifier.classifyPathObjects(pathObjects) > 0) {
				// Update displayed list - names may have changed - and classifier summary
				updateClassifierSummary(null);
				hierarchy.fireObjectClassificationsChangedEvent(this, pathObjects);
			}
		}
	}



	/**
	 * Try to select all features, forcing an update to check for current objects
	 */
	private void selectAllFeatures() {
		Map<PathClass, List<PathObject>> map = getTrainingMap();
		Set<String> featureNames = new TreeSet<>();
		for (List<PathObject> list : map.values()) {
			for (PathObject temp : list) {
				featureNames.addAll(temp.getMeasurementList().getMeasurementNames());
			}
		}
		// If we don't have any training features, let feature panel try to get them itself for current ImageDat
		if (featureNames.isEmpty())
			featurePanel.ensureMeasurementsUpdated();
		else
			featurePanel.updateMeasurementsByNames(featureNames);
		featurePanel.selectAllAvailableFeatures();
		updateSelectedFeaturesLabel();
	}


	/**
	 * Update the retained objects map using the data from the current image.
	 */
	private void updateRetainedObjectsMap() {
		PathObjectHierarchy hierarchy = getHierarchy();
		if (hierarchy != null) {
			Map<PathClass, List<PathObject>> mapCurrent = PathClassificationLabellingHelper.getClassificationMap(hierarchy, paramsUpdate.getBooleanParameterValue("trainFromPoints"));
			// Add in any retained objects, if we have some
			PathClassificationLabellingHelper.countObjectsInMap(mapCurrent);
			//		int retainedImageCount = retainedObjectsMap.addToTrainingMap(map, getImageData().getServerPath());
			retainedObjectsMap.put(getMapKey(getImageData()), mapCurrent);
			updateRetainedObjectsLabel();
		}
	}
	
	
	/**
	 * Update the retained objects map, then consolidate this into a single training map and return.
	 * 
	 * Entries should be added to the returned map in order of image path (sorted Strings).
	 * @return 
	 */
	public synchronized Map<PathClass, List<PathObject>> getTrainingMap() {
		updateRetainedObjectsMap();
		Map<PathClass, List<PathObject>> trainingMap = new TreeMap<>();
		retainedObjectsMap.addToTrainingMap(trainingMap);
		int nObjectsAfter = PathClassificationLabellingHelper.countObjectsInMap(trainingMap);
		logger.info("{} objects available for classifier training from {} images", nObjectsAfter, retainedObjectsMap.size());
		return trainingMap;
	}



	private synchronized void updateClassification(boolean interactive) {

		PathObjectHierarchy hierarchy = getHierarchy();
		if (hierarchy == null) {
			if (interactive)
				Dialogs.showErrorMessage("Classification error", "No objects available to classify!");
			btnSaveClassifier.setDisable(!classifier.isValid());
			return;
		}

		List<String> features = featurePanel.getSelectedFeatures();
		// If we've no features, default to trying to get
		if (features.isEmpty() && interactive) {
			selectAllFeatures();
			features = featurePanel.getSelectedFeatures();
			if (features.size() == 1)	
				Dialogs.showInfoNotification("Feature selection", "Classifier set to train using the only available feature");
			else if (!features.isEmpty())
				Dialogs.showInfoNotification("Feature selection", "Classifier set to train using all " + features.size() + " available features");
		}

		// If still got no features, we're rather stuck
		if (features.isEmpty()) {
			Dialogs.showErrorMessage("Classification error", "No features available to use for classification!");
			btnSaveClassifier.setDisable(classifier == null || !classifier.isValid());
			return;
		}

		updatingClassification = true;

		// Get training map
		double maxTrainingProportion = paramsUpdate.getIntParameterValue("maxTrainingPercent") / 100.;
		long seed = paramsUpdate.getIntParameterValue("randomSeed");
		SplitType splitType = (SplitType)paramsUpdate.getChoiceParameterValue("splitType");
		Map<PathClass, List<PathObject>> map = getTrainingMap();

		// Apply limit if needed
		if (paramsUpdate.getBooleanParameterValue("limitTrainingToRepresentedClasses")) {
			Set<PathClass> representedClasses = map.keySet();
			for (List<PathObject> values : map.values()) {
				Iterator<PathObject> iter = values.iterator();
				while (iter.hasNext()) {
					PathClass pathClass = iter.next().getPathClass();
					if (pathClass != null && !representedClasses.contains(pathClass.getBaseClass()))
						iter.remove();
				}
			}
		}

		// TODO: The order of entries in the map is not necessarily consistent (e.g. when a new annotation is added to the hierarchy - 
		// irrespective of whether or not it has a classification).  Consequently, classifiers that rely on 'randomness' (e.g. random forests...)
		// can give different results for the same training data.  With 'auto-update' selected, this looks somewhat disturbing...
		Map<PathClass, List<PathObject>> mapTraining = PathClassificationLabellingHelper.resampleClassificationMap(map, splitType, maxTrainingProportion, seed);
		if (mapTraining.size() <= 1) {
			logger.error("Training samples from at least two different classes required to train a classifier!");
			updatingClassification = false;
			return;
		}
		
		// Try to create a separate test map, if we can
		Map<PathClass, List<PathObject>> mapTest = map;
		boolean testOnTrainingData = true;
		if (mapTraining != map) {
			for (Entry<PathClass, List<PathObject>> entry : mapTraining.entrySet()) {
				mapTest.get(entry.getKey()).removeAll(entry.getValue());
				logger.info("Number of training samples for " + entry.getKey() + ": " + entry.getValue().size());
			}
			testOnTrainingData = false;
		}


		// Balance the classes for training, if necessary
		if (paramsUpdate.getBooleanParameterValue("balanceClasses")) {
			logger.debug("Balancing classes...");
			int maxSize = -1;
			for (List<PathObject> temp : mapTraining.values())
				maxSize = Math.max(maxSize, temp.size());
			Random random = new Random(seed);
			for (PathClass key : mapTraining.keySet()) {
				List<PathObject> temp = mapTraining.get(key);
				int size = temp.size();
				if (maxSize == size)
					continue;
				// Ensure a copy is made
				List<PathObject> list = new ArrayList<>(temp);
				for (int i = 0; i < maxSize - size; i++) {
					list.add(temp.get(random.nextInt(size)));
				}
				mapTraining.put(key, list);
			}
		}

		BackgroundClassificationTask task = new BackgroundClassificationTask(hierarchy, features, mapTraining, mapTest, testOnTrainingData);
		
		qupath.submitShortTask(task);
		
		
//		doClassification(hierarchy, features, mapTraining, mapTest, testOnTrainingData);
	}
	
	
	
	private void doClassification(final PathObjectHierarchy hierarchy, final List<String> features, final Map<PathClass, List<PathObject>> mapTraining, final Map<PathClass, List<PathObject>> mapTest, final boolean testOnTrainingData) {

		if (!Platform.isFxApplicationThread())
			Platform.runLater(() -> {
				progressIndicator.setProgress(-1);
				progressIndicator.setPrefSize(30, 30);
				progressIndicator.setVisible(true);
			});
		
		long startTime = System.currentTimeMillis();

		// Train classifier with requested normalization
		Normalization normalization = (Normalization)paramsUpdate.getChoiceParameterValue("normalizationMethod");
		String errorMessage = null;
		boolean classifierChanged = classifier != lastClassifierCompleted;
		try {
			classifierChanged = classifier.updateClassifier(mapTraining, features, normalization) || classifierChanged;
		} catch (Exception e) {
			errorMessage = "Classifier training failed with message:\n" + e.getLocalizedMessage() + 
					"\nPlease try again with different settings.";
			e.printStackTrace();
		}

		if (classifier == null || !classifier.isValid()) {
			updateClassifierSummary(errorMessage);
			logger.error("Classifier is invalid!");
			updatingClassification = false;
			btnSaveClassifier.setDisable(classifier == null || !classifier.isValid());
			return;
		}


		long middleTime = System.currentTimeMillis();
		logger.info(String.format("Classifier training time: %.2f seconds", (middleTime-startTime)/1000.));

		// Create an intensity classifier, if required
		PathObjectClassifier intensityClassifier = panelIntensities.getIntensityClassifier();

		// Apply classifier to everything
		Collection<PathObject> pathObjectsOrig = hierarchy.getDetectionObjects();
		int nClassified = 0;
		
		// Possible get proxy objects, depending on the thread we're on
		Collection<PathObject> pathObjects;
		if (Platform.isFxApplicationThread())
			pathObjects = pathObjectsOrig;
		else
			pathObjects = pathObjectsOrig.stream().map(p -> new PathObjectClassificationProxy(p)).collect(Collectors.toList());

		// Omit any objects that have already been classified by anything other than any of the target classes
		if (paramsUpdate.getBooleanParameterValue("limitTrainingToRepresentedClasses")) {
			Iterator<PathObject> iterator = pathObjects.iterator();
			Set<PathClass> representedClasses = mapTraining.keySet();
			while (iterator.hasNext()) {
				PathClass currentClass = iterator.next().getPathClass();
				if (currentClass != null && !representedClasses.contains(currentClass.getBaseClass()))
					iterator.remove();
			}
		}



		// In the event that we're using retained images, ensure we classify everything in our test set
		if (retainedObjectsMap.size() > 1 && !mapTest.isEmpty()) {
			for (Entry<PathClass, List<PathObject>> entry : mapTest.entrySet()) {
				pathObjects.addAll(entry.getValue());
			}
		}

		if (classifierChanged || hierarchyChanged) {
			nClassified = classifier.classifyPathObjects(pathObjects);
		} else {
			logger.info("Main classifier unchanged...");
		}


		if (intensityClassifier != null)
			intensityClassifier.classifyPathObjects(pathObjects);
		//		}
		if (nClassified > 0) {
//			qupath.getViewer().repaint();
		} else if (classifierChanged || hierarchyChanged)
			logger.error("Classification failed - no objects classified!");
		long endTime = System.currentTimeMillis();
		logger.info(String.format("Classification time: %.2f seconds", (endTime-middleTime)/1000.));
//		panelClassifier.setCursor(cursor);

		
		completeClassification(hierarchy, pathObjects, pathObjectsOrig, mapTest, testOnTrainingData);
	}
	
	
	private void completeClassification(final PathObjectHierarchy hierarchy, final Collection<PathObject> classifiedObjects, final Collection<PathObject> originalObjects, final Map<PathClass, List<PathObject>> mapTest, final boolean testOnTrainingData) {
		if (!Platform.isFxApplicationThread())
			Platform.runLater(() -> completeClassification(hierarchy, classifiedObjects, originalObjects, mapTest, testOnTrainingData));
		else {
			
			
			// Test the classifications of the test set... which may or may not be the same as the training set
			if (mapTest != null) {
				int nCorrect = 0;
				List<PathClass> pathClasses = new ArrayList<>(mapTest.keySet());
				Collections.sort(pathClasses);
				ConfusionMatrix<PathClass> confusion = new ConfusionMatrix<>(pathClasses);
				int nCorrectTumor = 0;
				//			int nWrong = 0;
				int nUnclassified = 0;
				int n = 0;
				PathClass tumorClass = PathClassFactory.getPathClass(StandardPathClasses.TUMOR);
				// If we have multiple classes, it can be beneficial to see how tumor vs. everything else performs
				boolean multiclassContainsTumor = mapTest.containsKey(tumorClass) && mapTest.size() > 2; // Create a tumor vs. everything else classifier
				for (Entry<PathClass, List<PathObject>> entry : mapTest.entrySet()) {
					PathClass pathClass = entry.getKey();
					boolean isTumor = pathClass.equals(tumorClass);
					for (PathObject testObject : entry.getValue()) {
						PathClass tempClass = testObject.getPathClass();
						if (tempClass == null) {
							nUnclassified++;
							n++;
							continue;
						}
						PathClass resultClass = tempClass.getBaseClass(); // We've probably applied an intensity classifier by now
						confusion.registerClassification(pathClass, resultClass);
						if (resultClass.equals(pathClass))
							nCorrect++;
						if (multiclassContainsTumor && (isTumor && pathClass.equals(resultClass) || (!isTumor && !resultClass.equals(tumorClass))))
							nCorrectTumor++;
						n++;
					}
				}
				// Log the results
				if (testOnTrainingData) {
					logger.info(String.format("Percentage of correctly classified objects in TRAINING set: %.2f%% (n=%d)", nCorrect*100./n, n));
					logger.warn("It is *strongly* advised not to report accuracies based on testing using the training set!");
				}
				else {
					logger.info(String.format("Percentage of correctly classified objects in test set: %.2f%% (n=%d)", nCorrect*100./n, n));
					if (multiclassContainsTumor)
						logger.info(String.format("Percentage of correctly classified objects in test set (Tumor vs. everything else): %.2f%% (n=%d)", nCorrectTumor*100./n, n));
				}
				if (nUnclassified > 0)
					logger.info(String.format("Number of unclassified objects in the test set: %d (%.2f%%)", nUnclassified, nUnclassified*100./n));
	
				logger.info("Confusion matrix\n"+confusion.toString());
	
				// Only interested in changes from now
				hierarchyChanged = false;
			}

			
			
			
			progressIndicator.setVisible(false);
			
			// Update the classification of any proxy objects
			int nChanged = classifiedObjects.parallelStream().mapToInt(p -> {
				if (p instanceof PathObjectClassificationProxy && ((PathObjectClassificationProxy)p).updateObject())
					return 1;
				else
					return 0;
			}).sum();
			if (classifiedObjects != originalObjects)
				logger.info("Number of reclassified objects: {} of {}", nChanged, classifiedObjects.size());
			
			// Update displayed list - names may have changed - and classifier summary
			updateClassifierSummary(null);

			btnSaveClassifier.setDisable(!classifier.isValid());

			hierarchy.fireObjectClassificationsChangedEvent(this, originalObjects);

			lastClassifierCompleted = classifier;
			updatingClassification = false;
		}
	}
	
	


	private ImageData<BufferedImage> getImageData() {
		return qupath.getImageData();
	}


	private void updateClassifierSummary(String message) {
		if (textClassifier == null)
			return;
		if (message == null) {
			if (classifier == null || !classifier.isValid())
				message = "No valid classifier";
			else
				message = classifier.getDescription();
		}
		textClassifier.setText(message);
		//			textClassifier.setCaretPosition(0);
	}





	//	void maybeUpdate() { 
	//		maybeUpdate(false);
	//	}


	/**
	 * Decide whether to update.
	 * This this decision involves checking if the selected object is in the hierarchy, need a special 
	 * flag to indicate that this was called due to a object removal event - in which case this check should be adapted.
	 */
	private void maybeUpdate() { 
		if (!tbAutoUpdate.isDisabled() && 
				// Only run if we are auto-updating
				tbAutoUpdate.isSelected() && 
				!updatingClassification &&
				// Only run if current selected object is in the hierarchy - otherwise it is being edited
				getHierarchy() != null &&
				(getHierarchy().getSelectionModel().noSelection() || PathObjectTools.hierarchyContainsObject(getHierarchy(), getHierarchy().getSelectionModel().getSelectedObject())) &&
				// Only run if panel is visible - otherwise should have turned off auto-update
				panelClassifier.isVisible() && 
				panelClassifier.getScene().getWindow().isShowing()) {
			//			updateClassificationInBackground();
			updateClassification(false);
			qupath.getViewer().repaint();
			//			viewer.repaint();
		}
	}

	private static boolean containsObjectsOfClass(final Collection<PathObject> pathObjects, final Class<? extends PathObject> cls) {
		for (PathObject temp : pathObjects) {
			if (cls == null || cls.isInstance(temp))
				return true;
		}
		return false;
	}

	@Override
	public void hierarchyChanged(PathObjectHierarchyEvent event) {
		if (event.getSource() == this)
			return;

		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(() -> hierarchyChanged(event));
			return;
		}


		// Flag that the hierarchy has changed if this is any kind of event other than an object classification event
		hierarchyChanged = hierarchyChanged || !event.isObjectClassificationEvent();

		// TODO: Avoid reliance on this, and instead check the training objects used
		if (event.isStructureChangeEvent()) {
			// Don't respond to adding/removing annotation objects without classes set
			if (event.isAddedOrRemovedEvent()) {
				PathObject pathObjectChanged = event.getChangedObjects().get(0);
				if (!(pathObjectChanged instanceof PathAnnotationObject) || pathObjectChanged.getPathClass() == null)
					return;
			}
		} else if (event.isObjectClassificationEvent()) {
			// If classifications have changed, we only care if these contain annotations
			boolean containsAnnotations = containsObjectsOfClass(event.getChangedObjects(), PathAnnotationObject.class);
			if (!containsAnnotations)
				return;
		} else if (event.getEventType() == HierarchyEventType.CHANGE_OTHER) {
			return;
		}

		maybeUpdate(); // TODO: See if calls can be reduced
	}



	/**
	 * Update the current ImageData, which is used for accessing annotated regions.
	 * 
	 * @param imageDataOld The current ImageData - the panel will stop listening to changes here (may be null)
	 * @param imageDataNew The new ImageData (may be null)
	 */
	public void setImageData(final ImageData<BufferedImage> imageDataOld, final ImageData<BufferedImage> imageDataNew) {
		hierarchyChanged = true;
		if (imageDataOld == imageDataNew)
			return;
		if (imageDataOld != null)
			imageDataOld.getHierarchy().removePathObjectListener(this);
		if (imageDataNew != null)
			imageDataNew.getHierarchy().addPathObjectListener(this);
	}
	
	
	@Override
	public void changed(ObservableValue<? extends ImageData<BufferedImage>> source, ImageData<BufferedImage> imageDataOld,
			ImageData<BufferedImage> imageDataNew) {

		// Check if we've anything that we should cache
		if (panelClassifier.isVisible() && imageDataOld != null) {
			Map<PathClass, List<PathObject>> map = PathClassificationLabellingHelper.getClassificationMap(imageDataOld.getHierarchy(), paramsUpdate.getBooleanParameterValue("trainFromPoints"));
			if (!map.isEmpty() && !retainedObjectsMap.containsValue(map)) {
				String key = getMapKey(imageDataOld);
				if (Dialogs.showYesNoDialog("Retain training objects", "Retain current training objects in classifier?")) {
					retainedObjectsMap.put(key, map);
					updateRetainedObjectsLabel();
				} else {
					retainedObjectsMap.remove(key);
					updateRetainedObjectsLabel();					
				}
			}
		}

		setImageData(imageDataOld, imageDataNew);
	}





	/**
	 * Stand-in object that can be used to intercept classifications.
	 * 
	 * The reason is that we wouldn't want to interrupt a half-finished classification... by classifying these
	 * stand-in objects in a background thread, we can make sure not to be modifying any PathObjects during the
	 * (possibly-lengthy) training &amp; application of a classifier, and then return to the Platform JavaFX thread for the final update.
	 * 
	 * This somewhat-awkward approach is used to keep the complexity here, while retaining a simple definition of a PathObjectClassifier.
	 * 
	 * @author Pete Bankhead
	 *
	 */
	static class PathObjectClassificationProxy extends PathObject {

		private PathObject pathObject;
		private PathClass pathClass;
		private double classificationProbability;

		private PathObjectClassificationProxy(final PathObject pathObject) {
			super(pathObject.getMeasurementList());
			this.pathObject = pathObject;
			this.pathClass = pathObject.getPathClass();
			this.classificationProbability = pathObject.getClassProbability();
		}

		@Override
		public boolean isEditable() {
			return pathObject.isEditable();
		}

		@Override
		public PathClass getPathClass() {
			return pathClass;
		}

		@Override
		public void setPathClass(PathClass pathClass, double classProbability) {
			this.pathClass = pathClass;
			this.classificationProbability = classProbability;
		}

		@Override
		public double getClassProbability() {
			return classificationProbability;
		}

		@Override
		public ROI getROI() {
			return pathObject.getROI();
		};


		/**
		 * Update the internally-stored object, returning true if its classification changed
		 * 
		 * @return
		 */
		public boolean updateObject() {
			boolean changed = pathObject.getPathClass() != pathClass;
			pathObject.setPathClass(pathClass, classificationProbability);
			return changed;
		}


	}

	
	
	
	void showRetainedTrainingMap(final RetainedTrainingObjects retainedObjects) {
		
		
		Map<String, Map<PathClass, List<PathObject>>> map = retainedObjects.getMap();
		
		// Determine columns
		Set<PathClass> pathClasses = new TreeSet<>();
		for (Map<PathClass, List<PathObject>> temp : map.values()) {
			pathClasses.addAll(temp.keySet());
		}

		// Don't show a badly-formed table with nothing in it...
		if (pathClasses.isEmpty()) {
			Dialogs.showMessageDialog("Training objects", "No training objects selected!");
			return;
		}

		// Set up table
		TableView<String> table = new TableView<>();
		table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		table.getItems().setAll(map.keySet());
		Collections.sort(table.getItems());
		
		// Create columns
		TableColumn<String, String> colName = new TableColumn<>("Image");
		colName.setCellValueFactory(column -> new ReadOnlyObjectWrapper<>(column.getValue()));
//		colName.setCellValueFactory(column -> new ReadOnlyObjectWrapper<>(ServerTools.getDefaultShortServerName(column.getValue())));
		colName.setPrefWidth(240);
		
		table.getColumns().add(colName);
		int nColWidth = 80;
		for (PathClass pathClass : pathClasses) {
			TableColumn<String, Integer> col = new TableColumn<>(pathClass.getName());
			col.setCellValueFactory(column -> {
				if (map.get(column.getValue()).get(pathClass) == null)
					return new ReadOnlyObjectWrapper<Integer>(0);
				else
					return new ReadOnlyObjectWrapper<Integer>(map.get(column.getValue()).get(pathClass).size());
			});
			col.setPrefWidth(nColWidth);
			table.getColumns().add(col);
		}
		table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
		
		// Show
		Stage dialog = new Stage();
		dialog.initOwner(qupath.getStage());
		dialog.setTitle("Training objects");
		dialog.setScene(new Scene(table));
		dialog.showAndWait();
		
	}
	
	


}