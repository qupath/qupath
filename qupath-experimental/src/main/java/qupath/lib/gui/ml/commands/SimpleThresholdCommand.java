package qupath.lib.gui.ml.commands;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
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
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.helpers.PaneToolsFX;
import qupath.lib.gui.ml.PixelClassificationOverlay;
import qupath.lib.gui.ml.PixelClassifierImageSelectionPane;
import qupath.lib.gui.ml.PixelClassifierTools;
import qupath.lib.gui.ml.PixelClassifierImageSelectionPane.ClassificationResolution;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.overlays.PathOverlay;
import qupath.lib.images.ImageData;
import qupath.lib.objects.classes.PathClass;
import qupath.opencv.ml.pixel.PixelClassifiers;
import qupath.opencv.ml.pixel.ValueToClassification;
import qupath.opencv.ml.pixel.ValueToClassification.ThresholdClassifier;
import qupath.opencv.ml.pixel.features.ColorTransforms;
import qupath.opencv.ml.pixel.features.ColorTransforms.ColorTransform;
import qupath.opencv.ml.pixel.features.FeatureCalculators;
import qupath.opencv.tools.MultiscaleFeatures;
import qupath.opencv.tools.MultiscaleFeatures.MultiscaleFeature;

/**
 * Apply simple thresholding to an image via the pixel classification framework to support 
 * thresholding at any resolution, optionally with visual feedback via an overlay.
 * <p>
 * TODO: This is currently unfinished!
 * 
 * @author Pete Bankhead
 *
 */
public class SimpleThresholdCommand implements PathCommand {
	
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
			DisplayHelpers.showNoImageError("Simple threshold");
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
	private ComboBox<PathClass> classificationsEqual = new ComboBox<>();
	
	private ComboBox<ColorTransform> transforms = new ComboBox<>();
	private ReadOnlyObjectProperty<ColorTransform> selectedChannel = transforms.getSelectionModel().selectedItemProperty();
//	private Slider slider = new Slider();
//	private DoubleProperty threshold = slider.valueProperty();
	
	private Spinner<Double> sigmaSpinner = new Spinner<>(new SpinnerValueFactory.DoubleSpinnerValueFactory(0, 16.0, 0.0));
	private ReadOnlyObjectProperty<Double> sigma = sigmaSpinner.valueProperty();

	private Spinner<Double> spinner = new Spinner<>(new SpinnerValueFactory.DoubleSpinnerValueFactory(-Double.MAX_VALUE, Double.MAX_VALUE, 0.0));
	private ReadOnlyObjectProperty<Double> threshold = spinner.valueProperty();
	
	private ObjectProperty<PixelClassificationOverlay> selectedOverlay = new SimpleObjectProperty<>();
	private ObjectProperty<PixelClassifier> currentClassifier = new SimpleObjectProperty<>();

	private Map<QuPathViewer, PathOverlay> map = new WeakHashMap<>();
	
	private void showGUI() {
		
		var pane = new GridPane();

		classificationsAbove.setItems(qupath.getAvailablePathClasses());
		classificationsEqual.setItems(qupath.getAvailablePathClasses());
		classificationsBelow.setItems(qupath.getAvailablePathClasses());
		
		int row = 0;
		
		Label labelResolution = new Label("Resolution");
		labelResolution.setLabelFor(comboResolutions);
		PaneToolsFX.addGridRow(pane, row++, 0, "Select image resolution to threshold (higher values mean lower resolution, and faster thresholding)",
				labelResolution, comboResolutions, comboResolutions);

		Label label = new Label("Channel");
		label.setLabelFor(transforms);
		PaneToolsFX.addGridRow(pane, row++, 0, "Select channel to threshold", label, transforms, transforms);
		
		Label labelPrefilter = new Label("Prefilter");
		labelPrefilter.setLabelFor(comboPrefilter);
		comboPrefilter.getItems().setAll(
				MultiscaleFeature.GAUSSIAN,
				MultiscaleFeature.LAPLACIAN,
				MultiscaleFeature.GRADIENT_MAGNITUDE,
				MultiscaleFeature.WEIGHTED_STD_DEV
				);
		comboPrefilter.getSelectionModel().selectFirst();
		PaneToolsFX.addGridRow(pane, row++, 0, "Select image smoothing filter (Gaussian is usually best)", labelPrefilter, comboPrefilter, comboPrefilter);

		label = new Label("Smoothing sigma");
		label.setLabelFor(sigmaSpinner);
		Label labelSigma = new Label();
		labelSigma.textProperty().bind(
				Bindings.createStringBinding(() -> GeneralTools.formatNumber(sigma.get(), 2), sigma)
				);
		PaneToolsFX.addGridRow(pane, row++, 0, "Select smoothing sigma value (higher values give a smoother result)", label, sigmaSpinner, labelSigma);

		label = new Label("Threshold");
		label.setLabelFor(spinner);
		Label labelThreshold = new Label();
		labelThreshold.textProperty().bind(
				Bindings.createStringBinding(() -> GeneralTools.formatNumber(threshold.get(), 2), threshold)
				);
		PaneToolsFX.addGridRow(pane, row++, 0, "Select threshold value", label, spinner, labelThreshold);

		Label labelAbove = new Label("Above threshold");
		labelAbove.setLabelFor(classificationsAbove);

		Label labelEqual = new Label("Equal to threshold");
		labelEqual.setLabelFor(classificationsEqual);

		Label labelBelow = new Label("Below threshold");
		labelBelow.setLabelFor(classificationsBelow);
		PaneToolsFX.addGridRow(pane, row++, 0, "Select classification for pixels above the threshold", labelAbove, classificationsAbove, classificationsAbove);
		PaneToolsFX.addGridRow(pane, row++, 0, "Select classification for pixels with exactly the threshold value", labelEqual, classificationsEqual, classificationsEqual);
		PaneToolsFX.addGridRow(pane, row++, 0, "Select classification for pixels below the threshold", labelBelow, classificationsBelow, classificationsBelow);

//		var paneLabels = PaneToolsFX.createColumnGrid(labelBelow, labelEqual, labelAbove);
//		var paneCombos = PaneToolsFX.createColumnGrid(classificationsBelow, classificationsEqual, classificationsAbove);
//		PaneToolsFX.addGridRow(pane, row++, 0, null, paneLabels, paneLabels);
//		PaneToolsFX.addGridRow(pane, row++, 0, null, paneCombos, paneCombos);
		
		var enableButtons = qupath.viewerProperty().isNotNull().and(selectedOverlay.isNotNull()).and(currentClassifier.isNotNull());
		
		var btnSave = new Button("Save as pixel classifier");
		btnSave.disableProperty().bind(enableButtons.not());
		btnSave.setOnAction(e -> {
			try {
				PixelClassifierImageSelectionPane.promptToSaveClassifier(qupath.getProject(), currentClassifier.get());
			} catch (IOException ex) {
				DisplayHelpers.showErrorMessage("Save classifier", ex);
			}
		});
		PaneToolsFX.addGridRow(pane, row++, 0, "Save current thresholder as a pixel classifier", btnSave, btnSave, btnSave);

		
		var btnCreateObjects = new Button("Create objects");
		btnCreateObjects.disableProperty().bind(enableButtons.not());
		var btnClassifyObjects = new Button("Classify detections");
		btnClassifyObjects.disableProperty().bind(enableButtons.not());
		var tilePane = PaneToolsFX.createColumnGrid(btnCreateObjects, btnClassifyObjects);
//		btnCreateObjects.prefWidthProperty().bind(btnClassifyObjects.widthProperty());
		
		btnCreateObjects.setOnAction(e -> {
			PixelClassifierImageSelectionPane.promptToCreateObjects(qupath.getImageData(), 
					(PixelClassificationImageServer)selectedOverlay.get().getPixelClassificationServer());
		});
		btnClassifyObjects.setOnAction(e -> {
			PixelClassifierTools.classifyDetectionsByCentroid(qupath.getImageData(), currentClassifier.get(), true);
		});
		PaneToolsFX.addGridRow(pane, row++, 0, null, tilePane, tilePane, tilePane);
		
		
		selectedPrefilter.addListener((v, o, n) -> updateClassification());
		transforms.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
			updateClassification();
//			if (n != null) {
//				slider.setMin(n.getMinAllowed());
//				slider.setMax(n.getMaxAllowed());
//				slider.setValue((n.getMinAllowed() + n.getMaxAllowed()) / 2.0);
//				slider.setBlockIncrement((slider.getMax()-slider.getMin()) / 100.0);
//			}
		});
		sigma.addListener((v, o, n) -> updateClassification());
		threshold.addListener((v, o, n) -> updateClassification());
		selectedResolution.addListener((v, o, n) -> updateClassification());
		
//		threshold.addListener((v, o, n) -> {
//			if (!slider.isValueChanging())
//				updateClassification();
//			});
//		slider.valueChangingProperty().addListener((v, o, n) -> {
//			if (!n)
//				updateClassification();
//		});
		classificationsAbove.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> updateClassification());
		classificationsBelow.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> updateClassification());
		
		pane.setVgap(6.0);
		pane.setHgap(6.0);
		pane.setPadding(new Insets(10.0));
		
		PaneToolsFX.setMaxWidth(Double.MAX_VALUE, comboResolutions, comboPrefilter,
				transforms, spinner, sigmaSpinner, classificationsAbove, classificationsEqual, classificationsBelow,
				btnSave, btnClassifyObjects, btnCreateObjects, tilePane);
		PaneToolsFX.setFillWidth(Boolean.TRUE, comboResolutions, comboPrefilter,
				transforms, spinner, sigmaSpinner, classificationsAbove, classificationsEqual, classificationsBelow,
				btnSave, btnClassifyObjects, btnCreateObjects, tilePane);
//		PaneToolsFX.setHGrowPriority(Priority.ALWAYS, comboResolutions, comboPrefilter,
//				transforms, spinner, sigmaSpinner, classificationsAbove, classificationsEqual, classificationsBelow,
//				btnSave, btnClassifyObjects, btnCreateObjects, tilePane);
		
		updateGUI();
		
		pane.setMinSize(GridPane.USE_COMPUTED_SIZE, GridPane.USE_COMPUTED_SIZE);
		pane.setMaxSize(GridPane.USE_COMPUTED_SIZE, GridPane.USE_COMPUTED_SIZE);
		
		stage = new Stage();
		stage.setTitle("Simple threshold");
		stage.initOwner(qupath.getStage());
		stage.setScene(new Scene(pane));
		stage.setAlwaysOnTop(true);
		stage.sizeToScene();
		stage.setResizable(false);
		stage.show();
		
		stage.sizeToScene();
		
		stage.setOnHiding(e -> {
			for (var entry : map.entrySet()) {
				if (entry.getKey().getCustomPixelLayerOverlay() == entry.getValue()) {
					PixelClassificationImageServer.setPixelLayer(entry.getKey().getImageData(), null);
					selectedOverlay.set(null);
					entry.getKey().resetCustomPixelLayerOverlay();
				}
			}
		});
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
		
		comboResolutions.getItems().setAll(PixelClassifierImageSelectionPane.getDefaultResolutions(imageData, selectedResolution.get()));
		if (selectedResolution.get() == null)
			comboResolutions.getSelectionModel().selectLast();
		
		transforms.getItems().setAll(getAvailableTransforms(imageData));
		if (transforms.getSelectionModel().getSelectedItem() == null)
			transforms.getSelectionModel().selectFirst();
		
//		var display = viewer.getImageDisplay();
//		var validChannels = new ArrayList<SingleChannelDisplayInfo>();
//		for (var channel : display.availableChannels()) {
//			if (channel instanceof SingleChannelDisplayInfo)
//				validChannels.add((SingleChannelDisplayInfo)channel);
//		}
//		transforms.getItems().setAll(validChannels);

//		var channel = selectedChannel.get();
//		if (channel != null) {
//			slider.setMin(channel.getMinAllowed());
//			slider.setMax(channel.getMaxAllowed());
//			slider.setValue((channel.getMinAllowed() + channel.getMaxAllowed()) / 2.0);
//			slider.setBlockIncrement((slider.getMax()-slider.getMin()) / 100.0);
//		}
		
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
		
		var transform = selectedChannel.get();
		var thresholdValue = threshold.get();
		var resolution = selectedResolution.get();
		if (transform == null || thresholdValue == null || resolution == null)
			return;
		
		var feature = selectedPrefilter.get();
		double sigmaValue = sigma.get();
		
		PixelClassifier classifier;
		ThresholdClassifier thresholder = ValueToClassification.createThresholdClassifier(
				threshold.get(),
				classificationsBelow.getSelectionModel().getSelectedItem(),
				classificationsEqual.getSelectionModel().getSelectedItem(),
				classificationsAbove.getSelectionModel().getSelectedItem()
				);
		
		if (feature == null || sigmaValue <= 0) {
			classifier = PixelClassifiers.createThresholdingClassifier(
					transform,
					resolution.getPixelCalibration(),
					thresholder);
		} else {
			var calculator = FeatureCalculators.createMultiscaleFeatureCalculator(
					Collections.singletonList(transform),
					new double[] {sigmaValue},
					-1,
					false,
					feature
					);
			
			classifier = PixelClassifiers.createThresholdingClassifier(
					calculator,
					resolution.getPixelCalibration(),
					thresholder);
		}
		
//		PixelClassificationImageServer server = new PixelClassificationImageServer(imageData, classifier);

		// Try (admittedly unsuccessfully) to reduce flicker
		viewer.setMinimumRepaintSpacingMillis(1000L);
		viewer.repaint();
		
		var overlay = PixelClassificationOverlay.createPixelClassificationOverlay(viewer, classifier);
		overlay.setLivePrediction(true);
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
	public static List<ColorTransform> getAvailableTransforms(ImageData<BufferedImage> imageData) {
		var validChannels = new ArrayList<ColorTransform>();
		var server = imageData.getServer();
		for (var channel : server.getMetadata().getChannels()) {
			validChannels.add(ColorTransforms.createChannelExtractor(channel.getName()));
		}
		if (server.nChannels() > 1) {
			validChannels.add(ColorTransforms.createMeanChannelTransform());
			validChannels.add(ColorTransforms.createMaximumChannelTransform());
			validChannels.add(ColorTransforms.createMinimumChannelTransform());
		}
		return validChannels;
	}
	
	
}
