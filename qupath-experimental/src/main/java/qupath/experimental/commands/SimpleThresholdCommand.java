package qupath.experimental.commands;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import qupath.lib.classifiers.pixel.PixelClassificationImageServer;
import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.ml.ClassificationResolution;
import qupath.lib.gui.ml.PixelClassificationOverlay;
import qupath.lib.gui.ml.PixelClassifierTools;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.overlays.PathOverlay;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ColorTransforms;
import qupath.lib.images.servers.ColorTransforms.ColorTransform;
import qupath.lib.objects.classes.PathClass;
import qupath.opencv.ml.pixel.PixelClassifiers;
import qupath.opencv.ops.ImageOp;
import qupath.opencv.ops.ImageOps;
import qupath.opencv.tools.MultiscaleFeatures;
import qupath.opencv.tools.MultiscaleFeatures.MultiscaleFeature;

/**
 * Apply simple thresholding to an image via the pixel classification framework to support 
 * thresholding at any resolution, optionally with visual feedback via an overlay.
 * 
 * @author Pete Bankhead
 *
 */
public class SimpleThresholdCommand implements Runnable {
	
	private QuPathGUI qupath;
	
	private Stage stage;
	
	/**
	 * Constructor.
	 * @param qupath
	 */
	public SimpleThresholdCommand(QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
		if (qupath.getImageData() == null) {
			Dialogs.showNoImageError("Simple threshold");
			return;
		}
		if (stage == null || !stage.isShowing())
			showGUI();
		else
			stage.toFront();
	}
	
	private ComboBox<ClassificationResolution> comboResolutions = new ComboBox<>();
	private ReadOnlyObjectProperty<ClassificationResolution> selectedResolution = comboResolutions.getSelectionModel().selectedItemProperty();

	private ComboBox<MultiscaleFeatures.MultiscaleFeature> comboPrefilter = new ComboBox<>();
	private ReadOnlyObjectProperty<MultiscaleFeatures.MultiscaleFeature> selectedPrefilter = comboPrefilter.getSelectionModel().selectedItemProperty();

	private ComboBox<PathClass> classificationsBelow = new ComboBox<>();
	private ComboBox<PathClass> classificationsAbove = new ComboBox<>();
	
	private ComboBox<ColorTransform> transforms = new ComboBox<>();
	private ReadOnlyObjectProperty<ColorTransform> selectedChannel = transforms.getSelectionModel().selectedItemProperty();
	
	private Spinner<Double> sigmaSpinner = new Spinner<>(new SpinnerValueFactory.DoubleSpinnerValueFactory(0, 16.0, 0.0, 0.5));
	private ReadOnlyObjectProperty<Double> sigma = sigmaSpinner.valueProperty();

	private Map<ColorTransform, Double> availableTransforms = new LinkedHashMap<>();
	
	private SpinnerValueFactory.DoubleSpinnerValueFactory thresholdValueFactory = new SpinnerValueFactory.DoubleSpinnerValueFactory(-Double.MAX_VALUE, Double.MAX_VALUE, 0.0, 0.5);
	private Spinner<Double> spinner = new Spinner<>(thresholdValueFactory);
	private ReadOnlyObjectProperty<Double> threshold = spinner.valueProperty();
	
	private CheckBox cbLimitToAnnotations = new CheckBox("Limit to annotations");
	
	private ObjectProperty<PixelClassificationOverlay> selectedOverlay = new SimpleObjectProperty<>();
	private ObjectProperty<PixelClassifier> currentClassifier = new SimpleObjectProperty<>();

	private Map<QuPathViewer, PathOverlay> map = new WeakHashMap<>();
	
	
	
	private void showGUI() {
		
		var pane = new GridPane();

		classificationsAbove.setItems(qupath.getAvailablePathClasses());
		classificationsBelow.setItems(qupath.getAvailablePathClasses());
		
		cbLimitToAnnotations.selectedProperty().addListener((v, o, n) -> {
			var overlay = selectedOverlay.get();
			if (overlay != null)
				overlay.setUseAnnotationMask(n);
		});
				
		int row = 0;
		
		Label labelResolution = new Label("Resolution");
		labelResolution.setLabelFor(comboResolutions);
		PaneTools.addGridRow(pane, row++, 0, "Select image resolution to threshold (higher values mean lower resolution, and faster thresholding)",
				labelResolution, comboResolutions, comboResolutions);

		Label label = new Label("Channel");
		label.setLabelFor(transforms);
		PaneTools.addGridRow(pane, row++, 0, "Select channel to threshold", label, transforms, transforms);
		
		Label labelPrefilter = new Label("Prefilter");
		labelPrefilter.setLabelFor(comboPrefilter);
		comboPrefilter.getItems().setAll(
				MultiscaleFeature.GAUSSIAN,
				MultiscaleFeature.LAPLACIAN,
				MultiscaleFeature.GRADIENT_MAGNITUDE,
				MultiscaleFeature.WEIGHTED_STD_DEV
				);
		comboPrefilter.getSelectionModel().selectFirst();
		PaneTools.addGridRow(pane, row++, 0, "Select image smoothing filter (Gaussian is usually best)", labelPrefilter, comboPrefilter, comboPrefilter);

		label = new Label("Smoothing sigma");
		label.setLabelFor(sigmaSpinner);
		Label labelSigma = new Label();
		labelSigma.textProperty().bind(
				Bindings.createStringBinding(() -> GeneralTools.formatNumber(sigma.get(), 2), sigma)
				);
		GuiTools.restrictSpinnerInputToNumber(sigmaSpinner, true);
		PaneTools.addGridRow(pane, row++, 0, "Select smoothing sigma value (higher values give a smoother result)", label, sigmaSpinner, labelSigma);

		label = new Label("Threshold");
		label.setLabelFor(spinner);
		Label labelThreshold = new Label();
		labelThreshold.textProperty().bind(
				Bindings.createStringBinding(() -> GeneralTools.formatNumber(threshold.get(), 2), threshold)
				);
		GuiTools.restrictSpinnerInputToNumber(spinner, true);
		PaneTools.addGridRow(pane, row++, 0, "Select threshold value", label, spinner, labelThreshold);

		Label labelAbove = new Label("Above threshold");
		labelAbove.setLabelFor(classificationsAbove);

		Label labelBelow = new Label("Below threshold");
		labelBelow.setLabelFor(classificationsBelow);
		PaneTools.addGridRow(pane, row++, 0, "Select classification for pixels above the threshold."
				+ "\nDouble-click on labels to switch above & below.", labelAbove, classificationsAbove, classificationsAbove);
		PaneTools.addGridRow(pane, row++, 0, "Select classification for pixels less than or equal to the threshold."
				+ "\nDouble-click on labels to switch above & below.", labelBelow, classificationsBelow, classificationsBelow);
		
		labelAbove.setOnMouseClicked(e -> {
			if (e.getClickCount() == 2)
				switchClassifications();
		});
		labelBelow.setOnMouseClicked(labelAbove.getOnMouseClicked());

		PaneTools.addGridRow(pane,  row++, 0, "Apply live prediction only to annotated regions (useful for previewing)", cbLimitToAnnotations, cbLimitToAnnotations, cbLimitToAnnotations);
		
		var enableButtons = qupath.viewerProperty().isNotNull().and(selectedOverlay.isNotNull()).and(currentClassifier.isNotNull());
		
		var btnSave = new Button("Save as pixel classifier");
		btnSave.disableProperty().bind(enableButtons.not());
		btnSave.setOnAction(e -> {
			try {
				PixelClassifierTools.promptToSaveClassifier(qupath.getProject(), currentClassifier.get());
			} catch (IOException ex) {
				Dialogs.showErrorMessage("Save classifier", ex);
			}
		});
		PaneTools.addGridRow(pane, row++, 0, "Save current thresholder as a pixel classifier", btnSave, btnSave, btnSave);

		
		var btnCreateObjects = new Button("Create objects");
		btnCreateObjects.disableProperty().bind(enableButtons.not());
		var btnClassifyObjects = new Button("Classify detections");
		btnClassifyObjects.disableProperty().bind(enableButtons.not());
		var tilePane = PaneTools.createColumnGrid(btnCreateObjects, btnClassifyObjects);
//		btnCreateObjects.prefWidthProperty().bind(btnClassifyObjects.widthProperty());
		
		btnCreateObjects.setOnAction(e -> {
			PixelClassifierTools.promptToCreateObjects(qupath.getImageData(), 
					(PixelClassificationImageServer)selectedOverlay.get().getPixelClassificationServer());
		});
		btnClassifyObjects.setOnAction(e -> {
			PixelClassifierTools.classifyDetectionsByCentroid(qupath.getImageData(), currentClassifier.get(), true);
		});
		PaneTools.addGridRow(pane, row++, 0, null, tilePane, tilePane, tilePane);
		
		selectedPrefilter.addListener((v, o, n) -> updateClassification());
		transforms.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
			thresholdValueFactory.setAmountToStepBy(availableTransforms.getOrDefault(n, 0.5));
			updateClassification();
		});
		sigma.addListener((v, o, n) -> updateClassification());
		threshold.addListener((v, o, n) -> updateClassification());
		selectedResolution.addListener((v, o, n) -> updateClassification());
		
		classificationsAbove.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> updateClassification());
		classificationsBelow.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> updateClassification());
		
		pane.setVgap(6.0);
		pane.setHgap(6.0);
		pane.setPadding(new Insets(10.0));
		
		PaneTools.setMaxWidth(Double.MAX_VALUE, comboResolutions, comboPrefilter,
				transforms, spinner, sigmaSpinner, classificationsAbove, classificationsBelow,
				btnSave, btnClassifyObjects, btnCreateObjects, tilePane);
		PaneTools.setFillWidth(Boolean.TRUE, comboResolutions, comboPrefilter,
				transforms, spinner, sigmaSpinner, classificationsAbove, classificationsBelow,
				btnSave, btnClassifyObjects, btnCreateObjects, tilePane);
		PaneTools.setHGrowPriority(Priority.ALWAYS, comboResolutions, comboPrefilter,
				transforms, spinner, sigmaSpinner, classificationsAbove, classificationsBelow,
				btnSave, btnClassifyObjects, btnCreateObjects, tilePane);
		
		updateGUI();
		
		pane.setMinSize(GridPane.USE_COMPUTED_SIZE, GridPane.USE_COMPUTED_SIZE);
		pane.setMaxSize(GridPane.USE_COMPUTED_SIZE, GridPane.USE_COMPUTED_SIZE);
		
		stage = new Stage();
		stage.setTitle("Simple threshold");
		stage.initOwner(qupath.getStage());
		stage.setScene(new Scene(pane));
		stage.sizeToScene();
		stage.setResizable(false);
		stage.show();
		
		stage.setOnHiding(e -> {
			for (var entry : map.entrySet()) {
				if (entry.getKey().getCustomPixelLayerOverlay() == entry.getValue()) {
					var imageData = entry.getKey().getImageData();
					if (imageData != null)
						PixelClassificationImageServer.setPixelLayer(entry.getKey().getImageData(), null);
					selectedOverlay.set(null);
					entry.getKey().resetCustomPixelLayerOverlay();
				}
			}
		});
	}
	
	/**
	 * Switch high and low classifications.
	 */
	private void switchClassifications() {
		var below = classificationsBelow.getSelectionModel().getSelectedItem();
		var above = classificationsAbove.getSelectionModel().getSelectedItem();
		classificationsBelow.getSelectionModel().select(above);
		classificationsAbove.getSelectionModel().select(below);
	}
	
	
	private void updateGUI() {
		
		var viewer = qupath.getViewer();
		var imageData = viewer.getImageData();
		if (imageData == null) {
			transforms.getItems().clear();
			transforms.setDisable(true);
			spinner.setDisable(true);
			sigmaSpinner.setDisable(true);
			return;
		}
		transforms.setDisable(false);
		spinner.setDisable(false);
		sigmaSpinner.setDisable(false);
		
		sigmaSpinner.setEditable(true);
		spinner.setEditable(true);
		
		comboResolutions.getItems().setAll(ClassificationResolution.getDefaultResolutions(imageData, selectedResolution.get()));
		if (selectedResolution.get() == null)
			comboResolutions.getSelectionModel().selectLast();
		
		transforms.getItems().setAll(getAvailableTransforms(imageData));
		if (transforms.getSelectionModel().getSelectedItem() == null)
			transforms.getSelectionModel().selectFirst();
		
	}
	
	private void updateClassification() {
		
		var viewer = qupath.getViewer();
		if (viewer == null)
			return;
		
		var imageData = viewer.getImageData();
		if (imageData == null) {
			selectedOverlay.set(null);
			viewer.resetCustomPixelLayerOverlay();
			return;
		}
		
		var channel = selectedChannel.get();
		var thresholdValue = threshold.get();
		var resolution = selectedResolution.get();
		if (channel == null || thresholdValue == null || resolution == null)
			return;
		
		var feature = selectedPrefilter.get();
		double sigmaValue = sigma.get();
		
		PixelClassifier classifier;
		
		List<ImageOp> ops = new ArrayList<>();
		if (feature != null && sigmaValue > 0) {
			if (feature == MultiscaleFeature.GAUSSIAN)
				ops.add(ImageOps.Filters.gaussianBlur(sigmaValue));
			else
				ops.add(ImageOps.Filters.features(Collections.singletonList(feature), sigmaValue, sigmaValue));
		}
		
		ops.add(ImageOps.Threshold.threshold(threshold.get()));
		Map<Integer, PathClass> classifications = new LinkedHashMap<>();
		classifications.put(0, classificationsBelow.getSelectionModel().getSelectedItem());
		classifications.put(1, classificationsAbove.getSelectionModel().getSelectedItem());
		
		var op = ImageOps.Core.sequential(ops);
		
		var transformer = ImageOps.buildImageDataOp(channel).appendOps(op);
		
		classifier = PixelClassifiers.createThresholdingClassifier(
				transformer,
				resolution.getPixelCalibration(),
				classifications);

		// Try (admittedly unsuccessfully) to reduce flicker
		viewer.setMinimumRepaintSpacingMillis(1000L);
		viewer.repaint();
		
		var overlay = PixelClassificationOverlay.createPixelClassificationOverlay(viewer, classifier);
		overlay.setLivePrediction(true);
		overlay.setUseAnnotationMask(cbLimitToAnnotations.isSelected());
		selectedOverlay.set(overlay);
		this.currentClassifier.set(classifier);
		viewer.setCustomPixelLayerOverlay(overlay);
		map.put(viewer, overlay);
		imageData.getHierarchy().fireObjectMeasurementsChangedEvent(this, imageData.getHierarchy().getAnnotationObjects());

		viewer.resetMinimumRepaintSpacingMillis();
	}
	
	
	
	

	/**
	 * Get a list of relevant color transforms for a specific image.
	 * @param imageData
	 * @return
	 */
	private Collection<ColorTransform> getAvailableTransforms(ImageData<BufferedImage> imageData) {
		var validChannels = new LinkedHashMap<ColorTransform, Double>();
		var server = imageData.getServer();
		double increment = server.getPixelType().isFloatingPoint() ? 0.1 : 0.5;
		double incrementDeconvolved = 0.05;

		for (var channel : server.getMetadata().getChannels()) {
			validChannels.put(ColorTransforms.createChannelExtractor(channel.getName()), increment);
		}
		var stains = imageData.getColorDeconvolutionStains();
		if (stains != null) {
			validChannels.put(ColorTransforms.createColorDeconvolvedChannel(stains, 1), incrementDeconvolved);
			validChannels.put(ColorTransforms.createColorDeconvolvedChannel(stains, 2), incrementDeconvolved);
			validChannels.put(ColorTransforms.createColorDeconvolvedChannel(stains, 3), incrementDeconvolved);
		}
		if (server.nChannels() > 1) {
			validChannels.put(ColorTransforms.createMeanChannelTransform(), increment);
			validChannels.put(ColorTransforms.createMaximumChannelTransform(), increment);
			validChannels.put(ColorTransforms.createMinimumChannelTransform(), increment);
		}
		availableTransforms = validChannels;
		return validChannels.keySet();
	}
	
	
}
