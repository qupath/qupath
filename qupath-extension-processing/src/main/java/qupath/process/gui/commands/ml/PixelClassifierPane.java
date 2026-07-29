/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2026 QuPath developers, The University of Edinburgh
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

package qupath.process.gui.commands.ml;

import java.time.Duration;
import java.util.Objects;
import java.util.Random;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import javafx.util.Subscription;
import org.bytedeco.javacpp.PointerScope;
import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.TermCriteria;
import org.bytedeco.opencv.opencv_ml.ANN_MLP;
import org.bytedeco.opencv.opencv_ml.KNearest;
import org.bytedeco.opencv.opencv_ml.LogisticRegression;
import org.bytedeco.opencv.opencv_ml.RTrees;
import org.bytedeco.opencv.opencv_ml.TrainData;
import org.controlsfx.glyphfont.FontAwesome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.dialogs.Dialogs;
import qupath.fx.utils.FXUtils;
import qupath.fx.utils.GridPaneUtils;
import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.classifiers.pixel.PixelClassifierMetadata;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.actions.ActionTools;
import qupath.lib.gui.actions.InfoMessage;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.IconFactory;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.io.GsonTools;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.opencv.ml.ConfusionMatrix;
import qupath.opencv.ml.FeaturePreprocessor;
import qupath.opencv.ml.OpenCVClassifiers;
import qupath.opencv.ml.OpenCVClassifiers.OpenCVStatModel;
import qupath.opencv.ml.pixel.PixelClassifiers;
import qupath.opencv.ops.ImageDataOp;
import qupath.opencv.ops.ImageOps;
import qupath.process.gui.commands.ml.op.MultiscaleImageDataOpBuilder;
import qupath.process.gui.commands.ml.op.ImageDataOpBuilder;
import qupath.process.gui.commands.ml.PixelClassifierTraining.ClassifierTrainingData;

import java.awt.image.BufferedImage;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Main user interface for interactively training a {@link PixelClassifier}.
 * 
 * @author Pete Bankhead
 */
public class PixelClassifierPane {
	
	private static final Logger logger = LoggerFactory.getLogger(PixelClassifierPane.class);
	
	private static final ObservableList<ImageDataOpBuilder> defaultFeatureCalculatorBuilders = FXCollections.observableArrayList();

	private final QuPathGUI qupath;

	private final ObjectProperty<ImageData<BufferedImage>> imageDataProperty = new SimpleObjectProperty<>();

	private final ObservableList<ImageDataOpBuilder> featureOpBuilders = FXCollections.observableArrayList();
    private final ObservableList<ClassificationResolution> resolutions = FXCollections.observableArrayList();

	private final TrainingViewerPane trainingViewerPane;
	private final TrainingDetailsPane trainingDetailsPane = new TrainingDetailsPane();
	private final FeatureDetailsPane featureDetailsPane = new FeatureDetailsPane();
	private final JsonDisplay<PixelClassifier> jsonDisplay = new JsonDisplay<>();
	private final MetricsBrowser<PathClass> metricsBrowser = new MetricsBrowser<>();

	private final BorderPane paneMain = new BorderPane();
	private Pane paneDetails;

	private final BooleanProperty showMore = new SimpleBooleanProperty(false);

	private final TrainingPieChart pieChart = new TrainingPieChart();
	private final TrainingImageManager trainingImageManager;
	
	private final BooleanProperty livePrediction = new SimpleBooleanProperty(false);

	private final ObjectProperty<ClassificationResolution> resolution = new SimpleObjectProperty<>();
	private final ObjectProperty<OpenCVStatModel> statModel = new SimpleObjectProperty<>();
	private final ObjectProperty<ImageDataOpBuilder> opBuilder = new SimpleObjectProperty<>();
	private final ObjectProperty<ImageServerMetadata.ChannelType> outputType = new SimpleObjectProperty<>();

	private final PathObjectHierarchyListener hierarchyListener = this::handleHierarchyChange;

	private final BooleanProperty featuresIncompatible = new SimpleBooleanProperty(false);
	private final BooleanProperty resolutionsIncompatible = new SimpleBooleanProperty(false);

	/**
	 * The last trained classifier
	 */
	private final ObjectProperty<PixelClassifier> currentClassifier = new SimpleObjectProperty<>();

	private final PixelClassifierOverlayManager overlayManager;

	private final PixelClassifierTraining helper = new PixelClassifierTraining(null);
	private final PixelClassifierAdvancedOptions advancedOptions = new PixelClassifierAdvancedOptions();

	private final ComboBox<ClassificationResolution> comboResolutions = PixelClassifierUtils.createHGrowComboBox(resolutions);

	private Subscription subscription = Subscription.EMPTY;

	private Stage stage;

	/**
	 * Constructor.
	 * @param qupath the current {@link QuPathGUI} that will be used for interactive training.
	 */
	public PixelClassifierPane(final QuPathGUI qupath) {
		this.qupath = qupath;
		this.imageDataProperty.bind(qupath.imageDataProperty());
		this.overlayManager = createOverlayManager(qupath, helper);
		this.trainingImageManager = createTrainingImageManager(qupath);
		this.trainingViewerPane = createTrainingViewerPane(qupath, overlayManager);
	}

	private PixelClassifierOverlayManager createOverlayManager(QuPathGUI qupath, PixelClassifierTraining helper) {
		var overlayManager = new PixelClassifierOverlayManager(qupath.getViewerManager(), qupath.getImageRegionStore(), helper);
		overlayManager.classifierProperty().bind(currentClassifier);
		overlayManager.livePredictionProperty().bind(livePrediction);
		return overlayManager;
	}

	private static TrainingImageManager createTrainingImageManager(QuPathGUI qupath) {
		return new TrainingImageManager(qupath);
	}

	private static TrainingViewerPane createTrainingViewerPane(QuPathGUI qupath, PixelClassifierOverlayManager overlayManager) {
		return new TrainingViewerPane(qupath.viewerProperty(), overlayManager);
	}

	/**
	 * Show the stage containing this pane.
	 */
	public void showStage() {
		if (stage == null) {
			stage = createUI();
		}
		stage.show();
	}

	private void initializeSubscriptions() {
		appendAllSubscriptions(
				livePrediction.subscribe(this::updateClassifier),
				statModel.subscribe(this::updateClassifier),
				outputType.subscribe(this::updateClassifier),
				resolution.subscribe(n -> {
					updateResolution(n);
					updateClassifier();
					overlayManager.ensureOverlaySet();
				}),
				opBuilder.subscribe(this::updateFeatureCalculator),
				showMore.subscribe(this::handleShowDetails),
				imageDataProperty.subscribe(this::handleImageDataChange),
				stage.focusedProperty().subscribe(this::handleStageFocussed)
		);
	}

	private void appendAllSubscriptions(Subscription... subscriptions) {
		for (var subscription : subscriptions) {
			appendSubscription(subscription);
		}
	}

	private void appendSubscription(Subscription subscription) {
		this.subscription = this.subscription.and(subscription);
	}

	private void handleImageDataChange(ImageData<BufferedImage> oldValue, ImageData<BufferedImage> newValue) {
		if (oldValue != null)
			oldValue.getHierarchy().removeListener(hierarchyListener);
		if (newValue != null)
			newValue.getHierarchy().addListener(hierarchyListener);
		updateAvailableResolutions(newValue);
		checkFeaturesCompatible();
		checkResolutionsCompatible();
	}



	private Stage createUI() {

		var imageData = imageDataProperty.get();
		
        GridPane pane = new GridPane();
		pane.setHgap(5);
		pane.setVgap(5);
		pane.setMinWidth(400);
		pane.setMaxWidth(400);
		pane.setPadding(new Insets(5));

		// Update options
		updateAvailableResolutions(imageData);
		updateAvailableFeatureOpBuilders(imageData);

		// Add main options
		addClassifierSelectionControls(pane);
		addResolutionSelectionControls(pane);
		addFeatureSelectionControls(pane, imageData);
		addOutputTypeControls(pane);
		addRegionSelectionControls(pane);

		addAdvancedOptions(pane);
		addLivePredictionButton(pane);

		addPieChart(pane);

		addShowDetailsPane(pane);

		addSeparator(pane);
		
		addStandardPixelClassifierButtons(pane);

		paneMain.setLeft(pane);

		ensureResolutionSelected(imageData);
		updateFeatureCalculator();

		handleImageDataChange(null, imageData);

		return createStage(new BorderPane(paneMain));
	}

	private void handleShowDetails(boolean doShow) {
		boolean createPane = paneDetails == null && doShow;
		if (createPane) {
			paneDetails = createDetailsPane();
			paneMain.setCenter(paneDetails);
			paneMain.getScene().getWindow().sizeToScene();
		} else if (doShow) {
			// Only update the width if we're actually adding a control
			// (not if we've simply started re-subscribing to changes)
			if (paneMain.getCenter() != paneDetails) {
				paneMain.setCenter(paneDetails);
				var window = paneMain.getScene().getWindow();
				window.setWidth(window.getWidth() + Math.max(100, paneDetails.getWidth()));
			}
		} else if (paneDetails != null) {
			if (paneMain.getCenter() != null) {
				paneMain.setCenter(null);
				var window = paneMain.getScene().getWindow();
				window.setWidth(window.getWidth() - paneDetails.getWidth());
			}
		}
	}

	private Pane createDetailsPane() {
		var tabPane = new TabPane();
		tabPane.getTabs().add(
				new Tab("Viewer", trainingViewerPane)
		);
		tabPane.getTabs().add(
				new Tab("Classifier", trainingDetailsPane)
		);
		var paneImportance = new BorderPane(featureDetailsPane);
		var btnImportance = new Button();
		btnImportance.textProperty().bind(Bindings.createStringBinding(() -> {
			if (featureDetailsPane.hasImportanceProperty().get())
				return "Update feature importance";
			else
				return "Calculate feature importance";
		}, featureDetailsPane.hasImportanceProperty()));
		btnImportance.setMaxWidth(Double.MAX_VALUE);
		btnImportance.disableProperty().bind(imageDataProperty.isNull());
		btnImportance.setOnAction(e -> calculateVariableImportance());
		paneImportance.setBottom(btnImportance);
		tabPane.getTabs().add(
				new Tab("Features", paneImportance)
		);

		var paneMetrics = new BorderPane(metricsBrowser);
		var btnCrossValidation = new Button();
		btnCrossValidation.setMaxWidth(Double.MAX_VALUE);
		var hasMetrics = Bindings.isNotEmpty(metricsBrowser.getConfusionMatrices());
		btnCrossValidation.textProperty().bind(Bindings.createStringBinding(() -> {
			if (hasMetrics.get())
				return "Update cross validation";
			else
				return "Compute cross validation";
		}, hasMetrics));
		btnCrossValidation.disableProperty().bind(imageDataProperty.isNull());
		btnCrossValidation.setOnAction(e -> computeCrossValidation());
		paneMetrics.setBottom(btnCrossValidation);
		tabPane.getTabs().add(
				new Tab("Metrics", paneMetrics)
		);
		jsonDisplay.itemProperty().bind(currentClassifier);
		tabPane.getTabs().add(
				new Tab("JSON", jsonDisplay)
		);
		for (var tab : tabPane.getTabs()) {
			tab.setClosable(false);
		}
		var pane = new BorderPane(tabPane);
		pane.setLeft(new Separator(Orientation.VERTICAL));
		return pane;
	}


	private void ensureResolutionSelected(ImageData<?> imageData) {
		if (comboResolutions.getItems().isEmpty()) {
			logger.warn("No resolutions available!");
			return;
		}
		// If no image, pick middle resolution (previous default)
		if (imageData == null || resolutions.size() == 1) {
			comboResolutions.getSelectionModel().clearAndSelect(resolutions.size() / 2);
			return;
		}
		// If we have an image, try to pick a sensible default based on the image size.
		// This is mostly to pick the full resolution for 'small' images,
		// rather than a low resolution that is unlikely to be desired.
		var server = imageData.getServer();
		double maxDim = Math.max(server.getWidth(), server.getHeight());
		double pixelSize = server.getPixelCalibration().getAveragedPixelSize().doubleValue();
		int ind = 0;
		while (ind < resolutions.size()-1) {
			var res = resolutions.get(ind);
			// We assume the pixel calibration units are the same...
			var cal = res.getPixelCalibration();
			double downsample = cal.getAveragedPixelSize().doubleValue() / pixelSize;
			if (maxDim / downsample <= 4096)
				break;
			ind++;
		}
		comboResolutions.getSelectionModel().select(ind);
	}

	private Stage createStage(Pane content) {
		var stage = new Stage();
		stage.setScene(new Scene(content));

		stage.setMinHeight(450);
		stage.setMinWidth(400);
		stage.sizeToScene();

		stage.initOwner(qupath.getStage());
		stage.setTitle("Train pixel classifier");

		stage.showingProperty().subscribe(this::handleStageShowingChange);

		return stage;
	}


	private static Label createFixedWidthLabelForNode(String text, Node nodeFor) {
		var label = new Label(text);
		label.setMinWidth(Label.USE_PREF_SIZE);
		label.setMaxWidth(Label.USE_PREF_SIZE);
		if (nodeFor != null)
			label.setLabelFor(nodeFor);
		return label;
	}

	private static Button createFixedWidthButton(String text) {
		var button = new Button(text);
		button.setMinWidth(Label.USE_PREF_SIZE);
		button.setMaxWidth(Double.MAX_VALUE);
		GridPane.setFillWidth(button, Boolean.TRUE);
		return button;
	}



	private void addClassifierSelectionControls(GridPane pane) {
		ComboBox<OpenCVStatModel> comboClassifier = PixelClassifierUtils.createHGrowComboBox();
		var labelClassifier = createFixedWidthLabelForNode("Classifier", comboClassifier);

		comboClassifier.getItems().addAll(
				OpenCVClassifiers.createStatModel(RTrees.class),
				OpenCVClassifiers.createStatModel(ANN_MLP.class),
				OpenCVClassifiers.createStatModel(LogisticRegression.class),
				OpenCVClassifiers.createStatModel(KNearest.class)
		);

		comboClassifier.getSelectionModel().clearAndSelect(1);

		labelClassifier.setLabelFor(comboClassifier);

		statModel.bind(comboClassifier.getSelectionModel().selectedItemProperty());
		var btnEditClassifier = createFixedWidthButton("Edit");
		btnEditClassifier.setOnAction(e -> promptToEditClassifierParameters());
		btnEditClassifier.disableProperty().bind(statModel.isNull());

		GridPaneUtils.addGridRow(pane, pane.getRowCount(), 0,
				"Choose classifier type (RTrees or ANN_MLP are generally good choices)",
				labelClassifier, comboClassifier, btnEditClassifier);
	}


	private void addResolutionSelectionControls(GridPane pane) {
		var labelResolution = createFixedWidthLabelForNode("Resolution", comboResolutions);

		var actionAddResolution = ActionTools.createAction(this::promptToAddResolution);
		actionAddResolution.setText("Edit");

		resolution.bind(comboResolutions.getSelectionModel().selectedItemProperty());

		var incompatibleMessage = InfoMessage.error("Selected resolution does not match the current image.\n" +
				"Consider selecting a different resolution.");
		ActionTools.installInfoMessage(actionAddResolution,
				Bindings.when(resolutionsIncompatible).then(incompatibleMessage).otherwise((InfoMessage)null));

		var btnResolution = ActionTools.createButton(actionAddResolution);
		GridPaneUtils.addGridRow(pane, pane.getRowCount(), 0,
				"Choose the base image resolution based upon required detail in the classification (see preview on the right)",
				labelResolution, comboResolutions, btnResolution);
	}

	private void addFeatureSelectionControls(GridPane pane, ImageData<BufferedImage> imageData) {
		ComboBox<ImageDataOpBuilder> comboFeatures = PixelClassifierUtils.createHGrowComboBox(featureOpBuilders);

		var labelFeatures = createFixedWidthLabelForNode("Features", comboFeatures);
		opBuilder.bind(comboFeatures.getSelectionModel().selectedItemProperty());
		comboFeatures.getSelectionModel().selectFirst();


		var actionCustomFeatures = ActionTools.createAction(() -> {
			var imageDataTemp = imageDataProperty.get();
			if (opBuilder.get().doCustomize(imageDataTemp)) {
				updateFeatureCalculator();
				checkFeaturesCompatible();
			}
		});
		actionCustomFeatures.setText("Edit");
		actionCustomFeatures.disabledProperty().bind(Bindings.createBooleanBinding(() -> {
			var calc = opBuilder.get();
			var imageDataTemp = imageDataProperty.get();
			return calc == null || imageDataTemp == null || !calc.canCustomize(imageDataTemp);
		}, opBuilder, imageDataProperty));

		var incompatibleMessage = InfoMessage.error("Features are not compatible with the current image.\n" +
				"Please select different features.");
		ActionTools.installInfoMessage(actionCustomFeatures,
				Bindings.when(featuresIncompatible).then(incompatibleMessage).otherwise((InfoMessage)null));

		var btnCustomizeFeatures = ActionTools.createButton(actionCustomFeatures);
		btnCustomizeFeatures.setMinWidth(Button.USE_PREF_SIZE);
		btnCustomizeFeatures.setMaxWidth(Button.USE_PREF_SIZE);

		comboFeatures.getSelectionModel().select(0);

		HBox.setHgrow(comboFeatures, Priority.ALWAYS);
		HBox.setHgrow(btnCustomizeFeatures, Priority.NEVER);
		GridPaneUtils.addGridRow(pane, pane.getRowCount(), 0,
				"Select features for the classifier",
				labelFeatures, comboFeatures, btnCustomizeFeatures);
	}

	private void checkFeaturesCompatible() {
		var imageData = imageDataProperty.get();
		var op = opBuilder.get();
        featuresIncompatible.set(
				imageData != null &&
				op != null &&
				!op.supportsImage(imageData, PixelCalibration.getDefaultInstance())
		);
	}

	private void checkResolutionsCompatible() {
		var imageData = imageDataProperty.get();
		var res = resolution.getValue();
		if (imageData == null || res == null) {
			resolutionsIncompatible.set(false);
			return;
		}
		var cal = imageData.getServer().getPixelCalibration();
		resolutionsIncompatible.set(
				!Objects.equals(cal.getPixelWidthUnit(), res.getPixelCalibration().getPixelWidthUnit()) ||
						!Objects.equals(cal.getPixelHeightUnit(), res.getPixelCalibration().getPixelHeightUnit()) ||
						cal.getAveragedPixelSize().doubleValue() > res.getPixelCalibration().getAveragedPixelSize().doubleValue()
		);
	}

	private void addOutputTypeControls(GridPane pane) {
		ComboBox<ImageServerMetadata.ChannelType> comboOutput = PixelClassifierUtils.createHGrowComboBox();
		comboOutput.getItems().addAll(ImageServerMetadata.ChannelType.CLASSIFICATION, ImageServerMetadata.ChannelType.PROBABILITY);
		outputType.bind(comboOutput.getSelectionModel().selectedItemProperty());

		comboOutput.getSelectionModel().clearAndSelect(0);
		var labelOutput = createFixedWidthLabelForNode("Output", comboOutput);
		GridPaneUtils.addGridRow(pane, pane.getRowCount(), 0,
				"Choose whether to output classifications only, or estimated probabilities per class (not all classifiers support probabilities, which also require more memory)",
				labelOutput, comboOutput, comboOutput);
	}

	private void addAdvancedOptions(GridPane pane) {
		// Add training & prediction buttons
		var btnProject = createLoadTrainingButton();
		var btnAdvancedOptions = createAdvancedOptionsButton();

		// Add pie chart and cursor tracking
		var panePredict = GridPaneUtils.createColumnGridControls(btnProject, btnAdvancedOptions);
		pane.add(panePredict, 0, pane.getRowCount(), GridPane.REMAINING, 1);
	}

	private void addLivePredictionButton(GridPane pane) {
		var btnLive = createLivePredictionButton();
		pane.add(btnLive, 0, pane.getRowCount(), GridPane.REMAINING, 1);
	}

	private void addPieChart(GridPane pane) {
		GridPaneUtils.setFillWidth(Boolean.TRUE, pieChart);
		GridPaneUtils.setFillHeight(Boolean.TRUE, pieChart);
		GridPaneUtils.setVGrowPriority(Priority.ALWAYS, pieChart);
		GridPaneUtils.setHGrowPriority(Priority.ALWAYS, pieChart);
		pane.add(pieChart, 0, pane.getRowCount(), GridPane.REMAINING, 1);
	}

	private void addShowDetailsPane(GridPane pane) {
		var paneShowDetails = new BorderPane(createCursorLabel());
		paneShowDetails.setRight(createShowDetailsButton());
		pane.add(paneShowDetails, 0, pane.getRowCount(), GridPane.REMAINING, 1);
	}

	private Label createCursorLabel() {
		// Label showing classification at cursor location
		var labelCursor = new Label();
		labelCursor.textProperty().bind(overlayManager.cursorLocationProperty());
		labelCursor.setAlignment(Pos.CENTER);
		labelCursor.setTextAlignment(TextAlignment.CENTER);
		labelCursor.setContentDisplay(ContentDisplay.CENTER);
		labelCursor.setWrapText(true);
		labelCursor.setMaxHeight(Double.MAX_VALUE);
		labelCursor.setMaxWidth(Double.MAX_VALUE);

		labelCursor.setTooltip(new Tooltip("Prediction for current cursor location"));
		return labelCursor;
	}

	private ToggleButton createShowDetailsButton() {
		var btnShowDetails = new ToggleButton();
		btnShowDetails.setContentDisplay(ContentDisplay.RIGHT);
		btnShowDetails.selectedProperty().bindBidirectional(showMore);
		btnShowDetails.textProperty().bind(Bindings.when(showMore)
				.then("Less")
				.otherwise("More"));

		var iconMore = IconFactory.createNode(FontAwesome.Glyph.CARET_RIGHT);
		var iconLess = IconFactory.createNode(FontAwesome.Glyph.CARET_LEFT);
		btnShowDetails.graphicProperty().bind(Bindings.when(showMore)
				.then(iconLess)
				.otherwise(iconMore));
		btnShowDetails.setPrefWidth(70);
		return btnShowDetails;
	}


	private void addStandardPixelClassifierButtons(GridPane pane) {
		var classifierName = new SimpleStringProperty(null);
		var panePostProcess = new VBox(
				PixelClassifierUI.createSavePixelClassifierPane(qupath.projectProperty(), currentClassifier, classifierName),
				PixelClassifierUI.createPixelClassifierButtons(imageDataProperty, currentClassifier, classifierName)
		);
		panePostProcess.setSpacing(5);
		pane.add(panePostProcess, 0, pane.getRowCount(), GridPane.REMAINING, 1);
	}

	private void addSeparator(GridPane pane) {
		pane.add(new Separator(), 0, pane.getRowCount(), GridPane.REMAINING, 1);
	}

	private void addRegionSelectionControls(GridPane pane) {
		var comboRegionFilter = PixelClassifierUI.createRegionFilterCombo(qupath.getOverlayOptions());
		// Hack... this seems to fix a bug whereby the stage would grow in size whenever
		// this combo box (and subsequently others) was clicked on
//		comboRegionFilter.setPrefWidth(100);
		var labelRegion = createFixedWidthLabelForNode("Region", comboRegionFilter);
		GridPaneUtils.addGridRow(pane,  pane.getRowCount(), 0, "Control where the pixel classification is applied during preview",
				labelRegion, comboRegionFilter, comboRegionFilter);
	}

	private Button createAdvancedOptionsButton() {
		var button = createFixedWidthButton("Advanced options");
		button.setTooltip(new Tooltip("Advanced options to customize preprocessing and classifier behavior"));
		button.setOnAction(e -> {
			if (advancedOptions.promptToUpdateOptions()) {
				updateClassifier();
				overlayManager.predictionThreadsProperty().set(getLivePredictionThreads());
			}
		});
		return button;
	}

	private Button createLoadTrainingButton() {
		var button = createFixedWidthButton("Load training");
		button.setTooltip(new Tooltip("Train using annotations from more images in the current project"));
		button.setOnAction(e -> {
			if (trainingImageManager.promptToLoadTrainingImages()) {
				updateClassifier();
				int n = trainingImageManager.size();
				if (n > 0)
					button.setText("Load training (" + n + ")");
				else
					button.setText("Load training");
			}
		});
		button.disableProperty().bind(qupath.projectProperty().isNull());
		return button;
	}

	private ToggleButton createLivePredictionButton() {
		var button = new ToggleButton("Live prediction");
		button.setMaxWidth(Double.MAX_VALUE);
		button.selectedProperty().bindBidirectional(livePrediction);
		button.setTooltip(new Tooltip("Toggle whether to calculate classification 'live' while viewing the image"));
		return button;
	}


	private void handleStageFocussed(boolean isFocused) {
		if (isFocused) {
			overlayManager.ensureOverlaySet();
		}
	}


	/**
	 * Add to the list of default feature calculator builders that will be available when 
	 * this pane is opened.
	 * <p>
	 * This provides a mechanism to install additional feature calculators.
	 * <p>
	 * Note that the builder will only be added if it is not already present.
	 * @param builder the builder to be installed
	 * 
	 * @return true if the builder was added, false otherwise.
	 */
	public static synchronized void installDefaultFeatureClassificationBuilder(ImageDataOpBuilder builder) {
		if (!Platform.isFxApplicationThread()) {
			logger.debug("Delegating installDefaultFeatureClassificationBuilder to the application thread");
			Platform.runLater(() -> installDefaultFeatureClassificationBuilder(builder));
		}
		if (!defaultFeatureCalculatorBuilders.contains(builder)) {
			defaultFeatureCalculatorBuilders.add(builder);
		}
	}

		
	/**
	 * Update the available resolutions for the specified ImageData.
	 * @param imageData
	 */
	private void updateAvailableResolutions(ImageData<BufferedImage> imageData) {
		if (imageData == null) {
			return;
		}
		var selected = resolution.get();
		var requestedResolutions = ClassificationResolution.getDefaultResolutions(imageData, selected);
		if (!resolutions.equals(requestedResolutions)) {
			resolutions.setAll(ClassificationResolution.getDefaultResolutions(imageData, selected));
			for (var r : resolutions) {
				if (selected != null && selected.getPixelCalibration().equals(r.getPixelCalibration())) {
					comboResolutions.getSelectionModel().select(r);
					break;
				}
			}
			if (!resolutions.isEmpty() && comboResolutions.getSelectionModel().getSelectedItem() == null)
				comboResolutions.getSelectionModel().selectFirst();
		}
	}


	private void updateAvailableFeatureOpBuilders(ImageData<BufferedImage> imageData) {
		featureOpBuilders.add(MultiscaleImageDataOpBuilder.create2D(imageData));
		featureOpBuilders.add(MultiscaleImageDataOpBuilder.create3D(imageData));
		featureOpBuilders.addAll(defaultFeatureCalculatorBuilders);
	}
	
	
	private void updateFeatureCalculator() {
		var resolution = this.resolution.get();
		if (resolution == null)
			return;
		var cal = resolution.getPixelCalibration();
		var imageData = imageDataProperty.get();
		
		// Check we can support the requested channels before proceeding
		// This is a bit of a hack because we know some implementations will fail with more channels than OpenCV
		// can handle (on a call to OpenCVTools.mergeChannels).
		// We'd rather show a notification instead of just logging the error - although this risks being a problem
		// for an implementation that *would* work, so we may consider restricting the check to only known failures.
		var featureOpBuilder = opBuilder.get();
		var featureOp = featureOpBuilder.build(imageData, cal);
		int nFeatures = featureOp.getChannels(imageData).size();
		if (nFeatures > opencv_core.CV_CN_MAX) {
			Dialogs.showErrorNotification("Pixel classifier", "Too many features! Requested " + nFeatures + " but maximum is " + opencv_core.CV_CN_MAX +
					".\nFeatures will not be updated - please select a smaller number and continue training.");
			return;
		}
		helper.setFeatureOp(featureOp);
		var featureServer = helper.getFeatureServer(imageData);
		if (featureServer == null) {
			trainingViewerPane.getAvailableFeatures().clear();
		} else {
			trainingViewerPane.getAvailableFeatures().setAll(
					featureServer.getMetadata().getChannels().stream().map(ImageChannel::getName).toList()
			);
		}
		updateClassifier();
		checkFeaturesCompatible();
	}




	private int getLivePredictionThreads() {
		int n = advancedOptions.getNumThreads();
		return  n < 0 ? PathPrefs.numCommandThreadsProperty().get() : Math.max(n, 1);
	}

	
	private void updateClassifier() {
		if (livePrediction.get())
			doClassification();
		else
			overlayManager.resetOverlay();
	}


	private void doClassification() {
		var imageData = imageDataProperty.get();
		if (imageData == null) {
			if (qupath.getAllViewers().stream().noneMatch(v -> v.getImageData() != null)) {
				logger.debug("doClassification() called, but no images are open");
				return;
			}
		}

		var op = helper.getFeatureOp();
		if (op == null) {
			Dialogs.showWarningNotification("Pixel classifier", "No features selected!");
			return;
		}

		if (!op.supportsImage(imageData)) {
			Dialogs.showWarningNotification("Pixel classifier", "Selected features aren't compatible with the current image");
			return;
		}

		var model = statModel.get();
		if (model == null) {
			Dialogs.showErrorNotification("Pixel classifier", "No classifier selected!");
			return;
		}

		this.helper.setBoundaryStrategy(advancedOptions.getBoundaryStrategy());

		List<ClassifierTrainingData> allTrainingData = getAllTrainingData();
		if (allTrainingData.isEmpty()) {
			pieChart.reset();
			return;
		}

		var trainer = new ModelTrainer(helper, advancedOptions);

		try (var trainingData = ClassifierTrainingData.merge(allTrainingData)) {
			var trainedModel = trainer.train(model, trainingData);

			currentClassifier.set(trainedModel.createPixelClassifier(
					model.supportsProbabilities() ? outputType.get() : ImageServerMetadata.ChannelType.CLASSIFICATION));

			updatePieChartCounts(trainedModel.labels(), trainedModel.countsIndexedByLabels());
			trainingDetailsPane.update(
					trainedModel.model(),
					trainedModel.labels(),
					trainedModel.trainingTime());
			featureDetailsPane.update(
					trainedModel.model(),
					trainedModel.getFeatureNames(imageData));
		}
	}

	private void calculateVariableImportance() {
		var trainingImages = trainingImageManager.getTrainingImageData();
		if (trainingImages.isEmpty()) {
			logger.warn("Can't compute variable importance without an image open");
			return;
		}
		try (var rtrees = RTrees.create()) {
			rtrees.setMaxDepth(0);
			rtrees.setTermCriteria(
					new TermCriteria(TermCriteria.COUNT, 100, 0));
			rtrees.setCalculateVarImportance(true);

			var model = OpenCVClassifiers.wrapStatModel(rtrees);
			List<ClassifierTrainingData> allTrainingData = helper.createTrainingData(trainingImages);
			if (allTrainingData.isEmpty()) {
				logger.warn("Can't compute variable importance without training data!");
				return;
			}
			var trainer = new ModelTrainer(helper, advancedOptions);
			try (var scope = new PointerScope()) {
				try (ClassifierTrainingData otherImages = ClassifierTrainingData.merge(allTrainingData)) {
					var trainedModel = trainer.train(model, otherImages);
					featureDetailsPane.update(model, trainedModel.getFeatureNames(trainingImages.iterator().next()));
				}
			}
		} catch (Exception e) {
			logger.error("Error calculating variable importance: {}", e.getMessage(), e);
		}
	}

	private static OpenCVStatModel duplicateStatModel(OpenCVStatModel model) {
		var gson = GsonTools.getInstance();
		return gson.fromJson(
				gson.toJson(model, OpenCVStatModel.class), OpenCVStatModel.class
		);
	}

	private List<ClassifierTrainingData> getAllTrainingData() {
		try {
			var trainingImages = trainingImageManager.getTrainingImageData();
			if (trainingImages.size() > 1)
				logger.debug("Creating training data from {} images", trainingImages.size());
			return helper.createTrainingData(trainingImages);
		} catch (Exception e) {
			logger.error("Error when updating training data", e);
			return List.of();
		}
	}

	/**
	 * Evaluate the predictions of a classifier.
	 * @param name name for confusion matrix
	 * @param samples the samples to use as input to the model
	 * @param normCatTargets the normalized categorical targets
	 * @param classLabels the class labels associated with the targets; see {@link TrainData#getTrainNormCatResponses()}
	 * @param model the model to use for prediction
	 * @param preprocessor the preprocessor to apply to the samples
	 * @param labels the QuPath-friendly mapping of classes to integer labels
	 * @return
	 */
	private static ConfusionMatrix<PathClass> evaluate(String name, Mat samples, Mat normCatTargets, Mat classLabels, OpenCVStatModel model,
													   FeaturePreprocessor preprocessor, Map<PathClass, Integer> labels) {
		if (preprocessor != null) {
			samples = samples.clone();
			preprocessor.apply(samples, false);
		}
		var confusion = new ConfusionMatrix<>(name, List.copyOf(labels.keySet()));
		IntBuffer bufferGroundTruth = normCatTargets.createBuffer();
		IntBuffer bufferClassList = classLabels.createBuffer();

		// This assumes that our labels are dense and start with 0... rather than being
		// all other the place, and potentially negative
		int n = labels.values().stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
		PathClass[] pathClasses = new PathClass[n];
		for (var entry : labels.entrySet()) {
			pathClasses[entry.getValue()] = entry.getKey();
		}

		var testResults = new Mat();
		model.predict(samples, testResults, new Mat());
		IntBuffer bufferPrediction = testResults.createBuffer();
		int nTest = testResults.rows();
		for (int i = 0; i < nTest; i++) {
			int targetClassInd = bufferClassList.get(bufferGroundTruth.get(i));
			confusion.accumulate(
					pathClasses[targetClassInd],
					pathClasses[bufferPrediction.get(i)]
			);
		}
		return confusion;
	}


	private static Mat createWeights(Mat targets, int[] countsIndexedByLabels) {
		int n = (int) targets.total();
		IntBuffer buffer = targets.createBuffer();
		Mat weights = new Mat(n, 1, opencv_core.CV_32FC1);
		try (FloatIndexer bufferWeights = weights.createIndexer()) {
			float[] weightArray = new float[countsIndexedByLabels.length];
			for (int i = 0; i < weightArray.length; i++) {
				int c = countsIndexedByLabels[i];
				weightArray[i] = c == 0 ? 1 : (float) n / c;
			}
			for (int i = 0; i < n; i++) {
				int label = buffer.get(i);
				bufferWeights.put(i, weightArray[label]);
			}
		}
		return weights;
	}


	private static int[] createCountsIndexedByLabels(Mat targets, Map<PathClass, Integer> labels) {
		int n = (int) targets.total();
		IntBuffer buffer = targets.createBuffer();
		var countsIndexedByLabels = new int[labels.values().stream().mapToInt(Integer::intValue).max().orElse(0) + 1];
		for (int i = 0; i < n; i++) {
			countsIndexedByLabels[buffer.get(i)] += 1;
		}
		return countsIndexedByLabels;
	}


	private void updatePieChartCounts(Map<PathClass, Integer> labels, int[] countsIndexedByLabels) {
		Map<PathClass, Integer> counts = new LinkedHashMap<>();
		for (var entry : labels.entrySet()) {
			counts.put(entry.getKey(), countsIndexedByLabels[entry.getValue()]);
		}
		pieChart.updateCounts(counts);
	}

	private void handleStageShowingChange(boolean isShowing) {
		if (isShowing) {
			handleStageShown();
		} else {
			handleStageHidden();
		}
	}

	private void handleStageShown() {
		initializeSubscriptions();
		this.overlayManager.start();
		var imageData = imageDataProperty.get();
		handleImageDataChange(null, imageData);

		// Don't forget where we were after we're hidden
		FXUtils.retainWindowPosition(stage);
	}


	private void handleStageHidden() {
		subscription.unsubscribe();
		for (var viewer : qupath.getAllViewers()) {
			var hierarchy = viewer.getHierarchy();
			if (hierarchy != null)
				hierarchy.removeListener(hierarchyListener);
		}
		overlayManager.stop();
		trainingImageManager.reset();
	}
	
	
	
	private void promptToEditClassifierParameters() {
		var model = statModel.get();
		if (model == null) {
			Dialogs.showErrorMessage("Edit parameters", "No classifier selected!");
			return;
		}
		GuiTools.showParameterDialog("Edit parameters", model.getParameterList());
		updateClassifier();
	}


    private void promptToAddResolution() {
		var imageData = imageDataProperty.get();
		ImageServer<BufferedImage> server = imageData == null ? null : imageData.getServer();
		if (server == null) {
			GuiTools.showNoImageError("Add resolution");
			return;
		}
		String units;
		Double pixelSize;
		PixelCalibration cal = server.getPixelCalibration();
		if (cal.hasPixelSizeMicrons()) {
			pixelSize = Dialogs.showInputDialog("Add resolution", "Enter requested pixel size in " + GeneralTools.micrometerSymbol(), 1.0);
			units = PixelCalibration.MICROMETER;
		} else {
			pixelSize = Dialogs.showInputDialog("Add resolution", "Enter requested downsample factor", 1.0);
			units = null;
		}
		
		if (pixelSize == null)
			return;
		
		ClassificationResolution res;
		if (PixelCalibration.MICROMETER.equals(units)) {
			double scale = pixelSize / cal.getAveragedPixelSizeMicrons();
			res = new ClassificationResolution("Custom", cal.createScaledInstance(scale, scale, 1));
		} else
			res = new ClassificationResolution("Custom", cal.createScaledInstance(pixelSize, pixelSize, 1));

		List<ClassificationResolution> temp = new ArrayList<>(resolutions);
		temp.add(res);
		temp.sort(Comparator.comparingDouble((ClassificationResolution w) -> w.cal.getAveragedPixelSize().doubleValue()));
		resolutions.setAll(temp);
		comboResolutions.getSelectionModel().select(res);
	}
	
	private void updateResolution(ClassificationResolution resolution) {
		trainingViewerPane.setResolution(resolution);
		ImageServer<BufferedImage> server = imageDataProperty.get() == null ? null : imageDataProperty.get().getServer();
		if (server == null || resolution == null)
			return;
		helper.setResolution(resolution.cal);
	}


	private void computeCrossValidation() {
		// TODO: Train models using cross validation in a background thread
		var model = statModel.get();
		if (model == null) {
			logger.warn("Can't compute cross validation, no model available");
			return;
		}
		var modelCV = duplicateStatModel(model);
		List<ConfusionMatrix<PathClass>> matrices = new ArrayList<>();
		List<ClassifierTrainingData> allTrainingData = getAllTrainingData();
		if (allTrainingData.isEmpty()) {
			logger.warn("Can't compute cross validation, no training data available");
			return;
		}
		var firstData = allTrainingData.getFirst();
		var labels = firstData.getLabelMap(); // Labels should be identical, via PixelClassifierTraining
		String splitType = allTrainingData.size() + " images";
		if (allTrainingData.size() == 1) {
			logger.warn("Splitting the training data");
			int nSplits = Math.min(5, firstData.size() / 10);
			if (nSplits <= 1) {
				logger.error("No enough data to compute cross validation (size={})", firstData.size());
				return;
			}
			allTrainingData = firstData.split(nSplits, new Random(advancedOptions.getRngSeed() + 1));
			splitType = "single image";
		}
		var trainer = new ModelTrainer(helper, advancedOptions);

		try (var scope = new PointerScope()) {
			for (int i = 0; i < allTrainingData.size(); i++) {
				var holdOutData = allTrainingData.get(i);
				try (ClassifierTrainingData otherImages = ClassifierTrainingData.merge(
						allTrainingData.stream().filter(d -> d != holdOutData).toList()
				)) {
					var trainedModel = trainer.train(modelCV, otherImages);
					try (var holdOutTest = holdOutData.getTrainData()) {
						var confusion = evaluate(
								holdOutData.getName(),
								holdOutTest.getTrainSamples(),
								holdOutTest.getTrainNormCatResponses(),
								holdOutTest.getClassLabels(),
								trainedModel.model(),
								trainedModel.featurePreprocessor(),
								labels);
						matrices.add(confusion);
						logger.debug("Fold {}: Accuracy = {}, F1 = {}", i + 1, confusion.getAccuracy(), confusion.getF1());
					}
				}
			}
			if (matrices.size() > 1)
				matrices.addFirst(ConfusionMatrix.sum("All splits (" + splitType + ")", matrices));
			metricsBrowser.getConfusionMatrices().setAll(matrices);
		}
	}
	

	private void handleHierarchyChange(PathObjectHierarchyEvent event) {
		// We want to update the classifier for every relevant event...  but not any unnecessary events
		if (event.isChanging() || event.isObjectMeasurementEvent())
			return;
		var changedObjects = event.getChangedObjects();
		if (event.isStructureChangeEvent() || event.isObjectClassificationEvent() || !changedObjects.isEmpty()) {
			if (event.isObjectClassificationEvent() || changedObjects.stream().anyMatch(p -> p.getPathClass() != null)) {
				if (changedObjects.stream().anyMatch(PathObject::isAnnotation) &&
						!(event.isAddedOrRemovedEvent() && changedObjects.stream().allMatch(PathObject::isLocked)))
					updateClassifier();
			}
		}
	}


	/**
	 * Trainer for an {@link OpenCVStatModel} to use with a {@link PixelClassifier}.
	 * <p>
	 * This effectively snapshots the required settings in its constructor, so that the trainer could be reused
	 * for multiple models.
	 * <p>
	 * Its purpose is to simplify the process of training multiple models using identical settings but different
	 * data, for example for cross-validation.
	 */
	private static class ModelTrainer {

		private final PixelCalibration resolution;
		private final int actualMaxSamples;
		private final FeatureNormalization featureNormalization;
		private final ImageDataOp baseFeatureCalculator;
		private final boolean reweightSamples;
		private final int rngSeed;

		ModelTrainer(PixelClassifierTraining helper, PixelClassifierAdvancedOptions advancedOptions) {
			this.resolution = helper.getResolution();
			this.baseFeatureCalculator = helper.getFeatureOp();;
			this.actualMaxSamples = advancedOptions.getMaxSamples();
			this.reweightSamples = advancedOptions.getReweightSamples();
			this.featureNormalization = advancedOptions.getNormalization();
			this.rngSeed = advancedOptions.getRngSeed();
		}

		public TrainedModel train(OpenCVStatModel model, ClassifierTrainingData trainingData) {

			opencv_core.setRNGSeed(rngSeed);

			var labels = trainingData.getLabelMap();

			try (var trainData = trainingData.getTrainData()) {
				if (actualMaxSamples > 0 && trainData.getNTrainSamples() > actualMaxSamples)
					trainData.setTrainTestSplit(actualMaxSamples, true);
				else
					trainData.shuffleTrainTest();

				// Apply feature preprocessing, if we need to
				var preprocessor = featureNormalization.build(trainData.getTrainSamples(), false);
				ImageDataOp featureCalculator;
				if (preprocessor.doesSomething()) {
					var preprocessingOp = ImageOps.ML.preprocessor(preprocessor);
					featureCalculator = baseFeatureCalculator.appendOps(preprocessingOp);
				} else {
					featureCalculator = baseFeatureCalculator;
				}

				// Using getTrainNormCatResponses() causes confusion if classes are not represented
				var targets = trainData.getTrainResponses();
				var countsIndexedByLabels = createCountsIndexedByLabels(targets, labels);
				Mat weights = reweightSamples ? createWeights(targets, countsIndexedByLabels) : null;

				// Create TrainData in an appropriate format (e.g. labels or one-hot encoding)
				var trainSamples = trainData.getTrainSamples();
				preprocessor.apply(trainSamples, false);

				Duration trainingTime;
				int nLabels = labels.values().stream().mapToInt(Integer::intValue).max().orElse(0);
				try (var modelTrainData = model.createTrainData(trainSamples, targets, nLabels, weights, false)) {
					//		 logger.info("Training data: {} x {}, Target data: {} x {}", trainSamples.rows(), trainSamples.cols(), trainResponses.rows(), trainResponses.cols());
					long startTime = System.nanoTime();
					model.train(modelTrainData);
					long endTime = System.nanoTime();
					trainingTime = Duration.ofNanos(endTime - startTime);

					return new TrainedModel(
							model,
							featureCalculator,
							preprocessor,
							labels,
							countsIndexedByLabels,
							resolution,
							trainingTime
							);
				}
			}
		}

	}

	record TrainedModel(OpenCVStatModel model,
							 ImageDataOp featureCalculator,
							 FeaturePreprocessor featurePreprocessor,
							 Map<PathClass, Integer> labels,
							 int[] countsIndexedByLabels,
							 PixelCalibration resolution,
							 Duration trainingTime) {

		PixelClassifier createPixelClassifier(ImageServerMetadata.ChannelType outputType) {
			return createPixelClassifier(512, 512, outputType);
		}

		PixelClassifier createPixelClassifier(int inputWidth, int inputHeight, ImageServerMetadata.ChannelType outputType) {
			if (!model.supportsProbabilities() && !model.supportsProbabilities()) {
				logger.warn("Output type {} not supported, will use {}", outputType, ImageServerMetadata.ChannelType.CLASSIFICATION);
			}

			// Channels are needed for probability output (and work for classification as well)
			var labels2 = new TreeMap<Integer, PathClass>();
			for (var entry : labels.entrySet()) {
				var previous = labels2.put(entry.getValue(), entry.getKey());
				if (previous != null)
					logger.warn("Duplicate label found! {} matches with {} and {}, only the latter be used", entry.getValue(), previous, entry.getKey());
			}
			var channels = ServerTools.classificationLabelsToChannels(labels2, true);

			PixelClassifierMetadata metadata = new PixelClassifierMetadata.Builder()
					.inputResolution(resolution)
					.inputShape(inputWidth, inputHeight)
					.setChannelType(outputType)
					.outputChannels(channels)
					.build();

			return PixelClassifiers.createClassifier(model, featureCalculator, metadata, true);
		}

		List<String> getFeatureNames(ImageData<BufferedImage> imageData) {
			return featureCalculator.getChannels(imageData).stream().map(ImageChannel::getName).toList();
		}

	}


}
